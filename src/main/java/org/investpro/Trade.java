package org.investpro;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

/**
 * A Trade represents a completed order (then called a trade), which is a transaction where one
 * party buys and the other one sells some amount of currency at a fixed price.
 *
 * @author Noel Nguemechieu
 */
@Getter
@Setter

@NoArgsConstructor
public class Trade {


    TradePair tradePair;
    Exchange exchange;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "local_trade_id", nullable = false)
     long localTradeId;
    Money fee;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    private double price;

    private double amount;

    private Side transactionType;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;


    // Constructors, getters, and other methods...

    public Trade(Exchange exchange, TradePair tradePair, double price, double amount, Side transactionType,
                 long localTradeId, Instant timestamp, Money fee) {
        this.tradePair = tradePair;
        this.price = price;
        this.amount = amount;
        this.transactionType = transactionType;
        this.localTradeId = localTradeId;
        this.timestamp = timestamp;
        this.fee = fee;
        this.exchange = exchange;
    }


    public Trade(TradePair tradePair, double price, double size, Side side, long tradeId, Instant time) {
        this.tradePair = tradePair;
        this.price = price;
        this.amount = size;
        this.transactionType = side;
        this.localTradeId = tradeId;
        this.timestamp = time;
    }

    public Trade(double price, double qty, Instant time) throws Exception {
        this.price = price;
        this.amount = qty;
        this.timestamp = time;
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


}
