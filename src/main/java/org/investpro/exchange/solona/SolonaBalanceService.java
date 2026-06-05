package org.investpro.exchange.solona;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Maps Solona wallet data into InvestPro account/balance models.
 *
 * <p>
 * Acts as a bridge between the raw Solona RPC layer (SOL + SPL tokens)
 * and the canonical {@code Account} / portfolio model used by InvestPro.
 */
public class SolonaBalanceService {

    private static final Logger log = LoggerFactory.getLogger(SolonaBalanceService.class);

    private final SolonaWalletService walletService;
    private final SolonaTokenService tokenService;

    public SolonaBalanceService(SolonaWalletService walletService,
            SolonaTokenService tokenService) {
        this.walletService = walletService;
        this.tokenService = tokenService;
    }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    /**
     * Returns a full balance snapshot for a Solona wallet address.
     *
     * <p>
     * The snapshot includes the native SOL balance and all SPL token balances,
     * with token metadata (symbol, name, decimals) resolved from the offline
     * registry
     * where available.
     *
     * @param address base-58 Solona public key
     * @return future resolving to the complete account snapshot
     */
    public CompletableFuture<SolonaAccountSnapshot> getAccountSnapshot(String address) {
        CompletableFuture<BigDecimal> solBalanceFuture = walletService.getSOLBalance(address);
        CompletableFuture<List<SolonaTokenBalance>> tokensFuture = walletService.getTokenBalances(address);

        return solBalanceFuture.thenCombine(tokensFuture, (solBalance, rawTokens) -> {
            List<SolonaTokenBalance> enriched = enrichTokenMetadata(rawTokens);
            log.info("Solona balance updated: address={}... sol={} tokens={}",
                    safePrefix(address), solBalance, enriched.size());
            return new SolonaAccountSnapshot(address, solBalance, enriched);
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Adds symbol and name from the offline token registry where available.
     */
    private List<SolonaTokenBalance> enrichTokenMetadata(List<SolonaTokenBalance> rawTokens) {
        List<SolonaTokenBalance> result = new ArrayList<>(rawTokens.size());
        for (SolonaTokenBalance raw : rawTokens) {
            SolonaTokenBalance enriched = tokenService.fetchTokenMetadata(raw.mint())
                    .map(meta -> new SolonaTokenBalance(
                            raw.mint(),
                            meta.symbol(),
                            raw.amount(),
                            raw.decimals()))
                    .orElse(raw);
            result.add(enriched);
        }
        return result;
    }

    /**
     * Returns only the first 6 characters of an address to avoid logging the full
     * key.
     */
    private static String safePrefix(String address) {
        if (address == null || address.length() < 6)
            return "???";
        return address.substring(0, 6);
    }

    // ── Data record ───────────────────────────────────────────────────────────

    /**
     * Immutable snapshot of a Solona wallet's balances at a point in time.
     *
     * @param address    base-58 owner public key
     * @param solBalance native SOL balance
     * @param tokens     SPL token balances (may be empty, never null)
     */
    public record SolonaAccountSnapshot(
            String address,
            BigDecimal solBalance,
            List<SolonaTokenBalance> tokens) {
        /**
         * Returns the total portfolio value in SOL (native SOL only, tokens not
         * converted).
         */
        public BigDecimal totalSolOnly() {
            return solBalance;
        }

        /** @return true if the wallet has any non-zero token balance. */
        public boolean hasTokens() {
            return tokens != null && tokens.stream().anyMatch(SolonaTokenBalance::isNonZero);
        }
    }
}
