package org.investpro.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Audit record for open position review by AI.
 * Stored in JSONL format for compliance and performance analysis.
 */
@Builder
@Getter
@Setter
@AllArgsConstructor
public class PositionAuditRecord {
    
    private String auditId;
    private LocalDateTime timestamp;
    
    // Position Context
    private String positionId;
    private String symbol;
    private String side;
    private double quantity;
    private double entryPrice;
    private double currentPrice;
    private double unrealizedPnlPercent;
    private long positionAgeMinutes;
    
    // Account State
    private double accountEquity;
    private double portfolioHeatPercent;
    private double currentDrawdownPercent;
    private Double currentStopLoss;
    private Double currentTakeProfit;
    
    // Risk Decision
    private boolean riskCanRemainOpen;
    private boolean riskWithinBounds;
    private int riskBlockersCount;
    private boolean deterministicExitRequired;
    
    // AI Response
    private String aiAction;
    private double aiConfidence;
    private Double aiSuggestedStopLoss;
    private Double aiSuggestedTakeProfit;
    private Double aiSuggestedCloseQuantity;
    private int aiConcernsCount;
    private int aiBlockersCount;
    private String aiModelName;
    private long aiProcessingTimeMs;
    
    // Final Gate Decision
    private boolean actionApproved;
    private String actionExecutionStrategy;
    private String actionReason;
}
