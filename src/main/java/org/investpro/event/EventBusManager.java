package org.investpro.event;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Application-level event bus singleton.
 * <p>
 * Provides a central publish/subscribe hub for application events
 * (as opposed to the agent-scoped
 * {@link org.investpro.core.agents.AgentEventBus}).
 * Any component can subscribe to all events via
 * {@link #subscribeToAll(Consumer)}.
 */
@Slf4j
public class EventBusManager {

    private static final EventBusManager INSTANCE = new EventBusManager();

    private final List<Consumer<AgentEvent>> allSubscribers = new CopyOnWriteArrayList<>();
    private final Map<String, List<Consumer<AgentEvent>>> typedSubscribers = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "investpro-event-bus-manager");
        t.setDaemon(true);
        return t;
    });

    private EventBusManager() {
    }

    public static EventBusManager getInstance() {
        return INSTANCE;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("EventBusManager started.");
        }
    }

    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            executor.shutdownNow();
            log.info("EventBusManager shutdown.");
        }
    }

    /**
     * Subscribe a listener that receives every published event.
     */
    public void subscribeToAll(Consumer<AgentEvent> listener) {
        if (listener != null) {
            allSubscribers.add(listener);
        }
    }

    /**
     * Subscribe a listener for a specific event type.
     */
    public void subscribe(String eventType, Consumer<AgentEvent> listener) {
        if (eventType == null || eventType.isBlank() || listener == null) {
            return;
        }
        typedSubscribers
                .computeIfAbsent(eventType.trim(), ignored -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    /**
     * Publish an event to all subscribers asynchronously.
     */
    public void publish(AgentEvent event) {
        if (event == null || !running.get()) {
            return;
        }
        executor.submit(() -> {
            for (Consumer<AgentEvent> subscriber : allSubscribers) {
                try {
                    subscriber.accept(event);
                } catch (Exception e) {
                    log.error("EventBusManager subscriber failed for event {}", event.type(), e);
                }
            }

            List<Consumer<AgentEvent>> perType = typedSubscribers.get(event.type());
            if (perType == null || perType.isEmpty()) {
                return;
            }

            for (Consumer<AgentEvent> subscriber : perType) {
                try {
                    subscriber.accept(event);
                } catch (Exception e) {
                    log.error("EventBusManager typed subscriber failed for event {}", event.type(), e);
                }
            }
        });
    }
}
