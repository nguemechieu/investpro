package org.investpro.strategy;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.impl.UnifiedStrategy;
import org.investpro.strategy.impl.UserStrategyAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry for catalog-driven trading strategies.
 *
 * This registry stores StrategyDefinition objects first and lazily creates
 * UnifiedStrategy instances only when requested.
 *
 * This mirrors the Python strategy registry pattern:
 * - register built-in definitions
 * - lazy instantiate selected strategy
 * - support aliases
 * - track active strategy
 * - configure parameters dynamically
 */
@Slf4j
@Getter
public final class StrategyRegistry {

    private static volatile StrategyRegistry instance;

    /**
     * Instantiated strategies, keyed by normalized strategy name.
     */
    private final Map<String, TradingStrategy> strategies = new LinkedHashMap<>();

    /**
     * Strategy catalog definitions, keyed by normalized strategy name.
     */
    private final Map<String, StrategyDefinition> definitions = new LinkedHashMap<>();

    /**
     * Current active strategy variant name.
     */
    private String activeName;

    /**
     * Fallback strategy if nothing else can be resolved.
     */
    private final UnifiedStrategy defaultStrategy = new UnifiedStrategy("Trend Following");

    private StrategyRegistry() {
        registerBuiltInStrategies();
    }

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

    private void registerBuiltInStrategies() {
        for (StrategyDefinition definition : StrategyCatalog.STRATEGY_DEFINITIONS.values()) {
            if (definition == null || isBlank(definition.getName())) {
                continue;
            }

            String normalized = StrategyCatalog.normalizeStrategyName(definition.getName());

            definitions.putIfAbsent(normalized, definition);

            if (activeName == null) {
                activeName = normalized;
            }
        }

        log.info("StrategyRegistry initialized with {} strategy definitions", definitions.size());
    }

    /**
     * Registers a concrete strategy instance.
     *
     * Useful for custom strategies that are not catalog-driven.
     */
    public synchronized void register(@NotNull String name, @NotNull TradingStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy must not be null");

        String normalized = StrategyCatalog.normalizeStrategyName(name);

        // Check for duplicate registration (especially important for user strategies)
        if (strategies.containsKey(normalized) || definitions.containsKey(normalized)) {
            log.warn(
                    "Strategy with ID '{}' is already registered. Skipping duplicate registration.",
                    normalized);
            return;
        }

        StrategyDefinition definition = StrategyCatalog.definition(normalized);
        definitions.put(normalized, definition);
        strategies.put(normalized, strategy);

        if (activeName == null) {
            activeName = normalized;
        }

        log.info("Registered strategy instance: {}", normalized);
    }

    /**
     * Registers a definition without instantiating the strategy yet.
     */
    public synchronized void registerDefinition(@NotNull StrategyDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");

        String normalized = StrategyCatalog.normalizeStrategyName(definition.getName());

        definitions.put(normalized, definition);

        if (activeName == null) {
            activeName = normalized;
        }

        log.info("Registered strategy definition: {}", normalized);
    }

    /**
     * Gets a strategy by name or alias.
     *
     * If the strategy has not been instantiated yet, this lazily creates it.
     */
    @Nullable
    public synchronized TradingStrategy getStrategy(@Nullable String name) {
        String normalized = StrategyCatalog.normalizeStrategyName(name);

        TradingStrategy existing = strategies.get(normalized);

        if (existing != null) {
            return existing;
        }

        return instantiateStrategy(normalized);
    }

    /**
     * Alias for compatibility with older code.
     */
    @Nullable
    public TradingStrategy get(@Nullable String name) {
        return getStrategy(name);
    }

    /**
     * Instantiates a catalog-driven UnifiedStrategy for the selected variant.
     */
    @Nullable
    private TradingStrategy instantiateStrategy(@NotNull String name) {
        StrategyDefinition definition = definitions.get(name);

        if (definition == null) {
            log.warn("Strategy definition not found: {}", name);
            return null;
        }

        UnifiedStrategy strategy = new UnifiedStrategy(definition.getName());
        strategy.applyParameters(definition.getParameters());

        strategies.put(name, strategy);

        log.debug(
                "Instantiated strategy: name={}, baseName={}",
                definition.getName(),
                definition.getBaseName());

        return strategy;
    }

    /**
     * Lists all available strategy names, including lazy definitions.
     */
    public List<String> list() {
        return definitions.keySet().stream().toList();
    }

    public List<String> listStrategyNames() {
        return list();
    }

    public Collection<TradingStrategy> getEnabledStrategies() {
        // Instantiate only the active strategy by default if nothing is loaded yet.
        if (strategies.isEmpty() && activeName != null) {
            getStrategy(activeName);
        }

        return strategies.values()
                .stream()
                .filter(Objects::nonNull)
                .filter(TradingStrategy::isEnabled)
                .toList();
    }

    public synchronized void setActive(@Nullable String name) {
        String normalized = StrategyCatalog.normalizeStrategyName(name);

        if (definitions.containsKey(normalized) || strategies.containsKey(normalized)) {
            activeName = normalized;
            log.info("Active strategy set to {}", activeName);
            return;
        }

        log.warn("Cannot set active strategy. Unknown strategy: {}", name);
    }

    public synchronized TradingStrategy configure(@Nullable String strategyName, @Nullable StrategyParameters params) {
        String targetName = StrategyCatalog.normalizeStrategyName(
                strategyName == null || strategyName.isBlank() ? activeName : strategyName);

        setActive(targetName);

        TradingStrategy target = resolveStrategy(targetName);

        if (target instanceof UnifiedStrategy unifiedStrategy) {
            unifiedStrategy.setStrategyName(targetName);

            if (params != null) {
                unifiedStrategy.applyParameters(params);
            }
        }

        return target;
    }

