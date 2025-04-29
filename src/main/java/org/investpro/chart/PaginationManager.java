package org.investpro.chart;

import javafx.scene.chart.XYChart;
import org.investpro.model.Candle;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class PaginationManager {

    private final XYChart<String, Number> chart;
    private final BiFunction<Instant, Integer, List<Candle>> fetchOlderCandlesFunction;
    private final Consumer<List<Candle>> appendCandlesFunction;

    private boolean isLoading = false;

    public PaginationManager(
            XYChart<String, Number> chart,
            BiFunction<Instant, Integer, List<Candle>> fetchOlderCandlesFunction,
            Consumer<List<Candle>> appendCandlesFunction
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

        List<Candle> olderCandles = fetchOlderCandlesFunction.apply(oldestTime, 100); // Load 100 candles
        if (olderCandles != null && !olderCandles.isEmpty()) {
            appendCandlesFunction.accept(olderCandles);
        }

        isLoading = false;
    }

    private @Nullable Instant getOldestCandleTime() {
        if (chart.getData().isEmpty()) return null;
        XYChart.Series<String, Number> series = chart.getData().getFirst();
        if (series.getData().isEmpty()) return null;

        String oldestTimestamp = series.getData().getFirst().getXValue();
        return Instant.parse(oldestTimestamp);
    }
}
