package org.investpro.strategy.lifecycle;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Canonical institutional assignment snapshot for the AI-driven strategy
 * lifecycle.
 *
 * <p>This object is data only. It contains no exchange access and no execution
 * authority; execution remains exclusively in the platform pipeline.</p>
 */
@Getter
@Builder(toBuilder = true)
@ToString
public class StrategyAssignment {

    @Builder.Default
    private final String assignmentId = UUID.randomUUID().toString();
    private final String strategyId;
    private final String strategyName;
    private final String symbol;
    private final String timeframe;
    @Builder.Default
    private final Instant assignedAt = Instant.now();
    private final String assignedBy;
    private final double score;
    private final double confidence;
    private final StrategyLifecycleStatus status;
    private final AIReviewDecision aiApproval;
    private final double aiScore;
    private final String aiReasoning;
    private final double backtestScore;
    private final double paperTradingScore;
    private final StrategyHealthLevel healthStatus;

    /** Builds a compact assignment snapshot from the richer lifecycle record. */
    public static StrategyAssignment fromRecord(StrategyLifecycleRecord record) {
        if (record == null) {
            return null;
        }
        double backtestScore = record.getRankScore() != null ? record.getRankScore().getBacktestScore() : record.getAssignmentScore();
        double paperScore = record.getRankScore() != null ? record.getRankScore().getPaperTradingScore() : record.getValidationScore() * 100.0;
        StrategyHealthLevel health = record.getLastHealthReport() == null ? null : record.getLastHealthReport().getHealthLevel();
        return StrategyAssignment.builder()
                .assignmentId(record.getAssignmentId())
                .strategyId(record.getStrategyId())
                .strategyName(record.getStrategyName())
                .symbol(record.getSymbol())
                .timeframe(record.getTimeframe())
                .assignedAt(record.getAssignedAt())
                .assignedBy(record.getAssignedBy())
                .score(record.getAssignmentScore())
                .confidence(record.getConfidence())
                .status(record.getLifecycleStatus())
                .aiApproval(record.getAiApprovalStatus())
                .aiScore(record.getAiConfidence() * 100.0)
                .aiReasoning(record.getAiReasoningSummary())
                .backtestScore(backtestScore)
                .paperTradingScore(paperScore)
                .healthStatus(health)
                .build();
    }
}
