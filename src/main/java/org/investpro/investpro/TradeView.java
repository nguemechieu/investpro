package org.investpro.investpro;

import javafx.application.Platform;
import javafx.scene.layout.Region;
import org.investpro.investpro.chart.CandleStickChartContainer;
import org.investpro.investpro.model.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeView extends Region {

    private static final Logger logger = LoggerFactory.getLogger(TradeView.class);


    TradeView(Exchange exchange, TradePair pair) {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> logger.error("[%s]: ".formatted(thread), exception));

        CandleStickChartContainer candleStickChartContainer =
                new CandleStickChartContainer(
                        exchange
                        , pair, true);
        candleStickChartContainer.widthProperty().addListener((_, _, newValue) -> setPrefWidth(newValue.doubleValue()));
        getStyleClass().add("anchor-pane");


    }

}