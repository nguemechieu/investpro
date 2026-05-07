package org.investpro.service;

import org.investpro.models.trading.Order;
import org.investpro.models.trading.TradePair;
import org.investpro.repository.OrderRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for Order domain operations.
 * Responsibilities:
 * - validate orders before persistence
 * - expose order lookup helpers
 * - manage order status transitions
 * - provide business-level order queries
 */
public class OrderService implements CrudService<Order, String> {

    private final OrderRepository repository;

    public static final String ORDER_STATUS_OPEN = "OPEN";
    public static final String ORDER_STATUS_FILLED = "FILLED";
    public static final String ORDER_STATUS_CANCELLED = "CANCELLED";
    public static final String ORDER_STATUS_PENDING = "PENDING";
    public static final String ORDER_STATUS_REJECTED = "REJECTED";
    public static final String ORDER_STATUS_PARTIALLY_FILLED = "PARTIALLY_FILLED";

    public static final String ORDER_TYPE_BUY = "BUY";
    public static final String ORDER_TYPE_SELL = "SELL";

    /**
     * Initialize the service with an order repository.
     *
     * @param repository the order repository
     */
    public OrderService(OrderRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Order save(Order order) throws SQLException, ClassNotFoundException {
        validateOrder(order);
        return repository.save(order);
    }

    @Override
    public List<Order> saveAll(List<Order> orders) throws SQLException {
        if (orders == null || orders.isEmpty()) {
            throw new IllegalArgumentException("orders list must not be null or empty");
        }

        for (Order order : orders) {
            validateOrder(order);
        }

        return repository.saveAll(orders);
    }

    @Override
    public Optional<Order> findById(String id) throws SQLException {
        if (isBlank(id)) {
            return Optional.empty();
        }

        return repository.findById(id.trim());
    }

    @Override
    public List<Order> findAll() throws SQLException {
        return repository.findAll();
    }

    @Override
    public boolean delete(String id) throws SQLException {
        if (isBlank(id)) {
            return false;
        }

        return repository.deleteById(id.trim());
    }

    @Override
    public boolean exists(String id) throws SQLException {
        if (isBlank(id)) {
            return false;
        }

        return repository.existsById(id.trim());
    }

    @Override
    public long count() throws SQLException {
        return repository.count();
    }

    /**
     * Find all orders for a specific trading pair.
     *
     * @param tradePair the trading pair
     * @return list of orders for the pair
     * @throws SQLException if database operation fails
     */
    public List<Order> findByTradePair(TradePair tradePair) throws SQLException {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }

        return repository.findByTradePair(tradePair);
    }

    /**
     * Find orders by status.
     *
     * @param status the order status
     * @return list of orders with the given status
     * @throws SQLException if database operation fails
     */
    public List<Order> findByStatus(String status) throws SQLException {
        String normalizedStatus = normalizeStatus(status);
        return repository.findByStatus(normalizedStatus);
    }

    /**
     * Find all pending orders.
     */
    public List<Order> getPendingOrders() throws SQLException {
        return findByStatus(ORDER_STATUS_PENDING);
    }

    /**
     * Find all filled orders.
     */
    public List<Order> getFilledOrders() throws SQLException {
        return findByStatus(ORDER_STATUS_FILLED);
    }

    /**
     * Find all cancelled orders.
     */
    public List<Order> getCancelledOrders() throws SQLException {
        return findByStatus(ORDER_STATUS_CANCELLED);
    }


    /**
     * Find all open orders.
     *
     * @return list of open orders
     * @throws SQLException if database operation fails
     */
    public List<Order> getOpenOrders() throws SQLException {
        return repository.findOpenOrders();
    }

    /**
     * Find open orders for a specific pair.
     *
     * @param tradePair the trading pair
     * @return list of open orders for the pair
     * @throws SQLException if database operation fails
     */
    public List<Order> getOpenOrdersByTradePair(TradePair tradePair) throws SQLException {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }

