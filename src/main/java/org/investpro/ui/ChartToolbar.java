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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
import org.investpro.ui.charts.CandleStickChart;
import org.investpro.ui.charts.CandleStickChartOptions;
import org.investpro.utils.DelayedSizeChangeListener;
import org.investpro.utils.PopOver;
import org.investpro.utils.ZoomDirection;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.investpro.ui.ChartToolbar.Tool.OPTIONS;
import static org.investpro.utils.FXUtils.computeTextDimensions;

/**
 * Responsive chart toolbar for InvestPro candlestick charts.
 *
 * <p>The toolbar is exchange-agnostic and works with OANDA, crypto exchanges, and any adapter
 * that exposes candle granularities in seconds. It normalizes and sorts granularities so the UI
 * stays consistent across exchanges.
 */
public class ChartToolbar extends Region {

    private static final int MIN_BUTTON_SIZE = 28;
    private static final int LARGE_WIDTH_BREAKPOINT = 900;
    private static final int MEDIUM_WIDTH_BREAKPOINT = 680;

    private final HBox toolbar;
    private final PopOver optionsPopOver;
    private final Separator functionOptionsSeparator;

    private MouseExitedPopOverFilter mouseExitedPopOverFilter;
    private volatile boolean mouseInsideOptionsButton;

