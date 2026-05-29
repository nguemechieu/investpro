package org.investpro.decision;

import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Complete structured decision output from BotTradeDecisionEngine.
 * Every signal is converted to a BotTradeDecision before any execution.
 * <p>
 * The bot NEVER trades directly from a signal.
 * Every signal must pass through this rigorous institutional-grade evaluation.
 */
public record BotTradeDecision(
        @NotNull TradePair tradePair,
        @NotNull Side side, // BUY or SELL
        @NotNull MarketRegime detectedRegime,
        @NotNull AssetMarketType assetType,
        @NotNull SetupSource setupSource, // STRATEGY, INDICATOR_COMPOSITE, or NONE
        @Nullable String selectedStrategyName, // null if indicator composite
        @Nullable IndicatorSetupType indicatorSetupType, // null if strategy
        @NotNull StrategyFitScore bestStrategyScore, // best strategy (even if not selected)
        @Nullable IndicatorSetupScore indicatorSetupScore, // null if strategy used
        @NotNull TradeCostEstimate costEstimate,
        @NotNull TradeExpectation expectation,
        @NotNull HoldingPeriodEstimate holdingPeriod,
        @NotNull FinalAction finalAction, // TRADE or SKIP
        @NotNull List<String> reasons, // detailed reasons for decision
        @NotNull List<String> warnings, // non-blocking warnings (may still trade)
        @NotNull List<String> blockers, // blocking issues (skip trade)
        @NotNull String fullAnalysisSummary, // complete human-readable analysis
        @NotNull Instant decidedAt) {

    /**
     * Final action for a trade decision
     */
    public enum FinalAction {
        TRADE("Execute trade"),
        SKIP("Skip trade"),
        WAIT("Wait for better conditions"),
        HOLD("Hold current state"),
        REDUCE_SIZE("Reduce order size and continue"),
        CLOSE("Close existing position");

        public final String description;

        FinalAction(String description) {
            this.description = description;
        }

        public boolean shouldTrade() {
            return this == TRADE || this == REDUCE_SIZE || this == CLOSE;
        }
    }

    // Convenience accessors
    public boolean willTrade() {
        return finalAction == FinalAction.TRADE;
    }

    public boolean willSkip() {
        return finalAction == FinalAction.SKIP;
    }

    public String getSetupSourceDescription() {
        if (setupSource == SetupSource.STRATEGY && selectedStrategyName != null) {
            return "Strategy: " + selectedStrategyName;
        } else if (setupSource == SetupSource.INDICATOR_COMPOSITE && indicatorSetupType != null) {
            return "Indicator Composite: " + indicatorSetupType.description;
        } else {
            return "No suitable setup";
        }
    }

    public boolean hasBlockingIssues() {
        return !blockers.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Return a concise one-line summary of the decision
     */
    public String getDecisionSummary() {
        return String.format(
                "%s %s %s [%s regime, %s fit] -> %s",
                finalAction.description,
                side.name(),
                tradePair.getSymbol(),
                detectedRegime.name(),
                setupSource.name(),
                expectation.getExpectedValueFormatted());
    }
}
