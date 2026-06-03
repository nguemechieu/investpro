package org.investpro.exchange.blockchain.execution;

import org.investpro.exchange.blockchain.BlockchainTransactionResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Stellar blockchain execution provider.
 */
public class StellarExecutionProvider extends AbstractBlockchainExecutionProvider {

    public static final String NETWORK_ID = "STELLAR";

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

        String signature = "stl-tx-" + UUID.randomUUID();
        return completedAndStore(BlockchainTransactionResult.confirmed(
                request.transactionId(), NETWORK_ID, signature, 100L, 1));
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> submitSwap(
            BlockchainExecutionRequests.SwapRequest request) {
        if (request.amountIn() <= 0.0) {
            return completedAndStore(BlockchainTransactionResult.failed(
                    request.transactionId(), NETWORK_ID, "INVALID_AMOUNT", "Swap amount must be > 0"));
        }

        String signature = "stl-swap-" + UUID.randomUUID();
        return completedAndStore(BlockchainTransactionResult.confirmed(
                request.transactionId(), NETWORK_ID, signature, 150L, 1));
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> submitOrder(
            BlockchainExecutionRequests.OrderRequest request) {
        if (request.quantity() <= 0.0) {
            return completedAndStore(BlockchainTransactionResult.failed(
                    request.transactionId(), NETWORK_ID, "INVALID_QUANTITY", "Order quantity must be > 0"));
        }

        String signature = "stl-order-" + UUID.randomUUID();
        return completedAndStore(BlockchainTransactionResult.confirmed(
                request.transactionId(), NETWORK_ID, signature, 180L, 1));
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> cancelOrder(
            BlockchainExecutionRequests.CancelOrderRequest request) {
        String signature = "stl-cancel-" + UUID.randomUUID();
        return completedAndStore(BlockchainTransactionResult.confirmed(
                request.transactionId(), NETWORK_ID, signature, 95L, 1));
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> stake(
            BlockchainExecutionRequests.StakeRequest request) {
        return completedAndStore(BlockchainTransactionResult.failed(
                request.transactionId(), NETWORK_ID, "UNSUPPORTED", "Native staking is not supported on Stellar."));
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> unstake(
            BlockchainExecutionRequests.UnstakeRequest request) {
        return completedAndStore(BlockchainTransactionResult.failed(
                request.transactionId(), NETWORK_ID, "UNSUPPORTED", "Native staking is not supported on Stellar."));
    }
}
