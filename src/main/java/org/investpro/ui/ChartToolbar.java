package org.investpro.ui;


import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Separator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import org.investpro.ui.charts.CandleStickChart;
import org.investpro.ui.charts.CandleStickChartOptions;
import org.investpro.ui.tools.PriceLine;
import org.investpro.utils.DelayedSizeChangeListener;
import org.investpro.utils.PopOver;
import org.investpro.utils.ZoomDirection;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.investpro.utils.FXUtils.computeTextDimensions;


/**
 * A resizable toolbar, placed at the top of a {@code CandleStickChart} and contained
 * inside a {@code CandleStickChartContainer}, that contains a series of labelled
 * buttons that allow for controlling the chart paired with this toolbar. Some of the
 * functions of the buttons are:
 *
 * <ul>
 * <li>Select the duration of each candle</li>
 * <li>Zoom the chart in/out</li>
 * <li>Print the chart</li>
 * <li>Configure the chart's options (via a PopOver triggered by a button)</li>
 * <li>Take screenshots of the chart</li>
 * <li>Enable/disable auto trading</li>
 * </ul>
 * <p>
 * The toolbar buttons are labelled with either text (which is used for the duration buttons,
 * e.g. "6h") or a glyph (e.g. magnifying glasses with a plus/minus for zoom in/out).
 * <p>
 * The toolbar is responsive and adjusts button sizes and fonts based on container width.
 * It manages a PopOver for chart options and handles mouse interactions elegantly.
 */
@Getter
@Setter
public class ChartToolbar extends Region {
    // Layout Constants
    private static final int POPOVER_BUFFER_PERCENTAGE = 10;
    private static final int FONT_SIZE_THRESHOLD = 900;
    private static final int FONT_SIZE_LARGE = 14;
    private static final int FONT_SIZE_MEDIUM = 13;
    private static final int GLYPH_FONT_SIZE_LARGE = 15;
    private static final int PADDING_ADJUSTMENT_FACTOR = 100;
    private static final int TEXT_PADDING_TOP_BOTTOM_LARGE = 4;
    private static final int TEXT_PADDING_LEFT_RIGHT_LARGE = 8;
    private static final int TEXT_PADDING_BASE = 4;
    private static final int GLYPH_PADDING_TOP_BOTTOM_LARGE = 5;
    private static final int GLYPH_PADDING_LEFT_RIGHT_LARGE = 10;
    private static final int GLYPH_PADDING_BASE = 5;
    private static final int SEPARATOR_PADDING_LARGE = 20;
    private static final int TEXT_BUTTON_WIDTH_PADDING = 15;
    private static final int SIZE_ADJUSTMENT_DELAY_PRIMARY = 750;
    private static final int SIZE_ADJUSTMENT_DELAY_SECONDARY = 300;

    // UI Components
    private final HBox toolbar = new HBox();
    private PopOver optionsPopOver;
    private MouseExitedPopOverFilter mouseExitedPopOverFilter;
    private volatile boolean mouseInsideOptionsButton;
    private Separator functionOptionsSeparator;

    // Action buttons
    private final Button screenshotButton = new Button("Screenshot");
    private final Button autoTradeButton = new Button("Auto Trade");

    // Callbacks for button actions
    private Runnable onScreenshotAction;
    private Runnable onAutoTradeAction;
    private Runnable onPrintAction;
    public ChartToolbar(ObservableNumberValue containerWidth, ObservableNumberValue containerHeight,
                        PopOver optionsPopOver, Separator functionOptionsSeparator) {
        this.optionsPopOver = optionsPopOver;
        this.functionOptionsSeparator = functionOptionsSeparator;
        Objects.requireNonNull(containerWidth, "containerWidth must not be null");
        Objects.requireNonNull(containerHeight, "containerHeight must not be null");

        List<Node> toolbarNodes = buildToolbarNodes();
        configureToolbar(toolbarNodes, containerWidth, containerHeight);
    }

    /**
     * Builds the list of toolbar nodes (buttons and separators)
     */
    private @NotNull List<Node> buildToolbarNodes() {
        List<Node> toolbarNodes = new ArrayList<>();
          addActionButtons(toolbarNodes);
        addFunctionButtons(toolbarNodes);
        return toolbarNodes;
    }


