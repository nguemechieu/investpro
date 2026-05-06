package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;

/**
 * Agent responsible for generating trading signals.
 * <p>
 * Processes:
 * - Market data from MarketDataAgent
 * - Technical indicators
 * - Pattern recognition
 * <p>
 * Publishes:
 * - Trading signals
 * - Signal confidence scores
 * - Strategy recommendations
 */
@Slf4j
public class SignalAgent implements org.investpro.core.agents.Agent {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SignalAgent.class);

    private volatile boolean running = false;
    private AgentContext context;

    @Override
    public String name() {
        return "SignalAgent";
    }

    @Override
    public void start(AgentContext context) {
        if (running) {
            log.warn("SignalAgent is already started");
            return;
        }

        this.context = context;
        this.running = true;

        log.info("SignalAgent started");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        this.running = false;
        this.context = null;

        log.info("SignalAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        try {
            // Signal agent processes market and analysis events
            if (AgentEvent.MARKET_TICK.equals(event.type()) ||
                    AgentEvent.MARKET_CANDLE.equals(event.type())) {
                generateSignal(event);
            }

        } catch (Exception e) {
            log.error("Error processing signal event", e);
        }
    }

    private void generateSignal(AgentEvent event) {
        // Analyze market data and generate signals
        log.debug("SignalAgent generating signals from market data");
    }
}
