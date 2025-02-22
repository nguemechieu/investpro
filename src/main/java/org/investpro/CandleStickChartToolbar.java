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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static org.investpro.FXUtils.computeTextDimensions;

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
@Getter
@Setter
public class CandleStickChartToolbar extends Region {
    /**
     * Supported granularity matching OANDA's standard (Seconds, Minutes, Hours, Days, Weeks, Months)
     */
    private static final List<Integer> SUPPORTED_GRANULARITY = Arrays.asList(
            5, 10, 15, 30, 60, 120, 240, 300, 600, 900, 1800, 3600, 7200, 10800, 14400, 21600, 28800, 43200,
            86400, 604800, 2592000
    );
    private HBox toolbar;
    private MouseExitedPopOverFilter mouseExitedPopOverFilter;
    private volatile boolean mouseInsideOptionsButton;
    private PopOver optionsPopOver;
    private Separator functionOptionsSeparator;
    CandleStickChartToolbar(ObservableNumberValue containerWidth, ObservableNumberValue containerHeight,
                            Set<Integer> granularities) {
        Objects.requireNonNull(containerWidth);
        Objects.requireNonNull(containerHeight);
        Objects.requireNonNull(granularities);

        List<Node> toolbarNodes = new ArrayList<>((2 * granularities.size()) + Tool.values().length + 1);
        boolean passedMinuteHourBoundary = false;
        boolean passedHourDayBoundary = false;
        boolean passedDayWeekBoundary = false;
        boolean passedWeekMonthBoundary = false;

        // Ensure we only include supported granularity
        List<Integer> sortedGranularity = new ArrayList<>(granularities);
        sortedGranularity.retainAll(SUPPORTED_GRANULARITY);
        Collections.sort(sortedGranularity);

        for (Integer granularity : sortedGranularity) {
            if (granularity < 3600) {  // Seconds and Minutes
                toolbarNodes.add(new ToolbarButton(granularityToLabel(granularity), granularity));
            } else if (granularity < 86400) { // Hours
                if (!passedMinuteHourBoundary) {
                    passedMinuteHourBoundary = true;
                    toolbarNodes.add(new Separator());
                }
                toolbarNodes.add(new ToolbarButton(granularityToLabel(granularity), granularity));
            } else if (granularity < 604800) { // Days
                if (!passedHourDayBoundary) {
                    passedHourDayBoundary = true;
                    toolbarNodes.add(new Separator());
                }
                toolbarNodes.add(new ToolbarButton(granularityToLabel(granularity), granularity));
            } else if (granularity < 2592000) { // Weeks
                if (!passedDayWeekBoundary) {
                    passedDayWeekBoundary = true;
                    toolbarNodes.add(new Separator());
                }
                toolbarNodes.add(new ToolbarButton(granularityToLabel(granularity), granularity));
            } else { // Months
                if (!passedWeekMonthBoundary) {
                    passedWeekMonthBoundary = true;
                    toolbarNodes.add(new Separator());
                }
                toolbarNodes.add(new ToolbarButton(granularityToLabel(granularity), granularity));
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
            if (tool == Tool.OPTIONS) {
                toolbarNodes.add(functionOptionsSeparator);
                toolbarButton = new ToolbarButton(Tool.OPTIONS);
                toolbarButton.setOnMouseEntered(_ -> {
                    mouseInsideOptionsButton = true;


                    if (optionsPopOver.isShowing() && toolbarButton.graphicLabel == null) {
                        optionsPopOver.hide();

                        return;
                    }
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
                } else if (tool.tool != null && tool.tool.isZoomFunction()) {
                    tool.setOnAction(_ -> candleStickChart.changeZoom(
                            tool.tool.getZoomDirection()));
                } else if (
                        tool.tool != null && tool.tool.isNavigationFunction()) {
                    tool.setOnAction(_ -> candleStickChart.changeNavigation(tool.tool.getNavigationDirection()));
                } else if (tool.tool != null && tool.tool.isDisplayFunction()) {
                    tool.setOnAction(_ -> {
                        tool.tool.displayChart(
                                candleStickChart.getData(),
                                candleStickChart.getXAxis(),
                                candleStickChart.getYAxis(),
                                candleStickChart.getYAxis2());

                    });
                } else if (tool.tool != null && tool.tool.isExportFunction()) {
                    tool.setOnAction(_ -> tool.tool.exportChart(candleStickChart));
                }


                tool.setOnMouseEntered(_ -> {
                    if (!mouseInsideOptionsButton) {
                        optionsPopOver.hide();
                    }
                });
            }

        }
    }

    void setChartOptions(@NotNull CandleStickChartOptions chartOptions) {
        optionsPopOver.setContentNode(chartOptions.getOptionsPane());
    }

    /**
     * Converts OANDA-supported granularities into appropriate labels for UI.
     */

    private static @NotNull String granularityToLabel(int actualGranularity) {
        if (actualGranularity < 60) {
            return "s" + actualGranularity;  // Seconds
        } else if (actualGranularity < 3600) {
            return "M" + (actualGranularity / 60);  // Minutes
        } else if (actualGranularity < 86400) {
            return "H" + (actualGranularity / 3600);  // Hours
        } else if (actualGranularity < 604800) {
            return "D";  // Days
        } else if (actualGranularity < 2592000) {
            return "W";  // Weeks (W1, W2, etc.)
        } else {
            return "Mo";  // Months
        }
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
        EXPORT_PDF("/img/pdf-solid.png"),        // NEW: Export chart as PDF
        SHARE("/img/share-solid.png");           // NEW: Share chart via link

        private final String img;

        Tool(String img) {
            this.img = img;
        }

        public String getImgPath() {
            return img;
        }

        public boolean isZoomFunction() {
            return this == ZOOM_IN || this == ZOOM_OUT;
        }

        public ZoomDirection getZoomDirection() {
            if (!isZoomFunction()) {
                throw new IllegalArgumentException("Cannot call getZoomDirection() on non-zoom function: " + name());
            }
            return this == ZOOM_IN ? ZoomDirection.IN : ZoomDirection.OUT;
        }

        public boolean isNavigationFunction() {
            return this == AUTO_SCROLL || this == REFRESH;
        }

        public boolean isExportFunction() {
            return this == SCREENSHOT || this == EXPORT_PDF || this == PRINT || this == SHARE;
        }

        public boolean isDisplayFunction() {
            return this == GRID_TOGGLE || this == FULL_SCREEN;
        }


        public void exportChart(CandleStickChart candleStickChart) {
            // Export chart as specified in the enum
            // Example usage:
            if (this == EXPORT_PDF) {
                //     // Export chart as PDF
                //...
                System.out.println("Export chart as PDF");
                candleStickChart.exportAsPDF();
            } else if (this == SCREENSHOT) {
                //     // Save chart as image
                //...
                System.out.println("Save chart as image");
                candleStickChart.captureScreenshot();
            } else if (this == PRINT) {
                //     // Print chart
                //...
                System.out.println("Print chart");
                candleStickChart.print();
            } else if (this == SHARE) {
                //     // Share chart via link
                //...
                System.out.println("Share chart via link");
                candleStickChart.shareLink();
            } else {
                throw new IllegalArgumentException("Cannot call exportChart() on non-export function: " + name());
            }

        }

        public Object getNavigationDirection() {
            if (!isNavigationFunction()) {
                throw new IllegalArgumentException("Cannot call getNavigationDirection() on non-navigation function: " + name());
            }
            return this == AUTO_SCROLL ? ScrollDirection.UP : ScrollDirection.DOWN;
        }

        public void displayChart(NavigableMap<Integer, CandleData> data, StableTicksAxis xAxis, StableTicksAxis yAxis, Object yAxis2) {
            // Display chart with provided data, xAxis, yAxis, and yAxis2 (optional)
            // Example usage:
            if (this == REFRESH) {
                //     // Update chart with new data
                //     //...
                System.out.println("Update chart with new data");
            } else if (this == EXPORT_PDF) {
                //     // Export chart as PDF
                //...
                System.out.println("Export chart as PDF");
            } else if (this == SCREENSHOT) {
                //     // Save chart as image
                //...
                System.out.println("Save chart as image");
            } else if (this == FULL_SCREEN) {
                //     // Toggle full-screen mode
                //...
                System.out.println("Toggle full-screen mode");
            } else if (this == GRID_TOGGLE) {
                //     // Toggle grid visibility
                //...
                System.out.println("Toggle grid visibility");
            } else if (this == AUTO_SCROLL) {
                //     // Toggle auto-scroll
                //...
                System.out.println("Toggle auto-scroll");
            } else {
                throw new IllegalArgumentException("Unsupported function: " + name());
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
            this(tool.name(), tool, tool.img, -1);
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
                graphicLabel = new ImageView(new Image(Objects.requireNonNull(ToolbarButton.class.getResourceAsStream(img))));
                setGraphic(graphicLabel);
            } else {
                graphicLabel = new ImageView(new Image(Objects.requireNonNull(
                        ToolbarButton.class.getResourceAsStream("/img/refresh-solid.png"))));

            }
            setMinSize(5, 5);
            setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            textOverrunProperty().set(OverrunStyle.CLIP);
            setEllipsisString("");
            getStyleClass().add("candle-chart-toolbar-button");
        }

        public void setActive(boolean active) {
            this.active.set(active);
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
}