    /**
     * Adds action buttons (screenshot, auto-trade).
     */
    private void addActionButtons(@NotNull List<Node> toolbarNodes) {
        toolbarNodes.add(autoTradeButton);
        toolbarNodes.add(screenshotButton);
        toolbarNodes.add(createInvisibleSeparator());
    }

    /**
     * Adds function buttons (zoom, navigation, overlays, print, options).
     */
    private void addFunctionButtons(@NotNull List<Node> toolbarNodes) {
        functionOptionsSeparator = createInvisibleSeparator();
        functionOptionsSeparator.setPadding(new Insets(0, SEPARATOR_PADDING_LARGE, 0, 0));

        if (optionsPopOver == null) {
            optionsPopOver = new PopOver();
            optionsPopOver.setTitle("Chart Options");
            optionsPopOver.setHeaderAlwaysVisible(true);
        }

        Tool[] essentialTools = {
            Tool.ZOOM_IN,
            Tool.ZOOM_OUT,
            Tool.JUMP_TO_LATEST,
            Tool.FIT_CHART,
            Tool.REFRESH_CHART,
            Tool.TOGGLE_CROSSHAIR,
            Tool.TOGGLE_PRICE_LINES,
            Tool.ADD_SUPPORT_LINE,
            Tool.ADD_RESISTANCE_LINE,
            Tool.ADD_ENTRY_LINE,
            Tool.ADD_STOP_LOSS_LINE,
            Tool.ADD_TAKE_PROFIT_LINE,
            Tool.CLEAR_PRICE_LINES,
            Tool.APPLY_SCALING,
            Tool.PRINT,
            Tool.OPTIONS
        };

        toolbarNodes.add(functionOptionsSeparator);

        for (Tool tool : essentialTools) {
            ToolbarButton toolbarButton;
            if (tool == Tool.OPTIONS) {
                toolbarButton = new ToolbarButton(Tool.OPTIONS);
                attachOptionsButtonBehavior(toolbarButton);
            } else {
                toolbarButton = new ToolbarButton(tool);
            }
            toolbarNodes.add(toolbarButton);
        }
    }

    /**
     * Attaches hover behavior to the options button.
     */
    private void attachOptionsButtonBehavior(@NotNull ToolbarButton toolbarButton) {
        toolbarButton.setOnAction(_ -> showOptionsPopOver(toolbarButton));
        toolbarButton.setOnMouseEntered(_ -> {
            mouseInsideOptionsButton = true;
            showOptionsPopOver(toolbarButton);
        });
        toolbarButton.setOnMouseExited(_ -> {
            mouseInsideOptionsButton = false;
            if (getScene() != null && getScene().getWindow() != null && mouseExitedPopOverFilter == null) {
                mouseExitedPopOverFilter = new MouseExitedPopOverFilter(getScene());
                getScene().getWindow().addEventFilter(MouseEvent.MOUSE_MOVED, mouseExitedPopOverFilter);
            }
        });
    }

    private void showOptionsPopOver(Node owner) {
        if (optionsPopOver == null || owner == null || owner.getScene() == null || owner.getScene().getWindow() == null) {
            return;
        }
        optionsPopOver.show(owner);
    }

    /**
     * Creates an invisible separator for visual grouping.
     */
    private Separator createInvisibleSeparator() {
        Separator separator = new Separator();
        separator.setOpacity(0);
        return separator;
    }

