package org.investpro.core.agents.market;

import lombok.extern.slf4j.Slf4j;

import lombok.Getter;
import lombok.Setter;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
/**
 * Normalizes market/account stream events for downstream agents.
 */
@Getter
@Setter
@Slf4j
public class MarketDataAgent implements Agent {
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
        log.info("MarketDataAgent started");
    }

    @Override
    public void stop() {
        running = false;

        log.info("MarketDataAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            log.info("MarketDataAgent onEvent() called before stopping");
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
                 AgentEvent.FILL_UPDATE -> log.debug("{} received {}", name(), event.type());
            default -> {
            }
        }
    }
}
