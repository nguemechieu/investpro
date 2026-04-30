package org.investpro.service;


import org.investpro.repository.TradeRepository;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for Trade domain operations.
 * Provides business logic and validation for trade-related operations.
 */
public class TradeService implements CrudService<Trade, String> {
    
    private final TradeRepository repository;
    
    /**
     * Initialize the service with a trade repository.
     *
     * @param repository the trade repository
     */
    public TradeService(TradeRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        this.repository = repository;
    }
    
    @Override
    public Trade save(Trade trade) throws SQLException, ClassNotFoundException {
        if (trade == null) {
            throw new IllegalArgumentException("trade must not be null");
        }
        validateTrade(trade);
        return repository.save(trade);
    }
    
    @Override
    public List<Trade> saveAll(List<Trade> trades) throws SQLException {
        if (trades == null || trades.isEmpty()) {
            throw new IllegalArgumentException("trades list must not be null or empty");
        }
        trades.forEach(this::validateTrade);
        return repository.saveAll(trades);
    }
    
    @Override
    public Optional<Trade> findById(String id) throws SQLException {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }
        return repository.findById(id);
    }
    
    @Override
    public List<Trade> findAll() throws SQLException {
        return repository.findAll();
    }
    
    @Override
    public boolean delete(String id) throws SQLException {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return repository.deleteById(id);
    }
    
    @Override
    public boolean exists(String id) throws SQLException {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return repository.existsById(id);
    }
    
    @Override
    public long count() throws SQLException {
        return repository.count();
    }
    
    /**
     * Find all trades for a specific trading pair.
     *
     * @param tradePair the trading pair
     * @return list of trades for the pair
     * @throws SQLException if database operation fails
     */
    public List<Trade> findByTradePair(TradePair tradePair) throws SQLException {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }
        return repository.findByTradePair(tradePair);
    }
    
    /**
     * Find trades within a time range.
     *
     * @param startTime the start instant
     * @param endTime the end instant
     * @return list of trades in the time range
     * @throws SQLException if database operation fails
     */
    public List<Trade> findByTimeRange(Instant startTime, Instant endTime) throws SQLException {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("time range must not be null");
        }
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        return repository.findByTimeRange(startTime, endTime);
    }
    
    /**
     * Find trades for a specific pair within a time range.
     *
     * @param tradePair the trading pair
     * @param startTime the start instant
     * @param endTime the end instant
     * @return list of matching trades
     * @throws SQLException if database operation fails
     */
    public List<Trade> findByTradePairAndTimeRange(TradePair tradePair, Instant startTime, Instant endTime) throws SQLException {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("time range must not be null");
        }
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        return repository.findByTradePairAndTimeRange(tradePair, startTime, endTime);
    }
    
    /**
     * Get the most recent trade for a pair.
     *
     * @param tradePair the trading pair
     * @return the most recent trade, or Optional.empty() if none exist
     * @throws SQLException if database operation fails
     */
    public Optional<Trade> getLatestTrade(TradePair tradePair) throws SQLException {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }
        Trade latest = repository.findLatestByTradePair(tradePair);
        return Optional.ofNullable(latest);
    }
    
    /**
     * Count trades for a specific pair.
     *
     * @param tradePair the trading pair
     * @return the count of trades
     * @throws SQLException if database operation fails
     */
    public long countByTradePair(TradePair tradePair) throws SQLException {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }
        return repository.countByTradePair(tradePair);
    }
    
    /**
     * Validate a trade before saving.
     *
     * @param trade the trade to validate
     * @throws IllegalArgumentException if trade is invalid
     */
    private void validateTrade(Trade trade) {
        if (trade.getTradePair() == null) {
            throw new IllegalArgumentException("trade pair must not be null");
        }
        // Note: Full validation depends on Trade class structure and available getters
    }
}
