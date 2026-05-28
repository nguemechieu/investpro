package org.investpro.exchange.throttle;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable throttle and retry configuration for a specific exchange.
 *
 * <p>Pre-built profiles are provided via static factory methods for the
 * most commonly used exchanges.
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

    public static @NotNull ExchangeThrottleProfile COINBASE() {
        return new ExchangeThrottleProfile(
                "COINBASE",
                10, 30,
                true,
                3, 500L, 30_000L, 2.0,
                5, 1_000L
        );
    }

    public static @NotNull ExchangeThrottleProfile OANDA() {
        return new ExchangeThrottleProfile(
                "OANDA",
                2, 5,
                false,
                5, 1_000L, 60_000L, 2.0,
                3, 2_000L
        );
    }

    public static @NotNull ExchangeThrottleProfile BINANCE() {
        return new ExchangeThrottleProfile(
                "BINANCE",
                20, 50,
                true,
                3, 250L, 15_000L, 2.0,
                5, 500L
        );
    }

    public static @NotNull ExchangeThrottleProfile ALPACA() {
        return new ExchangeThrottleProfile(
                "ALPACA",
                10, 20,
                true,
                3, 500L, 30_000L, 2.0,
                5, 1_000L
        );
    }

    public static @NotNull ExchangeThrottleProfile BITFINEX() {
        return new ExchangeThrottleProfile(
                "BITFINEX",
                10, 30,
                true,
                3, 500L, 30_000L, 2.0,
                5, 1_000L
        );
    }

    public static @NotNull ExchangeThrottleProfile DEX() {
        return new ExchangeThrottleProfile(
                "DEX",
                5, 10,
                false,
                5, 2_000L, 120_000L, 2.0,
                3, 3_000L
        );
    }

    public static @NotNull ExchangeThrottleProfile DEFAULT() {
        return new ExchangeThrottleProfile(
                "DEFAULT",
                5, 10,
                false,
                3, 1_000L, 60_000L, 2.0,
                3, 2_000L
        );
    }

    public long computeBackoffDelay(int attemptNumber) {
        double delay = retryBaseDelayMs * Math.pow(backoffMultiplier, attemptNumber);
        return Math.min((long) delay, retryMaxDelayMs);
    }

    public boolean isWebSocketPriority() {
        return websocketFirst;
    }

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