        return repository.findOpenOrdersByTradePair(tradePair);
    }

    /**
     * Count open orders.
     *
     * @return the count of open orders
     * @throws SQLException if database operation fails
     */
    public long countOpenOrders() throws SQLException {
        return repository.countOpenOrders();
    }

    /**
     * Cancel an order and persist the updated status.
     *
     * @param order the order to cancel
     * @return the updated order
     * @throws SQLException if database operation fails
     */
    public Order cancelOrder(Order order) throws SQLException {
        requireOrder(order);

        if (isOrderFilled(order)) {
            throw new IllegalStateException("Cannot cancel a filled order");
        }

        if (isOrderCancelled(order)) {
            return order;
        }

        order.setStatus(ORDER_STATUS_CANCELLED);
        return repository.save(order);
    }

    /**
     * Mark an order as open and persist it.
     */
    public Order markOpen(Order order) throws SQLException, ClassNotFoundException {
        requireOrder(order);
        order.setStatus(ORDER_STATUS_OPEN);
        return repository.save(order);
    }

    /**
     * Mark an order as pending and persist it.
     */
    public Order markPending(Order order) throws SQLException, ClassNotFoundException {
        requireOrder(order);
        order.setStatus(ORDER_STATUS_PENDING);
        return repository.save(order);
    }

    /**
     * Mark an order as filled and persist it.
     */
    public Order markFilled(Order order) throws SQLException, ClassNotFoundException {
        requireOrder(order);
        order.setStatus(ORDER_STATUS_FILLED);
        return repository.save(order);
    }

    /**
     * Mark an order as partially filled and persist it.
     */
    public Order markPartiallyFilled(Order order) throws SQLException, ClassNotFoundException {
        requireOrder(order);
        order.setStatus(ORDER_STATUS_PARTIALLY_FILLED);
        return repository.save(order);
    }

    /**
     * Mark an order as rejected and persist it.
     */
    public Order markRejected(Order order) throws SQLException, ClassNotFoundException {
        requireOrder(order);
        order.setStatus(ORDER_STATUS_REJECTED);
        return repository.save(order);
    }

    /**
     * Check if an order is still open/pending/partially filled.
     *
     * @param order the order to check
     * @return true if order is active, false otherwise
     */
    public boolean isOrderOpen(Order order) {
        if (order == null) {
            return false;
        }

        String status = normalizeStatusOrDefault(order.getStatus(), ORDER_STATUS_OPEN);

        return Objects.equals(status, ORDER_STATUS_OPEN)
                || Objects.equals(status, ORDER_STATUS_PENDING)
                || Objects.equals(status, ORDER_STATUS_PARTIALLY_FILLED);
    }

    public boolean isOrderFilled(Order order) {
        if (order == null) {
            return false;
        }

        return Objects.equals(
                normalizeStatusOrDefault(order.getStatus(), ""),
                ORDER_STATUS_FILLED
        );
    }

    public boolean isOrderCancelled(Order order) {
        if (order == null) {
            return false;
        }

        return Objects.equals(
                normalizeStatusOrDefault(order.getStatus(), ""),
                ORDER_STATUS_CANCELLED
        );
    }

    public boolean isOrderRejected(Order order) {
        if (order == null) {
            return false;
        }

        return Objects.equals(
                normalizeStatusOrDefault(order.getStatus(), ""),
                ORDER_STATUS_REJECTED
        );
    }

    public boolean isBuyOrder(Order order) {
        return order != null && Objects.equals(normalizeType(order.getType()), ORDER_TYPE_BUY);
    }

    public boolean isSellOrder(Order order) {
        return order != null && Objects.equals(normalizeType(order.getType()), ORDER_TYPE_SELL);
    }

    /**
     * Calculate total profit from a list of orders.
     */
    public double calculateTotalProfit(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;

        for (Order order : orders) {
            if (order != null) {
                total += order.getProfit();
            }
        }

        return total;
    }

    /**
     * Calculate total commission from a list of orders.
     */
    public double calculateTotalCommission(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;

        for (Order order : orders) {
            if (order != null) {
                total += order.getCommission();
            }
        }

        return total;
    }


    /**
     * Validate an order before saving.
     *
     * @param order the order to validate
     * @throws IllegalArgumentException if order is invalid
     */
    private void validateOrder(Order order) {
        requireOrder(order);

        if (isBlank(order.getSymbol())) {
            throw new IllegalArgumentException("order symbol must not be blank");
        }

        if (isBlank(order.getType())) {
            throw new IllegalArgumentException("order type must not be blank");
        }

        String type = normalizeType(order.getType());

        if (!Objects.equals(type, ORDER_TYPE_BUY) && !Objects.equals(type, ORDER_TYPE_SELL)) {
            throw new IllegalArgumentException("order type must be BUY or SELL but was: " + order.getType());
        }

        if (order.getQuantity() <= 0) {
            throw new IllegalArgumentException("order quantity must be positive");
        }

        if (order.getPrice() < 0) {
            throw new IllegalArgumentException("order price must not be negative");
        }

        if (order.getTakeProfit() < 0) {
            throw new IllegalArgumentException("order takeProfit must not be negative");
        }

        if (order.getStopLoss() < 0) {
            throw new IllegalArgumentException("order stopLoss must not be negative");
        }

        if (order.getCommission() < 0) {
            throw new IllegalArgumentException("order commission must not be negative");
        }

        if (isBlank(order.getStatus())) {
            order.setStatus(ORDER_STATUS_OPEN);
        } else {
            order.setStatus(normalizeStatus(order.getStatus()));
        }

        order.setType(type);
        order.setSymbol(order.getSymbol().trim().toUpperCase());
    }

    private void requireOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
    }

    private String normalizeStatus(String status) {
        if (isBlank(status)) {
            throw new IllegalArgumentException("status must not be null or blank");
        }

        return status.trim().toUpperCase();
    }

    private String normalizeStatusOrDefault(String status, String fallback) {
        if (isBlank(status)) {
            return fallback;
        }

        return status.trim().toUpperCase();
    }

    private String normalizeType(String type) {
        if (isBlank(type)) {
            return "";
        }

        return type.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}