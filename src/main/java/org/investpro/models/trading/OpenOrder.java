package org.investpro.models.trading;

import lombok.Data;

import  org.investpro.utils.Side;
import java.time.Instant;

/**
 * Represents an open (unfilled) order on an exchange.
 * Extends Order concept with additional fields for tracking pending orders.
 */
@Data
public class OpenOrder {
    private String orderId;
    private TradePair tradePair;
    private Side side;  // BUY or SELL
    private OrderType orderType;  // LIMIT, MARKET, STOP_LOSS, TAKE_PROFIT
    private double price;
    private double size;
    private double filledSize;
    private double remainingSize;
    private Instant createdAt;
    private Instant updatedAt;
    private OrderStatus status;
    private String clientOrderId;
    private double timeInForce;  // GTC, IOC, FOK, GTD
    private double avgFillPrice;
    private double commission;
    private String exchange;




    public enum OrderStatus {
        PENDING,
        OPEN,
        PARTIALLY_FILLED,
        FILLED,
        CANCELLED,
        REJECTED,
        EXPIRED
    }

    public enum OrderType {
        LIMIT,
        MARKET,
        STOP_LOSS,
        TAKE_PROFIT,
        STOP_LIMIT,
        TRAILING_STOP
    }

    public OpenOrder() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.status = OrderStatus.PENDING;
    }

    public OpenOrder(String orderId, TradePair tradePair, Side side, OrderType orderType, double price, int size) {
        this();
        this.orderId = orderId;
        this.tradePair = tradePair;
        this.side = side;
        this.orderType = orderType;
        this.price = price;
        this.size = size;
        this.remainingSize = size;
        this.filledSize = 0;
    }

    /**
     * Get percentage filled
     */
    public double getPercentFilled() {
        if (  size == 0) {
            return 0;
        }
        return (filledSize / size) * 100;
    }

    /**
     * Check if order is fully filled
     */
    public boolean isFullyFilled() {
        return Math.abs(filledSize - size) < 0.00000001;
    }

    /**
     * Check if order is still open for trading
     */
    public boolean isStillOpen() {
        return status == OrderStatus.OPEN || status == OrderStatus.PARTIALLY_FILLED;
    }

    /**
     * Get total order value (price * size)
     */
    public double getTotalValue() {

        return price * size;
    }

    /**
     * Get total filled value
     */
    public double getFilledValue() {

        return avgFillPrice* filledSize;
    }

    /**
     * Update filled size and remaining size
     */
    public void updateFillStatus(int newFilledSize) {
        this.filledSize = newFilledSize;
        double remainingDouble = size - newFilledSize;
        this.remainingSize = Math.max(0, remainingDouble);

        if (isFullyFilled()) {
            this.status = OrderStatus.FILLED;
        } else if (filledSize > 0) {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    /**
     * Mark order as cancelled
     */
    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    @Override
    public String toString() {
        return "OpenOrder{" +
                "orderId='" + orderId + '\'' +
                ", tradePair=" + tradePair +
                ", side=" + side +
                ", orderType=" + orderType +
                ", price=" + price +
                ", size=" + size +
                ", filledSize=" + filledSize +
                ", percentFilled=" + String.format("%.2f%%", getPercentFilled()) +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }
}
