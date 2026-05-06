package org.investpro.models.trading;

import lombok.Data;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.currency.Money;
import org.investpro.utils.Side;

import java.time.Instant;
import java.util.Objects;

/**
 * A Trade represents a completed order (then called a trade), which is an
 * transaction where one
 * party buys and the other one sells some amount of currency at a fixed price.
 *
 *
 */
@Data
@Slf4j
public class Trade {
    // Explicit getters (Lombok @Getter not being invoked during compilation)
    private TradePair tradePair;
    private double price;
    private double amount;
    private Side transactionType;
    private long localTradeId;
    private Instant timestamp;
    private double fee;

    // Additional trading fields
    private double stopLoss;
    private double takeProfit;
    private double swap;
    private double profit;

    public Trade() {
        super();
    }

    public Trade(TradePair tradePair, double price, double size, Side side, long tradeId, Instant time) {
        this.tradePair = tradePair;
        this.price = price;
        this.amount = size;
        this.transactionType = side;
        this.localTradeId = tradeId;
        this.timestamp = time;
        log.debug("{this}");
    }

    /**
     * Represents one line of the raw trade data. We use doubles because the results
     * don't need to be
     * *exact* (i.e. small rounding errors are fine), and we want to favor speed.
     */

    // 1315922016,5.800000000000,1.000000000000
    public Trade(TradePair tradePair, int timestamp, Money price, double amount, Side transactionType,
            long localTradeId, double fee) {
        this.tradePair = tradePair;
        this.timestamp = Instant.ofEpochSecond(timestamp);
        this.price = price.toDouble();
        this.amount = amount;
        this.transactionType = transactionType;
        this.localTradeId = localTradeId;
        this.fee = fee;
    }

    @Override
    public String toString() {
        return String.format("Trade [tradePair = %s, price = %s, amount = %s, transactionType = %s, localId = %s, " +
                "timestamp = %s, fee = %s]", tradePair, price, amount, transactionType, localTradeId, timestamp, fee);
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

}
