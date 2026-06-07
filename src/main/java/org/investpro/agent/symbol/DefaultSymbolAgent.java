package org.investpro.agent.symbol;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityType;
import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;
import org.investpro.news.NewsContext;
import org.investpro.news.NewsContextService;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.auto.AutoStrategyLab;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
public class DefaultSymbolAgent implements SymbolAgent {

    private final String exchangeId;
    private final TradePair pair;
    private final SymbolAgentConfig config;
    private final ExecutorService executorService;
    private final StrategyEvaluator strategyEvaluator;
    private final SymbolRiskEvaluator riskEvaluator;
    private final SymbolExecutionEngine executionEngine;
    private final TradabilityChecker tradabilityChecker;
    private final NewsContextService newsContextService;
    private final AutoStrategyLab autoStrategyLab;
    private final ArrayDeque<CandleData> candles = new ArrayDeque<>();
    private volatile SymbolAgentState state;
    private volatile Object latestTicker;
    private volatile Object latestOrderBook;
    private volatile LocalDateTime lastEvaluationStartedAt;

    public DefaultSymbolAgent(
            String exchangeId,
            TradePair pair,
            SymbolAgentMode mode,
            SymbolAgentConfig config,
            ExecutorService executorService,
            StrategyEvaluator strategyEvaluator,
            SymbolRiskEvaluator riskEvaluator,
            SymbolExecutionEngine executionEngine,
            TradabilityChecker tradabilityChecker,
            NewsContextService newsContextService,
            AutoStrategyLab autoStrategyLab) {
        this.exchangeId = exchangeId == null || exchangeId.isBlank() ? "UNKNOWN" : exchangeId;
        this.pair = Objects.requireNonNull(pair, "pair must not be null");
        this.config = config == null ? SymbolAgentConfig.defaults() : config;
        this.executorService = Objects.requireNonNull(executorService, "executorService must not be null");
        this.strategyEvaluator = Objects.requireNonNull(strategyEvaluator, "strategyEvaluator must not be null");
        this.riskEvaluator = Objects.requireNonNull(riskEvaluator, "riskEvaluator must not be null");
        this.executionEngine = Objects.requireNonNull(executionEngine, "executionEngine must not be null");
        this.tradabilityChecker = tradabilityChecker == null ? (ex, p, m) -> TradabilityDecision.allowed() : tradabilityChecker;
        this.newsContextService = newsContextService;
        this.autoStrategyLab = autoStrategyLab;
        this.state = new SymbolAgentState(
                pair,
                this.exchangeId,
                SymbolAgentStatus.CREATED,
                mode == null ? SymbolAgentMode.PAPER : mode,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                false,
                false,
                false,
                false,
                false,
                "",
                "",
                Map.of());
        log.info("SymbolAgent created. exchange={} symbol={}", this.exchangeId, symbol());
    }

    @Override
    public TradePair pair() {
        return pair;
    }

    @Override
    public SymbolAgentState state() {
        return state;
    }

    @Override
    public void start() {
        updateState(SymbolAgentStatus.STARTING, null);
        state = copyState(SymbolAgentStatus.WARMING_UP, LocalDateTime.now(), state.assignedStrategy(), state.hasOpenPosition(),
                state.hasPendingOrder(), state.lastSignal(), null);
        log.info("SymbolAgent started. exchange={} symbol={} mode={}", exchangeId, symbol(), state.mode());
        refreshTradability();
        refreshReadiness();
    }

    @Override
    public void pause() {
        updateState(SymbolAgentStatus.PAUSED, null);
        log.info("SymbolAgent paused. exchange={} symbol={}", exchangeId, symbol());
    }

    @Override
    public void resume() {
        if (state.status() == SymbolAgentStatus.PAUSED) {
            refreshTradability();
            refreshReadiness();
            log.info("SymbolAgent resumed. exchange={} symbol={}", exchangeId, symbol());
        }
    }

    @Override
    public void stop() {
        updateState(SymbolAgentStatus.STOPPING, null);
        updateState(SymbolAgentStatus.STOPPED, null);
        log.info("SymbolAgent stopped. exchange={} symbol={}", exchangeId, symbol());
    }

