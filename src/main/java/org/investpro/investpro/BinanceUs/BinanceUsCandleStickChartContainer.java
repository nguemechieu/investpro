package org.investpro.investpro.BinanceUs;

import javafx.animation.FadeTransition;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.investpro.investpro.ZoomDirection;

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


public class BinanceUsCandleStickChartContainer extends Region {

     Binance.BinanceU binanceUs;
    boolean sync=true;
    private  VBox candleChartContainer;
    private BinanceUsCandleStickChartToolbar toolbar;
    private  BinanceUsExchange exchange;
    private final String tradePair;
    private SimpleIntegerProperty secondsPerCandle;
    private BinanceUsCandleStickChartContainer candleStickChart;

    /**
     * Construct a new {@code CandleStickChartContainer} with liveSyncing mode off.
     */

    public BinanceUsCandleStickChartContainer(BinanceUsExchange exchange, BinanceUsCandleDataSupplier candleDataSupplier, String tradePair, boolean liveSyncing, int secondsPerCandle, ReadOnlyDoubleProperty readOnlyDoubleProperty, ReadOnlyDoubleProperty onlyDoubleProperty) throws URISyntaxException, IOException {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        this.exchange = exchange;
        this.tradePair = tradePair;
        this.secondsPerCandle = new SimpleIntegerProperty(3600);
        getStyleClass().add("candle-chart-container");
        setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
         candleDataSupplier = exchange.getCandleDataSupplier(this.secondsPerCandle.get(), tradePair);
        toolbar = new BinanceUsCandleStickChartToolbar(widthProperty(), heightProperty(),
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
        toolbar.registerEventHandlers(candleStickChart, this.secondsPerCandle);

        this.secondsPerCandle.addListener((observableDurationValue, oldDurationValue, newDurationValue) -> {
            if (!oldDurationValue.equals(newDurationValue)) {
                try {
                    createNewChart(newDurationValue.intValue(), liveSyncing);
                } catch (URISyntaxException | IOException e) {
                    throw new RuntimeException(e);
                }
                try {
                    toolbar.registerEventHandlers(candleStickChart, this.secondsPerCandle);
                } catch (URISyntaxException | IOException e) {
                    throw new RuntimeException(e);
                }
                toolbar.setChartOptions(candleStickChart.getChartOptions());
                toolbar.setActiveToolbarButton(this.secondsPerCandle);
                animateInNewChart(candleStickChart);
            }
        });

        //secondsPerCandle.set(300);
    }

    public BinanceUsCandleStickChartContainer(Binance.BinanceU binanceUs, String btcUsd, boolean b) {

        this.binanceUs = binanceUs;
        this.tradePair=btcUsd;
        this.sync=b;
    }

    private BinanceUsCandleStickChartOptions getChartOptions() {
        return new BinanceUsCandleStickChartOptions();
    }


    private void createNewChart(int secondsPerCandle, boolean liveSyncing) throws URISyntaxException, IOException {
        if (secondsPerCandle <= 0) {
            throw new IllegalArgumentException("secondsPerCandle must be positive but was: " + secondsPerCandle);
        }
        /*
        CandleDataSupplier candleDataSupplier = new ReverseRawTradeDataProcessor(Paths.get("C:\\bitstampUSD.csv"),
                secondsPerCandle.get(), TradePair.of(amountUnit, priceUnit));
        */


        candleStickChart = new BinanceUsCandleStickChartContainer(exchange, exchange.getCandleDataSupplier(secondsPerCandle, tradePair),
                tradePair, liveSyncing, secondsPerCandle, widthProperty(), heightProperty());
    }

    private void animateInNewChart(BinanceUsCandleStickChartContainer newChart) {
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

    public void changeZoom(ZoomDirection zoomDirection) {
    }
}