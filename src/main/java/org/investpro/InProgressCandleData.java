package org.investpro;


public record InProgressCandleData(double openPrice, double highPriceSoFar, double lowPriceSoFar,
                                   double closePrice, int openTime, long volumeSoFar) {


    @Override
    public String toString() {
        return "InProgressCandleData{" +
                "openPrice=" + openPrice +
                ", highPriceSoFar=" + highPriceSoFar +
                ", lowPriceSoFar=" + lowPriceSoFar +
                ", openTime=" + openTime +
                ", lastPrice=" + closePrice +
                ", volumeSoFar=" + volumeSoFar +
                '}';
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

                closePrice == other.closePrice &&
                volumeSoFar == other.volumeSoFar;
    }

}
