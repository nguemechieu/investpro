package org.investpro.strategy.lab;

import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.TradingStrategy;
import org.investpro.utils.Side;
import org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates consensus vote across multiple strategies.
 *
 * Responsibilities:
 * - Generate signals from top-ranked strategies
 * - Weight each vote by performance score and signal confidence
 * - Calculate BUY vs SELL vs HOLD weighted scores
 * - Determine consensus side
 * - Select best strategy on consensus side
 * - Return StrategyConsensusResult
 */
@Slf4j
public class StrategyVotingEngine {

    private static final double CONSENSUS_THRESHOLD = 0.15; // 15% margin required

    /**
     * Generate consensus from ranked strategies.
     *
     * @param symbol              symbol being voted on
     * @param timeframe           timeframe being voted on
     * @param context             strategy context with current market data
     * @param rankedReports       ranked strategy reports (best first)
     * @param maxStrategiesToVote maximum strategies to include in vote
     * @return consensus result with selected strategy
     */
    public StrategyConsensusResult vote(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @Nullable StrategyContext context,
            @NotNull List<StrategyPerformanceReport> rankedReports,
            int maxStrategiesToVote) {
        if (rankedReports == null || rankedReports.isEmpty()) {
            return createEmptyConsensus(symbol, timeframe, "No ranked strategies available");
        }

        // Collect votes from top strategies
        List<StrategyVote> votes = new ArrayList<>();
        double buyScore = 0.0;
        double sellScore = 0.0;
        double holdScore = 0.0;
        int buyVotes = 0;
        int sellVotes = 0;
        int holdVotes = 0;

        for (int i = 0; i < Math.min(maxStrategiesToVote, rankedReports.size()); i++) {
            StrategyPerformanceReport report = rankedReports.get(i);
            StrategyVote vote = generateVote(symbol, timeframe, context, report);

            if (vote != null) {
                votes.add(vote);
                double weight = vote.getWeight();

                if (vote.isBuy()) {
                    buyScore += weight;
                    buyVotes++;
                } else if (vote.isSell()) {
                    sellScore += weight;
                    sellVotes++;
                } else {
                    holdScore += weight;
                    holdVotes++;
                }
            }
        }

        // Determine consensus
        return calculateConsensus(symbol, timeframe, votes, buyScore, sellScore, holdScore, buyVotes, sellVotes,
                holdVotes);
    }

    /**
     * Generate a single vote from a strategy.
     */
    @Nullable
    private StrategyVote generateVote(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @Nullable StrategyContext context,
            @NotNull StrategyPerformanceReport report) {
        try {
            // Resolve strategy
            TradingStrategy strategy = StrategyRegistry.getInstance().getStrategy(report.getStrategyName());
            if (strategy == null) {
                log.warn("Could not resolve strategy: {}", report.getStrategyName());
                return null;
            }

            // Generate signal
            StrategySignal signal = null;
            if (context != null) {
                try {
                    signal = strategy.generateSignal(context);
                } catch (Exception e) {
                    log.debug("Signal generation failed for {}", report.getStrategyName(), e);
                    signal = null;
                }
            }

            // Create vote
            Side side = signal != null ? signal.getSide() : Side.HOLD;
            double confidence = signal != null ? signal.getConfidence() : 0.5;
            String reason = signal != null ? String.join(", ", signal.getReasons()) : "No signal";

            return StrategyVote.builder()
                    .strategyName(report.getStrategyName())
                    .baseStrategyName(report.getBaseStrategyName())
                    .symbol(symbol)
                    .timeframe(timeframe)
                    .side(side)
                    .confidence(confidence)
                    .score(report.getScore())
                    .reason(reason)
                    .signal(signal)
                    .performanceReport(report)
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate vote for {}", report.getStrategyName(), e);
            return null;
        }
    }

