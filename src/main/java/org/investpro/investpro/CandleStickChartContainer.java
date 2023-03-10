package org.investpro.investpro;

import javafx.animation.FadeTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * A {@link Region} that contains a {@code CandleStickChart} and a {@code CandleStickChartToolbar}.
 * The contained chart will display data for the given {@code tradePair}. The toolbar allows for changing
 * the duration in seconds of each candle as well as configuring the properties of the chart. When a new
 * duration is selected, this container automatically creates a new {@code CandleStickChart} and visually
 * transitions to it.
 *
 * @author noel martial nguemechieu
 */


public class CandleStickChartContainer extends Region {
    private final VBox candleChartContainer;
    private final CandleStickChartToolbar toolbar;
    private final Exchange exchange;
    private final String tradePair;
    private final SimpleIntegerProperty secondsPerCandle;
    private CandleStickChart candleStickChart;

    /**
     * Construct a new {@code CandleStickChartContainer} with liveSyncing mode off.
     */
    public CandleStickChartContainer(Exchange exchange, String tradePair) throws URISyntaxException, IOException {
        this(exchange, tradePair, true);
    }

    public CandleStickChartContainer(Exchange exchange, String tradePair, boolean liveSyncing) throws URISyntaxException, IOException {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        this.exchange = exchange;
        this.tradePair = tradePair;
        secondsPerCandle = new SimpleIntegerProperty(3600);
        getStyleClass().add("candle-chart-container");
        setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        CandleDataSupplier candleDataSupplier = exchange.getCandleDataSupplier(secondsPerCandle.get(), tradePair);
        toolbar = new CandleStickChartToolbar(widthProperty(), heightProperty(),
                candleDataSupplier.getSupportedGranularities());
        VBox toolbarContainer = new VBox(toolbar);
        toolbarContainer.setPrefWidth(Double.MAX_VALUE);
        toolbarContainer.setPrefHeight(20);
        toolbarContainer.prefWidthProperty().bind(prefWidthProperty());
        AnchorPane.setTopAnchor(toolbarContainer, 10.0);
        AnchorPane.setLeftAnchor(toolbarContainer, 82.0);
        AnchorPane.setRightAnchor(toolbarContainer, 0.0);


        candleChartContainer = new VBox();

        AnchorPane.setTopAnchor(candleChartContainer, 46.0);
        AnchorPane.setLeftAnchor(candleChartContainer, 15.0);
        AnchorPane.setRightAnchor(candleChartContainer, 15.0);
        AnchorPane.setBottomAnchor(candleChartContainer, 0.0);

        AnchorPane containerRoot = new AnchorPane(toolbarContainer, candleChartContainer);
        containerRoot.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        getChildren().setAll(containerRoot);
        // FIXME: candleStickChart is null at this point.
        toolbar.registerEventHandlers(candleStickChart, secondsPerCandle);

        secondsPerCandle.addListener((observableDurationValue, oldDurationValue, newDurationValue) -> {
            if (!oldDurationValue.equals(newDurationValue)) {
                createNewChart(newDurationValue.intValue(), liveSyncing);
                try {
                    toolbar.registerEventHandlers(candleStickChart, secondsPerCandle);
                } catch (URISyntaxException | IOException e) {
                    throw new RuntimeException(e);
                }
                toolbar.setChartOptions(candleStickChart.getChartOptions());
                toolbar.setActiveToolbarButton(secondsPerCandle);
                animateInNewChart(candleStickChart);
            }
        });

        //secondsPerCandle.set(300);
    }

    private void createNewChart(int secondsPerCandle, boolean liveSyncing) {
        if (secondsPerCandle <= 0) {
            throw new IllegalArgumentException("secondsPerCandle must be positive but was: " + secondsPerCandle);
        }
        /*
        CandleDataSupplier candleDataSupplier = new ReverseRawTradeDataProcessor(Paths.get("C:\\bitstampUSD.csv"),
                secondsPerCandle.get(), TradePair.of(amountUnit, priceUnit));
        */


        candleStickChart = new CandleStickChart(exchange, exchange.getCandleDataSupplier(secondsPerCandle, tradePair),
                tradePair, liveSyncing, secondsPerCandle, widthProperty(), heightProperty());
    }

    private void animateInNewChart(CandleStickChart newChart) {
        Objects.requireNonNull(newChart, "newChart must not be null");

        if (candleStickChart != null) {
            FadeTransition fadeTransitionOut = new FadeTransition(Duration.millis(300), candleStickChart);
            fadeTransitionOut.setFromValue(1.0);
            fadeTransitionOut.setToValue(0.0);
            fadeTransitionOut.setOnFinished(event -> {
                candleStickChart = newChart;
                candleChartContainer.getChildren().setAll(newChart);
                FadeTransition fadeTransitionIn = new FadeTransition(Duration.millis(300), candleStickChart);
                fadeTransitionIn.setFromValue(0.0);
                fadeTransitionIn.setToValue(1.0);
                fadeTransitionIn.play();
            });

            fadeTransitionOut.play();
        } else {
            candleStickChart = newChart;
            candleChartContainer.getChildren().setAll(newChart);
            FadeTransition fadeTransitionIn = new FadeTransition(Duration.millis(400), candleStickChart);
            fadeTransitionIn.setFromValue(0.0);
            fadeTransitionIn.setToValue(1.0);
            fadeTransitionIn.play();
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