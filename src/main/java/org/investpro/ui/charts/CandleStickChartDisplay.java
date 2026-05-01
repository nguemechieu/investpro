package org.investpro.ui.charts;


import javafx.geometry.Insets;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import org.investpro.core.TelegramNotifier;
import org.investpro.core.bot.SmartBot;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;
import org.investpro.ui.ChartContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example of how to use the CandleFX API to create a candle stick chart for the BTC/USD tradepair on Coinbase.
 * This component provides a complete charting solution with toolbar and options.
 */
@Getter
@Setter
public class CandleStickChartDisplay extends StackPane {

    private static final Logger logger = LoggerFactory.getLogger(CandleStickChartDisplay.class);
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
        
        logger.info("CandleStickChartDisplay initialized for %s on %s".formatted(tradePair.toString('/'), exchange.getName()));
    }

    /**
     * Get the active candlestick chart.
     * @return the CandleStickChart
     */
    public CandleStickChart getChart() {
        return chartContainer.getChart();
    }

}
