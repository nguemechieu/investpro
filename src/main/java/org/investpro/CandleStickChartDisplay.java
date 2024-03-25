package org.investpro;


import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CandleStickChartDisplay extends StackPane {

    private static final Logger logger = LoggerFactory.getLogger(CandleStickChartDisplay.class);

    public CandleStickChartDisplay(TradePair tradePair, Exchange exchange) {
        super();

        logger.debug(String.valueOf(this));

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