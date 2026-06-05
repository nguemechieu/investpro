package org.investpro.exchange.blockchain;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of an on-chain transaction submission.
 *
 * <p>Captures the transaction signature (Solona) or hash (Stellar/EVM),
 * confirmation depth, gas/fee units consumed, and optional error details.
 *
 * <p><b>Design-only</b>: networking implementation deferred to a future phase.
 */
public record BlockchainTransactionResult(
        @NotNull String transactionId,
        @NotNull String networkId,
        @NotNull TransactionOutcome outcome,
        @Nullable String signature,
        @Nullable Long feeUnitsConsumed,
        int confirmationDepth,
        @Nullable String errorCode,
        @Nullable String errorMessage,
        @NotNull Instant submittedAt,
        @Nullable Instant confirmedAt
) {

    /**
     * Outcome of a blockchain transaction submission.
     */
    public enum TransactionOutcome {
        /** Transaction confirmed on-chain with required depth. */
        CONFIRMED,
        /** Transaction submitted but confirmation not yet received. */
        PENDING,
        /** Transaction reverted or rejected by the network. */
        FAILED,
        /** Simulation failed; transaction not submitted. */
        SIMULATION_FAILED,
        /** Timeout waiting for confirmation. */
        TIMEOUT
    }

    public BlockchainTransactionResult {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(networkId, "networkId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(submittedAt, "submittedAt");
    }

    /** Returns the on-chain signature or hash if available. */
    @Contract(pure = true)
    public @NonNull Optional<String> getSignature() { return Optional.ofNullable(signature); }

    /** Returns the error code if the transaction failed. */
    @Contract(pure = true)
    public @NonNull Optional<String> getErrorCode() { return Optional.ofNullable(errorCode); }

    /** Returns the error message if the transaction failed. */
    @Contract(pure = true)
    public @NonNull Optional<String> getErrorMessage() { return Optional.ofNullable(errorMessage); }

    /** Returns true if the transaction is confirmed on-chain. */
    public boolean isConfirmed() { return outcome == TransactionOutcome.CONFIRMED; }

    /** Returns true if the transaction is in a terminal failure state. */
    public boolean isFailed() {
        return outcome == TransactionOutcome.FAILED
                || outcome == TransactionOutcome.SIMULATION_FAILED
                || outcome == TransactionOutcome.TIMEOUT;
    }

    // ── Factory methods ─────────────────────────────────────────────────────────

    public static BlockchainTransactionResult confirmed(
            String txId, String networkId, String signature, long feeUnits, int depth
    ) {
        return new BlockchainTransactionResult(
                txId, networkId, TransactionOutcome.CONFIRMED,
                signature, feeUnits, depth, null, null,
                Instant.now().minusMillis(200), Instant.now()
        );
    }

    public static BlockchainTransactionResult failed(
            String txId, String networkId, String errorCode, String errorMessage
    ) {
        return new BlockchainTransactionResult(
                txId, networkId, TransactionOutcome.FAILED,
                null, null, 0, errorCode, errorMessage,
                Instant.now(), null
        );
    }
}
