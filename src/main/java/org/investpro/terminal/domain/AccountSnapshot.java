package org.investpro.terminal.domain;

import java.time.Instant;
import java.util.List;

public record AccountSnapshot(
        String providerId,
        String accountId,
        List<Balance> balances,
        List<Position> positions,
        Instant timestamp
) {
    public AccountSnapshot {
        providerId = providerId == null ? "" : providerId.trim();
        accountId = accountId == null ? "" : accountId.trim();
        balances = balances == null ? List.of() : List.copyOf(balances);
        positions = positions == null ? List.of() : List.copyOf(positions);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }
}
