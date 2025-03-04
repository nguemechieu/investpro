package org.investpro;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@Getter
@Setter
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
        public void accept(Future<List<CandleData>> futureCandleData) {
            List<CandleData> candleData;
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
