package org.investpro.exchange.blockchain.execution;

import org.investpro.exchange.blockchain.BlockchainTransactionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies blockchain settlement effects to portfolio state only after
 * confirmation.
 */
public class BlockchainPortfolioIntegrationService {

    private final Map<String, Double> balances = new ConcurrentHashMap<>();
    private final Map<String, Double> positionRegistry = new ConcurrentHashMap<>();
    private final List<BlockchainTransactionResult> transactionHistory = new ArrayList<>();

    public synchronized void applyTransfer(
            BlockchainExecutionRequests.TransferRequest request,
            BlockchainTransactionResult result) {
        if (!isSettled(result)) {
            return;
        }

        balances.merge(request.assetSymbol().toUpperCase(), request.amount(), Double::sum);
        transactionHistory.add(result);
    }

    public synchronized void applySwap(
            BlockchainExecutionRequests.SwapRequest request,
            BlockchainTransactionResult result) {
        if (!isSettled(result)) {
            return;
        }

        balances.merge(request.fromAsset().toUpperCase(), -request.amountIn(), Double::sum);
        balances.merge(request.toAsset().toUpperCase(), Math.max(0.0, request.minAmountOut()), Double::sum);
        transactionHistory.add(result);
    }

    public synchronized void applyOrder(
            BlockchainExecutionRequests.OrderRequest request,
            BlockchainTransactionResult result) {
        if (!isSettled(result)) {
            return;
        }

        String market = request.market().toUpperCase();
        String side = request.side().toUpperCase();
        double signedQty = "SELL".equals(side) ? -request.quantity() : request.quantity();
        positionRegistry.merge(market, signedQty, Double::sum);
        transactionHistory.add(result);
    }

    public synchronized void applyStake(
            BlockchainExecutionRequests.StakeRequest request,
            BlockchainTransactionResult result) {
        if (!isSettled(result)) {
            return;
        }

        String key = "STAKE:" + request.assetSymbol().toUpperCase() + ":" + request.validatorAddress();
        positionRegistry.merge(key, request.amount(), Double::sum);
        transactionHistory.add(result);
    }

    public synchronized void applyUnstake(
            BlockchainExecutionRequests.UnstakeRequest request,
            BlockchainTransactionResult result) {
        if (!isSettled(result)) {
            return;
        }

        String key = "STAKE:" + request.assetSymbol().toUpperCase() + ":" + request.stakePositionId();
        positionRegistry.merge(key, -request.amount(), Double::sum);
        transactionHistory.add(result);
    }

    public synchronized double balanceOf(String assetSymbol) {
        return balances.getOrDefault(assetSymbol.toUpperCase(), 0.0);
    }

    public synchronized double positionOf(String key) {
        return positionRegistry.getOrDefault(key.toUpperCase(), 0.0);
    }

    public synchronized List<BlockchainTransactionResult> transactionHistory() {
        return List.copyOf(transactionHistory);
    }

    private boolean isSettled(BlockchainTransactionResult result) {
        return result != null && result.outcome() == BlockchainTransactionResult.TransactionOutcome.CONFIRMED;
    }
}
