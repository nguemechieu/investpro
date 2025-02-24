package org.investpro;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * A Trade represents a completed order (then called a trade), which is a transaction where one
 * party buys and the other one sells some amount of currency at a fixed price.
 *
 * @author Noel Nguemechieu
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Trade {


    private Side side;
    private ENUM_ORDER_TYPE order_type;
    private List<Double> prices;
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
    List<Trade> tradeList = new ArrayList<>();
    private double price;

    private double amount;


    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;


    // Constructors, getters, and other methods...
    private double stopLoss;
    private double takeProfit;

    public Trade(Exchange exchange, TradePair tradePair, double price, double amount, Side transactionType,
                 long localTradeId, Instant timestamp, Money fee) {
        this.tradePair = tradePair;
        this.price = price;
        this.amount = amount;
        this.side = transactionType;
        this.localTradeId = localTradeId;
        this.timestamp = timestamp;
        this.fee = fee;
        this.exchange = exchange;
    }
    SIGNAL signal;


    public Trade(TradePair tradePair, double price, double size, Side side, long tradeId, Instant time) {
        this.tradePair = tradePair;
        this.price = price;
        this.amount = size;
        this.side = side;
        this.localTradeId = tradeId;
        this.timestamp = time;
    }
    public Trade(Double price, double qty, Instant time) throws Exception {
        this.price = price;
        this.amount = qty;
        this.timestamp = time;
    }

    public Trade(Exchange exchange, TradePair tradePair, Side side, ENUM_ORDER_TYPE enumOrderType, double price, double amount, @NotNull Date timestamp, double stopLoss, double takeProfit) {
        this.tradePair = tradePair;
        this.price = price;
        this.amount = amount;
        this.side = side;
        this.timestamp = timestamp.toInstant();
        this.order_type = enumOrderType;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.exchange = exchange;
    }


    public Trade(TradePair tradePair, double price, double size, Side side, Instant timestamp) {
        this.tradePair = tradePair;
        this.price = price;
        this.amount = size;
        this.timestamp = timestamp;
        this.side = side;

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
                && Objects.equals(timestamp, other.timestamp)
                && Objects.equals(stopLoss, other.stopLoss)
                && Objects.equals(takeProfit, other.takeProfit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradePair, price, amount, side, localTradeId, timestamp);
    }


}
