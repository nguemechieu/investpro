package org.investpro.exchange.compat;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.normalization.NormalizedMarketSnapshot;
import org.investpro.exchange.registry.ExchangeCapabilityRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Migration bridge that integrates legacy {@link Exchange} subclasses with the
 * new institutional exchange runtime architecture.
 *
 * <p>Existing {@code Exchange} implementations do not need to be changed.
 * Wrap them in this bridge and register with {@link ExchangeCapabilityRegistry}
 * to gain:
 * <ul>
 *   <li>Capability-aware queries ({@code findByFeature}, {@code findSupporting})</li>
 *   <li>Runtime state tracking ({@link org.investpro.exchange.runtime.ExchangeRuntimeState})</li>
 *   <li>Normalized market snapshot compatibility</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   Exchange legacy = new CoinbaseSpotExchange(credentials);
 *   ExchangeAdapterMigrationBridge bridge = ExchangeAdapterMigrationBridge.wrap(legacy);
 *   bridge.registerWith(registry);
 * }</pre>
 *
 * <h3>Backward compatibility guarantee</h3>
 * <p>This class is additive only. It holds a reference to the original
 * {@code Exchange} instance; all existing callers of the original instance
 * continue to work unchanged.
 */
@Slf4j
public final class ExchangeAdapterMigrationBridge {

    private final Exchange delegate;
    private final ExchangeCapability capability;

    /** Per-exchange stale snapshot holder for normalization layer integration. */
    private volatile NormalizedMarketSnapshot lastSnapshot;

    private ExchangeAdapterMigrationBridge(
            @NotNull Exchange delegate,
            @NotNull ExchangeCapability capability
    ) {
        this.delegate = delegate;
        this.capability = capability;
    }

    /**
     * Wraps a legacy exchange using an auto-detected capability profile.
     *
     * <p>The profile is built from the exchange's own {@code supports*()} methods
     * declared in {@link org.investpro.exchange.contracts.ExchangeCapabilities}.
     *
     * @param exchange the legacy exchange to wrap
     * @return migration bridge instance
     */
    public static ExchangeAdapterMigrationBridge wrap(@NotNull Exchange exchange) {
        ExchangeCapability cap = detectCapability(exchange);
        return new ExchangeAdapterMigrationBridge(exchange, cap);
    }

    /**
     * Wraps a legacy exchange with an explicitly provided capability profile.
     *
     * <p>Use this form when the auto-detected profile is incomplete or when you
     * want to override capability flags for a specific deployment.
     *
     * @param exchange   the legacy exchange to wrap
     * @param capability the explicit capability profile
     * @return migration bridge instance
     */
    public static ExchangeAdapterMigrationBridge wrap(
            @NotNull Exchange exchange,
            @NotNull ExchangeCapability capability
    ) {
        return new ExchangeAdapterMigrationBridge(exchange, capability);
    }

    /**
     * Registers this bridge with an {@link ExchangeCapabilityRegistry}.
     *
     * <p>After registration, the exchange is visible to capability queries
     * ({@code findByFeature}, {@code findSupporting}, etc.).
     *
     * @param registry the target registry
     */
    public void registerWith(@NotNull ExchangeCapabilityRegistry registry) {
        registry.register(capability);
        log.info("[MigrationBridge] Registered legacy exchange '{}' with capability registry",
                capability.getExchangeName());
    }

    /** Returns the underlying legacy exchange instance. */
    public Exchange getDelegate() { return delegate; }

    /** Returns the capability profile derived or provided at construction. */
    public ExchangeCapability getCapability() { return capability; }

    /**
     * Caches the latest normalized snapshot for integration with
     * {@link org.investpro.exchange.normalization.MarketDataNormalizationLayer}.
     *
     * @param snapshot the latest snapshot
     */
    public void updateSnapshot(@NotNull NormalizedMarketSnapshot snapshot) {
        this.lastSnapshot = snapshot;
    }

    /**
     * Returns the last cached normalized snapshot, or a stale placeholder if none.
     *
     * @param symbol the symbol this snapshot is for
     * @return current or stale snapshot
     */
    public NormalizedMarketSnapshot lastSnapshot(@NotNull String symbol) {
        NormalizedMarketSnapshot s = lastSnapshot;
        if (s != null) return s;
        return NormalizedMarketSnapshot.stale(capability.getExchangeName(), symbol);
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Detects capabilities from the legacy exchange's contract methods.
     *
     * <p>Maps legacy boolean contract methods to the new {@link ExchangeCapability}
     * model. Fields not expressible via the contract default to sensible values.
     */
    private static ExchangeCapability detectCapability(@NotNull Exchange exchange) {
        String name = exchange.getClass().getSimpleName();
        return ExchangeCapability.builder()
                .exchangeName(name)
                .exchangeId(name.toLowerCase())
                .displayName(name)
                .supportsSpot(exchange.supportsCrypto() || exchange.supportsStocks())
                .supportsForex(exchange.supportsForex())
                .supportsCrypto(exchange.supportsCrypto())
                .supportsDerivatives(exchange.supportsDerivatives())
                .supportsLiveTrading(exchange.supportsLiveTrading())
                .supportsPaperTradingMode(exchange.supportsPaperTradingMode())
                .supportsOrderBook(exchange.supportsOrderBook())
                .supportsStopLossTakeProfit(exchange.supportsStopLossTakeProfit())
                .supportsMarginTrading(exchange.supportsLeverage())
                .supportsWebSocket(exchange.supportsAccountStreaming()
                        || exchange.supportsOrderStreaming()
                        || exchange.supportsTickerStreaming())
                .supportsPositions(exchange.supportsPositions())
                .supportsAccountTrades(exchange.supportsAccountTrades())
                .build();
    }
}
