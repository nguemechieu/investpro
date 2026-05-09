package org.investpro.ui.charts;

import lombok.extern.slf4j.Slf4j;

import org.investpro.exchange.consumers.UiExchangeStreamConsumer;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.indicators.ChartIndicator;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.geometry.Insets;
import javafx.print.PrinterJob;
import javafx.scene.Cursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.Axis;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import org.investpro.data.CandleDataPager;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.LiveTradesConsumer;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;
import org.investpro.ui.ChartContainer;
import org.investpro.ui.tools.InstantAxisFormatter;
import org.investpro.ui.tools.MoneyAxisFormatter;
import org.investpro.ui.tools.PriceLine;
import org.investpro.ui.tools.StableTicksAxis;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.DelayedSizeChangeListener;
import org.investpro.utils.FXUtils;
import org.investpro.utils.LogOnExceptionThreadFactory;
import org.investpro.utils.ZoomDirection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.investpro.ui.charts.ChartColors.AXIS_TICK_LABEL_COLOR;
import static org.investpro.ui.charts.ChartColors.BEAR_CANDLE_BORDER_COLOR;
import static org.investpro.ui.charts.ChartColors.BEAR_CANDLE_FILL_COLOR;
import static org.investpro.ui.charts.ChartColors.BULL_CANDLE_BORDER_COLOR;
import static org.investpro.ui.charts.ChartColors.BULL_CANDLE_FILL_COLOR;
import static org.investpro.ui.charts.ChartColors.PLACE_HOLDER_BORDER_COLOR;
import static org.investpro.ui.charts.ChartColors.PLACE_HOLDER_FILL_COLOR;

/**
 * Professional canvas-based candlestick chart for InvestPro.
 * <p>
 * Fixes the visual problems from the previous version:
 * - candle body and wick use the exact same center X
 * - candle slot width is separated from candle body width
 * - fit mode chooses a readable TradingView-like visible range
 * - y-axis range is recalculated from the visible candles only
 * - resize keeps the latest candle visible without compressing everything badly
 * <p>
 * This chart should be created by {@link ChartContainer}.
 */
