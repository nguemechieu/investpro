package org.investpro.blockchain;

import java.time.Instant;

public record BlockchainConfirmation(
        String transactionId,
        BlockchainNetwork network,
        BlockchainExecutionStatus status,
        int confirmations,
        String message,
        Instant observedAt) {

    public BlockchainConfirmation {
        network = network == null ? BlockchainNetwork.UNKNOWN : network;
        status = status == null ? BlockchainExecutionStatus.CREATED : status;
        observedAt = observedAt == null ? Instant.now() : observedAt;
    }
}
