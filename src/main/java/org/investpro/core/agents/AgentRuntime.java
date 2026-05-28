package org.investpro.core.agents;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.learning.LearningAgent;
import org.investpro.core.execution.ExecutionAgent;
import org.investpro.market.MarketDataAgent;
import org.investpro.models.trading.TradePair;
import org.investpro.portfolio.PortfolioAgent;
import org.investpro.reasoning.ReasoningAgent;
import org.investpro.signal.SignalAgent;
import org.investpro.strategy.StrategyAgent;
import org.investpro.symbol.SymbolAgent;
import org.investpro.symbol.SymbolAgentManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Starts, stops, and coordinates all InvestPro agents.
 */
@Getter
@Setter
@Slf4j
public class AgentRuntime {
    private final List<Agent> agents = new CopyOnWriteArrayList<>();
    private AgentContext context;
    private volatile boolean running;

    public static @NotNull AgentRuntime createDefault() {
        AgentRuntime runtime = new AgentRuntime();
        runtime.register(new MarketDataAgent());
        runtime.register(new SignalAgent());
        runtime.register(new StrategyAgent());
        runtime.register(new ReasoningAgent());
        runtime.register(new ExecutionAgent());
        runtime.register(new PortfolioAgent());
        runtime.register(new LearningAgent());
        return runtime;
    }

    public boolean register(Agent agent) {
        Agent newAgent = Objects.requireNonNull(agent, "agent must not be null");
        String newAgentName = safeName(newAgent);
        boolean alreadyRegistered = agents.stream()
                .anyMatch(existing -> newAgentName.equals(safeName(existing)));
        if (alreadyRegistered) {
            log.debug("Agent already registered: {}", newAgentName);
            return false;
        }

        agents.add(newAgent);
        return true;
    }

    /**
     * Register and start a per-symbol SymbolAgent.
     * If the runtime is already running the agent is started immediately;
     * otherwise it will be started with the rest of the agents when start() is called.
     */
    public boolean registerSymbol(@NotNull TradePair symbol, @NotNull SymbolAgentManager symbolAgentManager) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(symbolAgentManager, "symbolAgentManager must not be null");

        String agentName = "SymbolAgent[" + symbol.toString('/') + "]";
        boolean alreadyRegistered = agents.stream()
                .anyMatch(agent -> agentName.equals(safeName(agent)));
        if (alreadyRegistered) {
            return false;
        }

        SymbolAgent symbolAgent = new SymbolAgent(symbol, symbolAgentManager);
        agents.add(symbolAgent);

        if (running && context != null) {
            context.getEventBus().subscribeAll(symbolAgent::onEvent);
            try {
                symbolAgent.start(context);
                log.info("SymbolAgent started: {}", symbolAgent.name());
            } catch (Exception exception) {
                agents.remove(symbolAgent);
                throw new RuntimeException("Failed to start SymbolAgent " + symbolAgent.name(), exception);
            }
        }

        return true;
    }

    public void start(AgentContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        if (context.getEventBus() == null) {
            context.setEventBus(new AgentEventBus());
        }

        context.getEventBus().start();
        running = true;

        for (Agent agent : agents) {
            try {
                context.getEventBus().subscribeAll(agent::onEvent);
                agent.start(context);
                log.info("Agent started: {}", agent.name());
            } catch (Exception exception) {
                log.error("Failed to start agent {}", agent.name(), exception);
            }
        }
    }

    public void stop() {
        for (Agent agent : agents) {
            try {
                agent.stop();
            } catch (Exception exception) {
                log.warn("Failed to stop agent {}", agent.name(), exception);
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

    private String safeName(Agent agent) {
        if (agent == null) {
            return "";
        }

        String name = agent.name();
        return name == null ? "" : name.trim();
    }
}
