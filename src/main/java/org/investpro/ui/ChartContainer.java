package org.investpro.ui;

import javafx.animation.FadeTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.print.PrinterJob;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.data.ReverseRawTradeDataProcessor;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;
import org.investpro.ui.charts.CandleStickChart;
import org.investpro.ui.charts.VolumeIndicatorPanel;
import org.investpro.indicators.ChartIndicator;
import org.investpro.ui.theme.MonitoringTheme;
import org.investpro.utils.CandleAggregator;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.PopOver;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Professional chart container for displaying candlestick charts with
 * interactive controls.
 * <p>
 * Responsibilities:
 * - Hosts a CandleStickChart with proper lifecycle management.
 * - Provides timeframe selection and toolbar controls.
 * - Rebuilds the chart when timeframe changes.
 * - Exposes callbacks so TradingWindow/SystemCore can own bot execution.
 * - Supports screenshot, print, refresh, and disposal.
 */
@Getter
@Setter
@Slf4j
public class ChartContainer extends Region {
    private static final int DEFAULT_SECONDS_PER_CANDLE = 3600;
    private static final String DEFAULT_TIMEFRAME = "1h";

    private static final double MIN_WIDTH = 350;
    private static final double MIN_HEIGHT = 350;
    private static final double TOOLBAR_HEIGHT = 46;
    private static final double PREF_WIDTH = 900;
    private static final double PREF_HEIGHT = 600;

    private static final String CONTAINER_STYLE_CLASS = "candle-chart-container";
    private static final String ROOT_STYLE_CLASS = "chart-container-root";
    private static final String TOOLBAR_CONTAINER_CLASS = "chart-toolbar-container";
    private static final String CHART_HOST_CLASS = "chart-host";
    private static final String TIMEFRAME_LABEL_CLASS = "chart-timeframe-label";
    private static final String TIMEFRAME_SELECTOR_CLASS = "chart-timeframe-selector";

    private static final String ROOT_STYLE = "-fx-border-color: #263246; "
            + "-fx-border-width: 1; "
            + "-fx-background-color: #0a0e17;";

    private static final String TOOLBAR_CONTAINER_STYLE = "-fx-background-color: #101827; "
            + "-fx-border-color: #263246; "
            + "-fx-border-width: 0 0 1 0;";

    private static final String CHART_HOST_STYLE = "-fx-background-color: #0a0e17;";

    private static final String TIMEFRAME_LABEL_STYLE = "-fx-text-fill: #9aa7ba; "
            + "-fx-font-size: 12px; "
            + "-fx-font-weight: bold;";

    private static final String TIMEFRAME_SELECTOR_STYLE = "-fx-background-color: #0f1724; "
            + "-fx-border-color: #2a3548; "
            + "-fx-border-radius: 3; "
            + "-fx-background-radius: 3; "
            + "-fx-text-fill: #e5edf7; "
            + "-fx-font-size: 12px; "
            + "-fx-padding: 3 6;";

    private static final int FADE_OUT_DURATION = 180;
    private static final int FADE_IN_DURATION = 220;
    private static final DateTimeFormatter SNAPSHOT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final double TOOLBAR_SPACING = 10;
    private static final double TOOLBAR_PADDING = 7;
    private static final double TOOLBAR_PADDING_HORIZONTAL = 10;

    private final Exchange exchange;
    private TradePair tradePair;
    private final boolean liveSyncing;
    private final TradingService tradingService;
    private final String telegramToken;

    private final SimpleIntegerProperty secondsPerCandle = new SimpleIntegerProperty(DEFAULT_SECONDS_PER_CANDLE);
    private String selectedTimeframe = DEFAULT_TIMEFRAME;

    private final AnchorPane root = new AnchorPane();
    private final HBox toolbarContainer = new HBox(TOOLBAR_SPACING);
    private final VBox candleChartContainer = new VBox();
    private final ComboBox<String> timeframeSelector = new ComboBox<>(
            FXCollections.observableArrayList(CandleAggregator.getSupportedTimeframes()));

