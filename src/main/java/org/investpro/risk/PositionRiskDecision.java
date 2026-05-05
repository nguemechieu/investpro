package org.investpro.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Risk decision for an open position.
 * Combines deterministic risk rules with health score to determine if position should remain open.
 * Used by PositionActionFinalGate to authorize AI-recommended actions.
 */
@Builder
@Getter
@Setter
@AllArgsConstructor
public class PositionRiskDecision {
    
    // =========================================================================
    // Primary Decision
    // =========================================================================
    
    /** Can position remain open? (or must it be closed?) */
    private boolean canRemainOpen;
    
    /** Is position within acceptable risk bounds? */
    private boolean withinRiskBounds;
    
    /** Reason for decision */
    private String reason;
    
    // =========================================================================
    // Health Context
    // =========================================================================
    
    /** Position health score (0.0-1.0) */
    private PositionHealthScore healthScore;
    
    // =========================================================================
    // Position Modifications
    // =========================================================================
    
    /** Suggested new stop-loss price (if adjustment recommended) */
    private Double suggestedStopLoss;
    
    /** Suggested new take-profit price (if adjustment recommended) */
    private Double suggestedTakeProfit;
    
    /** Suggested reduction percentage (0.0-1.0) */
    private Double suggestedReductionPercent;
    
    // =========================================================================
    // Constraints & Limits
    // =========================================================================
    
    /** Maximum exposure allowed for this position (as % of account) */
    private double maxAllowedExposurePercent;
    
    /** Current exposure (as % of account) */
    private double currentExposurePercent;
    
    /** Is current exposure within limits? */
    private boolean exposureWithinLimits;
    
    // =========================================================================
    // Feedback
    // =========================================================================
    
    /** Critical issues (position must be closed or reduced) */
    private List<String> blockers;
    
    /** Warnings (position should be monitored or modified) */
    private List<String> warnings;
    
    /** Recommendations (improvements to consider) */
    private List<String> recommendations;
    
    // =========================================================================
    // Deterministic Rules
    // =========================================================================
    
    /** Does a deterministic risk rule require position exit? */
    private boolean deterministicExitRequired;
    
    /** Reason for deterministic exit (if any) */
    private String deterministicExitReason;
    
    /** Allowed AI actions for this position */
    private List<String> allowedAiActions;
    
    /** Disallowed AI actions for this position */
    private List<String> disallowedAiActions;
    
    // =========================================================================
    // Metadata
    // =========================================================================
    
    /** When was this decision made? */
    private LocalDateTime decidedAt;
    
    /** How long is this decision valid? (seconds) */
    private int validForSeconds;
    
    private String decisionSummary;
    
    // =========================================================================
    // Helpers
    // =========================================================================
    
    /**
     * Check if position is in critical condition.
     */
    public boolean isCritical() {
        return !canRemainOpen || deterministicExitRequired || (blockers != null && !blockers.isEmpty());
    }
    
    /**
     * Check if position is safe to hold.
     */
    public boolean isSafe() {
        return canRemainOpen && withinRiskBounds && (blockers == null || blockers.isEmpty());
    }
    
    /**
     * Check if specific AI action is allowed.
     */
    public boolean isActionAllowed(String action) {
        if (disallowedAiActions != null && disallowedAiActions.contains(action)) {
            return false;
        }
        if (allowedAiActions != null && !allowedAiActions.isEmpty()) {
            return allowedAiActions.contains(action);
        }
        return true; // Allow by default if no restrictions
    }
    
    /**
     * Get all feedback as formatted string.
     */
    public String getAllFeedback() {
        StringBuilder sb = new StringBuilder();
        
        if (blockers != null && !blockers.isEmpty()) {
            sb.append("BLOCKERS:\n");
            for (String blocker : blockers) {
                sb.append("  ✗ ").append(blocker).append("\n");
            }
            sb.append("\n");
        }
        
        if (warnings != null && !warnings.isEmpty()) {
            sb.append("WARNINGS:\n");
            for (String warning : warnings) {
                sb.append("  ⚠ ").append(warning).append("\n");
            }
            sb.append("\n");
        }
        
        if (recommendations != null && !recommendations.isEmpty()) {
            sb.append("RECOMMENDATIONS:\n");
            for (String rec : recommendations) {
                sb.append("  ✓ ").append(rec).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Create a safe decision saying position must close.
     */
    public static PositionRiskDecision mustCloseDecision(String reason, PositionHealthScore health) {
        return PositionRiskDecision.builder()
                .canRemainOpen(false)
                .withinRiskBounds(false)
                .reason(reason)
                .healthScore(health)
                .deterministicExitRequired(true)
                .deterministicExitReason(reason)
                .blockers(List.of(reason))
                .warnings(List.of())
                .recommendations(List.of("Close position immediately"))
                .allowedAiActions(List.of("CLOSE_POSITION", "ESCALATE_TO_MANUAL_REVIEW"))
                .disallowedAiActions(List.of("HOLD", "MOVE_TAKE_PROFIT", "MOVE_STOP_LOSS", "TRAIL_STOP"))
                .decidedAt(LocalDateTime.now())
                .validForSeconds(300)
                .decisionSummary("CRITICAL: Position must be closed")
                .build();
    }
    
    /**
     * Create a safe decision saying position is healthy and can stay open.
     */
    public static PositionRiskDecision safeDecision(PositionHealthScore health) {
        return PositionRiskDecision.builder()
                .canRemainOpen(true)
                .withinRiskBounds(true)
                .reason("Position is within risk bounds")
                .healthScore(health)
                .deterministicExitRequired(false)
                .blockers(List.of())
                .warnings(List.of())
                .recommendations(List.of("Continue monitoring"))
                .decidedAt(LocalDateTime.now())
                .validForSeconds(300)
                .decisionSummary("Position is safe to hold")
                .build();
    }
}
