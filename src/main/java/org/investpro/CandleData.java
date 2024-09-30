package org.investpro;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * @author Noel Nguemechieu
 */
public class CandleData {
    private double openPrice=0;
    private double closePrice=0;
    private double highPrice=0;
    private double lowPrice=0;
    private int openTime=0;
    private static double volume=0;
    private double averagePrice=0;
    private double volumeWeightedAveragePrice=0;
    private boolean placeHolder=false;

    public CandleData(double openPrice, double closePrice, double highPrice, double lowPrice, int openTime,
                      double volume) {
        this(openPrice, closePrice, highPrice, lowPrice, openTime, volume, (highPrice + lowPrice) / 2,
                volume * ((highPrice + lowPrice) / 2), false);
    }

    public CandleData(double openPrice, double closePrice, double highPrice, double lowPrice, int openTime,
                      double volume, double averagePrice, double volumeWeightedAveragePrice, boolean placeHolder) {

        this.openPrice = openPrice;
        this.closePrice = closePrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.openTime = openTime;
        CandleData.volume = volume;
        this.averagePrice = averagePrice;
        this.volumeWeightedAveragePrice = volumeWeightedAveragePrice;
        this.placeHolder = placeHolder;
    }


    public CandleData(double aDouble, double aDouble1, double aDouble2, double aDouble3, int anInt, int closeTime, double volume) {
        this(aDouble, aDouble1, aDouble2, aDouble3, anInt, volume, (aDouble + aDouble1 + aDouble2 + aDouble3) / 4,
                volume * ((aDouble + aDouble1 + aDouble2 + aDouble3) / 4), false);
    }

    public CandleData(Instant instant, double closePrice, double highPrice, double lowPrice, double v, long volume) {

        this.openTime = instant.getNano();
        this.closePrice = closePrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        CandleData.volume = volume;
        this.averagePrice = (openPrice +  highPrice+ lowPrice+ closePrice) / 4;
        this.volumeWeightedAveragePrice = volume * ((openPrice+lowPrice+closePrice+highPrice) / 4);
        this.placeHolder = false;

    }

    public CandleData() {

    }


    @Contract("_, _, _, _, _ -> new")
    public static @NotNull CandleData of(long timestamp, double open, double high, double low, double close) {
        return new CandleData(open, high, low, close, Math.toIntExact(timestamp), volume, (open + high + low + close) / 4,
                (open + high + low + close) / 4 * volume, false);
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public double getHighPrice() {
        return highPrice;
    }

    public double getLowPrice() {
        return lowPrice;
    }

    public int getOpenTime() {
        return openTime;
    }

    public double getVolume() {
        return volume;
    }

    public double getAveragePrice() {
        return averagePrice;
    }

    public double getVolumeWeightedAveragePrice() {
        return volumeWeightedAveragePrice;
    }

    public boolean isPlaceHolder() {
        return placeHolder;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }else if (object == null || object.getClass() != getClass()) {
            return false;
        }
        CandleData other = (CandleData) object;

        return openPrice == other.openPrice &&
                closePrice == other.closePrice &&
                highPrice == other.highPrice &&
                lowPrice == other.lowPrice &&
                openTime == other.openTime &&
                averagePrice == other.averagePrice &&
                volumeWeightedAveragePrice == other.volumeWeightedAveragePrice &&
                placeHolder == other.placeHolder;
    }

    @Override
    public int hashCode() {
        return Objects.hash(openPrice, closePrice, highPrice, lowPrice, openTime, volume, averagePrice,
                volumeWeightedAveragePrice, placeHolder);
    }

    @Override
    public String toString() {
        return String.format("CandleData [openPrice = %f, closePrice = %f, highPrice = %f, lowPrice = %f, " +
                        "openTime = %d, volume = %f, placeHolder = %b]", openPrice, closePrice, highPrice, lowPrice,
                openTime, volume, placeHolder);
    }
}
