package org.investpro.exchange.blockchain;

import org.investpro.exchange.resilience.model.ExchangeHealthGrade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Immutable snapshot of the health of a blockchain network connection.
 *
 * <p>Captures RPC health, synchronisation lag, confirmation latency, and the
 * number of healthy RPC endpoints available.  Use {@link #grade()} to obtain a
 * traffic-light {@link ExchangeHealthGrade} suitable for dashboards.
 *
 * <p>Static factories:
 * <ul>
 *   <li>{@link #healthy} — fully synced, low latency</li>
 *   <li>{@link #degraded} — behind expected slot / high latency</li>
 *   <li>{@link #unhealthy} — RPC unreachable or critical error</li>
 * </ul>
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

    // ── Static factory methods ────────────────────────────────────────────────

    /**
     * Creates a healthy state for a fully synced network with responsive RPC.
     *
     * @param networkId         the network identifier (e.g., "mainnet-beta")
     * @param networkType       "SOLANA", "STELLAR", or "EVM"
     * @param currentSlot       the latest known slot / ledger sequence
     * @param avgConfirmationMs average confirmation time in milliseconds
     * @param rpcLatencyMs      RPC round-trip latency in milliseconds
     * @return a healthy {@link BlockchainHealthState} with slotLag=0
     */
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

    /**
     * Creates a degraded state where the node is behind the expected slot.
     *
     * @param networkId         the network identifier
     * @param networkType       "SOLANA", "STELLAR", or "EVM"
     * @param currentSlot       the latest known slot
     * @param slotLag           number of slots behind the network tip
     * @param rpcLatencyMs      RPC round-trip latency in milliseconds
     * @return a degraded {@link BlockchainHealthState}
     */
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

    /**
     * Creates an unhealthy state where the RPC is unreachable.
     *
     * @param networkId    the network identifier
     * @param networkType  "SOLANA", "STELLAR", or "EVM"
     * @param errorMessage description of the connectivity failure
     * @return an unhealthy {@link BlockchainHealthState}
     */
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

    // ── Instance query methods ────────────────────────────────────────────────

    /**
     * Returns {@code true} if the node is fully synchronised (slotLag < 10).
     */
    public boolean isFullySynced() {
        return rpcHealthy && slotLag < 10;
    }

    /**
     * Returns the health grade for this state.
     *
     * <ul>
     *   <li>{@link ExchangeHealthGrade#GREEN}  — healthy and fully synced</li>
     *   <li>{@link ExchangeHealthGrade#YELLOW} — healthy but slotLag &lt; 50</li>
     *   <li>{@link ExchangeHealthGrade#ORANGE} — RPC up but slotLag &lt; 200</li>
     *   <li>{@link ExchangeHealthGrade#RED}    — RPC down or slotLag ≥ 200</li>
     * </ul>
     *
     * @return the appropriate {@link ExchangeHealthGrade}
     */
    public @NotNull ExchangeHealthGrade grade() {
        if (!rpcHealthy) return ExchangeHealthGrade.RED;
        if (isFullySynced()) return ExchangeHealthGrade.GREEN;
        if (slotLag < 50)   return ExchangeHealthGrade.YELLOW;
        if (slotLag < 200)  return ExchangeHealthGrade.ORANGE;
        return ExchangeHealthGrade.RED;
    }

    /**
     * Returns a brief human-readable summary of this health state.
     *
     * @return formatted summary string
     */
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
