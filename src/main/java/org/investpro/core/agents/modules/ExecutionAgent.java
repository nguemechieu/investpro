package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;

/**
 * Agent responsible for trade execution coordination.
 * <p>
 * Processes:
 * - Risk-approved signals
 * - AI reasoning results
 * - Execution commands
 * <p>
 * Publishes:
 * - Execution events
 * - Order submissions
 * - Trade execution notifications
 * <p>
 * Note: This agent publishes execution requests but does not directly
 * place orders. Final execution goes through TradeExecutionCoordinator
 * and FinalRiskGate.
 */
@Slf4j
public class ExecutionAgent implements org.investpro.core.agents.Agent {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ExecutionAgent.class);

    private volatile boolean running = false;
    private AgentContext context;

    @Override
    public String name() {
        return "ExecutionAgent";
    }

    @Override
    public void start(AgentContext context) {
        if (running) {
            log.warn("ExecutionAgent is already started");
            return;
        }

        this.context = context;
        this.running = true;

        log.info("ExecutionAgent started");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        this.running = false;
        this.context = null;

        log.info("ExecutionAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        try {
            // Execution agent processes approved signals
            if (AgentEvent.STRATEGY_SIGNAL_APPROVED.equals(event.type()) ||
                    AgentEvent.RISK_APPROVED.equals(event.type())) {
                coordinateExecution(event);
            }

        } catch (Exception e) {
            log.error("Error processing execution event", e);
        }
    }

    private void coordinateExecution(AgentEvent event) {
        // Coordinate execution through TradeExecutionCoordinator
        log.debug("ExecutionAgent coordinating trade execution");
    }
}
