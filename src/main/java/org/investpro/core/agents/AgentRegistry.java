package org.investpro.core.agents;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe registry for managing agent instances.
 * <p>
 * Responsibilities:
 * - register/unregister agents
 * - retrieve agents by ID
 * - start/stop all registered agents
 * - prevent duplicate agent IDs
 * - log errors without crashing the runtime
 */
@Slf4j
public class AgentRegistry {
    private final Map<String, Agent> agents = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Register a new agent.
     * <p>
     * Throws IllegalArgumentException if an agent with the same ID already exists.
     *
     * @param agent agent to register (must not be null)
     */
    public void register(@NotNull Agent agent) {
        Objects.requireNonNull(agent, "agent cannot be null");

        String id = agent.name();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Agent name cannot be null or blank");
        }

        lock.writeLock().lock();
        try {
            if (agents.containsKey(id)) {
                throw new IllegalArgumentException(
                        "Agent with id '%s' is already registered".formatted(id));
            }

            agents.put(id, agent);
            log.debug("Agent registered: id={}", id);

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Unregister an agent by ID.
     * <p>
     * Does nothing if no agent with that ID exists.
     *
     * @param agentId ID of the agent to unregister
     */
    public void unregister(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId cannot be null");

        lock.writeLock().lock();
        try {
            if (agents.remove(agentId) != null) {
                log.debug("Agent unregistered: id={}", agentId);
            }

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get all registered agents.
     * <p>
     * Returns an unmodifiable list.
     *
     * @return list of all registered agents
     */
    @NotNull
    public List<Agent> getAgents() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(agents.values()));

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get an agent by its ID.
     *
     * @param id agent ID
     * @return optional containing the agent, or empty if not found
     */
    @NotNull
    public Optional<Agent> getAgent(@NotNull String id) {
        Objects.requireNonNull(id, "id cannot be null");

        lock.readLock().lock();
        try {
            return Optional.ofNullable(agents.get(id));

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if an agent with the given ID is registered.
     *
     * @param id agent ID
     * @return true if registered, false otherwise
     */
    public boolean hasAgent(@NotNull String id) {
        Objects.requireNonNull(id, "id cannot be null");

        lock.readLock().lock();
        try {
            return agents.containsKey(id);

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Start all registered agents.
     * <p>
     * If an agent fails to start, logs the error and continues starting
     * the remaining agents.
     *
     * @param context agent context to pass to all agents
     */
    public void startAll(@NotNull AgentContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        lock.readLock().lock();
        try {
            for (Agent agent : agents.values()) {
                try {
                    if (context.getEventBus() != null) {
                        context.getEventBus().subscribeAll(agent::onEvent);
                    }
                    agent.start(context);
                    log.debug("Agent started: id={}", agent.name());

                } catch (Exception e) {
                    log.error("Failed to start agent: id={}", agent.name(), e);
                }
            }

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Stop all registered agents.
     * <p>
     * If an agent fails to stop, logs the error and continues stopping
     * the remaining agents.
     */
    public void stopAll() {
        lock.readLock().lock();
        try {
            for (Agent agent : agents.values()) {
                try {
                    agent.stop();
                    log.debug("Agent stopped: id={}", agent.name());

                } catch (Exception e) {
                    log.error("Failed to stop agent: id={}", agent.name(), e);
                }
            }

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Publish an event to all registered agents.
     * <p>
     * Calls onEvent(event) for each agent. If an agent throws an exception,
     * logs the error and continues publishing to remaining agents.
     *
     * @param event event to publish
     */
    public void publishEvent(@NotNull AgentEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        lock.readLock().lock();
        try {
            for (Agent agent : agents.values()) {
                try {
                    agent.onEvent(event);

                } catch (Exception e) {
                    log.error("Agent event handler failed: id={}", agent.name(), e);
                }
            }

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the number of registered agents.
     */
    public int size() {
        lock.readLock().lock();
        try {
            return agents.size();

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clear all registered agents.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            agents.clear();
            log.debug("Agent registry cleared");

        } finally {
            lock.writeLock().unlock();
        }
    }
}
