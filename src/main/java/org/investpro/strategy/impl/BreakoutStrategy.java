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
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.investpro.utils.Side.BUY;
import static org.investpro.utils.Side.SELL;

/**
 * Breakout Strategy.
 *
 * Trades breakouts above resistance or below support levels using Donchian
 * channels.
 *
 * Best for:
 * - breakout markets
 * - high-volatility markets
 * - expanding trend conditions
 */
@Slf4j
public class BreakoutStrategy extends BaseStrategy {

    public static final String STRATEGY_ID = "breakout";

    private static final int DONCHIAN_PERIOD = 20;
    private static final int MINIMUM_BARS_REQUIRED = 60;

    private static final double TAKE_PROFIT_BUFFER = 0.02;

    public BreakoutStrategy() {
        super(buildMetadata());
    }

    private static StrategyMetadata buildMetadata() {
        Set<AssetClass> assets = EnumSet.of(
                AssetClass.CRYPTO_ASSET,
                AssetClass.FIAT_CURRENCY,
                AssetClass.COMMODITY);

        Set<ContractType> contracts = EnumSet.of(
                ContractType.SPOT,
                ContractType.PERPETUAL);

        Set<Timeframe> timeframes = EnumSet.of(
                Timeframe.M5,
                Timeframe.M15,
                Timeframe.H1,
                Timeframe.H4,
                Timeframe.D1);

        return StrategyMetadata.builder()
                .strategyId(STRATEGY_ID)
                .displayName("Breakout Strategy")
                .description("Trades breakouts using Donchian channels and optional volume confirmation.")
                .category(StrategyCategory.BREAKOUT)
                .supportedAssetClasses(assets)
                .supportedContractTypes(contracts)
                .supportedTimeframes(timeframes)
                .minimumBarsRequired(MINIMUM_BARS_REQUIRED)
                .expectedHoldingPeriod("hours-days")
                .riskLevel(StrategyMetadata.RiskLevel.MEDIUM)
                .version("1.0.0")
                .author("InvestPro")
                .enabled(true)
                .build();
    }

