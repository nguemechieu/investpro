package org.investpro;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Objects;


public class Trade {
    public static CandleData candle;
     Side side;
    private JSONObject trade;
    private TradePair tradePair;
    private Money price;
    private Money amount;

    private long localTradeId;
    private Instant timestamp;
    private Money fee;

    public Trade(TradePair tradePair, Money price, Money amount, Side side,
                 long localTradeId, Instant timestamp, Money fee) {
        this.tradePair = tradePair;
        this.price = price;
        this.side = side;
        this.amount = amount;
        this.localTradeId = localTradeId;
        this.timestamp = timestamp;
        this.fee = fee;
    }


    public Trade(TradePair tradePair, Money price, Money amount, Side transactionType,
                 long localTradeId, long timestamp) {
        this(tradePair, price, amount, transactionType, localTradeId, Instant.ofEpochSecond(timestamp),
                DefaultMoney.NULL_MONEY);
    }

    public Trade(TradePair tradePair, Money price, Money amount, Side transactionType,
                 long localTradeId, long timestamp, Money fee) {
        this(tradePair, price, amount, transactionType, localTradeId, Instant.ofEpochSecond(timestamp), fee);
    }

    public Trade(int i, JSONObject trade) {
        this.trade = trade;

    }

    public Trade(@NotNull TradePair tradePair, Money price, Money size,Side side, long id, Instant time) {

        this.tradePair = tradePair;
        this.price = price;
        this.amount = size;

        this.side = side;
        this.localTradeId = id;
        this.timestamp = time;
        this.fee = DefaultMoney.NULL_MONEY;

    }

    public Trade(String symbol, String id, double price, double amount, String side, String time) {
        this.tradePair = new TradePair(Currency.of(symbol.substring(0, 3)), Currency.of(symbol.substring(3, symbol.length()-1)));
        this.price = DefaultMoney.ofFiat(price, "USD");
        this.amount = DefaultMoney.ofFiat(amount, "USD");
        this.side = Side.valueOf(side);
        this.timestamp = Instant.parse(time);
        this.fee = DefaultMoney.NULL_MONEY;
        this.localTradeId = Long.parseLong(id);
    }

    public TradePair getTradePair() {
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



    @Override
    public String toString() {
        return String.format("Trade [tradePair = %s, price = %s, amount = %s, transactionType = %s, localId = %s, " +
                "timestamp = %s, fee = %s]", tradePair, price, amount
                , localTradeId, timestamp, fee);
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
                && side == other.side
                && localTradeId == other.localTradeId
                && Objects.equals(timestamp, other.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradePair, price, amount, side, localTradeId, timestamp);
    }

    public JSONObject getTrade() {
        return trade;
    }

    public void setTrade(JSONObject trade) {
        this.trade = trade;
    }


}
