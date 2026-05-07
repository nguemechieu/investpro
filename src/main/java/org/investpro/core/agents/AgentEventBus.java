package org.investpro.core.agents;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Lightweight event bus for InvestPro agents.
 */
@Slf4j

public class AgentEventBus {
    private final Map<String, List<Consumer<AgentEvent>>> subscribers = new ConcurrentHashMap<>();
    private final List<Consumer<AgentEvent>> allSubscribers = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "investpro-agent-event-bus");
        thread.setDaemon(true);
        return thread;
    });

    public AgentEventBus() {
    }

    private final AtomicBoolean running = new AtomicBoolean(false);

    public void start() {
        running.set(true);
    }

    public void stop() {
        running.set(false);
        executor.shutdownNow();
    }

    public void subscribe(String eventType, Consumer<AgentEvent> handler) {
        if (eventType == null || handler == null) {
            return;
        }
        subscribers.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void subscribeAll(Consumer<AgentEvent> handler) {
        if (handler != null) {
            allSubscribers.add(handler);
        }
    }

    public void publish(AgentEvent event) {
        if (event == null || !running.get()) {
            return;
        }

        List<Consumer<AgentEvent>> typedHandlers = subscribers.getOrDefault(event.type(), List.of());

        for (Consumer<AgentEvent> handler : typedHandlers) {
            safeAccept(handler, event);
        }

        for (Consumer<AgentEvent> handler : allSubscribers) {
            safeAccept(handler, event);
        }
    }

    public void publishAsync(AgentEvent event) {
        if (event == null || !running.get()) {
            return;
        }
        executor.submit(() -> publish(event));
    }

    private void safeAccept(Consumer<AgentEvent> handler, AgentEvent event) {
        try {
            handler.accept(event);
        } catch (Exception exception) {
            log.error("Agent event handler failed for {}", event, exception);
        }
    }
}
