package org.investpro.risk;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.strategy.StrategySignal;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class RiskAgent implements Agent {
    private final RiskReviewer reviewer;
    private AgentContext context;
    private volatile boolean running;

    public RiskAgent(RiskReviewer reviewer) {
        this.reviewer = Objects.requireNonNull(reviewer, "reviewer cannot be null");
    }

    @Override
    public String name() {
        return "RiskAgent";
    }

    @Override
    public void start(AgentContext context) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.running = true;
        log.info("RiskAgent started");
    }

    @Override
    public void stop() {
        running = false;
        context = null;
        log.info("RiskAgent stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null || !isSignalEvent(event)) {
            return;
        }

        Map<String, Object> working = new HashMap<>();
        if (event.metadata() != null) {
            working.putAll(event.metadata());
        }
        if (event.payload() instanceof Map<?, ?> payloadMap) {
            payloadMap.forEach((key, value) -> {
                if (key != null) {
                    working.put(String.valueOf(key), value);
                }
            });
        } else if (event.payload() != null) {
            working.put("payload", event.payload());
        }
        if (event.payload() instanceof StrategySignal signal) {
            working.put("signal", signal);
        }
        working.putIfAbsent("decision_id", UUID.randomUUID().toString());

        process(working)
                .thenAccept(this::publishReviewed)
                .exceptionally(exception -> {
                    log.error("RiskAgent review failed", exception);
                    Map<String, Object> failed = new HashMap<>(working);
                    String reason = exception.getMessage() == null ? "Risk review failed" : exception.getMessage();
                    block(failed, reason);
                    remember("failed", failed);
                    publishAlert(failed, reason);
                    publishReviewed(failed);
                    return null;
                });
    }

    public CompletableFuture<Map<String, Object>> process(Map<String, Object> inputContext) {
        Map<String, Object> working = new HashMap<>();
        if (inputContext != null) {
            working.putAll(inputContext);
        }

        working.put("symbol", normalizeSymbol(working.get("symbol")));
        working.putIfAbsent("decision_id", UUID.randomUUID().toString());

        if (Boolean.FALSE.equals(asBoolean(working.get("trade_allowed")))) {
            String reason = firstText(working.get("block_reason"), "Blocked by regime");
            block(working, reason);
            remember("blocked_by_regime", working);
            return CompletableFuture.completedFuture(working);
        }

        Object signalValue = working.get("signal");
        if (!(signalValue instanceof StrategySignal rawSignal) || !rawSignal.normalized().isActionable()) {
            String reason = firstText(working.get("news_bias_reason"), "No active signal.");
            block(working, reason);
            remember("skipped", working);
            return CompletableFuture.completedFuture(working);
        }

        StrategySignal signal = rawSignal.normalized();
        working.put("signal", signal);
        working.put("symbol", normalizeSymbol(firstText(working.get("symbol"), signal.getSymbol())));
        working.putIfAbsent("timeframe", signal.getTimeframe());

        // Filter out null values before creating immutable map copy
        // (Map.copyOf() does not allow null values)
        Map<String, Object> contextForRequest = new LinkedHashMap<>();
        working.forEach((key, value) -> {
            if (value != null) {
                contextForRequest.put(key, value);
            }
        });

        RiskReviewRequest request = RiskReviewRequest.builder()
                .symbol(String.valueOf(working.get("symbol")))
                .decisionId(String.valueOf(working.get("decision_id")))
                .signal(signal)
                .dataset(working.get("dataset"))
                .timeframe(firstText(working.get("timeframe"), signal.getTimeframe()))
                .regimeSnapshot(working.get("regime_snapshot"))
                .portfolioSnapshot(working.get("portfolio_snapshot"))
                .context(Map.copyOf(contextForRequest))
                .build();

        return reviewer.review(request)
                .thenApply(review -> applyReview(working, review == null
                        ? RiskReviewResult.rejected("Risk reviewer returned no decision")
                        : review));
    }

    private Map<String, Object> applyReview(Map<String, Object> working, RiskReviewResult review) {
        working.put("trade_review", review);
        if (!review.isApproved()) {
            String reason = firstText(review.getReason(), "Rejected by risk engine");
            block(working, reason);
            publishAlert(working, reason);
            remember("rejected", working);
            return working;
        }

        working.put("halt_pipeline", false);
        working.put("risk_blocked", false);
        working.put("risk_reason", null);
        remember("approved", working);
        return working;
    }

    private void block(Map<String, Object> working, String reason) {
        working.put("halt_pipeline", true);
        working.put("risk_blocked", true);
        working.put("risk_reason", reason);
    }

    private void publishReviewed(Map<String, Object> updatedContext) {
        AgentContext current = context;
        if (current == null || current.getEventBus() == null) {
            return;
        }
        // Filter out null values before creating immutable map copy
        // (Map.copyOf() does not allow null values)
        Map<String, Object> cleanMetadata = new LinkedHashMap<>();
        if (updatedContext != null) {
            updatedContext.forEach((key, value) -> {
                if (value != null) {
                    cleanMetadata.put(key, value);
                }
            });
        }
        current.getEventBus().publishAsync(new AgentEvent(
                AgentEvent.RISK_REVIEWED,
                name(),
                updatedContext,
                Instant.now(),
                Map.copyOf(cleanMetadata)));
    }

    private void publishAlert(Map<String, Object> working, String reason) {
        AgentContext current = context;
        if (current == null || current.getEventBus() == null) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("symbol", working.get("symbol"));
        metadata.put("decision_id", working.get("decision_id"));
        metadata.put("risk_reason", reason);
        current.getEventBus().publishAsync(AgentEvent.of(AgentEvent.RISK_ALERT, name(), reason, metadata));
    }

    private void remember(String outcome, Map<String, Object> working) {
        log.debug("RiskAgent memory outcome={} symbol={} decisionId={} reason={}",
                outcome,
                working.get("symbol"),
                working.get("decision_id"),
                working.get("risk_reason"));
    }

    private boolean isSignalEvent(AgentEvent event) {
        return AgentEvent.SIGNAL_CREATED.equals(event.type())
                || "SIGNAL".equals(event.type())
                || "STRATEGY_SIGNAL".equals(event.type())
                || AgentEvent.STRATEGY_SIGNAL_APPROVED.equals(event.type());
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String normalizeSymbol(Object value) {
        String text = firstText(value, "");
        return text.isBlank() ? "" : text.trim().toUpperCase();
    }

    private String firstText(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return String.valueOf(value).trim();
    }
}
