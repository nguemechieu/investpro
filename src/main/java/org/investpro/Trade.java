package org.investpro;


import jakarta.persistence.*;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;

/**
 * A Trade represents a completed order (then called a trade), which is a transaction where one
 * party buys and the other one sells some amount of currency at a fixed price.
 *
 * @author Noel Nguemechieu
 */

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

    @Column(name = "price", nullable = false)
    @Convert(converter = MoneyConverter.class)
    private Money price;

    @Column(name = "amount", nullable = false)
    @Convert(converter = MoneyConverter.class)
    private Money amount;
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private Side transactionType;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;


    // Constructors, getters, and other methods...

    public Trade(Exchange exchange, TradePair tradePair, Money price, Money amount, Side transactionType,
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

    public Trade() {

    }

    public Trade(TradePair tradePair, Money price, Money size, Side side, long tradeId, Instant time) {
        this.tradePair = tradePair;
        this.price = price;
        this.amount = size;
        this.transactionType = side;
        this.localTradeId = tradeId;
        this.timestamp = time;
    }

    public Trade(double price, double qty, Instant time) throws SQLException, ClassNotFoundException {
        this.price =Money.of(BigDecimal.valueOf(price));
        this.amount = Money.of(BigDecimal.valueOf(qty));
        this.timestamp = time;
    }


    public TradePair getTradePair() {
        return tradePair;
    }

    public double getPrice() {
        return price.toDouble();
    }

    public void setPrice(Money price) {
        this.price = price;
    }

    public double getAmount() {
        return amount.toDouble();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setAmount(Money amount) {
        this.amount = amount;
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

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public @NotNull Trade getTrade() {
        return this;
    }

    public boolean isSell() {
        return transactionType == Side.SELL;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isBuy() {
        return transactionType == Side.BUY;
    }

    public void setTradePair(TradePair tradePair) {
        this.tradePair = tradePair;
    }


    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }
}
