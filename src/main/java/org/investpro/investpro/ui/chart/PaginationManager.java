package org.investpro.investpro.ui.chart;


import org.investpro.investpro.model.CandleData;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class PaginationManager {

    private final CandleStickChart chart;
    private final BiFunction<Instant, Integer, List<CandleData>> fetchOlderCandlesFunction;
    private final Consumer<List<CandleData>> appendCandlesFunction;

    private boolean isLoading = false;

    public PaginationManager(
            CandleStickChart chart,
            BiFunction<Instant, Integer, List<CandleData>> fetchOlderCandlesFunction,
            Consumer<List<CandleData>> appendCandlesFunction
    ) {
        this.chart = chart;
        this.fetchOlderCandlesFunction = fetchOlderCandlesFunction;
        this.appendCandlesFunction = appendCandlesFunction;
        setupScrollListener();
    }

    private void setupScrollListener() {
        chart.setOnScroll(event -> {
            if (event.getDeltaY() > 0 && !isLoading) {
                // User scrolls UP (zoom out / go left in time)
                loadMoreCandles();
            }
        });
    }

    private void loadMoreCandles() {
        isLoading = true;

        Instant oldestTime = getOldestCandleTime();
        if (oldestTime == null) {
            isLoading = false;
            return;
        }

        List<CandleData> olderCandles = fetchOlderCandlesFunction.apply(oldestTime, 100); // Load 100 candles
        if (olderCandles != null && !olderCandles.isEmpty()) {
            appendCandlesFunction.accept(olderCandles);
        }

        isLoading = false;
    }

    private @Nullable Instant getOldestCandleTime() {
//        if (chart.getData().isEmpty()) return null;
//        XYChart.Series<String, Number> series = chart.getData().getFirst();
//        if (series.getData().isEmpty()) return null;
//
//        String oldestTimestamp = series.getData().getFirst().getXValue();
        return Instant.now();///parse(oldestTimestamp);
    }
}
