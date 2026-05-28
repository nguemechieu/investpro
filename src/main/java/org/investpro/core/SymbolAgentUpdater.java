package org.investpro.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.symbol.SymbolAgentManager;
import org.investpro.symbol.SymbolAgentState;
import org.investpro.symbol.SymbolEvaluationState;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Symbol Agent Updater - Bridges trading events to SymbolAgentManager UI state.
 * <p>
 * Responsibilities:
 * - Subscribe to AgentEventBus for strategy and trading events
 * - Listen for strategy signals (approved, rejected)
 * - Listen for portfolio updates
 * - Listen for trading events (orders, positions)
 * - Update SymbolAgentManager with current state
 * - Enable real-time UI updates in MarketWatch panel
 */
@Slf4j
public class SymbolAgentUpdater implements Consumer<AgentEvent> {
    private final SymbolAgentManager symbolAgentManager;
    private final AgentEventBus eventBus;

    @Getter
    private volatile boolean listening = false;

    public SymbolAgentUpdater(
            @NotNull AgentEventBus eventBus,
            @NotNull SymbolAgentManager symbolAgentManager) {
        this.eventBus = eventBus;
        this.symbolAgentManager = symbolAgentManager;
    }

    /**
     * Start listening to all agent events
     */
    public void start() {
        if (listening) {
            log.warn("SymbolAgentUpdater is already listening");
            return;
        }

        try {
            eventBus.subscribeAll(this);
            listening = true;
            log.info("\u2705 SymbolAgentUpdater started - Real-time symbol state updates enabled");
        } catch (Exception exception) {
            log.error("Failed to start SymbolAgentUpdater", exception);
        }
    }

    /**
     * Stop listening to agent events
     */
    public void stop() {
        if (!listening) {
            return;
        }

        eventBus.unsubscribeAll(this);
        listening = false;
        log.info("SymbolAgentUpdater stopped");
    }

    /**
     * Main event consumer - routes events to appropriate handlers
     */
    @Override
    public void accept(AgentEvent event) {
        if (event == null || !listening) {
            return;
        }

        try {
            String eventType = event.type();

            switch (eventType) {
                case "MARKET_TICK" -> handleMarketTick(event);
                case "SIGNAL_CREATED" -> handleSignalCreated(event);
                case "STRATEGY_SIGNAL_APPROVED" -> handleStrategySignalApproved(event);
                case "STRATEGY_SIGNAL_REJECTED" -> handleStrategySignalRejected(event);
                case "ORDER_SUBMITTED" -> handleOrderSubmitted(event);
                case "POSITION_CLOSED" -> handlePositionClosed(event);
                case "PORTFOLIO_UPDATED" -> handlePortfolioUpdated(event);
                case "RISK_ALERT" -> handleRiskAlert(event);
                case "MARKET_ALERT" -> handleMarketAlert(event);
            }

        } catch (Exception exception) {
            log.error("Failed to process symbol agent event", exception);
        }
    }

    /**
     * Handle MARKET_TICK events - update live bid/ask/spread on symbol state.
     */
    private void handleMarketTick(AgentEvent event) {
        TradePair symbol = extractTradePair(event);
        if (symbol == null) return;

        Object payload = event.payload();
        if (!(payload instanceof Ticker ticker)) return;

        SymbolAgentState state = symbolAgentManager.ensureSymbol(symbol);
        state.setBidPrice(ticker.getBidPrice());
        state.setAskPrice(ticker.getAskPrice());
        if (ticker.getAskPrice() > 0) {
            state.setSpreadPercent((ticker.getAskPrice() - ticker.getBidPrice()) / ticker.getAskPrice() * 100.0);
        }
        symbolAgentManager.updateState(symbol, state);
    }

    /**
     * Handle SIGNAL_CREATED events — capture signal direction and confidence for display.
     */
    private void handleSignalCreated(AgentEvent event) {
        try {
            TradePair symbol = extractTradePair(event);
            if (symbol == null) return;

            String side = getStringAttribute(event, "side", "");
            String strategyName = getStringAttribute(event, "strategy_name", "");
            double confidence = getDoubleAttribute(event, "confidence");

            if (side.isBlank()) return;

            SymbolAgentState state = symbolAgentManager.ensureSymbol(symbol);
            state.setLastSignalSide(side);
            state.setLastSignalConfidence(confidence);
            state.setLastSignalStrategy(strategyName.isBlank() ? null : strategyName);
            state.setLastSignalTime(System.currentTimeMillis());
            symbolAgentManager.updateState(symbol, state);
            log.debug("Symbol {} signal: {} conf={}", symbol, side, confidence);

        } catch (Exception exception) {
            log.error("Failed to handle signal created", exception);
        }
    }

    /**
     * Handle strategy signal approved - update symbol state with signal
     */
    private void handleStrategySignalApproved(AgentEvent event) {
        try {
            TradePair symbol = extractTradePair(event);
            if (symbol == null) {
                return;
            }

            String strategyName = getStringAttribute(event, "strategy_name", "Unknown");
            String side = getStringAttribute(event, "side", "");
            double confidence = getDoubleAttribute(event, "confidence");

            SymbolAgentState state = symbolAgentManager.ensureSymbol(symbol);
            state.setActiveStrategyName(strategyName);
            if (state.getState() == SymbolEvaluationState.NOT_STARTED
                    || state.getState() == SymbolEvaluationState.COLLECTING_DATA
                    || state.getState() == SymbolEvaluationState.BACKTESTING
                    || state.getState() == SymbolEvaluationState.RANKING) {
                state.setState(SymbolEvaluationState.ASSIGNED);
                state.setCanTradeLive(false);
                state.setBlockReason("Signal approved before paper validation completed");
            }
            if (!side.isBlank()) {
                state.setLastSignalSide(side);
                state.setLastSignalConfidence(confidence);
                state.setLastSignalStrategy(strategyName);
                state.setLastSignalTime(System.currentTimeMillis());
            }

            symbolAgentManager.updateState(symbol, state);
            log.debug("Symbol {} assigned to strategy: {} signal={}", symbol, strategyName, side);

        } catch (Exception exception) {
            log.error("Failed to handle strategy signal approved", exception);
        }
    }

