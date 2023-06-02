package org.investpro.Coinbase;

import javafx.application.Platform;
import org.investpro.CandleStickChartContainer;
import org.investpro.Currency;
import org.investpro.Log;
import org.investpro.TradePair;

public class CoinbasePro {
    String BTC_USD = "LTC-USD";

    public static void createMarketOrder(TradePair toString, String type0, String side, double size) {

    }


    public CandleStickChartContainer start() throws Exception {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> Log.error("[" + thread + "]: " + exception));
        CandleStickChartContainer candleStickChartContainer =
                new CandleStickChartContainer(
                        new Coinbase(), new TradePair(
                                Currency.of("BTC"), Currency.of("USD")
                ), true);


        candleStickChartContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return candleStickChartContainer;
    }

}