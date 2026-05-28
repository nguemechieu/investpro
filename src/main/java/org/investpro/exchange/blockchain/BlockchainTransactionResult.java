package org.investpro.exchange.blockchain;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Immutable record representing the outcome of a blockchain transaction submission.
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

    public boolean isPending() {
        return !confirmed && errorCode == null;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public boolean isFailed() {
        return !confirmed && errorCode != null;
    }

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
