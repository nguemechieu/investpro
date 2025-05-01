package org.investpro.investpro.chart;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class ChartSettingsPanel extends VBox {

    public ChartSettingsPanel(
            Consumer<Double> onCandleWidthChange,
            Consumer<Integer> onMaxCandlesChange,
            Runnable onToggleTheme,
            Runnable onToggleIndicators,
            Runnable onResetAll
    ) {
        this.setSpacing(10);
        this.setPadding(new Insets(10));

        // Candle Width Slider
        Label candleWidthLabel = new Label("Candle Width:");
        Slider candleWidthSlider = new Slider(1, 20, 5);
        candleWidthSlider.setShowTickLabels(true);
        candleWidthSlider.setShowTickMarks(true);
        candleWidthSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                onCandleWidthChange.accept(newVal.doubleValue())
        );

        // Max Candles Field
        Label maxCandlesLabel = new Label("Max Candles:");
        TextField maxCandlesField = new TextField("500");
        maxCandlesField.setOnAction(e -> {
            try {
                int max = Integer.parseInt(maxCandlesField.getText());
                onMaxCandlesChange.accept(max);
            } catch (NumberFormatException ex) {
                maxCandlesField.setText("500");
            }
        });

        // Theme Toggle Button
        Button themeButton = new Button("Toggle Theme");
        themeButton.setOnAction(e -> onToggleTheme.run());

        // Show/Hide Indicators Button
        Button indicatorsButton = new Button("Toggle Indicators");
        indicatorsButton.setOnAction(e -> onToggleIndicators.run());

        // Reset All Button
        Button resetButton = new Button("Reset All Settings");
        resetButton.setOnAction(e -> onResetAll.run());

        getChildren().addAll(
                candleWidthLabel, candleWidthSlider,
                maxCandlesLabel, maxCandlesField,
                themeButton,
                indicatorsButton,
                resetButton
        );
    }
}
