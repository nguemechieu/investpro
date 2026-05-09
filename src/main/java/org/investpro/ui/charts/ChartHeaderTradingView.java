package org.investpro.ui.charts;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * TradingView-like professional chart header displaying key metrics.
 * Shows symbol, current price, change, volume, and other OHLCV info.
 *
 * @author NOEL NGUEMECHIEU
 */
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

    public ChartHeaderTradingView() {
        initializeUI();
    }

    private void initializeUI() {
        setPrefHeight(70);
        setStyle("-fx-background-color: " + SECONDARY_BG + "; "
                + "-fx-border-color: #263246; "
                + "-fx-border-width: 0 0 1 0;");
        setPadding(new Insets(12, 16, 12, 16));
        setSpacing(16);

        // Top row: Symbol, Price, Change, Timeframe
        HBox topRow = createTopRow();
        getChildren().add(topRow);

        // Bottom row: OHLCV values
        HBox bottomRow = createBottomRow();
        getChildren().add(bottomRow);
    }

    private HBox createTopRow() {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);

        // Symbol
        symbolLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");
        symbolLabel.setText("BTC/USD");

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
        updateChange(price,changePercent);
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
}
