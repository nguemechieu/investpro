package org.investpro.ui.charts;

import lombok.extern.slf4j.Slf4j;


import javafx.geometry.Insets;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;
import org.investpro.ui.ChartContainer;
/**
 * Example of how to use the CandleFX API to create a candle stick chart for the BTC/USD tradepair on Coinbase.
 * This component provides a complete charting solution with toolbar and options.
 */
@Getter
@Setter
@Slf4j
public class CandleStickChartDisplay extends StackPane {
    ChartContainer chartContainer;
    public CandleStickChartDisplay(TradePair tradePair, Exchange exchange, String  telegramToken) {
        this(tradePair, exchange, telegramToken, null);
    }

    public CandleStickChartDisplay(TradePair tradePair, Exchange exchange, String telegramToken, TradingService tradingService) {
        super();

        setMinSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // Create the chart container with live syncing enabled
        chartContainer = new ChartContainer(exchange, tradePair, true, telegramToken, tradingService);
        chartContainer.setMinSize(0, 0);
        chartContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        
        // Configure the display
        VBox.setVgrow(chartContainer, Priority.ALWAYS);
        HBox.setHgrow(chartContainer, Priority.ALWAYS);

        setPadding(Insets.EMPTY);
        
        // Add the chart container
        getChildren().add(chartContainer);
        
        log.info("CandleStickChartDisplay initialized for %s on %s".formatted(tradePair.toString('/'), exchange.getName()));
    }

    /**
     * Get the active candlestick chart.
     * @return the CandleStickChart
     */
    public CandleStickChart getChart() {
        return chartContainer.getChart();
    }

}
