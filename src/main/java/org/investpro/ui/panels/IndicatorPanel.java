package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.investpro.indicators.IndicatorType;
import org.investpro.indicators.IndicatorManager;
import org.investpro.indicators.IndicatorSettings;

import java.util.function.Consumer;

/**
 * UI panel for selecting and configuring technical indicators
 */
public class IndicatorPanel extends VBox {
    private final IndicatorManager indicatorManager;
    private final ComboBox<IndicatorType> indicatorSelector;
    private final Spinner<Integer> periodSpinner;
    private final ColorPicker colorPicker;
    private final CheckBox visibleCheckbox;
    private final Spinner<Double> strokeWidthSpinner;
    private final Spinner<Double> stdDevSpinner;
    private Consumer<IndicatorType> onIndicatorChanged;
    
    public IndicatorPanel(IndicatorManager indicatorManager) {
        this.indicatorManager = indicatorManager;
        
        // Create components
        this.indicatorSelector = new ComboBox<>();
        this.periodSpinner = new Spinner<>(1, 500, 20);
        this.colorPicker = new ColorPicker(Color.ORANGE);
        this.visibleCheckbox = new CheckBox("Visible");
        this.strokeWidthSpinner = new Spinner<>(0.5, 5.0, 2.0, 0.5);
        this.stdDevSpinner = new Spinner<>(0.5, 5.0, 2.0, 0.5);
        
        setupUI();
        setupListeners();
    }
    
    private void setupUI() {
        setStyle("-fx-border-color: #263246; " +
                "-fx-border-width: 1; " +
                "-fx-background-color: #101827; " +
                "-fx-padding: 10;");
        setSpacing(10);
        setPadding(new Insets(10));
        
        // Title
        Label titleLabel = new Label("Technical Indicators");
        titleLabel.setStyle("-fx-text-fill: #e5edf7; -fx-font-size: 14px; -fx-font-weight: bold;");
        
        // Indicator selector
        HBox selectorBox = createLabeledControl("Indicator:", indicatorSelector);
        indicatorSelector.getItems().addAll(IndicatorType.values());
        indicatorSelector.setValue(IndicatorType.SMA);
        indicatorSelector.setPrefWidth(150);
        indicatorSelector.setStyle("-fx-background-color: #0f1724; " +
                "-fx-text-fill: #e5edf7; " +
                "-fx-border-color: #2a3548;");
        
        // Period control
        HBox periodBox = createLabeledControl("Period:", periodSpinner);
        periodSpinner.setPrefWidth(100);
        periodSpinner.setStyle("-fx-background-color: #0f1724; -fx-text-fill: #e5edf7;");
        
        // Stroke width control
        HBox strokeBox = createLabeledControl("Stroke Width:", strokeWidthSpinner);
        strokeWidthSpinner.setPrefWidth(100);
        strokeWidthSpinner.setStyle("-fx-background-color: #0f1724; -fx-text-fill: #e5edf7;");
        
        // Color picker
        HBox colorBox = createLabeledControl("Color:", colorPicker);
        colorPicker.setPrefWidth(100);
        
        // Visible checkbox
        visibleCheckbox.setSelected(true);
        visibleCheckbox.setStyle("-fx-text-fill: #e5edf7; -fx-padding: 5;");
        HBox visibleBox = new HBox(10);
        visibleBox.getChildren().addAll(new Label(" "), visibleCheckbox);
        
        // Standard deviation control (for Bollinger Bands)
        HBox stdDevBox = createLabeledControl("Std Dev:", stdDevSpinner);
        stdDevSpinner.setPrefWidth(100);
        stdDevSpinner.setStyle("-fx-background-color: #0f1724; -fx-text-fill: #e5edf7;");
        stdDevBox.setVisible(false);
        stdDevBox.setManaged(false);
        
        // Button to apply indicator
        Button applyButton = new Button("Apply Indicator");
        applyButton.setStyle("-fx-background-color: #00D9FF; " +
                "-fx-text-fill: #000; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 16;");
        applyButton.setPrefWidth(150);
        applyButton.setOnAction(e -> applyIndicator());
        
        // Clear button
        Button clearButton = new Button("Clear");
        clearButton.setStyle("-fx-background-color: #FF6B6B; " +
                "-fx-text-fill: #fff; " +
                "-fx-padding: 8 16;");
        clearButton.setPrefWidth(150);
        clearButton.setOnAction(e -> clearIndicator());
        
        HBox buttonBox = new HBox(10);
        buttonBox.getChildren().addAll(applyButton, clearButton);
        
        // Store reference to stdDevBox for showing/hiding
        indicatorSelector.setOnAction(e -> {
            IndicatorType selectedType = indicatorSelector.getValue();
            stdDevBox.setVisible(selectedType == IndicatorType.BOLLINGER);
            stdDevBox.setManaged(selectedType == IndicatorType.BOLLINGER);
        });
        
        // Add all components
        getChildren().addAll(
            titleLabel,
            new Separator(),
            selectorBox,
            periodBox,
            strokeBox,
            colorBox,
            visibleBox,
            stdDevBox,
            new Separator(),
            buttonBox
        );
    }
    
