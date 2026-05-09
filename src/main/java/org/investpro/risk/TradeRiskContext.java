package org.investpro.risk;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.RiskProfile;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.ExecutionStrategy;
import org.investpro.enums.LiquidityProfile;
import org.investpro.enums.PsychologyProfile;
import org.investpro.enums.ProbabilityLevel;
import org.investpro.enums.CapitalProtection;
import org.investpro.enums.SystemDesign;
import org.investpro.enums.TradingSessionStatus;
import org.investpro.models.trading.TradePair;

/**
 * Immutable context capturing all risk inputs for a potential trade.
 * <p>
 * This object is the risk snapshot passed into:
 * - RiskManagementSystem.evaluateTrade(...)
 * - AiTradeReviewRequest.from(...)
 * - TradeExecutionCoordinator.processSignal(...)
 * - ExecutionEngine.executeApprovedOrder(...)
 * <p>
 * It is intentionally immutable and server-capable so it can later be sent to
 * an
 * authoritative backend decision service.
 */

@Getter
@Slf4j
@Value
@Builder(toBuilder = true)
public class TradeRiskContext {

    // =========================================================================
    // Trade identification
    // =========================================================================

    /** Trading symbol/pair, for example BTC/USD, EUR/USD, AAPL/USD. */
    TradePair symbol;

    /** Asset class, for example CRYPTO, FOREX, STOCK, COMMODITY. */
    String assetClass;

    /** Contract type, for example SPOT, FUTURES, CFD, PERPETUAL, OPTION. */
    String contractType;

    /** Broker/exchange name, for example Coinbase, OANDA, Alpaca. */
    String broker;

    // =========================================================================
    // Account state
    // =========================================================================

    /** Account equity in account currency. */
    double accountEquity;

    /** Available cash / buying power. */
    double availableCash;

    /** Current total open position risk in account currency. */
    double currentOpenRisk;

    /** Current used margin, if known. */
    @Builder.Default
    double usedMargin = 0.0;

    /** Current free margin, if known. */
    @Builder.Default
    double freeMargin = 0.0;

    /** Current account balance before unrealized P&L, if known. */
    @Builder.Default
    double accountBalance = 0.0;

    // =========================================================================
    // Trade parameters
    // =========================================================================

    /** Requested position size in units/contracts/base asset quantity. */
    double requestedPositionSize;

    /** Requested leverage. 1.0 means no leverage. */
    @Builder.Default
    double requestedLeverage = 1.0;

    /** Intended entry/current price. */
    double entryPrice;

    /** Stop-loss price. */
    double stopLossPrice;

    /** Take-profit price. */
    double takeProfitPrice;

    /** Optional current bid price. */
    @Builder.Default
    double bidPrice = 0.0;

    /** Optional current ask price. */
    @Builder.Default
    double askPrice = 0.0;

    /** Optional current market price if different from entry. */
    @Builder.Default
    double currentPrice = 0.0;

    // =========================================================================
    // Trade expectation
    // =========================================================================

    /** Expected win rate from 0.0 to 1.0. */
    @Builder.Default
    double expectedWinRate = 0.0;

    /** Expected reward/risk ratio. Reward divided by risk. */
    @Builder.Default
    double expectedRewardRiskRatio = 0.0;

    /** Optional expected value in account currency. */
    @Builder.Default
    double expectedValue = 0.0;

    // =========================================================================
    // Risk framework selections
    // =========================================================================

    RiskProfile riskProfile;
    MarketBehavior marketBehavior;
    ExecutionStrategy executionStrategy;
    LiquidityProfile liquidityProfile;
    PsychologyProfile psychologyProfile;
    ProbabilityLevel probabilityLevel;
    CapitalProtection capitalProtection;
    SystemDesign systemDesign;

    /** Current trading session status for the symbol. */
    TradingSessionStatus tradingSessionStatus;

    /** Human-readable notes for the symbol trading session. */
    @Builder.Default
    String tradingSessionNotes = "";

