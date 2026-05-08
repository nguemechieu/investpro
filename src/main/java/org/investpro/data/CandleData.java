package org.investpro.data;


import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * @author NOEL NGUEMECHIEU
 */

public record CandleData(double openPrice, double closePrice, double highPrice, double lowPrice, int openTime,
                         double volume, double averagePrice, double volumeWeightedAveragePrice, boolean placeHolder) {
    public CandleData(double openPrice, double closePrice, double highPrice, double lowPrice, int openTime,
                      double volume) {
        this(openPrice, closePrice, highPrice, lowPrice, openTime, volume, (highPrice + lowPrice + openPrice + closePrice) / 4,
                volume, false);
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (object == null || object.getClass() != getClass()) {
            return false;
        }

        CandleData other = (CandleData) object;

        return openPrice == other.openPrice &&
                closePrice == other.closePrice &&
                highPrice == other.highPrice &&
                lowPrice == other.lowPrice &&
                openTime == other.openTime &&
                volume == other.volume &&
                averagePrice == other.averagePrice &&
                volumeWeightedAveragePrice == other.volumeWeightedAveragePrice &&
                placeHolder == other.placeHolder;
    }

    @Override
    public @NotNull String toString() {
        return String.format("CandleData [openPrice = %f, closePrice = %f, highPrice = %f, lowPrice = %f, " +
                        "openTime = %d, volume = %f, placeHolder = %b]", openPrice, closePrice, highPrice, lowPrice,
                openTime, volume, placeHolder);
    }


    public long getOpenTime() {
        return openTime;
    }



    public Instant timestamp() {

        return  Instant.now();
    }
}
