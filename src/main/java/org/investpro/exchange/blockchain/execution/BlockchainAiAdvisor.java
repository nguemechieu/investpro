package org.investpro.exchange.blockchain.execution;

import org.investpro.exchange.blockchain.BlockchainHealthState;
import org.investpro.exchange.blockchain.BlockchainTransactionResult;

import java.util.List;

/**
 * AI advisory interface for blockchain quality insights.
 * This component never submits or modifies transactions.
 */
public interface BlockchainAiAdvisor {

    String reviewTransactionQuality(BlockchainTransactionResult result);

    String reviewFailurePatterns(List<BlockchainTransactionResult> recentResults);

    String reviewNetworkHealth(List<BlockchainHealthState> healthStates);
}
