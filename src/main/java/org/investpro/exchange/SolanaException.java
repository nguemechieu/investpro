package org.investpro.exchange;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Root exception for all Solana network adapter errors.
 *
 * <p>Sub-classes carry additional context (RPC error codes, transaction signatures, etc.)
 * so callers can react appropriately without parsing message strings.
 *
 * <p>Note: this class lives in {@code org.investpro.exchange} following the same convention
 * as {@link StellarNetwork}. Move to {@code org.investpro.exchange.solana} once that
 * package directory is created.
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class SolanaException extends RuntimeException {

    /**
     * -- GETTER --
     * RPC error code returned by the Solana node, or 0 when not applicable.
     */
    private final int rpcErrorCode;

    public SolanaException(String message) {
        this(message, 0, null);
    }

    public SolanaException(String message, Throwable cause) {
        this(message, 0, cause);
    }

    public SolanaException(String message, int rpcErrorCode) {
        this(message, rpcErrorCode, null);
    }

    public SolanaException(String message, int rpcErrorCode, Throwable cause) {
        super(message, cause);
        this.rpcErrorCode = rpcErrorCode;
    }

    // ── Typed sub-classes ─────────────────────────────────────────────────

    /** Thrown when the Solana network adapter is disabled in config. */
    public static final class SolanaDisabledException extends SolanaException {
        public SolanaDisabledException() {
            super("Solana integration is disabled. Set solana.enabled=true in config.properties.");
        }
        public SolanaDisabledException(String message) {
            super(message);
        }
    }

    /** Thrown when live trading is attempted but {@code solana.tradingEnabled=false}. */
    public static final class TradingDisabledException extends SolanaException {
        public TradingDisabledException() {
            super("Solana live trading is disabled. Set solana.tradingEnabled=true in config.properties.");
        }
        public TradingDisabledException(String message) {
            super(message);
        }
    }

    /** Thrown when an RPC call returns a non-null error object. */
    public static final class RpcException extends SolanaException {
        public RpcException(String method, int code, String rpcMessage) {
            super("Solana RPC error [%s] code=%d: %s".formatted(method, code, rpcMessage), code);
        }
    }

    /** Thrown when a Solana wallet address fails validation. */
    public static final class InvalidAddressException extends SolanaException {
        public InvalidAddressException(String address) {
            super("Invalid Solana address: '%s'".formatted(maskAddress(address)));
        }

        private static String maskAddress(String addr) {
            if (addr == null || addr.length() < 8) return "<invalid>";
            return addr.substring(0, 4) + "\u2026" + addr.substring(addr.length() - 4);
        }
    }

    /** Thrown when pre-trade safety checks fail. */
    public static final class SafetyCheckException extends SolanaException {
        public SafetyCheckException(String reason) {
            super("Solana pre-trade safety check failed: " + reason);
        }
    }
}
