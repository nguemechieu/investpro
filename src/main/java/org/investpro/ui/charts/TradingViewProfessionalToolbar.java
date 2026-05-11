package org.investpro.ui.charts;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

/**
 * Professional TradingView-style toolbar for chart controls.
 * Provides tools for drawing, studies, timeframe selection, and view options.
 * <p>
 * Features:
 * - Technical study selection (Moving Averages, RSI, MACD, etc.)
 * - Drawing tools (Trend lines, Support/Resistance levels, Fibonacci, etc.)
 * - Timeframe quick selector
 * - Chart type toggle (Candle, Bar, Line)
 * - Zoom and pan controls
 * - Professional dark theme
 *
 * @author NOEL NGUEMECHIEU
 */
@Getter
@Setter
@Slf4j
public class TradingViewProfessionalToolbar extends VBox {
    private static final String BG_PRIMARY = "#101827";
    private static final String BG_SECONDARY = "#0f1724";
    private static final String TEXT_PRIMARY = "#e5edf7";
    private static final String TEXT_SECONDARY = "#9aa7ba";
    private static final String ACCENT_COLOR = "#3b82f6";
    private static final String SEPARATOR_COLOR = "#263246";

    private final HBox toolbarTop;
    private final HBox toolbarBottom;

    private final ComboBox<String> studiesDropdown;
    private final ComboBox<String> drawingToolsDropdown;
    private final ComboBox<String> timeframeSelector;

    private final ToggleGroup chartTypeGroup;
    private final ToggleButton candleTypeBtn;
    private final ToggleButton barTypeBtn;
    private final ToggleButton lineTypeBtn;

    private final Button trendlineBtn;
    private final Button forkBtn;
    private final Button pitchforkBtn;
    private final Button regressionBtn;

    private final Button resetZoomBtn;
    private final Button zoomInBtn;
    private final Button zoomOutBtn;

    private Consumer<String> onStudySelected;
    private Consumer<String> onDrawingToolSelected;
    private Consumer<String> onTimeframeChanged;
    private Consumer<String> onChartTypeChanged;
    private Runnable onZoomReset;
    private Runnable onZoomIn;
    private Runnable onZoomOut;
private   CandleStickChart candleStickChart;
    public TradingViewProfessionalToolbar(@NotNull CandleStickChart candleStickChart) {
        studiesDropdown = new ComboBox<>();
        drawingToolsDropdown = new ComboBox<>();
        timeframeSelector = new ComboBox<>();
        chartTypeGroup = new ToggleGroup();
        this.candleStickChart=candleStickChart;

        candleTypeBtn = new ToggleButton("📊 Candle");
        barTypeBtn = new ToggleButton("📈 Bar");
        lineTypeBtn = new ToggleButton("📉 Line");

        trendlineBtn = new Button("↗ Trendline");
        forkBtn = new Button("🔱 Schiff Fork");
        pitchforkBtn = new Button("⚡ Pitchfork");
        regressionBtn = new Button("📐 Regression");

        resetZoomBtn = new Button("⟲ Reset");
        zoomInBtn = new Button("🔍+");
        zoomOutBtn = new Button("🔍−");

        toolbarTop = new HBox(10);
        toolbarBottom = new HBox(10);

        initializeUI();
        setupEventHandlers();
    }

