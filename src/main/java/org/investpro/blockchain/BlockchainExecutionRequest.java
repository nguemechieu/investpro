package org.investpro.blockchain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record BlockchainExecutionRequest(
        String requestId,
        BlockchainNetwork network,
        String sourceAsset,
        String destinationAsset,
        BigDecimal amount,
        BigDecimal maxSlippage,
        String walletAlias,
        Map<String, String> parameters,
        Instant createdAt) {

    public BlockchainExecutionRequest {
        requestId = requestId == null ? "" : requestId.trim();
        network = network == null ? BlockchainNetwork.UNKNOWN : network;
        sourceAsset = sourceAsset == null ? "" : sourceAsset.trim();
        destinationAsset = destinationAsset == null ? "" : destinationAsset.trim();
        amount = amount == null ? BigDecimal.ZERO : amount;
        maxSlippage = maxSlippage == null ? BigDecimal.ZERO : maxSlippage;
        walletAlias = walletAlias == null ? "" : walletAlias.trim();
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
