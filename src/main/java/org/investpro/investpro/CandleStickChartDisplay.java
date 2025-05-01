package org.investpro.investpro;

import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import org.investpro.investpro.chart.CandleStickChartContainer;
import org.investpro.investpro.model.TradePair;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CandleStickChartDisplay extends StackPane {

    private static final Logger logger = LoggerFactory.getLogger(CandleStickChartDisplay.class);

    public CandleStickChartDisplay(@NotNull Exchange exchange, TradePair selectedPair) {
        super();
        logger.info(
                "CandleStickChartDisplay: exchange={}",
                exchange
        );
        CandleStickChartContainer candleStickChartContainer = new CandleStickChartContainer(
                exchange, selectedPair, true

        );
        candleStickChartContainer.setPrefSize(
                1543,
                780
        );
        AnchorPane.setTopAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setLeftAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setRightAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setBottomAnchor(candleStickChartContainer, 30.0);
        candleStickChartContainer.widthProperty().addListener((_, _, newValue) -> setPrefWidth(newValue.doubleValue()));
        candleStickChartContainer.heightProperty().addListener((_, _, newValue) -> setPrefHeight(newValue.doubleValue()));
        getChildren().addAll(candleStickChartContainer);

    }
}