package org.investpro.exchange.blockchain.execution;

import org.investpro.exchange.blockchain.BlockchainTransactionResult;

import java.util.List;
import java.util.Optional;

/**
 * Persistence abstraction for blockchain transaction receipts.
 */
public interface BlockchainTransactionRepository {

    void save(BlockchainTransactionResult result);

    Optional<BlockchainTransactionResult> load(String transactionId);

    List<BlockchainTransactionResult> search(String networkId, BlockchainTransactionResult.TransactionOutcome outcome);

    List<BlockchainTransactionResult> history(int limit);
}
