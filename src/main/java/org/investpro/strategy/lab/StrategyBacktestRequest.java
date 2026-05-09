package org.investpro.strategy.lab;

import lombok.Builder;
import lombok.Getter;
import org.investpro.data.CandleData;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Request to backtest a single strategy on a symbol/timeframe.
 *
 * Contains all parameters needed to run a strategy backtest:
 * - what strategy to test
 * - which symbol and timeframe
 * - historical candles
 * - capital and risk parameters
 * - execution settings
 */
@Getter
@Builder(toBuilder = true)
public class StrategyBacktestRequest {

    /**
     * Trading symbol, e.g., "BTC/USD", "EUR/USD".
     */
    @NotNull
    private final String symbol;

    /**
     * Trading timeframe, e.g., M15, H1, D1.
     */
    @NotNull
    private final Timeframe timeframe;

    /**
     * Strategy name to test.
     * Can be catalog variant like "Trend Following | Swing | Conservative"
     * or simple strategy ID like "trend-following".
     */
    @NotNull
    private final String strategyName;

    /**
     * Optional strategy definition with parameters.
     * If null, will be looked up from StrategyCatalog using strategyName.
     */
    private final StrategyDefinition strategyDefinition;

    /**
     * Historical candles for backtesting.
     * Must be at least 50 candles for meaningful results.
     */
    @NotNull
    private final List<CandleData> candles;

    /**
     * Initial capital for simulation.
     * Default: 10000.0
     */
    @Builder.Default
    private final double initialCapital = 10000.0;

    /**
     * Commission rate per trade (e.g., 0.001 for 0.1%).
     * Default: 0.001
     */
    @Builder.Default
    private final double commissionRate = 0.001;

    /**
     * Slippage rate per trade (e.g., 0.0002 for 0.02%).
     * Default: 0.0002
     */
    @Builder.Default
    private final double slippageRate = 0.0002;

    /**
     * Maximum number of trades to execute.
     * Default: Integer.MAX_VALUE (no limit).
     */
    @Builder.Default
    private final int maxTrades = Integer.MAX_VALUE;

    /**
     * Whether to allow short trades.
     * Default: true.
     */
    @Builder.Default
    private final boolean allowShorts = true;

    /**
     * Whether to use RiskManagement stops/targets.
     * Default: true.
     */
    @Builder.Default
    private final boolean useRiskManagement = true;

    /**
     * When this backtest was requested.
     */
    @Builder.Default
    private final Instant requestedAt = Instant.now();

    /**
     * Fallback exit bars if neither take-profit nor stop-loss hit.
     * Default: 20.
     */
    @Builder.Default
    private final int fallbackExitBars = 20;
}
