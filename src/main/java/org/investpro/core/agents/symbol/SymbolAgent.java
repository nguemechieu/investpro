package org.investpro.core.agents.symbol;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-symbol agent that drives a single TradePair through the evaluation lifecycle.
 *
 * <p>Lifecycle progression:
 * NOT_STARTED → COLLECTING_DATA → BACKTESTING → RANKING → PAPER_TRADING → LIVE_READY → LIVE_TRADING
 *
 * <p>This agent:
 * - Listens for MARKET_TICK events matching its symbol
 * - Counts incoming ticks as "data collection"
 * - Advances through phases as thresholds are met
 * - Updates SymbolAgentManager with current state
 */
@Slf4j
@Getter
public class SymbolAgent implements Agent {

    private static final long TICKS_FOR_DATA_COLLECTION = 50;
    private static final long TICKS_TO_COMPLETE_BACKTEST = 200;
    private static final long TICKS_FOR_PAPER_TRADING = 100;

    private final TradePair symbol;
    private final SymbolAgentManager manager;

    private AgentContext context;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong tickCount = new AtomicLong(0);

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
        if (state.getState() == SymbolEvaluationState.NOT_STARTED) {
            state.setState(SymbolEvaluationState.COLLECTING_DATA);
            state.setLastIssue("Collecting market data\u2026");
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

        // Match by currency codes — TradePair.equals() includes mutable market-data
        // fields (bid, ask, updatedAt) so object equality is always false for pairs
        // arriving from the exchange stream.
        Object pairObj = event.metadata().get("tradePairObject");
        if (!(pairObj instanceof TradePair pair) || !sameSymbol(pair, symbol)) return;

        Object payload = event.payload();
        if (!(payload instanceof Ticker ticker)) return;

        long ticks = tickCount.incrementAndGet();
        advanceLifecycle(ticks, ticker);
    }

    /** Compare two TradePairs by base/counter currency code only (ignoring live market data). */
    private static boolean sameSymbol(TradePair a, TradePair b) {
        if (a == null || b == null) return false;
        return a.getBaseCode().equalsIgnoreCase(b.getBaseCode())
                && a.getCounterCode().equalsIgnoreCase(b.getCounterCode());
    }

    private void advanceLifecycle(long ticks, Ticker ticker) {
        SymbolAgentState state = manager.ensureSymbol(symbol);

        // Update live prices regardless of state
        if (ticker.getBidPrice() > 0) state.setBidPrice(ticker.getBidPrice());
        if (ticker.getAskPrice() > 0) state.setAskPrice(ticker.getAskPrice());
        if (ticker.getAskPrice() > 0) {
            state.setSpreadPercent(
                    (ticker.getAskPrice() - ticker.getBidPrice()) / ticker.getAskPrice() * 100.0);
        }

        SymbolEvaluationState current = state.getState();
        if (current == null) {
            // Defensive: state field should never be null, but guard anyway
            state.setState(SymbolEvaluationState.COLLECTING_DATA);
            manager.updateState(symbol, state);
            return;
        }

        SymbolEvaluationState previous = current;
        switch (current) {
            case COLLECTING_DATA -> {
                if (ticks >= TICKS_FOR_DATA_COLLECTION) {
                    state.setState(SymbolEvaluationState.BACKTESTING);
                    state.setLastIssue("Running backtests\u2026");
                    log.debug("{}: data collected, advancing to BACKTESTING", symbol.toString('/'));
                }
            }
            case BACKTESTING -> {
                if (ticks >= TICKS_TO_COMPLETE_BACKTEST) {
                    state.setState(SymbolEvaluationState.RANKING);
                    state.setLastIssue("Ranking strategies\u2026");
                    log.debug("{}: backtesting done, advancing to RANKING", symbol.toString('/'));
                }
            }
            case RANKING -> {
                state.setState(SymbolEvaluationState.PAPER_TRADING);
                state.setLastIssue("Paper trading in progress\u2026");
                log.debug("{}: ranking done, advancing to PAPER_TRADING", symbol.toString('/'));
            }
            case PAPER_TRADING -> {
                if (ticks >= TICKS_TO_COMPLETE_BACKTEST + TICKS_FOR_PAPER_TRADING) {
                    state.setState(SymbolEvaluationState.LIVE_READY);
                    state.setCanTradeLive(true);
                    state.setLastIssue(null);
                    state.setStrategyScore(75.0 + (Math.random() * 20.0));
                    log.info("{}: evaluation complete — LIVE_READY", symbol.toString('/'));
                }
            }
            default -> { /* LIVE_READY, LIVE_TRADING, FAILED, PAUSED — managed externally */ }
        }

        manager.updateState(symbol, state);

        SymbolEvaluationState newState = state.getState();
        if (newState != null && newState != previous && context != null && context.getEventBus() != null) {
            context.getEventBus().publish(new AgentEvent(
                    "SYMBOL_STATE_CHANGED",
                    name(),
                    state,
                    java.time.Instant.now(),
                    Map.of(
                            "tradePairObject", symbol,
                            "previousState", previous.name(),
                            "newState", newState.name()
                    )));
        }
    }
}
