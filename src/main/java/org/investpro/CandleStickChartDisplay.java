package org.investpro;


import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Example of how to use the CandleFX API to create a candle stick chart for the BTC/USD tradepair on Coinbase.
 */
public class CandleStickChartDisplay extends StackPane {

    private static final Logger logger = LoggerFactory.getLogger(CandleStickChartDisplay.class);

    public CandleStickChartDisplay(TradePair tradePair, Exchange exchange) {
        super();

        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> logger.error(STR."[\{thread}]: ", exception));
        ChartContainer candleStickChartContainer = new ChartContainer(
                exchange, tradePair, true);
        AnchorPane.setTopAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setLeftAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setRightAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setBottomAnchor(candleStickChartContainer, 30.0);
        candleStickChartContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        candleStickChartContainer.widthProperty().addListener((observable, oldValue, newValue) -> setPrefWidth(newValue.doubleValue()));
        candleStickChartContainer.heightProperty().addListener((observable, oldValue, newValue) -> setPrefHeight(newValue.doubleValue()));
        getChildren().add(candleStickChartContainer);


    }


}