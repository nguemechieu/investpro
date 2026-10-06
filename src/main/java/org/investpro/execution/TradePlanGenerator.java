package org.investpro.execution;

import org.investpro.decision.TradePlan;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Generates deterministic fallback trade plans from ticker, side, signal strength,
 * account balance, and trade pair context.
 */
public class TradePlanGenerator {

    private static final BigDecimal MIN_EFFECTIVE_BALANCE = BigDecimal.valueOf(1000.0);

    private static final BigDecimal BASE_RISK_PERCENT = BigDecimal.valueOf(0.01);      // 1%
    private static final BigDecimal BASE_REWARD_PERCENT = BigDecimal.valueOf(0.02);    // 2%
    private static final BigDecimal BASE_POSITION_FRACTION = BigDecimal.valueOf(0.005); // 0.5%

    private static final BigDecimal MIN_POSITION_SIZE = BigDecimal.valueOf(0.001);
    private static final BigDecimal MAX_POSITION_FRACTION = BigDecimal.valueOf(0.02);   // 2%

    private static final int PRICE_SCALE = 8;
    private static final int SIZE_SCALE = 8;

    @NotNull
    public TradePlan generate(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull Ticker ticker,
            double signalStrength,
            double accountBalance
    ) {
        validateInputs(tradePair, side, ticker, accountBalance);

        double confidence = normalizeSignalStrength(signalStrength);

        BigDecimal entryPrice = BigDecimal.valueOf(resolveEntryPrice(side, ticker))
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        BigDecimal effectiveBalance = BigDecimal.valueOf(Math.max(accountBalance, MIN_EFFECTIVE_BALANCE.doubleValue()));

        /*
         * Signal strength now matters:
         *
         * weak signal   -> smaller position, normal risk
         * strong signal -> larger position, slightly wider reward target
         */
        BigDecimal confidenceMultiplier = BigDecimal.valueOf(confidence);

        BigDecimal riskPercent = BASE_RISK_PERCENT
                .multiply(BigDecimal.valueOf(0.75 + (confidence * 0.50)))
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        BigDecimal rewardPercent = BASE_REWARD_PERCENT
                .multiply(BigDecimal.valueOf(0.75 + (confidence * 0.75)))
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        BigDecimal positionFraction = BASE_POSITION_FRACTION
                .multiply(confidenceMultiplier)
                .min(MAX_POSITION_FRACTION)
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);

        BigDecimal stopLoss = calculateStopLoss(side, entryPrice, riskPercent);
        BigDecimal takeProfit = calculateTakeProfit(side, entryPrice, rewardPercent);

        BigDecimal positionSize = calculatePositionSize(
                effectiveBalance,
                positionFraction,
                entryPrice
        );

        BigDecimal riskAmount = calculateRiskAmount(side, entryPrice, stopLoss, positionSize);
        BigDecimal rewardAmount = calculateRewardAmount(side, entryPrice, takeProfit, positionSize);

        double riskRewardRatio = riskAmount.signum() > 0
                ? rewardAmount.divide(riskAmount, 6, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        return new TradePlan(
                entryPrice,
                stopLoss,
                takeProfit,
                positionSize,
                riskAmount,
                rewardAmount,
                riskRewardRatio
        );
    }

    private void validateInputs(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull Ticker ticker,
            double accountBalance
    ) {
        if (tradePair.toString().isBlank()) {
            throw new IllegalArgumentException("Trade pair cannot be blank.");
        }

        if (side != Side.BUY && side != Side.SELL) {
            throw new IllegalArgumentException("Unsupported trade side for " + tradePair + ": " + side);
        }

        if (!Double.isFinite(accountBalance)) {
            throw new IllegalArgumentException("Account balance must be finite for " + tradePair);
        }

        double bid = ticker.getBidPrice();
        double ask = ticker.getAskPrice();

        boolean hasValidBid = Double.isFinite(bid) && bid > 0.0;
        boolean hasValidAsk = Double.isFinite(ask) && ask > 0.0;

        if (!hasValidBid && !hasValidAsk) {
            throw new IllegalArgumentException(
                    "Cannot generate trade plan for " + tradePair + ": ticker has no valid bid/ask price."
            );
        }
    }

    private double normalizeSignalStrength(double signalStrength) {
        if (!Double.isFinite(signalStrength)) {
            return 0.25;
        }

        /*
         * Supports both:
         * 0.0 - 1.0 confidence scale
         * 0.0 - 100.0 percentage scale
         */
        double normalized = signalStrength > 1.0
                ? signalStrength / 100.0
                : signalStrength;

        return Math.clamp(normalized, 0.10, 1.0);
    }

    private double resolveEntryPrice(@NotNull Side side, @NotNull Ticker ticker) {
        double bid = ticker.getBidPrice();
        double ask = ticker.getAskPrice();

        boolean bidValid = Double.isFinite(bid) && bid > 0.0;
        boolean askValid = Double.isFinite(ask) && ask > 0.0;

        if (side == Side.BUY && askValid) {
            return ask;
        }

        if (side == Side.SELL && bidValid) {
            return bid;
        }

        if (bidValid && askValid) {
            return (bid + ask) / 2.0;
        }

        if (bidValid) {
            return bid;
        }

        return ask;
    }

    private BigDecimal calculateStopLoss(
            @NotNull Side side,
            @NotNull BigDecimal entryPrice,
            @NotNull BigDecimal riskPercent
    ) {
        BigDecimal stopLoss = side == Side.BUY
                ? entryPrice.multiply(BigDecimal.ONE.subtract(riskPercent))
                : entryPrice.multiply(BigDecimal.ONE.add(riskPercent));

        return stopLoss.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTakeProfit(
            @NotNull Side side,
            @NotNull BigDecimal entryPrice,
            @NotNull BigDecimal rewardPercent
    ) {
        BigDecimal takeProfit = side == Side.BUY
                ? entryPrice.multiply(BigDecimal.ONE.add(rewardPercent))
                : entryPrice.multiply(BigDecimal.ONE.subtract(rewardPercent));

        return takeProfit.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePositionSize(
            @NotNull BigDecimal effectiveBalance,
            @NotNull BigDecimal positionFraction,
            @NotNull BigDecimal entryPrice
    ) {
        BigDecimal capitalToUse = effectiveBalance.multiply(positionFraction);

        BigDecimal positionSize = capitalToUse.divide(
                entryPrice.max(BigDecimal.ONE),
                SIZE_SCALE,
                RoundingMode.HALF_UP
        );

        return positionSize.max(MIN_POSITION_SIZE);
    }

    private BigDecimal calculateRiskAmount(
            @NotNull Side side,
            @NotNull BigDecimal entryPrice,
            @NotNull BigDecimal stopLoss,
            @NotNull BigDecimal positionSize
    ) {
        BigDecimal riskAmount = side == Side.BUY
                ? entryPrice.subtract(stopLoss).multiply(positionSize)
                : stopLoss.subtract(entryPrice).multiply(positionSize);

        return riskAmount.abs().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRewardAmount(
            @NotNull Side side,
            @NotNull BigDecimal entryPrice,
            @NotNull BigDecimal takeProfit,
            @NotNull BigDecimal positionSize
    ) {
        BigDecimal rewardAmount = side == Side.BUY
                ? takeProfit.subtract(entryPrice).multiply(positionSize)
                : entryPrice.subtract(takeProfit).multiply(positionSize);

        return rewardAmount.abs().setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}