    private void initializeUI() {
        setPrefHeight(80);
        setStyle("-fx-background-color: " + BG_PRIMARY + "; "
                + "-fx-border-color: " + SEPARATOR_COLOR + "; "
                + "-fx-border-width: 0 0 1 0;");
        setPadding(new Insets(8, 10, 8, 10));
        setSpacing(8);

        // Top toolbar: Studies and Drawing tools
        toolbarTop.setAlignment(Pos.CENTER_LEFT);
        toolbarTop.setStyle("-fx-background-color: transparent;");
        toolbarTop.setSpacing(10);
        toolbarTop.setPrefHeight(30);

        // Studies section
        Label studiesLabel = createLabel("Studies:");
        initializeStudiesDropdown();
        toolbarTop.getChildren().addAll(studiesLabel, studiesDropdown);

        // Separator
        Separator sep1 = new Separator();
        sep1.setStyle("-fx-padding: 0; -fx-border-color: " + SEPARATOR_COLOR + ";");
        toolbarTop.getChildren().add(sep1);

        // Drawing tools section
        Label drawingLabel = createLabel("Drawing:");
        initializeDrawingToolsDropdown();
        toolbarTop.getChildren().addAll(drawingLabel, drawingToolsDropdown);

        // Drawing buttons
        styleButton(trendlineBtn);
        styleButton(forkBtn);
        styleButton(pitchforkBtn);
        styleButton(regressionBtn);
        toolbarTop.getChildren().addAll(trendlineBtn, forkBtn, pitchforkBtn, regressionBtn);

        // Spacer
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        toolbarTop.getChildren().add(spacer1);

        // Timeframe selector on right
        Label tfLabel = createLabel("Timeframe:");
        initializeTimeframeSelector();
        timeframeSelector.setPrefWidth(100);
        toolbarTop.getChildren().addAll(tfLabel, timeframeSelector);

        getChildren().add(toolbarTop);

        // Bottom toolbar: Chart type and zoom controls
        toolbarBottom.setAlignment(Pos.CENTER_LEFT);
        toolbarBottom.setStyle("-fx-background-color: transparent;");
        toolbarBottom.setSpacing(8);
        toolbarBottom.setPrefHeight(30);

        // Chart type buttons
        Label typeLabel = createLabel("Chart Type:");
        candleTypeBtn.setToggleGroup(chartTypeGroup);
        barTypeBtn.setToggleGroup(chartTypeGroup);
        lineTypeBtn.setToggleGroup(chartTypeGroup);
        candleTypeBtn.setSelected(true);

        for (ToggleButton btn : new ToggleButton[] { candleTypeBtn, barTypeBtn, lineTypeBtn }) {
            styleButton(btn, 80, 28);
        }

        toolbarBottom.getChildren().addAll(typeLabel, candleTypeBtn, barTypeBtn, lineTypeBtn);

        // Separator
        Separator sep2 = new Separator();
        sep2.setStyle("-fx-padding: 0; -fx-border-color: " + SEPARATOR_COLOR + ";");
        toolbarBottom.getChildren().add(sep2);

        // Zoom controls
        Label zoomLabel = createLabel("Zoom:");
        styleButton(zoomInBtn, 50, 28);
        styleButton(resetZoomBtn, 60, 28);
        styleButton(zoomOutBtn, 50, 28);
        toolbarBottom.getChildren().addAll(zoomLabel, zoomInBtn, resetZoomBtn, zoomOutBtn);

        // Spacer
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        toolbarBottom.getChildren().add(spacer2);

        getChildren().add(toolbarBottom);
        setStudiesEnabled(true);
    }

    private void initializeStudiesDropdown() {
        String[] studies = {
                "None",
                "Moving Average (SMA)",
                "Exponential MA (EMA)",
                "Weighted MA (WMA)",
                "RSI (14)",
                "MACD",
                "Stochastic",
                "Bollinger Bands",
                "ATR (14)",
                "Volume Profile",
                "Ichimoku Cloud",
                "Alligator",
                "CCI (20)",
                "Momentum"
        };

        studiesDropdown.getItems().addAll(studies);
        studiesDropdown.setValue("None");
        styleDropdown(studiesDropdown, 200);

        studiesDropdown.setOnAction(e -> {
            if (onStudySelected != null) {
                onStudySelected.accept(studiesDropdown.getValue());
            }
        });
    }

    private void initializeDrawingToolsDropdown() {
        String[] tools = {
                "None",
                "Trend Line",
                "Horizontal Line",
                "Vertical Line",
                "Rectangle",
                "Circle",
                "Text Label",
                "Fibonacci Retracement",
                "Fibonacci Extension",
                "Channel",
                "Gann Box"
        };

        drawingToolsDropdown.getItems().addAll(tools);
        drawingToolsDropdown.setValue("None");
        styleDropdown(drawingToolsDropdown, 200);

        drawingToolsDropdown.setOnAction(e -> {
            if (onDrawingToolSelected != null) {
                onDrawingToolSelected.accept(drawingToolsDropdown.getValue());
            }
        });
    }

