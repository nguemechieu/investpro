package org.investpro.ui.charts;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.investpro.data.CandleData;
import org.investpro.indicators.*;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

/**
 * TradingView-style technical indicators panel.
 * Displays real-time indicator values with color-coded signals.
 *
 * @author NOEL NGUEMECHIEU
 */
@Getter
@Setter
@Slf4j
public class TechnicalIndicatorsPanel extends VBox {
    private static final String BG_COLOR = "#0f1724";
    private static final String SECONDARY_BG = "#1a1f2e";
    private static final String TEXT_PRIMARY = "#e5edf7";
    private static final String TEXT_SECONDARY = "#9aa7ba";
    private static final String GREEN = "#4CAF50";
    private static final String RED = "#F44336";
    private static final String YELLOW = "#FFC107";
    private static final String BLUE = "#2196F3";

    private ScrollPane scrollPane;
    private VBox indicatorsContainer;
    private final Map<String, IndicatorValueBox> indicatorBoxes = new LinkedHashMap<>();
    private CandleStickChart candleStickChart;

    // Configuration parameters from AppConfig (.env file)
    private static final int SMA_PERIOD_SHORT = AppConfig.getInt("INDICATOR_SMA_SHORT", 20);
    private static final int SMA_PERIOD_MID = AppConfig.getInt("INDICATOR_SMA_MID", 50);
    private static final int SMA_PERIOD_LONG = AppConfig.getInt("INDICATOR_SMA_LONG", 200);
    private static final int RSI_PERIOD = AppConfig.getInt("INDICATOR_RSI_PERIOD", 14);
    private static final int STOCHASTIC_PERIOD = AppConfig.getInt("INDICATOR_STOCHASTIC_PERIOD", 14);
    private static final int STOCHASTIC_K_PERIOD = AppConfig.getInt("INDICATOR_STOCHASTIC_K_PERIOD", 3);
    private static final int STOCHASTIC_D_PERIOD = AppConfig.getInt("INDICATOR_STOCHASTIC_D_PERIOD", 3);
    private static final int BB_PERIOD = AppConfig.getInt("INDICATOR_BB_PERIOD", 20);
    private static final double BB_STD_DEV = AppConfig.getDouble("INDICATOR_BB_STD_DEV", 2.0);
    private static final int ATR_PERIOD = AppConfig.getInt("INDICATOR_ATR_PERIOD", 14);
    private static final int VOLATILITY_PERIOD = AppConfig.getInt("INDICATOR_VOLATILITY_PERIOD", 20);
    private static final int VOLUME_PERIOD = AppConfig.getInt("INDICATOR_VOLUME_PERIOD", 20);

    // Technical Indicators (initialized with config values)
    private final SimpleMovingAverageIndicator sma20 = new SimpleMovingAverageIndicator(SMA_PERIOD_SHORT);
    private final SimpleMovingAverageIndicator sma50 = new SimpleMovingAverageIndicator(SMA_PERIOD_MID);
    private final SimpleMovingAverageIndicator sma200 = new SimpleMovingAverageIndicator(SMA_PERIOD_LONG);
    private final RSIIndicator rsi14 = new RSIIndicator(RSI_PERIOD);
    private final MACDIndicator macd = new MACDIndicator();
    private final StochasticIndicator stochastic = new StochasticIndicator(STOCHASTIC_PERIOD, STOCHASTIC_K_PERIOD,
            STOCHASTIC_D_PERIOD);
    private final BollingerBandsIndicator bb20 = new BollingerBandsIndicator(BB_PERIOD, BB_STD_DEV);
    private final ATRIndicator atr14 = new ATRIndicator(ATR_PERIOD);
    private final VolatilityIndicator volatility = new VolatilityIndicator(VOLATILITY_PERIOD);
    private final VolumeIndicator volumeIndicator = new VolumeIndicator(VOLUME_PERIOD);
    private final OBVIndicator obv = new OBVIndicator();

    private final DecimalFormat decimalFormat = new DecimalFormat("#.##");
    private volatile ScheduledFuture<?> updateFuture;

    public TechnicalIndicatorsPanel(@NotNull CandleStickChart candleStickChart) {
        this.candleStickChart = candleStickChart;
        initializeUI();
    }

