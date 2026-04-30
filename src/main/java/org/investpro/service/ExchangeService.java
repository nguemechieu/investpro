package org.investpro.service;

import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain service for exchange integration and management.
 * Handles exchange connections, trade pair discovery, and market data aggregation.
 */
public class ExchangeService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExchangeService.class);
    
    private final Map<String, Exchange> exchanges = new ConcurrentHashMap<>();
    
    /**
     * Register an exchange for use in the application.
     *
     * @param exchangeName the exchange name
     * @param exchange the exchange instance
     */
    public void registerExchange(String exchangeName, Exchange exchange) {
        if (exchangeName == null || exchangeName.isEmpty()) {
            throw new IllegalArgumentException("exchangeName must not be null or empty");
        }
        if (exchange == null) {
            throw new IllegalArgumentException("exchange must not be null");
        }
        
        exchanges.put(exchangeName.toUpperCase(), exchange);
        logger.info("Registered exchange: " + exchangeName);
    }
    
    /**
     * Get an exchange by name.
     *
     * @param exchangeName the exchange name
     * @return the exchange, or null if not found
     */
    public Exchange getExchange(String exchangeName) {
        if (exchangeName == null || exchangeName.isEmpty()) {
            return null;
        }
        return exchanges.get(exchangeName.toUpperCase());
    }
    
    /**
     * Check if an exchange is registered.
     *
     * @param exchangeName the exchange name
     * @return true if registered, false otherwise
     */
    public boolean isExchangeRegistered(String exchangeName) {
        if (exchangeName == null || exchangeName.isEmpty()) {
            return false;
        }
        return exchanges.containsKey(exchangeName.toUpperCase());
    }
    
    /**
     * Get all registered exchanges.
     *
     * @return list of exchange names
     */
    public List<String> getAllExchanges() {
        return new ArrayList<>(exchanges.keySet());
    }
    
    /**
     * Get all available trade pairs from an exchange.
     *
     * @param exchangeName the exchange name
     * @return list of available trade pairs
     * @throws SQLException if database operation fails
     */
    public List<TradePair> getAvailablePairs(String exchangeName) throws SQLException {
        Exchange exchange = getExchange(exchangeName);
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange not found: " + exchangeName);
        }
        
        try {
            return exchange.getTradePairSymbol();
        } catch (Exception e) {
            logger.error("Failed to get trade pairs from " + exchangeName, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get all available pairs from all registered exchanges.
     *
     * @return map of exchange name to list of pairs
     * @throws SQLException if database operation fails
     */
    public Map<String, List<TradePair>> getAllAvailablePairs() throws SQLException {
        Map<String, List<TradePair>> allPairs = new ConcurrentHashMap<>();
        
        for (Map.Entry<String, Exchange> entry : exchanges.entrySet()) {
            try {
                List<TradePair> pairs = entry.getValue().getTradePairSymbol();
                allPairs.put(entry.getKey(), pairs);
            } catch (Exception e) {
                logger.warn("Failed to fetch pairs from " + entry.getKey(), e);
                allPairs.put(entry.getKey(), new ArrayList<>());
            }
        }
        
        return allPairs;
    }
    
    /**
     * Search for a trade pair across all exchanges.
     *
     * @param baseCurrency the base currency code
     * @param quoteCurrency the quote currency code
     * @return list of exchanges offering this pair
     * @throws SQLException if database operation fails
     */
    public List<String> searchPair(String baseCurrency, String quoteCurrency) throws SQLException {
        if (baseCurrency == null || quoteCurrency == null) {
            return new ArrayList<>();
        }
        
        List<String> exchangesWithPair = new ArrayList<>();
        
        for (Map.Entry<String, Exchange> entry : exchanges.entrySet()) {
            try {
                List<TradePair> pairs = entry.getValue().getTradePairSymbol();
                boolean found = pairs.stream()
                    .anyMatch(p -> p.getKey().getCode().equals(baseCurrency) && 
                              p.getValue().getCode().equals(quoteCurrency));
                if (found) {
                    exchangesWithPair.add(entry.getKey());
                }
            } catch (Exception e) {
                logger.debug("Error searching pair in " + entry.getKey(), e);
            }
        }
        
        return exchangesWithPair;
    }
    
    /**
     * Get market data aggregation (average prices across exchanges).
     * Note: This is a placeholder - actual implementation would aggregate real-time data.
     *
     * @param baseCurrency the base currency
     * @param quoteCurrency the quote currency
     * @return average price across exchanges, or null if not found
     * @throws SQLException if database operation fails
     */
    public Double getAggregatedPrice(String baseCurrency, String quoteCurrency) throws SQLException {
        List<String> exchangesWithPair = searchPair(baseCurrency, quoteCurrency);
        
        if (exchangesWithPair.isEmpty()) {
            return null;
        }
        
        // Placeholder: In real implementation, fetch current prices from each exchange
        // and calculate average
        logger.info("Aggregating prices from " + exchangesWithPair.size() + " exchanges");
        return null;
    }
    
    /**
     * Unregister an exchange.
     *
     * @param exchangeName the exchange name
     * @return true if exchange was unregistered, false if not found
     */
    public boolean unregisterExchange(String exchangeName) {
        if (exchangeName == null || exchangeName.isEmpty()) {
            return false;
        }
        boolean removed = exchanges.remove(exchangeName.toUpperCase()) != null;
        if (removed) {
            logger.info("Unregistered exchange: " + exchangeName);
        }
        return removed;
    }
}
