package org.investpro.ai;

import lombok.Getter;
import org.investpro.risk.RiskDecision;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Final risk gate that combines deterministic risk and AI reasoning decisions.
 * <p>
 * FinalRiskGate is the final authority on whether a trade can execute.
 * <p>
 * Core rule:
 * Deterministic risk blockers ALWAYS win.
 * <p>
 * Additional safety rule:
 * AI can reduce risk, wait, reject, or escalate.
 * AI cannot increase position size, leverage, or risk beyond RiskDecision limits.
 */
public final class FinalRiskGate {

    private static final Logger logger = LoggerFactory.getLogger(FinalRiskGate.class);

    private FinalRiskGate() {
    }

    public enum OrderApprovalStatus {
        APPROVED,
        REJECTED,
        WAIT,
        ESCALATE_TO_MANUAL_REVIEW
    }

    /**
     * Makes the final decision on whether to approve an order.
     *
     * @param riskDecision deterministic decision from RiskManagementSystem
     * @param aiResponse AI reasoning response
     * @return final approval decision
     */
    public static OrderApprovalDecision makeDecision(
            RiskDecision riskDecision,
            AiTradeReviewResponse aiResponse
    ) {
        if (riskDecision == null) {
            return OrderApprovalDecision.rejected(
                    "Missing RiskDecision",
                    "RiskDecision was null. No order can be approved without deterministic risk validation."
            );
        }

        if (aiResponse == null) {
            return OrderApprovalDecision.escalate(
                    "Missing AI response",
                    "AI response was null. Trade requires manual review or fallback validation."
            );
        }

        List<String> riskBlockers = riskDecision.getBlockers();

        // Rule 1: deterministic risk blockers always win.
        if (riskBlockers != null && !riskBlockers.isEmpty()) {
            logger.warn("RiskManagementSystem has hard blockers. Order rejected.");
            return OrderApprovalDecision.rejected(
                    "Hard blockers from RiskManagementSystem",
                    "Risk blockers: %s".formatted(String.join("; ", riskBlockers))
            );
        }

        AiDecision aiDecision = aiResponse.getDecision();

        if (aiDecision == null) {
            logger.warn("AI decision was null. Escalating to manual review.");
            return OrderApprovalDecision.escalate(
                    "Invalid AI decision",
                    "AI response did not include a valid decision."
            );
        }

        // Rule 2: if deterministic risk says cannot proceed, reject.
        if (!riskDecision.canProceed()) {
            logger.warn("RiskDecision indicated trade cannot proceed. Order rejected.");
            return OrderApprovalDecision.rejected(
                    "RiskDecision cannot proceed",
                    "Risk assessment indicated the trade should not proceed."
            );
        }

        // Rule 3: AI can reject.
        if (aiDecision == AiDecision.REJECT) {
            logger.info("AI rejected trade. Order rejected.");
            return OrderApprovalDecision.rejected(
                    "AI reasoning rejected the trade",
                    safeExplanation(aiResponse)
            );
        }

        // Rule 4: AI can wait.
        if (aiDecision == AiDecision.WAIT) {
            logger.info("AI recommends waiting. Order not sent.");
            return OrderApprovalDecision.wait(
                    "Market conditions not optimal",
                    safeExplanation(aiResponse)
            );
        }

        // Rule 5: AI can escalate.
        if (aiDecision == AiDecision.ESCALATE_TO_MANUAL_REVIEW) {
            logger.info("AI escalated trade to manual review. Order not sent.");
            return OrderApprovalDecision.escalate(
                    "AI requires manual review",
                    safeExplanation(aiResponse)
            );
        }

        // Rule 6: APPROVE or APPROVE_WITH_REDUCED_SIZE can continue, but values must be clamped.
        if (aiDecision == AiDecision.APPROVE || aiDecision == AiDecision.APPROVE_WITH_REDUCED_SIZE) {
            double approvedPositionSize = clampPositionSize(
                    aiResponse.getSuggestedPositionSize(),
                    riskDecision.getFinalPositionSize()
            );

            double approvedRiskMultiplier = clampRiskMultiplier(
                    aiResponse.getSuggestedRiskMultiplier(),
                    riskDecision.getRiskMultiplier()
            );

            String executionStrategy = aiResponse.getRecommendedExecutionStrategy();

            if (approvedPositionSize <= 0) {
                return OrderApprovalDecision.escalate(
                        "Invalid approved position size",
                        "AI/risk approved flow produced a non-positive position size."
                );
            }

            if (aiDecision == AiDecision.APPROVE_WITH_REDUCED_SIZE
                    && approvedPositionSize >= riskDecision.getFinalPositionSize()) {
                logger.warn("AI selected APPROVE_WITH_REDUCED_SIZE but did not reduce size. Clamped to risk-approved size.");
            }

            logger.info("FinalRiskGate approved order. positionSize={}, riskMultiplier={}, executionStrategy={}",
                    approvedPositionSize, approvedRiskMultiplier, executionStrategy);

            return OrderApprovalDecision.approved(
                    "All final risk checks passed",
                    safeExplanation(aiResponse),
                    approvedPositionSize,
                    approvedRiskMultiplier,
                    executionStrategy
            );
        }

        logger.warn("Unexpected gate state: riskCanProceed={}, aiDecision={}",
                riskDecision.canProceed(), aiDecision);

        return OrderApprovalDecision.escalate(
                "Unexpected decision state",
                "Unexpected combination of RiskDecision and AI reasoning states."
        );
    }

