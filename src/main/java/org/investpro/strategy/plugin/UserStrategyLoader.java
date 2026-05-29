package org.investpro.strategy.plugin;

import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.StrategyDescriptor;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.StrategyType;
import org.investpro.strategy.StrategyValidationStatus;
import org.investpro.strategy.api.UserStrategy;
import org.investpro.strategy.impl.UserStrategyAdapter;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Scans the {@code strategies/} directory for JAR files containing
 * developer-provided {@link UserStrategy} implementations and registers
 * them with the {@link StrategyRegistry}.
 *
 * <p>Discovery uses Java's {@link ServiceLoader} mechanism: each JAR must
 * declare its {@link UserStrategy} implementation(s) in
 * {@code META-INF/services/org.investpro.strategy.api.UserStrategy}.</p>
 *
 * <p>After loading, each strategy is wrapped in a {@link UserStrategyAdapter}
 * (so it implements {@link org.investpro.strategy.TradingStrategy}) and
 * registered with {@code StrategyType.USER_PLUGIN} and
 * {@code StrategyValidationStatus.UNVALIDATED}.</p>
 *
 * <p><strong>CRITICAL:</strong> Loading a plugin does NOT grant live trading
 * permission. Every plugin must pass the full lifecycle:
 * validation → backtest → AI review → paper trading → live approval.</p>
 *
 * <p>This class is not thread-safe. Call {@link #loadAndRegister()} once
 * at startup from the main application thread.</p>
 */
@Slf4j
public class UserStrategyLoader {

    /** Default directory scanned for strategy JARs. */
    private static final String DEFAULT_STRATEGY_DIR = "strategies";

    private final Path strategyDir;
    private final StrategyRegistry registry;

    /** Creates a loader using the default {@code strategies/} directory. */
    public UserStrategyLoader() {
        this(Paths.get(DEFAULT_STRATEGY_DIR), StrategyRegistry.getInstance());
    }

    /**
     * Creates a loader with custom directory and registry (useful for testing).
     *
     * @param strategyDir path to the JAR directory
     * @param registry    the registry to register strategies into
     */
    public UserStrategyLoader(Path strategyDir, StrategyRegistry registry) {
        this.strategyDir = strategyDir;
        this.registry = registry;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Scans the strategy directory for JARs, loads all {@link UserStrategy}
     * implementations via {@link ServiceLoader}, and registers them.
     *
     * @return list of strategy IDs that were successfully loaded and registered
     */
    public List<String> loadAndRegister() {
        List<String> registered = new ArrayList<>();
        File dir = strategyDir.toFile();

        if (!dir.exists() || !dir.isDirectory()) {
            log.info("Strategy directory '{}' does not exist or is not a directory; skipping JAR scan", strategyDir);
            return registered;
        }

        File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            log.info("No JAR files found in '{}'", strategyDir);
            return registered;
        }

        log.info("Scanning {} JAR file(s) in '{}'", jars.length, strategyDir);

        for (File jar : jars) {
            try {
                List<String> loaded = loadJar(jar);
                registered.addAll(loaded);
            } catch (Exception e) {
                log.error("Failed to load JAR '{}': {}", jar.getName(), e.getMessage(), e);
            }
        }

        log.info("Loaded {} user strategy plugin(s) from '{}'", registered.size(), strategyDir);
        return registered;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private List<String> loadJar(File jar) throws Exception {
        List<String> loaded = new ArrayList<>();
        URL jarUrl = jar.toURI().toURL();

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarUrl}, getClass().getClassLoader())) {

            ServiceLoader<UserStrategy> serviceLoader =
                    ServiceLoader.load(UserStrategy.class, classLoader);

            for (UserStrategy userStrategy : serviceLoader) {
                try {
                    String id = userStrategy.getId();
                    if (id == null || id.isBlank()) {
                        log.warn("Skipping strategy from '{}': blank ID", jar.getName());
                        continue;
                    }
                    if (registry.isRegistered(id)) {
                        log.warn("Strategy '{}' already registered; skipping duplicate from '{}'", id, jar.getName());
                        continue;
                    }

                    UserStrategyAdapter adapter = new UserStrategyAdapter(userStrategy);
                    StrategyDescriptor descriptor = buildDescriptor(userStrategy, jar.getName());
                    registry.register(descriptor, adapter);
                    loaded.add(id);
                    log.info("Loaded plugin strategy: id='{}' name='{}' from '{}'",
                            id, userStrategy.getName(), jar.getName());
                } catch (Exception e) {
                    log.error("Error registering strategy from '{}': {}", jar.getName(), e.getMessage(), e);
                }
            }
        }
        return loaded;
    }

    private StrategyDescriptor buildDescriptor(UserStrategy strategy, String jarFileName) {
        return StrategyDescriptor.builder()
                .strategyId(strategy.getId())
                .name(strategy.getName())
                .description(strategy.getDescription())
                .strategyType(StrategyType.USER_PLUGIN)
                .source("JAR:" + jarFileName)
                .version("1.0.0")
                .author("Developer Plugin")
                .warmupBars(strategy.requiredWarmupBars())
                .validationStatus(StrategyValidationStatus.UNVALIDATED)
                .liveAllowed(false)
                .build();
    }
}
