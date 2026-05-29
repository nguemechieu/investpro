package org.investpro.execution;

import org.investpro.decision.TradePlan;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * Generates deterministic fallback trade plans from ticker and side.
 */
public class TradePlanGenerator {

    @NotNull
    public TradePlan generate(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull Ticker ticker,
            double signalStrength,
            double accountBalance) {

        BigDecimal entryPrice = BigDecimal.valueOf(side == Side.BUY ? ticker.getAskPrice() : ticker.getBidPrice());
        BigDecimal riskPercent = BigDecimal.valueOf(0.01);
        BigDecimal rewardPercent = BigDecimal.valueOf(0.02);

        BigDecimal stopLoss = side == Side.BUY
                ? entryPrice.multiply(BigDecimal.ONE.subtract(riskPercent))
                : entryPrice.multiply(BigDecimal.ONE.add(riskPercent));
        BigDecimal takeProfit = side == Side.BUY
                ? entryPrice.multiply(BigDecimal.ONE.add(rewardPercent))
                : entryPrice.multiply(BigDecimal.ONE.subtract(rewardPercent));

        BigDecimal effectiveBalance = BigDecimal.valueOf(Math.max(1000.0, accountBalance));
        BigDecimal positionSize = effectiveBalance.multiply(BigDecimal.valueOf(0.005))
                .divide(entryPrice.max(BigDecimal.ONE), 8, java.math.RoundingMode.HALF_UP)
                .max(BigDecimal.valueOf(0.001));

        BigDecimal riskAmount = side == Side.BUY
                ? entryPrice.subtract(stopLoss).multiply(positionSize)
                : stopLoss.subtract(entryPrice).multiply(positionSize);
        BigDecimal rewardAmount = side == Side.BUY
                ? takeProfit.subtract(entryPrice).multiply(positionSize)
                : entryPrice.subtract(takeProfit).multiply(positionSize);

        double riskRewardRatio = riskAmount.signum() > 0
                ? rewardAmount.doubleValue() / riskAmount.doubleValue()
                : 0.0;

        return new TradePlan(entryPrice, stopLoss, takeProfit, positionSize, riskAmount, rewardAmount, riskRewardRatio);
    }
}