    /**
     * Handle strategy signal rejected - mark symbol as failed
     */
    private void handleStrategySignalRejected(AgentEvent event) {
        try {
            TradePair symbol = extractTradePair(event);
            if (symbol == null) {
                return;
            }

            String reason = getStringAttribute(event, "rejection_reason", "Strategy evaluation failed");

            SymbolAgentState state = symbolAgentManager.ensureSymbol(symbol);
            state.setState(SymbolEvaluationState.FAILED);
            state.setLastIssue(reason);
            state.setCanTradeLive(false);

            symbolAgentManager.updateState(symbol, state);
            log.debug("Symbol {} evaluation failed: {}", symbol, reason);

        } catch (Exception exception) {
            log.error("Failed to handle strategy signal rejected", exception);
        }
    }

    /**
     * Handle order submitted - update to live trading
     */
    private void handleOrderSubmitted(AgentEvent event) {
        try {
            TradePair symbol = extractTradePair(event);
            if (symbol == null) {
                return;
            }

            SymbolAgentState state = symbolAgentManager.ensureSymbol(symbol);
            state.setState(SymbolEvaluationState.LIVE_TRADING);
            state.setCanTradeLive(true);

            symbolAgentManager.updateState(symbol, state);
            log.debug("Symbol {} now live trading", symbol);

        } catch (Exception exception) {
            log.error("Failed to handle order submitted", exception);
        }
    }

    /**
     * Handle position closed - back to assigned/ready
     */
    private void handlePositionClosed(AgentEvent event) {
        try {
            TradePair symbol = extractTradePair(event);
            if (symbol == null) {
                return;
            }

            double pnl = getDoubleAttribute(event, "pnl");
            double pnlPercent = getDoubleAttribute(event, "pnl_percent");

            SymbolAgentState state = symbolAgentManager.ensureSymbol(symbol);
            state.setState(SymbolEvaluationState.LIVE_READY);
            state.setCanTradeLive(true);

            symbolAgentManager.updateState(symbol, state);
            log.debug("Symbol {} position closed: pnl={} ({}%)", symbol, pnl, pnlPercent);

        } catch (Exception exception) {
            log.error("Failed to handle position closed", exception);
        }
    }

    /**
     * Handle portfolio update - refresh all tracked symbols
     */
    private void handlePortfolioUpdated(AgentEvent event) {
        try {
            for (SymbolAgentState state : symbolAgentManager.getAllStates()) {
                if (state != null) {
                    state.updateTimestamp();
                }
            }

            log.debug("Portfolio updated: {} symbols refreshed",
                    symbolAgentManager.getAllStates().size());

        } catch (Exception exception) {
            log.error("Failed to handle portfolio update", exception);
        }
    }

    /**
     * Handle risk alert - mark symbol with error
     */
    private void handleRiskAlert(AgentEvent event) {
        try {
            TradePair symbol = extractTradePair(event);
            if (symbol == null) {
                return;
            }

            String riskType = getStringAttribute(event, "risk_type", "UNKNOWN");
            String riskMessage = getStringAttribute(event, "risk_message", "Risk threshold exceeded");

            SymbolAgentState state = symbolAgentManager.ensureSymbol(symbol);
            state.setLastIssue(riskType + ": " + riskMessage);

            symbolAgentManager.updateState(symbol, state);
            log.debug("Symbol {} risk alert: {}", symbol, riskType);

        } catch (Exception exception) {
            log.error("Failed to handle risk alert", exception);
        }
    }

    /**
     * Handle market alert - inform about market conditions
     */
    private void handleMarketAlert(AgentEvent event) {
        try {
            TradePair symbol = extractTradePair(event);
            if (symbol == null) {
                return;
            }

            String alertType = getStringAttribute(event, "alert_type", "UNKNOWN");
            String condition = getStringAttribute(event, "condition", "N/A");

            SymbolAgentState state = symbolAgentManager.ensureSymbol(symbol);
            state.setLastIssue(alertType + ": " + condition);

            symbolAgentManager.updateState(symbol, state);
            log.debug("Symbol {} market alert: {}", symbol, alertType);

        } catch (Exception exception) {
            log.error("Failed to handle market alert", exception);
        }
    }

    /**
     * Extract TradePair from event metadata
     */
    private TradePair extractTradePair(AgentEvent event) {
        Map<String, Object> metadata = event.metadata();
        if (metadata == null) {
            return null;
        }

        Object tradePairObj = metadata.get("tradePairObject");
        if (tradePairObj instanceof TradePair) {
            return (TradePair) tradePairObj;
        }

        tradePairObj = metadata.get("tradePair");
        if (tradePairObj instanceof TradePair) {
            return (TradePair) tradePairObj;
        }

        return null;
    }

    /**
     * Get string attribute from event metadata
     */
    private String getStringAttribute(AgentEvent event, String key, String defaultValue) {
        Object value = event.metadata().getOrDefault(key, defaultValue);
        return value instanceof String ? (String) value : defaultValue;
    }

    /**
     * Get double attribute from event metadata
     */
    private double getDoubleAttribute(AgentEvent event, String key, double defaultValue) {
        Object value = event.metadata().getOrDefault(key, defaultValue);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private double getDoubleAttribute(AgentEvent event, String key) {
        return getDoubleAttribute(event, key, 0.0);
    }

}
