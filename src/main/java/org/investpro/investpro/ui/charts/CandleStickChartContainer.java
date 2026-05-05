package org.investpro.investpro.ui.charts;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.investpro.investpro.CandleDataSupplier;
import org.investpro.investpro.CandleStickChartOptions;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.models.TradePair;
import org.investpro.investpro.ui.CandleStickChartToolbar;

import java.util.Objects;

/**
 * A {@link Region} that contains a {@code CandleStickChart} and a {@code CandleStickChartToolbar}.
 * The contained chart will display data for the given {@code tradePair}. The toolbar allows for changing
 * the duration in seconds of each candle as well as configuring the properties of the chart. When a new
 * duration is selected, this container automatically creates a new {@code CandleStickChart} and visually
 * transitions to it.
 */
public class CandleStickChartContainer extends Region {
    private final VBox candleChartContainer;
    private final CandleStickChartToolbar toolbar;
    private final ChartWorkspacePane workspacePane;
    private final Exchange exchange;
    private final TradePair tradePair;
    private final String tokens;
    private final SimpleIntegerProperty secondsPerCandle;
    private ChartLayout chartLayout;
    private final VBox root;

    public CandleStickChartContainer(Exchange exchange, TradePair tradePair, String tokens) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(tradePair, "tradePair must not be null");

        this.exchange = exchange;
        this.tradePair = tradePair;
        this.tokens = tokens;
        this.secondsPerCandle = new SimpleIntegerProperty(3600);

        getStyleClass().add("candle-chart-container");
        getStylesheets().add(Objects.requireNonNull(
                CandleStickChartContainer.class.getResource("/css/chart.css")
        ).toExternalForm());
        setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);

        CandleDataSupplier candleDataSupplier = exchange.getCandleDataSupplier(secondsPerCandle.get(), tradePair);
        toolbar = new CandleStickChartToolbar(widthProperty(), heightProperty(), candleDataSupplier.getSupportedGranularity());

        candleChartContainer = new VBox();
        candleChartContainer.getStyleClass().add("chart-stage");
        candleChartContainer.setFillWidth(true);
        candleChartContainer.setMinHeight(0);
        candleChartContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(candleChartContainer, Priority.ALWAYS);

        Rectangle chartClip = new Rectangle();
        chartClip.widthProperty().bind(candleChartContainer.widthProperty());
        chartClip.heightProperty().bind(candleChartContainer.heightProperty());
        candleChartContainer.setClip(chartClip);

        workspacePane = new ChartWorkspacePane(exchange, tradePair, toolbar, candleChartContainer);
        workspacePane.setMinSize(0, 0);
        VBox.setVgrow(workspacePane, Priority.ALWAYS);

        root = new VBox(workspacePane);
        root.getStyleClass().add("chart-shell");
        root.setFillWidth(true);
        root.setMinSize(0, 0);
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        root.prefHeightProperty().bind(prefHeightProperty());
        root.prefWidthProperty().bind(prefWidthProperty());
        VBox.setVgrow(candleChartContainer, Priority.ALWAYS);
        getChildren().setAll(root);

        chartLayout = createNewChart(secondsPerCandle.get());
        chartLayout.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(chartLayout, Priority.ALWAYS);
        candleChartContainer.getChildren().add(chartLayout);
        toolbar.registerEventHandlers(chartLayout.getChart(), secondsPerCandle);
        toolbar.setChartOptions(chartLayout.getChart().getChartOptions());
        toolbar.setActiveToolbarButton(secondsPerCandle);

        secondsPerCandle.addListener((obs, oldVal, newVal) -> {
            if (!oldVal.equals(newVal)) {
                ChartLayout newChart = createNewChart(newVal.intValue());
                newChart.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                VBox.setVgrow(newChart, Priority.ALWAYS);
                toolbar.registerEventHandlers(newChart.getChart(), secondsPerCandle);
                toolbar.setChartOptions(newChart.getChart().getChartOptions());
                toolbar.setActiveToolbarButton(secondsPerCandle);
                animateInNewChart(newChart);
            }
        });

        secondsPerCandle.set(3600);
    }


    private ChartLayout createNewChart(int secondsPerCandle) {
        if (secondsPerCandle <= 0) {
            throw new IllegalArgumentException("secondsPerCandle must be positive but was: " + secondsPerCandle);
        }

        try {
            return new ChartLayout(
                    exchange,
                    tradePair,
                    exchange.getCandleDataSupplier(secondsPerCandle, tradePair),
                    true,
                    secondsPerCandle,
                    candleChartContainer.widthProperty(),
                    candleChartContainer.heightProperty(),
                    tokens
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void animateInNewChart(ChartLayout newChart) {
        Objects.requireNonNull(newChart, "newChart must not be null");

        ChartLayout oldChart = chartLayout;
        chartLayout = newChart;
        chartLayout.setOpacity(1.0);
        candleChartContainer.getChildren().setAll(newChart);
        workspacePane.setCandlestickContent(candleChartContainer);

        if (oldChart != null) {
            oldChart.setVisible(false);
            oldChart.setManaged(false);
            oldChart.shutdown();
        }
    }

    @Override
    protected double computeMinWidth(double height) {
        return 350;
    }

    @Override
    protected double computeMinHeight(double width) {
        return 350;
    }

    @Override
    protected void layoutChildren() {
        root.resizeRelocate(0, 0, getWidth(), getHeight());
    }

    @Override
    protected double computePrefWidth(double height) {
        return root.prefWidth(height);
    }

    @Override
    protected double computePrefHeight(double width) {
        return root.prefHeight(width);
    }

    public CandleStickChart getChart() {
        return chartLayout.getChart();
    }

    public TradePair getTradePair() {
        return tradePair;
    }

    public int getSecondsPerCandle() {
        return secondsPerCandle.get();
    }

    public void setSecondsPerCandle(int seconds) {
        if (seconds > 0 && secondsPerCandle.get() != seconds) {
            secondsPerCandle.set(seconds);
        }
    }

    public CandleStickChartOptions getChartOptions() {
        return getChart().getChartOptions();
    }

    public Node createChartOptionsPane() {
        return getChartOptions().createMirroredOptionsPane();
    }

    public void refreshChart() {
        getChart().refreshChart();
    }

    public void showTradeTicket() {
        getChart().showTradeTicket();
    }

    public void toggleAiTrading() {
        getChart().toggleAiTrading();
    }

    public boolean isAiTradingEnabled() {
        return getChart().isAiTradingEnabled();
    }

    public void jumpToLatestCandle() {
        getChart().jumpToLatestCandle();
    }

    public void fitChart() {
        getChart().fitChart();
    }

    public void captureScreenshot() {
        getChart().captureScreenshot();
    }

    public void toggleBidAskLines() {
        getChart().setBidAskLinesVisible(!getChart().isBidAskLinesVisible());
    }

    public boolean isBidAskLinesVisible() {
        return getChart().isBidAskLinesVisible();
    }

    public void toggleVolumeBars() {
        CandleStickChartOptions options = getChartOptions();
        options.setShowVolume(!options.isShowVolume());
        refreshChart();
    }

    public double getLatestClosePrice() {
        return getChart().getLatestClosePrice();
    }

    public String getSignalBias() {
        return getChart().getSignalBias();
    }

    public int getLoadedCandleCount() {
        return getChart().getLoadedCandleCount();
    }

    public void shutdown() {
        if (chartLayout != null) {
            chartLayout.shutdown();
        }
        candleChartContainer.getChildren().clear();
        workspacePane.shutdown();
    }
}
