package org.investpro.investpro;

import org.jetbrains.annotations.NotNull;

import java.util.Date;

public record CreateOrderRequest(String symbol, Side side, ENUM_ORDER_TYPE orderType, double price, double size,
                                 Date timestamp, double stopLoss, double takeProfit) {
    public CreateOrderRequest(String symbol, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, Date timestamp, double stopLoss, double takeProfit) {


        this.symbol = symbol;
        this.side = side;
        this.orderType = orderType;
        this.price = price;
        this.size = size;
        this.timestamp = timestamp;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
    }

}
