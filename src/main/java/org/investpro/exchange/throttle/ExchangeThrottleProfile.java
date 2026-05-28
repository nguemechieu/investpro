package org.investpro.exchange.throttle;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable throttle and retry configuration for a specific exchange.
 *
 * <p>Pre-built profiles are provided via static factory methods for the
 * most commonly used exchanges.  All profiles expose:
 * <ul>
 *   <li>Rate limits ({@link #maxRequestsPerSecond}, {@link #burstLimit})</li>
 *   <li>Transport preference ({@link #websocketFirst})</li>
 *   <li>Exponential back-off parameters for REST retries</li>
 *   <li>WebSocket reconnect configuration</li>
 * </ul>
 *
 * <p>Back-off delay for attempt {@code n} (0-indexed) is computed as:
 * <pre>
 *   delay = min(retryBaseDelayMs * backoffMultiplier^n, retryMaxDelayMs)
 * </pre>
 *
 * <p>Use {@link #DEFAULT()} as a safe conservative baseline for any exchange
 * not listed here.
 */
public record ExchangeThrottleProfile(
        @NotNull String exchangeName,
        int maxRequestsPerSecond,
        int burstLimit,
        boolean websocketFirst,
        int restRetryMaxAttempts,
        long retryBaseDelayMs,
        long retryMaxDelayMs,
        double backoffMultiplier,
        int websocketMaxReconnects,
        long websocketReconnectDelayMs
) {

    // ── Pre-built exchange profiles ───────────────────────────────────────────

    /**
     * Throttle profile for <b>Coinbase Advanced Trade</b>.
     * WebSocket-first; 10 REST requests/s; burst of 30; exponential back-off capped at 30 s.
     */
    public static @NotNull ExchangeThrottleProfile COINBASE() {
        return new ExchangeThrottleProfile(
                "COINBASE",
                10, 30,
                true,
                3, 500L, 30_000L, 2.0,
                5, 1_000L
        );
    }

    /**
     * Throttle profile for <b>OANDA</b>.
     * REST-first; conservative 2 requests/s due to rate limits; back-off capped at 60 s.
     */
    public static @NotNull ExchangeThrottleProfile OANDA() {
        return new ExchangeThrottleProfile(
                "OANDA",
                2, 5,
                false,
                5, 1_000L, 60_000L, 2.0,
                3, 2_000L
        );
    }

    /**
     * Throttle profile for <b>Binance</b>.
     * WebSocket-first; high-frequency 20 requests/s; burst of 50; back-off capped at 15 s.
     */
    public static @NotNull ExchangeThrottleProfile BINANCE() {
        return new ExchangeThrottleProfile(
                "BINANCE",
                20, 50,
                true,
                3, 250L, 15_000L, 2.0,
                5, 500L
        );
    }

    /**
     * Throttle profile for <b>Alpaca</b>.
     * WebSocket-first; 10 requests/s; burst of 20; back-off capped at 30 s.
     */
    public static @NotNull ExchangeThrottleProfile ALPACA() {
        return new ExchangeThrottleProfile(
                "ALPACA",
                10, 20,
                true,
                3, 500L, 30_000L, 2.0,
                5, 1_000L
        );
    }

    /**
     * Throttle profile for <b>Bitfinex</b>.
     * WebSocket-first; 10 requests/s; burst of 30; back-off capped at 30 s.
     */
    public static @NotNull ExchangeThrottleProfile BITFINEX() {
        return new ExchangeThrottleProfile(
                "BITFINEX",
                10, 30,
                true,
                3, 500L, 30_000L, 2.0,
                5, 1_000L
        );
    }

    /**
     * Throttle profile for <b>DEX / on-chain</b> interactions.
     * REST-first (no WebSocket); lower rates due to RPC constraints; aggressive back-off.
     */
    public static @NotNull ExchangeThrottleProfile DEX() {
        return new ExchangeThrottleProfile(
                "DEX",
                5, 10,
                false,
                5, 2_000L, 120_000L, 2.0,
                3, 3_000L
        );
    }

    /**
     * Conservative default profile for any exchange not explicitly configured.
     * Safe for any REST-based exchange with unknown rate limits.
     */
    public static @NotNull ExchangeThrottleProfile DEFAULT() {
        return new ExchangeThrottleProfile(
                "DEFAULT",
                5, 10,
                false,
                3, 1_000L, 60_000L, 2.0,
                3, 2_000L
        );
    }

    // ── Instance methods ──────────────────────────────────────────────────────

    /**
     * Computes the exponential back-off delay for a given retry attempt number.
     *
     * @param attemptNumber zero-indexed attempt number (0 = first retry)
     * @return delay in milliseconds, capped at {@link #retryMaxDelayMs}
     */
    public long computeBackoffDelay(int attemptNumber) {
        double delay = retryBaseDelayMs * Math.pow(backoffMultiplier, attemptNumber);
        return Math.min((long) delay, retryMaxDelayMs);
    }

    /**
     * Returns {@code true} if WebSocket is the preferred transport for this exchange.
     * When {@code true}, REST polling should be used only as a fallback.
     */
    public boolean isWebSocketPriority() {
        return websocketFirst;
    }

    /**
     * Returns a brief human-readable summary of this throttle profile.
     *
     * @return formatted summary string
     */
    public @NotNull String summary() {
        return "ThrottleProfile[%s rps=%d burst=%d ws=%b retry=%dx base=%dms max=%dms]"
                .formatted(
                        exchangeName,
                        maxRequestsPerSecond,
                        burstLimit,
                        websocketFirst,
                        restRetryMaxAttempts,
                        retryBaseDelayMs,
                        retryMaxDelayMs
                );
    }
}
