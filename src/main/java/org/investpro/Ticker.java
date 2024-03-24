package org.investpro;

public class Ticker {
    double lastPrice;
    double bidPrice;
    double askPrice;
    double volume;
    long timestamp;

    public Ticker() {
    }

    public Ticker(double lastPrice, double bidPrice, double askPrice, double volume, long timestamp) {
        super();
        this.lastPrice = lastPrice;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        this.volume = volume;
        this.timestamp = timestamp;
    }

    public double getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(double lastPrice) {
        this.lastPrice = lastPrice;
    }

    public double getBidPrice() {
        return bidPrice;
    }

    public void setBidPrice(double bidPrice) {
        this.bidPrice = bidPrice;
    }

    public double getAskPrice() {
        return askPrice;
    }

    public void setAskPrice(double askPrice) {
        this.askPrice = askPrice;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
