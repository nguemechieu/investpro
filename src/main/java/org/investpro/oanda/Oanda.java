package org.investpro.oanda;

import javafx.application.Platform;
import javafx.scene.layout.AnchorPane;
import org.investpro.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;

public class Oanda {
    String name;
    String email;
    String password;
    long lastTransactionID;
    long createdByUserID;
    double NAV;
    double marginCloseoutUnrealizedPL;
    double marginCallMarginUsed;
    double openPositionCount;
    double withdrawalLimit;
    double positionValue;
    double marginRate;
    double marginCallPercent;
    double balance;
    double resettablePL, financing;
    Date createdTime;
    String alias;
    String currency;
    double commission;
    double marginCloseoutPercent;
    long id;
    long openTradeCount;
    long pendingOrderCount;
    boolean hedgingEnabled;
    int resettablePLTime;
    ArrayList<Trade> trades;

    public Oanda() {
        trades = new ArrayList<>();

    }

    public CandleStickChartContainer start() throws URISyntaxException, IOException {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> Log.error("[" + thread + "]: \n" + exception));
        CandleStickChartContainer candleStickChartContainer =
                new CandleStickChartContainer(new OandaClient("https://api-fxtrade.oanda.com/", OandaClient.getApi_key(), OandaClient.getAccountID()),
                        new TradePair(Currency.of("BTC"), Currency.of("USD")
                ), true);
        AnchorPane.setTopAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setLeftAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setRightAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setBottomAnchor(candleStickChartContainer, 30.0);
        candleStickChartContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return candleStickChartContainer;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

}

