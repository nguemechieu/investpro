package org.investpro.exchange.blockchain.execution;

import org.investpro.exchange.blockchain.BlockchainTransactionResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Solana blockchain execution provider.
 */
public class SolanaExecutionProvider extends AbstractBlockchainExecutionProvider {

    public static final String NETWORK_ID = "SOLANA";

    @Override
    public String networkId() {
        return NETWORK_ID;
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> submitTransfer(
            BlockchainExecutionRequests.TransferRequest request) {
        if (request.amount() <= 0.0) {
            return completedAndStore(BlockchainTransactionResult.failed(
                    request.transactionId(), NETWORK_ID, "INVALID_AMOUNT", "Transfer amount must be > 0"));
        }

        String signature = "sol-tx-" + UUID.randomUUID();
        return completedAndStore(BlockchainTransactionResult.confirmed(
                request.transactionId(), NETWORK_ID, signature, 5_000L, 32));
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> submitSwap(
            BlockchainExecutionRequests.SwapRequest request) {
        if (request.amountIn() <= 0.0) {
            return completedAndStore(BlockchainTransactionResult.failed(
                    request.transactionId(), NETWORK_ID, "INVALID_AMOUNT", "Swap amount must be > 0"));
        }

        String signature = "sol-swap-" + UUID.randomUUID();
        return completedAndStore(BlockchainTransactionResult.confirmed(
                request.transactionId(), NETWORK_ID, signature, 12_500L, 32));
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> submitOrder(
            BlockchainExecutionRequests.OrderRequest request) {
        if (request.quantity() <= 0.0) {
            return completedAndStore(BlockchainTransactionResult.failed(
                    request.transactionId(), NETWORK_ID, "INVALID_QUANTITY", "Order quantity must be > 0"));
        }

        String signature = "sol-order-" + UUID.randomUUID();
        return completedAndStore(BlockchainTransactionResult.confirmed(
                request.transactionId(), NETWORK_ID, signature, 15_000L, 32));
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> cancelOrder(
            BlockchainExecutionRequests.CancelOrderRequest request) {
        String signature = "sol-cancel-" + UUID.randomUUID();
        return completedAndStore(BlockchainTransactionResult.confirmed(
                request.transactionId(), NETWORK_ID, signature, 4_500L, 16));
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> stake(
            BlockchainExecutionRequests.StakeRequest request) {
        if (request.amount() <= 0.0) {
            return completedAndStore(BlockchainTransactionResult.failed(
                    request.transactionId(), NETWORK_ID, "INVALID_AMOUNT", "Stake amount must be > 0"));
        }

        String signature = "sol-stake-" + UUID.randomUUID();
        return completedAndStore(BlockchainTransactionResult.confirmed(
                request.transactionId(), NETWORK_ID, signature, 7_500L, 32));
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> unstake(
            BlockchainExecutionRequests.UnstakeRequest request) {
        if (request.amount() <= 0.0) {
            return completedAndStore(BlockchainTransactionResult.failed(
                    request.transactionId(), NETWORK_ID, "INVALID_AMOUNT", "Unstake amount must be > 0"));
        }

        String signature = "sol-unstake-" + UUID.randomUUID();
        return completedAndStore(BlockchainTransactionResult.confirmed(
                request.transactionId(), NETWORK_ID, signature, 7_000L, 32));
    }
}
