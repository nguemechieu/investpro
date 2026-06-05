package org.investpro.exchange.solona;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable snapshot of a confirmed Solona transaction.
 *
 * @param signature   base-58 transaction signature (unique ID)
 * @param slot        Solona slot number the transaction was confirmed in
 * @param blockTime   wall-clock time of confirmation (null if not available)
 * @param status      "confirmed" | "finalized" | "failed" | "unknown"
 * @param feeSol      transaction fee in SOL (converted from lamports)
 * @param confirmed   true if the transaction was confirmed without error
 */
public record SolonaTransaction(
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
    public @NonNull String toString() {
        return "SolonaTransaction[sig=%s slot=%d status=%s fee=%s SOL]"
                .formatted(shortSignature(), slot, status, feeSol);
    }
}
