package org.investpro;

import javafx.beans.property.IntegerProperty;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static java.lang.System.out;

public abstract class CandleDataSupplier implements Supplier<Future<List<CandleData>>> {

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
        } else {
            out.println("CandleS " + this);
        }
        this.numCandles = numCandles;
        this.secondsPerCandle = secondsPerCandle;
        this.tradePair = tradePair;
        this.endTime = endTime;


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

        out.println("CandleDataSupplier equals " + that);
        return numCandles == that.numCandles &&
                secondsPerCandle == that.secondsPerCandle &&
                Objects.equals(tradePair, that.tradePair) &&
                Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numCandles, secondsPerCandle, tradePair, endTime);
    }

    public abstract List<CandleData> getCandleData();


    public abstract CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair);

    public abstract CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(
            TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle);

    public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt);
}
