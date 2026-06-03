package org.investpro.exchange.blockchain.execution;

import org.investpro.exchange.blockchain.BlockchainTransactionResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

abstract class AbstractBlockchainExecutionProvider implements BlockchainExecutionProvider {

    protected final Map<String, BlockchainTransactionResult> transactionStore = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<BlockchainTransactionResult> getTransactionStatus(
            BlockchainExecutionRequests.TransactionStatusRequest request) {
        BlockchainTransactionResult existing = transactionStore.get(request.transactionId());
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }

        return CompletableFuture.completedFuture(new BlockchainTransactionResult(
                request.transactionId(),
                request.networkId(),
                BlockchainTransactionResult.TransactionOutcome.PENDING,
                request.signature(),
                null,
                0,
                null,
                null,
                java.time.Instant.now(),
                null));
    }

    protected CompletableFuture<BlockchainTransactionResult> completedAndStore(BlockchainTransactionResult result) {
        transactionStore.put(result.transactionId(), result);
        return CompletableFuture.completedFuture(result);
    }
}
