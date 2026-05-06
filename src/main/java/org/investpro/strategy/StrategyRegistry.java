package org.investpro.strategy;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.market.AssetClass;
import org.investpro.market.ContractType;
import org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central registry for all trading strategies.
 * Manages strategy registration, lookup, and filtering.
 */
@Slf4j
@Getter
public class StrategyRegistry {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(StrategyRegistry.class);
    private static StrategyRegistry instance;
    private final Map<String, TradingStrategy> strategiesById = new ConcurrentHashMap<>();

    private StrategyRegistry() {
    }

    /**
     * Gets the singleton instance of StrategyRegistry.
     */
    public static synchronized StrategyRegistry getInstance() {
        if (instance == null) {
            instance = new StrategyRegistry();
        }
        return instance;
    }

    /**
     * Registers a trading strategy.
     * Validates that metadata is complete and strategyId is unique.
     *
     * @param strategy the strategy to register
     * @throws IllegalArgumentException if validation fails
     */
    public void register(@NotNull TradingStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy must not be null");

        StrategyMetadata metadata = strategy.getMetadata();
        Objects.requireNonNull(metadata, "strategy metadata must not be null");
        Objects.requireNonNull(metadata.getStrategyId(), "strategy ID must not be null");

        String strategyId = metadata.getStrategyId();
        if (strategiesById.containsKey(strategyId)) {
            throw new IllegalArgumentException("Strategy with ID already registered: " + strategyId);
        }

        try {
            strategy.validateConfiguration();
        } catch (IllegalStateException e) {
            log.error("Strategy {} failed validation: {}", strategyId, e.getMessage());
            throw new IllegalArgumentException("Strategy validation failed: " + e.getMessage(), e);
        }

        strategiesById.put(strategyId, strategy);
        log.info("Registered strategy: {} ({})", metadata.getDisplayName(), strategyId);
    }

    /**
     * Unregisters a strategy by ID.
     */
    public void unregister(@NotNull String strategyId) {
        TradingStrategy removed = strategiesById.remove(strategyId);
        if (removed != null) {
            log.info("Unregistered strategy: {}", strategyId);
        }
    }

    /**
     * Gets all registered strategies.
     */
    @NotNull
    public List<TradingStrategy> getAllStrategies() {
        return new ArrayList<>(strategiesById.values());
    }

    /**
     * Gets all enabled strategies.
     */
    @NotNull
    public List<TradingStrategy> getEnabledStrategies() {
        return strategiesById.values().stream()
                .filter(TradingStrategy::isEnabled)
                .filter(s -> s.getMetadata().isEnabled())
                .collect(Collectors.toList());
    }

    /**
     * Gets a strategy by ID.
     */
    @Nullable
    public TradingStrategy getStrategy(@NotNull String strategyId) {
        return strategiesById.get(strategyId);
    }

    /**
     * Gets strategies by category.
     */
    @NotNull
    public List<TradingStrategy> getStrategiesByCategory(@NotNull StrategyCategory category) {
        return strategiesById.values().stream()
                .filter(s -> s.getMetadata().getCategory() == category)
                .collect(Collectors.toList());
    }

    /**
     * Gets strategies supporting a specific asset class.
     */
    @NotNull
    public List<TradingStrategy> getStrategiesByAssetClass(@NotNull AssetClass assetClass) {
        return strategiesById.values().stream()
                .filter(s -> s.supportsAssetClass(assetClass))
                .collect(Collectors.toList());
    }

    /**
     * Gets strategies supporting a specific timeframe.
     */
    @NotNull
    public List<TradingStrategy> getStrategiesByTimeframe(@NotNull Timeframe timeframe) {
        return strategiesById.values().stream()
                .filter(s -> s.supportsTimeframe(timeframe))
                .collect(Collectors.toList());
    }

    /**
     * Gets strategies compatible with multiple criteria.
     */
    @NotNull
    public List<TradingStrategy> getCompatibleStrategies(
            @NotNull AssetClass assetClass,
            @NotNull ContractType contractType,
            @NotNull Timeframe timeframe) {
        return strategiesById.values().stream()
                .filter(s -> s.supportsAssetClass(assetClass))
                .filter(s -> s.supportsContractType(contractType))
                .filter(s -> s.supportsTimeframe(timeframe))
                .collect(Collectors.toList());
    }

    /**
     * Gets count of registered strategies.
     */
    public int getStrategyCount() {
        return strategiesById.size();
    }

    /**
     * Gets count of enabled strategies.
     */
    public int getEnabledStrategyCount() {
        return getEnabledStrategies().size();
    }

    /**
     * Clears all strategies (useful for testing).
     */
    public void clear() {
        strategiesById.clear();
        log.info("Cleared all registered strategies");
    }
}
