package org.investpro.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response object returned by AiPositionManagerService after reviewing an open
 * position.
 * Contains AI recommendation, confidence, and detailed reasoning about the
 * position.
 *
 * The AI NEVER modifies positions directly. This response feeds into
 * PositionActionValidator
 * and PositionActionFinalGate for validation and final authority before
 * execution.
 */
@Slf4j
@Builder
@Getter
@Setter
@AllArgsConstructor
public class AiPositionManagementResponse {

    // =========================================================================
    // AI Decision
    // =========================================================================

    /** Recommended position action (HOLD, REDUCE_SIZE, CLOSE, etc.) */
    private AiPositionAction action;

    /** Confidence in recommendation (0.0-1.0) */
    private double confidence;

    // =========================================================================
    // Action-Specific Suggestions
    // =========================================================================

    /** Suggested stop-loss price (if MOVE_STOP_LOSS or defensive) */
    @Nullable
    private Double suggestedStopLoss;

    /** Suggested take-profit price (if MOVE_TAKE_PROFIT or action modifies TP) */
    @Nullable
    private Double suggestedTakeProfit;

    /** Quantity to close (if CLOSE_POSITION or TAKE_PARTIAL_PROFIT) */
    @Nullable
    private Double suggestedCloseQuantity;

    /**
     * Percentage reduction recommended (0.1 = reduce by 10%, 1.0 = close entirely)
     */
    @Nullable
    private Double suggestedRiskReductionPercent;

    // =========================================================================
    // Reasoning
    // =========================================================================

    /** Positive factors supporting this recommendation */
    private List<String> confirmations;

    /** Risk factors or concerns identified */
    private List<String> concerns;

    /** Hard blockers that prevent other actions */
    private List<String> blockers;

    /** Specific recommendations for the trader */
    private List<String> recommendations;

    /** Detailed explanation in natural language */
    private String explanation;

    // =========================================================================
    // Metadata
    // =========================================================================

    /** AI model name (e.g., "gpt-4-turbo", "local-fallback") */
    private String modelName;

    /** Timestamp when response was created */
    private LocalDateTime createdAt;

    /** Processing time in milliseconds */
    private long processingTimeMs;

    // =========================================================================
    // Error Handling
    // =========================================================================

    /** If true, AI processing had issues (API error, invalid JSON, etc.) */
    private boolean hadErrors;

    /** Error message if hadErrors is true */
    @Nullable
    private String errorMessage;

    // =========================================================================
    // Factory Methods
    // =========================================================================

    /**
     * Create a safe response indicating the AI could not process the request.
     * Used when API is unavailable, request is malformed, or AI returns invalid
     * data.
     */
    public static AiPositionManagementResponse failedResponse(String errorMessage, String modelName) {
        return AiPositionManagementResponse.builder()
                .action(AiPositionAction.ESCALATE_TO_MANUAL_REVIEW)
                .confidence(0.0)
                .confirmations(List.of())
                .concerns(List.of("AI processing failed"))
                .blockers(List.of(errorMessage))
                .recommendations(List.of("Manual review required"))
                .explanation("AI position manager encountered an error and cannot provide recommendation.")
                .modelName(modelName)
                .createdAt(LocalDateTime.now())
                .hadErrors(true)
                .errorMessage(errorMessage)
                .processingTimeMs(0)
                .build();
    }

    /**
     * Create a safe HOLD recommendation with no changes.
     * Used as default when no action is recommended.
     */
    public static AiPositionManagementResponse holdRecommendation(String reason, String modelName) {
        return AiPositionManagementResponse.builder()
                .action(AiPositionAction.HOLD)
                .confidence(0.8)
                .confirmations(List.of(reason))
                .concerns(List.of())
                .blockers(List.of())
                .recommendations(List.of("Continue monitoring position", "Review on next signal"))
                .explanation("Position is healthy. No action recommended at this time.")
                .modelName(modelName)
                .createdAt(LocalDateTime.now())
                .hadErrors(false)
                .processingTimeMs(0)
                .build();
    }

    /**
     * Validate that critical fields are set.
     * 
     * @return true if response is valid and actionable
     */
    public boolean isValid() {
        return action != null
                && confidence >= 0.0 && confidence <= 1.0
                && confirmations != null
                && concerns != null
                && !explanation.isBlank();
    }

    /**
     * Get a summary of the recommendation.
     */
    public String getSummary() {
        return "Action: %s, Confidence: %.1f%%, Blockers: %d, Concerns: %d".formatted(
                action,
                confidence * 100,
                blockers != null ? blockers.size() : 0,
                concerns != null ? concerns.size() : 0);
    }
}
