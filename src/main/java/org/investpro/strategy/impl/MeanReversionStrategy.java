package org.investpro.strategy.impl;

import org.investpro.strategy.StrategyCategory;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategyMetadata;
import org.investpro.strategy.StrategySignal;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.market.AssetClass;
import org.investpro.market.ContractType;
import org.investpro.timeframe.Timeframe;
import org.investpro.trading.MarketBehavior;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mean Reversion Strategy
 * Trades reversions to the mean using Bollinger Bands and RSI.
 * Best for: Range-bound markets, mean-reverting assets
 */
@Slf4j
public class MeanReversionStrategy extends BaseStrategy {

    public MeanReversionStrategy() {
        super(buildMetadata());
    }

    private static StrategyMetadata buildMetadata() {
        Set<AssetClass> assets = new HashSet<>();
        assets.add(AssetClass.CRYPTO_ASSET);
        assets.add(AssetClass.FIAT_CURRENCY);
        assets.add(AssetClass.EQUITY);

        Set<ContractType> contracts = new HashSet<>();
        contracts.add(ContractType.SPOT);

        Set<Timeframe> timeframes = new HashSet<>();
        timeframes.add(Timeframe.M15);
        timeframes.add(Timeframe.M30);
        timeframes.add(Timeframe.H1);
        timeframes.add(Timeframe.H4);

        return StrategyMetadata.builder()
                .strategyId("mean-reversion-v1")
                .displayName("Mean Reversion Strategy")
                .description("Trades mean reversions using Bollinger Bands and RSI extremes")
                .category(StrategyCategory.MEAN_REVERSION)
                .supportedAssetClasses(assets)
                .supportedContractTypes(contracts)
                .supportedTimeframes(timeframes)
                .minimumBarsRequired(80)
                .expectedHoldingPeriod("minutes-hours")
                .riskLevel(StrategyMetadata.RiskLevel.MEDIUM)
                .version("1.0.0")
                .author("InvestPro")
                .enabled(true)
                .build();
    }

    @Override
    @NotNull
    public StrategySignal generateSignal(@NotNull StrategyContext context) {
        if (!hasEnoughBars(context)) {
            return noSignal(context, "Insufficient bars");
        }

        try {
            List<CandleData> candles = context.getCandles();
            CandleData latest = candles.get(candles.size() - 1);
            double currentPrice = context.getCurrentPrice();

            // Calculate SMA(20) and Bollinger Bands
            double sma20 = calculateSMA(candles, 20);
            double stdDev = calculateStdDev(candles, 20, sma20);
            double upperBand = sma20 + (2 * stdDev);
            double lowerBand = sma20 - (2 * stdDev);

            // Calculate RSI(14)
            double rsi14 = calculateSimpleRSI(candles, 14);

            // Buy signal: Price at lower band + RSI < 30
            if (currentPrice < lowerBand && rsi14 < 30) {
                updateSignalDescription("Price at lower Bollinger Band, RSI oversold");
                return buildBuySignal(context, currentPrice, lowerBand * 0.97, sma20);
            }

            // Sell signal: Price at upper band + RSI > 70
            if (currentPrice > upperBand && rsi14 > 70) {
                updateSignalDescription("Price at upper Bollinger Band, RSI overbought");
                return buildSellSignal(context, currentPrice, upperBand * 1.03, sma20);
            }

            return noSignal(context, "Price within bands");
        } catch (Exception e) {
            log.error("Error in mean reversion signal", e);
            return noSignal(context, "Analysis error");
        }
    }

    @Override
    public boolean supportsMarketBehavior(@NotNull MarketBehavior marketBehavior) {
        // Mean reversion works in most market conditions
        return true;
    }

    private double calculateSMA(List<CandleData> candles, int period) {
        int start = Math.max(0, candles.size() - period);
        double sum = 0;
        for (int i = start; i < candles.size(); i++) {
            sum += candles.get(i).closePrice();
        }
        return sum / (candles.size() - start);
    }

    private double calculateStdDev(List<CandleData> candles, int period, double mean) {
        int start = Math.max(0, candles.size() - period);
        double sumSquares = 0;
        int count = candles.size() - start;
        for (int i = start; i < candles.size(); i++) {
            double diff = candles.get(i).closePrice() - mean;
            sumSquares += diff * diff;
        }
        return Math.sqrt(sumSquares / count);
    }

    private double calculateSimpleRSI(List<CandleData> candles, int period) {
        if (candles.size() < period + 1)
            return 50;

        double upSum = 0, downSum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            double change = candles.get(i).closePrice() - candles.get(i - 1).closePrice();
            if (change > 0)
                upSum += change;
            else
                downSum += -change;
        }

        double rs = (downSum == 0) ? 100 : upSum / downSum;
        return 100 - (100 / (1 + rs));
    }

    private StrategySignal buildBuySignal(StrategyContext context, double entry, double stopLoss, double takeProfit) {
        return StrategySignal.builder()
                .symbol(context.getSymbol())
                .timeframe(context.getTimeframe())
                .strategyId(metadata.getStrategyId())
                .side(StrategySignal.SignalSide.BUY)
                .confidence(0.60)
                .entryPrice(entry)
                .stopLoss(stopLoss)
                .takeProfit(takeProfit)
                .expectedValue(1.5)
                .reasons(List.of("Oversold condition", "Bollinger Band bounce setup"))
                .marketBehavior(context.getMarketBehavior())
                .build();
    }

    private StrategySignal buildSellSignal(StrategyContext context, double entry, double stopLoss, double takeProfit) {
        return StrategySignal.builder()
                .symbol(context.getSymbol())
                .timeframe(context.getTimeframe())
                .strategyId(metadata.getStrategyId())
                .side(StrategySignal.SignalSide.SELL)
                .confidence(0.60)
                .entryPrice(entry)
                .stopLoss(stopLoss)
                .takeProfit(takeProfit)
                .expectedValue(1.5)
                .reasons(List.of("Overbought condition", "Bollinger Band bounce setup"))
                .marketBehavior(context.getMarketBehavior())
                .build();
    }
}
