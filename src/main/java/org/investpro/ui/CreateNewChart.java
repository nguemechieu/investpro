package org.investpro.ui;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.geometry.Insets;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.ui.charts.CandleStickChartDisplay;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Utility class responsible for creating/opening chart tabs.
 * <p>
 * This keeps TradingWindow cleaner and avoids repeating chart-tab logic.
 */
public final class CreateNewChart {

    private static final Logger logger = LoggerFactory.getLogger(CreateNewChart.class);
     TabPane chartTabPane;
     Exchange exchange;
     TradePair tradePair;

    private CreateNewChart() {
        // Utility class
    }

    /**
     * Opens a chart tab for the selected symbol.
     * If the chart already exists, it selects the existing tab instead.
     *
     * @param chartTabPane the main chart workspace tab pane
     * @param exchange     the selected exchange
     * @param tradePair    the selected trading pair
     */
    public static void openChart(
            TabPane chartTabPane,
            Exchange exchange,
            TradePair tradePair
    ) {

        Objects.requireNonNull(chartTabPane, "chartTabPane must not be null");
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(tradePair, "tradePair must not be null");

        String tabTitle = buildTabTitle(exchange, tradePair);
        CandleStickChartDisplay chartDisplay;
        for (Tab existingTab : chartTabPane.getTabs()) {
            if (Objects.equals(existingTab.getText(), tabTitle)) {
                chartTabPane.getSelectionModel().select(existingTab);
                return;
            }
        }

        chartDisplay = new CandleStickChartDisplay(tradePair, exchange, exchange.getTelegramToken());

        BorderPane content = new BorderPane(chartDisplay);
        content.setPadding(new Insets(4));

        Tab chartTab = new Tab(tabTitle);
        chartTab.setContent(content);
        chartTab.setClosable(true);
        chartTab.setOnClosed(event -> logger.info("Chart closed: {}", tabTitle));

        chartTabPane.getTabs().add(chartTab);
        chartTabPane.getSelectionModel().select(chartTab);

        logger.info("Chart opened: {}", tabTitle);

    }

    /**
     * Opens a chart only if it does not already exist.
     *
     * @param chartTabPane the main chart workspace tab pane
     * @param exchange the selected exchange
     * @param tradePair the selected trading pair
     * @return true if a new chart was created, false if it already existed
     */
    public static boolean openChartIfMissing(
            TabPane chartTabPane,
            Exchange exchange,
            TradePair tradePair
    ) {
        Objects.requireNonNull(chartTabPane, "chartTabPane must not be null");
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(tradePair, "tradePair must not be null");

        String tabTitle = buildTabTitle(exchange, tradePair);

        for (Tab existingTab : chartTabPane.getTabs()) {
            if (Objects.equals(existingTab.getText(), tabTitle)) {
                chartTabPane.getSelectionModel().select(existingTab);
                return false;
            }
        }

        openChart(chartTabPane, exchange, tradePair);
        return true;
    }

    /**
     * Closes all open chart tabs.
     *
     * @param chartTabPane the chart tab pane
     */
    public static void closeAllCharts(TabPane chartTabPane) {
        if (chartTabPane == null) {
            return;
        }

        chartTabPane.getTabs().clear();
        logger.info("All chart tabs closed.");
    }

    /**
     * Closes one chart tab for a symbol.
     *
     * @param chartTabPane the chart tab pane
     * @param exchange the exchange
     * @param tradePair the trading pair
     * @return true if a chart was closed
     */
    public static boolean closeChart(
            TabPane chartTabPane,
            Exchange exchange,
            TradePair tradePair
    ) {
        if (chartTabPane == null || exchange == null || tradePair == null) {
            return false;
        }

        String tabTitle = buildTabTitle(exchange, tradePair);

        Tab target = null;

        for (Tab tab : chartTabPane.getTabs()) {
            if (Objects.equals(tab.getText(), tabTitle)) {
                target = tab;
                break;
            }
        }

        if (target == null) {
            return false;
        }

        chartTabPane.getTabs().remove(target);

        return true;
    }

    private static @NotNull String buildTabTitle(Exchange exchange, TradePair tradePair) {
        String exchangeName;

        try {
            exchangeName = exchange.getDisplayName();
        } catch (Exception exception) {
            exchangeName = exchange.getName();
        }

        if (exchangeName == null || exchangeName.isBlank()) {
            exchangeName = exchange.getName();
        }

        String symbol;

        try {
            symbol = tradePair.toString('/');
        } catch (Exception exception) {
            symbol = String.valueOf(tradePair);
        }

        return "%s - %s".formatted(exchangeName, symbol);
    }
}