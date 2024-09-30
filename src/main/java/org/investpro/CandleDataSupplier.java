package org.investpro;

import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import javafx.beans.property.IntegerProperty;

/**
 * @author Noel Nguemechieu
 */
public abstract class CandleDataSupplier implements Supplier<Future<List<CandleData>>> {
    /**
     * The number of candles supplied per call to {@link #get()}.
     */
    protected final int numCandles;
    protected final int secondsPerCandle;
    protected final TradePair tradePair;
    protected final IntegerProperty endTime;
    List<CandleData> candleDataList = new ArrayList<>();

    public CandleDataSupplier(int numCandles, int secondsPerCandle, TradePair tradePair, IntegerProperty endTime) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(endTime);

        if (secondsPerCandle <= 0) {
            throw new IllegalArgumentException(String.format("secondsPerCandle must be positive but was: %d", secondsPerCandle));
        }
        if (numCandles <= 0) {
            throw new IllegalArgumentException("numCandles must be positive but was:%d".formatted(numCandles));
        }

        this.numCandles = numCandles;
        this.secondsPerCandle = secondsPerCandle;
        this.tradePair = tradePair;
        this.endTime = endTime;
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

  /**
          * Returns a set of supported granularity values (in seconds) that this data supplier can provide.
            *
            * @return a set of integers representing supported granularity levels
 */
    public abstract Set<Integer> getSupportedGranularity();

    /**
     * Creates and returns a new {@code CandleDataSupplier} instance with the given granularity and trade pair.
     *
     * @param secondsPerCandle the time interval for each candle in seconds
     * @param tradePair the trade pair for which the candle data should be supplied
     * @return a new {@code CandleDataSupplier} instance
     */
    public abstract CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair);
    public void add(CandleData of) {

        candleDataList.add(of);

    }

}
