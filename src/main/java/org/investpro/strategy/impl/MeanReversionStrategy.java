package org.investpro.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.StrategyCategory;
import org.investpro.enums.AssetClass;
import org.investpro.enums.ContractType;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategyMetadata;
import org.investpro.strategy.StrategySignal;
import org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.investpro.utils.Side.BUY;
import static org.investpro.utils.Side.SELL;

/**
 * Mean Reversion Strategy.
 *
 * Trades reversions back toward the mean using Bollinger Bands and RSI.
 *
 * Best for:
 * - range-bound markets
 * - low-volatility consolidation
 * - temporary overbought/oversold extremes
 */
@Slf4j
public class MeanReversionStrategy extends BaseStrategy {

    public static final String STRATEGY_ID = "mean-reversion";

    private static final int BOLLINGER_PERIOD = 20;
    private static final double BOLLINGER_STD_DEV_MULTIPLIER = 2.0;

    private static final int RSI_PERIOD = 14;
    private static final double OVERSOLD_RSI = 30.0;
    private static final double OVERBOUGHT_RSI = 70.0;

    private static final int MINIMUM_BARS_REQUIRED = 80;

    private static final double STOP_LOSS_BUFFER = 0.03;

    public MeanReversionStrategy() {
        super(buildMetadata());
    }

    private static StrategyMetadata buildMetadata() {
        Set<AssetClass> assets = EnumSet.of(
                AssetClass.CRYPTO_ASSET,
                AssetClass.FIAT_CURRENCY,
                AssetClass.EQUITY
        );

        Set<ContractType> contracts = EnumSet.of(
                ContractType.SPOT
        );

        Set<Timeframe> timeframes = EnumSet.of(
                Timeframe.M15,
                Timeframe.M30,
                Timeframe.H1,
                Timeframe.H4
        );

        return StrategyMetadata.builder()
                .strategyId(STRATEGY_ID)
                .displayName("Mean Reversion Strategy")
                .description("Trades mean reversions using Bollinger Bands and RSI extremes.")
                .category(StrategyCategory.MEAN_REVERSION)
                .supportedAssetClasses(assets)
                .supportedContractTypes(contracts)
                .supportedTimeframes(timeframes)
                .minimumBarsRequired(MINIMUM_BARS_REQUIRED)
                .expectedHoldingPeriod("minutes-hours")
                .riskLevel(StrategyMetadata.RiskLevel.MEDIUM)
                .version("1.0.0")
                .author("InvestPro")
                .enabled(true)
                .build();
    }

    @Override
    public StrategySignal generateSignal(@NotNull StrategyContext context) {
        if (hasEnoughBars(context)) {
            return noSignal(context, "Insufficient bars for mean-reversion analysis");
        }

        if (context.getCandles() == null || context.getCandles().isEmpty()) {
            return noSignal(context, "No candle data available");
        }

        try {
            List<CandleData> candles = context.getCandles();
            CandleData latest = candles.get(candles.size() - 1);

            if (latest == null) {
                return noSignal(context, "Latest candle is missing");
            }

            double currentPrice = resolveCurrentPrice(context, latest);

            if (currentPrice <= 0) {
                return noSignal(context, "Invalid current price");
            }

            double sma20 = calculateSMA(candles, BOLLINGER_PERIOD);
            double stdDev = calculateStdDev(candles, BOLLINGER_PERIOD, sma20);

            if (sma20 <= 0 || stdDev <= 0) {
                return noSignal(context, "Unable to calculate Bollinger Bands");
            }

            double upperBand = sma20 + (BOLLINGER_STD_DEV_MULTIPLIER * stdDev);
            double lowerBand = sma20 - (BOLLINGER_STD_DEV_MULTIPLIER * stdDev);

            double rsi14 = calculateSimpleRSI(candles, RSI_PERIOD);

            boolean oversoldAtLowerBand = currentPrice < lowerBand && rsi14 < OVERSOLD_RSI;
            boolean overboughtAtUpperBand = currentPrice > upperBand && rsi14 > OVERBOUGHT_RSI;

            if (oversoldAtLowerBand) {
                double stopLoss = lowerBand * (1.0 - STOP_LOSS_BUFFER);
                double takeProfit = sma20;

                updateSignalDescription(
                        "Mean-reversion BUY: price below lower Bollinger Band and RSI oversold. " +
                                "price=" + currentPrice +
                                ", lowerBand=" + lowerBand +
                                ", sma20=" + sma20 +
                                ", rsi14=" + rsi14
                );

                return buildBuySignal(context, currentPrice, stopLoss, takeProfit);
            }

            if (overboughtAtUpperBand) {
                double stopLoss = upperBand * (1.0 + STOP_LOSS_BUFFER);
                double takeProfit = sma20;

                updateSignalDescription(
                        "Mean-reversion SELL: price above upper Bollinger Band and RSI overbought. " +
                                "price=" + currentPrice +
                                ", upperBand=" + upperBand +
                                ", sma20=" + sma20 +
                                ", rsi14=" + rsi14
                );

                return buildSellSignal(context, currentPrice, stopLoss, takeProfit);
            }

            return noSignal(
                    context,
                    "No mean-reversion setup: price=" + currentPrice +
                            ", lowerBand=" + lowerBand +
                            ", upperBand=" + upperBand +
                            ", rsi14=" + rsi14
            );

        } catch (Exception exception) {
            log.error(
                    "Error generating mean-reversion signal for symbol={}",
                    context.getSymbol(),
                    exception
            );

            return noSignal(context, "Mean-reversion analysis error: " + exception.getMessage());
        }
    }

