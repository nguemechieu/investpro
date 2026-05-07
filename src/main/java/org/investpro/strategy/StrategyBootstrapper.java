package org.investpro.strategy;

import lombok.extern.slf4j.Slf4j;

/**
 * StrategyBootstrapper ensures strategies are initialized exactly once at
 * application startup.
 *
 * Responsibilities:
 * - Prevents duplicate initialization
 * - Delegates to StrategyInitializer
 * - Logs success/failure
 * - Validates registrations
 *
 * Usage:
 * 
 * <pre>
 * StrategyBootstrapper.initialize();
 * if (StrategyBootstrapper.isInitialized()) {
 *     // Ready to use strategies
 * }
 * </pre>
 */
@Slf4j
public final class StrategyBootstrapper {
    private static volatile boolean initialized = false;
    private static volatile boolean initializationAttempted = false;
    private static volatile Exception initializationError = null;

    private StrategyBootstrapper() {
        // This is a utility class, do not instantiate
    }

    /**
     * Initialize strategies once during application startup.
     *
     * Thread-safe: only executes once, subsequent calls are no-ops.
     * Can be called from multiple threads without issues.
     */
    public static synchronized void initialize() {
        if (initializationAttempted) {
            if (initialized) {
                log.debug("Strategies already initialized");
            } else if (initializationError != null) {
                log.error("Strategy initialization previously failed: {}",
                        initializationError.getMessage());
            }
            return;
        }

        initializationAttempted = true;

        try {
            log.info("Starting strategy framework initialization...");

            StrategyInitializer.initializeStrategies();

            // Validate that strategies were registered
            StrategyRegistry registry = StrategyRegistry.getInstance();
            int strategyCount = registry.getAllStrategies().size();

            if (strategyCount == 0) {
                throw new IllegalStateException(
                        "No strategies were registered during initialization");
            }

            // Log registered strategies
            log.info("Strategy initialization complete. Registered {} strategies:",
                    strategyCount);
            registry.getAllStrategies()
                    .forEach(strategy -> log.info("  - {} ({})", strategy.getName(), strategy.getId()));

            initialized = true;
            log.info("Strategy framework ready for use");

        } catch (Exception e) {
            initializationError = e;
            log.error("Failed to initialize strategy framework: {}",
                    e.getMessage(), e);
            initialized = false;
        }
    }

    /**
     * Check if strategies have been successfully initialized.
     *
     * @return true if StrategyBootstrapper.initialize() completed successfully
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the error that occurred during initialization (if any).
     *
     * @return Exception that prevented initialization, or null if successful
     */
    public static Exception getInitializationError() {
        return initializationError;
    }

    /**
     * Reset initialization state (useful for testing).
     * NOT for production use.
     */
    static void reset() {
        synchronized (StrategyBootstrapper.class) {
            initialized = false;
            initializationAttempted = false;
            initializationError = null;
            StrategyInitializer.reset();
        }
    }
}
