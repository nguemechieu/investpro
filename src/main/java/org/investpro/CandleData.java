package org.investpro;

import java.util.Objects;

/**
 * @author NOEL NGUEMECHIEU
 */
public class CandleData {
    private final double openPrice;
    private final double closePrice;
    private final double highPrice;
    private final double lowPrice;
    private final int openTime;
    private final double volume;
    private final double averagePrice;
    private final double volumeWeightedAveragePrice;
    private final boolean placeHolder;
    private int closeTime;

    public CandleData(double openPrice, double closePrice, double highPrice, double lowPrice, int openTime,
                      int closeTime,
                      double volume) {
        this(openPrice, closePrice, highPrice, lowPrice, openTime, closeTime, volume, (highPrice + lowPrice + openPrice + closePrice) / 4,
                volume, false);
        this.closeTime = closeTime;
    }

    public CandleData(double openPrice, double closePrice, double highPrice, double lowPrice, int openTime, int closeTime,
                      double volume, double averagePrice, double volumeWeightedAveragePrice, boolean placeHolder) {
        this.openPrice = openPrice;
        this.closePrice = closePrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.openTime = openTime;
        this.volume = volume;
        this.closeTime = closeTime;
        this.averagePrice = averagePrice;
        this.volumeWeightedAveragePrice = volumeWeightedAveragePrice;
        this.placeHolder = placeHolder;
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

    public Integer getOpenTime() {
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
                Objects.equals(closeTime, other.closeTime) &&
                volume == other.volume &&
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

    public Object getSignal() {
        return null;
    }

    public Object getMA200Price() {//Calculate MA

        double ma200Price = 0;

        for (int i = 0; i < 200; i++) {
            ma200Price += this.getClosePrice();
        }

        return ma200Price / 200;
    }

    public Object getMA50Price() {//Calculate MA

        double ma50Price = 0;

        for (int i = 0; i < 50; i++) {
            ma50Price += this.getClosePrice();
        }

        return ma50Price / 50;
    }

    public int getCloseTime() {
        return closeTime;
    }


    // return new Date(this.openTime);}
}