    // =========================================================================
    // Derived / calculated fields and risk limits
    // =========================================================================

    /** Current market volatility on 0.0 to 1.0 scale. */
    @Builder.Default
    double volatility = 0.0;

    /**
     * User/system max risk per trade as percent of equity, for example 1.0 = 1%.
     */
    @Builder.Default
    double maxRiskPerTrade = 2.0;

    /**
     * User/system max cumulative risk as percent of equity, for example 5.0 = 5%.
     */
    @Builder.Default
    double maxCumulativeRisk = 5.0;

    /** Optional maximum leverage allowed after profile/broker constraints. */
    @Builder.Default
    double maxAllowedLeverage = 1.0;

    /** Optional maximum drawdown allowed as percent. */
    @Builder.Default
    double maxAllowedDrawdownPercent = 20.0;

    /** Optional slippage estimate as percent. */
    @Builder.Default
    double estimatedSlippagePercent = 0.0;

    /** Optional commission/fee estimate in account currency. */
    @Builder.Default
    double estimatedFee = 0.0;

    // =========================================================================
    // Main risk calculations
    // =========================================================================

    /**
     * Calculate trade risk in currency amount.
     * <p>
     * Formula:
     * abs(entry - stopLoss) * positionSize
     */
    public double calculateTradeRisk() {
        if (!hasValidEntryPrice() || !hasStopLoss() || requestedPositionSize <= 0.0) {
            return 0.0;
        }

        double priceRisk = Math.abs(entryPrice - stopLossPrice);
        return safePositive(priceRisk * requestedPositionSize);
    }

    /**
     * Calculate trade risk as percentage of account equity.
     */
    public double calculateTradeRiskPercent() {
        if (accountEquity <= 0.0) {
            return 0.0;
        }

        return safePositive((calculateTradeRisk() / accountEquity) * 100.0);
    }

    /**
     * Calculate potential reward in currency amount.
     * <p>
     * Formula:
     * abs(takeProfit - entry) * positionSize
     */
    public double calculateTradeReward() {
        if (!hasValidEntryPrice() || !hasTakeProfit() || requestedPositionSize <= 0.0) {
            return 0.0;
        }

        double priceReward = Math.abs(takeProfitPrice - entryPrice);
        return safePositive(priceReward * requestedPositionSize);
    }

    /**
     * Calculate potential reward as percentage of account equity.
     */
    public double calculateTradeRewardPercent() {
        if (accountEquity <= 0.0) {
            return 0.0;
        }

        return safePositive((calculateTradeReward() / accountEquity) * 100.0);
    }

    /**
     * Calculate unrealized portfolio risk if this trade is added.
     */
    public double calculateTotalPortfolioRisk() {
        return safePositive(currentOpenRisk + calculateTradeRisk());
    }

    /**
     * Calculate total portfolio heat as percentage of account equity.
     */
    public double calculateTotalPortfolioRiskPercent() {
        if (accountEquity <= 0.0) {
            return 0.0;
        }

        return safePositive((calculateTotalPortfolioRisk() / accountEquity) * 100.0);
    }

    /**
     * Calculate position notional value.
     * <p>
     * Formula:
     * entryPrice * requestedPositionSize
     */
    public double calculatePositionNotional() {
        if (!hasValidEntryPrice() || requestedPositionSize <= 0.0) {
            return 0.0;
        }

        return safePositive(entryPrice * requestedPositionSize);
    }

    /**
     * Calculate margin required based on requested leverage.
     */
    public double calculateRequiredMargin() {
        double leverage = requestedLeverage <= 0.0 ? 1.0 : requestedLeverage;
        return calculatePositionNotional() / leverage;
    }

    /**
     * Calculate risk/reward ratio from stop-loss and take-profit distances.
     */
    public double calculateRewardRiskRatio() {
        double risk = calculateTradeRisk();
        double reward = calculateTradeReward();

        if (risk <= 0.0) {
            return 0.0;
        }

        return reward / risk;
    }

