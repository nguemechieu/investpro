package org.investpro.core.agents.portfolio;

import lombok.Getter;
import lombok.Setter;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks latest account, order, fill, and position events.
 */
@Getter
@Setter
public class PortfolioAgent implements Agent {

    private AgentContext context;
    private boolean running;
    AtomicReference<Object> lastAccount = new AtomicReference<>();
     AtomicReference<Object> lastPositions = new AtomicReference<>();

    private static  final Logger logger = LoggerFactory.getLogger(PortfolioAgent.class);

    @Override
    public String name() {
        return "PortfolioAgent";
    }

    @Override
    public void start(AgentContext context) {
        this.context = context;
        this.running = true;
        logger.info("PortfolioAgent started");
    }

    @Override
    public void stop() {
        running = false;
        lastAccount.set(null);
        lastPositions.set(null);
        logger.info("PortfolioAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            lastPositions.set(null);
            lastAccount.set(null);
            logger.warn("PortfolioAgent onEvent received null event");

            return;
        }

        switch (event.type()) {
            case AgentEvent.ACCOUNT_UPDATE -> {
                lastAccount.set(event.payload());
                 context.getEventBus().publishAsync(AgentEvent.portfolio(AgentEvent.PORTFOLIO_UPDATED, name(), event.payload()));
                 logger.info("PortfolioAgent onEvent account updated");
            }

            case AgentEvent.POSITION_UPDATE -> {
                lastPositions.set(event.payload());
                context.getEventBus().publishAsync(AgentEvent.portfolio(AgentEvent.EXPOSURE_UPDATED, name(), event.payload()));
                logger.info("PortfolioAgent onEvent position updated");
            }
            case AgentEvent.FILL_UPDATE, AgentEvent.ORDER_FILLED ->{
                    context.getEventBus().publishAsync(AgentEvent.portfolio(AgentEvent.PNL_UPDATED, name(), event.payload()));
                    logger.info("PortfolioAgent onEvent filled");
            }
            default -> {
            }
        }
    }
}
