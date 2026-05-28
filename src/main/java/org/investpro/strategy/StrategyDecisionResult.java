package org.investpro.strategy;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * Result of a strategy decision evaluation.
 * <p>
 * Wraps the output of Strategy DecisionService.generateDecision() including:
 * - Decision success/failure status
 * - Generated StrategySignal (if successful)
 * - Assignment used (if any)
 * - Rejection reasons (if failed)
 * - Warnings for downstream processing
 * <p>
 * Use factory methods for construction:
 * 
 * <pre>
 * StrategyDecisionResult.success(strategyId, assignment, signal, warnings);
 * StrategyDecisionResult.rejected(reason, warnings);
 * </pre>
 */
@Value
@Builder(toBuilder = true)
public class StrategyDecisionResult {

    /** True if decision was successful and signal is valid */
    boolean success;

    /** Strategy ID that generated this decision */
    String strategyId;

    /** Assignment that was used (null if rejected) */
    StrategyAssignment assignment;

    /** Generated signal (null if rejected) */
    StrategySignal signal;

    /** List of warnings (non-blocking issues) */
    @Builder.Default
    List<String> warnings = List.of();

    /** Reason for rejection (null if successful) */
    String rejectionReason;

    /** Timestamp of decision */
    @Builder.Default
    Instant timestamp = Instant.now();

    /**
     * Factory: Create successful decision result.
     *
     * @param strategyId ID of strategy that generated the signal
     * @param assignment StrategyAssignment that was used
     * @param signal     Generated StrategySignal
     * @param warnings   List of non-blocking warnings (may be empty)
     * @return StrategyDecisionResult with success=true
     */
    public static StrategyDecisionResult success(
            String strategyId,
            StrategyAssignment assignment,
            StrategySignal signal,
            List<String> warnings) {
        return StrategyDecisionResult.builder()
                .success(true)
                .strategyId(strategyId)
                .assignment(assignment)
                .signal(signal)
                .warnings(warnings != null ? warnings : List.of())
                .rejectionReason(null)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Factory: Create rejected decision result.
     *
     * @param reason   Human-readable explanation of rejection
     * @param warnings List of warnings that contributed to rejection (may be empty)
     * @return StrategyDecisionResult with success=false
     */
    public static StrategyDecisionResult rejected(String reason, List<String> warnings) {
        return StrategyDecisionResult.builder()
                .success(false)
                .strategyId(null)
                .assignment(null)
                .signal(null)
                .warnings(warnings != null ? warnings : List.of())
                .rejectionReason(reason)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Check if signal should be acted upon.
     *
     * @return true if success && signal is not null && signal is not HOLD
     */
    public boolean hasActionableSignal() {
        return success && signal != null && !signal.isHold();
    }

    /**
     * Get human-readable summary of decision.
     *
     * @return Summary string suitable for logging
     */
    public String getSummary() {
        if (success) {
            if (signal != null) {
                return String.format(
                        "SIGNAL: %s %s at %.8f (confidence=%.2f%%)",
                        signal.getSide(),
                        signal.getSymbol(),
                        signal.getEntryPrice(),
                        signal.getConfidence() * 100);
            } else {
                return "SUCCESS but no signal generated";
            }
        } else {
            return "REJECTED: " + rejectionReason;
        }
    }
}
