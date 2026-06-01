package org.investpro.strategy.recovery;

import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.management.StrategyAssignmentManager;

import java.util.Objects;

/**
 * Applies controlled resume operation for recovered assignments.
 */
public final class StrategyResumeService {

    private final StrategyAssignmentManager manager;

    public StrategyResumeService() {
        this.manager = StrategyAssignmentManager.getInstance();
    }

    public StrategyLifecycleRecord resumeAssignment(String assignmentId, String reason) {
        String safeReason = Objects.requireNonNullElse(reason, "Startup recovery resume");
        return manager.resumeAssignment(assignmentId, safeReason);
    }
}
