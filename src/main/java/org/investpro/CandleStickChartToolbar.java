package org.investpro;

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
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.util.Duration;
import org.investpro.chart.CandleStickChart;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.investpro.CandleStickChartToolbar.Tool.OPTIONS;
import static org.investpro.FXUtils.computeTextDimensions;
import static org.investpro.NavigationDirection.*;

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
 * </ul>
 * <p>
 * The toolbar buttons are labelled with either text (which is used for the duration buttons,
 * e.g. "6h") or a glyph (e.g. magnifying glasses with a plus/minus for zoom in/out).
 *
 * @author Michael Ennen
 */
public class CandleStickChartToolbar extends Region {
    private final HBox toolbar;
    private final PopOver optionsPopOver;
    private MouseExitedPopOverFilter mouseExitedPopOverFilter;
    private volatile boolean mouseInsideOptionsButton;
    private final Separator functionOptionsSeparator;

    CandleStickChartToolbar(ObservableNumberValue containerWidth, ObservableNumberValue containerHeight,
                            Set<Integer> granu) {
        Objects.requireNonNull(containerWidth);
        Objects.requireNonNull(containerHeight);
        Objects.requireNonNull(granu);

        List<Node> toolbarNodes = new ArrayList<>((2 * granu.size()) + Tool.values().length + 1);
        boolean passedMinuteHourBoundary = false;
        boolean passedHourDayBoundary = false;
        boolean passedDayWeekBoundary = false;
        boolean passedWeekMonthBoundary = false;
        for (Integer granularity : granu) {
            if (granularity < 60) {
                // For granularities less than 60 seconds, display seconds
                toolbarNodes.add(new ToolbarButton(granularity + "s", granularity));
            } else if (granularity < 3600) {
                // For granularities between 60 seconds and 1 hour, display minutes
                toolbarNodes.add(new ToolbarButton((granularity / 60) + "m", granularity));
            } else if (granularity < 86400) {
                // For granularities between 1 hour and 1 day, display hours
                if (!passedMinuteHourBoundary) {
                    passedMinuteHourBoundary = true;
                    Separator minuteHourSeparator = new Separator();
                    minuteHourSeparator.setOpacity(0);
                    toolbarNodes.add(minuteHourSeparator);
                }
                toolbarNodes.add(new ToolbarButton((granularity / 3600) + "h", granularity));
            } else if (granularity < 604800) {
                // For granularities between 1 day and 1 week, display days
                if (!passedHourDayBoundary) {
                    passedHourDayBoundary = true;
                    Separator hourDaySeparator = new Separator();
                    hourDaySeparator.setOpacity(0);
                    toolbarNodes.add(hourDaySeparator);
                }
                toolbarNodes.add(new ToolbarButton((granularity / 86400) + "d", granularity));
            } else if (granularity < 2592000) {
                // For granularities between 1 week and 1 month, display weeks
                if (!passedDayWeekBoundary) {
                    passedDayWeekBoundary = true;
                    Separator dayWeekSeparator = new Separator();
                    dayWeekSeparator.setOpacity(0);
                    toolbarNodes.add(dayWeekSeparator);
                }
                toolbarNodes.add(new ToolbarButton((granularity / 604800) + "w", granularity));
            } else {
                // For granularities greater than or equal to 1 month, display months
                if (!passedWeekMonthBoundary) {
                    passedWeekMonthBoundary = true;
                    Separator weekMonthSeparator = new Separator();
                    weekMonthSeparator.setOpacity(0);
                    toolbarNodes.add(weekMonthSeparator);
                }
                toolbarNodes.add(new ToolbarButton((granularity / 2592000) + "mo", granularity));
            }
        }

        Separator intervalZoomSeparator = new Separator();
        intervalZoomSeparator.setOpacity(0);
        toolbarNodes.add(intervalZoomSeparator);

        functionOptionsSeparator = new Separator();
        functionOptionsSeparator.setOpacity(0);
        functionOptionsSeparator.setPadding(new Insets(0, 20, 0, 0));

        optionsPopOver = new PopOver();
        optionsPopOver.setTitle("Chart Options");
        optionsPopOver.setHeaderAlwaysVisible(true);
        for (Tool tool : Tool.values()) {
            ToolbarButton toolbarButton;
            if (tool == OPTIONS) {
                toolbarNodes.add(functionOptionsSeparator);
                toolbarButton = new ToolbarButton(OPTIONS);
                toolbarButton.setOnMouseEntered(_ -> {
                    mouseInsideOptionsButton = true;
                    optionsPopOver.show(toolbarButton);
                });
                toolbarButton.setOnMouseExited(_ -> {
                    mouseInsideOptionsButton = false;
                    if (mouseExitedPopOverFilter == null) {
                        mouseExitedPopOverFilter = new MouseExitedPopOverFilter(getScene());
                        getScene().getWindow().addEventFilter(MouseEvent.MOUSE_MOVED, mouseExitedPopOverFilter);
                    }
                });
            } else {
                toolbarButton = new ToolbarButton(tool);
            }
            toolbarNodes.add(toolbarButton);
        }

        toolbar = new HBox();
        toolbar.getChildren().addAll(toolbarNodes);
        toolbar.getStyleClass().add("candle-chart-toolbar");

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

    void setActiveToolbarButton(IntegerProperty secondsPerCandle) {
        Objects.requireNonNull(secondsPerCandle);
        for (Node childNode : toolbar.getChildren()) {
            if (childNode instanceof ToolbarButton tool) {
                tool.setActive(secondsPerCandle.get() == tool.duration);
            }
        }
    }

    void registerEventHandlers(CandleStickChart candleStickChart, IntegerProperty secondsPerCandle) {
        Objects.requireNonNull(secondsPerCandle);

        for (Node childNode : toolbar.getChildren()) {
            if (childNode instanceof ToolbarButton tool) {
                if (tool.duration != -1) {
                    tool.setOnAction(_ -> secondsPerCandle.setValue(tool.duration));
                } else if (tool.tool != null && tool.tool.isZoomIn()) {
                    tool.setOnAction(_ -> candleStickChart.changeZoom(
                            tool.tool.getZoomDirection()));
                } else if (tool.tool != null && tool.tool.isZoomOut()) {
                    tool.setOnAction(_ -> candleStickChart.changeZoom(
                            tool.tool.getZoomDirection()));

                } else if (tool.tool != null && tool.tool.isFullScreen()) {
                    tool.setOnAction(_ -> candleStickChart.setFullScreen(true));

                } else if (tool.tool != null && tool.tool.isPrint_PDF()) {
                    tool.setOnAction(_ -> candleStickChart.exportAsPDF());
                } else if (tool.tool != null && tool.tool.isShare()) {
                    tool.setOnAction(_ -> candleStickChart.shareLink());
                } else if (tool.tool != null && tool.tool.isPrint()) {
                    tool.setOnAction(_ -> candleStickChart.print());
                } else if (tool.tool != null && tool.tool.isRefresh()) {
                    tool.setOnAction(_ -> candleStickChart.refreshChart());
                } else if (tool.tool != null && tool.tool.isAutoScroll()) {
                    tool.setOnAction(_ -> candleStickChart.setAutoScroll(!candleStickChart.isAutoScroll()));
                } else if (tool.tool != null && tool.tool.isGridToggle()) {
                    tool.setOnAction(_ -> candleStickChart.setShowGrid(!candleStickChart.isShowGrid()));
                } else if (tool.tool != null && tool.tool.isLeft()) {
                    tool.setOnAction(_ -> candleStickChart.changeNavigation(LEFT));
                } else if (tool.tool != null && tool.tool.isRight()) {
                    tool.setOnAction(_ -> candleStickChart.changeNavigation(RIGHT));
                } else if (tool.tool != null && tool.tool.isUp()) {
                    tool.setOnAction(_ -> candleStickChart.scroll(UP));
                } else if (tool.tool != null && tool.tool.isDown()) {
                    tool.setOnAction(_ -> candleStickChart.scroll(DOWN));
                } else if (tool.tool != null && tool.tool.isScreenshot()) {
                    tool.setOnAction(_ -> candleStickChart.captureScreenshot());
                } else if (tool.tool != null && tool.tool.isScrollUp()) {
                    tool.setOnAction(_ -> candleStickChart.scroll(UP));


                } else if (tool.tool != null && tool.tool.isScrollDown()) {
                    tool.setOnAction(_ -> candleStickChart.scroll(DOWN));

                } else if (tool.tool != null && tool.tool.isGRID_TOGGLE()) {
                    tool.setOnAction(_ -> candleStickChart.setShowGrid(true));

                } else if (tool.tool != null && tool.tool.isOPTIONS()) {
                    if (candleStickChart == null) {
                        return;
                    }
                    tool.setOnAction(_ -> optionsPopOver.show(tool));
                }


                tool.setOnMouseEntered(_ -> {
                    if (!mouseInsideOptionsButton) {
                        optionsPopOver.hide();
                    } else {
                        candleStickChart.setTooltip(new Tooltip());
                        optionsPopOver.show(tool);
                        mouseInsideOptionsButton = false;
                    }
                });

            }


        }
    }


    void setChartOptions(@NotNull CandleStickChartOptions chartOptions) {
        optionsPopOver.setContentNode(chartOptions.getOptionsPane());
    }

    public enum Tool {
        ZOOM_IN("/img/search-plus-solid.png"),
        ZOOM_OUT("/img/search-minus-solid.png"),
        PRINT("/img/print-solid.png"),
        OPTIONS("/img/options-solid.png"),
        REFRESH("/img/refresh-solid.png"),       // NEW: Refresh chart data
        SCREENSHOT("/img/camera-solid.png"),     // NEW: Save chart as image
        AUTO_SCROLL("/img/scroll-solid.png"),    // NEW: Toggle auto-scroll
        GRID_TOGGLE("/img/grid-solid.png"),      // NEW: Toggle grid visibility
        FULL_SCREEN("/img/fullscreen-solid.png"),// NEW: Toggle full-screen mode
        PRINT_PDF("/img/pdf-solid.png"),        // NEW: Export chart as PDF
        SHARE("/img/share-solid.png"),          // NEW: Share chart via link

        LEFT("/img/left-solid.png"), // Scroll  to the left
        RIGHT("/img/right-solid.png"), // Scroll  to the right
        UP("/img/up-solid.png"), // Scroll  up
        DOWN("/img/down-solid.png") // Scroll  down
        ;

        private final String img;

        Tool(String img) {
            this.img = img;
        }


        public @Nullable ZoomDirection getZoomDirection() {

            if (this == ZOOM_IN) {
                return ZoomIn();
            }
            if (this == ZOOM_OUT) {
                return ZoomOut();
            }
            return null;

        }

        private ZoomDirection ZoomOut() {
            return ZoomDirection.OUT; // Fix: Should zoom OUT, not IN
        }

        private ZoomDirection ZoomIn() {
            return ZoomDirection.IN;  // Fix: Should zoom IN, not OUT
        }

        public boolean isRefresh() {
            return this == REFRESH;
        }

        public boolean isNavigationFunction() {
            return this == LEFT || this == RIGHT;
        }

        public boolean isGRID_TOGGLE() {
            return this == GRID_TOGGLE;
        }




        public boolean isZoomIn() {
            return this == ZOOM_IN;
        }

        public boolean isZoomOut() {
            return this == ZOOM_OUT;
        }


        public boolean isShare() {
            return this == SHARE;
        }

        public boolean isFullScreen() {
            return this == FULL_SCREEN;
        }

        public boolean isPrint_PDF() {
            return this == PRINT_PDF;
        }

        public boolean isPrint() {
            return this == PRINT;
        }

        public boolean isScreenshot() {
            return this == SCREENSHOT;
        }

        public boolean isAutoScroll() {
            return this == AUTO_SCROLL;
        }

        public boolean isGridToggle() {
            return this == GRID_TOGGLE;
        }

        public boolean isLeft() {
            return this == LEFT;
        }

        public boolean isRight() {
            return this == RIGHT;
        }

        public boolean isUp() {
            return this == UP;
        }

        public boolean isDown() {
            return this == DOWN;
        }

        public boolean isScrollDown() {
            return this == DOWN;
        }

        public boolean isScrollUp() {
            return this == UP;
        }

        public boolean isOPTIONS() {
            return this == OPTIONS;
        }
    }


    private class SizeChangeListener extends DelayedSizeChangeListener {
        SizeChangeListener(BooleanProperty gotFirstSize, ObservableValue<Number> containerWidth,
                           ObservableValue<Number> containerHeight) {
            super(750, 300, gotFirstSize, containerWidth, containerHeight);
        }

        public void resize() {
            Font textFont = Font.font(containerWidth.getValue().doubleValue() >= 900 ? 14 : 13 -
                    (int) ((1000 - containerWidth.getValue().doubleValue()) / 100));
            int topBottomPadding = Math.max(0, 4 - (int) ((1000 - containerWidth.getValue().doubleValue()) / 100));
            int rightLeftPadding = Math.max(0, 8 - 2 * (int) ((1000 - containerWidth.getValue().doubleValue()) / 100));
            Insets textLabelPadding = containerWidth.getValue().doubleValue() >= 900 ? new Insets(4, 8, 4, 8) :
                    new Insets(topBottomPadding, rightLeftPadding, topBottomPadding, rightLeftPadding);
            Font glyphFont = Font.font(containerWidth.getValue().doubleValue() >= 900 ? 22 :
                    22 - (2 * (int) ((1000 - containerWidth.getValue().doubleValue()) / 100)));
            topBottomPadding = Math.max(0, 5 - (int) ((1000 - containerWidth.getValue().doubleValue()) / 100));
            rightLeftPadding = Math.max(0, 10 - 2 * (int) ((1000 - containerWidth.getValue().doubleValue()) / 100));
            Insets glyphLabelPadding = containerWidth.getValue().doubleValue() >= 900 ? new Insets(5, 10, 5, 10) :
                    new Insets(topBottomPadding, rightLeftPadding, topBottomPadding, rightLeftPadding);
            for (Node toolbarNode : toolbar.getChildren()) {
                if (toolbarNode instanceof ToolbarButton toolbarButton) {
                    if (toolbarButton.duration != -1) {
                        toolbarButton.setStyle("-fx-font-size: " + textFont.getSize());
                        toolbarButton.setPrefWidth(containerWidth.getValue().doubleValue() >= 900 ? -1 :
                                computeTextDimensions(toolbarButton.textLabel, textFont).getWidth() + 15);
                        toolbarButton.setPadding(textLabelPadding);
                    } else {
                        toolbarButton.graphicLabel.setFitHeight((int) glyphFont.getSize());
                        toolbarButton.graphicLabel.setFitWidth((int) glyphFont.getSize());
                        toolbarButton.setPadding(glyphLabelPadding);
                    }
                }
            }

            functionOptionsSeparator.setPadding(new Insets(0, containerWidth.getValue().doubleValue() >= 900 ? 20 :
                    20 - 2 * (int) ((1000 - containerWidth.getValue().doubleValue()) / 100), 0, 0));
        }
    }

    private class MouseExitedPopOverFilter implements EventHandler<MouseEvent> {
        private final Scene scene;

        MouseExitedPopOverFilter(Scene scene) {
            this.scene = scene;
        }

        @Override
        public void handle(MouseEvent event) {
            // TODO Maybe we should add a small buffer space to the popover, like 10%
            if (!(event.getScreenX() <= optionsPopOver.getX() + optionsPopOver.getWidth()
                    && event.getScreenX() >= optionsPopOver.getX()
                    && event.getScreenY() <= optionsPopOver.getY() + optionsPopOver.getHeight()
                    && event.getScreenY() >= optionsPopOver.getY())
                    && !mouseInsideOptionsButton) {
                optionsPopOver.hide(Duration.seconds(0.25));
                scene.getWindow().removeEventFilter(MouseEvent.MOUSE_MOVED, this);
                mouseExitedPopOverFilter = null;
            }
        }
    }

    private static class ToolbarButton extends Button {


        private final String textLabel;
        private final ImageView graphicLabel;
        private final Tool tool;
        private final int duration;
        private final PseudoClass activeClass = PseudoClass.getPseudoClass("active");
        private final BooleanProperty active = new BooleanPropertyBase(false) {
            public void invalidated() {
                pseudoClassStateChanged(activeClass, get());
            }

            @Override
            public Object getBean() {
                return ToolbarButton.this;
            }

            @Contract(pure = true)
            @Override
            public @NotNull String getName() {
                return "active";
            }
        };

        ToolbarButton(String textLabel, int duration) {
            this(textLabel, null, null, duration);
        }

        ToolbarButton(Tool tool) {
            this(null, tool, tool.img, -1);
        }

        private ToolbarButton(String textLabel, Tool tool, String img, int duration) {
            if (textLabel == null && img == null) {
                throw new IllegalArgumentException("textLabel and img were both null");
            }
            this.textLabel = textLabel;
            this.tool = tool;
            this.duration = duration;
            setText(textLabel == null ? "" : textLabel);

            if (img != null) {
                // Load the image
                graphicLabel = new ImageView(new Image(Objects.requireNonNull(ToolbarButton.class.getResourceAsStream(img))));

                // 🔹 Set fixed size for toolbar icons
                graphicLabel.setFitWidth(20); // Adjust icon width
                graphicLabel.setFitHeight(20); // Adjust icon height
                graphicLabel.setPreserveRatio(true); // Maintain aspect ratio

                setGraphic(graphicLabel);
            } else {
                graphicLabel = new ImageView(new Image(Objects.requireNonNull(
                        ToolbarButton.class.getResourceAsStream("/img/user.png")
                )));
            }

            // 🔹 Ensure buttons do not take excessive space
            setMinSize(30, 30);
            setMaxSize(40, 40);

            // 🔹 Set proper padding
            setPadding(new Insets(5, 5, 5, 5));

            textOverrunProperty().set(OverrunStyle.CLIP);
            setEllipsisString("");
            getStyleClass().add("candle-chart-toolbar-button");
        }

        public void setActive(boolean b) {
            if (b) {
                active.setValue(true);
                setStyle("-fx-background-color: #aa227f");
            } else {
                active.setValue(false);
                setStyle("");
            }

        }

    }
}