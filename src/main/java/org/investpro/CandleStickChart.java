package org.investpro;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.print.PrinterJob;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.investpro.ai.TradeStrategy;
import org.investpro.ai.TradingAI;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.SerializationHelper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.investpro.CandleStickChartUtils.*;
import static org.investpro.ChartColors.DarkTheme.*;
import static org.investpro.ChartColors.LightTheme.BEAR_CANDLE_FILL_COLOR;
import static org.investpro.ChartColors.LightTheme.BULL_CANDLE_FILL_COLOR;
import static org.investpro.Side.BUY;
import static org.investpro.Side.SELL;
import static org.investpro.exchanges.Oanda.granularityToString;
import static org.investpro.ui.TradingWindow.db1;

/**
 * A resizable chart that allows for analyzing the trading activity of a commodity over time. The chart is made up of
 * fixed-duration bars that range vertically from the price of the commodity at the beginning of the duration
 * (the open price) to the price at the end of the duration (the close price). Superimposed on these bars is a
 * line that ranges from the lowest price the commodity reached during the duration, to the highest price
 * reached. Hence, the name candle-stick chart (the line being the wick of a candle...although in this case it's
 * a double-ended wick!). The candles are color-coded to represent the type of activity that occurred during the
 * duration of the candle, if the price of the commodity increased during the duration, the candle is colored
 * green and represents a "bullish" trading period. Conversely, if the price decreased then the candle is colored
 * red which represents a "bearish" period. To display a {@code CandleStickChart} in a scene one must use
 * a {@link CandleStickChartContainer}. To enforce this usage, the constructors for this class are package-private.
 * <p>
 * JavaFX offers various charts in it's javafx.scene.chart package, but does not offer a candle-stick
 * chart out-of-the-box. It does however offer an XYChart which could be used as a starting-point for a candle-stick
 * chart. This is the <a href="http://hg.openjdk.java.net/openjfx/9-dev/rt/file/tip/apps/samples/Ensemble8/
 * src/samples/java/ensemble/samples/charts/candlestick/CandleStickChart.java">approach</a>
 * taken by the JavaFX developers for the <a href="http://www.oracle.com/technetwork/java/javase/overview/
 * javafx-samples-2158687.html">Ensemble demos</a> and also by <a href="https://github.com/rterp/StockChartsFX"
 * >StockChartsFX</a>. Indeed, this is the approach that we went with originally but decided to switch to the
 * present {@link Canvas}-based implementation that is contained herein.
 * <p>
 * The main reason for choosing a Canvas-based implementation is that by using a Canvas we obtain pixel-perfect
 * drawing capabilities and precise control over what should be displayed in response to panning and zooming. With the
 * old approach the drawing of the volume bars and the panning and zooming capabilities were all extremely ad-hoc and
 * buggy. For example the panning was simulated by using a ScrollPane which functioned very poorly when paging in
 * new candles (as the bounds of the pane were changing while scrolling was happening so "jumps" would occur).
 * Also in order to implement panning and zooming we needed access to all the chart's internal data (and then some)
 * and so the encapsulation of the chart's data by the Chart class was being completely bypassed.
 *
 * @author NOEL NGUEMECHIEU
 */
@Getter
@Setter
public class CandleStickChart extends Region {


    private static final Label HIGHEST_EXTREMA_LABEL = new Label();
    private static final Label LOWEST_EXTREMA_LABEL = new Label();
    private static final Color HIGHEST_EXTREMA_LABEL_COLOR = Color.YELLOW;
    private static final Color LOWEST_EXTREMA_LABEL_COLOR = Color.BISQUE;
    private final ContextMenu contextMenu;
    private final LineChart<Number, Number> chart;
    private final StableTicksAxis extraAxis = new StableTicksAxis(0, 1000);
    private final StableTicksAxis xAxis = new StableTicksAxis(0, 1000);
    private final StableTicksAxis yAxis = new StableTicksAxis(0, 1000);
    private static final Logger logger = LoggerFactory.getLogger(CandleStickChart.class);
    private static final int MAX_CANDLES = 1000; // Number of candles to keep in memory
    private final TradePair tradePair;
    private final boolean liveSyncing;
    private final Map<Integer, ZoomLevel> zoomLevelMap;
    private final double trailingStopPercentage = 0.02; // 2% trailing stop
    private final Map<Integer, Pair<Extrema, Extrema>> extremaCache = new ConcurrentHashMap<>();
    private final List<CandleData> getCandlesToDraw=new ArrayList<>();



    private final EventHandler<ScrollEvent> scrollHandler;
    TextField size_textField = new TextField();
    /**
     * Draws the chart contents on the canvas ensuring the latest data is always displayed first.
     * It also informs the user when no data is available or when an error occurs.
     */

    private final Exchange exchange;
    Label progressIndLbl = new Label();
    DateTimeFormatter formatter;
    TradingType sessionType;
    private CandleStickChartOptions chartOptions;
    private final TradeHistory tradeHistory;
    TextField trailingStopPercentageTextField = new TextField();
    private final AtomicBoolean isZooming = new AtomicBoolean(false);
    Properties config = new Properties();
    private ScheduledExecutorService updateInProgressCandleExecutor;

    private InProgressCandle inProgressCandle;
    private boolean autoTrading;
    double lastClose = -1;
    private final int secondsPerCandle;
    private Canvas canvas;
    private GraphicsContext graphicsContext;
    private int candleWidth = 10;
    private double mousePrevX = -1;
    private double mousePrevY = -1;
    private double scrollDeltaXSum;
    private UpdateInProgressCandleTask updateInProgressCandleTask;
    long lastUpdateTime;
    private CandleDataSupplier candleDataSupplier;
    private volatile ZoomLevel currZoomLevel;
    private volatile boolean paging;
    private double initialLowerBound = 0;
    private double initialUpperBound = 1000000;
    private ZoomDirection zoomDirection;
    private double chartWidth;
    private double chartHeight;
    private long inProgressCandleLastDraw = -1;
    private TelegramClient telegramBot;
    // Variables to track drag speed
    Instant now = Instant.now();
    private double velocityX = 0;
    private Instant lastDragTime;

    TradingAI tradingAI;
    Timeline timeline ;
    private Consumer<List<CandleData>> candlePageConsumer;
    /**
     * Maps an open time (as a Unix timestamp) to the computed candle data (high price, low price, etc.) for a trading
     * period beginning with that opening time. Thus, the key "1601798498" would be mapped to the candle data for trades
     * from the period of 1601798498 to 1601798498 + secondsPerCandle.
     */

    private List<OrderBook> prices;
    private double pixelsPerMonetaryUnit = candleWidth;

    /**
     * Handles mouse movements to display crosshair or tooltip.
     */
    private double lastMouseX = -1, lastMouseY = -1;
    private int currentPage = 0;
    double highestCandleValue = Double.MIN_VALUE;
    double lowestCandleValue = Double.MAX_VALUE;
    int candleIndexOfHighest = -1;
    int candleIndexOfLowest = -1;
    boolean autoScroll;
    TextField stopLossTextField = new TextField("stop_loss");
    TextField takeProfitTextField = new TextField("take_profit");
    double stopLoss;
    double takeProfit;
    private boolean useTrailingStop;
    private double current_price;
    // 🔹 Pagination Variables
    // Adjust based on performance needs
    private EventHandler<MouseEvent> mouseDraggedHandler;
    private KeyEventHandler keyHandler;
    private final NavigableMap<Integer, CandleData> data = new ConcurrentSkipListMap<>();
    private List<Trade> prices1;
    private long chartId;
    List<Trade> consumersTradeList=new ArrayList<>();
    private Consumer<List<Trade>> tradeConsumer=(c)-> consumersTradeList.addAll(c);

    private List<CandleData> fetchHistoricalData(TradePair tradePair, int pageIndex, int pageSize) {
        try {
            // Fetch data from the database or API with limit and offset
            return getCandleData(tradePair, secondsPerCandle,pageIndex * pageSize, pageSize);
        } catch (Exception e) {
            logger.error("Failed to fetch historical data for {}: {}", tradePair, e.getMessage());
            return Collections.emptyList();
        }
    }


        /**
         * Fetch historical candle data with pagination
         *
         * @param tradePair The currency pair (e.g., "EUR/USD").
         * @param offset    The starting point for fetching data.
         * @param pageSize  The number of records to fetch.
         * @return List of CandleData objects.
         */
        public List<CandleData> getCandleData(@NotNull TradePair tradePair, int timeframe, int offset, int pageSize) {

            try (EntityManager em = db1.entityManager.getEntityManagerFactory().createEntityManager()) {
                List<CandleData> result; // Default empty list
                result = em.createQuery(
                                "SELECT c FROM CandleData c WHERE c.tradePair = :tradePair AND c.timeframe = :timeframe ORDER BY c.openTime DESC",
                                CandleData.class)
                        .setParameter("tradePair", tradePair)
                        .setParameter("timeframe", timeframe) // Fix incorrect parameter binding
                        .setFirstResult(offset)
                        .setMaxResults(pageSize)
                        .getResultList();

                if (result.isEmpty()) {
                    logger.warn("No candle data found for TradePair: {} with timeframe: {}", tradePair, timeframe);
                    return updateInProgressCandleTask.getInProgressCandle().getCandleData();
                }
                return result;
            } catch (Exception e) {
                logger.error("Error fetching CandleData: {}", e.getMessage(), e);
                return updateInProgressCandleTask.getInProgressCandle().getCandleData(); // Return in-progress candle data as fallback
            }
            // Ensure EntityManager is closed properly
        }



    private void updateXAxisFormat(int secondsPerCandle, ZoneId timeZone) {


        if (secondsPerCandle <= 60) {
            // 1-minute and below (Intraday Trading)

            formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(timeZone);
        } else if (secondsPerCandle <= 14400) {
            // 1-minute to 4-hour timeframe

            formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm").withZone(timeZone);
        } else if (secondsPerCandle <= 86400) {
            // Daily timeframe
            formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(timeZone);
        } else {
            // Weekly, Monthly, or higher
            formatter = DateTimeFormatter.ofPattern("MMM yyyy").withZone(timeZone);
        }

        xAxis.setTickLabelFormatter(InstantAxisFormatter.of(formatter));
    }


    private int totalPages = 1;

    private List<CandleData> allCandles = new ArrayList<>(); // Stores all candles
    private List<CandleData> paginatedCandles = new ArrayList<>(); // Current page candles
    private CandleDataPager candleDataPager;
    private String chatId;
    private ProgressIndicator progressIndicator;
    private Font canvasNumberFont;
    private Thread autoScrollThread;
    private Tooltip tooltip = new Tooltip();
    private int chartPadding = 10;
    private String telegramBotToken;
    private EventHandler<? super MouseEvent> mouseClickedHandler;
    private EventHandler<? super MouseEvent> mouseMovedHandler;
    private EventHandler<? super MouseEvent> mouseReleasedHandler;
    private EventHandler<? super MouseEvent> mousePressedHandler;
    private TradeStrategy tradeStrategy;
    private EventHandler<? super ContextMenuEvent> contextMenuHandler;
    private XYChart.Data<CandleData, CandleData> hoveredDataItem = new XYChart.Data<>();
    private int numCandlesToSkip;
    private Canvas overlayCanvas;
    private double trailingStopPrice = 0.0;
    private double mouseDownX;
    private double mouseDownY;
    private boolean isDragging = false;
    private ScheduledExecutorService tooltipExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledExecutorService autoScrollExecutor = Executors.newSingleThreadScheduledExecutor();
    private GraphicsContext overlayGC;
    // Labels for time (X-axis) and price (Y-axis)
    private Label timeLabel = new Label();
    private Label priceLabel = new Label();
private Trade trade;
    /**
     * Creates a new {@code CandleStickChart}. This constructor is package-private because it should only
     * be instantiated by a {@link CandleStickChartContainer}.
     *
     * @param exchange           the {@code Exchange} object on which the trades represented by candles happened on
     * @param telegramBot the {@code CandleDataSupplier} that will supply contiguous chunks of
     *                           candle data, where successive supplies will be farther back in time
     * @param tradePair          the {@code TradePair} that this chart displays trading data for (the base (first) currency
     *                           will be the unit of the volume axis and the counter (second) currency will be the unit of the y-axis)
     * @param liveSyncing        if {@literal true} the chart will be updated in real-time to reflect ongoing trading
     *                           activity
     * @param secondsPerCandle   the duration in seconds each candle represents
     * @param containerWidth     the width property of the parent node that contains the chart
     * @param containerHeight    the height property of the parent node that contains the chart
     */
    CandleStickChart(Exchange exchange, TradePair tradePair,
                     boolean liveSyncing, int secondsPerCandle, ObservableNumberValue containerWidth,
                     ObservableNumberValue containerHeight, TelegramClient telegramBot) throws Exception, TelegramApiException {

        Objects.requireNonNull(exchange);

        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(containerWidth);
        Objects.requireNonNull(containerHeight);
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalArgumentException("CandleStickChart must be constructed on the JavaFX Application " +
                    "Thread but was called from \"" + Thread.currentThread() + "\".");
        }
        this.exchange=exchange;
        this.chartWidth = containerWidth.intValue();
        this.chartHeight = containerHeight.intValue();
        this.canvas = new Canvas(chartWidth - 100, chartHeight - 100);
        this.graphicsContext = canvas.getGraphicsContext2D();
        this.chart = new LineChart<>(xAxis, yAxis);
        this.overlayCanvas = new Canvas(chartWidth - 100, chartHeight - 100);
        this.overlayGC = overlayCanvas.getGraphicsContext2D();
        this.autoTrading = true;
        this.tradeStrategy = new TradeStrategy(TradeStrategy.Strategy.MOVING_AVERAGE);
        this.chartOptions = new CandleStickChartOptions();
        this.telegramBot = telegramBot;
        this.tradePair = tradePair;
        this.inProgressCandle=new InProgressCandle();
        this.secondsPerCandle = secondsPerCandle;
        this.liveSyncing = liveSyncing;
        this.zoomLevelMap = new ConcurrentHashMap<>();
        this.contextMenuHandler = getContextMenuHandlers();
        canvasNumberFont = Font.font(FXUtils.getMonospacedFont(), 12);
        progressIndicator = new ProgressIndicator(-1);
        getStyleClass().add("candle-chart");
        xAxis.setLabel("Time");
        yAxis.setLabel("Price");
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
        extraAxis.setAnimated(false);
        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);
        extraAxis.setAutoRanging(false);
        xAxis.setSide(Side.BOTTOM);
        yAxis.setSide(Side.RIGHT);
        extraAxis.setSide(Side.LEFT);
        xAxis.setForceZeroInRange(false);
        yAxis.setForceZeroInRange(false);
        updateXAxisFormat(secondsPerCandle, ZoneId.systemDefault());
        yAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getCounterCurrency()));
        extraAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getBaseCurrency()));
        Font axisFont = Font.font(FXUtils.getMonospacedFont(), 15);
        yAxis.setTickLabelFont(axisFont);
        xAxis.setTickLabelFont(axisFont);
        extraAxis.setTickLabelFont(axisFont);
        ToolBar trades_setting = new ToolBar();
        trades_setting.setBackground(Background.fill(Color.TRANSPARENT));
        // Thread pool for updating the current in-progress candle


        this.trade = new Trade(exchange, tradePair, BUY, ENUM_ORDER_TYPE.STOP_LOSS, BigDecimal.valueOf(0), BigDecimal.valueOf(10), new Date().toInstant(),BigDecimal.valueOf(stopLoss), BigDecimal.valueOf(takeProfit));





