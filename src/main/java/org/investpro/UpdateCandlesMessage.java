package org.investpro;

public class UpdateCandlesMessage {

    public double open;
    public double high;
    public double low;
    public double close;
    public double volume;
    public long timestamp;
    public String exchange;
    public String pair;
    public String type;
    public String side;
    public double amount;
    public double price;

    public UpdateCandlesMessage(double open, double high, double low, double close, double volume, long timestamp, String exchange, String pair, String type, String side, double amount, double price) {

        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.timestamp = timestamp;
        this.exchange = exchange;
        this.pair = pair;
        this.type = type;
        this.side = side;
        this.amount = amount;
        this.price = price;
    }

    @Override
    public String toString() {
        return "UpdateCandlesMessage{" +

                ", open=" + open +
                ", high=" + high +
                ", low=" + low +
                ", close=" + close +
                ", volume=" + volume +
                ", timestamp=" + timestamp +
                ", exchange='" + exchange + '\'' +
                ", pair='" + pair + '\'' +
                ", type='" + type + '\'' +
                ", side='" + side + '\'' +
                ", amount=" + amount +
                ", price=" + price +
                '}';
    }

    public double getOpen() {
        return open;
    }

    public void setOpen(double open) {
        this.open = open;
    }

    public double getHigh() {
        return high;
    }

    public void setHigh(double high) {
        this.high = high;
    }

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getClose() {
        return close;
    }

    public void setClose(double close) {
        this.close = close;
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

    /**
     *
     */
    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getPair() {
        return pair;
    }

    public void setPair(String pair) {
        this.pair = pair;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }
}
