package org.investpro.broker.repository;

import org.investpro.broker.events.BrokerActivityEvent;
import org.investpro.broker.events.BrokerActivityType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory broker event repository.
 */
public class InMemoryBrokerActivityRepository implements BrokerActivityRepository {

    private final CopyOnWriteArrayList<BrokerActivityEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(@NotNull BrokerActivityEvent event) {
        events.add(event);
    }

    @Override
    public @NotNull List<BrokerActivityEvent> findAll() {
        return List.copyOf(events);
    }

    @Override
    public @NotNull List<BrokerActivityEvent> findByType(@NotNull BrokerActivityType type) {
        return events.stream()
                .filter(event -> event.type() == type)
                .toList();
    }

    @Override
    public @NotNull List<BrokerActivityEvent> findFills() {
        return events.stream()
                .filter(BrokerActivityEvent::isFill)
                .toList();
    }
}
