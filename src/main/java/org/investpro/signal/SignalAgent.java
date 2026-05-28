package org.investpro.signal;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.data.CandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.persistence.repository.StrategyAssignmentRepository;
import org.investpro.service.StrategyDecisionService;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.StrategyDecisionResult;
import org.investpro.strategy.StrategySignal;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.Side;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SignalAgent listens to market-data events and publishes signal events.
 *
 * <p>Safety rule: this agent does not place trades directly.
 * Execution must stay inside the normal risk/execution pipeline.</p>
 */
@Slf4j
@Data
public class SignalAgent implements Agent {

    public static final String AGENT_NAME = "SignalAgent";

    public static final String EVENT_SIGNAL_GENERATED = "SIGNAL_GENERATED";
    public static final String EVENT_SIGNAL_HOLD = "SIGNAL_HOLD";
    public static final String EVENT_SIGNAL_ERROR = "SIGNAL_ERROR";
    public static final String EVENT_SIGNAL_AGENT_STATUS = "SIGNAL_AGENT_STATUS";

    private static final double MIN_CONFIDENCE_TO_EMIT_TRADE_SIGNAL = 0.58;
    private static final Duration MIN_SIGNAL_INTERVAL = Duration.ofSeconds(10);

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Map<String, SignalDecision> latestSignalsBySymbol = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSignalPublishedAt = new ConcurrentHashMap<>();
    private final Map<String, Double> lastPriceBySymbol = new ConcurrentHashMap<>();
    private final Map<String, Long> eventCountBySymbol = new ConcurrentHashMap<>();
    private final Map<String, List<CandleData>> candlesBySymbolTimeframe = new ConcurrentHashMap<>();
    private final Set<String> backfilledCandleKeys = ConcurrentHashMap.newKeySet();
    private final StrategyDecisionService strategyDecisionService = new StrategyDecisionService();

    private volatile AgentContext context;
    private volatile AgentEventBus eventBus;

    @Override
    public String name() {
        return AGENT_NAME;
    }

    @Override
    public void start(AgentContext context) {
        Objects.requireNonNull(context, "context must not be null");

        if (!running.compareAndSet(false, true)) {
            log.debug("{} already running.", AGENT_NAME);
            return;
        }

        this.context = context;
        this.eventBus = context.getEventBus();

        publishSystem("SignalAgent started.");
        log.info("{} started.", AGENT_NAME);
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        publishSystem("SignalAgent stopped.");

        context = null;
        eventBus = null;

        log.info("{} stopped.", AGENT_NAME);
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running.get() || event == null) {
            return;
        }

