package org.investpro;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

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

public class CandleData {


    private Long id;


    private double openPrice;

    private double closePrice;


    private double highPrice;


    private double lowPrice;


    private int openTime; // Changed to long for better timestamp storage

    private int closeTime; // Changed to long

    private double volume; // Removed static


    private boolean placeHolder = false;
    @Setter
    private boolean highlighted;


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
     * - Initializes a CandleData instance with core OHLC values.
     */
    public CandleData(double openPrice, double closePrice, double highPrice, double lowPrice, int openTime, int closeTime, double volume) {

        this.id = UUID.randomUUID().getLeastSignificantBits();
        this.openPrice = openPrice;
        this.closePrice = closePrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.volume = volume;

    }


    /**
     * **Factory Method**
     * - Creates a CandleData instance based on a timestamp.
     */
    @Contract("_, _, _, _, _, _ -> new")
    public static @NotNull CandleData of(int timestamp, double open, double high, double low, double close, double volume) {
        return new CandleData(open, close, high, low, timestamp, timestamp + 60, volume);
    }

    /**
     * **Equals & HashCode**
     * - Uses ID as the unique identifier.
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
     * - Formats `CandleData` object for logging/debugging.
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
}
