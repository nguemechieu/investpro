package org.investpro.ui.tools;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.util.Duration;
import lombok.Getter;
import org.controlsfx.control.PopOver;
import org.investpro.ui.charts.CandleStickChart;
import org.investpro.utils.DelayedSizeChangeListener;

import java.util.Objects;
import java.util.Set;

/**
 * Professional chart-only toolbar for InvestPro candlestick charts.
 *
 * <p>
 * This toolbar intentionally does not include desk-level trading controls:
 * BUY, SELL, New Order, Cancel All, Algo Trading, broker selector, symbol
 * selector,
 * or timeframe controls.
 * </p>
 *
 * <p>
 * It only includes chart tools a trader needs:
 * </p>
 * <ul>
 * <li>Cursor</li>
 * <li>Crosshair</li>
 * <li>Trendline</li>
 * <li>Horizontal line</li>
 * <li>Vertical line</li>
 * <li>Rectangle</li>
 * <li>Fibonacci retracement</li>
 * <li>Measure/ruler</li>
 * <li>Zoom in/out</li>
 * <li>Fit chart</li>
 * <li>Indicators</li>
 * <li>Screenshot</li>
 * <li>Print</li>
 * <li>Chart options</li>
 * </ul>
 */
@Getter
public class ChartToolbar extends Region {

    private static final int MIN_BUTTON_SIZE = 24;
    private static final int LARGE_WIDTH_BREAKPOINT = 900;
    private static final int MEDIUM_WIDTH_BREAKPOINT = 680;
    private static final int COMPACT_WIDTH_BREAKPOINT = 480;
    private static final Color TOOLBAR_ICON_COLOR = Color.web("#e5edf7");

    private final HBox toolbar;
    private final PopOver optionsPopOver;
    private final ChartOptions chartOptions;
    private ChartOptions activeChartOptions;

    private EventHandler<MouseEvent> mouseExitedPopOverFilter;
    private volatile boolean mouseInsideOptionsButton;

    private ToolbarButton activeDrawingToolButton;
    private Tool activeDrawingTool = Tool.CURSOR;

