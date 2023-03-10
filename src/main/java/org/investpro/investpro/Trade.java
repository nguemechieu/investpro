package org.investpro.investpro;

import org.json.JSONObject;

import java.time.Instant;
import java.util.Objects;


public class Trade {
    public static CandleData candle;
    private JSONObject trade;
    private String tradePair;
    private Money price;
    private Money amount;
    private Side transactionType;
    private long localTradeId;
    private Instant timestamp;
    private Money fee;

    public Trade(String tradePair, Money price, Money amount, Side transactionType,
                 long localTradeId, Instant timestamp, Money fee) {
        this.tradePair = tradePair;
        this.price = price;
        this.amount = amount;
        this.transactionType = transactionType;
        this.localTradeId = localTradeId;
        this.timestamp = timestamp;
        this.fee = fee;
    }

    public Trade(String tradePair, Money price, Money amount, Side transactionType,
                 long localTradeId, Instant timestamp) {
        this(tradePair, price, amount, transactionType, localTradeId,
                timestamp, DefaultMoney.NULL_MONEY);
    }

    public Trade(String tradePair, Money price, Money amount, Side transactionType,
                 long localTradeId, long timestamp) {
        this(tradePair, price, amount, transactionType, localTradeId, Instant.ofEpochSecond(timestamp),
                DefaultMoney.NULL_MONEY);
    }

    public Trade(String tradePair, Money price, Money amount, Side transactionType,
                 long localTradeId, long timestamp, Money fee) {
        this(tradePair, price, amount, transactionType, localTradeId, Instant.ofEpochSecond(timestamp), fee);
    }

    public Trade(int i, JSONObject trade) {
        this.trade = trade;
    }

    public String getTradePair() {
        return tradePair;
    }

    public Money getPrice() {
        return price;
    }

    public Money getAmount() {
        return amount;
    }

    /**
     * Returns the total amount of money this trade was, i.e. {@literal price * amount} in price
     * units.
     *
     * @return
     */
    public Money getTotal() {
        // TODO implement multiply method in Money..but think of how to do it with
        // different currencies..maybe involve a TradePair? btc * usd/btc = usd, which
        // is technically what we are doing here
        return DefaultMoney.ofFiat(price.toBigDecimal().multiply(amount.toBigDecimal()), "USD");
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Side getTransactionType() {
        return transactionType;
    }

    @Override
    public String toString() {
        return String.format("Trade [tradePair = %s, price = %s, amount = %s, transactionType = %s, localId = %s, " +
                "timestamp = %s, fee = %s]", tradePair, price, amount, transactionType, localTradeId, timestamp, fee);
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (object == null || object.getClass() != this.getClass()) {
            return false;
        }

        Trade other = (Trade) object;

        return Objects.equals(tradePair, other.tradePair)
                && Objects.equals(price, other.price)
                && Objects.equals(amount, other.amount)
                && transactionType == other.transactionType
                && localTradeId == other.localTradeId
                && Objects.equals(timestamp, other.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradePair, price, amount, transactionType, localTradeId, timestamp);
    }

    public JSONObject getTrade() {
        return trade;
    }

    public void setTrade(JSONObject trade) {
        this.trade = trade;
    }
}
