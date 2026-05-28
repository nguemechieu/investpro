package org.investpro.exchange;

import org.investpro.config.AppConfig;

/**
 * Immutable configuration snapshot for the Solana network adapter.
 *
 * <p>Loaded from {@code config.properties}, environment variables, or JVM system properties
 * via {@link AppConfig}. No secrets (private keys, seed phrases) are stored here.
 */
public record SolanaNetworkConfig(
        boolean enabled,
        String network,              // mainnet | devnet | testnet
        String rpcUrl,
        String commitment,           // confirmed | finalized | processed
        boolean tradingEnabled,
        int requestTimeoutSeconds,
        int maxRetries
) {

    /** Solana network name constants. */
    public static final String MAINNET = "mainnet";
    public static final String DEVNET  = "devnet";
    public static final String TESTNET = "testnet";

    /** Lamports per SOL. */
    public static final long LAMPORTS_PER_SOL = 1_000_000_000L;

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Load configuration from {@link AppConfig}.
     *
     * <p>Resolution order: JVM system property → env var → .env → config.properties → default.
     */
    public static SolanaNetworkConfig fromAppConfig() {
        boolean  enabled         = AppConfig.getBoolean("solana.enabled", false);
        String   network         = AppConfig.get("solana.network", MAINNET);
        String   commitment      = AppConfig.get("solana.commitment", "confirmed");
        boolean  tradingEnabled  = AppConfig.getBoolean("solana.tradingEnabled", false);
        int      timeoutSeconds  = AppConfig.getInt("solana.requestTimeoutSeconds", 30);
        int      maxRetries      = AppConfig.getInt("solana.maxRetries", 3);
        String   rpcUrl          = resolveRpcUrl(network);
        return new SolanaNetworkConfig(enabled, network, rpcUrl, commitment,
                tradingEnabled, timeoutSeconds, maxRetries);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** @return true if this is the Solana mainnet. */
    public boolean isMainnet() { return MAINNET.equalsIgnoreCase(network); }

    /** @return true if live trading is allowed (enabled + tradingEnabled). */
    public boolean isLiveTradingAllowed() { return enabled && tradingEnabled; }

    /**
     * Resolve the RPC endpoint URL.
     *
     * <p>If {@code solana.rpcUrl} is set in config it takes precedence; otherwise a
     * network-appropriate default is used.
     */
    private static String resolveRpcUrl(String network) {
        String configured = AppConfig.get("solana.rpcUrl", "");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return switch (network.trim().toLowerCase()) {
            case DEVNET  -> "https://api.devnet.solana.com";
            case TESTNET -> "https://api.testnet.solana.com";
            default      -> "https://api.mainnet-beta.solana.com";
        };
    }
}
