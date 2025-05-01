package org.investpro;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jetbrains.annotations.NotNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 📌 **CandleData Entity**
 * - Stores candlestick chart data for financial instruments.
 * - Supports persistence via JPA (Hibernate).
 * - Used for OHLC (Open, High, Low, Close) chart representation.
 */

@Getter
@Setter
@Entity
@Table(name = "candles")  // Define table name in the database
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE) // Enable caching
public class CandleData {

    @Column(name = "open_price")
    protected double openPrice;
    @Column(name = "close_price")
    protected double closePrice;
    @Column(name = "high_price")
    protected double highPrice;
    @Column(name = "low_price")
    protected double lowPrice;
    @Column(name = "open_time")
    protected int openTime; // Unix timestamp
    @Column(name = "close_time")
    protected int closeTime; // Unix timestamp
    @Column(name = "volume")
    protected double volume;
    @Column(name = "timeframe")
    protected int timeframe;
    @Column(name = "highlighted")
    protected boolean highlighted;
    @Column(name = "place_holder")
    protected boolean placeHolder = false;
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO) // Auto-increment ID
    private Long id;

    // ✅ Default constructor (Required for JPA)
    public CandleData() {
        this.id = UUID.randomUUID().getLeastSignificantBits();
        this.openPrice = 0.0;
        this.closePrice = 0.0;
        this.highPrice = 0.0;
        this.lowPrice = 0.0;
        this.openTime = 0;
        this.closeTime = 0;
        this.volume = 0.0;
        this.placeHolder = false;
    }

    /**
     * **Primary Constructor**
     */
    public CandleData(double openPrice, double closePrice, double highPrice, double lowPrice, int openTime, int closeTime, double volume) {
        this.openPrice = openPrice;
        this.closePrice = closePrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.volume = volume;
    }

    public CandleData(double high, double low, double open, double close, double volume, @NotNull Instant time) {
        this.openPrice = open;
        this.closePrice = close;
        this.highPrice = high;
        this.lowPrice = low;
        this.openTime = (int) time.toEpochMilli();
        this.closeTime = time.getNano();
        this.volume = volume;

    }

    /**
     * **Factory Method**
     */
    @NotNull
    public static CandleData of(int timestamp, double open, double high, double low, double close, double volume) {
        return new CandleData(open, close, high, low, timestamp, timestamp + 60, volume);
    }

    /**
     * **Equals & HashCode**
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        CandleData other = (CandleData) object;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * **String Representation**
     */
    @Override
    public String toString() {
        return String.format(
                "CandleData [id=%d, open=%.2f, close=%.2f, high=%.2f, low=%.2f, openTime=%d, closeTime=%d, volume=%.2f]",
                id, openPrice, closePrice, highPrice, lowPrice, openTime, closeTime, volume
        );
    }

    public Double getMax() {
        return Double.max(openPrice, closePrice);
    }

    public double getMin() {
        return Double.min(openPrice, closePrice);
    }

    public void updateExtrema(@NotNull CandleData candleData1) {
        this.highPrice = Double.max(this.highPrice, candleData1.highPrice);
        this.lowPrice = Double.min(this.lowPrice, candleData1.lowPrice);
    }

    public boolean isComplete() {
        return closePrice != 0.0;
    }

    public InProgressCandle getSnapshot() {
        return new InProgressCandle(openTime, openPrice, highPrice, lowPrice, closeTime, closePrice, volume);
    }
    public void closeCandle(long closeTime, double closePrice) {
        this.closeTime = (int) closeTime;
        this.closePrice = closePrice;
    }
}
