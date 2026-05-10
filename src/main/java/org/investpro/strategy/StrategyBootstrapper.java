package org.investpro.strategy;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * StrategyBootstrapper ensures strategies are initialized exactly once at
 * application startup.
 * <p>
 * Responsibilities:
 * - Prevents duplicate initialization
 * - Delegates to StrategyInitializer
 * - Logs success/failure
 * - Validates catalog definitions and instantiated strategies
 * <p>
 * Usage:
 *
 * <pre>
 * StrategyBootstrapper.initialize();
 * if (StrategyBootstrapper.isInitialized()) {
 *     // Ready to use strategies
 * }
 * </pre>
 */
@Getter
@Setter
@Slf4j
public final class StrategyBootstrapper {

    /**
     * -- GETTER --
     * Check if strategies have been successfully initialized.
     */

    private static volatile boolean initialized = false;
    private static volatile boolean initializationAttempted = false;
    /**
     * -- GETTER --
     * Get the error that occurred during initialization, if any.
     */

    private static volatile Exception initializationError = null;

    private StrategyBootstrapper() {
        // Utility class.
    }

    /**
     * Initialize strategies once during application startup.
     * <p>
     * Thread-safe: only executes once. Subsequent calls are no-ops.
     */
    public static synchronized void initialize() {
        if (initializationAttempted) {
            if (initialized) {
                log.debug("Strategy framework already initialized.");
            } else if (initializationError != null) {
                log.error(
                        "Strategy framework initialization previously failed: {}",
                        initializationError.getMessage());
            }

            return;
        }

        initializationAttempted = true;

        try {
            log.info("Starting strategy framework initialization...");

            StrategyInitializer.initializeStrategies();

            // Load user-developed strategies from strategies/ directory
            loadUserStrategies();

            StrategyRegistry registry = StrategyRegistry.getInstance();

            int definitionCount = safeDefinitionCount(registry);
            int instantiatedCount = safeInstantiatedCount(registry);

            if (definitionCount <= 0 && instantiatedCount <= 0) {
                throw new IllegalStateException(
                        "No strategy definitions or strategy instances were registered during initialization.");
            }

            log.info(
                    "Strategy framework initialized. definitions={}, instantiated={}",
                    definitionCount,
                    instantiatedCount);

            logStrategyDefinitions(registry);
            logInstantiatedStrategies(registry);

            // Initialize Strategy Lab backtesting engine
            initializeStrategyLab();

            initialized = true;
            initializationError = null;

            log.info("Strategy framework ready for use.");

        } catch (Exception exception) {
            initializationError = exception;
            initialized = false;

            log.error(
                    "Failed to initialize strategy framework: {}",
                    exception.getMessage(),
                    exception);
        }
    }

    /**
     * Initialize the Strategy Lab backtesting and assignment system.
     */
    private static void initializeStrategyLab() {
        try {
            log.info("Initializing Strategy Lab backtesting engine...");
            // Lazy initialize StrategyLabService singleton
            org.investpro.strategy.lab.StrategyLabService.getInstance();
            log.info("Strategy Lab backtesting engine initialized successfully.");
        } catch (Exception e) {
            log.warn("Failed to initialize Strategy Lab: {}", e.getMessage(), e);
            // Non-fatal: Strategy Lab is optional, continue initialization
        }
    }

