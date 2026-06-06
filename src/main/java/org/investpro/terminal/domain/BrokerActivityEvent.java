package org.investpro.terminal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record BrokerActivityEvent(
        String eventId,
        String providerId,
        String accountId,
        InstrumentId instrumentId,
        BrokerActivityEventType eventType,
        OrderId orderId,
        String fillId,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fee,
        String status,
        String message,
        String cursor,
        Instant eventTime,
        Instant receivedAt,
        String rawPayloadJson,
        Map<String, Object> metadata
) {
    public BrokerActivityEvent {
        eventId = eventId == null ? "" : eventId.trim();
        providerId = providerId == null ? "" : providerId.trim();
        accountId = accountId == null ? "" : accountId.trim();
        eventType = eventType == null ? BrokerActivityEventType.RECONCILIATION_DIFFERENCE_FOUND : eventType;
        fillId = fillId == null ? "" : fillId.trim();
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        price = price == null ? BigDecimal.ZERO : price;
        fee = fee == null ? BigDecimal.ZERO : fee;
        status = status == null ? "" : status.trim();
        message = message == null ? "" : message.trim();
        cursor = cursor == null ? "" : cursor.trim();
        eventTime = eventTime == null ? Instant.now() : eventTime;
        receivedAt = receivedAt == null ? Instant.now() : receivedAt;
        rawPayloadJson = rawPayloadJson == null ? "" : rawPayloadJson;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required for broker activity deduplication");
        }
    }

    public String dedupeKey() {
        return providerId + ":" + accountId + ":" + eventId;
    }
}
