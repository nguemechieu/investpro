package org.investpro.strategy.lab;

import org.investpro.enums.timeframe.Timeframe;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StrategyVotingEngineTest {

    private final StrategyVotingEngine engine = new StrategyVotingEngine();

    @Test
    void voteReturnsHoldWhenNoRankedStrategiesProvided() {
        StrategyConsensusResult result = engine.vote("BTC/USDT", Timeframe.M15, null, List.of(), 5);

        assertEquals(Side.HOLD, result.getConsensusSide());
        assertFalse(result.isConsensusReached());
        assertEquals("NONE", result.getSelectedStrategyName());
        assertEquals(0, result.getTotalVotes());
    }

    @Test
    void voteReturnsHoldWhenStrategiesCannotBeResolved() {
        StrategyPerformanceReport unknownA = report("missing-strategy-a", 80.0);
        StrategyPerformanceReport unknownB = report("missing-strategy-b", 75.0);

        StrategyConsensusResult result = engine.vote(
                "BTC/USDT",
                Timeframe.M15,
                null,
                List.of(unknownA, unknownB),
                5);

        assertEquals(Side.HOLD, result.getConsensusSide());
        assertFalse(result.isConsensusReached());
        assertEquals("NONE", result.getSelectedStrategyName());
        assertEquals(0, result.getTotalVotes());
        assertTrue(result.getReason().contains("no clear consensus"));
    }

    private static StrategyPerformanceReport report(String name, double score) {
        return StrategyPerformanceReport.builder()
                .strategyName(name)
                .baseStrategyName(name)
                .symbol("BTC/USDT")
                .timeframe(Timeframe.M15)
                .totalTrades(20)
                .winningTrades(11)
                .losingTrades(9)
                .winRate(0.55)
                .profitFactor(1.3)
                .totalReturn(8.0)
                .maxDrawdown(0.12)
                .averageRiskReward(1.3)
                .averageConfidence(0.6)
                .score(score)
                .netProfit(140.0)
                .build();
    }
}
