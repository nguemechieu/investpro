package org.investpro.investpro;

import javafx.application.Platform;
import javafx.scene.layout.AnchorPane;

import java.io.IOException;
import java.net.URISyntaxException;

public class OandaCandleStick {
    private static final TradePair BTC_USD = TradePair.of(Currency.of("USD"), Currency.ofFiat("CAD"));

    public CandleStickChartContainer start() throws URISyntaxException, IOException {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> Log.error("[" + thread + "]: \n" + exception));
        CandleStickChartContainer candleStickChartContainer =
                new CandleStickChartContainer(new OandaClient("https://api-fxtrade.oanda.com/", OandaClient.getApi_key(), OandaClient.getAccountID()), BTC_USD, true);
        AnchorPane.setTopAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setLeftAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setRightAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setBottomAnchor(candleStickChartContainer, 30.0);
        candleStickChartContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return candleStickChartContainer;
    }


}

