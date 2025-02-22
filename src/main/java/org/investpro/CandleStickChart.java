package org.investpro;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.geometry.VPos;
import javafx.print.PrinterJob;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.Axis;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.investpro.ai.TradingAI;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.investpro.CandleStickChartUtils.*;
import static org.investpro.ChartColors.DarkTheme.*;
import static org.investpro.ChartColors.LightTheme.BEAR_CANDLE_FILL_COLOR;
import static org.investpro.ChartColors.LightTheme.BULL_CANDLE_FILL_COLOR;
import static org.investpro.Side.BUY;
import static org.investpro.Side.SELL;
import static org.investpro.exchanges.Oanda.granularityToString;

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
    // 🔹 Pagination Variables
    // Adjust based on performance needs
    private final EventHandler<MouseEvent> mouseDraggedHandler;
    private static final Logger logger = LoggerFactory.getLogger(CandleStickChart.class);
    private static final int MAX_CANDLES = 1000; // Number of candles to keep in memory
    private final TradePair tradePair;
    private final boolean liveSyncing;
    private final Map<Integer, ZoomLevel> zoomLevelMap;
    private final KeyEventHandler keyHandler;
    private final StableTicksAxis xAxis = new StableTicksAxis();
    private final EventHandler<ScrollEvent> scrollHandler;
    private final StableTicksAxis yAxis = new StableTicksAxis();
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
    private final StableTicksAxis extraAxis = new StableTicksAxis();
    private final NavigableMap<Integer, CandleData> data = new ConcurrentSkipListMap<>();
    private final AtomicBoolean isZooming = new AtomicBoolean(false);
    Event event = new javafx.event.Event(
            EventType.ROOT
    );
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
    private List<OrderBook> prices1;
    private CandleDataSupplier candleDataSupplier;
    private volatile ZoomLevel currZoomLevel;
    private volatile boolean paging;
    private double initialLowerBound = 0;
    private double initialUpperBound = 1000000;
    private ZoomDirection zoomDirection;
    private double chartWidth = 900;
    private double chartHeight = 700;
    private long inProgressCandleLastDraw = -1;
    private TelegramClient telegramBot;
    // Variables to track drag speed
    Instant now = Instant.now();
    private double velocityX = 0;
    private Instant lastDragTime;

    TradingAI tradingAI;
    Timeline timeline = new Timeline();
    private Consumer<List<CandleData>> candlePageConsumer;
    /**
     * Maps an open time (as a Unix timestamp) to the computed candle data (high price, low price, etc.) for a trading
     * period beginning with that opening time. Thus, the key "1601798498" would be mapped to the candle data for trades
     * from the period of 1601798498 to 1601798498 + secondsPerCandle.
     */

    private List<OrderBook> prices;
    private double pixelsPerMonetaryUnit = candleWidth;
    private List<CandleData> candleData;
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
    String chartId = UUID.randomUUID().toString(); // Generate a unique identifier for the chart

    private int totalPages = 1;

    private List<CandleData> allCandles = new ArrayList<>(); // Stores all candles
    private List<CandleData> paginatedCandles = new ArrayList<>(); // Current page candles
    private CandleDataPager candleDataPager;
    private String chatId;
    private ProgressIndicator progressIndicator;
    private Font canvasNumberFont;
    private Thread autoScrollThread;
    private Tooltip tooltip = new Tooltip();
    private EventType<? extends Event> eventDispat =
            mouseMovedEvent();

    /**
     * Creates a new {@code CandleStickChart}. This constructor is package-private because it should only
     * be instantiated by a {@link CandleStickChartContainer}.
     *
     * @param exchange           the {@code Exchange} object on which the trades represented by candles happened on
     * @param candleDataSupplier the {@code CandleDataSupplier} that will supply contiguous chunks of
     *                           candle data, where successive supplies will be farther back in time
     * @param tradePair          the {@code TradePair} that this chart displays trading data for (the base (first) currency
     *                           will be the unit of the volume axis and the counter (second) currency will be the unit of the y-axis)
     * @param liveSyncing        if {@literal true} the chart will be updated in real-time to reflect ongoing trading
     *                           activity
     * @param secondsPerCandle   the duration in seconds each candle represents
     * @param containerWidth     the width property of the parent node that contains the chart
     * @param containerHeight    the height property of the parent node that contains the chart
     */
    CandleStickChart(Exchange exchange, CandleDataSupplier candleDataSupplier, TradePair tradePair,
                     boolean liveSyncing, int secondsPerCandle, ObservableNumberValue containerWidth,
                     ObservableNumberValue containerHeight, String telegramBotToken) throws Exception, TelegramApiException {
        Objects.requireNonNull(exchange);
        Objects.requireNonNull(candleDataSupplier);
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(containerWidth);
        Objects.requireNonNull(containerHeight);
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalArgumentException("CandleStickChart must be constructed on the JavaFX Application " +
                    "Thread but was called from \"" + Thread.currentThread() + "\".");
        }

        autoTrading = true;
