package org.investpro.strategy;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for ALL trading strategies — developer plugins and no-code strategies alike.
 *
 * <p>Every strategy that reaches InvestPro's pipeline must be registered here first.
 * The registry is the single source of truth for:
 * <ul>
 *   <li>Backtesting engine — "which strategies can be backtested?"</li>
 *   <li>Strategy Assignment Manager — "which strategies are available for assignment?"</li>
 *   <li>AI Review Engine — "which strategies need AI review?"</li>
 *   <li>UI panels — "what should be displayed in the strategy list?"</li>
 * </ul>
 *
 * <p><strong>CRITICAL:</strong> Registration does NOT grant live trading permission.
 * Strategies must pass the full safety-gate pipeline before live approval.
 * The {@link StrategyDescriptor#isLiveAllowed()} flag is the gate.</p>
 *
 * <p>Thread-safe: all operations use a {@link ConcurrentHashMap}.</p>
 */
@Slf4j
public class StrategyRegistry {

    private static volatile StrategyRegistry instance;

    /** strategyId → TradingStrategy. */
    private final ConcurrentHashMap<String, TradingStrategy> strategies = new ConcurrentHashMap<>();

    /** strategyId → StrategyDescriptor (metadata + status). */
    private final ConcurrentHashMap<String, StrategyDescriptor> descriptors = new ConcurrentHashMap<>();

    private StrategyRegistry() {
        log.info("StrategyRegistry initialised");
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton StrategyRegistry
     */
    public static StrategyRegistry getInstance() {
        StrategyRegistry local = instance;
        if (local == null) {
            synchronized (StrategyRegistry.class) {
                local = instance;
                if (local == null) {
                    local = new StrategyRegistry();
                    instance = local;
                }
            }
        }
        return local;
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers a strategy with a descriptor.
     *
     * @param descriptor the strategy metadata and pipeline status
     * @param strategy   the TradingStrategy implementation
     * @throws IllegalArgumentException if descriptor or strategy is null
     */
    public void register(StrategyDescriptor descriptor, TradingStrategy strategy) {
        if (descriptor == null) throw new IllegalArgumentException("descriptor must not be null");
        if (strategy == null) throw new IllegalArgumentException("strategy must not be null");

        String id = descriptor.getStrategyId();
        descriptors.put(id, descriptor);
        strategies.put(id, strategy);
        log.info("Registered strategy: id={} name='{}' type={}",
                id, descriptor.getName(), descriptor.getStrategyType());
    }

    /**
     * Updates the descriptor for an already-registered strategy (e.g. after validation).
     *
     * @param descriptor updated descriptor; must match an existing strategyId
     * @throws IllegalArgumentException if the strategyId is not registered
     */
    public void updateDescriptor(StrategyDescriptor descriptor) {
        if (!descriptors.containsKey(descriptor.getStrategyId())) {
            throw new IllegalArgumentException(
                    "Strategy not registered: " + descriptor.getStrategyId());
        }
        descriptors.put(descriptor.getStrategyId(), descriptor);
        log.debug("Updated descriptor for strategyId={} status={}",
                descriptor.getStrategyId(), descriptor.getValidationStatus());
    }

    /**
     * Removes a strategy from the registry.
     *
     * @param strategyId the strategy to remove
     */
    public void unregister(String strategyId) {
        if (strategyId == null) return;
        strategies.remove(strategyId);
        StrategyDescriptor removed = descriptors.remove(strategyId);
        if (removed != null) {
            log.info("Unregistered strategy: id={} name='{}'", strategyId, removed.getName());
        }
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Finds a strategy by its ID.
     *
     * @param strategyId the strategy ID
     * @return Optional containing the strategy if found
     */
    public Optional<TradingStrategy> findById(String strategyId) {
        return Optional.ofNullable(strategies.get(strategyId));
    }

    /**
     * Finds the descriptor for a strategy by its ID.
     *
     * @param strategyId the strategy ID
     * @return Optional containing the descriptor if found
     */
    public Optional<StrategyDescriptor> findDescriptorById(String strategyId) {
        return Optional.ofNullable(descriptors.get(strategyId));
    }

    /**
     * Returns all strategies of a given type.
     *
     * @param type the strategy type to filter by
     * @return unmodifiable list of matching strategies
     */
    public List<TradingStrategy> findByType(StrategyType type) {
        List<TradingStrategy> result = new ArrayList<>();
        for (Map.Entry<String, StrategyDescriptor> entry : descriptors.entrySet()) {
            if (entry.getValue().getStrategyType() == type) {
                TradingStrategy s = strategies.get(entry.getKey());
                if (s != null) result.add(s);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns strategies that support a given asset symbol.
     *
     * @param assetSymbol asset symbol, e.g. "BTC"
     * @return unmodifiable list of matching strategies (or all if no asset filter set)
     */
    public List<TradingStrategy> findByAsset(String assetSymbol) {
        List<TradingStrategy> result = new ArrayList<>();
        for (Map.Entry<String, StrategyDescriptor> entry : descriptors.entrySet()) {
            StrategyDescriptor desc = entry.getValue();
            List<String> assets = desc.getSupportedAssets();
            if (assets == null || assets.isEmpty() ||
                    assets.stream().anyMatch(a -> a.equalsIgnoreCase(assetSymbol))) {
                TradingStrategy s = strategies.get(entry.getKey());
                if (s != null) result.add(s);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns all registered strategies.
     *
     * @return unmodifiable collection of all strategies
     */
    public Collection<TradingStrategy> getAll() {
        return Collections.unmodifiableCollection(strategies.values());
    }

    /**
     * Returns all descriptors.
     *
     * @return unmodifiable collection of all descriptors
     */
    public Collection<StrategyDescriptor> getAllDescriptors() {
        return Collections.unmodifiableCollection(descriptors.values());
    }

    /**
     * Returns only strategies cleared for live trading.
     *
     * @return unmodifiable list of live-approved strategies
     */
    public List<TradingStrategy> getLiveApproved() {
        List<TradingStrategy> result = new ArrayList<>();
        for (Map.Entry<String, StrategyDescriptor> entry : descriptors.entrySet()) {
            if (entry.getValue().isLiveAllowed()) {
                TradingStrategy s = strategies.get(entry.getKey());
                if (s != null) result.add(s);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns whether a strategy is registered.
     *
     * @param strategyId the ID to check
     * @return true if registered
     */
    public boolean isRegistered(String strategyId) {
        return strategies.containsKey(strategyId);
    }

    /**
     * Returns the number of registered strategies.
     *
     * @return count
     */
    public int size() {
        return strategies.size();
    }

    /** Clears all registered strategies. Use only in tests. */
    public void clear() {
        strategies.clear();
        descriptors.clear();
        log.warn("StrategyRegistry cleared — all strategies removed");
    }
}
