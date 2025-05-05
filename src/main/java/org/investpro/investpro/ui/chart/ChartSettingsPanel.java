package org.investpro.investpro.ui.chart;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.ui.chart.overlay.ChartOverlay;
import org.investpro.investpro.ui.chart.overlay.RSIOverlay;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class ChartSettingsPanel extends VBox {

    private final Slider candleWidthSlider;
    private final Button resetButton;
    private final Button themeToggleButton;
    private final ComboBox<Integer> rsiPeriodComboBox;
    private final Map<String, CheckBox> indicatorToggles = new HashMap<>();
    private final MaxCandlesSetter maxCandles_to_display;
    CheckBox gridToggle = new CheckBox("Show Grid");
    ChoiceBox<String> showRSICheckBox = new ChoiceBox<>();
    private ChartOverlay activeOverlays;

    public ChartSettingsPanel(CandleStickChart chart,
                              CandleWidthChanger candleWidthChanger,
                              MaxCandlesSetter maxCandlesSetter,
                              Runnable themeToggler,
                              Runnable settingsResetter,
                              ChartOverlayManager overlayManager
    ) {
        setSpacing(10);
        this.maxCandles_to_display = maxCandlesSetter;
        setPadding(new Insets(10));
        Label rsiLabel = new Label("RSI Period");
        rsiPeriodComboBox = new ComboBox<>(FXCollections.observableArrayList(9, 14, 21));
        rsiPeriodComboBox.setValue(14);
        gridToggle.setSelected(true); // default on

        showRSICheckBox.setOnAction(e -> {
            int period = rsiPeriodComboBox.getValue();
            String rsiName = "RSI-" + period;
            if (!showRSICheckBox.getSelectionModel().getSelectedItem().isEmpty()) {
                overlayManager.removeOverlayByPrefix("RSI-"); // optional: avoid duplicate RSI
                overlayManager.addOverlay(new RSIOverlay(period));
            } else {
                overlayManager.removeOverlayByName(rsiName);
            }
        });

        // Candle Width Slider
        Label candleWidthLabel = new Label("Candle Width");
        candleWidthSlider = new Slider(2, 30, 10);
        configureSlider(candleWidthSlider, candleWidthChanger);

        // Indicator toggles
        addIndicatorToggle("RSI-14", new RSIOverlay(14), overlayManager);

        // Theme toggle button
        themeToggleButton = new Button("Toggle Theme");
        themeToggleButton.setOnAction(e -> themeToggler.run());

        // Reset Button
        resetButton = new Button("Reset Chart Settings");
        resetButton.setOnAction(e -> {
            settingsResetter.run();
            indicatorToggles.values().forEach(cb -> cb.setSelected(false));
        });

        getChildren().addAll(
                candleWidthLabel,
                candleWidthSlider,
                rsiLabel,
                rsiPeriodComboBox,
                showRSICheckBox,
                themeToggleButton,
                resetButton
        );
        gridToggle.setOnAction(e -> chart.setShowGrid(gridToggle.isSelected()));

    }


    private void configureSlider(Slider slider, CandleWidthChanger changer) {
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(7);
        slider.setMinorTickCount(1);
        slider.setSnapToTicks(true);
        slider.setLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return String.format("%.0f", value);
            }

            @Override
            public Double fromString(String s) {
                return Double.valueOf(s);
            }
        });
        slider.valueProperty().addListener((obs, oldVal, newVal) ->
                changer.changeCandleWidth(newVal.doubleValue()));
    }

    private void addIndicatorToggle(String name, ChartOverlay overlay, ChartOverlayManager manager) {
        CheckBox toggle = new CheckBox("Show " + name);
        toggle.setOnAction(e -> {
            if (toggle.isSelected()) {
                manager.addOverlay(overlay);
            } else {
                manager.removeOverlayByName(name);
            }
        });
        indicatorToggles.put(name, toggle);
    }

    @FunctionalInterface
    public interface CandleWidthChanger {
        void changeCandleWidth(double width);
    }

    @FunctionalInterface
    public interface MaxCandlesSetter {
        void setMaxCandles(int max);
    }
}
