package org.investpro.core.agents.symbol;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.TradePair;
import org.investpro.enums.timeframe.Timeframe;
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

    // Live market data (updated from MARKET_TICK events)
    private double bidPrice;
    private double askPrice;
    private double spreadPercent;

    // Last signal received (updated from SIGNAL_CREATED / STRATEGY_SIGNAL_APPROVED)
    @Nullable
    private String lastSignalSide;     // "BUY", "SELL", or null if no signal yet
    private double lastSignalConfidence;
    @Nullable
    private String lastSignalStrategy;
    private long lastSignalTime;

    /**
     * Determines the trading mode based on the current evaluation state.
     */
    public SymbolTradingMode getTradingMode() {

        return switch (state) {
            case NOT_STARTED, COLLECTING_DATA, BACKTESTING, RANKING -> SymbolTradingMode.TRAINING;
            case PAPER_TRADING -> SymbolTradingMode.PAPER_TRADING;
            case ASSIGNED -> {
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
     */
    public String getMarketWatchStatusText() {
        SymbolTradingMode mode = getTradingMode();
        String signalSuffix = getSignalText();

        return switch (mode) {
            case TRAINING -> "Training / Evaluating";
            case PAPER_TRADING -> "Paper trading candidates";
            case LIVE_READY -> {
                String base;
                if (activeStrategyName != null && activeTimeframe != null) {
                    base = "Live ready: " + activeStrategyName + " | " + activeTimeframe.getCode();
                } else {
                    base = "Live ready: " + (assignedStrategyName != null ? assignedStrategyName : "Unknown");
                }
                yield signalSuffix.isBlank() ? base : base + " " + signalSuffix;
            }
            case LIVE_TRADING -> {
                String base;
                if (activeStrategyName != null && activeTimeframe != null) {
                    base = "Live trading: " + activeStrategyName + " | " + activeTimeframe.getCode();
                } else {
                    base = "Live trading: " + (assignedStrategyName != null ? assignedStrategyName : "Unknown");
                }
                yield signalSuffix.isBlank() ? base : base + " " + signalSuffix;
            }
            case NO_ASSIGNMENT -> "No evaluated assignment";
            case BLOCKED -> {
                if (blockReason != null && !blockReason.isBlank()) {
                    yield "Blocked: " + blockReason;
                }
                yield "Blocked";
            }
            case PAUSED -> "Paused";
            case FAILED -> {
                if (lastIssue != null && !lastIssue.isBlank()) {
                    yield "Failed: " + lastIssue;
                }
                yield "Failed";
            }
            default -> "Unknown";
        };
    }

    /**
     * Returns a compact signal text like "\u25b2 BUY 0.82" or "\u25bc SELL 0.65", empty if no signal.
     */
    public String getSignalText() {
        if (lastSignalSide == null || lastSignalSide.isBlank()) return "";
        String arrow = "BUY".equalsIgnoreCase(lastSignalSide) ? "\u25b2" : "\u25bc";
        if (lastSignalConfidence > 0) {
            return String.format("%s %s %.2f", arrow, lastSignalSide.toUpperCase(), lastSignalConfidence);
        }
        return arrow + " " + lastSignalSide.toUpperCase();
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
            return null;
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
