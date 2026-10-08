package org.investpro.exchange.migration_bridge;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.registry.ExchangeCapabilityRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

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
 *   registry.register(bridge.getCapability().getExchangeName(), bridge.getCapability());
 * }</pre>
 *
 * <h3>Backward compatibility guarantee</h3>
 * <p>This class is additive only. It holds a reference to the original
 * {@code Exchange} instance; all existing callers of the original instance
 * continue to work unchanged.
 */
@Slf4j
public final class ExchangeAdapterMigrationBridge {

    /**
     * -- GETTER --
     * Returns the underlying legacy exchange instance.
     */
    @Getter
    private final Exchange delegate;
    /**
     * -- GETTER --
     * Returns the capability profile derived or provided at construction.
     */
    @Getter
    private final ExchangeCapability capability;


    private ExchangeAdapterMigrationBridge(
            @NotNull Exchange delegate,
            @NotNull ExchangeCapability capability
    ) {
        this.delegate = Objects.requireNonNull(delegate, "exchange must not be null");
        this.capability = Objects.requireNonNull(capability, "capability must not be null");
    }

    /**
     * Wraps a legacy exchange using an auto-detected capability profile.
     *
     * <p>Uses the exchange's declared complete profile, preserving endpoint,
     * market, order, authentication, and transport metadata.
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



    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Detects capabilities from the legacy exchange's declared profile.
     *
     * <p>Streaming support does not imply WebSocket transport, and order-book
     * support does not imply full depth. Keep these distinctions as declared by
     * the adapter. Static support does not grant account or product permission.
     */
    private static ExchangeCapability detectCapability(@NotNull Exchange exchange) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        return Objects.requireNonNull(exchange.getCapability(), "exchange capability profile must not be null")
                .toBuilder()
                .exchangeName(exchange.getName())
                .exchangeId(exchange.getExchangeId())
                .displayName(exchange.getDisplayName())
                .build();
    }
}
