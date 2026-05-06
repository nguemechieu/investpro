package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCoreDependencies;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentModule;
import org.investpro.core.agents.AgentRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Default trading agent module that registers the standard set of agents.
 * <p>
 * Registers:
 * - MarketDataAgent: collects market data
 * - SignalAgent: generates trading signals
 * - RiskAgent: evaluates risk
 * - PortfolioAgent: monitors portfolio
 * - PositionManagementAgent: manages position lifecycle
 * - ExecutionAgent: coordinates trade execution
 * - AuditAgent: logs and audits events
 * <p>
 * This module is a default implementation that can be replaced or
 * extended with custom agent modules.
 */
@Slf4j
public class DefaultTradingAgentModule implements AgentModule {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DefaultTradingAgentModule.class);

    @Override
    public @NotNull String moduleId() {
        return "DefaultTradingAgentModule";
    }

    @Override
    public void configure(
            @NotNull AgentRegistry registry,
            @NotNull SystemCoreDependencies dependencies) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(dependencies, "dependencies cannot be null");

        try {
            // Register agents in logical order
            registerAgent(registry, new MarketDataAgent());
            registerAgent(registry, new SignalAgent());
            registerAgent(registry, new RiskAgent());
            registerAgent(registry, new PortfolioAgent());
            registerAgent(registry, new PositionManagementAgent());
            registerAgent(registry, new ExecutionAgent());
            registerAgent(registry, new AuditAgent());

            log.info(
                    "DefaultTradingAgentModule configured. agents registered={}",
                    registry.size());

        } catch (Exception e) {
            log.error("Failed to configure DefaultTradingAgentModule", e);
            throw new RuntimeException("Failed to configure DefaultTradingAgentModule", e);
        }
    }

    private void registerAgent(@NotNull AgentRegistry registry, @NotNull Agent agent) {
        Objects.requireNonNull(registry, "registry cannot be null");
        Objects.requireNonNull(agent, "agent cannot be null");

        try {
            registry.register(agent);
            log.debug("Agent registered: {}", agent.name());

        } catch (Exception e) {
            log.error("Failed to register agent: {}", agent.name(), e);
            throw e;
        }
    }
}
