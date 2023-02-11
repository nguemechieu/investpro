package org.investpro.investpro.Coinbase;

import javafx.application.Platform;
import org.investpro.investpro.Log;
import org.investpro.investpro.Trade;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CoinbasePro {
    String BTC_USD = "LTC-USD";

    public static void createMarketOrder(String toString, String type0, String side, double size) {

    }


    public CoinbaseCandleStickChartContainer start() throws Exception {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> Log.error("[" + thread + "]: " + exception));
       CoinbaseCandleStickChartContainer candleStickChartContainer =
                new CoinbaseCandleStickChartContainer(
                        new Coinbase(null,null,null,null) {
                            @Override
                            public CoinbaseCandleDataSupplier getCandleDataSupplier(int secondsPerCandle, String tradePair) {
                                return null;
                            }

                            @Override
                            public CompletableFuture<List<Trade>> fetchRecentTradesUntil(String tradePair, Instant stopAt) {
                                return null;
                            }

                            @Override
                            public boolean isInputClosed() {
                                return false;
                            }

                            @Override
                            public void abort() {

                            }
                        }, BTC_USD, true);


        candleStickChartContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);



        return candleStickChartContainer;
    }

}