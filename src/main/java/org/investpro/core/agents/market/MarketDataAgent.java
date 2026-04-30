package org.investpro.core.agents.market;

import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Normalizes market/account stream events for downstream agents.
 */
public class MarketDataAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataAgent.class);

    private AgentContext context;
    private boolean running;

    @Override
    public String name() {
        return "MarketDataAgent";
    }

    @Override
    public void start(AgentContext context) {
        this.context = context;
        this.running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        switch (event.type()) {
            case AgentEvent.MARKET_TICK,
                 AgentEvent.MARKET_TRADE,
                 AgentEvent.MARKET_CANDLE,
                 AgentEvent.ORDER_BOOK_UPDATE,
                 AgentEvent.ACCOUNT_UPDATE,
                 AgentEvent.POSITION_UPDATE,
                 AgentEvent.ORDER_UPDATE,
                 AgentEvent.FILL_UPDATE -> logger.debug("{} received {}", name(), event.type());
            default -> {
            }
        }
    }
}
