package org.investpro.strategy;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.impl.BreakoutStrategy;
import org.investpro.strategy.impl.MeanReversionStrategy;
import org.investpro.strategy.impl.TrendFollowingStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * Initializes and registers all available trading strategies.
 * Should be called during application startup.
 */
@Slf4j
@Getter
@Setter
public class StrategyInitializer {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(StrategyInitializer.class);

    private static boolean initialized = false;

    public static synchronized void initializeStrategies() {
        if (initialized) {
            log.debug("Strategies already initialized");
            return;
        }

        StrategyRegistry registry = StrategyRegistry.getInstance();

        // Register built-in strategies
        registerTrendFollowingStrategy(registry);
        registerMeanReversionStrategy(registry);
        registerBreakoutStrategy(registry);

        initialized = true;
        log.info("Strategy initialization complete. Total strategies: {}", registry.getAllStrategies().size());
    }

    private static void registerTrendFollowingStrategy(@NotNull StrategyRegistry registry) {
        try {
            TradingStrategy strategy = new TrendFollowingStrategy();
            registry.register(strategy);
            log.info("Registered TrendFollowingStrategy");
        } catch (Exception e) {
            log.error("Failed to register TrendFollowingStrategy", e);
        }
    }

    private static void registerMeanReversionStrategy(@NotNull StrategyRegistry registry) {
        try {
            TradingStrategy strategy = new MeanReversionStrategy();
            registry.register(strategy);
            log.info("Registered MeanReversionStrategy");
        } catch (Exception e) {
            log.error("Failed to register MeanReversionStrategy", e);
        }
    }

    private static void registerBreakoutStrategy(@NotNull StrategyRegistry registry) {
        try {
            TradingStrategy strategy = new BreakoutStrategy();
            registry.register(strategy);
            log.info("Registered BreakoutStrategy");
        } catch (Exception e) {
            log.error("Failed to register BreakoutStrategy", e);
        }
    }

    public static void reset() {
        initialized = false;
    }
}
