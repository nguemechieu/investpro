package org.investpro.indicators.core;

import java.util.List;

public interface Indicator {
    String id();
    String name();
    int warmupBars();
    List<Double> calculate(List<Double> close);
}
