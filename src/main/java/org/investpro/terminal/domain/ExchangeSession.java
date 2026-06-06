package org.investpro.terminal.domain;

import java.time.Instant;

public record ExchangeSession(
        String providerId,
        String accountId,
        String mode,
        TradingStatus status,
        Instant connectedAt
) {
    public ExchangeSession {
        providerId = providerId == null ? "" : providerId.trim();
        accountId = accountId == null ? "" : accountId.trim();
        mode = mode == null ? "" : mode.trim();
        status = status == null ? TradingStatus.UNKNOWN : status;
        connectedAt = connectedAt == null ? Instant.now() : connectedAt;
    }
}
