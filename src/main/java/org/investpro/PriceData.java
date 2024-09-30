package org.investpro;

import java.time.Instant;

public class PriceData {
     Instant time;
    String symbol;

    public PriceData(Instant now, double lastPrice) {
        this.now = now;
        this.price = new Price(lastPrice,0,0);

    }
    Instant now;
     Price price;

    @Override
    public String toString() {
        return "PriceData{" +
                "time=" + time +
                ", symbol='" + symbol + '\'' +
                ", now=" + now +
                ", price=" + price +
                '}';
    }

    public PriceData(String symbol, double lastPrice, double bidPrice, double askPrice, Instant time) {
        this.symbol = symbol;
        this.price = new Price(lastPrice, bidPrice, askPrice);
        this.time = time;
    }
}
