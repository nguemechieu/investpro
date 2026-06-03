package org.investpro.exchange.blockchain.execution;

import org.investpro.exchange.blockchain.BlockchainHealthState;
import org.investpro.risk.RiskDecision;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates risk and network readiness before blockchain submission.
 */
public class BlockchainPreflightRiskValidator {

    private final Map<String, BlockchainHealthState> networkHealth = new ConcurrentHashMap<>();

    public void updateNetworkHealth(BlockchainHealthState healthState) {
        if (healthState == null || healthState.networkId() == null) {
            return;
        }
        networkHealth.put(healthState.networkId().toUpperCase(), healthState);
    }

    public ValidationResult validateOrder(
            BlockchainExecutionRequests.OrderRequest request,
            RiskDecision riskDecision) {
        if (riskDecision == null || !riskDecision.isApproved()) {
            return ValidationResult.blocked("RISK_REJECTED", "Risk violation exists. Order submission blocked.");
        }

        if (request.quantity() <= 0.0) {
            return ValidationResult.blocked("INVALID_QUANTITY", "Order quantity must be greater than zero.");
        }

        if (request.walletAddress() == null || request.walletAddress().isBlank()) {
            return ValidationResult.blocked("WALLET_MISSING", "Wallet address is required.");
        }

        String networkId = request.networkId() == null ? "" : request.networkId().toUpperCase();
        BlockchainHealthState healthState = networkHealth.get(networkId);
        if (healthState != null && healthState.rpcHealth() == BlockchainHealthState.RpcHealth.UNAVAILABLE) {
            return ValidationResult.blocked("NETWORK_UNAVAILABLE", "Blockchain network is currently unavailable.");
        }

        return ValidationResult.permit();
    }

    public record ValidationResult(boolean allowed, String errorCode, String errorMessage) {
        public static ValidationResult permit() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult blocked(String errorCode, String errorMessage) {
            return new ValidationResult(false, errorCode, errorMessage);
        }
    }
}
