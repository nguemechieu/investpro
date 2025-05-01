package org.investpro.investpro.chart;

import javafx.application.Platform;
import org.investpro.investpro.model.Candle;

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LiveUpdateManager {

    private final Supplier<Candle> fetchLatestCandleFunction;
    private final Consumer<Candle> addNewCandleFunction;
    private Timer timer;

    public LiveUpdateManager(
            Supplier<Candle> fetchLatestCandleFunction,
            Consumer<Candle> addNewCandleFunction
    ) {
        this.fetchLatestCandleFunction = fetchLatestCandleFunction;
        this.addNewCandleFunction = addNewCandleFunction;
    }

    public void start(long updateIntervalMillis) {
        stop(); // Stop any existing timer
        timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Candle latestCandle = fetchLatestCandleFunction.get();
                if (latestCandle != null) {
                    Platform.runLater(() -> addNewCandleFunction.accept(latestCandle));
                }
            }
        }, 0, updateIntervalMillis);
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
        }
    }
}
