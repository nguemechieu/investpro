package org.investpro;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static org.investpro.ui.TradingWindow.db1;

/**
 * Handles paginated fetching of new candle data in chronological order to a {@code CandleStickChart}.
 *
 * @author <a href="mailto: nguemechieu@live.com">nguem</a>
 */
@Getter
@Setter
public class CandleDataPager {


    private final CandleDataPreProcessor candleDataPreProcessor;
    private static final Logger logger = LoggerFactory.getLogger(CandleDataPager.class);
    private final boolean running = true; // Flag to manage the pager's state
    private final List<CandleData> candleDataList = new ArrayList<>();

    private CandleData inProgressCandle; // Store the in-progress candle separately

    public CandleDataPager(CandleStickChart candleStickChart, CandleDataSupplier candleDataSupplier) throws ExecutionException, InterruptedException {
        Objects.requireNonNull(candleStickChart, "CandleStickChart cannot be null.");
        Objects.requireNonNull(candleDataSupplier, "CandleDataSupplier cannot be null.");
        this.inProgressCandle= new InProgressCandle().snapshot();

        this.candleDataPreProcessor = new CandleDataPreProcessor(candleStickChart);
        candleDataPreProcessor.accept(CompletableFuture.completedFuture(candleDataSupplier.get().get().stream().toList()));
        logger.info("CandleDataSupplier has been initialized.");
        Consumer<CompletableFuture<List<CandleData>>> res = getCandleDataPreProcessor().andThen(
                candleData -> {
                    logger.info("CandleDataPreProcessor has been initialized.");
                         try {
                        candleDataList.addAll(candleData.get());

                         } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }
        );logger.info(
                "CandleDataSupplier has been initialized. CandleDataPreProcessor has been initialized.{}",res
        );



    }



    /**
     * Preprocesses incoming candle data before updating the chart.
     */
    @Getter
    @Setter
    protected final class CandleDataPreProcessor implements Consumer<CompletableFuture<List<CandleData>>> {
        private  CandleStickChart candleStickChart;
        private CandelDataConsumer candelDataConsumer;
        private boolean hitFirstNonPlaceHolder;

        CandleDataPreProcessor(CandleStickChart candleStickChart) {

            this.hitFirstNonPlaceHolder = true;
            this.candelDataConsumer =new CandelDataConsumer(candleStickChart);
            this.candleStickChart = candleStickChart;
            logger.info("CandleDataPreProcessor has been initialized.");
        }

        @Override
        public void accept(@NotNull CompletableFuture<List<CandleData>> futureCandleData) {
            futureCandleData.thenAcceptAsync(candleData -> {
                if (candleData == null || candleData.isEmpty()) {
                    logger.warn("Received empty candle data.");
                    return;
                }
                processCandleData(candleData);
            }).exceptionally(ex -> {
                logger.error("Error during candle data processing: ", ex);
                return null;
            });
        }

        /**
         * Processes the received candle data, separating complete and in-progress candles.
         */
        private void processCandleData(@NotNull List<CandleData> candleData) {
            List<CandleData> completeCandles = new ArrayList<>();
            CandleData latestIncompleteCandle = null;

            for (CandleData candle : candleData) {
                if (candle.isComplete()) {
                    completeCandles.add(candle);
                } else {
                    latestIncompleteCandle = candle; // Only keep the latest in-progress candle
                }

            }

            // If we haven't hit a non-placeholder yet, filter placeholders
            if (!hitFirstNonPlaceHolder) {
                int firstValidIndex = 0;
                while (firstValidIndex < completeCandles.size() && completeCandles.get(firstValidIndex).isPlaceHolder()) {
                    firstValidIndex++;
                }

                if (firstValidIndex == completeCandles.size()) {
                    logger.info("No valid trading candles found.");
                    candleData.add(new CandleData());
                  //  new Messages(Alert.AlertType.WARNING, "No non-placeholder candles found in the data.");
                }

                completeCandles = completeCandles.subList(firstValidIndex, completeCandles.size());
                hitFirstNonPlaceHolder = true;
            }


               getCandleDataPreProcessor().getCandelDataConsumer().accept(completeCandles);

            // Store in-progress candle separately
            if (latestIncompleteCandle != null) {
                inProgressCandle = latestIncompleteCandle;
                logger.info("📊 In-progress candle stored: {}", inProgressCandle);
            }
        }
    }
}
