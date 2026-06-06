package org.investpro.terminal.domain;

import java.time.Instant;
import java.util.List;

public record OrderBookSnapshot(
        InstrumentId instrumentId,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks,
        Instant timestamp,
        String sequence
) {
    public OrderBookSnapshot {
        if (instrumentId == null) {
            throw new IllegalArgumentException("instrumentId is required");
        }
        bids = bids == null ? List.of() : List.copyOf(bids);
        asks = asks == null ? List.of() : List.copyOf(asks);
        timestamp = timestamp == null ? Instant.now() : timestamp;
        sequence = sequence == null ? "" : sequence.trim();
    }
}
