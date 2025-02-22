package org.investpro;

import javafx.scene.control.Alert;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Handles paginated fetching of new candle data in chronological order to a {@code CandleStickChart}.
 *
 * @author <a href="mailto: nguemechieu@live.com">nguem</a>
 */
public class CandleDataPager {
    @Getter
    private final CandleDataSupplier candleDataSupplier;
    private final CandleDataPreProcessor candleDataPreProcessor;
    private static final Logger logger = LoggerFactory.getLogger(CandleDataPager.class);
    private volatile boolean running = true; // Flag to manage the pager's state

    public CandleDataPager(CandleStickChart candleStickChart, CandleDataSupplier candleDataSupplier) {
        Objects.requireNonNull(candleStickChart, "CandleStickChart cannot be null.");
        Objects.requireNonNull(candleDataSupplier, "CandleDataSupplier cannot be null.");
        this.candleDataSupplier = candleDataSupplier;
        this.candleDataPreProcessor = new CandleDataPreProcessor(candleStickChart);
    }

    public Consumer<Future<List<CandleData>>> getCandleDataPreProcessor() {
        return candleDataPreProcessor;
    }

    /**
     * Stops fetching new candle data.
     */
    public void stop() {
        running = false;
        logger.info("CandleDataPager has been stopped.");
    }

    /**
     * Accepts new candle data and updates the data supplier.
     */
    private void accept(List<CandleData> candleData) {
        if (!running) {
            logger.info("CandleDataPager is stopped. Ignoring new data.");
            candleData.clear();
            return;
        }

        try {
            getCandleDataSupplier().get().get().addAll(candleData);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while accepting new candle data.", e);
        } catch (ExecutionException e) {
            logger.error("Error fetching candle data from supplier.", e);
        }
    }

    public Function<? super Future<List<CandleData>>, ?> getCandleDataPreprocessor() {
        return this::accept;
    }

    private Object accept(Future<List<CandleData>> listFuture) {
        if (!listFuture.isDone()) {
            logger.warn("Candle data is not yet available.");
            return null;
        }

        List<CandleData> candleData;
        try {
            candleData = listFuture.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); // Preserve interrupted state
            logger.error("Candle data processing was interrupted.", ex);
            return null;
        } catch (ExecutionException ex) {
            logger.error("Error during candle data processing: ", ex.getCause());
            return null;
        }

        if (candleData == null || candleData.isEmpty()) {
            logger.warn("Received empty candle data.");
            return null;
        }

        candleDataPreProcessor.processCandleData(candleData);
        return candleData;
    }

    /**
     * Preprocesses incoming candle data before updating the chart.
     */
    private static final class CandleDataPreProcessor implements Consumer<Future<List<CandleData>>> {
        private final CandleStickChart candleStickChart;
        private boolean hitFirstNonPlaceHolder = false;

        CandleDataPreProcessor(CandleStickChart candleStickChart) {
            this.candleStickChart = candleStickChart;
        }

        @Override
        public void accept(@NotNull Future<List<CandleData>> futureCandleData) {
            if (!futureCandleData.isDone()) {
                logger.warn("Candle data is not yet available.");
                return;
            }

            List<CandleData> candleData;
            try {
                candleData = futureCandleData.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); // Preserve interrupted state
                logger.error("Candle data processing was interrupted.", ex);
                return;
            } catch (ExecutionException ex) {
                logger.error("Error during candle data processing: ", ex.getCause());
                return;
            }

            if (candleData == null || candleData.isEmpty()) {
                logger.warn("Received empty candle data.");
                return;
            }

            processCandleData(candleData);
        }

        /**
         * Processes the received candle data.
         */
        private void processCandleData(List<CandleData> candleData) {
            if (hitFirstNonPlaceHolder) {
                candleStickChart.getCandlePageConsumer().accept(candleData);
                return;
            }

            // Find the first non-placeholder candle
            int firstValidIndex = 0;
            while (firstValidIndex < candleData.size() && candleData.get(firstValidIndex).isPlaceHolder()) {
                firstValidIndex++;
            }

            if (firstValidIndex == candleData.size()) {
                logger.info("No valid trading candles found.");
                new Messages(Alert.AlertType.WARNING, "No non-placeholder candles found in the data.");
                return;
            }

            List<CandleData> validCandleData = candleData.subList(firstValidIndex, candleData.size());
            hitFirstNonPlaceHolder = true;
            candleStickChart.getCandlePageConsumer().accept(validCandleData);
        }
    }
}
