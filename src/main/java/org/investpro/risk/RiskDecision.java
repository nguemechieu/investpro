package org.investpro.risk;

import lombok.*;
import org.investpro.enums.ExecutionStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable decision object returned by RiskManagementSystem.evaluateTrade().
 * Contains approval status, position sizing, and detailed reasoning.
 */
@Value
@Builder
@AllArgsConstructor
@Data
public class RiskDecision {

    // Primary decision
    boolean approved;

    @Builder.Default
    RiskDecisionType decisionType = RiskDecisionType.APPROVE;

    @Override
    public String toString() {
        return "RiskDecision{" +
                "approved=" + approved +
                ", approvalReason='" + approvalReason + '\'' +
                ", finalPositionSize=" + finalPositionSize +
                ", finalLeverage=" + finalLeverage +
                ", riskMultiplier=" + riskMultiplier +
                ", expectedValue=" + expectedValue +
                ", portfolioHeat=" + portfolioHeat +
                ", estimatedSlippage=" + estimatedSlippage +
                ", recommendedExecutionStrategy=" + recommendedExecutionStrategy +
                ", blockers=" + blockers +
                ", warnings=" + warnings +
                ", recommendations=" + recommendations +
                ", humanReadableSummary='" + humanReadableSummary + '\'' +
                '}';
    }

    @Builder.Default
    String approvalReason = "";

    // Position sizing
    double finalPositionSize;
    double finalLeverage;

    @Builder.Default
    double riskMultiplier = 1.0;

    // Trade expectations
    double expectedValue;
    double portfolioHeat;
    double estimatedSlippage;

    // Recommendations
    @Builder.Default
    ExecutionStrategy recommendedExecutionStrategy = ExecutionStrategy.MARKET_ORDER;

    // Feedback
    @Builder.Default
    List<String> blockers = Collections.emptyList();

    @Builder.Default
    List<String> warnings = Collections.emptyList();

    @Builder.Default
    List<String> recommendations = Collections.emptyList();

    // Human-readable summary
    @Builder.Default
    String humanReadableSummary = "";

    /**
     * Quick method to check if trade can proceed.
     */
    public boolean canProceed() {
        return approved && isEmpty(blockers);
    }

    /**
     * Get all feedback as a single formatted string.
     */
    public String getAllFeedback() {
        StringBuilder sb = new StringBuilder();

        if (!isEmpty(blockers)) {
            sb.append("BLOCKERS (CANNOT TRADE):\n");
            for (String blocker : blockers) {
                if (blocker != null && !blocker.isBlank()) {
                    sb.append("  ✗ ").append(blocker).append("\n");
                }
            }
            sb.append("\n");
        }

        if (!isEmpty(warnings)) {
            sb.append("WARNINGS:\n");
            for (String warning : warnings) {
                if (warning != null && !warning.isBlank()) {
                    sb.append("  ⚠ ").append(warning).append("\n");
                }
            }
            sb.append("\n");
        }

        if (!isEmpty(recommendations)) {
            sb.append("RECOMMENDATIONS:\n");
            for (String recommendation : recommendations) {
                if (recommendation != null && !recommendation.isBlank()) {
                    sb.append("  ✓ ").append(recommendation).append("\n");
                }
            }
        }

        String feedback = sb.toString().trim();

        if (!feedback.isBlank()) {
            return feedback;
        }

        if (humanReadableSummary != null && !humanReadableSummary.isBlank()) {
            return humanReadableSummary;
        }

        return approved
                ? "Trade approved. No blockers or warnings."
                : "Trade rejected. No detailed feedback was provided.";
    }

    public static RiskDecision approved(
            String approvalReason,
            double finalPositionSize,
            double finalLeverage) {
        return RiskDecision.builder()
                .approved(true)
                .decisionType(RiskDecisionType.APPROVE)
                .approvalReason(nullToEmpty(approvalReason))
                .finalPositionSize(finalPositionSize)
                .finalLeverage(finalLeverage)
                .riskMultiplier(1.0)
                .blockers(Collections.emptyList())
                .warnings(Collections.emptyList())
                .recommendations(Collections.emptyList())
                .humanReadableSummary(nullToEmpty(approvalReason))
                .build();
    }

    public static RiskDecision rejected(String reason) {
        List<String> blockers = new ArrayList<>();

        if (reason != null && !reason.isBlank()) {
            blockers.add(reason);
        }

        return RiskDecision.builder()
                .approved(false)
                .decisionType(RiskDecisionType.REJECT)
                .approvalReason(nullToEmpty(reason))
                .finalPositionSize(0.0)
                .finalLeverage(0.0)
                .riskMultiplier(0.0)
                .expectedValue(0.0)
                .portfolioHeat(0.0)
                .estimatedSlippage(0.0)
                .blockers(blockers)
                .warnings(Collections.emptyList())
                .recommendations(Collections.emptyList())
                .humanReadableSummary(nullToEmpty(reason))
                .build();
    }

    public static RiskDecision rejected(List<String> blockers) {
        List<String> safeBlockers = safeList(blockers);

        String reason = safeBlockers.isEmpty()
                ? "Trade rejected by risk management."
                : String.join("; ", safeBlockers);

        return RiskDecision.builder()
                .approved(false)
                .decisionType(RiskDecisionType.REJECT)
                .approvalReason(reason)
                .finalPositionSize(0.0)
                .finalLeverage(0.0)
                .riskMultiplier(0.0)
                .expectedValue(0.0)
                .portfolioHeat(0.0)
                .estimatedSlippage(0.0)
                .blockers(safeBlockers)
                .warnings(Collections.emptyList())
                .recommendations(Collections.emptyList())
                .humanReadableSummary(reason)
                .build();
    }

    public static RiskDecision approvedWithWarnings(
            String approvalReason,
            double finalPositionSize,
            double finalLeverage,
            List<String> warnings,
            List<String> recommendations) {
        return RiskDecision.builder()
                .approved(true)
                .decisionType(RiskDecisionType.APPROVE)
                .approvalReason(nullToEmpty(approvalReason))
                .finalPositionSize(finalPositionSize)
                .finalLeverage(finalLeverage)
                .riskMultiplier(1.0)
                .blockers(Collections.emptyList())
                .warnings(safeList(warnings))
                .recommendations(safeList(recommendations))
                .humanReadableSummary(nullToEmpty(approvalReason))
                .build();
    }

    private static boolean isEmpty(List<String> value) {
        return value == null || value.isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> cleaned = new ArrayList<>();

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                cleaned.add(value);
            }
        }

        if (cleaned.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(cleaned);
    }

    // Explicit getters because Lombok @Value may not be invoked in your build/IDE.

}