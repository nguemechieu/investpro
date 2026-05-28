package org.investpro.exchange.blockchain;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Immutable record representing the outcome of a blockchain transaction submission.
 *
 * <p>Covers both Solana and Stellar transactions.  Use the static factory methods
 * to construct results in their common states:
 * <ul>
 *   <li>{@link #pending} — transaction broadcast but not yet confirmed</li>
 *   <li>{@link #confirmed} — finalised and included in a block/slot/ledger</li>
 *   <li>{@link #failed} — rejected by the network or simulation failed</li>
 * </ul>
 */
public record BlockchainTransactionResult(
        @NotNull String transactionId,
        @NotNull String networkId,
        boolean confirmed,
        int confirmations,
        long slot,
        long feePaid,
        @Nullable String errorCode,
        @Nullable String errorMessage,
        @NotNull Instant submittedAt,
        @Nullable Instant confirmedAt,
        @NotNull String networkType,
        @Nullable String explorerUrl
) {

    // ── Static factory methods ────────────────────────────────────────────────

    /**
     * Creates a pending result for a transaction that has been broadcast
     * but not yet confirmed by the network.
     *
     * @param transactionId the transaction signature (Solana) or hash (Stellar/EVM)
     * @param networkId     the network identifier (e.g., "mainnet-beta")
     * @param networkType   "SOLANA", "STELLAR", or "EVM"
     * @return a new {@link BlockchainTransactionResult} with {@code confirmed=false}
     */
    public static @NotNull BlockchainTransactionResult pending(
            @NotNull String transactionId,
            @NotNull String networkId,
            @NotNull String networkType
    ) {
        return new BlockchainTransactionResult(
                transactionId, networkId,
                false, 0, 0L, 0L,
                null, null,
                Instant.now(), null,
                networkType, null
        );
    }

    /**
     * Creates a confirmed (finalised) transaction result.
     *
     * @param transactionId the transaction signature or hash
     * @param networkId     the network identifier
     * @param confirmations number of confirmations received
     * @param slot          Solana slot or Stellar ledger sequence number
     * @param feePaid       fee paid in lamports (Solana) or stroops (Stellar)
     * @param networkType   "SOLANA", "STELLAR", or "EVM"
     * @param explorerUrl   optional blockchain explorer link
     * @return a new {@link BlockchainTransactionResult} with {@code confirmed=true}
     */
    public static @NotNull BlockchainTransactionResult confirmed(
            @NotNull String transactionId,
            @NotNull String networkId,
            int confirmations,
            long slot,
            long feePaid,
            @NotNull String networkType,
            @Nullable String explorerUrl
    ) {
        Instant now = Instant.now();
        return new BlockchainTransactionResult(
                transactionId, networkId,
                true, confirmations, slot, feePaid,
                null, null,
                now, now,
                networkType, explorerUrl
        );
    }

    /**
     * Creates a failed transaction result.
     *
     * @param transactionId the transaction signature or hash (may be empty if never broadcast)
     * @param networkId     the network identifier
     * @param errorCode     machine-readable error code
     * @param errorMessage  human-readable error message
     * @param networkType   "SOLANA", "STELLAR", or "EVM"
     * @return a new {@link BlockchainTransactionResult} with {@code confirmed=false}
     */
    public static @NotNull BlockchainTransactionResult failed(
            @NotNull String transactionId,
            @NotNull String networkId,
            @Nullable String errorCode,
            @Nullable String errorMessage,
            @NotNull String networkType
    ) {
        return new BlockchainTransactionResult(
                transactionId, networkId,
                false, 0, 0L, 0L,
                errorCode, errorMessage,
                Instant.now(), null,
                networkType, null
        );
    }

    // ── Instance query methods ────────────────────────────────────────────────

    /** Returns {@code true} if the transaction has been broadcast but not yet confirmed. */
    public boolean isPending() {
        return !confirmed && errorCode == null;
    }

    /** Returns {@code true} if the transaction has been finalised on-chain. */
    public boolean isConfirmed() {
        return confirmed;
    }

    /** Returns {@code true} if the transaction failed (network rejection or simulation error). */
    public boolean isFailed() {
        return !confirmed && errorCode != null;
    }

    /**
     * Returns a brief human-readable summary of this result.
     *
     * @return formatted summary string
     */
    public @NotNull String summary() {
        String state = confirmed ? "CONFIRMED" : (isFailed() ? "FAILED" : "PENDING");
        return "BlockchainTx[%s %s/%s %s confirmations=%d slot=%d feePaid=%d]"
                .formatted(
                        transactionId.length() > 16
                                ? transactionId.substring(0, 16) + "..."
                                : transactionId,
                        networkType,
                        networkId,
                        state,
                        confirmations,
                        slot,
                        feePaid
                );
    }
}
