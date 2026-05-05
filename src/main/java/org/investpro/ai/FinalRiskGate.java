package org.investpro.ai;

import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.investpro.risk.RiskDecision;

import java.time.LocalDateTime;

/**
 * Final risk gate that combines RiskManagementSystem and AI reasoning decisions.
 *
 * FinalRiskGate is the ultimate authority on whether a trade can execute.
 * It enforces the rule: Deterministic risk blockers ALWAYS win.
 *
 * Logic:
 * 1. If RiskDecision has blockers → REJECT (even if AI approves)
 * 2. If AI says REJECT → REJECT (override trading logic)
 * 3. If AI says WAIT → DO_NOT_SEND_ORDER (user can retry)
 * 4. If AI says ESCALATE_TO_MANUAL_REVIEW → DO_NOT_SEND_ORDER (await manual decision)
 * 5. If RiskDecision approves AND AI approves → APPROVE (all checks pass)
 *
 * No order is ever sent unless both RiskDecision and AI agree.
 */
public class FinalRiskGate {

    private static final Logger logger = LoggerFactory.getLogger(FinalRiskGate.class);

    public enum OrderApprovalStatus {
        /** Order is approved for execution */
        APPROVED,

        /** Order is rejected due to risk blockers */
        REJECTED,

        /** Order should wait for better conditions */
        WAIT,

        /** Order requires manual human review */
        ESCALATE_TO_MANUAL_REVIEW
    }