    public TradingStrategy resolveStrategy(@Nullable String strategyName) {
        String normalized = strategyName == null || strategyName.isBlank()
                ? null
                : StrategyCatalog.normalizeStrategyName(strategyName);

        if (normalized != null) {
            TradingStrategy selected = getStrategy(normalized);

            if (selected != null) {
                return selected;
            }
        }

        if (activeName != null) {
            TradingStrategy active = getStrategy(activeName);

            if (active != null) {
                return active;
            }
        }

        if (!definitions.isEmpty()) {
            String firstName = definitions.keySet().iterator().next();
            TradingStrategy first = getStrategy(firstName);

            if (first != null) {
                return first;
            }
        }

        return defaultStrategy;
    }

    public StrategySignal generateSignal(@NotNull StrategyContext context, @Nullable String strategyName) {
        TradingStrategy strategy = resolveStrategy(strategyName);
        return strategy.generateSignal(context);
    }

    public StrategySignal generateSignal(@NotNull StrategyContext context) {
        return generateSignal(context, activeName);
    }

    public boolean contains(@Nullable String name) {
        String normalized = StrategyCatalog.normalizeStrategyName(name);
        return definitions.containsKey(normalized) || strategies.containsKey(normalized);
    }

    public int definitionCount() {
        return definitions.size();
    }

    public int instantiatedCount() {
        return strategies.size();
    }

    public void clearInstantiatedStrategies() {
        strategies.clear();
        log.info("Cleared instantiated strategies. Definitions remain loaded: {}", definitions.size());
    }

    // =========================================================================
    // User Strategy Management
    // =========================================================================

    /**
     * Find a strategy by its ID.
     *
     * @param strategyId the strategy ID to search for
     * @return Optional containing the strategy if found, empty otherwise
     */
    public Optional<TradingStrategy> findById(@NotNull String strategyId) {
        Objects.requireNonNull(strategyId, "strategyId must not be null");

        String normalized = StrategyCatalog.normalizeStrategyName(strategyId);

        return Optional.ofNullable(strategies.get(normalized))
                .or(() -> Optional.ofNullable(instantiateStrategy(normalized)));
    }

    /**
     * Get all registered strategies (both built-in and user-developed).
     *
     * @return List of all TradingStrategy instances
     */
    public List<TradingStrategy> getAllStrategies() {
        return strategies.values()
                .stream()
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Get only user-developed strategies (UserStrategyAdapter instances).
     *
     * @return List of user strategies only
     */
    public List<TradingStrategy> getUserStrategies() {
        return strategies.values()
                .stream()
                .filter(Objects::nonNull)
                .filter(strategy -> strategy instanceof UserStrategyAdapter)
                .toList();
    }

    /**
     * Check if a strategy ID is already registered (duplicate check).
     *
     * @param strategyId the ID to check
     * @return true if already registered, false otherwise
     */
    public synchronized boolean hasDuplicate(@NotNull String strategyId) {
        Objects.requireNonNull(strategyId, "strategyId must not be null");

        String normalized = StrategyCatalog.normalizeStrategyName(strategyId);

        return strategies.containsKey(normalized) || definitions.containsKey(normalized);
    }

    /**
     * Unregister a strategy from the registry.
     *
     * Used to disable or remove strategies (e.g., on validation failure).
     *
     * @param strategyId the ID of the strategy to remove
     * @return true if successfully unregistered, false if not found
     */
    public synchronized boolean unregister(@NotNull String strategyId) {
        Objects.requireNonNull(strategyId, "strategyId must not be null");

        String normalized = StrategyCatalog.normalizeStrategyName(strategyId);

        boolean removedFromStrategies = strategies.remove(normalized) != null;
        boolean removedFromDefinitions = definitions.remove(normalized) != null;

        if (removedFromStrategies || removedFromDefinitions) {
            log.info("Unregistered strategy: {}", normalized);

            // Reset active strategy if we just unregistered it
            if (normalized.equals(activeName)) {
                activeName = definitions.isEmpty() && strategies.isEmpty()
                        ? null
                        : definitions.keySet().stream().findFirst().orElse(null);
                log.info("Reset active strategy to: {}", activeName);
            }

            return true;
        }

        log.warn("Cannot unregister strategy '{}' - not found in registry", normalized);
        return false;
    }

    /**
     * Instantiates all registered strategy definitions upfront.
     *
     * This ensures all strategies are "wired" and available for use by the StrategyEngine
     * rather than being lazily instantiated on-demand.
     *
     * @return The count of successfully instantiated strategies
     */
    public synchronized int instantiateAllStrategies() {
        int instantiatedCount = 0;
        List<String> definitionNames = new ArrayList<>(definitions.keySet());

        for (String name : definitionNames) {
            try {
                TradingStrategy strategy = getStrategy(name);

                if (strategy != null && !strategies.containsKey(name)) {
                    strategies.put(name, strategy);
                    instantiatedCount++;

                    log.debug("Instantiated strategy: {}", name);
                }
            } catch (Exception exception) {
                log.warn("Failed to instantiate strategy '{}': {}", name, exception.getMessage());
            }
        }

        log.info("Instantiated {} strategies for multi-strategy consensus", instantiatedCount);
        return instantiatedCount;
    }

    /**
     * Get the total count of instantiated strategies.
     */
    public int instantiatedCount() {
        return strategies.size();
    }

    /**
     * Get the total count of registered definitions.
     */
    public int definitionCount() {
        return definitions.size();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}