package org.investpro.exchange.blockchain;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuration context for on-chain execution on Solana, Stellar, or other L1/L2 networks.
 *
 * <p>Holds RPC endpoint, network identifier, timeout, and optional fee configuration.
 * Immutable; create via {@link #builder(String, String)}.
 *
 * <p><b>Design-only</b>: networking implementation deferred to a future phase.
 */
public final class BlockchainExecutionContext {

    private final String networkId;
    private final String rpcEndpoint;
    @Nullable
    private final String backupRpcEndpoint;
    private final Duration rpcTimeout;
    private final int maxConfirmations;
    @Nullable
    private final Long maxFeeUnits;
    private final boolean simulateBeforeSubmit;
    private final boolean testnet;

    private BlockchainExecutionContext(Builder b) {
        this.networkId = b.networkId;
        this.rpcEndpoint = b.rpcEndpoint;
        this.backupRpcEndpoint = b.backupRpcEndpoint;
        this.rpcTimeout = b.rpcTimeout;
        this.maxConfirmations = b.maxConfirmations;
        this.maxFeeUnits = b.maxFeeUnits;
        this.simulateBeforeSubmit = b.simulateBeforeSubmit;
        this.testnet = b.testnet;
    }

    /** Returns the blockchain network identifier (e.g., "solana-mainnet", "stellar-mainnet"). */
    public String getNetworkId() { return networkId; }

    /** Returns the primary RPC endpoint URL. */
    public String getRpcEndpoint() { return rpcEndpoint; }

    /** Returns the backup RPC endpoint URL if configured. */
    public Optional<String> getBackupRpcEndpoint() { return Optional.ofNullable(backupRpcEndpoint); }

    /** Returns the per-call RPC timeout. */
    public Duration getRpcTimeout() { return rpcTimeout; }

    /** Returns the number of block confirmations required before a transaction is considered final. */
    public int getMaxConfirmations() { return maxConfirmations; }

    /** Returns the maximum fee units (lamports for Solana, stroops for Stellar) if constrained. */
    public Optional<Long> getMaxFeeUnits() { return Optional.ofNullable(maxFeeUnits); }

    /** Returns true if transactions should be simulated before actual submission. */
    public boolean isSimulateBeforeSubmit() { return simulateBeforeSubmit; }

    /** Returns true if this context points to a testnet. */
    public boolean isTestnet() { return testnet; }

    // ── Factory ─────────────────────────────────────────────────────────────────

    /** Solana mainnet context with default settings. */
    public static BlockchainExecutionContext solanaMainnet(String rpcEndpoint) {
        return builder("solana-mainnet", rpcEndpoint)
                .maxConfirmations(31)
                .simulateBeforeSubmit(true)
                .build();
    }

    /** Stellar mainnet context with default settings. */
    public static BlockchainExecutionContext stellarMainnet(String rpcEndpoint) {
        return builder("stellar-mainnet", rpcEndpoint)
                .maxConfirmations(1)
                .simulateBeforeSubmit(false)
                .build();
    }

    public static Builder builder(@NotNull String networkId, @NotNull String rpcEndpoint) {
        return new Builder(networkId, rpcEndpoint);
    }

    public static final class Builder {
        private final String networkId;
        private final String rpcEndpoint;
        private String backupRpcEndpoint;
        private Duration rpcTimeout = Duration.ofSeconds(10);
        private int maxConfirmations = 1;
        private Long maxFeeUnits;
        private boolean simulateBeforeSubmit = true;
        private boolean testnet = false;

        private Builder(String networkId, String rpcEndpoint) {
            this.networkId = Objects.requireNonNull(networkId, "networkId");
            this.rpcEndpoint = Objects.requireNonNull(rpcEndpoint, "rpcEndpoint");
        }

        public Builder backupRpcEndpoint(String url) { this.backupRpcEndpoint = url; return this; }
        public Builder rpcTimeout(Duration d) { this.rpcTimeout = d; return this; }
        public Builder maxConfirmations(int n) { this.maxConfirmations = n; return this; }
        public Builder maxFeeUnits(long units) { this.maxFeeUnits = units; return this; }
        public Builder simulateBeforeSubmit(boolean b) { this.simulateBeforeSubmit = b; return this; }
        public Builder testnet(boolean b) { this.testnet = b; return this; }

        public BlockchainExecutionContext build() { return new BlockchainExecutionContext(this); }
    }
}
