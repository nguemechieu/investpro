package org.investpro.transfer;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferRequest(
        String fromProvider,
        String fromAccount,
        String toProvider,
        String toAccount,
        String currency,
        BigDecimal amount,
        String notes,
        String network,
        int priority,
        Instant requestedAt
) {

    public TransferRequest {
        requestedAt = requestedAt == null ? Instant.now() : requestedAt;
        amount = amount == null ? BigDecimal.ZERO : amount;
        fromProvider = safe(fromProvider);
        fromAccount = safe(fromAccount);
        toProvider = safe(toProvider);
        toAccount = safe(toAccount);
        currency = safe(currency);
        notes = safe(notes);
        network = safe(network);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
