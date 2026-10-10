package org.investpro.backtesting;

import org.investpro.data.CandleData;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class BacktestDataValidationTest {
    private CandleData candle(int time) { return new CandleData(100, 101, 102, 99, time, 0); }
    @Test void validDataAllowsZeroVolume() {
        assertDoesNotThrow(() -> BacktestDataValidation.validate(List.of(candle(0), candle(60))));
    }
    @Test void emptyDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BacktestDataValidation.validate(List.of()));
        assertThrows(IllegalArgumentException.class, () -> BacktestDataValidation.validate(null));
    }
    @Test void duplicateOrReversedTimesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BacktestDataValidation.validate(List.of(candle(60), candle(60))));
        assertThrows(IllegalArgumentException.class, () -> BacktestDataValidation.validate(List.of(candle(60), candle(0))));
    }
    @Test void invalidPriceRangesAndVolumeAreRejected() {
        for (CandleData bad : List.of(new CandleData(100, 101, 100, 99, 0, 1),
                new CandleData(100, 101, 102, 101, 0, 1),
                new CandleData(100, Double.NaN, 102, 99, 0, 1),
                new CandleData(100, 101, 102, 99, 0, -1))) {
            assertThrows(IllegalArgumentException.class, () -> BacktestDataValidation.validate(List.of(bad)));
        }
    }
    @Test void placeholderDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BacktestDataValidation.validate(
                List.of(new CandleData(100, 101, 102, 99, 0, 1, 100, 100, true))));
    }
    @Test void cancellationPreservesTheInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> BacktestDataValidation.validate(List.of(candle(0))));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
