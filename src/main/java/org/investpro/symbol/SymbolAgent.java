package org.investpro.symbol;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.data.CandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.investpro.persistence.repository.StrategyAssignmentRepository;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.lab.StrategyLabService;
import org.investpro.utils.CandleDataSupplier;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-symbol agent that drives a single TradePair through strategy evaluation.
 *
 * <p>Lifecycle progression:
 * NOT_STARTED -> COLLECTING_DATA -> BACKTESTING -> ASSIGNED/LIVE_TRADING/FAILED
 *
 * <p>This agent listens for market ticks, updates the symbol panel state, and
 * starts the real-candle strategy lab once enough market activity has arrived.
 */
@Slf4j
@Getter
public class SymbolAgent implements Agent {

    private static final long TICKS_FOR_DATA_COLLECTION = 50;

    private final TradePair symbol;
    private final SymbolAgentManager manager;

    private AgentContext context;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong tickCount = new AtomicLong(0);
    private final AtomicBoolean evaluationStarted = new AtomicBoolean(false);

    public SymbolAgent(TradePair symbol, SymbolAgentManager manager) {
        this.symbol = symbol;
        this.manager = manager;
    }

    @Override
    public String name() {
        return "SymbolAgent[" + symbol.toString('/') + "]";
    }

    @Override
    public void start(AgentContext context) {
        this.context = context;
        running.set(true);
        SymbolAgentState state = manager.ensureSymbol(symbol);
        StrategyAssignment existingAssignment = reusableExistingAssignment();
        if (existingAssignment != null) {
            markAssigned(existingAssignment);
        } else if (state.getState() == null || state.getState() == SymbolEvaluationState.NOT_STARTED) {
            state.setState(SymbolEvaluationState.COLLECTING_DATA);
            state.setLastIssue("Collecting market data...");
            manager.updateState(symbol, state);
        }
        log.info("SymbolAgent started for {}", symbol.toString('/'));
    }

    @Override
    public void stop() {
        running.set(false);
        log.info("SymbolAgent stopped for {}", symbol.toString('/'));
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running.get() || event == null) return;

        if (!AgentEvent.MARKET_TICK.equals(event.type())) return;

        Object pairObj = event.metadata().get("tradePairObject");
        if (!(pairObj instanceof TradePair pair) || !pair.equals(symbol)) return;

        Object payload = event.payload();
        if (!(payload instanceof Ticker ticker)) return;

