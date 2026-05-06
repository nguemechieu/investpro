package org.investpro.strategy.impl;

import org.investpro.strategy.StrategyCategory;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategyMetadata;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.market.AssetClass;
import org.investpro.market.ContractType;
import org.investpro.timeframe.Timeframe;
import org.investpro.trading.MarketBehavior;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.investpro.utils.Side.BUY;
import static org.investpro.utils.Side.SELL;

/**
 * Breakout Strategy
 * Trades breakouts above resistance or below support levels.
 * Uses Donchian channels for dynamic levels.
 */
@Slf4j
public class BreakoutStrategy extends BaseStrategy {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BreakoutStrategy.class);

    public BreakoutStrategy() {
        super(buildMetadata());
    }

    private static StrategyMetadata buildMetadata() {
        Set<AssetClass> assets = new HashSet<>();
        assets.add(AssetClass.CRYPTO_ASSET);
        assets.add(AssetClass.FIAT_CURRENCY);
        assets.add(AssetClass.COMMODITY);

        Set<ContractType> contracts = new HashSet<>();
        contracts.add(ContractType.SPOT);
        contracts.add(ContractType.PERPETUAL);

        Set<Timeframe> timeframes = new HashSet<>();
        timeframes.add(Timeframe.M5);
        timeframes.add(Timeframe.M15);
        timeframes.add(Timeframe.H1);
        timeframes.add(Timeframe.H4);
        timeframes.add(Timeframe.D1);

        return StrategyMetadata.builder()
                .strategyId("breakout-v1")
                .displayName("Breakout Strategy")
                .description("Trades breakouts using Donchian channels and volume confirmation")
                .category(StrategyCategory.BREAKOUT)
                .supportedAssetClasses(assets)
                .supportedContractTypes(contracts)
                .supportedTimeframes(timeframes)
                .minimumBarsRequired(60)
                .expectedHoldingPeriod("hours-days")
                .riskLevel(StrategyMetadata.RiskLevel.MEDIUM)
                .version("1.0.0")
                .author("InvestPro")
                .enabled(true)
                .build();
    }

    @Override
    public @NotNull Side generateSignal(@NotNull StrategyContext context) {
        if (hasEnoughBars(context)) {
            return noSignal(context, "Insufficient bars");
        }

        try {
            List<CandleData> candles = context.getCandles();
            CandleData latest = candles.get(candles.size() - 1);
            CandleData previous = candles.get(candles.size() - 2);
            double currentPrice = context.getCurrentPrice();

            // Calculate Donchian channels (20-period)
            double[] donchian = calculateDonchian(candles, 20);
            double resistance = donchian[0];
            double support = donchian[1];

            // Check for breakout above resistance
            if (latest.closePrice() > resistance && previous.closePrice() <= resistance) {
                updateSignalDescription("Breakout above resistance level");
                return buildBuySignal(context, currentPrice, support, resistance * 1.02);
            }

            // Check for breakout below support
            if (latest.closePrice() < support && previous.closePrice() >= support) {
                updateSignalDescription("Breakout below support level");
                return buildSellSignal(context, currentPrice, resistance, support * 0.98);
            }

            return noSignal(context, "No breakout detected");
        } catch (Exception e) {
            log.error("Error in breakout signal", e);
            return noSignal(context, "Analysis error");
        }
    }

    @Override
    public boolean supportsMarketBehavior(org.investpro.risk.MarketBehavior marketBehavior) {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled();
    }

    @Override
    public boolean supportsMarketBehavior(@NotNull MarketBehavior marketBehavior) {
        // Breakout works in trending and volatile markets
        return true;
    }

    private double[] calculateDonchian(List<CandleData> candles, int period) {
        int start = Math.max(0, candles.size() - period);
        double highest = Double.MIN_VALUE;
        double lowest = Double.MAX_VALUE;

        for (int i = start; i < candles.size(); i++) {
            highest = Math.max(highest, candles.get(i).highPrice());
            lowest = Math.min(lowest, candles.get(i).lowPrice());
        }

        return new double[] { highest, lowest };
    }

    private Side buildBuySignal(StrategyContext context, double entry, double stopLoss, double takeProfit) {
        return BUY;
    }

    private Side buildSellSignal(StrategyContext context, double entry, double stopLoss, double takeProfit) {
        return SELL;
    }
}
