package org.investpro.core.agents;

import org.investpro.dependency.SystemCoreDependencies;
import org.jetbrains.annotations.NotNull;

/**
 * Plugin interface for registering a collection of related agents.
 * <p>
 * Each AgentModule is responsible for:
 * - Creating instances of its agents
 * - Registering them with the AgentRegistry
 * - Optionally wiring them with dependencies from SystemCoreDependencies
 * <p>
 * Modules are instantiated and called during SystemCore initialization,
 * allowing for composable agent architectures without hard-coding agent
 * instantiation inside SystemCore.
 */
public interface AgentModule {

    /**
     * Unique identifier for this module.
     */
    @NotNull
    String moduleId();

    /**
     * Configure agents and register them with the provided registry.
     * <p>
     * Called once during SystemCore initialization.
     *
     * @param registry     agent registry to register agents with
     * @param dependencies system core dependencies (immutable)
     */
    void configure(
            @NotNull AgentRegistry registry,
            @NotNull SystemCoreDependencies dependencies);
}
