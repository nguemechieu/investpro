package org.investpro.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.ExecutionStrategy;

/**
 * Intent to perform an action on an open position.
 * Created by PositionActionFinalGate after combining:
 * - Position risk decision (deterministic rules)
 * - AI recommendation (validated)
 * - Broker capabilities
 * - Account state
 * - Execution constraints

 * Only approved PositionActionIntent objects are sent to ExecutionEngine.
 * AI cannot directly create this object—it must pass through final gate first.
 */
@Builder
@Getter
@Setter
@AllArgsConstructor
public class PositionActionIntent {
    
    // =========================================================================
    // Identity
    // =========================================================================
    
    /** Unique position ID */
    private String positionId;
    
    /** Symbol being managed */
    private TradePair symbol;
    
    // =========================================================================
    // Action Details
    // =========================================================================
    
    /** What action to perform */
    private AiPositionAction action;
    
    /** Quantity to close (for CLOSE_POSITION or TAKE_PARTIAL_PROFIT) */
    private Double quantityToClose;
    
    /** New stop-loss price (for MOVE_STOP_LOSS, TRAIL_STOP) */
    private Double newStopLoss;
    
    /** New take-profit price (for MOVE_TAKE_PROFIT) */
    private Double newTakeProfit;
    
    // =========================================================================
    // Execution Details
    // =========================================================================
    
    /** How to execute (MARKET, LIMIT, etc.) */
    private ExecutionStrategy executionStrategy;
    
    /** Reason for action (for audit trail) */
    private String reason;
    
    /** Confidence in action (0.0-1.0) */
    private double confidence;
    
    // =========================================================================
    // Approval
    // =========================================================================
    
    /** Has this action been approved by FinalRiskGate? */
    private boolean approved;
    
    /** Who approved this action? */
    private String approvedBy;
    
    /** Approval reason/explanation */
    private String approvalReason;
    
    // =========================================================================
    // Risk Context
    // =========================================================================
    
    /** Position risk at time of approval */
    private double estimatedRiskPercent;
    
    /** Estimated exposure reduction (if applicable) */
    private Double estimatedExposureReduction;
    
    /** Estimated P&L impact */
    private Double estimatedPnlImpact;
    
    // =========================================================================
    // Metadata
    // =========================================================================
    
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime approvedAt;
    private String source; // "AI", "MANUAL", "AUTOMATIC", etc.
    private double trailingStopDistance;

    // =========================================================================
    // Validation
    // =========================================================================
    
    /**
     * Check if intent is valid and can be executed.
     */
    public boolean isValid() {
        return positionId != null && !positionId.isBlank()
                && symbol != null
                && action != null
                && approved
                && executionStrategy != null;
    }
    
    /**
     * Check if this is a risky action.
     */
    public boolean isRiskyAction() {
        return switch (action) {
            case CLOSE_POSITION, MOVE_STOP_LOSS, HEDGE -> true;
            case REDUCE_SIZE, TAKE_PARTIAL_PROFIT, TRAIL_STOP, MOVE_TAKE_PROFIT, HOLD, ESCALATE_TO_MANUAL_REVIEW -> false;
        };
    }
    
    /**
     * Get a summary of the intent.
     */
    public String getSummary() {
        String summary = "%s %s @ %.2f%%".formatted(
                action.getDescription(),
                symbol,
                confidence * 100);

        if (quantityToClose != null && quantityToClose > 0) {
            summary += " (qty: %.2f)".formatted(quantityToClose);
        }

        if (newStopLoss != null) {
            summary += " (stop: $%.2f)".formatted(newStopLoss);
        }

        if (newTakeProfit != null) {
            summary += " (TP: $%.2f)".formatted(newTakeProfit);
        }

        return summary;
    }

    public double getSuggestedStopLoss() {
        return newStopLoss != null ? newStopLoss : 0.0;
    }

    public double getSuggestedTakeProfit() {
        return newTakeProfit != null ? newTakeProfit : 0.0;
    }

    public double getSuggestedCloseQuantity() {
        return  quantityToClose != null ? quantityToClose : 0.0;
    }

    public double getTrailingDistance() {
        return  trailingStopDistance;
    }
}
