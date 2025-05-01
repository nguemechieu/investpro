package org.investpro.investpro.model;

public class Price {
    public static double bidPrice;
    public static double askPrice;
    static double lastPrice;
    public Price(double lastPrice, double bidPrice, double askPrice) {
        Price.lastPrice = lastPrice;
        Price.bidPrice = bidPrice;
        Price.askPrice = askPrice;
    }


}
