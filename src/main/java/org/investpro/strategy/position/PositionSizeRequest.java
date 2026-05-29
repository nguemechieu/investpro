package org.investpro.strategy.position;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.investpro.strategy.lifecycle.StrategyLearningProfile;

import java.time.Instant;
import java.util.UUID;

/**
 * Request parameters for a position size calculation.
 * Submitted to {@link PositionSizingEngine} by the decision pipeline.
 * The RiskEngine must approve the resulting {@link PositionSizeResult} before
 * any order is submitted.
 */
@Getter
@Builder
@ToString
public class PositionSizeRequest {

    /** Unique request identifier (UUID). */
    @Builder.Default
    private final String requestId = UUID.randomUUID().toString();

    /** Assignment identifier from the strategy lifecycle. */
    private final String assignmentId;

    /** Trading symbol. */
    private final String symbol;

    /** Timeframe code. */
    private final String timeframe;

    /** Strategy identifier requesting the sizing. */
    private final String strategyId;

    /** Trade direction as string: BUY or SELL. */
    private final String side;

    /** Sizing method to apply. */
    private final PositionSizingMethod method;

    /** Total account equity in account currency. */
    private final double equity;

    /** Risk per trade as a decimal (e.g. 0.02 = 2%). */
    private final double riskPerTradePercent;

    /** Intended entry price. */
    private final double entryPrice;

    /** Stop-loss price. */
    private final double stopLossPrice;

    /** Absolute distance from entry to stop-loss (|entry - stopLoss|). */
    private final double stopLossDistance;

    /** Pip/tick value in account currency. */
    private final double pipValue;

    /** Fixed lot size (used by FIXED_LOT method). */
    private final double lotSize;

    /** Average True Range value (used by ATR_BASED method). */
    private final double atr;

    /** Current volatility measure (used by VOLATILITY_ADJUSTED method). */
    private final double volatility;

    /** Estimated win probability (used by KELLY_CRITERION method). */
    private final double winProbability;

    /** Expected win/loss ratio (used by KELLY_CRITERION method). */
    private final double winLossRatio;

    /** Current drawdown as a decimal (used by DRAWDOWN_SCALING method). */
    private final double currentDrawdownPercent;

    /** Maximum allowed position size as a decimal fraction of equity (e.g. 0.10 = 10%). */
    private final double maxPositionSizePercent;

    /** Absolute maximum dollar loss allowed per trade (used by MAX_LOSS method). */
    private final double maxDollarLoss;

    /** Maximum concurrent open positions (used by EQUAL_WEIGHT method). */
    private final int maxOpenPositions;

    /**
     * AI-derived size multiplier (0.25–1.50). Applied after the base calculation.
     * 1.0 = no adjustment (default).
     */
    @Builder.Default
    private final double aiSizeMultiplier = 1.0;

    /** Optional learning profile providing historical win/loss context for Kelly sizing. */
    private final StrategyLearningProfile learningProfile;

    /** Timestamp when this request was created. */
    @Builder.Default
    private final Instant requestedAt = Instant.now();
}
