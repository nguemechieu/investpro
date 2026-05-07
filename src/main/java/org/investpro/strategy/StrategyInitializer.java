package org.investpro.strategy;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.impl.BreakoutStrategy;
import org.investpro.strategy.impl.MeanReversionStrategy;
import org.investpro.strategy.impl.TrendFollowingStrategy;
import org.investpro.strategy.impl.UnifiedStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes and registers all available trading strategies.
 *
 * Startup responsibility:
 * - register legacy concrete strategies
 * - register catalog-driven strategy definitions
 * - avoid instantiating every catalog variant
 *
 * The StrategyRegistry should lazily instantiate UnifiedStrategy variants
 * when a specific strategy name is requested.
 */
@Slf4j
@Getter
@Setter
public final class StrategyInitializer {

    private static boolean initialized = false;

    private StrategyInitializer() {
    }

    public static synchronized void initializeStrategies() {
        if (initialized) {
            log.debug("Strategies already initialized");
            return;
        }

        StrategyRegistry registry = StrategyRegistry.getInstance();

        registerLegacyStrategies(registry);
        registerCatalogDefinitions(registry);
        registerDefaultUnifiedStrategy(registry);

        initialized = true;

        log.info(
                "Strategy initialization complete. instantiatedStrategies={}, definitions={}",
                registry.instantiatedCount(),
                registry.definitionCount()
        );
    }

    private static void registerLegacyStrategies(@NotNull StrategyRegistry registry) {
        registerLegacyStrategy(registry, new TrendFollowingStrategy());
        registerLegacyStrategy(registry, new MeanReversionStrategy());
        registerLegacyStrategy(registry, new BreakoutStrategy());
    }

    private static void registerLegacyStrategy(
            @NotNull StrategyRegistry registry,
            @NotNull TradingStrategy strategy
    ) {
        try {
            strategy.validateConfiguration();
            registry.register(strategy.getMetadata().getStrategyId(), strategy);

            log.info(
                    "Registered legacy strategy: id={}, name={}",
                    strategy.getMetadata().getStrategyId(),
                    strategy.getMetadata().getDisplayName()
            );

        } catch (Exception exception) {
            log.error(
                    "Failed to register legacy strategy: {}",
                    strategy.getClass().getSimpleName(),
                    exception
            );
        }
    }

    /**
     * Register all catalog definitions without creating UnifiedStrategy instances.
     *
     * This mirrors the Python registry pattern:
     * definitions are loaded first, then concrete strategy objects are created lazily.
     */
    private static void registerCatalogDefinitions(@NotNull StrategyRegistry registry) {
        int registered = 0;

        for (StrategyDefinition definition : StrategyCatalog.STRATEGY_DEFINITIONS.values()) {
            if (definition == null || isBlank(definition.getName())) {
                continue;
            }

            try {
                registry.registerDefinition(definition);
                registered++;
            } catch (Exception exception) {
                log.warn(
                        "Failed to register strategy definition: {}",
                        definition.getName(),
                        exception
                );
            }
        }

        log.info("Registered {} catalog strategy definitions", registered);
    }

    /**
     * Register one generic UnifiedStrategy instance.
     *
     * The rest of the variants should be created lazily by StrategyRegistry.
     */
    private static void registerDefaultUnifiedStrategy(@NotNull StrategyRegistry registry) {
        try {
            UnifiedStrategy unifiedStrategy = new UnifiedStrategy("Trend Following");

            registry.register("unified-strategy", unifiedStrategy);

            log.info("Registered default UnifiedStrategy instance");

        } catch (Exception exception) {
            log.error("Failed to register default UnifiedStrategy", exception);
        }
    }

    public static synchronized boolean isInitialized() {
        return initialized;
    }

    public static synchronized void reset() {
        initialized = false;
        log.info("StrategyInitializer reset");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}