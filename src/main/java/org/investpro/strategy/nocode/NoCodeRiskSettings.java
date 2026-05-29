package org.investpro.strategy.nocode;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * Risk and money-management settings attached to a no-code strategy.
 *
 * <p>These settings inform the InvestPro PositionSizingEngine and provide
 * default signal metadata. The RiskEngine remains the final authority on
 * whether a trade size is acceptable — these settings do not bypass it.</p>
 */
@Getter
@Builder
@ToString
public class NoCodeRiskSettings {

    /**
     * Stop-loss as a percentage of entry price (e.g. 1.5 = 1.5%).
     * A value of 0 means no stop-loss is defined by the strategy.
     */
    @Builder.Default
    private final double stopLossPercent = 1.5;

    /**
     * Take-profit as a percentage of entry price (e.g. 3.0 = 3.0%).
     * A value of 0 means no take-profit is defined.
     */
    @Builder.Default
    private final double takeProfitPercent = 3.0;

    /**
     * Maximum number of new trades this strategy may signal per calendar day.
     * The ExecutionEngine enforces this limit; a value of 0 means unlimited.
     */
    @Builder.Default
    private final int maxTradesPerDay = 5;

    /**
     * Maximum open drawdown allowed (percentage of equity) before the strategy
     * should stop generating new signals. Enforced by the RiskEngine.
     * A value of 0 means no drawdown limit is defined at the strategy level.
     */
    @Builder.Default
    private final double maxDrawdownPercent = 10.0;

    /**
     * Maximum position size as a percentage of total account equity.
     * 0 = use platform default.
     */
    @Builder.Default
    private final double maxPositionSizePercent = 5.0;

    /**
     * Allowed trading sessions. Empty = trade in any session.
     * Examples: "LONDON", "NEW_YORK", "TOKYO", "SYDNEY".
     */
    private final List<String> allowedSessions;

    // =========================================================================
    // Convenience
    // =========================================================================

    /** @return true if a stop-loss percentage has been defined. */
    public boolean hasStopLoss() {
        return stopLossPercent > 0;
    }

    /** @return true if a take-profit percentage has been defined. */
    public boolean hasTakeProfit() {
        return takeProfitPercent > 0;
    }

    /**
     * Calculates the stop-loss price for a BUY entry.
     *
     * @param entryPrice the entry price
     * @return stop-loss price
     */
    public double calculateStopLossForBuy(double entryPrice) {
        return entryPrice * (1.0 - stopLossPercent / 100.0);
    }

    /**
     * Calculates the stop-loss price for a SELL entry.
     *
     * @param entryPrice the entry price
     * @return stop-loss price
     */
    public double calculateStopLossForSell(double entryPrice) {
        return entryPrice * (1.0 + stopLossPercent / 100.0);
    }

    /**
     * Calculates the take-profit price for a BUY entry.
     *
     * @param entryPrice the entry price
     * @return take-profit price
     */
    public double calculateTakeProfitForBuy(double entryPrice) {
        return entryPrice * (1.0 + takeProfitPercent / 100.0);
    }

    /**
     * Calculates the take-profit price for a SELL entry.
     *
     * @param entryPrice the entry price
     * @return take-profit price
     */
    public double calculateTakeProfitForSell(double entryPrice) {
        return entryPrice * (1.0 - takeProfitPercent / 100.0);
    }
}