        long ticks = tickCount.incrementAndGet();
        advanceLifecycle(ticks, ticker);
    }

    private void advanceLifecycle(long ticks, Ticker ticker) {
        SymbolAgentState state = manager.ensureSymbol(symbol);

        if (ticker.getBidPrice() > 0) state.setBidPrice(ticker.getBidPrice());
        if (ticker.getAskPrice() > 0) state.setAskPrice(ticker.getAskPrice());
        if (ticker.getAskPrice() > 0) {
            state.setSpreadPercent(
                    (ticker.getAskPrice() - ticker.getBidPrice()) / ticker.getAskPrice() * 100.0);
        }

        SymbolEvaluationState previous = state.getState();
        switch (previous) {
            case COLLECTING_DATA -> {
                if (ticks >= TICKS_FOR_DATA_COLLECTION) {
                    state.setState(SymbolEvaluationState.BACKTESTING);
                    state.setLastIssue("Running real-candle strategy backtests...");
                    manager.updateState(symbol, state);
                    publishStateChange(previous, state);
                    log.debug("{}: data collected, advancing to BACKTESTING", symbol.toString('/'));
                    startRealStrategyEvaluation();
                    return;
                }
            }
            case BACKTESTING -> state.setLastIssue("Backtest and consensus assignment in progress...");
            case RANKING -> state.setLastIssue("Ranking strategies...");
            case PAPER_TRADING -> state.setLastIssue("Paper validation is waiting for future live paper-trade results.");
            default -> {
                // ASSIGNED, LIVE_READY, LIVE_TRADING, FAILED, and PAUSED are managed externally.
                // Market ticks must update quote fields without resetting strategy assignment state.
            }
        }

        manager.updateState(symbol, state);
        publishStateChange(previous, state);
    }

    private void startRealStrategyEvaluation() {
        if (!evaluationStarted.compareAndSet(false, true)) {
            return;
        }

        StrategyAssignment existingAssignment = reusableExistingAssignment();
        if (existingAssignment != null) {
            markAssigned(existingAssignment);
            return;
        }

        Timeframe timeframe = Timeframe.H1;
        fetchHistoricalCandles(timeframe)
                .thenCompose(candles -> StrategyLabService.getInstance()
                        .evaluateAndAssignBest(symbol.toString('/'), timeframe, candles))
                .thenAccept(assignment -> {
                    if (assignment == null) {
                        markFailed("No strategy passed real-candle evaluation");
                    } else {
                        markAssigned(assignment);
                    }
                })
                .exceptionally(exception -> {
                    markFailed(rootMessage(exception));
                    return null;
                });
    }

    private StrategyAssignment reusableExistingAssignment() {
        double reassignBelowScore = AppConfig.getDouble(
                "investpro.strategy.reassignBelowScore",
                AppConfig.getDouble("investpro.strategy.hardMinStrategyScore", 40.0));

        try {
            return StrategyAssignmentRepository.getInstance()
                    .getForSymbolAllTimeframes(symbol.toString('/'))
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(StrategyAssignment::isValid)
                    .filter(assignment -> !assignment.isExpired())
                    .filter(assignment -> assignment.getTimeframe() != null)
                    .filter(assignment -> assignment.getStrategyId() != null && !assignment.getStrategyId().isBlank())
                    .filter(assignment -> assignment.getScoreAtAssignment() >= reassignBelowScore)
                    .max(Comparator.comparingDouble(StrategyAssignment::getScoreAtAssignment))
                    .orElse(null);
        } catch (Exception exception) {
            log.debug("{}: unable to load existing strategy assignment: {}",
                    symbol.toString('/'), exception.getMessage());
            return null;
        }
    }

    private CompletableFuture<List<CandleData>> fetchHistoricalCandles(Timeframe timeframe) {
        if (context == null || context.getExchange() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Exchange context is unavailable"));
        }

        try {
            CandleDataSupplier supplier = context.getExchange().getCandleDataSupplier(timeframe.getSeconds(), symbol);
            if (supplier == null) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "No candle data supplier for " + symbol.toString('/') + " " + timeframe.getCode()));
            }

            Future<List<CandleData>> candlesFuture = supplier.get();
            return CompletableFuture.supplyAsync(() -> {
                try {
                    List<CandleData> candles = candlesFuture.get(20, TimeUnit.SECONDS);
                    return candles == null ? List.of() : candles;
                } catch (Exception exception) {
                    throw new IllegalStateException("Historical candle fetch failed", exception);
                }
            });
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private void markAssigned(StrategyAssignment assignment) {
        SymbolAgentState state = manager.ensureSymbol(symbol);
        SymbolEvaluationState previous = state.getState();
        boolean requirePaperTrading = AppConfig.getBoolean("investpro.strategy.requirePaperTradingBeforeLive", true);
        boolean startLiveAfterTraining = AppConfig.getBoolean("investpro.strategy.startLiveAfterTraining", true);
        boolean liveAfterTraining = !requirePaperTrading && startLiveAfterTraining;

        if (liveAfterTraining && context != null) {
            context.setAutoTradingEnabled(true);
            publishAutoTradingEnabled(assignment);
        }

        state.setState(requirePaperTrading
                ? SymbolEvaluationState.PAPER_TRADING
                : (liveAfterTraining ? SymbolEvaluationState.LIVE_TRADING : SymbolEvaluationState.LIVE_READY));
        state.setActiveStrategyName(assignment.getStrategyId());
        state.setAssignedStrategyName(assignment.getStrategyId());
        state.setActiveTimeframe(assignment.getTimeframe());
        state.setStrategyScore(assignment.getScoreAtAssignment());
        state.setCanTradeLive(!requirePaperTrading);
        state.setBlockReason(requirePaperTrading ? "Paper trading validation required before live trading" : null);
        state.setLastIssue(assignmentStatusMessage(requirePaperTrading, liveAfterTraining));
        manager.updateState(symbol, state);
        publishStateChange(previous, state);
        publishTrainingCompleted(assignment, liveAfterTraining);

        log.info("{}: real strategy evaluation assigned {} score={} requirePaper={} liveAfterTraining={}",
                symbol.toString('/'),
                assignment.getStrategyId(),
                assignment.getScoreAtAssignment(),
                requirePaperTrading,
                liveAfterTraining);
    }

    private String assignmentStatusMessage(boolean requirePaperTrading, boolean liveAfterTraining) {
        if (requirePaperTrading) {
            return "Assigned by backtest consensus; waiting for paper validation";
        }
        if (liveAfterTraining) {
            return "Training complete; live trading enabled through the guarded execution pipeline";
        }
        return null;
    }

    private void publishAutoTradingEnabled(StrategyAssignment assignment) {
        if (context == null || context.getEventBus() == null) {
            return;
        }

        context.getEventBus().publishAsync(new AgentEvent(
                "AUTO_TRADING_ENABLED",
                name(),
                "Auto trading enabled after strategy training.",
                java.time.Instant.now(),
                Map.of(
                        "tradePairObject", symbol,
                        "strategyId", safeStrategyId(assignment),
                        "timeframe", safeTimeframeCode(assignment),
                        "trigger", "TRAINING_COMPLETED"
                )));
    }

    private void publishTrainingCompleted(StrategyAssignment assignment, boolean liveAfterTraining) {
        if (context == null || context.getEventBus() == null) {
            return;
        }

        context.getEventBus().publishAsync(new AgentEvent(
                "STRATEGY_TRAINING_COMPLETED",
                name(),
                assignment,
                java.time.Instant.now(),
                Map.of(
                        "tradePairObject", symbol,
                        "strategyId", safeStrategyId(assignment),
                        "timeframe", safeTimeframeCode(assignment),
                        "score", assignment.getScoreAtAssignment(),
                        "liveAfterTraining", liveAfterTraining
                )));
    }

    private String safeStrategyId(StrategyAssignment assignment) {
        String strategyId = assignment == null ? null : assignment.getStrategyId();
        return strategyId == null ? "" : strategyId;
    }

    private String safeTimeframeCode(StrategyAssignment assignment) {
        return assignment == null || assignment.getTimeframe() == null ? "" : assignment.getTimeframe().getCode();
    }

    private void markFailed(String reason) {
        SymbolAgentState state = manager.ensureSymbol(symbol);
        SymbolEvaluationState previous = state.getState();

        state.setState(SymbolEvaluationState.FAILED);
        state.setCanTradeLive(false);
        state.setLastIssue(reason == null || reason.isBlank()
                ? "Strategy evaluation failed"
                : reason);
        manager.updateState(symbol, state);
        publishStateChange(previous, state);

        log.warn("{}: strategy evaluation failed: {}", symbol.toString('/'), state.getLastIssue());
    }

    private void publishStateChange(SymbolEvaluationState previous, SymbolAgentState state) {
        if (state.getState() == previous || context == null || context.getEventBus() == null) {
            return;
        }

        context.getEventBus().publish(new AgentEvent(
                "SYMBOL_STATE_CHANGED",
                name(),
                state,
                java.time.Instant.now(),
                Map.of(
                        "tradePairObject", symbol,
                        "previousState", previous.name(),
                        "newState", state.getState().name()
                )));
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null && cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor == null || cursor.getMessage() == null || cursor.getMessage().isBlank()
                ? "Strategy evaluation failed"
                : cursor.getMessage();
    }
}
