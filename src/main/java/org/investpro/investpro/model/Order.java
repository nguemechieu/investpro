package org.investpro.investpro.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.ENUM_ORDER_STATUS;
import org.investpro.investpro.ENUM_ORDER_TYPE;
import org.investpro.investpro.Side;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.Objects;

@Getter
@Setter
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

    Date timestamp;
    @Column(name = "stop_loss")
    double stopLoss;
    @Column(name = "take_profit")
    double takeProfit;
    @Column(name = "order_status", nullable = false)
    @Enumerated(EnumType.STRING)
    ENUM_ORDER_STATUS orderStatus;
    @Id

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    public Order() {
        this.orderId = -1;
        this.side = Side.UNKNOWN;
        this.orderType = ENUM_ORDER_TYPE.LIMIT;
        this.price = 0.0;
        this.size = 0.0;
        this.time = 0L;
        this.isWorking = false;
        this.orderStatus = ENUM_ORDER_STATUS.UNKNOWN;
    }

    public Order(String symbol, long orderId, String side, ENUM_ORDER_TYPE type, double price, double origQty, long time, String status, boolean isWorking) {

        this.symbol = symbol;
        this.orderId = orderId;
        this.side = Side.getSide(side);
        this.orderType = type;
        this.price = price;
        this.size = origQty;
        this.time = time;

        this.isWorking = isWorking;
        this.orderStatus = ENUM_ORDER_STATUS.valueOf(status);


    }

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





    public void setPrice(double price) {
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        this.price = price;
    }


    public void setSize(double size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than zero");
        }
        this.size = size;
    }


    public void setStopLoss(double stopLoss) {
        if (stopLoss < 0) {
            throw new IllegalArgumentException("Stop Loss cannot be negative");
        }
        this.stopLoss = stopLoss;
    }


    public void setTakeProfit(double takeProfit) {
        if (takeProfit < 0) {
            throw new IllegalArgumentException("Take Profit cannot be negative");
        }
        this.takeProfit = takeProfit;
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


}
