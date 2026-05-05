package org.investpro.ui;

import javafx.animation.FadeTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
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
import javafx.embed.swing.SwingFXUtils;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;
import lombok.Getter;
import org.investpro.data.ReverseRawTradeDataProcessor;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;
import org.investpro.ui.charts.CandleStickChart;
import org.investpro.utils.CandleAggregator;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.PopOver;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Professional chart container for displaying candlestick charts with interactive controls.
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 * <li>Hosts the CandleStickChart with proper lifecycle management</li>
 * <li>Provides interactive toolbar controls for timeframe selection and chart manipulation</li>
 * <li>Supports seamless timeframe switching with smooth transitions</li>
 * <li>Manages chart recreation when timeframe or trading pair changes</li>
 * <li>Keeps chart stretched to available workspace dynamically</li>
 * <li>Handles resource cleanup and disposal properly</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * ChartContainer container = new ChartContainer(exchange, tradePair, true);
 * container.setOnChartError(error -> System.err.println("Chart error: " + error));
 * container.setSecondsPerCandle(3600); // 1 hour
 * }</pre>
 *
 * <h3>Features:</h3>
 * <ul>
 * <li>Responsive design adapting to container resize</li>
 * <li>Smooth fade transitions when switching charts</li>
 * <li>Comprehensive error handling with callbacks</li>
 * <li>Professional styling with dark theme</li>
 * <li>Live candle syncing support</li>
 * </ul>
 *
 * Designed to be placed inside TradingWindow chart tabs.
 *
 * @see ChartToolbar
 * @see CandleStickChart
 */
@Getter
public class ChartContainer extends Region {
    private static final Logger LOGGER = Logger.getLogger(ChartContainer.class.getName());

    // Layout constants
    private static final int DEFAULT_SECONDS_PER_CANDLE = 3600;
    private static final String DEFAULT_TIMEFRAME = "1h";
    private static final double MIN_WIDTH = 350;
    private static final double MIN_HEIGHT = 350;
    private static final double TOOLBAR_HEIGHT = 46;
    private static final double PREF_WIDTH = 900;
    private static final double PREF_HEIGHT = 600;

    // Styling constants
    private static final String CONTAINER_STYLE_CLASS = "candle-chart-container";
    private static final String ROOT_STYLE_CLASS = "chart-container-root";
    private static final String TOOLBAR_CONTAINER_CLASS = "chart-toolbar-container";
    private static final String CHART_HOST_CLASS = "chart-host";
    private static final String TIMEFRAME_LABEL_CLASS = "chart-timeframe-label";
    private static final String TIMEFRAME_SELECTOR_CLASS = "chart-timeframe-selector";

    private static final String ROOT_STYLE = "-fx-border-color: #263246; " +
            "-fx-border-width: 1; " +
            "-fx-background-color: #0a0e17;";
    private static final String TOOLBAR_CONTAINER_STYLE = "-fx-background-color: #101827; " +
            "-fx-border-color: #263246; " +
            "-fx-border-width: 0 0 1 0;";
    private static final String CHART_HOST_STYLE = "-fx-background-color: #0a0e17;";
    private static final String TIMEFRAME_LABEL_STYLE = "-fx-text-fill: #9aa7ba; " +
            "-fx-font-size: 12px; " +
            "-fx-font-weight: bold;";
    private static final String TIMEFRAME_SELECTOR_STYLE = "-fx-background-color: #0f1724; " +
            "-fx-border-color: #2a3548; " +
            "-fx-border-radius: 3; " +
            "-fx-background-radius: 3; " +
            "-fx-text-fill: #e5edf7; " +
            "-fx-font-size: 12px; " +
            "-fx-padding: 3 6;";

    // Transition durations
    private static final int FADE_OUT_DURATION = 180;
    private static final int FADE_IN_DURATION = 220;
    private static final DateTimeFormatter SNAPSHOT_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final double TOOLBAR_SPACING = 10;
    private static final double TOOLBAR_PADDING = 7;
    private static final double TOOLBAR_PADDING_HORIZONTAL = 10;