@Getter
@Setter
@Slf4j
public class CandleStickChart extends Region {
    private static final DateTimeFormatter SCREENSHOT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter CROSSHAIR_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault());

    private static final int MIN_CHART_WIDTH = 360;
    private static final int MIN_CHART_HEIGHT = 260;
    private static final int RIGHT_AXIS_WIDTH = 92;
    private static final int LEFT_AXIS_WIDTH = 64;
    private static final int TIME_AXIS_HEIGHT = 34;
    private static final int HEADER_HEIGHT = 54;
    private static final int PRICE_BADGE_WIDTH = 96;
    private static final int PRICE_BADGE_HEIGHT = 22;

    private static final int MIN_VISIBLE_CANDLES = 40;
    private static final int TARGET_VISIBLE_CANDLES = 72;
    private static final int MAX_VISIBLE_CANDLES = 120;

    private static final int MIN_BODY_WIDTH = 2;
    private static final int MAX_BODY_WIDTH = 28;
    private static final int DEFAULT_BODY_WIDTH = 8;
    private static final int CANDLE_GAP = 2;

    private static final double VOLUME_AREA_RATIO = 0.20;
    private static final double PRICE_PADDING_RATIO = 0.12;
    private static final long LOADING_TIMEOUT_MS = 30_000L;

    private final Exchange exchange;
    private final TradePair tradePair;
    private final boolean liveSyncing;
    private final int secondsPerCandle;
    private final String telegramToken;
    private final TradingService tradingService;

    private final CandleStickChartOptions chartOptions;
    private final CandleDataPager candleDataPager;
    private final Consumer<List<CandleData>> candlePageConsumer;

    private final NavigableMap<Integer, CandleData> data = new ConcurrentSkipListMap<>();

    private final StableTicksAxis xAxis = new StableTicksAxis();
    private final StableTicksAxis yAxis = new StableTicksAxis();
    private final StableTicksAxis extraAxis = new StableTicksAxis();
    private final Line extraAxisExtension = new Line();

    private final ProgressIndicator progressIndicator = new ProgressIndicator(-1);
    private final Text loadingStatusText = new Text("Loading...");
    private final VBox loadingIndicatorContainer = new VBox(8, progressIndicator, loadingStatusText);

    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean drawRequested = new AtomicBoolean(false);
    private final AtomicBoolean eventFiltersInstalled = new AtomicBoolean(false);

    private final ScheduledExecutorService chartLoadingExecutor = Executors
            .newSingleThreadScheduledExecutor(new LogOnExceptionThreadFactory("CHART-DATA-LOADER"));
    private final ScheduledExecutorService chartTimeoutExecutor = Executors
            .newSingleThreadScheduledExecutor(new LogOnExceptionThreadFactory("CHART-LOADING-TIMEOUT"));

    private final PauseTransition redrawDebouncer = new PauseTransition(Duration.millis(16));
    private final PauseTransition noticeClearTimer = new PauseTransition(Duration.seconds(2.0));

    private final EventHandler<MouseEvent> mouseDraggedHandler = new MouseDraggedHandler();
    private final EventHandler<ScrollEvent> scrollHandler = new ScrollEventHandler();
    private final EventHandler<KeyEvent> keyHandler = new KeyEventHandler();

    private final Font canvasNumberFont = Font.font(FXUtils.getMonospacedFont(), 13);

    private final List<PriceLine> priceLines = new ArrayList<>();
    private final List<ChartIndicator> indicators = new ArrayList<>();
    private Image backgroundImage;
    private double backgroundImageOpacity = 0.22;

    // Candle selection callback
    private Consumer<CandleData> candleSelectionCallback;

    private Canvas canvas;
    private GraphicsContext graphicsContext;
    private StackPane chartStackPane;
    private Button newsEventsButton;

    private volatile boolean disposed;
    private volatile boolean paging;
    private volatile long loadingStartTime = -1L;

    private int candleBodyWidth = DEFAULT_BODY_WIDTH;
    private int firstVisibleIndex = 0;
    private int visibleCandles = TARGET_VISIBLE_CANDLES;

    private double mousePrevX = -1;
    private double mousePrevY = -1;
    private double scrollDeltaXSum;

    private double chartWidth = 1000;
    private double chartHeight = 700;

    // Scrollbar and scroll position tracking
    private static final int SCROLLBAR_HEIGHT = 8;
    private static final int SCROLLBAR_PADDING = 2;
    private long lastScrollTime = 0;
    private String scrollPositionText = "";

    // Market regime tracking
    private String currentMarketRegime = "RANGING";
    private Color regimeColor = Color.rgb(148, 163, 184);

    private double visibleMinPrice = 0;
    private double visibleMaxPrice = 1;
    private double visibleMaxVolume = 1;

    private double crosshairMouseX = -1;
    private double crosshairMouseY = -1;
    private boolean showCrosshair = true;
    private boolean showPriceLines = true;
    private double hoveredPrice = -1;
    private long hoveredTime = -1;

    private boolean autoTradeEnabled = false;

    private ChangeListener<Number> activeSizeListener;
    private ChangeListener<Boolean> activeFirstSizeListener;

    private final UpdateInProgressCandleTask updateInProgressCandleTask;
    private final ScheduledExecutorService updateInProgressCandleExecutor;
    private double canvasX;

    public CandleStickChart(
            Exchange exchange,
            CandleDataSupplier candleDataSupplier,
            TradePair tradePair,
            boolean liveSyncing,
            int secondsPerCandle,
            String telegramToken,
            TradingService tradingService,
            ObservableNumberValue containerWidth,
            ObservableNumberValue containerHeight) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(candleDataSupplier, "candleDataSupplier must not be null");
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        Objects.requireNonNull(containerWidth, "containerWidth must not be null");
        Objects.requireNonNull(containerHeight, "containerHeight must not be null");

        if (!Platform.isFxApplicationThread()) {
            throw new IllegalArgumentException(
                    "CandleStickChart must be constructed on the JavaFX Application Thread, but was called from %s"
                            .formatted(Thread.currentThread()));
        }

        this.exchange = exchange;
        this.tradePair = tradePair;
        this.liveSyncing = liveSyncing;
        this.secondsPerCandle = Math.max(1, secondsPerCandle);
        this.telegramToken = telegramToken == null ? "" : telegramToken.trim();
        this.tradingService = tradingService;

        this.chartOptions = new CandleStickChartOptions();
        this.candleDataPager = new CandleDataPager(this, candleDataSupplier);
        this.candlePageConsumer = new CandlePageConsumer();

        if (liveSyncing) {
            this.updateInProgressCandleTask = new UpdateInProgressCandleTask();
            this.updateInProgressCandleExecutor = Executors.newSingleThreadScheduledExecutor(
                    new LogOnExceptionThreadFactory("UPDATE-CURRENT-CANDLE"));
        } else {
            this.updateInProgressCandleTask = null;
            this.updateInProgressCandleExecutor = null;
        }

        configureStyle();
        configureAxes();
        configureLoadingOverlay();
        configureAxisExtension();

        getChildren().addAll(xAxis, yAxis, extraAxis, extraAxisExtension);

        initializeFirstLayout(containerWidth, containerHeight);
        initializeOptionListeners();

        if (liveSyncing) {
            initializeLiveSyncing();
        }
    }

    private void configureStyle() {
        getStyleClass().add("candle-chart");
        setFocusTraversable(true);
        setMinSize(MIN_CHART_WIDTH, MIN_CHART_HEIGHT);
    }

    private void configureAxes() {
        String axisStyle = "-fx-text-fill: #cbd5e1; -fx-border-color: #273244; -fx-background-color: #0b1220;";
        Font axisFont = Font.font(FXUtils.getMonospacedFont(), 12);

        xAxis.setManaged(false);
        yAxis.setManaged(false);
        extraAxis.setManaged(false);
        extraAxisExtension.setManaged(false);

        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
        extraAxis.setAnimated(false);

        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);
        extraAxis.setAutoRanging(false);

        xAxis.setForceZeroInRange(false);
        yAxis.setForceZeroInRange(false);
        extraAxis.setForceZeroInRange(false);

        xAxis.setSide(Side.BOTTOM);
        yAxis.setSide(Side.RIGHT);
        extraAxis.setSide(Side.LEFT);

        xAxis.setTickLabelFormatter(InstantAxisFormatter.of(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        yAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getCounterCurrency()));
        extraAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getBaseCurrency()));

        xAxis.setStyle(axisStyle);
        yAxis.setStyle(axisStyle);
        extraAxis.setStyle(axisStyle);

        xAxis.setTickLabelFill(AXIS_TICK_LABEL_COLOR);
        yAxis.setTickLabelFill(AXIS_TICK_LABEL_COLOR);
        extraAxis.setTickLabelFill(AXIS_TICK_LABEL_COLOR);

        xAxis.setTickLabelFont(axisFont);
        yAxis.setTickLabelFont(axisFont);
        extraAxis.setTickLabelFont(axisFont);

        extraAxis.setTickLabelsVisible(true);
        extraAxis.setTickMarkVisible(true);
        extraAxis.setMinorTickVisible(false);
        extraAxis.setMouseTransparent(true);
    }

    private void configureLoadingOverlay() {
        loadingIndicatorContainer.setAlignment(Pos.CENTER);
        loadingIndicatorContainer.setMouseTransparent(true);

        progressIndicator.setPrefSize(42, 42);
        progressIndicator.setVisible(false);

        loadingStatusText.setFont(Font.font(FXUtils.getMonospacedFont(), 13));
        loadingStatusText.setFill(Color.web("#cbd5e1"));
        loadingStatusText.setMouseTransparent(true);

        noticeClearTimer.setOnFinished(event -> {
            if (!loading.get() && !paging) {
                loadingStatusText.setText("");
            }
        });
    }

    private void configureAxisExtension() {
        extraAxisExtension.setStroke(Color.web("#475569"));
        extraAxisExtension.setStrokeWidth(1);
        extraAxisExtension.setMouseTransparent(true);
    }

    private void setupNewsEventsButton() {
        newsEventsButton = new Button("📰 News");
        newsEventsButton.setStyle(
                "-fx-font-size: 11; " +
                        "-fx-padding: 6 12; " +
                        "-fx-background-color: #1e40af; " +
                        "-fx-text-fill: #ffffff; " +
                        "-fx-border-color: #3b82f6; " +
                        "-fx-border-radius: 4; " +
                        "-fx-background-radius: 4; " +
                        "-fx-cursor: hand;");

        newsEventsButton.setOnMouseEntered(e -> newsEventsButton.setStyle(
                "-fx-font-size: 11; " +
                        "-fx-padding: 6 12; " +
                        "-fx-background-color: #1e3a8a; " +
                        "-fx-text-fill: #ffffff; " +
                        "-fx-border-color: #3b82f6; " +
                        "-fx-border-radius: 4; " +
                        "-fx-background-radius: 4; " +
                        "-fx-cursor: hand;"));

        newsEventsButton.setOnMouseExited(e -> newsEventsButton.setStyle(
                "-fx-font-size: 11; " +
                        "-fx-padding: 6 12; " +
                        "-fx-background-color: #1e40af; " +
                        "-fx-text-fill: #ffffff; " +
                        "-fx-border-color: #3b82f6; " +
                        "-fx-border-radius: 4; " +
                        "-fx-background-radius: 4; " +
                        "-fx-cursor: hand;"));

        newsEventsButton.setOnAction(event -> showNewsEvents());

        // Create a container for the button positioned at top-right
        HBox buttonContainer = new HBox(newsEventsButton);
        buttonContainer.setAlignment(Pos.TOP_RIGHT);
        buttonContainer.setPadding(new Insets(8, 12, 0, 0));
        buttonContainer.setMouseTransparent(false);

        chartStackPane.getChildren().add(buttonContainer);
    }

    private void showNewsEvents() {
        log.info("CandleStickChart: News events button clicked for {}", tradePair);

        // Create and show news events dialog/window
        Alert newsDialog = new Alert(Alert.AlertType.INFORMATION);
        newsDialog.setTitle("📰 Economic Events & News");
        newsDialog.setHeaderText("Economic Calendar for " + tradePair.toString('/'));

        String content = "Economic Events for: " + tradePair.getBaseCurrency() +
                " / " + tradePair.getCounterCurrency() + "\n\n" +
                "⏰ Upcoming Economic Events:\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +

                // Display upcoming events - this can be expanded with real data
                "🔹 Central Bank Announcements\n" +
                "   - Interest rate decisions\n" +
                "   - Policy statements\n" +
                "   - Economic forecasts\n\n" +
                "🔹 Economic Indicators\n" +
                "   - GDP releases\n" +
                "   - Inflation data (CPI, PPI)\n" +
                "   - Employment reports\n" +
                "   - Trade balance\n\n" +
                "🔹 Market Events\n" +
                "   - Corporate earnings\n" +
                "   - Dividend announcements\n" +
                "   - Stock splits\n\n" +
                "💡 Tip: Monitor economic calendars on:\n" +
                "   • Investing.com\n" +
                "   • Trading Economics\n" +
                "   • Central Bank websites\n" +
                "   • Major news outlets";

        newsDialog.setContentText(content);
        newsDialog.setWidth(500);
        newsDialog.setHeight(600);
        newsDialog.showAndWait();
    }

    private void initializeFirstLayout(ObservableNumberValue containerWidth, ObservableNumberValue containerHeight) {
        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);
        ChangeListener<Number> sizeListener = new SizeChangeListener(gotFirstSize, containerWidth, containerHeight);

        activeSizeListener = sizeListener;
        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);

        ChangeListener<Boolean> firstSizeListener = (observable, oldValue, newValue) -> {
            if (!Boolean.TRUE.equals(newValue)) {
                return;
            }

            chartWidth = Math.max(MIN_CHART_WIDTH, containerWidth.getValue().doubleValue());
            chartHeight = Math.max(MIN_CHART_HEIGHT, containerHeight.getValue().doubleValue());

            canvas = new Canvas(Math.max(1, chartWidth - LEFT_AXIS_WIDTH - RIGHT_AXIS_WIDTH),
                    Math.max(1, chartHeight - TIME_AXIS_HEIGHT));
            graphicsContext = canvas.getGraphicsContext2D();

            chartStackPane = new StackPane(canvas, loadingIndicatorContainer);
            chartStackPane.setManaged(false);
            getChildren().addFirst(chartStackPane);

            setupNewsEventsButton();

            canvas.setFocusTraversable(true);
            canvas.setOnMouseEntered(event -> {
                if (canvas.getScene() != null) {
                    canvas.getScene().setCursor(Cursor.CROSSHAIR);
                }
            });
            canvas.setOnMouseExited(event -> {
                if (canvas.getScene() != null) {
                    canvas.getScene().setCursor(Cursor.DEFAULT);
                }
            });

            layoutChart();
            initializeEventHandlers();
            startChartDataLoading();

            gotFirstSize.removeListener(activeFirstSizeListener);
            activeFirstSizeListener = null;
        };

        activeFirstSizeListener = firstSizeListener;
        gotFirstSize.addListener(firstSizeListener);
    }

    private void initializeOptionListeners() {
        redrawDebouncer.setOnFinished(event -> {
            drawRequested.set(false);
            drawChartContents(true);
        });

        chartOptions.horizontalGridLinesVisibleProperty()
                .addListener((observable, oldValue, newValue) -> requestChartRedraw());
        chartOptions.verticalGridLinesVisibleProperty()
                .addListener((observable, oldValue, newValue) -> requestChartRedraw());
        chartOptions.showVolumeProperty().addListener((observable, oldValue, newValue) -> {
            layoutChart();
            requestChartRedraw();
        });
        chartOptions.alignOpenCloseProperty().addListener((observable,
                oldValue, newValue) -> requestChartRedraw());
    }

    private void initializeEventHandlers() {
        if (canvas == null) {
            return;
        }

        canvas.setOnMouseClicked(event -> {
            canvas.requestFocus();
            if (event.getButton() == MouseButton.PRIMARY) {
                // Handle left-click for candle selection
                CandleData selectedCandle = getCandleAtPosition(event.getX());
                if (selectedCandle != null && candleSelectionCallback != null) {
                    candleSelectionCallback.accept(selectedCandle);
                }
                event.consume();
            } else if (event.getButton() == MouseButton.SECONDARY) {
                toggleCrosshair();
                event.consume();
            }
        });

        if (eventFiltersInstalled.compareAndSet(false, true)) {
            chartStackPane.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                mousePrevX = event.getScreenX();
                mousePrevY = event.getScreenY();
                chartStackPane.requestFocus();
                canvas.requestFocus();
            });
            chartStackPane.addEventFilter(MouseEvent.MOUSE_RELEASED,
                    event -> {
                        mousePrevX = -1;
                        mousePrevY = -1;
                        scrollDeltaXSum = 0;
                    });
            chartStackPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
            chartStackPane.addEventFilter(MouseEvent.MOUSE_MOVED, new MouseMovedHandler());
            chartStackPane.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
                crosshairMouseX = -1;
                crosshairMouseY = -1;
                hoveredPrice = -1;
                hoveredTime = -1;
                requestChartRedraw();
            });
            chartStackPane.addEventFilter(ScrollEvent.SCROLL, scrollHandler);
            chartStackPane.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        }
    }

    private void initializeLiveSyncing() {
        if (updateInProgressCandleExecutor == null || updateInProgressCandleTask == null) {
            return;
        }

        updateInProgressCandleExecutor.execute(() -> {
            if (disposed)
                return;
            boolean streamingStarted = false;
            try {
                CountDownLatch initLatch = exchange.getWebsocketClient() != null
                        ? exchange.getWebsocketClient().getInitializationLatch()
                        : null;
                if (exchange.getWebsocketClient() != null
                        && initLatch != null
                        && initLatch.await(10, SECONDS)
                        && exchange.getWebsocketClient().supportsStreamingTrades(tradePair)) {

                    ExchangeStreamConsumer exchangeS = new UiExchangeStreamConsumer();
                    exchange.getWebsocketClient().streamLiveTrades(tradePair, exchangeS);
                    streamingStarted = true;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                log.warn("WebSocket live stream failed for {}; polling fallback will be used.", tradePair,
                        exception);
            }

            if (!streamingStarted && !disposed) {
                startPollingForLiveTrades();
            }

            if (!disposed) {
                updateInProgressCandleExecutor.scheduleAtFixedRate(updateInProgressCandleTask, 5, 5, SECONDS);
            }
        });
    }

    private void startChartDataLoading() {
        if (disposed || !loading.compareAndSet(false, true))
            return;

        loadingStartTime = System.currentTimeMillis();
        showLoadingStatus("Fetching chart data...");

        chartTimeoutExecutor.schedule(this::checkLoadingTimeout, LOADING_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier(), chartLoadingExecutor)
                .thenAccept(candleDataPager.getCandleDataPreProcessor())
                .whenComplete((result, throwable) -> {
                    loadingStartTime = -1L;
                    loading.set(false);
                    if (disposed)
                        return;
                    if (throwable != null) {
                        log.error("Error loading chart data for {}", tradePair, throwable);
                        showErrorMessage("Failed to load chart data: %s".formatted(rootMessage(throwable)));
                    }
                });
    }

    private void checkLoadingTimeout() {
        if (!disposed && loading.get() && loadingStartTime > 0
                && System.currentTimeMillis() - loadingStartTime >= LOADING_TIMEOUT_MS) {
            showErrorMessage("Loading is taking too long. Check your exchange connection.");
        }
    }

    private void startPollingForLiveTrades() {
        if (disposed || updateInProgressCandleExecutor == null || updateInProgressCandleTask == null)
            return;
        updateInProgressCandleExecutor.scheduleAtFixedRate(() -> {
            if (disposed)
                return;
            try {
                Instant since = Instant.now().minusSeconds(Math.max(5, secondsPerCandle));
                exchange.fetchRecentTradesUntil(tradePair, since).thenAccept(trades -> {
                    if (trades != null) {
                        for (Trade trade : trades)
                            updateInProgressCandleTask.accept(trade);
                    }
                });
            } catch (Exception exception) {
                log.debug("Polling task failed for {}", tradePair, exception);
            }
        }, 3, 3, SECONDS);
    }

    private void setInitialState(List<CandleData> candles) {
        if (!Platform.isFxApplicationThread()) {
            runOnFx(() -> setInitialState(candles));
            return;
        }
        if (candles == null || candles.isEmpty()) {
            showErrorMessage("No candle data available for %s".formatted(tradePair.toString('-')));
            return;
        }
        data.clear();
        for (CandleData candle : sanitizeAndSort(candles)) {
            data.put(candle.openTime(), candle);
        }
        recalculateIndicators();
        fitLatestReadable();
        updateScrollPositionText();
        hideLoadingIndicator();
        if (updateInProgressCandleTask != null) {
            updateInProgressCandleTask.setReady(true);
        }
    }

    private List<CandleData> sanitizeAndSort(List<CandleData> candles) {
        return candles.stream().filter(Objects::nonNull).sorted(Comparator.comparingInt(CandleData::openTime)).toList();
    }

    private void mergeCandles(List<CandleData> candles) {
        if (candles == null || candles.isEmpty())
            return;
        for (CandleData candle : sanitizeAndSort(candles)) {
            data.put(candle.openTime(), candle);
        }
        if (!data.isEmpty()) {
            recalculateIndicators();
            clampVisibleWindow();
            recomputeVisiblePriceRange();
            drawChartContents(true);
        }
    }

    int visibles = 0;

    private void fitLatestReadable() {

        if (data.isEmpty() || canvas == null)
            return;
        int total = data.size();
        double width = Math.max(1.0, canvas.getWidth());
        visibles = Math.min(total, TARGET_VISIBLE_CANDLES);
        if (total <= MIN_VISIBLE_CANDLES)
            visibles = total;
        else if (width > 1350)
            visibles = Math.min(total, 90);
        else if (width < 650)
            visibles = Math.min(total, 55);
        setVisibleCandleCount(visibles);
        firstVisibleIndex = Math.max(0, total - visibleCandles);
        updateXAxisBoundsFromVisibleWindow();
        recomputeVisiblePriceRange();
        drawChartContents(true);
    }

    public void fitChart() {
        runOnFx(this::fitLatestReadable);
    }

    public void jumpToLatestCandle() {
        runOnFx(() -> {
            if (data.isEmpty())
                return;
            firstVisibleIndex = Math.max(0, data.size() - visibleCandles);
            updateXAxisBoundsFromVisibleWindow();
            recomputeVisiblePriceRange();
            drawChartContents(true);
        });
    }

    public void refreshChart() {
        if (disposed)
            return;
        data.clear();
        firstVisibleIndex = 0;
        visibleCandles = TARGET_VISIBLE_CANDLES;
        showLoadingStatus("Refreshing chart...");
        startChartDataLoading();
    }

    public void applyAdaptiveScaling() {
        runOnFx(() -> {
            if (data.isEmpty() || canvas == null)
                return;
            if (visibleCandles < MIN_VISIBLE_CANDLES || visibleCandles > MAX_VISIBLE_CANDLES)
                fitLatestReadable();
            else {
                updateCandleWidthFromVisibleCount();
                drawChartContents(true);
            }
        });
    }

    private void setVisibleCandleCount(int requestedVisible) {
        visibleCandles = Math.max(1, Math.min(data.size(), requestedVisible));
        updateCandleWidthFromVisibleCount();
    }

    private void updateCandleWidthFromVisibleCount() {
        if (canvas == null || visibleCandles <= 0) {
            candleBodyWidth = DEFAULT_BODY_WIDTH;
            return;
        }
        double slot = Math.max(1.0, canvas.getWidth() / visibleCandles);
        candleBodyWidth = clampInt((int) Math.floor(slot - CANDLE_GAP), MIN_BODY_WIDTH, MAX_BODY_WIDTH);
    }

    private double candleCenterX(int candleIndex) {
        double slot = Math.max(1.0, canvas.getWidth() / Math.max(1, visibleCandles));
        return snapPixel((candleIndex + 0.5) * slot);
    }

    private void updateXAxisBoundsFromVisibleWindow() {
        List<CandleData> visible = visibleCandlesSnapshot();
        if (visible.isEmpty())
            return;
        CandleData first = visible.get(0);
        CandleData last = visible.get(visible.size() - 1);
        xAxis.setLowerBound(first.openTime());
        xAxis.setUpperBound(last.openTime() + secondsPerCandle);
        xAxis.setTickLabelFormatter(InstantAxisFormatter.of(selectTimePattern()));
    }

    private @NotNull DateTimeFormatter selectTimePattern() {
        long range = Math.max(secondsPerCandle, (long) (xAxis.getUpperBound() - xAxis.getLowerBound()));
        if (range > 60L * 60L * 24L * 120L)
            return DateTimeFormatter.ofPattern("MMM yyyy");
        if (range > 60L * 60L * 24L * 2L)
            return DateTimeFormatter.ofPattern("MM-dd");
        return DateTimeFormatter.ofPattern("HH:mm");
    }

    private void clampVisibleWindow() {
        int total = data.size();
        if (total == 0) {
            firstVisibleIndex = 0;
            return;
        }
        visibleCandles = Math.max(1, Math.min(visibleCandles, total));
        firstVisibleIndex = clampInt(firstVisibleIndex, 0, Math.max(0, total - visibleCandles));
        updateCandleWidthFromVisibleCount();
        updateXAxisBoundsFromVisibleWindow();
    }

    private List<CandleData> visibleCandlesSnapshot() {
        if (data.isEmpty())
            return List.of();
        List<CandleData> all = new ArrayList<>(data.values());
        int total = all.size();
        int from = clampInt(firstVisibleIndex, 0, Math.max(0, total - 1));
        int to = clampInt(from + visibleCandles, from + 1, total);
        return new ArrayList<>(all.subList(from, to));
    }

    private void recomputeVisiblePriceRange() {
        List<CandleData> visible = visibleCandlesSnapshot();
        if (visible.isEmpty()) {
            visibleMinPrice = 0.0;
            visibleMaxPrice = 1.0;
            visibleMaxVolume = 1.0;
            return;
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double maxVolume = 0.0;
        for (CandleData candle : visible) {
            if (candle == null || candle.placeHolder())
                continue;
            min = Math.min(min, candle.lowPrice());
            max = Math.max(max, candle.highPrice());
            maxVolume = Math.max(maxVolume, candle.volume());
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            min = 0.0;
            max = 1.0;
        }
        double delta = max - min;
        if (!Double.isFinite(delta) || delta <= 0.0)
            delta = Math.max(Math.abs(max) * 0.01, 0.0001);
        double padding = delta * PRICE_PADDING_RATIO;
        visibleMinPrice = Math.max(0.0, min - padding);
        visibleMaxPrice = max + padding;
        visibleMaxVolume = Math.max(1.0, maxVolume);
        yAxis.setLowerBound(visibleMinPrice);
        yAxis.setUpperBound(visibleMaxPrice);
        extraAxis.setLowerBound(0.0);
        extraAxis.setUpperBound(visibleMaxVolume);
    }

    private void layoutChart() {
        if (canvas == null || chartStackPane == null)
            return;
        double totalW = Math.max(MIN_CHART_WIDTH, getWidth() <= 0 ? chartWidth : getWidth());
        double totalH = Math.max(MIN_CHART_HEIGHT, getHeight() <= 0 ? chartHeight : getHeight());
        chartWidth = totalW;
        chartHeight = totalH;
        double leftAxisWidth = chartOptions.isShowVolume() ? LEFT_AXIS_WIDTH : 0.0;
        canvasX = leftAxisWidth;
        double canvasW = Math.max(1.0, totalW - leftAxisWidth - RIGHT_AXIS_WIDTH);
        double canvasH = Math.max(1.0, totalH - TIME_AXIS_HEIGHT);
        canvas.setWidth(canvasW);
        canvas.setHeight(canvasH);
        chartStackPane.resizeRelocate(canvasX, 0, canvasW, canvasH);
        chartStackPane.setMinSize(canvasW, canvasH);
        chartStackPane.setPrefSize(canvasW, canvasH);
        chartStackPane.setMaxSize(canvasW, canvasH);
        xAxis.resizeRelocate(canvasX, canvasH, canvasW, TIME_AXIS_HEIGHT);
        yAxis.resizeRelocate(canvasX + canvasW, 0, RIGHT_AXIS_WIDTH, canvasH);
        double volumeH = canvasH * VOLUME_AREA_RATIO;
        double volumeY = canvasH - volumeH;
        extraAxis.resizeRelocate(0, volumeY, leftAxisWidth, volumeH);
        extraAxis.setVisible(chartOptions.isShowVolume());
        extraAxisExtension.setStartX(leftAxisWidth);
        extraAxisExtension.setEndX(leftAxisWidth);
        extraAxisExtension.setStartY(volumeY);
        extraAxisExtension.setEndY(canvasH);
        extraAxisExtension.setVisible(chartOptions.isShowVolume());
        layoutAxes();
        if (!data.isEmpty()) {
            clampVisibleWindow();
            recomputeVisiblePriceRange();
        }
    }

    private void layoutAxes() {
        xAxis.requestAxisLayout();
        yAxis.requestAxisLayout();
        extraAxis.requestAxisLayout();
        xAxis.layout();
        yAxis.layout();
        extraAxis.layout();
    }

    private void drawChartContents(boolean clear) {
        if (!Platform.isFxApplicationThread()) {
            runOnFx(() -> drawChartContents(clear));
            return;
        }
        if (disposed || canvas == null || graphicsContext == null)
            return;
        if (clear)
            clearCanvas();
        List<CandleData> visible = visibleCandlesSnapshot();
        if (visible.isEmpty()) {
            drawNoDataOverlay();
            return;
        }
        // Calculate current market regime
        calculateMarketRegime();
        recomputeVisiblePriceRange();
        drawTradingBackground();
        drawGridLines();
        double volumeScale = (canvas.getHeight() * VOLUME_AREA_RATIO) / visibleMaxVolume;
        int highIndex = -1, lowIndex = -1;
        double high = Double.NEGATIVE_INFINITY, low = Double.POSITIVE_INFINITY;
        double lastClose = -1.0;
        for (int i = 0; i < visible.size(); i++) {
            CandleData candle = visible.get(i);
            if (candle == null)
                continue;
            if (!candle.placeHolder()) {
                if (candle.highPrice() > high) {
                    high = candle.highPrice();
                    highIndex = i;
                }
                if (candle.lowPrice() < low) {
                    low = candle.lowPrice();
                    lowIndex = i;
                }
            }
            drawCandle(candle, i, volumeScale, lastClose);
            lastClose = candle.closePrice();
        }
        drawHighLowMarkers(high, low, highIndex, lowIndex);
        drawChartHeader(visible.get(visible.size() - 1));
        drawMarketRegime();
        if (showPriceLines)
            drawPriceLines();
        drawIndicators();
        drawCurrentPriceBadge(visible.get(visible.size() - 1));
        if (showCrosshair && crosshairMouseX >= 0 && crosshairMouseY >= 0)
            drawCrosshair();
        drawScrollbar();
        drawScrollPosition();
    }

    private void clearCanvas() {
        graphicsContext.setFill(Color.web("#050b14"));
        graphicsContext.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private void drawTradingBackground() {
        graphicsContext.setFill(Color.rgb(10, 14, 23));
        graphicsContext.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        drawBackgroundImage();
        graphicsContext.setFill(Color.rgb(15, 20, 31, 0.90));
        graphicsContext.fillRect(0, 0, canvas.getWidth(), HEADER_HEIGHT);
        if (chartOptions.isShowVolume()) {
            double volumeTop = canvas.getHeight() * (1.0 - VOLUME_AREA_RATIO);
            graphicsContext.setFill(Color.rgb(6, 10, 18, 0.54));
            graphicsContext.fillRect(0, volumeTop, canvas.getWidth(), canvas.getHeight() - volumeTop);
            graphicsContext.setStroke(Color.rgb(42, 52, 68, 0.70));
            graphicsContext.strokeLine(0, volumeTop, canvas.getWidth(), volumeTop);
        }
        graphicsContext.setStroke(Color.rgb(42, 52, 68, 0.70));
        graphicsContext.strokeLine(0, HEADER_HEIGHT, canvas.getWidth(), HEADER_HEIGHT);
    }

    private void drawBackgroundImage() {
        if (backgroundImage == null || backgroundImage.isError()) {
            return;
        }

        double canvasWidth = canvas.getWidth();
        double canvasHeight = canvas.getHeight();
        double imageWidth = backgroundImage.getWidth();
        double imageHeight = backgroundImage.getHeight();
        if (canvasWidth <= 0 || canvasHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return;
        }

        double scale = Math.max(canvasWidth / imageWidth, canvasHeight / imageHeight);
        double drawWidth = imageWidth * scale;
        double drawHeight = imageHeight * scale;
        double drawX = (canvasWidth - drawWidth) / 2.0;
        double drawY = (canvasHeight - drawHeight) / 2.0;
        double previousAlpha = graphicsContext.getGlobalAlpha();
        graphicsContext.setGlobalAlpha(clamp(backgroundImageOpacity, 0.0, 1.0));
        graphicsContext.drawImage(backgroundImage, drawX, drawY, drawWidth, drawHeight);
        graphicsContext.setGlobalAlpha(previousAlpha);
    }

    private void drawGridLines() {
        graphicsContext.setLineWidth(1.0);
        if (chartOptions.isHorizontalGridLinesVisible()) {
            graphicsContext.setStroke(Color.rgb(42, 52, 68, 0.42));
            for (Axis.TickMark<Number> tick : yAxis.getTickMarks()) {
                double y = snapPixel(priceToY(tick.getValue().doubleValue()));
                graphicsContext.strokeLine(0, y, canvas.getWidth(), y);
            }
        }
        if (chartOptions.isVerticalGridLinesVisible()) {
            graphicsContext.setStroke(Color.rgb(42, 52, 68, 0.25));
            int step = Math.max(1, visibleCandles / 8);
            for (int i = 0; i < visibleCandles; i += step) {
                double x = candleCenterX(i);
                graphicsContext.strokeLine(x, HEADER_HEIGHT, x, canvas.getHeight());
            }
        }
    }

    private void drawCandle(CandleData candle, int visibleIndex, double volumeScale, double lastClose) {
        double centerX = candleCenterX(visibleIndex);
        double slot = Math.max(1.0, canvas.getWidth() / Math.max(1, visibleCandles));
        double bodyWidth = Math.max(1.0, Math.min(candleBodyWidth, Math.max(1.0, slot - CANDLE_GAP)));
        double wickX = snapPixel(centerX);
        double bodyX = snapPixel(centerX - (bodyWidth / 2.0));
        if (bodyX > canvas.getWidth() || bodyX + bodyWidth < 0)
            return;
        double open = candle.openPrice();
        if (chartOptions.isAlignOpenClose() && lastClose > 0)
            open = lastClose;
        if (candle.placeHolder()) {
            double y = priceToY(open);
            graphicsContext.setFill(PLACE_HOLDER_FILL_COLOR);
            graphicsContext.fillRect(bodyX, snapPixel(y), bodyWidth, 1);
            graphicsContext.setStroke(PLACE_HOLDER_BORDER_COLOR);
            graphicsContext.strokeRect(bodyX, snapPixel(y), bodyWidth, 1);
            return;
        }
        double close = candle.closePrice();
        double high = Math.max(candle.highPrice(), Math.max(open, close));
        double low = Math.min(candle.lowPrice(), Math.min(open, close));
        boolean bearish = close < open;
        Paint fill = bearish ? BEAR_CANDLE_FILL_COLOR : BULL_CANDLE_FILL_COLOR;
        Paint stroke = bearish ? BEAR_CANDLE_BORDER_COLOR : BULL_CANDLE_BORDER_COLOR;
        double openY = priceToY(open), closeY = priceToY(close), highY = priceToY(high), lowY = priceToY(low);
        double bodyTop = snapPixel(Math.min(openY, closeY));
        double bodyBottom = snapPixel(Math.max(openY, closeY));
        double bodyHeight = Math.max(1.0, bodyBottom - bodyTop);
        graphicsContext.setStroke(stroke);
        graphicsContext.setLineWidth(candleStrokeWidth());
        graphicsContext.strokeLine(wickX, snapPixel(highY), wickX, bodyTop);
        graphicsContext.strokeLine(wickX, bodyTop + bodyHeight, wickX, snapPixel(lowY));
        graphicsContext.setFill(fill);
        graphicsContext.fillRect(bodyX, bodyTop, bodyWidth, bodyHeight);
        graphicsContext.setStroke(stroke);
        graphicsContext.strokeRect(bodyX, bodyTop, bodyWidth, bodyHeight);
        if (chartOptions.isShowVolume())
            drawVolumeBar(candle, bodyX, bodyWidth, fill, volumeScale);
    }

    private void drawVolumeBar(CandleData candle, double x, double width, Paint fill, double volumeScale) {
        double maxH = canvas.getHeight() * VOLUME_AREA_RATIO;
        double height = Math.min(maxH, Math.max(1.0, candle.volume() * volumeScale));
        double y = canvas.getHeight() - height;
        graphicsContext.setGlobalAlpha(0.55);
        graphicsContext.setFill(fill);
        graphicsContext.fillRect(x, y, width, height);
        graphicsContext.setGlobalAlpha(1.0);
    }

    private void drawHighLowMarkers(double high, double low, int highIndex, int lowIndex) {
        if (highIndex < 0 || lowIndex < 0)
            return;
        graphicsContext.setFont(canvasNumberFont);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(AXIS_TICK_LABEL_COLOR);
        graphicsContext.setFontSmoothingType(FontSmoothingType.LCD);
        double highX = candleCenterX(highIndex), highY = priceToY(high) - 10;
        double lowX = candleCenterX(lowIndex), lowY = priceToY(low) + 10;
        graphicsContext.setTextAlign(highX < canvas.getWidth() * 0.5 ? TextAlignment.LEFT : TextAlignment.RIGHT);
        graphicsContext.fillText(formatPrice(high), highX < canvas.getWidth() * 0.5 ? highX + 10 : highX - 10, highY);
        graphicsContext.setTextAlign(lowX < canvas.getWidth() * 0.5 ? TextAlignment.LEFT : TextAlignment.RIGHT);
        graphicsContext.fillText(formatPrice(low), lowX < canvas.getWidth() * 0.5 ? lowX + 10 : lowX - 10, lowY);
    }

    private void drawChartHeader(CandleData candle) {
        if (candle == null)
            return;
        graphicsContext
                .setFont(Font.font(FXUtils.getMonospacedFont(), Math.max(26, Math.min(56, canvas.getWidth() / 12))));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(Color.rgb(248, 250, 252, 0.07));
        graphicsContext.fillText(tradePair.toString('/'), canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
        boolean bullish = candle.closePrice() >= candle.openPrice();
        Color closeColor = bullish ? Color.rgb(38, 166, 154) : Color.rgb(239, 83, 80);
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 13));
        graphicsContext.setTextAlign(TextAlignment.LEFT);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(Color.rgb(226, 232, 240));
        graphicsContext.fillText(tradePair.toString('/'), 14, 18);
        graphicsContext.setFill(Color.rgb(148, 163, 184));
        graphicsContext.fillText(exchange.supportsTimeframe(secondsPerCandle), 112, 18);

        // Display market session
        String session = getMarketSession();
        Color sessionColor = getSessionColor(session);
        graphicsContext.setFill(sessionColor);
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 11));
        graphicsContext.fillText(session, canvas.getWidth() - 200, 18);

        drawHeaderValue("O", candle.openPrice(), 14, Color.rgb(203, 213, 225));
        drawHeaderValue("H", candle.highPrice(), 142, Color.rgb(38, 166, 154));
        drawHeaderValue("L", candle.lowPrice(), 270, Color.rgb(239, 83, 80));
        drawHeaderValue("C", candle.closePrice(), 398, closeColor);
        graphicsContext.setFill(Color.rgb(148, 163, 184));
        graphicsContext.fillText("Vol %s".formatted(compactNumber(candle.volume())), 526, 40);
        graphicsContext.fillText("%sChange ".formatted(compactNumber(tradePair.getChange())), 626, 70);

        graphicsContext.setTextAlign(TextAlignment.LEFT);

    }

    private String getMarketSession() {
        java.time.ZoneId marketZone = detectMarketTimeZone();
        java.time.LocalTime currentTime = java.time.LocalTime.now(marketZone);
        java.time.LocalDateTime currentDateTime = java.time.LocalDateTime.now(marketZone);
        java.time.DayOfWeek dayOfWeek = currentDateTime.getDayOfWeek();

        // Check if it's a weekend
        if (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            return "CLOSED (Weekend)";
        }

        // NYSE/NASDAQ: 9:30 AM - 4:00 PM ET
        if (isStockMarketPair()) {
            if (currentTime.isAfter(java.time.LocalTime.of(9, 30))
                    && currentTime.isBefore(java.time.LocalTime.of(16, 0))) {
                return "NYSE/NASDAQ OPEN";
            } else if (currentTime.isAfter(java.time.LocalTime.of(4, 0))
                    && currentTime.isBefore(java.time.LocalTime.of(9, 30))) {
                return "NYSE/NASDAQ PREMARKET";
            } else if (currentTime.isAfter(java.time.LocalTime.of(16, 0))
                    && currentTime.isBefore(java.time.LocalTime.of(20, 0))) {
                return "NYSE/NASDAQ AFTERHOURS";
            } else {
                return "NYSE/NASDAQ CLOSED";
            }
        }

        // Forex: 24/5 (Monday-Friday)
        if (isForexPair()) {
            return "FOREX OPEN (24h)";
        }

        // Crypto: 24/7
        if (isCryptoPair()) {
            return "CRYPTO OPEN (24/7)";
        }

        // Check for London Stock Exchange: 8:00 AM - 4:30 PM GMT
        if (isLondonMarketPair()) {
            if (currentTime.isAfter(java.time.LocalTime.of(8, 0))
                    && currentTime.isBefore(java.time.LocalTime.of(16, 30))) {
                return "LSE OPEN";
            } else if (currentTime.isAfter(java.time.LocalTime.of(7, 0))
                    && currentTime.isBefore(java.time.LocalTime.of(8, 0))) {
                return "LSE PREMARKET";
            } else {
                return "LSE CLOSED";
            }
        }

        return "MARKET CLOSED";
    }

    private java.time.ZoneId detectMarketTimeZone() {
        // Detect timezone based on trading pair
        if (isStockMarketPair()) {
            return java.time.ZoneId.of("America/New_York");
        } else if (isLondonMarketPair()) {
            return java.time.ZoneId.of("Europe/London");
        } else if (isForexPair()) {
            return java.time.ZoneId.of("Europe/London"); // Forex trades in London time
        }
        return java.time.ZoneId.systemDefault();
    }

    private boolean isStockMarketPair() {
        String base = tradePair.getBaseCurrency().getSymbol().toUpperCase();
        String counter = tradePair.getCounterCurrency().getSymbol().toUpperCase();
        // Check for common stock indicators (company symbols with USD/EUR)
        return (counter.equals("USD") || counter.equals("EUR")) &&
                (base.length() <= 5 && !isCryptoSymbol(base) && !isForexSymbol(base));
    }

    private boolean isForexPair() {
        String base = tradePair.getBaseCurrency().getSymbol().toUpperCase();
        String counter = tradePair.getCounterCurrency().getSymbol().toUpperCase();
        // Forex pairs are typically fiat currencies like EUR/USD, GBP/USD, etc.
        return isForexSymbol(base) && isForexSymbol(counter);
    }

    private boolean isCryptoPair() {
        String base = tradePair.getBaseCurrency().getSymbol().toUpperCase();
        String counter = tradePair.getCounterCurrency().getSymbol().toUpperCase();
        return isCryptoSymbol(base) || isCryptoSymbol(counter);
    }

    private boolean isLondonMarketPair() {
        String base = tradePair.getBaseCurrency().getSymbol().toUpperCase();
        String counter = tradePair.getCounterCurrency().getSymbol().toUpperCase();
        // Check for London Stock Exchange pairs (GBP pairs or UK-related indices)
        return (counter.equals("GBP") && base.length() <= 5 && !isCryptoSymbol(base)) ||
                base.contains("FTSE") || base.contains("LSE");
    }

    private boolean isCryptoSymbol(String symbol) {
        // Common crypto symbols
        String crypto = "BTC,ETH,XRP,LTC,BCH,EOS,BNB,XLM,XMR,DASH,ZEC,DOGE,ADA,IOTA,NEO,LINK,DOT,SOL,USDT,USDC,DAI,BUSD,WBTC,WETH";
        return crypto.contains(symbol);
    }

    private boolean isForexSymbol(String symbol) {
        // Common forex currency codes
        String forexCodes = "EUR,USD,GBP,JPY,CHF,CAD,AUD,NZD,CNY,INR,MXN,BRL,RUB,ZAR,SGD,HKD,SEK,NOK,DKK,PLN,CZK,HUF,RON,BGN,HRK";
        return forexCodes.contains(symbol);
    }

    private Color getSessionColor(String session) {
        if (session.contains("OPEN")) {
            return Color.rgb(76, 175, 80); // Green for open
        } else if (session.contains("PREMARKET") || session.contains("AFTERHOURS")) {
            return Color.rgb(255, 152, 0); // Orange for pre/after hours
        } else {
            return Color.rgb(244, 67, 54); // Red for closed
        }
    }

    private void drawHeaderValue(String label, double value, double x, Color color) {
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 12));
        graphicsContext.setTextAlign(TextAlignment.LEFT);
        graphicsContext.setFill(Color.rgb(148, 163, 184));
        graphicsContext.fillText(label, x, 40);
        graphicsContext.setFill(color);
        graphicsContext.fillText(formatPrice(value), x + 18, 40);
    }

    private void drawPriceLines() {
        for (PriceLine line : List.copyOf(priceLines)) {
            if (!line.isVisible() || !line.isValid())
                continue;
            double y = priceToY(line.getPrice());
            if (y < 0 || y > canvas.getHeight())
                continue;
            graphicsContext.setStroke(line.getColor());
            graphicsContext.setLineWidth(line.getLineWidth());
            if (line.isDashed())
                graphicsContext.setLineDashes(5, 5);
            graphicsContext.strokeLine(0, snapPixel(y), canvas.getWidth(), snapPixel(y));
            graphicsContext.setLineDashes();
            if (line.isLabelVisible()) {
                String text = (line.getLabel() == null || line.getLabel().isBlank())
                        ? formatPrice(line.getPrice())
                        : "%s %s".formatted(line.getLabel(), formatPrice(line.getPrice()));
                graphicsContext.setFill(line.getColor());
                graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 10));
                graphicsContext.setTextAlign(TextAlignment.LEFT);
                graphicsContext.fillText(text, 5, y - 4);
            }
        }
    }

    private void drawIndicators() {
        if (indicators.isEmpty())
            return;

        List<CandleData> visible = visibleCandlesSnapshot();
        if (visible.isEmpty())
            return;
        List<CandleData> allCandles = getAllCandleData();

        for (ChartIndicator indicator : List.copyOf(indicators)) {
            java.util.Map<String, double[]> values = indicator.getValues();
            if (values.isEmpty() || !indicator.isCalculated())
                continue;

            java.util.Map<String, javafx.scene.paint.Color> lineColors = getIndicatorColors(indicator.getName());

            for (java.util.Map.Entry<String, double[]> entry : values.entrySet()) {
                String lineName = entry.getKey();
                double[] lineValues = entry.getValue();
                javafx.scene.paint.Color color = lineColors.getOrDefault(lineName, javafx.scene.paint.Color.CYAN);

                double lastX = -1, lastY = -1;
                for (int i = 0; i < visible.size(); i++) {
                    int dataIndex = indexOfOpenTime(allCandles, visible.get(i).openTime());
                    if (dataIndex < 0 || dataIndex >= lineValues.length) {
                        lastX = -1;
                        lastY = -1;
                        continue;
                    }
                    double value = lineValues[dataIndex];
                    if (Double.isNaN(value) || Double.isInfinite(value)) {
                        lastX = -1;
                        lastY = -1;
                        continue;
                    }

                    double y = priceToY(value);
                    double x = candleCenterX(i);

                    if (lastX >= 0 && lastY >= 0) {
                        graphicsContext.setStroke(color);
                        graphicsContext.setLineWidth(1.5);
                        graphicsContext.strokeLine(snapPixel(lastX), snapPixel(lastY), snapPixel(x), snapPixel(y));
                    }

                    lastX = x;
                    lastY = y;
                }
            }
        }
    }

    private java.util.Map<String, javafx.scene.paint.Color> getIndicatorColors(String indicatorName) {
        java.util.Map<String, javafx.scene.paint.Color> colors = new java.util.HashMap<>();
        switch (indicatorName) {
            case "SMA20" -> colors.put("SMA", javafx.scene.paint.Color.web("#87CEEB"));
            case "EMA12" -> colors.put("EMA", javafx.scene.paint.Color.web("#FFA500"));
            case "RSI14" -> colors.put("RSI", javafx.scene.paint.Color.web("#9370DB"));
            case "MACD" -> {
                colors.put("MACD", javafx.scene.paint.Color.web("#2ecc71"));
                colors.put("Signal", javafx.scene.paint.Color.web("#e74c3c"));
                colors.put("Histogram", javafx.scene.paint.Color.web("#3498db"));
            }
            case "Bollinger Bands" -> {
                colors.put("MiddleBand", javafx.scene.paint.Color.web("#3498db"));
                colors.put("UpperBand", javafx.scene.paint.Color.web("#95a5a6"));
                colors.put("LowerBand", javafx.scene.paint.Color.web("#95a5a6"));
            }
            case "Stochastic14" -> {
                colors.put("K", javafx.scene.paint.Color.web("#e74c3c"));
                colors.put("D", javafx.scene.paint.Color.web("#3498db"));
            }
            case "ATR14" -> colors.put("ATR", javafx.scene.paint.Color.web("#e91e63"));
            case "Parabolic SAR" -> colors.put("SAR", javafx.scene.paint.Color.web("#f1c40f"));
            case "Fibonacci Retracement" -> {
                colors.put("0%", javafx.scene.paint.Color.web("#7f8c8d"));
                colors.put("23.6%", javafx.scene.paint.Color.web("#95a5a6"));
                colors.put("38.2%", javafx.scene.paint.Color.web("#bdc3c7"));
                colors.put("50%", javafx.scene.paint.Color.web("#e0e0e0"));
                colors.put("61.8%", javafx.scene.paint.Color.web("#bdc3c7"));
                colors.put("78.6%", javafx.scene.paint.Color.web("#95a5a6"));
                colors.put("100%", javafx.scene.paint.Color.web("#7f8c8d"));
            }
            case "ADX" -> {
                colors.put("+DI", javafx.scene.paint.Color.web("#2ecc71"));
                colors.put("-DI", javafx.scene.paint.Color.web("#e74c3c"));
                colors.put("ADX", javafx.scene.paint.Color.web("#3498db"));
            }
            case "CCI" -> colors.put("CCI", javafx.scene.paint.Color.web("#f39c12"));
            case "Zigzag" -> colors.put("Zigzag", javafx.scene.paint.Color.web("#e74c3c"));
            case "Fractal" -> {
                colors.put("UpFractal", javafx.scene.paint.Color.web("#2ecc71"));
                colors.put("DnFractal", javafx.scene.paint.Color.web("#e74c3c"));
            }
            case "Ichimoku Cloud" -> {
                colors.put("TenkanSen", javafx.scene.paint.Color.web("#3498db"));
                colors.put("KijunSen", javafx.scene.paint.Color.web("#f39c12"));
                colors.put("SenkouSpanA", javafx.scene.paint.Color.web("#2ecc71"));
                colors.put("SenkouSpanB", javafx.scene.paint.Color.web("#e74c3c"));
                colors.put("ChikouSpan", javafx.scene.paint.Color.web("#9370DB"));
            }
            case "Volume Weighted Average Price" -> colors.put("VWAP", javafx.scene.paint.Color.web("#1abc9c"));
            default -> colors.put("Line", javafx.scene.paint.Color.CYAN);
        }
        return colors;
    }

    private double getCandleWidth() {
        List<CandleData> visible = visibleCandlesSnapshot();
        if (visible.isEmpty())
            return 10;
        double totalWidth = canvas.getWidth();
        return Math.max(2, totalWidth / visible.size() * 0.8);
    }

    private void drawCurrentPriceBadge(CandleData candle) {
        double price = candle.closePrice();
        double y = priceToY(price);
        if (y < 0 || y > canvas.getHeight())
            return;
        boolean bullish = candle.closePrice() >= candle.openPrice();
        Color color = bullish ? Color.rgb(38, 166, 154) : Color.rgb(239, 83, 80);
        double x = canvas.getWidth() - PRICE_BADGE_WIDTH;
        double badgeY = clamp(y - PRICE_BADGE_HEIGHT / 2.0, HEADER_HEIGHT + 2,
                canvas.getHeight() - PRICE_BADGE_HEIGHT - 2);
        graphicsContext.setStroke(Color.rgb(148, 163, 184, 0.40));
        graphicsContext.setLineWidth(1);
        graphicsContext.setLineDashes(5, 5);
        // Draw price line from left to right edge (fully extending to right)
        graphicsContext.strokeLine(0, snapPixel(y), canvas.getWidth(), snapPixel(y));
        graphicsContext.setLineDashes();
        graphicsContext.setFill(color);
        graphicsContext.fillRoundRect(x, badgeY, PRICE_BADGE_WIDTH, PRICE_BADGE_HEIGHT, 5, 5);
        graphicsContext.setFill(Color.WHITE);
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 12));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.fillText(formatPrice(price), x + PRICE_BADGE_WIDTH / 2.0, badgeY + PRICE_BADGE_HEIGHT / 2.0);
    }

    private void drawCrosshair() {
        graphicsContext.setStroke(Color.web("#94a3b8", 0.58));
        graphicsContext.setLineWidth(1.0);
        graphicsContext.setLineDashes(4, 4);
        graphicsContext.strokeLine(crosshairMouseX, 0, crosshairMouseX, canvas.getHeight());
        graphicsContext.strokeLine(0, crosshairMouseY, canvas.getWidth(), crosshairMouseY);
        graphicsContext.setLineDashes();
        double price = yToPrice(crosshairMouseY);
        hoveredPrice = price;
        int index = clampInt(
                (int) Math.floor(crosshairMouseX / Math.max(1.0, canvas.getWidth() / Math.max(1, visibleCandles))), 0,
                Math.max(0, visibleCandles - 1));
        List<CandleData> visible = visibleCandlesSnapshot();
        if (index < visible.size())
            hoveredTime = visible.get(index).openTime();
        drawLabelBadge(formatPrice(price), canvas.getWidth() - PRICE_BADGE_WIDTH,
                clamp(crosshairMouseY - PRICE_BADGE_HEIGHT / 2.0, HEADER_HEIGHT + 2,
                        canvas.getHeight() - PRICE_BADGE_HEIGHT - 2),
                PRICE_BADGE_WIDTH);
        if (hoveredTime > 0) {
            String time = CROSSHAIR_TIME_FORMAT.format(Instant.ofEpochSecond(hoveredTime));
            double w = 150;
            drawLabelBadge(time, clamp(crosshairMouseX - w / 2.0, 2, canvas.getWidth() - w - 2),
                    canvas.getHeight() - PRICE_BADGE_HEIGHT - 2, w);
        }
    }

    private void drawLabelBadge(String text, double x, double y, double width) {
        graphicsContext.setFill(Color.rgb(30, 41, 59, 0.98));
        graphicsContext.fillRoundRect(x, y, width, PRICE_BADGE_HEIGHT, 5, 5);
        graphicsContext.setStroke(Color.rgb(148, 163, 184, 0.35));
        graphicsContext.strokeRoundRect(x, y, width, PRICE_BADGE_HEIGHT, 5, 5);
        graphicsContext.setFill(Color.rgb(241, 245, 249));
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 11));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.fillText(text, x + width / 2.0, y + PRICE_BADGE_HEIGHT / 2.0);
    }

    /**
     * Draws a visual scrollbar at the bottom of the chart showing the current
     * position in the historical data. The scrollbar helps users understand
     * where they are in the data timeline and how much data is available.
     */
    private void drawScrollbar() {
        if (data.isEmpty() || canvas == null || canvas.getWidth() <= 0)
            return;

        double scrollbarTop = canvas.getHeight() - SCROLLBAR_HEIGHT - SCROLLBAR_PADDING;
        double trackWidth = canvas.getWidth();
        double trackHeight = SCROLLBAR_HEIGHT;

        // Draw scrollbar track (background)
        graphicsContext.setFill(Color.rgb(20, 30, 45, 0.6));
        graphicsContext.fillRect(0, scrollbarTop, trackWidth, trackHeight);
        graphicsContext.setStroke(Color.rgb(42, 52, 68, 0.5));
        graphicsContext.setLineWidth(1);
        graphicsContext.strokeRect(0, scrollbarTop, trackWidth, trackHeight);

        // Calculate scrollbar thumb (handle) position and size
        int totalCandles = data.size();
        double thumbWidth = Math.max(20, (visibleCandles / (double) totalCandles) * trackWidth);
        double thumbX = (firstVisibleIndex / (double) Math.max(1, totalCandles - visibleCandles))
                * (trackWidth - thumbWidth);

        // Draw scrollbar thumb
        Color thumbColor = Color.rgb(100, 150, 220, 0.8);
        graphicsContext.setFill(thumbColor);
        graphicsContext.fillRect(thumbX, scrollbarTop, thumbWidth, trackHeight);

        // Draw thumb border
        graphicsContext.setStroke(Color.rgb(150, 180, 255, 0.9));
        graphicsContext.setLineWidth(1);
        graphicsContext.strokeRect(thumbX, scrollbarTop, thumbWidth, trackHeight);

        // Add subtle gradient-like shading
        graphicsContext.setFill(Color.rgb(255, 255, 255, 0.15));
        graphicsContext.fillRect(thumbX, scrollbarTop, thumbWidth, trackHeight / 2);
    }

    /**
     * Draws scroll position information showing which candles are currently
     * visible.
     * Auto-hides after 2 seconds of inactivity.
     */
    private void drawScrollPosition() {
        if (scrollPositionText.isEmpty() || canvas == null)
            return;

        // Auto-hide after 2 seconds
        long timeSinceLastScroll = System.currentTimeMillis() - lastScrollTime;
        if (timeSinceLastScroll > 2000) {
            return;
        }

        // Calculate opacity based on time (fade out effect)
        double opacity = Math.max(0, 1.0 - (timeSinceLastScroll / 2000.0));

        // Draw position text in bottom-left corner
        double x = 10;
        double y = canvas.getHeight() - SCROLLBAR_HEIGHT - SCROLLBAR_PADDING - 5;

        graphicsContext.setGlobalAlpha(opacity);
        graphicsContext.setFill(Color.rgb(30, 41, 59, 0.85));
        graphicsContext.fillRoundRect(x, y - 16, 240, 18, 3, 3);
        graphicsContext.setStroke(Color.rgb(100, 150, 220, 0.6));
        graphicsContext.setLineWidth(1);
        graphicsContext.strokeRoundRect(x, y - 16, 240, 18, 3, 3);

        graphicsContext.setFill(Color.rgb(200, 220, 255, 0.9));
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 10));
        graphicsContext.setTextAlign(TextAlignment.LEFT);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.fillText(scrollPositionText, x + 6, y - 7);
        graphicsContext.setGlobalAlpha(1.0);
    }

    private void drawNoDataOverlay() {
        clearCanvas();
        graphicsContext.setFill(Color.web("#94a3b8"));
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 16));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.fillText("No visible candle data", canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
    }

    /**
     * Calculate market regime based on volatility and trend strength.
     * Updates currentMarketRegime and regimeColor fields.
     */
    private void calculateMarketRegime() {
        List<CandleData> visible = visibleCandlesSnapshot();
        if (visible.isEmpty()) {
            currentMarketRegime = "NO DATA";
            regimeColor = Color.rgb(148, 163, 184);
            return;
        }

        // Calculate ATR (Average True Range) as percentage
        double atrPct = calculateATRPercentage(visible);

        // Calculate trend strength based on EMA comparison
        double emaSlow = calculateEMA(visible, 50);
        double emaFast = calculateEMA(visible, 12);
        double currentPrice = visible.get(visible.size() - 1).closePrice();
        double trendStrength = (currentPrice - emaSlow) / emaSlow;

        // Determine regime based on volatility and trend
        if (atrPct > 0.04) {
            currentMarketRegime = "🌪️ HIGH VOLATILITY";
            regimeColor = Color.rgb(239, 83, 80); // Red
        } else if (trendStrength > 0.02) {
            currentMarketRegime = "📈 UPTREND";
            regimeColor = Color.rgb(38, 166, 154); // Green
        } else if (trendStrength < -0.02) {
            currentMarketRegime = "📉 DOWNTREND";
            regimeColor = Color.rgb(239, 83, 80); // Red
        } else {
            currentMarketRegime = "📊 RANGING";
            regimeColor = Color.rgb(148, 163, 184); // Gray
        }
    }

    /**
     * Calculate ATR as percentage of current price.
     */
    private double calculateATRPercentage(List<CandleData> candles) {
        if (candles.size() < 2)
            return 0;

        double sumTR = 0;
        int period = Math.min(14, candles.size());

        for (int i = 1; i < period; i++) {
            CandleData current = candles.get(candles.size() - period + i);
            CandleData previous = candles.get(candles.size() - period + i - 1);

            double tr = Math.max(
                    current.highPrice() - current.lowPrice(),
                    Math.max(
                            Math.abs(current.highPrice() - previous.closePrice()),
                            Math.abs(current.lowPrice() - previous.closePrice())));
            sumTR += tr;
        }

        double atr = sumTR / period;
        double currentPrice = candles.get(candles.size() - 1).closePrice();
        return currentPrice > 0 ? atr / currentPrice : 0;
    }

    /**
     * Calculate Exponential Moving Average.
     */
    private double calculateEMA(List<CandleData> candles, int period) {
        if (candles.isEmpty())
            return 0;

        double k = 2.0 / (period + 1);
        double ema = candles.get(0).closePrice();

        for (int i = 1; i < Math.min(candles.size(), period * 3); i++) {
            ema = candles.get(i).closePrice() * k + ema * (1 - k);
        }

        return ema;
    }

    /**
     * Draws market regime badge on the chart header.
     * Positioned in top-right corner next to market session info.
     */
    private void drawMarketRegime() {
        if (canvas == null || currentMarketRegime.isEmpty()) {
            calculateMarketRegime();
        }

        double x = canvas.getWidth() - 320;
        double y = 18;

        // Draw background badge
        double badgeWidth = 110;
        double badgeHeight = 20;
        graphicsContext.setFill(Color.rgb(30, 41, 59, 0.7));
        graphicsContext.fillRoundRect(x - 5, y - 10, badgeWidth, badgeHeight, 3, 3);

        // Draw border
        graphicsContext.setStroke(regimeColor);
        graphicsContext.setLineWidth(1);
        graphicsContext.strokeRoundRect(x - 5, y - 10, badgeWidth, badgeHeight, 3, 3);

        // Draw regime text
        graphicsContext.setFill(regimeColor);
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 11));
        graphicsContext.setTextAlign(TextAlignment.LEFT);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.fillText(currentMarketRegime, x, y);
    }

    public void changeZoom(ZoomDirection direction) {
        runOnFx(() -> {
            if (data.isEmpty() || canvas == null || direction == null)
                return;
            int delta = direction == ZoomDirection.IN ? -8 : 8;
            int nextVisible = clampInt(visibleCandles + delta, Math.min(MIN_VISIBLE_CANDLES, data.size()),
                    Math.min(MAX_VISIBLE_CANDLES, data.size()));
            CandleData anchor = latestVisibleCandle().orElse(lastValue());
            int anchorTime = anchor == null ? -1 : anchor.openTime();
            setVisibleCandleCount(nextVisible);
            if (anchorTime > 0) {
                List<CandleData> all = new ArrayList<>(data.values());
                int anchorIndex = indexOfOpenTime(all, anchorTime);
                firstVisibleIndex = clampInt(anchorIndex - visibleCandles + 1, 0,
                        Math.max(0, data.size() - visibleCandles));
            } else {
                firstVisibleIndex = Math.max(0, data.size() - visibleCandles);
            }
            updateXAxisBoundsFromVisibleWindow();
            recomputeVisiblePriceRange();
            drawChartContents(true);
        });
    }

    private Optional<CandleData> latestVisibleCandle() {
        List<CandleData> visible = visibleCandlesSnapshot();
        return visible.isEmpty() ? Optional.empty() : Optional.ofNullable(visible.get(visible.size() - 1));
    }

    private CandleData lastValue() {
        return data.isEmpty() ? null : data.lastEntry().getValue();
    }

    /**
     * Gets all available candle data for indicator calculation.
     * 
     * @return List of all CandleData sorted by open time
     */
    public List<CandleData> getAllCandleData() {
        return new ArrayList<>(data.values());
    }

    private int indexOfOpenTime(List<CandleData> candles, int openTime) {
        for (int i = 0; i < candles.size(); i++)
            if (candles.get(i).openTime() == openTime)
                return i;
        return candles.size() - 1;
    }

    private void panByCandles(int candles) {
        if (data.isEmpty())
            return;

        int oldIndex = firstVisibleIndex;
        firstVisibleIndex = clampInt(firstVisibleIndex + candles, 0, Math.max(0, data.size() - visibleCandles));

        // Provide visual feedback when reaching boundaries
        if (candles < 0 && firstVisibleIndex == 0) {
            showTransientNotice("📍 Reached oldest data");
        } else if (candles > 0 && firstVisibleIndex >= Math.max(0, data.size() - visibleCandles)) {
            showTransientNotice("📍 Reached latest data");
        }

        // Update scroll position display
        updateScrollPositionText();
        lastScrollTime = System.currentTimeMillis();

        updateXAxisBoundsFromVisibleWindow();
        recomputeVisiblePriceRange();
        drawChartContents(true);
    }

    private void updateScrollPositionText() {
        if (data.isEmpty()) {
            scrollPositionText = "";
            return;
        }

        int total = data.size();
        int startIndex = firstVisibleIndex + 1;
        int endIndex = Math.min(firstVisibleIndex + visibleCandles, total);
        int progress = (int) ((100.0 * endIndex) / total);

        scrollPositionText = "Candles: %d-%d / %d (%d%%)".formatted(startIndex, endIndex, total, progress);
    }

    private void requestChartRedraw() {
        if (disposed)
            return;
        runOnFx(() -> {
            if (drawRequested.compareAndSet(false, true))
                redrawDebouncer.playFromStart();
        });
    }

    private double priceToY(double price) {
        double range = Math.max(0.00000001, visibleMaxPrice - visibleMinPrice);
        return canvas.getHeight() - ((price - visibleMinPrice) / range * canvas.getHeight());
    }

    private double yToPrice(double y) {
        double range = Math.max(0.00000001, visibleMaxPrice - visibleMinPrice);
        return visibleMinPrice + ((canvas.getHeight() - y) / canvas.getHeight() * range);
    }

    private double candleStrokeWidth() {
        return candleBodyWidth <= 3 ? 1.0 : 1.2;
    }

    private double snapPixel(double value) {
        return Math.round(value) + 0.5;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clampInt(int value, int min, int max) {
        return max < min ? min : Math.max(min, Math.min(max, value));
    }

    private String formatPrice(double value) {
        return String.valueOf(value);
    }

    private String compactNumber(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000)
            return "%.2fB".formatted(value / 1_000_000_000.0);
        if (abs >= 1_000_000)
            return "%.2fM".formatted(value / 1_000_000.0);
        if (abs >= 1_000)
            return "%.2fK".formatted(value / 1_000.0);
        return "%.2f".formatted(value);
    }

    public void addPriceLine(double price, Color color, String label) {
        if (!Double.isFinite(price) || price <= 0)
            return;
        addPriceLine(
                new PriceLine(price, color == null ? Color.web("#f59e0b") : color, label == null ? "" : label.trim()));
    }

    public void addPriceLine(@Nullable PriceLine priceLine) {
        if (priceLine == null || !priceLine.isValid())
            return;
        priceLines.add(priceLine.copy());
        requestChartRedraw();
    }

    public void addSupportPriceLine(double price) {
        addPriceLine(PriceLine.support(price));
    }

    public void addResistancePriceLine(double price) {
        addPriceLine(PriceLine.resistance(price));
    }

    public void addStopLossPriceLine(double price) {
        addPriceLine(PriceLine.stopLoss(price));
    }

    public void addTakeProfitPriceLine(double price) {
        addPriceLine(PriceLine.takeProfit(price));
    }

    public void addEntryPriceLine(double price) {
        addPriceLine(PriceLine.entry(price));
    }

    public void setBidPriceLine(double price) {
        replacePriceLineByLabel(PriceLine.bid(price));
    }

    public void setAskPriceLine(double price) {
        replacePriceLineByLabel(PriceLine.ask(price));
    }

    private void replacePriceLineByLabel(@NotNull PriceLine replacement) {
        priceLines.removeIf(line -> Objects.equals(line.getLabel(), replacement.getLabel()));
        priceLines.add(replacement.copy());
        requestChartRedraw();
    }

    public List<PriceLine> getPriceLines() {
        return priceLines.stream().map(PriceLine::copy).toList();
    }

    public void removePriceLine(double price) {
        priceLines.removeIf(line -> Double.compare(line.getPrice(), price) == 0);
        requestChartRedraw();
    }

    public void clearPriceLines() {
        priceLines.clear();
        requestChartRedraw();
    }

    public void setPriceLinesVisible(boolean visible) {
        showPriceLines = visible;
        requestChartRedraw();
    }

    public boolean isPriceLinesVisible() {
        return showPriceLines;
    }

    public void togglePriceLines() {
        setPriceLinesVisible(!showPriceLines);
    }

    // Indicator management methods
    public void addIndicator(ChartIndicator indicator) {
        if (indicator != null && indicators.stream().noneMatch(i -> i.getName().equals(indicator.getName()))) {
            indicator.calculate(getAllCandleData());
            indicators.add(indicator);
            requestChartRedraw();
        }
    }

    public void removeIndicator(String indicatorName) {
        indicators.removeIf(i -> i.getName().equals(indicatorName));
        requestChartRedraw();
    }

    public void clearIndicators() {
        indicators.clear();
        requestChartRedraw();
    }

    public List<ChartIndicator> getIndicators() {
        return List.copyOf(indicators);
    }

    public boolean hasIndicator(String indicatorName) {
        return indicators.stream().anyMatch(i -> i.getName().equals(indicatorName));
    }

    public void setBackgroundImage(@Nullable Image image) {
        this.backgroundImage = image;
        requestChartRedraw();
    }

    public void clearBackgroundImage() {
        setBackgroundImage(null);
    }

    public void setBackgroundImageOpacity(double opacity) {
        this.backgroundImageOpacity = clamp(opacity, 0.0, 1.0);
        requestChartRedraw();
    }

    private void recalculateIndicators() {
        if (indicators.isEmpty()) {
            return;
        }
        List<CandleData> candles = getAllCandleData();
        for (ChartIndicator indicator : List.copyOf(indicators)) {
            try {
                indicator.calculate(candles);
            } catch (Exception exception) {
                log.debug("Failed to calculate indicator {}", indicator.getName(), exception);
            }
        }
    }

    public void setCrosshairVisible(boolean visible) {
        showCrosshair = visible;
        if (!visible) {
            crosshairMouseX = -1;
            crosshairMouseY = -1;
        }
        requestChartRedraw();
    }

    public boolean isCrosshairVisible() {
        return showCrosshair;
    }

    /**
     * Gets the candlestick data at the given X coordinate on the canvas.
     *
     * @param canvasX the X coordinate on the canvas
     * @return the CandleData at that position, or null if no candle is at that
     *         position
     */
    private CandleData getCandleAtPosition(double canvasX) {
        if (data.isEmpty() || candleBodyWidth <= 0) {
            return null;
        }

        // Calculate which candle index this X position corresponds to
        // candleBodyWidth includes spacing, so we need to account for that
        double pixelsPerCandle = candleBodyWidth + 2; // 2 pixels for spacing
        int candleIndexOffset = (int) ((canvasX - 50) / pixelsPerCandle); // 50 is approximate left margin

        int candleIndex = firstVisibleIndex + candleIndexOffset;

        // Get the candle at this index
        if (data.size() > candleIndex && candleIndex >= 0) {
            int keyAtIndex = 0;
            int currentIndex = 0;
            for (int key : data.keySet()) {
                if (currentIndex == candleIndex) {
                    return data.get(key);
                }
                currentIndex++;
            }
        }

        return null;
    }

    public void toggleCrosshair() {
        setCrosshairVisible(!showCrosshair);
    }

    public double getLatestClosePrice() {
        CandleData last = lastValue();
        return last == null ? 0.0 : last.closePrice();
    }

    public void autoTrade() {
        setAutoTradeEnabled(!autoTradeEnabled);
    }

    public void setAutoTradeEnabled(boolean enabled) {
        autoTradeEnabled = enabled;
        showTransientNotice(enabled ? "Auto-trade enabled" : "Auto-trade disabled");
        requestChartRedraw();
    }

    public void screenshot() {
        runOnFx(() -> {
            try {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Save Chart Screenshot");
                chooser.setInitialFileName(
                        "%s-%s.png".formatted(tradePair.toString('-'), SCREENSHOT_FORMAT.format(LocalDateTime.now())));
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
                File file = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
                if (file == null)
                    return;
                SnapshotParameters parameters = new SnapshotParameters();
                parameters.setFill(Color.TRANSPARENT);
                WritableImage image = snapshot(parameters, null);
                ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(image, null), "png", file);
                showTransientNotice("Screenshot saved: %s".formatted(file.getName()));
            } catch (IOException exception) {
                log.error("Failed to save screenshot", exception);
                showErrorMessage("Screenshot failed: %s".formatted(rootMessage(exception)));
            }
        });
    }

    public void print() {
        runOnFx(() -> {
            PrinterJob job = PrinterJob.createPrinterJob();
            if (job == null) {
                showErrorMessage("No printer available.");
                return;
            }
            boolean proceed = job.showPrintDialog(getScene() == null ? null : getScene().getWindow());
            if (!proceed)
                return;
            if (job.printPage(this)) {
                job.endJob();
                showTransientNotice("Chart sent to printer.");
            } else
                showErrorMessage("Chart print failed.");
        });
    }

    private void showLoadingStatus(String message) {
        runOnFx(() -> {
            noticeClearTimer.stop();
            progressIndicator.setVisible(true);
            loadingStatusText.setFill(Color.WHITE);
            loadingStatusText.setText(message == null ? "" : message);
        });
    }

    private void showTransientNotice(String message) {
        runOnFx(() -> {
            progressIndicator.setVisible(false);
            loadingStatusText.setFill(Color.web("#fbbf24"));
            loadingStatusText.setText(message == null ? "" : message);
            noticeClearTimer.playFromStart();
        });
    }

    private void showErrorMessage(String message) {
        runOnFx(() -> {
            noticeClearTimer.stop();
            progressIndicator.setVisible(false);
            loadingStatusText.setFill(Color.web("#ff6b6b"));
            loadingStatusText.setText("Error: %s".formatted(message == null ? "Unknown" : message));
        });
    }

    private void hideLoadingIndicator() {
        runOnFx(() -> {
            progressIndicator.setVisible(false);
            if (noticeClearTimer.getStatus() != javafx.animation.Animation.Status.RUNNING)
                loadingStatusText.setText("");
        });
    }

    private String rootMessage(Throwable throwable) {
        if (throwable == null)
            return "Unknown error";
        Throwable current = throwable;
        while (current.getCause() != null)
            current = current.getCause();
        return current.getMessage() == null || current.getMessage().isBlank() ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private void runOnFx(Runnable runnable) {
        if (runnable == null)
            return;
        if (Platform.isFxApplicationThread())
            runnable.run();
        else
            Platform.runLater(runnable);
    }

    @Override
    protected void layoutChildren() {
        layoutChart();
        if (!data.isEmpty())
            drawChartContents(true);
    }

    @Override
    protected double computeMinWidth(double height) {
        return MIN_CHART_WIDTH;
    }

    @Override
    protected double computeMinHeight(double width) {
        return MIN_CHART_HEIGHT;
    }

    @Override
    protected double computePrefWidth(double height) {
        return chartWidth;
    }

    @Override
    protected double computePrefHeight(double width) {
        return chartHeight;
    }

    /**
     * Sets the callback to be invoked when a candlestick is clicked.
     *
     * @param callback Consumer that receives the clicked CandleData
     */
    public void setCandleSelectionCallback(Consumer<CandleData> callback) {
        this.candleSelectionCallback = callback;
    }

    public void dispose() {
        if (disposed)
            return;
        disposed = true;
        paging = false;
        loading.set(false);
        try {
            redrawDebouncer.stop();
            noticeClearTimer.stop();
        } catch (Exception ignored) {
        }
        try {
            if (exchange.getWebsocketClient() != null)
                exchange.getWebsocketClient().stopStreamLiveTrades(tradePair);
        } catch (Exception exception) {
            log.debug("Unable to stop live stream for {}", tradePair, exception);
        }
        shutdownExecutor(updateInProgressCandleExecutor);
        shutdownExecutor(chartLoadingExecutor);
        shutdownExecutor(chartTimeoutExecutor);
        runOnFx(() -> {
            data.clear();
            priceLines.clear();
            getChildren().clear();
        });
    }

    private void shutdownExecutor(ScheduledExecutorService executor) {
        if (executor == null)
            return;
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private class CandlePageConsumer implements Consumer<List<CandleData>> {
        @Override
        public void accept(List<CandleData> candleData) {
            if (!Platform.isFxApplicationThread()) {
                runOnFx(() -> accept(candleData));
                return;
            }
            if (data.isEmpty())
                setInitialState(candleData);
            else {
                mergeCandles(candleData);
                hideLoadingIndicator();
            }
        }
    }

    private class SizeChangeListener extends DelayedSizeChangeListener {
        SizeChangeListener(BooleanProperty gotFirstSize, ObservableValue<Number> containerWidth,
                ObservableValue<Number> containerHeight) {
            super(250, 120, gotFirstSize, containerWidth, containerHeight);
        }

        @Override
        public void resize() {
            if (disposed || canvas == null)
                return;
            layoutChart();
            if (!data.isEmpty()) {
                updateCandleWidthFromVisibleCount();
                clampVisibleWindow();
                recomputeVisiblePriceRange();
                drawChartContents(true);
            }
        }
    }

    private class MouseDraggedHandler implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent event) {
            if (disposed || paging || event.getButton() != MouseButton.PRIMARY)
                return;
            if (mousePrevX < 0) {
                mousePrevX = event.getScreenX();
                mousePrevY = event.getScreenY();
                return;
            }
            double dx = event.getScreenX() - mousePrevX;
            scrollDeltaXSum += dx;
            double slot = Math.max(4.0, canvas.getWidth() / Math.max(1, visibleCandles));
            if (Math.abs(scrollDeltaXSum) >= slot) {
                panByCandles((int) -Math.signum(scrollDeltaXSum));
                scrollDeltaXSum = 0;
            }
            mousePrevX = event.getScreenX();
            mousePrevY = event.getScreenY();
            event.consume();
        }
    }

    private class ScrollEventHandler implements EventHandler<ScrollEvent> {
        @Override
        public void handle(ScrollEvent event) {
            if (disposed || paging || event.isInertia())
                return;
            if (event.isControlDown()) {
                changeZoom(event.getDeltaY() > 0 ? ZoomDirection.IN : ZoomDirection.OUT);
                event.consume();
                return;
            }
            panByCandles(event.getDeltaY() > 0 ? -3 : 3);
            event.consume();
        }
    }

    private class KeyEventHandler implements EventHandler<KeyEvent> {
        @Override
        public void handle(KeyEvent event) {
            if (disposed)
                return;
            if (event.getCode() == KeyCode.LEFT) {
                panByCandles(-1);
                event.consume();
            } else if (event.getCode() == KeyCode.RIGHT) {
                panByCandles(1);
                event.consume();
            } else if (event.getCode() == KeyCode.HOME) {
                jumpToLatestCandle();
                event.consume();
            } else if (event.getCode() == KeyCode.F) {
                fitChart();
                event.consume();
            } else if (event.getCode() == KeyCode.C) {
                toggleCrosshair();
                event.consume();
            } else if (event.getCode() == KeyCode.F5) {
                refreshChart();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.PLUS) {
                changeZoom(ZoomDirection.IN);
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.MINUS) {
                changeZoom(ZoomDirection.OUT);
                event.consume();
            }
        }
    }

    private class MouseMovedHandler implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent event) {
            if (disposed || canvas == null)
                return;
            crosshairMouseX = clamp(event.getX(), 0, canvas.getWidth());
            crosshairMouseY = clamp(event.getY(), 0, canvas.getHeight());
            requestChartRedraw();
        }
    }

    private class UpdateInProgressCandleTask implements LiveTradesConsumer, Runnable {
        private final BlockingQueue<Trade> liveTradesQueue = new LinkedBlockingQueue<>();
        @Setter
        private volatile boolean ready;

        @Override
        public boolean containsKey(TradePair tradePair) {
            return tradePair != null && tradePair.equals(CandleStickChart.this.tradePair);
        }

        @Override
        public void remove(TradePair tradePair) {
            log.debug("Removing trade pair from chart consumer: {}", tradePair);
        }

        @Override
        public void put(TradePair tradePair) {
            log.debug("Registering trade pair for chart consumer: {}", tradePair);
        }

        @Override
        public Trade get(TradePair tradePair) {
            return null;
        }

        @Override
        public void accept(Trade trade) {
            if (trade != null)
                liveTradesQueue.offer(trade);
            log.info(liveTradesQueue.toString());
        }

        @Override
        public void acceptTrades(Trade trade) {
            accept(trade);
        }

        @Override
        public void run() {
            if (!ready || disposed)
                return;
            List<Trade> trades = new ArrayList<>();
            liveTradesQueue.drainTo(trades);
            if (!trades.isEmpty())
                requestChartRedraw();
        }
    }
}