    @Override
    public boolean supportsMarketBehavior(@NotNull MarketBehavior marketBehavior) {
        return marketBehavior == MarketBehavior.RANGING
                || marketBehavior == MarketBehavior.LOW_VOLATILITY
                || marketBehavior == MarketBehavior.REVERSAL;
    }

    @Override
    public Object getId() {
        return STRATEGY_ID ;
    }

    private double calculateSMA(@NotNull List<CandleData> candles, int period) {
        if (candles.size() < period) {
            return 0.0;
        }

        int start = candles.size() - period;
        double sum = 0.0;

        for (int i = start; i < candles.size(); i++) {
            CandleData candle = candles.get(i);

            if (candle == null || candle.closePrice() <= 0) {
                return 0.0;
            }

            sum += candle.closePrice();
        }

        return sum / period;
    }

    private double calculateStdDev(@NotNull List<CandleData> candles, int period, double mean) {
        if (candles.size() < period || mean <= 0) {
            return 0.0;
        }

        int start = candles.size() - period;
        double sumSquares = 0.0;

        for (int i = start; i < candles.size(); i++) {
            CandleData candle = candles.get(i);

            if (candle == null || candle.closePrice() <= 0) {
                return 0.0;
            }

            double diff = candle.closePrice() - mean;
            sumSquares += diff * diff;
        }

        return Math.sqrt(sumSquares / period);
    }

    private double calculateSimpleRSI(@NotNull List<CandleData> candles, int period) {
        if (candles.size() < period + 1) {
            return 50.0;
        }

        double gainSum = 0.0;
        double lossSum = 0.0;

        int start = candles.size() - period;

        for (int i = start; i < candles.size(); i++) {
            CandleData current = candles.get(i);
            CandleData previous = candles.get(i - 1);

            if (current == null || previous == null) {
                return 50.0;
            }

            double change = current.closePrice() - previous.closePrice();

            if (change > 0) {
                gainSum += change;
            } else if (change < 0) {
                lossSum += Math.abs(change);
            }
        }

        if (lossSum == 0.0) {
            return 100.0;
        }

        double relativeStrength = gainSum / lossSum;
        return 100.0 - (100.0 / (1.0 + relativeStrength));
    }

    private double resolveCurrentPrice(@NotNull StrategyContext context, @NotNull CandleData latest) {
        double currentPrice = context.getCurrentPrice();

        if (currentPrice > 0) {
            return currentPrice;
        }

        return latest.closePrice();
    }

    private StrategySignal buildBuySignal(
            @NotNull StrategyContext context,
            double entry,
            double stopLoss,
            double takeProfit
    ) {
        double confidence = calculateConfidence(entry, stopLoss, takeProfit);

        return StrategySignal.builder()
                .strategyId(STRATEGY_ID)
                .symbol(context.getSymbol().toString('/'))
                .timeframe(context.getTimeframe().toString())
                .side(BUY)
                .confidence(confidence)
                .entryPrice(entry)
                .stopLossPrice(stopLoss)
                .takeProfitPrice(takeProfit)
                .riskRewardRatio(calculateRiskRewardRatio(entry, stopLoss, takeProfit))
                .sessionStatus(context.getTradingSessionStatus())
                .sessionNotes(context.getTradingSession() == null ? null : context.getTradingSession().getNotes())
                .reason("Oversold mean-reversion setup: price below lower Bollinger Band with RSI below "
                        + OVERSOLD_RSI)

                .build();
    }

    private StrategySignal buildSellSignal(
            @NotNull StrategyContext context,
            double entry,
            double stopLoss,
            double takeProfit
    ) {
        double confidence = calculateConfidence(entry, stopLoss, takeProfit);

        return StrategySignal.builder()
                .strategyId(STRATEGY_ID)
                .symbol(context.getSymbol().toString('/'))
                .timeframe(context.getTimeframe().toString())
                .side(SELL)
                .confidence(confidence)
                .entryPrice(entry)
                .stopLossPrice(stopLoss)
                .takeProfitPrice(takeProfit)
                .riskRewardRatio(calculateRiskRewardRatio(entry, stopLoss, takeProfit))
                .sessionStatus(context.getTradingSessionStatus())
                .sessionNotes(context.getTradingSession() == null ? null : context.getTradingSession().getNotes())
                .reason("Overbought mean-reversion setup: price above upper Bollinger Band with RSI above "
                        + OVERBOUGHT_RSI)

                .build();
    }

    private double calculateRiskRewardRatio(double entry, double stopLoss, double takeProfit) {
        double risk = Math.abs(entry - stopLoss);
        double reward = Math.abs(takeProfit - entry);

        if (risk <= 0) {
            return 0.0;
        }

        return reward / risk;
    }

    private double calculateConfidence(double entry, double stopLoss, double takeProfit) {
        double riskRewardRatio = calculateRiskRewardRatio(entry, stopLoss, takeProfit);

        if (riskRewardRatio >= 2.0) {
            return 0.75;
        }

        if (riskRewardRatio >= 1.5) {
            return 0.65;
        }

        if (riskRewardRatio >= 1.0) {
            return 0.55;
        }

        return 0.45;
    }
}
