package org.investpro.broker.repository;

import org.investpro.broker.events.BrokerActivityEvent;
import org.investpro.broker.events.BrokerActivityType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface BrokerActivityRepository {

    void append(@NotNull BrokerActivityEvent event);

    @NotNull
    List<BrokerActivityEvent> findAll();

    @NotNull
    List<BrokerActivityEvent> findByType(@NotNull BrokerActivityType type);

    @NotNull
    List<BrokerActivityEvent> findFills();
}
