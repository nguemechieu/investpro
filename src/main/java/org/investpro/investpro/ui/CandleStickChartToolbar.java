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
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.investpro.investpro.CandleStickChartOptions;
import org.investpro.investpro.PopOver;
import org.investpro.investpro.SizeChangeListener;
import org.investpro.investpro.ZoomDirection;
import org.investpro.investpro.ui.charts.CandleStickChart;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.investpro.investpro.NavigationDirection.DOWN;
import static org.investpro.investpro.NavigationDirection.UP;

public class CandleStickChartToolbar extends Region {
    private final FlowPane toolbar;
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

        List<Integer> granularities = gran.stream().sorted().toList();
        List<Node> toolbarNodes = new ArrayList<>((2 * granularities.size()) + Tool.values().length + 1);
        boolean passedMinuteHourBoundary = false;
        boolean passedHourDayBoundary = false;
        boolean passedDayWeekBoundary = false;
        boolean passedWeekMonthBoundary = false;
        for (Integer granularity : granularities) {
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
        optionsPopOver.setTitle("Chart & Indicators");
        optionsPopOver.setHeaderAlwaysVisible(true);
        for (Tool tool : Tool.values()) {
            ToolbarButton toolbarButton;
            if (tool == Tool.OPTIONS) {
                toolbarNodes.add(functionOptionsSeparator);
                toolbarButton = new ToolbarButton(Tool.OPTIONS);
                toolbarButton.setOnMouseEntered(event -> {
                    mouseInsideOptionsButton = true;
                    toolbarButton.graphicLabel = new ImageView();
                    toolbarButton.textLabel = Tool.OPTIONS.name();

                    if (toolbarButton.tool == null)
                        return;
                    optionsPopOver.show(toolbarButton);
                });
                toolbarButton.setOnMouseExited(event -> {
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

        toolbar = new FlowPane();
        toolbar.setHgap(8);
        toolbar.setVgap(8);
        toolbar.setPadding(new Insets(6, 10, 6, 10));
        toolbar.getChildren().addAll(toolbarNodes);
        toolbar.getStyleClass().add("candle-chart-toolbar");
        toolbar.prefWrapLengthProperty().bind(widthProperty().subtract(20));
        toolbar.setMaxWidth(Double.MAX_VALUE);

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
                    tool.setOnAction(event -> secondsPerCandle.setValue(tool.duration));
                } else if (tool.tool != null) {
                    switch (tool.tool) {
                        case ZOOM_IN, ZOOM_OUT ->
                                tool.setOnAction(event -> candleStickChart.changeZoom(tool.tool.getZoomDirection()));
                        case FULL_SCREEN -> tool.setOnAction(event -> candleStickChart.toggleFullScreen());
                        case PRINT_PDF -> tool.setOnAction(event -> candleStickChart.exportAsPDF());
                        case SHARE -> tool.setOnAction(event -> candleStickChart.shareLink());
                        case PRINT -> tool.setOnAction(event -> candleStickChart.print());
                        case REFRESH -> tool.setOnAction(event -> candleStickChart.refreshChart());
                        case AUTO_SCROLL -> tool.setOnAction(event -> candleStickChart.jumpToLatestCandle());
                        case GRID_TOGGLE ->
                                tool.setOnAction(event -> candleStickChart.setShowGrid(!candleStickChart.isShowGrid()));
                        case LEFT -> tool.setOnAction(event -> candleStickChart.scroll(org.investpro.investpro.NavigationDirection.LEFT));
                        case RIGHT -> tool.setOnAction(event -> candleStickChart.scroll(org.investpro.investpro.NavigationDirection.RIGHT));
                        case UP, SCROLL_UP -> tool.setOnAction(event -> candleStickChart.scroll(UP));
                        case DOWN, SCROLL_DOWN -> tool.setOnAction(event -> candleStickChart.scroll(DOWN));
                        case SCREENSHOT -> tool.setOnAction(event -> candleStickChart.captureScreenshot());
                        case OPTIONS -> tool.setOnAction(event -> optionsPopOver.show(tool));
                        case TRADE -> tool.setOnAction(event -> candleStickChart.showTradeTicket());
                        case AI_TOGGLE -> {
                            tool.setActive(candleStickChart.isAiTradingEnabled());
                            tool.setOnAction(event -> {
                                candleStickChart.toggleAiTrading();
                                tool.setActive(candleStickChart.isAiTradingEnabled());
                            });
                        }
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
        TRADE(null, "Trade"),
        AI_TOGGLE(null, "AI"),
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
        private final String label;

        Tool(String img) {
            this(img, null);
        }

        Tool(String img, String label) {
            this.img = img;
            this.label = label;
        }

        public @Nullable ZoomDirection getZoomDirection() {
            return this == ZOOM_IN ? ZoomDirection.IN : this == ZOOM_OUT ? ZoomDirection.OUT : null;
        }

        public String getLabel() {
            return label == null ? name().replace('_', ' ') : label;
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
            String effectiveTextLabel = textLabel;
            if (effectiveTextLabel == null && tool != null && img == null) {
                effectiveTextLabel = tool.getLabel();
            }
            if (effectiveTextLabel == null && img == null) {
                throw new IllegalArgumentException("textLabel and img were both null");
            }
            this.textLabel = effectiveTextLabel;
            this.tool = tool;
            this.duration = duration;
            setText(effectiveTextLabel == null ? "" : effectiveTextLabel);

            if (img != null) {
                graphicLabel = new ImageView(new Image(Objects.requireNonNull(ToolbarButton.class.getResourceAsStream(img))));
                graphicLabel.setFitWidth(20);
                graphicLabel.setFitHeight(20);
                graphicLabel.setPreserveRatio(true);
                setGraphic(graphicLabel);
            } else {
                graphicLabel = new ImageView();
            }

            if (effectiveTextLabel != null && img == null) {
                setMinHeight(30);
                setPrefWidth(Math.max(52, effectiveTextLabel.length() * 12));
                setMaxWidth(Region.USE_COMPUTED_SIZE);
            } else {
                setMinSize(30, 30);
                setMaxSize(40, 40);
            }
            setPadding(new Insets(5, 5, 5, 5));
            textOverrunProperty().set(OverrunStyle.CLIP);
            setEllipsisString("");
            getStyleClass().add("candle-chart-toolbar-button");

            if (tool != null) {
                Tooltip.install(this, new Tooltip(tool.getLabel()));
            } else if (effectiveTextLabel != null) {
                Tooltip.install(this, new Tooltip("Switch to " + effectiveTextLabel + " candles"));
            }
        }

        public void setActive(boolean b) {
            active.setValue(b);
            setStyle(b
                    ? "-fx-background-color: linear-gradient(to bottom, #34d399, #0f766e); -fx-text-fill: #f8fafc; -fx-border-color: rgba(255,255,255,0.18);"
                    : "");
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
