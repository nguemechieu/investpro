package org.investpro.data;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.investpro.ui.charts.CandleStickChart;
import org.investpro.utils.CandleDataSupplier;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Pages new candle data in chronological order to a {@code CandleStickChart}
 * on-demand.
 *
 * @author NOEL NGUEMECHIEU
 */
@Getter
@Setter
@Slf4j
public class CandleDataPager {
    private final CandleDataSupplier candleDataSupplier;
    private final CandleDataPreProcessor candleDataPreProcessor;

    public CandleDataPager(CandleStickChart candleStickChart, CandleDataSupplier candleDataSupplier) {
        Objects.requireNonNull(candleStickChart);
        Objects.requireNonNull(candleDataSupplier);
        this.candleDataSupplier = candleDataSupplier;
        candleDataPreProcessor = new CandleDataPreProcessor(candleStickChart);
    }

    public Consumer<Future<List<CandleData>>> getCandleDataPreProcessor() {
        return candleDataPreProcessor;
    }

    private static class CandleDataPreProcessor implements Consumer<Future<List<CandleData>>> {
        private final CandleStickChart candleStickChart;
        private boolean hitFirstNonPlaceHolder;

        CandleDataPreProcessor(CandleStickChart candleStickChart) {
            this.candleStickChart = candleStickChart;
        }

        @Override
        public void accept(@NotNull Future<List<CandleData>> futureCandleData) {
            List<CandleData> candleData;
            try {
                // Use timeout of 30 seconds to prevent indefinite blocking
                // If future completes faster, it returns immediately
                candleData = futureCandleData.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                // Thread was interrupted (e.g., during shutdown)
                // Restore interrupt status as per best practices
                Thread.currentThread().interrupt();
                log.debug("Candle data fetch interrupted (likely during shutdown)", ex);
                return;
            } catch (ExecutionException ex) {
                // Future failed with an exception
                log.error("Error fetching candle data from supplier: ", ex);
                return;
            } catch (TimeoutException ex) {
                // Future didn't complete within timeout
                log.warn("Candle data fetch timed out after 30 seconds");
                return;
            }

            if (!candleData.isEmpty()) {
                if (hitFirstNonPlaceHolder) {
                    candleStickChart.getCandlePageConsumer().accept(candleData);
                } else {
                    int count = 0;
                    while (candleData.get(count).placeHolder()) {
                        count++;
                    }
                    List<CandleData> nonPlaceHolders = candleData.subList(count, candleData.size());
                    if (!nonPlaceHolders.isEmpty()) {
                        hitFirstNonPlaceHolder = true;
                        candleStickChart.getCandlePageConsumer().accept(nonPlaceHolders);
                    }
                }
            }
        }
    }
}
