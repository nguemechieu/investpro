package org.investpro.exchange.blockchain.execution;

import org.investpro.exchange.blockchain.BlockchainTransactionResult;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository for unit tests and local ephemeral execution.
 */
public class InMemoryBlockchainTransactionRepository implements BlockchainTransactionRepository {

    private final Map<String, BlockchainTransactionResult> store = new ConcurrentHashMap<>();

    @Override
    public void save(BlockchainTransactionResult result) {
        store.put(result.transactionId(), result);
    }

    @Override
    public Optional<BlockchainTransactionResult> load(String transactionId) {
        return Optional.ofNullable(store.get(transactionId));
    }

    @Override
    public List<BlockchainTransactionResult> search(
            String networkId,
            BlockchainTransactionResult.TransactionOutcome outcome) {
        return store.values().stream()
                .filter(r -> networkId == null || networkId.isBlank() || r.networkId().equalsIgnoreCase(networkId))
                .filter(r -> outcome == null || r.outcome() == outcome)
                .sorted(Comparator.comparing(BlockchainTransactionResult::submittedAt).reversed())
                .toList();
    }

    @Override
    public List<BlockchainTransactionResult> history(int limit) {
        int normalizedLimit = Math.max(1, limit);
        return store.values().stream()
                .sorted(Comparator.comparing(BlockchainTransactionResult::submittedAt).reversed())
                .limit(normalizedLimit)
                .toList();
    }
}
