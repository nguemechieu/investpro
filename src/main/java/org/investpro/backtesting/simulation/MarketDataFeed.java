package org.investpro.backtesting.simulation;

import org.investpro.data.CandleData;

import java.util.List;
import java.util.Objects;

/**
 * Cursor-based historical candle feed. It avoids stream pipelines and exposes
 * candles one at a time to the simulation engine.
 */
public final class MarketDataFeed {
    private final List<CandleData> candles;
    private int index = -1;

    public MarketDataFeed(List<CandleData> candles) {
        this.candles = Objects.requireNonNull(candles, "candles must not be null");
    }

    public boolean hasNext() {
        return index + 1 < candles.size();
    }

    public CandleData next() {
        index++;
        return candles.get(index);
    }

    public int index() {
        return index;
    }

    public int size() {
        return candles.size();
    }

    public CandleData last() {
        return candles.isEmpty() ? null : candles.get(candles.size() - 1);
    }
}
