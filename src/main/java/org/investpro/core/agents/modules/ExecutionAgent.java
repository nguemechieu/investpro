package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.execution.TradeExecutionCoordinator;
import org.investpro.core.agents.risk.RiskReviewResult;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.TradeRiskContext;
import org.investpro.strategy.StrategySignal;

import java.util.Map;
import java.util.Objects;

/**
 * Agent responsible for trade execution coordination.
 * <p>
 * Processes:
 * - Risk-approved signals
 * - AI reasoning results
 * - Execution commands
 * <p>
 * Publishes:
 * - Execution events
 * - Order submissions
 * - Trade execution notifications
 * <p>
 * Note: This agent publishes execution requests but does not directly
 * place orders. Final execution goes through Trade Execution Coordinator
 * and Final Risk Gate.
 */
@Slf4j
public class ExecutionAgent implements org.investpro.core.agents.Agent {
    private final TradeExecutionCoordinator tradeExecutionCoordinator;
    private volatile boolean running = false;
    private AgentContext context;

    public ExecutionAgent(TradeExecutionCoordinator tradeExecutionCoordinator) {
        this.tradeExecutionCoordinator = Objects.requireNonNull(
                tradeExecutionCoordinator,
                "tradeExecutionCoordinator cannot be null");
    }

    @Override
    public String name() {
        return "ExecutionAgent";
    }

    @Override
    public void start(AgentContext context) {
        if (running) {
            log.warn("ExecutionAgent is already started");
            return;
        }

        this.context = context;
        this.running = true;

        log.info("ExecutionAgent started");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        this.running = false;
        this.context = null;

        log.info("ExecutionAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        try {
            if (AgentEvent.RISK_REVIEWED.equals(event.type())) {
                coordinateExecution(event);
            }

        } catch (Exception e) {
            log.error("Error processing execution event", e);
        }
    }

    private void coordinateExecution(AgentEvent event) throws Exception {
        if (context == null || !context.isAutoTradingEnabled()) {
            log.info("ExecutionAgent blocked execution because auto trading is disabled.");
            return;
        }

        if (!(event.payload() instanceof Map<?, ?> rawContext)) {
            log.debug("ExecutionAgent ignored RISK_REVIEWED event without context map");
            return;
        }

        if (isTrue(rawContext.get("halt_pipeline")) || isTrue(rawContext.get("risk_blocked"))) {
            log.info("ExecutionAgent blocked by risk review. reason={}", rawContext.get("risk_reason"));
            return;
        }

        Object signalValue = rawContext.get("signal");
        if (!(signalValue instanceof StrategySignal signal)) {
            log.warn("ExecutionAgent cannot execute without StrategySignal");
            return;
        }

        RiskReviewResult review = rawContext.get("trade_review") instanceof RiskReviewResult result ? result : null;
        TradeRiskContext riskContext = rawContext.get("risk_context") instanceof TradeRiskContext existing
                ? existing
                : buildRiskContext(signal);

        tradeExecutionCoordinator.processReviewedSignal(signal, riskContext, review)
                .thenAccept(result -> log.info("ExecutionAgent execution result: {}", result.message()))
                .exceptionally(exception -> {
                    log.error("ExecutionAgent failed to coordinate execution", exception);
                    return null;
                });
    }

    private TradeRiskContext buildRiskContext(StrategySignal signal) throws Exception {
        if (context == null || context.getExchange() == null) {
            throw new IllegalStateException("Execution exchange is unavailable");
        }
        TradePair pair = context.getExchange().getTradePairSymbol().stream()
                .filter(candidate -> candidate.toString('/').equalsIgnoreCase(signal.getSymbol()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Signal instrument is not in the exchange catalog"));
        return org.investpro.core.pipeline.BotRiskContextService.fromSignal(context.getExchange(), pair, signal);
    }

    private boolean isTrue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
}
