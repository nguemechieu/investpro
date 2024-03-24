package org.investpro;
import java.time.Instant;
import java.util.Objects;

/**
 * A Trade represents a completed order (then called a trade), which is an transaction where one
 * party buys and the other one sells some amount of currency at a fixed price.
 *

 */
public class Trade {
    private TradePair tradePair;
    private Money price;
    private Money amount;
    private Side transactionType;
    private long localTradeId;
    private Instant timestamp;
    private Money fee;

    public Trade() {
        super();
    }

    public Trade(TradePair tradePair, Money price, Money size, Side side, long tradeId, Instant time) {
        this.tradePair = tradePair;
        this.price = price;
        this.amount = size;
        this.transactionType = side;
        this.localTradeId = tradeId;
        this.timestamp = time;
    }

    /**
     * Represents one line of the raw trade data. We use doubles because the results don't need to be
     * *exact* (i.e. small rounding errors are fine), and we want to favor speed.
     */

    // 1315922016,5.800000000000,1.000000000000
    public Trade(TradePair tradePair, int timestamp, Money price, Money amount, Side transactionType, long localTradeId, Money fee) {
        this.tradePair = tradePair;
        this.timestamp = Instant.ofEpochSecond(timestamp);
        this.price = price;
        this.amount = amount;
        this.transactionType = transactionType;
        this.localTradeId = localTradeId;
        this.fee = fee;
    }

    public void setTradePair(TradePair tradePair) {
        this.tradePair = tradePair;
    }

    public void setPrice(Money price) {
        this.price = price;
    }

    public void setAmount(Money amount) {
        this.amount = amount;
    }

    public void setTransactionType(Side transactionType) {
        this.transactionType = transactionType;
    }

    public long getLocalTradeId() {
        return localTradeId;
    }

    public void setLocalTradeId(long localTradeId) {
        this.localTradeId = localTradeId;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Money getFee() {
        return fee;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("Trade [tradePair = %s, price = %s, amount = %s, transactionType = %s, localId = %s, " +
                "timestamp = %s, fee = %s]", tradePair, price, amount, transactionType, localTradeId, timestamp, fee);
    }

    public void setFee(Money fee) {
        this.fee = fee;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, price, amount);
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (object == null || object.getClass() != getClass()) {
            return false;
        }

        Trade other = (Trade) object;

        return Objects.equals(timestamp, other.timestamp) &&
                Objects.equals(price, other.price) &&
                Objects.equals(amount, other.amount);
    }


    public Side getTransactionType() {
        return transactionType;

    }

    public Long getLocalId() {
        return localTradeId;
    }
}