    /**
     * Calculate expected value in account currency.
     * <p>
     * Formula:
     * winRate * reward - lossRate * risk - estimatedFee
     */
    public double calculateExpectedValue() {
        double winRate = clamp(expectedWinRate, 0.0, 1.0);
        double lossRate = 1.0 - winRate;

        double reward = calculateTradeReward();
        double risk = calculateTradeRisk();

        return (winRate * reward) - (lossRate * risk) - Math.max(0.0, estimatedFee);
    }

    /**
     * Calculate expected value as percentage of account equity.
     */
    public double calculateExpectedValuePercent() {
        if (accountEquity <= 0.0) {
            return 0.0;
        }

        return (calculateExpectedValue() / accountEquity) * 100.0;
    }

    // =========================================================================
    // Derived getters used by AI/risk/execution code
    // =========================================================================

    /**
     * Convert volatility from 0.0-1.0 scale to percent.
     */
    public double getVolatilityPercent() {
        return safePositive(volatility * 100.0);
    }

    /**
     * ATR-like percentage based on stop-loss distance from entry.
     */
    public double getAtr() {
        if (!hasValidEntryPrice() || !hasStopLoss()) {
            return 0.0;
        }

        double priceRisk = Math.abs(entryPrice - stopLossPrice);
        return safePositive((priceRisk / entryPrice) * 100.0);
    }

    /**
     * Current account drawdown / risk pressure estimate.
     */
    public double getCurrentDrawdownPercent() {
        if (accountEquity <= 0.0 || currentOpenRisk <= 0.0) {
            return 0.0;
        }

        return safePositive((currentOpenRisk / accountEquity) * 100.0);
    }

    /**
     * Portfolio heat as percentage of account equity.
     */
    public double getPortfolioHeatPercent() {
        return calculateTotalPortfolioRiskPercent();
    }

    /**
     * Spread percentage between bid/ask prices.
     */
    public double getSpreadPercent() {
        if (bidPrice <= 0.0 || askPrice <= 0.0 || askPrice < bidPrice) {
            return 0.0;
        }

        double mid = (bidPrice + askPrice) / 2.0;
        if (mid <= 0.0) {
            return 0.0;
        }

        return ((askPrice - bidPrice) / mid) * 100.0;
    }

    /**
     * Required margin as percentage of available cash.
     */
    public double getRequiredMarginPercentOfAvailableCash() {
        if (availableCash <= 0.0) {
            return 0.0;
        }

        return (calculateRequiredMargin() / availableCash) * 100.0;
    }

    /**
     * Risk limit usage for this trade, from 0.0 upward.
     * 1.0 means the trade exactly uses the configured max risk per trade.
     */
    public double getRiskLimitUsageRatio() {
        if (maxRiskPerTrade <= 0.0) {
            return 0.0;
        }

        return calculateTradeRiskPercent() / maxRiskPerTrade;
    }

    /**
     * Cumulative risk limit usage, from 0.0 upward.
     * 1.0 means the portfolio exactly uses the configured max cumulative risk.
     */
    public double getCumulativeRiskUsageRatio() {
        if (maxCumulativeRisk <= 0.0) {
            return 0.0;
        }

        return calculateTotalPortfolioRiskPercent() / maxCumulativeRisk;
    }

    /**
     * Returns the underlying TradePair.
     */
    public TradePair getTradePair() {
        return symbol;
    }

    /**
     * Symbol string helper for logs/UI.
     */
    public String getSymbolText() {
        if (symbol == null) {
            return "";
        }

        try {
            return symbol.toString('/');
        } catch (Exception exception) {
            return symbol.toString();
        }
    }

    // =========================================================================
    // Validation helpers
    // =========================================================================

    public boolean hasValidSymbol() {
        return symbol != null;
    }

