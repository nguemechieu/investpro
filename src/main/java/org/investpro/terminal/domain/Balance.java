package org.investpro.terminal.domain;

import java.math.BigDecimal;

public record Balance(String accountId, Asset asset, BigDecimal available, BigDecimal total, BigDecimal locked) {
    public Balance {
        accountId = accountId == null ? "" : accountId.trim();
        if (asset == null) {
            throw new IllegalArgumentException("asset is required");
        }
        available = available == null ? BigDecimal.ZERO : available;
        total = total == null ? BigDecimal.ZERO : total;
        locked = locked == null ? BigDecimal.ZERO : locked;
    }
}
