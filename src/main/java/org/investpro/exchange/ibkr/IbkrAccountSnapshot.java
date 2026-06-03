package org.investpro.exchange.ibkr;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record IbkrAccountSnapshot(
        String accountId,
        String broker,
        boolean paper,
        double equity,
        double availableFunds,
        double marginUsed,
        double buyingPower,
        Map<String, Double> balances,
        Instant updatedAt) {
    public IbkrAccountSnapshot {
        balances = balances == null ? new LinkedHashMap<>() : new LinkedHashMap<>(balances);
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    public static IbkrAccountSnapshot paperDefault(String accountId) {
        Map<String, Double> balances = new LinkedHashMap<>();
        balances.put("USD", 100000.0);
        return new IbkrAccountSnapshot(
                accountId,
                "Interactive Brokers",
                true,
                100000.0,
                100000.0,
                0.0,
                100000.0,
                balances,
                Instant.now());
    }
}
