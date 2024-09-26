package org.investpro;

import javafx.application.Platform;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeView extends Region {

    private static final Logger logger = LoggerFactory.getLogger(TradeView.class);


    TradeView(Exchange exchange) {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> logger.error(STR."[\{thread}]: ", exception));

        CandleStickChartContainer candleStickChartContainer =
                new CandleStickChartContainer(
                        exchange
                        , true);
        candleStickChartContainer.widthProperty().addListener((_, _, newValue) -> setPrefWidth(newValue.doubleValue()));
        getStyleClass().add("anchor-pane");


    }

}