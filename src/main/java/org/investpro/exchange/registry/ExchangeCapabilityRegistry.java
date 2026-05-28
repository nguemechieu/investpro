package org.investpro.exchange.registry;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.ExchangeFeature;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe, cache-backed registry for {@link ExchangeCapability} profiles.
 *
 * <p>Provides fast lookups by exchange name, feature, and venue type.
 * All mutating operations are safe for concurrent use.
 */
@Slf4j
public class ExchangeCapabilityRegistry {

    private final ConcurrentHashMap<String, ExchangeCapability> registry = new ConcurrentHashMap<>();

    // ─── Registration ──────────────────────────────────────────────

    /**
     * Registers a capability profile for the given exchange name.
     *
     * @param exchangeName unique exchange identifier (e.g. "Coinbase", "OANDA")
     * @param capability   the capability profile to associate
     */
    public void register(@NotNull String exchangeName, @NotNull ExchangeCapability capability) {
        Objects.requireNonNull(exchangeName, "exchangeName must not be null");
        Objects.requireNonNull(capability, "capability must not be null");
        registry.put(exchangeName, capability);
        log.info("Registered capability for exchange: {}", exchangeName);
    }

    /**
     * Removes the capability profile for the given exchange name, if present.
     *
     * @param exchangeName the exchange to unregister
     */
    public void unregister(@NotNull String exchangeName) {
        Objects.requireNonNull(exchangeName, "exchangeName must not be null");
        ExchangeCapability removed = registry.remove(exchangeName);
        if (removed != null) {
            log.info("Unregistered capability for exchange: {}", exchangeName);
        }
    }

    // ─── Retrieval ───────────────────────────────────────────────

    /**
     * Returns the capability profile for the given exchange, or empty if not registered.
     *
     * @param exchangeName the exchange identifier to look up
     * @return an {@link Optional} containing the capability, or empty
     */
    public Optional<ExchangeCapability> getCapability(@NotNull String exchangeName) {
        Objects.requireNonNull(exchangeName, "exchangeName must not be null");
        return Optional.ofNullable(registry.get(exchangeName));
    }

    /**
     * Returns an unmodifiable view of all registered capabilities keyed by exchange name.
     *
     * @return unmodifiable map of exchange name → capability
     */
    public Map<String, ExchangeCapability> getAll() {
        return Collections.unmodifiableMap(registry);
    }

    /**
     * Returns the total number of registered exchanges.
     *
     * @return count of registered capabilities
     */
    public int count() {
        return registry.size();
    }

    // ─── Feature Queries ──────────────────────────────────────────

    /**
     * Returns all exchange names that support the given feature.
     *
     * @param feature the feature to query
     * @return list of exchange names supporting the feature
     */
    public List<String> getSupportedExchanges(@NotNull ExchangeFeature feature) {
        Objects.requireNonNull(feature, "feature must not be null");
        return registry.entrySet().stream()
                .filter(e -> e.getValue().supports(feature))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Returns all capability profiles that support the given feature.
     *
     * @param feature the feature to filter by
     * @return list of matching capability profiles
     */
    public List<ExchangeCapability> findByFeature(@NotNull ExchangeFeature feature) {
        Objects.requireNonNull(feature, "feature must not be null");
        return registry.values().stream()
                .filter(cap -> cap.supports(feature))
                .collect(Collectors.toList());
    }

    /**
     * Returns all capability profiles that support ALL of the specified features.
     *
     * @param features one or more features that must all be supported
     * @return list of capability profiles supporting every specified feature
     */
    public List<ExchangeCapability> findSupporting(@NotNull ExchangeFeature... features) {
        Objects.requireNonNull(features, "features must not be null");
        return registry.values().stream()
                .filter(cap -> Arrays.stream(features).allMatch(cap::supports))
                .collect(Collectors.toList());
    }

    // ─── Venue Queries ─────────────────────────────────────────────

    /**
     * Returns all capability profiles for exchanges that support spot trading.
     *
     * @return list of spot-capable exchange profiles
     */
    public List<ExchangeCapability> findSpotExchanges() {
        return findByFeature(ExchangeFeature.SPOT_TRADING);
    }

    /**
     * Returns all capability profiles for exchanges that support forex trading.
     *
     * @return list of forex-capable exchange profiles
     */
    public List<ExchangeCapability> findForexExchanges() {
        return findByFeature(ExchangeFeature.FOREX_TRADING);
    }

    /**
     * Returns all capability profiles for exchanges that support crypto trading.
     *
     * @return list of crypto-capable exchange profiles
     */
    public List<ExchangeCapability> findCryptoExchanges() {
        return findByFeature(ExchangeFeature.CRYPTO_TRADING);
    }

    /**
     * Returns all capability profiles for exchanges that support native WebSocket streaming.
     *
     * @return list of WebSocket-capable exchange profiles
     */
    public List<ExchangeCapability> findWebSocketExchanges() {
        return findByFeature(ExchangeFeature.NATIVE_WEBSOCKET);
    }
}
