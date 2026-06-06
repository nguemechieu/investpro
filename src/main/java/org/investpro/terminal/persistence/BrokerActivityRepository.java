package org.investpro.terminal.persistence;

import org.investpro.terminal.domain.BrokerActivityEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BrokerActivityRepository {
    BrokerActivityEvent save(BrokerActivityEvent event);
    List<BrokerActivityEvent> saveAll(List<BrokerActivityEvent> events);
    boolean exists(String providerId, String accountId, String eventId);
    Optional<BrokerActivityEvent> findByEventId(String providerId, String accountId, String eventId);
    List<BrokerActivityEvent> findByOrderId(String providerId, String accountId, String orderId);
    List<BrokerActivityEvent> findByTimeRange(String providerId, String accountId, Instant from, Instant to);
    List<BrokerActivityEvent> findUnprojected(String providerId, String accountId, int limit);
    void markProjected(String providerId, String accountId, String eventId, Instant projectedAt);
}
