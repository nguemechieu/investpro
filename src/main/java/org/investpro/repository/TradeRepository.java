package org.investpro.repository;

import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * Repository for Trade entities with domain-specific operations.
 */
public interface TradeRepository extends Repository<Trade, String> {
    
    /**
     * Find all trades for a specific trading pair.
     *
     * @param tradePair the trading pair
     * @return list of trades for the pair
     * @throws SQLException if database operation fails
     */
    List<Trade> findByTradePair(TradePair tradePair) throws SQLException;
    
    /**
     * Find trades within a time range.
     *
     * @param startTime the start instant
     * @param endTime the end instant
     * @return list of trades in the time range
     * @throws SQLException if database operation fails
     */
    List<Trade> findByTimeRange(Instant startTime, Instant endTime) throws SQLException;
    
    /**
     * Find trades for a specific pair within a time range.
     *
     * @param tradePair the trading pair
     * @param startTime the start instant
     * @param endTime the end instant
     * @return list of matching trades
     * @throws SQLException if database operation fails
     */
    List<Trade> findByTradePairAndTimeRange(TradePair tradePair, Instant startTime, Instant endTime) throws SQLException;
    
    /**
     * Get the most recent trade for a pair.
     *
     * @param tradePair the trading pair
     * @return the most recent trade, or null if none exist
     * @throws SQLException if database operation fails
     */
    Trade findLatestByTradePair(TradePair tradePair) throws SQLException;
    
    /**
     * Count trades for a specific pair.
     *
     * @param tradePair the trading pair
     * @return the count of trades
     * @throws SQLException if database operation fails
     */
    long countByTradePair(TradePair tradePair) throws SQLException;
}
