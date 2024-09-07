package org.investpro;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "trades_response")
public class TradesResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "last_transaction_id", nullable = false)
    private String lastTransactionID;


    @JoinColumn(name = "trade_pair_id", nullable = false)
    // Ensure this column name matches the actual column in the 'trades_response' table
    private TradePair tradePair;

    @Column(nullable = false)
    private double price;

    @Column(nullable = false)
    private double size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP) // Adjust if you use Date type
    private Date timestamp;

    @Column(nullable = false)
    private double takeProfit;

    @Column(nullable = false)
    private double stopLoss;

    @Column(name = "local_trade_id", nullable = false)
    private long localTradeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private Side transactionType;

    @Column(nullable = false)
    private String instrument;

    @Column(nullable = false)
    private String state;

    @Column(name = "realized_pl", nullable = false)
    private double realizedPL;

    @Column(name = "unrealized_pl", nullable = false)
    private double unrealizedPL;

    @Column(nullable = false)
    private double financing;

    @Column(name = "current_units", nullable = false)
    private double currentUnits;

    @Column(name = "initial_units", nullable = false)
    private int initialUnits;

    @Column(name = "open_time", nullable = false)
    private LocalDateTime openTime;  // Changed from String to LocalDateTime for better date/time handling

    // Getters and Setters
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

    public TradePair getTradePair() {
        return tradePair;
    }

    public void setTradePair(TradePair tradePair) {
        this.tradePair = tradePair;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public Instant getTimestamp() {
        return timestamp.toInstant();
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = Date.from(timestamp);
    }

    public double getTakeProfit() {
        return takeProfit;
    }

    public void setTakeProfit(double takeProfit) {
        this.takeProfit = takeProfit;
    }

    public double getStopLoss() {
        return stopLoss;
    }

    public void setStopLoss(double stopLoss) {
        this.stopLoss = stopLoss;
    }

    public long getLocalTradeId() {
        return localTradeId;
    }

    public void setLocalTradeId(long localTradeId) {
        this.localTradeId = localTradeId;
    }

    public Side getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(Side transactionType) {
        this.transactionType = transactionType;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public double getRealizedPL() {
        return realizedPL;
    }

    public void setRealizedPL(double realizedPL) {
        this.realizedPL = realizedPL;
    }

    public double getUnrealizedPL() {
        return unrealizedPL;
    }

    public void setUnrealizedPL(double unrealizedPL) {
        this.unrealizedPL = unrealizedPL;
    }

    public double getFinancing() {
        return financing;
    }

    public void setFinancing(double financing) {
        this.financing = financing;
    }

    public double getCurrentUnits() {
        return currentUnits;
    }

    public void setCurrentUnits(double currentUnits) {
        this.currentUnits = currentUnits;
    }

    public int getInitialUnits() {
        return initialUnits;
    }

    public void setInitialUnits(int initialUnits) {
        this.initialUnits = initialUnits;
    }

    public LocalDateTime getOpenTime() {
        return openTime;
    }

    public void setOpenTime(LocalDateTime openTime) {
        this.openTime = openTime;
    }
}
