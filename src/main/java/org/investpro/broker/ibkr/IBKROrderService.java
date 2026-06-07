package org.investpro.broker.ibkr;

import org.investpro.exchange.ibkr.IbkrExchange;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.ORDER_TYPES;
import org.investpro.utils.Side;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class IBKROrderService {

    private final IbkrExchange exchange;

    public IBKROrderService(IbkrExchange exchange) {
        this.exchange = exchange;
    }

    public List<Order> getOrders() {
        List<Order> mapped = new ArrayList<>();
        List<OpenOrder> openOrders = exchange.fetchAllOpenOrders().join();
        for (OpenOrder openOrder : openOrders) {
            mapped.add(mapOpenOrder(openOrder));
        }
        return mapped;
    }

    public String placeOrder(Order order) {
        TradePair pair = resolveTradePair(order);
        Side side = order.getSide() == null ? Side.BUY : order.getSide();
        double quantity = Math.max(0.0, order.getQuantity());
        ORDER_TYPES orderType = resolveType(order);

        if (quantity <= 0.0) {
            throw new IllegalArgumentException("Order quantity must be greater than zero");
        }

        return switch (orderType) {
            case MARKET -> exchange.createMarketOrder(pair, side, quantity).join();
            case LIMIT -> exchange.createLimitOrder(pair, side, quantity, order.getPrice()).join();
            case STOP_LIMIT -> exchange.createStopOrder(pair, side, quantity, order.getPrice()).join();
            case TRAILING_STOP -> exchange.createTrailingStopOrder(pair, side, quantity,quantity/2, true).join();
        };
    }

    public boolean cancelOrder(String orderId) {
        String result = exchange.cancelOrder(orderId).join();
        return result != null && !result.isBlank() && !"NOT_FOUND".equalsIgnoreCase(result);
    }

    private ORDER_TYPES resolveType(Order order) {
        String rawType = order.getOrderType();
        if (rawType == null || rawType.isBlank()) {
            return ORDER_TYPES.MARKET;
        }
        try {
            return ORDER_TYPES.valueOf(rawType.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return ORDER_TYPES.MARKET;
        }
    }

    private TradePair resolveTradePair(Order order) {
        if (order.getTradePair() != null) {
            return order.getTradePair();
        }
        if (order.getSymbol() == null || order.getSymbol().isBlank()) {
            throw new IllegalArgumentException("Order requires trade pair or symbol");
        }
        try {
            return exchange.parsePair(order.getSymbol());
        } catch (SQLException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Unable to parse symbol into TradePair: " + order.getSymbol(), e);
        }
    }

    private Order mapOpenOrder(OpenOrder source) {
        Order order = new Order();
        order.setId(parseOrderId(source.getOrderId()));
        order.setDate(Date.from(source.getCreatedAt() == null ? Instant.now() : source.getCreatedAt()));
        order.setTradePair(source.getTradePair());
        order.setSide(source.getSide());
        order.setOrderType(source.getOrderType() == null ? ORDER_TYPES.MARKET.name() : source.getOrderType().name());
        order.setPrice(source.getPrice());
        order.setQuantity(source.getSize());
        order.setFilledQuantity(source.getFilledSize());
        order.setStatus(source.getStatus() == null ? "UNKNOWN" : source.getStatus().name());
        order.setCreatedAt(source.getCreatedAt());
        order.setUpdatedAt(source.getUpdatedAt());
        return order;
    }

    private Long parseOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(orderId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
