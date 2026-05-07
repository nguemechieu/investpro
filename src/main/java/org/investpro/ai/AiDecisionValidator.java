package org.investpro.ai;

import org.jetbrains.annotations.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.ExecutionStrategy;

/**
 * Enforces guardrails on AI decisions.
 *
 * Ensures that:
 * - AI cannot approve if RiskDecision has blockers
 * - AI cannot increase position size beyond RiskDecision.finalPositionSize
 * - AI cannot increase leverage beyond RiskDecision.finalLeverage
 * - AI cannot use MARKET execution for illiquid assets
 * - AI cannot approve if capital protection is NONE and psychology is
 * IMPULSIVE/FEARFUL
 * - Invalid AI output becomes ESCALATE_TO_MANUAL_REVIEW
 *
 * The AI layer operates within these constraints, and any violation results in
 * safe degradation.
 */
@Slf4j
public class AiDecisionValidator {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AiDecisionValidator.class);

    /**
     * Validate an AI response against guardrails.
     * If any guardrail is violated, adjust the decision to be safer.
     *
     * @param request    The original trade request
     * @param aiResponse The AI's response
     * @return The validated response (may be modified for safety)
     */
    public static AiTradeReviewResponse validateAndEnforce(AiTradeReviewRequest request,
            AiTradeReviewResponse aiResponse) {
        if (request == null || aiResponse == null) {
            return AiTradeReviewResponse.incompleteDataResponse("Invalid request or response");
        }

        // Collect all violations
        StringBuilder violations = new StringBuilder();
        AiTradeReviewResponse validatedResponse = aiResponse;

        // 1. Check: AI cannot approve if RiskDecision has blockers
        if (hasRiskBlockers(request)) {
            if (aiResponse.getDecision() == AiDecision.APPROVE ||
                    aiResponse.getDecision() == AiDecision.APPROVE_WITH_REDUCED_SIZE) {
                logger.warn("AI attempted to approve trade with risk blockers. Escalating to manual review.");
                violations.append("Risk blockers exist. ");
                validatedResponse = escalateToManualReview(aiResponse, "Risk blockers must be addressed");
            }
        }

        // 2. Check: AI cannot increase position size
        if (aiResponse.getSuggestedPositionSize() > request.getRiskDecision().getFinalPositionSize()) {
            logger.warn("AI attempted to increase position size beyond limit. Capping to RiskDecision limit.");
            violations.append("Position size exceeded. ");
            validatedResponse = capPositionSize(validatedResponse, request.getRiskDecision().getFinalPositionSize());
        }

        // 3. Check: AI cannot increase leverage
        if (aiResponse.getSuggestedRiskMultiplier() > 1.0) {
            logger.warn("AI suggested risk multiplier > 1.0. Capping to 1.0");
            violations.append("Risk multiplier exceeded. ");
            validatedResponse = capRiskMultiplier(validatedResponse, 1.0);
        }

        // 4. Check: Cannot use MARKET order for illiquid assets
        if (isIlliquid(request) && ExecutionStrategy.MARKET_ORDER.name()
                .equalsIgnoreCase(aiResponse.getRecommendedExecutionStrategy())) {
            logger.warn("AI recommended MARKET order for illiquid asset. Forcing LIMIT order.");
            violations.append("Market order not viable for illiquid assets. ");
            validatedResponse = overrideExecutionStrategy(validatedResponse, ExecutionStrategy.LIMIT_ORDER.name());
        }

        // 5. Check: Cannot approve if capital protection is NONE and psychology is
        // IMPULSIVE/FEARFUL
        if (hasCapitalProtectionRisk(request)) {
            if (aiResponse.getDecision() == AiDecision.APPROVE) {
                logger.warn("AI approved trade with capital protection risk. Escalating.");
                violations.append("Capital protection risk detected. ");
                validatedResponse = escalateToManualReview(aiResponse,
                        "Capital protection risk requires manual review");
            }
        }

        // 6. Check: AI response validity (NaN, null values, etc.)
        if (!isResponseValid(validatedResponse)) {
            logger.warn("AI response contains invalid values. Escalating to manual review.");
            violations.append("Invalid response data. ");
            validatedResponse = escalateToManualReview(aiResponse, "AI response contains invalid values");
        }

        // Log if any violations were found and corrected
        if (!violations.isEmpty()) {
            logger.info("AI response guardrails enforced: {}", violations);
        }

        return validatedResponse;
    }

    /**
     * Check if RiskDecision has any blockers.
     */
    private static boolean hasRiskBlockers(AiTradeReviewRequest request) {
        if (request.getRiskDecision() == null) {
            return true;
        }
        return request.getRiskDecision().getBlockers() != null &&
                !request.getRiskDecision().getBlockers().isEmpty();
    }

    /**
     * Check if asset is illiquid.
     */
    private static boolean isIlliquid(AiTradeReviewRequest request) {
        if (request.getRiskContext() == null || request.getRiskContext().getLiquidityProfile() == null) {
            return false;
        }
        String liquidity = request.getRiskContext().getLiquidityProfile().toString().toUpperCase();
        return liquidity.contains("ILLIQUID") || liquidity.contains("THIN");
    }

    /**
     * Check if there's a capital protection risk (no protection + impulsive/fearful
     * psychology).
     */
    private static boolean hasCapitalProtectionRisk(AiTradeReviewRequest request) {
        if (request.getRiskContext() == null) {
            return false;
        }

        String capitalProtection = request.getRiskContext().getCapitalProtection().toString().toUpperCase();
        String psychology = request.getRiskContext().getPsychologyProfile().toString().toUpperCase();

        return capitalProtection.contains("NONE") &&
                (psychology.contains("IMPULSIVE") || psychology.contains("FEARFUL"));
    }

    /**
     * Check if response contains valid data.
     */
    private static boolean isResponseValid(AiTradeReviewResponse response) {
        if (response == null || response.getDecision() == null) {
            return false;
        }

        // Check for NaN or invalid confidence
        if (Double.isNaN(response.getConfidence()) || response.getConfidence() < 0.0
                || response.getConfidence() > 1.0) {
            return false;
        }

        // Check for NaN or invalid risk multiplier
        if (Double.isNaN(response.getSuggestedRiskMultiplier()) || response.getSuggestedRiskMultiplier() < 0.0) {
            return false;
        }

        // Check for NaN or invalid position size
        return !Double.isNaN(response.getSuggestedPositionSize()) && !(response.getSuggestedPositionSize() < 0.0);
    }

    /**
     * Cap position size to maximum allowed.
     */
    private static AiTradeReviewResponse capPositionSize(AiTradeReviewResponse response, double maxSize) {
        return AiTradeReviewResponse.builder()
                .decision(response.getDecision())
                .confidence(response.getConfidence())
                .suggestedRiskMultiplier(response.getSuggestedRiskMultiplier())
                .suggestedPositionSize(maxSize)
                .recommendedExecutionStrategy(response.getRecommendedExecutionStrategy())
                .confirmations(response.getConfirmations())
                .concerns(response.getConcerns())
                .blockers(response.getBlockers())
                .recommendations(response.getRecommendations())
                .explanation(response.getExplanation())
                .modelName(response.getModelName())
                .createdAt(response.getCreatedAt())
                .processingTimeMs(response.getProcessingTimeMs())
                .hadErrors(response.isHadErrors())
                .errorMessage(response.getErrorMessage())
                .build();
    }

    /**
     * Cap risk multiplier to maximum allowed.
     */
    private static AiTradeReviewResponse capRiskMultiplier(AiTradeReviewResponse response, double maxMultiplier) {
        return AiTradeReviewResponse.builder()
                .decision(response.getDecision())
                .confidence(response.getConfidence())
                .suggestedRiskMultiplier(maxMultiplier)
                .suggestedPositionSize(response.getSuggestedPositionSize())
                .recommendedExecutionStrategy(response.getRecommendedExecutionStrategy())
                .confirmations(response.getConfirmations())
                .concerns(response.getConcerns())
                .blockers(response.getBlockers())
                .recommendations(response.getRecommendations())
                .explanation(response.getExplanation())
                .modelName(response.getModelName())
                .createdAt(response.getCreatedAt())
                .processingTimeMs(response.getProcessingTimeMs())
                .hadErrors(response.isHadErrors())
                .errorMessage(response.getErrorMessage())
                .build();
    }

    /**
     * Override execution strategy.
     */
    private static AiTradeReviewResponse overrideExecutionStrategy(@NotNull AiTradeReviewResponse response,
            String newStrategy) {
        return AiTradeReviewResponse.builder()
                .decision(response.getDecision())
                .confidence(response.getConfidence())
                .suggestedRiskMultiplier(response.getSuggestedRiskMultiplier())
                .suggestedPositionSize(response.getSuggestedPositionSize())
                .recommendedExecutionStrategy(newStrategy)
                .confirmations(response.getConfirmations())
                .concerns(response.getConcerns())
                .blockers(response.getBlockers())
                .recommendations(response.getRecommendations())
                .explanation(response.getExplanation())
                .modelName(response.getModelName())
                .createdAt(response.getCreatedAt())
                .processingTimeMs(response.getProcessingTimeMs())
                .hadErrors(response.isHadErrors())
                .errorMessage(response.getErrorMessage())
                .build();
    }

    /**
     * Escalate decision to manual review with reason.
     */
    private static AiTradeReviewResponse escalateToManualReview(AiTradeReviewResponse response, String reason) {
        return AiTradeReviewResponse.builder()
                .decision(AiDecision.ESCALATE_TO_MANUAL_REVIEW)
                .confidence(0.0)
                .suggestedRiskMultiplier(0.0)
                .suggestedPositionSize(0.0)
                .confirmations(response.getConfirmations())
                .concerns(response.getConcerns())
                .blockers(response.getBlockers())
                .recommendations(response.getRecommendations())
                .explanation(reason + ". " + response.getExplanation())
                .modelName(response.getModelName())
                .createdAt(response.getCreatedAt())
                .processingTimeMs(response.getProcessingTimeMs())
                .hadErrors(false)
                .build();
    }
}
