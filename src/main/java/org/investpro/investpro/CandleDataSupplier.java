package org.investpro.investpro;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.investpro.investpro.model.Candle;
import org.investpro.investpro.model.TradePair;

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
public abstract class CandleDataSupplier implements Supplier<Future<List<Candle>>> {
    /**
     * The number of candles supplied per call to {@link #get()}.
     */

    protected final int secondsPerCandle;
    protected final IntegerProperty endTime = new SimpleIntegerProperty(-1);
    protected TradePair tradePair;

    public CandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(endTime);

        if (secondsPerCandle <= 0) {
            throw new IllegalArgumentException(String.format("secondsPerCandle must be positive but was: %d", secondsPerCandle));
        }


        this.secondsPerCandle = secondsPerCandle;
        this.tradePair = tradePair;

    }


    public abstract Set<Integer> getSupportedGranularity();


}