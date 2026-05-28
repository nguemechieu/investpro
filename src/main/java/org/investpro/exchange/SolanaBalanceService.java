package org.investpro.exchange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Maps Solana wallet data into InvestPro account/balance models.
 *
 * <p>Acts as a bridge between the raw Solana RPC layer (SOL + SPL tokens)
 * and the canonical {@code Account} / portfolio model used by InvestPro.
 */
public class SolanaBalanceService {

    private static final Logger log = LoggerFactory.getLogger(SolanaBalanceService.class);

    private final SolanaWalletService walletService;
    private final SolanaTokenService  tokenService;

    public SolanaBalanceService(SolanaWalletService walletService,
                                SolanaTokenService  tokenService) {
        this.walletService = walletService;
        this.tokenService  = tokenService;
    }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    /**
     * Returns a full balance snapshot for a Solana wallet address.
     *
     * <p>The snapshot includes the native SOL balance and all SPL token balances,
     * with token metadata (symbol, name, decimals) resolved from the offline registry
     * where available.
     *
     * @param address base-58 Solana public key
     * @return future resolving to the complete account snapshot
     */
    public CompletableFuture<SolanaAccountSnapshot> getAccountSnapshot(String address) {
        CompletableFuture<BigDecimal>            solBalanceFuture   = walletService.getSOLBalance(address);
        CompletableFuture<List<SolanaTokenBalance>> tokensFuture    = walletService.getTokenBalances(address);

        return solBalanceFuture.thenCombine(tokensFuture, (solBalance, rawTokens) -> {
            List<SolanaTokenBalance> enriched = enrichTokenMetadata(rawTokens);
            log.info("Solana balance updated: address={}... sol={} tokens={}",
                    safePrefix(address), solBalance, enriched.size());
            return new SolanaAccountSnapshot(address, solBalance, enriched);
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Adds symbol and name from the offline token registry where available.
     */
    private List<SolanaTokenBalance> enrichTokenMetadata(List<SolanaTokenBalance> rawTokens) {
        List<SolanaTokenBalance> result = new ArrayList<>(rawTokens.size());
        for (SolanaTokenBalance raw : rawTokens) {
            SolanaTokenBalance enriched = tokenService.fetchTokenMetadata(raw.mint())
                    .map(meta -> new SolanaTokenBalance(
                            raw.mint(),
                            meta.symbol(),
                            raw.amount(),
                            raw.decimals()))
                    .orElse(raw);
            result.add(enriched);
        }
        return result;
    }

    /** Returns only the first 6 characters of an address to avoid logging the full key. */
    private static String safePrefix(String address) {
        if (address == null || address.length() < 6) return "???";
        return address.substring(0, 6);
    }

    // ── Data record ───────────────────────────────────────────────────────────

    /**
     * Immutable snapshot of a Solana wallet's balances at a point in time.
     *
     * @param address     base-58 owner public key
     * @param solBalance  native SOL balance
     * @param tokens      SPL token balances (may be empty, never null)
     */
    public record SolanaAccountSnapshot(
            String address,
            BigDecimal solBalance,
            List<SolanaTokenBalance> tokens
    ) {
        /** Returns the total portfolio value in SOL (native SOL only, tokens not converted). */
        public BigDecimal totalSolOnly() { return solBalance; }

        /** @return true if the wallet has any non-zero token balance. */
        public boolean hasTokens() {
            return tokens != null && tokens.stream().anyMatch(SolanaTokenBalance::isNonZero);
        }
    }
}
