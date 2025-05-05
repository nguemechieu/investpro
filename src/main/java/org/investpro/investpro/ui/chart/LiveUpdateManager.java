package org.investpro.investpro.ui.chart;

import javafx.application.Platform;

import org.investpro.investpro.model.CandleData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LiveUpdateManager {

    private static final Logger logger = LoggerFactory.getLogger(LiveUpdateManager.class);

    private final Supplier<CandleData> fetchLatestCandle;
    private final Consumer<CandleData> onNewCandle;
    private Timer timer;

    public LiveUpdateManager(Supplier<CandleData> fetchLatestCandle, Consumer<CandleData> onNewCandle) {
        this.fetchLatestCandle = fetchLatestCandle;
        this.onNewCandle = onNewCandle;
    }

    public void start(long intervalMillis) {
        stop(); // Stop any existing timer
        timer = new Timer("LiveCandleUpdater", true);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    CandleData latest = fetchLatestCandle.get();
                    if (latest != null) {
                        Platform.runLater(() -> {
                            try {
                                onNewCandle.accept(latest);
                            } catch (Exception e) {
                                logger.error("❌ Failed to consume new candle: {}", e.getMessage(), e);
                            }
                        });
                    }
                } catch (Exception e) {
                    logger.error("❌ Error fetching latest candle: {}", e.getMessage(), e);
                }
            }
        }, intervalMillis, intervalMillis);
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    public boolean isRunning() {
        return timer != null;
    }
}