    private void initializeTimeframeSelector() {
  List<String> timeframes = (List<String>) candleStickChart.getExchange().getSupportedTimeframes().stream().
          map(Timeframe::getCode);
        timeframeSelector.getItems().addAll(timeframes);
        timeframeSelector.setValue("1h");
        styleDropdown(timeframeSelector, 100);

        timeframeSelector.setOnAction(e -> {
            if (onTimeframeChanged != null) {
                onTimeframeChanged.accept(timeframeSelector.getValue());
            }
        });
    }

    private void setupEventHandlers() {
        candleTypeBtn.setOnAction(e -> {
            if (onChartTypeChanged != null)
                onChartTypeChanged.accept("CANDLE");
        });
        barTypeBtn.setOnAction(e -> {
            if (onChartTypeChanged != null)
                onChartTypeChanged.accept("BAR");
        });
        lineTypeBtn.setOnAction(e -> {
            if (onChartTypeChanged != null)
                onChartTypeChanged.accept("LINE");
        });

        trendlineBtn.setOnAction(e -> {
            if (onDrawingToolSelected != null)
                onDrawingToolSelected.accept("TRENDLINE");
        });
        forkBtn.setOnAction(e -> {
            if (onDrawingToolSelected != null)
                onDrawingToolSelected.accept("SCHIFF_FORK");
        });
        pitchforkBtn.setOnAction(e -> {
            if (onDrawingToolSelected != null)
                onDrawingToolSelected.accept("PITCHFORK");
        });
        regressionBtn.setOnAction(e -> {
            if (onDrawingToolSelected != null)
                onDrawingToolSelected.accept("REGRESSION");
        });

        zoomInBtn.setOnAction(e -> {
            if (onZoomIn != null)
                onZoomIn.run();
        });
        resetZoomBtn.setOnAction(e -> {
            if (onZoomReset != null)
                onZoomReset.run();
        });
        zoomOutBtn.setOnAction(e -> {
            if (onZoomOut != null)
                onZoomOut.run();
        });
    }

    private @NotNull Label createLabel(@NotNull String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px;");
        return label;
    }

    private void styleButton(@NotNull Button btn) {
        styleButton(btn, 90, 28);
    }

