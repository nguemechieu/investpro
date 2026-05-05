package org.investpro.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Audit record for position action execution.
 * Records what action was taken, whether it succeeded, and the outcome.
 */
@Builder
@Getter
@Setter
@AllArgsConstructor
public class PositionExecutionRecord {
    
    private String auditId;
    private LocalDateTime timestamp;
    
    // Position & Action
    private String positionId;
    private String symbol;
    private String action;
    private Double quantityToClose;
    private Double newStopLoss;
    private Double newTakeProfit;
    private double confidence;
    
    // Approval Info
    private String approvedBy;
    private String reason;
    
    // Execution Result
    private boolean executionSuccess;
    private String executionDetails;
    
    // Optional: Later populated with outcome
    private Double realizedPnL;
    private String outcomeDetails;
    private LocalDateTime completedAt;
}
