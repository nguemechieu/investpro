package org.investpro;


import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example of how to use the CandleFX API to create a candle stick chart for the BTC/USD tradepair on Coinbase.
 */
public class CandleStickChartDisplay extends StackPane {

    private static final Logger logger = LoggerFactory.getLogger(CandleStickChartDisplay.class);

    public CandleStickChartDisplay(TradePair tradePair, Exchange exchange) {
        super();

        ChartContainer candleStickChartContainer = new ChartContainer(
                exchange, tradePair, true);
        AnchorPane.setTopAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setLeftAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setRightAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setBottomAnchor(candleStickChartContainer, 30.0);
        candleStickChartContainer.setMaxSize(1200, 500);
        candleStickChartContainer.widthProperty().addListener((observable, oldValue, newValue) -> setPrefWidth(newValue.doubleValue()));
        candleStickChartContainer.heightProperty().addListener((observable, oldValue, newValue) -> setPrefHeight(newValue.doubleValue()));
        getChildren().add(candleStickChartContainer);


    }


}