package org.investpro.investpro;

import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.model.TradePair;

@Setter
@Getter
public class OrderManager {

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

}
