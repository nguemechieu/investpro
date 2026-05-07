package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;

/**
 * Agent responsible for portfolio monitoring and analysis.
 * <p>
 * Processes:
 * - Account updates
 * - Position changes
 * - Balance changes
 * - Trade fills
 * <p>
 * Publishes:
 * - Portfolio snapshots
 * - Exposure analysis
 * - Performance metrics
 */
@Slf4j
public class PortfolioAgent implements org.investpro.core.agents.Agent {
    private volatile boolean running = false;
    private AgentContext context;

    @Override
    public String name() {
        return "PortfolioAgent";
    }

    @Override
    public void start(AgentContext context) {
        if (running) {
            log.warn("PortfolioAgent is already started");
            return;
        }

        this.context = context;
        this.running = true;

        log.info("PortfolioAgent started");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        this.running = false;
        this.context = null;

        log.info("PortfolioAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        try {
            // Portfolio agent processes account and trade events
            if (AgentEvent.ACCOUNT_UPDATE.equals(event.type()) ||
                    AgentEvent.ORDER_FILLED.equals(event.type()) ||
                    AgentEvent.POSITION_UPDATE.equals(event.type())) {
                updatePortfolio(event);
            }

        } catch (Exception e) {
            log.error("Error processing portfolio event", e);
        }
    }

    private void updatePortfolio(AgentEvent event) {
        // Track portfolio and expose metrics
        log.debug("PortfolioAgent updating portfolio snapshot");
    }
}
