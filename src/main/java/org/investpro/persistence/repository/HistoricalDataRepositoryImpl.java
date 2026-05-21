package org.investpro.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File-based implementation of HistoricalDataRepository.
 * <p>
 * Stores historical candle data in JSON format for persistent access
 * across sessions. Used by backtesting to ensure consistent historical data.
 */
@Slf4j
public class HistoricalDataRepositoryImpl implements HistoricalDataRepository {

    private static final String DATA_DIR = "data/historical";
    private static final String FILE_EXTENSION = ".json";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static volatile HistoricalDataRepositoryImpl instance;

    private final ObjectMapper objectMapper;
    private final Map<String, List<CandleData>> memoryCache;

    public HistoricalDataRepositoryImpl() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.memoryCache = new ConcurrentHashMap<>();

        try {
            Files.createDirectories(Paths.get(DATA_DIR));
            log.info("Historical data directory initialized: {}", DATA_DIR);
        } catch (IOException exception) {
            log.error("Failed to create historical data directory", exception);
        }
    }

    public static HistoricalDataRepository getInstance() {
        HistoricalDataRepositoryImpl local = instance;

        if (local == null) {
            synchronized (HistoricalDataRepositoryImpl.class) {
                local = instance;

                if (local == null) {
                    local = new HistoricalDataRepositoryImpl();
                    instance = local;
                }
            }
        }

        return local;
    }

    @Override
    public void saveHistoricalData(
            TradePair pair,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String timeframe,
            List<CandleData> data
    ) throws SQLException {
        validateRequest(pair, startTime, endTime, timeframe);

        if (data == null || data.isEmpty()) {
            log.warn("No historical data to save for {} {}", displayPair(pair), timeframe);
            return;
        }

        try {
            List<CandleData> sortedData = mergeCandles(pair, timeframe, data);

            String fileName = generateFileName(pair, startTime, endTime, timeframe);
            Path filePath = Paths.get(DATA_DIR, fileName);

            String cacheKey = generateCacheKey(pair, timeframe);
            memoryCache.put(cacheKey, sortedData);

            String jsonData = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(sortedData);

            Files.writeString(filePath, jsonData, StandardCharsets.UTF_8);

            log.info(
                    "Saved {} historical candles for {} {} from {} to {}",
                    sortedData.size(),
                    displayPair(pair),
                    timeframe,
                    startTime,
                    endTime
            );

        } catch (IOException exception) {
            throw new SQLException(
                    "Failed to save historical data for " + displayPair(pair) + " " + timeframe,
                    exception
            );
        }
    }

    @Override
    public Optional<List<CandleData>> getHistoricalData(
            TradePair pair,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String timeframe
    ) throws SQLException {
        validateRequest(pair, startTime, endTime, timeframe);

        String cacheKey = generateCacheKey(pair, timeframe);

        List<CandleData> cachedData = memoryCache.get(cacheKey);

        if (cachedData != null && !cachedData.isEmpty()) {
            List<CandleData> filtered = filterByDateRange(cachedData, startTime, endTime);

            if (!filtered.isEmpty()) {
                return Optional.of(filtered);
            }
        }

        List<CandleData> loaded = loadBestMatchingDataFromDisk(pair, startTime, endTime, timeframe);

        if (loaded.isEmpty()) {
            return Optional.empty();
        }

        loaded = loaded.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(this::timestampOf))
                .collect(Collectors.toCollection(ArrayList::new));

        memoryCache.put(cacheKey, loaded);

        List<CandleData> filtered = filterByDateRange(loaded, startTime, endTime);

        if (filtered.isEmpty()) {
            return Optional.empty();
        }

        log.info(
                "Loaded {} historical candles for {} {}",
                filtered.size(),
                displayPair(pair),
                timeframe
        );

        return Optional.of(filtered);
    }

    @Override
    public boolean hasData(
            TradePair pair,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String timeframe
    ) throws SQLException {
        validateRequest(pair, startTime, endTime, timeframe);

        String cacheKey = generateCacheKey(pair, timeframe);

        List<CandleData> cached = memoryCache.get(cacheKey);

        if (cached != null && !filterByDateRange(cached, startTime, endTime).isEmpty()) {
            return true;
        }

        return !findMatchingFiles(pair, timeframe).isEmpty();
    }

    @Override
    public void deleteHistoricalData(
            TradePair pair,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String timeframe
    ) throws SQLException {
        validateRequest(pair, startTime, endTime, timeframe);

        String exactFileName = generateFileName(pair, startTime, endTime, timeframe);
        Path exactPath = Paths.get(DATA_DIR, exactFileName);

        try {
            if (Files.exists(exactPath)) {
                Files.delete(exactPath);
                log.info("Deleted historical data file: {}", exactPath);
            }

            String cacheKey = generateCacheKey(pair, timeframe);
            memoryCache.remove(cacheKey);

        } catch (IOException exception) {
            throw new SQLException(
                    "Failed to delete historical data for " + displayPair(pair) + " " + timeframe,
                    exception
            );
        }
    }

    @Override
    public void clearAll() throws SQLException {
        try {
            memoryCache.clear();

            Path dataDir = Paths.get(DATA_DIR);

            if (!Files.exists(dataDir)) {
                return;
            }

            try (Stream<Path> paths = Files.walk(dataDir)) {
                paths.filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException exception) {
                                log.warn("Failed to delete file: {}", path, exception);
                            }
                        });
            }

            log.info("Cleared all historical data");

        } catch (IOException exception) {
            throw new SQLException("Failed to clear historical data directory", exception);
        }
    }

    @Override
    public long getDataPointCount() {
        return memoryCache.values()
                .stream()
                .mapToLong(List::size)
                .sum();
    }

    @Override
    public CandleData save(CandleData entity) throws SQLException {
        if (entity == null) {
            throw new SQLException("Cannot save null CandleData");
        }

        return entity;
    }

    @Override
    public List<CandleData> saveAll(List<CandleData> entities) {
        if (entities == null) {
            return List.of();
        }

        return entities;
    }

    @Override
    public Optional<CandleData> findById(String id) throws SQLException {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        for (List<CandleData> candles : memoryCache.values()) {
            for (CandleData candle : candles) {
                if (candle != null && id.equals(String.valueOf(timestampOf(candle)))) {
                    return Optional.of(candle);
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public List<CandleData> findAll() throws SQLException {
        return memoryCache.values()
                .stream()
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteById(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        List<CandleData> removed = memoryCache.remove(id);

        if (removed != null) {
            return true;
        }

        boolean deletedAny = false;

        for (Map.Entry<String, List<CandleData>> entry : memoryCache.entrySet()) {
            List<CandleData> candles = entry.getValue();

            if (candles == null) {
                continue;
            }

            boolean removedFromList = candles.removeIf(
                    candle -> candle != null && id.equals(String.valueOf(timestampOf(candle)))
            );

            if (removedFromList) {
                deletedAny = true;
            }
        }

        return deletedAny;
    }

    @Override
    public boolean delete(CandleData entity) throws SQLException {
        if (entity == null) {
            return false;
        }

        long targetTimestamp = timestampOf(entity);
        boolean deleted = false;

        for (List<CandleData> candles : memoryCache.values()) {
            if (candles == null) {
                continue;
            }

            boolean removed = candles.removeIf(
                    candle -> candle != null && timestampOf(candle) == targetTimestamp
            );

            if (removed) {
                deleted = true;
            }
        }

        return deleted;
    }

    @Override
    public void deleteAll() throws SQLException {
        clearAll();
    }

    @Override
    public boolean existsById(String id) throws SQLException {
        return findById(id).isPresent() || memoryCache.containsKey(id);
    }

    @Override
    public long count() throws SQLException {
        return getDataPointCount();
    }

    private void validateRequest(
            TradePair pair,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String timeframe
    ) throws SQLException {
        if (pair == null) {
            throw new SQLException("TradePair must not be null");
        }

        if (startTime == null) {
            throw new SQLException("startTime must not be null");
        }

        if (endTime == null) {
            throw new SQLException("endTime must not be null");
        }

        if (timeframe == null || timeframe.isBlank()) {
            throw new SQLException("timeframe must not be blank");
        }

        if (endTime.isBefore(startTime)) {
            throw new SQLException("endTime must be after startTime");
        }
    }

    private List<CandleData> filterByDateRange(
            List<CandleData> candles,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }

        long startMillis = toEpochMillis(startTime);
        long endMillis = toEpochMillis(endTime);

        return candles.stream()
                .filter(Objects::nonNull)
                .filter(candle -> {
                    long timestamp = timestampOf(candle);
                    return timestamp >= startMillis && timestamp <= endMillis;
                })
                .collect(Collectors.toList());
    }

    private List<CandleData> loadBestMatchingDataFromDisk(
            TradePair pair,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String timeframe
    ) throws SQLException {
        List<Path> matchingFiles = findMatchingFiles(pair, timeframe);

        if (matchingFiles.isEmpty()) {
            return List.of();
        }

        List<CandleData> allCandles = new ArrayList<>();

        for (Path file : matchingFiles) {
            try {
                String jsonData = Files.readString(file, StandardCharsets.UTF_8);

                List<CandleData> data = objectMapper.readValue(
                        jsonData,
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, CandleData.class)
                );

                if (data != null && !data.isEmpty()) {
                    allCandles.addAll(data);
                }

            } catch (IOException exception) {
                throw new SQLException("Failed to read historical data file: " + file, exception);
            }
        }

        return filterByDateRange(allCandles, startTime, endTime);
    }

    private List<CandleData> loadAllMatchingDataFromDisk(TradePair pair, String timeframe) throws SQLException {
        List<Path> matchingFiles = findMatchingFiles(pair, timeframe);

        if (matchingFiles.isEmpty()) {
            return List.of();
        }

        List<CandleData> allCandles = new ArrayList<>();

        for (Path file : matchingFiles) {
            try {
                String jsonData = Files.readString(file, StandardCharsets.UTF_8);

                List<CandleData> data = objectMapper.readValue(
                        jsonData,
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, CandleData.class)
                );

                if (data != null && !data.isEmpty()) {
                    allCandles.addAll(data);
                }
            } catch (IOException exception) {
                throw new SQLException("Failed to read historical data file: " + file, exception);
            }
        }

        return allCandles;
    }

    private List<CandleData> mergeCandles(TradePair pair, String timeframe, List<CandleData> newData) throws SQLException {
        Map<Long, CandleData> byTimestamp = new TreeMap<>();

        String cacheKey = generateCacheKey(pair, timeframe);
        List<CandleData> cached = memoryCache.get(cacheKey);
        if (cached != null) {
            addCandlesByTimestamp(byTimestamp, cached);
        }

        addCandlesByTimestamp(byTimestamp, loadAllMatchingDataFromDisk(pair, timeframe));
        addCandlesByTimestamp(byTimestamp, newData);

        return new ArrayList<>(byTimestamp.values());
    }

    private void addCandlesByTimestamp(Map<Long, CandleData> byTimestamp, List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        for (CandleData candle : candles) {
            if (candle != null) {
                byTimestamp.put(timestampOf(candle), candle);
            }
        }
    }

    private List<Path> findMatchingFiles(TradePair pair, String timeframe) throws SQLException {
        Path dataDir = Paths.get(DATA_DIR);

        if (!Files.exists(dataDir)) {
            return List.of();
        }

        String filePrefix = safeFilePair(pair) + "_" + normalizeTimeframe(timeframe) + "_";

        try (Stream<Path> paths = Files.list(dataDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(filePrefix))
                    .filter(path -> path.getFileName().toString().endsWith(FILE_EXTENSION))
                    .sorted()
                    .collect(Collectors.toList());

        } catch (IOException exception) {
            throw new SQLException("Failed to scan historical data directory", exception);
        }
    }

    private String generateCacheKey(TradePair pair, String timeframe) {
        return safeFilePair(pair) + "_" + normalizeTimeframe(timeframe);
    }

    private @NotNull String generateFileName(
            TradePair pair,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String timeframe
    ) {
        String start = startTime.format(DATE_FORMATTER);
        String end = endTime.format(DATE_FORMATTER);

        return safeFilePair(pair)
                + "_"
                + normalizeTimeframe(timeframe)
                + "_"
                + start
                + "_"
                + end
                + FILE_EXTENSION;
    }

    private String safeFilePair(TradePair pair) {
        String text = displayPair(pair);

        return text
                .replace("/", "_")
                .replace("\\", "_")
                .replace(":", "_")
                .replace(" ", "")
                .toUpperCase(Locale.ROOT);
    }

    private String displayPair(TradePair pair) {
        if (pair == null) {
            return "UNKNOWN";
        }

        try {
            return pair.toString('/');
        } catch (Exception ignored) {
            try {
                return pair.getSymbol();
            } catch (Exception ignoredAgain) {
                return pair.toString();
            }
        }
    }

    private String normalizeTimeframe(String timeframe) {
        return timeframe == null
                ? "UNKNOWN"
                : timeframe.trim().toLowerCase(Locale.ROOT);
    }

    private long toEpochMillis(LocalDateTime dateTime) {
        return dateTime
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    private long timestampOf(CandleData candle) {
        if (candle == null) {
            return 0L;
        }

        try {
            return candle.timestamp().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
