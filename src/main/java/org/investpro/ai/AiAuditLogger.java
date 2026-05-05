package org.investpro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.risk.PositionRiskDecision;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Logs all AI trade reviews to a JSONL file for audit trail and analysis.
 * <p>
 * Each line is a complete JSON object representing one audit record.
 * This enables:
 * - Full audit trail for compliance
 * - Performance analysis (AI accuracy, confidence calibration)
 * - Feedback loop for model improvement
 * - Dispute resolution and transparency
 */
@Getter
public class AiAuditLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(AiAuditLogger.class);
    
    private final String logFilePath;
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    /**
     * Create audit logger that writes to specified file path.
     * File is created if it doesn't exist. Appends to existing file.
     *
     * @param logFilePath Path to JSONL audit log file
     */
    public AiAuditLogger(String logFilePath) {
        this.logFilePath = logFilePath;
        this.objectMapper = new ObjectMapper();
        
        // Ensure parent directories exist
        try {
            Files.createDirectories(Paths.get(logFilePath).getParent());
        } catch (IOException e) {
            logger.warn("Failed to create audit log directory: {}", e.getMessage());
        }
    }
    
    /**
     * Log an AI trade review.
     */
    public void logReview(AiTradeReviewRequest request, AiTradeReviewResponse aiResponse,
                         FinalRiskGate.OrderApprovalDecision gateDecision) {
        try {
            AiAuditRecord record = buildAuditRecord(request, aiResponse, gateDecision);
            String jsonLine = objectMapper.writeValueAsString(record);
            
            // Append to JSONL file (one JSON object per line)
            Files.writeString(
                    Paths.get(logFilePath),
                    "%s\n".formatted(jsonLine),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            
            logger.debug("AI trade review logged: {}", record.getAuditId());
            
        } catch (IOException e) {
            logger.error("Failed to write AI audit log: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error building audit record: {}", e.getMessage());
        }
    }
    
    /**
     * Build audit record from request, AI response, and gate decision.
     */
    private AiAuditRecord buildAuditRecord(AiTradeReviewRequest request, 
                                          AiTradeReviewResponse aiResponse,
                                          FinalRiskGate.OrderApprovalDecision gateDecision) {
        String blockersStr = request.getRiskDecision() != null && 
                           request.getRiskDecision().getBlockers() != null ?
                String.join("; ", request.getRiskDecision().getBlockers()) : "";

        assert request.getRiskDecision() != null;
        return AiAuditRecord.builder()
                // Identifiers
                .auditId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                
                // Trade Context
                .symbol(request.getSymbol())
                .strategyName(request.getStrategyName())
                .signalSide(request.getSignalSide())
                .signalConfidence(request.getSignalConfidence())
                
                // Risk Context
                .riskDecisionBlockers(blockersStr)
                .riskDecisionPositionSize(request.getRiskDecision().getFinalPositionSize())
                .riskDecisionLeverage(request.getRiskDecision().getFinalLeverage())
                
                // AI Response
                .aiDecision(aiResponse.getDecision().toString())
                .aiConfidence(aiResponse.getConfidence())
                .aiSuggestedRiskMultiplier(aiResponse.getSuggestedRiskMultiplier())
                .aiSuggestedPositionSize(aiResponse.getSuggestedPositionSize())
                .concernCount(aiResponse.getConcerns() != null ? aiResponse.getConcerns().size() : 0)
                .modelName(aiResponse.getModelName())
                .aiProcessingTimeMs(aiResponse.getProcessingTimeMs())
                .aiHadErrors(aiResponse.isHadErrors())
                
                // Final Gate Decision
                .finalDecision(gateDecision.getStatus().toString())
                .wasApproved(gateDecision.isApproved())
                .rejectionReason(gateDecision.isApproved() ? null : gateDecision.getSummary())
                
                // Account State at Review Time
                .accountEquity(request.getAccountEquity())
                .portfolioHeatPercent(request.getPortfolioHeatPercent())
                .drawdownPercent(request.getCurrentDrawdownPercent())
                
                // Outcome (populated later if trade executes)
                .realizedPnL(null)
                .exitReason(null)
                .userFeedback(null)
                
                .build();
    }
    
    /**
     * Log an open position AI review.
     */
    public void logPositionReview(
            String positionId,
            String symbol,
            AiPositionManagementRequest request,
            AiPositionManagementResponse aiResponse,
            PositionRiskDecision riskDecision,
            PositionActionIntent actionIntent) {
        
        try {
            PositionAuditRecord record = buildPositionAuditRecord(
                    positionId, symbol, request, aiResponse, riskDecision, actionIntent);
            
            String jsonLine = objectMapper.writeValueAsString(record);
            
            // Append to JSONL file
            Files.writeString(
                    Paths.get(logFilePath),
                    "%s\n".formatted(jsonLine),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            
            logger.debug("Position review logged: {}", record.getAuditId());
            
        } catch (IOException e) {
            logger.error("Failed to write position audit log: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error building position audit record: {}", e.getMessage());
        }
    }
    
    /**
     * Log a position action execution.
     */
    public void logPositionAction(
            String positionId,
            String symbol,
            PositionActionIntent actionIntent,
            boolean executionSuccess,
            String executionDetails) {
        
        try {
            PositionExecutionRecord record = PositionExecutionRecord.builder()
                    .auditId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now())
                    .positionId(positionId)
                    .symbol(symbol)
                    .action(actionIntent.getAction().toString())
                    .quantityToClose(actionIntent.getQuantityToClose())
                    .newStopLoss(actionIntent.getNewStopLoss())
                    .newTakeProfit(actionIntent.getNewTakeProfit())
                    .confidence(actionIntent.getConfidence())
                    .approvedBy(actionIntent.getApprovedBy())
                    .reason(actionIntent.getReason())
                    .executionSuccess(executionSuccess)
                    .executionDetails(executionDetails)
                    .build();
            
            String jsonLine = objectMapper.writeValueAsString(record);
            
            Files.writeString(
                    Paths.get(logFilePath),
                    "%s\n".formatted(jsonLine),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            
            logger.debug("Position action logged: {}", record.getAuditId());
            
        } catch (IOException e) {
            logger.error("Failed to write position action log: {}", e.getMessage());
        }
    }
    
    /**
     * Build audit record for position review.
     */
    private PositionAuditRecord buildPositionAuditRecord(
            String positionId,
            String symbol,
            AiPositionManagementRequest request,
            AiPositionManagementResponse aiResponse,
            PositionRiskDecision riskDecision,
            PositionActionIntent actionIntent) {
        
        return PositionAuditRecord.builder()
                .auditId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .positionId(positionId)
                .symbol(symbol)
                .side(request.getSide())
                .quantity(request.getQuantity())
                .entryPrice(request.getEntryPrice())
                .currentPrice(request.getCurrentPrice())
                .unrealizedPnlPercent(request.getUnrealizedPnlPercent())
                .positionAgeMinutes(request.getPositionAgeMinutes())
                
                // Risk Context
                .accountEquity(request.getAccountEquity())
                .portfolioHeatPercent(request.getPortfolioHeat())
                .currentDrawdownPercent(request.getCurrentDrawdown())
                .currentStopLoss(request.getCurrentStopLoss())
                .currentTakeProfit(request.getCurrentTakeProfit())
                
                // Risk Decision
                .riskCanRemainOpen(riskDecision.isCanRemainOpen())
                .riskWithinBounds(riskDecision.isWithinRiskBounds())
                .riskBlockersCount(riskDecision.getBlockers() != null ? riskDecision.getBlockers().size() : 0)
                .deterministicExitRequired(riskDecision.isDeterministicExitRequired())
                
                // AI Response
                .aiAction(aiResponse.getAction().toString())
                .aiConfidence(aiResponse.getConfidence())
                .aiSuggestedStopLoss(aiResponse.getSuggestedStopLoss())
                .aiSuggestedTakeProfit(aiResponse.getSuggestedTakeProfit())
                .aiSuggestedCloseQuantity(aiResponse.getSuggestedCloseQuantity())
                .aiConcernsCount(aiResponse.getConcerns() != null ? aiResponse.getConcerns().size() : 0)
                .aiBlockersCount(aiResponse.getBlockers() != null ? aiResponse.getBlockers().size() : 0)
                .aiModelName(aiResponse.getModelName())
                .aiProcessingTimeMs(aiResponse.getProcessingTimeMs())
                
                // Final Gate Decision
                .actionApproved(actionIntent != null && actionIntent.isApproved())
                .actionExecutionStrategy(actionIntent != null ? actionIntent.getExecutionStrategy().toString() : null)
                .actionReason(actionIntent != null ? actionIntent.getReason() : null)
                
                .build();
    }
    
    /**
     * Record a generic decision (for bot trading, order execution, etc).
     * This is a flexible method to log various decision types.
     * 
     * @param record Audit record to log
     */
    public void recordDecision(AiAuditRecord record) {
        if (record == null) {
            logger.warn("Cannot record null audit record");
            return;
        }
        
        try {
            String jsonLine = objectMapper.writeValueAsString(record);
            
            // Append to JSONL file (one JSON object per line)
            Files.writeString(
                    Paths.get(logFilePath),
                    "%s\n".formatted(jsonLine),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            
            logger.debug("Audit record logged: {}", record.getAuditId());
            
        } catch (IOException e) {
            logger.error("Failed to write audit log: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error logging audit record: {}", e.getMessage());
        }
    }
}
