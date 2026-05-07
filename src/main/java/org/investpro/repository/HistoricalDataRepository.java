package org.investpro.repository;

import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for persistent storage and retrieval of historical candlestick data.
 * Ensures historical data used for backtesting is stored and can be reused.
 */
public interface HistoricalDataRepository extends Repository<CandleData, String> {

    /**
     * Store historical candle data for a trading pair and time range.
     * 
     * @param pair The trading pair
     * @param startTime The start time of the data range
     * @param endTime The end time of the data range
     * @param timeframe The timeframe/granularity (e.g., "1m", "5m", "1h", "1d")
     * @param data The candle data to store
     * @throws SQLException if storage operation fails
     */
    void saveHistoricalData(TradePair pair, LocalDateTime startTime, LocalDateTime endTime, 
                           String timeframe, List<CandleData> data) throws SQLException;

    /**
     * Retrieve historical candle data for a trading pair and time range.
     * 
     * @param pair The trading pair
     * @param startTime The start time of the data range
     * @param endTime The end time of the data range
     * @param timeframe The timeframe/granularity
     * @return Optional containing the candle data if found, empty otherwise
     * @throws SQLException if retrieval operation fails
     */
    Optional<List<CandleData>> getHistoricalData(TradePair pair, LocalDateTime startTime, 
                                                 LocalDateTime endTime, String timeframe) throws SQLException;

    /**
     * Check if historical data exists for the given parameters.
     * 
     * @param pair The trading pair
     * @param startTime The start time
     * @param endTime The end time
     * @param timeframe The timeframe
     * @return true if data exists and covers the entire range, false otherwise
     * @throws SQLException if check operation fails
     */
    boolean hasData(TradePair pair, LocalDateTime startTime, LocalDateTime endTime, String timeframe) throws SQLException;

    /**
     * Delete historical data for a specific trading pair and time range.
     * 
     * @param pair The trading pair
     * @param startTime The start time
     * @param endTime The end time
     * @param timeframe The timeframe
     * @throws SQLException if delete operation fails
     */
    void deleteHistoricalData(TradePair pair, LocalDateTime startTime, LocalDateTime endTime, String timeframe) throws SQLException;

    /**
     * Clear all historical data (use with caution).
     * 
     * @throws SQLException if clear operation fails
     */
    void clearAll() throws SQLException;

    /**
     * Get the total count of stored historical data points.
     * 
     * @return The count of all stored candle data points
     */
    long getDataPointCount();
}