    // Dependencies
    private final Exchange exchange;
    private TradePair tradePair;
    private final boolean liveSyncing;
    private final TradingService tradingService;

    // State management
    private final SimpleIntegerProperty secondsPerCandle = new SimpleIntegerProperty(DEFAULT_SECONDS_PER_CANDLE);
    private String selectedTimeframe = DEFAULT_TIMEFRAME;

    // UI Components
    private final AnchorPane root = new AnchorPane();
    private final HBox toolbarContainer = new HBox(TOOLBAR_SPACING);
    private final VBox candleChartContainer = new VBox();
    private final ComboBox<String> timeframeSelector = new ComboBox<>(
            FXCollections.observableArrayList(CandleAggregator.getSupportedTimeframes()));

    /**
     * -- GETTER --
     *  Gets the toolbar for advanced customization.
     *
     */
    // Chart management
    private ChartToolbar toolbar;
    private CandleStickChart candleStickChart;
    private Set<Integer> supportedGranularities;

    // Error and event callbacks
    private Consumer<String> onChartError;
    private Runnable onChartCreated;
    private Runnable onChartDisposed;
    private final String telegramToken;

    // Screenshot configuration
    private File lastScreenshotDirectory;

    /**
     * Creates a new chart container for the specified trading pair.
     *
     * @param exchange      the exchange to get candle data from
     * @param tradePair     the trading pair to display
     * @param liveSyncing   whether to sync live candle updates
     * @param telegramToken telegram token
     * @throws NullPointerException if exchange or tradePair is null
     */
    public ChartContainer(Exchange exchange, TradePair tradePair, boolean liveSyncing,
                          String telegramToken) {
        this(exchange, tradePair, liveSyncing, telegramToken, null);
    }

    public ChartContainer(Exchange exchange, TradePair tradePair, boolean liveSyncing,
                          String telegramToken, TradingService tradingService) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.tradePair = Objects.requireNonNull(tradePair, "tradePair must not be null");
        this.liveSyncing = liveSyncing;
        this.telegramToken = Objects.requireNonNull(telegramToken, "telegramToken must not be null");
        this.tradingService = tradingService;

