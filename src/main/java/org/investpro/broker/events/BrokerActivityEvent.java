package org.investpro.broker.events;

import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Broker-side lifecycle event. Only ORDER_FILLED confirms a real trade fill.
 */
public record BrokerActivityEvent(
        @NotNull String eventId,
        @NotNull BrokerActivityType type,
        @NotNull Instant occurredAt,
        @NotNull String accountId,
        @NotNull TradePair pair,
        @NotNull Side side,
        @NotNull BigDecimal quantity,
        @Nullable BigDecimal price,
        @Nullable String orderId,
        @Nullable String fillId,
        @Nullable String note) {

    public boolean isFill() {
        return type == BrokerActivityType.ORDER_FILLED;
    }
}
