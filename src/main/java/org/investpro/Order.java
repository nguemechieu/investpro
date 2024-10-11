package org.investpro;

import jakarta.persistence.*;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

@Entity
@Table(name = "orders")
public class Order {

    @Column(name = "symbol", nullable = false)
    String symbol;
    @Column(name = "is_working", nullable = false)
    boolean isWorking;
    @Transient
    String lastTransactionID;  // Not mapped to database
    @Transient
    Order[] orders;  // Not mapped to database
    long orderId;
    long time;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;
    @Column(name = "side", nullable = false)
    @Enumerated(EnumType.STRING)
    Side side;
    @Column(name = "order_type", nullable = false)
    @Enumerated(EnumType.STRING)
    ENUM_ORDER_TYPE orderType;
    @Column(name = "price", nullable = false)
    double price;
    @Column(name = "size", nullable = false)
    double size;
    @Column(name = "timestamp", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    Date timestamp;
    @Column(name = "stop_loss")
    double stopLoss;
    @Column(name = "take_profit")
    double takeProfit;
    @Column(name = "order_status", nullable = false)
    @Enumerated(EnumType.STRING)
    ENUM_ORDER_STATUS orderStatus;

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
        this.isWorking = true;
    }

    public Order() {
    }

    public Order(String symbol, long orderId, String side, String type, double price, double origQty, long time, boolean equals) {
        this.id = orderId;  // Placeholder for account reference

        this.side = Side.getSide(side);
        this.orderType = ENUM_ORDER_TYPE.valueOf(type);
        this.orderId = orderId; // Placeholder
        this.price = price;
        this.size = origQty;
        this.time = time;
        this.isWorking = equals;
        this.symbol = symbol;
        //... more fields...
        setPrice(price);
        //... more fields...
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }



    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
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

    public boolean isWorking() {
        return isWorking;
    }

    public void setWorking(boolean working) {
        isWorking = working;
    }

    // Additional Methods
    public boolean isOrderActive() {
        return orderStatus == ENUM_ORDER_STATUS.NEW || orderStatus == ENUM_ORDER_STATUS.PARTIALLY_FILLED;
    }

    public void fulfillOrder() {
        if (orderStatus == ENUM_ORDER_STATUS.NEW || orderStatus == ENUM_ORDER_STATUS.PARTIALLY_FILLED) {
            orderStatus = ENUM_ORDER_STATUS.FILLED;
        } else {
            throw new IllegalStateException("Order cannot be fulfilled, current status: %s".formatted(orderStatus));
        }
    }

    @Override
    public String toString() {
        return "Order{id=%d,  symbol=%s, side=%s, orderType=%s, price=%s, size=%s, timestamp=%s, stopLoss=%s, takeProfit=%s, orderStatus=%s}".formatted(id, symbol, side, orderType, price, size, timestamp, stopLoss, takeProfit, orderStatus);
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }
}