    @Override
    public StrategySignal generateSignal(@NotNull StrategyContext context) {
        if (hasEnoughBars(context)) {
            return noSignal(context, "Insufficient bars for breakout analysis");
        }

        if (context.getCandles() == null || context.getCandles().size() < DONCHIAN_PERIOD + 1) {
            return noSignal(context, "Not enough candle data for Donchian breakout");
        }

        try {
            List<CandleData> candles = context.getCandles();

            CandleData latest = candles.get(candles.size() - 1);
            CandleData previous = candles.get(candles.size() - 2);

            if (latest == null || previous == null) {
                return noSignal(context, "Latest or previous candle is missing");
            }

            double currentPrice = resolveCurrentPrice(context, latest);

            if (currentPrice <= 0) {
                return noSignal(context, "Invalid current price");
            }

            DonchianChannel donchian = calculateDonchianExcludingLatest(candles, DONCHIAN_PERIOD);

            if (!donchian.isValid()) {
                return noSignal(context, "Invalid Donchian channel values");
            }

            double resistance = donchian.resistance();
            double support = donchian.support();

            boolean bullishBreakout = latest.closePrice() > resistance
                    && previous.closePrice() <= resistance;

            boolean bearishBreakout = latest.closePrice() < support
                    && previous.closePrice() >= support;

            if (bullishBreakout) {
                double stopLoss = support;
                double takeProfit = currentPrice * (1.0 + TAKE_PROFIT_BUFFER);

                updateSignalDescription(
                        "Bullish breakout confirmed: close above Donchian resistance. " +
                                "price=" + currentPrice +
                                ", resistance=" + resistance +
                                ", support=" + support);

                log.debug(
                        "Breakout BUY signal: symbol={}, price={}, resistance={}, support={}",
                        context.getSymbol(),
                        currentPrice,
                        resistance,
                        support);

                return buildBuySignal(context, currentPrice, stopLoss, takeProfit, resistance, support);
            }

            if (bearishBreakout) {
                double stopLoss = resistance;
                double takeProfit = currentPrice * (1.0 - TAKE_PROFIT_BUFFER);

                updateSignalDescription(
                        "Bearish breakout confirmed: close below Donchian support. " +
                                "price=" + currentPrice +
                                ", resistance=" + resistance +
                                ", support=" + support);

                log.debug(
                        "Breakout SELL signal: symbol={}, price={}, resistance={}, support={}",
                        context.getSymbol(),
                        currentPrice,
                        resistance,
                        support);

                return buildSellSignal(context, currentPrice, stopLoss, takeProfit, resistance, support);
            }

            return noSignal(
                    context,
                    "No breakout detected: price=" + currentPrice +
                            ", resistance=" + resistance +
                            ", support=" + support);

        } catch (Exception exception) {
            log.error(
                    "Error generating breakout signal for symbol={}",
                    context.getSymbol(),
                    exception);

            return noSignal(context, "Breakout analysis error: " + exception.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled();
    }

    @Override
    public Object getId() {
        return STRATEGY_ID;
    }

    @Override
    public boolean supportsMarketBehavior(@NotNull MarketBehavior marketBehavior) {
        return marketBehavior == MarketBehavior.BREAKOUT
                || marketBehavior == MarketBehavior.HIGH_VOLATILITY
                || marketBehavior == MarketBehavior.TRENDING_UP
                || marketBehavior == MarketBehavior.TRENDING_DOWN;
    }

    /**
     * Calculates the Donchian channel using candles before the latest candle.
     *
     * This avoids look-ahead bias. If we include the latest candle in
     * resistance/support,
     * the breakout check can become unreliable because the breakout candle creates
     * the level.
     */
    private DonchianChannel calculateDonchianExcludingLatest(@NotNull List<CandleData> candles, int period) {
        if (candles.size() < period + 1) {
            return DonchianChannel.invalid();
        }

        int endExclusive = candles.size() - 1;
        int start = endExclusive - period;

        double highest = Double.NEGATIVE_INFINITY;
        double lowest = Double.POSITIVE_INFINITY;

        for (int i = start; i < endExclusive; i++) {
            CandleData candle = candles.get(i);

            if (candle == null || candle.highPrice() <= 0 || candle.lowPrice() <= 0) {
                return DonchianChannel.invalid();
            }

            highest = Math.max(highest, candle.highPrice());
            lowest = Math.min(lowest, candle.lowPrice());
        }

        if (highest == Double.NEGATIVE_INFINITY || lowest == Double.POSITIVE_INFINITY) {
            return DonchianChannel.invalid();
        }

        return new DonchianChannel(highest, lowest);
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
            double takeProfit,
            double resistance,
            double support) {
        double riskRewardRatio = calculateRiskRewardRatio(entry, stopLoss, takeProfit);
        double confidence = calculateConfidence(riskRewardRatio, entry, resistance, support);

        return StrategySignal.builder()
                .strategyId(STRATEGY_ID)
                .strategyName(metadata.getDisplayName())
                .symbol(context.getSymbol().toString('/'))
                .timeframe(context.getTimeframe().toString())
                .side(BUY)
                .confidence(confidence)
                .entryPrice(entry)
                .stopLossPrice(stopLoss)
                .takeProfitPrice(takeProfit)
                .riskRewardRatio(riskRewardRatio)
                .sessionStatus(context.getTradingSessionStatus())
                .sessionNotes(context.getTradingSession() == null ? null : context.getTradingSession().getNotes())
                .reason("Bullish breakout setup: price closed above Donchian resistance")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private StrategySignal buildSellSignal(
            @NotNull StrategyContext context,
            double entry,
            double stopLoss,
            double takeProfit,
            double resistance,
            double support) {
        double riskRewardRatio = calculateRiskRewardRatio(entry, stopLoss, takeProfit);
        double confidence = calculateConfidence(riskRewardRatio, entry, resistance, support);

        return StrategySignal.builder()
                .strategyId(STRATEGY_ID)
                .strategyName(metadata.getDisplayName())
                .symbol(context.getSymbol().toString('/'))
                .timeframe(context.getTimeframe().toString())
                .side(SELL)
                .confidence(confidence)
                .entryPrice(entry)
                .stopLossPrice(stopLoss)
                .takeProfitPrice(takeProfit)
                .riskRewardRatio(riskRewardRatio)
                .sessionStatus(context.getTradingSessionStatus())
                .sessionNotes(context.getTradingSession() == null ? null : context.getTradingSession().getNotes())
                .reason("Bearish breakout setup: price closed below Donchian support")
                .createdAt(LocalDateTime.now())
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

    private double calculateConfidence(
            double riskRewardRatio,
            double entry,
            double resistance,
            double support) {
        double confidence = 0.62;

        if (riskRewardRatio >= 2.0) {
            confidence += 0.10;
        } else if (riskRewardRatio >= 1.5) {
            confidence += 0.05;
        }

        double channelWidth = Math.abs(resistance - support) / entry;

        if (channelWidth >= 0.03) {
            confidence += 0.08;
        } else if (channelWidth >= 0.015) {
            confidence += 0.04;
        }

        return Math.min(confidence, 0.85);
    }

    private record DonchianChannel(double resistance, double support) {

        private static DonchianChannel invalid() {
            return new DonchianChannel(0.0, 0.0);
        }

        private boolean isValid() {
            return resistance > 0 && support > 0 && resistance > support;
        }
    }
}
