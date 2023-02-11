package org.investpro.investpro.Coinbase;

import org.investpro.investpro.CandleData;
import org.investpro.investpro.Log;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;


public class CoinbaseCandleDataPager {
    private final Coinbase.CoinbaseCandleDataSupplier candleDataSupplier;
    private final CandleDataPreProcessor candleDataPreProcessor;


    public CoinbaseCandleDataPager(CoinbaseCandleStickChart candleStickChart, Coinbase.CoinbaseCandleDataSupplier candleDataSupplier) {


        Objects.requireNonNull(candleStickChart);
        Objects.requireNonNull(candleDataSupplier);
        this.candleDataSupplier = candleDataSupplier;
        this.candleDataPreProcessor = new CandleDataPreProcessor(candleStickChart);
    }


    public Coinbase.CoinbaseCandleDataSupplier getCandleDataSupplier() {
        return candleDataSupplier;
    }

    public Consumer<Future<List<CandleData>>> getCandleDataPreProcessor() {
        return candleDataPreProcessor;
    }

    private static class CandleDataPreProcessor implements Consumer<Future<List<CandleData>>> {
        private final CoinbaseCandleStickChart candleStickChart;
        private boolean hitFirstNonPlaceHolder;

        CandleDataPreProcessor(CoinbaseCandleStickChart candleStickChart) {
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
