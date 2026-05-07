package org.investpro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
@Getter
public class AiAuditLogger {

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
            log.warn("Failed to create audit log directory: {}", e.getMessage());
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
                    StandardOpenOption.APPEND);

            log.debug("AI trade review logged: {}", record.getAuditId());

        } catch (IOException e) {
            log.error("Failed to write AI audit log: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Error building audit record: {}", e.getMessage());
        }
    }

    /**
     * Build audit record from request, AI response, and gate decision.
     */
    private AiAuditRecord buildAuditRecord(AiTradeReviewRequest request,
            AiTradeReviewResponse aiResponse,
            FinalRiskGate.OrderApprovalDecision gateDecision) {
        String blockersStr = request.getRiskDecision() != null &&
                request.getRiskDecision().getBlockers() != null
                        ? String.join("; ", request.getRiskDecision().getBlockers())
                        : "";

        assert request.getRiskDecision() != null;
        return AiAuditRecord.builder()
                // Identifiers
                .auditId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())

                // Trade Context
                .symbol(request.getSymbol() != null ? request.getSymbol().toString() : "N/A")
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
            PositionActionIntent actionIntent) {

        // Log position review to audit trail
        log.info("Position review: {} - {} - Action: {}", positionId, symbol,
                aiResponse != null ? aiResponse.getAction() : "UNKNOWN");
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

        // Log position action to audit trail
        log.info("Position action: {} - {} - Success: {}", positionId, symbol, executionSuccess);
    }

    /**
     * Record a generic decision (for bot trading, order execution, etc).
     */
    public void recordDecision(AiAuditRecord record) {
        if (record == null) {
            log.warn("Cannot record null audit record");
            return;
        }
        log.info("Decision recorded: {}", record.getAuditId());
    }
}
