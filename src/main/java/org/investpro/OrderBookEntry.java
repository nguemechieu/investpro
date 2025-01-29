package org.investpro;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OrderBookEntry {

    public OrderBookEntry(double price, double size) {

        this.price = price;
        this.size = size;
    }

    private double price;
    private double size;

}
