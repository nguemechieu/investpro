package org.investpro;

import javafx.animation.FadeTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * A {@link Region} that contains a {@code CandleStickChart} and a {@code CandleStickChartToolbar}.
 * The contained chart will display data for the given {@code tradePair}. The toolbar allows for changing
 * the duration in seconds of each candle as well as configuring the properties of the chart. When a new
 * duration is selected, this container automatically creates a new {@code CandleStickChart} and visually
 * transitions to it.
 *
 * @author NOEL NGUEMECHIEU
 */
@Getter
@Setter
public class CandleStickChartContainer extends Region {
    private final BorderPane candleChartContainer;
    private final CandleStickChartToolbar toolbar;
    private final Exchange exchange;
    private final TradePair tradePair;

    private SimpleIntegerProperty secondsPerCandle;
    private CandleStickChart candleStickChart;


    public CandleStickChartContainer(Exchange exchange, TradePair tradePair, boolean liveSyncing) {
        Objects.requireNonNull(exchange, "exchange must not be null");

        this.exchange = exchange;
        this.tradePair = tradePair;



        secondsPerCandle = new SimpleIntegerProperty(3600);
        getStyleClass().add("candle-chart-container");
        setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        CandleDataSupplier candleDataSupplier = exchange.getCandleDataSupplier(secondsPerCandle.get(), tradePair);
        toolbar = new CandleStickChartToolbar(widthProperty(), heightProperty(),
                candleDataSupplier.getSupportedGranularity());
        VBox toolbarContainer = new VBox(toolbar);
        toolbarContainer.setPrefWidth(Double.MAX_VALUE);
        toolbarContainer.setPrefHeight(50);
        toolbarContainer.prefWidthProperty().bind(prefWidthProperty());
        AnchorPane.setTopAnchor(toolbarContainer, 10.0);
        AnchorPane.setLeftAnchor(toolbarContainer, 82.0);
        AnchorPane.setRightAnchor(toolbarContainer, 0.0);

        candleChartContainer = new BorderPane();
        candleChartContainer.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        AnchorPane.setTopAnchor(candleChartContainer, 46.0);
        AnchorPane.setLeftAnchor(candleChartContainer, 15.0);
        AnchorPane.setRightAnchor(candleChartContainer, 15.0);
        AnchorPane.setBottomAnchor(candleChartContainer, 0.0);

        AnchorPane containerRoot = new AnchorPane(toolbarContainer, candleChartContainer);
        containerRoot.prefHeightProperty().bind(prefHeightProperty());
        containerRoot.prefWidthProperty().bind(prefWidthProperty());
        getChildren().setAll(containerRoot);

        toolbar.registerEventHandlers(candleStickChart, secondsPerCandle);

        secondsPerCandle.addListener((_, oldDurationValue, newDurationValue) -> {
            if (!oldDurationValue.equals(newDurationValue)) {
                try {
                    createNewChart(newDurationValue.intValue(), liveSyncing);
                } catch (TelegramApiException | Exception e) {
                    throw new RuntimeException(e);
                }
                toolbar.registerEventHandlers(candleStickChart, secondsPerCandle);
                toolbar.setChartOptions(candleStickChart.getChartOptions());
                toolbar.setActiveToolbarButton(secondsPerCandle);
                animateInNewChart(candleStickChart);
            }
        });
        secondsPerCandle.set(3600);
        if (liveSyncing) {
            try {
                createNewChart(secondsPerCandle.get(), true);
            } catch (Exception | TelegramApiException e) {
                throw new RuntimeException(e);
            }
            animateInNewChart(candleStickChart);
            toolbar.registerEventHandlers(candleStickChart, secondsPerCandle);
            toolbar.setChartOptions(candleStickChart.getChartOptions());
            toolbar.setActiveToolbarButton(secondsPerCandle);
        }


    }

    private void createNewChart(int secondsPerCandle, boolean liveSyncing) throws Exception, TelegramApiException {
        if (secondsPerCandle <= 0) {
            throw new IllegalArgumentException("secondsPerCandle must be positive but was: %d".formatted(secondsPerCandle));
        }


        String telegram_token = "2032573404:AAGnxJpNMJBKqLzvE5q4kGt1cCGF632bP7A";
        candleStickChart = new CandleStickChart(exchange, exchange.getCandleDataSupplier(secondsPerCandle, tradePair)
                , tradePair, liveSyncing, secondsPerCandle, widthProperty(), heightProperty(), telegram_token);
    }

    private void animateInNewChart(CandleStickChart newChart) {
        Objects.requireNonNull(newChart, "newChart must not be null");


            candleStickChart = newChart;
            candleChartContainer.getChildren().setAll(newChart);
            FadeTransition fadeTransitionIn = new FadeTransition(Duration.millis(500), candleStickChart);
            fadeTransitionIn.setFromValue(0.0);
            fadeTransitionIn.setToValue(1.0);
            fadeTransitionIn.play();

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