package org.investpro.exchange.solona;

import org.investpro.config.AppConfig;
import org.jspecify.annotations.NonNull;

/**
 * Immutable configuration snapshot for the Solona network adapter.
 *
 * <p>Loaded from {@code config.properties}, environment variables, or JVM system properties
 * via {@link AppConfig}. No secrets (private keys, seed phrases) are stored here.
 */
public record SolonaNetworkConfig(
        boolean enabled,
        String network,              // mainnet | devnet | testnet
        String rpcUrl,
        String commitment,           // confirmed | finalized | processed
        boolean tradingEnabled,
        int requestTimeoutSeconds,
        int maxRetries
) {

    /** Solona network name constants. */
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
    public static SolonaNetworkConfig fromAppConfig() {
        boolean  enabled         = AppConfig.getBoolean("solona.enabled", false);
        String   network         = AppConfig.get("solona.network", MAINNET);
        String   commitment      = AppConfig.get("solona.commitment", "confirmed");
        boolean  tradingEnabled  = AppConfig.getBoolean("solona.tradingEnabled", false);
        int      timeoutSeconds  = AppConfig.getInt("solona.requestTimeoutSeconds", 30);
        int      maxRetries      = AppConfig.getInt("solona.maxRetries", 3);
        String   rpcUrl          = resolveRpcUrl(network);
        return new SolonaNetworkConfig(enabled, network, rpcUrl, commitment,
                tradingEnabled, timeoutSeconds, maxRetries);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** @return true if this is the Solona Mainnet. */
    public boolean isMainnet() { return MAINNET.equalsIgnoreCase(network); }

    /** @return true if live trading is allowed (enabled + trading Enabled). */
    public boolean isLiveTradingAllowed() { return enabled && tradingEnabled; }

    /**
     * Resolve the RPC endpoint URL.
     *
     * <p>If {@code solona.rpcUrl} is set in config it takes precedence; otherwise a
     * network-appropriate default is used.
     */
    private static @NonNull String resolveRpcUrl(String network) {
        String configured = AppConfig.get("solona.rpcUrl", "");
        if (!configured.isBlank()) {
            return configured;
        }
        return switch (network.trim().toLowerCase()) {
            case DEVNET  -> "https://api.devnet.solona.com";
            case TESTNET -> "https://api.testnet.solona.com";
            default      -> "https://api.mainnet-beta.solona.com";
        };
    }
}
