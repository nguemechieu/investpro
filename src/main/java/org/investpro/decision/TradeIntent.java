package org.investpro.decision;

import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Represents a market opportunity and the desired trade posture.
 *
 * <p>TradeIntent is the first object in the institutional decision pipeline. It captures
 * what the system <em>wants</em> to do — the opportunity, direction, and confidence —
 * without committing to how the trade will be executed. Execution details belong in
 * {@link ExecutionPlan}.</p>
 *
 * <p>Created from a strategy signal and a market context. Passed through the pipeline
 * to risk evaluation, AI reasoning, and finally execution planning.</p>
 */
public record TradeIntent(

        /** The instrument to trade. */
        @NotNull TradePair tradePair,

        /** Desired direction: BUY or SELL. */
        @NotNull Side side,

        /** Current market regime at signal time. */
        @NotNull MarketRegime regime,

        /** Asset market classification. */
        @NotNull AssetMarketType assetType,

        /** Source of the setup (strategy, indicator composite, signal aggregator). */
        @NotNull SetupSource setupSource,

        /** Name of the selected strategy generating this intent. May be null if indicator-based. */
        @Nullable String selectedStrategy,

        /** Indicator setup type name. May be null if strategy-based. */
        @Nullable String indicatorSetup,

        /**
         * Confidence in the trade opportunity as a plain double (0.0–1.0).
         * Not BigDecimal — this is a scoring metric, not money.
         */
        double confidence,

        /** Desired portfolio exposure as a percentage of account equity (0.0–100.0). */
        double desiredExposurePercent,

        /** When this intent was created. */
        @NotNull Instant createdAt,

        /** Execution mode controlling allocation strategy. */
        @NotNull DecisionMode mode

) {

    /**
     * Compact factory for simulation-mode intents where many fields are not meaningful.
     */
    public static TradeIntent simulation(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull MarketRegime regime,
            @NotNull AssetMarketType assetType,
            @NotNull SetupSource setupSource,
            @Nullable String strategy,
            double confidence) {

        return new TradeIntent(
                tradePair, side, regime, assetType, setupSource, strategy, null,
                confidence, 1.0, Instant.now(), DecisionMode.SIMULATION);
    }

    /** Returns true if confidence meets institutional minimum (≥ 0.65). */
    public boolean meetsConfidenceThreshold() {
        return confidence >= 0.65;
    }

    /** Returns true if this intent originates from a registered strategy. */
    public boolean isStrategyBased() {
        return setupSource == SetupSource.STRATEGY && selectedStrategy != null;
    }

    /**
     * Returns a short human-readable summary suitable for logging.
     */
    public String toSummary() {
        return String.format(
                "TradeIntent[%s %s | regime=%s | asset=%s | setup=%s | confidence=%.2f | mode=%s]",
                side.name(), tradePair.getSymbol(),
                regime.name(), assetType.name(),
                setupSource.name(), confidence, mode.name());
    }
}
