package org.investpro.persistence.repository;

import org.investpro.models.trading.Order;
import org.investpro.models.trading.TradePair;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * Repository for Orders entities with domain-specific operations.
 */
public interface OrderRepository extends Repository<Order, String> {
    
    /**
     * Find all orders for a specific trading pair.
     *
     * @param tradePair the trading pair
     * @return list of orders for the pair
     * @throws SQLException if database operation fails
     */
    List<Order> findByTradePair(TradePair tradePair) throws SQLException;
    
    /**
     * Find orders by status.
     *
     * @param status the order status
     * @return list of orders with the given status
     * @throws SQLException if database operation fails
     */
    List<Order> findByStatus(String status) throws SQLException;
    
    /**
     * Find orders within a time range.
     *
     * @param startTime the start instant
     * @param endTime the end instant
     * @return list of orders in the time range
     * @throws SQLException if database operation fails
     */
    List<Order> findByTimeRange(Instant startTime, Instant endTime) throws SQLException;
    
    /**
     * Find open orders (not filled or cancelled).
     *
     * @return list of open orders
     * @throws SQLException if database operation fails
     */
    List<Order> findOpenOrders() throws SQLException;
    
    /**
     * Find open orders for a specific pair.
     *
     * @param tradePair the trading pair
     * @return list of open orders for the pair
     * @throws SQLException if database operation fails
     */
    List<Order> findOpenOrdersByTradePair(TradePair tradePair) throws SQLException;
    
    /**
     * Count open orders.
     *
     * @return the count of open orders
     * @throws SQLException if database operation fails
     */
    long countOpenOrders() throws SQLException;
}