    /**
     * Load user-developed strategies from the strategies/ directory.
     *
     * This discovers UserStrategy implementations packaged as JAR files using
     * the ServiceLoader pattern (META-INF/services/).
     *
     * Non-fatal errors are logged but do not stop initialization.
     */
    private static void loadUserStrategies() {
        try {
            log.info("Loading user-developed strategies from strategies/ directory...");

            org.investpro.strategy.user.UserStrategyLoader loader = new org.investpro.strategy.user.UserStrategyLoader(
                    "strategies");

            int loadedCount = loader.loadIntoRegistry();

            if (loadedCount > 0) {
                log.info("Successfully loaded {} user strategy(ies)", loadedCount);
                loader.logSummary();
            } else {
                log.info("No user strategies found in strategies/ directory (optional)");
            }

            if (loader.getFailedCount() > 0) {
                log.warn(
                        "Failed to load {} user strategy(ies): {}",
                        loader.getFailedCount(),
                        loader.getFailedStrategies());
            }

        } catch (Exception e) {
            log.warn(
                    "User strategy loading failed (non-fatal): {}. Continue with built-in strategies only.",
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Check whether initialization has been attempted.
     */
    public static boolean wasInitializationAttempted() {
        return initializationAttempted;
    }

    /**
     * Returns true when initialization failed.
     */
    public static boolean hasInitializationError() {
        return initializationError != null;
    }

    /**
     * Human-readable initialization status.
     */
    public static @NotNull String getStatusSummary() {
        if (initialized) {
            return "Strategy framework initialized.";
        }

        if (initializationError != null) {
            return "Strategy framework failed to initialize: " + initializationError.getMessage();
        }

        if (initializationAttempted) {
            return "Strategy framework initialization attempted but not completed.";
        }

        return "Strategy framework not initialized yet.";
    }

    /**
     * Reset initialization state.
     *
     * Intended for tests only.
     */
    static synchronized void reset() {
        initialized = false;
        initializationAttempted = false;
        initializationError = null;
        StrategyInitializer.reset();
    }

    private static int safeDefinitionCount(StrategyRegistry registry) {
        try {
            return registry.definitionCount();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int safeInstantiatedCount(StrategyRegistry registry) {
        try {
            return registry.instantiatedCount();
        } catch (Exception ignored) {
            try {
                return registry.getStrategies().size();
            } catch (Exception ignoredAgain) {
                return 0;
            }
        }
    }

    private static void logStrategyDefinitions(StrategyRegistry registry) {
        try {
            registry.listStrategyNames();
            if (registry.listStrategyNames().isEmpty()) {
                return;
            }

            log.info("Available strategy definitions:");

            registry.listStrategyNames()
                    .stream()
                    .limit(25)
                    .forEach(name -> log.info("  - {}", name));

            int remaining = registry.listStrategyNames().size() - 25;

            if (remaining > 0) {
                log.info("  ... and {} more strategy definitions", remaining);
            }

        } catch (Exception exception) {
            log.debug("Unable to log strategy definitions: {}", exception.getMessage());
        }
    }

    private static void logInstantiatedStrategies(StrategyRegistry registry) {
        try {
            if (registry.getStrategies().isEmpty()) {
                log.info("No strategy instances loaded yet. Catalog strategies will be instantiated lazily.");
                return;
            }

            log.info("Instantiated strategies:");

            registry.getStrategies().forEach((name, strategy) -> {
                if (strategy == null) {
                    return;
                }

                log.info(
                        "  - {} ({})",
                        safeStrategyName(name),
                        safeStrategyId(strategy));
            });

        } catch (Exception exception) {
            log.debug("Unable to log instantiated strategies: {}", exception.getMessage());
        }
    }

    private static String safeStrategyName(Object strategy) {
        if (strategy == null) {
            return "UNKNOWN";
        }

        String byGetName = invokeString(strategy, "getName");
        if (!byGetName.isBlank()) {
            return byGetName;
        }

        String byName = invokeString(strategy, "name");
        if (!byName.isBlank()) {
            return byName;
        }

        return strategy.getClass().getSimpleName();
    }

    private static String safeStrategyId(Object strategy) {
        if (strategy == null) {
            return "UNKNOWN";
        }

        String byGetId = invokeString(strategy, "getId");
        if (!byGetId.isBlank()) {
            return byGetId;
        }

        String byId = invokeString(strategy, "id");
        if (!byId.isBlank()) {
            return byId;
        }

        String byStrategyId = invokeString(strategy, "getStrategyId");
        if (!byStrategyId.isBlank()) {
            return byStrategyId;
        }

        return strategy.getClass().getName();
    }

    private static String invokeString(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? "" : String.valueOf(value).trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}