package org.investpro;

import jakarta.persistence.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "last_transaction_id")
    private String lastTransactionID;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "account", fetch = FetchType.LAZY)
    private List<Order> orders;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "account", fetch = FetchType.LAZY)
    private List<Trade> trades;

    public Account() {
    }

    public Account(Long id, String lastTransactionID, List<Order> orders, List<Trade> trades) {
        this.id = id;
        this.lastTransactionID = lastTransactionID;
        this.orders = orders;
        this.trades = trades;


        for (Order order : orders) {
            order.setAccount(this);
        }

        for (Trade trade : trades) {
            trade.setAccount(this);
        }

    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLastTransactionID() {
        return lastTransactionID;
    }

    public void setLastTransactionID(String lastTransactionID) {
        this.lastTransactionID = lastTransactionID;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public void setOrders(List<Order> orders) {
        this.orders = orders;
    }

    public List<Trade> getTrades() {
        return trades;
    }

    public void setTrades(List<Trade> trades) {
        this.trades = trades;
    }

    public void clear() {
        this.orders.clear();
        this.trades.clear();
        this.lastTransactionID = null;
    }

    public void add(@NotNull Account account) {
        this.orders.addAll(account.getOrders());
        this.trades.addAll(account.getTrades());
    }
}
