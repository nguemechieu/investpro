package org.investpro;


import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

public record InProgressCandleData(int openTime, double openPrice, double highPriceSoFar, double lowPriceSoFar,
                                   int currentTill, double lastPrice, double volumeSoFar) {

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
    public String toString() {
        return String.format("InProgressCandleData [openTime = %d, openPrice = %f, highPriceSoFar = %f, " +
                        "lowPriceSoFar = %f, currentTill = %d, lastPrice = %f, volumeSoFar = %f]", openTime, openPrice,
                highPriceSoFar, lowPriceSoFar, currentTill, lastPrice, volumeSoFar);
    }

    public double getLowPriceSoFar() {
        return lowPriceSoFar;
    }

    public double getVolumeSoFar() {
        return volumeSoFar;
    }

    public double getHighPriceSoFar() {
        return highPriceSoFar;
    }

    public double getLastPrice() {
        return lastPrice;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public long getCurrentTill() {
        return currentTill;
    }

    @Contract(value = " -> new", pure = true)
    public @NotNull Date getOpenTime() {
        return new Date(openTime);
    }

    @Contract(value = " -> new", pure = true)
    public @NotNull Date getCloseTime() {
        return new Date(openTime + currentTill);
    }
}
