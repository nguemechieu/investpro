package org.investpro.core.agents;

/**
 * Base contract for all InvestPro agents.
 * <p>
 * Agents receive events from the AgentEventBus and may publish new events.
 * Agents must not block the JavaFX UI thread.
 * <p>
 * Each agent must implement:
 * - name(): unique agent identifier
 * - start(AgentContext): initialize agent resources
 * - stop(): clean up agent resources
 * - onEvent(AgentEvent): handle incoming events
 */
public interface Agent {

    /**
     * Unique identifier for this agent.
     * <p>
     * Used for registration and lookup in AgentRegistry.
     * Must be consistent across agent lifetime.
     *
     * @return agent identifier (non-null, non-blank)
     */
    String name();

    /**
     * Start the agent with the given context.
     * <p>
     * Called once during bot startup. Agent should initialize
     * its state and become ready to process events.
     *
     * @param context agent context (non-null)
     */
    void start(AgentContext context);

    /**
     * Stop the agent and clean up resources.
     * <p>
     * Called once during bot shutdown. Agent should release
     * any held resources and stop processing events.
     */
    void stop();

    /**
     * Handle an incoming event from the agent event bus.
     * <p>
     * This method must not block the calling thread.
     * If async processing is needed, use CompletableFuture
     * or executor service.
     *
     * @param event incoming event (may be null)
     */
    void onEvent(AgentEvent event);

    /**
     * Check if the agent is currently running.
     * <p>
     * Default implementation returns false.
     * Agents should override to provide accurate status.
     *
     * @return true if agent is started and operational
     */
    default boolean isRunning() {
        return false;
    }
}
