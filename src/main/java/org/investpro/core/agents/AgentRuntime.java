package org.investpro.core.agents;

import lombok.extern.slf4j.Slf4j;

import lombok.Getter;
import lombok.Setter;
import org.investpro.core.agents.execution.ExecutionAgent;
import org.investpro.core.agents.learning.LearningAgent;
import org.investpro.core.agents.market.MarketDataAgent;
import org.investpro.core.agents.portfolio.PortfolioAgent;
import org.investpro.core.agents.reasoning.ReasoningAgent;
import org.investpro.core.agents.modules.SignalAgent;
import org.investpro.core.agents.strategy.StrategyAgent;
import org.investpro.core.agents.symbol.SymbolAgent;
import org.investpro.core.agents.symbol.SymbolAgentManager;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Starts, stops, and coordinates all InvestPro agents.
 */
@Getter
@Setter
@Slf4j
public class AgentRuntime {
    private final List<Agent> agents = new ArrayList<>();
    private AgentContext context;

    private volatile boolean running;
    private final java.util.Map<Agent, java.util.function.Consumer<AgentEvent>> eventHandlers = new java.util.IdentityHashMap<>();
    private volatile long generation;
    private SymbolAgent agent;

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

    public synchronized void register(Agent agent) {
        agents.add(Objects.requireNonNull(agent, "agent must not be null"));
    }

    /**
     * Bulk-import agents from an AgentRegistry into this runtime.
     * Call before start() to load agents configured by a module.
     */
    public synchronized void importFrom(@NotNull AgentRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null");
        for (Agent agent : registry.getAgents()) {
            agents.add(Objects.requireNonNull(agent));
        }
    }

    /**
     * Register and start a per-symbol SymbolAgent.
     * If the runtime is already running the agent is started immediately;
     * otherwise it will be started with the rest of the agents when start() is called.
     */
    public synchronized void registerSymbol(@NotNull TradePair symbol, @NotNull SymbolAgentManager symbolAgentManager) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(symbolAgentManager, "symbolAgentManager must not be null");
        String agentName = "SymbolAgent[" + symbol.toString('/') + "]";
        // Null-safe name check — avoids NPE if any agent returns null from name()
        boolean alreadyRegistered = agents.stream()
                .anyMatch(a -> agentName.equals(a.name()));
        if (alreadyRegistered) return;
        agent = new SymbolAgent(symbol, symbolAgentManager);
        agents.add(agent);
        if (running && context != null) {
            context.getEventBus().subscribeAll(handlerFor(agent));
            try {
                agent.start(context);
                log.info("SymbolAgent started: {}", agent.name());
            } catch (Exception e) {
                throw  new RuntimeException("Failed to start SymbolAgent {}"+ agent.name(), e);
            }
        }
    }

    public synchronized void start(AgentContext context) {
        if (running) return;
        this.context = Objects.requireNonNull(context, "context must not be null");
        if (context.getEventBus() == null) {
            context.setEventBus(new AgentEventBus());
        }
        context.getEventBus().start();
        running = true;

        for (Agent agent : agents) {

            try {
                agent.start(context);
                context.getEventBus().subscribeAll(handlerFor(agent));
                log.info("Agent started: {}", agent.name());
                running = true;
            } catch (Exception exception) {
                log.error("Failed to start agent {}", agent.name(), exception);

            }
        }

    }

    public synchronized void stop() {
        running = false;
        generation++;
        if (context != null && context.getEventBus() != null)
            eventHandlers.values().forEach(context.getEventBus()::unsubscribeAll);
        eventHandlers.clear();
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

    private java.util.function.Consumer<AgentEvent> handlerFor(Agent agent) {
        return eventHandlers.computeIfAbsent(agent, _ -> {
            String name = agent.name();
            java.util.concurrent.Executor worker = switch (name == null ? "" : name) {
                case "MarketDataAgent" -> org.investpro.core.concurrent.AppExecutors.MARKET_DATA;
                case "RiskAgent" -> org.investpro.core.concurrent.AppExecutors.RISK;
                case "ExecutionAgent", "PositionManagementAgent" -> org.investpro.core.concurrent.AppExecutors.TRADING;
                case "ReasoningAgent" -> org.investpro.core.concurrent.AppExecutors.ASSISTANT;
                case "LearningAgent" -> org.investpro.core.concurrent.AppExecutors.BACKTEST;
                case "PortfolioAgent", "AuditAgent" -> org.investpro.core.concurrent.AppExecutors.IO;
                default -> org.investpro.core.concurrent.AppExecutors.STRATEGY;
            };
            var lane = new org.investpro.core.concurrent.OrderedExecutor(worker, 256);
            long session = generation;
            return event -> lane.execute(() -> {
                if (running && session == generation) {
                    try { agent.onEvent(event); }
                    catch (Exception error) { log.error("Agent {} failed processing {}", agent.name(), event.type(), error); }
                }
            });
        });
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
