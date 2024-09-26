package org.investpro;


import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CandleStickChartDisplay extends StackPane {

    private static final Logger logger = LoggerFactory.getLogger(CandleStickChartDisplay.class);

    public CandleStickChartDisplay( @NotNull Exchange exchange) {
        super();
        logger.info(
                "CandleStickChartDisplay: exchange={}",
                exchange
        );
        CandleStickChartContainer candleStickChartContainer = new CandleStickChartContainer(
                exchange,  true

        );
        candleStickChartContainer.setPrefSize(
                Double.MAX_VALUE,
                Double.MAX_VALUE
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