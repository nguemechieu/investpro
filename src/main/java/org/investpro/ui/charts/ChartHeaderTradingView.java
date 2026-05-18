package org.investpro.ui.charts;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.indicators.ChartIndicator;
import org.investpro.indicators.SimpleMovingAverageIndicator;
import org.investpro.indicators.ExponentialMovingAverageIndicator;
import org.investpro.indicators.RSIIndicator;
import org.investpro.indicators.MACDIndicator;
import org.investpro.indicators.BollingerBandsIndicator;
import org.investpro.indicators.StochasticIndicator;
import org.investpro.indicators.ATRIndicator;
import org.investpro.indicators.VolumeIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * TradingView-like professional chart header displaying key metrics.
 * Shows symbol, current price, change, volume, and other OHLCV info.
 *
 * @author NOEL NGUEMECHIEU
 */
@Slf4j
@Getter
public class ChartHeaderTradingView extends VBox {
    private static final String DARK_BG = "#0a0e17";
    private static final String SECONDARY_BG = "#101827";
    private static final String TEXT_PRIMARY = "#f1f5f9";
    private static final String TEXT_SECONDARY = "#9aa7ba";
    private static final String GREEN_COLOR = "#4CAF50";
    private static final String RED_COLOR = "#F44336";

    private final Label symbolLabel = new Label();
    private final Label priceLabel = new Label();
    private final Label changeLabel = new Label();
    private final Label timeframeLabel = new Label();

    private final Label openLabel = new Label();
    private final Label highLabel = new Label();
    private final Label lowLabel = new Label();
    private final Label closeLabel = new Label();
    private final Label volumeLabel = new Label();

    // Controls for indicators and chart type
    private final ComboBox<String> indicatorComboBox = new ComboBox<>();
    private final Button addIndicatorButton = new Button("+ Indicator");
    private final ComboBox<String> chartTypeComboBox = new ComboBox<>();

    public ChartHeaderTradingView(@NotNull CandleStickChart candleStickChart) {

        this.candleStickChart = candleStickChart;
        initializeUI();
    }

    List<CandleData> candleDataList = Collections.synchronizedList(new ArrayList<>());
    private final CandleStickChart candleStickChart;

    private void initializeUI() {
        CompletableFuture.runAsync(() -> {
            candleDataList.addAll(candleStickChart.getAllCandleData());

            // Display the latest candle data
            if (!candleDataList.isEmpty()) {
                CandleData latestCandle = candleDataList.getLast();
                javafx.application.Platform.runLater(() -> updateWithCandle(
                        candleStickChart.getTradePair().toString('/'),
                        candleStickChart.getTradePair().getLastPrice(),
                        candleStickChart.getTradePair().getChange(),
                        candleStickChart.getTradePair().getChangePercent(),
                        latestCandle.openPrice(),
                        latestCandle.highPrice(),
                        latestCandle.lowPrice(),
                        latestCandle.closePrice(),
                        latestCandle.volume(),
                        "1h"));
            } else {
                // Fallback: display trade pair price data if no candles available
                javafx.application.Platform.runLater(() -> updateWithCandle(
                        candleStickChart.getTradePair().toString('/'),
                        candleStickChart.getTradePair().getLastPrice(),
                        candleStickChart.getTradePair().getChange(),
                        candleStickChart.getTradePair().getChangePercent(),
                        0, 0, 0, candleStickChart.getTradePair().getLastPrice(), 0,
                        "1h"));
            }
        });
        setPrefHeight(120);
        setStyle("-fx-background-color: " + SECONDARY_BG + "; "
                + "-fx-border-color: #263246; "
                + "-fx-border-width: 0 0 1 0;");
        setPadding(new Insets(12, 16, 12, 16));
        setSpacing(8);

        // Top row: Symbol, Price, Change, Timeframe
        HBox topRow = createTopRow();
        getChildren().add(topRow);

        // Control row: Chart Type, Indicator selector, Add button
        HBox controlRow = createControlRow();
        getChildren().add(controlRow);

        // Bottom row: OHLCV values
        HBox bottomRow = createBottomRow();
        getChildren().add(bottomRow);

    }

    private @NotNull HBox createTopRow() {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);

        // Symbol
        symbolLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");
        symbolLabel.setText(candleStickChart.getTradePair().toString('/'));

        // Price
        priceLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");
        priceLabel.setText("45,231.50");

