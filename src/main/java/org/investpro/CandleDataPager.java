package org.investpro;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pages new candle data in chronological order to a {@code CandleStickChart} on-demand.
 *
 * @author  <a href="mailto: nguemechieu@live.com">nguem</a>
 */
public class CandleDataPager {
    private final CandleDataSupplier candleDataSupplier;
    private final CandleDataPreProcessor candleDataPreProcessor;
    private static final Logger logger = LoggerFactory.getLogger(CandleDataPager.class);

    public CandleDataPager(CandleStickChart candleStickChart, CandleDataSupplier candleDataSupplier) {
        Objects.requireNonNull(candleStickChart);
        Objects.requireNonNull(candleDataSupplier);
        this.candleDataSupplier = candleDataSupplier;
        candleDataPreProcessor = new CandleDataPreProcessor(candleStickChart);
    }

    public CandleDataSupplier getCandleDataSupplier() {
        return candleDataSupplier;
    }

    public Consumer<Future<List<CandleData>>> getCandleDataPreProcessor() {
        return candleDataPreProcessor;
    }

    private static final class CandleDataPreProcessor implements Consumer<Future<List<CandleData>>> {
      final   CandleStickChart candleStickChart;
        private boolean hitFirstNonPlaceHolder;


        CandleDataPreProcessor(CandleStickChart candleStickChart) {
            this.candleStickChart = candleStickChart;
        }

        List<CandleData> candleData = new ArrayList<>();
        @Override
        public void accept(@NotNull Future<List<CandleData>> futureCandleData) {

            try {
                candleData = futureCandleData.get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.error("exception during accepting futureCandleData: ", ex);
                return;
            }

            if (!candleData.isEmpty()) {
                if (hitFirstNonPlaceHolder) {
                    candleStickChart.getCandlePageConsumer().accept(candleData);
                } else {
                    int count = 0;
                    while (candleData.get(count).isPlaceHolder()) {
                        count++;
                        if (count == candleData.size()) {
                            logger.info("No non-placeholder candles found in the data");

                            new  Messages("Warning",
                                    "No non-placeholder candles found in the data"
                            );
                            break;
                        }
                    }
                    List<CandleData> nonPlaceHolders = candleData.subList(count, candleData.size());
                    if (!nonPlaceHolders.isEmpty()) {
                        hitFirstNonPlaceHolder = true;
                        candleStickChart.getCandlePageConsumer().accept( nonPlaceHolders);
                    }

                }
            }
        }
    }
}
