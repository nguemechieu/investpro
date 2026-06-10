package org.investpro.ui.charts;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import org.investpro.exchange.consumers.UiExchangeStreamConsumer;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.indicators.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.print.PrinterJob;
import javafx.scene.Cursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.Axis;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
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
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.investpro.data.CandleData;
import org.investpro.data.CandleDataPager;
import org.investpro.enums.TradingSessionStatus;
import org.investpro.exchange.Exchange;
import org.investpro.models.market.NewsEvent;
import org.investpro.models.trading.LiveTradesConsumer;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;
import org.investpro.spi.PluginIndicatorFactory;
import org.investpro.spi.PluginRegistry;
import org.investpro.ui.tools.ChartOptions;
import org.investpro.ui.tools.InstantAxisFormatter;
import org.investpro.ui.tools.MoneyAxisFormatter;
import org.investpro.ui.tools.StableTicksAxis;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.DelayedSizeChangeListener;
import org.investpro.utils.FXUtils;
import org.investpro.utils.LogOnExceptionThreadFactory;
import org.investpro.utils.ZoomDirection;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

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
 * This chart should be created by {@link ChartContainer}.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
@ToString(callSuper = true)

public class CandleStickChart extends Region {
    @Getter
    public enum ChartType {
        LINE("Line"),
        BAR_OHLC("Bar (OHLC)"),
        CANDLESTICK("Candlestick"),
        HEIKIN_ASHI("Heikin Ashi"),
        RENKO("Renko"),
        POINT_AND_FIGURE("Point & Figure"),
        VOLUME("Volume");

        private final String displayName;

        ChartType(String displayName) {
            this.displayName = displayName;
        }

