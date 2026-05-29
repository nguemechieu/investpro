package org.investpro.indicators.core;

import java.util.List;

public record Ohlcv(List<Double> open, List<Double> high, List<Double> low, List<Double> close, List<Double> volume) {
    public int size() { return close == null ? 0 : close.size(); }
}
