package org.investpro.strategy.lab;

import org.investpro.enums.timeframe.Timeframe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StrategyRankingEngineTest {

    private final StrategyRankingEngine engine = new StrategyRankingEngine();

    @Test
    void rankOrdersByScoreThenTradesDescending() {
        StrategyPerformanceReport highTrades = report("high-trades", 72.0, 25, 0.56, 1.8, 14.0, 0.10, 420.0);
        StrategyPerformanceReport lowTrades = report("low-trades", 72.0, 8, 0.58, 1.9, 13.0, 0.09, 390.0);
        StrategyPerformanceReport topScore = report("top-score", 80.0, 5, 0.52, 1.6, 12.0, 0.08, 300.0);

        List<StrategyPerformanceReport> ranked = engine.rank(List.of(lowTrades, topScore, highTrades));

        assertEquals("top-score", ranked.get(0).getStrategyName());
        assertEquals("high-trades", ranked.get(1).getStrategyName());
        assertEquals("low-trades", ranked.get(2).getStrategyName());
    }

    @Test
    void getBestTradableReturnsHighestScoreTradableReport() {
        StrategyPerformanceReport notTradable = report("weak", 85.0, 15, 0.30, 0.9, 4.0, 0.35, -20.0);
        StrategyPerformanceReport tradableA = report("tradable-a", 61.0, 18, 0.48, 1.4, 9.0, 0.12, 180.0);
        StrategyPerformanceReport tradableB = report("tradable-b", 74.0, 22, 0.55, 1.7, 16.0, 0.11, 320.0);

        StrategyPerformanceReport best = engine.getBestTradable(List.of(notTradable, tradableA, tradableB));

        assertNotNull(best);
        assertEquals("tradable-b", best.getStrategyName());
    }

    @Test
    void scoreNeverReturnsNegativeValue() {
        StrategyPerformanceReport poor = report("poor", 0.0, 6, 0.05, 0.2, -40.0, 0.95, -1200.0);

        double score = engine.score(poor);

        assertEquals(0.0, score);
    }

    private static StrategyPerformanceReport report(
            String name,
            double score,
            int trades,
            double winRate,
            double profitFactor,
            double totalReturn,
            double maxDrawdown,
            double netProfit) {
        return StrategyPerformanceReport.builder()
                .strategyName(name)
                .baseStrategyName(name)
                .symbol("BTC/USDT")
                .timeframe(Timeframe.M15)
                .totalTrades(trades)
                .winningTrades(Math.max(0, (int) Math.round(trades * winRate)))
                .losingTrades(Math.max(0, trades - (int) Math.round(trades * winRate)))
                .winRate(winRate)
                .profitFactor(profitFactor)
                .totalReturn(totalReturn)
                .maxDrawdown(maxDrawdown)
                .averageRiskReward(1.5)
                .averageConfidence(0.7)
                .score(score)
                .netProfit(netProfit)
                .build();
    }
}
