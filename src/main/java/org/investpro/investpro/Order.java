package org.investpro.investpro;

import java.util.Date;

public class Order {


    String timestamp;
     Long id;
     TRADE_ORDER_TYPE order_type;
     double lotSize;
     double price;
     double total;
     double remaining;
     double fee;
     String currency;
     Date created;
double stopLoss;
double takeProfit;

    public double getStopLoss() {
        return stopLoss;
    }

    public void setStopLoss(double stopLoss) {
        this.stopLoss = stopLoss;
    }

    public double getTakeProfit() {
        return takeProfit;
    }

    public void setTakeProfit(double takeProfit) {
        this.takeProfit = takeProfit;
    }

    @Override
    public String toString() {
        return
                       id +
                ", order_type=" + order_type +
                ", lotSize=" + lotSize +
                             ", symbol='" + symbol +currency+ '\'' + ", price=" + price +
                             ", stopLoss=" + stopLoss +
                             ", takeProfit=" + takeProfit +
                               ", status='" + status + '\'' +
                ", fee=" + fee +

                ", created=" + created +
                ", updated=" + updated +

                ", type='" + type + '\'' ;
    }

    public Order() {
        this.created = new Date();
        this.order_type = TRADE_ORDER_TYPE.NONE;
        this.lotSize = 0.0;
        this.price = 0.0;
        this.total = 0.0;
        this.remaining = 0.0;
        this.fee = 0.0;
        this.currency = "USD";
    }

    public Order(String timestamp, Long id, TRADE_ORDER_TYPE order_type, double lotSize, double price, double total, double remaining, double fee, String currency, Date created, Date updated, Date closed, String status, String symbol, String type) {
       this();
        this.timestamp = timestamp;
        this.id = id;
        this.order_type = order_type;
        this.lotSize = lotSize;
        this.price = price;
        this.total = total;
        this.remaining = remaining;
        this.fee = fee;
        this.currency = currency;
        this.created = created;
        this.updated = updated;
        this.closed = closed;
        this.status = status;
        this.symbol = symbol;
        this.type = type;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getId() {
        return String.valueOf(id);
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TRADE_ORDER_TYPE getOrder_type() {
        return order_type;
    }

    public void setOrder_type(TRADE_ORDER_TYPE order_type) {
        this.order_type = order_type;
    }

    public double getLotSize() {
        return lotSize;
    }

    public void setLotSize(double lotSize) {
        this.lotSize = lotSize;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public double getRemaining() {
        return remaining;
    }

    public void setRemaining(double remaining) {
        this.remaining = remaining;
    }

    public double getFee() {
        return fee;
    }

    public void setFee(double fee) {
        this.fee = fee;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public Date getClosed() {
        return closed;
    }

    public void setClosed(Date closed) {
        this.closed = closed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    Date updated;
     Date closed;
     String status;
     String symbol;
     String type;

}