    /**
     * Calculate consensus from votes.
     */
    @NotNull
    private StrategyConsensusResult calculateConsensus(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull List<StrategyVote> votes,
            double buyScore,
            double sellScore,
            double holdScore,
            int buyVotes,
            int sellVotes,
            int holdVotes) {
        // Determine consensus side
        Side consensusSide = Side.HOLD;
        double consensusConfidence = 0.5;
        double consensusScore = 0.0;
        boolean consensusReached = false;
        String reason = "";

        // Find strongest side
        if (buyScore > sellScore && buyScore > holdScore) {
            consensusSide = Side.BUY;
            consensusScore = buyScore;
            consensusConfidence = buyVotes > 0 ? buyScore / buyVotes : 0.5;

            // Check for minimum margin
            double margin = (buyScore - Math.max(sellScore, holdScore)) / Math.max(buyScore, 1.0);
            if (margin >= CONSENSUS_THRESHOLD) {
                consensusReached = true;
                reason = String.format("BUY consensus reached with %d votes, margin: %.1f%%", buyVotes, margin * 100);
            } else {
                reason = String.format("Weak BUY signal (%d votes, margin: %.1f%%)", buyVotes, margin * 100);
            }
        } else if (sellScore > buyScore && sellScore > holdScore) {
            consensusSide = Side.SELL;
            consensusScore = sellScore;
            consensusConfidence = sellVotes > 0 ? sellScore / sellVotes : 0.5;

            double margin = (sellScore - Math.max(buyScore, holdScore)) / Math.max(sellScore, 1.0);
            if (margin >= CONSENSUS_THRESHOLD) {
                consensusReached = true;
                reason = String.format("SELL consensus reached with %d votes, margin: %.1f%%", sellVotes, margin * 100);
            } else {
                reason = String.format("Weak SELL signal (%d votes, margin: %.1f%%)", sellVotes, margin * 100);
            }
        } else {
            consensusSide = Side.HOLD;
            consensusConfidence = 0.5;
            consensusScore = holdScore;
            reason = String.format(
                    "HOLD (no clear consensus: BUY=%d/%.1f, SELL=%d/%.1f, HOLD=%d/%.1f)",
                    buyVotes, buyScore,
                    sellVotes, sellScore,
                    holdVotes, holdScore);
        }

        // Find best strategy on consensus side
        String selectedStrategyName = "NONE";
        StrategySignal selectedSignal = null;

        for (StrategyVote vote : votes) {
            if (vote.getSide() == consensusSide) {
                selectedStrategyName = vote.getStrategyName();
                selectedSignal = vote.getSignal();
                break; // First vote on consensus side (already sorted by score)
            }
        }

        // Normalize confidence
        consensusConfidence = Math.min(1.0, Math.max(0.0, consensusConfidence));

        return StrategyConsensusResult.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .consensusSide(consensusSide)
                .consensusConfidence(consensusConfidence)
                .selectedStrategyName(selectedStrategyName)
                .selectedSignal(selectedSignal)
                .votes(votes)
                .buyVotes(buyVotes)
                .sellVotes(sellVotes)
                .holdVotes(holdVotes)
                .buyScore(buyScore)
                .sellScore(sellScore)
                .holdScore(holdScore)
                .consensusReached(consensusReached)
                .reason(reason)
                .generatedAt(Instant.now())
                .build();
    }

    /**
     * Create empty consensus when no strategies available.
     */
    @NotNull
    private StrategyConsensusResult createEmptyConsensus(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull String reason) {
        return StrategyConsensusResult.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .consensusSide(Side.HOLD)
                .consensusConfidence(0.0)
                .selectedStrategyName("NONE")
                .selectedSignal(null)
                .votes(List.of())
                .buyVotes(0)
                .sellVotes(0)
                .holdVotes(0)
                .buyScore(0.0)
                .sellScore(0.0)
                .holdScore(0.0)
                .consensusReached(false)
                .reason(reason)
                .generatedAt(Instant.now())
                .build();
    }
}
