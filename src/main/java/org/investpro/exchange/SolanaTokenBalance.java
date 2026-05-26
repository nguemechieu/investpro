package org.investpro.exchange;

import java.math.BigDecimal;

/**
 * Immutable snapshot of an SPL token balance for one mint/owner pair.
 *
 * @param mint     base-58 mint address of the SPL token
 * @param symbol   normalised ticker symbol (e.g. USDC, BONK) — may be empty
 *                 until enriched by {@link SolanaTokenService}
 * @param amount   human-readable token amount (already divided by 10^decimals)
 * @param decimals number of decimal places the token uses
 */
public record SolanaTokenBalance(
        String mint,
        String symbol,
        BigDecimal amount,
        int decimals
) {

    /** @return true if the amount is greater than zero. */
    public boolean isNonZero() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Returns a display-friendly string: {@code USDC 12.50}. */
    @Override
    public String toString() {
        String sym = (symbol == null || symbol.isBlank()) ? mint.substring(0, 6) + "…" : symbol;
        return "%s %s".formatted(sym, amount == null ? "0" : amount.toPlainString());
    }
}
