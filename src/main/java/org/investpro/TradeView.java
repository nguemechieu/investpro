package org.investpro;

import javafx.application.Platform;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeView extends Region {

    private static final Logger logger = LoggerFactory.getLogger(TradeView.class);
    private TradePair tradePair;

    TradeView(Exchange exchange) {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> logger.error(STR."[\{thread}]: ", exception));

        CandleStickChartContainer candleStickChartContainer =
                new CandleStickChartContainer(
                        exchange, tradePair
                        , true);
        candleStickChartContainer.widthProperty().addListener((observable, oldValue, newValue) -> setPrefWidth(newValue.doubleValue()));
        getStyleClass().add("anchor-pane");


    }

    public TradePair getTradePair() {
        return tradePair;
    }

    public void setTradePair(TradePair tradePair) {
        this.tradePair = tradePair;
    }
}