package org.investpro.strategy;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.impl.UnifiedStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
                definition.getBaseName()
        );

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
                strategyName == null || strategyName.isBlank() ? activeName : strategyName
        );

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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}