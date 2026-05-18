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
    private static final int MAX_CANDLES_PER_CONTEXT = 500;
    private static final int MIN_SEEDED_CANDLES = 120;

    public SignalAgent() {
        this.decisionService = new StrategyDecisionService();
    }

    @Override
    public String name() {
        return "SignalAgent";
    }

    @Override
    public void start(AgentContext context) {
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
    public void stop() {
        if (!running) {
            return;
        }

        this.running = false;
        this.context = null;
        this.eventBus = null;

        log.info("SignalAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
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
            Double bid = number(metadata.get("bid"), current > 0.0 ? current * 0.99995 : 0.0);
            Double ask = number(metadata.get("ask"), current > 0.0 ? current * 1.00005 : 0.0);
            Double volatility = number(metadata.get("volatility"), 0.0);
            Double volume = number(metadata.get("volume"), latest.volume());

            // Generate signal with correct StrategyDecisionService signature
            MarketBehavior behavior = MarketBehavior.RANGING; // Default, can be enhanced

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
                log.debug("Strategy decision rejected for {}/{}: {}", symbol, timeframe,
                        result.getRejectionReason());
                return;
            }

            if (!result.hasActionableSignal()) {
                log.trace("No actionable signal (HOLD) for {}/{}", symbol, timeframe);
                return;
            }

            StrategySignal signal = result.getSignal();
            publishSignal(signal, result);

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
        // Tick events are for real-time monitoring, not strategy signals
        log.trace("Received tick event for: {}", event.metadata().get("tradePair"));
    }

    /**
     * Publish strategy signal to the event bus.
     */
    private void publishSignal(@NotNull StrategySignal signal, @NotNull StrategyDecisionResult result) {
        if (eventBus == null) {
            log.warn("Event bus not available for publishing signal");
            return;
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
            if (requiresPaperValidation()) {
                metadata.put("trade_allowed", false);
                metadata.put("block_reason", "Paper trading validation required before live execution");
            }
            TradePair pair = parsePair(signal.getSymbol());
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

        } catch (Exception e) {
            log.error("Failed to publish signal event", e);
        }
    }

    private boolean requiresPaperValidation() {
        return Boolean.parseBoolean(System.getProperty(
                "tradeadviser.strategy.requirePaperTradingBeforeLive",
                "true"));
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

    private List<CandleData> seedHistoryIfNeeded(
            String symbol,
            String timeframe,
            TradePair tradePair,
            List<CandleData> currentCandles) {
        if (currentCandles == null) {
            currentCandles = List.of();
        }
        if (currentCandles.size() >= MIN_SEEDED_CANDLES || context == null || context.getExchange() == null
                || tradePair == null) {
            return currentCandles;
        }

        try {
            int seconds = CandleAggregator.TIMEFRAME_SECONDS.getOrDefault(timeframe, 3600);
            CandleDataSupplier supplier = context.getExchange().getCandleDataSupplier(seconds, tradePair);
            if (supplier == null) {
                return currentCandles;
            }

            List<CandleData> seeded = supplier.get().get(4, TimeUnit.SECONDS);
            if (seeded == null || seeded.isEmpty()) {
                return currentCandles;
            }

            List<CandleData> merged = new ArrayList<>(seeded);
            merged.addAll(currentCandles);
            List<CandleData> sorted = merged.stream()
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toMap(
                            CandleData::openTime,
                            candle -> candle,
                            (first, second) -> second,
                            java.util.TreeMap::new))
                    .values()
                    .stream()
                    .sorted(Comparator.comparingInt(CandleData::openTime))
                    .toList();

            List<CandleData> trimmed = new ArrayList<>(trimCandles(sorted));
            candleHistory.put("%s_%s".formatted(symbol, timeframe), trimmed);
            log.info("SignalAgent seeded {} candles for {}/{}", trimmed.size(), symbol, timeframe);
            return List.copyOf(trimmed);
        } catch (Exception exception) {
            log.debug("SignalAgent could not seed candle history for {}/{}: {}", symbol, timeframe,
                    exception.getMessage());
            return currentCandles;
        }
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
