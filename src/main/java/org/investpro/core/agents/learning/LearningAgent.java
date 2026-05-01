package org.investpro.core.agents.learning;

import lombok.Getter;
import lombok.Setter;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records learning observations from trade lifecycle outcomes.
 */
@Getter
@Setter
public class LearningAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(LearningAgent.class);

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
        logger.info("LearningAgent started");
    }

    @Override
    public void stop() {
        running = false;
        logger.info("LearningAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            logger.error("LearningAgent event was null");
            return;
        }

        switch (event.type()) {
            case AgentEvent.ORDER_FILLED,
                 AgentEvent.POSITION_CLOSED,
                 AgentEvent.RISK_REJECTED,
                 AgentEvent.REASONING_REJECTED,
                 AgentEvent.ORDER_REJECTED -> {
                logger.info("Learning observation from {}: {}", event.type(), event.payload());
                context.getEventBus().publishAsync(AgentEvent.learning(name(), event));
            }
            default -> {
            }
        }
    }
}
