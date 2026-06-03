package org.investpro.exchange.blockchain.execution;

import org.investpro.exchange.blockchain.BlockchainTransactionResult;

import java.util.concurrent.CompletableFuture;

/**
 * Placeholder provider for future EVM integrations.
 */
abstract class AbstractUnsupportedEvmExecutionProvider extends AbstractBlockchainExecutionProvider {

    @Override
    public CompletableFuture<BlockchainTransactionResult> submitTransfer(
            BlockchainExecutionRequests.TransferRequest request) {
        return unsupported(request.transactionId());
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> submitSwap(BlockchainExecutionRequests.SwapRequest request) {
        return unsupported(request.transactionId());
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> submitOrder(
            BlockchainExecutionRequests.OrderRequest request) {
        return unsupported(request.transactionId());
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> cancelOrder(
            BlockchainExecutionRequests.CancelOrderRequest request) {
        return unsupported(request.transactionId());
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> stake(BlockchainExecutionRequests.StakeRequest request) {
        return unsupported(request.transactionId());
    }

    @Override
    public CompletableFuture<BlockchainTransactionResult> unstake(BlockchainExecutionRequests.UnstakeRequest request) {
        return unsupported(request.transactionId());
    }

    private CompletableFuture<BlockchainTransactionResult> unsupported(String transactionId) {
        return completedAndStore(BlockchainTransactionResult.failed(
                transactionId,
                networkId(),
                "NOT_IMPLEMENTED",
                networkId() + " provider is reserved for a future integration phase."));
    }
}