        initialize();
    }

    /**
     * Initializes the UI components and loads the initial chart.
     */
    private void initialize() {
        getStyleClass().add(CONTAINER_STYLE_CLASS);
        setMinSize(MIN_WIDTH, MIN_HEIGHT);

        configureRoot();
        configureToolbar();
        configureChartHost();

        getChildren().setAll(root);

        try {
            createInitialChart();
            registerTimeframeListener();
            LOGGER.log(Level.INFO, "ChartContainer initialized successfully for %s".formatted(tradePair));
        } catch (Exception e) {
            handleChartError("Failed to initialize chart container: %s".formatted(e.getMessage()));
        }
    }

    /**
     * Configures the root pane with proper layout and styling.
     */
    private void configureRoot() {
        root.prefWidthProperty().bind(widthProperty());
        root.prefHeightProperty().bind(heightProperty());
        root.getStyleClass().add(ROOT_STYLE_CLASS);
        root.setStyle(ROOT_STYLE);

        root.getChildren().setAll(toolbarContainer, candleChartContainer);

        // Position toolbar at top
        AnchorPane.setTopAnchor(toolbarContainer, 0.0);
        AnchorPane.setLeftAnchor(toolbarContainer, 0.0);
        AnchorPane.setRightAnchor(toolbarContainer, 0.0);

        // Position chart below toolbar
        AnchorPane.setTopAnchor(candleChartContainer, TOOLBAR_HEIGHT);
        AnchorPane.setLeftAnchor(candleChartContainer, 0.0);
        AnchorPane.setRightAnchor(candleChartContainer, 0.0);
        AnchorPane.setBottomAnchor(candleChartContainer, 0.0);
    }

    /**
     * Configures the toolbar with timeframe selector and chart controls.
     */
    private void configureToolbar() {
        try {
            // Get supported granularities
            CandleDataSupplier defaultSupplier = buildCandleDataSupplier(secondsPerCandle.get());
            supportedGranularities = Set.copyOf(defaultSupplier.getSupportedGranularities());

            // Create separator and popover for toolbar
            Separator functionOptionsSeparator = new Separator(Orientation.VERTICAL);
            PopOver optionsPopOver = new PopOver();
            optionsPopOver.setTitle("Chart Options");
            optionsPopOver.setHeaderAlwaysVisible(true);

            // Create the toolbar with new constructor signature
            toolbar = new ChartToolbar(
                    candleChartContainer.widthProperty(),
                    candleChartContainer.heightProperty(),
                    optionsPopOver,
                    functionOptionsSeparator
            );
            HBox.setHgrow(toolbar, Priority.ALWAYS);
            toolbar.setMaxWidth(Double.MAX_VALUE);

            // Configure timeframe label
            Label timeframeLabel = new Label("Timeframe:");
            timeframeLabel.getStyleClass().add(TIMEFRAME_LABEL_CLASS);
            timeframeLabel.setStyle(TIMEFRAME_LABEL_STYLE);

            // Configure timeframe selector
            timeframeSelector.setValue(selectedTimeframe);
            timeframeSelector.setPrefWidth(90);
            timeframeSelector.setMinWidth(90);
            timeframeSelector.getStyleClass().add(TIMEFRAME_SELECTOR_CLASS);
            timeframeSelector.setStyle(TIMEFRAME_SELECTOR_STYLE);

            timeframeSelector.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null && !Objects.equals(oldValue, newValue)) {
                    applyTimeframe(newValue);
                }
            });

            // Create spacer to push controls to the left
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // Create vertical separator between timeframe and toolbar
            Separator separator = new Separator(Orientation.VERTICAL);

            // Configure toolbar container
            toolbarContainer.getStyleClass().add(TOOLBAR_CONTAINER_CLASS);
            toolbarContainer.setAlignment(Pos.CENTER_LEFT);
            toolbarContainer.setPadding(new Insets(TOOLBAR_PADDING, TOOLBAR_PADDING_HORIZONTAL,
                    TOOLBAR_PADDING, TOOLBAR_PADDING_HORIZONTAL));
            toolbarContainer.setMinHeight(TOOLBAR_HEIGHT);
            toolbarContainer.setPrefHeight(TOOLBAR_HEIGHT);
            toolbarContainer.setMaxHeight(TOOLBAR_HEIGHT);
            toolbarContainer.setStyle(TOOLBAR_CONTAINER_STYLE);

            // Add all components to toolbar container
            toolbarContainer.getChildren().setAll(
                    timeframeLabel,
                    timeframeSelector,
                    separator,
                    toolbar,
                    spacer
            );
        } catch (Exception e) {
            handleChartError("Failed to configure toolbar: " + e.getMessage());
        }
    }

    /**
     * Configures the chart host container.
     */
    private void configureChartHost() {
        candleChartContainer.getStyleClass().add(CHART_HOST_CLASS);
        candleChartContainer.setPadding(new Insets(0));
        candleChartContainer.setFillWidth(true);
        candleChartContainer.setStyle(CHART_HOST_STYLE);

        // Request layout when dimensions change
        candleChartContainer.widthProperty().addListener((observable, oldValue, newValue) -> requestLayout());
        candleChartContainer.heightProperty().addListener((observable, oldValue, newValue) -> requestLayout());
    }

    /**
     * Creates the initial chart with the default timeframe.
     */
    private void createInitialChart() {
        try {
            CandleStickChart chart = buildChart(secondsPerCandle.get());
            installChart(chart, false);
            LOGGER.log(Level.FINE, "Initial chart created successfully");
        } catch (SQLException | ClassNotFoundException exception) {
            handleChartError("Failed to create initial chart: " + exception.getMessage());
            throw new RuntimeException("Failed to create initial candle chart", exception);
        }
    }

    /**
     * Registers the listener for timeframe changes.
     */
    private void registerTimeframeListener() {
        secondsPerCandle.addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !Objects.equals(oldValue, newValue)) {
                recreateChartForTimeframe(newValue.intValue());
            }
        });
    }

    /**
     * Applies a timeframe change from the selector.
     *
     * @param timeframe the selected timeframe string (e.g., "1h")
     */
    private void applyTimeframe(String timeframe) {
        if (!CandleAggregator.isValidTimeframe(timeframe)) {
            LOGGER.log(Level.WARNING, "Invalid timeframe selected: " + timeframe);
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

    /**
     * Recreates the chart for a new timeframe with smooth transition.
     *
     * @param durationSeconds the candle duration in seconds
     */
    private void recreateChartForTimeframe(int durationSeconds) {
        try {
            LOGGER.log(Level.FINE, "Recreating chart for timeframe: " + durationSeconds + "s");
            CandleStickChart newChart = buildChart(durationSeconds);
            installChart(newChart, true);
        } catch (SQLException | ClassNotFoundException exception) {
            handleChartError("Failed to create chart for duration " + durationSeconds + "s: " + exception.getMessage());
            throw new RuntimeException("Failed to create chart with duration " + durationSeconds, exception);
        }
    }

    /**
     * Builds a new CandleStickChart with the specified duration.
     *
     * @param durationSeconds the candle duration in seconds
     * @return a new CandleStickChart instance
     * @throws SQLException if database operation fails
     * @throws ClassNotFoundException if database driver class not found
     * @throws IllegalArgumentException if durationSeconds is not positive
     */
    private CandleStickChart buildChart(int durationSeconds) throws SQLException, ClassNotFoundException {
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("secondsPerCandle must be positive but was: %d".formatted(durationSeconds));
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
                candleChartContainer.heightProperty()
        );
    }

    private CandleDataSupplier buildCandleDataSupplier(int durationSeconds) {
        Path rawTradeDataPath = configuredRawTradeDataPath();
        if (rawTradeDataPath != null) {
            try {
                LOGGER.log(Level.INFO, "Using ReverseRawTradeDataProcessor for " + rawTradeDataPath);
                return new ReverseRawTradeDataProcessor(rawTradeDataPath, durationSeconds, tradePair, exchange);
            } catch (IOException exception) {
                LOGGER.log(Level.WARNING, "Unable to use raw trade data processor; falling back to exchange supplier: " + exception.getMessage(), exception);
            }
        }
        return exchange.getCandleDataSupplier(durationSeconds, tradePair);
    }

    private Path configuredRawTradeDataPath() {
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

    /**
     * Installs a new chart, optionally with a fade transition.
     *
     * @param newChart the new chart to install
     * @param animated whether to use fade transition
     */
    private void installChart(CandleStickChart newChart, boolean animated) {
        Objects.requireNonNull(newChart, "newChart must not be null");

        CandleStickChart oldChart = this.candleStickChart;
        this.candleStickChart = newChart;

        // Configure chart sizing
        VBox.setVgrow(newChart, Priority.ALWAYS);
        newChart.prefWidthProperty().bind(candleChartContainer.widthProperty());
        newChart.prefHeightProperty().bind(candleChartContainer.heightProperty());

        // Register toolbar handlers
        registerToolbar(newChart);

        if (!animated || oldChart == null) {
            // No animation - direct swap
            disposeChart(oldChart);
            candleChartContainer.getChildren().setAll(newChart);
            newChart.setOpacity(1.0);
            executeOnChartCreated();
            return;
        }

        // Animated transition
        performFadeTransition(oldChart, newChart);
    }

    /**
     * Performs a fade transition between two charts.
     *
     * @param oldChart the chart to fade out
     * @param newChart the chart to fade in
     */
    private void performFadeTransition(CandleStickChart oldChart, CandleStickChart newChart) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(FADE_OUT_DURATION), oldChart);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        fadeOut.setOnFinished(_ -> {
            disposeChart(oldChart);
            candleChartContainer.getChildren().setAll(newChart);
            newChart.setOpacity(0.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(FADE_IN_DURATION), newChart);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setOnFinished(_ -> executeOnChartCreated());
            fadeIn.play();
        });

        fadeOut.play();
    }

    /**
     * Registers toolbar handlers with the chart.
     *
     * @param chart the chart to register with
     */
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
            toolbar.setOnAutoTradeAction(chart::autoTrade);
        } catch (Exception e) {
            handleChartError("Failed to register toolbar handlers: " + e.getMessage());
        }
    }

    private void saveChartSnapshot() {
        if (candleStickChart == null) {
            handleChartError("No chart is available to capture.");
            return;
        }

        try {
            // Show directory chooser for user to select save location
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Select Folder to Save Screenshot");
            
            // Set initial directory - use last selected or user home
            if (lastScreenshotDirectory != null && lastScreenshotDirectory.exists()) {
                directoryChooser.setInitialDirectory(lastScreenshotDirectory);
            } else {
                directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
            }
            
            // Show dialog
            File selectedDirectory = directoryChooser.showDialog(
                    candleStickChart.getScene() == null ? null : candleStickChart.getScene().getWindow()
            );
            
            // User cancelled the dialog
            if (selectedDirectory == null) {
                LOGGER.log(Level.INFO, "Screenshot save cancelled by user");
                return;
            }
            
            // Remember the selected directory for next time
            lastScreenshotDirectory = selectedDirectory;
            
            // Generate screenshot filename
            String filename = "InvestPro-%s-%s.png".formatted(
                    tradePair.toString('-'),
                    SNAPSHOT_FORMAT.format(LocalDateTime.now())
            );
            File output = new File(selectedDirectory, filename);
            
            // Capture and save screenshot
            WritableImage image = candleStickChart.snapshot(null, null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", output);
            
            LOGGER.log(Level.INFO, "Chart screenshot saved to " + output.getAbsolutePath());
        } catch (IOException exception) {
            handleChartError("Failed to save chart screenshot: " + exception.getMessage());
            LOGGER.log(Level.SEVERE, "Screenshot save error", exception);
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
            LOGGER.log(Level.INFO, "Chart sent to printer.");
        } else {
            handleChartError("Chart print was cancelled or failed.");
        }
    }

    /**
     * Safely disposes a chart, cleaning up its resources.
     *
     * @param chart the chart to dispose
     */
    private void disposeChart(CandleStickChart chart) {
        if (chart == null) {
            return;
        }

        try {
            chart.dispose();
            executeOnChartDisposed();
            LOGGER.log(Level.FINE, "Chart disposed successfully");
        } catch (Exception e) {
            // Log but don't throw - ensure chart switching never crashes the UI
            LOGGER.log(Level.WARNING, "Exception during chart disposal: " + e.getMessage(), e);
        }
    }

    /**
     * Handles chart errors by logging and invoking error callback.
     *
     * @param errorMessage the error message
     */
    private void handleChartError(String errorMessage) {
        LOGGER.log(Level.SEVERE, errorMessage);
        if (onChartError != null) {
            onChartError.accept(errorMessage);
        }
    }

    /**
     * Executes the onChartCreated callback if set.
     */
    private void executeOnChartCreated() {
        if (onChartCreated != null) {
            try {
                onChartCreated.run();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Exception in onChartCreated callback: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Executes the onChartDisposed callback if set.
     */
    private void executeOnChartDisposed() {
        if (onChartDisposed != null) {
            try {
                onChartDisposed.run();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Exception in onChartDisposed callback: " + e.getMessage(), e);
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

    // ========== PUBLIC API METHODS ==========

    /**
     * Gets the current candle chart displayed in this container.
     *
     * @return the current CandleStickChart or null if not yet created
     */
    public CandleStickChart getChart() {
        return candleStickChart;
    }

    /**
     * Gets the current candle duration in seconds.
     *
     * @return the seconds per candle value
     */
    public int getSecondsPerCandle() {
        return secondsPerCandle.get();
    }

    /**
     * Sets the candle duration in seconds.
     *
     * @param seconds the candle duration (must be positive)
     * @throws IllegalArgumentException if seconds is not positive
     */
    public void setSecondsPerCandle(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("seconds must be positive but was: " + seconds);
        }

        secondsPerCandle.set(seconds);

        // Update timeframe selector if possible
        String matchingTimeframe = CandleAggregator.TIMEFRAME_SECONDS.entrySet()
                .stream()
                .filter(entry -> Objects.equals(entry.getValue(), seconds))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (matchingTimeframe != null && !Objects.equals(timeframeSelector.getValue(), matchingTimeframe)) {
            selectedTimeframe = matchingTimeframe;
            timeframeSelector.setValue(matchingTimeframe);
        }
    }

    /**
     * Gets the seconds-per-candle property for binding and observation.
     *
     * @return the SimpleIntegerProperty
     */
    public SimpleIntegerProperty secondsPerCandleProperty() {
        return secondsPerCandle;
    }

    /**
     * Gets the currently selected timeframe string.
     *
     * @return the timeframe (e.g., "1h", "5m", "1d")
     */
    public String getSelectedTimeframe() {
        return selectedTimeframe;
    }

    /**
     * Sets the selected timeframe programmatically.
     *
     * @param timeframe the timeframe to select
     * @throws IllegalArgumentException if timeframe is invalid
     */
    public void setSelectedTimeframe(String timeframe) {
        if (timeframe == null || !CandleAggregator.isValidTimeframe(timeframe)) {
            throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        }
        timeframeSelector.setValue(timeframe);
    }

    /**
     * Gets the trading pair displayed in this container.
     *
     * @return the current trading pair
     */
    public TradePair getTradePair() {
        return tradePair;
    }

    /**
     * Sets a new trading pair and reloads the chart.
     *
     * @param tradePair the new trading pair
     * @throws NullPointerException if tradePair is null
     */
    public void setTradePairAndReload(TradePair tradePair) {
        this.tradePair = Objects.requireNonNull(tradePair, "tradePair must not be null");
        LOGGER.log(Level.INFO, "Trading pair changed to: " + tradePair);
        refreshChart();
    }

    /**
     * Rebuilds the chart using the current timeframe and trading pair.
     * Useful for refreshing after data updates or symbol changes.
     */
    public void refreshChart() {
        try {
            recreateChartForTimeframe(secondsPerCandle.get());
        } catch (Exception e) {
            handleChartError("Chart refresh failed: " + e.getMessage());
        }
    }

    /**
     * Gets the supported granularities for this container.
     *
     * @return set of supported granularities in seconds
     */
    public Set<Integer> getSupportedGranularities() {
        return supportedGranularities;
    }

    /**
     * Cleans up and disposes all chart resources.
     * Should be called before discarding this container.
     */
    public void dispose() {
        disposeChart(candleStickChart);
        candleStickChart = null;
        LOGGER.log(Level.INFO, "ChartContainer disposed");
    }

    // ========== CALLBACK METHODS ==========

    /**
     * Sets a callback to be invoked when chart errors occur.
     *
     * @param callback the callback function receiving the error message
     */
    public void setOnChartError(Consumer<String> callback) {
        this.onChartError = callback;
    }

    /**
     * Sets a callback to be invoked when a chart is successfully created.
     *
     * @param callback the callback function
     */
    public void setOnChartCreated(Runnable callback) {
        this.onChartCreated = callback;
    }

    /**
     * Sets a callback to be invoked when a chart is disposed.
     *
     * @param callback the callback function
     */
    public void setOnChartDisposed(Runnable callback) {
        this.onChartDisposed = callback;
    }
}