        // Change percentage and absolute
        changeLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + GREEN_COLOR + ";");
        changeLabel.setText("↑ +2.35% (+1,045.50)");

        // Timeframe
        timeframeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_SECONDARY + ";");
        timeframeLabel.setText("1h");

        // Add spacer to push timeframe to the right
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        row.getChildren().addAll(symbolLabel, priceLabel, changeLabel, spacer, timeframeLabel);
        return row;
    }

    private @NotNull HBox createControlRow() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 0, 0, 0));

        // Chart Type Selector
        Label chartTypeLabel = new Label("Chart:");
        chartTypeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_SECONDARY + ";");

        chartTypeComboBox.getItems().addAll("Candlestick", "Line", "Bar", "Area");
        chartTypeComboBox.setValue("Candlestick");
        chartTypeComboBox.setStyle("-fx-font-size: 11px; -fx-padding: 4;");
        chartTypeComboBox.setOnAction(e -> handleChartTypeChange(chartTypeComboBox.getValue()));

        // Indicator Selector
        Label indicatorLabel = new Label("Indicator:");
        indicatorLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_SECONDARY + ";");

        indicatorComboBox.getItems().addAll(
                "SMA (Simple MA)",
                "EMA (Exponential MA)",
                "RSI (Relative Strength)",
                "MACD",
                "Bollinger Bands",
                "Volume",
                "ATR (Average True Range)",
                "Stochastic");
        indicatorComboBox.setStyle("-fx-font-size: 11px; -fx-padding: 4;");
        indicatorComboBox.setPromptText("Select indicator...");

        // Add Indicator Button
        addIndicatorButton.setStyle(
                "-fx-font-size: 11px; " +
                        "-fx-padding: 4 12; " +
                        "-fx-background-color: #4CAF50; " +
                        "-fx-text-fill: white; " +
                        "-fx-cursor: hand;");
        addIndicatorButton.setOnAction(e -> handleAddIndicator(indicatorComboBox.getValue()));

        // Spacer
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        row.getChildren().addAll(
                chartTypeLabel, chartTypeComboBox,
                new Label(" | "),
                indicatorLabel, indicatorComboBox,
                addIndicatorButton,
                spacer);
        return row;
    }

    private void handleChartTypeChange(String chartType) {
        if (chartType == null || chartType.isEmpty()) {
            return;
        }

        try {
            switch (chartType.toLowerCase()) {
                case "candlestick" -> log.debug("Chart type: Candlestick (current)");
                case "line"        -> log.debug("Chart type: Line (feature coming soon)");
                case "bar"         -> log.debug("Chart type: Bar (feature coming soon)");
                case "area"        -> log.debug("Chart type: Area (feature coming soon)");
                default            -> log.warn("Unknown chart type: {}", chartType);
            }
        } catch (Exception e) {
            log.error("Error changing chart type: {}", chartType, e);
        }
    }

    private void handleAddIndicator(String indicatorName) {
        if (indicatorName == null || indicatorName.isEmpty()) {
            log.warn("No indicator selected");
            return;
        }

        try {
            ChartIndicator indicator = createIndicator(indicatorName);
            if (indicator != null) {
                candleStickChart.addIndicator(indicator);
                log.info("Added indicator: {}", indicatorName);
                indicatorComboBox.setValue(null);
            }
        } catch (Exception e) {
            log.error("Error adding indicator: {}", indicatorName, e);
        }
    }

    /**
     * Create a ChartIndicator instance based on indicator name
     */
    private ChartIndicator createIndicator(String indicatorName) {
        if (indicatorName == null || indicatorName.isEmpty()) {
            return null;
        }

        try {
            // Parse the indicator type from the combo box value
            String type = indicatorName.split(" ")[0].toUpperCase();

            return switch (type) {
                case "SMA" -> {
                    log.debug("Creating SMA20 indicator");
                    yield new SimpleMovingAverageIndicator(20);
                }
                case "EMA" -> {
                    log.debug("Creating EMA12 indicator");
                    yield new ExponentialMovingAverageIndicator(12);
                }
                case "RSI" -> {
                    log.debug("Creating RSI14 indicator");
                    yield new RSIIndicator(14);
                }
                case "MACD" -> {
                    log.debug("Creating MACD indicator");
                    yield new MACDIndicator();
                }
                case "BOLLINGER" -> {
                    log.debug("Creating Bollinger Bands indicator");
                    yield new BollingerBandsIndicator(20, 2.0);
                }
                case "VOLUME" -> {
                    log.debug("Creating Volume indicator");
                    yield new VolumeIndicator();
                }
                case "ATR" -> {
                    log.debug("Creating ATR14 indicator");
                    yield new ATRIndicator(14);
                }
                case "STOCHASTIC" -> {
                    log.debug("Creating Stochastic indicator");
                    yield new StochasticIndicator(14, 3, 3);
                }
                default -> {
                    log.warn("Unknown indicator type: {}", type);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.error("Error creating indicator: {}", indicatorName, e);
            return null;
        }
    }

    private HBox createBottomRow() {
        HBox row = new HBox(24);
        row.setAlignment(Pos.CENTER_LEFT);

        // OHLCV labels
        openLabel.setText("O 44,500");
        highLabel.setText("H 45,890");
        lowLabel.setText("L 44,200");
        closeLabel.setText("C 45,231");
        volumeLabel.setText("V 234.5K");

        for (Label label : new Label[] { openLabel, highLabel, lowLabel, closeLabel, volumeLabel }) {
            label.setStyle("-fx-font-size: 11px; "
                    + "-fx-font-family: 'Courier New'; "
                    + "-fx-text-fill: " + TEXT_SECONDARY + ";");
        }

        row.getChildren().addAll(openLabel, highLabel, lowLabel, closeLabel, volumeLabel);
        return row;
    }

    /**
     * Update header with candle data
     */
    public void updateWithCandle(String symbol, double price, double change, double changePercent,
            double open, double high, double low, double close, double volume, String timeframe) {
        symbolLabel.setText(symbol);
        priceLabel.setText(String.format("%.2f", price));

        // Color change based on sign
        String changeColor = change >= 0 ? GREEN_COLOR : RED_COLOR;
        String changeText = String.format("%s %+.2f (%+.2f%%)",
                change >= 0 ? "↑" : "↓", change, changePercent);
        changeLabel.setText(changeText);
        changeLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + changeColor + ";");

        timeframeLabel.setText(timeframe);

        openLabel.setText(String.format("O %.2f", open));
        highLabel.setText(String.format("H %.2f", high));
        lowLabel.setText(String.format("L %.2f", low));
        closeLabel.setText(String.format("C %.2f", close));

        // Format volume
        String volumeText;
        if (volume >= 1_000_000) {
            volumeText = String.format("V %.1fM", volume / 1_000_000);
        } else if (volume >= 1_000) {
            volumeText = String.format("V %.1fK", volume / 1_000);
        } else {
            volumeText = String.format("V %.0f", volume);
        }
        volumeLabel.setText(volumeText);
        updateChange(price, changePercent);
        updatePrice(price);
    }

    /**
     * Update with specific values
     */
    public void updatePrice(double price) {
        priceLabel.setText(String.format("%.2f", price));
    }

    public void updateChange(double change, double changePercent) {
        String changeColor = change >= 0 ? GREEN_COLOR : RED_COLOR;
        String changeText = String.format("%s %+.2f (%+.2f%%)",
                change >= 0 ? "↑" : "↓", change, changePercent);
        changeLabel.setText(changeText);
        changeLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + changeColor + ";");
    }

    public void setSymbol(String symbol) {
        symbolLabel.setText(symbol);
    }

    public void setTimeframe(String timeframe) {
        timeframeLabel.setText(timeframe);
    }

    /**
     * Update header with latest candle data from chart.
     * Call this whenever chart data is updated or candles are refreshed.
     */
    public void updateFromChart() {
        CompletableFuture.runAsync(() -> {
            List<CandleData> allCandles = candleStickChart.getAllCandleData();

            if (!allCandles.isEmpty()) {
                CandleData latest = allCandles.get(allCandles.size() - 1);
                javafx.application.Platform.runLater(() -> updateWithCandle(
                        candleStickChart.getTradePair().toString('/'),
                        candleStickChart.getTradePair().getLastPrice(),
                        candleStickChart.getTradePair().getChange(),
                        candleStickChart.getTradePair().getChangePercent(),
                        latest.openPrice(),
                        latest.highPrice(),
                        latest.lowPrice(),
                        latest.closePrice(),
                        latest.volume(),
                        timeframeLabel.getText()));
            }
        });
    }

    /**
     * Add a technical indicator to the chart.
     * Supported indicators: SMA, EMA, RSI, MACD, Bollinger Bands, Volume, ATR,
     * Stochastic
     */
    public void addIndicator(String indicatorType) {
        if (indicatorType == null || indicatorType.isEmpty()) {
            return;
        }

        try {
            ChartIndicator indicator = createIndicator(indicatorType);
            if (indicator != null) {
                candleStickChart.addIndicator(indicator);
                log.info("Successfully added indicator: {}", indicatorType);
            }
        } catch (Exception e) {
            log.error("Error adding indicator: {}", indicatorType, e);
        }
    }

    /**
     * Change the chart display type.
     * Supported types: Candlestick, Line, Bar, Area
     */
    public void setChartType(String chartType) {
        if (chartType == null || chartType.isEmpty()) {
            return;
        }

        try {
            switch (chartType.toLowerCase()) {
                case "candlestick" -> {
                    log.debug("Chart type: Candlestick (current)");
                    chartTypeComboBox.setValue("Candlestick");
                }
                case "line" -> {
                    log.debug("Chart type: Line (feature coming soon)");
                    chartTypeComboBox.setValue("Line");
                }
                case "bar" -> {
                    log.debug("Chart type: Bar (feature coming soon)");
                    chartTypeComboBox.setValue("Bar");
                }
                case "area" -> {
                    log.debug("Chart type: Area (feature coming soon)");
                    chartTypeComboBox.setValue("Area");
                }
                default -> log.warn("Unknown chart type: {}", chartType);
            }
        } catch (Exception e) {
            log.error("Error changing chart type: {}", chartType, e);
        }
    }

    /**
     * Get the currently selected chart type
     */
    public String getSelectedChartType() {
        return chartTypeComboBox.getValue();
    }

    /**
     * Get the list of available indicators
     */
    public List<String> getAvailableIndicators() {
        return new ArrayList<>(indicatorComboBox.getItems());
    }
}