    private ChartToolbar toolbar;
    private CandleStickChart candleStickChart;
    private EnhancedChartDisplay enhancedChartDisplay;
    private Set<Integer> supportedGranularity;

    private boolean useEnhancedDisplay = true; // Feature flag to enable/disable enhanced display
    private Consumer<String> onChartError;
    private Runnable onChartCreated;
    private Runnable onChartDisposed;
    private Runnable onAutoTradeAction;

    private File lastScreenshotDirectory;
    private boolean disposed;
    private boolean timeframeListenerRegistered;

    public ChartContainer(
            Exchange exchange,
            TradePair tradePair,
            boolean liveSyncing,
            String telegramToken,
            TradingService tradingService) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.tradePair = Objects.requireNonNull(tradePair, "tradePair must not be null");
        this.liveSyncing = liveSyncing;
        this.telegramToken = telegramToken == null ? "" : telegramToken.trim();
        this.tradingService = tradingService;

        initialize();
    }

    private void initialize() {
        getStyleClass().add(CONTAINER_STYLE_CLASS);
        setMinSize(MIN_WIDTH, MIN_HEIGHT);
        setPrefSize(PREF_WIDTH, PREF_HEIGHT);

        configureRoot();
        configureToolbar();
        configureChartHost();

        getChildren().setAll(root);

        try {
            createInitialChart();
            registerTimeframeListener();
            log.info("ChartContainer initialized successfully for {}", tradePair);
        } catch (Exception exception) {
            handleChartError("Failed to initialize chart container: " + rootMessage(exception));
        }
    }

    private void configureRoot() {
        root.prefWidthProperty().bind(widthProperty());
        root.prefHeightProperty().bind(heightProperty());
        root.getStyleClass().add(ROOT_STYLE_CLASS);
        root.setStyle(ROOT_STYLE);
        root.getChildren().setAll(toolbarContainer, candleChartContainer);

        AnchorPane.setTopAnchor(toolbarContainer, 0.0);
        AnchorPane.setLeftAnchor(toolbarContainer, 0.0);
        AnchorPane.setRightAnchor(toolbarContainer, 0.0);

        AnchorPane.setTopAnchor(candleChartContainer, TOOLBAR_HEIGHT);
        AnchorPane.setLeftAnchor(candleChartContainer, 0.0);
        AnchorPane.setRightAnchor(candleChartContainer, 0.0);
        AnchorPane.setBottomAnchor(candleChartContainer, 0.0);
    }

    private void configureToolbar() {
        toolbarContainer.getStyleClass().add(TOOLBAR_CONTAINER_CLASS);
        toolbarContainer.setAlignment(Pos.CENTER_LEFT);
        toolbarContainer.setPadding(new Insets(
                TOOLBAR_PADDING,
                TOOLBAR_PADDING_HORIZONTAL,
                TOOLBAR_PADDING,
                TOOLBAR_PADDING_HORIZONTAL));
        toolbarContainer.setMinHeight(TOOLBAR_HEIGHT);
        toolbarContainer.setPrefHeight(TOOLBAR_HEIGHT);
        toolbarContainer.setMaxHeight(TOOLBAR_HEIGHT);
        toolbarContainer.setStyle(TOOLBAR_CONTAINER_STYLE);

        try {
            CandleDataSupplier defaultSupplier = buildCandleDataSupplier(secondsPerCandle.get());
            supportedGranularity = Set.copyOf(defaultSupplier.getSupportedGranularities());

            Separator functionOptionsSeparator = new Separator(Orientation.VERTICAL);
            PopOver optionsPopOver = new PopOver();
            optionsPopOver.setTitle("Options");
            optionsPopOver.setHeaderAlwaysVisible(true);

            toolbar = new ChartToolbar(
                    candleChartContainer.widthProperty(),
                    candleChartContainer.heightProperty(),
                    optionsPopOver,
                    functionOptionsSeparator);

            HBox.setHgrow(toolbar, Priority.ALWAYS);
            toolbar.setMaxWidth(Double.MAX_VALUE);

            toolbarContainer.getChildren().setAll(
                    timeframeLabel(),
                    configuredTimeframeSelector(),
                    new Separator(Orientation.VERTICAL),
                    toolbar,
                    spacer());

        } catch (Exception exception) {
            log.warn("Failed to configure full toolbar, using fallback", exception);
            toolbarContainer.getChildren().setAll(
                    timeframeLabel(),
                    configuredTimeframeSelector(),
                    spacer());
        }
    }

    private @NotNull Label timeframeLabel() {
        Label label = new Label("TF:");
        label.getStyleClass().add(TIMEFRAME_LABEL_CLASS);
        label.setStyle(TIMEFRAME_LABEL_STYLE);
        return label;
    }

    private ComboBox<String> configuredTimeframeSelector() {
        if (timeframeSelector.getItems().isEmpty()) {
            timeframeSelector.getItems().setAll(CandleAggregator.TIMEFRAME_SECONDS.keySet());
        }

        if (!timeframeSelector.getItems().contains(selectedTimeframe)) {
            selectedTimeframe = DEFAULT_TIMEFRAME;
        }

        timeframeSelector.setValue(selectedTimeframe);
        timeframeSelector.setPrefWidth(90);
        timeframeSelector.setMinWidth(90);
        timeframeSelector.getStyleClass().add(TIMEFRAME_SELECTOR_CLASS);
        timeframeSelector.setStyle(TIMEFRAME_SELECTOR_STYLE);

        timeframeSelector.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!disposed && newValue != null && !Objects.equals(oldValue, newValue)) {
                applyTimeframe(newValue);
            }
        });

        return timeframeSelector;
    }

    private @NotNull Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void configureChartHost() {
        candleChartContainer.getStyleClass().add(CHART_HOST_CLASS);
        candleChartContainer.setPadding(new Insets(0));
        candleChartContainer.setFillWidth(true);
        candleChartContainer.setStyle(CHART_HOST_STYLE);
        candleChartContainer.widthProperty().addListener((observable, oldValue, newValue) -> requestLayout());
        candleChartContainer.heightProperty().addListener((observable, oldValue, newValue) -> requestLayout());
    }

    private void createInitialChart() {
        try {
            CandleStickChart chart = buildChart(secondsPerCandle.get());
            installChart(chart, false);
            log.debug("Initial chart created successfully");
        } catch (SQLException | ClassNotFoundException exception) {
            handleChartError("Failed to create initial chart: " + exception.getMessage());
            throw new RuntimeException("Failed to create initial candle chart", exception);
        }
    }

    private void registerTimeframeListener() {
        if (timeframeListenerRegistered) {
            return;
        }

        secondsPerCandle.addListener((observable, oldValue, newValue) -> {
            if (!disposed && newValue != null && !Objects.equals(oldValue, newValue)) {
                recreateChartForTimeframe(newValue.intValue());
            }
        });

        timeframeListenerRegistered = true;
    }

    private void applyTimeframe(String timeframe) {
        if (disposed) {
            return;
        }

        if (!CandleAggregator.isValidTimeframe(timeframe)) {
            log.warn("Invalid timeframe selected: {}", timeframe);
            return;
        }

        Integer seconds = CandleAggregator.TIMEFRAME_SECONDS.get(timeframe);
        if (seconds == null || seconds <= 0) {
            handleChartError("Invalid timeframe seconds: " + seconds);
            return;
        }

        selectedTimeframe = timeframe;

        if (secondsPerCandle.get() != seconds) {
            secondsPerCandle.set(seconds);
        }
    }

    private void recreateChartForTimeframe(int durationSeconds) {
        if (disposed) {
            return;
        }

        try {
            log.debug("Recreating chart for timeframe: {}s", durationSeconds);
            CandleStickChart newChart = buildChart(durationSeconds);
            installChart(newChart, true);
        } catch (SQLException | ClassNotFoundException exception) {
            handleChartError("Failed to create chart for duration " + durationSeconds + "s: " + exception.getMessage());
        } catch (RuntimeException exception) {
            handleChartError("Failed to recreate chart: " + rootMessage(exception));
        }
    }

    @Contract("_ -> new")
    private @NotNull CandleStickChart buildChart(int durationSeconds) throws SQLException, ClassNotFoundException {
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("secondsPerCandle must be positive but was: " + durationSeconds);
        }

        return new CandleStickChart(
                exchange,
                buildCandleDataSupplier(durationSeconds),
                tradePair,
                liveSyncing,
                durationSeconds,
                telegramToken,
                tradingService,
                candleChartContainer.widthProperty(),
                candleChartContainer.heightProperty());
    }

    private @NotNull CandleDataSupplier buildCandleDataSupplier(int durationSeconds) {
        Path rawTradeDataPath = configuredRawTradeDataPath();

        if (rawTradeDataPath != null) {
            try {
                log.info("Using ReverseRawTradeDataProcessor for {}", rawTradeDataPath);
                return new ReverseRawTradeDataProcessor(rawTradeDataPath, durationSeconds, tradePair, exchange);
            } catch (IOException exception) {
                log.warn(
                        "Unable to use raw trade data processor; falling back to exchange supplier: {}",
                        exception.getMessage(),
                        exception);
            }
        }

        CandleDataSupplier supplier = exchange.getCandleDataSupplier(durationSeconds, tradePair);
        if (supplier == null) {
            throw new IllegalStateException("Exchange returned null CandleDataSupplier for " + tradePair);
        }
        return supplier;
    }

    private @Nullable Path configuredRawTradeDataPath() {
        String configuredPath = System.getProperty("investpro.rawTradeData", "");
        if (configuredPath == null || configuredPath.isBlank()) {
            configuredPath = System.getenv("INVESTPRO_RAW_TRADE_DATA");
        }

        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }

        Path path = Path.of(configuredPath.trim());
        return Files.isRegularFile(path) ? path : null;
    }

    private void installChart(CandleStickChart newChart, boolean animated) {
        if (disposed) {
            disposeChart(newChart);
            return;
        }

        Objects.requireNonNull(newChart, "newChart must not be null");

        CandleStickChart oldChart = candleStickChart;
        candleStickChart = newChart;

        VBox.setVgrow(newChart, Priority.ALWAYS);
        newChart.prefWidthProperty().bind(candleChartContainer.widthProperty());
        newChart.prefHeightProperty().bind(candleChartContainer.heightProperty());

        registerToolbar(newChart);

        // Install enhanced display if enabled
        if (useEnhancedDisplay) {
            installEnhancedDisplay(newChart, oldChart, animated);
        } else {
            // Install basic chart
            if (!animated || oldChart == null) {
                disposeChart(oldChart);
                candleChartContainer.getChildren().setAll(newChart);
                newChart.setOpacity(1.0);
                executeOnChartCreated();
                return;
            }

            performFadeTransitionBasic(oldChart, newChart);
        }
    }

    private void installEnhancedDisplay(CandleStickChart newChart, CandleStickChart oldChart, boolean animated) {
        if (disposed) {
            return;
        }

        try {
            // Create or update enhanced display
            if (enhancedChartDisplay != null && !animated) {
                enhancedChartDisplay.dispose();
            }

            enhancedChartDisplay = new EnhancedChartDisplay(exchange, tradePair, newChart, tradingService);

            // Wire chart header and volume data from all candles
            List<CandleData> allCandles = newChart.getAllCandleData();
            if (allCandles != null && !allCandles.isEmpty()) {
                CandleData latestCandle = allCandles.getLast();

                // Update chart header with latest candle data
                double change = latestCandle.closePrice() - latestCandle.openPrice();
                double changePercent = (change / latestCandle.openPrice()) * 100;
                enhancedChartDisplay.updateHeader(
                        tradePair.toSlashSymbol(),
                        latestCandle.closePrice(),
                        change,
                        changePercent,
                        latestCandle.openPrice(),
                        latestCandle.highPrice(),
                        latestCandle.lowPrice(),
                        latestCandle.closePrice(),
                        latestCandle.volume());

                // Wire volume bars for all candles
                for (int i = 0; i < allCandles.size(); i++) {
                    CandleData candle = allCandles.get(i);
                    boolean isUpCandle = candle.closePrice() >= candle.openPrice();
                    VolumeIndicatorPanel.VolumeBar volumeBar = new VolumeIndicatorPanel.VolumeBar(
                            candle.volume(),
                            isUpCandle,
                            i,
                            candle.openTime() * 1000L, // Convert seconds to milliseconds
                            "V: " + String.format("%.2f", candle.volume()));
                    enhancedChartDisplay.addVolumeBar(volumeBar);
                }

                // Update technical indicators with actual calculated values
                List<ChartIndicator> indicators = newChart.getIndicators();
                if (indicators != null) {
                    for (ChartIndicator indicator : indicators) {
                        if (indicator.isCalculated()) {
                            String formattedValue = formatIndicatorValue(indicator);
                            String signalColor = getIndicatorSignalColor(indicator);
                            enhancedChartDisplay.updateIndicator(indicator.getName(), formattedValue, signalColor);
                        }
                    }
                }

                // Update volume panel MA20
                enhancedChartDisplay.updateVolumeMA();
            }

            // Wire toolbar settings
            enhancedChartDisplay.getProfessionalToolbar().setDrawingToolsEnabled(true);
            if (timeframeSelector.getValue() != null) {
                enhancedChartDisplay.getProfessionalToolbar().setTimeframe(timeframeSelector.getValue());
            }

            // Setup toolbar zoom callbacks - wire to actual chart zoom methods
            enhancedChartDisplay.getProfessionalToolbar().setOnZoomIn(() -> {
                if (!disposed) {
                    newChart.zoomIn();
                }
            });
            enhancedChartDisplay.getProfessionalToolbar().setOnZoomOut(() -> {
                if (!disposed) {
                    newChart.zoomOut();
                }
            });
            enhancedChartDisplay.getProfessionalToolbar().setOnZoomReset(() -> {
                if (!disposed) {
                    newChart.resetZoom();
                }
            });

            // Setup timeframe change callback
            enhancedChartDisplay.getProfessionalToolbar().setOnTimeframeChanged(this::applyTimeframe);

            if (!animated || oldChart == null) {
                disposeChart(oldChart);
                candleChartContainer.getChildren().setAll(enhancedChartDisplay);
                enhancedChartDisplay.setOpacity(1.0);
                executeOnChartCreated();
                return;
            }

            // Fade transition for enhanced display
            performFadeTransitionEnhanced(oldChart, enhancedChartDisplay);
        } catch (Exception e) {
            log.error("Failed to install enhanced display, falling back to basic chart", e);
            useEnhancedDisplay = false;
            installChart(newChart, animated);
        }
    }

    private void performFadeTransitionBasic(CandleStickChart oldChart, CandleStickChart newChart) {
        if (oldChart == null) {
            candleChartContainer.getChildren().setAll(newChart);
            executeOnChartCreated();
            return;
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(FADE_OUT_DURATION), oldChart);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        fadeOut.setOnFinished(event -> {
            disposeChart(oldChart);

            if (disposed) {
                disposeChart(newChart);
                return;
            }

            candleChartContainer.getChildren().setAll(newChart);
            newChart.setOpacity(0.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(FADE_IN_DURATION), newChart);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setOnFinished(done -> executeOnChartCreated());
            fadeIn.play();
        });

        fadeOut.play();
    }

    private void performFadeTransitionEnhanced(CandleStickChart oldChart, EnhancedChartDisplay enhancedDisplay) {
        if (oldChart == null) {
            candleChartContainer.getChildren().setAll(enhancedDisplay);
            enhancedDisplay.setOpacity(0.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(FADE_IN_DURATION), enhancedDisplay);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setOnFinished(done -> executeOnChartCreated());
            fadeIn.play();
            return;
        }

        javafx.scene.Node currentDisplay = !candleChartContainer.getChildren().isEmpty()
                ? candleChartContainer.getChildren().getFirst()
                : null;

        if (currentDisplay == null) {
            candleChartContainer.getChildren().setAll(enhancedDisplay);
            enhancedDisplay.setOpacity(1.0);
            executeOnChartCreated();
            return;
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(FADE_OUT_DURATION), currentDisplay);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        fadeOut.setOnFinished(event -> {
            disposeChart(oldChart);
            if (disposed) {
                enhancedDisplay.dispose();
                return;
            }

            candleChartContainer.getChildren().setAll(enhancedDisplay);
            enhancedDisplay.setOpacity(0.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(FADE_IN_DURATION), enhancedDisplay);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setOnFinished(done -> executeOnChartCreated());
            fadeIn.play();
            performFadeTransition(oldChart, candleStickChart);
        });

        fadeOut.play();
    }

    private void performFadeTransition(CandleStickChart oldChart, CandleStickChart newChart) {
        if (oldChart == null) {
            candleChartContainer.getChildren().setAll(newChart);
            newChart.setOpacity(0.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(FADE_IN_DURATION), newChart);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setOnFinished(done -> executeOnChartCreated());
            fadeIn.play();
            return;
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(FADE_OUT_DURATION), oldChart);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        fadeOut.setOnFinished(event -> {
            disposeChart(oldChart);

            if (disposed) {
                disposeChart(newChart);
                return;
            }

            candleChartContainer.getChildren().setAll(newChart);
            newChart.setOpacity(0.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(FADE_IN_DURATION), newChart);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setOnFinished(done -> executeOnChartCreated());
            fadeIn.play();
        });

        fadeOut.play();
    }

    private void registerToolbar(CandleStickChart chart) {
        if (toolbar == null || chart == null) {
            return;
        }

        try {
            toolbar.registerEventHandlers(chart, secondsPerCandle);
            toolbar.setChartOptions(chart.getChartOptions());
            toolbar.setActiveToolbarButton(secondsPerCandle);
            toolbar.setOnScreenshotAction(this::saveChartSnapshot);
            toolbar.setOnPrintAction(this::printChart);
            toolbar.setOnAutoTradeAction(() -> {
                if (onAutoTradeAction != null) {
                    onAutoTradeAction.run();
                } else {
                    chart.autoTrade();
                }
            });
        } catch (Exception exception) {
            handleChartError("Failed to register toolbar handlers: " + rootMessage(exception));
        }
    }

    private void saveChartSnapshot() {
        if (candleStickChart == null) {
            handleChartError("No chart is available to capture.");
            return;
        }

        try {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Select Folder to Save Screenshot");

            if (lastScreenshotDirectory != null && lastScreenshotDirectory.exists()) {
                directoryChooser.setInitialDirectory(lastScreenshotDirectory);
            } else {
                directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
            }

            File selectedDirectory = directoryChooser.showDialog(
                    candleStickChart.getScene() == null ? null : candleStickChart.getScene().getWindow());

            if (selectedDirectory == null) {
                log.info("Screenshot save cancelled by user");
                return;
            }

            lastScreenshotDirectory = selectedDirectory;

            String filename = "InvestPro-%s-%s.png".formatted(
                    tradePair.toString('-'),
                    SNAPSHOT_FORMAT.format(LocalDateTime.now()));

            File output = new File(selectedDirectory, filename);
            WritableImage image = candleStickChart.snapshot(null, null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", output);

            log.info("Chart screenshot saved to {}", output.getAbsolutePath());
        } catch (IOException exception) {
            handleChartError("Failed to save chart screenshot: " + exception.getMessage());
            log.error("Screenshot save error", exception);
        }
    }

    private void printChart() {
        if (candleStickChart == null) {
            handleChartError("No chart is available to print.");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            handleChartError("No printer is available.");
            return;
        }

        boolean printed = job.printPage(candleStickChart);
        if (printed) {
            job.endJob();
            log.info("Chart sent to printer.");
        } else {
            handleChartError("Chart print was cancelled or failed.");
        }
    }

    private void disposeChart(CandleStickChart chart) {
        if (chart == null) {
            return;
        }

        try {
            chart.prefWidthProperty().unbind();
            chart.prefHeightProperty().unbind();
            chart.dispose();
            executeOnChartDisposed();
            log.debug("Chart disposed successfully");
        } catch (Exception exception) {
            log.warn("Exception during chart disposal: {}", exception.getMessage(), exception);
        }
    }

    private void handleChartError(String errorMessage) {
        String message = errorMessage == null || errorMessage.isBlank()
                ? "Unknown chart error"
                : errorMessage;

        log.error(message);

        if (onChartError != null) {
            try {
                onChartError.accept(message);
            } catch (Exception exception) {
                log.warn("Exception in onChartError callback", exception);
            }
        }
    }

    private void executeOnChartCreated() {
        if (onChartCreated != null) {
            try {
                onChartCreated.run();
            } catch (Exception exception) {
                log.warn("Exception in onChartCreated callback: {}", exception.getMessage(), exception);
            }
        }
    }

    private void executeOnChartDisposed() {
        if (onChartDisposed != null) {
            try {
                onChartDisposed.run();
            } catch (Exception exception) {
                log.warn("Exception in onChartDisposed callback: {}", exception.getMessage(), exception);
            }
        }
    }

    @Override
    protected void layoutChildren() {
        double width = Math.max(MIN_WIDTH, getWidth());
        double height = Math.max(MIN_HEIGHT, getHeight());
        root.resizeRelocate(0, 0, width, height);
    }

    @Override
    protected double computeMinWidth(double height) {
        return MIN_WIDTH;
    }

    @Override
    protected double computeMinHeight(double width) {
        return MIN_HEIGHT;
    }

    @Override
    protected double computePrefWidth(double height) {
        return PREF_WIDTH;
    }

    @Override
    protected double computePrefHeight(double width) {
        return PREF_HEIGHT;
    }

    public CandleStickChart getChart() {
        return candleStickChart;
    }

    public int getSecondsPerCandle() {
        return secondsPerCandle.get();
    }

    public void setSecondsPerCandle(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("seconds must be positive but was: " + seconds);
        }

        if (secondsPerCandle.get() != seconds) {
            secondsPerCandle.set(seconds);
        }

        String matchingTimeframe = CandleAggregator.TIMEFRAME_SECONDS.entrySet()
                .stream()
                .filter(entry -> Objects.equals(entry.getValue(), seconds))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (matchingTimeframe != null && !Objects.equals(timeframeSelector.getValue(), matchingTimeframe)) {
            selectedTimeframe = matchingTimeframe;
            timeframeSelector.setValue(matchingTimeframe);
        }
    }

    public void setTimeframe(String timeframe) {
        applyTimeframe(timeframe);
    }

    public void setTradePair(@NotNull TradePair tradePair) {
        this.tradePair = Objects.requireNonNull(tradePair, "tradePair must not be null");
        refreshChart();
    }

    public void refreshChart() {
        if (disposed) {
            return;
        }

        try {
            recreateChartForTimeframe(secondsPerCandle.get());
        } catch (Exception exception) {
            handleChartError("Chart refresh failed: " + rootMessage(exception));
        }
    }

    public void dispose() {
        if (disposed) {
            return;
        }

        disposed = true;
        disposeChart(candleStickChart);
        candleStickChart = null;
        candleChartContainer.getChildren().clear();
        toolbarContainer.getChildren().clear();
        log.info("ChartContainer disposed");
    }

    /**
     * Sets the callback to be invoked when a candlestick is clicked.
     *
     * @param callback the callback to handle candle selection
     */
    public void setCandleSelectionCallback(Consumer<CandleData> callback) {
        if (candleStickChart != null) {
            candleStickChart.setCandleSelectionCallback(callback);
        }
    }

    public void setOnAutoTradeAction(Runnable callback) {
        this.onAutoTradeAction = callback;
        if (toolbar != null && candleStickChart != null) {
            registerToolbar(candleStickChart);
        }
    }

    /**
     * Backward-compatible overload for older malformed calls.
     * Prefer setOnAutoTradeAction(Runnable).
     */
    @Deprecated
    public void setOnAutoTradeAction(Object callback) {
        if (callback == null) {
            this.onAutoTradeAction = null;
            return;
        }

        if (callback instanceof Runnable runnable) {
            setOnAutoTradeAction(runnable);
            return;
        }

        throw new IllegalArgumentException("Auto-trade callback must be a Runnable");
    }

    private @NotNull String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }

        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    /**
     * Format indicator value for display in TechnicalIndicatorsPanel
     */
    private String formatIndicatorValue(ChartIndicator indicator) {
        if (indicator == null || !indicator.isCalculated()) {
            return "—";
        }

        try {
            Map<String, double[]> values = indicator.getValues();
            if (values.isEmpty()) {
                return "—";
            }

            // Get the last value from the first line (most indicators have one primary
            // line)
            double[] lastLineValues = values.values().iterator().next();
            if (lastLineValues == null || lastLineValues.length == 0) {
                return "—";
            }

            // Get the last calculated value (most recent)
            double lastValue = lastLineValues[lastLineValues.length - 1];

            if (Double.isNaN(lastValue) || Double.isInfinite(lastValue)) {
                return "—";
            }

            String indicatorName = indicator.getName().toUpperCase();

            // Format based on indicator type
            if (indicatorName.contains("RSI")) {
                return String.format("%.1f", lastValue); // RSI: 0-100
            } else if (indicatorName.contains("STOCH")) {
                return String.format("%.1f", lastValue); // Stochastic: 0-100
            } else if (indicatorName.contains("MACD") || indicatorName.contains("ATR") ||
                    indicatorName.contains("BAND") || indicatorName.contains("VOLATILITY")) {
                return String.format("%.4f", lastValue); // Price-like values
            } else if (indicatorName.contains("MA") || indicatorName.contains("SMA") ||
                    indicatorName.contains("EMA") || indicatorName.contains("VWAP")) {
                return String.format("%.2f", lastValue); // Price values
            } else if (indicatorName.contains("VOLUME") || indicatorName.contains("OBV")) {
                return String.format("%.0f", lastValue); // Volume: integer
            } else {
                return String.format("%.4f", lastValue); // Default precision
            }
        } catch (Exception exception) {
            log.debug("Failed to format indicator value for {}", indicator.getName(), exception);
            return "—";
        }
    }

    /**
     * Determine signal color for indicator based on value thresholds
     */
    private String getIndicatorSignalColor(ChartIndicator indicator) {
        if (indicator == null || !indicator.isCalculated()) {
            return "#9ca3af"; // Gray for invalid
        }

        try {
            Map<String, double[]> values = indicator.getValues();
            if (values.isEmpty()) {
                return "#9ca3af";
            }

            double[] lastLineValues = values.values().iterator().next();
            if (lastLineValues == null || lastLineValues.length == 0) {
                return "#9ca3af";
            }

            double lastValue = lastLineValues[lastLineValues.length - 1];
            String indicatorName = indicator.getName().toUpperCase();

            // RSI signal colors (0-100 scale)
            if (indicatorName.contains("RSI")) {
                if (lastValue >= 70) {
                    return "#F44336"; // Red: Overbought
                } else if (lastValue <= 30) {
                    return "#4CAF50"; // Green: Oversold (bullish)
                } else {
                    return "#2196F3"; // Blue: Neutral
                }
            }

            // Stochastic signal colors (0-100 scale)
            if (indicatorName.contains("STOCH")) {
                if (lastValue >= 80) {
                    return "#F44336"; // Red: Overbought
                } else if (lastValue <= 20) {
                    return "#4CAF50"; // Green: Oversold
                } else {
                    return "#2196F3"; // Blue: Neutral
                }
            }

            // MACD colors (positive/negative)
            if (indicatorName.contains("MACD")) {
                return lastValue > 0 ? "#4CAF50" : "#F44336"; // Green if positive, red if negative
            }

            // Default color based on positive/negative
            if (lastValue > 0) {
                return "#4CAF50"; // Green for positive
            } else if (lastValue < 0) {
                return "#F44336"; // Red for negative
            } else {
                return "#2196F3"; // Blue for zero/neutral
            }
        } catch (Exception exception) {
            log.debug("Failed to determine signal color for {}", indicator.getName(), exception);
            return "#9ca3af";
        }
    }
}
