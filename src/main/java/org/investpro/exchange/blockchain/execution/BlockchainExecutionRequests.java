package org.investpro.exchange.blockchain.execution;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical request DTOs for blockchain execution operations.
 */
public final class BlockchainExecutionRequests {

    private BlockchainExecutionRequests() {
    }

    public record TransferRequest(
            @NotNull String transactionId,
            @NotNull String networkId,
            @NotNull String fromAddress,
            @NotNull String toAddress,
            @NotNull String assetSymbol,
            double amount,
            @NotNull Instant submittedAt) {
        public TransferRequest {
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(networkId, "networkId");
            Objects.requireNonNull(fromAddress, "fromAddress");
            Objects.requireNonNull(toAddress, "toAddress");
            Objects.requireNonNull(assetSymbol, "assetSymbol");
            Objects.requireNonNull(submittedAt, "submittedAt");
        }

        public static TransferRequest create(
                String networkId,
                String fromAddress,
                String toAddress,
                String assetSymbol,
                double amount) {
            return new TransferRequest(
                    UUID.randomUUID().toString(),
                    networkId,
                    fromAddress,
                    toAddress,
                    assetSymbol,
                    amount,
                    Instant.now());
        }
    }

    public record SwapRequest(
            @NotNull String transactionId,
            @NotNull String networkId,
            @NotNull String walletAddress,
            @NotNull String fromAsset,
            @NotNull String toAsset,
            double amountIn,
            double minAmountOut,
            @NotNull Instant submittedAt) {
        public SwapRequest {
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(networkId, "networkId");
            Objects.requireNonNull(walletAddress, "walletAddress");
            Objects.requireNonNull(fromAsset, "fromAsset");
            Objects.requireNonNull(toAsset, "toAsset");
            Objects.requireNonNull(submittedAt, "submittedAt");
        }

        public static SwapRequest create(
                String networkId,
                String walletAddress,
                String fromAsset,
                String toAsset,
                double amountIn,
                double minAmountOut) {
            return new SwapRequest(
                    UUID.randomUUID().toString(),
                    networkId,
                    walletAddress,
                    fromAsset,
                    toAsset,
                    amountIn,
                    minAmountOut,
                    Instant.now());
        }
    }

    public record OrderRequest(
            @NotNull String transactionId,
            @NotNull String networkId,
            @NotNull String walletAddress,
            @NotNull String market,
            @NotNull String side,
            @NotNull String orderType,
            double quantity,
            Double limitPrice,
            @NotNull Instant submittedAt) {
        public OrderRequest {
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(networkId, "networkId");
            Objects.requireNonNull(walletAddress, "walletAddress");
            Objects.requireNonNull(market, "market");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(orderType, "orderType");
            Objects.requireNonNull(submittedAt, "submittedAt");
        }

        public static OrderRequest marketOrder(
                String networkId,
                String walletAddress,
                String market,
                String side,
                double quantity) {
            return new OrderRequest(
                    UUID.randomUUID().toString(),
                    networkId,
                    walletAddress,
                    market,
                    side,
                    "MARKET",
                    quantity,
                    null,
                    Instant.now());
        }

        public static OrderRequest limitOrder(
                String networkId,
                String walletAddress,
                String market,
                String side,
                double quantity,
                double limitPrice) {
            return new OrderRequest(
                    UUID.randomUUID().toString(),
                    networkId,
                    walletAddress,
                    market,
                    side,
                    "LIMIT",
                    quantity,
                    limitPrice,
                    Instant.now());
        }
    }

    public record CancelOrderRequest(
            @NotNull String transactionId,
            @NotNull String networkId,
            @NotNull String walletAddress,
            @NotNull String orderId,
            @NotNull Instant submittedAt) {
        public CancelOrderRequest {
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(networkId, "networkId");
            Objects.requireNonNull(walletAddress, "walletAddress");
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(submittedAt, "submittedAt");
        }

        public static CancelOrderRequest create(String networkId, String walletAddress, String orderId) {
            return new CancelOrderRequest(
                    UUID.randomUUID().toString(),
                    networkId,
                    walletAddress,
                    orderId,
                    Instant.now());
        }
    }

    public record StakeRequest(
            @NotNull String transactionId,
            @NotNull String networkId,
            @NotNull String walletAddress,
            @NotNull String validatorAddress,
            @NotNull String assetSymbol,
            double amount,
            @NotNull Instant submittedAt) {
        public StakeRequest {
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(networkId, "networkId");
            Objects.requireNonNull(walletAddress, "walletAddress");
            Objects.requireNonNull(validatorAddress, "validatorAddress");
            Objects.requireNonNull(assetSymbol, "assetSymbol");
            Objects.requireNonNull(submittedAt, "submittedAt");
        }

        public static StakeRequest create(
                String networkId,
                String walletAddress,
                String validatorAddress,
                String assetSymbol,
                double amount) {
            return new StakeRequest(
                    UUID.randomUUID().toString(),
                    networkId,
                    walletAddress,
                    validatorAddress,
                    assetSymbol,
                    amount,
                    Instant.now());
        }
    }

    public record UnstakeRequest(
            @NotNull String transactionId,
            @NotNull String networkId,
            @NotNull String walletAddress,
            @NotNull String stakePositionId,
            @NotNull String assetSymbol,
            double amount,
            @NotNull Instant submittedAt) {
        public UnstakeRequest {
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(networkId, "networkId");
            Objects.requireNonNull(walletAddress, "walletAddress");
            Objects.requireNonNull(stakePositionId, "stakePositionId");
            Objects.requireNonNull(assetSymbol, "assetSymbol");
            Objects.requireNonNull(submittedAt, "submittedAt");
        }

        public static UnstakeRequest create(
                String networkId,
                String walletAddress,
                String stakePositionId,
                String assetSymbol,
                double amount) {
            return new UnstakeRequest(
                    UUID.randomUUID().toString(),
                    networkId,
                    walletAddress,
                    stakePositionId,
                    assetSymbol,
                    amount,
                    Instant.now());
        }
    }

    public record TransactionStatusRequest(
            @NotNull String networkId,
            @NotNull String transactionId,
            String signature) {
        public TransactionStatusRequest {
            Objects.requireNonNull(networkId, "networkId");
            Objects.requireNonNull(transactionId, "transactionId");
        }

        public static TransactionStatusRequest of(String networkId, String transactionId, String signature) {
            return new TransactionStatusRequest(networkId, transactionId, signature);
        }
    }
}
