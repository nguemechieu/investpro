package org.investpro.models.trading;


import lombok.Getter;
import lombok.Setter;
import  org.investpro.utils.Side;

import java.time.Instant;
import java.util.Date;

@Getter
@Setter
public class Order {

    private String symbol;
    private double quantity;
    private double price;
    private double commission;
    private double takeProfit;
    private double stopLoss;
    private double swap;
    private double profit;
    private String status;
    private Date date;
    private String orderType;
    private double slippage;
    private Side side;
    private Long id;

    public Order(Date date, String orderType, String symbol, double quantity, double price, double commission,
                 double takeProfit, double stopLoss, double swap, double profit) {
        this.date = date;
        this.orderType = orderType;
        this.symbol = symbol;
        this.quantity = quantity;
        this.price = price;
        this.commission = commission;
        this.takeProfit = takeProfit;
        this.stopLoss = stopLoss;
        this.swap = swap;
        this.profit = profit;
    }

    public Order(Long id, Date date, String orderType, Side side, String symbol, double quantity, double price, double stopLoss, double takeProfit, double slippage) {
        this.date = date;
        this.orderType = orderType;
        this.symbol = symbol;
        this.quantity = quantity;
        this.price = price;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.side = side;
        this.id = id;
        this.slippage = slippage;
    }

    public Order() {

    }

    public String getType() {
        return orderType;
    }

    public void setType(String type) {
        this.orderType = type;
    }


    private TradePair tradePair;

    private Instant createdAt;
    private Instant updatedAt;

    private double cummulativeQuoteQty;

    private double filledQuantity;

    private  Instant timeInForce;
}
