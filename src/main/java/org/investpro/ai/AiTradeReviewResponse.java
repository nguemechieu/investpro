package org.investpro.ai;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Immutable response object returned by AiReasoningService after reviewing a
 * trade.
 * Contains the AI's decision, confidence level, and detailed reasoning.
 * <p>
 * The AI NEVER places trades directly. This response feeds into FinalRiskGate
 * for final authority.
 */
@Slf4j
@Value
@Builder
@Data
public class AiTradeReviewResponse {
    // =========================================================================
    // Decision
    // =========================================================================

    /**
     * AI decision: APPROVE, APPROVE_WITH_REDUCED_SIZE, WAIT, REJECT, or
     * ESCALATE_TO_MANUAL_REVIEW
     */
    AiDecision decision;

    /** Confidence in decision (0.0-1.0, where 1.0 is maximum confidence) */
    double confidence;

    // =========================================================================
    // Risk Adjustments (if applicable)
    // =========================================================================

    /**
     * Suggested risk multiplier (0.5 = reduce to 50% of normal, 1.0 = keep as-is,
     * >1.0 not allowed)
     */
    double suggestedRiskMultiplier;

    /**
     * Suggested position size (if reduced, must be <=
     * RiskDecision.finalPositionSize)
     */
    double suggestedPositionSize;

    /**
     * Recommended execution strategy (e.g., LIMIT_ORDER instead of MARKET_ORDER)
     */
    String recommendedExecutionStrategy;

    // =========================================================================
    // Reasoning
    // =========================================================================

    /** Confirmations: List of positive factors supporting the decision */
    List<String> confirmations;

    /** Concerns: List of risk factors or concerns identified */
    List<String> concerns;

    /** Blockers: List of hard blockers that prevent approval (if any) */
    List<String> blockers;

    /** Recommendations: List of specific recommendations for the trader */
    List<String> recommendations;

    /** Detailed explanation of the decision in natural language */
    String explanation;

    // =========================================================================
    // Metadata
    // =========================================================================

    /** AI model name used (e.g., "gpt-4-turbo", "local-fallback") */
    String modelName;

    /** Timestamp when response was created */
    LocalDateTime createdAt;

    /** Processing time in milliseconds */
    long processingTimeMs;

    // =========================================================================
    // Validation & Error Handling
    // =========================================================================

    /** If true, AI processing had issues (invalid JSON, API error, etc.) */
    boolean hadErrors;

    /** Error message if hadErrors is true */
    @Nullable
    String errorMessage;

    /**
     * Create a safe response indicating the AI could not process the request.
     * Used when API is unavailable, request is malformed, or AI returns invalid
     * data.
     */
    public static AiTradeReviewResponse failedResponse(String errorMessage, String modelName) {
        return AiTradeReviewResponse.builder()
                .decision(AiDecision.ESCALATE_TO_MANUAL_REVIEW)
                .confidence(0.0)
                .suggestedRiskMultiplier(0.0)
                .suggestedPositionSize(0.0)
                .confirmations(List.of())
                .concerns(List.of("AI processing failed"))
                .blockers(List.of())
                .recommendations(List.of("Manual review required"))
                .explanation("AI reasoning engine encountered an error: " + errorMessage)
                .modelName(modelName)
                .createdAt(LocalDateTime.now())
                .processingTimeMs(0)
                .hadErrors(true)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * Create a safe response indicating the AI cannot process this trade.
     * Used when required data is missing or trade setup is incomplete.
     */
    public static AiTradeReviewResponse incompleteDataResponse(String reason) {
        return AiTradeReviewResponse.builder()
                .decision(AiDecision.ESCALATE_TO_MANUAL_REVIEW)
                .confidence(0.0)
                .suggestedRiskMultiplier(0.0)
                .suggestedPositionSize(0.0)
                .confirmations(List.of())
                .concerns(List.of("Incomplete or missing data"))
                .blockers(List.of())
                .recommendations(List.of("Provide complete trade context"))
                .explanation("Cannot analyze trade due to incomplete data: " + reason)
                .modelName("none")
                .createdAt(LocalDateTime.now())
                .processingTimeMs(0)
                .hadErrors(true)
                .errorMessage(reason)
                .build();
    }
}
