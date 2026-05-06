package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;

/**
 * Agent responsible for auditing and logging all agent events.
 * <p>
 * Processes:
 * - All events from the agent event bus
 * - Error events
 * - Trade events
 * <p>
 * Publishes:
 * - Audit logs
 * - Event summaries
 */
@Slf4j
public class AuditAgent implements org.investpro.core.agents.Agent {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuditAgent.class);

    private volatile boolean running = false;
    private AgentContext context;

    @Override
    public String name() {
        return "AuditAgent";
    }

    @Override
    public void start(AgentContext context) {
        if (running) {
            log.warn("AuditAgent is already started");
            return;
        }

        this.context = context;
        this.running = true;

        log.info("AuditAgent started");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        this.running = false;
        this.context = null;

        log.info("AuditAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        try {
            // Audit agent logs all events for audit trail
            auditEvent(event);

        } catch (Exception e) {
            log.error("Error processing audit event", e);
        }
    }

    private void auditEvent(AgentEvent event) {
        // Log event for audit trail
        log.debug("AuditAgent: event={} source={}", event.type(), event.source());
    }
}
