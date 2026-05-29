package org.investpro.strategy.lab;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.TradingSessionStatus;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.*;
import org.investpro.utils.HistoricalDataPrefetcher;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.*;

/**
 * Executes backtest for a single strategy on a symbol/timeframe.
 * <p>
 * Responsibilities:
 * - Resolve strategy from registry
 * - Simulate strategy on historical candles
 * - Track entry/exit trades with P&L
 * - Calculate performance metrics
 * - Return StrategyPerformanceReport
 * <p>
 * This is BACKTESTING ONLY. No live orders are sent.
 */
@Slf4j
@Data
public class StrategyBacktestRunner {

    private static final int MIN_LOOKBACK_BARS = 50;
    private double totalLoss;

    /**
     * Run a backtest for one strategy on one symbol/timeframe.
     */
    public StrategyPerformanceReport run(@NotNull StrategyBacktestRequest request) {
        try {
            log.info(
                    "Starting backtest: {} on {}/{} with {} candles",
                    request.getStrategyName(),
                    request.getSymbol(),
                    request.getTimeframe().getCode(),
                    request.getCandles().size());

            // Validate request
            if (!validateRequest(request)) {
                return createFailureReport(request, "Request validation failed");
            }

            // Get the strategy
            TradingStrategy strategy = resolveStrategy(request.getStrategyName());
            if (strategy == null) {
                return createFailureReport(request, "Strategy not found: " + request.getStrategyName());
            }

            // Run simulation
            return simulateStrategy(request, strategy);

        } catch (Exception e) {
            log.error("Backtest failed for {}", request.getStrategyName(), e);
            return createFailureReport(request, "Backtest error: " + e.getMessage());
        }
    }

    /**
     * Validate backtest request.
     */
    private boolean validateRequest(StrategyBacktestRequest request) {
        if (request.getCandles() == null
                || !HistoricalDataPrefetcher.hasEnoughDataForBasicTesting(request.getCandles().size())) {
            int candleCount = request.getCandles() == null ? 0 : request.getCandles().size();
            log.warn("Insufficient candles for basic backtest: {} < {}", candleCount, MIN_LOOKBACK_BARS);
            return false;
        }

        if (request.getSymbol() == null || request.getSymbol().isBlank()) {
            log.warn("Invalid symbol");
            return false;
        }

        if (request.getTimeframe() == null) {
            log.warn("Invalid timeframe");
            return false;
        }

        return true;
    }

    /**
     * Resolve strategy from registry.
     */
    private TradingStrategy resolveStrategy(String strategyName) {
        try {
            StrategyRegistry registry = StrategyRegistry.getInstance();
            return registry.getStrategy(strategyName);
        } catch (Exception e) {
            log.error("Failed to resolve strategy: {}", strategyName, e);
            return null;
        }
    }