        public ChartType next() {
            ChartType[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

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

    private static final int MAX_VISIBLE_CANDLES = 120;

    private static final int MIN_BODY_WIDTH = 2;
    private static final int MAX_BODY_WIDTH = 28;
    private static final int DEFAULT_BODY_WIDTH = 8;
    private static final int CANDLE_GAP = 2;

    private static final double VOLUME_AREA_RATIO = 0.20;
    private static final double PRICE_PADDING_RATIO = 0.12;
    private static final double MAX_LIVE_TRADE_PRICE_DEVIATION_RATIO = 0.12;
    private static final long LOADING_TIMEOUT_MS = 30_000L;
    private static final int MAX_PERSISTED_DRAWINGS = 250;
    private static final String DRAWING_FORMAT_VERSION = "v1";
    private static final Preferences CHART_PREFERENCES = Preferences.userRoot().node("org/investpro/chart");

    // ============================================================
    // Modern InvestPro chart theme
    // ============================================================
    private static final Color THEME_BG_TOP = Color.rgb(6, 10, 18);
    private static final Color THEME_BG_BOTTOM = Color.rgb(13, 20, 33);
    private static final Color THEME_PANEL = Color.rgb(15, 23, 42, 0.92);
    private static final Color THEME_GRID_MAJOR = Color.rgb(148, 163, 184, 0.18);
    private static final Color THEME_GRID_MINOR = Color.rgb(148, 163, 184, 0.08);
    private static final Color THEME_TEXT = Color.rgb(226, 232, 240);
    private static final Color THEME_MUTED = Color.rgb(148, 163, 184);
    private static final Color THEME_BORDER = Color.rgb(51, 65, 85, 0.85);
    private static final Color THEME_ACCENT = Color.rgb(56, 189, 248);
    private static final Color THEME_BUY = Color.rgb(34, 197, 94);
    private static final Color THEME_SELL = Color.rgb(239, 68, 68);

    private final Exchange exchange;
    private final TradePair tradePair;
    private final boolean liveSyncing;
    private final int secondsPerCandle;
    private final String telegramToken;
    private final TradingService tradingService;

    private final ChartOptions chartOptions;
    private final String chartSettingsKeyPrefix;
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
    private final ToggleGroup leftToolToggleGroup = new ToggleGroup();
    private final EnumMap<ChartInteractionTool, ToggleButton> leftToolButtons = new EnumMap<>(
            ChartInteractionTool.class);
    private ScrollPane leftToolPalette;

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
    private PriceLine currentMarketPriceLine;
    private final List<ChartDrawing> chartDrawings = new ArrayList<>();
    private ChartInteractionTool activeInteractionTool = ChartInteractionTool.CURSOR;
    private ChartDrawing drawingPreview;
    private ChartPoint drawingAnchor;
    private int selectedDrawingIndex = -1;
    private ChartPoint movingAnchor;
    private ChartDrawing movingDrawingOriginal;
    private final List<Indicator> indicators = new ArrayList<>();
    private final List<ChartEvent> chartEvents = Collections.synchronizedList(new ArrayList<>());
    private boolean showChartEvents = true; // Toggle for displaying events
    private ChartType chartType = ChartType.CANDLESTICK;
    private Image backgroundImage;
    private double backgroundImageOpacity = 0.22;

    // Trade visualization overlay for displaying trade markers, order levels, and
    // P&L zones
    private TradeVisualizationOverlay tradeVisualizationOverlay;
    private Canvas tradeOverlayCanvas;
    private boolean showTradeOverlay = true; // Toggle for displaying trades

    /**
     * Sets the callback to be invoked when a candlestick is clicked.
     */
    // Candle selection callback
    private Consumer<CandleData> candleSelectionCallback;

    private Canvas canvas;
    private GraphicsContext graphicsContext;
    private StackPane chartStackPane;

    private volatile boolean disposed;
    private volatile boolean restoringPreferences;
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
    private double componentWidth;

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
        this.chartSettingsKeyPrefix = buildChartSettingsPrefix();

        this.chartOptions = new ChartOptions();
        this.candleDataPager = new CandleDataPager(this, candleDataSupplier);
        this.candlePageConsumer = new CandlePageConsumer();

        restoreChartPreferences();

        // Initialize trade visualization overlay
        this.tradeVisualizationOverlay = new TradeVisualizationOverlay();

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
        configureLeftToolPalette();

        getChildren().addAll(xAxis, yAxis, extraAxis, extraAxisExtension, leftToolPalette);

        initializeFirstLayout(containerWidth, containerHeight);
        initializeOptionListeners();

        if (liveSyncing) {
            initializeLiveSyncing();
        }
    }

    private String buildChartSettingsPrefix() {
        String exchangeName = sanitizeForPreferenceKey(exchange == null ? "unknown" : exchange.getName());
        String symbol = sanitizeForPreferenceKey(tradePair == null ? "unknown" : tradePair.toString('-'));
        return exchangeName + "." + symbol + "." + secondsPerCandle;
    }

    private String sanitizeForPreferenceKey(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private String prefKey(String suffix) {
        return chartSettingsKeyPrefix + "." + suffix;
    }

    private void restoreChartPreferences() {
        restoringPreferences = true;
        try {
            chartOptions.setVerticalGridLinesVisible(CHART_PREFERENCES.getBoolean(prefKey("grid.vertical"),
                    chartOptions.isVerticalGridLinesVisible()));
            chartOptions.setHorizontalGridLinesVisible(CHART_PREFERENCES.getBoolean(prefKey("grid.horizontal"),
                    chartOptions.isHorizontalGridLinesVisible()));
            chartOptions
                    .setShowVolume(CHART_PREFERENCES.getBoolean(prefKey("showVolume"), chartOptions.isShowVolume()));
            chartOptions.setAlignOpenClose(
                    CHART_PREFERENCES.getBoolean(prefKey("alignOpenClose"), chartOptions.isAlignOpenClose()));
            chartOptions.setShowNewsEvents(
                    CHART_PREFERENCES.getBoolean(prefKey("showNewsEvents"), chartOptions.isShowNewsEvents()));

            String chartTypeName = CHART_PREFERENCES.get(prefKey("chartType"), ChartType.CANDLESTICK.name());
            try {
                chartType = ChartType.valueOf(chartTypeName);
            } catch (IllegalArgumentException ignored) {
                chartType = ChartType.CANDLESTICK;
            }

            showCrosshair = CHART_PREFERENCES.getBoolean(prefKey("crosshair"), showCrosshair);
            showPriceLines = CHART_PREFERENCES.getBoolean(prefKey("showPriceLines"), showPriceLines);
            showChartEvents = CHART_PREFERENCES.getBoolean(prefKey("showChartEvents"), showChartEvents);
            showTradeOverlay = CHART_PREFERENCES.getBoolean(prefKey("showTradeOverlay"), showTradeOverlay);
            autoTradeEnabled = CHART_PREFERENCES.getBoolean(prefKey("autoTrade"), autoTradeEnabled);
            backgroundImageOpacity = clamp(
                    CHART_PREFERENCES.getDouble(prefKey("backgroundOpacity"), backgroundImageOpacity),
                    0.0,
                    1.0);

            visibleCandles = clampInt(
                    CHART_PREFERENCES.getInt(prefKey("visibleCandles"), visibleCandles),
                    MIN_VISIBLE_CANDLES,
                    MAX_VISIBLE_CANDLES);

            String interactionTool = CHART_PREFERENCES.get(prefKey("interactionTool"),
                    ChartInteractionTool.CURSOR.name());
            try {
                activeInteractionTool = ChartInteractionTool.valueOf(interactionTool);
            } catch (IllegalArgumentException ignored) {
                activeInteractionTool = ChartInteractionTool.CURSOR;
            }

            restorePersistedDrawings();
        } finally {
            restoringPreferences = false;
        }
    }

    private void persistChartPreferences() {
        if (restoringPreferences) {
            return;
        }

        CHART_PREFERENCES.putBoolean(prefKey("grid.vertical"), chartOptions.isVerticalGridLinesVisible());
        CHART_PREFERENCES.putBoolean(prefKey("grid.horizontal"), chartOptions.isHorizontalGridLinesVisible());
        CHART_PREFERENCES.putBoolean(prefKey("showVolume"), chartOptions.isShowVolume());
        CHART_PREFERENCES.putBoolean(prefKey("alignOpenClose"), chartOptions.isAlignOpenClose());
        CHART_PREFERENCES.putBoolean(prefKey("showNewsEvents"), chartOptions.isShowNewsEvents());
        CHART_PREFERENCES.put(prefKey("chartType"), chartType.name());

        CHART_PREFERENCES.putBoolean(prefKey("crosshair"), showCrosshair);
        CHART_PREFERENCES.putBoolean(prefKey("showPriceLines"), showPriceLines);
        CHART_PREFERENCES.putBoolean(prefKey("showChartEvents"), showChartEvents);
        CHART_PREFERENCES.putBoolean(prefKey("showTradeOverlay"), showTradeOverlay);
        CHART_PREFERENCES.putBoolean(prefKey("autoTrade"), autoTradeEnabled);
        CHART_PREFERENCES.putDouble(prefKey("backgroundOpacity"), backgroundImageOpacity);
        CHART_PREFERENCES.putInt(prefKey("visibleCandles"), visibleCandles);
        CHART_PREFERENCES.put(prefKey("interactionTool"), activeInteractionTool == null
                ? ChartInteractionTool.CURSOR.name()
                : activeInteractionTool.name());

        persistChartDrawings();
    }

    private void restorePersistedDrawings() {
        chartDrawings.clear();

        String serialized = CHART_PREFERENCES.get(prefKey("drawings"), "");
        if (serialized == null || serialized.isBlank()) {
            return;
        }

        String[] tokens = serialized.split(";");
        for (String token : tokens) {
            if (chartDrawings.size() >= MAX_PERSISTED_DRAWINGS) {
                break;
            }

            ChartDrawing drawing = parsePersistedDrawing(token);
            if (drawing != null) {
                chartDrawings.add(drawing);
            }
        }
    }

    private void persistChartDrawings() {
        if (restoringPreferences) {
            return;
        }

        if (chartDrawings.isEmpty()) {
            CHART_PREFERENCES.remove(prefKey("drawings"));
            return;
        }

        StringJoiner joiner = new StringJoiner(";");
        int persistedCount = 0;

        for (ChartDrawing drawing : chartDrawings) {
            if (drawing == null || drawing.preview() || !drawing.isDrawable()) {
                continue;
            }

            joiner.add(serializeDrawing(drawing));
            persistedCount++;

            if (persistedCount >= MAX_PERSISTED_DRAWINGS) {
                break;
            }
        }

        if (persistedCount == 0) {
            CHART_PREFERENCES.remove(prefKey("drawings"));
            return;
        }

        CHART_PREFERENCES.put(prefKey("drawings"), joiner.toString());
    }

    private String serializeDrawing(ChartDrawing drawing) {
        return DRAWING_FORMAT_VERSION
                + "|" + drawing.type().name()
                + "|" + drawing.startTime()
                + "|" + drawing.startPrice()
                + "|" + drawing.endTime()
                + "|" + drawing.endPrice();
    }

    private ChartDrawing parsePersistedDrawing(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String[] parts = token.split("\\|", -1);
        if (parts.length != 5 && parts.length != 6) {
            return null;
        }

        try {
            int offset = 0;
            if (parts.length == 6) {
                if (!DRAWING_FORMAT_VERSION.equals(parts[0])) {
                    return null;
                }
                offset = 1;
            }

            ChartInteractionTool type = ChartInteractionTool.valueOf(parts[offset]);
            if (!type.isDrawingTool()) {
                return null;
            }

            long startTime = Long.parseLong(parts[offset + 1]);
            double startPrice = Double.parseDouble(parts[offset + 2]);
            long endTime = Long.parseLong(parts[offset + 3]);
            double endPrice = Double.parseDouble(parts[offset + 4]);

            ChartDrawing drawing = ChartDrawing.persisted(type, startTime, startPrice, endTime, endPrice);
            return drawing.isDrawable() ? drawing : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void configureStyle() {
        getStyleClass().add("candle-chart");
        setFocusTraversable(true);
        setMinSize(MIN_CHART_WIDTH, MIN_CHART_HEIGHT);
    }

    private void configureAxes() {
        String axisStyle = """
                -fx-background-color: #0f172a;
                -fx-border-color: #334155;
                -fx-tick-label-fill: #cbd5e1;
                """;

        Font axisFont = Font.font(FXUtils.getMonospacedFont(), 12);

        // Resolve instrument-level precision (OANDA displayPrecision or equivalent).
        // This ensures price axis tick labels match the quoted decimal places exactly.
        int pricePrecision = exchange.getDisplayPrecision(tradePair);

        xAxis.setManaged(false);
        yAxis.setManaged(false);
        extraAxis.setManaged(false);
        extraAxisExtension.setManaged(false);

        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
        extraAxis.setAnimated(false);

        extraAxis.setSide(Side.LEFT);
        xAxis.setForceZeroInRange(false);
        yAxis.setForceZeroInRange(false);

        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);
        extraAxis.setAutoRanging(false);

        /*
         * X axis is time-like numeric data, so do not force zero.
         */
        xAxis.setForceZeroInRange(false);

        /*
         * Right-side price axis.
         * Price should not force zero because forex/crypto/stocks need tight scaling.
         */
        yAxis.configureForPriceAxis();
        yAxis.setAnimationDurationMillis(2000);
        yAxis.setAnimated(false);
        xAxis.setAnimationDurationMillis(2000);
        xAxis.setAnimated(false);

        /*
         * Left-side volume axis.
         * Volume should start from zero.
         */
        extraAxis.configureForVolumeAxis();

        xAxis.setSide(Side.BOTTOM);
        yAxis.setSide(Side.RIGHT);
        extraAxis.setSide(Side.LEFT);

        xAxis.setTickLabelFormatter(
                InstantAxisFormatter.of(DateTimeFormatter.ofPattern("MM-dd HH:mm")));

        // Price axis: use instrument display precision so labels always match quoted
        // decimals.
        yAxis.setTickLabelFormatter(
                new MoneyAxisFormatter(tradePair.getCounterCurrency(), pricePrecision));
        yAxis.setPrecision(pricePrecision);

        // Extra (volume/base) axis: use base currency fractional digits.
        extraAxis.setTickLabelFormatter(
                new MoneyAxisFormatter(tradePair.getCounterCurrency()));

        xAxis.setStyle(axisStyle);
        yAxis.setStyle(axisStyle);
        extraAxis.setStyle(axisStyle);

        xAxis.setTickLabelFill(THEME_MUTED);
        yAxis.setTickLabelFill(THEME_TEXT);
        extraAxis.setTickLabelFill(THEME_MUTED);

        xAxis.setTickLabelFont(axisFont);
        yAxis.setTickLabelFont(axisFont);
        extraAxis.setTickLabelFont(axisFont);

        xAxis.setTickLabelsVisible(true);
        xAxis.setTickMarkVisible(true);
        xAxis.setMinorTickVisible(false);

        yAxis.setTickLabelsVisible(true);
        yAxis.setTickMarkVisible(true);
        yAxis.setMinorTickVisible(false);

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

    private void configureLeftToolPalette() {
        VBox buttonColumn = new VBox(6);
        buttonColumn.setPadding(new Insets(8));
        buttonColumn.setStyle("-fx-background-color: transparent;");

        buttonColumn.getChildren().addAll(
                createToolToggle("▣", "Cursor", this::activateCursorTool, ChartInteractionTool.CURSOR),
                createToolAction("+", "Crosshair", this::toggleCrosshair),
                new Separator(),
                createToolToggle("/", "Trendline", this::activateTrendlineTool, ChartInteractionTool.TRENDLINE),
                createToolToggle("─", "Horizontal Line", this::activateHorizontalLineTool,
                        ChartInteractionTool.HORIZONTAL_LINE),
                createToolToggle("│", "Vertical Line", this::activateVerticalLineTool,
                        ChartInteractionTool.VERTICAL_LINE),
                createToolToggle("▭", "Rectangle", this::activateRectangleTool, ChartInteractionTool.RECTANGLE),
                createToolToggle("△", "Triangle", this::activateTriangleTool, ChartInteractionTool.TRIANGLE),
                createToolToggle("◯", "Circle", this::activateCircleTool, ChartInteractionTool.CIRCLE),
                createToolToggle("F", "Fibonacci", this::activateFibonacciTool, ChartInteractionTool.FIBONACCI),
                createToolToggle("M", "Measure", this::activateMeasureTool, ChartInteractionTool.MEASURE),
                createToolToggle("R", "Long/Short (Risk/Reward)", this::activateRiskRewardTool,
                        ChartInteractionTool.RISK_REWARD),
                createToolToggle("E", "Erase Objects", this::activateEraseObjectsTool,
                        ChartInteractionTool.ERASE_OBJECTS),
                new Separator(),
                createToolAction("T", "Text (coming soon)", () -> showTransientNotice("Text tool is coming soon")),
                createToolAction("A", "Arrow (coming soon)", () -> showTransientNotice("Arrow tool is coming soon")),
                createToolAction("B", "Brush (coming soon)", () -> showTransientNotice("Brush tool is coming soon")),
                createToolAction("P", "Path (coming soon)", () -> showTransientNotice("Path tool is coming soon")),
                createToolAction("G", "Magnet (coming soon)", () -> showTransientNotice("Magnet mode is coming soon")),
                new Separator(),
                createToolAction("C", "Clear drawings", this::clearChartDrawings));

        leftToolPalette = new ScrollPane(buttonColumn);
        leftToolPalette.setManaged(false);
        leftToolPalette.setFitToWidth(true);
        leftToolPalette.setFitToHeight(true);
        leftToolPalette.setPannable(true);
        leftToolPalette.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftToolPalette.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        leftToolPalette.setFocusTraversable(false);
        leftToolPalette.setStyle("""
                -fx-background-color: rgba(15, 23, 42, 0.92);
                -fx-background-insets: 0;
                -fx-border-color: rgba(71, 85, 105, 0.85);
                -fx-border-width: 1;
                -fx-border-radius: 10;
                -fx-background-radius: 10;
                """);

        syncLeftToolPaletteSelection();
    }

    private @NonNull ToggleButton createToolToggle(
            String label,
            String tooltip,
            Runnable action,
            ChartInteractionTool tool) {
        ToggleButton button = new ToggleButton(label);
        styleToolButton(button, tooltip);
        button.setToggleGroup(leftToolToggleGroup);
        button.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
        if (tool != null) {
            leftToolButtons.put(tool, button);
        }
        return button;
    }

    private Button createToolAction(String label, String tooltip, Runnable action) {
        Button button = new Button(label);
        styleToolButton(button, tooltip);
        button.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
        return button;
    }

    private void styleToolButton(ButtonBase button, String tooltip) {
        button.setMinSize(30, 30);
        button.setPrefSize(30, 30);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(tooltip));
        button.setStyle("""
                -fx-background-color: rgba(30, 41, 59, 0.92);
                -fx-text-fill: #e2e8f0;
                -fx-font-size: 12px;
                -fx-font-weight: bold;
                -fx-background-radius: 7;
                -fx-border-color: rgba(71, 85, 105, 0.75);
                -fx-border-width: 1;
                -fx-border-radius: 7;
                -fx-cursor: hand;
                """);
    }

    private void syncLeftToolPaletteSelection() {
        if (leftToolButtons.isEmpty()) {
            return;
        }

        leftToolButtons.values().forEach(button -> button.setSelected(false));
        ToggleButton selected = leftToolButtons.get(activeInteractionTool);
        if (selected != null) {
            selected.setSelected(true);
        }
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

            // Create overlay canvas for trade visualization
            tradeOverlayCanvas = new Canvas(Math.max(1, chartWidth - LEFT_AXIS_WIDTH - RIGHT_AXIS_WIDTH),
                    Math.max(1, chartHeight - TIME_AXIS_HEIGHT));
            tradeOverlayCanvas.setMouseTransparent(true);

            chartStackPane = new StackPane(canvas, tradeOverlayCanvas, loadingIndicatorContainer);
            chartStackPane.setManaged(false);
            getChildren().addFirst(chartStackPane);

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
                .addListener((observable, oldValue, newValue) -> {
                    persistChartPreferences();
                    requestChartRedraw();
                });
        chartOptions.verticalGridLinesVisibleProperty()
                .addListener((observable, oldValue, newValue) -> {
                    persistChartPreferences();
                    requestChartRedraw();
                });
        chartOptions.showVolumeProperty().addListener((observable, oldValue, newValue) -> {
            persistChartPreferences();
            layoutChart();
            requestChartRedraw();
        });
        chartOptions.alignOpenCloseProperty().addListener((observable,
                oldValue, newValue) -> {
            persistChartPreferences();
            requestChartRedraw();
        });
        chartOptions.showNewsEventsProperty().addListener((observable, oldValue, newValue) -> {
            persistChartPreferences();
            requestChartRedraw();
        });
    }

    private void initializeEventHandlers() {
        if (canvas == null) {
            return;
        }

        canvas.setOnMouseClicked(event -> {
            canvas.requestFocus();
            if (isDrawingToolActive()) {
                event.consume();
                return;
            }
            if (event.getButton() == MouseButton.PRIMARY) {
                if (selectDrawingAt(event.getX(), event.getY())) {
                    event.consume();
                    return;
                }
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
                if (event.getButton() == MouseButton.PRIMARY && eraseDrawing(event)) {
                    event.consume();
                    return;
                }
                if (event.getButton() == MouseButton.PRIMARY && beginDrawing(event)) {
                    event.consume();
                    return;
                }
                if (event.getButton() == MouseButton.PRIMARY && beginMoveDrawing(event)) {
                    event.consume();
                    return;
                }
                mousePrevX = event.getScreenX();
                mousePrevY = event.getScreenY();
                chartStackPane.requestFocus();
                canvas.requestFocus();
            });
            chartStackPane.addEventFilter(MouseEvent.MOUSE_RELEASED,
                    event -> {
                        if (event.getButton() == MouseButton.PRIMARY && finishDrawing(event)) {
                            event.consume();
                        } else if (event.getButton() == MouseButton.PRIMARY && finishMoveDrawing()) {
                            event.consume();
                        }
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
            if (disposed) {
                return;
            }

            boolean streamingStarted = false;

            try {
                var websocketClient = exchange.getWebsocketClient();

                CountDownLatch initLatch = websocketClient == null
                        ? null
                        : websocketClient.getInitializationLatch();

                if (websocketClient != null
                        && initLatch != null
                        && initLatch.await(10, SECONDS)
                        && websocketClient.supportsStreamingTrades(tradePair)) {

                    ExchangeStreamConsumer exchangeS = new UiExchangeStreamConsumer()
                            .onTradeUpdate(updateInProgressCandleTask::accept)
                            .onStatus(message -> log.debug("Chart stream status for {}: {}", tradePair, message))
                            .onError((exchangeName, throwable) -> log.warn(
                                    "Chart live trade stream error. exchange={} pair={} error={}",
                                    exchangeName,
                                    tradePair,
                                    throwable == null ? "unknown" : throwable.getMessage(),
                                    throwable));

                    websocketClient.streamLiveTrades(tradePair, exchangeS);
                    streamingStarted = true;

                    log.info("Live trade WebSocket stream started for {}", tradePair);
                }

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for websocket initialization for {}", tradePair);

            } catch (Exception exception) {
                log.warn(
                        "WebSocket live stream failed for {}; polling fallback will be used.",
                        tradePair,
                        exception);
            }

            if (!streamingStarted && !disposed) {
                startPollingForLiveTrades();
            }

            if (!disposed) {
                updateInProgressCandleExecutor.scheduleAtFixedRate(
                        updateInProgressCandleTask,
                        250,
                        250,
                        TimeUnit.MILLISECONDS);
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

    private static final int MIN_VISIBLE_CANDLES = 30;
    private static final int TARGET_VISIBLE_CANDLES = 70;
    private static final int LARGE_SCREEN_VISIBLE_CANDLES = 90;
    private static final int SMALL_SCREEN_VISIBLE_CANDLES = 55;

    private int visibleCandleHint = TARGET_VISIBLE_CANDLES;

    private void fitLatestReadable() {
        if (data.isEmpty() || canvas == null) {
            return;
        }

        int totalCandles = data.size();
        double canvasWidth = Math.max(1.0, canvas.getWidth());

        int targetVisibleCandles = calculateReadableVisibleCandles(totalCandles, canvasWidth);

        setVisibleCandleCount(targetVisibleCandles);

        firstVisibleIndex = Math.max(0, totalCandles - visibleCandles);

        updateXAxisBoundsFromVisibleWindow();
        recomputeVisiblePriceRange();
        drawChartContents(true);
    }

    private int calculateReadableVisibleCandles(int totalCandles, double canvasWidth) {
        if (totalCandles <= 0) {
            return 0;
        }

        int target;

        if (totalCandles <= MIN_VISIBLE_CANDLES) {
            target = totalCandles;
        } else if (canvasWidth > 1350) {
            target = LARGE_SCREEN_VISIBLE_CANDLES;
        } else if (canvasWidth < 650) {
            target = SMALL_SCREEN_VISIBLE_CANDLES;
        } else {
            target = TARGET_VISIBLE_CANDLES;
        }

        visibleCandleHint = Math.min(target, totalCandles);
        return visibleCandleHint;
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

    private void setVisibleCandleCount(int requestedVisible) {
        int previousVisible = visibleCandles;
        visibleCandles = Math.max(1, Math.min(data.size(), requestedVisible));
        if (previousVisible != visibleCandles) {
            persistChartPreferences();
        }
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
        CandleData first = visible.getFirst();
        CandleData last = visible.getLast();
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
        recomputeVisiblePriceRange(visibleCandlesSnapshot());
    }

    private void recomputeVisiblePriceRange(List<CandleData> visible) {
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
            delta = minPriceRangeFor(max);
        double padding = delta * PRICE_PADDING_RATIO;
        visibleMinPrice = min > 0.0 ? Math.max(minPriceFloor(min), min - padding) : min - padding;
        visibleMaxPrice = max + padding;
        if (visibleMaxPrice <= visibleMinPrice) {
            double expansion = minPriceRangeFor(max);
            visibleMinPrice = min > 0.0 ? Math.max(minPriceFloor(min), min - expansion) : min - expansion;
            visibleMaxPrice = max + expansion;
        }
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
        if (tradeOverlayCanvas != null) {
            tradeOverlayCanvas.setWidth(canvasW);
            tradeOverlayCanvas.setHeight(canvasH);
        }
        chartStackPane.resizeRelocate(canvasX, 0, canvasW, canvasH);
        chartStackPane.setMinSize(canvasW, canvasH);
        chartStackPane.setPrefSize(canvasW, canvasH);
        chartStackPane.setMaxSize(canvasW, canvasH);
        xAxis.resizeRelocate(canvasX, canvasH, canvasW, TIME_AXIS_HEIGHT);
        double pricePlotH = pricePlotHeight();
        yAxis.resizeRelocate(canvasX + canvasW, 0, RIGHT_AXIS_WIDTH, pricePlotH);
        double volumeH = volumeAreaHeight();
        double volumeY = volumeTopY();
        extraAxis.resizeRelocate(0, volumeY, leftAxisWidth, volumeH);
        extraAxis.setVisible(chartOptions.isShowVolume());
        if (leftToolPalette != null) {
            if (chartOptions.isShowVolume() && leftAxisWidth > 0) {
                double toolX = 4;
                double toolY = 8;
                double toolW = Math.max(34, leftAxisWidth - 8);
                double toolH = Math.max(120, volumeY - 12);
                leftToolPalette.setVisible(true);
                leftToolPalette.resizeRelocate(toolX, toolY, toolW, toolH);
            } else {
                leftToolPalette.setVisible(false);
            }
        }
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
        List<CandleData> visibleRaw = visibleCandlesSnapshot();
        if (visibleRaw.isEmpty()) {
            drawNoDataOverlay();
            return;
        }
        List<CandleData> visible = buildRenderableCandles(visibleRaw);
        if (visible.isEmpty()) {
            drawNoDataOverlay();
            return;
        }
        // Calculate current market regime
        calculateMarketRegime();
        recomputeVisiblePriceRange(visible);
        drawTradingBackground();
        drawGridLines();
        double volumeScale = volumeAreaHeight() / visibleMaxVolume;
        int highIndex = -1, lowIndex = -1;
        double high = Double.NEGATIVE_INFINITY, low = Double.POSITIVE_INFINITY;

        switch (chartType) {
            case LINE -> drawLineSeries(visible, volumeScale, true);
            case BAR_OHLC -> drawOhlcBars(visible, volumeScale);
            case VOLUME -> drawVolumeDominantSeries(visible);
            default -> drawCandlesByType(visible, volumeScale);
        }

        for (int i = 0; i < visible.size(); i++) {
            CandleData candle = visible.get(i);
            if (candle == null || candle.placeHolder()) {
                continue;
            }
            if (candle.highPrice() > high) {
                high = candle.highPrice();
                highIndex = i;
            }
            if (candle.lowPrice() < low) {
                low = candle.lowPrice();
                lowIndex = i;
            }
        }
        drawHighLowMarkers(high, low, highIndex, lowIndex);
        drawChartHeader(visible.getLast());
        drawMarketRegime();
        if (showPriceLines)
            drawPriceLines();
        drawChartDrawings();
        drawIndicators();
        if (showChartEvents)
            drawChartEvents();
        drawCurrentPriceBadge(visible.getLast());
        if (showCrosshair && crosshairMouseX >= 0 && crosshairMouseY >= 0)
            drawCrosshair();
        drawScrollbar();
        drawScrollPosition();

        // Render trade visualization overlay
        if (showTradeOverlay && tradeOverlayCanvas != null && tradeVisualizationOverlay != null) {
            updateTradeOverlayDimensions(visible);
            tradeVisualizationOverlay.render(tradeOverlayCanvas, tradeOverlayCanvas.getGraphicsContext2D());
        }
    }

    private void clearCanvas() {
        graphicsContext.setFill(Color.web("#050b14"));
        graphicsContext.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private void drawTradingBackground() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        graphicsContext.setFill(THEME_BG_TOP);
        graphicsContext.fillRect(0, 0, w, h);
        graphicsContext.setFill(THEME_BG_BOTTOM);
        graphicsContext.fillRect(0, h * 0.42, w, h * 0.58);
        drawBackgroundImage();
        graphicsContext.setFill(THEME_PANEL);
        graphicsContext.fillRoundRect(8, 8, Math.max(1, w - 16), HEADER_HEIGHT - 10, 14, 14);
        graphicsContext.setStroke(THEME_BORDER);
        graphicsContext.setLineWidth(1.0);
        graphicsContext.strokeRoundRect(8, 8, Math.max(1, w - 16), HEADER_HEIGHT - 10, 14, 14);
        graphicsContext.setStroke(Color.rgb(125, 211, 252, 0.16));
        graphicsContext.strokeLine(18, 10, w - 18, 10);
        if (chartOptions.isShowVolume()) {
            double volumeTop = volumeTopY();
            graphicsContext.setFill(Color.rgb(15, 23, 42, 0.62));
            graphicsContext.fillRoundRect(8, volumeTop, Math.max(1, w - 16), h - volumeTop - 12, 12, 12);
            graphicsContext.setStroke(Color.rgb(51, 65, 85, 0.65));
            graphicsContext.strokeLine(12, volumeTop, w - 12, volumeTop);
        }
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
            for (Axis.TickMark<Number> tick : yAxis.getTickMarks()) {
                double y = snapPixel(priceToY(tick.getValue().doubleValue()));
                if (y < 0 || y > pricePlotHeight()) {
                    continue;
                }
                graphicsContext.setStroke(THEME_GRID_MAJOR);
                graphicsContext.strokeLine(0, y, canvas.getWidth(), y);
            }
        }
        if (chartOptions.isVerticalGridLinesVisible()) {
            int step = Math.max(1, visibleCandles / 8);
            for (int i = 0; i < visibleCandles; i += step) {
                double x = candleCenterX(i);
                graphicsContext.setStroke(i % (step * 2) == 0 ? THEME_GRID_MAJOR : THEME_GRID_MINOR);
                graphicsContext.strokeLine(x, HEADER_HEIGHT, x, pricePlotHeight());
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

    private void drawCandlesByType(List<CandleData> visible, double volumeScale) {
        double lastClose = -1.0;
        for (int i = 0; i < visible.size(); i++) {
            CandleData candle = visible.get(i);
            if (candle == null) {
                continue;
            }
            drawCandle(candle, i, volumeScale, lastClose);
            lastClose = candle.closePrice();
        }
    }

    private void drawOhlcBars(List<CandleData> visible, double volumeScale) {
        double slot = Math.max(1.0, canvas.getWidth() / Math.max(1, visibleCandles));
        double tick = Math.max(2.0, Math.min(7.0, slot * 0.32));
        for (int i = 0; i < visible.size(); i++) {
            CandleData candle = visible.get(i);
            if (candle == null || candle.placeHolder()) {
                continue;
            }
            double x = snapPixel(candleCenterX(i));
            double openY = snapPixel(priceToY(candle.openPrice()));
            double closeY = snapPixel(priceToY(candle.closePrice()));
            double highY = snapPixel(priceToY(candle.highPrice()));
            double lowY = snapPixel(priceToY(candle.lowPrice()));
            Paint stroke = candle.closePrice() < candle.openPrice() ? BEAR_CANDLE_BORDER_COLOR
                    : BULL_CANDLE_BORDER_COLOR;
            graphicsContext.setStroke(stroke);
            graphicsContext.setLineWidth(1.3);
            graphicsContext.strokeLine(x, highY, x, lowY);
            graphicsContext.strokeLine(x - tick, openY, x, openY);
            graphicsContext.strokeLine(x, closeY, x + tick, closeY);

            if (chartOptions.isShowVolume()) {
                drawVolumeBar(candle, x - 1, 2, stroke, volumeScale);
            }
        }
    }

    private void drawLineSeries(List<CandleData> visible, double volumeScale, boolean withVolume) {
        if (visible.size() < 2) {
            return;
        }
        graphicsContext.setStroke(THEME_ACCENT);
        graphicsContext.setLineWidth(1.8);

        double previousX = -1;
        double previousY = -1;
        for (int i = 0; i < visible.size(); i++) {
            CandleData candle = visible.get(i);
            if (candle == null || candle.placeHolder()) {
                continue;
            }
            double x = candleCenterX(i);
            double y = priceToY(candle.closePrice());
            if (previousX >= 0) {
                graphicsContext.strokeLine(previousX, previousY, x, y);
            }
            previousX = x;
            previousY = y;

            if (withVolume && chartOptions.isShowVolume()) {
                drawVolumeBar(candle, x - 1, 2, Color.rgb(56, 189, 248, 0.7), volumeScale);
            }
        }
    }

    private void drawVolumeDominantSeries(List<CandleData> visible) {
        double baseY = canvas.getHeight() - 2;
        double areaTop = HEADER_HEIGHT + 16;
        double areaHeight = Math.max(1.0, baseY - areaTop);
        double slot = Math.max(1.0, canvas.getWidth() / Math.max(1, visibleCandles));
        double barWidth = Math.max(1.0, Math.min(slot - 1.0, 8.0));

        for (int i = 0; i < visible.size(); i++) {
            CandleData candle = visible.get(i);
            if (candle == null || candle.placeHolder()) {
                continue;
            }
            double x = candleCenterX(i) - (barWidth / 2.0);
            double ratio = Math.max(0.0, Math.min(1.0, candle.volume() / visibleMaxVolume));
            double h = Math.max(1.0, ratio * areaHeight);
            Paint fill = candle.closePrice() < candle.openPrice() ? Color.rgb(239, 68, 68, 0.74)
                    : Color.rgb(34, 197, 94, 0.74);
            graphicsContext.setFill(fill);
            graphicsContext.fillRect(x, baseY - h, barWidth, h);
        }

        drawLineSeries(visible, 1.0, false);
    }

    private List<CandleData> buildRenderableCandles(List<CandleData> visibleRaw) {
        if (visibleRaw == null || visibleRaw.isEmpty()) {
            return List.of();
        }
        return switch (chartType) {
            case HEIKIN_ASHI -> buildHeikinAshiCandles(visibleRaw);
            case RENKO -> buildRenkoCandles(visibleRaw);
            case POINT_AND_FIGURE -> buildPointAndFigureCandles(visibleRaw);
            default -> visibleRaw;
        };
    }

    private List<CandleData> buildHeikinAshiCandles(List<CandleData> source) {
        List<CandleData> result = new ArrayList<>(source.size());
        double previousHaOpen = 0.0;
        double previousHaClose = 0.0;
        for (int i = 0; i < source.size(); i++) {
            CandleData candle = source.get(i);
            if (candle == null) {
                continue;
            }
            if (candle.placeHolder()) {
                result.add(candle);
                continue;
            }

            double haClose = (candle.openPrice() + candle.highPrice() + candle.lowPrice() + candle.closePrice()) / 4.0;
            double haOpen = (i == 0)
                    ? (candle.openPrice() + candle.closePrice()) / 2.0
                    : (previousHaOpen + previousHaClose) / 2.0;
            double haHigh = Math.max(candle.highPrice(), Math.max(haOpen, haClose));
            double haLow = Math.min(candle.lowPrice(), Math.min(haOpen, haClose));

            result.add(new CandleData(
                    haOpen,
                    haClose,
                    haHigh,
                    haLow,
                    candle.openTime(),
                    candle.volume(),
                    candle.averagePrice(),
                    candle.volumeWeightedAveragePrice(),
                    false));

            previousHaOpen = haOpen;
            previousHaClose = haClose;
        }
        return result;
    }

    private List<CandleData> buildRenkoCandles(List<CandleData> source) {
        List<CandleData> result = new ArrayList<>(source.size());
        double boxSize = deriveBoxSize(source, 72.0);
        double brickClose = firstValidClose(source);
        if (!Double.isFinite(brickClose)) {
            return source;
        }

        for (CandleData candle : source) {
            if (candle == null || candle.placeHolder()) {
                result.add(candle);
                continue;
            }
            double target = candle.closePrice();
            while (Math.abs(target - brickClose) >= boxSize) {
                brickClose += Math.signum(target - brickClose) * boxSize;
            }

            double open = result.isEmpty() || result.getLast() == null
                    ? brickClose
                    : result.getLast().closePrice();
            double high = Math.max(open, brickClose);
            double low = Math.min(open, brickClose);
            result.add(new CandleData(
                    open,
                    brickClose,
                    high,
                    low,
                    candle.openTime(),
                    candle.volume(),
                    candle.averagePrice(),
                    candle.volumeWeightedAveragePrice(),
                    false));
        }
        return result;
    }

    private List<CandleData> buildPointAndFigureCandles(List<CandleData> source) {
        List<CandleData> result = new ArrayList<>(source.size());
        double boxSize = deriveBoxSize(source, 96.0);
        double reversal = boxSize * 3.0;
        double plotted = firstValidClose(source);
        if (!Double.isFinite(plotted)) {
            return source;
        }
        int direction = 0;

        for (CandleData candle : source) {
            if (candle == null || candle.placeHolder()) {
                result.add(candle);
                continue;
            }
            double price = candle.closePrice();
            double diff = price - plotted;

            if ((direction >= 0 && diff >= boxSize) || (direction == -1 && diff >= reversal)) {
                plotted += Math.floor(diff / boxSize) * boxSize;
                direction = 1;
            } else if ((direction <= 0 && diff <= -boxSize) || (direction == 1 && diff <= -reversal)) {
                plotted -= Math.floor((-diff) / boxSize) * boxSize;
                direction = -1;
            }

            double open = result.isEmpty() || result.getLast() == null
                    ? plotted
                    : result.getLast().closePrice();
            double high = Math.max(open, plotted);
            double low = Math.min(open, plotted);
            result.add(new CandleData(
                    open,
                    plotted,
                    high,
                    low,
                    candle.openTime(),
                    candle.volume(),
                    candle.averagePrice(),
                    candle.volumeWeightedAveragePrice(),
                    false));
        }
        return result;
    }

    private double firstValidClose(List<CandleData> source) {
        for (CandleData candle : source) {
            if (candle != null && !candle.placeHolder()) {
                return candle.closePrice();
            }
        }
        return Double.NaN;
    }

    private double deriveBoxSize(List<CandleData> source, double divisor) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double reference = 0.0;
        int count = 0;

        for (CandleData candle : source) {
            if (candle == null || candle.placeHolder()) {
                continue;
            }
            min = Math.min(min, candle.lowPrice());
            max = Math.max(max, candle.highPrice());
            reference += Math.abs(candle.closePrice());
            count++;
        }

        if (!Double.isFinite(min) || !Double.isFinite(max) || count == 0) {
            return 0.0001;
        }

        double rangeBased = Math.max((max - min) / Math.max(8.0, divisor), 0.0001);
        double referencePrice = Math.max(reference / count, 0.0001);
        double percentBased = referencePrice * 0.0012;
        return Math.max(rangeBased, percentBased);
    }

    private void drawVolumeBar(CandleData candle, double x, double width, Paint fill, double volumeScale) {
        double maxH = volumeAreaHeight();
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
        if (candle == null) {
            return;
        }
        boolean bullish = candle.closePrice() >= candle.openPrice();
        Color closeColor = bullish ? THEME_BUY : THEME_SELL;

        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(),
                Math.max(28, Math.min(60, canvas.getWidth() / 12))));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(Color.rgb(148, 163, 184, 0.055));
        graphicsContext.fillText(tradePair.toString('/'), canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);

        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 15));
        graphicsContext.setTextAlign(TextAlignment.LEFT);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(THEME_TEXT);
        graphicsContext.fillText(tradePair.toString('/'), 18, 20);

        drawSmallPill(exchange.supportsTimeframe(secondsPerCandle), 118, 20, THEME_ACCENT,
                Color.rgb(8, 47, 73, 0.95));

        drawHeaderValue("O", candle.openPrice(), 18, THEME_MUTED);
        drawHeaderValue("H", candle.highPrice(), 145, THEME_BUY);
        drawHeaderValue("L", candle.lowPrice(), 272, THEME_SELL);
        drawHeaderValue("C", candle.closePrice(), 399, closeColor);

        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 11));
        graphicsContext.setFill(THEME_MUTED);
        graphicsContext.fillText("Vol " + compactNumber(candle.volume()), 530, 40);

        String session = getMarketSession();
        Color sessionColor = getSessionColor(session);
        double badgeX = Math.max(600, canvas.getWidth() - 245);
        double chartTypeBadgeX = Math.max(495, badgeX - 150);
        drawSmallPill(chartType.getDisplayName(), chartTypeBadgeX, 20, THEME_ACCENT, Color.rgb(8, 47, 73, 0.95));
        drawSmallPill(session, badgeX, 20, sessionColor, Color.rgb(15, 23, 42, 0.95));
        graphicsContext.setTextAlign(TextAlignment.LEFT);
    }

    public void setChartType(ChartType newChartType) {
        if (newChartType == null) {
            return;
        }
        runOnFx(() -> {
            if (chartType == newChartType) {
                return;
            }
            chartType = newChartType;
            persistChartPreferences();
            recomputeVisiblePriceRange();
            drawChartContents(true);
        });
    }

    public void cycleChartType() {
        runOnFx(() -> {
            chartType = chartType.next();
            persistChartPreferences();
            recomputeVisiblePriceRange();
            drawChartContents(true);
            showTransientNotice("Chart Type: " + chartType.getDisplayName());
        });
    }

    private void drawSmallPill(String text, double x, double centerY, Color accent, Color background) {
        if (text == null || text.isBlank()) {
            return;
        }
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 11));
        double width = Math.max(54, text.length() * 7.0 + 18);
        double height = 20;
        double y = centerY - height / 2.0;
        graphicsContext.setFill(background);
        graphicsContext.fillRoundRect(x, y, width, height, 999, 999);
        graphicsContext.setStroke(Color.color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0.75));
        graphicsContext.setLineWidth(1.0);
        graphicsContext.strokeRoundRect(x, y, width, height, 999, 999);
        graphicsContext.setFill(accent);
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.fillText(text, x + width / 2.0, centerY);
        graphicsContext.setTextAlign(TextAlignment.LEFT);
    }

    private String getMarketSession() {
        TradingSessionStatus status = tradePair == null
                ? TradingSessionStatus.UNKNOWN
                : tradePair.getTradingSessionStatus();

        return switch (status) {
            case OPEN -> "OPEN";
            case CLOSED -> "CLOSED";
            case BREAK -> "BREAK";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    private Color getSessionColor(String session) {
        if (session.contains("OPEN")) {
            return Color.rgb(76, 175, 80); // Green for open
        } else if (session.contains("BREAK")) {
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
            if (y < 0 || y > pricePlotHeight())
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

    private void drawChartDrawings() {
        List<ChartDrawing> drawings = new ArrayList<>(chartDrawings);
        if (drawingPreview != null) {
            drawings.add(drawingPreview);
        }

        for (int index = 0; index < drawings.size(); index++) {
            ChartDrawing drawing = drawings.get(index);
            if (drawing == null || !drawing.isDrawable()) {
                continue;
            }

            graphicsContext.setStroke(drawing.color());
            graphicsContext.setFill(drawing.color());
            graphicsContext.setLineWidth(drawing.preview() ? 1.0 : 1.6);
            if (drawing.preview()) {
                graphicsContext.setLineDashes(6, 5);
            } else {
                graphicsContext.setLineDashes();
            }

            double x1 = timeToX(drawing.startTime());
            double y1 = priceToY(drawing.startPrice());
            double x2 = timeToX(drawing.endTime());
            double y2 = priceToY(drawing.endPrice());

            switch (drawing.type()) {
                case TRENDLINE ->
                    graphicsContext.strokeLine(snapPixel(x1), snapPixel(y1), snapPixel(x2), snapPixel(y2));
                case HORIZONTAL_LINE -> graphicsContext.strokeLine(0, snapPixel(y1), canvas.getWidth(), snapPixel(y1));
                case VERTICAL_LINE ->
                    graphicsContext.strokeLine(snapPixel(x1), HEADER_HEIGHT, snapPixel(x1), pricePlotHeight());
                case RECTANGLE -> drawDrawingRectangle(x1, y1, x2, y2, drawing.color());
                case TRIANGLE -> drawDrawingTriangle(x1, y1, x2, y2, drawing.color());
                case CIRCLE -> drawDrawingCircle(x1, y1, x2, y2, drawing.color());
                case FIBONACCI -> drawFibonacciRetracement(x1, x2, drawing);
                case MEASURE -> drawMeasurement(x1, y1, x2, y2, drawing);
                case RISK_REWARD -> drawRiskReward(x1, y1, x2, y2, drawing);
                default -> {
                }
            }

            if (!drawing.preview() && index == selectedDrawingIndex) {
                drawDrawingSelectionHandles(x1, y1, x2, y2, drawing);
            }

            graphicsContext.setLineDashes();
        }
    }

    private void drawDrawingSelectionHandles(double x1, double y1, double x2, double y2, ChartDrawing drawing) {
        graphicsContext.setGlobalAlpha(1.0);
        graphicsContext.setStroke(Color.web("#f8fafc"));
        graphicsContext.setFill(Color.web("#0f172a"));
        graphicsContext.setLineWidth(1.2);

        double size = 8.0;
        drawHandle(x1, y1, size);
        drawHandle(x2, y2, size);

        if (drawing.type() == ChartInteractionTool.RECTANGLE
                || drawing.type() == ChartInteractionTool.TRIANGLE
                || drawing.type() == ChartInteractionTool.CIRCLE
                || drawing.type() == ChartInteractionTool.RISK_REWARD
                || drawing.type() == ChartInteractionTool.MEASURE
                || drawing.type() == ChartInteractionTool.TRENDLINE) {
            drawHandle(x1, y2, size);
            drawHandle(x2, y1, size);
        }
    }

    private void drawHandle(double x, double y, double size) {
        double half = size / 2.0;
        graphicsContext.fillRoundRect(snapPixel(x - half), snapPixel(y - half), size, size, 3, 3);
        graphicsContext.strokeRoundRect(snapPixel(x - half), snapPixel(y - half), size, size, 3, 3);
    }

    private void drawDrawingRectangle(double x1, double y1, double x2, double y2, Color color) {
        double x = Math.min(x1, x2);
        double y = Math.min(y1, y2);
        double width = Math.abs(x2 - x1);
        double height = Math.abs(y2 - y1);
        graphicsContext.setGlobalAlpha(0.12);
        graphicsContext.setFill(color);
        graphicsContext.fillRect(x, y, width, height);
        graphicsContext.setGlobalAlpha(1.0);
        graphicsContext.setStroke(color);
        graphicsContext.strokeRect(snapPixel(x), snapPixel(y), width, height);
    }

    private void drawDrawingTriangle(double x1, double y1, double x2, double y2, Color color) {
        double left = Math.min(x1, x2);
        double right = Math.max(x1, x2);
        double top = Math.min(y1, y2);
        double bottom = Math.max(y1, y2);
        double apexX = left + ((right - left) / 2.0);

        double[] xPoints = { apexX, right, left };
        double[] yPoints = { top, bottom, bottom };
        graphicsContext.setGlobalAlpha(0.12);
        graphicsContext.setFill(color);
        graphicsContext.fillPolygon(xPoints, yPoints, 3);
        graphicsContext.setGlobalAlpha(1.0);
        graphicsContext.setStroke(color);
        graphicsContext.strokePolygon(xPoints, yPoints, 3);
    }

    private void drawDrawingCircle(double x1, double y1, double x2, double y2, Color color) {
        double x = Math.min(x1, x2);
        double y = Math.min(y1, y2);
        double width = Math.abs(x2 - x1);
        double height = Math.abs(y2 - y1);
        graphicsContext.setGlobalAlpha(0.12);
        graphicsContext.setFill(color);
        graphicsContext.fillOval(x, y, width, height);
        graphicsContext.setGlobalAlpha(1.0);
        graphicsContext.setStroke(color);
        graphicsContext.strokeOval(snapPixel(x), snapPixel(y), width, height);
    }

    private void drawFibonacciRetracement(double x1, double x2, ChartDrawing drawing) {
        double left = Math.min(x1, x2);
        double right = Math.max(x1, x2);
        double topPrice = Math.max(drawing.startPrice(), drawing.endPrice());
        double bottomPrice = Math.min(drawing.startPrice(), drawing.endPrice());
        double range = topPrice - bottomPrice;
        if (!Double.isFinite(range) || range <= 0) {
            return;
        }

        double[] levels = { 0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0 };
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 10));
        graphicsContext.setTextAlign(TextAlignment.LEFT);
        graphicsContext.setTextBaseline(VPos.CENTER);
        for (double level : levels) {
            double price = topPrice - (range * level);
            double y = snapPixel(priceToY(price));
            graphicsContext.strokeLine(left, y, right, y);
            graphicsContext.fillText("%.1f%% %s".formatted(level * 100.0, formatPrice(price)),
                    Math.min(right + 6, canvas.getWidth() - 100), y - 7);
        }
    }

    private void drawMeasurement(double x1, double y1, double x2, double y2, ChartDrawing drawing) {
        graphicsContext.strokeLine(snapPixel(x1), snapPixel(y1), snapPixel(x2), snapPixel(y2));

        double priceDelta = drawing.endPrice() - drawing.startPrice();
        long secondsDelta = Math.abs(drawing.endTime() - drawing.startTime());
        String label = "%s (%+.2f%%) | %s".formatted(
                formatPrice(priceDelta),
                drawing.startPrice() == 0 ? 0.0 : (priceDelta / drawing.startPrice()) * 100.0,
                formatDuration(secondsDelta));
        drawLabelBadge(label, Math.min(x1, x2) + Math.abs(x2 - x1) / 2.0, Math.min(y1, y2) - 18,
                Math.max(130, label.length() * 7.2), 22, drawing.color());
    }

    private void drawRiskReward(double x1, double y1, double x2, double y2, ChartDrawing drawing) {
        double left = Math.min(x1, x2);
        double right = Math.max(x1, x2);
        double width = Math.max(24.0, right - left);
        double rewardTop = Math.min(y1, y2);
        double rewardBottom = Math.max(y1, y2);
        double riskHeight = Math.max(1.0, Math.abs(y2 - y1));
        double riskTop;
        double riskBottom;

        if (drawing.endPrice() >= drawing.startPrice()) {
            riskTop = y1;
            riskBottom = y1 + riskHeight;
        } else {
            riskTop = y1 - riskHeight;
            riskBottom = y1;
        }

        double pricePlotH = pricePlotHeight();
        riskTop = clamp(riskTop, HEADER_HEIGHT, pricePlotH);
        riskBottom = clamp(riskBottom, HEADER_HEIGHT, pricePlotH);
        double riskY = Math.min(riskTop, riskBottom);
        double riskH = Math.max(1.0, Math.abs(riskBottom - riskTop));
        double rewardY = Math.min(rewardTop, rewardBottom);
        double rewardH = Math.max(1.0, Math.abs(rewardBottom - rewardTop));

        graphicsContext.setGlobalAlpha(0.18);
        graphicsContext.setFill(Color.web("#22c55e"));
        graphicsContext.fillRect(left, rewardY, width, rewardH);
        graphicsContext.setFill(Color.web("#ef4444"));
        graphicsContext.fillRect(left, riskY, width, riskH);
        graphicsContext.setGlobalAlpha(1.0);

        graphicsContext.setLineDashes();
        graphicsContext.setStroke(Color.web("#e2e8f0"));
        graphicsContext.setLineWidth(1.2);
        graphicsContext.strokeRect(snapPixel(left), snapPixel(rewardY), width, rewardH);
        graphicsContext.strokeRect(snapPixel(left), snapPixel(riskY), width, riskH);
        graphicsContext.setStroke(Color.web("#f8fafc"));
        graphicsContext.strokeLine(left, snapPixel(y1), left + width, snapPixel(y1));

        double reward = Math.abs(drawing.endPrice() - drawing.startPrice());
        String label = "R:R 1.00 | " + formatPrice(reward);
        drawLabelBadge(label, left + width / 2.0, Math.min(rewardY, riskY) - 14,
                Math.max(112, label.length() * 7.2), 22, Color.web("#e2e8f0"));
    }

    private void drawLabelBadge(String text, double centerX, double centerY, double width, double height,
            Color accent) {
        double x = clamp(centerX - width / 2.0, 4, Math.max(4, canvas.getWidth() - width - 4));
        double y = clamp(centerY - height / 2.0, HEADER_HEIGHT,
                Math.max(HEADER_HEIGHT, pricePlotHeight() - height - 4));
        graphicsContext.setFill(Color.rgb(2, 6, 23, 0.86));
        graphicsContext.fillRoundRect(x, y, width, height, 8, 8);
        graphicsContext.setStroke(accent);
        graphicsContext.strokeRoundRect(x, y, width, height, 8, 8);
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 10));
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(accent);
        graphicsContext.fillText(text, x + width / 2.0, y + height / 2.0);
        graphicsContext.setTextAlign(TextAlignment.LEFT);
    }

    private void drawIndicators() {
        if (indicators.isEmpty())
            return;

        List<CandleData> visible = visibleCandlesSnapshot();
        if (visible.isEmpty())
            return;
        List<CandleData> allCandles = getAllCandleData();

        for (Indicator indicator : List.copyOf(indicators)) {
            java.util.Map<String, double[]> values = indicator.getValues();
            if (values == null || values.isEmpty() || !indicator.isCalculated())
                continue;

            boolean priceOverlay = isPriceOverlayIndicator(indicator.getName());
            java.util.Map<String, javafx.scene.paint.Color> lineColors = getIndicatorColors(indicator.getName());

            double minValue = Double.POSITIVE_INFINITY;
            double maxValue = Double.NEGATIVE_INFINITY;
            if (!priceOverlay) {
                for (double[] lineValues : values.values()) {
                    if (lineValues == null || lineValues.length == 0) {
                        continue;
                    }
                    for (CandleData candle : visible) {
                        int dataIndex = indexOfOpenTime(allCandles, candle.openTime());
                        if (dataIndex < 0 || dataIndex >= lineValues.length) {
                            continue;
                        }
                        double candidate = lineValues[dataIndex];
                        if (Double.isNaN(candidate) || Double.isInfinite(candidate)) {
                            continue;
                        }
                        minValue = Math.min(minValue, candidate);
                        maxValue = Math.max(maxValue, candidate);
                    }
                }

                if (!Double.isFinite(minValue) || !Double.isFinite(maxValue)) {
                    continue;
                }

                double range = maxValue - minValue;
                double padding = Math.max(1.0, Math.max(Math.abs(maxValue), Math.abs(minValue)) * 0.05);
                if (range < 1e-9) {
                    minValue -= padding;
                    maxValue += padding;
                } else {
                    minValue -= padding;
                    maxValue += padding;
                }
            }

            for (java.util.Map.Entry<String, double[]> entry : values.entrySet()) {
                String lineName = entry.getKey();
                double[] lineValues = entry.getValue();
                if (lineValues == null || lineValues.length == 0) {
                    continue;
                }

                javafx.scene.paint.Color color = resolveIndicatorLineColor(lineColors, lineName);

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

                    double y = priceOverlay ? priceToY(value) : indicatorToY(value, minValue, maxValue);
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

    private boolean isPriceOverlayIndicator(String indicatorName) {
        return IndicatorCatalog.isPriceOverlay(indicatorName);
    }

    private @NonNull String normalizeIndicatorName(String indicatorName) {
        return indicatorName == null ? "" : indicatorName.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private @NonNull String normalizeIndicatorLineName(String lineName) {
        if (lineName == null) {
            return "";
        }
        return lineName
                .replaceAll("[_\\s]+", "")
                .toUpperCase(Locale.ROOT);
    }

    private double indicatorToY(double value, double minValue, double maxValue) {
        double chartHeight = Math.max(1.0, canvas.getHeight() - 24.0);
        double topPadding = 12.0;
        double range = Math.max(1e-9, maxValue - minValue);
        double ratio = (value - minValue) / range;
        ratio = clamp(ratio, 0.0, 1.0);
        return topPadding + ((1.0 - ratio) * chartHeight);
    }

    private java.util.Map<String, javafx.scene.paint.Color> getIndicatorColors(String indicatorName) {
        java.util.Map<String, javafx.scene.paint.Color> colors = new java.util.HashMap<>();
        switch (normalizeIndicatorName(indicatorName)) {
            case "SMA20", "SMA", "SIMPLEMOVINGAVERAGE" -> colors.put("SMA", javafx.scene.paint.Color.web("#87CEEB"));
            case "EMA12", "EMA", "EXPONENTIALMOVINGAVERAGE" ->
                colors.put("EMA", javafx.scene.paint.Color.web("#FFA500"));
            case "RSI14", "RSI", "RELATIVESTRENGTHINDEX" -> colors.put("RSI", javafx.scene.paint.Color.web("#9370DB"));
            case "MACD" -> {
                colors.put("MACD", javafx.scene.paint.Color.web("#2ecc71"));
                colors.put("Signal", javafx.scene.paint.Color.web("#e74c3c"));
                colors.put("Histogram", javafx.scene.paint.Color.web("#3498db"));
            }
            case "BOLLINGERBANDS" -> {
                colors.put("MiddleBand", javafx.scene.paint.Color.web("#3498db"));
                colors.put("UpperBand", javafx.scene.paint.Color.web("#95a5a6"));
                colors.put("LowerBand", javafx.scene.paint.Color.web("#95a5a6"));
            }
            case "STOCHASTIC" -> {
                colors.put("K", javafx.scene.paint.Color.web("#e74c3c"));
                colors.put("D", javafx.scene.paint.Color.web("#3498db"));
            }
            case "ATR14" -> colors.put("ATR", javafx.scene.paint.Color.web("#e91e63"));
            case "PARABOLICSAR" -> colors.put("SAR", javafx.scene.paint.Color.web("#f1c40f"));
            case "FIBONACCIRETRACEMENT" -> {
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
            case "ZIGZAG" -> colors.put("Zigzag", javafx.scene.paint.Color.web("#e74c3c"));
            case "FRACTAL" -> {
                colors.put("UpFractal", javafx.scene.paint.Color.web("#2ecc71"));
                colors.put("DnFractal", javafx.scene.paint.Color.web("#e74c3c"));
            }
            case "ICHIMOKU" -> {
                colors.put("TenkanSen", javafx.scene.paint.Color.web("#3498db"));
                colors.put("KijunSen", javafx.scene.paint.Color.web("#f39c12"));
                colors.put("SenkouSpanA", javafx.scene.paint.Color.web("#2ecc71"));
                colors.put("SenkouSpanB", javafx.scene.paint.Color.web("#e74c3c"));
                colors.put("ChikouSpan", javafx.scene.paint.Color.web("#9370DB"));
            }
            case "VWAP" -> colors.put("VWAP", javafx.scene.paint.Color.web("#1abc9c"));
            default -> colors.put("Line", javafx.scene.paint.Color.CYAN);
        }
        return colors;
    }

    private javafx.scene.paint.Color resolveIndicatorLineColor(
            java.util.Map<String, javafx.scene.paint.Color> lineColors,
            String lineName) {
        if (lineColors == null || lineColors.isEmpty()) {
            return javafx.scene.paint.Color.CYAN;
        }
        javafx.scene.paint.Color direct = lineColors.get(lineName);
        if (direct != null) {
            return direct;
        }
        String normalizedLine = normalizeIndicatorLineName(lineName);
        for (java.util.Map.Entry<String, javafx.scene.paint.Color> entry : lineColors.entrySet()) {
            if (normalizeIndicatorLineName(entry.getKey()).equals(normalizedLine)) {
                return entry.getValue();
            }
        }
        return javafx.scene.paint.Color.CYAN;
    }

    /**
     * Draw all chart events (trade entries, exits, economic events, etc.)
     */
    private void drawChartEvents() {
        if (chartEvents.isEmpty() || canvas == null || graphicsContext == null)
            return;

        List<CandleData> visible = visibleCandlesSnapshot();
        if (visible.isEmpty())
            return;

        for (ChartEvent event : List.copyOf(chartEvents)) {
            if (!event.isVisible())
                continue;
            if (isNewsEvent(event) && !chartOptions.isShowNewsEvents())
                continue;

            // Calculate X position from event timestamp
            double eventX = getEventXPosition(event, visible);
            if (eventX < 0 || eventX > canvas.getWidth())
                continue;

            // Draw vertical line
            graphicsContext.setStroke(Color.web(event.getHexColor()));
            graphicsContext.setLineWidth(event.getLineWidth());
            graphicsContext.strokeLine(snapPixel(eventX), 0, snapPixel(eventX), canvas.getHeight());

            // Draw event label/marker
            drawEventMarker(eventX, event);
        }
    }

    /**
     * Calculate the X position of an event based on its timestamp
     */
    private double getEventXPosition(ChartEvent event, List<CandleData> visible) {
        if (visible.isEmpty())
            return -1;

        Instant eventTime = event.getTimestamp();
        if (eventTime == null)
            return -1;

        CandleData first = visible.getFirst();
        CandleData last = visible.getLast();

        long eventSeconds = eventTime.getEpochSecond();
        long firstSeconds = first.openTime();
        long lastSeconds = last.openTime() + secondsPerCandle;

        if (eventSeconds < firstSeconds || eventSeconds > lastSeconds)
            return -1; // Event outside visible range

        double ratio = (double) (eventSeconds - firstSeconds) / (lastSeconds - firstSeconds);
        return snapPixel(ratio * canvas.getWidth());
    }

    /**
     * Draw the event marker (icon, label, color dot)
     */
    private void drawEventMarker(double eventX, ChartEvent event) {
        // Draw colored circle at top of the chart
        double markerY = 15;
        double markerRadius = 4;

        graphicsContext.setFill(Color.web(event.getHexColor()));
        graphicsContext.fillOval(eventX - markerRadius, markerY - markerRadius,
                markerRadius * 2, markerRadius * 2);

        // Draw label background
        String label = event.getLabel() != null ? event.getLabel() : event.getType().label;
        graphicsContext.setFont(Font.font(FXUtils.getMonospacedFont(), 9));
        graphicsContext.setTextAlign(TextAlignment.CENTER);

        // Approximate text width (character count * 6.5)
        double labelWidth = label.length() * 6.5;
        double labelHeight = 14;
        double labelX = eventX - labelWidth / 2;
        double labelY = markerY + markerRadius + 8;

        // Draw semi-transparent background for label
        graphicsContext.setFill(Color.web("#050b14", 0.9));
        graphicsContext.fillRect(labelX - 2, labelY - labelHeight / 2, labelWidth + 4, labelHeight);

        // Draw label text
        graphicsContext.setFill(Color.web(event.getHexColor()));
        graphicsContext.fillText(label, eventX, labelY + 2);

        // Draw border around label
        graphicsContext.setStroke(Color.web(event.getHexColor(), 0.6));
        graphicsContext.setLineWidth(0.5);
        graphicsContext.strokeRect(labelX - 2, labelY - labelHeight / 2, labelWidth + 4, labelHeight);
    }

    // Event Management Methods

    /**
     * Add a chart event to be drawn
     */
    public void addChartEvent(ChartEvent event) {
        if (event == null)
            return;
        chartEvents.add(event);
        requestChartRedraw();
    }

    /**
     * Add multiple chart events
     */
    public void addChartEvents(List<ChartEvent> events) {
        if (events == null || events.isEmpty())
            return;
        chartEvents.addAll(events);
        requestChartRedraw();
    }

    public void setNewsEvents(List<NewsEvent> events) {
        chartEvents.removeIf(this::isNewsEvent);
        if (events == null || events.isEmpty()) {
            requestChartRedraw();
            return;
        }

        List<ChartEvent> mappedEvents = events.stream()
                .filter(Objects::nonNull)
                .filter(event -> event.getEventTime() != null)
                .map(event -> ChartEvent.builder()
                        .id("news-" + event.getEventTime().getEpochSecond() + "-" + safeNewsKey(event.getTitle()))
                        .timestamp(event.getEventTime())
                        .type(event.getImportance() == NewsEvent.Importance.CRITICAL
                                ? ChartEvent.EventType.NEWS
                                : ChartEvent.EventType.ECONOMIC_EVENT)
                        .label(event.getCurrency() + " " + event.getImportance().name())
                        .description(event.getTitle())
                        .hexColor(event.getImportance() == NewsEvent.Importance.CRITICAL
                                ? "#ef4444"
                                : event.getImportance() == NewsEvent.Importance.HIGH
                                        ? "#f97316"
                                        : "#f59e0b")
                        .visible(true)
                        .lineWidth(1)
                        .build())
                .toList();

        chartEvents.addAll(mappedEvents);
        requestChartRedraw();
    }

    private String safeNewsKey(String text) {
        if (text == null || text.isBlank()) {
            return "event";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private boolean isNewsEvent(ChartEvent event) {
        if (event == null || event.getType() == null) {
            return false;
        }

        return event.getType() == ChartEvent.EventType.NEWS
                || event.getType() == ChartEvent.EventType.ECONOMIC_EVENT;
    }

    /**
     * Remove a chart event by ID
     */
    public void removeChartEvent(String eventId) {
        boolean removed = chartEvents.removeIf(e -> e.getId().equals(eventId));
        if (removed)
            requestChartRedraw();
    }

    /**
     * Clear all chart events
     */
    public void clearChartEvents() {
        if (!chartEvents.isEmpty()) {
            chartEvents.clear();
            requestChartRedraw();
        }
    }

    /**
     * Get all chart events
     */
    public List<ChartEvent> getChartEvents() {
        return new ArrayList<>(chartEvents);
    }

    /**
     * Toggle visibility of chart events
     */
    public void toggleChartEvents() {
        showChartEvents = !showChartEvents;
        clearChartEvents();
        setShowChartEvents(showChartEvents);
        requestChartRedraw();
    }

    /**
     * Set chart events visibility
     */
    public void setShowChartEvents(boolean show) {
        if (showChartEvents != show) {
            showChartEvents = show;
            persistChartPreferences();
            requestChartRedraw();
        }
    }

    private void updateLatestCandleFromTrade(@NotNull Trade trade) {
        if (data.isEmpty()) {
            return;
        }

        double price = trade.getPrice();
        double amount = trade.getAmount();

        if (!Double.isFinite(price) || price <= 0.0) {
            return;
        }

        int candleOpenTime = (int) ((trade.getTimestamp().getEpochSecond() / secondsPerCandle) * secondsPerCandle);

        CandleData existing = data.get(candleOpenTime);
        if (existing == null) {
            return;
        }

        double referencePrice = existing.closePrice() > 0.0 ? existing.closePrice() : existing.openPrice();
        if (!isPlausibleLiveTradePrice(price, referencePrice)) {
            log.debug("Ignoring implausible live trade price for {}. reference={} incoming={} candleOpenTime={}",
                    tradePair,
                    referencePrice,
                    price,
                    candleOpenTime);
            return;
        }

        CandleData candleData = new CandleData(

                existing.openPrice(),
                price,
                Math.max(existing.highPrice(), price),
                Math.min(existing.lowPrice(), price),
                existing.openTime(),
                (existing.volume() + Math.max(0.0, amount)),
                existing.averagePrice(), existing.volumeWeightedAveragePrice(),
                false);

        data.put(candleOpenTime, candleData);
    }

    private boolean isPlausibleLiveTradePrice(double tradePrice, double referencePrice) {
        if (!Double.isFinite(tradePrice) || tradePrice <= 0.0) {
            return false;
        }
        if (!Double.isFinite(referencePrice) || referencePrice <= 0.0) {
            return true;
        }
        double deviation = Math.abs(tradePrice - referencePrice) / referencePrice;
        return deviation <= MAX_LIVE_TRADE_PRICE_DEVIATION_RATIO;
    }

    private void drawCurrentPriceBadge(CandleData candle) {
        double price = candle.closePrice();
        double y = priceToY(price);
        double pricePlotH = pricePlotHeight();
        if (y < 0 || y > pricePlotH) {
            return;
        }
        boolean bullish = candle.closePrice() >= candle.openPrice();
        Color color = bullish ? THEME_BUY : THEME_SELL;
        double x = canvas.getWidth() - PRICE_BADGE_WIDTH - 6;
        double badgeY = clamp(y - PRICE_BADGE_HEIGHT / 2.0, HEADER_HEIGHT + 2,
                pricePlotH - PRICE_BADGE_HEIGHT - 8);
        graphicsContext.setStroke(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.50));
        graphicsContext.setLineWidth(1);
        graphicsContext.setLineDashes(6, 6);
        graphicsContext.strokeLine(0, snapPixel(y), canvas.getWidth(), snapPixel(y));
        graphicsContext.setLineDashes();
        graphicsContext.setFill(color);
        graphicsContext.fillRoundRect(x, badgeY, PRICE_BADGE_WIDTH, PRICE_BADGE_HEIGHT, 999, 999);
        graphicsContext.setStroke(Color.rgb(255, 255, 255, 0.20));
        graphicsContext.strokeRoundRect(x, badgeY, PRICE_BADGE_WIDTH, PRICE_BADGE_HEIGHT, 999, 999);
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
        double pricePlotH = pricePlotHeight();
        double priceMouseY = clamp(crosshairMouseY, 0.0, pricePlotH);
        graphicsContext.strokeLine(0, priceMouseY, canvas.getWidth(), priceMouseY);
        graphicsContext.setLineDashes();
        double price = yToPrice(priceMouseY);
        hoveredPrice = price;
        int index = clampInt(
                (int) Math.floor(crosshairMouseX / Math.max(1.0, canvas.getWidth() / Math.max(1, visibleCandles))), 0,
                Math.max(0, visibleCandles - 1));
        List<CandleData> visible = visibleCandlesSnapshot();
        if (index < visible.size())
            hoveredTime = visible.get(index).openTime();
        drawLabelBadge(formatPrice(price), canvas.getWidth() - PRICE_BADGE_WIDTH,
                clamp(priceMouseY - PRICE_BADGE_HEIGHT / 2.0, HEADER_HEIGHT + 2,
                        pricePlotH - PRICE_BADGE_HEIGHT - 2),
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
        if (data.isEmpty() || canvas == null || canvas.getWidth() <= 0) {
            return;
        }

        double scrollbarTop = canvas.getHeight() - SCROLLBAR_HEIGHT - SCROLLBAR_PADDING;
        double trackWidth = canvas.getWidth();
        double trackHeight = SCROLLBAR_HEIGHT;

        graphicsContext.setFill(Color.rgb(15, 23, 42, 0.70));
        graphicsContext.fillRoundRect(8, scrollbarTop, trackWidth - 16, trackHeight, 999, 999);
        graphicsContext.setStroke(Color.rgb(51, 65, 85, 0.85));
        graphicsContext.strokeRoundRect(8, scrollbarTop, trackWidth - 16, trackHeight, 999, 999);

        int totalCandles = data.size();
        double thumbWidth = Math.max(28, (visibleCandles / (double) Math.max(1, totalCandles)) * (trackWidth - 16));
        double denominator = Math.max(1, totalCandles - visibleCandles);
        double thumbX = 8 + (firstVisibleIndex / denominator) * ((trackWidth - 16) - thumbWidth);

        graphicsContext.setFill(Color.rgb(56, 189, 248, 0.82));
        graphicsContext.fillRoundRect(thumbX, scrollbarTop, thumbWidth, trackHeight, 999, 999);
        graphicsContext.setStroke(Color.rgb(186, 230, 253, 0.95));
        graphicsContext.strokeRoundRect(thumbX, scrollbarTop, thumbWidth, trackHeight, 999, 999);
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
        double emaSlow = calculateEMA(visible);

        double currentPrice = visible.getLast().closePrice();
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
        double currentPrice = candles.getLast().closePrice();
        return currentPrice > 0 ? atr / currentPrice : 0;
    }

    /**
     * Calculate Exponential Moving Average (50-period).
     */
    private double calculateEMA(@NotNull List<CandleData> candles) {
        if (candles.isEmpty())
            return 0;

        int period = 50;
        double k = 2.0 / (period + 1);
        double ema = candles.getFirst().closePrice();

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
        if (canvas == null) {
            return;
        }
        if (currentMarketRegime == null || currentMarketRegime.isBlank()) {
            calculateMarketRegime();
        }
        String label = currentMarketRegime == null || currentMarketRegime.isBlank()
                ? "REGIME N/A"
                : currentMarketRegime;
        // Position regime label at top-left corner for better organization
        double x = 20;
        double y = 42;
        drawSmallPill(label, x, y, regimeColor, Color.rgb(15, 23, 42, 0.95));
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
        return visible.isEmpty() ? Optional.empty() : Optional.ofNullable(visible.getLast());
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
        double plotHeight = pricePlotHeight();
        return plotHeight - ((price - visibleMinPrice) / range * plotHeight);
    }

    private double yToPrice(double y) {
        double range = Math.max(0.00000001, visibleMaxPrice - visibleMinPrice);
        double plotHeight = pricePlotHeight();
        double clampedY = clamp(y, 0.0, plotHeight);
        return visibleMinPrice + ((plotHeight - clampedY) / plotHeight * range);
    }

    private double pricePlotHeight() {
        if (canvas == null) {
            return 1.0;
        }
        double height = Math.max(1.0, canvas.getHeight());
        if (!chartOptions.isShowVolume()) {
            return height;
        }
        return Math.max(1.0, height - volumeAreaHeight());
    }

    private double volumeAreaHeight() {
        if (canvas == null || !chartOptions.isShowVolume()) {
            return 0.0;
        }
        return Math.max(1.0, canvas.getHeight() * VOLUME_AREA_RATIO);
    }

    private double volumeTopY() {
        if (canvas == null) {
            return 0.0;
        }
        return Math.max(0.0, canvas.getHeight() - volumeAreaHeight());
    }

    private long xToTime(double x) {
        double lower = xAxis.getLowerBound();
        double upper = xAxis.getUpperBound();
        double range = Math.max(secondsPerCandle, upper - lower);
        double ratio = clamp(x / Math.max(1.0, canvas.getWidth()), 0.0, 1.0);
        return Math.round(lower + (ratio * range));
    }

    private double timeToX(long epochSecond) {
        double lower = xAxis.getLowerBound();
        double upper = xAxis.getUpperBound();
        double range = Math.max(secondsPerCandle, upper - lower);
        return ((epochSecond - lower) / range) * canvas.getWidth();
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
        if (!Double.isFinite(value)) {
            return "-";
        }
        double abs = Math.abs(value);
        if (abs == 0.0) {
            return "0";
        }
        if (abs >= 1_000_000_000.0)
            return "%.2fB".formatted(value / 1_000_000_000.0);
        if (abs >= 1_000_000.0)
            return "%.2fM".formatted(value / 1_000_000.0);
        if (abs >= 1_000.0)
            return "%.2fK".formatted(value / 1_000.0);
        if (abs >= 1.0)
            return "%.4f".formatted(value);

        int precision = Math.min(12, Math.max(2, (int) Math.ceil(-Math.log10(abs)) + 2));
        return ("%." + precision + "f").formatted(value);
    }

    private double minPriceRangeFor(double referencePrice) {
        double abs = Math.abs(referencePrice);
        if (!Double.isFinite(abs) || abs <= 0.0) {
            return 1.0;
        }
        return Math.max(abs * 0.01, Math.pow(10.0, Math.floor(Math.log10(abs)) - 2.0));
    }

    private double minPriceFloor(double referencePrice) {
        double abs = Math.abs(referencePrice);
        if (!Double.isFinite(abs) || abs <= 0.0) {
            return 0.0;
        }
        return Math.pow(10.0, Math.floor(Math.log10(abs)) - 4.0);
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

    private String formatDuration(long seconds) {
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;

        if (days > 0) {
            return "%dd %dh".formatted(days, hours);
        }
        if (hours > 0) {
            return "%dh %dm".formatted(hours, minutes);
        }
        return "%dm".formatted(minutes);
    }

    private void addPriceLine(PriceLine priceLine) {
        if (priceLine != null && priceLine.isValid()) {
            priceLines.add(priceLine.copy());
            requestChartRedraw();
        }
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

    public void clearPriceLines() {
        priceLines.clear();
        currentMarketPriceLine = null;
        requestChartRedraw();
    }

    public void setCurrentMarketPrice(double price) {
        if (!Double.isFinite(price) || price <= 0.0) {
            clearCurrentMarketPrice();
            return;
        }

        if (currentMarketPriceLine == null) {
            currentMarketPriceLine = new PriceLine(price, Color.web("#38bdf8"), "Market", false);
            currentMarketPriceLine.setLineWidth();
            priceLines.add(currentMarketPriceLine);
        } else {
            currentMarketPriceLine.setPrice(price);
        }

        requestChartRedraw();
    }

    public void clearCurrentMarketPrice() {
        if (currentMarketPriceLine != null) {
            priceLines.remove(currentMarketPriceLine);
            currentMarketPriceLine = null;
            requestChartRedraw();
        }
    }

    public void setPriceLinesVisible(boolean visible) {
        showPriceLines = visible;
        persistChartPreferences();
        requestChartRedraw();
    }

    public void activateCursorTool() {
        setActiveInteractionTool(ChartInteractionTool.CURSOR);
    }

    public void activateTrendlineTool() {
        setActiveInteractionTool(ChartInteractionTool.TRENDLINE);
    }

    public void activateHorizontalLineTool() {
        setActiveInteractionTool(ChartInteractionTool.HORIZONTAL_LINE);
    }

    public void activateVerticalLineTool() {
        setActiveInteractionTool(ChartInteractionTool.VERTICAL_LINE);
    }

    public void activateRectangleTool() {
        setActiveInteractionTool(ChartInteractionTool.RECTANGLE);
    }

    public void activateTriangleTool() {
        setActiveInteractionTool(ChartInteractionTool.TRIANGLE);
    }

    public void activateCircleTool() {
        setActiveInteractionTool(ChartInteractionTool.CIRCLE);
    }

    public void activateFibonacciTool() {
        setActiveInteractionTool(ChartInteractionTool.FIBONACCI);
    }

    public void activateMeasureTool() {
        setActiveInteractionTool(ChartInteractionTool.MEASURE);
    }

    public void activateRiskRewardTool() {
        setActiveInteractionTool(ChartInteractionTool.RISK_REWARD);
    }

    public void activateEraseObjectsTool() {
        setActiveInteractionTool(ChartInteractionTool.ERASE_OBJECTS);
    }

    public void clearChartDrawings() {
        chartDrawings.clear();
        drawingPreview = null;
        drawingAnchor = null;
        movingAnchor = null;
        movingDrawingOriginal = null;
        selectedDrawingIndex = -1;
        persistChartDrawings();
        requestChartRedraw();
    }

    private void setActiveInteractionTool(ChartInteractionTool tool) {
        activeInteractionTool = tool == null ? ChartInteractionTool.CURSOR : tool;
        syncLeftToolPaletteSelection();
        drawingPreview = null;
        drawingAnchor = null;
        movingAnchor = null;
        movingDrawingOriginal = null;
        persistChartPreferences();
        showTransientNotice(activeInteractionTool.notice());
        requestChartRedraw();
    }

    private boolean isDrawingToolActive() {
        return activeInteractionTool != null && activeInteractionTool.isDrawingTool();
    }

    private boolean isMoveMode() {
        return activeInteractionTool == ChartInteractionTool.CURSOR;
    }

    private boolean isEraseMode() {
        return activeInteractionTool == ChartInteractionTool.ERASE_OBJECTS;
    }

    private boolean beginDrawing(MouseEvent event) {
        if (!isDrawingToolActive() || canvas == null || data.isEmpty()) {
            return false;
        }

        selectedDrawingIndex = -1;

        chartStackPane.requestFocus();
        canvas.requestFocus();
        drawingAnchor = chartPointFromMouse(event);
        drawingPreview = ChartDrawing.from(activeInteractionTool, drawingAnchor, drawingAnchor, true);
        requestChartRedraw();
        return true;
    }

    private boolean beginMoveDrawing(MouseEvent event) {
        if (!isMoveMode() || canvas == null || data.isEmpty()) {
            return false;
        }

        int hitIndex = findDrawingIndexAt(event.getX(), event.getY());
        if (hitIndex < 0) {
            return false;
        }

        selectedDrawingIndex = hitIndex;
        movingDrawingOriginal = chartDrawings.get(hitIndex);
        movingAnchor = chartPointFromMouse(event);
        requestChartRedraw();
        return true;
    }

    private boolean eraseDrawing(MouseEvent event) {
        if (!isEraseMode() || canvas == null || data.isEmpty()) {
            return false;
        }

        int hitIndex = findDrawingIndexAt(event.getX(), event.getY());
        if (hitIndex < 0) {
            return false;
        }

        chartDrawings.remove(hitIndex);
        selectedDrawingIndex = -1;
        movingAnchor = null;
        movingDrawingOriginal = null;
        persistChartDrawings();
        showTransientNotice("Drawing erased");
        requestChartRedraw();
        return true;
    }

    private boolean updateDrawing(MouseEvent event) {
        if (!isDrawingToolActive() || drawingAnchor == null) {
            return false;
        }

        ChartPoint current = chartPointFromMouse(event);
        drawingPreview = ChartDrawing.from(activeInteractionTool, drawingAnchor, current, true);
        requestChartRedraw();
        return true;
    }

    private boolean updateMoveDrawing(MouseEvent event) {
        if (movingAnchor == null || movingDrawingOriginal == null || selectedDrawingIndex < 0) {
            return false;
        }

        ChartPoint current = chartPointFromMouse(event);
        long deltaTime = current.time() - movingAnchor.time();
        double deltaPrice = current.price() - movingAnchor.price();
        chartDrawings.set(selectedDrawingIndex, movingDrawingOriginal.movedBy(deltaTime, deltaPrice));
        requestChartRedraw();
        return true;
    }

    private boolean finishDrawing(MouseEvent event) {
        if (!isDrawingToolActive() || drawingAnchor == null) {
            return false;
        }

        ChartPoint current = chartPointFromMouse(event);
        ChartDrawing drawing = ChartDrawing.from(activeInteractionTool, drawingAnchor, current, false);
        if (drawing.isDrawable()) {
            chartDrawings.add(drawing);
            persistChartDrawings();
            showTransientNotice(activeInteractionTool.label() + " added");
        }

        drawingAnchor = null;
        drawingPreview = null;
        requestChartRedraw();
        return true;
    }

    private boolean finishMoveDrawing() {
        if (movingAnchor == null || movingDrawingOriginal == null || selectedDrawingIndex < 0) {
            return false;
        }

        movingAnchor = null;
        movingDrawingOriginal = null;
        persistChartDrawings();
        requestChartRedraw();
        return true;
    }

    private boolean selectDrawingAt(double canvasX, double canvasY) {
        int hitIndex = findDrawingIndexAt(canvasX, canvasY);
        if (hitIndex < 0) {
            if (selectedDrawingIndex >= 0) {
                selectedDrawingIndex = -1;
                requestChartRedraw();
            }
            return false;
        }

        selectedDrawingIndex = hitIndex;
        requestChartRedraw();
        showTransientNotice("Drawing selected. Drag to move.");
        return true;
    }

    private int findDrawingIndexAt(double canvasX, double canvasY) {
        for (int index = chartDrawings.size() - 1; index >= 0; index--) {
            ChartDrawing drawing = chartDrawings.get(index);
            if (drawing != null && drawing.hitTest(canvasX, canvasY, this)) {
                return index;
            }
        }
        return -1;
    }

    private ChartPoint chartPointFromMouse(MouseEvent event) {
        double x = clamp(event.getX(), 0, canvas.getWidth());
        double y = clamp(event.getY(), 0, canvas.getHeight());
        return new ChartPoint(xToTime(x), yToPrice(y));
    }

    private enum ChartInteractionTool {
        CURSOR("Cursor", "Cursor tool"),
        TRENDLINE("Trendline", "Drag to draw a trendline"),
        HORIZONTAL_LINE("Horizontal line", "Click a price level"),
        VERTICAL_LINE("Vertical line", "Click a time level"),
        RECTANGLE("Rectangle", "Drag to draw a rectangle"),
        TRIANGLE("Triangle", "Drag to draw a triangle"),
        CIRCLE("Circle", "Drag to draw a circle"),
        FIBONACCI("Fibonacci", "Drag swing high to low"),
        MEASURE("Measure", "Drag to measure move"),
        RISK_REWARD("Risk Reward", "Drag entry to target"),
        ERASE_OBJECTS("Erase", "Click a drawing to erase it");

        private final String label;
        private final String notice;

        ChartInteractionTool(String label, String notice) {
            this.label = label;
            this.notice = notice;
        }

        private boolean isDrawingTool() {
            return this != CURSOR && this != ERASE_OBJECTS;
        }

        private String label() {
            return label;
        }

        private String notice() {
            return notice;
        }
    }

    private record ChartPoint(long time, double price) {
    }

    private record ChartDrawing(
            ChartInteractionTool type,
            long startTime,
            double startPrice,
            long endTime,
            double endPrice,
            Color color,
            boolean preview) {

        private static ChartDrawing persisted(
                ChartInteractionTool type,
                long startTime,
                double startPrice,
                long endTime,
                double endPrice) {
            ChartInteractionTool safeType = type == null ? ChartInteractionTool.CURSOR : type;
            return new ChartDrawing(
                    safeType,
                    startTime,
                    startPrice,
                    endTime,
                    endPrice,
                    colorFor(safeType),
                    false);
        }

        private static ChartDrawing from(
                ChartInteractionTool type,
                ChartPoint start,
                ChartPoint end,
                boolean preview) {
            ChartInteractionTool safeType = type == null ? ChartInteractionTool.CURSOR : type;
            ChartPoint safeStart = start == null ? new ChartPoint(0, 0) : start;
            ChartPoint safeEnd = end == null ? safeStart : end;
            return new ChartDrawing(
                    safeType,
                    safeStart.time(),
                    safeStart.price(),
                    safeEnd.time(),
                    safeEnd.price(),
                    colorFor(safeType),
                    preview);
        }

        private static Color colorFor(ChartInteractionTool type) {
            return switch (type) {
                case TRENDLINE -> Color.web("#38bdf8");
                case HORIZONTAL_LINE -> Color.web("#f59e0b");
                case VERTICAL_LINE -> Color.web("#a78bfa");
                case RECTANGLE -> Color.web("#22c55e");
                case TRIANGLE -> Color.web("#06b6d4");
                case CIRCLE -> Color.web("#ec4899");
                case FIBONACCI -> Color.web("#f97316");
                case MEASURE, RISK_REWARD -> Color.web("#e2e8f0");
                default -> Color.web("#94a3b8");
            };
        }

        private boolean isDrawable() {
            if (type == null || type == ChartInteractionTool.CURSOR) {
                return false;
            }
            return Double.isFinite(startPrice)
                    && Double.isFinite(endPrice)
                    && startTime > 0
                    && endTime > 0;
        }

        private ChartDrawing movedBy(long deltaTime, double deltaPrice) {
            if (type == null) {
                return this;
            }

            long newStartTime = Math.max(1L, startTime + deltaTime);
            long newEndTime = Math.max(1L, endTime + deltaTime);
            double newStartPrice = Math.max(0.0, startPrice + deltaPrice);
            double newEndPrice = Math.max(0.0, endPrice + deltaPrice);

            return switch (type) {
                case HORIZONTAL_LINE ->
                    new ChartDrawing(type, startTime, newStartPrice, endTime, newStartPrice, color, preview);
                case VERTICAL_LINE ->
                    new ChartDrawing(type, newStartTime, startPrice, newStartTime, endPrice, color, preview);
                default -> new ChartDrawing(type, newStartTime, newStartPrice, newEndTime, newEndPrice, color, preview);
            };
        }

        private boolean hitTest(double canvasX, double canvasY, CandleStickChart chart) {
            if (!isDrawable()) {
                return false;
            }

            double x1 = chart.timeToX(startTime);
            double y1 = chart.priceToY(startPrice);
            double x2 = chart.timeToX(endTime);
            double y2 = chart.priceToY(endPrice);
            double tolerance = preview ? 10.0 : 8.0;

            return switch (type) {
                case HORIZONTAL_LINE -> Math.abs(canvasY - y1) <= tolerance;
                case VERTICAL_LINE -> Math.abs(canvasX - x1) <= tolerance;
                case TRENDLINE, MEASURE -> distanceToSegment(canvasX, canvasY, x1, y1, x2, y2) <= tolerance;
                case RECTANGLE -> containsRectangle(canvasX, canvasY, x1, y1, x2, y2, tolerance);
                case TRIANGLE -> containsTriangle(canvasX, canvasY, x1, y1, x2, y2, tolerance);
                case CIRCLE -> containsEllipse(canvasX, canvasY, x1, y1, x2, y2, tolerance);
                case RISK_REWARD -> containsRiskReward(canvasX, canvasY, x1, y1, x2, y2, tolerance);
                case FIBONACCI -> containsFibonacci(canvasX, canvasY, x1, x2, chart, tolerance);
                default -> false;
            };
        }

        private static double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            if (dx == 0.0 && dy == 0.0) {
                return Math.hypot(px - x1, py - y1);
            }

            double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
            t = Math.max(0.0, Math.min(1.0, t));
            double projectionX = x1 + t * dx;
            double projectionY = y1 + t * dy;
            return Math.hypot(px - projectionX, py - projectionY);
        }

        private static boolean containsRectangle(double px, double py, double x1, double y1, double x2, double y2,
                double tolerance) {
            double left = Math.min(x1, x2) - tolerance;
            double right = Math.max(x1, x2) + tolerance;
            double top = Math.min(y1, y2) - tolerance;
            double bottom = Math.max(y1, y2) + tolerance;
            return px >= left && px <= right && py >= top && py <= bottom;
        }

        private static boolean containsTriangle(double px, double py, double x1, double y1, double x2, double y2,
                double tolerance) {
            double left = Math.min(x1, x2);
            double right = Math.max(x1, x2);
            double top = Math.min(y1, y2);
            double bottom = Math.max(y1, y2);
            double apexX = left + ((right - left) / 2.0);

            if (px < left - tolerance || px > right + tolerance || py < top - tolerance || py > bottom + tolerance) {
                return false;
            }

            return pointInTriangle(px, py, apexX, top, right, bottom, left, bottom)
                    || distanceToSegment(px, py, apexX, top, right, bottom) <= tolerance
                    || distanceToSegment(px, py, right, bottom, left, bottom) <= tolerance
                    || distanceToSegment(px, py, left, bottom, apexX, top) <= tolerance;
        }

        private static boolean pointInTriangle(double px, double py, double ax, double ay, double bx, double by,
                double cx, double cy) {
            double area = triangleArea(ax, ay, bx, by, cx, cy);
            if (area == 0.0) {
                return false;
            }
            double area1 = triangleArea(px, py, bx, by, cx, cy);
            double area2 = triangleArea(ax, ay, px, py, cx, cy);
            double area3 = triangleArea(ax, ay, bx, by, px, py);
            return Math.abs(area - (area1 + area2 + area3)) <= 0.5;
        }

        private static double triangleArea(double ax, double ay, double bx, double by, double cx, double cy) {
            return Math.abs((ax * (by - cy) + bx * (cy - ay) + cx * (ay - by)) / 2.0);
        }

        private static boolean containsEllipse(double px, double py, double x1, double y1, double x2, double y2,
                double tolerance) {
            double left = Math.min(x1, x2);
            double right = Math.max(x1, x2);
            double top = Math.min(y1, y2);
            double bottom = Math.max(y1, y2);
            double radiusX = Math.max((right - left) / 2.0, tolerance);
            double radiusY = Math.max((bottom - top) / 2.0, tolerance);
            double centerX = left + radiusX;
            double centerY = top + radiusY;

            double normalized = Math.pow((px - centerX) / (radiusX + tolerance), 2)
                    + Math.pow((py - centerY) / (radiusY + tolerance), 2);
            return normalized <= 1.0;
        }

        private static boolean containsRiskReward(double px, double py, double x1, double y1, double x2, double y2,
                double tolerance) {
            double left = Math.min(x1, x2) - tolerance;
            double right = Math.max(x1, x2) + tolerance;
            double rewardDistance = Math.abs(y2 - y1);
            double top = Math.min(Math.min(y1, y2), y1 - rewardDistance) - tolerance;
            double bottom = Math.max(Math.max(y1, y2), y1 + rewardDistance) + tolerance;
            return px >= left && px <= right && py >= top && py <= bottom;
        }

        private boolean containsFibonacci(double px, double py, double x1, double x2, CandleStickChart chart,
                double tolerance) {
            double top = Math.max(startPrice, endPrice);
            double bottom = Math.min(startPrice, endPrice);
            double range = top - bottom;
            if (!Double.isFinite(range) || range <= 0.0) {
                return false;
            }

            double left = Math.min(x1, x2) - tolerance;
            double right = Math.max(x1, x2) + tolerance;
            if (px < left || px > right) {
                return false;
            }

            double[] levels = { 0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0 };
            for (double level : levels) {
                double price = top - (range * level);
                double y = chart.priceToY(price);
                if (Math.abs(py - y) <= tolerance) {
                    return true;
                }
            }

            return false;
        }
    }

    @Data
    private static final class PriceLine {
        private double price;
        private Color color;
        private String label;
        private boolean visible;
        private boolean dashed;
        private boolean labelVisible;
        private double lineWidth;

        private PriceLine(double price, Color color, String label, boolean dashed) {
            this(price, color, label, true, dashed, true, 1.0);
        }

        private PriceLine(
                double price,
                Color color,
                String label,
                boolean visible,
                boolean dashed,
                boolean labelVisible,
                double lineWidth) {
            this.price = sanitizePrice(price);
            this.color = color == null ? Color.web("#f59e0b") : color;
            this.label = label == null ? "" : label.trim();
            this.visible = visible;
            this.dashed = dashed;
            this.labelVisible = labelVisible;
            this.lineWidth = sanitizeLineWidth(lineWidth);
        }

        private static PriceLine support(double price) {
            return new PriceLine(price, Color.web("#22c55e"), "Support", true, true, true, 1.2);
        }

        private static PriceLine resistance(double price) {
            return new PriceLine(price, Color.web("#ef4444"), "Resistance", true, true, true, 1.2);
        }

        private static PriceLine stopLoss(double price) {
            return new PriceLine(price, Color.web("#dc2626"), "SL", true, true, true, 1.4);
        }

        private static PriceLine takeProfit(double price) {
            return new PriceLine(price, Color.web("#16a34a"), "TP", true, true, true, 1.4);
        }

        private static PriceLine entry(double price) {
            return new PriceLine(price, Color.web("#3b82f6"), "Entry", true, false, true, 1.3);
        }

        private boolean isValid() {
            return Double.isFinite(price) && price > 0.0;
        }

        private PriceLine copy() {
            return new PriceLine(price, color, label, visible, dashed, labelVisible, lineWidth);
        }

        private double getPrice() {
            return price;
        }

        private void setPrice(double price) {
            this.price = sanitizePrice(price);
        }

        private Color getColor() {
            return color;
        }

        private String getLabel() {
            return label;
        }

        private boolean isVisible() {
            return visible;
        }

        private boolean isDashed() {
            return dashed;
        }

        private boolean isLabelVisible() {
            return labelVisible;
        }

        private double getLineWidth() {
            return lineWidth;
        }

        private void setLineWidth() {
            this.lineWidth = sanitizeLineWidth(1.6);
        }

        private static double sanitizePrice(double value) {
            return Double.isFinite(value) && value > 0.0 ? value : 0.0;
        }

        private static double sanitizeLineWidth(double value) {
            if (!Double.isFinite(value) || value <= 0.0) {
                return 1.0;
            }
            return Math.min(value, 6.0);
        }
    }

    // Indicator management methods
    public void openIndicatorDialog() {
        runOnFx(() -> {
            List<String> choices = new ArrayList<>(PluginIndicatorFactory.supportedChoices());
            if (choices.isEmpty()) {
                showErrorMessage("No indicators are currently available.");
                return;
            }
            choices.add("Clear Indicators");

            javafx.scene.control.ChoiceDialog<String> dialog = new javafx.scene.control.ChoiceDialog<>(choices.get(0),
                    choices);
            dialog.setTitle("Indicators");
            dialog.setHeaderText("Add chart indicator");
            dialog.setContentText("Indicator");

            dialog.showAndWait().ifPresent(choice -> {
                if ("Clear Indicators".equals(choice)) {
                    clearIndicators();
                    showTransientNotice("Indicators cleared");
                    return;
                }

                Indicator indicator = createIndicator(choice);
                if (indicator == null) {
                    showErrorMessage("Unsupported indicator: " + choice);
                    return;
                }

                addIndicator(indicator);
                showTransientNotice("Added " + indicator.getName());
            });
        });
    }

    private Indicator createIndicator(String choice) {
        return PluginIndicatorFactory.create(choice, PluginRegistry.loadDefault()).orElse(null);
    }

    public void addIndicator(Indicator indicator) {
        if (indicator != null && indicators.stream().noneMatch(i -> i.getName().equals(indicator.getName()))) {
            indicator.calculate(getAllCandleData());
            indicators.add(indicator);
            requestChartRedraw();
        }
    }

    public void clearIndicators() {
        indicators.clear();
        requestChartRedraw();
    }

    public List<Indicator> getIndicators() {
        return List.copyOf(indicators);
    }

    public void setBackgroundImage(Image image) {
        this.backgroundImage = image;
        persistChartPreferences();
        requestChartRedraw();
    }

    public void setBackgroundImageOpacity(double opacity) {
        this.backgroundImageOpacity = clamp(opacity, 0.0, 1.0);
        persistChartPreferences();
        requestChartRedraw();
    }

    public void clearBackgroundImage() {
        setBackgroundImage(null);
    }

    private void recalculateIndicators() {
        if (indicators.isEmpty()) {
            return;
        }
        List<CandleData> candles = getAllCandleData();
        for (Indicator indicator : List.copyOf(indicators)) {
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
        persistChartPreferences();
        requestChartRedraw();
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
        persistChartPreferences();
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
        if (executor == null) {
            return;
        }

        executor.shutdown();

        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();

                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("Executor did not terminate cleanly after forced shutdown.");
                }
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void reset() {
        if (disposed) {
            log.warn("Cannot reset disposed chart");
            return;
        }

        if (!Platform.isFxApplicationThread()) {
            runOnFx(this::reset);
            return;
        }

        try {
            log.info("Resetting CandleStickChart for {}", tradePair);

            // Clear all data
            data.clear();

            // Clear overlays and indicators
            priceLines.clear();
            indicators.clear();
            chartEvents.clear();

            // Reset chart state
            firstVisibleIndex = 0;
            visibleCandles = TARGET_VISIBLE_CANDLES;
            candleBodyWidth = DEFAULT_BODY_WIDTH;

            // Reset zoom and pan
            mousePrevX = -1;
            mousePrevY = -1;
            scrollDeltaXSum = 0;

            // Reset scroll tracking
            lastScrollTime = 0;
            scrollPositionText = "";

            // Reset price tracking
            visibleMinPrice = 0;
            visibleMaxPrice = 1;
            visibleMaxVolume = 1;

            // Reset crosshair
            crosshairMouseX = -1;
            crosshairMouseY = -1;

            // Reset hover state
            hoveredPrice = -1;
            hoveredTime = -1;

            // Reset drawing interaction state
            selectedDrawingIndex = -1;
            movingAnchor = null;
            movingDrawingOriginal = null;
            drawingPreview = null;
            drawingAnchor = null;

            // Reset market regime
            currentMarketRegime = "RANGING";
            regimeColor = Color.rgb(148, 163, 184);

            // Clear any pending operations
            paging = false;
            loading.set(false);
            drawRequested.set(false);

            // Clear loading timeout
            loadingStartTime = -1L;

            // Hide loading indicator
            hideLoadingIndicator();

            // Trigger redraw
            requestChartRedraw();

            log.info("CandleStickChart reset completed for {}", tradePair);
        } catch (Exception e) {
            log.error("Error resetting CandleStickChart", e);
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
            if (updateMoveDrawing(event)) {
                event.consume();
                return;
            }
            if (updateDrawing(event)) {
                event.consume();
                return;
            }
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

        private static final int MAX_PENDING_TRADES = 4_000;
        private static final int MAX_DRAIN_PER_RUN = 2_000;
        private static final int RECENT_TRADE_CACHE_LIMIT = 20_000;
        private static final long OVERFLOW_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(60);
        private static final long TARGET_RENDER_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

        private final BlockingQueue<Trade> liveTradesQueue = new LinkedBlockingQueue<>(MAX_PENDING_TRADES);
        private final Set<String> recentlyAcceptedTradeKeys = ConcurrentHashMap.newKeySet();
        private final Queue<String> recentlyAcceptedTradeOrder = new ConcurrentLinkedQueue<>();

        private final AtomicReference<Trade> latestTrade = new AtomicReference<>();
        private final AtomicLong droppedOldestSinceLastLog = new AtomicLong(0);
        private final AtomicLong droppedLatestSinceLastLog = new AtomicLong(0);
        private final AtomicLong lastOverflowLogNanos = new AtomicLong(0);
        private final AtomicLong lastRunNanos = new AtomicLong(0);

        @Setter
        private volatile boolean ready;

        @Override
        public boolean containsKey(TradePair tradePair) {
            return tradePair != null && tradePair.equals(CandleStickChart.this.tradePair);
        }

        @Override
        public void remove(TradePair tradePair) {
            if (!containsKey(tradePair)) {
                return;
            }

            liveTradesQueue.clear();
            recentlyAcceptedTradeKeys.clear();
            recentlyAcceptedTradeOrder.clear();
            latestTrade.set(null);
            droppedOldestSinceLastLog.set(0);
            droppedLatestSinceLastLog.set(0);
            lastOverflowLogNanos.set(0);
            lastRunNanos.set(0);

            log.debug("Removed live trade consumer data for {}", tradePair);
        }

        @Override
        public void put(TradePair tradePair) {
            if (containsKey(tradePair)) {
                log.debug("Registered chart live trade consumer for {}", tradePair);
            }
        }

        @Override
        public Trade get(TradePair tradePair) {
            Trade trade = latestTrade.get();

            if (trade == null || tradePair == null) {
                return trade;
            }

            return tradePair.equals(trade.getTradePair()) ? trade : null;
        }

        @Override
        public void accept(Trade trade) {
            if (trade == null || disposed) {
                return;
            }

            if (!containsKey(trade.getTradePair())) {
                return;
            }

            latestTrade.set(trade);

            if (!rememberTrade(trade)) {
                return;
            }

            if (liveTradesQueue.offer(trade)) {
                return;
            }

            Trade dropped = liveTradesQueue.poll();

            if (dropped != null && liveTradesQueue.offer(trade)) {
                droppedOldestSinceLastLog.incrementAndGet();
                logOverflowIfNeeded();
            } else {
                droppedLatestSinceLastLog.incrementAndGet();
                logOverflowIfNeeded();
            }
        }

        private boolean rememberTrade(Trade trade) {
            String key = tradeKey(trade);
            if (!recentlyAcceptedTradeKeys.add(key)) {
                return false;
            }

            recentlyAcceptedTradeOrder.offer(key);
            while (recentlyAcceptedTradeKeys.size() > RECENT_TRADE_CACHE_LIMIT) {
                String oldest = recentlyAcceptedTradeOrder.poll();
                if (oldest == null) {
                    break;
                }
                recentlyAcceptedTradeKeys.remove(oldest);
            }
            return true;
        }

        private String tradeKey(Trade trade) {
            String pair = trade.getTradePair() == null ? "" : trade.getTradePair().toString('/');
            if (trade.getLocalTradeId() != 0L) {
                return pair + "|id|" + trade.getLocalTradeId();
            }

            Instant timestamp = trade.getTimestamp();
            long epochNanos = timestamp == null
                    ? 0L
                    : TimeUnit.SECONDS.toNanos(timestamp.getEpochSecond()) + timestamp.getNano();
            return pair
                    + "|ts|" + epochNanos
                    + "|p|" + Double.doubleToLongBits(trade.getPrice())
                    + "|a|" + Double.doubleToLongBits(trade.getAmount())
                    + "|s|" + trade.getTransactionType();
        }

        private void logOverflowIfNeeded() {
            long now = System.nanoTime();
            long last = lastOverflowLogNanos.get();

            if (now - last < OVERFLOW_LOG_INTERVAL_NANOS) {
                return;
            }

            if (!lastOverflowLogNanos.compareAndSet(last, now)) {
                return;
            }

            long droppedOldest = droppedOldestSinceLastLog.getAndSet(0);
            long droppedLatest = droppedLatestSinceLastLog.getAndSet(0);

            log.warn(
                    "Live trades queue saturated for {}. droppedOldest={}, droppedLatest={}, queueSize={}, capacity={}. Chart remains live, but older trade ticks are being compacted.",
                    tradePair,
                    droppedOldest,
                    droppedLatest,
                    liveTradesQueue.size(),
                    MAX_PENDING_TRADES);
        }

        @Override
        public void acceptTrades(Trade trade) {
            accept(trade);
        }

        @Override
        public void run() {
            if (!ready || disposed) {
                return;
            }

            long now = System.nanoTime();
            long previousRun = lastRunNanos.getAndSet(now);
            if (previousRun > 0) {
                long lagNanos = now - previousRun - TARGET_RENDER_INTERVAL_NANOS;
                if (lagNanos > TimeUnit.MILLISECONDS.toNanos(750)) {
                    log.debug("Chart render lag detected for {} lagMs={} queueSize={}",
                            tradePair,
                            TimeUnit.NANOSECONDS.toMillis(lagNanos),
                            liveTradesQueue.size());
                }
            }

            int drainLimit = liveTradesQueue.size() > MAX_DRAIN_PER_RUN
                    ? Math.min(MAX_PENDING_TRADES, liveTradesQueue.size())
                    : MAX_DRAIN_PER_RUN;
            List<Trade> trades = new ArrayList<>(drainLimit);
            liveTradesQueue.drainTo(trades, drainLimit);

            if (trades.isEmpty()) {
                return;
            }

            boolean changed = false;

            for (Trade trade : trades) {
                if (trade == null || !containsKey(trade.getTradePair())) {
                    continue;
                }

                latestTrade.set(trade);
                updateLatestCandleFromTrade(trade);
                changed = true;
            }

            if (changed) {
                requestChartRedraw();
            }

            if (liveTradesQueue.size() > (MAX_PENDING_TRADES * 3 / 4)) {
                log.debug("Chart trade queue high-watermark pair={} queueSize={} capacity={}",
                        tradePair,
                        liveTradesQueue.size(),
                        MAX_PENDING_TRADES);
            }
        }
    }

    /**
     * Zoom in on the chart (show fewer candles)
     */
    public void zoomIn() {
        if (visibleCandles > MIN_VISIBLE_CANDLES) {
            visibleCandles = Math.max(MIN_VISIBLE_CANDLES, visibleCandles - 5);
            requestChartRedraw();
        }
    }

    /**
     * Zoom out on the chart (show more candles)
     */
    public void zoomOut() {
        visibleCandles = Math.min(MAX_VISIBLE_CANDLES, visibleCandles + 5);
        requestChartRedraw();
    }

    /**
     * Reset zoom to default view
     */
    public void resetZoom() {
        visibleCandles = TARGET_VISIBLE_CANDLES;
        requestChartRedraw();
    }

    // ============================================================================
    // Trade Visualization Overlay Methods
    // ============================================================================

    /**
     * Update trade overlay dimensions and candle data.
     * Called before rendering the overlay each frame.
     */
    private void updateTradeOverlayDimensions(List<CandleData> visibleCandles) {
        if (tradeVisualizationOverlay == null || visibleCandles.isEmpty()) {
            return;
        }

        double chartW = canvas.getWidth();
        double chartH = pricePlotHeight();
        double priceMin = yAxis.getLowerBound();
        double priceMax = yAxis.getUpperBound();
        int visibleCount = this.visibleCandles;

        tradeVisualizationOverlay.updateChartDimensions(chartW, chartH, priceMin, priceMax, visibleCount);
        tradeVisualizationOverlay.updateCandles(getAllCandleData(), firstVisibleIndex,
                firstVisibleIndex + visibleCount - 1);
    }

    /**
     * Add a trade marker (entry/exit point) to the overlay.
     */
    public void addTradeMarker(TradeVisualizationOverlay.TradeMarker marker) {
        if (tradeVisualizationOverlay != null) {
            tradeVisualizationOverlay.addTradeMarker(marker);
            requestChartRedraw();
        }
    }

    /**
     * Add a long entry trade marker.
     */
    public void addLongEntryMarker(double price, long timestamp, String label, double quantity,
            Double takeProfit, Double stopLoss) {
        addTradeMarker(TradeVisualizationOverlay.TradeMarker.builder()
                .type(TradeVisualizationOverlay.TradeType.LONG_ENTRY)
                .price(price)
                .timestamp(timestamp)
                .label(label)
                .quantity(quantity)
                .takeProfit(takeProfit)
                .stopLoss(stopLoss)
                .build());
    }

    /**
     * Add a short entry trade marker.
     */
    public void addShortEntryMarker(double price, long timestamp, String label, double quantity,
            Double takeProfit, Double stopLoss) {
        addTradeMarker(TradeVisualizationOverlay.TradeMarker.builder()
                .type(TradeVisualizationOverlay.TradeType.SHORT_ENTRY)
                .price(price)
                .timestamp(timestamp)
                .label(label)
                .quantity(quantity)
                .takeProfit(takeProfit)
                .stopLoss(stopLoss)
                .build());
    }

    /**
     * Add an exit trade marker.
     */
    public void addExitMarker(double price, long timestamp, String label, double quantity) {
        addTradeMarker(TradeVisualizationOverlay.TradeMarker.builder()
                .type(TradeVisualizationOverlay.TradeType.EXIT)
                .price(price)
                .timestamp(timestamp)
                .label(label)
                .quantity(quantity)
                .build());
    }

    /**
     * Add an order level (take-profit, stop-loss, resistance, support).
     */
    public void addOrderLevel(TradeVisualizationOverlay.OrderLevel level) {
        if (tradeVisualizationOverlay != null) {
            tradeVisualizationOverlay.addOrderLevel(level);
            requestChartRedraw();
        }
    }

    /**
     * Add a take-profit order level.
     */
    public void addTakeProfitLevel(double price, String label) {
        addOrderLevel(TradeVisualizationOverlay.OrderLevel.builder()
                .type(TradeVisualizationOverlay.OrderLevelType.TAKE_PROFIT)
                .price(price)
                .label(label)
                .build());
    }

    /**
     * Add a stop-loss order level.
     */
    public void addStopLossLevel(double price, String label) {
        addOrderLevel(TradeVisualizationOverlay.OrderLevel.builder()
                .type(TradeVisualizationOverlay.OrderLevelType.STOP_LOSS)
                .price(price)
                .label(label)
                .build());
    }

    /**
     * Add a resistance level.
     */
    public void addResistanceLevel(double price, String label) {
        addOrderLevel(TradeVisualizationOverlay.OrderLevel.builder()
                .type(TradeVisualizationOverlay.OrderLevelType.RESISTANCE)
                .price(price)
                .label(label)
                .build());
    }

    /**
     * Add a support level.
     */
    public void addSupportLevel(double price, String label) {
        addOrderLevel(TradeVisualizationOverlay.OrderLevel.builder()
                .type(TradeVisualizationOverlay.OrderLevelType.SUPPORT)
                .price(price)
                .label(label)
                .build());
    }

    /**
     * Add a profit/loss zone.
     */
    public void addPnLZone(TradeVisualizationOverlay.PnLZone zone) {
        if (tradeVisualizationOverlay != null) {
            tradeVisualizationOverlay.addPnLZone(zone);
            requestChartRedraw();
        }
    }

    /**
     * Add a profit zone.
     */
    public void addProfitZone(double priceLow, double priceHigh, String label) {
        addPnLZone(TradeVisualizationOverlay.PnLZone.builder()
                .priceLow(priceLow)
                .priceHigh(priceHigh)
                .isProfit(true)
                .label(label)
                .build());
    }

    /**
     * Add a loss zone.
     */
    public void addLossZone(double priceLow, double priceHigh, String label) {
        addPnLZone(TradeVisualizationOverlay.PnLZone.builder()
                .priceLow(priceLow)
                .priceHigh(priceHigh)
                .isProfit(false)
                .label(label)
                .build());
    }

    /**
     * Clear all trade markers from the overlay.
     */
    public void clearTradeOverlay() {
        if (tradeVisualizationOverlay != null) {
            tradeVisualizationOverlay.clear();
            requestChartRedraw();
        }
    }

    /**
     * Toggle trade overlay visibility.
     */
    public void toggleTradeOverlay() {
        setShowTradeOverlay(!showTradeOverlay);
    }

    /**
     * Set trade overlay visibility.
     */
    public void setShowTradeOverlay(boolean show) {
        showTradeOverlay = show;
        if (tradeOverlayCanvas != null) {
            tradeOverlayCanvas.setVisible(show);
        }
        persistChartPreferences();
        requestChartRedraw();
    }

    /**
     * Get trade info at a specific price level (for tooltips).
     */
    public String getTradeInfoAtPrice(double price) {
        if (tradeVisualizationOverlay == null) {
            return "";
        }
        return tradeVisualizationOverlay.getTradeInfoAtPrice(price);
    }

}