// Thread pool for fetching new candle data periodically





// Define the runnable task to update candles periodically





        trades_setting.setPadding(new Insets(5, 5, 5, 5));
        Button buyButton = new Button("Buy");
        //Load  configuration
        try {
            config.load(
                    Objects.requireNonNull(
                            CandleStickChart.class.getClassLoader().getResourceAsStream("settings.properties")
                    )
            );
            stopLossTextField.setText(String.valueOf(config.getProperty("stopLoss")));

            takeProfitTextField.setText(String.valueOf(config.getProperty("takeProfit")));
            stopLossTextField.setEditable(true);
            takeProfitTextField.setEditable(true);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Error loading configuration file: " + e.getMessage()
            );
        }



        Button sellButton = new Button("Sell");

        this.tradeHistory = new TradeHistory();
        trades_setting.getItems().addAll(buyButton, sellButton, stopLossTextField, takeProfitTextField);

        trades_setting.setTranslateX(100);
        trades_setting.setTranslateY(chartHeight - 250);
        takeProfitTextField.setPrefWidth(100);
        stopLossTextField.setPrefWidth(100);
        getChildren().addAll(trades_setting);
        VBox loadingIndicatorContainer = new VBox(progressIndLbl, progressIndicator);
        progressIndicator.setPrefSize(40, 40);

        loadingIndicatorContainer.setAlignment(Pos.CENTER);
        loadingIndicatorContainer.setMouseTransparent(true);
        chartOptions = new CandleStickChartOptions();
        this.updateInProgressCandleExecutor = Executors.newSingleThreadScheduledExecutor(
                new LogOnExceptionThreadFactory("UPDATE-CURRENT-CANDLE")
        );
        this.updateInProgressCandleTask=new UpdateInProgressCandleTask();

        updateInProgressCandleTask.exchange=exchange;


// Get the candle data supplier from the exchange