    ChartToolbar(ObservableNumberValue containerWidth,
                 ObservableNumberValue containerHeight,
                 Set<Integer> granularities) {
        Objects.requireNonNull(containerWidth, "containerWidth must not be null");
        Objects.requireNonNull(containerHeight, "containerHeight must not be null");
        Objects.requireNonNull(granularities, "granularities must not be null");

        this.optionsPopOver = new PopOver();
        this.optionsPopOver.setTitle("Chart Options");
        this.optionsPopOver.setHeaderAlwaysVisible(true);

        this.functionOptionsSeparator = new Separator();
        this.functionOptionsSeparator.setOpacity(0);
        this.functionOptionsSeparator.setPadding(new Insets(0, 18, 0, 0));

        this.toolbar = new HBox(6);
        this.toolbar.setAlignment(Pos.CENTER_LEFT);
        this.toolbar.getStyleClass().add("candle-chart-toolbar");
        this.toolbar.setFillHeight(true);

        List<Node> toolbarNodes = buildToolbarNodes(normalizeGranularities(granularities));
        toolbar.getChildren().setAll(toolbarNodes);

        getStyleClass().add("candle-chart-toolbar-wrapper");
        getChildren().setAll(toolbar);

        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);
        SizeChangeListener sizeListener = new SizeChangeListener(gotFirstSize, containerWidth, containerHeight);
        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);

        ChangeListener<Boolean> gotFirstSizeChangeListener = new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (Boolean.TRUE.equals(newValue)) {
                    sizeListener.resize();
                    gotFirstSize.removeListener(this);
                }
            }
        };
        gotFirstSize.addListener(gotFirstSizeChangeListener);
    }

    private List<Node> buildToolbarNodes(List<Integer> sortedGranularities) {
        List<Node> nodes = new ArrayList<>((sortedGranularities.size() * 2) + Tool.values().length + 4);

        GranularityBucket previousBucket = null;
        for (Integer granularity : sortedGranularities) {
            if (granularity == null || granularity <= 0) {
                continue;
            }

            GranularityBucket bucket = bucketFor(granularity);
            if (previousBucket != null && bucket != previousBucket) {
                nodes.add(invisibleSeparator(10));
            }
            previousBucket = bucket;

            nodes.add(new ToolbarButton(formatGranularity(granularity), granularity));
        }

        nodes.add(invisibleSeparator(14));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        nodes.add(spacer);

        for (Tool tool : Tool.values()) {
            ToolbarButton toolbarButton = new ToolbarButton(tool);

            if (tool == OPTIONS) {
                nodes.add(functionOptionsSeparator);
                configureOptionsButton(toolbarButton);
            }

            nodes.add(toolbarButton);
        }

        return nodes;
    }

    private List<Integer> normalizeGranularities(Set<Integer> granularities) {
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();

        for (Integer granularity : granularities) {
            if (granularity == null || granularity <= 0) {
                continue;
            }
            normalized.add(granularity);
        }

        // Safe defaults for OANDA + common crypto exchanges when an adapter returns nothing.
        if (normalized.isEmpty()) {
            normalized.addAll(List.of(60, 300, 900, 1800, 3600, 14400, 86400, 604800, 2592000));
        }

        return normalized.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private Separator invisibleSeparator(int rightPadding) {
        Separator separator = new Separator();
        separator.setOpacity(0);
        separator.setPadding(new Insets(0, rightPadding, 0, 0));
        return separator;
    }

    private String formatGranularity(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m";
        }
        if (seconds < 86400) {
            return (seconds / 3600) + "h";
        }
        if (seconds < 604800) {
            return (seconds / 86400) + "d";
        }
        if (seconds < 2592000) {
            return (seconds / 604800) + "w";
        }
        return (seconds / 2592000) + "mo";
    }

    private GranularityBucket bucketFor(int seconds) {
        if (seconds < 60) {
            return GranularityBucket.SECONDS;
        }
        if (seconds < 3600) {
            return GranularityBucket.MINUTES;
        }
        if (seconds < 86400) {
            return GranularityBucket.HOURS;
        }
        if (seconds < 604800) {
            return GranularityBucket.DAYS;
        }
        if (seconds < 2592000) {
            return GranularityBucket.WEEKS;
        }
        return GranularityBucket.MONTHS;
    }

    private void configureOptionsButton(ToolbarButton toolbarButton) {
        toolbarButton.setOnMouseEntered(event -> {
            mouseInsideOptionsButton = true;
            optionsPopOver.show(toolbarButton);
        });

        toolbarButton.setOnMouseExited(event -> {
            mouseInsideOptionsButton = false;
            Scene scene = getScene();
            if (scene == null || scene.getWindow() == null) {
                optionsPopOver.hide(Duration.seconds(0.25));
                return;
            }
            if (mouseExitedPopOverFilter == null) {
                mouseExitedPopOverFilter = new MouseExitedPopOverFilter(scene);
                scene.getWindow().addEventFilter(MouseEvent.MOUSE_MOVED, mouseExitedPopOverFilter);
            }
        });
    }

    @Override
    protected void layoutChildren() {
        toolbar.resizeRelocate(0, 0, getWidth(), getHeight());
    }

    @Override
    protected double computePrefHeight(double width) {
        return 42;
    }

    @Override
    protected double computeMinHeight(double width) {
        return 34;
    }

    @Override
    protected double computePrefWidth(double height) {
        return toolbar.prefWidth(height);
    }

    void setActiveToolbarButton(IntegerProperty secondsPerCandle) {
        Objects.requireNonNull(secondsPerCandle, "secondsPerCandle must not be null");

        for (Node childNode : toolbar.getChildren()) {
            if (childNode instanceof ToolbarButton tool) {
                tool.setActive(tool.duration != -1 && secondsPerCandle.get() == tool.duration);
            }
        }
    }

    void registerEventHandlers(CandleStickChart candleStickChart, IntegerProperty secondsPerCandle) {
        Objects.requireNonNull(candleStickChart, "candleStickChart must not be null");
        Objects.requireNonNull(secondsPerCandle, "secondsPerCandle must not be null");

        for (Node childNode : toolbar.getChildren()) {
            if (!(childNode instanceof ToolbarButton tool)) {
                continue;
            }

            if (tool.duration != -1) {
                tool.setOnAction(event -> {
                    secondsPerCandle.setValue(tool.duration);
                    setActiveToolbarButton(secondsPerCandle);
                });
                continue;
            }

            if (tool.tool == null) {
                continue;
            }

            switch (tool.tool) {
                case ZOOM_IN, ZOOM_OUT -> tool.setOnAction(event -> candleStickChart.changeZoom(tool.tool.getZoomDirection()));
                case PRINT -> tool.setOnAction(event -> candleStickChart.print());
                case SCREENSHOT -> tool.setOnAction(event -> candleStickChart.screenshot());
                case CROSSHAIR -> tool.setOnAction(event -> candleStickChart.toggleCrosshair());
                case AUTO_TRADE -> tool.setOnAction(event -> candleStickChart.autoTrade());
                case REFRESH -> tool.setOnAction(event -> candleStickChart.refreshChart());
                case OPTIONS -> {
                    // Popover is handled by mouse enter/exit.
                }
            }
        }
    }

    void setChartOptions(CandleStickChartOptions chartOptions) {
        Objects.requireNonNull(chartOptions, "chartOptions must not be null");
        optionsPopOver.setContentNode(chartOptions.getOptionsPane());
    }

    enum Tool {
        ZOOM_IN("/img/search-plus-solid.png", "+", "Zoom In"),
        ZOOM_OUT("/img/search-minus-solid.png", "−", "Zoom Out"),
        REFRESH("/img/refresh-solid.png", "⟳", "Refresh"),
        CROSSHAIR("/img/crosshairs-solid.png", "⌖", "Crosshair"),
        SCREENSHOT("/img/screenshot.png", "▣", "Screenshot"),
        AUTO_TRADE("/img/auto-trade-solid.png", "A", "Auto Trade"),
        PRINT("/img/print-solid.png", "⎙", "Print"),
        OPTIONS("/img/cog-solid.png", "⚙", "Options");

        private final String img;
        private final String fallbackText;
        private final String label;

        Tool(String img, String fallbackText, String label) {
            this.img = img;
            this.fallbackText = fallbackText;
            this.label = label;
        }

        boolean isZoomFunction() {
            return this == ZOOM_IN || this == ZOOM_OUT;
        }

        ZoomDirection getZoomDirection() {
            if (!isZoomFunction()) {
                throw new IllegalArgumentException("cannot call getZoomDirection() on non-zoom function: " + name());
            }
            return this == ZOOM_IN ? ZoomDirection.IN : ZoomDirection.OUT;
        }
    }

    private enum GranularityBucket {
        SECONDS,
        MINUTES,
        HOURS,
        DAYS,
        WEEKS,
        MONTHS
    }

    private class SizeChangeListener extends DelayedSizeChangeListener {
        SizeChangeListener(BooleanProperty gotFirstSize,
                           ObservableValue<Number> containerWidth,
                           ObservableValue<Number> containerHeight) {
            super(750, 300, gotFirstSize, containerWidth, containerHeight);
        }

        public void resize() {
            double width = Math.max(320, containerWidth.getValue().doubleValue());

            Font textFont;
            Font glyphFont;
            Insets textPadding;
            Insets glyphPadding;
            double gap;

            if (width >= LARGE_WIDTH_BREAKPOINT) {
                textFont = Font.font(13);
                glyphFont = Font.font(18);
                textPadding = new Insets(5, 10, 5, 10);
                glyphPadding = new Insets(6, 10, 6, 10);
                gap = 6;
            } else if (width >= MEDIUM_WIDTH_BREAKPOINT) {
                textFont = Font.font(12);
                glyphFont = Font.font(16);
                textPadding = new Insets(4, 8, 4, 8);
                glyphPadding = new Insets(5, 8, 5, 8);
                gap = 4;
            } else {
                textFont = Font.font(11);
                glyphFont = Font.font(14);
                textPadding = new Insets(3, 6, 3, 6);
                glyphPadding = new Insets(4, 6, 4, 6);
                gap = 3;
            }

            toolbar.setSpacing(gap);

            for (Node toolbarNode : toolbar.getChildren()) {
                if (!(toolbarNode instanceof ToolbarButton toolbarButton)) {
                    continue;
                }

                if (toolbarButton.duration != -1) {
                    toolbarButton.setStyle("-fx-font-size: " + textFont.getSize() + "px;");
                    toolbarButton.setPrefWidth(width >= LARGE_WIDTH_BREAKPOINT
                            ? Region.USE_COMPUTED_SIZE
                            : computeTextDimensions(toolbarButton.textLabel, textFont).getWidth() + 16);
                    toolbarButton.setMinHeight(MIN_BUTTON_SIZE);
                    toolbarButton.setPadding(textPadding);
                } else {
                    toolbarButton.setStyle("-fx-font-size: " + glyphFont.getSize() + "px;");
                    if (toolbarButton.graphicLabel != null) {
                        toolbarButton.graphicLabel.setFitHeight(glyphFont.getSize());
                        toolbarButton.graphicLabel.setFitWidth(glyphFont.getSize());
                    }
                    toolbarButton.setMinHeight(MIN_BUTTON_SIZE);
                    toolbarButton.setPadding(glyphPadding);
                }
            }

            functionOptionsSeparator.setPadding(new Insets(0, width >= LARGE_WIDTH_BREAKPOINT ? 18 : 8, 0, 0));
            requestLayout();
        }
    }

    private class MouseExitedPopOverFilter implements EventHandler<MouseEvent> {
        private final Scene scene;

        MouseExitedPopOverFilter(Scene scene) {
            this.scene = scene;
        }

        @Override
        public void handle(MouseEvent event) {
            if (scene == null || scene.getWindow() == null) {
                optionsPopOver.hide(Duration.seconds(0.25));
                mouseExitedPopOverFilter = null;
                return;
            }

            boolean insidePopover = event.getScreenX() <= optionsPopOver.getX() + optionsPopOver.getWidth()
                    && event.getScreenX() >= optionsPopOver.getX()
                    && event.getScreenY() <= optionsPopOver.getY() + optionsPopOver.getHeight()
                    && event.getScreenY() >= optionsPopOver.getY();

            if (!insidePopover && !mouseInsideOptionsButton) {
                optionsPopOver.hide(Duration.seconds(0.25));
                scene.getWindow().removeEventFilter(MouseEvent.MOUSE_MOVED, this);
                mouseExitedPopOverFilter = null;
            }
        }
    }

    private static class ToolbarButton extends Button {
        private static final PseudoClass ACTIVE_CLASS = PseudoClass.getPseudoClass("active");

        private final String textLabel;
        private final ImageView graphicLabel;
        private final Tool tool;
        private final int duration;

        ToolbarButton(String textLabel, int duration) {
            this(textLabel, null, null, duration);
        }

        ToolbarButton(Tool tool) {
            this(null, tool, tool.img, -1);
        }

        private ToolbarButton(String textLabel, Tool tool, String img, int duration) {
            if (textLabel == null && tool == null && img == null) {
                throw new IllegalArgumentException("textLabel, tool, and img were all null");
            }

            this.textLabel = textLabel;
            this.tool = tool;
            this.duration = duration;

            setText(textLabel == null ? "" : textLabel);

            ImageView loadedGraphic = null;
            if (img != null) {
                loadedGraphic = tryLoadIcon(img);
                if (loadedGraphic != null) {
                    setGraphic(loadedGraphic);
                } else if (tool != null) {
                    setText(tool.fallbackText);
                }
            }

            this.graphicLabel = loadedGraphic;

            setMinSize(MIN_BUTTON_SIZE, MIN_BUTTON_SIZE);
            setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            setFocusTraversable(false);
            setTextOverrun(OverrunStyle.CLIP);
            setEllipsisString("");
            getStyleClass().add("candle-chart-toolbar-button");
            setTooltip(new Tooltip(tool == null ? textLabel : tool.label));
        }

        private static ImageView tryLoadIcon(String img) {
            try {
                InputStream stream = ToolbarButton.class.getResourceAsStream(img);
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
