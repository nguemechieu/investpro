package org.investpro;


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
}
