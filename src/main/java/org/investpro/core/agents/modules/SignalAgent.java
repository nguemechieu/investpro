package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.data.CandleData;
import org.investpro.enums.MarketBehavior;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.StrategyDecisionResult;
import org.investpro.service.StrategyDecisionService;
import org.investpro.strategy.StrategySignal;
import org.investpro.utils.CandleAggregator;
import org.investpro.utils.CandleDataSupplier;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent responsible for generating trading signals from market data events.
 * <p>
 * Processes MARKET_CANDLE and MARKET_TICK events and generates strategy signals
 * via StrategyDecisionService.
 * <p>
 * Publishes strategy_signal events to the event bus.
 */
@Slf4j
public class SignalAgent implements Agent {

    private volatile boolean running = false;
    private AgentContext context;
    private AgentEventBus eventBus;
    private final StrategyDecisionService decisionService;
    private final Map<String, List<CandleData>> candleHistory = new ConcurrentHashMap<>();
    private final Map<String, org.investpro.models.trading.Ticker> quotes = new ConcurrentHashMap<>();
    private final Map<String, Integer> publishedBars = new ConcurrentHashMap<>();
    private final Map<String, AgentEvent> latestCandleEvents = new ConcurrentHashMap<>();
    private final Map<String, Long> tickEvaluationTimes = new ConcurrentHashMap<>();
    private final Map<String, String> decisionReasons = new ConcurrentHashMap<>();
    private static final int MAX_CANDLES_PER_CONTEXT = 500;
    private static final int MIN_SEEDED_CANDLES = 120;

    public SignalAgent() {
        this(new StrategyDecisionService());
    }

    SignalAgent(StrategyDecisionService decisionService) {
        this.decisionService = java.util.Objects.requireNonNull(decisionService);
    }

    @Override
    public String name() {
        return "SignalAgent";
    }

    @Override
    public synchronized void start(AgentContext context) {
        if (running) {
            log.warn("SignalAgent is already started");
            return;
        }

        this.context = context;
        this.eventBus = context.getEventBus();
        this.running = true;

        log.info("SignalAgent started and subscribed to market events");
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }

        this.running = false;
        this.context = null;
        this.eventBus = null;
        quotes.clear();
        publishedBars.clear();
        latestCandleEvents.clear();
        tickEvaluationTimes.clear();
        decisionReasons.clear();
        seedTasks.values().forEach(task -> task.cancel(true)); seedTasks.clear(); seedAttempts.clear();
        candleHistory.clear();

