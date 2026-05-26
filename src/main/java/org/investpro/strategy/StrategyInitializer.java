package org.investpro.strategy;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.spi.PluginRegistry;
import org.investpro.spi.StrategyProvider;
import org.investpro.spi.StrategyProviderContext;
import org.investpro.strategy.impl.UnifiedStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes and registers all available trading strategies.
 *
 * Startup responsibility:
 * - register legacy concrete strategies
 * - register catalog-driven strategy definitions
 * - avoid instantiating every catalog variant
 * <p>
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

        // OPTIMIZATION: Use tiered strategy selection instead of instantiating all
        // ~6,563 strategies upfront.
        // This avoids startup delays:
        // - Stage 1: Generate 3000 candidate strategies
        // - Stage 2: Backtest filter → top 100 by profit factor
        // - Stage 3: Paper trade filter → top 20
        // - Stage 4: Live eligible → top 3 per symbol/timeframe
        //
        // Strategies are progressively instantiated only as they pass each filter.
        // Selection runs asynchronously in background during startup.
        startTieredStrategySelection(registry);

        initialized = true;

        log.info(
                "Strategy initialization complete. definitions={} (tiered selection in progress)",
                registry.definitionCount());
    }

    /**
     * Start tiered strategy selection process asynchronously.
     *
     * Avoids blocking startup by running selection in background thread.
     * Only highest-performing strategies are instantiated.
     */
    private static void startTieredStrategySelection(@NotNull StrategyRegistry registry) {
        try {
            log.info("Starting tiered strategy selection (3000 → 100 → 20 → 3+)...");

            // Get Strategy Lab service for backtesting
            org.investpro.strategy.lab.StrategyLabService strategyLabService = org.investpro.strategy.lab.StrategyLabService
                    .getInstance();

            // Create and start selection service
            StrategySelectionService selectionService = StrategySelectionService.getInstance(registry,
                    strategyLabService);
            selectionService.startTieredSelection();

            log.info("Tiered strategy selection started (running asynchronously)");

        } catch (Exception exception) {
            log.warn("Failed to start tiered strategy selection: {}. Continuing with lazy strategy loading.",
                    exception.getMessage());
            // Non-fatal: strategies will be loaded lazily on demand
        }
    }

    private static void registerLegacyStrategies(@NotNull StrategyRegistry registry) {
        int registered = 0;
        try {
            for (StrategyProvider provider : PluginRegistry.loadDefault().strategyProviders()) {
                if (provider == null || !provider.enabledByDefault()) {
                    continue;
                }
                TradingStrategy strategy = provider.create(new StrategyProviderContext(null, PluginRegistry.loadDefault(), null, java.util.Map.of()));
                if (strategy != null) {
                    registerLegacyStrategy(registry, strategy);
                    registered++;
                }
            }
            log.info("Registered {} strategy provider instances", registered);
        } catch (Exception exception) {
            log.warn("Failed to register strategy providers. Legacy StrategyRegistry definitions remain available.", exception);
        }
    }

    private static void registerLegacyStrategy(
            @NotNull StrategyRegistry registry,
            @NotNull TradingStrategy strategy) {
        try {
            strategy.validateConfiguration();
            registry.register(strategy.getMetadata().getStrategyId(), strategy);

            log.info(
                    "Registered legacy strategy: id={}, name={}",
                    strategy.getMetadata().getStrategyId(),
                    strategy.getMetadata().getDisplayName());

        } catch (Exception exception) {
            log.error(
                    "Failed to register legacy strategy: {}",
                    strategy.getClass().getSimpleName(),
                    exception);
        }
    }

    /**
     * Register all catalog definitions without creating UnifiedStrategy instances.
     *
     * This mirrors the Python registry pattern:
     * definitions are loaded first, then concrete strategy objects are created
     * lazily.
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
                        exception);
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
