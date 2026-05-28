package org.investpro.core;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentRegistry;
import org.investpro.dependency.SystemCoreDependencies;
import org.investpro.signal.SignalAgent;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Registers the default trading agents used by InvestPro.
 *
 * <p>This module should be called after SystemCore has initialized its core
 * dependencies and after TradingService is available.</p>
 *
 * <p>Safety rule: agents registered here should analyze, monitor, filter,
 * or publish events. They should not bypass the normal risk/execution pipeline.</p>
 */
@Slf4j
public class DefaultTradingAgentModule {

    private static final List<String> OPTIONAL_AGENT_CLASS_NAMES = List.of(
            /*
             * Add future agent class names here when they exist.
             *
             * Examples:
             * "org.investpro.signal.RiskAgent",
             * "org.investpro.signal.ExecutionAgent",
             * "org.investpro.signal.PortfolioAgent",
             * "org.investpro.signal.NewsAgent",
             * "org.investpro.signal.StrategySelectionAgent"
             */
    );

    public void configure(
            @NotNull AgentRegistry agentRegistry,
            @NotNull SystemCoreDependencies systemCoreDependencies
    ) {
        Objects.requireNonNull(agentRegistry, "agentRegistry must not be null");
        Objects.requireNonNull(systemCoreDependencies, "systemCoreDependencies must not be null");

        List<Agent> agents = new ArrayList<>();

        /*
         * Core default agent.
         * SignalAgent listens to market events and publishes SIGNAL events.
         * It does not place trades directly.
         */
        agents.add(new SignalAgent());

        /*
         * Optional future agents loaded by class name.
         * This lets the module stay stable while the agent system grows.
         */
        agents.addAll(loadOptionalAgents());

        int registered = 0;

        for (Agent agent : agents) {
            if (agent == null) {
                continue;
            }

            if (registerAgent(agentRegistry, agent)) {
                registered++;
                log.info("Registered default trading agent: {}", safeAgentName(agent));
            }
        }

        log.info("DefaultTradingAgentModule configured. registeredAgents={}", registered);
    }

    private List<Agent> loadOptionalAgents() {
        List<Agent> agents = new ArrayList<>();

        for (String className : OPTIONAL_AGENT_CLASS_NAMES) {
            if (className == null || className.isBlank()) {
                continue;
            }

            try {
                Class<?> type = Class.forName(className.trim());

                if (!Agent.class.isAssignableFrom(type)) {
                    log.warn("Skipping optional agent because it does not implement Agent: {}", className);
                    continue;
                }

                Object instance = type.getDeclaredConstructor().newInstance();
                agents.add((Agent) instance);

            } catch (ClassNotFoundException ignored) {
                log.debug("Optional agent not found: {}", className);
            } catch (Exception exception) {
                log.warn("Failed to load optional agent {}: {}", className, exception.getMessage(), exception);
            }
        }

        return agents;
    }

    private boolean registerAgent(@NotNull AgentRegistry registry, @NotNull Agent agent) {
        /*
         * Support common registry APIs without forcing one exact method name.
         * This helps while AgentRegistry is still evolving.
         */
        for (String methodName : List.of("register", "add", "registerAgent", "addAgent")) {
            if (invokeRegistryMethod(registry, methodName, agent)) {
                return true;
            }
        }

        throw new IllegalStateException(
                "AgentRegistry does not expose a supported registration method. " +
                        "Expected one of: register(Agent), add(Agent), registerAgent(Agent), addAgent(Agent)."
        );
    }

    private boolean invokeRegistryMethod(
            @NotNull AgentRegistry registry,
            @NotNull String methodName,
            @NotNull Agent agent
    ) {
        try {
            Method method = registry.getClass().getMethod(methodName, Agent.class);
            method.invoke(registry, agent);
            return true;

        } catch (NoSuchMethodException ignored) {
            return false;

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to register agent %s using AgentRegistry.%s(Agent)"
                            .formatted(safeAgentName(agent), methodName),
                    exception
            );
        }
    }

    private String safeAgentName(@NotNull Agent agent) {
        try {
            String name = agent.name();
            return name == null || name.isBlank()
                    ? agent.getClass().getSimpleName()
                    : name.trim();
        } catch (Exception ignored) {
            return agent.getClass().getSimpleName();
        }
    }
}