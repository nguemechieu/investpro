package org.investpro;

import java.util.Date;

public class Orders {

    String Symbol;
    double Quantity;
    double Price;
    double Commission;
    double TakeProfit;
    double StopLoss;
    double swap;
    double profit;
    private String status;
    private Date date;
    private String Type;

    public Orders(Date date, String type, String symbol, double quantity, double price, double commission, double takeProfit, double stopLoss, double swap, double profit) {
        this.date = date;
        Type = type;
        Symbol = symbol;
        Quantity = quantity;
        Price = price;
        Commission = commission;
        TakeProfit = takeProfit;
        StopLoss = stopLoss;
        this.swap = swap;
        this.profit = profit;
    }

    public String getType() {
        return Type;
    }

    public void setType(String type) {
        Type = type;
    }

    public String getSymbol() {
        return Symbol;
    }

    public void setSymbol(String symbol) {
        Symbol = symbol;
    }

    public double getQuantity() {
        return Quantity;
    }

    public void setQuantity(double quantity) {
        Quantity = quantity;
    }

    public double getPrice() {
        return Price;
    }

    public void setPrice(double price) {
        Price = price;
    }

    public double getCommission() {
        return Commission;
    }

    public void setCommission(double commission) {
        Commission = commission;
    }

    public double getTakeProfit() {
        return TakeProfit;
    }

    public void setTakeProfit(double takeProfit) {
        TakeProfit = takeProfit;
    }

    public double getStopLoss() {
        return StopLoss;
    }

    public void setStopLoss(double stopLoss) {
        StopLoss = stopLoss;
    }

    public double getSwap() {
        return swap;
    }

    public void setSwap(double swap) {
        this.swap = swap;
    }

    public double getProfit() {
        return profit;
    }

    public void setProfit(double profit) {
        this.profit = profit;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
