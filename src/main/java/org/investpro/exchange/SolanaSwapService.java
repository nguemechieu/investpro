package org.investpro.exchange;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for Solana token swaps.
 *
 * <p>Designed for future integration with Jupiter Aggregator (https://jup.ag).
 * No concrete implementation is provided in this release; live trading must
 * remain disabled ({@code solana.tradingEnabled=false}) until an implementation
 * is verified.
 *
 * <p>All methods return {@link CompletableFuture}s to ensure non-blocking operation.
 */
public interface SolanaSwapService {

    /**
     * Requests a swap quote from the aggregator.
     *
     * @param inputMint   base-58 mint of the token to sell
     * @param outputMint  base-58 mint of the token to buy
     * @param amount      amount of input tokens to swap (in human-readable units)
     * @return future resolving to a swap quote
     */
    CompletableFuture<SolanaSwapQuote> getQuote(String inputMint,
                                                 String outputMint,
                                                 BigDecimal amount);

    /**
     * Executes a swap based on a previously obtained quote.
     *
     * @param request fully-parameterised swap request
     * @return future resolving to the swap result
     * @throws SolanaException.TradingDisabledException if live trading is disabled
     */
    CompletableFuture<SolanaSwapResult> executeSwap(SolanaSwapRequest request);

    // ── Supporting records ────────────────────────────────────────────────────

    /**
     * A swap quote from the aggregator.
     *
     * @param inputMint    base-58 mint of the token to sell
     * @param outputMint   base-58 mint of the token to buy
     * @param inputAmount  token amount being sold
     * @param outputAmount estimated token amount to be received
     * @param priceImpact  estimated price impact as a fraction (e.g. 0.01 = 1 %)
     * @param fee          estimated protocol/platform fee
     */
    record SolanaSwapQuote(
            String inputMint,
            String outputMint,
            BigDecimal inputAmount,
            BigDecimal outputAmount,
            BigDecimal priceImpact,
            BigDecimal fee
    ) {}

    /**
     * The result of a completed (or failed) swap execution.
     *
     * @param success   true if the swap was submitted successfully
     * @param signature base-58 transaction signature on success, null on failure
     * @param error     error message on failure, null on success
     */
    record SolanaSwapResult(
            boolean success,
            String signature,
            String error
    ) {
        public static SolanaSwapResult failed(String message) {
            return new SolanaSwapResult(false, null, message);
        }
        public static SolanaSwapResult succeeded(String sig) {
            return new SolanaSwapResult(true, sig, null);
        }
    }

    /**
     * A swap execution request.
     *
     * @param inputMint      base-58 mint of the token to sell
     * @param outputMint     base-58 mint of the token to buy
     * @param amount         amount of input tokens (human-readable)
     * @param maxSlippage    max acceptable slippage as a fraction (e.g. 0.005 = 0.5 %)
     * @param walletAddress  base-58 wallet that will sign and pay for the swap
     */
    record SolanaSwapRequest(
            String inputMint,
            String outputMint,
            BigDecimal amount,
            BigDecimal maxSlippage,
            String walletAddress
    ) {}
}
