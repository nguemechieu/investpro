package org.investpro.activity;

import org.investpro.models.trading.TradePair;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BrokerActivityRepository {
    void save(BrokerActivityEvent event);

    void saveAll(List<BrokerActivityEvent> events);

    Optional<BrokerActivityEvent> findByEventId(String exchangeId, String eventId);

    List<BrokerActivityEvent> findByTradePair(TradePair pair);

    List<BrokerActivityEvent> findByOrderId(String exchangeId, String accountId, String orderId);

    List<BrokerActivityEvent> findByTradeId(String exchangeId, String accountId, String tradeId);

    List<BrokerActivityEvent> findByPositionId(String exchangeId, String accountId, String positionId);

    List<BrokerActivityEvent> findByTimeRange(String exchangeId, String accountId, Instant from, Instant to);

    List<BrokerActivityEvent> findUnprojectedEvents(String exchangeId, String accountId, int limit);

    void markProjected(String exchangeId, String eventId, Instant projectedAt);

    void markProjectionFailed(String exchangeId, String eventId, String reason);

    boolean exists(String exchangeId, String eventId);

    /** Legacy overload for callers that don't have accountId. */
    default List<BrokerActivityEvent> findByOrderId(String exchangeId, String orderId) {
        return findByOrderId(exchangeId, null, orderId);
    }

    default List<BrokerActivityEvent> findByTradeId(String exchangeId, String tradeId) {
        return findByTradeId(exchangeId, null, tradeId);
    }

    default List<BrokerActivityEvent> findByTimeRange(String exchangeId, Instant from, Instant to) {
        return findByTimeRange(exchangeId, null, from, to);
    }
}
