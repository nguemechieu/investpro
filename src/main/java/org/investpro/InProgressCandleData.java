package org.investpro;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter

public class InProgressCandleData {
    private long openTime;
    private double openPrice;
    private double highPriceSoFar;
    private double lowPriceSoFar;
    private long currentTill;
    private double lastPrice;
    private double volumeSoFar;
    private double closePriceSoFar;
    private long closeTime;

    public InProgressCandleData(long openTime, double openPrice, double highPriceSoFar, double lowPriceSoFar,
                                long currentTill, double lastPrice, double volumeSoFar) {
        this.openTime = openTime;
        this.openPrice = openPrice;
        this.highPriceSoFar = highPriceSoFar;
        this.lowPriceSoFar = lowPriceSoFar;
        this.currentTill = currentTill;
        this.lastPrice = lastPrice;
        this.volumeSoFar = volumeSoFar;
    }


    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (object == null || object.getClass() != getClass()) {
            return false;
        }

        InProgressCandleData other = (InProgressCandleData) object;

        return openTime == other.openTime &&
                openPrice == other.openPrice &&
                highPriceSoFar == other.highPriceSoFar &&
                lowPriceSoFar == other.lowPriceSoFar &&
                currentTill == other.currentTill &&
                lastPrice == other.lastPrice &&
                volumeSoFar == other.volumeSoFar;

    }

    @Override
    public int hashCode() {
        return Objects.hash(openTime, openPrice, highPriceSoFar, lowPriceSoFar, currentTill, lastPrice, volumeSoFar);
    }

    @Override
    public String toString() {
        return String.format("InProgressCandleData [openTime = %d, openPrice = %f, highPriceSoFar = %f, " +
                        "lowPriceSoFar = %f, currentTill = %d, lastPrice = %f, volumeSoFar = %f]", openTime, openPrice,
                highPriceSoFar, lowPriceSoFar, currentTill, lastPrice, volumeSoFar);
    }

}