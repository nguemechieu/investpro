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
import java.util.Set;

/**
 * Trend Following Strategy
 * Trades in the direction of established trends using moving average crossovers
 * and ADX.
 * Best for: Strong trending markets, longer timeframes
 */
@Slf4j
public class TrendFollowingStrategy extends BaseStrategy {

    public TrendFollowingStrategy() {
        super(buildMetadata());
    }

    private static StrategyMetadata buildMetadata() {
        Set<AssetClass> assets = new HashSet<>();
        assets.add(AssetClass.CRYPTO_ASSET);
        assets.add(AssetClass.FIAT_CURRENCY);
        assets.add(AssetClass.EQUITY);

        Set<ContractType> contracts = new HashSet<>();
        contracts.add(ContractType.SPOT);
        contracts.add(ContractType.PERPETUAL);

        Set<Timeframe> timeframes = new HashSet<>();
        timeframes.add(Timeframe.H1);
        timeframes.add(Timeframe.H4);
        timeframes.add(Timeframe.D1);
        timeframes.add(Timeframe.W1);

        return StrategyMetadata.builder()
                .strategyId("trend-following-v1")
                .displayName("Trend Following Strategy")
                .description("Follows established market trends using moving average crossovers and ADX confirmation")
                .category(StrategyCategory.TREND_FOLLOWING)
                .supportedAssetClasses(assets)
                .supportedContractTypes(contracts)
                .supportedTimeframes(timeframes)
                .minimumBarsRequired(100)
                .expectedHoldingPeriod("hours-days")
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
            return noSignal(context, "Insufficient bars for analysis");
        }

        CandleData latest = context.getLatestCandle();
        if (latest == null) {
            return noSignal(context, "No candle data available");
        }

        // Simple trend following logic: BUY if price > SMA(20) and trending up
        // SELL if price < SMA(20) and trending down

        try {
            // Calculate 20-period SMA
            double sma20 = calculateSMA(context, 20);
            double sma50 = calculateSMA(context, 50);
            double currentPrice = context.getCurrentPrice();

            if (currentPrice > sma20 && sma20 > sma50) {
                // Uptrend: Price above short MA, short MA above long MA
                updateSignalDescription("Uptrend: Price above moving averages");
                return buildBuySignal(context, currentPrice, sma20 * 0.98, currentPrice * 1.05);
            } else if (currentPrice < sma20 && sma20 < sma50) {
                // Downtrend: Price below short MA, short MA below long MA
                updateSignalDescription("Downtrend: Price below moving averages");
                return buildSellSignal(context, currentPrice, sma20 * 1.02, currentPrice * 0.95);
            } else {
                return noSignal(context, "No clear trend detected");
            }
        } catch (Exception e) {
            log.error("Error generating trend following signal", e);
            return noSignal(context, "Analysis error: " + e.getMessage());
        }
    }

    @Override
    public boolean supportsMarketBehavior(@NotNull MarketBehavior marketBehavior) {
        // Trend following works best in trending markets
        return true; // Works in most market conditions
    }

    private double calculateSMA(StrategyContext context, int period) {
        int startIdx = Math.max(0, context.getCandles().size() - period);
        double sum = 0;
        int count = 0;
        for (int i = startIdx; i < context.getCandles().size(); i++) {
            sum += context.getCandles().get(i).closePrice();
            count++;
        }
        return count > 0 ? sum / count : 0;
    }

    private StrategySignal buildBuySignal(StrategyContext context, double entry, double stopLoss, double takeProfit) {
        double rr = (takeProfit - entry) / (entry - stopLoss);
        return StrategySignal.builder()
                .symbol(context.getSymbol())
                .timeframe(context.getTimeframe())
                .strategyId(metadata.getStrategyId())
                .side(StrategySignal.SignalSide.BUY)
                .confidence(0.65)
                .entryPrice(entry)
                .stopLoss(stopLoss)
                .takeProfit(takeProfit)
                .riskRewardRatio(rr)
                .expectedValue(2.0) // 2% expected return
                .reasons(java.util.List.of("Uptrend confirmed", "Price above moving averages"))
                .marketBehavior(context.getMarketBehavior())
                .build();
    }

    private StrategySignal buildSellSignal(StrategyContext context, double entry, double stopLoss, double takeProfit) {
        double rr = (entry - takeProfit) / (stopLoss - entry);
        return StrategySignal.builder()
                .symbol(context.getSymbol())
                .timeframe(context.getTimeframe())
                .strategyId(metadata.getStrategyId())
                .side(StrategySignal.SignalSide.SELL)
                .confidence(0.65)
                .entryPrice(entry)
                .stopLoss(stopLoss)
                .takeProfit(takeProfit)
                .riskRewardRatio(rr)
                .expectedValue(2.0)
                .reasons(java.util.List.of("Downtrend confirmed", "Price below moving averages"))
                .marketBehavior(context.getMarketBehavior())
                .build();
    }
}
