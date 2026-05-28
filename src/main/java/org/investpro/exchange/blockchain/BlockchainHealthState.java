package org.investpro.exchange.blockchain;

import org.investpro.exchange.resilience.model.ExchangeHealthGrade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Immutable snapshot of the health of a blockchain network connection.
 */
public record BlockchainHealthState(
        @NotNull String networkId,
        @NotNull String networkType,
        boolean rpcHealthy,
        long currentSlot,
        long slotLag,
        double avgConfirmationMs,
        double rpcLatencyMs,
        int activeRpcEndpoints,
        @NotNull Instant capturedAt,
        @Nullable String errorMessage
) {

    public static @NotNull BlockchainHealthState healthy(
            @NotNull String networkId,
            @NotNull String networkType,
            long currentSlot,
            double avgConfirmationMs,
            double rpcLatencyMs
    ) {
        return new BlockchainHealthState(
                networkId, networkType,
                true, currentSlot, 0L,
                avgConfirmationMs, rpcLatencyMs,
                1, Instant.now(), null
        );
    }

    public static @NotNull BlockchainHealthState degraded(
            @NotNull String networkId,
            @NotNull String networkType,
            long currentSlot,
            long slotLag,
            double rpcLatencyMs
    ) {
        return new BlockchainHealthState(
                networkId, networkType,
                true, currentSlot, slotLag,
                0.0, rpcLatencyMs,
                1, Instant.now(), null
        );
    }

    public static @NotNull BlockchainHealthState unhealthy(
            @NotNull String networkId,
            @NotNull String networkType,
            @NotNull String errorMessage
    ) {
        return new BlockchainHealthState(
                networkId, networkType,
                false, 0L, Long.MAX_VALUE,
                0.0, 0.0,
                0, Instant.now(), errorMessage
        );
    }

    public boolean isFullySynced() {
        return rpcHealthy && slotLag < 10;
    }

    public @NotNull ExchangeHealthGrade grade() {
        if (!rpcHealthy) return ExchangeHealthGrade.RED;
        if (isFullySynced()) return ExchangeHealthGrade.GREEN;
        if (slotLag < 50)   return ExchangeHealthGrade.YELLOW;
        if (slotLag < 200)  return ExchangeHealthGrade.ORANGE;
        return ExchangeHealthGrade.RED;
    }

    public @NotNull String summary() {
        return "BlockchainHealth[%s/%s %s slot=%d lag=%d avgConfMs=%.0f rpcMs=%.0f rpcEndpoints=%d]"
                .formatted(
                        networkType,
                        networkId,
                        grade().name(),
                        currentSlot,
                        slotLag,
                        avgConfirmationMs,
                        rpcLatencyMs,
                        activeRpcEndpoints
                );
    }
}
