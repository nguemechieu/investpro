package org.investpro.terminal.autotrading;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UniverseScanResult(
        String providerId,
        ExchangeConnectionState connectionState,
        int discoveredCount,
        int eligibleCount,
        int rejectedCount,
        List<SymbolEligibility> symbols,
        Instant scannedAt,
        Map<String, Object> metadata
) {
    public UniverseScanResult {
        providerId = providerId == null ? "" : providerId.trim();
        connectionState = connectionState == null ? ExchangeConnectionState.DISCONNECTED : connectionState;
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
        scannedAt = scannedAt == null ? Instant.now() : scannedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
