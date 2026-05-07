package org.investpro.core.agents.learning;

import lombok.extern.slf4j.Slf4j;

import lombok.Getter;
import lombok.Setter;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
/**
 * Records learning observations from trade lifecycle outcomes.
 */
@Getter
@Setter
@Slf4j
public class LearningAgent implements Agent {
    private AgentContext context;
    private boolean running;

    @Override
    public String name() {
        return "LearningAgent";
    }

    @Override
    public void start(AgentContext context) {
        this.context = context;
        this.running = true;
        log.info("LearningAgent started");
    }

    @Override
    public void stop() {
        running = false;
        log.info("LearningAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            log.error("LearningAgent event was null");
            return;
        }

        switch (event.type()) {
            case AgentEvent.ORDER_FILLED,
                 AgentEvent.POSITION_CLOSED,
                 AgentEvent.RISK_REJECTED,
                 AgentEvent.REASONING_REJECTED,
                 AgentEvent.ORDER_REJECTED -> {
                log.info("Learning observation from {}: {}", event.type(), event.payload());
                context.getEventBus().publishAsync(AgentEvent.learning(name(), event));
            }
            default -> {
            }
        }
    }
}