        log.info("SignalAgent stopped");
    }

    @Override
    public synchronized void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        try {
            if (AgentEvent.MARKET_CANDLE.equals(event.type())) {
                handleCandleEvent(event);
            } else if (AgentEvent.MARKET_TICK.equals(event.type())) {
                handleTickEvent(event);
            }
        } catch (Exception e) {
            log.error("Error processing signal event: {}", event.type(), e);
        }
    }

    /**
     * Handle MARKET_CANDLE events to generate strategy signals.
     */
    private void handleCandleEvent(AgentEvent event) {
        Map<String, Object> metadata = event.metadata();
        if (metadata == null || metadata.isEmpty()) {
            log.debug("Candle event has no metadata");
            return;
        }

        try {
            String symbol = resolveSymbol(metadata);
            String timeframe = resolveTimeframe(metadata);
            TradePair tradePair = resolveTradePair(metadata);
            if (symbol == null || timeframe == null) return;
            latestCandleEvents.put(symbol + "_" + timeframe, event);

            List<CandleData> candles = seedHistoryIfNeeded(symbol, timeframe, tradePair,
                    resolveCandles(symbol, timeframe, event.payload()));

            if (symbol == null || timeframe == null || candles == null || candles.isEmpty()) {
                log.debug("Incomplete candle data: symbol={}, timeframe={}, candleCount={}",
                        symbol, timeframe, candles != null ? candles.size() : 0);
                return;
            }

            // Extract prices
            CandleData latest = candles.get(candles.size() - 1);
            Double current = number(metadata.get("current"), latest.closePrice());
            org.investpro.models.trading.Ticker quote = quotes.get(symbol);
            if (quote == null || quote.getQuoteType() != org.investpro.models.trading.Ticker.QuoteType.LIVE
                    || System.currentTimeMillis() - quote.getTimestamp() > 30_000
                    || quote.getTimestamp() > System.currentTimeMillis() + 1_000
                    || !(quote.getBidPrice() > 0) || !(quote.getAskPrice() > quote.getBidPrice())) {
                recordDecisionReason(symbol + "_" + timeframe, "Waiting for fresh live bid/ask quote");
                return;
            }
            Double bid = quote.getBidPrice();
            Double ask = quote.getAskPrice();
            Double volatility = number(metadata.get("volatility"), 0.0);
            Double volume = number(metadata.get("volume"), latest.volume());

            // Generate signal with correct StrategyDecisionService signature
            MarketBehavior behavior = MarketBehavior.valueOf(
                    new org.investpro.strategy.auto.MarketRegimeDetector().detect(candles).name());

            StrategyDecisionResult result = decisionService.generateDecision(
                    symbol,
                    timeframe,
                    candles,
                    bid,
                    ask,
                    current,
                    volatility,
                    volume,
                    behavior,
                    tradePair);

            if (!result.isSuccess()) {
                recordDecisionReason(symbol + "_" + timeframe, result.getRejectionReason());
                return;
            }

            if (!result.hasActionableSignal()) {
                recordDecisionReason(symbol + "_" + timeframe, "HOLD: " + result.getSignal().getReason());
                return;
            }

            decisionReasons.remove(symbol + "_" + timeframe);
            StrategySignal signal = result.getSignal().toBuilder()
                    .metadata("bid", bid).metadata("ask", ask)
                    .metadata("quote_timestamp", quote.getTimestamp())
                    .metadata("volatility", volatility).build();
            String publicationKey = symbol + "_" + timeframe + "_" + (result.getAssignment() == null
                    ? signal.getStrategyId() : result.getAssignment().getAssignmentId());
            var publish = new java.util.concurrent.atomic.AtomicBoolean(false);
            publishedBars.compute(publicationKey, (_, previous) -> {
                if (previous == null || latest.openTime() > previous) {
                    publish.set(true);
                    return latest.openTime();
                }
                return previous;
            });
            if (!publish.get()) return;
            if (!publishSignal(signal, result, tradePair)) {
                publishedBars.remove(publicationKey, latest.openTime());
                return;
            }

            log.info("Strategy signal: {} {} at {} (confidence: {}, strategy: {})",
                    signal.getSide(), symbol, timeframe,
                    String.format("%.2f", signal.getConfidence()),
                    signal.getStrategyId());

        } catch (Exception e) {
            log.error("Error handling candle event", e);
        }
    }

    /**
     * Handle MARKET_TICK events - real-time price updates.
     */
    private void handleTickEvent(AgentEvent event) {
        if (event.payload() instanceof org.investpro.models.trading.Ticker ticker) {
            String symbol = resolveSymbol(event.metadata());
            if (symbol != null) {
                quotes.put(symbol, ticker);
                // Retry a candle skipped because its quote arrived later. Limit evaluations per context.
                latestCandleEvents.forEach((key, candleEvent) -> {
                    if (!symbol.equals(resolveSymbol(candleEvent.metadata()))) return;
                    long now = System.currentTimeMillis();
                    var evaluate = new java.util.concurrent.atomic.AtomicBoolean(false);
                    tickEvaluationTimes.compute(key, (_, previous) -> {
                        if (previous == null || now - previous >= 1_000) {
                            evaluate.set(true);
                            return now;
                        }
                        return previous;
                    });
                    if (evaluate.get()) handleCandleEvent(candleEvent);
                });
            }
        }
        // Tick events are for real-time monitoring, not strategy signals
        log.trace("Received tick event for: {}", event.metadata().get("tradePair"));
    }

    /**
     * Publish strategy signal to the event bus.
     */
    private boolean publishSignal(@NotNull StrategySignal signal, @NotNull StrategyDecisionResult result, TradePair pair) {
        if (eventBus == null) {
            log.warn("Event bus not available for publishing signal");
            return false;
        }

        try {
            // Create strategy_signal event and publish
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("symbol", signal.getSymbol());
            metadata.put("timeframe", signal.getTimeframe());
            metadata.put("side", signal.getSide());
            metadata.put("confidence", signal.getConfidence());
            metadata.put("strategy_name", signal.getStrategyName());
            if (result.getAssignment() != null) {
                metadata.put("assignment_id", result.getAssignment().getAssignmentId());
                metadata.put("assignment_score", result.getAssignment().getScoreAtAssignment());
                metadata.put("assignment_mode", result.getAssignment().getMode());
            }
            if (requiresPaperValidation() && context != null && context.getExchange() != null
                    && !context.getExchange().isBotPaperTrading()
                    && !hasLiveApproval(result.getAssignment())) {
                metadata.put("trade_allowed", false);
                metadata.put("block_reason", "Paper trading validation required before live execution");
            }
            if (pair != null) {
                metadata.put("tradePairObject", pair);
                metadata.put("tradePair", pair);
            }

            AgentEvent signalEvent = new AgentEvent(
                    AgentEvent.SIGNAL_CREATED,
                    "SignalAgent",
                    signal,
                    Instant.now(),
                    metadata);

            eventBus.publish(signalEvent);

            log.debug("Signal published: {} {} (confidence: {})",
                    signal.getSide(), signal.getSymbol(),
                    String.format("%.2f", signal.getConfidence()));
            return true;

        } catch (Exception e) {
            log.error("Failed to publish signal event", e);
            return false;
        }
    }

    private boolean requiresPaperValidation() {
        return Boolean.parseBoolean(System.getProperty(
                "investpro.strategy.requirePaperTradingBeforeLive",
                "true"));
    }

    static boolean hasLiveApproval(org.investpro.strategy.StrategyAssignment assignment) {
        if (assignment == null || !assignment.isValid()) return false;
        return org.investpro.strategy.management.StrategyAssignmentManager.getInstance()
                .getRecord(assignment.getAssignmentId())
                .filter(record -> java.util.Objects.equals(record.getStrategyId(), assignment.getStrategyId())
                        && java.util.Objects.equals(record.getSymbol(), assignment.getSymbol())
                        && java.util.Objects.equals(record.getTimeframe(), assignment.getTimeframe().getCode())
                        && org.investpro.strategy.management.StrategyAssignmentGatekeeper.getInstance()
                        .canTrade(record).isAllowed())
                .isPresent();
    }

    private void recordDecisionReason(String key, String reason) {
        String value = reason == null ? "Unspecified decision rejection" : reason;
        if (!value.equals(decisionReasons.put(key, value))) {
            log.info("Strategy evaluation {}: {}", key, value);
        }
    }
    private String resolveSymbol(Map<String, Object> metadata) {
        Object symbol = metadata.get("symbol");
        if (symbol != null && !String.valueOf(symbol).isBlank()) {
            return String.valueOf(symbol).trim();
        }

        Object tradePair = metadata.get("tradePair");
        if (tradePair != null && !String.valueOf(tradePair).isBlank()) {
            return String.valueOf(tradePair).trim();
        }

        return null;
    }

    private String resolveTimeframe(Map<String, Object> metadata) {
        Object timeframe = metadata.get("timeframe");
        if (timeframe != null && !String.valueOf(timeframe).isBlank()) {
            return String.valueOf(timeframe).trim();
        }
        return "1h";
    }

    private TradePair resolveTradePair(Map<String, Object> metadata) {
        Object direct = metadata.get("tradePairObject");
        if (direct instanceof TradePair pair) {
            return pair;
        }

        Object tradePair = metadata.get("tradePair");
        if (tradePair instanceof TradePair pair) {
            return pair;
        }

        return null;
    }

    private List<CandleData> resolveCandles(String symbol, String timeframe, Object payload) {
        String key = "%s_%s".formatted(symbol, timeframe);

        if (payload instanceof List<?> list) {
            List<CandleData> candles = list.stream()
                    .filter(CandleData.class::isInstance)
                    .map(CandleData.class::cast)
                    .toList();
            if (!candles.isEmpty()) {
                candleHistory.put(key, new ArrayList<>(trimCandles(candles)));
            }
            return candles;
        }

        if (payload instanceof CandleData candle) {
            List<CandleData> candles = candleHistory.computeIfAbsent(
                    key,
                    ignored -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (candles) {
                candles.removeIf(existing -> existing != null && existing.openTime() == candle.openTime());
                candles.add(candle);
                candles.sort(java.util.Comparator.comparingInt(CandleData::openTime));
                while (candles.size() > MAX_CANDLES_PER_CONTEXT) {
                    candles.removeFirst();
                }
                return List.copyOf(candles);
            }
        }

        return List.of();
    }

    private List<CandleData> trimCandles(List<CandleData> candles) {
        if (candles.size() <= MAX_CANDLES_PER_CONTEXT) {
            return candles;
        }
        return candles.subList(candles.size() - MAX_CANDLES_PER_CONTEXT, candles.size());
    }

    private final Map<String, Long> seedAttempts = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.CompletableFuture<?>> seedTasks = new ConcurrentHashMap<>();

    private List<CandleData> seedHistoryIfNeeded(String symbol, String timeframe, TradePair tradePair,
            List<CandleData> currentCandles) {
        if (currentCandles == null) currentCandles = List.of();
        AgentContext session = context;
        if (currentCandles.size() >= MIN_SEEDED_CANDLES || session == null
                || session.getExchange() == null || tradePair == null) return currentCandles;
        String key = symbol + "_" + timeframe;
        long now = System.currentTimeMillis();
        if (seedTasks.containsKey(key) || now - seedAttempts.getOrDefault(key, 0L) < 30_000) return currentCandles;
        seedAttempts.put(key, now);
        var future = org.investpro.core.concurrent.AppExecutors.submit(org.investpro.core.concurrent.AppExecutors.MARKET_DATA, () -> {
            int seconds = CandleAggregator.TIMEFRAME_SECONDS.getOrDefault(timeframe, 3600);
            CandleDataSupplier supplier = session.getExchange().getCandleDataSupplier(seconds, tradePair);
            return supplier == null ? List.<CandleData>of() : supplier.get().get(4, TimeUnit.SECONDS);
        });
        seedTasks.put(key, future);
        future.whenComplete((seeded, error) -> {
            synchronized (SignalAgent.this) {
                seedTasks.remove(key, future);
                if (!running || context != session) return;
                if (error != null || seeded == null || seeded.isEmpty()) {
                    log.debug("SignalAgent history unavailable for {}/{}", symbol, timeframe); return;
                }
                List<CandleData> latest = candleHistory.getOrDefault(key, List.of());
                int latestBar = latest.isEmpty() ? Integer.MAX_VALUE : latest.getLast().openTime();
                var merged = new java.util.TreeMap<Integer, CandleData>();
                seeded.stream().filter(java.util.Objects::nonNull).filter(c -> c.openTime() <= latestBar)
                        .forEach(c -> merged.put(c.openTime(), c));
                latest.forEach(c -> merged.put(c.openTime(), c));
                candleHistory.put(key, new ArrayList<>(trimCandles(new ArrayList<>(merged.values()))));
            }
        });
        return currentCandles;
    }
    private double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // Use fallback.
            }
        }
        return fallback;
    }

    private TradePair parsePair(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        String[] parts = symbol.replace('_', '/').replace('-', '/').split("/");
        if (parts.length < 2) {
            return null;
        }
        try {
            return new TradePair(parts[0], parts[1]);
        } catch (Exception exception) {
            log.debug("Unable to parse TradePair from signal symbol {}", symbol, exception);
            return null;
        }
    }
}
