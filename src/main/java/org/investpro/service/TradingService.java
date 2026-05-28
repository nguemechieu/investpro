package org.investpro.service;

import org.investpro.core.SystemCore;
import org.investpro.exchange.Exchange;
import org.investpro.models.Account;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Domain service for trading operations.
 * Coordinates between Trade, Order, and Currency services to handle complex trading scenarios.
 */

public record TradingService(SystemCore systemCore,TradeService tradeService, OrderService orderService, CurrencyService currencyService) {

    /**
     * Initialize the trading service with required dependencies.
     *
     * @param tradeService    the trade service
     * @param orderService    the order service
     * @param currencyService the currency service
     */
    public TradingService {
        if (tradeService == null || orderService == null || currencyService == null) {
            throw new IllegalArgumentException("All services must not be null");
        }
    }

    /**
     * Execute a trade by recording the trade.
     * Note: Implementation depends on Trade class constructors.
     *
     * @param tradePair the trading pair
     * @param price     the price per unit
     * @param amount    the amount/size of trade
     * @param side      the side (BUY or SELL)
     * @return the created trade
     * @throws SQLException if database operation fails
     */
    public Trade executeTrade(Exchange exchange, TradePair tradePair, double price, double amount,
                              String type, Side side, double sl, double tp, double slippage)
            throws SQLException, ClassNotFoundException {

        if (tradePair == null || side == null) {
            throw new IllegalArgumentException("Invalid trade parameters");
        }

        // Create and save the trade with appropriate constructor
        Trade trade = new Trade(tradePair, price, amount, side, System.currentTimeMillis(), Instant.now());
        exchange.createOrder(UUID.randomUUID().hashCode(), tradePair, type, price, amount, side, sl, tp, slippage);

        return tradeService.save(trade);
    }

    /**
     * Get trading statistics for a pair.
     *
     * @param tradePair the trading pair
     * @return trading statistics
     * @throws SQLException if database operation fails
     */
    public TradingStatistics getTradingStatistics(TradePair tradePair) throws SQLException {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }

        long tradeCount = tradeService.countByTradePair(tradePair);
        long openOrderCount = orderService.getOpenOrdersByTradePair(tradePair).size();
        List<Trade> recentTrades = tradeService.findByTradePair(tradePair);

        return new TradingStatistics(tradePair, tradeCount, openOrderCount, recentTrades);
    }

    /**
     * Get open positions for all pairs.
     *
     * @return list of open orders across all pairs
     * @throws SQLException if database operation fails
     */
    public List<Order> getOpenPositions() throws SQLException {
        return orderService.getOpenOrders();
    }

    /**
     * Calculate total value of open positions.
     *
     * @return total value
     * @throws SQLException if database operation fails
     */
    public double calculateTotalExposure() throws SQLException {
        List<Order> openOrders = orderService.getOpenOrders();
        if (openOrders.isEmpty()) {
            return 0; // Or return Money.ZERO
        }

        double total = 0.0;
        for (Order order : openOrders) {
            // Note: Orders stores quantity as double
            total += order.getQuantity();
        }
        return total;
    }

    public @NotNull Account getAccount() {
        try {
            return   systemCore.getExchange().fetchAccount().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Inner class for trading statistics.
     */
    public record TradingStatistics(TradePair tradePair, long tradeCount, long openOrderCount,
                                    List<Trade> recentTrades) {
    }
}
