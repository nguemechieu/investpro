package org.investpro.strategy.position;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

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

    /** Trading symbol. */
    private final String symbol;

    /** Timeframe. */
    private final String timeframe;

    /** Strategy identifier requesting the sizing. */
    private final String strategyId;

    /** Sizing method to apply. */
    private final PositionSizingMethod method;

    /** Total account equity in account currency. */
    private final double accountEquity;

    /** Risk percentage per trade (e.g. 1.0 = 1%). */
    private final double riskPercent;

    /** Intended entry price. */
    private final double entryPrice;

    /** Stop-loss price. */
    private final double stopLossPrice;

    /** Average True Range value (used for ATR_BASED method). */
    private final double atrValue;

    /** Current volatility measure (used for VOLATILITY_BASED method). */
    private final double currentVolatility;

    /** Estimated win probability (used for KELLY_CRITERION method). */
    private final double winProbability;

    /** Expected win/loss ratio (used for KELLY_CRITERION method). */
    private final double winLossRatio;

    /** Current drawdown as a percentage (used for DRAWDOWN_SCALING method). */
    private final double currentDrawdownPercent;

    /** Maximum allowed position size as a percentage of equity. */
    private final double maxPositionSizePercent;

    /**
     * Size multiplier from an AI signal review (0.1-1.0).
     * Applied after the base calculation.
     * Default 1.0 = no AI adjustment.
     */
    @Builder.Default
    private final double aiSizeMultiplier = 1.0;

    /** Timestamp when this request was created. */
    @Builder.Default
    private final Instant requestedAt = Instant.now();
}