// Initialize candle data pager & consumer
        this.candleDataPager = new CandleDataPager(this, exchange.getCandleDataSupplier(secondsPerCandle,tradePair));
        candleDataPager.setInProgressCandle(new CandleData());
        candleDataPager.getCandleDataPreProcessor().accept( CompletableFuture.completedFuture(exchange.getCandleDataSupplier(secondsPerCandle,tradePair).get().get().stream().toList()));
        Consumer<CompletableFuture<List<CandleData>>> response = candleDataPager.getCandleDataPreProcessor().andThen(
                (rx) -> Platform.runLater(() -> {
                    try {
                        if (rx != null && !rx.get().isEmpty()) {
                            data.clear();
                            data.values().addAll(rx.get());
                            drawChartContents(true, 0);
                        } else {
                            logger.warn("No data received for chart updates.");
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }));
        logger.info("Chart updates{}",response);

        this.candlePageConsumer = new CandelDataConsumer(this);
        Consumer<List<CandleData>> re1 = candlePageConsumer.andThen(
                (rx) -> Platform.runLater(() -> {
                    if (rx != null && !rx.stream().toList().isEmpty()) {
                        TradeHistory.addTrade(rx.getFirst());
                        tradeHistory.updateTradeHistory(trade);
                    } else {
                        logger.warn("No data received for trade history updates.");
                    }
                }));
        logger.info("Trade history{}", re1);


        updateInProgressCandleExecutor.scheduleAtFixedRate(() -> {
            try {
                boolean initialized = exchange.getWebsocketClient(exchange, tradePair, secondsPerCandle)
                        .getInitializationLatch()
                        .await(10, TimeUnit.SECONDS);
                updateInProgressCandleTask.inProgressCandle=inProgressCandle;

                updateInProgressCandleTask.accept(candleDataPager.getCandleDataList());
                updateInProgressCandleTask.run();
                updateInProgressCandleTask.setReady(true);

                if (!initialized) {
                    logger.error("Websocket failed to initialize for {}", tradePair);
                } else {
                    logger.info("Websocket initialized for {}", tradePair);
                    exchange.getWebsocketClient(exchange, tradePair, secondsPerCandle)
                            .streamLiveTrades(tradePair, updateInProgressCandleTask);
                    Platform.runLater(() -> drawChartContents(true, 0)); // Force update after websocket data
                }
            } catch (Exception e) {
                logger.error("Error in websocket syncing: ", e);
            }
        }, 5, 5, TimeUnit.SECONDS);


        this.mouseClickedHandler = getMouseClickHandlers();
        this.mouseMovedHandler = getMouseMovedHandlers();
        this.scrollHandler = getScrollHandlers();
        this.mousePressedHandler = getMousePressedHandlers();
        this.mouseReleasedHandler = getMouseReleasedHandlers();
        this.contextMenu = getContextMenus();
        Platform.runLater(() -> getChildren().addAll(xAxis, yAxis, extraAxis,canvas, overlayCanvas));



        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);
        final ChangeListener<Number> sizeListener = new SizeChangeListener(gotFirstSize, containerWidth, containerHeight);
        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);






        // When the application starts up and tries to initialize a candle stick chart the size can
        // fluctuate. So we wait to get the "final" size before laying out the chart. After we get
        // the size, we remove this listener from the gotFirstSize property.
        ChangeListener<? super Boolean> gotFirstSizeChangeListener = new ChangeListener<>() {


            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                double numberOfVisibleWholeCandles = Math.floor(containerWidth.getValue().doubleValue() / candleWidth);
                chartWidth = (numberOfVisibleWholeCandles * candleWidth) - 60 + (((double) candleWidth) / 2);
                chartWidth = (Math.floor(containerWidth.getValue().doubleValue() / candleWidth) * candleWidth) - 60 +
                        ((double) (candleWidth) / 2);
                chartHeight = containerHeight.getValue().doubleValue();


                overlayGC.fillText(
                        "Chart size: " + chartWidth + "x" + chartHeight, 0, 10
                );
                overlayCanvas.setTranslateX(60);


                currZoomLevel = new ZoomLevel(0, candleWidth, secondsPerCandle, canvas.widthProperty(), new InstantAxisFormatter(DateTimeFormatter.ISO_INSTANT), candleWidth);


                StackPane chartStackPane = new StackPane(canvas, loadingIndicatorContainer);

                initializeOverlay();
                chartStackPane.setTranslateX(64); // Only necessary when wrapped in StackPane...why?
                getChildren().addFirst(chartStackPane);


                //Set the layout
                layoutChart();

                Platform.runLater(() -> {
                    double width = Math.max(300, containerWidth.getValue().doubleValue());
                    double height = Math.max(300, containerHeight.getValue().doubleValue());

                    canvas.setWidth(width - 100);
                    canvas.setHeight(height - 100);

                    overlayCanvas.setTranslateX(60);
                    drawChartContents(true, 0);
                });

                // Set event handlers
                canvas.setOnMouseEntered(_ -> canvas.getScene().setCursor(Cursor.HAND));
                canvas.setOnMouseExited(_ -> canvas.getScene().setCursor(Cursor.DEFAULT));
                canvas.setOnScroll(scrollHandler);
                canvas.setOnMouseClicked(mouseClickedHandler);
                canvas.setOnMouseMoved(mouseMovedHandler);
                canvas.setOnMouseDragged(mouseDraggedHandler);
                canvas.setOnKeyPressed(keyHandler);
                canvas.setOnMouseReleased(mouseReleasedHandler);
                canvas.setOnMousePressed(mousePressedHandler);

                chartStackPane.setOnContextMenuRequested(contextMenuHandler);


                // Initialize the  scroll
                initializeMomentumScrolling(); // Enable momentum scrolling


                chartOptions.horizontalGridLinesVisibleProperty().addListener((_, _, _) ->
                        drawChartContents(true,0));
                chartOptions.verticalGridLinesVisibleProperty().addListener((_, _, _) ->
                        drawChartContents(true,0));
                chartOptions.showVolumeProperty().addListener((_, _, _) -> drawChartContents(true,0));
                chartOptions.alignOpenCloseProperty().addListener((_, _, _) -> drawChartContents(true,0));



                gotFirstSize.removeListener(this);
            }


        };
        trades_setting_save();

        buyButton.setOnAction(event -> {

            try {

                double stopLoss = Double.parseDouble(config.getProperty("stopLoss", "0.0"));
                double takeProfit = Double.parseDouble(config.getProperty("takeProfit", "0.0"));



                tradeStrategy.setStopLoss(stopLoss);
                tradeStrategy.setTakeProfit(takeProfit);
                tradeStrategy.setTradePair(tradePair);
                tradeStrategy.setExchange(exchange);
                tradeStrategy.setSize(current_price);

                tradeStrategy.setSide(BUY);

                tradeStrategy.executeBuyCommand();

                event.consume();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });
        sellButton.setOnAction(event -> {

            try {



                double stopLoss = Double.parseDouble(config.getProperty("stopLoss", "0.0"));
                double takeProfit = Double.parseDouble(config.getProperty("takeProfit", "0.0"));

                tradeStrategy.setStopLoss(stopLoss);
                tradeStrategy.setTakeProfit(takeProfit);
                tradeStrategy.setTradePair(tradePair);
                tradeStrategy.setExchange(exchange);
                tradeStrategy.setSize(current_price);
                tradeStrategy.setSide(SELL);

                tradeStrategy.executeSellCommand();

                event.consume();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });
        CompletableFuture.runAsync(() ->
                Platform.runLater(this::initializeTelegramBot));

        gotFirstSize.addListener(gotFirstSizeChangeListener);


    }

    private void trades_setting_save() {
        Properties config = new Properties();
        try (InputStream input = new FileInputStream("settings.properties")) {
            config.load(input);
        } catch (IOException e) {
           logger.error("Failed to load properties");
        }

        config.setProperty("stopLoss", String.valueOf(stopLoss + 0.01));
        config.setProperty("takeProfit", String.valueOf(takeProfit + 0.01));

        try (FileOutputStream output = new FileOutputStream("trades.properties")) {
            config.store(output, "Trade settings");
        } catch (IOException e) {
            logger.error("Failed to save properties");
        }
    }



    @Contract(pure = true)
    private @NotNull EventHandler<? super ContextMenuEvent> getContextMenuHandlers() {
        return event -> {
            if (event.getSource() instanceof CandleStickChart charts) {
                CandleData candleData = charts.getData().lastEntry().getValue();
                if (candleData != null) {
                    ContextMenu contextMenu = new ContextMenu();
                    MenuItem showDetailsItem = new MenuItem("Show Details");
                    showDetailsItem.setOnAction(_ -> showCandleDetailsPopup(candleData));
                    contextMenu.getItems().add(showDetailsItem);
                    contextMenu.show(charts, event.getScreenX(), event.getScreenY());
                }
            }
        };
    }

    private @NotNull ContextMenu getContextMenus() {
        // Create the main ContextMenu
        ContextMenu contextMenu = new ContextMenu();

        // First menu item: Export Chart
        MenuItem exportChartItem = new MenuItem("Export Chart");
        exportChartItem.setAccelerator(KeyCombination.keyCombination("Ctrl+E"));
        exportChartItem.setMnemonicParsing(true);
        exportChartItem.setOnAction(_ -> exportAsPDF());

        // Second menu item: Refresh Chart
        MenuItem refreshChartItem = new MenuItem("Refresh Chart");
        refreshChartItem.setAccelerator(KeyCombination.keyCombination("Ctrl+R"));
        refreshChartItem.setMnemonicParsing(true);
        refreshChartItem.setOnAction(_ -> refreshChart());

        // Third: Create a submenu for additional settings
        Menu settingsMenu = new Menu("Settings");
        settingsMenu.setMnemonicParsing(true);

        // A submenu item: Configure settings
        MenuItem configureItem = new MenuItem("Configure");
        configureItem.setAccelerator(KeyCombination.keyCombination("Ctrl+C"));
        configureItem.setMnemonicParsing(true);
        configureItem.setOnAction(_ -> openConfiguration());
        settingsMenu.getItems().add(configureItem);

        // Add all items to the context menu; a separator can help group related items
        contextMenu.getItems().addAll(exportChartItem, refreshChartItem, new SeparatorMenuItem(), settingsMenu);

        return contextMenu;
    }

    private void openConfiguration() {

        config.setProperty("stopLoss", stopLossTextField.getText());
        config.setProperty("takeProfit", takeProfitTextField.getText());
        config.setProperty("size", size_textField.getText());

        config.setProperty("trailingStopPercentage", trailingStopPercentageTextField.getText());

        Stage stage = new Stage();
        VBox vBox = new VBox(10);
        size_textField.setPromptText(
                "Enter size in pips (e.g., 100 for 100 pips)");
        stopLossTextField.setPromptText(
                "Enter stop loss in pips (e.g., 20 for 20 pips)");

        takeProfitTextField.setPromptText(
                "Enter take profit in pips (e.g., 30 for 30 pips)");


        vBox.getChildren().addAll(stopLossTextField, takeProfitTextField, size_textField);
        Button save = new Button("Save");
        save.setOnAction(_ -> {
            // Save the configuration


            SaveConfiguration(config);
            stage.close();
        });
        vBox.getChildren().add(save);
        stage.setScene(new Scene(vBox, 200, 100));
        stage.show();
    }

    private void SaveConfiguration(@NotNull Properties config) {
        try (OutputStream out = Files.newOutputStream(Paths.get("settings.properties"))) {
            config.store(out, "Configuration");
        } catch (IOException e) {
            throw new RuntimeException("Error saving configuration", e);
        }
    }

    @Contract(pure = true)
    private @NotNull EventHandler<? super MouseEvent> getMouseReleasedHandlers() {
        return _ -> {
            if (isDragging) {
                isDragging = false;
            }


        };
    }

    @Contract(pure = true)
    private @NotNull EventHandler<? super MouseEvent> getMousePressedHandlers() {
        return mouseEvent -> {
            if (mouseEvent.getButton() == MouseButton.PRIMARY) {
                mouseDownX = mouseEvent.getX();
                mouseDownY = mouseEvent.getY();
              //  drawCrosshair(mouseDownX, mouseDownY);
                isDragging = true;
            }

        };
    }

    @Contract(pure = true)
    private @NotNull EventHandler<ScrollEvent> getScrollHandlers() {
        return scrollEvent -> {
            double zoomFactor = currZoomLevel.calculateZoomFactor(scrollEvent.getDeltaY());
            double newWidth = currZoomLevel.getCandleWidth() * zoomFactor;
            double newHeight = chartHeight * zoomFactor;
            double newX = (chartWidth - newWidth) / 2;
            double newY = (chartHeight - newHeight) / 2;
            int currZoomLevels =
                    (int) Math.round(Math.log(newWidth / currZoomLevel.getCandleWidth()) / Math.log(2));
            logger.info("Scroll event: {} (zoom factor: {})", scrollEvent, zoomFactor);
            currZoomLevel = new ZoomLevel(currZoomLevels, (int) newWidth, secondsPerCandle, canvas.widthProperty(), new InstantAxisFormatter(DateTimeFormatter.ISO_INSTANT), candleWidth);
            canvas.setTranslateX(newX);
            canvas.setTranslateY(newY);
            canvas.setWidth(newWidth);
            canvas.setHeight(newHeight);
            drawChartContents(true,1);
        };
    }

    @Contract(pure = true)
    private @NotNull EventHandler<? super MouseEvent> getMouseMovedHandlers() {
        return mouseEvent -> {
            if (mouseEvent.getButton() == MouseButton.PRIMARY) {
                double x = mouseEvent.getX() - 60;
                double y = mouseEvent.getY() - 60;

                drawCrosshair(x, y);
                int index = (int) Math.floor(x / candleWidth);
                if (index >= 0 && index < data.size()) {
                    CandleData candle = data.values().stream().toList().get(index);
                    logger.info("Mouse moved over candle: {}", candle);
                }
            }
        };
    }

    @Contract(pure = true)
    private @NotNull EventHandler<? super MouseEvent> getMouseClickHandlers() {
        return mouseEvent -> {
            if (mouseEvent.getButton() == MouseButton.PRIMARY) {
                double x = mouseEvent.getX() - 60;

                // Ensure data is not empty before showing tooltip
                if (!data.isEmpty()) {
                    showTooltip(data.lastEntry().getValue());
                } else {
                    logger.warn("No data available to display tooltip.");
                    return;
                }

                // Determine the clicked candle index
                int index = (int) Math.floor(x / candleWidth);
                if (index >= 0 && index < data.size()) {
                    List<CandleData> candleList = new ArrayList<>(data.values());
                    CandleData candle = candleList.get(index);
                    logger.info("Clicked on candle: {}", candle);

                    // Optional: Perform additional action on clicked candle
                    handleCandleClick(candle);
                } else {
                    logger.warn("Click position is outside valid candle range. Index: {}, Data size: {}", index, data.size());
                }
            }
        };
    }

    private void handleCandleClick(CandleData candle) {
        if (candle == null) {
            logger.warn("Clicked on an invalid candle (null).");
            return;
        }

        logger.info("Candle clicked: {}", candle);

        // Step 1: Highlight the clicked candle
        highlightCandle(candle);

        // Step 2: Show detailed candle information in a popup
        showCandleDetailsPopup(candle);
    }

    private void highlightCandle(CandleData candle) {
        // Example: Change the color of the clicked candle
        Platform.runLater(() -> {
            // Assuming you have a method to update the candle's style
            candle.setHighlighted(true); // You need to implement this in your CandleData class
            refreshChart();
        });
    }

    private void showCandleDetailsPopup(CandleData candle) {
        // Example: Create a popup with detailed candle information
        Platform.runLater(() -> {
            // Assuming you have a method to create a popup with candle details
            createCandleDetailsPopup(candle);
        });
    }

    /**
     * Applies a smooth momentum effect when the user releases the drag.
     */
    private void applyMomentumScrolling(double initialVelocity) {

        if (Math.abs(initialVelocity) < 1) return;

        double deceleration = 0.95;
        DoubleProperty velocityProperty = new SimpleDoubleProperty(initialVelocity);
        timeline = new Timeline(new KeyFrame(Duration.millis(16), _ -> {
            double newVelocity = velocityProperty.get() * deceleration;

            if (Math.abs(newVelocity) < 1 || xAxis.getLowerBound() <= data.firstKey() || xAxis.getUpperBound() >= data.lastKey()) {
                timeline.stop();
            } else {
                moveXAxisByPixels(newVelocity * 0.016);
                velocityProperty.set(newVelocity);
            }
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }


    /**
     * Sets the bounds of the x-axis either one full candle to the right or left, depending on the sign
     * of deltaX. Currently, the magnitude of deltaX does not matter (each call to this method only moves
     * the duration of one full candle).
     *
     * @param deltaX set the bounds either one candle over to the right or left from the current position
     */
    private void setAxisBoundsForMove(int deltaX) {
        if (deltaX == 1) {
            xAxis.setUpperBound(xAxis.getUpperBound() + secondsPerCandle);
            xAxis.setLowerBound(xAxis.getLowerBound() + secondsPerCandle);
        } else if (deltaX == -1) {
            xAxis.setUpperBound(xAxis.getUpperBound() - secondsPerCandle);
            xAxis.setLowerBound(xAxis.getLowerBound() - secondsPerCandle);
        } else {
            throw new IllegalArgumentException("deltaX must be 1 or -1 but was: " + deltaX);
        }
    }

    private void adjustYAxisScaling() {
        double yAxisRange = yAxis.getUpperBound() - yAxis.getLowerBound();
        double newUpperBound = yAxis.getUpperBound() - (yAxisRange * 0.1);
        double newLowerBound = yAxis.getLowerBound() + (yAxisRange * 0.1);
        yAxis.setUpperBound(newUpperBound);
        yAxis.setLowerBound(newLowerBound);
    }

    /**
     * Moves the xAxis based on user dragging.
     */
    private void moveXAxisByPixels(double deltaX) {
        double shiftAmount = deltaX * (xAxis.getUpperBound() - xAxis.getLowerBound()) / (chartWidth - 100);
        xAxis.setLowerBound(xAxis.getLowerBound() - shiftAmount);
        xAxis.setUpperBound(xAxis.getUpperBound() - shiftAmount);

    }

    private void sendTelegramAlert(String message) {
        try {
            telegramBot.sendMessage(telegramBot.getChatId(), "📈 Trade Alert: " + message);
            logger.info("Telegram Alert Sent: {}", message);
        } catch (Exception e) {
            logger.error("Failed to send Telegram alert: ", e);
        }
    }

    private void createCandleDetailsPopup(@NotNull CandleData candle) {
        // Assuming you have a method to create a popup with candle details
        String popupContent = "Candle Details:\n" +
                "Open: " + candle.getOpenPrice() + "\n" +
                "High: " + candle.getHighPrice() + "\n" +
                "Low: " + candle.getLowPrice() + "\n" +
                "Close: " + candle.getClosePrice() + "\n" +
                "Volume: " + candle.getVolume();

        Alert popup = new Alert(Alert.AlertType.INFORMATION);
        popup.setTitle("Candle Details");
        popup.setHeaderText(null);
        popup.setContentText(popupContent);
        popup.setResizable(true);
        popup.setOnShowing(
                _ -> {
                    // Add custom CSS styling to the popup
                    popup.getDialogPane().setStyle("-fx-background-color: rgba(49,195,177,0.73); -fx-text-fill: white;");
                }

        );
        popup.setOnHidden(
                _ -> {
                    // Reset the candle's style
                    candle.setHighlighted(false);
                    popup.hide();

                    // Refresh the chart
                    refreshChart();


    });}

    private @NotNull String determineSignal(@NotNull CandleData candle) {
        // Simple strategy: buy when close price is above 20% of open price and sell when close price is below 80% of open price
        double openPrice = candle.getOpenPrice();
        double closePrice = candle.getClosePrice();
        double threshold = 0.2;

        if (closePrice > openPrice * (1 + threshold)) {
            return "BUY";
        } else if (closePrice < openPrice * (1 - threshold)) {
            return "SELL";
        } else {
            return "HOLD";
        }
    }

    private void trainAIWithHistoricalData(List<CandleData> candles) {
        if (candles.isEmpty()) {
            logger.info("No historical data available for training AI");
            return;
        }

        try {


            // Fetch historical market data
            List<CandleData> historicalData = new ArrayList<>(candles);// Example: 500 candles

            // Define attributes
            ArrayList<Attribute> attributes = new ArrayList<>();
            attributes.add(new Attribute("open"));
            attributes.add(new Attribute("high"));
            attributes.add(new Attribute("low"));
            attributes.add(new Attribute("close"));
            attributes.add(new Attribute("volume"));

            // Define class labels
            ArrayList<String> classValues = new ArrayList<>();
            classValues.add("HOLD");
            classValues.add("BUY");
            classValues.add("SELL");
            attributes.add(new Attribute("class", classValues));

            // Create training dataset
            Instances trainingData = new Instances("MarketData", attributes, 0);
            trainingData.setClassIndex(attributes.size() - 1);

            // Populate dataset with historical data
            for (CandleData candle : candles) {
                DenseInstance instance = new DenseInstance(attributes.size());
                instance.setDataset(trainingData);
                instance.setValue(attributes.get(0), candle.getOpenPrice());
                instance.setValue(attributes.get(1), candle.getHighPrice());
                instance.setValue(attributes.get(2), candle.getLowPrice());
                instance.setValue(attributes.get(3), candle.getClosePrice());
                instance.setValue(attributes.get(4), candle.getVolume());

                // Assign class label based on simple strategy (e.g., breakout trading)
                String signal = determineSignal(candle);
                instance.setValue(attributes.get(5), classValues.indexOf(signal));

                trainingData.add(instance);
            }// Normalize data before training AI
            for (int i = 0; i < trainingData.numAttributes() - 1; i++) {
                double mean = trainingData.attributeStats(i).numericStats.mean;
                double stdDev = trainingData.attributeStats(i).numericStats.stdDev;
                for (int j = 0; j < trainingData.numInstances(); j++) {
                    trainingData.instance(j).setValue(i, (trainingData.instance(j).value(i) - mean) / stdDev);
                }
            }

            // Train AI model
            tradingAI = new TradingAI(trainingData);
            logger.info("Training AI model{}",trainingData);
// Save model after training
            SerializationHelper.write("ai_trading_model.model", tradingAI);

// Load model instead of retraining
            if (new File("ai_trading_model.model").exists()) {
                tradingAI = (TradingAI) SerializationHelper.read("ai_trading_model.model");
            } else {
                tradingAI.trainModel();
            }

            logger.info("AI Model successfully trained with historical market data.");

        } catch (Exception e) {
            logger.error("Error training AI with historical data: ", e);
        }
    }

    private void computeExtremaIfNeeded(int zoomLevel) {
        if (!extremaCache.containsKey(zoomLevel)) {
            extremaCache.put(zoomLevel, computeExtremaForZoomLevel(zoomLevel));
        }
    }

    private void executeMoveLogic(int deltaX, boolean skipDraw) {
        if (getData().isEmpty()) {
            logger.warn("Candle data is empty or null. Skipping move logic.");
            return;
        }

        int desiredXLowerBound = (int) xAxis.getLowerBound() + (deltaX == 1 ? secondsPerCandle : -secondsPerCandle);
        int minCandlesRemaining = 3;

        if (desiredXLowerBound <= getData().descendingMap().lastEntry().getValue().getOpenTime() - (minCandlesRemaining - 1) * secondsPerCandle) {
            setAxisBoundsForMove(deltaX);
            setYAndExtraAxisBounds();
            if (!skipDraw) {
                drawChartContents(true,4);
            }
        }
    }

    /**
     * Sets the y-axis and extra axis bounds using only the x-axis lower bound.
     */
    private void setYAndExtraAxisBounds() {
        logger.info("xAxis lower bound: {}", (int) xAxis.getLowerBound());

        final double idealBufferSpaceMultiplier = 0.35;
        int adjustedXValue = (int) xAxis.getLowerBound() - secondsPerCandle; // Offset for correct extrema retrieval


        // Retrieve extrema values safely using Optional
        Pair<Extrema, Extrema> extremaPair = currZoomLevel.getExtremaForCandleRangeMap().get(adjustedXValue);
        if (extremaPair == null) {
            logger.warn("Extrema values missing for x-value: {}. Using fallback values.", adjustedXValue);
            setDefaultYAxisBounds();
            return;
        }

        double yAxisMax = Extrema.getMax(extremaPair);
        double yAxisMin = Extrema.getMin(extremaPair);
        double yAxisDelta = yAxisMax - yAxisMin;

        // Apply buffer space for visibility improvement
        yAxis.setUpperBound(yAxisMax + (yAxisDelta * idealBufferSpaceMultiplier));
        yAxis.setLowerBound(Math.max(0, yAxisMin - (yAxisDelta * idealBufferSpaceMultiplier))); // Prevent negative lower bounds

        // Set extra axis bounds based on extrema
        extraAxis.setUpperBound(yAxisMax);
        extraAxis.setLowerBound(yAxisMin);
    }

    /**
     * Fallback method to set default y-axis bounds when extrema data is missing.
     */
    private void setDefaultYAxisBounds() {
        final double defaultUpperBound = 100.0; // Adjust based on typical data range
        final double defaultLowerBound = 0.0;

        yAxis.setUpperBound(defaultUpperBound);
        yAxis.setLowerBound(defaultLowerBound);

        extraAxis.setUpperBound(defaultUpperBound);
        extraAxis.setLowerBound(defaultLowerBound);

        logger.info("Default y-axis bounds set: ({}, {})", yAxis.getLowerBound(), yAxis.getUpperBound());
    }

    private void layoutChart() {
        logger.info("CandleStickChart.layoutChart start");

        graphicsContext.setFill(Color.BLACK);
        graphicsContext.fillRect(0, 0, chartWidth, chartHeight);

        double top = snappedTopInset();
        double left = snappedLeftInset();
        top = snapPositionY(top);
        left = snapPositionX(left);

        // Default candle width
        double candleWidth = 10;  // Adjust based on your preference

        // Calculate the number of candles to display (adjusting for axis width)
        double numberOfCandles = Math.floor((chartWidth - (2 * yAxis.getWidth())) / candleWidth);

        // Recalculate candle width to fit perfectly within the available space
        candleWidth = (chartWidth - (2 * yAxis.getWidth())) / numberOfCandles;

        // Get max candle value in a blocking way to avoid async issues
        double maxCandleValue;

        maxCandleValue = data.values()

                .stream()
                .map(CandleData::getMax)
                .max(Double::compareTo)
                .orElse(1.0);  // Default to 1.0 to prevent division by zero

        if (maxCandleValue == 0)
            maxCandleValue = 1;


        // Calculate candle width based on the max value

        // Calculate candle height based on the max value
        double candleHeight = chartHeight / maxCandleValue;

        // Define axis dimensions
        double xAxisHeight = Math.max(candleHeight * 2, 30);  // Ensure minimum height for visibility
        double yAxisWidth = Math.max(candleWidth * 2, 40);  // Ensure minimum width
        // Make space for the X-axis

        // Ensure values are valid
        yAxisWidth = Math.max(yAxisWidth, 0);
        xAxisHeight = Math.max(xAxisHeight, 0);

        // Left Y-Axis (extraAxis)
        extraAxis.setPrefSize(yAxisWidth, canvas.getHeight());

        // Right Y-Axis (Primary yAxis)
        yAxis.setPrefSize(yAxisWidth, canvas.getHeight());

        yAxis.setLabel("Price");

        // X-Axis (Bottom)
        xAxis.setPrefSize((chartWidth - 100), xAxisHeight);
        xAxis.setLayoutX(left + yAxisWidth); // Align with candle width
        xAxis.setLayoutY(chartHeight - xAxisHeight);
        xAxis.setLabel("Time");
        xAxis.setTranslateY(chartHeight - 100);
        yAxis.setTranslateY(canvas.getTranslateY());
        yAxis.setTranslateX(-canvas.getTranslateX() + chartWidth - 40);
        extraAxis.setTranslateY(canvas.getTranslateY());
        extraAxis.setTranslateX(-canvas.getTranslateX() + 50);
        xAxis.setTranslateX(-canvas.getTranslateX() + 75);
        xAxis.setLayoutY(-top);
        xAxis.setLayoutX(left);
        yAxis.setLayoutY(canvas.getTranslateY());
        extraAxis.setLayoutY(canvas.getTranslateY());
        extraAxis.setLayoutX(canvas.getTranslateX());
        // Request layout updates
        extraAxis.requestAxisLayout();
        extraAxis.layout();
        yAxis.requestAxisLayout();
        yAxis.layout();
        xAxis.requestAxisLayout();
        xAxis.layout();
        double yAxisMax = getData().values()

                .stream()
                .map(CandleData::getMax)
                .max(Double::compareTo)
                .orElse(1.0);  // Default to 1.0 to prevent division by zero

        // Adjust y-axis bounds to accommodate buffer space
        double yAxisDelta = 1;// Default to 1.0 to prevent division by zero

        double yAxisMin = Math.min(0, yAxisMax - (yAxisDelta * 0.35)); // Prevent negative bounds
        yAxis.setUpperBound(yAxisMax + (yAxisDelta * 0.35)); // Ensure buffer space
        yAxis.setLowerBound(Math.max(0, yAxisMin - (yAxisDelta * 0.35))); // Prevent negative bounds

        // Adjust canvas position to fit inside axes
        canvas.setLayoutX(left + yAxisWidth);
        canvas.setLayoutY(top);
        canvas.requestFocus();
        canvas.setCursor(Cursor.DEFAULT);


        logger.info("CandleStickChart.layoutChart end");
    }

    private @NotNull Pair<Extrema, Extrema> computeExtremaForZoomLevel(int zoomLevel) {


        int windowSize = (int) Math.pow(2, zoomLevel);
        List<Double> closePrices = new ArrayList<>();
        for (CandleData data : data.values()) {
            closePrices.add(data.getClosePrice());
        }
        List<Double> finalClosePrices = closePrices;
        Supplier<? extends List<Double>> supplier =
                () -> IntStream.range(0, data.size() - windowSize + 1)
                        .mapToObj(i -> finalClosePrices.subList(i, i + windowSize))
                        .mapToDouble(List::getFirst)

                        .boxed()
                        .collect(Collectors.toList());

        AtomicReference<Double> left =
                new AtomicReference<>(supplier.get().stream()
                        .min(
                                Comparator.comparingDouble(Double::doubleValue)
                        )
                        .orElseThrow(() -> new IllegalStateException("No data points found")));
        AtomicReference<Double> right =
                new AtomicReference<>(supplier.get().stream()
                        .max(
                                Comparator.comparingDouble(Double::doubleValue)
                        )
                        .orElseThrow(() -> new IllegalStateException("No data points found")));
        supplier.get().forEach(closePrices::add);

        ObjDoubleConsumer<Double> accumulator = (aDouble, value) -> {
            if (aDouble < left.get()) left.set(value);
            if (value > right.get()) right.set(value);
        };


        Supplier<Double> supplier1 = () -> 0.0;
        BiConsumer<Double, Double> combiner = new BiConsumer<>() {
            @Override
            public void accept(Double aDouble, Double aDouble2) {


                left.set(Math.min(left.get(), aDouble));
                right.set(Math.max(right.get(), aDouble2));

            }

            @Override
            public @NotNull BiConsumer<Double, Double> andThen(@NotNull BiConsumer<? super Double, ? super Double> after) {
                return BiConsumer.super.andThen(after);
            }
        };
        closePrices = Collections.singletonList(data.values().stream()
                .mapToDouble(CandleData::getClosePrice)
                .limit(windowSize).collect(supplier1, accumulator, combiner));

        double sum = closePrices.stream().mapToDouble(Double::doubleValue).sum();
        double average = sum / windowSize;

        Extrema lowExtrema = new Extrema(average, average);
        Extrema highExtrema = new Extrema(average, average);
        return new Pair<>(lowExtrema, highExtrema);
    }

    private synchronized void initializeTelegramBot() {


        if (telegramBot.isOnline()) {

            startTelegramPolling();
        }
            chartId = telegramBot.getChatId();

        }

    private void debounceTooltipUpdate(CandleData candle) {
        tooltipExecutor.schedule(() -> Platform.runLater(() -> tooltip.setText(getTooltipText(candle))), 1000, TimeUnit.MILLISECONDS);
    }

    private @NotNull String getTooltipText(@NotNull CandleData candle) {
        return String.format("Open: %.2f, Close: %.2f, High: %.2f, Low: %.2f, Volume: %.2f",
                candle.getOpenPrice(), candle.getClosePrice(), candle.getHighPrice(), candle.getLowPrice(), candle.getVolume());
    }

    private void showTooltip(@NotNull CandleData candleData1) {

        debounceTooltipUpdate(candleData1);
        tooltip.setText(
                getTooltipText(candleData1)
        );
        tooltip.show(this,
                chartWidth - 200, chartHeight / 3);
        tooltip.setX(chartWidth - tooltip.getWidth() - 200);
        tooltip.setY(chartHeight / 3);
        tooltip.setOnAutoHide(
                (_) -> tooltip.hide()
        );
        tooltip.setOpacity(0.9);

    }

    private @NotNull String getSessionType(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 60 -> TradingType.SCALPING.name();  // 1-minute candles
            case 300 -> TradingType.DAY_TRADING.name();  // 5-minute candles
            case 900 -> TradingType.SWING_TRADING.name();  // 15-minute candles
            case 1800 -> TradingType.SWING_TRADING.name(); // 30-minute candles
            case 3600 -> TradingType.POSITION_TRADING.name(); // 1-hour candles
            case 14400 -> TradingType.TREND_TRADING.name(); // 4-hour candles
            case 86400 -> TradingType.POSITION_TRADING.name(); // 1-day candles
            case 604800 -> TradingType.POSITION_TRADING.name(); // 1-week candles
            default -> "Unknown";
        };
    }

    @Contract(pure = true)
    private @NotNull String getTradeMode() {
        return (autoTrading) ? "MANUAL" : "AUTO";

    }

    private String formatVolume(double volume) {
        return NumberFormat.getInstance().format(volume);
    }

    private String formatPrice(double openPrice) {
        return NumberFormat.getInstance().format(openPrice);
    }

    private void initializeMomentumScrolling() {
        AtomicReference<Double> velocityX = new AtomicReference<>(0.0); // Initial velocity in pixels per second
        AtomicReference<Double> lastMouseX = new AtomicReference<>(0.0); // X position of the mouse at the start of dragging
        AtomicReference<Instant> lastDragTime = new AtomicReference<>(Instant.now()); // Time of the last mouse drag

        if (canvas == null)
            return;

        // Calculate the amount of pixels to move the chart based on the initial velocity
        // Track mouse drag start
        canvas.setOnMousePressed(event -> {

            // Calculate initial velocity based on mouse position
            lastMouseX.set(event.getX());
            lastDragTime.set(Instant.now());
            velocityX.set(0.0);

        });


        // Track mouse movement and calculate velocity
        canvas.setOnMouseDragged(event -> {
            double currentX = event.getX();
            Instant currentTime = Instant.now();

            // Calculate time difference in seconds
            double timeDiff = Duration.millis(lastDragTime.get().toEpochMilli() - currentTime.toEpochMilli()).toMillis() / 1000.0;
            if (timeDiff > 0) {
                velocityX.set((currentX - lastMouseX.get()) / timeDiff); // Speed = Distance / Time
            }

            lastMouseX.set(currentX);
            lastDragTime.set(currentTime);

            // Move the chart as the user drags
            moveXAxisByPixels(currentX - lastMouseX.get());

        });

        // Apply momentum scrolling on release
        canvas.setOnMouseReleased(_ -> applyMomentumScrolling(velocityX.get()));
    }

    private Trade executeTrade(SIGNAL signal, double price) {



            try {
                double amount = 0.02;
                double stopLoss = price * 0.99;
                double takeProfit = price * 1.02;

                if (signal == SIGNAL.BUY) {


                     trade.createOrder(
                             tradePair, BigDecimal.valueOf(price), BigDecimal.valueOf(amount),BUY, ENUM_ORDER_TYPE.STOP_LOSS, new Date().toInstant(), BigDecimal.valueOf(stopLoss), BigDecimal.valueOf(takeProfit), secondsPerCandle,tradeConsumer
                     );



                    sendTelegramAlert("✅ BUY order executed at $" + price);
                } else if (signal == SIGNAL.SELL) {

                    trade.createOrder(
                            tradePair, BigDecimal.valueOf(price), BigDecimal.valueOf(amount),SELL, ENUM_ORDER_TYPE.STOP_LOSS, new Date().toInstant(), BigDecimal.valueOf(stopLoss), BigDecimal.valueOf(takeProfit), secondsPerCandle,tradeConsumer
                    );




                    sendTelegramAlert("❌ SELL order executed at $" + price);
                }
            } catch (Exception e) {
                logger.error("Trade execution failed: ", e);
                sendTelegramAlert("⚠️ Error executing trade: " + e.getMessage());
            }
        return trade;


    }

    private void moveAlongX(int deltaX, boolean skipDraw) {
        if (deltaX != 1 && deltaX != -1) {
            throw new RuntimeException("deltaX must be 1 or -1 but was: " + deltaX);
        }

        Platform.runLater(() -> {
            if (!progressIndicator.isVisible()) {
                executeMoveLogic(deltaX, skipDraw);
                adjustYAxisScaling();
                progressIndLbl.setText("");
            } else {
                progressIndLbl.setText(
                        "Loading...%"+getProgress()+"..."
                );
            }
        });

        adjustXAxisScaling();
        moveXAxisByPixels(deltaX * 0.016); // Move xAxis 16 pixels per millisecond


    }

    private void adjustXAxisScaling() {
        double xAxisRange = xAxis.getUpperBound() - xAxis.getLowerBound();
        double newUpperBound = xAxis.getUpperBound() - (xAxisRange * 0.05);
        double newLowerBound = xAxis.getLowerBound() + (xAxisRange * 0.05);
        xAxis.setUpperBound(newUpperBound);
        xAxis.setLowerBound(newLowerBound);
    }

    /**
     * Handles logic for live-syncing new candles and shifting the chart accordingly.
     */
    private void handleLiveSyncing(int numCandlesToSkip) {
        if (xAxis.getUpperBound() >= inProgressCandleLastDraw &&
                xAxis.getUpperBound() < inProgressCandleLastDraw + ((chartWidth - 100) * secondsPerCandle)) {
            if (numCandlesToSkip == 0) {
                moveAlongX(1, true);
            }
        }
        inProgressCandleLastDraw = (int) inProgressCandle.getOpenTime();
    }

    /**
     * Clears the chart canvas before redrawing.
     */
    private void clearChartCanvas() {
        graphicsContext.setFill(Color.BLACK);
        graphicsContext.fillRect(0, 0, chartWidth - 100, chartHeight - 100);
    }

    /**
     * Calculates the number of pixels per monetary unit to correctly position candles.
     */
    private double calculatePixelsPerMonetaryUnit() {
        double monetaryUnitsPerPixel = (yAxis.getUpperBound() - yAxis.getLowerBound()) / canvas.getHeight();
        return 1d / monetaryUnitsPerPixel;
    }

    /**
     * Draws the chart contents on the canvas corresponding to the current x-axis, y-axis, and extra (volume) axis
     * bounds.
     */
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MILLIS = 100;

    private void drawChartContentsInternal(boolean clearCanvas, int retryCount) {
        if (Platform.isFxApplicationThread()) {
            // Already on JavaFX thread, execute directly
            drawChartContents(clearCanvas, retryCount);
        } else {
            // Ensure execution on JavaFX thread
            Platform.runLater(() -> drawChartContents(clearCanvas, retryCount));
        }
    }

    private void drawChartContents(boolean clearCanvas, int retryCount) {
        if (clearCanvas) {
            clearChartCanvas();
        }

        int numVisibleCandles = getNumVisibleCandles();
        NavigableMap<Integer, CandleData> candlesToDraw = data;

        logger.info("Drawing chart with {} candles (visible: {})", candlesToDraw.size(), numVisibleCandles);

        if (candlesToDraw.isEmpty()) {
            if (retryCount < MAX_RETRIES) {
                logger.warn("No candles to draw. Retrying... attempt: {}", retryCount + 1);
                graphicsContext.fillText(
                        "No candles to draw. Retrying... attempt: {}", retryCount + 1,
                        xAxis.getWidth() / 2, yAxis.getHeight() / 2
                );
                PauseTransition pause = new PauseTransition(Duration.millis(RETRY_DELAY_MILLIS));
                pause.setOnFinished(_ -> drawChartContentsInternal(clearCanvas, retryCount + 1));
                pause.play();
            } else {
                logger.warn("No candles to draw after {} retries.", MAX_RETRIES);

                graphicsContext.fillText(
                        "No candles to draw after {} retries.", MAX_RETRIES,
                        xAxis.getWidth() / 2, yAxis.getHeight() / 2
                );
            }
            return; // Stop execution if no data
        }

        // Proceed to draw chart if candles are available
        drawGridLines();
        drawMarketInfos(candlesToDraw);
        drawVolumeBars(candlesToDraw, calculatePixelsPerMonetaryUnit());
        drawBidAsk(candlesToDraw);
        drawXAxisLabels(candlesToDraw);

        // Draw candlestick values
        candlesToDraw.descendingMap().values().forEach(candle -> {
            double currentY = calculateY(candle.getMax());
            double currentX = calculateX(candle.getOpenTime());
            graphicsContext.strokeText(String.format("%.2f", candle.getMax()), currentX, currentY);
            double spacingX = (canvas.getWidth() - xAxis.getWidth()) / (numVisibleCandles - 1);
            currentX += spacingX;
            graphicsContext.strokeText(String.format("%.2f", candle.getClosePrice()), currentX, currentY);
        });

        drawCandles(candlesToDraw, calculatePixelsPerMonetaryUnit());
    }



    private double calculateX(int openTime) {
        return (canvas.getWidth() - xAxis.getWidth()) * (openTime - data.firstKey()) / (data.lastKey() - data.firstKey()) + xAxis.getTranslateX();
    }

    private double calculateY(Double max) {
        return (canvas.getHeight() - (yAxis.getHeight() * (max - yAxis.getLowerBound()) / (yAxis.getUpperBound() - yAxis.getLowerBound()))) + yAxis.getTranslateY();
    }

    private int getNumVisibleCandles() {
        return (int) Math.floor((canvas.getWidth() - xAxis.getWidth()) / candleWidth);
    }

    private double calculateCandleHeight(double volume) {
        double monetaryUnitSize = (yAxis.getUpperBound() - yAxis.getLowerBound()) / canvas.getHeight();
        return volume / monetaryUnitSize;
    }

    private double calculateYPosition(double closePrice) {
        double monetaryUnitSize = (yAxis.getUpperBound() - yAxis.getLowerBound()) / canvas.getHeight();
        double y = (yAxis.getUpperBound() - closePrice) / monetaryUnitSize;
        return yAxis.getLayoutY() + y * canvas.getHeight();
    }

    private double calculateCandleWidth() {
        // 80% of the candle width
        return secondsPerCandle * 0.8;
    }

    private double calculateXPosition(int openTime) {
        double x = (openTime - (int) xAxis.getLowerBound()) * secondsPerCandle;
        return xAxis.getLayoutX() + x;
    }

    private void drawMarketInfos(@NotNull NavigableMap<Integer, CandleData> candlesToDraws) {
        graphicsContext.setFill(Color.GRAY);
        graphicsContext.setStroke(Color.BLACK);
        graphicsContext.setFont(new Font("Arial", 16));

        double baseX = 20;
        double baseY = 20;
        double spacingX = 150; // Horizontal spacing for columns
        double spacingY = 25;  // Vertical spacing for rows

        if (candlesToDraws.lastEntry() == null) return;
        double currentX = baseX;
        double currentY = baseY;
        // 🟢 Spread Calculation (Bid-Ask)
        CandleData latestCandle = candlesToDraws.lastEntry().getValue();
        if (latestCandle == null) {
            logger.warn("No candle data available to draw market information");
            graphicsContext.fillText(
                    "Market Information unavailable",
                    xAxis.getWidth() / 2, yAxis.getHeight() / 2
            );
            return;
        }

        // 🟢 Percentage Change Calculation
        double percentageChange = ((latestCandle.getClosePrice() - latestCandle.getOpenPrice()) / latestCandle.getOpenPrice()) * 100;

//        double bidPrice =// prices1.stream().toList().stream().findFirst().get().getPrice().getBidEntries().getFirst().getPrice() - 0.0001; // Example bid
//        double askPrice = prices1.stream().toList().stream().findFirst().get().getPrice().getAskEntries().getFirst().getPrice() + 0.0001; // Example ask
//        double spread = askPrice - bidPrice;
        // 🟢 Display Symbol and Timeframe
        graphicsContext.fillText(
                "Symbol: " + tradePair.toString('/') +
                        "  Time: " + new SimpleDateFormat("HH:mm:ss").format(new Date()) +
                        "  TimeFrame: " + granularityToString(secondsPerCandle) +
                        " Spread: N/A" + " %Change: " + percentageChange,
                currentX, currentY
        );

        // Move to next row
        currentY += spacingY;

        // 🟢 Display OHLC Data
        graphicsContext.fillText("O: " + formatPrice(latestCandle.getOpenPrice()), currentX, currentY);
        currentX += spacingX;
        graphicsContext.fillText("H: " + formatPrice(latestCandle.getHighPrice()), currentX, currentY);
        currentX += spacingX;
        graphicsContext.fillText("L: " + formatPrice(latestCandle.getLowPrice()), currentX, currentY);
        currentX += spacingX;
        graphicsContext.fillText("C: " + formatPrice(latestCandle.getClosePrice()), currentX, currentY);
        currentX += spacingX;
        graphicsContext.fillText("V: " + formatVolume(latestCandle.getVolume()), currentX, currentY);
        currentX += spacingX;
        graphicsContext.fillText("Session: " + getSessionType(secondsPerCandle), currentX, currentY);
        // 🟢 Trading Signal
        currentX += spacingX;
        graphicsContext.fillText("Signal: " + determineSignal(latestCandle), currentX + 100, currentY);
        // Move to next row
        currentY += spacingY;
        currentX = baseX;


        // 🟢 Bot & Trading Session Information
        currentY += spacingY;
        graphicsContext.fillText("Bot: " + telegramBot.getUsername(), baseX, currentY);
        currentX += spacingX + 50;


        graphicsContext.fillText("Mode: " + getTradeMode(), currentX, currentY);


    }

    private void drawBidAsk(@NotNull NavigableMap<Integer, CandleData> data) {


        double bottomY = canvas.getHeight() - extraAxis.getHeight();
        double left =
                extraAxis.getBoundsInParent().getMinX() + extraAxis.getWidth() / 2;

        double leftX = left + yAxis.getWidth();
        double rightX = (chartWidth - 100) - extraAxis.getWidth();
        double labelWidth = 50; // Adjust based on your preference
        double labelHeight = 20; // Adjust based on your preference

        // Calculate X-axis labels position and size
        double labelSpacing = (rightX - leftX) / (prices1.size() - 1);
        double labelX = leftX;

        // Draw X-axis labels
        for (Map.Entry<Integer, CandleData> entry : data.entrySet()) {
            int candleTime = entry.getKey();

            // Calculate X-axis label position and size
            double labelXPosition = labelX + (labelWidth / 2);
            double labelYPosition = bottomY + (labelHeight / 2);

            // Draw X-axis label
            graphicsContext.setTextAlign(TextAlignment.CENTER);
            graphicsContext.setTextBaseline(VPos.CENTER);

            Paint randomColor =
                    new Random().nextDouble() > 0.5 ? Color.BLACK : Color.WHITE; // Random color for labels
            graphicsContext.setFill(randomColor); // Black color for labels
            graphicsContext.fillText(String.valueOf(Date.from(Instant.ofEpochSecond(candleTime))), labelXPosition, labelYPosition);

            // Update label position for the next label
            labelX += labelSpacing;
        }
        if (prices1.size() < 2) {
            return;
        }
        // Draw bid and ask prices
        double bidPrice = 0.23;
        double askPrice = 0.34;

        // Calculate bid and ask prices position and size
        double bidPriceYPosition = bottomY - 20;


        // Draw bid and ask prices
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(Color.RED); // Black color for labels
        graphicsContext.fillText("Bid: " + bidPrice, leftX, bidPriceYPosition);
        graphicsContext.setFill(Color.GREEN); // White color for labels
        graphicsContext.fillText("Ask: " + askPrice, rightX, bidPriceYPosition);
    }

    /**
     * Updates the highest and lowest candle values for extrema markers.
     */
    private void updateExtremaValues(@NotNull CandleData candleDatum, int candleIndex) {
        if (candleDatum.getHighPrice() > highestCandleValue) {
            highestCandleValue = candleDatum.getHighPrice();
            candleIndexOfHighest = candleIndex;
        }

        if (candleDatum.getLowPrice() < lowestCandleValue) {
            lowestCandleValue = candleDatum.getLowPrice();
            candleIndexOfLowest = candleIndex;
        }
    }

    private void drawXAxisLabels(@NotNull NavigableMap<Integer, CandleData> candlesToDraw) {
        // X-axis labels are drawn on the bottom side of the chart
        double bottomY = canvas.getHeight() - extraAxis.getHeight();
        double left =
                extraAxis.getBoundsInParent().getMinX() + extraAxis.getWidth() / 2;

        double leftX = left + yAxis.getWidth();
        double rightX = (chartWidth - 100) - extraAxis.getWidth();
        double labelWidth = 50; // Adjust based on your preference
        double labelHeight = 20; // Adjust based on your preference

        // Calculate X-axis labels position and size
        double labelSpacing = (rightX - leftX) / (data.size() - 1);
        double labelX = leftX;

        // Draw X-axis labels
        for (Map.Entry<Integer, CandleData> entry : candlesToDraw.entrySet()) {
            int candleTime = entry.getKey();

            // Calculate X-axis label position and size
            double labelXPosition = labelX + (labelWidth / 2);
            double labelYPosition = bottomY + (labelHeight / 2);

            // Draw X-axis label
            graphicsContext.setTextAlign(TextAlignment.CENTER);
            graphicsContext.setTextBaseline(VPos.CENTER);
            graphicsContext.setFill(Color.web("#000000")); // Black color for labels
            graphicsContext.fillText(formatter.format(Instant.ofEpochMilli(candleTime)), labelXPosition, labelYPosition);

            // Update label position for the next label
            labelX += labelSpacing;
        }
    }

    /**
     * Draws placeholder candles where no trading occurred.
     */
    private void drawPlaceholderCandle(CandleData candleDatum, int candleIndex, double pixelsPerMonetaryUnit, double lastClose) {
        graphicsContext.beginPath();
        double candleOpenPrice = chartOptions.isAlignOpenClose() && lastClose != -1 ? lastClose : candleDatum.getOpenPrice();
        double candleYOrigin = cartesianToScreenCoords((candleOpenPrice - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        graphicsContext.moveTo(((chartWidth - 100) - (candleIndex * candleWidth)) + 1, candleYOrigin);
        graphicsContext.rect(canvas.getWidth() - (candleIndex * candleWidth), candleYOrigin, candleWidth - 1, 1);
        graphicsContext.setFill(PLACE_HOLDER_FILL_COLOR);
        graphicsContext.fill();
        graphicsContext.setStroke(PLACE_HOLDER_BORDER_COLOR);
        graphicsContext.setLineWidth(1);
        graphicsContext.stroke();


    }

    /**
     * Draws an actual candle including the body, wicks, and volume bar.
     */
    private void drawActualCandle(@NotNull CandleData candleDatum, int candleIndex, double pixelsPerMonetaryUnit) {
        // Determine colors
        boolean openAboveClose = candleDatum.getOpenPrice() > candleDatum.getClosePrice();
        Paint candleBorderColor = openAboveClose ? BEAR_CANDLE_BORDER_COLOR : BULL_CANDLE_BORDER_COLOR;
        Paint candleFillColor = openAboveClose ? BEAR_CANDLE_FILL_COLOR : BULL_CANDLE_FILL_COLOR;

        double candleYOrigin = cartesianToScreenCoords(
                ((openAboveClose ? candleDatum.getOpenPrice() : candleDatum.getClosePrice()) - yAxis.getLowerBound()) * pixelsPerMonetaryUnit
        );

        double candleHeight = Math.abs(candleDatum.getOpenPrice() - candleDatum.getClosePrice()) * pixelsPerMonetaryUnit;

        // Draw the candle body
        graphicsContext.beginPath();
        graphicsContext.rect((chartWidth - 100) - (candleIndex * candleWidth), candleYOrigin, candleWidth - 2, candleHeight - 2);
        graphicsContext.setFill(candleFillColor);
        graphicsContext.fill();
        graphicsContext.setStroke(candleBorderColor);
        graphicsContext.setLineWidth(2);
        graphicsContext.stroke();
    }

    /**
     * Draws high and low markers for extrema values.
     */
    private void drawExtremaMarkers(double highestCandleValue, double lowestCandleValue, int candleIndexOfHighest, int candleIndexOfLowest) {
        if (candleIndexOfHighest != -1) {

            drawMarker(highestCandleValue, candleIndexOfHighest, HIGHEST_EXTREMA_LABEL, HIGHEST_EXTREMA_LABEL_COLOR);
        }

        if (candleIndexOfLowest != -1) {
            drawMarker(lowestCandleValue, candleIndexOfLowest, LOWEST_EXTREMA_LABEL, LOWEST_EXTREMA_LABEL_COLOR);
        }

    }

    private void drawMarker(double highestCandleValue, int candleIndexOfHighest, Label highestExtremaLabel, Color highestExtremaLabelColor) {
        Platform.runLater(() -> {
            double candleYOrigin = cartesianToScreenCoords(
                    (highestCandleValue - yAxis.getLowerBound()) * pixelsPerMonetaryUnit
            );

            double markerXPos = (chartWidth - 100) - (candleIndexOfHighest * candleWidth) - ((double) candleWidth / 2);
            double markerYPos = candleYOrigin - 10; // Offset to prevent overlap
            final double HIGHEST_EXTREMA_LABEL_OFFSET = 5;

            // Update label properties
            highestExtremaLabel.setLayoutX(markerXPos);
            highestExtremaLabel.setLayoutY(candleYOrigin - HIGHEST_EXTREMA_LABEL_OFFSET);
            highestExtremaLabel.setText(String.format("%.2f", highestCandleValue));
            highestExtremaLabel.setTextFill(highestExtremaLabelColor);
            highestExtremaLabel.setStyle("-fx-font-weight: bold; -fx-background-color: rgb(47,191,58);");

            // Ensure label is added only once
            if (!getChildren().contains(highestExtremaLabel)) {
                getChildren().add(highestExtremaLabel);
            }

            // Draw marker arrow (optional)
            drawMarkerArrow(markerXPos, markerYPos, highestExtremaLabelColor);
        });
    }

    private void drawVolumeBars(@NotNull NavigableMap<Integer, CandleData> candlesToDraw, double pixelsPerMonetaryUnit) {
        this.pixelsPerMonetaryUnit = pixelsPerMonetaryUnit;
        // Volume axis is drawn on the right side of the chart
        double rightX = (chartWidth - 100) - extraAxis.getWidth();
        double volumeAxisHeight = extraAxis.getHeight();

        double left =
                ((chartWidth - 100) - extraAxis.getWidth() - rightX) / 2; // Center the volume axis

        // Calculate maximum volume to determine the height of the volume bars
        double barWidth = (rightX - left) / data.size();

        // Draw volume bars
        for (Map.Entry<Integer, CandleData> entry : candlesToDraw.entrySet()) {
            CandleData candleData = entry.getValue();

            // Calculate volume bar dimensions
            double maxVolume =
                    // Ensure volume is not zero to prevent division by zero
                    candleData.getVolume() > 0 ? candleData.getVolume() : 1;

            // Calculate volume bar position and height based on the candle's open time and volume
            double volumeHeight = (candleData.getVolume() / maxVolume) * volumeAxisHeight;
            double barY = (canvas.getHeight() - extraAxis.getHeight()) - (volumeHeight / 2);

            // Draw volume bar
            graphicsContext.setFill(Color.BLUE); // Green color for volume bars
            graphicsContext.fillRect(rightX - barWidth - 2, barY, barWidth, volumeHeight);
            graphicsContext.fillText(String.format("%.2f", candleData.getVolume()),
                    rightX - barWidth - 10, barY + volumeHeight / 2);
        }
    }

    private double cartesianToScreenCoords(double yCoordinate) {
        return -yCoordinate + canvas.getHeight();
    }

    /**
     * Calculates how many candles to skip based on the current x-axis bounds.
     * Implements a retry mechanism in case of data unavailability.
     */
    private int calculateNumCandlesToSkip() {

        int numCandlesToSkip;


        Map.Entry<Integer, CandleData> lastEntry = getData().lastEntry();


        if (lastEntry == null) {
            lastEntry = Map.entry(0, new CandleData());
        }


        synchronized (getData()) {
            numCandlesToSkip = (int) (((chartWidth - 100) / pixelsPerMonetaryUnit) / (getData().size() * secondsPerCandle));

            // Ensure numCandlesToSkip does not exceed available data range
            while (true) {

                if (!(lastEntry.getKey() < getXAxis().getUpperBound() - numCandlesToSkip * secondsPerCandle))
                    break;
                numCandlesToSkip++;

                if (numCandlesToSkip * secondsPerCandle > lastEntry.getKey() - Objects.requireNonNull(xAxis).getLowerBound()) {
                    break;
                }
            }
        }

        int openTime = lastEntry.getValue().getOpenTime();
        return Math.max(((int) getXAxis().getUpperBound() - openTime) / secondsPerCandle, 0);
    }

    /**
     * Retrieves the set of candles that should be drawn based on the current x-axis bounds.
     */
    private NavigableMap<Integer, CandleData> getCandlesToDraw(int numCandlesToSkip, int numVisibleCandles) {


          // Ensure there is at least one candle.
          NavigableMap<Integer, CandleData> candlesToDraw = getData();

          int startTime;

          // Adjust the number of visible candles if the available data is less.
          if (candlesToDraw.size() < numVisibleCandles) {
              numVisibleCandles = getData().size();
          }


          // Calculate the time boundaries for the subMap.
          // 'endTime' is calculated by taking the xAxis upper bound, subtracting one candle's duration.
          int endTime = (int) xAxis.getUpperBound() - secondsPerCandle;
          // 'startTime' is determined by going back numVisibleCandles from the end.
          startTime = endTime - (numVisibleCandles * secondsPerCandle);
          // 'skipTime' adjusts the upper limit of the subMap by skipping a specified number of candles.
          int skipTime = endTime - (numCandlesToSkip * secondsPerCandle);
          // Ensure the subMap does not exceed the available data range.
          if (startTime < 0 || skipTime < 0) {
              logger.info(
                      "Cannot draw candles. Start time or skip time is negative."
              );
              return Collections.emptyNavigableMap();
          }


          // Return the portion of the data that falls within the calculated time window.
          return candlesToDraw.subMap(startTime, true, skipTime, true);

    }

    /**
     * Draws horizontal and vertical grid lines based on chart settings.
     */
    private void drawGridLines() {


        if (graphicsContext == null) {
            logger.info(
                    "Cannot draw grid lines. Graphics context is null."
            );
            return;
        }
        if (chartOptions.isHorizontalGridLinesVisible()) {
            for (Axis.TickMark<Number> tickMark : yAxis.getTickMarks()) {
                graphicsContext.setStroke(Color.rgb(189, 189, 189, 0.6));
                graphicsContext.setLineWidth(1.5);
                graphicsContext.strokeLine(0, tickMark.getPosition(), (chartWidth - 100), tickMark.getPosition());
            }
        }

        if (chartOptions.isVerticalGridLinesVisible()) {
            for (Axis.TickMark<Number> tickMark : xAxis.getTickMarks()) {
                graphicsContext.setStroke(Color.rgb(189, 189, 189, 0.6));
                graphicsContext.setLineWidth(1.5);
                graphicsContext.strokeLine(tickMark.getPosition(), 0, tickMark.getPosition(), (chartHeight - 100));
            }
        }
        if (chartOptions.isGridVisible()) {
            graphicsContext.setStroke(Color.rgb(189, 189, 189, 0.6));
            graphicsContext.setLineWidth(1.5);
            graphicsContext.strokeRect(0, 0, (chartWidth - 100), (chartHeight - 100));
        }

        if (chartOptions.isShowVolume()) {
            for (CandleData candleData : getCandlesToDraw(calculateNumCandlesToSkip(), getNumVisibleCandles()).values()) {
                drawVolumeBar(candleData);
            }
        }
        if (chartOptions.isAlignOpenClose()) {
            for (CandleData candleData : getCandlesToDraw(calculateNumCandlesToSkip(), getNumVisibleCandles()).values()) {
                drawOpenClose(candleData);
            }
        }
    }


    CandleStickChartOptions getChartOptions() {
        return chartOptions;
    }

    // Handles the case when returning to an existing zoom level.
    private void updateExistingZoomLevel(int nextZoomLevelId) {
        currZoomLevel = zoomLevelMap.get(nextZoomLevelId);

        List<CandleData> candleData = new ArrayList<>(data.values());

        // Compute extrema only for new live-syncing data since the last time we used this zoom level.
        List<CandleData> newLiveSyncData = candleData.subList(
                Math.max(0, candleData.size() - currZoomLevel.getNumVisibleCandles()),
                candleData.size()
        );

        computeExtrema(currZoomLevel, newLiveSyncData);
    }

    @Override
    protected double computeMinWidth(double height) {
        return 200;
    }

    @Override
    protected double computeMinHeight(double width) {
        return 200;
    }

    private void drawOpenClose(@NotNull CandleData candleData) {
        double x = calculateXPosition(candleData.getOpenTime());
        double width = calculateCandleWidth();
        double y = calculateYPosition(candleData.getOpenPrice());
        double height = calculateCandleHeight(candleData.getClosePrice() - candleData.getOpenPrice());

        graphicsContext.setStroke(Color.TRANSPARENT);
        graphicsContext.setFill(Color.web(
                candleData.getClosePrice() > candleData.getOpenPrice() ? "#008000" : "#FF0000"

        ));
        graphicsContext.fillRect(x, y, width, height);
    }

    @Override
    protected double computePrefHeight(double width) {
        return chartHeight;
    }


    @Override
    protected double computePrefWidth(double height) {
        return chartWidth;
    }

    private void drawVolumeBar(@NotNull CandleData candleData) {
        double x = calculateXPosition(candleData.getOpenTime());
        double width = calculateCandleWidth();
        double y = calculateYPosition(candleData.getClosePrice());
        double height = calculateCandleHeight(candleData.getVolume());

        graphicsContext.setStroke(Color.TRANSPARENT);
        graphicsContext.setFill(Color.RED);
        graphicsContext.fillRect(x, y, width, height);
    }

    /**
     * Draws the candle sticks, placeholders, and volume bars.
     */
    private void drawCandles(@NotNull NavigableMap<Integer, CandleData> candlesToDraw, double pixelsPerMonetaryUnit) {
        logger.info("Drawing {} candles.", candlesToDraw.size());


        AtomicReference<Double> lastClose = new AtomicReference<>(-1.0);

        double highestCandleValue = Double.MIN_VALUE;
        double lowestCandleValue = Double.MAX_VALUE;
        int candleIndexOfHighest = -1;
        int candleIndexOfLowest = -1;


        // Process candles in batches

        CompletableFuture.runAsync(() -> {
            List<List<CandleData>> batches = splitIntoBatches(candlesToDraw.values());

            for (List<CandleData> batch : batches) {
                Platform.runLater(() -> {
                    int index = calculateNumCandlesToSkip();
                    for (CandleData batchCandle : batch) {
                        updateExtremaValues(batchCandle, index);
                        drawSingleCandle(batchCandle, index, pixelsPerMonetaryUnit, lastClose.get());
                        lastClose.set(batchCandle.getClosePrice());

                        index++;
                    }
                });
            }
        }).thenRunAsync(() -> Platform.runLater(() ->
                drawExtremaMarkers(highestCandleValue, lowestCandleValue, candleIndexOfHighest, candleIndexOfLowest)
        )).exceptionally(
                e -> {
                    throw new RuntimeException("Error occurred while drawing candles: ", e);

                }
        );

    }

    private @NotNull List<List<CandleData>> splitIntoBatches(@NotNull Collection<CandleData> values) {
        List<List<CandleData>> batches = new ArrayList<>();

        List<CandleData> currentBatch = new ArrayList<>();
        for (CandleData candle : values) {
            currentBatch.add(candle);
            if (currentBatch.size() >= 50) {
                batches.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
            }
        }
        if (!currentBatch.isEmpty()) {
            batches.add(new ArrayList<>(currentBatch));
        }
        return batches;
    }


    public void captureScreenshot() {
        try {
            BufferedImage image = new BufferedImage((int) canvas.getWidth(), (int) canvas.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();

            // Translate the Graphics2D object to the center of the canvas.


            g2d.dispose();
            ImageIO.write(image, "png", new File("chart.png"));
        } catch (IOException e) {
            logger.error("Error capturing screenshot: ", e);
            throw new RuntimeException(
                    "Failed to capture screenshot, see error log for details.");
        }
    }

    /**
     * Draws a single candle including wicks, body, and volume bar.
     */
    private void drawSingleCandle(@NotNull CandleData candleDatum, int candleIndex, double pixelsPerMonetaryUnit, double lastClose) {
        if (candleDatum.isPlaceHolder()) {
            drawPlaceholderCandle(candleDatum, candleIndex, pixelsPerMonetaryUnit, lastClose);
        } else {
            drawActualCandle(candleDatum, candleIndex, pixelsPerMonetaryUnit);
        }
        drawVolumeBar(candleDatum);

        graphicsContext.closePath();
    }

    /**
     * Draws an arrow to indicate the extrema marker.
     */
    private void drawMarkerArrow(double x, double y, Color color) {


        if (graphicsContext == null) {
            logger.info(
                    "Unable to draw arrow. Graphics context is null. Chart may not be displayed correctly."
            );
            return;
        }
        graphicsContext.setStroke(color);
        graphicsContext.setLineWidth(2);

        // Draw a small arrow above the label
        graphicsContext.strokeLine(x + 5, y, x + 5, y - 10);
        graphicsContext.strokeLine(x + 2, y - 5, x + 8, y - 5);
    }

    void changeZoom(ZoomDirection zoomDirection) {
        if (isZooming.get()) return; // Prevent multiple zooms at once

        isZooming.set(true);
        Platform.runLater(() -> {
            int newCandleWidth = currZoomLevel.getCandleWidth() + (zoomDirection == ZoomDirection.IN ? -1 : 1);
            if (newCandleWidth <= 1) return;
            handleNewZoomLevel(
                    newCandleWidth, newCandleWidth,
                    (int) (canvas.getWidth() / (newCandleWidth * pixelsPerMonetaryUnit)),
                    (int) (canvas.getWidth() / (currZoomLevel.getCandleWidth() * pixelsPerMonetaryUnit))
            );


            pruneData();  // Keep data optimized
            drawChartContents(false,0);

            isZooming.set(false);
        });
    }

    private void pruneData() {
        while (data.size() > MAX_CANDLES) {
            data.pollFirstEntry(); // Remove oldest candle
        }
    }

    // Handles the case when a new zoom level needs to be created.
    private void handleNewZoomLevel(int nextZoomLevelId, int newCandleWidth, int newLowerBoundX, int currMinXValue) {
        ZoomLevel newZoomLevel = new ZoomLevel(nextZoomLevelId, newCandleWidth, secondsPerCandle,
                canvas.widthProperty(), getXAxisFormatterForRange(xAxis.getUpperBound() - newLowerBoundX),
                currMinXValue);

        if (data.isEmpty()) {
            data.putAll(newZoomLevel.createInitialData());
        }

        int numCandlesToSkip = Math.max((((int) xAxis.getUpperBound()) -
                data.values().stream().toList().stream().map(CandleData::getOpenTime).toList().getFirst()) / secondsPerCandle, 0);

        if (newLowerBoundX - (numCandlesToSkip * secondsPerCandle) < currZoomLevel.getMinXValue()) {
            paging = true;
            Platform.runLater(() -> progressIndicator.setVisible(true));

            CompletableFuture
                    .supplyAsync(getCandleDataSupplier())
                    .thenApply(candleData -> {
                                List<CandleData> newCandleData;
                                try {
                                    newCandleData = new ArrayList<>(candleData.get());
                                } catch (InterruptedException | ExecutionException e) {
                                    throw new RuntimeException(e);
                                }
                                newCandleData.addAll(newCandleData.size(), data.sequencedValues());
                                return newCandleData;
                            }
                    )


                    .thenRunAsync(() -> Platform.runLater(() -> {
                        try {
                            List<CandleData> candleData = new ArrayList<>(data.values());
                            computeExtrema(newZoomLevel, candleData);
                            zoomLevelMap.put(nextZoomLevelId, newZoomLevel);
                            currZoomLevel = newZoomLevel;
                        } finally {
                            progressIndicator.setVisible(false);
                            paging = false;
                        }
                    }))
                    .exceptionally(throwable -> {
                        throw new RuntimeException("Exception in data paging: ", throwable);

                    });
        } else {
            List<CandleData> candleData = new ArrayList<>(data.values());
            computeExtrema(newZoomLevel, candleData);
            zoomLevelMap.put(nextZoomLevelId, newZoomLevel);
            currZoomLevel = newZoomLevel;
            updateExistingZoomLevel(currZoomLevel.getZoomLevelId());
        }
    }

    // Helper method to compute extrema for zoom levels.
    private void computeExtrema(@NotNull ZoomLevel zoomLevel, List<CandleData> candleData) {
        putSlidingWindowExtrema(zoomLevel.getExtremaForCandleRangeMap(), candleData, zoomLevel.getNumVisibleCandles());
        putExtremaForRemainingElements(zoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                candleData.size() - zoomLevel.getNumVisibleCandles(), candleData.size()));
    }

    public void setAutoScroll(boolean enable) {
        if (enable) {
            // Prevent multiple executions
            if (autoScrollExecutor.isShutdown()) {
                autoScrollExecutor = Executors.newSingleThreadScheduledExecutor();
            }

            // Use JavaFX Timeline instead of Thread.sleep()
            Timeline autoScrollTimeline = new Timeline(
                    new KeyFrame(Duration.seconds(1), event -> {
                        double currentX = xAxis.getValueForDisplay(xAxis.getLowerBound()).doubleValue();
                        double newX = currentX - 100;

                        if (newX < xAxis.getLowerBound()) {
                            newX = xAxis.getUpperBound();
                        }

                        xAxis.setLowerBound(newX);
                        event.consume();
                    })
            );
            autoScrollTimeline.setCycleCount(Animation.INDEFINITE);
            autoScrollTimeline.play();
        } else {
            autoScrollExecutor.shutdown();
        }
    }




    public void print() {
        // Create a printer job
        PrinterJob printerJob = PrinterJob.createPrinterJob();

        if (printerJob != null && printerJob.showPrintDialog(null)) {
            boolean success = printerJob.printPage(this);

            if (success) {
                printerJob.endJob();
                showAlert("Print Success", "Chart printed successfully.");
            } else {
                showAlert("Print Failed", "Could not print the chart.");
            }
        } else {
            showAlert("Print Canceled", "Printing was canceled by the user.");
        }
    }

    // Utility method to show alerts
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Fetches Telegram bot updates in a non-blocking way.
     */
    public void startTelegramPolling() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> CompletableFuture.runAsync(() -> {
            try {
                List<TelegramBotInfo> updates = telegramBot.fetchUpdates();
                updates.forEach(update -> logger.warn("New Message: {}", update.getMessage()));
            } catch (Exception e) {
                logger.error("Error fetching Telegram updates", e);
                throw new RuntimeException("Failed to fetch Telegram updates, see error log for details.");
            }
        }), 5, 5, SECONDS);
    }

    // Copies the shareable link to the system clipboard
    private void copyToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    // Opens the shareable link in the default web browser
    private void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                desktop.browse(URI.create(url));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to open browser: " + e.getMessage());
        }
    }

    public void changeNavigation(NavigationDirection navigationDirection) {
        if (!(navigationDirection instanceof NavigationDirection direction)) {
            throw new IllegalArgumentException("Invalid navigation direction: " + null);
        }

        switch (direction) {
            case LEFT:
                navigateLeft();
                break;
            case RIGHT:
                navigateRight();
                break;
            case UP:
                navigateUp();
                break;
            case DOWN:
                navigateDown();

                break;
            default:
                throw new IllegalArgumentException("Unsupported navigation direction: " + direction);
        }
    }

    // Helper methods for navigation
    private void navigateLeft() {

        logger.info("Navigating left...");
        double stepSize = 50; // Adjust step size
        double newTranslateX = canvas.getTranslateX() - stepSize;
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), canvas);
        transition.setToX(newTranslateX);
        transition.play();
        logger.info("Smoothly moved right to new X position: {}", newTranslateX);
    }

    private void navigateRight() {

        logger.info("Navigating right...");
        double stepSize = 50; // Adjust step size

        double newTranslateX = -canvas.getTranslateX() + stepSize;

        TranslateTransition transition = new TranslateTransition(Duration.millis(300), canvas);
        transition.setToX(newTranslateX);
        transition.play();
    }
    // Create a separate overlay canvas (once, during initialization)

    private boolean checkTrendConfirmation(SIGNAL signal, double price) {
        // Calculate percentage change from current price to 0.0 (or previous close price)

        // and compare it with the desired percentage change (e.g., 20% for a buy signal)
        double priceChange = calculatePercentChange(price, data.values().stream().toList());

        if (signal == SIGNAL.BUY || signal == SIGNAL.STRONG_BUY) {
            return priceChange > 0.2; // Only buy if price is rising
        } else if (signal == SIGNAL.SELL || signal == SIGNAL.STRONG_SELL) {
            return priceChange < -0.2; // Only sell if price is falling
        }
        return false;
    }

    private double calculatePercentChange(double price, @NotNull List<CandleData> list) {
        double previousPrice = list.get(list.size() - 2).getClosePrice();
        return (price - previousPrice) / previousPrice;
    }

    private void navigateUp() {

        logger.info("Navigating up...");
        double stepSize = 50; // Adjust step size
        double newTranslateY = canvas.getTranslateY() - stepSize;
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), canvas);
        transition.setToY(newTranslateY);
        transition.play();
        logger.info("Smoothly moved down to new Y position: {}", newTranslateY);

    }

    private void navigateDown() {
        logger.info("Navigating down...");

        double stepSize = 50; // Adjust step size
        double newTranslateY = canvas.getTranslateY() + stepSize;

        TranslateTransition transition = new TranslateTransition(Duration.millis(300), canvas);
        transition.setToY(newTranslateY);
        Interpolator kvInterpolator = new Interpolator() {
            @Override
            protected double curve(double t) {
                return Math.sin(Math.PI * t);
            }
        };
        transition.setInterpolator(kvInterpolator);

        transition.setInterpolator(kvInterpolator);
        transition.play();

        logger.info("Smoothly moving down to new Y position: {}", newTranslateY);

    }

    public void shareLink() {
        try {
            // Generate a unique shareable link (Example: Could be a real API endpoint)

            String shareableLink = getUserDocumentDirectory();

            // Copy link to clipboard
            copyToClipboard(shareableLink);

            // Optionally, open the link in the default browser
            openInBrowser(shareableLink);

            logger.info("Chart shared! Link copied to clipboard: {}", shareableLink);
        } catch (Exception e) {
            throw new RuntimeException("Failed to share chart: " + e.getMessage());
        }
    }

    public void exportAsPDF() {
        // Ensure a valid filename
        String filename = (tradePair != null) ? tradePair.toString('_') : "chart";
        filename += ".pdf"; // Append extension

        // Define the save location
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Chart as PDF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fileChooser.setInitialFileName(filename); // Set initial filename
        File file = fileChooser.showSaveDialog(
                getPrimaryStage()

        );

        if (file == null) {
            logger.warn("File selection was canceled.");
            return;
        }

        // Ensure the parent directory exists
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            new Messages(Alert.AlertType.INFORMATION, "Warning: Could not create directories for " + parentDir.getAbsolutePath());
            return;
        }

        // Take snapshot of chart
        WritableImage snapshot = snapshot(new SnapshotParameters(), null);
        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(snapshot, null);

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            // Convert BufferedImage to PDImageXObject
            PDImageXObject pdImage = LosslessFactory.createFromImage(document, bufferedImage);

            // Get page dimensions
            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();

            // Calculate scale to fit image properly
            float imageWidth = pdImage.getWidth();
            float imageHeight = pdImage.getHeight();
            float scale = Math.min(pageWidth / imageWidth, pageHeight / imageHeight);

            float scaledWidth = imageWidth * scale;
            float scaledHeight = imageHeight * scale;

            // Center image on page
            float x = (pageWidth - scaledWidth) / 2;
            float y = (pageHeight - scaledHeight) / 2;

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(pdImage, x, y, scaledWidth, scaledHeight);
            }

            document.save(file);
            logger.info("PDF saved successfully: {}", file.getAbsolutePath());

            // Open PDF in default viewer
            Desktop.getDesktop().open(file);

        } catch (IOException e) {
            logger.error("Error saving PDF", e);
            showAlert("Error Saving PDF", "Failed to save the chart as PDF.");
        }
    }

    @Contract(" -> new")
    private @NotNull Window getPrimaryStage() {
        return new Stage();
    }

    private @NotNull String getUserDocumentDirectory() {
        // Replace this with your own logic to retrieve the user's document directory
        String chartId=new Date().toString();
        File file = new File("user" + chartId);
        Path path =
                Paths.get(System.getProperty("user.home"), "Documents", "shareable" + chartId + ".pdf");

        // Check if directory already exists
        if (!file.exists()) {
            boolean created = file.mkdir(); // Create directory
            if (created) {
                logger.info("Directory created successfully.");
            } else {
                logger.info("Failed to create directory.");
            }
        } else {
            logger.info("Directory already exists.");
        }

        return path.toString();

    }

    /**
     * Initializes the overlay canvas and labels.
     */
    private void initializeOverlay() {
        overlayCanvas.setMouseTransparent(true);


        // Style for floating labels
        styleLabel(timeLabel);
        styleLabel(priceLabel);
        getChildren().addAll(timeLabel, priceLabel);
    }

    /**
     * Draws the crosshair at (x, y) and updates the floating price & time labels.
     */
    private void drawCrosshair(double x, double y) {
        // Clear the overlay canvas without affecting the main chart
        overlayGC.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());

        // Set crosshair style
        overlayGC.setStroke(Color.WHEAT);
           overlayGC.setLineWidth(1);

        // Draw vertical & horizontal crosshair lines
        overlayGC.strokeLine(x, 0, x, overlayCanvas.getHeight());
        overlayGC.strokeLine(0, y, overlayCanvas.getWidth(), y);

        // Update floating labels
        updateLabels(x, y);
    }

    /**
     * Updates and positions the floating price and time labels near the crosshair.
     */
    private void updateLabels(double x, double y) {
        double timeValue = xAxis.getValueForDisplay(x).doubleValue();
        double priceValue
                // Get the nearest price value from the chart data
                ;
        priceValue = data.values().stream()
                .filter(candleData -> candleData.getOpenTime() <= timeValue && timeValue <= candleData.getCloseTime())
                .mapToDouble(CandleData::getClosePrice)
                .min().orElse(0.0);

        // Create a formatter for time

        // Format time
        String timeText = formatter.format(Instant.ofEpochSecond((long) timeValue));
        timeLabel.setText(timeText);
        positionLabel(timeLabel, x, overlayCanvas.getHeight() - 20, false);

        // Format price
        String priceText = String.format("%.2f", priceValue);
        priceLabel.setText(priceText);
        positionLabel(priceLabel, overlayCanvas.getWidth() - 60, y, true);
    }

    /**
     * Positions a label close to the crosshair.
     */
    private void positionLabel(Label label, double x, double y, boolean alignRight) {
        double padding = 5;
        if (alignRight) {
            label.setLayoutX(x - label.getWidth() - padding);
        } else {
            label.setLayoutX(x + padding);
        }
        label.setLayoutY(y - (label.getHeight() / 2));
    }

    /**
     * Styles the labels for better visibility.
     */
    private void styleLabel(@NotNull Label label) {
        label.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-text-fill: white; -fx-padding: 4px; -fx-border-radius: 4px;");
        label.setVisible(true);
    }

    public boolean isShowGrid() {
        return getChartOptions().isGridVisible();
    }

    public void setShowGrid(boolean b) {
        getChartOptions().setGridVisible(b);

    }

    public void setFullScreen(boolean b) {
        Stage primaryStage = (Stage) getStage();

        if (b) {
            primaryStage.setMaximized(true);
            primaryStage.setFullScreen(true);
            primaryStage.centerOnScreen();
        } else {
            primaryStage.setMaximized(false);
            primaryStage.setFullScreen(false);
        }
    }



    public enum NavigationDirection {
        LEFT,
        RIGHT,
        UP,
        DOWN,
        ZOOM_IN,
        ZOOM_OUT
    }

    private Object getStage() {
        return InvestPro.class;
    }

    public void refreshChart() {

    }



    public void scroll(CandleStickChartToolbar.@NotNull Tool tool) {
        switch (tool) {
            case LEFT:
                scrollNow(NavigationDirection.LEFT);
                break;
            case RIGHT:
                scrollNow(NavigationDirection.RIGHT);
                break;
            case UP:
                scrollNow(NavigationDirection.UP);
                break;
            case DOWN:
                scrollNow(NavigationDirection.DOWN);
                break;
            default:
                break;
        }
    }

    private void scrollNow(@NotNull NavigationDirection navigationDirection) {
        // Define scroll steps for horizontal and vertical movement
        double horizontalStep = 10;
        double verticalStep = 10;

        // Determine the maximum value for xAxis bounds based on your data
        double maxXValue = data.values().stream()
                .mapToDouble(CandleData::getOpenTime)
                .max()
                .orElse(0);

        double newLowerBound, newUpperBound;

        // Use a switch-case to decide how to adjust the xAxis based on the direction
        switch (navigationDirection) {
            case LEFT:
                newLowerBound = xAxis.getLowerBound() - horizontalStep;
                newUpperBound = xAxis.getUpperBound() - horizontalStep;
                if (newLowerBound >= 0) { // ensure we don't scroll past the start
                    xAxis.setLowerBound(newLowerBound);
                    xAxis.setUpperBound(newUpperBound);
                }
                break;
            case RIGHT:
                newLowerBound = xAxis.getLowerBound() + horizontalStep;
                newUpperBound = xAxis.getUpperBound() + horizontalStep;
                if (newUpperBound <= maxXValue) { // ensure we don't scroll past available data
                    xAxis.setLowerBound(newLowerBound);
                    xAxis.setUpperBound(newUpperBound);
                }
                break;
            case UP:
                // For vertical adjustments, adjust as needed. Here we treat UP as moving the view 'up' by subtracting
                newLowerBound = xAxis.getLowerBound() - verticalStep;
                newUpperBound = xAxis.getUpperBound() - verticalStep;
                // You may wish to add bounds-checking here if necessary
                xAxis.setLowerBound(newLowerBound);
                xAxis.setUpperBound(newUpperBound);
                break;
            case DOWN:
                newLowerBound = xAxis.getLowerBound() + verticalStep;
                newUpperBound = xAxis.getUpperBound() + verticalStep;
                // You may wish to add bounds-checking here if necessary
                xAxis.setLowerBound(newLowerBound);
                xAxis.setUpperBound(newUpperBound);
                break;
            default:
                // Optionally, handle other cases or do nothing
                break;
        }
    }

    private int getProgress() {
        long startTime = System.currentTimeMillis();
        long totalTime = System.currentTimeMillis() - startTime;

        long elapsedTime = System.currentTimeMillis() - lastUpdateTime;
        return (int) (elapsedTime / (double) totalTime);
    }

    private void setUserStopLossTakeProfit() {


        // Update stop loss and take profit based on user inputs
        if (Objects.equals(stopLossTextField.getText(), "") || Objects.equals(takeProfitTextField.getText(), "")) {
            takeProfitTextField.setText("100");
            stopLossTextField.setText("100");

        }
        if (!stopLossTextField.getText().matches("\\d+(\\.\\d+)?") || !takeProfitTextField.getText().matches("\\d+(\\.\\d+)?")) {
            logger.error("Stop loss and take profit must be numeric!");
            return;
        }
        if (Double.parseDouble(stopLossTextField.getText()) >= Double.parseDouble(takeProfitTextField.getText())) {
            logger.error("Stop loss must be less than take profit!");
            return;
        }

        stopLoss = Double.parseDouble(stopLossTextField.getText());
        takeProfit = Double.parseDouble(takeProfitTextField.getText());
    }

    private void executeTradeWithRiskManagement(SIGNAL signal, double price, double stopLoss, double takeProfit) {
        Trade trade = executeTrade(signal, price);
        trade.setStopLoss(BigDecimal.valueOf(stopLoss));
        trade.setTakeProfit(BigDecimal.valueOf(takeProfit));

        if (useTrailingStop) {

            if (trade.getSignal().isLong()) {
                useTrailing_Stop(current_price, true);
            } else if (trade.getSignal().isShort()) {
                useTrailing_Stop(current_price, false);
            }

            }


    }

    private void useTrailing_Stop(double currentPrice, boolean isLongTrade) {
        // Calculate the new trailing stop price based on current market price
        double newStopPrice;

        if (isLongTrade) {
            // For long positions: Stop moves up as price increases
            newStopPrice = currentPrice - (currentPrice * trailingStopPercentage);
            if (newStopPrice > trailingStopPrice) {
                trailingStopPrice = newStopPrice;
                if (logger.isInfoEnabled()) {
                    logger.info("Trailing stop updated: {}", trailingStopPrice);
                }
            }
        } else {
            // For short positions: Stop moves down as price decreases
            newStopPrice = currentPrice + (currentPrice * trailingStopPercentage);
            if (newStopPrice < trailingStopPrice || trailingStopPrice == 0.0) {
                trailingStopPrice = newStopPrice;
                logger.info("Trailing stop updated: {}", trailingStopPrice);
            }
        }

        // Check if stop-loss has been triggered
        if ((isLongTrade && currentPrice <= trailingStopPrice) || (!isLongTrade && currentPrice >= trailingStopPrice)) {
            executeTradeExit(isLongTrade);
            logger.info("Trailing stop hit. Exiting trade.");
        }
    }

    private void executeTradeExit(boolean isLongTrade) {
        logger.info("Executing trade exit at price {}", current_price);
        if (isLongTrade) {
            // Exit long trade
            tradeHistory.putSignal(tradePair, SIGNAL.STOP_LOSS);

        }
    }

    private class KeyEventHandler implements EventHandler<KeyEvent> {
        @Override
        public void handle(KeyEvent event) {
            if (paging) {
                event.consume();
                return;
            }

            boolean consume = false;
            if (event.isControlDown() && event.getCode() == KeyCode.PLUS) {
                changeZoom(ZoomDirection.IN);
                consume = true;
            } else if (event.isControlDown() && event.getCode() == KeyCode.MINUS) {
                changeZoom(ZoomDirection.OUT);
                consume = true;
            }

            int deltaX = 0;
            if (event.getCode() == KeyCode.LEFT) {
                deltaX = -1;
                consume = true;
            } else if (event.getCode() == KeyCode.RIGHT) {
                deltaX = 1;
                consume = true;
            }

            if (deltaX != 0) {
                moveAlongX(deltaX, false);
            }

            if (consume) {
                event.consume();
            }
        }
    }

    private class SizeChangeListener extends DelayedSizeChangeListener {
        SizeChangeListener(BooleanProperty gotFirstSize, ObservableValue<Number> containerWidth,
                           ObservableValue<Number> containerHeight) {
            super(750, 300, gotFirstSize, containerWidth, containerHeight);
        }
        // Global cache for historical data
        private final Map<Integer, List<CandleData>> candleCache = new HashMap<>();

        @Override
        public void resize() {
            chartWidth = Math.max(300, Math.floor(containerWidth.getValue().doubleValue() / candleWidth) *
                    candleWidth - 60 + ((double) candleWidth / 2));
            chartHeight = Math.max(300, containerHeight.getValue().doubleValue());

            canvas.setWidth(chartWidth - 100);
            canvas.setHeight(chartHeight - 100);

            int newLowerBoundX = (int) (xAxis.getUpperBound() - (currZoomLevel.getNumVisibleCandles() * secondsPerCandle));

            if (newLowerBoundX < currZoomLevel.getMinXValue() && !paging) {
                // Need to load more data
                paging = true;
                progressIndicator.setVisible(true);
                progressIndLbl.setText("Loading data... Please wait");

                try {
                    int pageSize = 500; // Load 500 candles per request
                    int cachedSize = getCachedDataSize(tradePair); // Check the cache size
                    int pageIndex = cachedSize / pageSize; // Determine the next page index

                    // Fetch historical data from cache or API
                    List<CandleData> newCandleData = getCachedOrFetchHistoricalData(tradePair, pageIndex, pageSize);

                    if (!newCandleData.isEmpty()) {
                        cacheHistoricalData(tradePair, newCandleData); // Store in cache

                        logger.info("Loaded {} candles for {} (Page {})", newCandleData.size(), tradePair, pageIndex);

                        Platform.runLater(() -> {
                            List<CandleData> cachedData = candleCache.values().stream().toList().getFirst();

                            // Update UI with cached data
                            putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(),
                                    cachedData, currZoomLevel.getNumVisibleCandles());
                            putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(),
                                    cachedData.subList(Math.max(0, cachedData.size() - currZoomLevel.getNumVisibleCandles()), cachedData.size()));

                            xAxis.setLowerBound(newLowerBoundX);
                            setYAndExtraAxisBounds();
                            layoutChart();

                            // ✅ Ensure chart is redrawn after data update
                            drawChartContents(true,5);

                            progressIndicator.setVisible(false);
                            paging = false;
                        });
                    } else {
                        logger.warn("No more historical data available.");
                        Platform.runLater(() -> {
                            progressIndicator.setVisible(false);
                            paging = false;
                        });
                    }
                } catch (Exception e) {
                    logger.error("Error fetching historical data: {}", e.getMessage(), e);
                    paging = false;
                    progressIndicator.setVisible(false);
                }
            } else {
                // If no additional data needed, just redraw the chart
                drawChartContents(true,1);
            }
        }





    }
    private List<CandleData> getCachedOrFetchHistoricalData(TradePair tradePair, int pageIndex, int pageSize) {
        // Check if cache exists
        if (candleCache.containsKey(tradePair)) {
            List<CandleData> cachedData = candleCache.get(tradePair);

            // If enough cached data is available, return a subset instead of fetching
            if (cachedData.size() >= (pageIndex + 1) * pageSize) {
                logger.info("Using cached data for {} (Page {})", tradePair, pageIndex);
                return cachedData.subList(pageIndex * pageSize, (pageIndex + 1) * pageSize);
            }
        }

        // Fetch from database or API if not in cache
        return fetchHistoricalData(tradePair, pageIndex, pageSize);
    }


    private int getCachedDataSize(TradePair tradePair) {
        return candleCache.getOrDefault(tradePair, Collections.emptyList()).size();
    }

    private void cacheHistoricalData(TradePair tradePair, List<CandleData> newData) {
        candleCache.putIfAbsent(tradePair, new ArrayList<>()); // Initialize if not exists
        candleCache.get(tradePair).addAll(0, newData); // Prepend new data
    }
    // Global cache for historical data
    private final Map<TradePair, List<CandleData>> candleCache = new HashMap<>();


     @Getter
     @Setter
    public class UpdateInProgressCandleTask extends LiveTradesConsumer implements Runnable {
        private final BlockingQueue<Trade> liveTradesQueue;

        private boolean ready;

        UpdateInProgressCandleTask() {
            liveTradesQueue = new LinkedBlockingQueue<>();
        }

    @Override
        public void run() {






        int currentTill = (int) Instant.now().getEpochSecond();
            List<Trade> liveTrades = new ArrayList<>();
            liveTradesQueue.drainTo(liveTrades);

            // Get rid of trades we already know about
            List<Trade> newTrades = liveTrades.stream().filter(trade -> trade.getTimestamp().getEpochSecond() >
                    inProgressCandle.getCurrentTill()).toList();
            for (Trade tra : newTrades) {
                @NotNull InProgressCandleData inProgressCandles=new InProgressCandleData(Instant.now().toEpochMilli(), tra.getOpenPrice(),tra.getHighPrice(),tra.getLowPrice(),Instant.MIN.getNano(), tra.getClosePrice(),tra.getAmount().doubleValue());
                handleInProgressCandle(inProgressCandles,candleDataPager.getCandleDataList());
            }
            // Partition the trades between the current in-progress candle and the candle after that (which we may
            // have entered after last update).
            Map<Boolean, List<Trade>> candlePartitionedNewTrades = newTrades.stream().collect(
                    Collectors.partitioningBy(trade -> trade.getTimestamp().getEpochSecond() >=
                            inProgressCandle.getOpenTime() + secondsPerCandle));

            // Update the in-progress candle with new trades partitioned in the in-progress candle's duration
            List<Trade> currentCandleTrades = candlePartitionedNewTrades.get(false);

            if (!currentCandleTrades.isEmpty()) {
                inProgressCandle.setHighPriceSoFar(Math.max(currentCandleTrades.stream().mapToDouble(
                                m->m.getPrice().doubleValue()
                        ).max().getAsDouble(),
                        inProgressCandle.getHighPriceSoFar()));
                inProgressCandle.setLowPriceSoFar(Math.max(currentCandleTrades.stream().mapToDouble(
                        m->m.getPrice().doubleValue()
                        ).sum(),
                        inProgressCandle.getLowPriceSoFar()));
                inProgressCandle.setVolumeSoFar(inProgressCandle.getVolumeSoFar() +
                        currentCandleTrades.stream().mapToDouble(
                                m->m.getAmount().doubleValue()
                        ).sum());
                inProgressCandle.setCurrentTill(currentTill);
                data.put((int) inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
            }

            List<Trade> nextCandleTrades = candlePartitionedNewTrades.get(true);
            if (Instant.now().getEpochSecond() >= inProgressCandle.getOpenTime() + secondsPerCandle) {
                // Reset in-progress candle
                inProgressCandle.setOpenTime(inProgressCandle.getOpenTime() + secondsPerCandle);
                inProgressCandle.setOpenPrice(inProgressCandle.getClosePriceSoFar());

                if (!nextCandleTrades.isEmpty()) {
                    inProgressCandle.setIsPlaceholder(false);
                    inProgressCandle.setHighPriceSoFar(nextCandleTrades.stream().mapToDouble(
                            m->m.getPrice().doubleValue()
                        ).max().getAsDouble());

                    inProgressCandle.setLowPriceSoFar(currentCandleTrades.stream().mapToDouble(    m->m.getPrice().doubleValue()).sum());

                    inProgressCandle.setVolumeSoFar(nextCandleTrades.stream().mapToDouble(m->m.getAmount().doubleValue()).sum());
                    inProgressCandle.setCurrentTill((int) nextCandleTrades.getFirst().getTimestamp().getEpochSecond());
                } else {
                    inProgressCandle.setIsPlaceholder(true);
                    inProgressCandle.setHighPriceSoFar(inProgressCandle.getClosePriceSoFar());
                    inProgressCandle.setLowPriceSoFar(inProgressCandle.getClosePriceSoFar());
                    inProgressCandle.setVolumeSoFar(inProgressCandle.getVolumeSoFar());
                    inProgressCandle.setCurrentTill((int) Instant.now().getEpochSecond());
                    inProgressCandle.setClosePriceSoFar(inProgressCandle.getClosePriceSoFar());

                    data.put((int) inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
                }

                data.put((int) inProgressCandle.getOpenTime(), inProgressCandle.snapshot());

                trainAIWithHistoricalData(data.values().stream().toList());
                logger.info("AI Training Complete");


                 current_price = exchange.streamLivePrices(tradePair).getLast().getPrice();

                // Train the AI model
                SIGNAL signal = tradingAI.getSignal(current_price, data.values().stream().toList(), tradeStrategy);

                if (autoTrading) {
                    boolean confirmTrade = checkTrendConfirmation(signal, current_price);

                    setUserStopLossTakeProfit();

                    if (confirmTrade) executeTradeWithRiskManagement(signal, current_price, stopLoss, takeProfit);
                }


            }


        }

        private static final int MAX_RETRIES = 3;
        private static final long INITIAL_DELAY_MS = 500; // Initial retry delay (500ms)

        @Override
        public void accept(List<CandleData> candleData) {
            if (Platform.isFxApplicationThread()) {
                logger.error("Candle data paging must not happen on FX thread!");
                throw new IllegalStateException("Candle data paging must not happen on FX thread!");
            }


            if (candleData == null || candleData.isEmpty()) {
                logger.warn("Candle data is empty, retrying fetch...");
                retryFetch(1); // 🔁 Retry fetch logic
                return;
            }


            if (candleData.getFirst().getOpenTime() >= candleData.get(1).getOpenTime()) {
                logger.error("Paged candle data must be in ascending order by x-value");
                throw new IllegalArgumentException("Paged candle data must be in ascending order by x-value");
            }


            updateChartWithNewData(candleData);

            setAutoScroll(true);
           handleInitialData(candleData);
        }

        /**
         * Retry mechanism to fetch candles again in case of failures.
         */
        private void retryFetch(int retryCount) {
            if (retryCount > MAX_RETRIES) {
                logger.error("Max retries exceeded. Unable to fetch candle data.");
                graphicsContext.fillText(
                        "Unable to fetch candle data. Please check your internet connection or try again later.",
                        (getWidth() - 2 * chartPadding) / 2,
                        (getHeight() - 2 * chartPadding) / 2

                );
                return;
            }

            CompletableFuture.delayedExecutor(INITIAL_DELAY_MS * retryCount, TimeUnit.MILLISECONDS)
                    .execute(() -> {
                        logger.warn("Retrying fetch attempt {}/{}", retryCount, MAX_RETRIES);
                        CompletableFuture<List<CandleData>> futureCandles = new CompletableFuture<>();
                        futureCandles.complete(candleDataPager.getCandleDataList());
                        futureCandles.whenComplete((candles, throwable) -> {
                            if (throwable == null) {
                                accept(candles);
                            } else {
                                logger.error("Retry {} failed due to: ", retryCount, throwable);
                                retryFetch(retryCount + 1);
                            }
                        });
                    });
        }

        /**
         * Handles the first data fetch, initializes the chart, and fetches live data if enabled.
         */
        private void handleInitialData(List<CandleData> candleData) {
            if (liveSyncing) {
              handleLiveSyncing(calculateNumCandlesToSkip());
                fetchInProgressCandleData(candleData);
            } else {
                setInitialState(candleData);
            }
        }

        private void setInitialState(@NotNull List<CandleData> candleData) {
            currZoomLevel.setMinXValue(candleData.getFirst().getOpenTime());
            drawChartContents(false,0);
        }

        /**
         * Updates the chart when new data pages arrive.
         */
        private void updateChartWithNewData(@NotNull List<CandleData> candleData) {
            int slidingWindowSize = currZoomLevel.getNumVisibleCandles();
            Map<Integer, CandleData> extremaData = new TreeMap<>();

            for (CandleData candleData1: candleData){
                if (extremaData.containsKey(candleData1.getOpenTime())) {
                    extremaData.computeIfAbsent(candleData1.getOpenTime(), _ -> candleData.getFirst()).updateExtrema(candleData1);
                } else {
                    extremaData.put(candleData1.getOpenTime(), candleData1);
                }
            }

            List<CandleData> newDataPlusOffset = new ArrayList<>(candleData);
            newDataPlusOffset.addAll(extremaData.values());

            putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), newDataPlusOffset, slidingWindowSize);
            getData().values().addAll(candleData);
            currZoomLevel.setMinXValue(candleData.getFirst().getOpenTime());
            computeExtremaIfNeeded(currZoomLevel.getZoomLevelId());
            drawChartContents(true,1);
        }

        /**
         * Fetches in-progress candle data and ensures up-to-date live syncing.
         */
        private void fetchInProgressCandleData(@NotNull List<CandleData> candleData) {
            long secondsIntoCurrentCandle = Instant.now().getEpochSecond() - (candleData.getLast().getOpenTime() + secondsPerCandle);
            inProgressCandle.setOpenTime(candleData.getLast().getOpenTime() + secondsPerCandle);

            CompletableFuture<Optional<?>> inProgressCandleDataOptionalFuture = exchange.fetchCandleDataForInProgressCandle(
                    tradePair,
                    Instant.ofEpochSecond(candleData.getLast().getOpenTime() + secondsPerCandle),
                    secondsIntoCurrentCandle,
                    secondsPerCandle
            );

            inProgressCandleDataOptionalFuture.whenComplete((inProgressCandleDataOptional, throwable) -> {
                if (throwable == null && inProgressCandleDataOptional.isPresent()) {
                    handleInProgressCandle((InProgressCandleData) inProgressCandleDataOptional.get(), candleData);
                } else {
                    throw new RuntimeException("Error fetching in-progress candle data: ", throwable);
                }
            });
        }

        /**
         * Processes in-progress candle data.
         */
        private void handleInProgressCandle(@NotNull InProgressCandleData inProgressCandleData, List<CandleData> candleData) {
            Consumer<List<Trade>> candletradesFuture =
                    (trades) -> {
                        inProgressCandle.setHighPriceSoFar(trades.stream().mapToDouble(Trade::getHighPrice).sum());
                        inProgressCandle.setLowPriceSoFar(
                                trades.stream().mapToDouble(
                                        Trade::getLowPrice
                                ).max().orElse(0)
                        );
                        inProgressCandleData.setLowPriceSoFar(
                                inProgressCandleData.getLowPriceSoFar()
                        );
                        inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolumeSoFar() + trades.stream().mapToDouble(
                                m->m.getAmount().doubleValue()
                        ).sum());
                        inProgressCandle.setClosePriceSoFar(
                                inProgressCandleData.getClosePriceSoFar()
                        );
                    };
            CompletableFuture<List<Trade>> tradesFuture = exchange.fetchRecentTradesUntil(exchange,
                    tradePair,
                    Instant.ofEpochSecond(inProgressCandleData.getCurrentTill()), secondsPerCandle,
                    candletradesFuture
            );

            tradesFuture.whenComplete((trades, exception) -> {
                if (exception == null) {
                    updateInProgressCandle(inProgressCandleData, trades);
                    Platform.runLater(() -> setInitialState(candleData));
                } else {
                    logger.error("Error fetching recent trades: ", exception);
                    retryFetch(1);
                }
            });
        }

        /**
         * Updates the in-progress candle with fetched data.
         */
        private void updateInProgressCandle(@NotNull InProgressCandleData inProgressCandleData, @NotNull List<Trade> trades) {
            inProgressCandle.setOpenPrice(inProgressCandleData.getOpenPrice());
            inProgressCandle.setCurrentTill((int) Instant.now().getEpochSecond());

            if (trades.isEmpty()) {
                inProgressCandle.setHighPriceSoFar(inProgressCandleData.getHighPriceSoFar());
                inProgressCandle.setLowPriceSoFar(inProgressCandleData.getLowPriceSoFar());
                inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolumeSoFar());
                inProgressCandle.setClosePriceSoFar(inProgressCandleData.getLastPrice());
            } else {
                inProgressCandle.setHighPriceSoFar(trades.stream().mapToDouble(Trade::getHighPrice).sum());

                inProgressCandle.setLowPriceSoFar(Math.min(trades.stream().mapToDouble(Trade::getLowPrice).sum(),
                        inProgressCandleData.getLowPriceSoFar()));
                inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolumeSoFar() +
                        trades.stream().mapToDouble(
                                m->m.getAmount().doubleValue()
                        ).sum());
            }
            prices1.addAll(trades);
        }


    }

    }
