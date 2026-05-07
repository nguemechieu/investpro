package org.investpro.ai;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * Immutable audit record for every AI trade review.
 * Used for logging, tracing, and feedback collection.
 *
 * Each review request/response pair is logged to enable:
 * - Audit trail for compliance
 * - Performance metrics (AI accuracy, confidence calibration)
 * - Feedback loop for model improvement
 * - Dispute resolution and transparency
 */
@Slf4j
@Data
@Builder
public class AiAuditRecord {
 // =========================================================================
    // Identifiers
    // =========================================================================

    /** Unique identifier for this audit record (UUID) */
    String auditId;

    /** Timestamp when the review occurred */
    LocalDateTime timestamp;

    // =========================================================================
    // Trade Context
    // =========================================================================

    /** Trading pair (e.g., "BTC/USD") */
    String symbol;

    /** Strategy that generated the signal */
    String strategyName;

    /** Signal side (LONG or SHORT) */
    String signalSide;

    /** Signal confidence (0.0-1.0) */
    double signalConfidence;

    // =========================================================================
    // Risk Context
    // =========================================================================

    /** RiskDecision blockers if any */
    @Nullable
    String riskDecisionBlockers;

    /** RiskDecision final position size */
    double riskDecisionPositionSize;

    /** RiskDecision final leverage */
    double riskDecisionLeverage;

    // =========================================================================
    // AI Response
    // =========================================================================

    /** AI decision made */
    String aiDecision;

    /** AI confidence in decision (0.0-1.0) */
    double aiConfidence;

    /** AI suggested risk multiplier */
    double aiSuggestedRiskMultiplier;

    /** AI suggested position size */
    double aiSuggestedPositionSize;

    /** Number of concerns identified */
    int concernCount;

    /** AI model used */
    String modelName;

    /** AI processing time in milliseconds */
    long aiProcessingTimeMs;

    /** Did AI encounter errors during processing */
    boolean aiHadErrors;

    // =========================================================================
    // Final Gate Decision
    // =========================================================================

    /** Final decision from FinalRiskGate */
    String finalDecision;

    /** Was the trade approved for execution */
    boolean wasApproved;

    /** Reason for final decision if rejected */
    @Nullable
    String rejectionReason;

    // =========================================================================
    // Account State at Review Time
    // =========================================================================

    /** Account equity at time of review */
    double accountEquity;

    /** Portfolio heat at time of review */
    double portfolioHeatPercent;

    /** Current drawdown at time of review */
    double drawdownPercent;

    // =========================================================================
    // Outcome (populated after trade execution)
    // =========================================================================

    /** If trade was executed, what was the realized P&L */
    @Nullable
    Double realizedPnL;

    /** If trade was executed, what was the exit reason (STP, TGT, Manual, etc.) */
    @Nullable
    String exitReason;

    /** User feedback on the AI decision (if provided) */
    @Nullable
    String userFeedback;

    // =========================================================================
    // Explicit builder method (Lombok @Builder not being invoked)
    // =========================================================================

    public static AiAuditRecordBuilder builder() {
        return new AiAuditRecordBuilder();
    }

    // Builder pattern implementation
    public static class AiAuditRecordBuilder {
        private String auditId;
        private LocalDateTime timestamp;
        private String symbol;
        private String strategyName;
        private String signalSide;
        private double signalConfidence;
        private String riskDecisionBlockers;
        private double riskDecisionPositionSize;
        private double riskDecisionLeverage;
        private String aiDecision;
        private double aiConfidence;
        private double aiSuggestedRiskMultiplier;
        private double aiSuggestedPositionSize;
        private int concernCount;
        private String modelName;
        private long aiProcessingTimeMs;
        private boolean aiHadErrors;
        private String finalDecision;
        private boolean wasApproved;
        private String rejectionReason;
        private double accountEquity;
        private double portfolioHeatPercent;
        private double drawdownPercent;
        private Double realizedPnL;
        private String exitReason;
        private String userFeedback;

        public AiAuditRecordBuilder auditId(String auditId) {
            this.auditId = auditId;
            return this;
        }

