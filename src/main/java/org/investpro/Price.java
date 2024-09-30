package org.investpro;

public class Price {
    public Price(double lastPrice, double bidPrice, double askPrice) {
        Price.lastPrice = lastPrice;
        Price.bidPrice = bidPrice;
        Price.askPrice = askPrice;
    }
    static double lastPrice;
     static double bidPrice;
    static double askPrice;



}
