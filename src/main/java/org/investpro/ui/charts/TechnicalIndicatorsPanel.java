package org.investpro.ui.charts;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public TechnicalIndicatorsPanel() {
        initializeUI();
    }

    private void initializeUI() {
        setPrefWidth(280);
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
        addIndicator("MA(20)", "—", TEXT_SECONDARY, "Moving Average 20");
        addIndicator("MA(50)", "—", TEXT_SECONDARY, "Moving Average 50");
        addIndicator("MA(200)", "—", TEXT_SECONDARY, "Moving Average 200");

        addSeparator();

        // Momentum Indicators
        addIndicator("RSI(14)", "—", TEXT_SECONDARY, "Relative Strength Index");
        addIndicator("MACD", "—", TEXT_SECONDARY, "Moving Average Convergence Divergence");
        addIndicator("Stoch", "—", TEXT_SECONDARY, "Stochastic Oscillator");

        addSeparator();

        // Volatility Indicators
        addIndicator("BBands", "—", TEXT_SECONDARY, "Bollinger Bands");
        addIndicator("ATR(14)", "—", TEXT_SECONDARY, "Average True Range");
        addIndicator("Volatility", "—", TEXT_SECONDARY, "Price Volatility");

        addSeparator();

        // Volume Indicators
        addIndicator("Volume", "—", TEXT_SECONDARY, "Trading Volume");
        addIndicator("OBV", "—", TEXT_SECONDARY, "On-Balance Volume");
    }

    private void addIndicator(@NotNull String name, @NotNull String value, String color, String tooltip) {
        IndicatorValueBox box = new IndicatorValueBox(name, value, color, tooltip);
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
     * Clear all indicator values
     */
    public void clearIndicators() {
        indicatorBoxes.values().forEach(box -> box.updateValue("—", TEXT_SECONDARY));
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

    /**
     * Signal strength helper
     */
    public static class SignalStrength {
        public static String getSignalColor(double value, double upperThreshold, double lowerThreshold) {
            if (value > upperThreshold)
                return GREEN;
            if (value < lowerThreshold)
                return RED;
            return YELLOW;
        }

        public static String getRSISignal(double rsi) {
            if (rsi > 70)
                return RED; // Overbought
            if (rsi < 30)
                return GREEN; // Oversold
            return BLUE; // Neutral
        }

        public static String getMACDSignal(boolean bullish) {
            return bullish ? GREEN : RED;
        }

        public static String getTrendSignal(double price, double ma20, double ma50, double ma200) {
            if (price > ma20 && ma20 > ma50 && ma50 > ma200)
                return GREEN; // Strong uptrend
            if (price < ma20 && ma20 < ma50 && ma50 < ma200)
                return RED; // Strong downtrend
            return YELLOW; // Mixed
        }
    }
}
