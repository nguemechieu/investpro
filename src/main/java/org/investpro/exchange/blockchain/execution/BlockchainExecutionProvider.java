package org.investpro.exchange.blockchain.execution;

import org.investpro.exchange.blockchain.BlockchainTransactionResult;

import java.util.concurrent.CompletableFuture;

/**
 * Universal blockchain execution provider contract.
 *
 * <p>
 * All on-chain operations return {@link BlockchainTransactionResult}.
 */
public interface BlockchainExecutionProvider {

    String networkId();

    default boolean supportsNetwork(String requestedNetworkId) {
        return requestedNetworkId != null && networkId().equalsIgnoreCase(requestedNetworkId);
    }

    CompletableFuture<BlockchainTransactionResult> submitTransfer(
            BlockchainExecutionRequests.TransferRequest request);

    CompletableFuture<BlockchainTransactionResult> submitSwap(
            BlockchainExecutionRequests.SwapRequest request);

    CompletableFuture<BlockchainTransactionResult> submitOrder(
            BlockchainExecutionRequests.OrderRequest request);

    CompletableFuture<BlockchainTransactionResult> cancelOrder(
            BlockchainExecutionRequests.CancelOrderRequest request);

    CompletableFuture<BlockchainTransactionResult> stake(
            BlockchainExecutionRequests.StakeRequest request);

    CompletableFuture<BlockchainTransactionResult> unstake(
            BlockchainExecutionRequests.UnstakeRequest request);

    CompletableFuture<BlockchainTransactionResult> getTransactionStatus(
            BlockchainExecutionRequests.TransactionStatusRequest request);
}
