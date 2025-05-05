package org.investpro.investpro.model;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

@Getter
@Setter
@ToString

public class InProgressCandle {
    private int openTime;
    private double openPrice;
    @Setter
    @Getter
    private double highPriceSoFar;
    private double lowPriceSoFar;
    private int currentTill;
    private double lastPrice;
    private double volumeSoFar;
    private boolean visible; // is the in-progress candle currently visible on screen?
    private boolean placeHolder;


    /**
     * Creates a new (immutable) {@code CandleData} by copying the fields from this {@code InProgressCandle}.
     * This in effect creates a frozen "snapshot" of the in-progress candle data. This is useful when the current
     * time passes the close time of the current in-progress candle ,and it needs to be added to a chart's data set.
     */
    public CandleData snapshot() {
        return new CandleData(openPrice, lastPrice, highPriceSoFar, lowPriceSoFar, openTime, volumeSoFar);
    }

    public void setIsPlaceholder(boolean isPlaceholder) {
        this.placeHolder = isPlaceholder;
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
                Objects.equals(lastPrice, other.lastPrice) &&
                Objects.equals(volumeSoFar, other.volumeSoFar) &&
                Objects.equals(visible, other.visible) &&
                placeHolder == other.placeHolder;
    }

    @Override
    public int hashCode() {
        return Objects.hash(openTime, openPrice, highPriceSoFar, lowPriceSoFar, currentTill, lastPrice, volumeSoFar,
                visible, placeHolder);
    }

    public void setCloseTime(int i) {

        this.currentTill = i;
    }
}