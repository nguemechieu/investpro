package org.investpro.event;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.persistence.EventLogRepositoryImpl;

import java.util.function.Consumer;

/**
 * Listens to all application events and persists them to the database.
 * <p>
 * Wired to {@link EventBusManager} via {@code subscribeToAll} so every
 * published event is automatically stored for audit and replay purposes.
 */
@Slf4j
@SuppressWarnings("unused")
public class EventPersistenceListener implements Consumer<AgentEvent> {

    private final EventLogRepositoryImpl repository;

    public EventPersistenceListener(EventLogRepositoryImpl repository) {
        this.repository = repository;
    }

    @Override
    public void accept(AgentEvent event) {
        if (event == null) {
            return;
        }
        try {
            repository.save(event);
        } catch (Exception e) {
            log.warn("Failed to persist event type={} source={}: {}",
                    event.type(), event.source(), e.getMessage());
        }
    }
}
