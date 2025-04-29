package org.investpro;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
public class InProgressCandle {
    private final List<CandleData> candleData=new ArrayList<>();
    private long openTime;
    private double openPrice;
    private double highPriceSoFar;
    private double lowPriceSoFar;
    private int currentTill;
    private double closePriceSoFar;
    private double volumeSoFar;
    private boolean visible; // is the in-progress candle currently visible on screen?
    private boolean placeHolder;


    private boolean closed; // Tracks whether the candle is closed
    private long closeTime; // Records the time when the candle closes

    public InProgressCandle() {
        candleData.add(new CandleData());
        this.openTime = candleData.getFirst().getOpenTime();
        this.openPrice = candleData.getFirst().getOpenPrice();
        this.highPriceSoFar = candleData.getFirst().getHighPrice();
        this.lowPriceSoFar = candleData.getFirst().getLowPrice();
        this.currentTill = candleData.getFirst().getCloseTime();
        this.closePriceSoFar = candleData.getFirst().getClosePrice();
        this.volumeSoFar = candleData.getFirst().getVolume();
        this.visible = true;
        this.placeHolder = false;
        this.closed = false;
        this.closeTime = 0; // Initialize closeTime to 0
    }

    public InProgressCandle(int openTime, double openPrice, double highPrice, double lowPrice, int closeTime, double closePrice, double volume) {
        this.openTime = openTime;
        this.openPrice = openPrice;
        this.highPriceSoFar = highPrice;
        this.lowPriceSoFar = lowPrice;
        this.currentTill = closeTime;
        this.closePriceSoFar = closePrice;
        this.volumeSoFar = volume;
        this.visible = true;
        this.placeHolder = false;
        this.closed = false;
        this.closeTime = 0; // Initialize closeTime to 0
    }

    /**
     * Creates a new (immutable) {@code CandleData} by copying the fields from this {@code InProgressCandle}.
     * This in effect creates a frozen "snapshot" of the in-progress candle data. This is useful when the current
     * time passes the close time of the current in-progress candle, and it needs to be added to a chart's data set.
     */
    public CandleData snapshot() {
        return new CandleData(openPrice, highPriceSoFar, lowPriceSoFar, closePriceSoFar, Math.toIntExact(openTime), Math.toIntExact(closeTime), volumeSoFar);
    }

    /**
     * Marks this candle as closed and sets the closing price and time.
     *
     * @param closeTime  The timestamp when the candle closed.
     * @param closePrice The closing price of the candle.
     */
    public void closeCandle(long closeTime, double closePrice) {
        if (!closed) {
            this.closeTime = closeTime;
            this.closePriceSoFar = closePrice;
            this.closed = true;
        }
    }

    public void setIsPlaceholder(boolean isPlaceholder) {
        this.placeHolder = isPlaceholder;
    }

    @Override
    public String toString() {
        return String.format("InProgressCandle [openTime = %d, openPrice = %f, highPriceSoFar = %f, " +
                        "lowPriceSoFar = %f, currentTill = %d, closePrice = %f, volumeSoFar = %f, visible = %b, " +
                        "placeHolder = %b, closed = %b, closeTime = %d]",
                openTime, openPrice, highPriceSoFar, lowPriceSoFar, currentTill,
                closePriceSoFar, volumeSoFar, visible, placeHolder, closed, closeTime);
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (object == null || object.getClass() != getClass()) {
            return false;
        }

        InProgressCandle other = (InProgressCandle) object;

        return Objects.equals(openTime, other.openTime) &&
                Objects.equals(openPrice, other.openPrice) &&
                Objects.equals(highPriceSoFar, other.highPriceSoFar) &&
                Objects.equals(lowPriceSoFar, other.lowPriceSoFar) &&
                Objects.equals(currentTill, other.currentTill) &&
                Objects.equals(closePriceSoFar, other.closePriceSoFar) &&
                Objects.equals(volumeSoFar, other.volumeSoFar) &&
                Objects.equals(visible, other.visible) &&
                placeHolder == other.placeHolder &&
                closed == other.closed &&
                Objects.equals(closeTime, other.closeTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(openTime, openPrice, highPriceSoFar, lowPriceSoFar, currentTill,
                closePriceSoFar, volumeSoFar, visible, placeHolder, closed, closeTime);
    }

    /**
     * Calculates the average price of the candle so far.
     *
     * @return The average price based on available data.
     */
    public double getAveragePriceSoFar() {
        return (openPrice + highPriceSoFar + lowPriceSoFar + closePriceSoFar) / 4;
    }

    public void set(CandleData candleData) {
        this.openTime = candleData.getOpenTime();
        this.openPrice = candleData.getOpenPrice();
        this.highPriceSoFar = candleData.getHighPrice();
        this.lowPriceSoFar = candleData.getLowPrice();
        this.currentTill = candleData.getCloseTime();
        this.closePriceSoFar = candleData.getClosePrice();
        this.volumeSoFar = candleData.getVolume();
        this.visible = true;
        this.placeHolder = false;
        this.closed = false;
        this.closeTime = 0; // Initialize closeTime to 0
    }
    double bid;
    double ask;

    public void setTimestamp(@NotNull Instant now) {
        this.currentTill = now.getNano(); //
    }

    public double getLastPrice() {
        return closePriceSoFar;
    }

    public void setLastPrice(double v) {
        this.closePriceSoFar = v;
    }
}