    @Override
    public void assignStrategy(StrategyDefinition strategy) {
        state = state.withStrategy(strategy);
        log.info("SymbolAgent strategy assigned. exchange={} symbol={} strategy={}",
                exchangeId, symbol(), strategy == null ? "none" : strategy.getName());
        refreshReadiness();
    }

    @Override
    public void unassignStrategy() {
        state = state.withStrategy(null);
        updateState(SymbolAgentStatus.STRATEGY_UNASSIGNED, null);
    }

    @Override
    public synchronized void onCandle(CandleData candle) {
        if (candle == null || state.status() == SymbolAgentStatus.STOPPED) {
            return;
        }
        candles.addLast(candle);
        while (candles.size() > config.maxCandlesInMemory()) {
            candles.removeFirst();
        }
        LocalDateTime candleTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(candle.openTime()),
                ZoneId.systemDefault());
        state = new SymbolAgentState(pair, exchangeId, state.status(), state.mode(), state.assignedStrategy(),
                state.startedAt(), candleTime, state.lastSignalAt(), state.lastEvaluationAt(),
                state.lastBrokerActivityAt(), candles.size(), candles.size() >= config.warmupCandles(),
                state.tradable(), state.orderSubmissionAllowed(), state.hasOpenPosition(), state.hasPendingOrder(),
                state.lastSignal(), state.lastError(), state.metadata());
        refreshReadiness();
        if (config.autoEvaluateOnCandleClose() && state.marketDataReady() && state.status() == SymbolAgentStatus.ACTIVE) {
            evaluateNow("candle-close");
        }
    }

    @Override
    public void onTicker(Object ticker) {
        latestTicker = ticker;
    }

    @Override
    public void onOrderBook(Object orderBook) {
        latestOrderBook = orderBook;
    }

    @Override
    public void onBrokerActivity(BrokerActivityEvent event) {
        if (event == null || event.getTradePair() == null || !samePair(event.getTradePair())) {
            return;
        }
        boolean pending = switch (event.getActivityType() == null ? BrokerActivityType.UNKNOWN : event.getActivityType()) {
            case ORDER_CREATED, ORDER_SUBMITTED, ORDER_UPDATED, ORDER_PENDING_CONFIRMATION, ORDER_PARTIALLY_FILLED -> true;
            case ORDER_FILLED, ORDER_CANCELLED, ORDER_REJECTED, ORDER_EXPIRED -> false;
            default -> state.hasPendingOrder();
        };
        boolean openPosition = switch (event.getActivityType() == null ? BrokerActivityType.UNKNOWN : event.getActivityType()) {
            case POSITION_OPENED, ORDER_FILLED -> true;
            case POSITION_CLOSED -> false;
            default -> state.hasOpenPosition();
        };
        state = new SymbolAgentState(pair, exchangeId, state.status(), state.mode(), state.assignedStrategy(),
                state.startedAt(), state.lastCandleAt(), state.lastSignalAt(), state.lastEvaluationAt(),
                LocalDateTime.now(), state.candlesLoaded(), state.marketDataReady(), state.tradable(),
                state.orderSubmissionAllowed(), openPosition, pending, state.lastSignal(), state.lastError(), state.metadata());
        log.info("SymbolAgent broker activity received. exchange={} symbol={} type={}", exchangeId, symbol(), event.getActivityType());
    }

    @Override
    public CompletableFuture<Void> evaluateNow(String reason) {
        return CompletableFuture.runAsync(() -> evaluate(reason), executorService);
    }

    @Override
    public CompletableFuture<Void> reviewStrategyNow(String reason) {
        return CompletableFuture.runAsync(() -> {
            if (!config.autoImproveEnabled() || autoStrategyLab == null) {
                log.debug("SymbolAgent strategy review skipped. exchange={} symbol={} reason={}", exchangeId, symbol(), reason);
                return;
            }
            log.info("SymbolAgent strategy review requested. exchange={} symbol={} reason={}", exchangeId, symbol(), reason);
        }, executorService);
    }

    private void evaluate(String reason) {
        if (!canEvaluate(reason)) {
            return;
        }
        lastEvaluationStartedAt = LocalDateTime.now();
        state = copyState(state.status(), state.startedAt(), state.assignedStrategy(), state.hasOpenPosition(),
                state.hasPendingOrder(), state.lastSignal(), null);
        MarketContext marketContext = marketContext();
        if (marketContext.newsContext() != null
                && marketContext.newsContext().criticalNegativeNews()
                && config.blockIfNewsCritical()) {
            updateState(SymbolAgentStatus.RISK_BLOCKED, "Critical negative news blocks new entries");
            log.warn("SymbolAgent blocked by news context. exchange={} symbol={}", exchangeId, symbol());
            return;
        }
        AgentStrategySignal signal;
        try {
            signal = strategyEvaluator.evaluate(state.assignedStrategy(), pair, List.copyOf(candles), marketContext);
        } catch (Exception exception) {
            updateState(SymbolAgentStatus.ERROR, exception.getMessage());
            log.error("SymbolAgent strategy evaluation failed. exchange={} symbol={}", exchangeId, symbol(), exception);
            return;
        }
        if (signal == null || signal.signalType() == SignalType.HOLD || signal.signalType() == SignalType.NEUTRAL) {
            setSignal(signal == null ? "NEUTRAL" : signal.signalType().name());
            return;
        }
        if (signal.signalType() == SignalType.EXIT) {
            log.info("SymbolAgent exit signal produced. exchange={} symbol={} reason={}", exchangeId, symbol(), signal.reason());
            setSignal("EXIT");
            return;
        }
        TradeIntent intent = createTradeIntent(signal);
        log.info("SymbolAgent trade intent created. exchange={} symbol={} side={} confidence={}",
                exchangeId, symbol(), intent.side(), intent.confidence());
        RiskDecision decision = riskEvaluator.evaluateTradeIntent(intent, state, marketContext);
        if (decision == null || !decision.approved()) {
            updateState(SymbolAgentStatus.RISK_BLOCKED, decision == null ? "Risk rejected" : decision.reason());
            log.warn("SymbolAgent risk rejected. exchange={} symbol={} reason={}",
                    exchangeId, symbol(), decision == null ? "Risk rejected" : decision.reason());
            return;
        }
        executionEngine.submitTradeIntent(intent, decision);
        state = new SymbolAgentState(pair, exchangeId, SymbolAgentStatus.ACTIVE, state.mode(), state.assignedStrategy(),
                state.startedAt(), state.lastCandleAt(), LocalDateTime.now(), LocalDateTime.now(),
                state.lastBrokerActivityAt(), candles.size(), state.marketDataReady(), state.tradable(),
                state.orderSubmissionAllowed(), state.hasOpenPosition(), true, signal.signalType().name(),
                null, state.metadata());
        log.info("SymbolAgent execution submitted. exchange={} symbol={} strategy={}", exchangeId, symbol(), intent.strategyName());
    }

    private boolean canEvaluate(String reason) {
        if (state.status() == SymbolAgentStatus.PAUSED || state.status() == SymbolAgentStatus.STOPPED) return false;
        if (state.assignedStrategy() == null) {
            updateState(SymbolAgentStatus.STRATEGY_UNASSIGNED, "No assigned strategy");
            return false;
        }
        if (!state.marketDataReady()) {
            updateState(SymbolAgentStatus.WAITING_FOR_DATA, "Waiting for warmup candles");
            return false;
        }
        refreshTradability();
        if (config.requireTradabilityCheck() && (!state.tradable() || !state.orderSubmissionAllowed())) {
            updateState(SymbolAgentStatus.WAITING_FOR_TRADABILITY, "Symbol is not tradable");
            return false;
        }
        if (config.duplicateOrderProtection() && state.hasPendingOrder()) {
            log.info("SymbolAgent duplicate order protection skipped evaluation. exchange={} symbol={} reason={}", exchangeId, symbol(), reason);
            return false;
        }
        if (lastEvaluationStartedAt != null
                && Duration.between(lastEvaluationStartedAt, LocalDateTime.now()).compareTo(config.evaluationCooldown()) < 0) {
            return false;
        }
        return true;
    }

    private void refreshReadiness() {
        if (state.status() == SymbolAgentStatus.PAUSED || state.status() == SymbolAgentStatus.STOPPED) return;
        if (state.assignedStrategy() == null) {
            updateState(SymbolAgentStatus.STRATEGY_UNASSIGNED, null);
        } else if (!state.marketDataReady()) {
            updateState(SymbolAgentStatus.WARMING_UP, null);
        } else if (!state.tradable() && config.requireTradabilityCheck()) {
            updateState(SymbolAgentStatus.WAITING_FOR_TRADABILITY, null);
        } else {
            updateState(SymbolAgentStatus.ACTIVE, null);
        }
    }

    private void refreshTradability() {
        TradabilityDecision decision = tradabilityChecker.check(exchangeId, pair, state.mode());
        state = new SymbolAgentState(pair, exchangeId, state.status(), state.mode(), state.assignedStrategy(),
                state.startedAt(), state.lastCandleAt(), state.lastSignalAt(), state.lastEvaluationAt(),
                state.lastBrokerActivityAt(), state.candlesLoaded(), state.marketDataReady(),
                decision.tradable(), decision.orderSubmissionAllowed(), state.hasOpenPosition(),
                state.hasPendingOrder(), state.lastSignal(), decision.tradable() ? state.lastError() : decision.reason(),
                state.metadata());
    }

    private MarketContext marketContext() {
        NewsContext newsContext = newsContextService == null
                ? NewsContext.empty(baseSymbol())
                : newsContextService.getContextForSymbol(baseSymbol(), Duration.ofHours(24));
        return new MarketContext(latestTicker, latestOrderBook, newsContext, LocalDateTime.now(), Map.of());
    }

    private TradeIntent createTradeIntent(AgentStrategySignal signal) {
        OrderSide side = signal.signalType() == SignalType.SELL ? OrderSide.SELL : OrderSide.BUY;
        BigDecimal quantity = new BigDecimal(signal.metadata().getOrDefault("quantity", "0"));
        return new TradeIntent(null, pair, exchangeId, state.assignedStrategy().getName(), signal.signalType(), side,
                OrderType.MARKET, quantity, null, null, signal.reason(), signal.confidence(), LocalDateTime.now(), signal.metadata());
    }

    private void setSignal(String signal) {
        state = new SymbolAgentState(pair, exchangeId, state.status(), state.mode(), state.assignedStrategy(),
                state.startedAt(), state.lastCandleAt(), LocalDateTime.now(), LocalDateTime.now(),
                state.lastBrokerActivityAt(), candles.size(), state.marketDataReady(), state.tradable(),
                state.orderSubmissionAllowed(), state.hasOpenPosition(), state.hasPendingOrder(), signal, null, state.metadata());
    }

    private void updateState(SymbolAgentStatus status, String error) {
        state = state.withStatus(status, error);
    }

    private SymbolAgentState copyState(
            SymbolAgentStatus status,
            LocalDateTime startedAt,
            StrategyDefinition strategy,
            boolean hasOpenPosition,
            boolean hasPendingOrder,
            String lastSignal,
            String lastError) {
        return new SymbolAgentState(pair, exchangeId, status, state.mode(), strategy, startedAt,
                state.lastCandleAt(), state.lastSignalAt(), LocalDateTime.now(), state.lastBrokerActivityAt(),
                candles.size(), candles.size() >= config.warmupCandles(), state.tradable(), state.orderSubmissionAllowed(),
                hasOpenPosition, hasPendingOrder, lastSignal, lastError, state.metadata());
    }

    private boolean samePair(TradePair eventPair) {
        return eventPair.toString('/').equalsIgnoreCase(pair.toString('/'));
    }

    private String symbol() {
        return pair.toString('/');
    }

    private String baseSymbol() {
        String symbol = symbol();
        int slash = symbol.indexOf('/');
        return slash > 0 ? symbol.substring(0, slash) : symbol;
    }
}
