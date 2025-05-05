package org.investpro.investpro.ui.chart;

import javafx.animation.FadeTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.investpro.investpro.*;
import org.investpro.investpro.model.TradePair;
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
    private final Exchange exchange;
    private final TradePair tradePair;
    private final SimpleIntegerProperty secondsPerCandle;
    private ChartLayout chartLayout;

    public CandleStickChartContainer(Exchange exchange, TradePair tradePair, String tokens) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(tradePair, "tradePair must not be null");

        this.exchange = exchange;
        this.tradePair = tradePair;
        this.secondsPerCandle = new SimpleIntegerProperty(3600);

        getStyleClass().add("candle-chart-container");
        setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);

        CandleDataSupplier candleDataSupplier = exchange.getCandleDataSupplier(secondsPerCandle.get(), tradePair);
        toolbar = new CandleStickChartToolbar(widthProperty(), heightProperty(), candleDataSupplier.getSupportedGranularity());

        VBox toolbarContainer = new VBox(toolbar);
        toolbarContainer.setPrefWidth(Double.MAX_VALUE);
        toolbarContainer.setPrefHeight(50);
        toolbarContainer.prefWidthProperty().bind(prefWidthProperty());
        AnchorPane.setTopAnchor(toolbarContainer, 10.0);
        AnchorPane.setLeftAnchor(toolbarContainer, 82.0);
        AnchorPane.setRightAnchor(toolbarContainer, 0.0);

        candleChartContainer = new VBox();
        candleChartContainer.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        AnchorPane.setTopAnchor(candleChartContainer, 46.0);
        AnchorPane.setLeftAnchor(candleChartContainer, 15.0);
        AnchorPane.setRightAnchor(candleChartContainer, 15.0);
        AnchorPane.setBottomAnchor(candleChartContainer, 0.0);

        AnchorPane containerRoot = new AnchorPane(toolbarContainer, candleChartContainer);
        containerRoot.prefHeightProperty().bind(prefHeightProperty());
        containerRoot.prefWidthProperty().bind(prefWidthProperty());
        getChildren().setAll(containerRoot);

        chartLayout = createNewChart(secondsPerCandle.get());
        candleChartContainer.getChildren().add(chartLayout);
        toolbar.registerEventHandlers(chartLayout.getChart(), secondsPerCandle);
        toolbar.setChartOptions(chartLayout.getChart().getChartOptions());
        toolbar.setActiveToolbarButton(secondsPerCandle);

        secondsPerCandle.addListener((obs, oldVal, newVal) -> {
            if (!oldVal.equals(newVal)) {
                ChartLayout newChart = createNewChart(newVal.intValue());
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
            String tokens = "2032573404:AAGnxJpNMJBKqLzvE5q4kGt1cCGF632bP7A";
            return new ChartLayout(exchange, tradePair, exchange.getCandleDataSupplier(secondsPerCandle, tradePair), true, secondsPerCandle, widthProperty(), heightProperty(), tokens);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void animateInNewChart(ChartLayout newChart) {
        Objects.requireNonNull(newChart, "newChart must not be null");

        if (chartLayout != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(500), chartLayout);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                chartLayout = newChart;
                candleChartContainer.getChildren().setAll(newChart);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(500), chartLayout);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            fadeOut.play();
        } else {
            chartLayout = newChart;
            candleChartContainer.getChildren().setAll(newChart);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(500), chartLayout);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
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
}
