package org.investpro.core.agents;

/**
 * Base contract for all InvestPro agents.
 *
 * Agents receive events from the AgentEventBus and may publish new events.
 * Agents must not block the JavaFX UI thread.
 */
public interface Agent {

    String name();

    void start(AgentContext context);

    void stop();

    void onEvent(AgentEvent event);
}