        try {
            String eventType = safe(event.type()).toUpperCase();

            if (!isMarketDataEvent(eventType)) {
                return;
            }

            Object payload = event.payload();
            TradePair tradePair = resolveTradePair(event, payload);

            if (tradePair == null) {
                tradePair = activeTradePair();
            }

            if (tradePair == null) {
                return;
            }

            if (AgentEvent.MARKET_CANDLE.equals(eventType)
                    && payload instanceof CandleData candle
                    && publishAssignedStrategySignal(event, tradePair, candle)) {
                return;
            }

            SignalDecision decision = analyze(eventType, tradePair, payload);

            if (decision == null) {
                return;
            }

            latestSignalsBySymbol.put(decision.symbol(), decision);

            if (shouldPublish(decision)) {
                publishSignal(decision);
            }

        } catch (Exception exception) {
            log.warn("{} failed to process event: {}", AGENT_NAME, exception.getMessage(), exception);
            publishError(exception);
        }
    }

    private boolean publishAssignedStrategySignal(AgentEvent event, TradePair tradePair, CandleData candle) {
        AgentEventBus bus = eventBus;
        if (bus == null || tradePair == null || candle == null) {
            return false;
        }

        String symbol = tradePairText(tradePair);
        Timeframe timeframe = resolveTimeframe(event, symbol);
        List<CandleData> candles = appendCandle(symbol, timeframe, candle);
        if (candles.size() < 50) {
            candles = backfillCandles(symbol, timeframe, tradePair, candles);
        }
        double current = candle.closePrice();
        double spread = current > 0.0 ? Math.max(current * 0.0001, 0.00000001) : 0.0;
        double bid = numberMetadata(event, "bid", current > 0.0 ? current - spread / 2.0 : 0.0);
        double ask = numberMetadata(event, "ask", current > 0.0 ? current + spread / 2.0 : 0.0);

        StrategyDecisionResult decision = strategyDecisionService.generateDecision(
                symbol,
                timeframe.getCode(),
                candles,
                bid,
                ask,
                current,
                0.0,
                candle.volume(),
                null,
                tradePair);

        if (decision == null || !decision.isSuccess() || decision.getSignal() == null) {
            log.debug("Assigned strategy decision unavailable for {} {}: {}",
                    symbol,
                    timeframe.getCode(),
                    decision == null ? "no decision" : decision.getRejectionReason());
            return false;
        }

        StrategySignal signal = decision.getSignal().normalized();
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (event.metadata() != null) {
            metadata.putAll(event.metadata());
        }
        metadata.put("symbol", symbol);
        metadata.put("tradePair", symbol);
        metadata.put("tradePairObject", tradePair);
        metadata.put("timeframe", signal.getTimeframe());
        metadata.put("strategy", signal.getStrategyName());
        metadata.put("strategyId", signal.getStrategyId());
        metadata.put("side", signal.getSide().name());
        metadata.put("confidence", signal.getConfidence());
        metadata.put("assignmentId", decision.getAssignment().getAssignmentId());

        bus.publish(new AgentEvent(
                AgentEvent.SIGNAL_CREATED,
                AGENT_NAME,
                signal,
                Instant.now(),
                metadata));

        log.info("{} published assigned strategy signal. symbol={} timeframe={} strategy={} side={} confidence={}",
                AGENT_NAME,
                symbol,
                signal.getTimeframe(),
                signal.getStrategyId(),
                signal.getSide(),
                "%.2f".formatted(signal.getConfidence()));
        return true;
    }

    private List<CandleData> appendCandle(String symbol, Timeframe timeframe, CandleData candle) {
        String key = symbol + "::" + timeframe.getCode();
        List<CandleData> candles = candlesBySymbolTimeframe.computeIfAbsent(
                key,
                ignored -> java.util.Collections.synchronizedList(new ArrayList<>()));
        synchronized (candles) {
            candles.add(candle);
            candles.sort(Comparator.comparingLong(CandleData::openTime));
            while (candles.size() > 500) {
                candles.removeFirst();
            }
            return List.copyOf(candles);
        }
    }

    private List<CandleData> backfillCandles(
            String symbol,
            Timeframe timeframe,
            TradePair tradePair,
            List<CandleData> currentCandles
    ) {
        AgentContext activeContext = context;
        if (activeContext == null || activeContext.getExchange() == null || tradePair == null || timeframe == null) {
            return currentCandles;
        }

        String key = symbol + "::" + timeframe.getCode();
        if (!backfilledCandleKeys.add(key)) {
            return currentCandles;
        }

        try {
            CandleDataSupplier supplier = activeContext.getExchange()
                    .getCandleDataSupplier(timeframe.getSeconds(), tradePair);
            if (supplier == null) {
                return currentCandles;
            }

            Future<List<CandleData>> future = supplier.get();
            List<CandleData> historical = future == null ? List.of() : future.get(10, TimeUnit.SECONDS);
            if (historical == null || historical.isEmpty()) {
                return currentCandles;
            }

            List<CandleData> merged = new ArrayList<>(historical);
            if (currentCandles != null) {
                merged.addAll(currentCandles);
            }

            merged = merged.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(CandleData::openTime))
                    .toList();

            List<CandleData> target = candlesBySymbolTimeframe.computeIfAbsent(
                    key,
                    ignored -> java.util.Collections.synchronizedList(new ArrayList<>()));
            synchronized (target) {
                target.clear();
                int from = Math.max(0, merged.size() - 500);
                target.addAll(merged.subList(from, merged.size()));
                log.info("{} backfilled {} candles for {}", AGENT_NAME, target.size(), key);
                return List.copyOf(target);
            }
        } catch (Exception exception) {
            log.warn("{} candle backfill failed for {}: {}", AGENT_NAME, key, exception.getMessage());
            return currentCandles;
        }
    }

    private Timeframe resolveTimeframe(AgentEvent event, String symbol) {
        Object value = event == null || event.metadata() == null ? null : event.metadata().get("timeframe");
        String text = value == null ? "" : String.valueOf(value).trim();
        if (!text.isBlank()) {
            for (Timeframe candidate : Timeframe.values()) {
                if (candidate.name().equalsIgnoreCase(text) || candidate.getCode().equalsIgnoreCase(text)) {
                    return candidate;
                }
            }
        }

        StrategyAssignment assignment = symbol == null || symbol.isBlank()
                ? null
                : StrategyAssignmentRepository.getInstance()
                .getForSymbolAllTimeframes(symbol)
                .stream()
                .filter(Objects::nonNull)
                .filter(StrategyAssignment::isValid)
                .filter(candidate -> !candidate.isExpired())
                .max(Comparator.comparingDouble(StrategyAssignment::getScoreAtAssignment))
                .orElse(null);
        if (assignment != null && assignment.getTimeframe() != null) {
            return assignment.getTimeframe();
        }

        return Timeframe.H1;
    }

    private double numberMetadata(AgentEvent event, String key, double fallback) {
        Object value = event == null || event.metadata() == null ? null : event.metadata().get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    public SignalDecision analyze(
            String eventType,
            TradePair tradePair,
             Object payload
    ) {
        String symbol = tradePairText(tradePair);
        double currentPrice = resolvePrice(payload);

        if (!Double.isFinite(currentPrice) || currentPrice <= 0.0) {
            return null;
        }

        long events = eventCountBySymbol.merge(symbol, 1L, Long::sum);
        Double previousPrice = lastPriceBySymbol.put(symbol, currentPrice);

        if (previousPrice == null || previousPrice <= 0.0) {
            return holdSignal(
                    tradePair,
                    currentPrice,
                    0.35,
                    "Waiting for baseline price.",
                    eventType,
                    events
            );
        }

        double changePercent = ((currentPrice - previousPrice) / previousPrice) * 100.0;
        double absChange = Math.abs(changePercent);

        Side side;
        double confidence;
        String reason;

        if (changePercent >= 0.08) {
            side = Side.BUY;
            confidence = clamp(0.52 + Math.min(0.35, absChange / 2.5));
            reason = "Short-term momentum turned bullish.";
        } else if (changePercent <= -0.08) {
            side = Side.SELL;
            confidence = clamp(0.52 + Math.min(0.35, absChange / 2.5));
            reason = "Short-term momentum turned bearish.";
        } else {
            return holdSignal(
                    tradePair,
                    currentPrice,
                    0.42,
                    "Price movement is not strong enough.",
                    eventType,
                    events
            );
        }

        if (payload instanceof CandleData) {
            confidence = clamp(confidence + 0.06);
            reason += " Candle confirmation detected.";
        }

        if (payload instanceof OrderBook) {
            confidence = clamp(confidence - 0.05);
            reason += " Order book signal kept conservative.";
        }

        return new SignalDecision(
                tradePair,
                symbol,
                side,
                confidence,
                currentPrice,
                "SignalAgent Momentum",
                reason,
                eventType,
                Instant.now(),
                Map.of(
                        "previousPrice", previousPrice,
                        "changePercent", changePercent,
                        "eventCount", events
                )
        );
    }

    private SignalDecision holdSignal(
            TradePair tradePair,
            double currentPrice,
            double confidence,
            String reason,
            String eventType,
            long events
    ) {
        return new SignalDecision(
                tradePair,
                tradePairText(tradePair),
                Side.HOLD,
                clamp(confidence),
                currentPrice,
                "SignalAgent Momentum",
                reason,
                eventType,
                Instant.now(),
                Map.of("eventCount", events)
        );
    }

    private boolean shouldPublish(SignalDecision decision) {
        String symbol = decision.symbol();

        if (decision.side() == Side.HOLD && decision.confidence() < 0.50) {
            return false;
        }

        if (decision.side() != Side.HOLD && decision.confidence() < MIN_CONFIDENCE_TO_EMIT_TRADE_SIGNAL) {
            return false;
        }

        Instant now = Instant.now();
        Instant lastPublished = lastSignalPublishedAt.get(symbol);

        if (lastPublished != null && Duration.between(lastPublished, now).compareTo(MIN_SIGNAL_INTERVAL) < 0) {
            return false;
        }

        lastSignalPublishedAt.put(symbol, now);
        return true;
    }

    private void publishSignal(SignalDecision decision) {
        AgentEventBus bus = eventBus;

        if (bus == null) {
            return;
        }

        String eventType = decision.side() == Side.HOLD
                ? EVENT_SIGNAL_HOLD
                : EVENT_SIGNAL_GENERATED;

        bus.publish(new AgentEvent(
                eventType,
                AGENT_NAME,
                decision,
                Instant.now(),
                Map.of(
                        "symbol", decision.symbol(),
                        "side", decision.side().name(),
                        "confidence", decision.confidence(),
                        "strategy", decision.strategyName(),
                        "sourceEventType", decision.sourceEventType()
                )
        ));

        log.info(
                "{} published signal. symbol={} side={} confidence={} reason={}",
                AGENT_NAME,
                decision.symbol(),
                decision.side(),
                "%.2f".formatted(decision.confidence()),
                decision.reason()
        );
    }

    private void publishSystem(String message) {
        AgentEventBus bus = eventBus;

        if (bus == null) {
            return;
        }

        bus.publish(new AgentEvent(
                EVENT_SIGNAL_AGENT_STATUS,
                AGENT_NAME,
                safe(message),
                Instant.now(),
                Map.of()
        ));
    }
    AgentEventBus bus;
    private void publishError(Throwable throwable) {
        bus = eventBus;

        if (bus == null) {
            return;
        }

        bus.publish(new AgentEvent(
                EVENT_SIGNAL_ERROR,
                AGENT_NAME,
                throwable,
                Instant.now(),
                Map.of(
                        "message", safe("SignalAgent failed to process market event."),
                        "error", rootMessage(throwable)
                )
        ));
    }

    private boolean isMarketDataEvent(String eventType) {
        return eventType.contains("TICK")
                || eventType.contains("TICKER")
                || eventType.contains("TRADE")
                || eventType.contains("CANDLE")
                || eventType.contains("ORDER_BOOK")
                || eventType.contains("BOOK")
                || eventType.contains("MARKET");
    }

    private @Nullable TradePair activeTradePair() {
        AgentContext activeContext = context;

        if (activeContext == null) {
            return null;
        }

        try {
            return activeContext.getSelectedTradePair();
        } catch (Exception ignored) {
            return null;
        }
    }

    private @Nullable TradePair resolveTradePair(AgentEvent event, Object payload) {
        if (event.metadata() != null) {
            Object metadataValue = event.metadata().get("tradePair");

            if (metadataValue instanceof TradePair pair) {
                return pair;
            }
        }

        if (payload instanceof Trade trade) {
            return invokeTradePairGetter(trade);
        }

        if (payload instanceof Ticker ticker) {
            return invokeTradePairGetter(ticker);
        }

        return null;
    }

    private @Nullable TradePair invokeTradePairGetter(Object target) {
        if (target == null) {
            return null;
        }

        for (String methodName : java.util.List.of("getTradePair", "tradePair", "getPair", "pair")) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);

                if (value instanceof TradePair pair) {
                    return pair;
                }

            } catch (ReflectiveOperationException ignored) {
                // Try next method.
            }
        }

        return null;
    }

    private double resolvePrice(Object payload) {
        return switch (payload) {
            case null -> Double.NaN;
            case Number number -> number.doubleValue();
            case CandleData candle -> safeDouble(candle.closePrice());
            default -> firstFiniteNumberFromMethods(
                    payload
            );
        };

    }

    private double firstFiniteNumberFromMethods(Object target) {
        for (String methodName : new String[]{"getLast", "last", "getLastPrice", "lastPrice", "getPrice", "price", "getClose", "close", "closePrice", "getBid", "bid", "getAsk", "ask"}) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);

                double number = toDouble(value);

                if (Double.isFinite(number) && number > 0.0) {
                    return number;
                }

            } catch (ReflectiveOperationException ignored) {
                // Try next method.
            }
        }

        return Double.NaN;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return Double.NaN;
            }
        }

        return Double.NaN;
    }

    private double safeDouble(double value) {
        return Double.isFinite(value) ? value : Double.NaN;
    }

    private String tradePairText(@Nullable TradePair tradePair) {
        return tradePair == null ? "" : tradePair.toString('/');
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, value));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }

        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    public record SignalDecision(
            TradePair tradePair,
            String symbol,
            Side side,
            double confidence,
            double price,
            String strategyName,
            String reason,
            String sourceEventType,
            Instant timestamp,
            Map<String, Object> metadata
    ) {
        @Override
        public @NonNull String toString() {
            return "%s %s confidence %.0f%% via %s | %s".formatted(
                    symbol == null || symbol.isBlank() ? "-" : symbol,
                    side == null ? Side.HOLD : side,
                    confidence * 100.0,
                    strategyName == null || strategyName.isBlank() ? "SignalAgent" : strategyName,
                    reason == null ? "N/A" : reason
            );
        }
    }
}
