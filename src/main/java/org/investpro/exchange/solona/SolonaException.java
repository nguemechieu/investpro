package org.investpro.exchange.solona;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.investpro.exchange.StellarNetwork;

/**
 * Root exception for all Solona network adapter errors.
 *
 * <p>Sub-classes carry additional context (RPC error codes, transaction signatures, etc.)
 * so callers can react appropriately without parsing message strings.
 *
 * <p>Note: this class lives in {@code org.investpro.exchange} following the same convention
 * as {@link StellarNetwork}. Move to {@code org.investpro.exchange.solona} once that
 * package directory is created.
 */

@EqualsAndHashCode(callSuper = true)
@Data
public class SolonaException extends RuntimeException {

    /**
     * -- GETTER --
     * RPC error code returned by the Solona node, or 0 when not applicable.
     */
    private final int rpcErrorCode;

    public SolonaException(String message) {
        this(message, 0, null);
    }

    public SolonaException(String message, Throwable cause) {
        this(message, 0, cause);
    }

    public SolonaException(String message, int rpcErrorCode) {
        this(message, rpcErrorCode, null);
    }

    public SolonaException(String message, int rpcErrorCode, Throwable cause) {
        super(message, cause);
        this.rpcErrorCode = rpcErrorCode;
    }

    // ── Typed sub-classes ─────────────────────────────────────────────────

    /** Thrown when the Solona network adapter is disabled in config. */
    public static final class SolonaDisabledException extends SolonaException {
        public SolonaDisabledException() {
            super("Solona integration is disabled. Set solona.enabled=true in config.properties.");
        }
        public SolonaDisabledException(String message) {
            super(message);
        }
    }

    /** Thrown when live trading is attempted but {@code solona.tradingEnabled=false}. */
    public static final class TradingDisabledException extends SolonaException {
        public TradingDisabledException() {
            super("Solona live trading is disabled. Set solona.tradingEnabled=true in config.properties.");
        }
        public TradingDisabledException(String message) {
            super(message);
        }
    }

    /** Thrown when an RPC call returns a non-null error object. */
    public static final class RpcException extends SolonaException {
        public RpcException(String method, int code, String rpcMessage) {
            super("Solona RPC error [%s] code=%d: %s".formatted(method, code, rpcMessage), code);
        }
    }

    /** Thrown when a Solona wallet address fails validation. */
    public static final class InvalidAddressException extends SolonaException {
        public InvalidAddressException(String address) {
            super("Invalid Solona address: '%s'".formatted(maskAddress(address)));
        }

        private static String maskAddress(String addr) {
            if (addr == null || addr.length() < 8) return "<invalid>";
            return addr.substring(0, 4) + "\u2026" + addr.substring(addr.length() - 4);
        }
    }

    /** Thrown when pre-trade safety checks fail. */
    public static final class SafetyCheckException extends SolonaException {
        public SafetyCheckException(String reason) {
            super("Solona pre-trade safety check failed: " + reason);
        }
    }
}