    private void styleButton(@NotNull Button btn, double width, double height) {
        btn.setPrefSize(width, height);
        btn.setStyle("-fx-padding: 5; "
                + "-fx-font-size: 11px; "
                + "-fx-text-fill: " + TEXT_PRIMARY + "; "
                + "-fx-background-color: " + BG_SECONDARY + "; "
                + "-fx-border-color: " + SEPARATOR_COLOR + "; "
                + "-fx-border-radius: 3; "
                + "-fx-background-radius: 3; "
                + "-fx-cursor: hand;");

        // Hover effect
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-padding: 5; "
                + "-fx-font-size: 11px; "
                + "-fx-text-fill: " + TEXT_PRIMARY + "; "
                + "-fx-background-color: #1a2535; "
                + "-fx-border-color: " + ACCENT_COLOR + "; "
                + "-fx-border-radius: 3; "
                + "-fx-background-radius: 3; "
                + "-fx-cursor: hand;"));

        btn.setOnMouseExited(e -> btn.setStyle("-fx-padding: 5; "
                + "-fx-font-size: 11px; "
                + "-fx-text-fill: " + TEXT_PRIMARY + "; "
                + "-fx-background-color: " + BG_SECONDARY + "; "
                + "-fx-border-color: " + SEPARATOR_COLOR + "; "
                + "-fx-border-radius: 3; "
                + "-fx-background-radius: 3; "
                + "-fx-cursor: hand;"));
    }

    private void styleButton(@NotNull ToggleButton btn, double width, double height) {
        btn.setPrefSize(width, height);
        btn.setStyle("-fx-padding: 5; "
                + "-fx-font-size: 11px; "
                + "-fx-text-fill: " + TEXT_PRIMARY + "; "
                + "-fx-background-color: " + BG_SECONDARY + "; "
                + "-fx-border-color: " + SEPARATOR_COLOR + "; "
                + "-fx-border-radius: 3; "
                + "-fx-background-radius: 3;");

        btn.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                btn.setStyle("-fx-padding: 5; "
                        + "-fx-font-size: 11px; "
                        + "-fx-text-fill: " + TEXT_PRIMARY + "; "
                        + "-fx-background-color: " + ACCENT_COLOR + "; "
                        + "-fx-border-color: " + ACCENT_COLOR + "; "
                        + "-fx-border-radius: 3; "
                        + "-fx-background-radius: 3;");
            } else {
                btn.setStyle("-fx-padding: 5; "
                        + "-fx-font-size: 11px; "
                        + "-fx-text-fill: " + TEXT_PRIMARY + "; "
                        + "-fx-background-color: " + BG_SECONDARY + "; "
                        + "-fx-border-color: " + SEPARATOR_COLOR + "; "
                        + "-fx-border-radius: 3; "
                        + "-fx-background-radius: 3;");
            }
        });
    }

    private void styleDropdown(@NotNull ComboBox<String> combo, double width) {
        combo.setPrefWidth(width);
        combo.setStyle("-fx-font-size: 11px; "
                + "-fx-text-fill: " + TEXT_PRIMARY + "; "
                + "-fx-background-color: " + BG_SECONDARY + "; "
                + "-fx-border-color: " + SEPARATOR_COLOR + "; "
                + "-fx-border-radius: 3; "
                + "-fx-background-radius: 3;");
    }

    // Callback setters
    public void setOnStudySelected(@NotNull Consumer<String> callback) {
        this.onStudySelected = callback;
    }

    public void setOnDrawingToolSelected(@NotNull Consumer<String> callback) {
        this.onDrawingToolSelected = callback;
    }

    public void setOnTimeframeChanged(@NotNull Consumer<String> callback) {
        this.onTimeframeChanged = callback;
    }

    public void setOnChartTypeChanged(@NotNull Consumer<String> callback) {
        this.onChartTypeChanged = callback;
    }

    public void setOnZoomReset(@NotNull Runnable callback) {
        this.onZoomReset = callback;
    }

    public void setOnZoomIn(@NotNull Runnable callback) {
        this.onZoomIn = callback;
    }

    public void setOnZoomOut(@NotNull Runnable callback) {
        this.onZoomOut = callback;
    }

    /**
     * Get selected study
     */
    public String getSelectedStudy() {
        return studiesDropdown.getValue();
    }

    /**
     * Get selected drawing tool
     */
    public String getSelectedDrawingTool() {
        return drawingToolsDropdown.getValue();
    }

    /**
     * Get selected timeframe
     */
    public String getSelectedTimeframe() {
        return timeframeSelector.getValue();
    }

    /**
     * Get selected chart type
     */
    public String getSelectedChartType() {
        if (candleTypeBtn.isSelected())
            return "CANDLE";
        if (barTypeBtn.isSelected())
            return "BAR";
        if (lineTypeBtn.isSelected())
            return "LINE";
        return "CANDLE";
    }

    /**
     * Programmatically set timeframe
     */
    public void setTimeframe(@NotNull String timeframe) {
        timeframeSelector.setValue(timeframe);
    }

    /**
     * Enable/disable specific controls
     */
    public void setStudiesEnabled(boolean enabled) {
        studiesDropdown.setDisable(!enabled);
    }

    public void setDrawingToolsEnabled(boolean enabled) {
        drawingToolsDropdown.setDisable(!enabled);
        trendlineBtn.setDisable(!enabled);
        forkBtn.setDisable(!enabled);
        pitchforkBtn.setDisable(!enabled);
        regressionBtn.setDisable(!enabled);
    }
}
