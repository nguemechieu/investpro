package org.investpro.core.agents;

import lombok.Getter;
import lombok.Setter;
import org.investpro.core.agents.execution.ExecutionAgent;
import org.investpro.core.agents.learning.LearningAgent;
import org.investpro.core.agents.market.MarketDataAgent;
import org.investpro.core.agents.portfolio.PortfolioAgent;
import org.investpro.core.agents.reasoning.ReasoningAgent;
import org.investpro.core.agents.risk.RiskAgent;
import org.investpro.core.agents.signal.SignalAgent;
import org.investpro.core.agents.strategy.StrategyAgent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Starts, stops, and coordinates all InvestPro agents.
 */
@Getter
@Setter
public class AgentRuntime {

    private static final Logger logger = LoggerFactory.getLogger(AgentRuntime.class);

    private final List<Agent> agents = new ArrayList<>();
    private AgentContext context;

    private boolean running;

    public static @NotNull AgentRuntime createDefault() {
        AgentRuntime runtime = new AgentRuntime();
        runtime.register(new MarketDataAgent());
        runtime.register(new SignalAgent());
        runtime.register(new StrategyAgent());
        runtime.register(new RiskAgent());
        runtime.register(new ReasoningAgent());
        runtime.register(new ExecutionAgent());
        runtime.register(new PortfolioAgent());
        runtime.register(new LearningAgent());
        return runtime;
    }

    public void register(Agent agent) {
        agents.add(Objects.requireNonNull(agent, "agent must not be null"));
    }

    public List<Agent> getAgents() {
        return Collections.unmodifiableList(agents);
    }

    public void start(AgentContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        if (context.getEventBus() == null) {
            context.setEventBus(new AgentEventBus());
        }
        context.getEventBus().start();

        for (Agent agent : agents) {
            context.getEventBus().subscribeAll(agent::onEvent);

            try {
                agent.start(context);
                logger.info("Agent started: {}", agent.name());
            } catch (Exception exception) {
                logger.error("Failed to start agent {}", agent.name(), exception);
            }
        }
        running = true;
    }

    public void stop() {
        for (Agent agent : agents) {
            try {
                agent.stop();
            } catch (Exception exception) {
                logger.warn("Failed to stop agent {}", agent.name(), exception);
            }
        }
        if (context != null && context.getEventBus() != null) {
            context.getEventBus().stop();
        }
        running = false;
    }

    public void publish(AgentEvent event) {
        if (context != null && context.getEventBus() != null) {
            context.getEventBus().publishAsync(event);
        }
    }

    public void setAutoTradingEnabled(boolean enabled) {
        if (context != null) {
            context.setAutoTradingEnabled(enabled);
        }
    }

    public void setAiReasoningEnabled(boolean enabled) {
        if (context != null) {
            context.setAiReasoningEnabled(enabled);
        }
    }

}
