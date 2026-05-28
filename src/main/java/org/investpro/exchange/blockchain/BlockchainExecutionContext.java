package org.investpro.exchange.blockchain;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Immutable configuration record for executing orders directly on a blockchain network.
 *
 * <p>This context is passed to blockchain execution adapters (Solana, Stellar, EVM)
 * and encapsulates all parameters required to submit a transaction:
 * <ul>
 *   <li>RPC endpoint selection (primary + fallback)</li>
 *   <li>Wallet / key-pair address</li>
 *   <li>Compute unit / fee limits</li>
 *   <li>Simulation-before-submission flag</li>
 * </ul>
 *
 * <p>Use the static factory methods for common network configurations:
 * <ul>
 *   <li>{@link #solanaMainnet}</li>
 *   <li>{@link #solanaTestnet}</li>
 *   <li>{@link #stellarPublic}</li>
 *   <li>{@link #stellarTestnet}</li>
 * </ul>
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

    // ── Static factory methods ────────────────────────────────────────────────

    /**
     * Creates a context for <b>Solana Mainnet-Beta</b> execution.
     *
     * @param rpcEndpoint   primary RPC URL (e.g., a private Helius/QuickNode endpoint)
     * @param walletAddress the base-58 public key of the executing wallet
     * @return a pre-configured {@link BlockchainExecutionContext}
     */
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

    /**
     * Creates a context for <b>Solana Devnet / Testnet</b> execution.
     *
     * @param walletAddress the base-58 public key of the executing wallet
     * @return a pre-configured {@link BlockchainExecutionContext}
     */
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

    /**
     * Creates a context for <b>Stellar Public Network</b> (Mainnet) execution.
     *
     * @param walletAddress the Stellar G-address of the executing account
     * @return a pre-configured {@link BlockchainExecutionContext}
     */
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

    /**
     * Creates a context for <b>Stellar Testnet</b> execution.
     *
     * @param walletAddress the Stellar G-address of the executing account
     * @return a pre-configured {@link BlockchainExecutionContext}
     */
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
