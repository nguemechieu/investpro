package org.investpro;

import javafx.beans.property.IntegerProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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


    public abstract Set<Integer> getSupportedGranularity();
}
