package org.investpro;

import java.util.Objects;


public class InProgressCandle {
    private int openTime;
    private double openPrice;
    private double highPriceSoFar;
    private double lowPriceSoFar;
    private int currentTill;
    private double lastPrice;
    private double volumeSoFar;
    private boolean visible; // is the in-progress candle currently visible on screen?
    private boolean placeHolder;

    private int closeTime;
    private double closePriceSoFar;

    public int getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(int closeTime) {
        this.closeTime = closeTime;
    }


    public CandleData snapshot() {
        return new CandleData(openPrice, lastPrice, highPriceSoFar, lowPriceSoFar, openTime, volumeSoFar);
    }

    public int getOpenTime() {
        return openTime;
    }

    public void setOpenTime(int openTime) {
        this.openTime = openTime;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(double openPrice) {
        this.openPrice = openPrice;
    }

    public double getHighPriceSoFar() {
        return highPriceSoFar;
    }

    public void setHighPriceSoFar(double highPriceSoFar) {
        this.highPriceSoFar = highPriceSoFar;
    }

    public double getLowPriceSoFar() {
        return lowPriceSoFar;
    }

    public void setLowPriceSoFar(double lowSoFar) {
        this.lowPriceSoFar = lowSoFar;
    }

    public int getCurrentTill() {
        return currentTill;
    }

    public void setCurrentTill(int currentTill) {
        this.currentTill = currentTill;
    }

    public double getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(double lastPrice) {
        this.lastPrice = lastPrice;
    }

    public double getVolumeSoFar() {
        return volumeSoFar;
    }

    public void setVolumeSoFar(double volumeSoFar) {
        this.volumeSoFar = volumeSoFar;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setIsPlaceholder(boolean isPlaceholder) {
        this.placeHolder = isPlaceholder;
    }

    @Override
    public String toString() {
        return String.format("InProgressCandle [openTime = %d, openPrice = %f, highPriceSoFar = %f, " +
                        "lowPriceSoFar = %f, currentTill = %d, lastPrice = %f, volumeSoFar = %f, visible = %b, " +
                        "placeHolder = %b]", openTime, openPrice, highPriceSoFar, lowPriceSoFar, currentTill,
                lastPrice, volumeSoFar, visible, placeHolder);
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

    public void setOpenPriceSoFar(int i) {
        this.openPrice = i;
    }

    public double getClosePriceSoFar() {
        return closePriceSoFar;
    }

    public void setClosePriceSoFar(double i) {
        this.closePriceSoFar = i;
    }

    public void setOpenTimeSoFar(int i) {
        this.openTime = i;
    }

    public void setCloseTimeSoFar(int i) {
        this.closeTime = i;
    }

    public double getTotalVolume() {

        return volumeSoFar * currentTill;


    }

    public double getVolume24h() {
        return volumeSoFar * 24;
    }

    public double getHighPrice24h() {
        return highPriceSoFar * 24;
    }

    public double getLowPrice24h() {
        return lowPriceSoFar * 24;
    }

    public void setVolume(double volume) {
        this.volumeSoFar = volume;
    }

    public void setClosePrice(double closePrice) {
        this.closePriceSoFar = closePrice;
    }

    public void setHighestBidPrice(double highestBidPrice) {
        this.highPriceSoFar = highestBidPrice;
    }

    public double getAveragePriceSofar() {
        return (highPriceSoFar + lowPriceSoFar) / 2;
    }
}
