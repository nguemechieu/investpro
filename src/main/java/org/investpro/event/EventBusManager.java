package org.investpro.event;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Application-level event bus singleton.
 * <p>
 * Provides a central publish/subscribe hub for application events
 * (as opposed to the agent-scoped {@link org.investpro.core.agents.AgentEventBus}).
 * Any component can subscribe to all events via {@link #subscribeToAll(Consumer)}.
 */
@Slf4j
public class EventBusManager {

    private static final EventBusManager INSTANCE = new EventBusManager();

    private final List<Consumer<AgentEvent>> allSubscribers = new CopyOnWriteArrayList<>();
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
        });
    }
}
