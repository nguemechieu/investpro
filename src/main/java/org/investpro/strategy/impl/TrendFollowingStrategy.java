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
 * Trend Following Strategy.
 *
 * Trades in the direction of established trends using SMA20/SMA50 alignment.
 *
 * Best for:
 * - trending markets
 * - H1/H4/D1/W1 timeframes
 * - crypto, forex, equities
 */
@Slf4j
public class TrendFollowingStrategy extends BaseStrategy {

    public static final String STRATEGY_ID = "trend-following";

    private static final int FAST_SMA_PERIOD = 20;
    private static final int SLOW_SMA_PERIOD = 50;
    private static final int MINIMUM_BARS_REQUIRED = 100;

    private static final double STOP_LOSS_PERCENT = 0.02;
    private static final double TAKE_PROFIT_PERCENT = 0.05;

    public TrendFollowingStrategy() {
        super(buildMetadata());
    }

    private static StrategyMetadata buildMetadata() {
        Set<AssetClass> assets = EnumSet.of(
                AssetClass.CRYPTO_ASSET,
                AssetClass.FIAT_CURRENCY,
                AssetClass.EQUITY);

        Set<ContractType> contracts = EnumSet.of(
                ContractType.SPOT,
                ContractType.PERPETUAL);

        Set<Timeframe> timeframes = EnumSet.of(
                Timeframe.H1,
                Timeframe.H4,
                Timeframe.D1,
                Timeframe.W1);

        return StrategyMetadata.builder()
                .strategyId(STRATEGY_ID)
                .displayName("Trend Following Strategy")
                .description("Follows established market trends using SMA20/SMA50 alignment.")
                .category(StrategyCategory.TREND_FOLLOWING)
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
            return noSignal(context, "Insufficient bars for trend analysis");
        }

        CandleData latest = context.getLatestCandle();
        if (latest == null) {
            return noSignal(context, "No candle data available");
        }

        try {
            double currentPrice = resolveCurrentPrice(context, latest);

            if (currentPrice <= 0) {
                return noSignal(context, "Invalid current price");
            }

            double sma20 = calculateSMA(context.getCandles(), FAST_SMA_PERIOD);
            double sma50 = calculateSMA(context.getCandles(), SLOW_SMA_PERIOD);

            if (sma20 <= 0 || sma50 <= 0) {
                return noSignal(context, "Unable to calculate moving averages");
            }

            boolean bullishTrend = currentPrice > sma20 && sma20 > sma50;
            boolean bearishTrend = currentPrice < sma20 && sma20 < sma50;

            if (bullishTrend) {
                double stopLoss = currentPrice * (1.0 - STOP_LOSS_PERCENT);
                double takeProfit = currentPrice * (1.0 + TAKE_PROFIT_PERCENT);

                updateSignalDescription(
                        "Bullish trend confirmed: price > SMA20 > SMA50. " +
                                "Entry=" + currentPrice +
                                ", SL=" + stopLoss +
                                ", TP=" + takeProfit);

                log.debug(
                        "Trend-following BUY signal: symbol={}, price={}, sma20={}, sma50={}",
                        context.getSymbol(),
                        currentPrice,
                        sma20,
                        sma50);

                return buildBuySignal(context, currentPrice, stopLoss, takeProfit, sma20, sma50);
            }

            if (bearishTrend) {
                double stopLoss = currentPrice * (1.0 + STOP_LOSS_PERCENT);
                double takeProfit = currentPrice * (1.0 - TAKE_PROFIT_PERCENT);

                updateSignalDescription(
                        "Bearish trend confirmed: price < SMA20 < SMA50. " +
                                "Entry=" + currentPrice +
                                ", SL=" + stopLoss +
                                ", TP=" + takeProfit);

                log.debug(
                        "Trend-following SELL signal: symbol={}, price={}, sma20={}, sma50={}",
                        context.getSymbol(),
                        currentPrice,
                        sma20,
                        sma50);

                return buildSellSignal(context, currentPrice, stopLoss, takeProfit, sma20, sma50);
            }

            return noSignal(
                    context,
                    "No clear trend: price=" + currentPrice + ", SMA20=" + sma20 + ", SMA50=" + sma50);

        } catch (Exception exception) {
            log.error(
                    "Error generating trend-following signal for symbol={}",
                    context.getSymbol(),
                    exception);

            return noSignal(context, "Trend-following analysis error: " + exception.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled();
    }

    /**
     * Prefer String here. If your parent interface requires Object, change this
     * return type back to Object.
     */
    @Override
    public String getName() {
        return getMetadata().getDisplayName();
    }

    @Override
    public Object getId() {
        return STRATEGY_ID;
    }

    @Override
    public boolean supportsMarketBehavior(@NotNull MarketBehavior marketBehavior) {
        return marketBehavior.isTrendFriendly();
    }

    private double calculateSMA(@NotNull List<CandleData> candles, int period) {
        if (candles.size() < period) {
            return 0.0;
        }

        int startIndex = candles.size() - period;
        double sum = 0.0;

        for (int i = startIndex; i < candles.size(); i++) {
            CandleData candle = candles.get(i);

            if (candle == null || candle.closePrice() <= 0) {
                return 0.0;
            }

            sum += candle.closePrice();
        }

        return sum / period;
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
            double sma20,
            double sma50) {
        double riskRewardRatio = calculateRiskRewardRatio(entry, stopLoss, takeProfit);
        double confidence = calculateConfidence(riskRewardRatio, entry, sma20, sma50);

        log.debug(
                "Built BUY signal: symbol={}, entry={}, stopLoss={}, takeProfit={}, rr={}",
                context.getSymbol(),
                entry,
                stopLoss,
                takeProfit,
                riskRewardRatio);

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
                .reason("Bullish trend-following setup: price > SMA20 > SMA50")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private StrategySignal buildSellSignal(
            @NotNull StrategyContext context,
            double entry,
            double stopLoss,
            double takeProfit,
            double sma20,
            double sma50) {
        double riskRewardRatio = calculateRiskRewardRatio(entry, stopLoss, takeProfit);
        double confidence = calculateConfidence(riskRewardRatio, entry, sma20, sma50);

        log.debug(
                "Built SELL signal: symbol={}, entry={}, stopLoss={}, takeProfit={}, rr={}",
                context.getSymbol(),
                entry,
                stopLoss,
                takeProfit,
                riskRewardRatio);

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
                .reason("Bearish trend-following setup: price < SMA20 < SMA50")
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

    private double calculateConfidence(double riskRewardRatio, double entry, double sma20, double sma50) {
        double confidence = 0.60;

        if (riskRewardRatio >= 2.0) {
            confidence += 0.10;
        } else if (riskRewardRatio >= 1.5) {
            confidence += 0.05;
        }

        double fastSlowDistance = Math.abs(sma20 - sma50) / entry;

        if (fastSlowDistance >= 0.02) {
            confidence += 0.10;
        } else if (fastSlowDistance >= 0.01) {
            confidence += 0.05;
        }

        return Math.min(confidence, 0.85);
    }
}