        public AiAuditRecordBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AiAuditRecordBuilder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public AiAuditRecordBuilder strategyName(String strategyName) {
            this.strategyName = strategyName;
            return this;
        }

        public AiAuditRecordBuilder signalSide(String signalSide) {
            this.signalSide = signalSide;
            return this;
        }

        public AiAuditRecordBuilder signalConfidence(double signalConfidence) {
            this.signalConfidence = signalConfidence;
            return this;
        }

        public AiAuditRecordBuilder riskDecisionBlockers(String riskDecisionBlockers) {
            this.riskDecisionBlockers = riskDecisionBlockers;
            return this;
        }

        public AiAuditRecordBuilder riskDecisionPositionSize(double riskDecisionPositionSize) {
            this.riskDecisionPositionSize = riskDecisionPositionSize;
            return this;
        }

        public AiAuditRecordBuilder riskDecisionLeverage(double riskDecisionLeverage) {
            this.riskDecisionLeverage = riskDecisionLeverage;
            return this;
        }

        public AiAuditRecordBuilder aiDecision(String aiDecision) {
            this.aiDecision = aiDecision;
            return this;
        }

        public AiAuditRecordBuilder aiConfidence(double aiConfidence) {
            this.aiConfidence = aiConfidence;
            return this;
        }

        public AiAuditRecordBuilder aiSuggestedRiskMultiplier(double aiSuggestedRiskMultiplier) {
            this.aiSuggestedRiskMultiplier = aiSuggestedRiskMultiplier;
            return this;
        }

        public AiAuditRecordBuilder aiSuggestedPositionSize(double aiSuggestedPositionSize) {
            this.aiSuggestedPositionSize = aiSuggestedPositionSize;
            return this;
        }

        public AiAuditRecordBuilder concernCount(int concernCount) {
            this.concernCount = concernCount;
            return this;
        }

        public AiAuditRecordBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public AiAuditRecordBuilder aiProcessingTimeMs(long aiProcessingTimeMs) {
            this.aiProcessingTimeMs = aiProcessingTimeMs;
            return this;
        }

        public AiAuditRecordBuilder aiHadErrors(boolean aiHadErrors) {
            this.aiHadErrors = aiHadErrors;
            return this;
        }

        public AiAuditRecordBuilder finalDecision(String finalDecision) {
            this.finalDecision = finalDecision;
            return this;
        }

        public AiAuditRecordBuilder wasApproved(boolean wasApproved) {
            this.wasApproved = wasApproved;
            return this;
        }

        public AiAuditRecordBuilder rejectionReason(String rejectionReason) {
            this.rejectionReason = rejectionReason;
            return this;
        }

        public AiAuditRecordBuilder accountEquity(double accountEquity) {
            this.accountEquity = accountEquity;
            return this;
        }

        public AiAuditRecordBuilder portfolioHeatPercent(double portfolioHeatPercent) {
            this.portfolioHeatPercent = portfolioHeatPercent;
            return this;
        }

        public AiAuditRecordBuilder drawdownPercent(double drawdownPercent) {
            this.drawdownPercent = drawdownPercent;
            return this;
        }

        public AiAuditRecordBuilder realizedPnL(Double realizedPnL) {
            this.realizedPnL = realizedPnL;
            return this;
        }

        public AiAuditRecordBuilder exitReason(String exitReason) {
            this.exitReason = exitReason;
            return this;
        }

        public AiAuditRecordBuilder userFeedback(String userFeedback) {
            this.userFeedback = userFeedback;
            return this;
        }

        public AiAuditRecord build() {
            return new AiAuditRecord(
                    auditId, timestamp, symbol, strategyName, signalSide, signalConfidence,
                    riskDecisionBlockers, riskDecisionPositionSize, riskDecisionLeverage,
                    aiDecision, aiConfidence, aiSuggestedRiskMultiplier, aiSuggestedPositionSize,
                    concernCount, modelName, aiProcessingTimeMs, aiHadErrors,
                    finalDecision, wasApproved, rejectionReason,
                    accountEquity, portfolioHeatPercent, drawdownPercent,
                    realizedPnL, exitReason, userFeedback);
        }
    }
}
