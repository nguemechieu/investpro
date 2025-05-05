package org.investpro.investpro.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableNumberValue;
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
import javafx.util.Duration;
import org.investpro.investpro.CandleStickChartOptions;
import org.investpro.investpro.PopOver;
import org.investpro.investpro.SizeChangeListener;
import org.investpro.investpro.ZoomDirection;
import org.investpro.investpro.ui.chart.CandleStickChart;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.investpro.investpro.NavigationDirection.DOWN;
import static org.investpro.investpro.NavigationDirection.UP;
import static org.investpro.investpro.ui.CandleStickChartToolbar.Tool.LEFT;
import static org.investpro.investpro.ui.CandleStickChartToolbar.Tool.RIGHT;

public class CandleStickChartToolbar extends Region {
    private final HBox toolbar;
    private final PopOver optionsPopOver;
    Separator functionOptionsSeparator;
    private MouseExitedPopOverFilter mouseExitedPopOverFilter;
    private volatile boolean mouseInsideOptionsButton;

    public CandleStickChartToolbar(ObservableNumberValue containerWidth,
                                   ObservableNumberValue containerHeight,
                                   Set<Integer> gran) {
        Objects.requireNonNull(containerWidth);
        Objects.requireNonNull(containerHeight);
        Objects.requireNonNull(gran);

        List<Node> toolbarNodes = new ArrayList<>((2 * gran.size()) + Tool.values().length + 1);
        boolean passedMinuteHourBoundary = false;
        boolean passedHourDayBoundary = false;
        boolean passedDayWeekBoundary = false;
        boolean passedWeekMonthBoundary = false;
        for (Integer granularity : gran) {
            if (granularity < 60) {
                toolbarNodes.add(new ToolbarButton(granularity + "s", granularity));
            } else if (granularity < 3600) {
                toolbarNodes.add(new ToolbarButton((granularity / 60) + "m", granularity));
            } else if (granularity < 86400) {
                if (!passedMinuteHourBoundary) {
                    passedMinuteHourBoundary = true;
                    toolbarNodes.add(new Separator());
                }
                toolbarNodes.add(new ToolbarButton((granularity / 3600) + "h", granularity));
            } else if (granularity < 604800) {
                if (!passedHourDayBoundary) {
                    passedHourDayBoundary = true;
                    toolbarNodes.add(new Separator());
                }
                toolbarNodes.add(new ToolbarButton((granularity / 86400) + "d", granularity));
            } else if (granularity < 2592000) {
                if (!passedDayWeekBoundary) {
                    passedDayWeekBoundary = true;
                    toolbarNodes.add(new Separator());
                }
                toolbarNodes.add(new ToolbarButton((granularity / 604800) + "w", granularity));
            } else {
                if (!passedWeekMonthBoundary) {
                    passedWeekMonthBoundary = true;
                    toolbarNodes.add(new Separator());
                }
                toolbarNodes.add(new ToolbarButton((granularity / 2592000) + "mo", granularity));
            }
        }

        toolbarNodes.add(new Separator());

        functionOptionsSeparator = new Separator();
        functionOptionsSeparator.setOpacity(0);
        functionOptionsSeparator.setPadding(new Insets(0, 20, 0, 0));

        optionsPopOver = new PopOver();
        optionsPopOver.setTitle("Options");
        optionsPopOver.setHeaderAlwaysVisible(true);
        for (Tool tool : Tool.values()) {
            ToolbarButton toolbarButton;
            if (tool == Tool.OPTIONS) {
                toolbarNodes.add(functionOptionsSeparator);
                toolbarButton = new ToolbarButton(Tool.OPTIONS);
                toolbarButton.setOnMouseEntered(_ -> {
                    mouseInsideOptionsButton = true;
                    toolbarButton.graphicLabel = new ImageView();
                    toolbarButton.textLabel = Tool.OPTIONS.name();

                    if (toolbarButton.tool == null)
                        return;
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
        gotFirstSize.addListener((obs, oldVal, newVal) -> {
            sizeListener.resize();

            // gotFirstSize.removeListener();
        });

        getChildren().setAll(toolbar);
    }

    public void setActiveToolbarButton(IntegerProperty secondsPerCandle) {
        Objects.requireNonNull(secondsPerCandle);
        for (Node childNode : toolbar.getChildren()) {
            if (childNode instanceof ToolbarButton tool) {
                tool.setActive(secondsPerCandle.get() == tool.duration);
            }
        }
    }

    public void registerEventHandlers(CandleStickChart candleStickChart, IntegerProperty secondsPerCandle) {
        Objects.requireNonNull(secondsPerCandle);

        for (Node childNode : toolbar.getChildren()) {
            if (childNode instanceof ToolbarButton tool) {
                if (tool.duration != -1) {
                    tool.setOnAction(_ -> secondsPerCandle.setValue(tool.duration));
                } else if (tool.tool != null) {
                    switch (tool.tool) {
                        case ZOOM_IN, ZOOM_OUT ->
                                tool.setOnAction(_ -> candleStickChart.changeZoom(tool.tool.getZoomDirection()));
                        case FULL_SCREEN -> tool.setOnAction(_ -> candleStickChart.setFullScreen(true));
                        case PRINT_PDF -> tool.setOnAction(_ -> candleStickChart.exportAsPDF());
                        case SHARE -> tool.setOnAction(_ -> candleStickChart.shareLink());
                        case PRINT -> tool.setOnAction(_ -> candleStickChart.print());
                        case GRID_TOGGLE ->
                                tool.setOnAction(_ -> candleStickChart.setShowGrid(!candleStickChart.isShowGrid()));
                        case LEFT -> tool.setOnAction(_ -> candleStickChart.changeNavigation(LEFT));
                        case RIGHT -> tool.setOnAction(_ -> candleStickChart.changeNavigation(RIGHT));
                        case UP, SCROLL_UP -> tool.setOnAction(_ -> candleStickChart.scroll(UP));
                        case DOWN, SCROLL_DOWN -> tool.setOnAction(_ -> candleStickChart.scroll(DOWN));
                        case SCREENSHOT -> tool.setOnAction(_ -> candleStickChart.captureScreenshot());
                        case OPTIONS -> tool.setOnAction(_ -> optionsPopOver.show(tool));
////                        case RSI_TOGGLE -> tool.setOnAction(_ -> candleStickChart.toggleRSI());
//                        case MACD_TOGGLE -> tool.setOnAction(_ -> candleStickChart.toggleMACD());
//                        case BB_TOGGLE -> tool.setOnAction(_ -> candleStickChart.toggleBollingerBands());
                    }
                }
            }
        }
    }

    public void setChartOptions(@NotNull CandleStickChartOptions chartOptions) {
        optionsPopOver.setContentNode(chartOptions.getOptionsPane());
    }

    public enum Tool {
        ZOOM_IN("/img/search-plus-solid.png"),
        ZOOM_OUT("/img/search-minus-solid.png"),
        PRINT("/img/print-solid.png"),
        OPTIONS("/img/options-solid.png"),
        REFRESH("/img/refresh-solid.png"),
        SCREENSHOT("/img/camera-solid.png"),
        AUTO_SCROLL("/img/scroll-solid.png"),
        GRID_TOGGLE("/img/grid-solid.png"),
        FULL_SCREEN("/img/fullscreen-solid.png"),
        PRINT_PDF("/img/pdf-solid.png"),
        SHARE("/img/share-solid.png"),
        LEFT("/img/left-solid.png"),
        RIGHT("/img/right-solid.png"),
        UP("/img/up-solid.png"),
        DOWN("/img/down-solid.png"),
        SCROLL_UP("/img/up-solid.png"),
        SCROLL_DOWN("/img/down-solid.png");
//
//        // New indicator toggles
//        RSI_TOGGLE("/img/rsi-icon.png"),
//        MACD_TOGGLE("/img/macd-icon.png"),
//        BB_TOGGLE("/img/bollinger-icon.png");

        private final String img;

        Tool(String img) {
            this.img = img;
        }

        public @Nullable ZoomDirection getZoomDirection() {
            return this == ZOOM_IN ? ZoomDirection.IN : this == ZOOM_OUT ? ZoomDirection.OUT : null;
        }
    }

    public static class ToolbarButton extends Button {
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
        String textLabel;
        ImageView graphicLabel;

        public ToolbarButton(String textLabel, int duration) {
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
                graphicLabel = new ImageView(new Image(Objects.requireNonNull(ToolbarButton.class.getResourceAsStream(img))));
                graphicLabel.setFitWidth(20);
                graphicLabel.setFitHeight(20);
                graphicLabel.setPreserveRatio(true);
                setGraphic(graphicLabel);
            } else {
                graphicLabel = new ImageView();
            }

            setMinSize(30, 30);
            setMaxSize(40, 40);
            setPadding(new Insets(5, 5, 5, 5));
            textOverrunProperty().set(OverrunStyle.CLIP);
            setEllipsisString("");
            getStyleClass().add("candle-chart-toolbar-button");
        }

        public void setActive(boolean b) {
            active.setValue(b);
            setStyle(b ? "-fx-background-color: #16dda1" : "");
        }
    }

    private class MouseExitedPopOverFilter implements EventHandler<MouseEvent> {
        private final Scene scene;

        MouseExitedPopOverFilter(Scene scene) {
            this.scene = scene;
        }

        @Override
        public void handle(@NotNull MouseEvent event) {
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
