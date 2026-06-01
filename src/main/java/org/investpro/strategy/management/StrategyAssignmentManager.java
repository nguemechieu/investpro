package org.investpro.strategy.management;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.decision.MarketRegime;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.event.EventBusManager;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.ai.AIReplacementEngine;
import org.investpro.strategy.ai.AIStrategyHealthEngine;
import org.investpro.strategy.lifecycle.*;
import org.investpro.strategy.performance.StrategyLearningEngine;
import org.investpro.strategy.performance.StrategyPerformanceTracker;
import org.investpro.strategy.persistence.StrategyLifecyclePersistenceService;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central lifecycle manager for strategy assignments.
 *
 * <p>
 * Manages the entire lifecycle from ASSIGNED through LIVE_ACTIVE to ARCHIVED.
 * All state transitions are logged and published as {@link AgentEvent} events.
 * AI engines provide advisory input; this manager applies all transitions.
 * </p>
 *
 * <p>
 * <strong>CRITICAL:</strong> This manager orchestrates lifecycle transitions
 * only.
 * It NEVER places orders or bypasses RiskEngine controls.
 * </p>
 */
@Slf4j
public class StrategyAssignmentManager {

    private static volatile StrategyAssignmentManager instance;

    private final ConcurrentHashMap<String, StrategyLifecycleRecord> lifecycleRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastPromotionGateFailures = new ConcurrentHashMap<>();

    private final AIStrategyHealthEngine healthEngine = AIStrategyHealthEngine.getInstance();
    private final AIReplacementEngine replacementEngine = AIReplacementEngine.getInstance();
    private final StrategyPerformanceTracker performanceTracker = StrategyPerformanceTracker.getInstance();
    private final StrategyLearningEngine learningEngine = StrategyLearningEngine.getInstance();
    private final StrategyAssignmentGatekeeper gatekeeper = StrategyAssignmentGatekeeper.getInstance();
    private final StrategyLifecyclePersistenceService persistence = StrategyLifecyclePersistenceService.getInstance();
    private final EventBusManager eventBus = EventBusManager.getInstance();

    private static final String SOURCE = "StrategyAssignmentManager";

