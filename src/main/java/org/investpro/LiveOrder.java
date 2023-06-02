package org.investpro;

public class LiveOrder {
    double price;
    double amount;
    double size;
    String symbol;
    String side;
    String type;
    String time;

    public LiveOrder(double price, double amount, double size, String symbol, String side, String type, String time) {
        this.price = price;
        this.amount = amount;
        this.size = size;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.time = time;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }


}