    private HBox createLabeledControl(String label, Control control) {
        HBox box = new HBox(10);
        box.setStyle("-fx-alignment: CENTER_LEFT;");
        Label labelControl = new Label(label);
        labelControl.setStyle("-fx-text-fill: #9aa7ba; -fx-min-width: 80;");
        box.getChildren().addAll(labelControl, control);
        HBox.setHgrow(control, Priority.ALWAYS);
        return box;
    }
    
    private void setupListeners() {
        // Update settings when controls change
        periodSpinner.valueProperty().addListener((obs, old, newVal) -> {
            IndicatorType type = indicatorSelector.getValue();
            if (type != null) {
                IndicatorSettings settings = indicatorManager.getSettings(type);
                if (settings != null) {
                    settings.setPeriod(newVal);
                }
            }
        });
        
        colorPicker.valueProperty().addListener((obs, old, newVal) -> {
            IndicatorType type = indicatorSelector.getValue();
            if (type != null) {
                IndicatorSettings settings = indicatorManager.getSettings(type);
                if (settings != null) {
                    settings.setColor(newVal);
                }
            }
        });
        
        strokeWidthSpinner.valueProperty().addListener((obs, old, newVal) -> {
            IndicatorType type = indicatorSelector.getValue();
            if (type != null) {
                IndicatorSettings settings = indicatorManager.getSettings(type);
                if (settings != null) {
                    settings.setStrokeWidth(newVal);
                }
            }
        });
        
        visibleCheckbox.selectedProperty().addListener((obs, old, newVal) -> {
            IndicatorType type = indicatorSelector.getValue();
            if (type != null) {
                IndicatorSettings settings = indicatorManager.getSettings(type);
                if (settings != null) {
                    settings.setVisible(newVal);
                }
            }
        });
        
        stdDevSpinner.valueProperty().addListener((obs, old, newVal) -> {
            IndicatorType type = indicatorSelector.getValue();
            if (type == IndicatorType.BOLLINGER) {
                IndicatorSettings settings = indicatorManager.getSettings(type);
                if (settings != null) {
                    settings.setStdDeviation(newVal);
                }
            }
        });
        
        // Update UI when indicator selection changes
        indicatorSelector.setOnAction(e -> updateUIForSelectedIndicator());
    }
    
    private void updateUIForSelectedIndicator() {
        IndicatorType type = indicatorSelector.getValue();
        if (type == null || type == IndicatorType.NONE) {
            periodSpinner.setDisable(true);
            colorPicker.setDisable(true);
            strokeWidthSpinner.setDisable(true);
            return;
        }
        
        IndicatorSettings settings = indicatorManager.getSettings(type);
        if (settings != null) {
            periodSpinner.getValueFactory().setValue(settings.getPeriod());
            colorPicker.setValue(settings.getColor());
            strokeWidthSpinner.getValueFactory().setValue(settings.getStrokeWidth());
            visibleCheckbox.setSelected(settings.isVisible());
            stdDevSpinner.getValueFactory().setValue(settings.getStdDeviation());
        }
        
        periodSpinner.setDisable(false);
        colorPicker.setDisable(false);
        strokeWidthSpinner.setDisable(false);
    }
    
    private void applyIndicator() {
        IndicatorType type = indicatorSelector.getValue();
        if (type != null && type != IndicatorType.NONE) {
            indicatorManager.setActiveIndicator(type);
            if (onIndicatorChanged != null) {
                onIndicatorChanged.accept(type);
            }
        }
    }
    
    private void clearIndicator() {
        indicatorManager.setActiveIndicator(IndicatorType.NONE);
        indicatorSelector.setValue(IndicatorType.NONE);
        if (onIndicatorChanged != null) {
            onIndicatorChanged.accept(IndicatorType.NONE);
        }
    }
    
    public void setOnIndicatorChanged(Consumer<IndicatorType> callback) {
        this.onIndicatorChanged = callback;
    }
}
