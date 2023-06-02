package org.investpro;

import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;

import java.util.Date;
import java.util.Objects;


public class CandleData extends RecursiveTreeObject<CandleData> {
    private final double averagePrice = -1;
    public int closeTime = -1;
    private int openTime = -1;
    private double openPrice = -1;
    private double closePrice = -1;
    private double highPrice = -1;
    private double lowPrice = -1;
    private double volume = -1;
    private double volumeWeightedAveragePrice;
    private boolean placeHolder;

    public CandleData(double openPrice, double closePrice, double highPrice, double lowPrice, int openTime,
                      double volume) {


        if (openTime == -1 && openPrice == 1 && closePrice == -1 && highPrice == -1 && lowPrice == -1 && volume == -1) {

            throw new IllegalArgumentException("Invalid CandleData openTime: " + openTime + " closeTime: " + closePrice + " highPrice: " + highPrice + " lowPrice: " + lowPrice);

        }

        this.openTime = openTime;
        this.openPrice = openPrice;
        this.closePrice = closePrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.volume = volume;
    }


    public int getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(int closeTime) {
        this.closeTime = closeTime;
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
        }

        if (object == null || object.getClass() != getClass()) {
            return false;
        }

        CandleData other = (CandleData) object;

        return openPrice == other.openPrice && closePrice == other.closePrice && highPrice == other.highPrice && lowPrice == other.lowPrice && Objects.equals(openTime, other.openTime) && volume == other.volume && volumeWeightedAveragePrice == other.volumeWeightedAveragePrice && placeHolder == other.placeHolder;
    }

    @Override
    public int hashCode() {
        return Objects.hash(openPrice, closePrice, highPrice, lowPrice, openTime, volume, averagePrice,
                volumeWeightedAveragePrice, placeHolder);
    }

    @Override
    public String toString() {

        return String.format("Open = %f, Close= %f, High= %f, Low = %f, " +
                        "OpenTime = %s, Volume = %f", openPrice, closePrice, highPrice, lowPrice,
                new Date(openTime), volume);
    }


}
