package org.investpro;

import java.util.ArrayList;

public class OrderBookEntry {

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public OrderBookEntry(double price, double size) {

        this.price = price;
        this.size = size;
    }

    private double price;
    private double size;

}
