package org.investpro.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provides Solana on-chain transaction operations.
 *
 * <p>
 * <b>Safety:</b> {@link #sendTransaction(String)} is guarded by the
 * {@code solana.tradingEnabled} configuration flag. Calling it when trading
 * is disabled always throws {@link SolanaException.TradingDisabledException}.
 */
public class SolanaTransactionService {

    private static final Logger log = LoggerFactory.getLogger(SolanaTransactionService.class);
    private static final long DEFAULT_FEE_LAMPORTS = 5_000L;

    private final SolanaNetworkClient rpc;
    private final SolanaNetworkConfig config;

    public SolanaTransactionService(SolanaNetworkClient rpc, SolanaNetworkConfig config) {
        this.rpc = rpc;
        this.config = config;
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns the most recent confirmed transactions for the given address.
     *
     * @param address base-58 owner public key
     * @param limit   max number of transactions (1–100)
     * @return future resolving to the list of transactions (oldest to newest)
     */
    public CompletableFuture<List<SolanaTransaction>> getRecentTransactions(
            String address, int limit) {

        return rpc.getSignaturesForAddress(address, Math.min(100, Math.max(1, limit)))
                .thenCompose(signaturesNode -> {
                    List<CompletableFuture<SolanaTransaction>> fetches = new ArrayList<>();
                    if (signaturesNode != null && signaturesNode.isArray()) {
                        for (JsonNode sig : signaturesNode) {
                            String signature = sig.path("signature").asText("");
                            if (!signature.isBlank()) {
                                fetches.add(getTransaction(signature));
                            }
                        }
                    }
                    return CompletableFuture.allOf(fetches.toArray(new CompletableFuture[0]))
                            .thenApply(ignored -> {
                                List<SolanaTransaction> results = new ArrayList<>(fetches.size());
                                for (CompletableFuture<SolanaTransaction> f : fetches) {
                                    try {
                                        results.add(f.join());
                                    } catch (Exception joinError) {
                                    }
                                }
                                return results;
                            });
                });
    }

    /**
     * Returns the parsed details of a single confirmed transaction.
     *
     * @param signature base-58 transaction signature
     * @return future resolving to the transaction details
     */
    public CompletableFuture<SolanaTransaction> getTransaction(String signature) {
        return rpc.getTransaction(signature)
                .thenApply(result -> parseTransaction(signature, result));
    }

    // ── Fee estimate ──────────────────────────────────────────────────────────

    /**
     * Estimates the fee for a transaction message.
     *
     * <p>
     * Falls back to the default fee ({@value DEFAULT_FEE_LAMPORTS} lamports)
     * if the RPC call fails.
     *
     * @return estimated fee in lamports
     */
    public CompletableFuture<Long> estimateFee(String messageBase64) {
        if (messageBase64 == null || messageBase64.isBlank()) {
            return CompletableFuture.completedFuture(DEFAULT_FEE_LAMPORTS);
        }
        return rpc.getFeeForMessage(messageBase64)
                .exceptionally(ex -> {
                    log.debug("Solana: fee estimation failed, using default: {}", ex.getMessage());
                    return DEFAULT_FEE_LAMPORTS;
                });
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    /**
     * Sends a fully signed, base-64 encoded transaction to the Solana network.
     *
     * <p>
     * <b>Safety checks performed before submission:</b>
     * <ol>
     * <li>{@code solana.enabled=true}</li>
     * <li>{@code solana.tradingEnabled=true}</li>
     * </ol>
     *
     * @param signedTransactionBase64 base-64 encoded signed transaction bytes
     * @return future resolving to the transaction signature
     * @throws SolanaException.TradingDisabledException if trading is not enabled
     */
    public CompletableFuture<String> sendTransaction(String signedTransactionBase64) {
        if (!config.isLiveTradingAllowed()) {
            return CompletableFuture.failedFuture(
                    new SolanaException.TradingDisabledException(
                            "Solana live trading is disabled. Set solana.tradingEnabled=true " +
                                    "and solana.enabled=true in config.properties to enable it."));
        }

        log.info("Solana: submitting transaction to network={}", config.network());
        return rpc.sendTransaction(signedTransactionBase64)
                .whenComplete((sig, ex) -> {
                    if (ex == null) {
                        log.info("Solana transaction submitted: sig={}...", safePrefix16(sig));
                    } else {
                        log.warn("Solana transaction submission failed: {}", ex.getMessage());
                    }
                });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private SolanaTransaction parseTransaction(String signature, JsonNode result) {
        if (result == null || result.isNull() || result.isMissingNode()) {
            return new SolanaTransaction(signature, -1L, null, "unknown",
                    BigDecimal.ZERO, false);
        }

        long slot = result.path("slot").asLong(-1L);
        long blockTime = result.path("blockTime").asLong(-1L);
        Instant ts = blockTime >= 0 ? Instant.ofEpochSecond(blockTime) : null;

        JsonNode meta = result.path("meta");
        boolean hasErr = !meta.path("err").isNull() && !meta.path("err").isMissingNode();
        long feeLam = meta.path("fee").asLong(DEFAULT_FEE_LAMPORTS);
        BigDecimal feeSol = BigDecimal.valueOf(feeLam)
                .divide(BigDecimal.valueOf(SolanaNetworkConfig.LAMPORTS_PER_SOL),
                        9, RoundingMode.HALF_UP);

        String status = hasErr ? "failed" : "confirmed";
        return new SolanaTransaction(signature, slot, ts, status, feeSol, !hasErr);
    }

    private static String safePrefix16(String s) {
        if (s == null || s.length() < 16)
            return s;
        return s.substring(0, 16);
    }
}
