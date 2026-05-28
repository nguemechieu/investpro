package org.investpro.risk;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
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
@ToString
public class RiskDecision {

    boolean approved;

    @Builder.Default
    String approvalReason = "";

    double finalPositionSize;
    double finalLeverage;

    @Builder.Default
    double riskMultiplier = 1.0;

    double expectedValue;
    double portfolioHeat;
    double estimatedSlippage;

    @Builder.Default
    ExecutionStrategy recommendedExecutionStrategy = ExecutionStrategy.MARKET_ORDER;

    @Builder.Default
    List<String> blockers = Collections.emptyList();

    @Builder.Default
    List<String> warnings = Collections.emptyList();

    @Builder.Default
    List<String> recommendations = Collections.emptyList();

    @Builder.Default
    String humanReadableSummary = "";

    @Builder.Default
    Object sourcePayload = null;

    /**
     * Quick method to check if trade can proceed.
     */
    public boolean canProceed() {
        return approved && isEmpty(blockers);
    }

    /**
     * Alias used by execution/bot code.
     */
    public double getApprovedSize() {
        return canProceed() ? finalPositionSize : 0.0;
    }

    /**
     * Combined reasons for UI, logs, bot explanations, and journals.
     */
    public List<String> getReasons() {
        List<String> reasons = new ArrayList<>();

        if (!isBlank(approvalReason)) {
            reasons.add(approvalReason);
        }

        reasons.addAll(safeList(blockers));
        reasons.addAll(safeList(warnings));
        reasons.addAll(safeList(recommendations));

        if (reasons.isEmpty() && !isBlank(humanReadableSummary)) {
            reasons.add(humanReadableSummary);
        }

        if (reasons.isEmpty()) {
            reasons.add(approved
                    ? "Trade approved."
                    : "Trade rejected.");
        }

        return Collections.unmodifiableList(reasons);
    }

    /**
     * Get all feedback as a single formatted string.
     */
    public String getAllFeedback() {
        StringBuilder sb = new StringBuilder();

        if (!isEmpty(blockers)) {
            sb.append("BLOCKERS (CANNOT TRADE):\n");
            for (String blocker : blockers) {
                if (!isBlank(blocker)) {
                    sb.append("  ✗ ").append(blocker).append("\n");
                }
            }
            sb.append("\n");
        }

        if (!isEmpty(warnings)) {
            sb.append("WARNINGS:\n");
            for (String warning : warnings) {
                if (!isBlank(warning)) {
                    sb.append("  ⚠ ").append(warning).append("\n");
                }
            }
            sb.append("\n");
        }

        if (!isEmpty(recommendations)) {
            sb.append("RECOMMENDATIONS:\n");
            for (String recommendation : recommendations) {
                if (!isBlank(recommendation)) {
                    sb.append("  ✓ ").append(recommendation).append("\n");
                }
            }
        }

        String feedback = sb.toString().trim();

        if (!feedback.isBlank()) {
            return feedback;
        }

        if (!isBlank(humanReadableSummary)) {
            return humanReadableSummary;
        }

        return approved
                ? "Trade approved. No blockers or warnings."
                : "Trade rejected. No detailed feedback was provided.";
    }

    public static RiskDecision approved(
            String approvalReason,
            double finalPositionSize,
            double finalLeverage
    ) {
        return RiskDecision.builder()
                .approved(true)
                .approvalReason(nullToEmpty(approvalReason))
                .finalPositionSize(Math.max(0.0, finalPositionSize))
                .finalLeverage(Math.max(0.0, finalLeverage))
                .riskMultiplier(1.0)
                .blockers(Collections.emptyList())
                .warnings(Collections.emptyList())
                .recommendations(Collections.emptyList())
                .humanReadableSummary(nullToEmpty(approvalReason))
                .build();
    }

    public static RiskDecision rejected(String reason) {
        List<String> blockers = new ArrayList<>();

        if (!isBlank(reason)) {
            blockers.add(reason);
        }

        String safeReason = isBlank(reason)
                ? "Trade rejected by risk management."
                : reason.trim();

        return RiskDecision.builder()
                .approved(false)
                .approvalReason(safeReason)
                .finalPositionSize(0.0)
                .finalLeverage(0.0)
                .riskMultiplier(0.0)
                .expectedValue(0.0)
                .portfolioHeat(0.0)
                .estimatedSlippage(0.0)
                .blockers(Collections.unmodifiableList(blockers))
                .warnings(Collections.emptyList())
                .recommendations(Collections.emptyList())
                .humanReadableSummary(safeReason)
                .build();
    }

    public static RiskDecision rejected(List<String> blockers) {
        List<String> safeBlockers = safeList(blockers);

        String reason = safeBlockers.isEmpty()
                ? "Trade rejected by risk management."
                : String.join("; ", safeBlockers);

        return RiskDecision.builder()
                .approved(false)
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
            List<String> recommendations
    ) {
        return RiskDecision.builder()
                .approved(true)
                .approvalReason(nullToEmpty(approvalReason))
                .finalPositionSize(Math.max(0.0, finalPositionSize))
                .finalLeverage(Math.max(0.0, finalLeverage))
                .riskMultiplier(1.0)
                .blockers(Collections.emptyList())
                .warnings(safeList(warnings))
                .recommendations(safeList(recommendations))
                .humanReadableSummary(nullToEmpty(approvalReason))
                .build();
    }

    public static RiskDecision rejectedFromPayload(String reason, Object sourcePayload) {
        return RiskDecision.builder()
                .approved(false)
                .approvalReason(nullToEmpty(reason))
                .finalPositionSize(0.0)
                .finalLeverage(0.0)
                .riskMultiplier(0.0)
                .blockers(safeList(List.of(nullToEmpty(reason))))
                .warnings(Collections.emptyList())
                .recommendations(Collections.emptyList())
                .humanReadableSummary(nullToEmpty(reason))
                .sourcePayload(sourcePayload)
                .build();
    }

    public RiskDecision withSourcePayload(Object payload) {
        return RiskDecision.builder()
                .approved(approved)
                .approvalReason(approvalReason)
                .finalPositionSize(finalPositionSize)
                .finalLeverage(finalLeverage)
                .riskMultiplier(riskMultiplier)
                .expectedValue(expectedValue)
                .portfolioHeat(portfolioHeat)
                .estimatedSlippage(estimatedSlippage)
                .recommendedExecutionStrategy(recommendedExecutionStrategy)
                .blockers(safeList(blockers))
                .warnings(safeList(warnings))
                .recommendations(safeList(recommendations))
                .humanReadableSummary(humanReadableSummary)
                .sourcePayload(payload)
                .build();
    }

    private static boolean isEmpty(List<String> value) {
        return value == null || value.isEmpty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> cleaned = new ArrayList<>();

        for (String value : values) {
            if (!isBlank(value)) {
                cleaned.add(value.trim());
            }
        }

        if (cleaned.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(cleaned);
    }
}