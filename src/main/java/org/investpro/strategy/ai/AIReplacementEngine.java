package org.investpro.strategy.ai;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.event.EventBusManager;
import org.investpro.strategy.lifecycle.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Evaluates whether a degraded strategy should be replaced, demoted, paused, or
 * kept.
 *
 * <p>
 * <strong>CRITICAL:</strong> This engine is an advisor only.
 * It NEVER replaces strategies autonomously. All replacement actions are
 * performed
 * exclusively by
 * {@link org.investpro.strategy.management.StrategyAssignmentManager}.
 * </p>
 */
@Slf4j
public class AIReplacementEngine {

    private static volatile AIReplacementEngine instance;

    private static final String SOURCE = "AIReplacementEngine";
    private static final double SCORE_IMPROVEMENT_THRESHOLD = 10.0;

    private AIReplacementEngine() {
        log.info("AIReplacementEngine initialised");
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton AIReplacementEngine
     */
    public static AIReplacementEngine getInstance() {
        AIReplacementEngine local = instance;
        if (local == null) {
            synchronized (AIReplacementEngine.class) {
                local = instance;
                if (local == null) {
                    local = new AIReplacementEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Evaluates whether the degraded strategy should be replaced.
     *
     * @param degradedRecord      the lifecycle record of the degraded strategy
     * @param availableCandidates list of ranked candidate strategies to consider
     * @return AIReplacementReport with recommendation
     */
    public AIReplacementReport evaluateReplacement(
            StrategyLifecycleRecord degradedRecord,
            List<StrategyRankScore> availableCandidates) {

        if (degradedRecord == null) {
            log.warn("evaluateReplacement called with null degraded record");
            return buildReport(null, AIReplacementRecommendation.KEEP,
                    0.0, "Unknown", null, 0.0, 0.0, List.of("Null input"), 0.0, 0.0, "N/A");
        }

        log.debug("Evaluating replacement for assignment={}", degradedRecord.getAssignmentId());

        double currentScore = degradedRecord.getRankScore() != null
                ? degradedRecord.getRankScore().getCompositeScore()
                : 0.0;

        List<String> reasons = new ArrayList<>();

        // Collect health info
        StrategyHealthReport health = degradedRecord.getLastHealthReport();
        if (health != null) {
            reasons.add("Health level: " + health.getHealthLevel().name()
                    + " (score: " + String.format("%.1f", health.getHealthScore()) + ")");
        }

        // Find best candidate
        StrategyRankScore bestCandidate = null;
        if (availableCandidates != null && !availableCandidates.isEmpty()) {
            bestCandidate = availableCandidates.stream()
                    .filter(c -> !c.getStrategyId().equals(degradedRecord.getStrategyId()))
                    .max(Comparator.comparingDouble(StrategyRankScore::getCompositeScore))
                    .orElse(null);
        }

        double candidateScore = bestCandidate != null ? bestCandidate.getCompositeScore() : 0.0;
        double scoreDiff = candidateScore - currentScore;
        double regimeFitImprovement = 0.0;
        double riskProfileImprovement = 0.0;
        String performanceComparison;

        AIReplacementRecommendation recommendation;
        double aiConfidence;
        String reasoning;

        if (health == null || health.getHealthLevel() == StrategyHealthLevel.WATCH) {
            // Not severe enough to replace
            recommendation = AIReplacementRecommendation.KEEP;
            aiConfidence = 0.60;
            reasoning = "Performance dip appears temporary. Recommend continued monitoring.";
            performanceComparison = "Current strategy within acceptable bounds.";
        } else if (health.isRegimeShifting()) {
            recommendation = AIReplacementRecommendation.PAUSE;
            aiConfidence = 0.70;
            reasoning = "Market regime shift detected. Strategy should be paused pending regime stabilisation.";
            reasons.add("Regime shift detected");
            performanceComparison = "Pausing to protect capital during regime transition.";
        } else if (bestCandidate != null && scoreDiff > SCORE_IMPROVEMENT_THRESHOLD) {
            recommendation = AIReplacementRecommendation.REPLACE;
            aiConfidence = 0.75;
            regimeFitImprovement = Math.max(0, bestCandidate.getRegimeFitScore()
                    - (degradedRecord.getRankScore() != null ? degradedRecord.getRankScore().getRegimeFitScore() : 0));
            riskProfileImprovement = Math.max(0, bestCandidate.getRiskScore()
                    - (degradedRecord.getRankScore() != null ? degradedRecord.getRankScore().getRiskScore() : 0));
            reasoning = String.format(
                    "Candidate %s has significantly higher composite score (+%.1f). Recommend replacement.",
                    bestCandidate.getStrategyId(), scoreDiff);
            reasons.add(String.format("Replacement candidate score: %.1f vs current: %.1f",
                    candidateScore, currentScore));
            performanceComparison = String.format(
                    "Candidate outperforms current by %.1f composite score points.", scoreDiff);
        } else {
            recommendation = AIReplacementRecommendation.DEMOTE;
            aiConfidence = 0.65;
            reasoning = "Strategy is degraded but no significantly better candidate found. Demote to paper trading for re-validation.";
            performanceComparison = bestCandidate != null
                    ? String.format("Best candidate score: %.1f (improvement: %.1f)", candidateScore, scoreDiff)
                    : "No replacement candidates available.";
        }

        AIReplacementReport report = buildReport(
                degradedRecord, recommendation, aiConfidence, reasoning,
                bestCandidate != null ? bestCandidate.getStrategyId() : null,
                candidateScore, currentScore, reasons,
                regimeFitImprovement, riskProfileImprovement, performanceComparison);

        log.info("Replacement evaluation: assignment={} recommendation={} confidence={}",
                degradedRecord.getAssignmentId(), recommendation, String.format("%.2f", aiConfidence));

        EventBusManager.getInstance().publish(
                AgentEvent.of(AgentEvent.AI_REPLACEMENT_EVALUATED, SOURCE, report));

        return report;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private AIReplacementReport buildReport(StrategyLifecycleRecord record,
            AIReplacementRecommendation recommendation,
            double confidence, String reasoning,
            String candidateId, double candidateScore,
            double currentScore, List<String> reasons,
            double regimeFit, double riskImprovement,
            String perfComparison) {
        return AIReplacementReport.builder()
                .reportId(UUID.randomUUID().toString())
                .assignmentId(record != null ? record.getAssignmentId() : "UNKNOWN")
                .currentStrategyId(record != null ? record.getStrategyId() : "UNKNOWN")
                .symbol(record != null ? record.getSymbol() : "UNKNOWN")
                .timeframe(record != null ? record.getTimeframe() : "UNKNOWN")
                .recommendation(recommendation)
                .aiConfidence(confidence)
                .reasoningSummary(reasoning)
                .candidateStrategyId(candidateId)
                .candidateScore(candidateScore)
                .currentScore(currentScore)
                .replacementReasons(reasons)
                .regimeFitImprovement(regimeFit)
                .riskProfileImprovement(riskImprovement)
                .recentPerformanceComparison(perfComparison)
                .generatedAt(Instant.now())
                .build();
    }
}