    public boolean hasValidEntryPrice() {
        return entryPrice > 0.0 && Double.isFinite(entryPrice);
    }

    public boolean hasStopLoss() {
        return stopLossPrice > 0.0 && Double.isFinite(stopLossPrice);
    }

    public boolean hasTakeProfit() {
        return takeProfitPrice > 0.0 && Double.isFinite(takeProfitPrice);
    }

    public boolean hasValidPositionSize() {
        return requestedPositionSize > 0.0 && Double.isFinite(requestedPositionSize);
    }

    public boolean hasValidAccountEquity() {
        return accountEquity > 0.0 && Double.isFinite(accountEquity);
    }

    public boolean hasEnoughAvailableCash() {
        return availableCash <= 0.0 || calculateRequiredMargin() <= availableCash;
    }

    public boolean isRiskWithinPerTradeLimit() {
        return maxRiskPerTrade <= 0.0 || calculateTradeRiskPercent() <= maxRiskPerTrade;
    }

    public boolean isRiskWithinCumulativeLimit() {
        return maxCumulativeRisk <= 0.0 || calculateTotalPortfolioRiskPercent() <= maxCumulativeRisk;
    }

    public boolean isLeverageWithinLimit() {
        return maxAllowedLeverage <= 0.0 || requestedLeverage <= maxAllowedLeverage;
    }

    public boolean isDrawdownWithinLimit() {
        return maxAllowedDrawdownPercent <= 0.0 || getCurrentDrawdownPercent() <= maxAllowedDrawdownPercent;
    }

    /**
     * Returns true if the context has the minimum inputs needed for risk
     * evaluation.
     */
    public boolean isValidForRiskEvaluation() {
        return hasValidSymbol()
                && hasValidAccountEquity()
                && hasValidEntryPrice()
                && hasValidPositionSize();
    }

    /**
     * Returns true if this context has enough information for bracket-style risk
     * evaluation.
     */
    public boolean hasBracketRiskPlan() {
        return hasValidEntryPrice() && hasStopLoss() && hasTakeProfit();
    }

    // =========================================================================
    // Advanced risk assessment methods
    // =========================================================================

    /**
     * Comprehensive risk viability check combining all constraints.
     *
     * @return true if the trade passes all risk gates
     */
    public boolean isTradeViable() {
        return isValidForRiskEvaluation()
                && hasBracketRiskPlan()
                && hasEnoughAvailableCash()
                && isRiskWithinPerTradeLimit()
                && isRiskWithinCumulativeLimit()
                && isLeverageWithinLimit()
                && isDrawdownWithinLimit()
                && hasPositiveExpectancy();
    }

    /**
     * Check if trade has positive expected value.
     */
    public boolean hasPositiveExpectancy() {
        return calculateExpectedValue() > 0.0;
    }

    /**
     * Check if reward/risk ratio meets minimum threshold (default 1.0).
     */
    public boolean meetMinimumRewardRiskRatio() {
        return calculateRewardRiskRatio() >= 1.0;
    }

    /**
     * Check if reward/risk ratio meets a specific threshold.
     */
    public boolean meetMinimumRewardRiskRatio(double minimumRatio) {
        if (minimumRatio <= 0.0) {
            return true;
        }

        return calculateRewardRiskRatio() >= minimumRatio;
    }

    /**
     * Get risk severity level: LOW, MEDIUM, HIGH, CRITICAL based on portfolio heat.
     */
    public String getRiskSeverityLevel() {
        double portfolioHeat = getPortfolioHeatPercent();

        if (portfolioHeat < 2.0) {
            return "LOW";
        } else if (portfolioHeat < 5.0) {
            return "MEDIUM";
        } else if (portfolioHeat < 10.0) {
            return "HIGH";
        } else {
            return "CRITICAL";
        }
    }