    private void initializeUI() {
        int panelWidth = AppConfig.getInt("UI_INDICATORS_PANEL_WIDTH", 280);
        setPrefWidth(panelWidth);
        setStyle("-fx-background-color: " + BG_COLOR + "; "
                + "-fx-border-color: #263246; "
                + "-fx-border-width: 0 1 0 0;");

        // Header
        Label headerLabel = new Label("Technical Indicators");
        headerLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");
        VBox header = new VBox(headerLabel);
        header.setStyle("-fx-background-color: " + SECONDARY_BG + "; "
                + "-fx-border-color: #263246; "
                + "-fx-border-width: 0 0 1 0;");
        header.setPadding(new Insets(10, 12, 10, 12));

        // Indicators container with scroll
        indicatorsContainer = new VBox(8);
        indicatorsContainer.setPadding(new Insets(12));
        indicatorsContainer.setStyle("-fx-background-color: " + BG_COLOR + ";");

        scrollPane = new ScrollPane(indicatorsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: " + BG_COLOR + "; "
                + "-fx-control-inner-background: " + BG_COLOR + "; "
                + "-fx-padding: 0;");

        // Add common indicators
        initializeCommonIndicators();

        getChildren().addAll(header, scrollPane);
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);
    }

    private void initializeCommonIndicators() {
        // Moving Averages
        addIndicator("SMA" + SMA_PERIOD_SHORT, "Simple Moving Average " + SMA_PERIOD_SHORT);
        addIndicator("SMA" + SMA_PERIOD_MID, "Simple Moving Average " + SMA_PERIOD_MID);
        addIndicator("SMA" + SMA_PERIOD_LONG, "Simple Moving Average " + SMA_PERIOD_LONG);

        addSeparator();

        // Momentum Indicators
        addIndicator("RSI" + RSI_PERIOD, "Relative Strength Index");
        addIndicator("MACD", "Moving Average Convergence Divergence");
        addIndicator("Stochastic", "Stochastic Oscillator");

        addSeparator();

        // Volatility Indicators
        addIndicator("BB" + BB_PERIOD, "Bollinger Bands");
        addIndicator("ATR" + ATR_PERIOD, "Average True Range");
        addIndicator("Volatility", "Price Volatility");

        addSeparator();

        // Volume Indicators
        addIndicator("Volume", "Trading Volume");
        addIndicator("OBV", "On-Balance Volume");
    }

    private void addIndicator(@NotNull String name, String tooltip) {
        IndicatorValueBox box = new IndicatorValueBox(name, "—", TEXT_SECONDARY, tooltip);
        indicatorBoxes.put(name, box);
        indicatorsContainer.getChildren().add(box);
    }

    private void addSeparator() {
        Separator sep = new Separator();
        sep.setStyle("-fx-text-fill: #263246;");
        indicatorsContainer.getChildren().add(sep);
    }

    /**
     * Update indicator value
     */
    public void updateIndicator(@NotNull String indicatorName, @NotNull String value, String signalColor) {
        IndicatorValueBox box = indicatorBoxes.get(indicatorName);
        if (box != null) {
            box.updateValue(value, signalColor);
        }
    }

    /**
     * Update multiple indicators at once
     */
    public void updateIndicators(@NotNull Map<String, String> values, @NotNull Map<String, String> colors) {
        values.forEach((name, value) -> {
            String color = colors.getOrDefault(name, TEXT_SECONDARY);
            updateIndicator(name, value, color);
        });
    }

    /**
     * Update all indicators with real candle data and display signals
     */
    public void updateAllIndicators(@NotNull List<CandleData> candleData) {
        if (candleData.isEmpty()) {
            clearIndicators();
            return;
        }

        try {
            // Calculate all indicators
            sma20.calculate(candleData);
            sma50.calculate(candleData);
            sma200.calculate(candleData);
            rsi14.calculate(candleData);
            macd.calculate(candleData);
            stochastic.calculate(candleData);
            bb20.calculate(candleData);
            atr14.calculate(candleData);
            volatility.calculate(candleData);
            volumeIndicator.calculate(candleData);
            obv.calculate(candleData);

            // Get latest values and update display
            double lastPrice = candleData.get(candleData.size() - 1).closePrice();

            // Update Moving Averages
            updateMovingAverageIndicator("SMA" + SMA_PERIOD_SHORT, sma20, lastPrice);
            updateMovingAverageIndicator("SMA" + SMA_PERIOD_MID, sma50, lastPrice);
            updateMovingAverageIndicator("SMA" + SMA_PERIOD_LONG, sma200, lastPrice);

            // Update Momentum Indicators
            updateRSIIndicator("RSI" + RSI_PERIOD, rsi14);
            updateMACDIndicator("MACD", macd);
            updateStochasticIndicator("Stochastic", stochastic);

            // Update Volatility Indicators
            updateBollingerBandsIndicator("BB" + BB_PERIOD, bb20, lastPrice);
            updateATRIndicator("ATR" + ATR_PERIOD, atr14);
            updateOBVIndicator("OBV", obv);

        } catch (Exception e) {
            log.warn("Error updating indicators", e);
        }
    }

    /**
     * Update moving average indicator with signal color
     */
    private void updateMovingAverageIndicator(@NotNull String indicatorName,
            @NotNull SimpleMovingAverageIndicator indicator,
            double currentPrice) {
        Map<String, double[]> values = indicator.getValues();
        if (values.isEmpty() || values.get("SMA") == null) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double[] smaValues = values.get("SMA");
        if (smaValues.length == 0) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double lastSMA = smaValues[smaValues.length - 1];
        String signal = String.format("%,.2f", lastSMA);
        String color = currentPrice > lastSMA ? GREEN : RED;
        updateIndicator(indicatorName, signal, color);
    }

    /**
     * Update RSI indicator with signal color
     */
    private void updateRSIIndicator(@NotNull String indicatorName, @NotNull RSIIndicator indicator) {
        Map<String, double[]> values = indicator.getValues();
        if (values.isEmpty() || values.get("RSI") == null) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double[] rsiValues = values.get("RSI");
        if (rsiValues.length == 0) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double lastRSI = rsiValues[rsiValues.length - 1];
        String signal = String.format("%.1f", lastRSI);
        String color = TEXT_SECONDARY;
        if (lastRSI > 70) {
            color = RED; // Overbought
        } else if (lastRSI < 30) {
            color = GREEN; // Oversold
        }
        updateIndicator(indicatorName, signal, color);
    }

    /**
     * Update MACD indicator with signal color
     */
    private void updateMACDIndicator(@NotNull String indicatorName, @NotNull MACDIndicator indicator) {
        Map<String, double[]> values = indicator.getValues();
        if (values.isEmpty() || values.get("MACD") == null) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double[] macdValues = values.get("MACD");
        double[] signalValues = values.getOrDefault("Signal", new double[0]);

        if (macdValues.length == 0) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double lastMACD = macdValues[macdValues.length - 1];
        String signal = String.format("%.4f", lastMACD);
        String color = lastMACD > 0 ? GREEN : RED;
        updateIndicator(indicatorName, signal, color);
    }

    /**
     * Update Stochastic indicator with signal color
     */
    private void updateStochasticIndicator(@NotNull String indicatorName, @NotNull StochasticIndicator indicator) {
        Map<String, double[]> values = indicator.getValues();
        if (values.isEmpty() || values.get("K") == null) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double[] kValues = values.get("K");
        if (kValues.length == 0) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double lastK = kValues[kValues.length - 1];
        String signal = String.format("%.1f", lastK);
        String color = TEXT_SECONDARY;
        if (lastK > 80) {
            color = RED; // Overbought
        } else if (lastK < 20) {
            color = GREEN; // Oversold
        }
        updateIndicator(indicatorName, signal, color);
    }

    /**
     * Update Bollinger Bands indicator with signal
     */
    private void updateBollingerBandsIndicator(@NotNull String indicatorName,
            @NotNull BollingerBandsIndicator indicator,
            double currentPrice) {
        Map<String, double[]> values = indicator.getValues();
        if (values.isEmpty() || values.get("Middle") == null) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double[] upperBand = values.getOrDefault("Upper", new double[0]);
        double[] lowerBand = values.getOrDefault("Lower", new double[0]);

        if (upperBand.length == 0 || lowerBand.length == 0) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        String signal = "Mid";
        String color = YELLOW;
        if (currentPrice > upperBand[upperBand.length - 1]) {
            signal = "Upper";
            color = RED;
        } else if (currentPrice < lowerBand[lowerBand.length - 1]) {
            signal = "Lower";
            color = GREEN;
        }
        updateIndicator(indicatorName, signal, color);
    }

    /**
     * Update ATR indicator with signal
     */
    private void updateATRIndicator(@NotNull String indicatorName, @NotNull ATRIndicator indicator) {
        Map<String, double[]> values = indicator.getValues();
        if (values.isEmpty() || values.get("ATR") == null) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double[] atrValues = values.get("ATR");
        if (atrValues.length == 0) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double lastATR = atrValues[atrValues.length - 1];
        String signal = String.format("%,.2f", lastATR);
        updateIndicator(indicatorName, signal, TEXT_SECONDARY);
    }

    /**
     * Update Volatility indicator with signal
     */
    private void updateVolatilityIndicator(@NotNull String indicatorName, @NotNull VolatilityIndicator indicator) {
        Map<String, double[]> values = indicator.getValues();
        if (values.isEmpty() || values.get("Volatility") == null) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double[] volValues = values.get("Volatility");
        if (volValues.length == 0) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double lastVol = volValues[volValues.length - 1];
        String signal = String.format("%.2f%%", lastVol * 100);
        String color = lastVol > 0.02 ? YELLOW : TEXT_SECONDARY;
        updateIndicator(indicatorName, signal, color);
    }

    /**
     * Update Volume indicator with signal
     */
    private void updateVolumeIndicator(@NotNull String indicatorName, @NotNull VolumeIndicator indicator) {
        Map<String, double[]> values = indicator.getValues();
        if (values.isEmpty() || values.get("Volume") == null) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double[] volValues = values.get("Volume");
        if (volValues.length < 2) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double lastVolume = volValues[volValues.length - 1];
        double prevVolume = volValues[volValues.length - 2];
        String signal = formatLargeNumber(lastVolume);
        String color = lastVolume > prevVolume ? GREEN : RED;
        updateIndicator(indicatorName, signal, color);
    }

    /**
     * Update OBV indicator with signal
     */
    private void updateOBVIndicator(@NotNull String indicatorName, @NotNull OBVIndicator indicator) {
        Map<String, double[]> values = indicator.getValues();
        if (values.isEmpty() || values.get("OBV") == null) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double[] obvValues = values.get("OBV");
        if (obvValues.length < 2) {
            updateIndicator(indicatorName, "N/A", TEXT_SECONDARY);
            return;
        }

        double lastOBV = obvValues[obvValues.length - 1];
        double prevOBV = obvValues[obvValues.length - 2];
        String signal = formatLargeNumber(lastOBV);
        String color = lastOBV > prevOBV ? GREEN : RED;
        updateIndicator(indicatorName, signal, color);
    }

    /**
     * Format large numbers to human-readable format (K, M, B)
     */
    private @NotNull String formatLargeNumber(double value) {
        if (value >= 1_000_000_000) {
            return String.format("%.1fB", value / 1_000_000_000);
        } else if (value >= 1_000_000) {
            return String.format("%.1fM", value / 1_000_000);
        } else if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000);
        } else {
            return String.format("%.0f", value);
        }
    }

    /**
     * Clear all indicator values
     */
    public void clearIndicators() {
        indicatorBoxes.values().forEach(box -> box.updateValue("—", TEXT_SECONDARY));
    }

    /**
     * Shutdown the panel and release resources
     */
    public void shutdown() {
        if (updateFuture != null && !updateFuture.isDone()) {
            updateFuture.cancel(false);
        }
    }

    /**
     * Individual indicator display box
     */
    @Getter
    @Slf4j
    public static class IndicatorValueBox extends VBox {
        private final Label nameLabel;
        private final Label valueLabel;

        public IndicatorValueBox(@NotNull String name, @NotNull String initialValue, String color, String tooltip) {
            setPadding(new Insets(8));
            setStyle("-fx-background-color: " + SECONDARY_BG + "; "
                    + "-fx-border-color: #263246; "
                    + "-fx-border-radius: 4; "
                    + "-fx-background-radius: 4;");
            setSpacing(4);

            nameLabel = new Label(name);
            nameLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_SECONDARY + ";");
            if (tooltip != null && !tooltip.isEmpty()) {
                javafx.scene.control.Tooltip tt = new javafx.scene.control.Tooltip(tooltip);
                javafx.scene.control.Tooltip.install(nameLabel, tt);
            }

            valueLabel = new Label(initialValue);
            valueLabel.setStyle("-fx-font-size: 13px; -fx-font-family: 'Courier New'; "
                    + "-fx-font-weight: bold; -fx-text-fill: " + color + ";");

            getChildren().addAll(nameLabel, valueLabel);
        }

        public void updateValue(@NotNull String value, String color) {
            valueLabel.setText(value);
            valueLabel.setStyle("-fx-font-size: 13px; -fx-font-family: 'Courier New'; "
                    + "-fx-font-weight: bold; -fx-text-fill: " + color + ";");
        }
    }

}