//



        this.chartOptions = new CandleStickChartOptions();
        this.exchange = exchange;
        this.tradePair = tradePair;
        this.secondsPerCandle = secondsPerCandle;
        this.liveSyncing = liveSyncing;
        this.zoomLevelMap = new ConcurrentHashMap<>();
        this.tradeHistory = new TradeHistory();
        this.prices = new ArrayList<>();
        this.prices1 = new ArrayList<>();
        prices1.add(new OrderBook());
        this.candleData = new ArrayList<>();


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
        VBox loadingIndicatorContainer = new VBox(progressIndLbl, progressIndicator);
        progressIndicator.setPrefSize(40, 40);
        if (progressIndicator.isVisible()) progressIndLbl.setText("Loading  " + tradePair.toString('/') + " ...");
        loadingIndicatorContainer.setAlignment(Pos.CENTER);
        loadingIndicatorContainer.setMouseTransparent(true);

        data.put(0, new CandleData(0, 0, 0, 0, 0, 0, 0));
        this.candleDataSupplier = candleDataSupplier;


        CompletableFuture.supplyAsync(() -> candleDataSupplier)
                .thenAcceptAsync(dat -> Platform.runLater(() -> {
                    // Safely update UI here
                    try {

                        prices1 = exchange.fetchOrderBook(tradePair).get();
                        for (CandleData candleData1 : dat.get().get()) {
                            data.put(candleData1.getOpenTime(), candleData1);


                            allCandles.add(candleData1);
                            if (data.size() > 1000) {
                                data.remove(data.firstKey());

                                allCandles.removeFirst();
                            }

                            if (chartOptions.isShowTooltip()) {
                                showTooltip(candleData1);
                            }
                        }


                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                })).exceptionally(ex -> {
                    throw new RuntimeException("Failed to fetch candle data", ex);

                });

        chartOptions = new CandleStickChartOptions();
        this.candleDataPager = new CandleDataPager(this, getCandleDataSupplier());


        getChildren().addAll(xAxis, yAxis, extraAxis);
        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);
        final ChangeListener<Number> sizeListener = new SizeChangeListener(gotFirstSize, containerWidth, containerHeight);
        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);


        if (liveSyncing) {
            inProgressCandle = new InProgressCandle();
            updateInProgressCandleTask = new UpdateInProgressCandleTask();
            updateInProgressCandleExecutor = Executors.newSingleThreadScheduledExecutor(
                    new LogOnExceptionThreadFactory("UPDATE-CURRENT-CANDLE"));

            CompletableFuture.runAsync(() -> {
                boolean websocketInitialized = false;
                try {
                    websocketInitialized = exchange.getWebsocketClient().getInitializationLatch().await(
                            10, SECONDS);
                } catch (InterruptedException ex) {
                    logger.error("Interrupted while waiting for websocket client to be initialized: ", ex);
                }

                if (!websocketInitialized) {
                    logger.error("websocket client: {} was not initialized after 10 seconds", exchange.getWebsocketClient().getUri().getHost());
                } else {
                    if (exchange.getWebsocketClient().supportsStreamingTrades(tradePair)) {
                        exchange.getWebsocketClient().streamLiveTrades(tradePair, updateInProgressCandleTask);
                    }

                    updateInProgressCandleExecutor.scheduleAtFixedRate(updateInProgressCandleTask, 5, 5, SECONDS);
                }
                try {
                    trainAIWithHistoricalData(candleDataPager.getCandleDataSupplier().get().get().stream().toList());
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
                logger.info("AI Training Complete");

                // Train the AI model
                SIGNAL signal = tradingAI.getMovingAverageSignal(prices1, prices1.stream().findFirst().get().getAskEntries().stream().toList());

                double current_price = prices1.stream().toList().getLast().getAskEntries().getLast().getPrice();
                if (autoTrading) executeTrade(signal, current_price);

            });
        } else {
            inProgressCandle = null;
            updateInProgressCandleTask = null;
            updateInProgressCandleExecutor = null;
        }

        candlePageConsumer = new CandlePageConsumer();
        mouseDraggedHandler = new MouseDraggedHandler();
        scrollHandler = new ScrollEventHandler();
        keyHandler = new KeyEventHandler();
        CompletableFuture.runAsync(() ->
                Platform.runLater(() ->
                        initializeTelegramBot(telegramBotToken)));
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
                canvas = new Canvas(chartWidth - 100, chartHeight - 100);

                currZoomLevel = new ZoomLevel(0, candleWidth, secondsPerCandle, canvas.widthProperty(), new InstantAxisFormatter(DateTimeFormatter.ISO_INSTANT), candleWidth);


                StackPane chartStackPane = new StackPane(canvas, loadingIndicatorContainer);
                chartStackPane.setTranslateX(64); // Only necessary when wrapped in StackPane...why?
                getChildren().addFirst(chartStackPane);

                graphicsContext = canvas.getGraphicsContext2D();

                layoutChart();



                canvas.setOnMouseEntered(_ -> canvas.getScene().setCursor(Cursor.HAND));
                canvas.setOnMouseExited(_ -> canvas.getScene().setCursor(Cursor.DEFAULT));

                AtomicBoolean isDragging = new AtomicBoolean(false);
                canvas.setOnMousePressed(event -> {
                    isDragging.set(true);
                    canvas.getScene().setCursor(Cursor.CLOSED_HAND);
                    event.consume();
                });

                canvas.setOnMouseDragged(event -> {
                    if (isDragging.get()) {
                        canvas.getScene().setCursor(Cursor.HAND);
                        mouseDraggedHandler.handle(event);
                    }
                });

                canvas.setOnMouseReleased(event -> {
                    isDragging.set(false);
                    canvas.getScene().setCursor(Cursor.DEFAULT);
                    event.consume();
                });

                canvas.setOnKeyPressed(keyHandler);
                initializeEventHandlers();
                initializeMomentumScrolling(); // Enable momentum scrolling


                chartOptions.horizontalGridLinesVisibleProperty().addListener((_, _, _) ->
                        drawChartContents(true));
                chartOptions.verticalGridLinesVisibleProperty().addListener((_, _, _) ->
                        drawChartContents(true));
                chartOptions.showVolumeProperty().addListener((_, _, _) -> drawChartContents(true));
                chartOptions.alignOpenCloseProperty().addListener((_, _, _) -> drawChartContents(true));


                gotFirstSize.removeListener(this);
            }


        };

        gotFirstSize.addListener(gotFirstSizeChangeListener);


    }

    private EventType<? extends Event> mouseMovedEvent() {
        return MouseEvent.MOUSE_MOVED;
    }



    private void trainAIWithHistoricalData(List<CandleData> candles) {
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
            for (CandleData candle : historicalData) {
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
            tradingAI.trainModel();

            logger.info("AI Model successfully trained with historical market data.");

        } catch (Exception e) {
            logger.error("Error training AI with historical data: ", e);
        }
    }

    private synchronized void initializeTelegramBot(String telegramBotToken) {
        if (this.telegramBot == null) {
            telegramBot = new TelegramClient(telegramBotToken);
            telegramBot.run();
            chartId = telegramBot.getChatId();
            logger.info("Telegram bot started");
        }
    }

    private void showTooltip(@NotNull CandleData candleData1) {
        tooltip.setText(String.format("Open: %.2f, Close: %.2f, High: %.2f, Low: %.2f, Volume: %.2f",
                candleData1.getOpenPrice(), candleData1.getClosePrice(), candleData1.getHighPrice(), candleData1.getLowPrice(), candleData1.getVolume()));
        tooltip.show(canvas,
                eventDispat.hashCode(), eventDispat.hashCode());
    }

    private void initializeMomentumScrolling() {
        AtomicReference<Double> velocityX = new AtomicReference<>((double) 0); // Initial velocity in pixels per second
        AtomicReference<Double> lastMouseX = new AtomicReference<>((double) 0); // X position of the mouse at the start of dragging
        AtomicReference<Instant> lastDragTime = new AtomicReference<>(Instant.now()); // Time of the last mouse drag
        // Track mouse drag start
        canvas.setOnMousePressed(event -> {

            // Calculate initial velocity based on mouse position
            lastMouseX.set(event.getX());
            lastDragTime.set(Instant.now());
            velocityX.set((double) 0);

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
        drawChartContents(false);
    }

    private void sendTelegramAlert(String message) {
        try {
            telegramBot.sendMessage(telegramBot.getChatId(), "📈 Trade Alert: " + message);
            logger.info("Telegram Alert Sent: {}", message);
        } catch (Exception e) {
            logger.error("Failed to send Telegram alert: ", e);
        }
    }

    private void executeTrade(SIGNAL signal, double price) {
        CompletableFuture.runAsync(() -> {
            try {
                double amount = 0.02;
                double stopLoss = price * 0.99;
                double takeProfit = price * 1.02;

                if (signal == SIGNAL.BUY) {
                    exchange.createOrder(tradePair, BUY, ENUM_ORDER_TYPE.STOP_LOSS, price, amount, new Date(), stopLoss, takeProfit);
                    sendTelegramAlert("✅ BUY order executed at $" + price);
                } else if (signal == SIGNAL.SELL) {
                    exchange.createOrder(tradePair, SELL, ENUM_ORDER_TYPE.STOP_LOSS, price, amount, new Date(), stopLoss, takeProfit);
                    sendTelegramAlert("❌ SELL order executed at $" + price);
                }
            } catch (Exception e) {
                logger.error("Trade execution failed: ", e);
                sendTelegramAlert("⚠️ Error executing trade: " + e.getMessage());
            }
        }).exceptionally(ex -> {
            logger.error("Trade execution failed: ", ex);
            sendTelegramAlert("���️ Error executing trade: " + ex.getMessage());
            return null;
        });
    }

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

    private void initializeEventHandlers() {
        if (canvas.getParent() != null) {
            canvas.getParent().addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
                mousePrevX = -1;
                mousePrevY = -1;
                event.consume();
            });

            canvas.getParent().addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
            canvas.getParent().addEventFilter(ScrollEvent.SCROLL, scrollHandler);
            canvas.getParent().addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
        } else {
            canvas.parentProperty().addListener((_, _, newValue) -> {
                if (newValue != null) {
                    newValue.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
                        mousePrevX = -1;
                        mousePrevY = -1;
                        event.consume();
                    });

                    newValue.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
                    newValue.addEventFilter(ScrollEvent.SCROLL, scrollHandler);
                    newValue.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
                }
            });
        }
    }

    private void moveAlongX(int deltaX, boolean skipDraw) {
        if (deltaX != 1 && deltaX != -1) {
            throw new RuntimeException("deltaX must be 1 or -1 but was: " + deltaX);
        }

        Platform.runLater(() -> {
            if (!progressIndicator.isVisible()) {
                executeMoveLogic(deltaX, skipDraw);
            }
        });
        adjustYAxisScaling();

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
                drawChartContents(true);
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

    /**
     * Draws the chart contents on the canvas corresponding to the current x-axis, y-axis, and extra (volume) axis
     * bounds.
     */
    private void drawChartContents(boolean clearCanvas) {

        // Adjusted calculation for `numCandlesToSkip`
        int numCandlesToSkip = calculateNumCandlesToSkip();

        if (liveSyncing && inProgressCandleLastDraw != inProgressCandle.getOpenTime()) {
            handleLiveSyncing(numCandlesToSkip);
        }

        if (clearCanvas) {
            clearChartCanvas();
        }

        double pixelsPerMonetaryUnit = calculatePixelsPerMonetaryUnit();
        NavigableMap<Integer, CandleData> candlesToDraw = getCandlesToDraw(numCandlesToSkip);

        drawGridLines();
        if (data.isEmpty()) {
            logger.warn("No candle data available to draw chart contents");
            return;
        }

        drawCandles(candlesToDraw, pixelsPerMonetaryUnit);
        drawVolumeBars(candlesToDraw, pixelsPerMonetaryUnit);
        drawXAxisLabels(candlesToDraw);
        drawMarketInfos(candlesToDraw);
        drawBidAsk(candlesToDraw);

    }

    private void drawMarketInfos(@NotNull NavigableMap<Integer, CandleData> candlesToDraws) {
        graphicsContext.setFill(Color.YELLOWGREEN);
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
        double bidPrice = latestCandle.getClosePrice() - 0.0001; // Example bid
        double askPrice = latestCandle.getClosePrice() + 0.0001; // Example ask
        double spread = askPrice - bidPrice;
        // 🟢 Percentage Change Calculation
        double percentageChange = ((latestCandle.getClosePrice() - latestCandle.getOpenPrice()) / latestCandle.getOpenPrice()) * 100;


        // 🟢 Display Symbol and Timeframe
        graphicsContext.fillText(
                "Symbol: " + tradePair.toString('/') +
                        "  Time: " + new SimpleDateFormat("HH:mm:ss").format(new Date()) +
                        "  TimeFrame: " + granularityToString(secondsPerCandle) +
                        " Spread: " + spread + " %Change: " + percentageChange,
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
            graphicsContext.setFill(Color.web("#000000")); // Black color for labels
            graphicsContext.fillText(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.ofEpochSecond(candleTime)), labelXPosition, labelYPosition);

            // Update label position for the next label
            labelX += labelSpacing;
        }
        if (prices1.size() < 2) {
            return;
        }
        // Draw bid and ask prices
        double bidPrice = prices1.getFirst().getAskEntries().getFirst().getPrice();
        double askPrice = prices1.getFirst().getBidEntries().getFirst().getPrice();

        // Calculate bid and ask prices position and size
        double bidPriceYPosition = bottomY - 20;


        // Draw bid and ask prices
        graphicsContext.setTextAlign(TextAlignment.CENTER);
        graphicsContext.setTextBaseline(VPos.CENTER);
        graphicsContext.setFill(Color.web("#000000")); // Black color for labels
        graphicsContext.fillText("Bid: " + bidPrice, leftX, bidPriceYPosition);
        graphicsContext.fillText("Ask: " + askPrice, rightX, bidPriceYPosition);
    }

    private void drawXAxisLabels(NavigableMap<Integer, CandleData> candlesToDraw) {
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
            graphicsContext.fillText(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.ofEpochSecond(candleTime)), labelXPosition, labelYPosition);

            // Update label position for the next label
            labelX += labelSpacing;
        }
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
            graphicsContext.setFill(Color.web("#4CAF50")); // Green color for volume bars
            graphicsContext.fillRect(rightX - barWidth - 2, barY, barWidth, volumeHeight);
            graphicsContext.fillText(String.format("%.2f", candleData.getVolume()),
                    rightX - barWidth - 10, barY + volumeHeight / 2);
        }
    }

    /**
     * Calculates how many candles to skip based on the current x-axis bounds.
     * Implements a retry mechanism in case of data unavailability.
     */
    private int calculateNumCandlesToSkip() {

        int numCandlesToSkip;


        Map.Entry<Integer, CandleData> lastEntry = getData().descendingMap().lastEntry();


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
     * Retrieves the set of candles that should be drawn based on the current x-axis bounds.
     */
    private NavigableMap<Integer, CandleData> getCandlesToDraw(int numCandlesToSkip) {

        if (currZoomLevel == null) {
            DateTimeFormatter format =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            currZoomLevel = new ZoomLevel(0, candleWidth, secondsPerCandle, canvas.widthProperty(), new InstantAxisFormatter(format), candleWidth); // Default zoom level
        }

        return getData().subMap(((int) xAxis.getUpperBound() - secondsPerCandle) -
                        ((currZoomLevel.getNumVisibleCandles()) * secondsPerCandle), true,
                ((int) xAxis.getUpperBound() - secondsPerCandle) - (numCandlesToSkip * secondsPerCandle), true);
    }

    /**
     * Draws horizontal and vertical grid lines based on chart settings.
     */
    private void drawGridLines() {
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
            for (CandleData candleData : getCandlesToDraw(calculateNumCandlesToSkip()).values()) {
                drawVolumeBar(candleData);
            }
        }
    }

    private void drawVolumeBar(CandleData candleData) {
        double x = calculateXPosition(candleData.getOpenTime());
        double width = calculateCandleWidth();
        double y = calculateYPosition(candleData.getClosePrice());
        double height = calculateCandleHeight(candleData.getVolume());

        graphicsContext.setStroke(Color.TRANSPARENT);
        graphicsContext.setFill(Color.RED);
        graphicsContext.fillRect(x, y, width, height);
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
            List<List<CandleData>> batches = splitIntoBatches(candlesToDraw.values(), 50);

            for (List<CandleData> batch : batches) {
                Platform.runLater(() -> {
                    int index = calculateNumCandlesToSkip();
                    for (CandleData batchCandle : batch) {
                        updateExtremaValues(batchCandle, index);
                        drawSingleCandle(batchCandle, index, pixelsPerMonetaryUnit, lastClose.get());
                        lastClose.set(batchCandle.getClosePrice());
                    }
                });
            }
        }).thenRunAsync(() -> Platform.runLater(() ->
                drawExtremaMarkers(highestCandleValue, lowestCandleValue, candleIndexOfHighest, candleIndexOfLowest)
        )).exceptionally(
                e -> {
                    logger.error("Error occurred while drawing candles: ", e);
                    return null;
                }
        );

    }

    private @NotNull List<List<CandleData>> splitIntoBatches(@NotNull Collection<CandleData> values, int index2) {
        List<List<CandleData>> batches = new ArrayList<>();

        List<CandleData> currentBatch = new ArrayList<>();
        for (CandleData candle : values) {
            currentBatch.add(candle);
            if (currentBatch.size() >= index2) {
                batches.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
            }
        }
        if (!currentBatch.isEmpty()) {
            batches.add(new ArrayList<>(currentBatch));
        }
        return batches;
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

    /**
     * Draws a single candle including wicks, body, and volume bar.
     */
    private void drawSingleCandle(@NotNull CandleData candleDatum, int candleIndex, double pixelsPerMonetaryUnit, double lastClose) {
        if (candleDatum.isPlaceHolder()) {
            drawPlaceholderCandle(candleDatum, candleIndex, pixelsPerMonetaryUnit, lastClose);
        } else {
            drawActualCandle(candleDatum, candleIndex, pixelsPerMonetaryUnit);
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

    /**
     * Draws an arrow to indicate the extrema marker.
     */
    private void drawMarkerArrow(double x, double y, Color color) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setStroke(color);
        gc.setLineWidth(2);

        // Draw a small arrow above the label
        gc.strokeLine(x + 5, y, x + 5, y - 10);
        gc.strokeLine(x + 2, y - 5, x + 8, y - 5);
    }

    private double cartesianToScreenCoords(double yCoordinate) {
        return -yCoordinate + canvas.getHeight();
    }

    void changeZoom(ZoomDirection zoomDirection) {
        if (isZooming.get()) return; // Prevent multiple zooms at once

        isZooming.set(true);
        Platform.runLater(() -> {
            int newCandleWidth = currZoomLevel.getCandleWidth() + (zoomDirection == ZoomDirection.IN ? -1 : 1);
            if (newCandleWidth <= 1) return;

            pruneData();  // Keep data optimized
            drawChartContents(true);

            isZooming.set(false);
        });
    }

    private void pruneData() {
        int maxStoredCandles = 2000;
        while (data.size() > maxStoredCandles) {
            data.remove(data.firstKey()); // Remove oldest entry
        }
    }

    // Handles the case when a new zoom level needs to be created.
    private void handleNewZoomLevel(int nextZoomLevelId, int newCandleWidth, int newLowerBoundX, int currMinXValue) {
        ZoomLevel newZoomLevel = new ZoomLevel(nextZoomLevelId, newCandleWidth, secondsPerCandle,
                canvas.widthProperty(), getXAxisFormatterForRange(xAxis.getUpperBound() - newLowerBoundX),
                currMinXValue);

        int numCandlesToSkip = Math.max((((int) xAxis.getUpperBound()) -
                data.lastEntry().getValue().getOpenTime()) / secondsPerCandle, 0);

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

    // Helper method to compute extrema for zoom levels.
    private void computeExtrema(ZoomLevel zoomLevel, List<CandleData> candleData) {
        putSlidingWindowExtrema(zoomLevel.getExtremaForCandleRangeMap(), candleData, zoomLevel.getNumVisibleCandles());
        putExtremaForRemainingElements(zoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                candleData.size() - zoomLevel.getNumVisibleCandles(), candleData.size()));
    }

    @Override
    protected double computePrefHeight(double width) {
        return chartHeight;
    }

    Consumer<List<CandleData>> getCandlePageConsumer() {
        return candlePageConsumer;
    }

    @Override
    protected double computePrefWidth(double height) {
        return chartWidth;
    }

    public void setAutoScroll(boolean b) {
        autoScroll = b;
        if (autoScroll) {
            // Start a new thread to handle auto scrolling.
            new Thread(() -> {
                while (autoScroll) {
                    try {
                        Thread.sleep(1000);
                        Platform.runLater(() -> {
                            double currentX = (double) xAxis.getValueForDisplay(xAxis.getLowerBound());
                            double newX = currentX - 100;
                            if (newX < xAxis.getLowerBound()) {
                                newX = xAxis.getUpperBound();
                            }
                            xAxis.setLowerBound(newX);
                        });
                    } catch (InterruptedException e) {
                        logger.error("Exception in auto scrolling thread: ", e);
                    }
                }
            }).start();
        } else {
            // Stop the auto scrolling thread.
            autoScrollThread.interrupt();
        }
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

    public void changeNavigation(Object navigationDirection) {
        if (!(navigationDirection instanceof NavigationDirection direction)) {
            throw new IllegalArgumentException("Invalid navigation direction: " + navigationDirection);
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
            case ZOOM_IN:
                zoomIn();
                break;
            case ZOOM_OUT:
                zoomOut();
                break;
            default:
                throw new IllegalArgumentException("Unsupported navigation direction: " + direction);
        }
    }

    // Helper methods for navigation
    private void navigateLeft() {
        System.out.println("Navigating left...");
        // Implement logic to move the chart left
    }

    private void navigateRight() {
        System.out.println("Navigating right...");
        // Implement logic to move the chart right
    }

    private void navigateUp() {
        System.out.println("Navigating up...");
        // Implement logic to move the chart up
    }

    private void navigateDown() {
        System.out.println("Navigating down...");
        // Implement logic to move the chart down
    }

    private void zoomIn() {
        System.out.println("Zooming in...");
        // Implement zoom-in logic
    }

    private void zoomOut() {
        System.out.println("Zooming out...");
        // Implement zoom-out logic
    }


    public Object getYAxis2() {
        return extraAxis;
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


    public void shareLink() {
        try {
            // Generate a unique shareable link (Example: Could be a real API endpoint)

            String shareableLink = getUserDocumentDirectory("shareable");

            // Copy link to clipboard
            copyToClipboard(shareableLink);

            // Optionally, open the link in the default browser
            openInBrowser(shareableLink);

            System.out.println("Chart shared! Link copied to clipboard: " + shareableLink);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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

    public void exportAsPDF() {
        try {
            // Generate a unique exportable link (Example: Could be a real API endpoint)

            String exportableLink = getUserDocumentDirectory("chart");

            // Copy link to clipboard
            copyToClipboard(exportableLink);

            // Optionally, open the link in the default web browser
            openInBrowser(exportableLink);

            System.out.println("Exportable link copied to clipboard: " + exportableLink);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String getUserDocumentDirectory(String userDocument) {
        // Replace this with your own logic to retrieve the user's document directory
        File file = new File("user" + chartId);
        Path path =
                Paths.get(System.getProperty("user.home"), "Documents", userDocument + chartId + ".pdf");
        if (!file.exists()) {
            file.mkdir();
        }
        return path.toString();

    }

    private void drawCrosshair(double x, double y) {
//
        graphicsContext.clearRect(0, 0,
                xAxis.getWidth(), yAxis.getHeight());
        drawChartContents(true);
//        // Draw crosshair lines
//        graphicsContext.setStroke(Color.GRAY);
//        graphicsContext.setLineWidth(1);
//        graphicsContext.strokeLine(x, 0, x, yAxis.getHeight());
//        graphicsContext.strokeLine(0, y, xAxis.getWidth(), y);
//
        graphicsContext.save();
        graphicsContext.setStroke(Color.GRAY);
        graphicsContext.setLineWidth(1);
        graphicsContext.strokeLine(x, 0, x, yAxis.getHeight());
        graphicsContext.strokeLine(0, y, xAxis.getWidth(), y);
        graphicsContext.setStroke(Color.BLACK);
        graphicsContext.restore();


    }


    public enum NavigationDirection {
        LEFT,
        RIGHT,
        UP,
        DOWN,
        ZOOM_IN,
        ZOOM_OUT
    }

    private class SizeChangeListener extends DelayedSizeChangeListener {
        SizeChangeListener(BooleanProperty gotFirstSize, ObservableValue<Number> containerWidth,
                           ObservableValue<Number> containerHeight) {
            super(750, 300, gotFirstSize, containerWidth, containerHeight);
        }

        @Override
        public void resize() {
            chartWidth = Math.max(300, Math.floor(containerWidth.getValue().doubleValue() / candleWidth) *
                    candleWidth - 60 + ((double) candleWidth / 2));
            chartHeight = Math.max(300, containerHeight.getValue().doubleValue());

            canvas.setWidth(chartWidth - 100);
            canvas.setHeight(chartHeight - 100);

            // Because the chart has been resized, the number of visible candles has changed and thus we must
            // recompute the sliding window extrema where the size of the sliding window is the new number of
            // visible candles.
            int newLowerBoundX = (int) (xAxis.getUpperBound() - (currZoomLevel.getNumVisibleCandles() *
                    secondsPerCandle));
            if (newLowerBoundX < currZoomLevel.getMinXValue()) {
                // We need to try and request more data so that we can properly resize the chart.
                paging = true;
                progressIndicator.setVisible(true);

                CompletableFuture.supplyAsync(getCandleDataSupplier()).thenAccept(
                        getCandleDataPager().getCandleDataPreProcessor()).whenComplete((_, _) -> {
                    currZoomLevel.getExtremaForCandleRangeMap().clear();
                    List<CandleData> candleData = new ArrayList<>(getData().values());
                    putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(),
                            candleData, currZoomLevel.getNumVisibleCandles());
                    putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(),
                            candleData.subList(candleData.size() - (int) (double) currZoomLevel.getNumVisibleCandles(), candleData.size()));
                    Platform.runLater(() -> {
                        xAxis.setLowerBound(newLowerBoundX);
                        setYAndExtraAxisBounds();

                        layoutChart();

                        drawChartContents(true);
                        progressIndicator.setVisible(false);
                        paging = false;
                    });
                });
            } else {
                currZoomLevel.getExtremaForCandleRangeMap().clear();
                List<CandleData> candleData = new ArrayList<>(getData().values());
                putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(),
                        candleData, currZoomLevel.getNumVisibleCandles());
                putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(),
                        candleData.subList(candleData.size() - (int) (double) currZoomLevel.getNumVisibleCandles(), candleData.size()));
                xAxis.setLowerBound(newLowerBoundX);
                setYAndExtraAxisBounds();
                layoutChart();
                drawChartContents(true);
            }
        }
    }

    public class UpdateInProgressCandleTask extends LiveTradesConsumer implements Runnable {
        private final BlockingQueue<Trade> liveTradesQueue;
        @Setter
        private boolean ready;

        UpdateInProgressCandleTask() {
            liveTradesQueue = new LinkedBlockingQueue<>();
        }

        @Override
        public void acceptTrades(@NotNull List<Trade> trades) {
            liveTradesQueue.addAll(trades);
        }

        @Override
        public void run() {
            if (inProgressCandle == null) {
                throw new RuntimeException("inProgressCandle was null in live syncing mode.");
            }
            if (!ready) {
                graphicsContext.fillText(
                        "No new trades since " + inProgressCandle.getOpenTime(),
                        200, 200 + 20

                );
                return;
            }

            int currentTill = (int) Instant.now().getEpochSecond();
            List<Trade> liveTrades = new ArrayList<>();
            liveTradesQueue.drainTo(liveTrades);

            // Get rid of trades we already know about
            List<Trade> newTrades = liveTrades.stream().filter(trade -> trade.getTimestamp().getEpochSecond() >
                    inProgressCandle.getCurrentTill()).toList();

            // Partition the trades between the current in-progress candle and the candle after that (which we may
            // have entered after last update).
            Map<Boolean, List<Trade>> candlePartitionedNewTrades = newTrades.stream().collect(
                    Collectors.partitioningBy(trade -> trade.getTimestamp().getEpochSecond() >=
                            inProgressCandle.getOpenTime() + secondsPerCandle));

            // Update the in-progress candle with new trades partitioned in the in-progress candle's duration
            List<Trade> currentCandleTrades = candlePartitionedNewTrades.get(false);

            if (!currentCandleTrades.isEmpty()) {
                inProgressCandle.setHighPriceSoFar(Math.max(currentCandleTrades.stream().mapToDouble(Trade::getPrice).max().getAsDouble(),
                        inProgressCandle.getHighPriceSoFar()));
                inProgressCandle.setLowPriceSoFar(Math.max(currentCandleTrades.stream().mapToDouble(Trade::getPrice).min().getAsDouble(),
                        inProgressCandle.getLowPriceSoFar()));
                inProgressCandle.setVolumeSoFar(inProgressCandle.getVolumeSoFar() +
                        currentCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                inProgressCandle.setCurrentTill(currentTill);
                inProgressCandle.setClosePriceSoFar(currentCandleTrades.getLast()
                        .getPrice());
                data.put((int) inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
            }

            List<Trade> nextCandleTrades = candlePartitionedNewTrades.get(true);
            if (Instant.now().getEpochSecond() >= inProgressCandle.getOpenTime() + secondsPerCandle) {
                // Reset in-progress candle
                inProgressCandle.setOpenTime(inProgressCandle.getOpenTime() + secondsPerCandle);
                inProgressCandle.setOpenPrice(inProgressCandle.getClosePriceSoFar());

                if (!nextCandleTrades.isEmpty()) {
                    inProgressCandle.setIsPlaceholder(false);
                    inProgressCandle.setHighPriceSoFar(nextCandleTrades.stream().mapToDouble(Trade::getPrice).max().getAsDouble());
                    inProgressCandle.setLowPriceSoFar(currentCandleTrades.stream().mapToDouble(Trade::getPrice).min().getAsDouble());
                    inProgressCandle.setVolumeSoFar(nextCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                    inProgressCandle.setClosePriceSoFar(nextCandleTrades.getFirst().getPrice());
                    inProgressCandle.setCurrentTill((int) nextCandleTrades.getFirst().getTimestamp().getEpochSecond());
                } else {
                    inProgressCandle.setIsPlaceholder(true);
                    inProgressCandle.setHighPriceSoFar(inProgressCandle.getClosePriceSoFar());
                    inProgressCandle.setLowPriceSoFar(inProgressCandle.getClosePriceSoFar());
                    inProgressCandle.setVolumeSoFar(0);
                }

                getData().put((int) inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
            }

            drawChartContents(true);
        }

    }

    private class CandlePageConsumer implements Consumer<List<CandleData>> {
        private static final int MAX_RETRIES = 3;
        private static final long INITIAL_DELAY_MS = 500; // Initial retry delay (500ms)

        @Override
        public void accept(List<CandleData> candleData) {
            if (Platform.isFxApplicationThread()) {
                logger.error("Candle data paging must not happen on FX thread!");
                throw new IllegalStateException("Candle data paging must not happen on FX thread!");
            }

            if (candleData.isEmpty()) {
                logger.warn("Candle data is empty, retrying...");
                retryFetch(1);
                return;
            }

            if (candleData.getFirst().getOpenTime() >= candleData.get(1).getOpenTime()) {
                logger.error("Paged candle data must be in ascending order by x-value");
                throw new IllegalArgumentException("Paged candle data must be in ascending order by x-value");
            }
            if (data.lastEntry() == null) {
                handleInitialData(candleData);
                return;
            }
            synchronized (data) {
                if (data.isEmpty()) {
                    handleInitialData(candleData);
                } else {
                    updateChartWithNewData(candleData);
                }
            }
        }

        /**
         * Retry mechanism to fetch candles again in case of failures.
         */
        private void retryFetch(int retryCount) {
            if (retryCount > MAX_RETRIES) {
                logger.error("Max retries exceeded. Unable to fetch candle data.");
                return;
            }

            CompletableFuture.delayedExecutor(INITIAL_DELAY_MS * retryCount, TimeUnit.MILLISECONDS)
                    .execute(() -> {
                        logger.warn("Retrying fetch attempt {}/{}", retryCount, MAX_RETRIES);
                        CompletableFuture<List<CandleData>> futureCandles = new CompletableFuture<>();
                        try {
                            futureCandles.complete(candleDataPager.getCandleDataSupplier().get().get());
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
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
                if (inProgressCandle == null) {
                    inProgressCandle = new InProgressCandle();
                }
                fetchInProgressCandleData(candleData);
            } else {
                setInitialState(candleData);
            }
        }

        private void setInitialState(@NotNull List<CandleData> candleData) {
            data.clear();
            data.putAll(candleData.stream().collect(Collectors.toMap(CandleData::getOpenTime, Function.identity())));
            currZoomLevel.setMinXValue(candleData.getFirst().getOpenTime());
            drawChartContents(false);
        }

        /**
         * Updates the chart when new data pages arrive.
         */
        private void updateChartWithNewData(List<CandleData> candleData) {
            int slidingWindowSize = currZoomLevel.getNumVisibleCandles();
            Map<Integer, CandleData> extremaData = new TreeMap<>(data.subMap(
                    (int) currZoomLevel.getMinXValue(),
                    (int) (currZoomLevel.getMinXValue() + (currZoomLevel.getNumVisibleCandles() * secondsPerCandle))
            ));

            List<CandleData> newDataPlusOffset = new ArrayList<>(candleData);
            newDataPlusOffset.addAll(extremaData.values());

            putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), newDataPlusOffset, slidingWindowSize);
            data.putAll(candleData.stream().collect(Collectors.toMap(CandleData::getOpenTime, Function.identity())));
            currZoomLevel.setMinXValue(candleData.getFirst().getOpenTime());
            drawChartContents(true);
        }

        /**
         * Fetches in-progress candle data and ensures up-to-date live syncing.
         */
        private void fetchInProgressCandleData(List<CandleData> candleData) {
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
                    logger.error("Error fetching in-progress candle data: ", throwable);
                }
            });
        }

        /**
         * Processes in-progress candle data.
         */
        private void handleInProgressCandle(InProgressCandleData inProgressCandleData, List<CandleData> candleData) {
            Consumer<List<Trade>> candletradesFuture =
                    (trades) -> {
                        inProgressCandle.setHighPriceSoFar(Math.max(trades.stream().mapToDouble(Trade::getPrice).max().orElse(0),
                                inProgressCandleData.getHighPriceSoFar()));
                        inProgressCandle.setLowPriceSoFar(Math.min(trades.stream().mapToDouble(Trade::getPrice).min().orElse(0),
                                inProgressCandleData.getLowPriceSoFar()));
                        inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolumeSoFar() + trades.stream().mapToDouble(Trade::getAmount).sum());
                        inProgressCandle.setClosePriceSoFar(trades.getLast().getPrice());
                    };
            CompletableFuture<List<Trade>> tradesFuture = exchange.fetchRecentTradesUntil(
                    tradePair,
                    Instant.ofEpochSecond(inProgressCandleData.getCurrentTill()),
                    candletradesFuture
            );

            tradesFuture.whenComplete((trades, exception) -> {
                if (exception == null) {
                    updateInProgressCandle(inProgressCandleData, trades);
                    Platform.runLater(() -> setInitialState(candleData));
                } else {
                    logger.error("Error fetching recent trades: ", exception);
                }
            });
        }

        /**
         * Updates the in-progress candle with fetched data.
         */
        private void updateInProgressCandle(InProgressCandleData inProgressCandleData, List<Trade> trades) {
            inProgressCandle.setOpenPrice(inProgressCandleData.getOpenPrice());
            inProgressCandle.setCurrentTill((int) Instant.now().getEpochSecond());

            if (trades.isEmpty()) {
                inProgressCandle.setHighPriceSoFar(inProgressCandleData.getHighPriceSoFar());
                inProgressCandle.setLowPriceSoFar(inProgressCandleData.getLowPriceSoFar());
                inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolumeSoFar());
                inProgressCandle.setClosePriceSoFar(inProgressCandleData.getLastPrice());
            } else {
                inProgressCandle.setHighPriceSoFar(Math.max(trades.stream().mapToDouble(Trade::getPrice).max().orElse(0),
                        inProgressCandleData.getHighPriceSoFar()));
                inProgressCandle.setLowPriceSoFar(Math.min(trades.stream().mapToDouble(Trade::getPrice).min().orElse(0),
                        inProgressCandleData.getLowPriceSoFar()));
                inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolumeSoFar() +
                        trades.stream().mapToDouble(Trade::getAmount).sum());
                inProgressCandle.setClosePriceSoFar(trades.getLast().getPrice());
            }
        }


    }

    private class MouseDraggedHandler implements EventHandler<MouseEvent> {
        @Override
        public void handle(MouseEvent event) {
            if (paging) {
                event.consume();
                return;
            }

            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            if (mousePrevX == -1 && mousePrevY == -1) {
                mousePrevX = event.getScreenX();
                mousePrevY = event.getScreenY();
                return;
            }

            double dx = event.getScreenX() - mousePrevX;

            scrollDeltaXSum += dx;

            if (Math.abs(scrollDeltaXSum) >= 10) {
                int deltaX = (int) -Math.signum(scrollDeltaXSum);
                moveAlongX(deltaX, false);
                scrollDeltaXSum = 0;
            }
            mousePrevX = event.getScreenX();
            mousePrevY = event.getScreenY();

            drawCrosshair(mousePrevX, mousePrevY);
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

    private class ScrollEventHandler implements EventHandler<ScrollEvent> {
        @Override
        public void handle(ScrollEvent event) {
            if (paging) {
                event.consume();
                return;
            }

            if (event.getDeltaY() != 0 && event.getTouchCount() == 0 && !event.isInertia()) {
                final double direction = -Math.signum(event.getDeltaY());

                if (direction == 1.0d) {
                    changeZoom(ZoomDirection.OUT);
                } else if (direction == -1.0d) {
                    changeZoom(ZoomDirection.IN);
                }
            }
            event.consume();
        }
    }
}