    /**
     * Calculate volatility-adjusted position size: base size reduced by volatility.
     * <p>
     * Formula:
     * adjustedSize = requestedPositionSize * (1.0 - volatility *
     * volatilityAdjustmentFactor)
     */
    public double getVolatilityAdjustedPositionSize(double volatilityAdjustmentFactor) {
        if (volatilityAdjustmentFactor < 0.0) {
            return requestedPositionSize;
        }

        double adjustmentFactor = 1.0 - (volatility * volatilityAdjustmentFactor);
        return safePositive(requestedPositionSize * Math.max(0.1, adjustmentFactor));
    }

    /**
     * Calculate volatility-adjusted leverage: reduced by volatility.
     * <p>
     * Formula:
     * adjustedLeverage = requestedLeverage / (1.0 + volatility *
     * volatilityAdjustmentFactor)
     */
    public double getVolatilityAdjustedLeverage(double volatilityAdjustmentFactor) {
        if (volatilityAdjustmentFactor < 0.0) {
            return requestedLeverage;
        }

        double denominator = 1.0 + (volatility * volatilityAdjustmentFactor);
        return safePositive(requestedLeverage / denominator);
    }

    /**
     * Calculate risk-adjusted stop-loss price based on volatility.
     * Wider stop-loss during high volatility to reduce whipsaws.
     */
    public double getVolatilityAdjustedStopLoss(double volatilityExpansionFactor) {
        if (!hasValidEntryPrice() || !hasStopLoss() || volatilityExpansionFactor < 0.0) {
            return stopLossPrice;
        }

        double baseDistance = Math.abs(entryPrice - stopLossPrice);
        double adjustedDistance = baseDistance * (1.0 + volatility * volatilityExpansionFactor);
        double isLong = entryPrice > stopLossPrice ? 1.0 : -1.0;

        return entryPrice - (adjustedDistance * isLong);
    }

    /**
     * Get margin utilization percentage: used margin / available cash.
     */
    public double getMarginUtilizationPercent() {
        if (availableCash <= 0.0) {
            return 0.0;
        }

        return (usedMargin / availableCash) * 100.0;
    }

    /**
     * Get free margin as percentage of available cash.
     */
    public double getFreeMarginPercent() {
        if (availableCash <= 0.0) {
            return 0.0;
        }

        return (freeMargin / availableCash) * 100.0;
    }

    /**
     * Calculate max position size based on available cash and leverage.
     */
    public double getMaxPositionSizeByAvailableCash() {
        if (availableCash <= 0.0 || entryPrice <= 0.0) {
            return 0.0;
        }

        return safePositive((availableCash * requestedLeverage) / entryPrice);
    }

    /**
     * Calculate max position size based on risk limit.
     */
    public double getMaxPositionSizeByRiskLimit() {
        if (accountEquity <= 0.0 || !hasStopLoss() || entryPrice <= stopLossPrice) {
            return 0.0;
        }

        double maxRiskAmount = (accountEquity * maxRiskPerTrade) / 100.0;
        double priceRisk = Math.abs(entryPrice - stopLossPrice);

        if (priceRisk <= 0.0) {
            return 0.0;
        }

        return safePositive(maxRiskAmount / priceRisk);
    }

    /**
     * Get recommended position size: minimum of cash and risk limit constraints.
     */
    public double getRecommendedMaxPositionSize() {
        double byAvailableCash = getMaxPositionSizeByAvailableCash();
        double byRiskLimit = getMaxPositionSizeByRiskLimit();

        if (byAvailableCash <= 0.0) {
            return byRiskLimit;
        }

        if (byRiskLimit <= 0.0) {
            return byAvailableCash;
        }

        return Math.min(byAvailableCash, byRiskLimit);
    }

    /**
     * Check if position size exceeds recommended max.
     */
    public boolean isPositionSizeExceedsRecommended() {
        double maxSize = getRecommendedMaxPositionSize();
        return maxSize > 0.0 && requestedPositionSize > maxSize;
    }