    private StrategyAssignmentManager() {
        for (StrategyLifecycleRecord record : persistence.recoverAll()) {
            lifecycleRecords.put(record.getAssignmentId(), record);
        }
        log.info("StrategyAssignmentManager initialised with {} recovered assignment(s)", lifecycleRecords.size());
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton StrategyAssignmentManager
     */
    public static StrategyAssignmentManager getInstance() {
        StrategyAssignmentManager local = instance;
        if (local == null) {
            synchronized (StrategyAssignmentManager.class) {
                local = instance;
                if (local == null) {
                    local = new StrategyAssignmentManager();
                    instance = local;
                }
            }
        }
        return local;
    }

    // =========================================================================
    // Lifecycle Transitions
    // =========================================================================

    /**
     * Creates a new strategy lifecycle record at DISCOVERED status.
     *
     * @param symbol       trading symbol
     * @param timeframe    timeframe
     * @param strategyId   strategy identifier
     * @param strategyName human-readable strategy name
     * @param score        composite assignment score
     * @param reason       reason for this assignment
     * @return the newly created StrategyLifecycleRecord
     */
    public StrategyLifecycleRecord assign(String symbol, Timeframe timeframe,
            String strategyId, String strategyName,
            double score, String reason) {
        String assignmentId = UUID.randomUUID().toString();
        StrategyLifecycleRecord record = StrategyLifecycleRecord.builder()
                .assignmentId(assignmentId)
                .symbol(symbol)
                .timeframe(timeframe != null ? timeframe.getCode() : "UNKNOWN")
                .strategyId(strategyId)
                .strategyName(strategyName)
                .assignmentScore(score)
                .confidence(score / 100.0)
                .assignedAt(Instant.now())
                .assignedBy(SOURCE)
                .assignmentReason(reason)
                .marketRegime(MarketRegime.UNKNOWN)
                .lifecycleStatus(StrategyLifecycleStatus.DISCOVERED)
                .assignmentMode("AUTO")
                .promotionHistory(new ArrayList<>())
                .demotionHistory(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        lifecycleRecords.put(assignmentId, record);
        persistence.upsert(record);
        persistence.logLifecycleEvent(assignmentId, null, StrategyLifecycleStatus.DISCOVERED, reason);
        log.info("Strategy assigned: id={} strategy={} symbol={}/{} score={}",
                assignmentId, strategyId, symbol,
                timeframe != null ? timeframe.getCode() : "UNKNOWN", score);

        eventBus.publish(AgentEvent.of(AgentEvent.STRATEGY_ASSIGNED, SOURCE, record));
        return record;
    }

    /**
     * Promotes a strategy to LIVE_ACTIVE status.
     *
     * @param assignmentId the assignment identifier
     * @param reason       reason for promotion
     * @return updated StrategyLifecycleRecord
     */
    public StrategyLifecycleRecord promoteToLive(String assignmentId, String reason) {
        StrategyLifecycleRecord record = lifecycleRecords.get(assignmentId);
        if (record == null) {
            log.warn("promoteToLive: assignment not found: {}", assignmentId);
            return null;
        }

        StrategyAssignmentGatekeeper.GateResult gateResult = gatekeeper.canPromoteLive(record);
        if (!gateResult.isAllowed()) {
            lastPromotionGateFailures.put(assignmentId, gateResult.summary());
            log.warn("Blocked live promotion for assignment {} [{}]: {}",
                    assignmentId, record.getStrategyId(), gateResult.summary());
            return null;
        }

        lastPromotionGateFailures.remove(assignmentId);

        return transitionStatus(assignmentId, StrategyLifecycleStatus.LIVE_ACTIVE,
                reason, AgentEvent.STRATEGY_PROMOTED, true, false);
    }

    /**
     * Returns the last safety-gate block reason for a promotion attempt.
     */
    public Optional<String> getLastPromotionGateFailure(String assignmentId) {
        if (assignmentId == null || assignmentId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lastPromotionGateFailures.get(assignmentId));
    }

    /**
     * Updates validation evidence for an existing lifecycle record.
     *
     * <p>
     * This is used by validation/backtest pipelines to attach report data that
     * is required by live promotion safety gates.
     * </p>
     */
    public StrategyLifecycleRecord updateValidationContext(
            String assignmentId,
            AIStrategyReview aiReview,
            StrategyValidationReport validationReport,
            StrategyHealthReport healthReport,
            double validationScore,
            StrategyLifecycleStatus nextStatus) {
        StrategyLifecycleRecord existing = lifecycleRecords.get(assignmentId);
        if (existing == null) {
            log.warn("updateValidationContext: assignment not found: {}", assignmentId);
            return null;
        }

        StrategyLifecycleStatus targetStatus = nextStatus != null ? nextStatus : existing.getLifecycleStatus();

        StrategyLifecycleRecord updated = StrategyLifecycleRecord.builder()
                .assignmentId(existing.getAssignmentId())
                .symbol(existing.getSymbol())
                .timeframe(existing.getTimeframe())
                .strategyId(existing.getStrategyId())
                .strategyName(existing.getStrategyName())
                .assignmentScore(existing.getAssignmentScore())
                .confidence(existing.getConfidence())
                .assignedAt(existing.getAssignedAt())
                .assignedBy(existing.getAssignedBy())
                .assignmentReason(existing.getAssignmentReason())
                .marketRegime(existing.getMarketRegime())
                .lifecycleStatus(targetStatus)
                .assignmentMode(existing.getAssignmentMode())
                .aiApprovalStatus(aiReview != null ? aiReview.getDecision() : existing.getAiApprovalStatus())
                .aiConfidence(aiReview != null ? aiReview.getAiConfidence() : existing.getAiConfidence())
                .aiReasoningSummary(
                        aiReview != null ? aiReview.getReasoningSummary() : existing.getAiReasoningSummary())
                .validationScore(validationScore > 0.0 ? validationScore : existing.getValidationScore())
                .lastHealthReport(healthReport != null ? healthReport : existing.getLastHealthReport())
                .lastAIReview(aiReview != null ? aiReview : existing.getLastAIReview())
                .lastValidationReport(validationReport != null ? validationReport : existing.getLastValidationReport())
                .rankScore(existing.getRankScore())
                .learningProfile(existing.getLearningProfile())
                .promotionHistory(existing.getPromotionHistory())
                .demotionHistory(existing.getDemotionHistory())
                .createdAt(existing.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        lifecycleRecords.put(assignmentId, updated);
        persistence.upsert(updated);
        if (aiReview != null) {
            persistence.saveAIReview(assignmentId, "STRATEGY", aiReview.isApproved(),
                    aiReview.getAiConfidence(), aiReview.getReasoningSummary());
            eventBus.publish(AgentEvent.of(aiReview.isApproved()
                    ? AgentEvent.AI_STRATEGY_APPROVED
                    : AgentEvent.AI_STRATEGY_REJECTED, SOURCE, aiReview));
        }
        if (healthReport != null) {
            persistence.saveHealthSnapshot(assignmentId, healthReport.getHealthScore(),
                    healthReport.getHealthLevel() != null ? healthReport.getHealthLevel().name() : "UNKNOWN");
        }
        return updated;
    }

    /**
     * Demotes a strategy from live to paper trading (DEMOTED status).
     *
     * @param assignmentId the assignment identifier
     * @param reason       reason for demotion
     * @return updated StrategyLifecycleRecord
     */
    public StrategyLifecycleRecord demote(String assignmentId, String reason) {
        return transitionStatus(assignmentId, StrategyLifecycleStatus.DEMOTED,
                reason, AgentEvent.STRATEGY_DEMOTED, false, true);
    }

    /**
     * Pauses an active strategy.
     *
     * @param assignmentId the assignment identifier
     * @param reason       reason for pausing
     * @return updated StrategyLifecycleRecord
     */
    public StrategyLifecycleRecord pause(String assignmentId, String reason) {
        return transitionStatus(assignmentId, StrategyLifecycleStatus.PAUSED,
                reason, AgentEvent.STRATEGY_PAUSED, false, false);
    }

    /**
     * Resumes a paused strategy (returns to LIVE_ACTIVE if was live, else
     * PAPER_TRADING).
     *
     * @param assignmentId the assignment identifier
     * @param reason       reason for resuming
     * @return updated StrategyLifecycleRecord
     */
    public StrategyLifecycleRecord resume(String assignmentId, String reason) {
        StrategyLifecycleRecord record = lifecycleRecords.get(assignmentId);
        if (record == null) {
            log.warn("resume: assignment not found: {}", assignmentId);
            return null;
        }
        StrategyLifecycleStatus resumeStatus = record.getDemotionHistory() != null
                && !record.getDemotionHistory().isEmpty()
                        ? StrategyLifecycleStatus.PAPER_TRADING
                        : StrategyLifecycleStatus.LIVE_ACTIVE;
        return transitionStatus(assignmentId, resumeStatus,
                reason, AgentEvent.STRATEGY_RESUMED, false, false);
    }

    /**
     * Replaces a strategy with a new one. Marks the old as REPLACED and creates a
     * new ASSIGNED record.
     *
     * @param assignmentId    the old assignment identifier
     * @param newStrategyId   new strategy identifier
     * @param newStrategyName new strategy name
     * @param reason          reason for replacement
     * @return the new StrategyLifecycleRecord
     */
    public StrategyLifecycleRecord replace(String assignmentId, String newStrategyId,
            String newStrategyName, String reason) {
        StrategyLifecycleRecord old = lifecycleRecords.get(assignmentId);
        if (old == null) {
            log.warn("replace: assignment not found: {}", assignmentId);
            return null;
        }
        // Mark old as replaced
        transitionStatus(assignmentId, StrategyLifecycleStatus.REPLACED,
                reason, AgentEvent.STRATEGY_REPLACED, false, false);

        // Create new assignment
        StrategyLifecycleRecord newRecord = StrategyLifecycleRecord.builder()
                .assignmentId(UUID.randomUUID().toString())
                .symbol(old.getSymbol())
                .timeframe(old.getTimeframe())
                .strategyId(newStrategyId)
                .strategyName(newStrategyName)
                .assignmentScore(0.0)
                .confidence(0.0)
                .assignedAt(Instant.now())
                .assignedBy(SOURCE)
                .assignmentReason("Replacement for " + assignmentId + ": " + reason)
                .marketRegime(MarketRegime.UNKNOWN)
                .lifecycleStatus(StrategyLifecycleStatus.DISCOVERED)
                .assignmentMode("AUTO")
                .promotionHistory(new ArrayList<>())
                .demotionHistory(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        lifecycleRecords.put(newRecord.getAssignmentId(), newRecord);
        persistence.upsert(newRecord);
        persistence.logLifecycleEvent(newRecord.getAssignmentId(), null, StrategyLifecycleStatus.DISCOVERED, reason);
        log.info("Strategy replaced: old={} new={} newStrategy={}",
                assignmentId, newRecord.getAssignmentId(), newStrategyId);
        eventBus.publish(AgentEvent.of(AgentEvent.STRATEGY_ASSIGNED, SOURCE, newRecord));
        return newRecord;
    }

    /**
     * Archives a strategy (terminal state - no further transitions).
     *
     * @param assignmentId the assignment identifier
     * @param reason       reason for archiving
     * @return updated StrategyLifecycleRecord
     */
    public StrategyLifecycleRecord archive(String assignmentId, String reason) {
        return transitionStatus(assignmentId, StrategyLifecycleStatus.ARCHIVED,
                reason, AgentEvent.STRATEGY_ARCHIVED, false, false);
    }

    /**
     * Updates the lifecycle status directly.
     *
     * @param assignmentId the assignment identifier
     * @param newStatus    the target status
     * @param reason       reason for the transition
     * @return updated StrategyLifecycleRecord
     */
    public StrategyLifecycleRecord updateLifecycleStatus(String assignmentId,
            StrategyLifecycleStatus newStatus,
            String reason) {
        return transitionStatus(assignmentId, newStatus, reason, null, false, false);
    }

    /**
     * Recovers assignment metadata on startup without automatic replacement.
     *
     * @param activeAssignments active strategy assignments recovered from
     *                          repository
     * @return number of assignments recovered or synced
     */
    public int recoverAssignmentsOnStartup(List<StrategyAssignment> activeAssignments) {
        if (activeAssignments == null || activeAssignments.isEmpty()) {
            return 0;
        }

        int recoveredCount = 0;
        for (StrategyAssignment assignment : activeAssignments) {
            if (assignment == null || assignment.getAssignmentId() == null || assignment.getAssignmentId().isBlank()) {
                continue;
            }

            StrategyLifecycleRecord existing = lifecycleRecords.get(assignment.getAssignmentId());
            if (existing != null) {
                transitionStatus(
                        assignment.getAssignmentId(),
                        StrategyLifecycleStatus.RECOVERED,
                        "Recovered existing assignment metadata on startup",
                        AgentEvent.STRATEGY_ASSIGNMENT_RECOVERED,
                        false,
                        false);
                recoveredCount++;
                continue;
            }

            StrategyLifecycleRecord recovered = StrategyLifecycleRecord.builder()
                    .assignmentId(assignment.getAssignmentId())
                    .symbol(assignment.getSymbol())
                    .timeframe(assignment.getTimeframe() != null ? assignment.getTimeframe().getCode() : "UNKNOWN")
                    .strategyId(assignment.getStrategyId())
                    .strategyName(assignment.getStrategyId())
                    .assignmentScore(assignment.getScoreAtAssignment())
                    .confidence(Math.max(0.0, Math.min(1.0, assignment.getScoreAtAssignment() / 100.0)))
                    .assignedAt(assignment.getAssignedAt())
                    .assignedBy(assignment.getAssignedBy() != null ? assignment.getAssignedBy().name() : SOURCE)
                    .assignmentReason(assignment.getReason())
                    .marketRegime(MarketRegime.UNKNOWN)
                    .lifecycleStatus(StrategyLifecycleStatus.RECOVERED)
                    .assignmentMode(assignment.getMode() != null ? assignment.getMode().name() : "AUTO")
                    .promotionHistory(new ArrayList<>())
                    .demotionHistory(new ArrayList<>())
                    .createdAt(assignment.getAssignedAt() != null ? assignment.getAssignedAt() : Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            lifecycleRecords.put(recovered.getAssignmentId(), recovered);
            persistence.upsert(recovered);
            persistence.logLifecycleEvent(recovered.getAssignmentId(), null, StrategyLifecycleStatus.RECOVERED,
                    "Recovered from assignment repository on startup");
            eventBus.publish(AgentEvent.of(AgentEvent.STRATEGY_ASSIGNMENT_RECOVERED, SOURCE, recovered));
            recoveredCount++;
        }

        return recoveredCount;
    }

    /**
     * Resumes a recovered or paused assignment while preserving ownership.
     */
    public StrategyLifecycleRecord resumeAssignment(String assignmentId, String reason) {
        StrategyLifecycleRecord existing = lifecycleRecords.get(assignmentId);
        if (existing == null) {
            log.warn("resumeAssignment: assignment not found: {}", assignmentId);
            return null;
        }
        StrategyLifecycleStatus target = existing.getLifecycleStatus() == StrategyLifecycleStatus.PAUSED
                ? StrategyLifecycleStatus.RESUMED
                : StrategyLifecycleStatus.RESUMED;
        return transitionStatus(assignmentId, target, reason,
                AgentEvent.STRATEGY_ASSIGNMENT_RESUMED, false, false);
    }

    /**
     * Marks assignment for controlled re-evaluation by supervisor.
     */
    public StrategyLifecycleRecord requestReevaluation(String assignmentId, String reason) {
        return transitionStatus(assignmentId, StrategyLifecycleStatus.NEEDS_REVIEW,
                reason != null ? reason : "Controlled re-evaluation requested",
                AgentEvent.STRATEGY_REEVALUATION_REQUESTED,
                false, false);
    }

    /**
     * Performs safety-aware replacement. If blocked, assignment enters
     * REPLACEMENT_PENDING and emits STRATEGY_REPLACEMENT_BLOCKED.
     */
    public StrategyLifecycleRecord replaceAssignmentSafely(String assignmentId,
            String newStrategyId,
            String newStrategyName,
            boolean hasOpenPosition,
            String reason) {
        if (hasOpenPosition) {
            transitionStatus(assignmentId, StrategyLifecycleStatus.REPLACEMENT_PENDING,
                    reason != null ? reason : "Replacement blocked: open position ownership exists",
                    AgentEvent.STRATEGY_REPLACEMENT_BLOCKED,
                    false, false);
            return null;
        }
        return replace(assignmentId, newStrategyId, newStrategyName, reason);
    }

    public StrategyLifecycleRecord markNeedsReview(String assignmentId, String reason) {
        return transitionStatus(assignmentId, StrategyLifecycleStatus.NEEDS_REVIEW,
                reason != null ? reason : "Manual review required",
                AgentEvent.STRATEGY_ASSIGNMENT_NEEDS_REVIEW,
                false, false);
    }

    public StrategyLifecycleRecord markOrphanedPosition(String assignmentId, String reason) {
        return transitionStatus(assignmentId, StrategyLifecycleStatus.ORPHANED_POSITION,
                reason != null ? reason : "Orphaned broker position detected",
                AgentEvent.STRATEGY_ORPHANED_POSITION_DETECTED,
                false, false);
    }

    // =========================================================================
    // Queries
    // =========================================================================

    /**
     * Returns the lifecycle record for an assignment.
     *
     * @param assignmentId the assignment identifier
     * @return Optional containing the record, or empty if not found
     */
    public Optional<StrategyLifecycleRecord> getRecord(String assignmentId) {
        return Optional.ofNullable(lifecycleRecords.get(assignmentId));
    }

    /**
     * Returns all lifecycle records.
     *
     * @return unmodifiable list of all records
     */
    public List<StrategyLifecycleRecord> getAllRecords() {
        return Collections.unmodifiableList(new ArrayList<>(lifecycleRecords.values()));
    }

    /**
     * Returns all records with the given lifecycle status.
     *
     * @param status the status to filter by
     * @return list of matching records
     */
    public List<StrategyLifecycleRecord> getRecordsByStatus(StrategyLifecycleStatus status) {
        return lifecycleRecords.values().stream()
                .filter(r -> r.getLifecycleStatus() == status)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns all records that are currently in a live state (LIVE_ACTIVE or
     * WATCH).
     *
     * @return list of live strategy records
     */
    public List<StrategyLifecycleRecord> getLiveRecords() {
        return lifecycleRecords.values().stream()
                .filter(r -> r.getLifecycleStatus() != null && r.getLifecycleStatus().isLive())
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns all records currently in DEGRADED status.
     *
     * @return list of degraded strategy records
     */
    public List<StrategyLifecycleRecord> getDegradedRecords() {
        return getRecordsByStatus(StrategyLifecycleStatus.DEGRADED);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private StrategyLifecycleRecord transitionStatus(String assignmentId,
            StrategyLifecycleStatus newStatus,
            String reason, String eventType,
            boolean isPromotion, boolean isDemotion) {
        StrategyLifecycleRecord existing = lifecycleRecords.get(assignmentId);
        if (existing == null) {
            log.warn("transitionStatus: assignment not found: {}", assignmentId);
            return null;
        }

        String historyEntry = Instant.now().toString() + " | " + reason;
        List<String> promoHistory = new ArrayList<>(
                existing.getPromotionHistory() != null ? existing.getPromotionHistory() : List.of());
        List<String> demotionHistory = new ArrayList<>(
                existing.getDemotionHistory() != null ? existing.getDemotionHistory() : List.of());

        if (isPromotion)
            promoHistory.add(historyEntry);
        if (isDemotion)
            demotionHistory.add(historyEntry);

        StrategyLifecycleRecord updated = StrategyLifecycleRecord.builder()
                .assignmentId(existing.getAssignmentId())
                .symbol(existing.getSymbol())
                .timeframe(existing.getTimeframe())
                .strategyId(existing.getStrategyId())
                .strategyName(existing.getStrategyName())
                .assignmentScore(existing.getAssignmentScore())
                .confidence(existing.getConfidence())
                .assignedAt(existing.getAssignedAt())
                .assignedBy(existing.getAssignedBy())
                .assignmentReason(existing.getAssignmentReason())
                .marketRegime(existing.getMarketRegime())
                .lifecycleStatus(newStatus)
                .assignmentMode(existing.getAssignmentMode())
                .aiApprovalStatus(existing.getAiApprovalStatus())
                .aiConfidence(existing.getAiConfidence())
                .aiReasoningSummary(existing.getAiReasoningSummary())
                .validationScore(existing.getValidationScore())
                .lastHealthReport(existing.getLastHealthReport())
                .lastAIReview(existing.getLastAIReview())
                .lastValidationReport(existing.getLastValidationReport())
                .rankScore(existing.getRankScore())
                .learningProfile(existing.getLearningProfile())
                .promotionHistory(Collections.unmodifiableList(promoHistory))
                .demotionHistory(Collections.unmodifiableList(demotionHistory))
                .createdAt(existing.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        lifecycleRecords.put(assignmentId, updated);
        persistence.upsert(updated);
        persistence.logLifecycleEvent(assignmentId, existing.getLifecycleStatus(), newStatus, reason);

        log.info("Lifecycle transition: assignment={} {}->{}  reason={}",
                assignmentId, existing.getLifecycleStatus(), newStatus, reason);

        if (eventType != null) {
            eventBus.publish(AgentEvent.of(eventType, SOURCE, updated));
        }
        return updated;
    }

    /** Returns whether a live assignment can currently process a trade signal. */
    public StrategyAssignmentGatekeeper.GateResult canTrade(String assignmentId) {
        return gatekeeper.canTrade(lifecycleRecords.get(assignmentId));
    }
}
