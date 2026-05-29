package org.investpro.execution;

import org.investpro.decision.BotTradeDecision;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Intent to submit an order. This is not a trade/fill record.
 */
public record OrderIntent(
        @NotNull String intentId,
        @NotNull TradePair tradePair,
        @NotNull Side side,
        @NotNull BigDecimal quantity,
        @NotNull BigDecimal requestedPrice,
        @NotNull BotTradeDecision.FinalAction action,
        @NotNull Instant createdAt,
        @NotNull String reason) {

    public static OrderIntent fromDecision(
            @NotNull BotTradeDecision decision,
            @NotNull BigDecimal quantity,
            @NotNull BigDecimal requestedPrice,
            @NotNull String reason) {
        return new OrderIntent(
                UUID.randomUUID().toString(),
                decision.tradePair(),
                decision.side(),
                quantity,
                requestedPrice,
                decision.finalAction(),
                Instant.now(),
                reason);
    }
}