    /**
     * Simulate strategy on historical candles.
     */
    private StrategyPerformanceReport simulateStrategy(
            StrategyBacktestRequest request,
            TradingStrategy strategy) {
        List<CandleData> candles = request.getCandles();
        List<StrategyBacktestTrade> trades = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        double equity = request.getInitialCapital();
        double peakEquity = equity;
        totalLoss = 0.0;
        int tradeCount = 0;

        // Simulation state
        boolean inTrade = false;
        StrategyBacktestTrade currentTrade = null;
        int entryBar = -1;

        // Loop through candles starting from sufficient lookback
        for (int i = MIN_LOOKBACK_BARS; i < candles.size() && tradeCount < request.getMaxTrades(); i++) {
            CandleData candle = candles.get(i);
            CandleData previousCandle = i > 0 ? candles.get(i - 1) : candle;

            // Get candle window for context
            List<CandleData> window = new ArrayList<>(candles.subList(0, i + 1));

            // Check if we need to exit current trade
            if (inTrade && currentTrade != null) {
                ExitSignal exit = checkExit(
                        currentTrade,
                        candle,
                        previousCandle,
                        i - entryBar,
                        request.getFallbackExitBars());

                if (exit.shouldExit) {
                    StrategyBacktestTrade closedTrade = closeTrade(
                            currentTrade,
                            exit.exitPrice,
                            candle.openTime(),
                            i - entryBar,
                            exit.exitReason,
                            request.getCommissionRate(),
                            request.getSlippageRate());

                    trades.add(closedTrade);
                    equity += closedTrade.getProfitLoss();

                    if (closedTrade.isLoss()) {
                        totalLoss += Math.abs(closedTrade.getProfitLoss());
                    }

                    if (equity > peakEquity) {
                        peakEquity = equity;
                    }

                    inTrade = false;
                    currentTrade = null;
                    entryBar = -1;
                }
            }

            // Generate signal if not in trade
            if (!inTrade) {
                StrategySignal signal = generateSignal(strategy, request, window);

                if (signal != null && signal.isActionable() && tradeCount < request.getMaxTrades()) {
                    // Check if we can trade this side
                    if (!request.isAllowShorts() && signal.isSell()) {
                        continue;
                    }

                    // Enter trade
                    currentTrade = StrategyBacktestTrade.builder()
                            .strategyName(request.getStrategyName())
                            .symbol(request.getSymbol())
                            .timeframe(request.getTimeframe())
                            .side(signal.getSide())
                            .entryPrice(candle.closePrice()) // Use candle close as entry
                            .quantity(calculateQuantity(equity, request.getInitialCapital()))
                            .confidence(signal.getConfidence())
                            .entryReason(String.join(", ", signal.getReasons()))
                            .entryTime(Instant.now())
                            .build();

                    inTrade = true;
                    entryBar = i;
                    tradeCount++;
                }
            }
        }

        // Close any remaining open trade
        if (inTrade && currentTrade != null) {
            CandleData lastCandle = candles.get(candles.size() - 1);
            StrategyBacktestTrade closedTrade = closeTrade(
                    currentTrade,
                    lastCandle.closePrice(),
                    lastCandle.openTime(),
                    candles.size() - entryBar,
                    "Backtest end",
                    request.getCommissionRate(),
                    request.getSlippageRate());
            trades.add(closedTrade);
            equity += closedTrade.getProfitLoss();
        }

        // Calculate statistics
        return calculateReport(request, trades, warnings, equity, peakEquity);
    }

    /**
     * Generate signal from strategy.
     */
    private StrategySignal generateSignal(
            TradingStrategy strategy,
            StrategyBacktestRequest request,
            List<CandleData> window) {
        try {
            TradePair tradePair = parsePair(request.getSymbol());
            StrategyContext context = StrategyContext.builder()
                    .symbol(tradePair)
                    .timeframe(request.getTimeframe())
                    .candles(window)
                    .currentPrice(window.isEmpty() ? 0 : window.get(window.size() - 1).closePrice())
                    .bid(window.isEmpty() ? 0 : window.get(window.size() - 1).lowPrice())
                    .ask(window.isEmpty() ? 0 : window.get(window.size() - 1).highPrice())
                    .marketBehavior(MarketBehavior.RANGING)
                    .volatility(0.01)
                    .averageVolume(1000000)
                    .tradingSessionStatus(TradingSessionStatus.OPEN)
                    .barsAvailable(window.size())
                    .build();

            return strategy.generateSignal(context);
        } catch (Exception e) {
            log.debug("Signal generation failed", e);
            return null;
        }
    }

    private TradePair parsePair(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        String normalized = symbol.trim().replace('_', '/').replace('-', '/');
        String[] parts = normalized.split("/");
        if (parts.length < 2) {
            return null;
        }

        try {
            return new TradePair(parts[0], parts[1]);
        } catch (Exception exception) {
            log.debug("Unable to parse TradePair from backtest symbol {}", symbol, exception);
            return null;
        }
    }

    /**
     * Check if current trade should be exited.
     */
    private ExitSignal checkExit(
            StrategyBacktestTrade trade,
            CandleData candle,
            CandleData previousCandle,
            int barsHeld,
            int fallbackExitBars) {
        double close = candle.closePrice();
        double high = candle.highPrice();
        double low = candle.lowPrice();

        // Check stop loss
        if (trade.getSide() == Side.BUY) {
            // For longs, stop is below entry
            if (low <= trade.getEntryPrice() * 0.95) { // 5% stop by default
                return new ExitSignal(true, low, "Hit stop loss");
            }
            // Check take profit (5% target by default)
            if (high >= trade.getEntryPrice() * 1.05) {
                return new ExitSignal(true, high, "Hit take profit");
            }
        } else if (trade.getSide() == Side.SELL) {
            // For shorts, stop is above entry
            if (high >= trade.getEntryPrice() * 1.05) {
                return new ExitSignal(true, high, "Hit stop loss");
            }
            // Check take profit
            if (low <= trade.getEntryPrice() * 0.95) {
                return new ExitSignal(true, low, "Hit take profit");
            }
        }

        // Check fallback exit (timeout)
        if (barsHeld >= fallbackExitBars) {
            return new ExitSignal(true, close, "Timeout exit");
        }

        return new ExitSignal(false, close, null);
    }

