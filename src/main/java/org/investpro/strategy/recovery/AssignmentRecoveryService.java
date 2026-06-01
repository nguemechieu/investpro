package org.investpro.strategy.recovery;

import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.investpro.config.AppConfigKeys;
import org.investpro.core.agents.AgentEvent;
import org.investpro.event.EventBusManager;
import org.investpro.persistence.repository.StrategyAssignmentRepository;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.management.StrategyAssignmentManager;

import java.util.List;

/**
 * Startup recovery coordinator for strategy assignments.
 */
@Slf4j
public final class AssignmentRecoveryService {

    private final StrategyAssignmentRepository assignmentRepository;
    private final StrategyAssignmentManager lifecycleManager;
    private final EventBusManager eventBus;

    public AssignmentRecoveryService() {
        this.assignmentRepository = StrategyAssignmentRepository.getInstance();
        this.lifecycleManager = StrategyAssignmentManager.getInstance();
        this.eventBus = EventBusManager.getInstance();
    }

    public int recoverAssignmentsOnStartup() {
        boolean recoverEnabled = AppConfig.getBoolean(AppConfigKeys.STRATEGY_ASSIGNMENT_RECOVER_ON_STARTUP, true);
        if (!recoverEnabled) {
            log.info("Strategy assignment startup recovery is disabled by config");
            return 0;
        }

        List<StrategyAssignment> activeAssignments = assignmentRepository.getAllActive();
        int recovered = lifecycleManager.recoverAssignmentsOnStartup(activeAssignments);
        eventBus.publish(AgentEvent.of(AgentEvent.STRATEGY_ASSIGNMENTS_LOADED,
                "AssignmentRecoveryService", activeAssignments,
                java.util.Map.of("count", activeAssignments.size(), "recovered", recovered)));

        return recovered;
    }
}
