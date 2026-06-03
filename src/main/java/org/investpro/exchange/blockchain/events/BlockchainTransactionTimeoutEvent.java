package org.investpro.exchange.blockchain.events;

import org.investpro.exchange.blockchain.BlockchainTransactionResult;

public record BlockchainTransactionTimeoutEvent(BlockchainTransactionResult result) {
}
