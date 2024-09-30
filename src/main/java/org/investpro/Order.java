package org.investpro;

import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;

public class Order {
    String symbol;

    boolean isWorking;

    public Order() {
    }

    public String getLastTransactionID() {
        return lastTransactionID;
    }

    public Order[] getOrders() {
        return orders;
    }

    public void setOrders(Order[] orders) {
        this.orders = orders;
    }

    Order []orders;

    public void setLastTransactionID(String lastTransactionID) {
        this.lastTransactionID = lastTransactionID;
    }

    String lastTransactionID;

    public Order(String symbol, long orderId, String side, String type, double price, double qty, long time, boolean isWorking) {
        this.id = orderId;  // Placeholder for account reference

        this.side = Side.getSide(side);
        this.orderType = ENUM_ORDER_TYPE.valueOf(type);
        setPrice(price);
        setSize(qty);
        this.timestamp =  Date.from(Instant.ofEpochMilli(time));
        setStopLoss(stopLoss);
        setTakeProfit(takeProfit);
        this.isWorking = isWorking;
        this.orderStatus = ENUM_ORDER_STATUS.NEW;  // Initial status is 'NEW'
    }

    @Override
    public String toString() {
        return "Order{id=%d, account=%s, tradePair=%s, side=%s, orderType=%s, price=%s, size=%s, timestamp=%s, stopLoss=%s, takeProfit=%s, orderStatus=%s}".formatted(id, account, symbol, side, orderType, price, size, timestamp, stopLoss, takeProfit, orderStatus);
    }

    private Long id;
    private Account account;  // Foreign key reference to Account

    private Side side;
    private ENUM_ORDER_TYPE orderType;
    private double price;
    private double size;
    private Date timestamp;
    private double stopLoss;
    private double takeProfit;
    private ENUM_ORDER_STATUS orderStatus;

    // Constructor
    public Order(@NotNull String symbol, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, Date timestamp, double stopLoss, double takeProfit) {

        this.symbol = symbol;
        this.side = side;
        this.orderType = orderType;
        setPrice(price);
        setSize(size);
        this.timestamp = timestamp;
        setStopLoss(stopLoss);
        setTakeProfit(takeProfit);
        this.orderStatus = ENUM_ORDER_STATUS.NEW;  // Initial status is 'NEW'
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }



    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public ENUM_ORDER_TYPE getOrderType() {
        return orderType;
    }

    public void setOrderType(ENUM_ORDER_TYPE orderType) {
        this.orderType = orderType;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        this.price = price;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than zero");
        }
        this.size = size;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public double getStopLoss() {
        return stopLoss;
    }

    public void setStopLoss(double stopLoss) {
        if (stopLoss < 0) {
            throw new IllegalArgumentException("Stop Loss cannot be negative");
        }
        this.stopLoss = stopLoss;
    }

    public double getTakeProfit() {
        return takeProfit;
    }

    public void setTakeProfit(double takeProfit) {
        if (takeProfit < 0) {
            throw new IllegalArgumentException("Take Profit cannot be negative");
        }
        this.takeProfit = takeProfit;
    }

    public ENUM_ORDER_STATUS getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(ENUM_ORDER_STATUS orderStatus) {
        this.orderStatus = orderStatus;
    }

    // Additional Methods



    /**
     * Check if the order is still active (NEW or PARTIALLY_FILLED).
     *
     * @return true if the order is active, false otherwise.
     */
    public boolean isOrderActive() {
        return orderStatus == ENUM_ORDER_STATUS.NEW || orderStatus == ENUM_ORDER_STATUS.PARTIALLY_FILLED;
    }

    /**
     * Fulfill the order, changing its status to FILLED.
     */
    public void fulfillOrder() {
        if (orderStatus == ENUM_ORDER_STATUS.NEW || orderStatus == ENUM_ORDER_STATUS.PARTIALLY_FILLED) {
            orderStatus = ENUM_ORDER_STATUS.FILLED;
        } else {
            throw new IllegalStateException("Order cannot be fulfilled, current status: %s".formatted(orderStatus));
        }
    }

}
