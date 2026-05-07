package org.investpro.strategy.lab;

import lombok.Builder;
import lombok.Getter;
import org.investpro.strategy.StrategySignal;
import org.investpro.utils.Side;
import org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Consensus result from voting across multiple strategies.
 *
 * Aggregates votes from all strategies and produces final consensus:
 * - Consensus side (BUY / SELL / HOLD)
 * - Selected best strategy on consensus side
 * - Vote counts and weighted scores
 */
@Getter
@Builder(toBuilder = true)
public class StrategyConsensusResult {

    /**
     * Symbol being voted on.
     */
    @NotNull
    private final String symbol;

    /**
     * Timeframe being voted on.
     */
    @NotNull
    private final Timeframe timeframe;

    /**
     * Final consensus side: BUY, SELL, or HOLD.
     */
    @NotNull
    private final Side consensusSide;

    /**
     * Consensus confidence (0.0 to 1.0).
     */
    @Builder.Default
    private final double consensusConfidence = 0.5;

    /**
     * Name of the selected strategy on the consensus side.
     * This is the highest-scoring strategy that agrees with consensus.
     */
    @NotNull
    private final String selectedStrategyName;

    /**
     * Signal from the selected strategy.
     */
    @Nullable
    private final StrategySignal selectedSignal;

    /**
     * All votes cast.
     */
    @Builder.Default
    private final List<StrategyVote> votes = new ArrayList<>();

    /**
     * Number of votes for BUY.
     */
    @Builder.Default
    private final int buyVotes = 0;

    /**
     * Number of votes for SELL.
     */
    @Builder.Default
    private final int sellVotes = 0;

    /**
     * Number of votes for HOLD.
     */
    @Builder.Default
    private final int holdVotes = 0;

    /**
     * Weighted score for BUY side (sum of weights).
     */
    @Builder.Default
    private final double buyScore = 0.0;

    /**
     * Weighted score for SELL side.
     */
    @Builder.Default
    private final double sellScore = 0.0;

    /**
     * Weighted score for HOLD side.
     */
    @Builder.Default
    private final double holdScore = 0.0;

    /**
     * True if consensus was reached with good agreement.
     * Requires minimum vote margin.
     */
    @Builder.Default
    private final boolean consensusReached = false;

    /**
     * Explanation of consensus result.
     */
    @Nullable
    private final String reason;

    /**
     * When this consensus was generated.
     */
    @Builder.Default
    private final Instant generatedAt = Instant.now();

    /**
     * Total votes cast.
     */
    public int getTotalVotes() {
        return buyVotes + sellVotes + holdVotes;
    }

    /**
     * Strongest competing score (second strongest side).
     */
    public double getCompetingScore() {
        if (consensusSide == Side.BUY) {
            return Math.max(sellScore, holdScore);
        } else if (consensusSide == Side.SELL) {
            return Math.max(buyScore, holdScore);
        } else {
            return Math.max(buyScore, sellScore);
        }
    }

    /**
     * Consensus margin: how much stronger consensus vs competing.
     */
    public double getConsensusMargin() {
        double consensusScore = getConsensusScore();
        double competing = getCompetingScore();
        if (competing == 0)
            return consensusScore;
        return (consensusScore - competing) / competing;
    }

    /**
     * Get the score of the consensus side.
     */
    public double getConsensusScore() {
        return switch (consensusSide) {
            case BUY -> buyScore;
            case SELL -> sellScore;
            case HOLD -> holdScore;
        };
    }

    /**
     * Whether consensus is strong (margin > 20%).
     */
    public boolean isStrongConsensus() {
        return consensusReached && getConsensusMargin() > 0.20;
    }

    /**
     * Whether consensus is weak (margin < 10%).
     */
    public boolean isWeakConsensus() {
        return consensusReached && getConsensusMargin() < 0.10;
    }
}
