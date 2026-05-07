package org.investpro.core.agents.symbol;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.TradePair;
import org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the current state of a symbol's strategy evaluation and trading
 * readiness.
 * 
 * This is the single source of truth for MarketWatch and System Monitor
 * regarding
 * what evaluation/trading state a symbol is in.
 */
@Data
@Builder
@Slf4j
public class SymbolAgentState {

    @NotNull
    private TradePair symbol;

    @NotNull
    private SymbolEvaluationState state;

    @Nullable
    private String activeStrategyName;

    @Nullable
    private Timeframe activeTimeframe;

    private double strategyScore;

    private boolean canTradeLive;

    @Nullable
    private String lastIssue;

    @Nullable
    private String assignedStrategyName;

    @Nullable
    private String blockReason;

    private long lastUpdated;

    /**
     * Determines the trading mode based on the current evaluation state.
     * 
     * Mapping:
     * - NOT_STARTED, COLLECTING_DATA, BACKTESTING, RANKING -> TRAINING
     * - PAPER_TRADING -> PAPER_TRADING
     * - ASSIGNED -> LIVE_READY if assignment exists, NO_ASSIGNMENT if not
     * - LIVE_READY -> LIVE_READY
     * - LIVE_TRADING -> LIVE_TRADING
     * - FAILED -> FAILED
     * - PAUSED -> PAUSED
     * - null -> UNKNOWN
     */
    public SymbolTradingMode getTradingMode() {
        if (state == null) {
            return SymbolTradingMode.UNKNOWN;
        }

        return switch (state) {
            case NOT_STARTED, COLLECTING_DATA, BACKTESTING, RANKING -> SymbolTradingMode.TRAINING;
            case PAPER_TRADING -> SymbolTradingMode.PAPER_TRADING;
            case ASSIGNED -> {
                // If assigned, check if we have a strategy name
                if (assignedStrategyName != null && !assignedStrategyName.isBlank()) {
                    yield SymbolTradingMode.LIVE_READY;
                } else {
                    yield SymbolTradingMode.NO_ASSIGNMENT;
                }
            }
            case LIVE_READY -> SymbolTradingMode.LIVE_READY;
            case LIVE_TRADING -> SymbolTradingMode.LIVE_TRADING;
            case FAILED -> SymbolTradingMode.FAILED;
            case PAUSED -> SymbolTradingMode.PAUSED;
        };
    }

    /**
     * Returns human-readable status text for MarketWatch display.
     * 
     * Examples:
     * - "Training / Evaluating" (for TRAINING mode)
     * - "Paper trading candidates" (for PAPER_TRADING)
     * - "Live ready: RSI_Breakout | 1h" (for LIVE_READY with strategy/timeframe)
     * - "Live trading: MovingAvg | 5m" (for LIVE_TRADING)
     * - "No evaluated assignment" (for NO_ASSIGNMENT)
     * - "Blocked: Manual pause" (for BLOCKED with reason)
     * - "Paused" (for PAUSED)
     * - "Failed: Insufficient data" (for FAILED with reason)
     * - "Unknown" (for UNKNOWN)
     */
    public String getMarketWatchStatusText() {
        SymbolTradingMode mode = getTradingMode();

        switch (mode) {
            case TRAINING:
                return "Training / Evaluating";

            case PAPER_TRADING:
                return "Paper trading candidates";

            case LIVE_READY:
                if (activeStrategyName != null && activeTimeframe != null) {
                    return "Live ready: " + activeStrategyName + " | " + activeTimeframe.getCode();
                }
                return "Live ready: " + (assignedStrategyName != null ? assignedStrategyName : "Unknown");

            case LIVE_TRADING:
                if (activeStrategyName != null && activeTimeframe != null) {
                    return "Live trading: " + activeStrategyName + " | " + activeTimeframe.getCode();
                }
                return "Live trading: " + (assignedStrategyName != null ? assignedStrategyName : "Unknown");

            case NO_ASSIGNMENT:
                return "No evaluated assignment";

            case BLOCKED:
                if (blockReason != null && !blockReason.isBlank()) {
                    return "Blocked: " + blockReason;
                }
                return "Blocked";

            case PAUSED:
                return "Paused";

            case FAILED:
                if (lastIssue != null && !lastIssue.isBlank()) {
                    return "Failed: " + lastIssue;
                }
                return "Failed";

            case UNKNOWN:
            default:
                return "Unknown";
        }
    }

    /**
     * Checks if this symbol is allowed to trade live.
     */
    public boolean isLiveAllowed() {
        return getTradingMode().isLiveAllowed() && canTradeLive;
    }

    /**
     * Returns a human-readable reason if live trading is blocked.
     */
    public String getLiveBlockedReason() {
        if (canTradeLive && getTradingMode().isLiveAllowed()) {
            return null; // Not blocked
        }

        if (blockReason != null && !blockReason.isBlank()) {
            return blockReason;
        }

        SymbolTradingMode mode = getTradingMode();
        if (!mode.isLiveAllowed()) {
            return "Mode not live-ready: " + mode.getDisplayName();
        }

        if (!canTradeLive) {
            return "Evaluation not complete";
        }

        return "Unknown reason";
    }

    /**
     * Update timestamp to now.
     */
    public void updateTimestamp() {
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Check if state is stale (older than specified milliseconds).
     */
    public boolean isStale(long maxAgeMs) {
        return System.currentTimeMillis() - lastUpdated > maxAgeMs;
    }
}
