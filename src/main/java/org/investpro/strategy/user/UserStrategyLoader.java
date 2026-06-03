package org.investpro.strategy.user;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.api.UserStrategy;
import org.investpro.strategy.impl.UserStrategyAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.jar.JarFile;

/**
 * Loads user-developed strategies from JAR files.
 *
 * Locations:
 * - User strategy JARs should be placed in the "strategies/" directory
 * - Each JAR must have a ServiceLoader configuration file:
 * META-INF/services/org.investpro.strategy.api.UserStrategy
 * - The service file should list implementation classes, one per line
 *
 * Example service file content:
 * com.example.MyEmaStrategy
 * com.example.MyRsiStrategy
 *
 * Behavior:
 * - Creates strategies/ directory if missing
 * - Scans all .jar files in the directory
 * - Uses URLClassLoader to load each JAR
 * - Instantiates all services found via ServiceLoader
 * - Validates each strategy with UserStrategyValidator
 * - Registers valid strategies into StrategyRegistry
 * - Logs detailed information for debugging
 * - Does NOT crash if one strategy fails to load
 */
@Getter
@Setter
@Slf4j
public class UserStrategyLoader {

    private final Path strategiesPath;
    private final Map<String, UserStrategyDescriptor> loadedStrategies = new HashMap<>();
    private final Map<String, String> failedStrategies = new HashMap<>();

    public UserStrategyLoader(@NotNull Path strategiesPath) {
        this.strategiesPath = strategiesPath;
    }

    public UserStrategyLoader(@NotNull String strategiesPath) {
        this(Paths.get(strategiesPath));
    }

    /**
     * Load all user strategies from the strategies directory.
     *
     * @return number of strategies successfully loaded
     */
    public int loadIntoRegistry() {
        try {
            ensureStrategiesDirectoryExists();
            List<File> jarFiles = findStrategyJars();

            log.info("Found {} strategy JARs", jarFiles.size());

            for (File jar : jarFiles) {
                loadStrategyJar(jar);
            }

            logSummary();
            return loadedStrategies.size();

        } catch (Exception e) {
            log.error("Failed to initialize user strategy loader", e);
            return 0;
        }
    }

    private void ensureStrategiesDirectoryExists() throws IOException {
        if (!Files.exists(strategiesPath)) {
            Files.createDirectories(strategiesPath);
            log.info("Created strategies directory: {}", strategiesPath);
        }
    }

    private List<File> findStrategyJars() {
        List<File> jars = new ArrayList<>();

        if (!Files.exists(strategiesPath)) {
            log.warn("Strategies directory does not exist: {}", strategiesPath);
            return jars;
        }

        try (var stream = Files.list(strategiesPath)) {
            stream
                    .filter(p -> p.toString().endsWith(".jar"))
                    .map(Path::toFile)
                    .forEach(jars::add);
        } catch (IOException e) {
            log.error("Error scanning strategies directory", e);
        }

        return jars;
    }

    private void loadStrategyJar(@NotNull File jarFile) {
        try {
            log.info("Loading strategy JAR: {}", jarFile.getName());

            // Verify JAR file is valid
            if (!isValidJarFile(jarFile)) {
                failedStrategies.put(jarFile.getName(), "Invalid or corrupted JAR file");
                return;
            }

            // Create URLClassLoader for this JAR
            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[] { jarFile.toURI().toURL() },
                    Thread.currentThread().getContextClassLoader())) {

                // Use ServiceLoader to find all UserStrategy implementations
                ServiceLoader<UserStrategy> serviceLoader = ServiceLoader.load(
                        UserStrategy.class,
                        classLoader);

                int strategyCount = 0;
                for (UserStrategy strategy : serviceLoader) {
                    loadStrategy(strategy, jarFile);
                    strategyCount++;
                }

                if (strategyCount == 0) {
                    log.warn("No UserStrategy implementations found in JAR: {}", jarFile.getName());
                    failedStrategies.put(jarFile.getName(), "No UserStrategy services found");
                } else {
                    log.info("Loaded {} strategy/strategies from {}", strategyCount, jarFile.getName());
                }

            } catch (Exception e) {
                log.error("Error loading strategies from JAR: {}", jarFile.getName(), e);
                failedStrategies.put(jarFile.getName(), e.getMessage());
            }

        } catch (Exception e) {
            log.error("Unexpected error processing JAR: {}", jarFile.getName(), e);
            failedStrategies.put(jarFile.getName(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private boolean isValidJarFile(@NotNull File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            // If we can open it as a JAR, it's valid
            return true;
        } catch (Exception e) {
            log.warn("Invalid JAR file: {} ({})", jarFile.getName(), e.getMessage());
            return false;
        }
    }

    private void loadStrategy(@NotNull UserStrategy userStrategy, @NotNull File jarFile) {
        try {
            String id = userStrategy.id();
            String name = userStrategy.name();

            log.debug("Discovered user strategy: id={}, name={}", id, name);

            // Validate the strategy
            UserStrategyValidationResult validation = UserStrategyValidator.validate(userStrategy);

            if (!validation.isValid()) {
                log.error("Strategy validation failed for {}: {}", id, validation.getSummary());
                failedStrategies.put(id, validation.getSummary());
                return;
            }

            // Create descriptor
            UserStrategyDescriptor descriptor = UserStrategyDescriptor.builder()
                    .id(id)
                    .name(name)
                    .description(userStrategy.getDescription())
                    .sourceJar(jarFile.getAbsolutePath())
                    .loadedAtEpochMs(System.currentTimeMillis())
                    .status(UserStrategyStatus.LOADED)
                    .validated(true)
                    .warmupBars(userStrategy.requiredWarmupBars())
                    .validationResult(validation)
                    .build();

            // Wrap with adapter
            UserStrategyAdapter adapter = new UserStrategyAdapter(userStrategy);

            // Register into StrategyRegistry
            StrategyRegistry registry = StrategyRegistry.getInstance();
            registry.register(id, adapter);

            loadedStrategies.put(id, descriptor);
            log.info("Successfully loaded user strategy: {} [{}]", name, id);

        } catch (Exception e) {
            log.error("Error loading user strategy", e);
            try {
                failedStrategies.put(userStrategy.id(), e.getMessage());
            } catch (Exception ignored) {
                failedStrategies.put("unknown", e.getMessage());
            }
        }
    }

    public void logSummary() {
        log.info("========== USER STRATEGY LOADER SUMMARY ==========");
        log.info("Successfully loaded: {}", loadedStrategies.size());
        log.info("Failed to load: {}", failedStrategies.size());

        if (!loadedStrategies.isEmpty()) {
            log.info("Loaded strategies:");
            loadedStrategies.forEach((id, desc) -> {
                log.info("  - {} [{}] from {}", desc.getName(), id, desc.getSourceJar());
            });
        }

        if (!failedStrategies.isEmpty()) {
            log.warn("Failed strategies:");
            failedStrategies.forEach((id, reason) -> {
                log.warn("  - {} : {}", id, reason);
            });
        }
    }

    public Map<String, UserStrategyDescriptor> getLoadedStrategies() {
        return new HashMap<>(loadedStrategies);
    }

    public Map<String, String> getFailedStrategies() {
        return new HashMap<>(failedStrategies);
    }

    public int getLoadedCount() {
        return loadedStrategies.size();
    }

    public int getFailedCount() {
        return failedStrategies.size();
    }
}
