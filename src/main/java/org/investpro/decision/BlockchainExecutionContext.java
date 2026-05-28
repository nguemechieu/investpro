package org.investpro.decision;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Immutable blockchain execution context for on-chain trade routing.
 *
 * <p>Provides the parameters required to submit a transaction on a blockchain network —
 * Solana, Stellar, or Ethereum-compatible — including network identity, RPC endpoint,
 * wallet address (public key only — never private keys), token mint, and gas/fee estimates.</p>
 *
 * <h3>Security note:</h3>
 * <p>This record stores ONLY public addresses and fee estimates. Private keys, seed
 * phrases, and signing credentials are NEVER stored here. Transaction signing is
 * delegated to a secure signing layer outside this record.</p>
 *
 * @param network            the target blockchain network
 * @param chainId            chain identifier (e.g. "mainnet-beta" for Solana, "PUBLIC" for Stellar)
 * @param rpcEndpoint        RPC/API endpoint URL (no credentials embedded)
 * @param walletAddress      public wallet address / public key (NOT the private key)
 * @param tokenMint          token mint address or asset code (e.g. "EPjFWdd5..." for USDC on Solana)
 * @param gasEstimate        estimated transaction fee in native token units (SOL, XLM, ETH)
 * @param slippageTolerance  maximum acceptable slippage as a fraction (e.g. 0.005 = 0.5%)
 * @param memo               optional transaction memo or reference
 */
public record BlockchainExecutionContext(
        BlockchainNetwork network,
        String chainId,
        String rpcEndpoint,
        String walletAddress,
        @Nullable String tokenMint,
        BigDecimal gasEstimate,
        double slippageTolerance,
        @Nullable String memo
) {

    // ─── Inner enum ───────────────────────────────────────────────────────────

    /** Supported blockchain networks. */
    public enum BlockchainNetwork {
        /** Solana mainnet or devnet — high throughput, low fees. */
        SOLANA("SOL"),
        /** Stellar network — fast, low-cost cross-border payments and DEX. */
        STELLAR("XLM"),
        /** Ethereum mainnet. */
        ETHEREUM("ETH"),
        /** Ethereum-compatible Layer-2 (e.g. Arbitrum, Optimism). */
        EVM_L2("ETH"),
        /** Any other blockchain not explicitly listed. */
        OTHER("UNKNOWN");

        /** Native currency symbol for fee estimation. */
        public final String nativeCurrency;

        BlockchainNetwork(String nativeCurrency) {
            this.nativeCurrency = nativeCurrency;
        }

        /** Returns {@code true} if this network uses Solana's account model. */
        public boolean isSolana() { return this == SOLANA; }

        /** Returns {@code true} if this network uses Stellar's protocol. */
        public boolean isStellar() { return this == STELLAR; }
    }

    // ─── Compact constructor (validation) ─────────────────────────────────────

    public BlockchainExecutionContext {
        if (network == null)          throw new IllegalArgumentException("network must not be null");
        if (chainId == null || chainId.isBlank()) throw new IllegalArgumentException("chainId must not be blank");
        if (rpcEndpoint == null || rpcEndpoint.isBlank()) throw new IllegalArgumentException("rpcEndpoint must not be blank");
        if (walletAddress == null || walletAddress.isBlank()) throw new IllegalArgumentException("walletAddress (public key) must not be blank");
        if (gasEstimate == null)      gasEstimate = BigDecimal.ZERO;
        slippageTolerance = Math.max(0.0, Math.min(0.5, slippageTolerance)); // cap at 50%
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    /**
     * Creates a Solana execution context for a token swap.
     *
     * @param rpcEndpoint       Solana RPC endpoint URL
     * @param walletPublicKey   base58-encoded Solana wallet public key
     * @param tokenMint         token mint address (SPL token)
     * @param slippageTolerance acceptable slippage fraction (e.g. 0.005)
     */
    public static BlockchainExecutionContext solana(
            String rpcEndpoint,
            String walletPublicKey,
            String tokenMint,
            double slippageTolerance) {
        return new BlockchainExecutionContext(
                BlockchainNetwork.SOLANA, "mainnet-beta",
                rpcEndpoint, walletPublicKey, tokenMint,
                new BigDecimal("0.000005"), slippageTolerance, null);
    }

    /**
     * Creates a Stellar execution context.
     *
     * @param horizonEndpoint   Stellar Horizon API URL
     * @param walletPublicKey   Stellar public key (G...)
     * @param assetCode         Stellar asset code (e.g. "USDC")
     */
    public static BlockchainExecutionContext stellar(
            String horizonEndpoint,
            String walletPublicKey,
            String assetCode) {
        return new BlockchainExecutionContext(
                BlockchainNetwork.STELLAR, "PUBLIC",
                horizonEndpoint, walletPublicKey, assetCode,
                new BigDecimal("0.00001"), 0.001, null);
    }

    // ─── Derived properties ───────────────────────────────────────────────────

    /** Returns {@code true} if the context targets Solana. */
    public boolean isSolana() { return network.isSolana(); }

    /** Returns {@code true} if the context targets Stellar. */
    public boolean isStellar() { return network.isStellar(); }

    /**
     * Returns a safe string representation for logging — includes network and chain ID
     * but omits RPC endpoint and wallet address to reduce accidental credential exposure.
     */
    @Override
    public String toString() {
        return "BlockchainExecutionContext{network=" + network
                + ", chainId='" + chainId + "'"
                + ", token=" + (tokenMint != null ? tokenMint.substring(0, Math.min(8, tokenMint.length())) + "..." : "null")
                + ", gasEstimate=" + gasEstimate
                + ", slippage=" + slippageTolerance + "}";
    }
}
