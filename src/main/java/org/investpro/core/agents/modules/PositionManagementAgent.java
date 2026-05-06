package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;

/**
 * Agent responsible for position lifecycle management.
 * <p>
 * Processes:
 * - Approved execution signals
 * - Position entry/exit
 * - Stop loss and take profit management
 * - Position maintenance
 * <p>
 * Publishes:
 * - Position updates
 * - Entry/exit events
 * - Position maintenance actions
 */
@Slf4j
public class PositionManagementAgent implements org.investpro.core.agents.Agent {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PositionManagementAgent.class);

    private volatile boolean running = false;
    private AgentContext context;

    @Override
    public String name() {
        return "PositionManagementAgent";
    }

    @Override
    public void start(AgentContext context) {
        if (running) {
            log.warn("PositionManagementAgent is already started");
            return;
        }

        this.context = context;
        this.running = true;

        log.info("PositionManagementAgent started");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        this.running = false;
        this.context = null;

        log.info("PositionManagementAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        try {
            // Position management agent processes execution and position events
            if (AgentEvent.ORDER_SUBMITTED.equals(event.type()) ||
                    AgentEvent.POSITION_UPDATE.equals(event.type()) ||
                    AgentEvent.POSITION_CLOSED.equals(event.type())) {
                managePosition(event);
            }

        } catch (Exception e) {
            log.error("Error processing position event", e);
        }
    }

    private void managePosition(AgentEvent event) {
        // Manage position lifecycle
        log.debug("PositionManagementAgent managing positions");
    }
}
