package org.investpro.activity;

import org.investpro.models.trading.TradePair;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryBrokerActivityRepository implements BrokerActivityRepository {
    private final ConcurrentMap<String, BrokerActivityEvent> eventsByKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> projectedFlags = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> projectionErrors = new ConcurrentHashMap<>();

    @Override
    public void save(BrokerActivityEvent event) {
        if (event == null) return;
        eventsByKey.putIfAbsent(key(event.getExchangeId(), event.getEventId()), event);
    }

    @Override
    public void saveAll(List<BrokerActivityEvent> events) {
        if (events != null) events.forEach(this::save);
    }

    @Override
    public Optional<BrokerActivityEvent> findByEventId(String exchangeId, String eventId) {
        return Optional.ofNullable(eventsByKey.get(key(exchangeId, eventId)));
    }

    @Override
    public List<BrokerActivityEvent> findByTradePair(TradePair pair) {
        return sorted(eventsByKey.values().stream()
                .filter(e -> Objects.equals(pair, e.getTradePair()))
                .toList());
    }

    @Override
    public List<BrokerActivityEvent> findByOrderId(String exchangeId, String accountId, String orderId) {
        return sorted(eventsByKey.values().stream()
                .filter(e -> Objects.equals(exchangeId, e.getExchangeId()))
                .filter(e -> accountId == null || Objects.equals(accountId, e.getAccountId()))
                .filter(e -> Objects.equals(orderId, e.getOrderId()))
                .toList());
    }

    @Override
    public List<BrokerActivityEvent> findByTradeId(String exchangeId, String accountId, String tradeId) {
        return sorted(eventsByKey.values().stream()
                .filter(e -> Objects.equals(exchangeId, e.getExchangeId()))
                .filter(e -> accountId == null || Objects.equals(accountId, e.getAccountId()))
                .filter(e -> Objects.equals(tradeId, e.getTradeId()))
                .toList());
    }

    @Override
    public List<BrokerActivityEvent> findByPositionId(String exchangeId, String accountId, String positionId) {
        return sorted(eventsByKey.values().stream()
                .filter(e -> Objects.equals(exchangeId, e.getExchangeId()))
                .filter(e -> accountId == null || Objects.equals(accountId, e.getAccountId()))
                .filter(e -> Objects.equals(positionId, e.getPositionId()))
                .toList());
    }

    @Override
    public List<BrokerActivityEvent> findByTimeRange(String exchangeId, String accountId, Instant from, Instant to) {
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? Instant.now() : to;
        return sorted(eventsByKey.values().stream()
                .filter(e -> exchangeId == null || Objects.equals(exchangeId, e.getExchangeId()))
                .filter(e -> accountId == null || Objects.equals(accountId, e.getAccountId()))
                .filter(e -> !e.getEventTime().isBefore(start) && !e.getEventTime().isAfter(end))
                .toList());
    }

    @Override
    public List<BrokerActivityEvent> findUnprojectedEvents(String exchangeId, String accountId, int limit) {
        return eventsByKey.values().stream()
                .filter(e -> exchangeId == null || Objects.equals(exchangeId, e.getExchangeId()))
                .filter(e -> accountId == null || Objects.equals(accountId, e.getAccountId()))
                .filter(e -> !projectedFlags.getOrDefault(key(e.getExchangeId(), e.getEventId()), false))
                .filter(e -> !projectionErrors.containsKey(key(e.getExchangeId(), e.getEventId())))
                .sorted(Comparator.comparing(BrokerActivityEvent::getEventTime))
                .limit(limit <= 0 ? Integer.MAX_VALUE : limit)
                .toList();
    }

    @Override
    public void markProjected(String exchangeId, String eventId, Instant projectedAt) {
        projectedFlags.put(key(exchangeId, eventId), true);
        projectionErrors.remove(key(exchangeId, eventId));
    }

    @Override
    public void markProjectionFailed(String exchangeId, String eventId, String reason) {
        projectionErrors.put(key(exchangeId, eventId), reason == null ? "unknown" : reason);
    }

    @Override
    public boolean exists(String exchangeId, String eventId) {
        return eventsByKey.containsKey(key(exchangeId, eventId));
    }

    private static List<BrokerActivityEvent> sorted(List<BrokerActivityEvent> events) {
        return events.stream()
                .sorted(Comparator.comparing(BrokerActivityEvent::getEventTime)
                        .thenComparing(BrokerActivityEvent::getEventId))
                .toList();
    }

    private static String key(String exchangeId, String eventId) {
        return (exchangeId == null ? "unknown" : exchangeId) + "::" + (eventId == null ? "unknown" : eventId);
    }
}
