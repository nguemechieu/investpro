package org.investpro;

import javafx.animation.FadeTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.io.IOException;
import java.sql.SQLException;
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
public class ChartContainer extends Region {
   final CandleStickChartToolbar toolbar;
   final VBox candleChartContainer = new VBox();
    private final Exchange exchange;
    private TradePair tradePair;
   final SimpleIntegerProperty secondsPerCandle;

    public TradePair getTradePair() {
        return tradePair;
    }

    /**
     * Construct a new {@code CandleStickChartContainer} with liveSyncing mode off.
     */


    public ChartContainer(Exchange exchange, boolean liveSyncing) {
        Objects.requireNonNull(exchange, "exchange must not be null");

        this.exchange = exchange;

        candleChartContainer.setSpacing(10);
        candleChartContainer.setAlignment(Pos.CENTER);
        secondsPerCandle = new SimpleIntegerProperty(3600);
        getStyleClass().add("candle-chart-container");
        CandleDataSupplier candleDataSupplier = exchange.getCandleDataSupplier(secondsPerCandle.get(), tradePair);
        toolbar = new CandleStickChartToolbar(widthProperty(), heightProperty(

        ),
                candleDataSupplier.getSupportedGranularity());
        HBox toolbarContainer = new HBox(toolbar);
        toolbarContainer.setPrefWidth(Double.MAX_VALUE);
        toolbarContainer.setPrefHeight(20);
        toolbarContainer.prefWidthProperty().bind(prefWidthProperty());
        AnchorPane.setTopAnchor(toolbarContainer, 10.0);
        AnchorPane.setLeftAnchor(toolbarContainer, 82.0);
        AnchorPane.setRightAnchor(toolbarContainer, 0.0);
        AnchorPane.setBottomAnchor(toolbarContainer, 0.0);

        AnchorPane.setTopAnchor(candleChartContainer, 46.0);
        AnchorPane.setLeftAnchor(candleChartContainer, 15.0);
        AnchorPane.setRightAnchor(candleChartContainer, 15.0);
        AnchorPane.setBottomAnchor(candleChartContainer, 0.0);
        AnchorPane containerRoot = new AnchorPane(toolbarContainer, candleChartContainer);

        containerRoot.prefHeightProperty().bind(prefHeightProperty());
        containerRoot.prefWidthProperty().bind(prefWidthProperty());
        containerRoot.setBorder(Border.stroke(Color.PINK));
        getChildren().setAll(containerRoot);
        toolbar.registerEventHandlers(candleStickChart, secondsPerCandle);

        secondsPerCandle.addListener((_, oldDurationValue, newDurationValue) -> {
            if (!oldDurationValue.equals(newDurationValue)) {
                try {
                    try {
                        createNewChart(newDurationValue.intValue(), liveSyncing);
                    } catch (ClassNotFoundException | IOException e) {
                        throw new RuntimeException(e);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                toolbar.registerEventHandlers(candleStickChart, secondsPerCandle);
                toolbar.setChartOptions(candleStickChart.getChartOptions());
                toolbar.setActiveToolbarButton(secondsPerCandle);
                animateInNewChart(candleStickChart);
            }
        });

        secondsPerCandle.set(300);
    }
    private CandleStickChart candleStickChart;

    public void setTradePair(TradePair tradePair) {
        this.tradePair = tradePair;
    }

    private void createNewChart(int secondsPerCandle, boolean liveSyncing) throws SQLException, ClassNotFoundException, IOException {
        if (secondsPerCandle <= 0) {
            throw new IllegalArgumentException("secondsPerCandle must be positive but was: %d".formatted(secondsPerCandle));
        }
        candleStickChart = new CandleStickChart(

                exchange, tradePair,
                exchange.getCandleDataSupplier(secondsPerCandle, tradePair),

                liveSyncing,
                secondsPerCandle,
                widthProperty(),
                heightProperty()
        );
    }

    private void animateInNewChart(CandleStickChart newChart) {
        Objects.requireNonNull(newChart, "newChart must not be null");

        if (candleStickChart != null) {
            FadeTransition fadeTransitionOut = new FadeTransition(Duration.millis(500), candleStickChart);
            fadeTransitionOut.setFromValue(1.0);
            fadeTransitionOut.setToValue(0.0);
            fadeTransitionOut.setOnFinished(_ -> {
                candleStickChart = newChart;
                candleChartContainer.getChildren().setAll(newChart);
                FadeTransition fadeTransitionIn = new FadeTransition(Duration.millis(500), candleStickChart);
                fadeTransitionIn.setFromValue(0.0);
                fadeTransitionIn.setToValue(1.0);
                fadeTransitionIn.play();
            });

            fadeTransitionOut.play();
        } else {
            candleStickChart = newChart;
            candleChartContainer.getChildren().setAll(newChart);
            FadeTransition fadeTransitionIn = new FadeTransition(Duration.millis(500), candleStickChart);
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
