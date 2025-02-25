package org.investpro;

import javafx.animation.FadeTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;
import java.util.Set;

/**
 * A {@link javafx.scene.layout.Region} that contains a {@code CandleStickChart} and a {@code CandleStickChartToolbar}.
 * The contained chart will display data for the given {@code tradePair}. The toolbar allows for changing
 * the duration in seconds of each candle as well as configuring the properties of the chart. When a new
 * duration is selected, this container automatically creates a new {@code CandleStickChart} and visually
 * transitions to it.
 *
 * @author Michael Ennen
 */
@Getter
@Setter
public class CandleStickChartContainer extends Region {
    private VBox candleChartContainer;
    private final CandleStickChartToolbar toolbar;
    private final Exchange exchange;
    private final TradePair tradePair;
    private SimpleIntegerProperty secondsPerCandle;
    private CandleStickChart candleStickChart;

    /**
     * Construct a new {@code CandleStickChartContainer} with liveSyncing mode off.
     */


    public CandleStickChartContainer(Exchange exchange, TradePair tradePair, boolean liveSyncing) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        this.exchange = exchange;
        this.tradePair = tradePair;
        this.secondsPerCandle = new SimpleIntegerProperty(60);

        getStyleClass().add("candle-chart-container");
        setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);

        toolbar = new CandleStickChartToolbar(widthProperty(), heightProperty(),
                getSupportedGranularity());
        VBox toolbarContainer = new VBox(toolbar);
        toolbarContainer.setPrefWidth(Double.MAX_VALUE);
        toolbarContainer.setPrefHeight(50);
        toolbarContainer.prefWidthProperty().bind(prefWidthProperty());
        AnchorPane.setTopAnchor(toolbarContainer, 10.0);
        AnchorPane.setLeftAnchor(toolbarContainer, 82.0);
        AnchorPane.setRightAnchor(toolbarContainer, 0.0);

        this.candleChartContainer = new VBox();
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
                createNewChart(newDurationValue.intValue(), liveSyncing);
                toolbar.registerEventHandlers(candleStickChart, secondsPerCandle);
                toolbar.setChartOptions(candleStickChart.getChartOptions());
                toolbar.setActiveToolbarButton(secondsPerCandle);
                animateInNewChart(candleStickChart);
            }
            secondsPerCandle.set(60);
        });

    }


    public Set<Integer> getSupportedGranularity() {
        return Set.of(
                5,       // S5: 5 seconds
                10,      // S10: 10 seconds
                15,      // S15: 15 seconds
                30,      // S30: 30 seconds
                60,      // M1: 1 minute (60 seconds)
                300,     // M5: 5 minutes
                900,     // M15: 15 minutes
                1800,    // M30: 30 minutes
                3600,    // H1: 1 hour (3600 seconds)
                7200,    // H2: 2 hours
                10800,   // H3: 3 hours
                14400,   // H4: 4 hours
                21600,   // H6: 6 hours
                28800,   // H8: 8 hours
                43200,   // H12: 12 hours
                86400,   // D: 1 day (86400 seconds)
                604800,  // W: 1 week (604800 seconds)
                2592000  // M: 1 month (approx. 30 days, 2592000 seconds)
        );
    }

    private void createNewChart(int secondsPerCandle, boolean liveSyncing) {
        if (secondsPerCandle <= 0) {
            throw new IllegalArgumentException("secondsPerCandle must be positive but was: " + secondsPerCandle);
        }
        /*
        CandleDataSupplier candleDataSupplier = new ReverseRawTradeDataProcessor(Paths.get("C:\\bitstampUSD.csv"),
                secondsPerCandle.get(), TradePair.of(amountUnit, priceUnit));
        */
        try {
            String telegram_token = "2032573404:AAGnxJpNMJBKqLzvE5q4kGt1cCGF632bP7A";
            candleStickChart = new CandleStickChart(exchange,
                    tradePair, liveSyncing, secondsPerCandle, widthProperty(), heightProperty(), new TelegramClient(telegram_token));
        } catch (Exception | TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void animateInNewChart(CandleStickChart newChart) {
        Objects.requireNonNull(newChart, "newChart must not be null");

        if (candleStickChart != null) {
            FadeTransition fadeTransitionOut = new FadeTransition(Duration.millis(500), candleStickChart);
            fadeTransitionOut.setFromValue(1.0);
            fadeTransitionOut.setToValue(0.0);
            fadeTransitionOut.setOnFinished(event -> {
                candleStickChart = newChart;
                candleChartContainer.getChildren().setAll(newChart);
                FadeTransition fadeTransitionIn = new FadeTransition(Duration.millis(500), candleStickChart);
                fadeTransitionIn.setFromValue(0.0);
                fadeTransitionIn.setToValue(1.0);
                fadeTransitionIn.play();
                event.consume();
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
