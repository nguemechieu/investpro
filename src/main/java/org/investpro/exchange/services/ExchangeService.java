package org.investpro.exchange.services;

import org.investpro.exchange.contracts.ExchangeIdentity;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.AuthCheckResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central coordination layer for all exchange adapters.
 *
 * <p>
 * The UI and trading services call this service, NOT exchange adapters
 * directly.
 * This enables:
 * <ul>
 * <li>Consistent credential management</li>
 * <li>Capability-aware behavior</li>
 * <li>Unified diagnostics</li>
 * <li>Future adapter swapping/fallback</li>
 * </ul>
 */
public class ExchangeService {
    private static final Logger logger = LoggerFactory.getLogger(ExchangeService.class);

    private final Map<String, ExchangeIdentity> adapters = new ConcurrentHashMap<>();

    public ExchangeService() {
        logger.info("ExchangeService initialized");
    }

    // ==================== Adapter Registration ====================

    /**
     * Register an exchange adapter by name.
     *
     * @param exchangeName Unique exchange identifier (e.g., "Coinbase", "OANDA")
     * @param adapter      The adapter implementation
     */
    public void register(@NotNull String exchangeName, @NotNull ExchangeIdentity adapter) {
        if (exchangeName == null || exchangeName.isBlank()) {
            throw new IllegalArgumentException("exchangeName must not be blank");
        }
        if (adapter == null) {
            throw new IllegalArgumentException("adapter must not be null");
        }
        adapters.put(exchangeName, adapter);
        logger.info("Registered exchange adapter: {}", exchangeName);
    }

    /**
     * Unregister an adapter.
     */
    public void unregister(@NotNull String exchangeName) {
        adapters.remove(exchangeName);
        logger.info("Unregistered exchange adapter: {}", exchangeName);
    }

    // ==================== Adapter Access ====================

    /**
     * Get an adapter by name.
     *
     * @throws IllegalArgumentException if adapter not found
     */
    @NotNull
    public ExchangeIdentity getAdapter(@NotNull String exchangeName) {
        ExchangeIdentity adapter = adapters.get(exchangeName);
        if (adapter == null) {
            throw new IllegalArgumentException("Exchange adapter not found: " + exchangeName);
        }
        return adapter;
    }

    /**
     * Get an adapter by name, wrapped in Optional.
     */
    @NotNull
    public Optional<ExchangeIdentity> getAdapterOptional(@NotNull String exchangeName) {
        return Optional.ofNullable(adapters.get(exchangeName));
    }

    /**
     * Get all registered exchange names.
     */
    @NotNull
    public List<String> getAvailableExchanges() {
        return new ArrayList<>(adapters.keySet());
    }

    /**
     * Check if an adapter is registered.
     */
    public boolean isRegistered(@NotNull String exchangeName) {
        return adapters.containsKey(exchangeName);
    }

    // ==================== Capability Queries ====================

    /**
     * Get the capability profile of an exchange.
     *
     * @throws IllegalArgumentException if adapter not found
     */
    @NotNull
    public ExchangeCapability getCapability(@NotNull String exchangeName) {
        return getAdapter(exchangeName).getCapability();
    }

    /**
     * Get all capability profiles.
     */
    @NotNull
    public Map<String, ExchangeCapability> getAllCapabilities() {
        Map<String, ExchangeCapability> result = new LinkedHashMap<>();
        for (var entry : adapters.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getCapability());
        }
        return result;
    }

    // ==================== Authentication ====================

    /**
     * Check authentication for an exchange.
     *
     * @throws IllegalArgumentException if adapter not found
     */
    @NotNull
    public AuthCheckResult checkAuthentication(@NotNull String exchangeName) {
        logger.info("Checking authentication for exchange: {}", exchangeName);
        AuthCheckResult result = getAdapter(exchangeName).checkAuthentication();
        logger.info("Auth check result for {}: success={}, httpStatus={}", exchangeName, result.isSuccess(),
                result.getHttpStatus());
        return result;
    }

    /**
     * Check authentication for all registered exchanges.
     */
    @NotNull
    public Map<String, AuthCheckResult> checkAllAuthentication() {
        Map<String, AuthCheckResult> results = new LinkedHashMap<>();
        for (String name : getAvailableExchanges()) {
            try {
                results.put(name, checkAuthentication(name));
            } catch (Exception e) {
                logger.warn("Auth check failed for {}: {}", name, e.getMessage());
            }
        }
        return results;
    }

    // ==================== Statistics ====================

    /**
     * Get count of registered adapters.
     */
    public int getAdapterCount() {
        return adapters.size();
    }
}
