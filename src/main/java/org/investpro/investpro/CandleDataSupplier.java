package org.investpro.investpro;

import javafx.beans.property.IntegerProperty;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * @Noel Martial Nguemechieu
 */
public abstract class CandleDataSupplier implements Supplier<Future<List<CandleData>>> {
    private static final Set<Integer> GRANULARITIES = Set.of(60, 60 * 5, 60 * 15, 60 * 30, 3600, 3600 * 2, 3600 * 3, 3600 * 4, 3600 * 6, 3600 * 24, 3600 * 24 * 7, 3600 * 24 * 7 * 4, 3600 * 24 * 365);
    /**
     * The number of candles supplied per call to {@link #get()}.
     */
    protected final int numCandles;
    protected final int secondsPerCandle;
    protected final TradePair tradePair;
    protected final IntegerProperty endTime;

    public CandleDataSupplier(int numCandles, int secondsPerCandle, TradePair tradePair, IntegerProperty endTime) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(endTime);
        if (numCandles <= 0) {
            throw new IllegalArgumentException("numCandles must be positive but was: " + numCandles);
        }
        if (secondsPerCandle <= 0) {
            throw new IllegalArgumentException("secondsPerCandle must be positive but was: " + secondsPerCandle);
        }
        this.numCandles = numCandles;
        this.secondsPerCandle = secondsPerCandle;
        this.tradePair = tradePair;
        this.endTime = endTime;
    }

    public Set<Integer> getSupportedGranularities() {
        return GRANULARITIES;
    }

    @Override
    public String toString() {
        return "CandleDataSupplier [" +
                "numCandles=" + numCandles +
                ", secondsPerCandle=" + secondsPerCandle +
                ", tradePair=" + tradePair +
                ", endTime=" + endTime +
                ']';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CandleDataSupplier that = (CandleDataSupplier) o;
        return numCandles == that.numCandles &&
                secondsPerCandle == that.secondsPerCandle &&
                Objects.equals(tradePair, that.tradePair) &&
                Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numCandles, secondsPerCandle, tradePair, endTime);
    }
}
