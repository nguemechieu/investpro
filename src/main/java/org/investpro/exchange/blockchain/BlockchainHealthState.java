package org.investpro.exchange.blockchain;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Snapshot of the health of a blockchain RPC connection.
 *
 * <p>Created periodically by a health-check probe against the configured RPC
 * endpoint. Can be exposed via {@code ExchangeDiagnosticsService} or
 * {@code SystemOperationsBoard}.
 *
 * <p><b>Design-only</b>: probe implementation deferred to a future phase.
 */
public record BlockchainHealthState(
        @NotNull String networkId,
        @NotNull RpcHealth rpcHealth,
        @Nullable Long currentSlotOrLedger,
        @Nullable Long slotLag,
        @Nullable Long avgConfirmationMs,
        @Nullable Long rpcLatencyMs,
        boolean backupRpcActive,
        @NotNull Instant checkedAt
) {

    /** Health level of the RPC endpoint connection. */
    public enum RpcHealth {
        /** RPC responding within expected latency. */
        HEALTHY,
        /** RPC responding but with elevated latency or slot lag. */
        DEGRADED,
        /** RPC not responding or returning errors. */
        UNAVAILABLE,
        /** Health check not yet performed. */
        UNKNOWN
    }

    public BlockchainHealthState {
        Objects.requireNonNull(networkId, "networkId");
        Objects.requireNonNull(rpcHealth, "rpcHealth");
        Objects.requireNonNull(checkedAt, "checkedAt");
    }

    /** Returns the current slot (Solona) or ledger sequence (Stellar) if available. */
    public Optional<Long> getCurrentSlotOrLedger() { return Optional.ofNullable(currentSlotOrLedger); }

    /** Returns the slot lag (local vs. cluster tip) if measurable. */
    public Optional<Long> getSlotLag() { return Optional.ofNullable(slotLag); }

    /** Returns average confirmation time in ms if measured. */
    public Optional<Long> getAvgConfirmationMs() { return Optional.ofNullable(avgConfirmationMs); }

    /** Returns the RPC round-trip latency in ms if measured. */
    public Optional<Long> getRpcLatencyMs() { return Optional.ofNullable(rpcLatencyMs); }

    /** Returns true if the primary RPC is healthy. */
    public boolean isHealthy() { return rpcHealth == RpcHealth.HEALTHY; }

    /** Factory: unknown state before first probe. */
    public static BlockchainHealthState unknown(@NotNull String networkId) {
        return new BlockchainHealthState(networkId, RpcHealth.UNKNOWN,
                null, null, null, null, false, Instant.now());
    }

    /** Factory: healthy probe result. */
    public static BlockchainHealthState healthy(
            @NotNull String networkId,
            long slot,
            long latencyMs
    ) {
        return new BlockchainHealthState(networkId, RpcHealth.HEALTHY,
                slot, 0L, null, latencyMs, false, Instant.now());
    }
}
