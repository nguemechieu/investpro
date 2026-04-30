package org.investpro.core.agents.portfolio;

import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks latest account, order, fill, and position events.
 */
public class PortfolioAgent implements Agent {

    private AgentContext context;
    private boolean running;
    private final AtomicReference<Object> lastAccount = new AtomicReference<>();
    private final AtomicReference<Object> lastPositions = new AtomicReference<>();

    @Override
    public String name() {
        return "PortfolioAgent";
    }

    @Override
    public void start(AgentContext context) {
        this.context = context;
        this.running = true;
    }

    @Override
    public void stop() {
        running = false;
        lastAccount.set(null);
        lastPositions.set(null);
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        switch (event.type()) {
            case AgentEvent.ACCOUNT_UPDATE -> {
                lastAccount.set(event.payload());
                context.getEventBus().publishAsync(AgentEvent.portfolio(AgentEvent.PORTFOLIO_UPDATED, name(), event.payload()));
            }
            case AgentEvent.POSITION_UPDATE -> {
                lastPositions.set(event.payload());
                context.getEventBus().publishAsync(AgentEvent.portfolio(AgentEvent.EXPOSURE_UPDATED, name(), event.payload()));
            }
            case AgentEvent.FILL_UPDATE, AgentEvent.ORDER_FILLED ->
                    context.getEventBus().publishAsync(AgentEvent.portfolio(AgentEvent.PNL_UPDATED, name(), event.payload()));
            default -> {
            }
        }
    }
}
