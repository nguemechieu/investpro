package org.investpro.exchange.throttle;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;

/**
 * Per-exchange configuration for request pacing, retry, and backoff.
 *
 * <p>Profiles are consumed by {@code AdaptivePollingEngine} and REST clients
 * to pace API calls, avoid rate-limit errors, and recover gracefully.
 *
 * <p>Use the static factory methods ({@link #coinbase()}, {@link #oanda()},
 * {@link #dex()}) for pre-tuned profiles, or {@link #builder(String)} for
 * custom configuration.
 */
public final class ExchangeThrottleProfile {

    private final String exchangeName;
    /** Minimum delay between consecutive REST requests to the same endpoint. */
    private final Duration requestPaceInterval;
    /** Whether WebSocket should be preferred over REST polling. */
    private final boolean websocketFirst;
    /** Maximum number of retry attempts on transient failures. */
    private final int maxRetries;
    /** Base delay for exponential backoff on retries. */
    private final Duration backoffBase;
    /** Maximum delay cap for exponential backoff. */
    private final Duration backoffMax;
    /** Maximum burst requests allowed per second. */
    private final int burstLimitPerSecond;
    /** Multiplier applied to backoff base on each successive retry. */
    private final double backoffMultiplier;

    private ExchangeThrottleProfile(Builder b) {
        this.exchangeName = b.exchangeName;
        this.requestPaceInterval = b.requestPaceInterval;
        this.websocketFirst = b.websocketFirst;
        this.maxRetries = b.maxRetries;
        this.backoffBase = b.backoffBase;
        this.backoffMax = b.backoffMax;
        this.burstLimitPerSecond = b.burstLimitPerSecond;
        this.backoffMultiplier = b.backoffMultiplier;
    }

    // ── Pre-built profiles ───────────────────────────────────────────────────────

    /**
     * Profile tuned for Coinbase Advanced Trade API.
     *
     * <ul>
     *   <li>WebSocket-first: market data via stream, REST for account ops only
     *   <li>10 req/s burst; 200ms minimum pacing
     * </ul>
     */
    public static ExchangeThrottleProfile coinbase() {
        return builder("Coinbase")
                .websocketFirst(true)
                .requestPaceInterval(Duration.ofMillis(200))
                .burstLimitPerSecond(10)
                .maxRetries(4)
                .backoffBase(Duration.ofMillis(500))
                .backoffMax(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Profile tuned for OANDA REST API.
     *
     * <ul>
     *   <li>Careful REST pacing; OANDA pricing stream used for ticks
     *   <li>3 req/s burst; 400ms minimum pacing
     * </ul>
     */
    public static ExchangeThrottleProfile oanda() {
        return builder("OANDA")
                .websocketFirst(false)
                .requestPaceInterval(Duration.ofMillis(400))
                .burstLimitPerSecond(3)
                .maxRetries(5)
                .backoffBase(Duration.ofSeconds(1))
                .backoffMax(Duration.ofMinutes(2))
                .build();
    }

    /**
     * Profile tuned for DEX / blockchain RPC nodes.
     *
     * <ul>
     *   <li>RPC-aware throttling; high retry count for transient node failures
     *   <li>5 req/s burst; 300ms pacing
     * </ul>
     */
    public static ExchangeThrottleProfile dex() {
        return builder("DEX")
                .websocketFirst(false)
                .requestPaceInterval(Duration.ofMillis(300))
                .burstLimitPerSecond(5)
                .maxRetries(8)
                .backoffBase(Duration.ofMillis(300))
                .backoffMax(Duration.ofSeconds(60))
                .backoffMultiplier(2.0)
                .build();
    }

    /** Default balanced profile. */
    public static ExchangeThrottleProfile defaultProfile(String exchangeName) {
        return builder(exchangeName)
                .requestPaceInterval(Duration.ofMillis(250))
                .burstLimitPerSecond(5)
                .maxRetries(3)
                .backoffBase(Duration.ofMillis(500))
                .backoffMax(Duration.ofSeconds(30))
                .build();
    }

    // ── Accessors ────────────────────────────────────────────────────────────────

    public String getExchangeName() { return exchangeName; }
    public Duration getRequestPaceInterval() { return requestPaceInterval; }
    public boolean isWebsocketFirst() { return websocketFirst; }
    public int getMaxRetries() { return maxRetries; }
    public Duration getBackoffBase() { return backoffBase; }
    public Duration getBackoffMax() { return backoffMax; }
    public int getBurstLimitPerSecond() { return burstLimitPerSecond; }
    public double getBackoffMultiplier() { return backoffMultiplier; }

    /** Computes the delay for retry attempt {@code n} (0-indexed). */
    public Duration backoffDelay(int n) {
        if (n <= 0) return backoffBase;
        long ms = (long) (backoffBase.toMillis() * Math.pow(backoffMultiplier, n));
        return Duration.ofMillis(Math.min(ms, backoffMax.toMillis()));
    }

    @Override
    public String toString() {
        return "ThrottleProfile{exchange='" + exchangeName
                + "', wsFirst=" + websocketFirst
                + ", pace=" + requestPaceInterval.toMillis() + "ms"
                + ", burst=" + burstLimitPerSecond + "/s}";
    }

    // ── Builder ─────────────────────────────────────────────────────────────────

    public static Builder builder(@NotNull String exchangeName) {
        return new Builder(exchangeName);
    }

    public static final class Builder {
        private final String exchangeName;
        private Duration requestPaceInterval = Duration.ofMillis(250);
        private boolean websocketFirst = false;
        private int maxRetries = 3;
        private Duration backoffBase = Duration.ofMillis(500);
        private Duration backoffMax = Duration.ofSeconds(30);
        private int burstLimitPerSecond = 5;
        private double backoffMultiplier = 2.0;

        private Builder(String exchangeName) {
            this.exchangeName = Objects.requireNonNull(exchangeName);
        }

        public Builder requestPaceInterval(Duration d) { this.requestPaceInterval = d; return this; }
        public Builder websocketFirst(boolean b) { this.websocketFirst = b; return this; }
        public Builder maxRetries(int n) { this.maxRetries = n; return this; }
        public Builder backoffBase(Duration d) { this.backoffBase = d; return this; }
        public Builder backoffMax(Duration d) { this.backoffMax = d; return this; }
        public Builder burstLimitPerSecond(int n) { this.burstLimitPerSecond = n; return this; }
        public Builder backoffMultiplier(double m) { this.backoffMultiplier = m; return this; }

        public ExchangeThrottleProfile build() { return new ExchangeThrottleProfile(this); }
    }
}
