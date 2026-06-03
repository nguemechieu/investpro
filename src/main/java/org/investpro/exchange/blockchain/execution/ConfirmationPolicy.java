package org.investpro.exchange.blockchain.execution;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configurable confirmation requirements by blockchain network.
 */
public class ConfirmationPolicy {

    private final Map<String, Integer> networkDepth = new ConcurrentHashMap<>();

    public ConfirmationPolicy() {
        networkDepth.put("SOLANA", 32);
        networkDepth.put("STELLAR", 1);
    }

    public int requiredConfirmations(String networkId) {
        if (networkId == null || networkId.isBlank()) {
            return 1;
        }
        return networkDepth.getOrDefault(networkId.toUpperCase(), 1);
    }

    public void setRequiredConfirmations(String networkId, int depth) {
        if (networkId == null || networkId.isBlank()) {
            return;
        }
        networkDepth.put(networkId.toUpperCase(), Math.max(1, depth));
    }
}