    /**
     * Get slippage impact in account currency.
     */
    public double getSlippageImpact() {
        return calculatePositionNotional() * (estimatedSlippagePercent / 100.0);
    }

    /**
     * Get total cost of trade: fees + slippage.
     */
    public double getTotalTradeCost() {
        return estimatedFee + getSlippageImpact();
    }

    /**
     * Calculate net expected value after fees and slippage.
     */
    public double getNetExpectedValue() {
        return calculateExpectedValue() - getTotalTradeCost();
    }

    /**
     * Get a diagnostic string for logging and debugging.
     */
    public String toDebugString() {
        return String.format(
                "TradeRiskContext{symbol=%s, equity=%.2f, cash=%.2f, size=%.2f, "
                        + "entry=%.2f, stop=%.2f, tp=%.2f, risk=%.2f(%.2f%%), "
                        + "reward=%.2f(%.2f%%), rr=%.2f, leverage=%.2f, "
                        + "volatility=%.2f, heat=%.2f%%, viable=%s}",
                getSymbolText(),
                accountEquity,
                availableCash,
                requestedPositionSize,
                entryPrice,
                stopLossPrice,
                takeProfitPrice,
                calculateTradeRisk(),
                calculateTradeRiskPercent(),
                calculateTradeReward(),
                calculateTradeRewardPercent(),
                calculateRewardRiskRatio(),
                requestedLeverage,
                volatility,
                getPortfolioHeatPercent(),
                isTradeViable());
    }

    /**
     * Get a summary string for UI display.
     */
    public String toSummaryString() {
        return String.format(
                "%s: Risk %.2f%% | Heat %.2f%% | RR %.2f | EV %.2f | %s",
                getSymbolText(),
                calculateTradeRiskPercent(),
                getPortfolioHeatPercent(),
                calculateRewardRiskRatio(),
                calculateExpectedValue(),
                getRiskSeverityLevel());
    }

    /**
     * Normalize dangerous numeric values into safer defaults.
     */
    public TradeRiskContext normalized() {
        return this.toBuilder()
                .accountEquity(safePositive(accountEquity))
                .availableCash(safePositive(availableCash))
                .currentOpenRisk(safePositive(currentOpenRisk))
                .usedMargin(safePositive(usedMargin))
                .freeMargin(safePositive(freeMargin))
                .accountBalance(safePositive(accountBalance))
                .requestedPositionSize(safePositive(requestedPositionSize))
                .requestedLeverage(
                        requestedLeverage <= 0.0 || !Double.isFinite(requestedLeverage) ? 1.0 : requestedLeverage)
                .entryPrice(safePositive(entryPrice))
                .stopLossPrice(safePositive(stopLossPrice))
                .takeProfitPrice(safePositive(takeProfitPrice))
                .bidPrice(safePositive(bidPrice))
                .askPrice(safePositive(askPrice))
                .currentPrice(safePositive(currentPrice))
                .expectedWinRate(clamp(expectedWinRate, 0.0, 1.0))
                .expectedRewardRiskRatio(safePositive(expectedRewardRiskRatio))
                .expectedValue(Double.isFinite(expectedValue) ? expectedValue : 0.0)
                .volatility(clamp(volatility, 0.0, 1.0))
                .maxRiskPerTrade(safePositive(maxRiskPerTrade))
                .maxCumulativeRisk(safePositive(maxCumulativeRisk))
                .maxAllowedLeverage(safePositive(maxAllowedLeverage))
                .maxAllowedDrawdownPercent(safePositive(maxAllowedDrawdownPercent))
                .estimatedSlippagePercent(safePositive(estimatedSlippagePercent))
                .estimatedFee(safePositive(estimatedFee))
                .tradingSessionStatus(tradingSessionStatus)
                .tradingSessionNotes(tradingSessionNotes == null ? "" : tradingSessionNotes)
                .build();
    }

    private static double safePositive(double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            return 0.0;
        }

        return value;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }

        return Math.max(min, Math.min(max, value));
    }
}
