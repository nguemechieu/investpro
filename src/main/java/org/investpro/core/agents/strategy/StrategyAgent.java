package org.investpro.core.agents.strategy;

import lombok.Getter;
import lombok.Setter;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.signal.Signal;

/**
 * Filters and approves/rejects signals before risk review.
 */
@Getter
@Setter
public class StrategyAgent implements Agent {

    private AgentContext context;
    private boolean running;

    @Override
    public String name() {
        return "StrategyAgent";
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
        if (!running || event == null || !AgentEvent.SIGNAL_CREATED.equals(event.type())) {
            return;
        }

        if (!(event.payload() instanceof Signal signal)) {
            return;
        }

        if (signal.getConfidence() >= 0.50 && !"HOLD".equalsIgnoreCase(signal.getSide())) {
            context.getEventBus().publishAsync(AgentEvent.of(AgentEvent.STRATEGY_SIGNAL_APPROVED, name(), signal));
        } else {
            context.getEventBus().publishAsync(AgentEvent.of(AgentEvent.STRATEGY_SIGNAL_REJECTED, name(), signal));
        }
    }
}
