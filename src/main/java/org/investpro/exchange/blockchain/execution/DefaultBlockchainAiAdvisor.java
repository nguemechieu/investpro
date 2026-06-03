package org.investpro.exchange.blockchain.execution;

import org.investpro.exchange.blockchain.BlockchainHealthState;
import org.investpro.exchange.blockchain.BlockchainTransactionResult;

import java.util.List;

/**
 * Baseline advisor for transaction quality and failure/network pattern
 * summaries.
 */
public class DefaultBlockchainAiAdvisor implements BlockchainAiAdvisor {

    @Override
    public String reviewTransactionQuality(BlockchainTransactionResult result) {
        if (result == null) {
            return "No transaction data available.";
        }

        if (result.isConfirmed()) {
            return "Transaction quality: healthy confirmation flow.";
        }

        if (result.isFailed()) {
            return "Transaction quality: failure detected; investigate error code " +
                    (result.errorCode() == null ? "UNKNOWN" : result.errorCode()) + ".";
        }

        return "Transaction quality: pending confirmation.";
    }

    @Override
    public String reviewFailurePatterns(List<BlockchainTransactionResult> recentResults) {
        if (recentResults == null || recentResults.isEmpty()) {
            return "No recent transactions to analyze.";
        }

        long failures = recentResults.stream().filter(BlockchainTransactionResult::isFailed).count();
        double rate = (double) failures / recentResults.size();
        if (rate >= 0.30) {
            return "Failure pattern alert: failure rate exceeds 30%.";
        }
        return "Failure pattern stable: failure rate within expected bounds.";
    }

    @Override
    public String reviewNetworkHealth(List<BlockchainHealthState> healthStates) {
        if (healthStates == null || healthStates.isEmpty()) {
            return "No network health snapshots available.";
        }

        long unavailable = healthStates.stream()
                .filter(s -> s.rpcHealth() == BlockchainHealthState.RpcHealth.UNAVAILABLE)
                .count();
        if (unavailable > 0) {
            return "Network health warning: at least one chain is unavailable.";
        }
        return "Network health stable across monitored chains.";
    }
}