    /**
     * Close a trade and calculate P&L.
     */
    private StrategyBacktestTrade closeTrade(
            StrategyBacktestTrade trade,
            double exitPrice,
            long exitTimestamp,
            int barsHeld,
            String exitReason,
            double commissionRate,
            double slippageRate) {
        double entryGross = trade.getEntryPrice() * trade.getQuantity();
        double exitGross = exitPrice * trade.getQuantity();

        // Apply commission and slippage
        double commission = (entryGross + exitGross) * commissionRate;
        double slippage = (entryGross + exitGross) * slippageRate;
        double costs = commission + slippage;

        double profitLoss;
        if (trade.getSide() == Side.BUY) {
            profitLoss = (exitPrice - trade.getEntryPrice()) * trade.getQuantity() - costs;
        } else {
            profitLoss = (trade.getEntryPrice() - exitPrice) * trade.getQuantity() - costs;
        }

        double profitLossPercent = profitLoss / entryGross * 100.0;

        return trade.toBuilder()
                .exitPrice(exitPrice)
                .exitTime(Instant.ofEpochMilli(exitTimestamp))
                .exitReason(exitReason)
                .profitLoss(profitLoss)
                .profitLossPercent(profitLossPercent)
                .barsHeld(barsHeld)
                .build();
    }

    /**
     * Calculate quantity based on equity and Kelly criterion.
     */
    private double calculateQuantity(double currentEquity, double initialCapital) {
        // Use fixed fraction of current equity (e.g., 1%)
        return (currentEquity * 0.01) / 100.0; // Assume 100 as price
    }

