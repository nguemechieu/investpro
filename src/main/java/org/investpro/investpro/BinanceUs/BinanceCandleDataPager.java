package org.investpro.investpro.BinanceUs;

import org.investpro.investpro.CandleData;
import org.investpro.investpro.Log;
import org.investpro.investpro.oanda.CandleDataSupplier;
import org.investpro.investpro.oanda.OandaCandleStickChartToolbar;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;


public class BinanceCandleDataPager {
    private final CandleDataSupplier candleDataSupplier;
    private final CandleDataPreProcessor candleDataPreProcessor;

    public BinanceCandleDataPager(BinanceUsCandleStickChart candleStickChart, CandleDataSupplier candleDataSupplier) {
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

    private static class CandleDataPreProcessor implements Consumer<Future<List<CandleData>>> {
        private final BinanceUsCandleStickChart candleStickChart;
        private boolean hitFirstNonPlaceHolder;

        CandleDataPreProcessor(BinanceUsCandleStickChart candleStickChart) {
            this.candleStickChart = candleStickChart;
        }

        @Override
        public void accept(@NotNull Future<List<CandleData>> futureCandleData) {
            List<CandleData> candleData;
            try {
                candleData = futureCandleData.get();
            } catch (InterruptedException | ExecutionException ex) {
                Log.error("exception during accepting futureCandleData: " + ex);
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
