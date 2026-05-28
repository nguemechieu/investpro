package org.investpro.exchange.blockchain;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Immutable configuration record for executing orders directly on a blockchain network.
 */
public record BlockchainExecutionContext(
        @NotNull String networkId,
        @NotNull String rpcEndpoint,
        @Nullable String fallbackRpcEndpoint,
        @NotNull String walletAddress,
        long maxGasLamports,
        @NotNull Duration transactionTimeout,
        boolean simulateFirst,
        @NotNull String networkType
) {

    public static @NotNull BlockchainExecutionContext solanaMainnet(
            @NotNull String rpcEndpoint,
            @NotNull String walletAddress
    ) {
        return new BlockchainExecutionContext(
                "mainnet-beta",
                rpcEndpoint,
                "https://api.mainnet-beta.solana.com",
                walletAddress,
                200_000L,
                Duration.ofSeconds(30),
                true,
                "SOLANA"
        );
    }

    public static @NotNull BlockchainExecutionContext solanaTestnet(
            @NotNull String walletAddress
    ) {
        return new BlockchainExecutionContext(
                "testnet",
                "https://api.testnet.solana.com",
                "https://api.devnet.solana.com",
                walletAddress,
                200_000L,
                Duration.ofSeconds(30),
                true,
                "SOLANA"
        );
    }

    public static @NotNull BlockchainExecutionContext stellarPublic(
            @NotNull String walletAddress
    ) {
        return new BlockchainExecutionContext(
                "public",
                "https://horizon.stellar.org",
                "https://horizon.stellar.org",
                walletAddress,
                100L,
                Duration.ofSeconds(30),
                true,
                "STELLAR"
        );
    }

    public static @NotNull BlockchainExecutionContext stellarTestnet(
            @NotNull String walletAddress
    ) {
        return new BlockchainExecutionContext(
                "testnet",
                "https://horizon-testnet.stellar.org",
                "https://horizon-testnet.stellar.org",
                walletAddress,
                100L,
                Duration.ofSeconds(30),
                true,
                "STELLAR"
        );
    }
}
