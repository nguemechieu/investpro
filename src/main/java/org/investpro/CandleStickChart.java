package org.investpro;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
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

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.investpro.CandleStickChartUtils.*;
import static org.investpro.Side.BUY;
import static org.investpro.Side.SELL;

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


    private final CandleDataPager candleDataPager;
    private static final Logger logger = LoggerFactory.getLogger(CandleStickChart.class);
    private static final int MAX_CANDLES = 1000; // Number of candles to keep in memory
    private final TradePair tradePair;
    private final boolean liveSyncing;
    private final Map<Integer, ZoomLevel> zoomLevelMap;
    private final KeyEventHandler keyHandler;
    // 🔹 Pagination Variables
    private static final int CANDLES_PER_PAGE = 50; // Adjust based on performance needs
    private final EventHandler<MouseEvent> mouseDraggedHandler;
    private final EventHandler<ScrollEvent> scrollHandler;
    private final String chatId;
    /**
     * Draws the chart contents on the canvas ensuring the latest data is always displayed first.
     * It also informs the user when no data is available or when an error occurs.
     */

    private final Exchange exchange;
    Label progressIndLbl = new Label();
    String tradeMode = "Auto";
    DateTimeFormatter formatter;
    TradingType sessionType;
    private CandleStickChartOptions chartOptions;
    private final TradeHistory tradeHistory;

    private final StableTicksAxis xAxis;
    private final StableTicksAxis yAxis;
    private final StableTicksAxis extraAxis;
    private final ProgressIndicator progressIndicator;
    private ScheduledExecutorService updateInProgressCandleExecutor;
    private final Line extraAxisExtension;
    private InProgressCandle inProgressCandle;
    private boolean autoTrading;

    private final Font canvasNumberFont;
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
    List<OrderBook> orderBookList = new ArrayList<>();
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

    // Define a simple trading signal for training labels
    private @NotNull String determineSignal(@NotNull CandleData candle) {
        double momentum = candle.getClosePrice() - candle.getOpenPrice();
        if (momentum > 5) return "BUY";  // Example condition
        if (momentum < -5) return "SELL";
        return "HOLD";
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

    private void initializeEventHandlers() {
        if (canvas.getParent() != null) {
            addEventFilters(canvas.getParent());
        } else {
            canvas.parentProperty().addListener((_, _, newValue) -> {
                if (newValue != null) {
                    addEventFilters(newValue);
                }
            });
        }

    }
    private int totalPages = 1;

    /**
     * Handles mouse clicks for double-click zoom reset and right-click context menu.
     */
    private void handleMouseClick(@NotNull MouseEvent event) {
        if (event.getClickCount() == 2) { // Double-click to reset zoom
            resetZoom();
        }

        if (event.getButton() == MouseButton.SECONDARY) { // Right-click for context menu
            showContextMenu(event);
        }
    }
    private List<CandleData> allCandles = new ArrayList<>(); // Stores all candles
    private List<CandleData> paginatedCandles = new ArrayList<>(); // Current page candles

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
        this.telegramBot = new TelegramClient(telegramBotToken);
        this.chatId = telegramBot.getChatId();
        this.chartOptions = new CandleStickChartOptions();
        this.exchange = exchange;
        this.tradePair = tradePair;
        this.secondsPerCandle = secondsPerCandle;
        this.liveSyncing = liveSyncing;
        this.zoomLevelMap = new ConcurrentHashMap<>();
        this.tradeHistory = new TradeHistory();
        this.prices = new ArrayList<>();
        this.prices1 = new ArrayList<>();
        this.candleData = new ArrayList<>();

        this.candleDataSupplier = candleDataSupplier;
        candleDataPager = new CandleDataPager(this, candleDataSupplier);

        this.updateInProgressCandleExecutor = Executors.newScheduledThreadPool(4);

        this.inProgressCandle = new InProgressCandle();
        this.updateInProgressCandleTask = new UpdateInProgressCandleTask(exchange, secondsPerCandle, tradePair, candleData);



        chartOptions = new CandleStickChartOptions();
        canvasNumberFont = Font.font(FXUtils.getMonospacedFont(), 12);
        progressIndicator = new ProgressIndicator(-1);
        getStyleClass().add("candle-chart");
        xAxis = new StableTicksAxis();

        xAxis.setLabel("Time");
        yAxis = new StableTicksAxis();

        yAxis.setLabel("Price");
        extraAxis = new StableTicksAxis();
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

        // We want to extend the extra axis (volume) visually so that it encloses the chart area.
        extraAxisExtension = new Line();
        Paint lineColor = Color.rgb(15, 195, 195);
        extraAxisExtension.setFill(lineColor);
        extraAxisExtension.setStroke(lineColor);
        extraAxisExtension.setSmooth(false);
        extraAxisExtension.setStrokeWidth(1);

        getChildren().addAll(xAxis, yAxis, extraAxis, extraAxisExtension);
        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);
        final ChangeListener<Number> sizeListener = new SizeChangeListener(gotFirstSize, containerWidth, containerHeight);
        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);

        candlePageConsumer = new CandlePageConsumer();
        mouseDraggedHandler = new MouseDraggedHandler();
        scrollHandler = new ScrollEventHandler();
        keyHandler = new KeyEventHandler();

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
                        drawChartContents());
                chartOptions.verticalGridLinesVisibleProperty().addListener((_, _, _) ->
                        drawChartContents());
                chartOptions.showVolumeProperty().addListener((_, _, _) -> drawChartContents());
                chartOptions.alignOpenCloseProperty().addListener((_, _, _) -> drawChartContents());


                gotFirstSize.removeListener(this);
            }


        };

        gotFirstSize.addListener(gotFirstSizeChangeListener);


    }

    private void run() {



            try {
                if (!exchange.getWebsocketClient().getInitializationLatch().await(
                        10, SECONDS)) {
                    throw new IllegalStateException("WebSocket client not initialized");
                } else {
                    if (exchange.getWebsocketClient().supportsStreamingTrades(tradePair)) {
                        exchange.getWebsocketClient().streamLiveTrades(tradePair, updateInProgressCandleTask);


                    }
                }

                updateInProgressCandleExecutor.scheduleWithFixedDelay(
                        () -> {
                            try {
                                updateInProgressCandleTask.run();
                            } catch (Exception e) {
                                logger.error("Error in main loop: ", e);
                            }
                        },
                        1000, 1000, TimeUnit.MILLISECONDS

                );

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }


        try {
            orderBookList.addAll(exchange.fetchOrderBook(tradePair).get().stream().toList());
        } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }


        try {
            double current_price = exchange.fetchLivesBidAsk(tradePair);
            trainAIWithHistoricalData(candleDataPager.getCandleDataSupplier().get().get().stream().toList());
            logger.info("AI Training Complete");

            // Train the AI model
            SIGNAL signal = tradingAI.getMovingAverageSignal(prices1, prices1.stream().findFirst().get().getAskEntries().stream().toList());

            if (autoTrading) executeTrade(signal, current_price);

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error training AI: ", e);
            }


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


    /**
     * Handles key releases for additional actions.
     */
    private void handleKeyRelease(@NotNull KeyEvent event) {
        switch (event.getCode()) {
            case PLUS, ADD -> zoomIn();
            case MINUS, SUBTRACT -> zoomOut();
            case LEFT -> moveAlongX(-1);
            case RIGHT -> moveAlongX(1);
            case R -> resetZoom();
        }
    }

    /**
     * Resets zoom to the default level.
     */
    private void resetZoom() {
        xAxis.setLowerBound(initialLowerBound);
        xAxis.setUpperBound(initialUpperBound);
        setYAndExtraAxisBounds();
        drawChartContents();
    }

    /**
     * Zooms in the chart.
     */
    private void zoomIn() {
        updateChartWithZoom((int) (candleWidth * 0.8), ZoomDirection.IN);
    }

    /**
     * Zooms out the chart.
     */
    private void zoomOut() {
        updateChartWithZoom((int) (candleWidth * 1.2), ZoomDirection.OUT);
    }

    /**
     * Displays a right-click context menu with options.
     */
    private void showContextMenu(@NotNull MouseEvent event) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem resetZoomItem = new MenuItem("Reset Zoom");
        resetZoomItem.setOnAction(e -> resetZoom());

        MenuItem zoomInItem = new MenuItem("Zoom In");
        zoomInItem.setOnAction(e -> zoomIn());

        MenuItem zoomOutItem = new MenuItem("Zoom Out");
        zoomOutItem.setOnAction(e -> zoomOut());

        contextMenu.getItems().addAll(resetZoomItem, zoomInItem, zoomOutItem);
        contextMenu.setAutoHide(true);
        contextMenu.show(canvas, event.getScreenX(), event.getScreenY());
        progressIndLbl.setVisible(false);


    }



    private void adjustXAxisScaling() {
        double xAxisRange = xAxis.getUpperBound() - xAxis.getLowerBound();

        // Adjust the boundaries by reducing 10% of the range on both sides
        double newUpperBound = xAxis.getUpperBound() - (xAxisRange * 0.1);
        double newLowerBound = xAxis.getLowerBound() + (xAxisRange * 0.1);

        // Ensure the bounds are valid (new lower should be less than new upper)
        if (newLowerBound < newUpperBound) {
            xAxis.setUpperBound(newUpperBound);
            xAxis.setLowerBound(newLowerBound);
        } else {
            logger.warn("Invalid xAxis bounds: newLowerBound={} newUpperBound={}", newLowerBound, newUpperBound);
        }

        logger.info("xAxis adjusted: Lower={} Upper={}", xAxis.getLowerBound(), xAxis.getUpperBound());
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

    private void sendTelegramAlert(String message) {
        try {
            telegramBot.sendMessage(chatId, "📈 Trade Alert: " + message);
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
                    sendTelegramAlert("BUY order executed at $" + price);
                } else if (signal == SIGNAL.SELL) {
                    exchange.createOrder(tradePair, SELL, ENUM_ORDER_TYPE.STOP_LOSS, price, amount, new Date(), stopLoss, takeProfit);
                    sendTelegramAlert("SELL order executed at $" + price);
                }
            } catch (Exception e) {
                logger.error("Trade execution failed: ", e);
                sendTelegramAlert("Error executing trade: " + e.getMessage());
            }
        });
    }

    /**
     * Adds event filters to the parent container of the canvas.
     */
    private void addEventFilters(@NotNull Parent parent) {
        parent.addEventFilter(MouseEvent.MOUSE_RELEASED, _ -> {
            mousePrevX = -1;
            mousePrevY = -1;
        });

        parent.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseDraggedHandler);
        parent.addEventFilter(ScrollEvent.SCROLL, scrollHandler);
        parent.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);

        // 🚀 New Features
        parent.addEventFilter(MouseEvent.MOUSE_CLICKED, this::handleMouseClick);
        parent.addEventFilter(MouseEvent.MOUSE_MOVED, this::handleMouseMove);
        parent.addEventFilter(KeyEvent.KEY_RELEASED, this::handleKeyRelease);
    }

    private void handleMouseMove(@NotNull MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        // Prevent unnecessary redraws
        if (mouseX == lastMouseX && mouseY == lastMouseY) return;

        drawCrosshair(mouseX, mouseY);
        lastMouseX = mouseX;
        lastMouseY = mouseY;

    }

    private void drawCrosshair(double x, double y) {

        graphicsContext.clearRect(0, 0,
                xAxis.getWidth(), yAxis.getHeight());
        graphicsContext.setStroke(Color.BLACK);
        // Optimize tooltip handling;
        drawChartContents();
        // Draw crosshair lines
        graphicsContext.setStroke(Color.GRAY);
        graphicsContext.setLineWidth(1);
        graphicsContext.strokeLine(x, 0, x, yAxis.getHeight());
        graphicsContext.strokeLine(0, y, xAxis.getWidth(), y);

        // Optimize tooltip handling
        Optional<CandleData> nearestCandle = findNearestCandle(x);
//      nearestCandle.ifPresent(candle -> {
//            Tooltip tooltip = new Tooltip();
//            tooltip.setText(String.format("O: %.2f  H: %.2f  L: %.2f  C: %.2f",
//                    candle.getOpenPrice(), candle.getHighPrice(), candle.getLowPrice(), candle.getClosePrice()));
//            tooltip.show(canvas, x + 15, y - 15);
//        });

        lastMouseX = x;
        lastMouseY = y;

    }

    private void drawChartContents() {


        drawCandles();
    }

    private Optional<CandleData> findNearestCandle(double x) {
        double minDistance = Double.MAX_VALUE;
        Optional<CandleData> nearestCandle = Optional.empty();

        for (CandleData candle : candleData) {
            double distance = Math.abs(x - candle.getOpenTime());
            if (distance < minDistance) {
                minDistance = distance;
                nearestCandle = Optional.of(candle);
            }
        }

        return nearestCandle;
    }

    /**
     * Sets the y-axis and extra axis bounds using only the x-axis lower bound.
     */
    private void setYAndExtraAxisBounds() {
        logger.info("xAxis lower bound: {}", (int) xAxis.getLowerBound());
        final double idealBufferSpaceMultiplier = 0.35;
        if (!currZoomLevel.getExtremaForCandleRangeMap().containsKey((int) xAxis.getLowerBound())) {

            // normal chart functioning, and could we handle it more gracefully?
            logger.info("The extrema map did not contain extrema for x-value: {}", (int) xAxis.getLowerBound());
            logger.info("extrema map: {}", new TreeMap<>(currZoomLevel.getExtremaForCandleRangeMap()));
            currZoomLevel.setMinXValue(xAxis.getLowerBound());
        }

        // The y-axis and extra axis extrema are obtained using a key offset by minus one candle duration. This makes
        // the chart work correctly. I don't fully understand the logic behind it, so I am leaving a note for
        // my future self.


        // Calculate y-axis and extra axis bounds based on the y-axis extrema.

        Pair<Extrema, Extrema> value =
                new Pair<>(new Extrema(Double.MIN_VALUE, Double.MAX_VALUE),
                        new Extrema(Double.MIN_VALUE, Double.MAX_VALUE));

        Double yAxisMax = Extrema.getMax(value);
        Double yAxisMin = Extrema.getMin(value);
        final double yAxisDelta = yAxisMax - yAxisMin;
        yAxis.setUpperBound(yAxisMax + (yAxisDelta * idealBufferSpaceMultiplier));
        yAxis.setLowerBound(Math.max(0, yAxisMin - (yAxisDelta * idealBufferSpaceMultiplier)));

        extraAxis.setUpperBound(Extrema.getMax(value));

        List<CandleData> pair = candleData.stream().toList();
        Map<Integer, Pair<Extrema, Extrema>> extrema = new HashMap<>();
        putExtremaForRemainingElements(extrema, pair);

    }
    // ⏩ Move to the next page

    private void drawCandles() {
        CompletableFuture.runAsync(this::run);

        allCandles.clear();
        allCandles.addAll(candleData);


        if (allCandles.isEmpty()) {
            logger.warn("No candle data available.");
            graphicsContext.fillText(
                    "No candle data available.",
                    canvas.getWidth() / 2,
                    canvas.getHeight() / 2

            );

            CompletableFuture.runAsync(() -> {
                try {
                    candleData.addAll(candleDataSupplier.get().get().stream().toList());
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
            return;
        }

        // 🔹 Calculate total pages
        totalPages = (int) Math.ceil((double) allCandles.size() / CANDLES_PER_PAGE);

        // 🔹 Fetch candles for the current page
        int startIndex = Math.max(0, currentPage * CANDLES_PER_PAGE);
        int endIndex = Math.min(startIndex + CANDLES_PER_PAGE, allCandles.size());
        paginatedCandles = allCandles.subList(startIndex, endIndex);

        // 🔹 Initialize variables
        double highestCandleValue = Double.MIN_VALUE;
        double lowestCandleValue = Double.MAX_VALUE;
        double halfCandleWidth = candleWidth * 0.5;
        int candleIndex = 0;

        // 🔹 Ensure valid xAxis range
        double pixelsPerMonetaryUnit = 1d / ((yAxis.getUpperBound() - yAxis.getLowerBound()) / canvas.getHeight());
        if (xAxis.getLowerBound() >= xAxis.getUpperBound()) {
            logger.error("Invalid xAxis bounds: Lower={} Upper={}", xAxis.getLowerBound(), xAxis.getUpperBound());
            return;
        }

        // 🎯 Display OHLCV Information
        displayOHLCVInfo(paginatedCandles.getLast());

        // 🔹 Draw paginated candles
        for (CandleData candle : candleData) {
            if (candleIndex < currZoomLevel.getNumVisibleCandles() + 2) {
                highestCandleValue = Math.max(highestCandleValue, candle.getHighPrice());
                lowestCandleValue = Math.min(lowestCandleValue, candle.getLowPrice());
            }

            drawCandle(candle, candleIndex, halfCandleWidth, pixelsPerMonetaryUnit);
            candleIndex++;
        }

        // 🔹 Draw latest candle separately
//        if (!paginatedCandles.isEmpty()) {
//            CandleData latestCandle = paginatedCandles.getLast();
//            drawCandle(latestCandle, paginatedCandles.size() - 1, (double) candleWidth / 2, pixelsPerMonetaryUnit);
//        }

        // 🔹 Smooth Y-Axis scaling
        smoothYAxisScaling(highestCandleValue, lowestCandleValue);

        // 🔹 Draw bid/ask price lines
        drawBidAskLines();

        // 🔹 Display Pagination Info
        logger.info("Page {}/{} | Displaying candles {} - {}", currentPage + 1, totalPages, startIndex, endIndex);
    }

    /**
     * Draws a single candle on the chart.
     */
    private void drawCandle(@NotNull CandleData candle, int candleIndex, double halfCandleWidth,
                            double pixelsPerMonetaryUnit) {



        boolean isBullish = candle.getClosePrice() >= candle.getOpenPrice();
        Color candleColor = isBullish ? Color.GREEN : Color.RED;


        double x = xAxis.getWidth() - ((candleIndex + 1) * candleWidth);
        double openY = cartesianToScreenCords((candle.getOpenPrice() - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        double closeY = cartesianToScreenCords((candle.getClosePrice() - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        double highY = cartesianToScreenCords((candle.getHighPrice() - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        double lowY = cartesianToScreenCords((candle.getLowPrice() - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);

        // Draw wick (high to low)
        graphicsContext.setStroke(Color.GOLD);
        graphicsContext.setLineWidth(2);
        graphicsContext.strokeLine(x + halfCandleWidth, highY, x + halfCandleWidth, lowY);

        // Draw candle body
        graphicsContext.setFill(candleColor);
        graphicsContext.fillRect(x, Math.min(openY, closeY), candleWidth - 1, Math.abs(openY - closeY));

        // Draw volume bar
        if (chartOptions.isShowVolume()) {
            double volumeHeight = (candle.getVolume() / extraAxis.getUpperBound()) * 100;
            double volumeY = canvas.getHeight() - volumeHeight;
            graphicsContext.setFill(candleColor);
            graphicsContext.fillRect(x, volumeY, candleWidth - 1, volumeHeight);
        }
        // Draw open/close lines
        if (chartOptions.isAlignOpenClose()) {
            graphicsContext.setStroke(Color.ORANGE);
            graphicsContext.setLineWidth(1);
            graphicsContext.strokeLine(x, openY, x, closeY);
        }
        // Draw candle border
        graphicsContext.setStroke(Color.BLACK);
        graphicsContext.setLineWidth(1);
        graphicsContext.strokeRect(x, Math.min(openY, closeY), candleWidth - 1, Math.abs(openY - closeY));
        // Draw candle label
        if (candleIndex >= currZoomLevel.getNumVisibleCandles() && candleIndex < currZoomLevel.getNumVisibleCandles() + 2) {
            double labelX = x + halfCandleWidth - 10;
            double labelY = (openY + closeY) / 2;
            graphicsContext.setFill(Color.PURPLE);
            graphicsContext.setFont(new Font("Arial", 10));
            graphicsContext.fillText(String.format("%.2f", candle.getClosePrice()), labelX, labelY - 5);
        }


        canvas.requestFocus();
        canvas.setCursor(Cursor.DEFAULT);

    }

    private void fetchMoreCandles() {
        CompletableFuture.runAsync(() -> {
            try {
                List<CandleData> newCandles = candleDataPager.getCandleDataSupplier().get().get().stream().toList();

                if (!newCandles.isEmpty()) {
                    Platform.runLater(() -> {
                        allCandles.addAll(newCandles);
                        totalPages = (int) Math.ceil((double) allCandles.size() / CANDLES_PER_PAGE);
                        drawCandles(); // Refresh chart with new data
                    });
                }
            } catch (Exception e) {
                logger.error("Error fetching more candles", e);
            }
        });
    }

    /**
     * Moves forward or backward in the paginated candle data.
     *
     * @param direction +1 to move forward, -1 to move backward.
     */
    private void navigatePages(int direction) {
        int newPage = currentPage + direction;

        if (newPage >= 0 && newPage < totalPages) {
            currentPage = newPage;


            logger.info("Moved to page {}/{}", currentPage + 1, totalPages);
        } else {
            logger.warn("Cannot move further. Already at the {} page.", (newPage < 0) ? "first" : "last");

            graphicsContext.fillText(
                    "Cannot move further. Already at the {} page.",
                    (canvas.getWidth() - graphicsContext.getFont().getSize() * "End of data".length()) / 2,
                    canvas.getHeight() / 2
            );
        }
    }

    /**
     * Draws bid/ask price lines on the chart.
     */
    private void drawBidAskLines() {
        CompletableFuture.runAsync(() -> {
            try {
                prices = exchange.fetchOrderBook(tradePair).get().stream().toList();


                if (prices.isEmpty()) {
                    logger.warn("No bid/ask prices available.");
                    return;
                }

                double bidPrice = prices.getLast().getBidEntries().getLast().getPrice();
                double askPrice = prices.getLast().getAskEntries().getLast().getPrice();

                drawBidAsk(bidPrice, askPrice);

            } catch (Exception e) {
                logger.error("Error fetching bid/ask prices", e);
            }
        });


    }

    private void drawBidAsk(double bidPrice, double askPrice) {
        double bidY = cartesianToScreenCords((bidPrice - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        double askY = cartesianToScreenCords((askPrice - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);


        graphicsContext.setLineWidth(1);
        graphicsContext.setStroke(Color.LIGHTGREEN);
        graphicsContext.strokeLine(0, bidY, xAxis.getWidth(), bidY);
        graphicsContext.setStroke(Color.LIGHTYELLOW);
        graphicsContext.strokeLine(0, askY, xAxis.getWidth(), askY);
    }

    /**
     * Smoothly adjusts the Y-axis scaling based on the highest and lowest visible prices.
     * Ensures price action remains visible and prevents sudden jumps in scaling.
     *
     * @param highestCandleValue The highest price within the visible range.
     * @param lowestCandleValue  The lowest price within the visible range.
     */
    private void smoothYAxisScaling(double highestCandleValue, double lowestCandleValue) {
        if (highestCandleValue == Double.MIN_VALUE || lowestCandleValue == Double.MAX_VALUE) {
            logger.error("No valid price data found for Y-axis scaling.");
            new Messages(Alert.AlertType.ERROR, "No valid price data found for Y-axis scaling.");
            return;
        }

        // Define a small margin to prevent candles from touching the top/bottom
        double marginFactor = 0.05; // 5% margin above and below
        double priceRange = highestCandleValue - lowestCandleValue;
        double upperBound = highestCandleValue + (priceRange * marginFactor);
        double lowerBound = lowestCandleValue - (priceRange * marginFactor);

        // Ensure bounds remain valid and avoid negative or zero scaling
        if (lowerBound < 0) {
            lowerBound = 0;
        }

        if (upperBound == lowerBound) {
            // If there's no price difference, add a small range to avoid flatlining
            upperBound += 1;
            lowerBound -= 1;
        }

        // Animate Y-axis scaling for smooth transitions
        Timeline yAxisScaleAnimation = new Timeline(
                new KeyFrame(Duration.millis(500), // Smooth transition in 500ms
                        new KeyValue(yAxis.lowerBoundProperty(), lowerBound, Interpolator.EASE_BOTH),
                        new KeyValue(yAxis.upperBoundProperty(), upperBound, Interpolator.EASE_BOTH)
                )
        );

        double finalLowerBound = lowerBound;
        double finalUpperBound = upperBound;
        yAxisScaleAnimation.setOnFinished(_ -> logger.info("Y-axis scaling updated: [{} - {}]", finalLowerBound, finalUpperBound));
        yAxisScaleAnimation.play();
    }

    private void drawGridLines() {
        // 🚀 Ensure valid ranges to avoid division errors
        double yAxisRange = yAxis.getUpperBound() - yAxis.getLowerBound();
        double extraAxisRange = extraAxis.getUpperBound() - extraAxis.getLowerBound();

        if (yAxisRange <= 0 || extraAxisRange <= 0) {
            logger.warn("Invalid Y-Axis or Extra Axis Range: Skipping grid drawing.");
            return;
        }

        double yAxisPixelsPerPriceUnit = canvas.getHeight() / yAxisRange;
        double extraAxisPixelsPerPriceUnit = canvas.getHeight() / extraAxisRange;

        // 🚀 Ensure valid candle width and prevent division by zero
        int numVisibleCandles = Math.max(currZoomLevel.getNumVisibleCandles(), 1);
        double candleWidth = xAxis.getWidth() / numVisibleCandles;

        double xStart = xAxis.getWidth() - (numVisibleCandles + 1) * candleWidth;
        double xEnd = xAxis.getWidth();
        double yAxisLowerBound = yAxis.getLowerBound();
        double yAxisUpperBound = yAxis.getUpperBound();
        double extraAxisLowerBound = extraAxis.getLowerBound();
        double yAxisLength = yAxisUpperBound - yAxisLowerBound;

        GraphicsContext gc = canvas.getGraphicsContext2D();


        gc.setStroke(Color.LIGHTGRAY);
        gc.setLineWidth(0.5);

        // 🔹 Draw vertical grid lines (Time Axis)
        for (double x = xStart; x <= xEnd; x += candleWidth) {
            gc.strokeLine(x, 0, x, canvas.getHeight());
        }

        // 🔹 Draw horizontal grid lines (Price Axis)
        int numGridLines = 10;  // Adjust for better spacing
        double ySpacing = yAxisLength / numGridLines;

        for (double i = 0; i <= numGridLines; i++) {
            double yPrice = yAxisLowerBound + (i * ySpacing);
            double yScreen = canvas.getHeight() - ((yPrice - yAxisLowerBound) * yAxisPixelsPerPriceUnit);

            // Ensure the grid line is within visible bounds
            if (yScreen >= 0 && yScreen <= canvas.getHeight()) {
                gc.strokeLine(0, yScreen, xAxis.getWidth(), yScreen);
            }
        }

        // 🔹 Draw extra axis lines (for volume or other indicators)
        int extraAxisLines = 5;
        double extraSpacing = extraAxisRange / extraAxisLines;

        for (double i = 0; i <= extraAxisLines; i++) {
            double extraPrice = extraAxisLowerBound + (i * extraSpacing);
            double extraY = canvas.getHeight() - ((extraPrice - extraAxisLowerBound) * extraAxisPixelsPerPriceUnit);

            // Ensure it's within bounds
            if (extraY >= 0 && extraY <= canvas.getHeight()) {
                gc.setStroke(Color.DARKGRAY);
                gc.strokeLine(0, extraY, xAxis.getWidth(), extraY);
            }
        }

        logger.info("✅ Grid lines drawn successfully.");
    }

    private void updateXAxisBoundsWithAnimation() {
        if (candleData.isEmpty()) return;

        double latestTimestamp = candleData.stream().toList().getLast().getOpenTime();

        double targetUpperBound = latestTimestamp + secondsPerCandle;
        double targetLowerBound = targetUpperBound - (currZoomLevel.getNumVisibleCandles() * secondsPerCandle);

        if (Math.abs(xAxis.getUpperBound() - targetUpperBound) > 0.01) {
            animateXAxisTransition(targetLowerBound, targetUpperBound);
            updateYAxisBoundsWithAnimation(targetLowerBound, targetUpperBound);
        }
    }

    /**
     * Smoothly animates xAxis transition when new data arrives.
     */
    private void animateXAxisTransition(double newLowerBound, double newUpperBound) {
        Timeline timeline = new Timeline();

        // Animate lower bound
        KeyValue keyValueLower = new KeyValue(xAxis.lowerBoundProperty(), newLowerBound, Interpolator.EASE_BOTH);
        KeyFrame keyFrameLower = new KeyFrame(Duration.millis(500), keyValueLower);

        // Animate upper bound
        KeyValue keyValueUpper = new KeyValue(xAxis.upperBoundProperty(), newUpperBound, Interpolator.EASE_BOTH);
        KeyFrame keyFrameUpper = new KeyFrame(Duration.millis(500), keyValueUpper);

        timeline.getKeyFrames().addAll(keyFrameLower, keyFrameUpper);
        timeline.play();
    }

    private void updateYAxisBoundsWithAnimation(double newLowerBound, double newUpperBound) {
        yAxis.setLowerBound(newLowerBound);
        yAxis.setUpperBound(newUpperBound);
        smoothYAxisScaling(candleData.stream().mapToDouble(CandleData::getClosePrice).max().orElse(Double.MIN_VALUE),
                candleData.stream().mapToDouble(CandleData::getClosePrice).min().orElse(Double.MAX_VALUE));
    }


    /**
     * Formats the price to 4 decimal places.
     */
    @Contract(pure = true)
    private @NotNull String formatPrice(double price) {
        return String.format("%.4f", price);
    }

    /**
     * Formats volume in 'K' or 'M' for large numbers.
     */
    private @NotNull String formatVolume(double volume) {
        if (volume >= 1_000_000) return String.format("%.2fM", volume / 1_000_000);
        if (volume >= 1_000) return String.format("%.2fK", volume / 1_000);
        return String.format("%.2f", volume);
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

    /**
     * Displays OHLCV data in a format similar to TradingView.
     * Includes Symbol, Timeframe, OHLC values, Percentage Change, Spread, Bot Info, and Trading Signal.
     */
    private void displayOHLCVInfo(@NotNull CandleData latestCandle) {
        graphicsContext.setFill(Color.YELLOWGREEN);
        graphicsContext.setStroke(Color.BLACK);
        graphicsContext.setFont(new Font("Arial", 16));

        double baseX = 20;
        double baseY = 20;
        double spacingX = 150; // Horizontal spacing for columns
        double spacingY = 25;  // Vertical spacing for rows
        double currentX = baseX;
        double currentY = baseY;
        // 🟢 Spread Calculation (Bid-Ask)
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


        graphicsContext.fillText("Mode: " + tradeMode, currentX, currentY);


    }

    private void initializeMomentumScrolling() {
        // Track mouse drag start
        canvas.setOnMousePressed(event -> {

            // Calculate initial velocity based on mouse position
            lastMouseX = event.getX();
            lastDragTime = Instant.now();
            velocityX = 0;

        });


        // Track mouse movement and calculate velocity
        canvas.setOnMouseDragged(event -> {
            double currentX = event.getX();
            Instant currentTime = Instant.now();

            // Calculate time difference in seconds
            double timeDiff = Duration.millis(lastDragTime.toEpochMilli() - currentTime.toEpochMilli()).toMillis() / 1000.0;
            if (timeDiff > 0) {
                velocityX = (currentX - lastMouseX) / timeDiff; // Speed = Distance / Time
            }

            lastMouseX = currentX;
            lastDragTime = currentTime;

            // Move the chart as the user drags
            moveXAxisByPixels(currentX - lastMouseX);

        });

        // Apply momentum scrolling on release
        canvas.setOnMouseReleased(_ -> applyMomentumScrolling(velocityX));
    }

    /**
     * Moves the xAxis based on user dragging.
     */
    private void moveXAxisByPixels(double deltaX) {
        double shiftAmount = deltaX * (xAxis.getUpperBound() - xAxis.getLowerBound()) / canvas.getWidth();
        xAxis.setLowerBound(xAxis.getLowerBound() - shiftAmount);
        xAxis.setUpperBound(xAxis.getUpperBound() - shiftAmount);
        drawChartContents();
    }

    /**
     * Applies a smooth momentum effect when the user releases the drag.
     */
    private void applyMomentumScrolling(double initialVelocity) {
        if (Math.abs(initialVelocity) < 1) return;

        double deceleration = 0.95;
        DoubleProperty velocityProperty = new SimpleDoubleProperty(initialVelocity);

        KeyFrame keyFrame = new KeyFrame(Duration.millis(16), event -> {
            double newVelocity = velocityProperty.get() * deceleration;
            if (Math.abs(newVelocity) < 1) {
                timeline.stop();
            } else {
                velocityProperty.set(newVelocity);
                moveXAxisByPixels(newVelocity * 0.016);
            }
            event.consume();
        });

        timeline.getKeyFrames().setAll(keyFrame);
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }




    private double cartesianToScreenCords(double yCoordinate) {
        return -yCoordinate + canvas.getHeight();
    }

    private void adjustYAxisScaling() {
        double yAxisRange = yAxis.getUpperBound() - yAxis.getLowerBound();
        double newUpperBound = yAxis.getUpperBound() - (yAxisRange * 0.1);
        double newLowerBound = yAxis.getLowerBound() + (yAxisRange * 0.1);
        yAxis.setUpperBound(newUpperBound);
        yAxis.setLowerBound(newLowerBound);
    }

    private void updateChartWithZoom(int newCandleWidth, ZoomDirection zoomDirection) {
        this.zoomDirection = zoomDirection;
        int newLowerBoundX = (int) (xAxis.getUpperBound() - ((int) (canvas.getWidth() / newCandleWidth) * secondsPerCandle));
        xAxis.setLowerBound(newLowerBoundX);
        candleWidth = newCandleWidth;
        setYAndExtraAxisBounds();
        setAxisBoundsForMove(newCandleWidth);
        updateXAxisBoundsWithAnimation();


    }

    Consumer<List<CandleData>> getCandlePageConsumer() {
        return candlePageConsumer;
    }
    CandleStickChartOptions getChartOptions() {
        return chartOptions;
    }

    @Override
    protected double computePrefWidth(double height) {
        return chartWidth;
    }

    @Override
    protected double computeMinWidth(double height) {
        return 200;
    }

    @Override
    protected double computeMinHeight(double width) {
        return 200;
    }

    @Override
    protected double computePrefHeight(double width) {
        return chartHeight;
    }

    private void setInitialState(List<CandleData> candleData) {

        if (candleData == null || candleData.isEmpty()) return;
        adjustXAxisScaling();
        adjustYAxisScaling();
        if (liveSyncing) {

            // Calculate initial zoom level based on available candle width
            double availableWidth = candleWidth;
            double initialZoomWidth = Math.min(availableWidth, 100);
            int initialZoomLevel = (int) Math.floor(availableWidth / initialZoomWidth);
            int secondsPerCandle = (int) Math.floor(initialZoomWidth / candleWidth);

            currZoomLevel = new ZoomLevel(initialZoomLevel, (int) initialZoomWidth, secondsPerCandle, canvas.widthProperty(),
                    getXAxisFormatterForRange(xAxis.getUpperBound() - xAxis.getLowerBound()),
                    candleData.getFirst().getOpenTime());
            zoomLevelMap.put(initialZoomLevel, currZoomLevel);
            xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());





        }

        xAxis.setUpperBound(candleData.getLast().getOpenTime() + secondsPerCandle);
        xAxis.setLowerBound((candleData.getLast().getOpenTime() + secondsPerCandle) -
                (int) (Math.floor(canvas.getWidth() / candleWidth) * secondsPerCandle));

        setYAndExtraAxisBounds();
        candleData.addAll(candleData.stream().collect(Collectors.toMap(CandleData::getOpenTime, Function.identity())).values());

        progressIndicator.setVisible(false);
        updateInProgressCandleTask.setReady(true);

        putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(),
                candleData,
                currZoomLevel.getNumVisibleCandles());
        putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                (candleData.size() - currZoomLevel.getNumVisibleCandles() - (liveSyncing ? 1 : 0)),
                candleData.size()));
    }



    private @NotNull String granularityToString(int actualGranularity) {

        String x;
        String str;
        if (actualGranularity < 3600) {
            x = String.valueOf(actualGranularity / 60);
            str = "M";
        } else if (actualGranularity < 86400) {
            x = String.valueOf((actualGranularity / 3600));
            str = "H";
        } else if (actualGranularity < 604800) {
            x = String.valueOf(actualGranularity / 86400);
            str = "D";
        } else if (actualGranularity < 2592000) {
            x = String.valueOf((actualGranularity / 604800));
            str = "W";
        } else {
            x = String.valueOf((actualGranularity * 7 / 2592000 / 7));
            str = "M";
        }
        return str + x;

    }


    @Getter
    public enum TradingType {

        SCALPING("Seconds to Minutes", "Fast executions, small profits", "High"),
        DAY_TRADING("Intraday", "Momentum, Breakouts", "High"),
        SWING_TRADING("Days to Weeks", "Trend Following", "Medium"),
        POSITION_TRADING("Weeks to Years", "Buy & Hold, Long-Term Trends", "Low"),
        TREND_TRADING("Varies", "Follows Market Trends", "Medium"),
        BREAKOUT_TRADING("Varies", "Entry on Support/Resistance Breaks", "Medium"),
        ARBITRAGE_TRADING("Milliseconds to Seconds", "Price Differences Across Markets", "Low"),
        ALGORITHMIC_TRADING("Automated", "AI, Quantitative Strategies", "Medium"),
        FOREX_TRADING("Varies", "Currencies (EUR/USD, GBP/JPY)", "Medium"),
        CRYPTO_TRADING("24/7", "Bitcoin, Altcoins", "High"),
        STOCK_TRADING("Varies", "Equities", "Medium"),
        COMMODITIES_TRADING("Varies", "Gold, Oil, Silver", "Medium"),
        HIGH_FREQUENCY_TRADING("Milliseconds", "Algo Trading, AI", "High");

        private final String timeframe;
        private final String strategyFocus;
        private final String riskLevel;

        TradingType(String timeframe, String strategyFocus, String riskLevel) {
            this.timeframe = timeframe;
            this.strategyFocus = strategyFocus;
            this.riskLevel = riskLevel;
        }

        @Override
        public @NotNull String toString() {
            return String.format("%s: Timeframe = %s, Strategy Focus = %s, Risk Level = %s",
                    name(), timeframe, strategyFocus, riskLevel);
        }
    }

    private void layoutChart() {
        // Get canvas dimensions
        double canvasWidth = canvas.getWidth();
        double canvasHeight = canvas.getHeight();

        // Calculate chart width and height based on candle width
        double chartWidth = Math.max(300, Math.floor(canvasWidth / candleWidth) * candleWidth - 60 +
                (((double) candleWidth) / 2));
        double chartHeight = Math.max(300, canvasHeight);
        canvas.setWidth(chartWidth - 100);
        canvas.setHeight(chartHeight - 100);

        // Set the main chart region size and position
        setWidth(chartWidth);
        setHeight(chartHeight);
        setLayoutX((canvasWidth - chartWidth) / 2);
        setLayoutY((canvasHeight - chartHeight) / 2);

        // 📌 Position the Y-Axis on the right
        yAxis.setLayoutX(chartWidth - yAxis.getWidth());
        yAxis.setLayoutY(0);
        yAxis.setPrefHeight(chartHeight - 50); // Leave space for x-axis


        // 📌 Position the Extra Axis on the Left
        extraAxis.setLayoutX(0);
        extraAxis.setLayoutY(0);
        extraAxis.setPrefHeight(chartHeight - 50);

        // 📌 Position the X-Axis at the Bottom
        xAxis.setLayoutX(0);
        xAxis.setLayoutY(canvas.getHeight());
        xAxis.setPrefWidth(canvas.getWidth());
        xAxis.setTranslateX(-yAxis.getWidth() + 250);
        xAxis.setTranslateY(
                extraAxis.getHeight() - 50
        );
        yAxis.setTranslateY(xAxis.getTranslateY());
        yAxis.setTranslateX(xAxis.getWidth() + 50);
        yAxis.setPrefSize(canvas.getWidth(), canvas.getHeight());
        extraAxis.setTranslateY(chartHeight - canvas.getHeight());
        extraAxis.setTranslateY(xAxis.getTranslateY());
        extraAxis.setTranslateX(-xAxis.getWidth() - canvas.getWidth() + 203);
        extraAxis.setPrefSize(canvas.getWidth(), canvas.getHeight());


        canvas.setTranslateX(-yAxis.getLayoutX() - 100 + chartWidth);
        canvas.setTranslateY(xAxis.getTranslateY());

        logger.info("CandleStickChart.layoutChart updated successfully");
    }

    private void moveAlongY(int deltaY) {
        double lowPrice = candleData.stream().toList().getLast().getLowPrice();
        double highPrice = candleData.stream().toList().getLast().getHighPrice();
        double newMinYValue = currZoomLevel.getMinYValue() + deltaY * (highPrice - lowPrice) / 100;
        double newMaxYValue = currZoomLevel.getMaxYValue() + deltaY * (highPrice - lowPrice) / 100;
        if (newMinYValue < lowPrice) {
            newMinYValue = lowPrice;
        }
        if (newMaxYValue > highPrice) {
            newMaxYValue = highPrice;
        }
        currZoomLevel.setMinYValue(newMinYValue);
        currZoomLevel.setMaxYValue(newMaxYValue);
        drawChartContents();


    }

    private void moveAlongX(int deltaX) {
        double newMinXValue = currZoomLevel.getMinXValue() + deltaX * secondsPerCandle;
        double newMaxXValue = currZoomLevel.getMaxXValue() + deltaX * secondsPerCandle;
        if (newMinXValue < 0) {
            newMinXValue = 0;
        }
        if (newMaxXValue > tradeHistory.getEndTime()) {
            newMaxXValue = tradeHistory.getEndTime();
        }
        currZoomLevel.setMinXValue(newMinXValue);
        currZoomLevel.setMaxXValue(newMaxXValue);
    }

    private class SizeChangeListener extends DelayedSizeChangeListener {
        SizeChangeListener(BooleanProperty gotFirstSize, ObservableValue<Number> containerWidth,
                           ObservableValue<Number> containerHeight) {
            super(750, 300, gotFirstSize, containerWidth, containerHeight);
        }

        @Override
        public void resize() {
            chartWidth = Math.max(300, Math.floor(containerWidth.getValue().doubleValue() / candleWidth) *
                    candleWidth - 60 + (((double) candleWidth) / 2));
            chartHeight = Math.max(300, containerHeight.getValue().doubleValue());


            // Because the chart has been resized, the number of visible candles has changed, and thus we must
            // recompute the sliding window extrema where the size of the sliding window is the new number of
            // visible candles.
            int newLowerBoundX = (int) (xAxis.getUpperBound() - (currZoomLevel.getNumVisibleCandles() *
                    secondsPerCandle));
            if (newLowerBoundX < currZoomLevel.getMinXValue()) {
                // We need to try and request more data so that we can properly resize the chart.
                paging = true;
                progressIndicator.setVisible(true);
                CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier()).thenAccept(
                        candleDataPager.getCandleDataPreProcessor()).whenComplete((_, _) -> {
                    currZoomLevel.getExtremaForCandleRangeMap().clear();

                    try {
                        candleData = candleDataPager.getCandleDataSupplier().get().get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                    putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(),
                            candleData, currZoomLevel.getNumVisibleCandles());
                    putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(),
                            candleData.subList(candleData.size() - currZoomLevel.getNumVisibleCandles(), candleData.size()));
                    Platform.runLater(() -> {
                        xAxis.setLowerBound(newLowerBoundX);
                        setYAndExtraAxisBounds();

                        updateYAxisBoundsWithAnimation(
                                xAxis.getLowerBound(), yAxis.getLowerBound()
                        );
                        layoutChart();
                        drawChartContents();
                        progressIndicator.setVisible(false);
                        paging = false;

                    });
                });
            } else {
                currZoomLevel.getExtremaForCandleRangeMap().clear();

                putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(),
                        candleData, currZoomLevel.getNumVisibleCandles());
                putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(),
                        candleData.subList(candleData.size() -
                                currZoomLevel.getNumVisibleCandles(), candleData.size()));
                xAxis.setLowerBound(newLowerBoundX);
                setYAndExtraAxisBounds();
                layoutChart();
                drawChartContents();
            }
        }
    }

    private class CandlePageConsumer implements Consumer<List<CandleData>> {

        @Override
        public void accept(List<CandleData> candleData) {
            if (Platform.isFxApplicationThread()) {
                logger.error("candle data paging must not happen on FX thread!");
                throw new IllegalStateException("candle data paging must not happen on FX thread!");
            }

            if (candleData.isEmpty()) {
                logger.warn("candleData was empty");
                return;
            }

            if (candleData.getFirst().getOpenTime() >= candleData.get(1).getOpenTime()) {
                logger.error("Paged candle data must be in ascending order by x-value");
                throw new IllegalArgumentException("Paged candle data must be in ascending order by x-value");
            }


                if (liveSyncing) {
                    if (inProgressCandle == null) {
                        throw new RuntimeException("inProgressCandle was null in live syncing mode.");
                    }
                    // We obtained the first page of candle data which does *not* include the current in-progress
                    // candle. Since we are live-syncing we need to fetch the data for what has occurred so far in
                    // the current candle.
                    long secondsIntoCurrentCandle = (Instant.now().toEpochMilli() / 1000L) -
                            (candleData.getLast().getOpenTime() + secondsPerCandle);
                    inProgressCandle.setOpenTime(candleData.getLast().getOpenTime() +
                            secondsPerCandle);

                    // We first attempt to get caught up by simply requesting shorter duration candles. Say this chart
                    // is displaying one hour per candle and secondsIntoCurrentCandle is 1800 (30 minutes). Then we
                    // would request candles starting from when the current in-progress candle started but with
                    // a supported duration closest to, but less than the current duration, 1800/200 (as 200 is the
                    // limit of candles per page). This would give us 9 second candles that we can then sum. This will
                    // catch the data up to within 9 seconds of current time (or in this case roughly within 0.25% of
                    // current time).
                    CompletableFuture<Optional<?>> inProgressCandleDataOptionalFuture = exchange
                            .fetchCandleDataForInProgressCandle(tradePair, Instant.ofEpochSecond(
                                            candleData.getLast().getOpenTime() + secondsPerCandle),
                                    secondsIntoCurrentCandle, secondsPerCandle);
                    inProgressCandleDataOptionalFuture.whenComplete((inProgressCandleDataOptional, throwable) -> {
                        if (throwable == null) {
                            if (inProgressCandleDataOptional.isPresent()) {
                                InProgressCandleData inProgressCandleData = (InProgressCandleData) inProgressCandleDataOptional.get();

                                // Our second attempt to get caught up requests all trades that have happened since
                                // the time of the last sub-candle (the 9 seconds long candles from above). This will
                                // get us caught up to the current time. The reason we don't use the more simple
                                // approach of requesting all the trades that have happened in the current candle to
                                // begin with is that this can take a prohibitively long time if the candle duration
                                // is too large (some exchanges have multiple trades every second).

                                Consumer<List<Trade>> tradeConsumer =
                                        (tr) -> tradeHistory.getAllTrades().addAll(tr);


                                exchange.fetchRecentTradesUntil(
                                        tradePair, Instant.ofEpochSecond(inProgressCandleData.getCurrentTill()),

                                        tradeConsumer
                                );


                                if (tradeHistory.getAllTrades().isEmpty()) {
                                    // No trading activity happened in addition to the sub-candles from above.


                                    logger.info(
                                            "No trades have happened in addition to the sub-candles from above. " +
                                                    "This is likely due to an exchange having multiple trades every second. " +
                                                    "This is likely due to the candle duration being too large. "
                                    );

                                    telegramBot.sendMessage(chatId,
                                            "No trades have happened in addition to the sub-candles from above. " +
                                                    "This is likely due to an exchange having multiple trades every second. " +
                                                    "This is likely due to the candle duration being too large. "
                                    );
                                    inProgressCandle.setIsPlaceholder(true);
                                    inProgressCandle.setHighPriceSoFar(
                                            inProgressCandleData.getHighPriceSoFar());
                                    inProgressCandle.setLowPriceSoFar(inProgressCandleData.getLowPriceSoFar());
                                    inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolumeSoFar());
                                    inProgressCandle.setClosePriceSoFar(inProgressCandleData.getLastPrice());
                                } else {
                                    // We need to factor in the trades that have happened after the
                                    // "currentTill" time of the in-progress candle.
                                    inProgressCandle.setHighPriceSoFar(Math.max(tradeHistory.getAllTrades().stream().mapToDouble(
                                                    Trade::getPrice).max().getAsDouble(),
                                            inProgressCandleData.getHighPriceSoFar()));
                                    inProgressCandle.setLowPriceSoFar(Math.max(tradeHistory.getAllTrades().stream().mapToDouble(
                                                    Trade::getPrice).min().stream().sum(),
                                            inProgressCandleData.getLowPriceSoFar()));
                                    inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolumeSoFar() +
                                            tradeHistory.getAllTrades().stream().mapToDouble(
                                                    Trade::getAmount).sum());
                                    inProgressCandle.setClosePriceSoFar(tradeHistory.getAllTrades().getLast().getPrice());
                                }
                                Platform.runLater(() -> setInitialState(candleData));
                            } else {
                                logger.error("error fetching recent trades ");

                            }

                        } else {
                            // No trades have happened during the current candle so far.

                            logger.info(
                                    "No trades have happened during the current candle so far. " +
                                            "This is likely due to an exchange having multiple trades every second. " +
                                            "This is likely due to the candle duration being too large. "
                            );
                            telegramBot.sendMessage(chatId,
                                    "No trades have happened during the current candle so far. " +
                                            "This is likely due to an exchange having multiple trades every second. " +
                                            "This is likely due to the candle duration being too large. "
                            );
                            inProgressCandle.setIsPlaceholder(true);
                            inProgressCandle.setVolumeSoFar(0);
                            inProgressCandle.setCurrentTill((int) (secondsIntoCurrentCandle +
                                    (candleData.getLast().getOpenTime() + secondsPerCandle)));
                            inProgressCandle.setHighPriceSoFar(candleData.getLast().getHighPrice());
                            inProgressCandle.setLowPriceSoFar(candleData.getLast().getLowPrice());
                            if (candleData.isEmpty()) {

                                // No data available. We cannot compute the y-axis extrema.
                                // So we will set the y-axis extrema to the current candle's high and low prices.
                                inProgressCandle.setHighPriceSoFar(candleData.getLast().getHighPrice());
                                inProgressCandle.setLowPriceSoFar(candleData.getLast().getLowPrice());
                            }
                            Platform.runLater(() -> setInitialState(candleData));
                        }
                    });

                } else {
                    setInitialState(candleData);
                }

            int slidingWindowSize = currZoomLevel.getNumVisibleCandles();

                // In order to compute the y-axis extrema for the new data in the page, we have to include the
                // first numVisibleCandles from the previous page (otherwise the sliding window will not be able
                // to reach all the way).
            Map<Integer, CandleData> extremaData = new TreeMap<>();
            extremaData.put(candleData.size() - 1, candleData.getLast());
                List<CandleData> newDataPlusOffset = new ArrayList<>(candleData);
                newDataPlusOffset.addAll(extremaData.values());
                putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), newDataPlusOffset,
                        slidingWindowSize);
            candleData.addAll(candleData.stream().collect(Collectors.toMap(CandleData::getOpenTime,
                    Function.identity())).values());
            Platform.runLater(() -> setInitialState(newDataPlusOffset));
                currZoomLevel.setMinXValue(candleData.getFirst().getOpenTime());


        }
    }

    private class MouseDraggedHandler implements EventHandler<MouseEvent> {
        @Override
        public void handle(@NotNull MouseEvent event) {
            double x = event.getX();
            double y = event.getY();

            double deltaX = x - lastMouseX;
            double deltaY = y - lastMouseY;

            lastMouseX = x;
            lastMouseY = y;

            if (event.isPrimaryButtonDown()) {
                moveAlongX((int) deltaX);
            } else if (event.isMiddleButtonDown()) {
                moveAlongY((int) deltaY);
            }
        }
    }

    protected class KeyEventHandler implements EventHandler<KeyEvent> {


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
                moveAlongX(deltaX);
            }

            if (consume) {
                event.consume();
            }
        }


        public void changeZoom(ZoomDirection zoomDirection) {
            final int multiplier = zoomDirection == ZoomDirection.IN ? -1 : 1;
            if (currZoomLevel == null) {
                logger.error("currZoomLevel was null!");
                currZoomLevel = new ZoomLevel(0, candleWidth, secondsPerCandle, canvas.widthProperty(), new InstantAxisFormatter(formatter), candleWidth);
                return;
            }
            int newCandleWidth = currZoomLevel.getCandleWidth() - multiplier;
            if (newCandleWidth <= 1) {
                new Messages(Alert.AlertType.ERROR, "Can't go below one pixel for candle width.");
                return;
            }
            adjustYAxisScaling();
            adjustXAxisScaling();
            updateChartWithZoom(newCandleWidth, zoomDirection);


        }

    }

    private class ScrollEventHandler implements EventHandler<ScrollEvent> {
        @Override
        public void handle(ScrollEvent event) {
            if (paging) {

                event.consume();

                return;
            }
//            if (event.getDeltaY() != 0 && event.getTouchCount() == 0 && !event.isInertia()) {
//                double direction = -Math.signum(event.getDeltaY());
//
//                if (direction > 0) {
//                    nextPage(); // Scroll up -> Move forward
//                } else {
//                    previousPage(); // Scroll down -> Move backward
//                }}
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

        public void changeZoom(ZoomDirection zoomDirection) {
            final int multiplier = zoomDirection == ZoomDirection.IN ? -1 : 1;

            int newCandleWidth = currZoomLevel.getCandleWidth() - multiplier;
            if (newCandleWidth <= 1) {
                new Messages(Alert.AlertType.ERROR, "Can't go below one pixel for candle width.");
                return;
            }

            updateChartWithZoom(newCandleWidth, zoomDirection);
        }

    }
}