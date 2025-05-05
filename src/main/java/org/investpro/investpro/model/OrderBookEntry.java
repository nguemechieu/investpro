package org.investpro.investpro.model;

import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.ENUM_ORDER_TYPE;
import org.investpro.investpro.Side;

@Setter
@Getter
public class OrderBookEntry {

    double price;
    private final double size;

    public OrderBookEntry(double price, double size) {

        this.price = price;
        this.size = size;
    }


    ENUM_ORDER_TYPE buy;

    Side side;

    double amount;
}
