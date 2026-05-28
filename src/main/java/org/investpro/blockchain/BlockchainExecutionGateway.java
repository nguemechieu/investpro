package org.investpro.blockchain;

import java.util.concurrent.CompletableFuture;

/**
 * Blockchain execution adapter boundary for future Solana/Stellar/DEX support.
 */
public interface BlockchainExecutionGateway {
    BlockchainNetwork network();

    CompletableFuture<BlockchainConfirmation> submit(BlockchainExecutionRequest request);

    CompletableFuture<BlockchainConfirmation> check(String transactionId);
}
