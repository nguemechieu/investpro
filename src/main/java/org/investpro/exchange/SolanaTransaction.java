package org.investpro.exchange;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable snapshot of a confirmed Solana transaction.
 *
 * @param signature   base-58 transaction signature (unique ID)
 * @param slot        Solana slot number the transaction was confirmed in
 * @param blockTime   wall-clock time of confirmation (null if not available)
 * @param status      "confirmed" | "finalized" | "failed" | "unknown"
 * @param feeSol      transaction fee in SOL (converted from lamports)
 * @param confirmed   true if the transaction was confirmed without error
 */
public record SolanaTransaction(
        String signature,
        long slot,
        Instant blockTime,
        String status,
        BigDecimal feeSol,
        boolean confirmed
) {

    /** @return first 16 characters of the signature for display/logging. */
    public String shortSignature() {
        return signature == null || signature.length() < 16
                ? signature
                : signature.substring(0, 16) + "…";
    }

    @Override
    public String toString() {
        return "SolanaTransaction[sig=%s slot=%d status=%s fee=%s SOL]"
                .formatted(shortSignature(), slot, status, feeSol);
    }
}
