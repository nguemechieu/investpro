package org.investpro.investpro.Coinbase;

import javafx.application.Platform;
import org.investpro.investpro.CandleStickChartContainer;
import org.investpro.investpro.Log;

public class CoinbasePro {
    String BTC_USD = "LTC-USD";

    public static void createMarketOrder(String toString, String type0, String side, double size) {

    }


    public CandleStickChartContainer start() throws Exception {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> Log.error("[" + thread + "]: " + exception));
        CandleStickChartContainer candleStickChartContainer =
                new CandleStickChartContainer(
                        new Coinbase(), BTC_USD, true);


        candleStickChartContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return candleStickChartContainer;
    }

}