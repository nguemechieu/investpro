package org.investpro;

import java.util.TreeMap;

public class OrderManager {

    TreeMap<Long, Order> ordersTreeMap = new TreeMap<>();
    long Placed;
    TradePair Pair;
    Side Type;
    double
            Price;
    double Amount;
    Boolean Fills;
    boolean Filled;
    long Total;
    String Status;

    public OrderManager() {
    }

    public long getPlaced() {
        return Placed;
    }

    public void setPlaced(long placed) {
        Placed = placed;
    }

    public TradePair getPair() {
        return Pair;
    }

    public void setPair(TradePair pair) {
        Pair = pair;
    }

    public Side getType() {
        return Type;
    }

    public void setType(Side type) {
        Type = type;
    }

    public double getPrice() {
        return Price;
    }

    public void setPrice(double price) {
        Price = price;
    }

    public double getAmount() {
        return Amount;
    }

    public void setAmount(double amount) {
        Amount = amount;
    }

    public Boolean getFills() {
        return Fills;
    }

    public void setFills(Boolean fills) {
        Fills = fills;
    }

    public boolean isFilled() {
        return Filled;
    }

    public void setFilled(boolean filled) {
        Filled = filled;
    }

    public long getTotal() {
        return Total;
    }

    public void setTotal(long total) {
        Total = total;
    }

    public String getStatus() {
        return Status;
    }

    public void setStatus(String status) {
        Status = status;
    }
}