    /**
     * Compatibility constructor.
     *
     * <p>
     * The {@code granularities} argument is intentionally ignored because
     * timeframe controls belong to the parent chart header or trading desk.
     * </p>
     */
    public ChartToolbar(
            ObservableNumberValue containerWidth,
            ObservableNumberValue containerHeight,
            Set<Integer> granularity) {
        this(containerWidth, containerHeight);
        this.granularity=granularity;
    }
private Set<Integer> granularity;
    public ChartToolbar(
            ObservableNumberValue containerWidth,
            ObservableNumberValue containerHeight) {
        Objects.requireNonNull(containerWidth, "containerWidth must not be null");
        Objects.requireNonNull(containerHeight, "containerHeight must not be null");

        this.optionsPopOver = new PopOver();
        this.optionsPopOver.setTitle("Chart Options");
        this.optionsPopOver.setHeaderAlwaysVisible(true);
        this.optionsPopOver.setDetached(false);
        this.optionsPopOver.setCornerRadius(18);
        this.optionsPopOver.setArrowIndent(12);

        this.chartOptions = new ChartOptions();
        this.activeChartOptions = chartOptions;
        setChartOptions(chartOptions);

        this.toolbar = new HBox(6);
        this.toolbar.setAlignment(Pos.CENTER_LEFT);
        this.toolbar.setFillHeight(true);
        this.toolbar.getStyleClass().add("candle-chart-toolbar");

        getStyleClass().add("candle-chart-toolbar-wrapper");

        buildToolbar();
        getChildren().setAll(toolbar);

        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);
        SizeChangeListener sizeListener = new SizeChangeListener(gotFirstSize, containerWidth, containerHeight);

        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);

        ChangeListener<Boolean> firstSizeListener = new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (Boolean.TRUE.equals(newValue)) {
                    sizeListener.resize();
                    gotFirstSize.removeListener(this);
                }
            }
        };

        gotFirstSize.addListener(firstSizeListener);
    }

    private void buildToolbar() {
        toolbar.getChildren().setAll(
                new ToolbarButton(Tool.CURSOR),
                new ToolbarButton(Tool.CROSSHAIR),
                verticalSeparator(),

                new ToolbarButton(Tool.TRENDLINE),
                new ToolbarButton(Tool.HORIZONTAL_LINE),
                new ToolbarButton(Tool.VERTICAL_LINE),
                new ToolbarButton(Tool.RECTANGLE),
                new ToolbarButton(Tool.TRIANGLE),
                new ToolbarButton(Tool.CIRCLE),
                new ToolbarButton(Tool.FIBONACCI),
                new ToolbarButton(Tool.MEASURE),
                new ToolbarButton(Tool.RISK_REWARD),
                new ToolbarButton(Tool.ERASE_OBJECTS),
                verticalSeparator(),

                new ToolbarButton(Tool.ZOOM_IN),
                new ToolbarButton(Tool.ZOOM_OUT),
                new ToolbarButton(Tool.FIT_CHART),
                new ToolbarButton(Tool.CHART_TYPE),
                verticalSeparator(),

                new ToolbarButton(Tool.EVENTS),
                new ToolbarButton(Tool.INDICATORS),
                new ToolbarButton(Tool.SCREENSHOT),
                new ToolbarButton(Tool.PRINT),
                spacer(),

                createOptionsButton());
    }

    private ToolbarButton createOptionsButton() {
        ToolbarButton optionsButton = new ToolbarButton(Tool.OPTIONS);
        configureOptionsButton(optionsButton);
        return optionsButton;
    }

    private Separator verticalSeparator() {
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.getStyleClass().add("candle-chart-toolbar-separator");
        separator.setOpacity(0.45);
        separator.setPadding(new Insets(0, 3, 0, 3));
        return separator;
    }

    private Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void configureOptionsButton(ToolbarButton toolbarButton) {
        toolbarButton.setOnMouseEntered(event -> {
            mouseInsideOptionsButton = true;
            showOptionsPopOver(toolbarButton);
        });

        toolbarButton.setOnMouseExited(events -> {
            mouseInsideOptionsButton = false;

            Scene scene = getScene();
            if (scene == null || scene.getWindow() == null) {
                optionsPopOver.hide(Duration.seconds(0.20));
                return;
            }

            if (mouseExitedPopOverFilter == null) {
                final Scene capturedScene = scene;
                mouseExitedPopOverFilter = event -> {
                    if (capturedScene.getWindow() == null) {
                        optionsPopOver.hide(Duration.seconds(0.20));
                        mouseExitedPopOverFilter = null;
                        return;
                    }
                    boolean insidePopover = event.getScreenX() <= optionsPopOver.getX() + optionsPopOver.getWidth()
                            && event.getScreenX() >= optionsPopOver.getX()
                            && event.getScreenY() <= optionsPopOver.getY() + optionsPopOver.getHeight()
                            && event.getScreenY() >= optionsPopOver.getY();
                    if (!insidePopover && !mouseInsideOptionsButton) {
                        optionsPopOver.hide(Duration.seconds(0.20));
                        capturedScene.getWindow().removeEventFilter(MouseEvent.MOUSE_MOVED, mouseExitedPopOverFilter);
                        mouseExitedPopOverFilter = null;
                    }
                };
                scene.getWindow().addEventFilter(MouseEvent.MOUSE_MOVED, mouseExitedPopOverFilter);
            }
        });

        toolbarButton.setOnAction(event -> {
            if (optionsPopOver.isShowing()) {
                optionsPopOver.hide(Duration.ZERO);
            } else {
                showOptionsPopOver(toolbarButton);
            }
        });
    }

    private void showOptionsPopOver(ToolbarButton toolbarButton) {
        setChartOptions(activeChartOptions);
        if (!optionsPopOver.isShowing()) {
            optionsPopOver.show(toolbarButton);
        }
    }

    @Override
    protected void layoutChildren() {
        toolbar.resizeRelocate(0, 0, getWidth(), getHeight());
    }

    @Override
    protected double computePrefHeight(double width) {
        return 36;
    }

    @Override
    protected double computeMinHeight(double width) {
        return 30;
    }

    @Override
    protected double computePrefWidth(double height) {
        return toolbar.prefWidth(height);
    }

    /**
     * Kept for compatibility with older chart-container code.
     *
     * <p>
     * This toolbar no longer owns timeframe buttons, so this method is
     * intentionally empty.
     * </p>
     */
    public void setActiveToolbarButton(javafx.beans.property.IntegerProperty secondsPerCandle) {
        // Intentionally empty.
    }

    public void registerEventHandlers(
            CandleStickChart candleStickChart) {
        Objects.requireNonNull(candleStickChart, "candleStickChart must not be null");

        for (Node childNode : toolbar.getChildren()) {
            if (!(childNode instanceof ToolbarButton toolButton) || toolButton.tool == null) {
                continue;
            }

            switch (toolButton.tool) {
                case CURSOR -> toolButton.setOnAction(event -> {
                    activateDrawingTool(toolButton, Tool.CURSOR);
                    candleStickChart.activateCursorTool();
                });

                case CROSSHAIR -> toolButton.setOnAction(event -> {
                    activateDrawingTool(toolButton, Tool.CROSSHAIR);
                    candleStickChart.toggleCrosshair();
                });

                case TRENDLINE -> toolButton.setOnAction(event -> {
                    activateDrawingTool(toolButton, Tool.TRENDLINE);
                    candleStickChart.activateTrendlineTool();
                });

                case HORIZONTAL_LINE -> toolButton.setOnAction(event -> {
                    activateDrawingTool(toolButton, Tool.HORIZONTAL_LINE);
                    candleStickChart.activateHorizontalLineTool();
                });

                case VERTICAL_LINE -> toolButton.setOnAction(event -> {
                    activateDrawingTool(toolButton, Tool.VERTICAL_LINE);
                    candleStickChart.activateVerticalLineTool();
                });

                case RECTANGLE -> toolButton.setOnAction(event -> {
                    activateDrawingTool(toolButton, Tool.RECTANGLE);
                    candleStickChart.activateRectangleTool();
                });

                case TRIANGLE -> toolButton.setOnAction(event -> {
                    activateDrawingTool(toolButton, Tool.TRIANGLE);
                    candleStickChart.activateTriangleTool();
                });

                case CIRCLE -> toolButton.setOnAction(event -> {
                    activateDrawingTool(toolButton, Tool.CIRCLE);
                    candleStickChart.activateCircleTool();
                });

                case FIBONACCI -> toolButton.setOnAction(event -> {
                    activateDrawingTool(toolButton, Tool.FIBONACCI);
                    candleStickChart.activateFibonacciTool();
                });

                case MEASURE -> toolButton.setOnAction(event -> {
                    activateDrawingTool(toolButton, Tool.MEASURE);
                    candleStickChart.activateMeasureTool();
                });

                case RISK_REWARD -> toolButton.setOnAction(event -> {
                    activateDrawingTool(toolButton, Tool.RISK_REWARD);
                    candleStickChart.activateRiskRewardTool();
                });

                case ERASE_OBJECTS -> toolButton.setOnAction(event -> {
                    activateDrawingTool(toolButton, Tool.ERASE_OBJECTS);
                    candleStickChart.activateEraseObjectsTool();
                });

                case ZOOM_IN -> toolButton.setOnAction(event -> candleStickChart.zoomIn());

                case ZOOM_OUT -> toolButton.setOnAction(event -> candleStickChart.zoomOut());

                case FIT_CHART -> toolButton.setOnAction(event -> candleStickChart.fitChart());

                case CHART_TYPE -> toolButton.setOnAction(event -> candleStickChart.cycleChartType());

                case EVENTS -> toolButton.setOnAction(event -> candleStickChart.toggleChartEvents());

                case INDICATORS -> toolButton.setOnAction(event -> candleStickChart.openIndicatorDialog());

                case SCREENSHOT -> toolButton.setOnAction(event -> candleStickChart.screenshot());

                case PRINT -> toolButton.setOnAction(event -> candleStickChart.print());

                case OPTIONS -> configureOptionsButton(toolButton);
            }
        }

        activateDefaultTool();
    }

    public void setChartOptions(ChartOptions chartOptions) {
        Objects.requireNonNull(chartOptions, "chartOptions must not be null");
        this.activeChartOptions = chartOptions;
        optionsPopOver.setContentNode(chartOptions.getOptionsPane());
        optionsPopOver.setDetached(false);
        optionsPopOver.setCornerRadius(18);
        optionsPopOver.setArrowIndent(12);
        optionsPopOver.setHeaderAlwaysVisible(true);
    }

    private void activateDefaultTool() {
        for (Node childNode : toolbar.getChildren()) {
            if (childNode instanceof ToolbarButton button && button.tool == Tool.CURSOR) {
                activateDrawingTool(button, Tool.CURSOR);
                return;
            }
        }
    }

    private void activateDrawingTool(ToolbarButton button, Tool tool) {
        if (button == null || tool == null || !tool.isDrawingTool()) {
            return;
        }

        if (activeDrawingToolButton != null && activeDrawingToolButton != button) {
            activeDrawingToolButton.setActive(false);
        }

        activeDrawingToolButton = button;
        activeDrawingTool = tool;
        button.setActive(true);
    }

    enum Tool {
        CURSOR("↖", "Cursor"),
        CROSSHAIR("⌖", "Crosshair"),

        TRENDLINE("╱", "Trendline"),
        HORIZONTAL_LINE("─", "Horizontal Line"),
        VERTICAL_LINE("│", "Vertical Line"),
        RECTANGLE("▭", "Rectangle"),
        TRIANGLE("△", "Triangle"),
        CIRCLE("○", "Circle"),
        FIBONACCI("φ", "Fibonacci Retracement"),
        MEASURE("↔", "Measure"),
        RISK_REWARD("◫", "Risk Reward"),
        ERASE_OBJECTS("⌫", "Erase Chart Objects"),

        ZOOM_IN("+", "Zoom In"),
        ZOOM_OUT("−", "Zoom Out"),
        FIT_CHART("⛶", "Fit Chart"),
        CHART_TYPE("◨", "Cycle Chart Type"),

        EVENTS("☷", "Toggle Events"),
        INDICATORS("ƒ", "Indicators"),
        SCREENSHOT("▣", "Screenshot"),
        PRINT("⎙", "Print Chart"),
        OPTIONS("⚙", "Chart Options");

        private final String fallbackText;
        private final String label;

        Tool(String fallbackText, String label) {
            this.fallbackText = fallbackText;
            this.label = label;
        }

        boolean isDrawingTool() {
            return this == CURSOR
                    || this == CROSSHAIR
                    || this == TRENDLINE
                    || this == HORIZONTAL_LINE
                    || this == VERTICAL_LINE
                    || this == RECTANGLE
                    || this == TRIANGLE
                    || this == CIRCLE
                    || this == FIBONACCI
                    || this == MEASURE
                    || this == RISK_REWARD
                    || this == ERASE_OBJECTS;
        }
    }

    private class SizeChangeListener extends DelayedSizeChangeListener {

        SizeChangeListener(
                BooleanProperty gotFirstSize,
                ObservableValue<Number> containerWidth,
                ObservableValue<Number> containerHeight) {
            super(650, 220, gotFirstSize, containerWidth, containerHeight);
        }

        @Override
        public void resize() {
            double width = Math.max(260, containerWidth.getValue().doubleValue());

            Font glyphFont;
            Insets buttonPadding;
            double gap;

            if (width >= LARGE_WIDTH_BREAKPOINT) {
                glyphFont = Font.font(14);
                buttonPadding = new Insets(4, 7, 4, 7);
                gap = 6;
            } else if (width >= MEDIUM_WIDTH_BREAKPOINT) {
                glyphFont = Font.font(13);
                buttonPadding = new Insets(3, 6, 3, 6);
                gap = 5;
            } else if (width >= COMPACT_WIDTH_BREAKPOINT) {
                glyphFont = Font.font(12);
                buttonPadding = new Insets(3, 5, 3, 5);
                gap = 4;
            } else {
                glyphFont = Font.font(11);
                buttonPadding = new Insets(2, 4, 2, 4);
                gap = 3;
            }

            toolbar.setSpacing(gap);

            for (Node toolbarNode : toolbar.getChildren()) {
                if (toolbarNode instanceof ToolbarButton toolbarButton) {
                    toolbarButton.setStyle("-fx-font-size: " + glyphFont.getSize() + "px;");
                    toolbarButton.setMinSize(MIN_BUTTON_SIZE, MIN_BUTTON_SIZE);
                    toolbarButton.setPrefHeight(MIN_BUTTON_SIZE);
                    toolbarButton.setPadding(buttonPadding);

                    if (toolbarButton.iconNode instanceof Label iconLabel) {
                        iconLabel.setStyle("-fx-font-size: " + glyphFont.getSize() + "px;");
                    }
                }
            }

            requestLayout();
        }
    }

    private static class ToolbarButton extends Button {
        private static final PseudoClass ACTIVE_CLASS = PseudoClass.getPseudoClass("active");

        private final Node iconNode;
        private final Tool tool;

        ToolbarButton(Tool tool) {
            if (tool == null) {
                throw new IllegalArgumentException("tool must not be null");
            }

            this.tool = tool;

            this.iconNode = createIcon(tool);

            setGraphic(iconNode);
            setText("");

            setMinSize(MIN_BUTTON_SIZE, MIN_BUTTON_SIZE);
            setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            setFocusTraversable(false);
            setTextOverrun(OverrunStyle.CLIP);
            setEllipsisString("");
            setTooltip(new Tooltip(tool.label));

            getStyleClass().add("candle-chart-toolbar-button");
            if (tool.isDrawingTool()) {
                getStyleClass().add("drawing-tool-button");
            }
            if (tool == Tool.ERASE_OBJECTS) {
                getStyleClass().add("erase-objects-button");
            }
        }

        private static Node createIcon(Tool tool) {
            return switch (tool) {
                case CURSOR -> glyph(tool.fallbackText);
                case CROSSHAIR -> crosshairIcon();
                case TRENDLINE -> singleLine(-5, 5, 5, -5);
                case HORIZONTAL_LINE -> singleLine(-5, 0, 5, 0);
                case VERTICAL_LINE -> singleLine(0, -5, 0, 5);
                case RECTANGLE -> strokedRectangle(10, 8, 1.8);
                case TRIANGLE -> triangleIcon();
                case CIRCLE -> strokedCircle(4.5, 1.8);
                case FIBONACCI -> glyph(tool.fallbackText);
                case MEASURE -> measureIcon();
                case RISK_REWARD -> riskRewardIcon();
                case ERASE_OBJECTS -> eraseIcon();
                case ZOOM_IN -> magnifierIcon(true);
                case ZOOM_OUT -> magnifierIcon(false);
                case FIT_CHART -> fitChartIcon();
                case CHART_TYPE -> chartTypeIcon();
                case EVENTS -> calendarIcon();
                case INDICATORS -> indicatorIcon();
                case SCREENSHOT -> screenshotIcon();
                case PRINT -> printIcon();
                case OPTIONS -> glyph(tool.fallbackText);
            };
        }

        private static Label glyph(String text) {
            Label glyph = new Label(text);
            glyph.getStyleClass().add("candle-chart-toolbar-glyph");
            glyph.setTextFill(TOOLBAR_ICON_COLOR);
            glyph.setMouseTransparent(true);
            glyph.setAlignment(Pos.CENTER);
            glyph.setMinSize(14, 14);
            return glyph;
        }

        private static StackPane iconPane(Node... nodes) {
            StackPane iconPane = new StackPane(nodes);
            iconPane.getStyleClass().add("candle-chart-toolbar-icon");
            iconPane.setMouseTransparent(true);
            iconPane.setMinSize(14, 14);
            iconPane.setPrefSize(14, 14);
            iconPane.setMaxSize(14, 14);
            return iconPane;
        }

        private static Line strokeLine(double startX, double startY, double endX, double endY) {
            Line line = new Line(startX, startY, endX, endY);
            line.setStroke(TOOLBAR_ICON_COLOR);
            line.setStrokeWidth(1.8);
            line.setStrokeLineCap(StrokeLineCap.ROUND);
            line.setMouseTransparent(true);
            return line;
        }

        private static Node singleLine(double startX, double startY, double endX, double endY) {
            return iconPane(strokeLine(startX, startY, endX, endY));
        }

        private static Rectangle strokedRectangle(double width, double height, double strokeWidth) {
            Rectangle rectangle = new Rectangle(width, height);
            rectangle.setFill(Color.TRANSPARENT);
            rectangle.setStroke(TOOLBAR_ICON_COLOR);
            rectangle.setStrokeWidth(strokeWidth);
            rectangle.setArcWidth(2);
            rectangle.setArcHeight(2);
            rectangle.setMouseTransparent(true);
            return rectangle;
        }

        private static Node triangleIcon() {
            Polygon polygon = new Polygon(
                    0.0, -4.5,
                    -5.0, 4.5,
                    5.0, 4.5);
            polygon.setFill(Color.TRANSPARENT);
            polygon.setStroke(TOOLBAR_ICON_COLOR);
            polygon.setStrokeWidth(1.8);
            polygon.setMouseTransparent(true);
            return iconPane(polygon);
        }

        private static Node strokedCircle(double radius, double strokeWidth) {
            Circle circle = new Circle(radius);
            circle.setFill(Color.TRANSPARENT);
            circle.setStroke(TOOLBAR_ICON_COLOR);
            circle.setStrokeWidth(strokeWidth);
            circle.setMouseTransparent(true);
            return iconPane(circle);
        }

        private static Node crosshairIcon() {
            Circle circle = new Circle(4.3);
            circle.setFill(Color.TRANSPARENT);
            circle.setStroke(TOOLBAR_ICON_COLOR);
            circle.setStrokeWidth(1.5);
            return iconPane(
                    circle,
                    strokeLine(-6, 0, 6, 0),
                    strokeLine(0, -6, 0, 6));
        }

        private static Node measureIcon() {
            return iconPane(
                    strokeLine(-5, 0, 5, 0),
                    strokeLine(-5, -3.5, -5, 3.5),
                    strokeLine(5, -3.5, 5, 3.5));
        }

        private static Node riskRewardIcon() {
            Rectangle risk = new Rectangle(4, 8);
            risk.setTranslateX(-2.5);
            risk.setTranslateY(1.5);
            risk.setFill(Color.TRANSPARENT);
            risk.setStroke(TOOLBAR_ICON_COLOR);
            risk.setStrokeWidth(1.6);
            Rectangle reward = new Rectangle(4, 5);
            reward.setTranslateX(2.5);
            reward.setTranslateY(-1.0);
            reward.setFill(Color.TRANSPARENT);
            reward.setStroke(TOOLBAR_ICON_COLOR);
            reward.setStrokeWidth(1.6);
            return iconPane(risk, reward, strokeLine(0, -5.5, 0, 5.5));
        }

        private static Node eraseIcon() {
            Polygon eraser = new Polygon(
                    -4.5, 1.5,
                    -1.5, -4.5,
                    4.5, -4.5,
                    1.5, 1.5);
            eraser.setFill(Color.TRANSPARENT);
            eraser.setStroke(TOOLBAR_ICON_COLOR);
            eraser.setStrokeWidth(1.6);
            return iconPane(eraser, strokeLine(-5.5, 4.5, 5.5, 4.5));
        }

        private static Node magnifierIcon(boolean zoomIn) {
            Circle lens = new Circle(3.8);
            lens.setFill(Color.TRANSPARENT);
            lens.setStroke(TOOLBAR_ICON_COLOR);
            lens.setStrokeWidth(1.6);
            lens.setTranslateX(-1.5);
            lens.setTranslateY(-1.5);

            Line handle = strokeLine(2, 2, 5.5, 5.5);
            Line horizontal = strokeLine(-3.5, 0, -0.2, 0);
            horizontal.setTranslateX(-1.4);
            horizontal.setTranslateY(-1.5);
            if (!zoomIn) {
                return iconPane(lens, handle, horizontal);
            }
            Line vertical = strokeLine(0, -3.3, 0, -0.1);
            vertical.setTranslateX(-1.4);
            vertical.setTranslateY(1.8);
            return iconPane(lens, handle, horizontal, vertical);
        }

        private static Node fitChartIcon() {
            return iconPane(
                    strokeLine(-5.5, -2.0, -5.5, -5.5),
                    strokeLine(-5.5, -5.5, -2.0, -5.5),
                    strokeLine(5.5, -2.0, 5.5, -5.5),
                    strokeLine(5.5, -5.5, 2.0, -5.5),
                    strokeLine(-5.5, 2.0, -5.5, 5.5),
                    strokeLine(-5.5, 5.5, -2.0, 5.5),
                    strokeLine(5.5, 2.0, 5.5, 5.5),
                    strokeLine(5.5, 5.5, 2.0, 5.5));
        }

        private static Node chartTypeIcon() {
            Rectangle leftBar = new Rectangle(2.4, 5.5);
            leftBar.setTranslateX(-4.2);
            leftBar.setTranslateY(1.7);
            leftBar.setFill(Color.TRANSPARENT);
            leftBar.setStroke(TOOLBAR_ICON_COLOR);
            leftBar.setStrokeWidth(1.4);

            Rectangle middleBar = new Rectangle(2.4, 8.5);
            middleBar.setTranslateX(-0.4);
            middleBar.setTranslateY(0.2);
            middleBar.setFill(Color.TRANSPARENT);
            middleBar.setStroke(TOOLBAR_ICON_COLOR);
            middleBar.setStrokeWidth(1.4);

            Rectangle rightBar = new Rectangle(2.4, 3.8);
            rightBar.setTranslateX(3.4);
            rightBar.setTranslateY(2.5);
            rightBar.setFill(Color.TRANSPARENT);
            rightBar.setStroke(TOOLBAR_ICON_COLOR);
            rightBar.setStrokeWidth(1.4);

            Line line = strokeLine(-5.5, 3.5, -1.5, -2.0);
            Line line2 = strokeLine(-1.5, -2.0, 5.5, 1.0);
            return iconPane(leftBar, middleBar, rightBar, line, line2);
        }

        private static Node calendarIcon() {
            Rectangle frame = new Rectangle(10, 9);
            frame.setFill(Color.TRANSPARENT);
            frame.setStroke(TOOLBAR_ICON_COLOR);
            frame.setStrokeWidth(1.5);
            frame.setArcWidth(2);
            frame.setArcHeight(2);
            frame.setTranslateY(0.5);
            return iconPane(
                    frame,
                    strokeLine(-5, -2.5, 5, -2.5),
                    strokeLine(-2.5, -5, -2.5, -2.0),
                    strokeLine(2.5, -5, 2.5, -2.0),
                    strokeLine(-3, 1.0, 3, 1.0));
        }

        private static Node indicatorIcon() {
            SVGPath path = new SVGPath();
            path.setContent("M -6 3 C -4 -4 -1 -4 1 0 S 5 5 6 -2");
            path.setStroke(TOOLBAR_ICON_COLOR);
            path.setFill(Color.TRANSPARENT);
            path.setStrokeWidth(1.7);
            path.setStrokeLineCap(StrokeLineCap.ROUND);
            path.setMouseTransparent(true);
            return iconPane(path);
        }

        private static Node screenshotIcon() {
            Rectangle frame = new Rectangle(10, 8);
            frame.setFill(Color.TRANSPARENT);
            frame.setStroke(TOOLBAR_ICON_COLOR);
            frame.setStrokeWidth(1.5);
            frame.setArcWidth(2);
            frame.setArcHeight(2);
            Circle shutter = new Circle(2.0);
            shutter.setFill(Color.TRANSPARENT);
            shutter.setStroke(TOOLBAR_ICON_COLOR);
            shutter.setStrokeWidth(1.4);
            shutter.setTranslateX(0.5);
            shutter.setTranslateY(0.3);
            return iconPane(frame, shutter, strokeLine(-4.5, -4.5, -1.5, -4.5));
        }

        private static Node printIcon() {
            Rectangle printerBody = new Rectangle(10, 5.5);
            printerBody.setFill(Color.TRANSPARENT);
            printerBody.setStroke(TOOLBAR_ICON_COLOR);
            printerBody.setStrokeWidth(1.5);
            printerBody.setTranslateY(1.5);

            Rectangle paper = new Rectangle(7, 4.5);
            paper.setFill(Color.TRANSPARENT);
            paper.setStroke(TOOLBAR_ICON_COLOR);
            paper.setStrokeWidth(1.4);
            paper.setTranslateY(-3.5);

            return iconPane(printerBody, paper, strokeLine(-2.5, 1.5, 2.5, 1.5));
        }

        private final BooleanProperty active = new BooleanPropertyBase(false) {
            @Override
            public void invalidated() {
                pseudoClassStateChanged(ACTIVE_CLASS, get());
            }

            @Override
            public Object getBean() {
                return ToolbarButton.this;
            }

            @Override
            public String getName() {
                return "active";
            }
        };

        public void setActive(boolean active) {
            this.active.set(active);
        }
    }
}
