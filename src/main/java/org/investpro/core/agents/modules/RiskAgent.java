package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;

/**
 * Agent responsible for risk management and evaluation.
 * <p>
 * Processes:
 * - Trading signals from SignalAgent
 * - Portfolio exposure
 * - Account drawdown
 * - Position sizing
 * <p>
 * Publishes:
 * - Risk assessments
 * - Position sizing recommendations
 * - Risk gate decisions
 */
@Slf4j
public class RiskAgent implements org.investpro.core.agents.Agent {
    private volatile boolean running = false;
    private AgentContext context;

    @Override
    public String name() {
        return "RiskAgent";
    }

    @Override
    public void start(AgentContext context) {
        if (running) {
            log.warn("RiskAgent is already started");
            return;
        }

        this.context = context;
        this.running = true;

        log.info("RiskAgent started");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        this.running = false;
        this.context = null;

        log.info("RiskAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        try {
            // Risk agent processes signals and trade events
            if (AgentEvent.SIGNAL_CREATED.equals(event.type()) ||
                    AgentEvent.STRATEGY_SIGNAL_APPROVED.equals(event.type())) {
                evaluateRisk(event);
            }

        } catch (Exception e) {
            log.error("Error processing risk event", e);
        }
    }

    private void evaluateRisk(AgentEvent event) {
        // Evaluate risk for proposed trade
        log.debug("RiskAgent evaluating risk");
    }
}
