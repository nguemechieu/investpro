package org.investpro.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of HistoricalDataRepository using file-based storage.
 * Stores historical candle data in JSON format for persistent access across
 * sessions.
 * Used by backtesting to ensure consistent historical data.
 */
@Slf4j
public class HistoricalDataRepositoryImpl implements HistoricalDataRepository {

    private static final String DATA_DIR = "data/historical";
    private static final String FILE_EXTENSION = ".json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ObjectMapper objectMapper;
    private final Map<String, List<CandleData>> memoryCache;

    public HistoricalDataRepositoryImpl() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.memoryCache = new ConcurrentHashMap<>();

        // Create data directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
            log.info("Historical data directory initialized: {}", DATA_DIR);
        } catch (IOException e) {
            log.error("Failed to create historical data directory", e);
        }
    }

    @Override
    public void saveHistoricalData(TradePair pair, LocalDateTime startTime, LocalDateTime endTime,
            String timeframe, List<CandleData> data) throws SQLException {
        if (data == null || data.isEmpty()) {
            log.warn("No data to save for {}_{}", pair.getSymbol(), timeframe);
            return;
        }

        try {
            String fileName = generateFileName(pair, startTime, endTime, timeframe);
            Path filePath = Paths.get(DATA_DIR, fileName);

            // Store in memory cache
            String cacheKey = generateCacheKey(pair, timeframe);
            memoryCache.put(cacheKey, new ArrayList<>(data));

            // Store to disk
            String jsonData = objectMapper.writeValueAsString(data);
            Files.write(filePath, jsonData.getBytes());

            log.info("Saved {} historical data points for {}/{}  ({} to {})",
                    data.size(), pair.getSymbol(), timeframe, startTime, endTime);
        } catch (IOException e) {
            throw new SQLException("Failed to save historical data for " + pair.getSymbol(), e);
        }
    }

    @Override
    public Optional<List<CandleData>> getHistoricalData(TradePair pair, LocalDateTime startTime,
            LocalDateTime endTime, String timeframe) throws SQLException {
        String cacheKey = generateCacheKey(pair, timeframe);

        // Check memory cache first
        if (memoryCache.containsKey(cacheKey)) {
            List<CandleData> cachedData = memoryCache.get(cacheKey);
            // Filter by date range
            List<CandleData> filtered = cachedData.stream()
                    .filter(
                            CandleData::placeHolder
                    )
                    .toList();
            return Optional.of(filtered);
        }

        // Try to load from disk
        try {
            String fileName = generateFileName(pair, startTime, endTime, timeframe);
            Path filePath = Paths.get(DATA_DIR, fileName);

            if (Files.exists(filePath)) {
                String jsonData = new String(Files.readAllBytes(filePath));
                List<CandleData> data = objectMapper.readValue(jsonData,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, CandleData.class));

                // Store in cache for faster access
                memoryCache.put(cacheKey, data);

                log.info("Loaded {} historical data points for {}/{}",
                        data.size(), pair.getSymbol(), timeframe);
                return Optional.of(data);
            }
        } catch (IOException e) {
            throw new SQLException("Failed to load historical data for " + pair.getSymbol() + "/" + timeframe, e);
        }

        return Optional.empty();
    }

    @Override
    public boolean hasData(TradePair pair, LocalDateTime startTime, LocalDateTime endTime, String timeframe)
            throws SQLException {
        String cacheKey = generateCacheKey(pair, timeframe);

        // Check cache
        if (memoryCache.containsKey(cacheKey)) {
            return true;
        }

        // Check file system
        try {
            String fileName = generateFileName(pair, startTime, endTime, timeframe);
            Path filePath = Paths.get(DATA_DIR, fileName);
            return Files.exists(filePath);
        } catch (Exception e) {
            log.debug("Error checking for data existence", e);
            return false;
        }
    }

    @Override
    public void deleteHistoricalData(TradePair pair, LocalDateTime startTime, LocalDateTime endTime, String timeframe)
            throws SQLException {
        try {
            String fileName = generateFileName(pair, startTime, endTime, timeframe);
            Path filePath = Paths.get(DATA_DIR, fileName);

            if (Files.exists(filePath)) {
                Files.delete(filePath);

                String cacheKey = generateCacheKey(pair, timeframe);
                memoryCache.remove(cacheKey);

                log.info("Deleted historical data for {}/{}", pair.getSymbol(), timeframe);
            }
        } catch (IOException e) {
            throw new SQLException("Failed to delete historical data", e);
        }
    }

    @Override
    public void clearAll() throws SQLException {
        try {
            memoryCache.clear();
            Files.walk(Paths.get(DATA_DIR))
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete file: {}", path, e);
                        }
                    });
            log.info("Cleared all historical data");
        } catch (IOException e) {
            throw new SQLException("Failed to clear historical data directory", e);
        }
    }

    @Override
    public long getDataPointCount() {
        return memoryCache.values().stream()
                .mapToLong(List::size)
                .sum();
    }

    @Override
    public CandleData save(CandleData entity) throws SQLException {
        // Not used for this repository
        return entity;
    }

    @Override
    public List<CandleData> saveAll(List<CandleData> entities) throws SQLException {
        // Not used for this repository
        return entities;
    }

    @Override
    public Optional<CandleData> findById(String id) throws SQLException {
        // Not used for this repository
        return Optional.empty();
    }

    @Override
    public List<CandleData> findAll() throws SQLException {
        // Return all cached data
        return memoryCache.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteById(String s) throws SQLException {
        return memoryCache.remove(s).isEmpty();
    }

    @Override
    public boolean delete(CandleData entity) throws SQLException {
        return false;
    }

    @Override
    public void deleteAll() throws SQLException {

    }

    @Override
    public boolean existsById(String s) throws SQLException {
        return false;
    }

    @Override
    public long count() throws SQLException {
        return 0;
    }

    /**
     * Generate a unique cache key for a trading pair and timeframe.
     */
    private String generateCacheKey(TradePair pair, String timeframe) {
        return pair.getSymbol() + "_" + timeframe;
    }

    /**
     * Generate a unique file name for storing historical data.
     */
    private String generateFileName(TradePair pair, LocalDateTime startTime, LocalDateTime endTime, String timeframe) {
        String start = startTime.format(DATE_FORMATTER);
        String end = endTime.format(DATE_FORMATTER);
        return pair.getSymbol().replace("/", "_") + "_" + timeframe + "_" + start + "_" + end + FILE_EXTENSION;
    }
}