    /**
     * Calculate performance report.
     */
    private StrategyPerformanceReport calculateReport(
            StrategyBacktestRequest request,
            @NonNull List<StrategyBacktestTrade> trades,
            List<String> warnings,
            double finalEquity,
            double peakEquity) {
        int winCount = 0;
        int lossCount = 0;
        double sumWins = 0.0;
        double sumLosses = 0.0;
        double sumRiskReward = 0.0;
        double sumConfidence = 0.0;

        for (StrategyBacktestTrade trade : trades) {
            if (trade.isWin()) {
                winCount++;
                sumWins += trade.getProfitLoss();
            } else if (trade.isLoss()) {
                lossCount++;
                sumLosses += Math.abs(trade.getProfitLoss());
            }
            sumRiskReward += trade.getRiskRewardRatio();
            sumConfidence += trade.getConfidence();
        }

        int totalTrades = trades.size();
        double winRate = totalTrades > 0 ? (double) winCount / totalTrades : 0.0;
        double totalReturn = ((finalEquity - request.getInitialCapital()) / request.getInitialCapital()) * 100.0;
        double netProfit = finalEquity - request.getInitialCapital();
        double maxDrawdown = calculateMaxDrawdown(trades, request.getInitialCapital());
        double profitFactor = sumLosses > 0 ? sumWins / sumLosses : (sumWins > 0 ? Double.POSITIVE_INFINITY : 0.0);
        double averageWin = winCount > 0 ? sumWins / winCount : 0.0;
        double averageLoss = lossCount > 0 ? sumLosses / lossCount : 0.0;
        double averageRiskReward = totalTrades > 0 ? sumRiskReward / totalTrades : 1.0;
        double averageConfidence = totalTrades > 0 ? sumConfidence / totalTrades : 0.5;
        double sharpe = calculateSharpeApproximation(trades, finalEquity, request.getInitialCapital());
        double score = calculateScore(winRate, totalReturn, profitFactor, maxDrawdown, averageRiskReward,
                averageConfidence, totalTrades);

        String baseName = StrategyCatalog.resolveBaseStrategyName(request.getStrategyName());

        if (totalTrades < 5) {
            warnings.add("Insufficient trades (" + totalTrades + ") for meaningful statistics");
        }
        if (maxDrawdown > 0.30) {
            warnings.add("High drawdown: " + String.format("%.1f%%", maxDrawdown * 100));
        }

        return StrategyPerformanceReport.builder()
                .strategyName(request.getStrategyName())
                .baseStrategyName(baseName)
                .symbol(request.getSymbol())
                .timeframe(request.getTimeframe())
                .totalTrades(totalTrades)
                .winningTrades(winCount)
                .losingTrades(lossCount)
                .winRate(winRate)
                .totalReturn(totalReturn)
                .netProfit(netProfit)
                .maxDrawdown(maxDrawdown)
                .profitFactor(profitFactor)
                .averageWin(averageWin)
                .averageLoss(averageLoss)
                .averageRiskReward(averageRiskReward)
                .averageConfidence(averageConfidence)
                .sharpeApproximation(sharpe)
                .score(score)
                .trades(trades)
                .warnings(warnings)
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * Calculate maximum drawdown.
     */
    private double calculateMaxDrawdown(List<StrategyBacktestTrade> trades, double initialCapital) {
        double peak = initialCapital;
        double maxDD = 0.0;
        double equity = initialCapital;

        for (StrategyBacktestTrade trade : trades) {
            equity += trade.getProfitLoss();
            if (equity > peak) {
                peak = equity;
            }
            double dd = (peak - equity) / peak;
            if (dd > maxDD) {
                maxDD = dd;
            }
        }

        return maxDD;
    }

    /**
     * Calculate Sharpe ratio approximation.
     */
    private double calculateSharpeApproximation(
            List<StrategyBacktestTrade> trades,
            double finalEquity,
            double initialCapital) {
        if (trades.isEmpty())
            return 0.0;

        double returns = (finalEquity - initialCapital) / initialCapital;
        double variance = 0.0;
        double avgReturn = returns / trades.size();

        for (StrategyBacktestTrade trade : trades) {
            double tradeReturn = trade.getProfitLoss() / initialCapital;
            variance += Math.pow(tradeReturn - avgReturn, 2);
        }

        double stdDev = Math.sqrt(variance / Math.max(1, trades.size() - 1));
        if (stdDev == 0)
            return 0.0;

        // Approximate Sharpe (assuming 252 trading days)
        return (returns / stdDev) * Math.sqrt(252);
    }

    /**
     * Calculate overall performance score (0-100+).
     */
    private double calculateScore(
            double winRate,
            double totalReturn,
            double profitFactor,
            double maxDrawdown,
            double averageRiskReward,
            double averageConfidence,
            int tradeCount) {
        double score = 0.0;

        // Win rate: 0-25 points
        score += Math.min(25, winRate * 100);

        // Total return: 0-20 points (capped at 20% return)
        score += Math.min(20, totalReturn);

        // Profit factor: 0-15 points
        score += Math.min(15, profitFactor * 5);

        // Risk/reward: 0-10 points
        score += Math.min(10, averageRiskReward * 5);

        // Confidence: 0-10 points
        score += Math.min(10, averageConfidence * 20);

        // Drawdown penalty: -20 points max
        score -= Math.min(20, maxDrawdown * 100);

        // Trade reliability bonus: 0-20 points
        if (tradeCount >= 20) {
            score += 20;
        } else if (tradeCount >= 10) {
            score += 10;
        } else if (tradeCount >= 5) {
            score += 5;
        }

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Create failure report.
     */
    private StrategyPerformanceReport createFailureReport(StrategyBacktestRequest request, String reason) {
        String baseName = StrategyCatalog.resolveBaseStrategyName(request.getStrategyName());

        return StrategyPerformanceReport.builder()
                .strategyName(request.getStrategyName())
                .baseStrategyName(baseName)
                .symbol(request.getSymbol())
                .timeframe(request.getTimeframe())
                .totalTrades(0)
                .winningTrades(0)
                .losingTrades(0)
                .winRate(0.0)
                .totalReturn(0.0)
                .netProfit(0.0)
                .maxDrawdown(0.0)
                .profitFactor(0.0)
                .score(0.0)
                .trades(List.of())
                .warnings(List.of(reason))
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * Helper class for exit signal.
     */
    private static class ExitSignal {
        boolean shouldExit;
        double exitPrice;
        String exitReason;

        ExitSignal(boolean shouldExit, double exitPrice, String exitReason) {
            this.shouldExit = shouldExit;
            this.exitPrice = exitPrice;
            this.exitReason = exitReason;
        }
    }
}
