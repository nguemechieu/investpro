package org.investpro.investpro.ui.chart.overlay;

import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.ui.chart.CandleStickChart;


import java.util.List;

public interface ChartOverlay {
    String getName();


    void clear(CandleStickChart chart);

    void apply(CandleStickChart chart, List<CandleData> candles);
}
