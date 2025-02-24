package org.investpro;

import javafx.beans.property.IntegerProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * @author Noel Nguemechieu
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public abstract class CandleDataSupplier implements Supplier<Future<List<CandleData>>> {
    /**
     * The number of candles supplied per call to {@link #get()}.
     */

    protected final int secondsPerCandle;
    protected final TradePair tradePair;
    protected final IntegerProperty endTime;
    List<CandleData> candleDataList = new ArrayList<>();

    public CandleDataSupplier(int secondsPerCandle, TradePair tradePair, IntegerProperty endTime) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(endTime);

        if (secondsPerCandle <= 0) {
            throw new IllegalArgumentException(String.format("secondsPerCandle must be positive but was: %d", secondsPerCandle));
        }


        this.secondsPerCandle = secondsPerCandle;
        this.tradePair = tradePair;
        this.endTime = endTime;
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

    public Object getSupportedGranularities() {
        return getSupportedGranularity();
    }
}
