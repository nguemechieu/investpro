package org.investpro;

public record InProgressCandleData(int openTime, double openPrice, double highPriceSoFar, double lowPriceSoFar,
                                   int closeTime, double lastPrice, double volumeSoFar) {



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
                closeTime == other.closeTime &&
                lastPrice == other.lastPrice &&
                volumeSoFar == other.volumeSoFar;
    }

    @Override
    public String toString() {
        return String.format("InProgressCandleData [openTime = %d, openPrice = %f, highPriceSoFar = %f, " +
                        "lowPriceSoFar = %f, currentTill = %d, lastPrice = %f, volumeSoFar = %f]", openTime, openPrice,
                highPriceSoFar, lowPriceSoFar, closeTime, lastPrice, volumeSoFar);
    }

    public double getHighPriceSoFar() {
        return highPriceSoFar;
    }


    public double getLowPriceSoFar() {
        return lowPriceSoFar;

    }

    public double getVolumeSoFar() {
        return volumeSoFar;

    }

    @Override
    public int closeTime() {
        return closeTime;
    }


    public double getOpenPrice() {
        return openPrice;


    }

    public double getLastPrice() {
        return lastPrice;

    }
}