    /**
     * Configures the toolbar container and sets up dynamic resizing.
     */
    private void configureToolbar(List<Node> toolbarNodes, ObservableNumberValue containerWidth,
                                   ObservableNumberValue containerHeight) {
        toolbar.getChildren().addAll(toolbarNodes);
        toolbar.getStyleClass().add("candle-chart-toolbar");
        toolbar.setFillHeight(true);

        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);
        final SizeChangeListener sizeListener = new SizeChangeListener(gotFirstSize, containerWidth,
                containerHeight);
        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);
        ChangeListener<? super Boolean> gotFirstSizeChangeListener = new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                sizeListener.resize();
                gotFirstSize.removeListener(this);
            }
        };

        gotFirstSize.addListener(gotFirstSizeChangeListener);
        getChildren().setAll(toolbar);
    }

    @Override
    protected void layoutChildren() {
        toolbar.resizeRelocate(0, 0, getWidth(), getHeight());
    }

    @Override
    protected double computeMinWidth(double height) {
        return 0;
    }

    @Override
    protected double computePrefWidth(double height) {
        return 520;
    }

    @Override
    protected double computeMinHeight(double width) {
        return 28;
    }

    @Override
    protected double computePrefHeight(double width) {
        return 32;
    }

    /**
     * Sets which toolbar button is active based on the selected granularity.
     *
     * @param secondsPerCandle the selected candle duration in seconds
     * @throws NullPointerException if secondsPerCandle is null
     */
    void setActiveToolbarButton(IntegerProperty secondsPerCandle) {
        Objects.requireNonNull(secondsPerCandle, "secondsPerCandle must not be null");
        for (Node childNode : toolbar.getChildren()) {
            if (childNode instanceof ToolbarButton tool) {
                tool.setActive(secondsPerCandle.get() == tool.duration);
            }
        }
    }

    /**
     * Registers event handlers for all toolbar buttons with the chart.
     * Connects granularity buttons to timeframe changes and tool buttons to their actions.
     *
     * @param candleStickChart the chart to control
     * @param secondsPerCandle the property tracking the current candle duration
     * @throws NullPointerException if either parameter is null
     */
    void registerEventHandlers(CandleStickChart candleStickChart, IntegerProperty secondsPerCandle) {
        Objects.requireNonNull(candleStickChart, "candleStickChart must not be null");
        Objects.requireNonNull(secondsPerCandle, "secondsPerCandle must not be null");

        this.candleStickChart = candleStickChart;
        registerToolHandlers(candleStickChart);
        registerActionHandlers();
    }


    /**
     * Registers handlers for tool buttons (zoom, print, options).
     */
    private void registerToolHandlers(CandleStickChart candleStickChart) {
        for (Node childNode : toolbar.getChildren()) {
            if (childNode instanceof ToolbarButton tool) {
                switch (tool.tool) {
                    // Zoom functions
                    case ZOOM_IN, ZOOM_OUT -> tool.setOnAction(_ -> candleStickChart.changeZoom(tool.tool.getZoomDirection()));
                    // Chart navigation
                    case JUMP_TO_LATEST -> tool.setOnAction(_ -> candleStickChart.jumpToLatestCandle());
                    case FIT_CHART -> tool.setOnAction(_ -> candleStickChart.fitChart());
                    case REFRESH_CHART -> tool.setOnAction(_ -> candleStickChart.refreshChart());
                    case AUTO_TRADE  -> tool.setOnAction(_ -> candleStickChart.autoTrade());
                    case SCREEN_SHOT -> tool.setOnAction(_ -> candleStickChart.screenshot());

                    // Crosshair controls
                    case TOGGLE_CROSSHAIR -> tool.setOnAction(_ -> candleStickChart.toggleCrosshair());

                    // Price line controls
                    case TOGGLE_PRICE_LINES -> tool.setOnAction(_ -> candleStickChart.togglePriceLines());
                    case ADD_SUPPORT_LINE -> tool.setOnAction(_ -> addPromptedPriceLine(candleStickChart, PriceLineKind.SUPPORT));
                    case ADD_RESISTANCE_LINE -> tool.setOnAction(_ -> addPromptedPriceLine(candleStickChart, PriceLineKind.RESISTANCE));
                    case ADD_ENTRY_LINE -> tool.setOnAction(_ -> addPromptedPriceLine(candleStickChart, PriceLineKind.ENTRY));
                    case ADD_STOP_LOSS_LINE -> tool.setOnAction(_ -> addPromptedPriceLine(candleStickChart, PriceLineKind.STOP_LOSS));
                    case ADD_TAKE_PROFIT_LINE -> tool.setOnAction(_ -> addPromptedPriceLine(candleStickChart, PriceLineKind.TAKE_PROFIT));
                    case CLEAR_PRICE_LINES -> tool.setOnAction(_ -> candleStickChart.clearPriceLines());

                    // Adaptive scaling
                    case APPLY_SCALING -> tool.setOnAction(_ -> candleStickChart.applyAdaptiveScaling());

                    // Other functions
                    case PRINT -> tool.setOnAction(_ -> executePrintAction());
                    case null -> {
                        // No action for null tool (shouldn't happen)
                    }
                    case OPTIONS -> tool.setOnAction(_ -> showOptionsPopOver(tool));
                }
            }
        }
    }

    private void addPromptedPriceLine(CandleStickChart chart, PriceLineKind kind) {
        if (chart == null || kind == null) {
            return;
        }

        double defaultPrice = chart.getHoveredPrice() > 0 ? chart.getHoveredPrice() : chart.getLatestClosePrice();
        TextInputDialog dialog = new TextInputDialog(defaultPrice > 0 ? String.valueOf(defaultPrice) : "");
        dialog.setTitle(kind.dialogTitle());
        dialog.setHeaderText(kind.dialogTitle());
        dialog.setContentText("Price:");

        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty()) {
            return;
        }

        double price;
        try {
            price = Double.parseDouble(answer.get().trim());
        } catch (NumberFormatException exception) {
            return;
        }

        if (!Double.isFinite(price) || price <= 0.0) {
            return;
        }

        switch (kind) {
            case SUPPORT -> chart.addSupportPriceLine(price);
            case RESISTANCE -> chart.addResistancePriceLine(price);
            case ENTRY -> chart.addEntryPriceLine(price);
            case STOP_LOSS -> chart.addStopLossPriceLine(price);
            case TAKE_PROFIT -> chart.addTakeProfitPriceLine(price);
        }
    }

    /**
     * Registers handlers for action buttons (screenshot, auto-trade).
     */
    private void registerActionHandlers() {
        screenshotButton.setOnAction(_ -> executeScreenshotAction());
        autoTradeButton.setOnAction(_ -> executeAutoTradeAction());
    }

    /**
     * Sets the chart options pane to be displayed in the options PopOver.
     *
     * @param chartOptions the chart options UI component
     * @throws NullPointerException if chartOptions is null
     */
    void setChartOptions(@NotNull CandleStickChartOptions chartOptions) {
        Objects.requireNonNull(chartOptions, "chartOptions must not be null");
        optionsPopOver.setContentNode(chartOptions.getOptionsPane());
    }

    /**
     * Stores reference to the candlestick chart for direct method calls.
     * This allows toolbar to properly manage chart state via public API methods.
     */
    private CandleStickChart candleStickChart;






    /**
     * Shows or hides all price lines on the chart.
     *
     * @param visible true to show price lines, false to hide
     */
    public void setChartPriceLinesVisible(boolean visible) {
        if (candleStickChart != null) {
            candleStickChart.setPriceLinesVisible(visible);
        }
    }

    /**
     * Checks if price lines are currently visible.
     *
     * @return true if price lines are visible, false otherwise
     */
    public boolean isChartPriceLinesVisible() {
        return candleStickChart != null && candleStickChart.isPriceLinesVisible();
    }

    public void toggleChartPriceLines() {
        if (candleStickChart != null) {
            candleStickChart.togglePriceLines();
        }
    }

    /**
     * Shows or hides the crosshair overlay.
     *
     * @param visible true to show crosshair, false to hide
     */
    public void setChartCrosshairVisible(boolean visible) {
        if (candleStickChart != null) {
            candleStickChart.setCrosshairVisible(visible);
        }
    }

    /**
     * Checks if the crosshair overlay is currently visible.
     *
     * @return true if crosshair is visible, false otherwise
     */
    public boolean isChartCrosshairVisible() {
        return candleStickChart != null && candleStickChart.isCrosshairVisible();
    }

    public void toggleChartCrosshair() {
        if (candleStickChart != null) {
            candleStickChart.toggleCrosshair();
        }
    }

    public void changeChartZoom(ZoomDirection zoomDirection) {
        if (candleStickChart != null) {
            candleStickChart.changeZoom(zoomDirection);
        }
    }



    /**
     * Refreshes the entire chart by reloading data.
     */
    public void refreshChart() {
        if (candleStickChart != null) {
            candleStickChart.refreshChart();
        }
    }

    /**
     * Jumps the view to the latest/most recent candle.
     */
    public void jumpToLatestCandle() {
        if (candleStickChart != null) {
            candleStickChart.jumpToLatestCandle();
        }
    }

    /**
     * Fits all available chart data into the current view.
     */
    public void fitChart() {
        if (candleStickChart != null) {
            candleStickChart.fitChart();
        }
    }

    /**
     * Applies adaptive scaling to optimize candle visibility.
     */
    public void applyAdaptiveScaling() {
        if (candleStickChart != null) {
            candleStickChart.applyAdaptiveScaling();
        }
    }

    /**
     * Gets the current zoom level index.
     *
     * @return the zoom level index, or -1 if chart is not ready
     */
    public int getCurrentChartZoomLevel() {
        return candleStickChart != null ? candleStickChart.getCurrentZoomLevelIndex() : -1;
    }

    public void disposeChart() {
        if (candleStickChart != null) {
            candleStickChart.dispose();
            candleStickChart = null;
        }
    }






    /**
     * Executes the screenshot action if a callback is set.
     */
    private void executeScreenshotAction() {
        if (onScreenshotAction != null) {
            onScreenshotAction.run();
        } else if (candleStickChart != null) {
            candleStickChart.screenshot();
        }
    }

    private void executeAutoTradeAction() {
        if (onAutoTradeAction != null) {
            onAutoTradeAction.run();
        } else if (candleStickChart != null) {
            candleStickChart.autoTrade();
        }
    }

    private void executePrintAction() {
        if (onPrintAction != null) {
            onPrintAction.run();
        } else if (candleStickChart != null) {
            candleStickChart.print();
        }
    }

    /**
     * Enumeration of toolbar tools (non-granularity buttons).
     * Includes zoom controls, navigation, crosshair toggle, and price line management.
     */
    enum Tool {
        // Zoom controls
        ZOOM_IN("/img/search-plus-solid.png", "Zoom In"),
        ZOOM_OUT("/img/search-minus-solid.png", "Zoom Out"),

        // Chart navigation
        JUMP_TO_LATEST("/img/arrow-right-solid.png", "Jump to Latest"),
        FIT_CHART("/img/expand-solid.png", "Fit Chart"),
        REFRESH_CHART("/img/refresh-solid.png", "Refresh Chart"),

        // Overlay controls
        TOGGLE_CROSSHAIR("/img/crosshairs-solid.png", "Toggle Crosshair"),
        TOGGLE_PRICE_LINES("/img/minus-solid.png", "Toggle Price Lines"),
        ADD_SUPPORT_LINE("/img/plus-solid.png", "Add Support Line"),
        ADD_RESISTANCE_LINE("/img/plus-solid.png", "Add Resistance Line"),
        ADD_ENTRY_LINE("/img/plus-solid.png", "Add Entry Line"),
        ADD_STOP_LOSS_LINE("/img/plus-solid.png", "Add Stop Loss Line"),
        ADD_TAKE_PROFIT_LINE("/img/plus-solid.png", "Add Take Profit Line"),
        CLEAR_PRICE_LINES("/img/trash-solid.png", "Clear Price Lines"),

        // Adaptive scaling
        APPLY_SCALING("/img/sliders-solid.png", "Apply Adaptive Scaling"),

        // Chart options
        PRINT("/img/print-solid.png", "Print Chart"),
        OPTIONS("/img/cog-solid.png", "Chart Options"),
        SCREEN_SHOT("/img/screenshot.png","Chart Screen Shot" ),
        AUTO_TRADE("/img/auto-trade-solid.png", "Auto Trade");


        private final String imgPath;
        private final String tooltip;

        Tool(String imgPath, String tooltip) {
            this.imgPath = imgPath;
            this.tooltip = tooltip;
        }

        /**
         * Gets the resource path to the icon image.
         */
        String getImagePath() {
            return imgPath;
        }

        /**
         * Gets the tooltip text for this tool.
         */
        String getTooltipText() {
            return tooltip;
        }

        String getFallbackLabel() {
            return switch (this) {
                case ZOOM_IN -> "+";
                case ZOOM_OUT -> "-";
                case JUMP_TO_LATEST -> ">|";
                case FIT_CHART -> "Fit";
                case REFRESH_CHART -> "Ref";
                case TOGGLE_CROSSHAIR -> "Cross";
                case TOGGLE_PRICE_LINES -> "Line";
                case ADD_SUPPORT_LINE -> "Sup";
                case ADD_RESISTANCE_LINE -> "Res";
                case ADD_ENTRY_LINE -> "Ent";
                case ADD_STOP_LOSS_LINE -> "SL";
                case ADD_TAKE_PROFIT_LINE -> "TP";
                case CLEAR_PRICE_LINES -> "Clr";
                case APPLY_SCALING -> "Scale";
                case PRINT -> "Print";
                case OPTIONS -> "Opt";
                case SCREEN_SHOT -> "Screen Shot";
                case AUTO_TRADE -> "AI Trade";
            };
        }

        /**
         * Checks if this tool is a zoom function.
         */
        boolean isZoomFunction() {
            return this == ZOOM_IN || this == ZOOM_OUT;
        }

        /**
         * Gets the zoom direction for zoom tools.
         *
         * @return the zoom direction
         * @throws IllegalArgumentException if this is not a zoom tool
         */
        ZoomDirection getZoomDirection() {
            if (!isZoomFunction()) {
                throw new IllegalArgumentException("cannot call getZoomDirection() on non-zoom function: %s".formatted(name()));
            }
            return this == ZOOM_IN ? ZoomDirection.IN : ZoomDirection.OUT;
        }
    }

    private enum PriceLineKind {
        SUPPORT("Add Support Line"),
        RESISTANCE("Add Resistance Line"),
        ENTRY("Add Entry Line"),
        STOP_LOSS("Add Stop Loss Line"),
        TAKE_PROFIT("Add Take Profit Line");

        private final String dialogTitle;

        PriceLineKind(String dialogTitle) {
            this.dialogTitle = dialogTitle;
        }

        String dialogTitle() {
            return dialogTitle;
        }
    }

    /**
     * A specialized Button for the toolbar that can be either a text-labeled granularity button
     * or a glyph-labeled tool button.
     */
    private static class ToolbarButton extends Button {
        private final String textLabel;
        private final ImageView graphicLabel;
        private final Tool tool;
        private final int duration;
        private final PseudoClass activeClass = PseudoClass.getPseudoClass("active");

        private final BooleanProperty active = new BooleanPropertyBase(false) {
            @Override
            public void invalidated() {
                pseudoClassStateChanged(activeClass, get());
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

        /**
         * Creates a glyph-labeled button for tools.
         *
         * @param tool the tool type
         */
        ToolbarButton(Tool tool) {
            this(null, tool, tool.getImagePath(), -1);
        }

        /**
         * Internal constructor for all button types.
         *
         * @param textLabel the text label or null
         * @param tool the tool type or null
         * @param imgPath the image path or null
         * @param duration the duration in seconds or -1
         */
        private ToolbarButton(String textLabel, Tool tool, String imgPath, int duration) {
            if (textLabel == null && imgPath == null) {
                throw new IllegalArgumentException("textLabel and imgPath cannot both be null");
            }
            this.textLabel = textLabel;
            this.tool = tool;
            this.duration = duration;

            // Set text content
            setText(textLabel == null ? "" : textLabel);

            // Set icon if provided
            if (imgPath != null) {
                InputStream imageStream = ToolbarButton.class.getResourceAsStream(imgPath);
                if (imageStream != null) {
                    Image image = new Image(imageStream);
                    graphicLabel = new ImageView(image);
                    setGraphic(graphicLabel);
                } else {
                    graphicLabel = null;
                    setText(tool == null ? "" : tool.getFallbackLabel());
                }
            } else {
                graphicLabel = null;
            }

            if (tool != null) {
                setTooltip(new Tooltip(tool.getTooltipText()));
                setAccessibleText(tool.getTooltipText());
            }

            // Configure button appearance
            setMinSize(5, 5);
            setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            textOverrunProperty().set(OverrunStyle.CLIP);
            setEllipsisString("");
            getStyleClass().add("candle-chart-toolbar-button");
        }

        /**
         * Sets whether this button is currently active/selected.
         *
         * @param active true if active, false otherwise
         */
        public void setActive(boolean active) {
            this.active.set(active);
        }


    }

    /**
     * Listener that adjusts toolbar button sizes and fonts based on container dimensions.
     * Uses delayed sizing to debounce rapid resize events.
     */
    private class SizeChangeListener extends DelayedSizeChangeListener {
        SizeChangeListener(BooleanProperty gotFirstSize, ObservableValue<Number> containerWidth,
                           ObservableValue<Number> containerHeight) {
            super(SIZE_ADJUSTMENT_DELAY_PRIMARY, SIZE_ADJUSTMENT_DELAY_SECONDARY, gotFirstSize,
                    containerWidth, containerHeight);
        }

        /**
         * Recalculates and applies sizes, padding, and fonts to all toolbar buttons.
         */
        public void resize() {
            double width = containerWidth.getValue().doubleValue();
            boolean isLargeScreen = width >= FONT_SIZE_THRESHOLD;

            // Calculate text button dimensions
            Font textFont = calculateTextFont(isLargeScreen, width);
            Insets textPadding = calculateTextPadding(isLargeScreen, width);

            // Calculate glyph button dimensions
            Font glyphFont = calculateGlyphFont(isLargeScreen, width);
            Insets glyphPadding = calculateGlyphPadding(isLargeScreen, width);

            // Apply sizes to buttons
            applyButtonSizes(textFont, textPadding, glyphFont, glyphPadding, width);

            // Update separator padding
            updateSeparatorPadding(isLargeScreen, width);
        }

        /**
         * Calculates appropriate font size for text buttons.
         */
        private Font calculateTextFont(boolean isLargeScreen, double width) {
            if (isLargeScreen) {
                return Font.font(FONT_SIZE_LARGE);
            } else {
                int sizeReduction = (int) ((1000 - width) / PADDING_ADJUSTMENT_FACTOR);
                return Font.font(Math.max(8, FONT_SIZE_MEDIUM - sizeReduction));
            }
        }

        /**
         * Calculates appropriate padding for text buttons.
         */
        private Insets calculateTextPadding(boolean isLargeScreen, double width) {
            if (isLargeScreen) {
                return new Insets(TEXT_PADDING_TOP_BOTTOM_LARGE, TEXT_PADDING_LEFT_RIGHT_LARGE,
                        TEXT_PADDING_TOP_BOTTOM_LARGE, TEXT_PADDING_LEFT_RIGHT_LARGE);
            } else {
                int sizeReduction = (int) ((1000 - width) / PADDING_ADJUSTMENT_FACTOR);
                int topBottom = Math.max(0, TEXT_PADDING_BASE - sizeReduction);
                int leftRight = Math.max(0, TEXT_PADDING_LEFT_RIGHT_LARGE - 2 * sizeReduction);
                return new Insets(topBottom, leftRight, topBottom, leftRight);
            }
        }

        /**
         * Calculates appropriate font size for glyph buttons.
         */
        private Font calculateGlyphFont(boolean isLargeScreen, double width) {
            if (isLargeScreen) {
                return Font.font(GLYPH_FONT_SIZE_LARGE);
            } else {
                int sizeReduction = 2 * (int) ((1000 - width) / PADDING_ADJUSTMENT_FACTOR);
                return Font.font(Math.max(12, GLYPH_FONT_SIZE_LARGE - sizeReduction));
            }
        }

        /**
         * Calculates appropriate padding for glyph buttons.
         */
        private Insets calculateGlyphPadding(boolean isLargeScreen, double width) {
            if (isLargeScreen) {
                return new Insets(GLYPH_PADDING_TOP_BOTTOM_LARGE, GLYPH_PADDING_LEFT_RIGHT_LARGE,
                        GLYPH_PADDING_TOP_BOTTOM_LARGE, GLYPH_PADDING_LEFT_RIGHT_LARGE);
            } else {
                int sizeReduction = (int) ((1000 - width) / PADDING_ADJUSTMENT_FACTOR);
                int topBottom = Math.max(0, GLYPH_PADDING_BASE - sizeReduction);
                int leftRight = Math.max(0, GLYPH_PADDING_LEFT_RIGHT_LARGE - 2 * sizeReduction);
                return new Insets(topBottom, leftRight, topBottom, leftRight);
            }
        }

        /**
         * Applies calculated sizes and padding to all toolbar buttons.
         */
        private void applyButtonSizes(Font textFont, Insets textPadding, Font glyphFont,
                                      Insets glyphPadding, double width) {
            long buttonCount = toolbar.getChildren()
                    .stream()
                    .filter(node -> node instanceof Button)
                    .count();
            double compactWidth = Math.max(
                    24.0,
                    Math.min(48.0, (Math.max(280.0, width) - 12.0) / Math.max(1.0, buttonCount))
            );
            for (Node toolbarNode : toolbar.getChildren()) {
                if (toolbarNode instanceof Button button && !(toolbarNode instanceof ToolbarButton)) {
                    button.setFont(glyphFont);
                    button.setPadding(glyphPadding);
                    button.setMinWidth(Math.min(24.0, compactWidth));
                    button.setPrefWidth(Math.max(compactWidth, 34.0));
                    button.setMaxWidth(Math.max(compactWidth, 34.0));
                    button.setTextOverrun(OverrunStyle.CLIP);
                } else if (toolbarNode instanceof ToolbarButton toolbarButton) {
                    if (toolbarButton.duration != -1) {
                        // Text button (granularity)
                        toolbarButton.setStyle("-fx-font-size: %s".formatted(textFont.getSize()));
                        double textWidth = computeTextDimensions(toolbarButton.textLabel, textFont).getWidth();
                        double targetWidth = Math.min(compactWidth, textWidth + TEXT_BUTTON_WIDTH_PADDING);
                        toolbarButton.setMinWidth(Math.min(24.0, targetWidth));
                        toolbarButton.setPrefWidth(targetWidth);
                        toolbarButton.setMaxWidth(targetWidth);
                        toolbarButton.setPadding(textPadding);
                    } else {
                        // Glyph button (tool)
                        if (toolbarButton.graphicLabel != null) {
                            toolbarButton.graphicLabel.setFitHeight(glyphFont.getSize());
                            toolbarButton.graphicLabel.setFitWidth(glyphFont.getSize());
                        }
                        toolbarButton.setFont(glyphFont);
                        toolbarButton.setMinWidth(Math.min(24.0, compactWidth));
                        toolbarButton.setPrefWidth(compactWidth);
                        toolbarButton.setMaxWidth(compactWidth);
                        toolbarButton.setPadding(glyphPadding);
                    }
                }
            }
        }

        /**
         * Updates the padding of the separator between function and option buttons.
         */
        private void updateSeparatorPadding(boolean isLargeScreen, double width) {
            if (isLargeScreen) {
                functionOptionsSeparator.setPadding(new Insets( width,0, 0, 0));
            } else {
                functionOptionsSeparator.setPadding(new Insets(width, 2, 0, 0));
            }
        }
    }

    /**
     * Event filter that hides the options PopOver when the mouse exits it.
     * Includes a buffer zone around the PopOver to prevent flickering.
     */
    private class MouseExitedPopOverFilter implements EventHandler<MouseEvent> {
        private final Scene scene;

        MouseExitedPopOverFilter(Scene scene) {
            this.scene = Objects.requireNonNull(scene, "scene must not be null");
        }

        @Override
        public void handle(@NotNull MouseEvent event) {
            if (!isMouseOverPopOver(event) && !mouseInsideOptionsButton) {
                optionsPopOver.hide(Duration.seconds(0.25));
                scene.getWindow().removeEventFilter(MouseEvent.MOUSE_MOVED, this);
                mouseExitedPopOverFilter = null;
            }
        }

        /**
         * Checks if the mouse position is over the PopOver or its buffer zone.
         *
         * @param event the mouse event
         * @return true if the mouse is over the PopOver or buffer zone
         */
        private boolean isMouseOverPopOver(MouseEvent event) {
            double bufferZone = optionsPopOver.getWidth() * (POPOVER_BUFFER_PERCENTAGE / 100.0);
            double minX = optionsPopOver.getX() - bufferZone;
            double maxX = optionsPopOver.getX() + optionsPopOver.getWidth() + bufferZone;
            double minY = optionsPopOver.getY() - bufferZone;
            double maxY = optionsPopOver.getY() + optionsPopOver.getHeight() + bufferZone;

            return event.getScreenX() >= minX && event.getScreenX() <= maxX &&
                   event.getScreenY() >= minY && event.getScreenY() <= maxY;
        }
    }
}
