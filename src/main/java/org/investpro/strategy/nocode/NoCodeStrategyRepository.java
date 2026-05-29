package org.investpro.strategy.nocode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * JSON-based persistence layer for {@link NoCodeStrategyDefinition} objects.
 *
 * <p>Strategies are stored as individual JSON files in
 * {@code ~/.investpro/strategies/nocode/} using the strategy's ID as the
 * filename ({@code <strategyId>.json}).</p>
 *
 * <p>All I/O is synchronised at the instance level. For production use from
 * multiple threads, create one shared instance or use external synchronisation.</p>
 *
 * <p>This class is Jackson-based; ensure {@code jackson-databind} and
 * {@code jackson-datatype-jsr310} are on the classpath.</p>
 */
@Slf4j
public class NoCodeStrategyRepository {

    /** Base directory: {@code ~/.investpro/strategies/nocode/} */
    private static final String BASE_DIR = System.getProperty("user.home")
            + File.separator + ".investpro"
            + File.separator + "strategies"
            + File.separator + "nocode";

    private final Path storageDir;
    private final ObjectMapper mapper;

    /** Creates a repository using the default storage directory. */
    public NoCodeStrategyRepository() {
        this(Paths.get(BASE_DIR));
    }

    /**
     * Creates a repository using a custom storage directory (useful for testing).
     *
     * @param storageDir path to the directory where JSON files are stored
     */
    public NoCodeStrategyRepository(Path storageDir) {
        this.storageDir = storageDir;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ensureDirectoryExists();
    }

    // =========================================================================
    // CRUD operations
    // =========================================================================

    /**
     * Persists a strategy definition. Creates a new file or overwrites an
     * existing one with the same ID.
     *
     * @param def the definition to save
     * @throws RuntimeException if writing fails
     */
    public synchronized void save(NoCodeStrategyDefinition def) {
        if (def == null) throw new IllegalArgumentException("Strategy definition must not be null");
        Path file = fileFor(def.getStrategyId());
        try {
            mapper.writeValue(file.toFile(), def);
            log.info("Saved no-code strategy '{}' to {}", def.getName(), file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save strategy '" + def.getName() + "'", e);
        }
    }

    /**
     * Loads a strategy by its ID.
     *
     * @param strategyId the strategy ID
     * @return an {@link Optional} containing the strategy, or empty if not found
     */
    public synchronized Optional<NoCodeStrategyDefinition> findById(String strategyId) {
        if (strategyId == null || strategyId.isBlank()) return Optional.empty();
        Path file = fileFor(strategyId);
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(file.toFile(), NoCodeStrategyDefinition.class));
        } catch (IOException e) {
            log.error("Failed to load strategy '{}': {}", strategyId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Loads all persisted no-code strategies.
     *
     * @return unmodifiable list of all definitions; empty if none found
     */
    public synchronized List<NoCodeStrategyDefinition> findAll() {
        File dir = storageDir.toFile();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return Collections.emptyList();
        List<NoCodeStrategyDefinition> result = new ArrayList<>();
        for (File f : files) {
            try {
                result.add(mapper.readValue(f, NoCodeStrategyDefinition.class));
            } catch (IOException e) {
                log.warn("Could not load strategy from {}: {}", f.getName(), e.getMessage());
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Deletes a strategy by its ID.
     *
     * @param strategyId the strategy ID to delete
     * @return true if the file was deleted, false if it did not exist
     */
    public synchronized boolean delete(String strategyId) {
        if (strategyId == null || strategyId.isBlank()) return false;
        Path file = fileFor(strategyId);
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) log.info("Deleted no-code strategy: {}", strategyId);
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete strategy '{}': {}", strategyId, e.getMessage());
            return false;
        }
    }

    /**
     * Exports a strategy to an arbitrary target path as JSON.
     *
     * @param strategyId the strategy to export
     * @param targetPath full path to the destination file
     * @return true on success
     */
    public synchronized boolean export(String strategyId, Path targetPath) {
        Optional<NoCodeStrategyDefinition> def = findById(strategyId);
        if (def.isEmpty()) {
            log.warn("Cannot export: strategy '{}' not found", strategyId);
            return false;
        }
        try {
            mapper.writeValue(targetPath.toFile(), def.get());
            log.info("Exported strategy '{}' to {}", strategyId, targetPath);
            return true;
        } catch (IOException e) {
            log.error("Export failed for '{}': {}", strategyId, e.getMessage());
            return false;
        }
    }

    /**
     * Imports a strategy from an external JSON file. If a strategy with the same
     * ID already exists, it is overwritten.
     *
     * @param sourcePath path to the source JSON file
     * @return the imported definition
     * @throws RuntimeException if reading or saving fails
     */
    public synchronized NoCodeStrategyDefinition importFrom(Path sourcePath) {
        try {
            NoCodeStrategyDefinition def = mapper.readValue(sourcePath.toFile(),
                    NoCodeStrategyDefinition.class);
            save(def);
            log.info("Imported strategy '{}' from {}", def.getName(), sourcePath);
            return def;
        } catch (IOException e) {
            throw new RuntimeException("Failed to import strategy from " + sourcePath, e);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Path fileFor(String strategyId) {
        return storageDir.resolve(strategyId + ".json");
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            log.error("Cannot create no-code strategy directory '{}': {}", storageDir, e.getMessage());
        }
    }

    /** @return the storage directory path. */
    public Path getStorageDir() {
        return storageDir;
    }
}