    /**
     * Make final decision on whether to approve an order.
     * Combines RiskDecision (deterministic guardrails) and AiTradeReviewResponse (AI reasoning).
     *
     * @param riskDecision From RiskManagementSystem
     * @param aiResponse From AiReasoningService
     * @return Final approval status and reasoning
     */
    public static OrderApprovalDecision makeDecision(RiskDecision riskDecision, AiTradeReviewResponse aiResponse) {
        if (riskDecision == null || aiResponse == null) {
            return OrderApprovalDecision.rejected(
                    "Invalid decision objects",
                    "RiskDecision or AiResponse was null"
            );
        }

        // Rule 1: Deterministic risk blockers ALWAYS win
        if (riskDecision.getBlockers() != null && !riskDecision.getBlockers().isEmpty()) {
            logger.warn("RiskManagementSystem has hard blockers. Order rejected.");
            return OrderApprovalDecision.rejected(
                    "Hard blockers from RiskManagementSystem",
                    "Risk blockers: %s".formatted(String.join("; ", riskDecision.getBlockers()))
            );
        }

        // Rule 2: If AI says REJECT, respect it
        if (aiResponse.getDecision() == AiDecision.REJECT) {
            logger.info("AI rejected trade. Order rejected.");
            return OrderApprovalDecision.rejected(
                    "AI reasoning rejected the trade",
                    aiResponse.getExplanation()
            );
        }

        // Rule 3: If AI says WAIT, don't send order
        if (aiResponse.getDecision() == AiDecision.WAIT) {
            logger.info("AI recommends waiting. Order not sent.");
            return OrderApprovalDecision.wait(
                    "Market conditions not optimal",
                    aiResponse.getExplanation()
            );
        }

        // Rule 4: If AI says ESCALATE_TO_MANUAL_REVIEW, don't send order
        if (aiResponse.getDecision() == AiDecision.ESCALATE_TO_MANUAL_REVIEW) {
            logger.info("AI escalated to manual review. Order not sent.");
            return OrderApprovalDecision.escalate(
                    "AI requires manual review",
                    aiResponse.getExplanation()
            );
        }

        // Rule 5: If RiskDecision is not approved, don't send order
        if (!riskDecision.canProceed()) {
            logger.warn("RiskDecision indicated trade cannot proceed. Order rejected.");
            return OrderApprovalDecision.rejected(
                    "RiskDecision cannot proceed",
                    "Risk assessment indicated trade should not proceed"
            );
        }

        // Rule 6: Both RiskDecision and AI must approve
        if (riskDecision.canProceed() &&
            (aiResponse.getDecision() == AiDecision.APPROVE ||
             aiResponse.getDecision() == AiDecision.APPROVE_WITH_REDUCED_SIZE)) {
            logger.info("Both RiskManagementSystem and AI approve. Order approved for execution.");
            return OrderApprovalDecision.approved(
                    "All risk checks passed",
                    aiResponse.getExplanation(),
                    aiResponse.getSuggestedPositionSize(),
                    aiResponse.getSuggestedRiskMultiplier(),
                    aiResponse.getRecommendedExecutionStrategy()
            );
        }

        // If we get here, something unexpected happened
        logger.warn("Unexpected gate state: RiskDecision.canProceed={}, AI Decision={}",
                riskDecision.canProceed(), aiResponse.getDecision());
        return OrderApprovalDecision.escalate(
                "Unexpected decision state",
                "Unexpected combination of RiskDecision and AI reasoning states"
        );
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

        private OrderApprovalDecision(OrderApprovalStatus status, String summary, String explanation,
                                     double suggestedPositionSize, double suggestedRiskMultiplier,
                                     String recommendedExecutionStrategy) {
            this.status = status;
            this.summary = summary;
            this.explanation = explanation;
            this.suggestedPositionSize = suggestedPositionSize;
            this.suggestedRiskMultiplier = suggestedRiskMultiplier;
            this.recommendedExecutionStrategy = recommendedExecutionStrategy;
            this.decidedAt = LocalDateTime.now();
        }

        // Explicit getters (Lombok @Getter not being invoked)
        public OrderApprovalStatus getStatus() {
            return status;
        }

        public String getSummary() {
            return summary;
        }

        public String getExplanation() {
            return explanation;
        }

        public double getSuggestedPositionSize() {
            return suggestedPositionSize;
        }

        public double getSuggestedRiskMultiplier() {
            return suggestedRiskMultiplier;
        }

        public String getRecommendedExecutionStrategy() {
            return recommendedExecutionStrategy;
        }

        public LocalDateTime getDecidedAt() {
            return decidedAt;
        }

        public boolean isApproved() {
            return status == OrderApprovalStatus.APPROVED;
        }

        @SuppressWarnings("unused")
        public boolean requiresManualReview() {
            return status == OrderApprovalStatus.ESCALATE_TO_MANUAL_REVIEW;
        }

        @SuppressWarnings("unused")
        public boolean shouldWait() {
            return status == OrderApprovalStatus.WAIT;
        }

        @SuppressWarnings("unused")
        public boolean isRejected() {
            return status == OrderApprovalStatus.REJECTED;
        }

        // Factory methods
        @Contract("_, _, _, _, _ -> new")
        static @NotNull OrderApprovalDecision approved(String summary, String explanation,
                                                       double positionSize, double riskMultiplier,
                                                       String executionStrategy) {
            return new OrderApprovalDecision(OrderApprovalStatus.APPROVED, "%s\nAll risk checks passed".formatted(summary), explanation,
                    positionSize, riskMultiplier, executionStrategy);
        }

        @Contract("_, _ -> new")
        static @NotNull OrderApprovalDecision rejected(String summary, String explanation) {
            return new OrderApprovalDecision(OrderApprovalStatus.REJECTED, summary, explanation, 0.0, 0.0, null);
        }

        @Contract("_, _ -> new")
        static @NotNull OrderApprovalDecision wait(String summary, String explanation) {
            return new OrderApprovalDecision(OrderApprovalStatus.WAIT, "%s\nMarket conditions not optimal".formatted(summary), explanation, 0.0, 0.0, null);
        }

        @Contract("_, _ -> new")
        static @NotNull OrderApprovalDecision escalate(String summary, String explanation) {
            return new OrderApprovalDecision(OrderApprovalStatus.ESCALATE_TO_MANUAL_REVIEW, summary, explanation, 0.0, 0.0, null);
        }
    }
}