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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.util.Duration;
import lombok.Getter;
import org.controlsfx.control.PopOver;
import org.investpro.ui.charts.CandleStickChart;
import org.investpro.utils.DelayedSizeChangeListener;

import java.io.InputStream;
import java.util.Objects;
import java.util.Set;

/**
 * Professional chart-only toolbar for InvestPro candlestick charts.
 *
 * <p>This toolbar intentionally does not include desk-level trading controls:
 * BUY, SELL, New Order, Cancel All, Algo Trading, broker selector, symbol selector,
 * or timeframe controls.</p>
 *
 * <p>It only includes chart tools a trader needs:</p>
 * <ul>
 *     <li>Cursor</li>
 *     <li>Crosshair</li>
 *     <li>Trendline</li>
 *     <li>Horizontal line</li>
 *     <li>Vertical line</li>
 *     <li>Rectangle</li>
 *     <li>Fibonacci retracement</li>
 *     <li>Measure/ruler</li>
 *     <li>Zoom in/out</li>
 *     <li>Fit chart</li>
 *     <li>Indicators</li>
 *     <li>Screenshot</li>
 *     <li>Print</li>
 *     <li>Chart options</li>
 * </ul>
 */
@Getter
public class ChartToolbar extends Region {

    private static final int MIN_BUTTON_SIZE = 24;
    private static final int LARGE_WIDTH_BREAKPOINT = 900;
    private static final int MEDIUM_WIDTH_BREAKPOINT = 680;
    private static final int COMPACT_WIDTH_BREAKPOINT = 480;

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
     * <p>The {@code granularities} argument is intentionally ignored because
     * timeframe controls belong to the parent chart header or trading desk.</p>
     */
    public ChartToolbar(
            ObservableNumberValue containerWidth,
            ObservableNumberValue containerHeight,
            Set<Integer> granularities
    ) {
        this(containerWidth, containerHeight);
    }

    public ChartToolbar(
            ObservableNumberValue containerWidth,
            ObservableNumberValue containerHeight
    ) {
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
                verticalSeparator(),

                new ToolbarButton(Tool.EVENTS),
                new ToolbarButton(Tool.INDICATORS),
                new ToolbarButton(Tool.SCREENSHOT),
                new ToolbarButton(Tool.PRINT),
                spacer(),

                createOptionsButton()
        );
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
                    boolean insidePopover =
                            event.getScreenX() <= optionsPopOver.getX() + optionsPopOver.getWidth()
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
     * <p>This toolbar no longer owns timeframe buttons, so this method is intentionally empty.</p>
     */
    public void setActiveToolbarButton(javafx.beans.property.IntegerProperty secondsPerCandle) {
        // Intentionally empty.
    }

    public void registerEventHandlers(
            CandleStickChart candleStickChart,
            javafx.beans.property.IntegerProperty secondsPerCandle
    ) {
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
        CURSOR("/img/mouse-pointer-solid.png", "↖", "Cursor"),
        CROSSHAIR("/img/crosshairs-solid.png", "⌖", "Crosshair"),

        TRENDLINE("/img/trendline.png", "╱", "Trendline"),
        HORIZONTAL_LINE("/img/horizontal-line.png", "─", "Horizontal Line"),
        VERTICAL_LINE("/img/vertical-line.png", "│", "Vertical Line"),
        RECTANGLE("/img/rectangle.png", "▭", "Rectangle"),
        TRIANGLE("/img/triangle.png", "△", "Triangle"),
        CIRCLE("/img/circle.png", "○", "Circle"),
        FIBONACCI("/img/fibonacci.png", "Φ", "Fibonacci Retracement"),
        MEASURE("/img/ruler-solid.png", "↔", "Measure"),
        RISK_REWARD("/img/sliders-solid.png", "RR", "Risk Reward"),
        ERASE_OBJECTS("/img/eraser-solid.png", "⌫", "Erase Chart Objects"),

        ZOOM_IN("/img/search-plus-solid.png", "+", "Zoom In"),
        ZOOM_OUT("/img/search-minus-solid.png", "−", "Zoom Out"),
        FIT_CHART("/img/expand-solid.png", "⛶", "Fit Chart"),

        EVENTS("/img/calendar-days-solid.png", "Ev", "Toggle Events"),
        INDICATORS("/img/chart-line-solid.png", "ƒ", "Indicators"),
        SCREENSHOT("/img/screenshot.png", "▣", "Screenshot"),
        PRINT("/img/print-solid.png", "⎙", "Print Chart"),
        OPTIONS("/img/cog-solid.png", "⚙", "Chart Options");

        private final String img;
        private final String fallbackText;
        private final String label;

        Tool(String img, String fallbackText, String label) {
            this.img = img;
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
                ObservableValue<Number> containerHeight
        ) {
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

                    if (toolbarButton.iconNode instanceof ImageView imageView) {
                        imageView.setFitHeight(glyphFont.getSize());
                        imageView.setFitWidth(glyphFont.getSize());
                    } else if (toolbarButton.iconNode instanceof Label iconLabel) {
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

            ImageView loadedGraphic = tryLoadIcon(tool.img);

            if (loadedGraphic != null) {
                this.iconNode = loadedGraphic;
            } else {
                Label fallbackGlyph = new Label(tool.fallbackText);
                fallbackGlyph.getStyleClass().add("candle-chart-toolbar-glyph");
                fallbackGlyph.setMouseTransparent(true);
                this.iconNode = fallbackGlyph;
            }

            setGraphic(iconNode);
            setText("");

            setMinSize(MIN_BUTTON_SIZE, MIN_BUTTON_SIZE);
            setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            setFocusTraversable(false);
            setTextOverrun(OverrunStyle.CLIP);
            setEllipsisString("");
            setTooltip(new Tooltip(tool.label));

            getStyleClass().add("candle-chart-toolbar-button");
        }

        private static ImageView tryLoadIcon(String img) {
            try (InputStream stream = ToolbarButton.class.getResourceAsStream(img)) {
                if (stream == null) {
                    return null;
                }

                Image image = new Image(stream);

                if (image.isError()) {
                    return null;
                }

                ImageView imageView = new ImageView(image);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                imageView.setMouseTransparent(true);
                return imageView;

            } catch (Exception ignored) {
                return null;
            }
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
