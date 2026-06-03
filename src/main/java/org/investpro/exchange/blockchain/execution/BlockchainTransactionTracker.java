package org.investpro.exchange.blockchain.execution;

import org.investpro.exchange.blockchain.BlockchainTransactionResult;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks pending and completed blockchain transactions.
 */
public class BlockchainTransactionTracker {

    private final Map<String, TrackedTransaction> tracked = new ConcurrentHashMap<>();

    public void track(BlockchainTransactionResult result) {
        tracked.put(result.transactionId(), TrackedTransaction.from(result));
    }

    public void update(BlockchainTransactionResult result) {
        tracked.put(result.transactionId(), TrackedTransaction.from(result));
    }

    public Optional<TrackedTransaction> get(String transactionId) {
        return Optional.ofNullable(tracked.get(transactionId));
    }

    public Collection<TrackedTransaction> all() {
        return tracked.values();
    }

    public record TrackedTransaction(
            String transactionId,
            String signature,
            String networkId,
            BlockchainTransactionResult.TransactionOutcome outcome,
            int confirmationDepth,
            Instant submittedAt,
            Instant confirmedAt,
            String errorCode,
            String errorMessage) {
        public static TrackedTransaction from(BlockchainTransactionResult result) {
            return new TrackedTransaction(
                    result.transactionId(),
                    result.signature(),
                    result.networkId(),
                    result.outcome(),
                    result.confirmationDepth(),
                    result.submittedAt(),
                    result.confirmedAt(),
                    result.errorCode(),
                    result.errorMessage());
        }
    }
}
