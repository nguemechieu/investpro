package org.investpro.investpro.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OrderBookEntry {

    private final double price;
    private final double size;

    public OrderBookEntry(double price, double size) {

        this.price = price;
        this.size = size;
    }

}