    private static double clampPositionSize(double aiSuggestedSize, double riskApprovedSize) {
        if (riskApprovedSize <= 0) {
            return 0.0;
        }

        if (aiSuggestedSize <= 0) {
            return riskApprovedSize;
        }

        return Math.min(aiSuggestedSize, riskApprovedSize);
    }

    private static double clampRiskMultiplier(double aiSuggestedMultiplier, double riskApprovedMultiplier) {
        if (riskApprovedMultiplier <= 0) {
            return 0.0;
        }

        if (aiSuggestedMultiplier <= 0) {
            return riskApprovedMultiplier;
        }

        return Math.min(aiSuggestedMultiplier, riskApprovedMultiplier);
    }

    private static String safeExplanation(AiTradeReviewResponse aiResponse) {
        if (aiResponse == null || aiResponse.getExplanation() == null || aiResponse.getExplanation().isBlank()) {
            return "No AI explanation provided.";
        }

        return aiResponse.getExplanation();
    }

    /**
     * Result of FinalRiskGate decision.
     */
    @Getter
    public static class OrderApprovalDecision {
        private final OrderApprovalStatus status;
        private final String summary;
        private final String explanation;
        private final double suggestedPositionSize;
        private final double suggestedRiskMultiplier;
        private final String recommendedExecutionStrategy;
        private final LocalDateTime decidedAt;

        private OrderApprovalDecision(
                OrderApprovalStatus status,
                String summary,
                String explanation,
                double suggestedPositionSize,
                double suggestedRiskMultiplier,
                String recommendedExecutionStrategy
        ) {
            this.status = status;
            this.summary = summary;
            this.explanation = explanation;
            this.suggestedPositionSize = suggestedPositionSize;
            this.suggestedRiskMultiplier = suggestedRiskMultiplier;
            this.recommendedExecutionStrategy = recommendedExecutionStrategy;
            this.decidedAt = LocalDateTime.now();
        }

        public boolean isApproved() {
            return status == OrderApprovalStatus.APPROVED;
        }

        public boolean requiresManualReview() {
            return status == OrderApprovalStatus.ESCALATE_TO_MANUAL_REVIEW;
        }

        public boolean shouldWait() {
            return status == OrderApprovalStatus.WAIT;
        }

        public boolean isRejected() {
            return status == OrderApprovalStatus.REJECTED;
        }

        @Contract("_, _, _, _, _ -> new")
        static @NotNull OrderApprovalDecision approved(
                String summary,
                String explanation,
                double positionSize,
                double riskMultiplier,
                String executionStrategy
        ) {
            return new OrderApprovalDecision(
                    OrderApprovalStatus.APPROVED,
                    summary,
                    explanation,
                    positionSize,
                    riskMultiplier,
                    executionStrategy
            );
        }

        @Contract("_, _ -> new")
        static @NotNull OrderApprovalDecision rejected(String summary, String explanation) {
            return new OrderApprovalDecision(
                    OrderApprovalStatus.REJECTED,
                    summary,
                    explanation,
                    0.0,
                    0.0,
                    null
            );
        }

        @Contract("_, _ -> new")
        static @NotNull OrderApprovalDecision wait(String summary, String explanation) {
            return new OrderApprovalDecision(
                    OrderApprovalStatus.WAIT,
                    summary,
                    explanation,
                    0.0,
                    0.0,
                    null
            );
        }

        @Contract("_, _ -> new")
        static @NotNull OrderApprovalDecision escalate(String summary, String explanation) {
            return new OrderApprovalDecision(
                    OrderApprovalStatus.ESCALATE_TO_MANUAL_REVIEW,
                    summary,
                    explanation,
                    0.0,
                    0.0,
                    null
            );
        }
    }
}