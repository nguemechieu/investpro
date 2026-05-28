package org.investpro.decision;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event emitted when a trade order is fully filled at the execution venue.
 *
 * @param decisionId    unique decision identifier
 * @param fillPrice     actual fill price
 * @param fillQuantity  actual filled quantity
 * @param venue         execution venue that filled the order
 * @param occurredAt    timestamp when the fill was received
 */
public record ExecutionFilledEvent(
        String decisionId,
        BigDecimal fillPrice,
        BigDecimal fillQuantity,
        ExecutionVenueType venue,
        Instant occurredAt
) {
    public ExecutionFilledEvent {
        if (fillPrice == null)    fillPrice = BigDecimal.ZERO;
        if (fillQuantity == null) fillQuantity = BigDecimal.ZERO;
        if (occurredAt == null)   occurredAt = Instant.now();
    }

    public static ExecutionFilledEvent of(
            String decisionId, BigDecimal price, BigDecimal qty, ExecutionVenueType venue) {
        return new ExecutionFilledEvent(decisionId, price, qty, venue, Instant.now());
    }
}
