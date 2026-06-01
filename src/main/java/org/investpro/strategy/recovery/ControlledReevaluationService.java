package org.investpro.strategy.recovery;

import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.investpro.config.AppConfigKeys;
import org.investpro.strategy.StrategyAssignment;

import java.util.Objects;

/**
 * Applies startup-safe reevaluation policy for assignment replacement
 * decisions.
 */
@Slf4j
public final class ControlledReevaluationService {

    public boolean shouldAutoReassignOnStartup() {
        return AppConfig.getBoolean(AppConfigKeys.STRATEGY_ASSIGNMENT_AUTO_REASSIGN_ON_STARTUP, false);
    }

    public StrategyHandoffDecision evaluateReplacement(StrategyAssignment assignment, boolean hasOpenPosition) {
        if (assignment == null) {
            return StrategyHandoffDecision.REQUIRE_MANUAL_REVIEW;
        }

        if (hasOpenPosition) {
            return StrategyHandoffDecision.BLOCK_UNTIL_POSITION_CLOSED;
        }

        if (assignment.isManual() || assignment.isDisabled() || !assignment.canBeAutoReplaced()) {
            return StrategyHandoffDecision.REQUIRE_MANUAL_REVIEW;
        }

        return StrategyHandoffDecision.ALLOW_IMMEDIATE_REPLACEMENT;
    }

    public boolean mustRouteToManualReviewForOrphanedPositions() {
        return AppConfig.getBoolean(
                AppConfigKeys.STRATEGY_ASSIGNMENT_REQUIRE_MANUAL_REVIEW_FOR_ORPHANED_POSITIONS,
                true);
    }

    public double startupReevaluationScoreThreshold() {
        return AppConfig.getDouble("investpro.strategy.reassignBelowScore",
                AppConfig.getDouble("investpro.strategy.hardMinStrategyScore", 40.0));
    }

    public boolean isScoreHealthy(StrategyAssignment assignment) {
        if (assignment == null) {
            return false;
        }

        double score = assignment.getScoreAtAssignment();
        double threshold = startupReevaluationScoreThreshold();
        boolean healthy = score >= threshold;

        if (!healthy) {
            log.info("Assignment {} score {} below startup threshold {}",
                    Objects.requireNonNullElse(assignment.getAssignmentId(), ""),
                    score,
                    threshold);
        }

        return healthy;
    }
}
