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
import javafx.geometry.Side;
import javafx.geometry.*;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.Axis;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.util.Duration;

import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
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
import static org.investpro.TradeHistory.tradeHistory;

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
public class CandleStickChart extends AnchorPane {



    private final CandleDataPager candleDataPager;
    private static final Logger logger = LoggerFactory.getLogger(CandleStickChart.class);
    /**
     * Draws the chart contents on the canvas ensuring the latest data is always displayed first.
     * It also informs the user when no data is available or when an error occurs.
     */
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000; // 2 seconds
    private final Exchange exchange;
    private final TradePair tradePair;
    private final boolean liveSyncing;
    private final Map<Integer, ZoomLevel> zoomLevelMap;
    private final KeyEventHandler keyHandler;
    private final Consumer<List<CandleData>> candlePageConsumer;
    private final EventHandler<MouseEvent> mouseDraggedHandler;
    private final EventHandler<ScrollEvent> scrollHandler;
    private final String chatId;
    Label progressIndLbl = new Label();
    String tradeMode = "Auto";
    DateTimeFormatter formatter;
    TradingType sessionType;
    private CandleStickChartOptions chartOptions;
    /**
     * Maps an open time (as a Unix timestamp) to the computed candle data (high price, low price, etc.) for a trading
     * period beginning with that opening time. Thus, the key "1601798498" would be mapped to the candle data for trades
     * from the period of 1601798498 to 1601798498 + secondsPerCandle.
     */
    private NavigableMap<Integer, CandleData> data;
    private List<Trade> prices;

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
    private double lastMouseX = 0;
    private double velocityX = 0;
    private Instant lastDragTime;
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
                     ObservableNumberValue containerHeight, String telegramBotToken) throws IOException, TelegramApiException, InterruptedException, ParseException {
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


        this.candleDataSupplier = candleDataSupplier;
        candleDataPager = new CandleDataPager(this, candleDataSupplier);
        data = Collections.synchronizedNavigableMap(new TreeMap<>(Integer::compare));




        chartOptions = new CandleStickChartOptions();
        canvasNumberFont = Font.font(FXUtils.getMonospacedFont(), 11);
        progressIndicator = new ProgressIndicator(-1);
        getStyleClass().add("candle-chart");
        xAxis = new StableTicksAxis();
        yAxis = new StableTicksAxis();
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
        progressIndLbl.setText("Loading market data " + tradePair.toString('/') + " ...");
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


                initWebSocket();
                CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier()).thenAccept(
                        candleDataPager.getCandleDataPreProcessor());


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


                gotFirstSize.removeListener(this);
            }
        };

        gotFirstSize.addListener(gotFirstSizeChangeListener);

        chartOptions.horizontalGridLinesVisibleProperty().addListener((_, _, _) ->
                drawChartContents());
        chartOptions.verticalGridLinesVisibleProperty().addListener((_, _, _) ->
                drawChartContents());
        chartOptions.showVolumeProperty().addListener((_, _, _) -> drawChartContents());
        chartOptions.alignOpenCloseProperty().addListener((_, _, _) -> drawChartContents());


    }

    private void initWebSocket() {


        inProgressCandle = new InProgressCandle();
        updateInProgressCandleTask = new UpdateInProgressCandleTask(exchange, secondsPerCandle, tradePair, data);
        updateInProgressCandleExecutor = Executors.newSingleThreadScheduledExecutor(
                new LogOnExceptionThreadFactory("UPDATE-CURRENT-CANDLE"));

        CompletableFuture.runAsync(() -> {
            boolean websocketInitialized = true;


            try {
                prices = exchange.fetchRecentTradesUntil(tradePair, Instant.now()).get();
                prices1 = exchange.fetchOrderBook(tradePair).get();

                if (!exchange.getWebsocketClient().getInitializationLatch().await(
                        10, SECONDS)) {
                    websocketInitialized = false;
                } else {
                    if (exchange.getWebsocketClient().supportsStreamingTrades(tradePair)) {
                        exchange.getWebsocketClient().streamLiveTrades(tradePair, updateInProgressCandleTask);
                    }
                }


                ArrayList<Attribute> attributes = new ArrayList<>();
                attributes.add(new Attribute("open"));
                attributes.add(new Attribute("high"));
                attributes.add(new Attribute("low"));
                attributes.add(new Attribute("close"));
                attributes.add(new Attribute("volume"));

                // Class Attribute (BUY, SELL, HOLD)
                ArrayList<String> classValues = new ArrayList<>();
                classValues.add("HOLD");
                classValues.add("BUY");
                classValues.add("SELL");
                attributes.add(new Attribute("class", classValues));

                // Create an empty dataset
                Instances trainingData = new Instances("MarketData", attributes, 0);
                trainingData.setClassIndex(attributes.size() - 1);

                // Generate some sample data
                for (int i = 0; i < 100; i++) {
                    DenseInstance instance = new DenseInstance(attributes.size());
                    instance.setDataset(trainingData);
                    instance.setValue(attributes.get(0), Math.random() * 100 + 100); // Open
                    instance.setValue(attributes.get(1), Math.random() * 100 + 150); // High
                    instance.setValue(attributes.get(2), Math.random() * 100 + 50);  // Low
                    instance.setValue(attributes.get(3), Math.random() * 100 + 100); // Close
                    instance.setValue(attributes.get(4), Math.random() * 1000);      // Volume
                    instance.setValue(attributes.get(5), (int) (Math.random() * 3)); // Class (BUY, SELL, HOLD)

                    trainingData.add(instance);
                }

                // Train the AI model
                TradingAI tradingAI = new TradingAI(trainingData);
                tradingAI.trainModel();
                SIGNAL signal = tradingAI.getMovingAverageSignal(prices1, prices.stream().findFirst().get().getPrice());


                if (autoTrading) executeTrade(signal);


            } catch (InterruptedException | ExecutionException | InvalidKeyException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }


            if (!websocketInitialized) {
                logger.error("websocket client: {} was not initialized after 10 seconds", exchange.getWebsocketClient().getUri().getHost());
            } else {
                if (exchange.getWebsocketClient().supportsStreamingTrades(tradePair)) {
                    exchange.getWebsocketClient().streamLiveTrades(tradePair, updateInProgressCandleTask);
                }

                updateInProgressCandleExecutor.scheduleAtFixedRate(updateInProgressCandleTask, 5, 10, SECONDS);


            }
            trainAIWithHistoricalData();
        });

    }

    private void trainAIWithHistoricalData() {
        try {
            // Fetch historical market data
            List<CandleData> historicalData = new ArrayList<>(data.values());// Example: 500 candles

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
            }

            // Train AI model
            TradingAI tradingAI = new TradingAI(trainingData);
            tradingAI.trainModel();
            logger.info("AI Model successfully trained with historical market data.");

        } catch (Exception e) {
            logger.error("Error training AI with historical data: ", e);
        }
    }

    private void sendTelegramAlert(String message) {
        try {
            TelegramClient.sendMessage(chatId, "📈 Trade Alert: " + message);
            logger.info("Telegram Alert Sent: {}", message);
        } catch (Exception e) {
            logger.error("Failed to send Telegram alert: ", e);
        }
    }

    private void executeTrade(SIGNAL signal) {
        try {
            // Fetch live bid/ask prices

            double price = prices1.stream().toList().getFirst().getAskEntries().getFirst().getPrice();// Execute at bid price

            // Trade execution parameters
            double amount = 0.02;  // Lot size
            double stopLoss = price * 0.99;  // 1% SL
            double takeProfit = price * 1.02; // 2% TP

            // Check if trade already exists to prevent duplication
            if (tradeHistory.stream().toList().stream().map(c -> c.tradePair).toList().getLast() == tradePair && tradeHistory.getFirst().getSignal() == signal) {
                logger.info("Skipping duplicate {} trade for {}", signal, tradePair);
                return;
            }

            switch (signal) {
                case BUY -> {
                    exchange.createOrder(tradePair, BUY, ENUM_ORDER_TYPE.STOP_LOSS, price, amount, new Date(), stopLoss, takeProfit);
                    sendTelegramAlert("BUY order executed at $" + price);
                }
                case SELL -> {
                    exchange.createOrder(tradePair, SELL, ENUM_ORDER_TYPE.STOP_LOSS, price, amount, new Date(), stopLoss, takeProfit);
                    sendTelegramAlert("SELL order executed at $" + price);
                }
                default -> {
                    logger.info("No trade executed.");
                    sendTelegramAlert(
                            "No trade executed for " + tradePair + ". Current price: $" + price
                    );
                }
            }

            // Store trade signal to prevent unnecessary repeats
            tradeHistory.getFirst().put(tradePair, signal);

        } catch (Exception e) {
            logger.error("Trade execution failed: ", e);
            sendTelegramAlert(
                    "Failed to execute trade for " + tradePair + ". Error: " + e.getMessage()
            );
        }
    }

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

    /**
     * Adds event filters to the parent container of the canvas.
     */
    private void addEventFilters(@NotNull Parent parent) {
        parent.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
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

    /**
     * Handles mouse movements to display crosshair or tooltip.
     */
    private void handleMouseMove(@NotNull MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();
        updateCrosshair(mouseX, mouseY);
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

    /**
     * Updates crosshair position for better UX.
     */
    private void updateCrosshair(double x, double y) {
        graphicsContext.setStroke(Color.GRAY);
        graphicsContext.setLineWidth(1);
        graphicsContext.clearRect(0, 0, chartWidth, chartHeight);


        graphicsContext.strokeLine(x, 0, x, chartHeight);
        graphicsContext.strokeLine(0, y, chartWidth, y);
        drawChartContents();
    }

    private void moveAlongX(int deltaX) {
        if (deltaX != 1 && deltaX != -1) {
            logger.warn("Invalid deltaX value: {}. Allowed values are -1 or 1.", deltaX);
            return;
        }
        adjustXAxisScaling();

        // Check if progress indicator is already visible
        if (Platform.isFxApplicationThread()) {
            if (progressIndicator.isVisible()) return;
        } else {
            Platform.runLater(() -> {
                if (progressIndicator.isVisible()) {
                    progressIndLbl.setVisible(true);
                    progressIndLbl.setText("Loading...");

                }
            });
        }

        int direction = (deltaX == 1) ? 1 : -1;
        int desiredXLowerBound = (int) xAxis.getLowerBound() + (direction * secondsPerCandle);

        int minCandlesRemaining = Math.max(3, currZoomLevel.getNumVisibleCandles() / 10);

        // Prevent moving too far ahead
        if (desiredXLowerBound <= data.lastEntry().getValue().getOpenTime() -
                (minCandlesRemaining - 1) * secondsPerCandle) {

            if (desiredXLowerBound <= currZoomLevel.getMinXValue()) {
                CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier())
                        .thenApply(data -> {
                            try {
                                if (data == null || data.get().isEmpty()) {
                                    throw new RuntimeException("Candle data is empty or null.");
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                            return data;
                        })
                        .thenAccept(candleDataPager.getCandleDataPreProcessor())
                        .exceptionally(throwable -> {
                            logger.error("Error loading additional candle data: ", throwable);
                            Platform.runLater(() -> {
                                progressIndLbl.setVisible(true);
                                progressIndLbl.setText("Error: " + throwable.getMessage());
                            });
                            return null;
                        })
                        .thenRun(() -> Platform.runLater(() -> {
                            paging = true;
                            progressIndicator.setVisible(true);
                            progressIndLbl.setVisible(true);

                            setAxisBoundsForMove(deltaX);
                            setYAndExtraAxisBounds();

                            progressIndicator.setVisible(false);
                            progressIndLbl.setVisible(false);
                            paging = false;

                            drawChartContents();
                        }));
            } else {
                setAxisBoundsForMove(deltaX);
                setYAndExtraAxisBounds();
                drawChartContents();
            }
        }
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
        Pair<Extrema, Extrema> extremaForRange = currZoomLevel.getExtremaForCandleRangeMap().get(
                (int) xAxis.getLowerBound() - secondsPerCandle);

        if (extremaForRange == null) {
            logger.error("extremaForRange was null!");


            return;
        }

        Double yAxisMax = extremaForRange.getValue().getMax();
        Double yAxisMin = extremaForRange.getValue().getMin();
        final double yAxisDelta = yAxisMax - yAxisMin;
        yAxis.setUpperBound(yAxisMax + (yAxisDelta * idealBufferSpaceMultiplier));
        yAxis.setLowerBound(Math.max(0, yAxisMin - (yAxisDelta * idealBufferSpaceMultiplier)));

        extraAxis.setUpperBound(currZoomLevel.getExtremaForCandleRangeMap().get(
                (int) xAxis.getLowerBound() - secondsPerCandle).getKey().getMax());

        List<CandleData> pair = data.values().stream().toList();
        Map<Integer, Pair<Extrema, Extrema>> extrema = new HashMap<>();
        putExtremaForRemainingElements(extrema, pair);
    }

    private void layoutChart() {
        logger.info("CandleStickChart.layoutChart start");
        extraAxisExtension.setStartX(chartWidth - 37.5);
        extraAxisExtension.setEndX(chartWidth - 37.5);
        extraAxisExtension.setStartY(0);
        extraAxisExtension.setEndY((chartHeight - 100) * 0.75);

        graphicsContext.setFill(Color.BLACK);
        graphicsContext.fillRect(0, 0, chartWidth - 100, chartHeight - 100);
        double top = snappedTopInset();
        double left = snappedLeftInset();
        top = snapPositionY(top);
        left = snapPositionX(left);

        // try and work out width and height of axes
        double xAxisWidth;
        double xAxisHeight = 25; // guess x-axis height to start with
        double yAxisWidth = 0;
        double yAxisHeight;
        for (int count = 0; count < 3; count++) {
            yAxisHeight = snapSizeY(chartHeight - xAxisHeight);
            if (yAxisHeight < 0) {
                yAxisHeight = 0;
            }
            yAxisWidth = yAxis.prefWidth(yAxisHeight);
            xAxisWidth = snapSizeX(chartWidth - yAxisWidth);
            if (xAxisWidth < 0) {
                xAxisWidth = 0;
            }
            double newXAxisHeight = xAxis.prefHeight(xAxisWidth);
            if (newXAxisHeight == xAxisHeight) {
                break;
            }
            xAxisHeight = newXAxisHeight;
        }

        xAxisHeight = Math.ceil(xAxisHeight);
        yAxisWidth = Math.ceil(yAxisWidth);

        // calc yAxis x-pos
        double yAxisX = left + 1;
        left += yAxisWidth;
        xAxis.setLayoutX(left);
        yAxis.setLayoutX(yAxisX);
        xAxis.setPrefSize(chartWidth - 100, xAxisHeight);
        yAxis.setPrefSize(yAxisWidth, chartHeight - 100);
        extraAxis.setPrefSize(yAxisWidth, (chartHeight - 100) * 0.25);
        xAxis.setLayoutY(chartHeight - 100);
        yAxis.setLayoutY(top);
        extraAxis.setLayoutX(chartWidth - 38);
        extraAxis.setLayoutY((chartHeight - 100) * 0.75);
        yAxis.setTranslateX(chartWidth - 38);
        extraAxis.setTranslateX(-chartWidth + 38);
        extraAxisExtension.setTranslateX(-chartWidth + 38);

        xAxis.requestAxisLayout();
        xAxis.layout();
        yAxis.requestAxisLayout();
        yAxis.layout();
        extraAxis.requestAxisLayout();
        extraAxis.layout();
        canvas.setLayoutX(left);
        canvas.setLayoutY(top);
        logger.info("CandleStickChart.layoutChart end");
    }

    private void drawChartContents() {
        int attempts = 0;

        while (attempts < MAX_RETRIES) {


            if (data != null && !data.isEmpty()) {
                break;
            }

            logger.warn("No market data available. Retrying... Attempt {}", attempts + 1);
            attempts++;

            try {
                Thread.sleep(RETRY_DELAY_MS); // Wait before retrying
                List<CandleData> data0 = candleDataPager.getCandleDataSupplier().get().get(); // Replace with the actual method to refresh/retrieve data

                for (CandleData candleData : data0) {
                    data.put(data.size(), candleData);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Retry interrupted", e);
                return;
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        if (data == null || data.isEmpty()) {
            // Display message when no data is available
            graphicsContext.clearRect(0, 0, chartWidth, chartHeight);
            graphicsContext.setFill(Color.RED);
            graphicsContext.setFont(new Font("Arial", 20));
            graphicsContext.fillText("NO MARKET DATA AVAILABLE", chartWidth / 2 - 100, chartHeight / 2);
            logger.error("No market data available to draw.");

        }

        // Draw grid lines
        drawGridLines();


        // Initialize variables
        double highestCandleValue = Double.MIN_VALUE;
        double lowestCandleValue = Double.MAX_VALUE;
        double halfCandleWidth = candleWidth * 0.5;
        double lastClose = -1;
        int candleIndex = 0;

        // Ensure latest data is displayed first
        double pixelsPerMonetaryUnit = 1d / ((yAxis.getUpperBound() - yAxis.getLowerBound()) / canvas.getHeight());
        if (xAxis.getLowerBound() >= xAxis.getUpperBound()) {
            logger.error("Invalid xAxis bounds: Lower={} Upper={}", xAxis.getLowerBound(), xAxis.getUpperBound());
            return;
        }
// Display OHLCV similar to TradingView
        displayOHLCVInfo(data.lastEntry().getValue());


        // Draw candles
        for (CandleData candle : data.values()) {
            if (candleIndex < currZoomLevel.getNumVisibleCandles() + 2) {
                highestCandleValue = Math.max(highestCandleValue, candle.getHighPrice());
                lowestCandleValue = Math.min(lowestCandleValue, candle.getLowPrice());
            }

            drawCandle(candle, candleIndex, halfCandleWidth, pixelsPerMonetaryUnit, lastClose);
            lastClose = candle.getClosePrice();
            candleIndex++;
        }

        // Smooth Y-Axis scaling based on the highest and lowest visible prices
        smoothYAxisScaling(highestCandleValue, lowestCandleValue);


        logger.info("Chart drawing completed.");
        // drawChartContents();
    }

    /**
     * Draws a single candle on the chart.
     */
    private void drawCandle(@NotNull CandleData candle, int candleIndex, double halfCandleWidth,
                            double pixelsPerMonetaryUnit, double lastClose) {
        boolean isBullish = candle.getClosePrice() >= candle.getOpenPrice();
        Color candleColor = isBullish ? Color.GREEN : Color.RED;

        double x = canvas.getWidth() - ((candleIndex + 1) * candleWidth);
        double openY = cartesianToScreenCords((candle.getOpenPrice() - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        double closeY = cartesianToScreenCords((candle.getClosePrice() - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        double highY = cartesianToScreenCords((candle.getHighPrice() - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        double lowY = cartesianToScreenCords((candle.getLowPrice() - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);

        // Draw wick (high to low)
        graphicsContext.setStroke(candleColor);
        graphicsContext.setLineWidth(2);
        graphicsContext.strokeLine(x + halfCandleWidth, highY, x + halfCandleWidth, lowY);

        // Draw candle body
        graphicsContext.setFill(candleColor);
        graphicsContext.fillRect(x, Math.min(openY, closeY), candleWidth - 1, Math.abs(openY - closeY));

        // Draw volume bar
        if (!chartOptions.isShowVolume()) {
            double volumeHeight = (candle.getVolume() / extraAxis.getUpperBound()) * 100;
            double volumeY = canvas.getHeight() - volumeHeight;
            graphicsContext.setFill(candleColor);
            graphicsContext.fillRect(x, volumeY, candleWidth - 1, volumeHeight);
        }
        // Draw open/close lines
        if (chartOptions.isAlignOpenClose()) {
            graphicsContext.setStroke(Color.BLACK);
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
            graphicsContext.setFill(Color.BLACK);
            graphicsContext.setFont(new Font("Arial", 10));
            graphicsContext.fillText(String.format("%.2f", candle.getClosePrice()), labelX, labelY - 5);
        }

        try {
            drawBidAskLines();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Draws bid/ask price lines on the chart.
     */
    private void drawBidAskLines() throws ExecutionException, InterruptedException {


        if (prices == null) return;

        List<Double> bidPrice = Collections.singletonList(prices.stream().toList().getFirst().getPrice());
        List<Double> askPrice = Collections.singletonList(prices.stream().toList().getFirst().getPrice());

        double bidY = cartesianToScreenCords((bidPrice.getLast() - yAxis.getLowerBound()) *
                (1d / ((yAxis.getUpperBound() - yAxis.getLowerBound()) / canvas.getHeight())));
        double askY = cartesianToScreenCords((askPrice.getLast() - 0.01 - yAxis.getLowerBound()) *
                (1d / ((yAxis.getUpperBound() - yAxis.getLowerBound()) / canvas.getHeight())));

        graphicsContext.setStroke(Color.BLUE);
        graphicsContext.setLineWidth(1);
        graphicsContext.strokeLine(0, bidY, canvas.getWidth(), bidY);
        graphicsContext.fillText("Bid: " + bidPrice, 10, bidY - 5);

        graphicsContext.setStroke(Color.RED);
        graphicsContext.strokeLine(0, askY, canvas.getWidth(), askY);
        graphicsContext.fillText("Ask: " + askPrice, 10, askY - 5);
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

    private void updateXAxisBoundsWithAnimation() {
        if (data.isEmpty()) {
            logger.warn("No data available to update xAxis bounds.");
            return;
        }

        double latestTimestamp = data.lastEntry().getValue().getOpenTime();
        double earliestTimestamp = data.firstEntry().getValue().getOpenTime();

        double targetUpperBound = latestTimestamp + secondsPerCandle;
        double targetLowerBound = targetUpperBound - (currZoomLevel.getNumVisibleCandles() * secondsPerCandle);

        // Animate transition if bounds are changing
        if (xAxis.getUpperBound() < targetUpperBound) {
            animateXAxisTransition(targetLowerBound, targetUpperBound);
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

    /**
     * Displays OHLCV data in a format similar to TradingView.
     */
    private void displayOHLCVInfo(@NotNull CandleData latestCandle) {
        graphicsContext.setFill(Color.WHITE);
        graphicsContext.setFont(new Font("Arial", 16));

        double textX = 20;
        double textY = 20;

        // Display Symbol and Timeframe
        graphicsContext.fillText("Symbol: " + tradePair.toString('/') + "  Time: " + new SimpleDateFormat("HH:mm:ss").format(new Date()) +
                "  TimeFrame: " + granularityToString(secondsPerCandle), textX, textY);

        // OHLC Data

        textX += 150;
        textY += 20;
        // Reset X position for new line
        graphicsContext.fillText("O: " + formatPrice(latestCandle.getOpenPrice()), textX, textY);
        textX += 150;
        graphicsContext.fillText("H: " + formatPrice(latestCandle.getHighPrice()), textX, textY);
        textX += 150;
        graphicsContext.fillText("L: " + formatPrice(latestCandle.getLowPrice()), textX, textY);
        textX += 150;
        graphicsContext.fillText("C: " + formatPrice(latestCandle.getClosePrice()), textX, textY);
        textX += 150;
        graphicsContext.fillText("V: " + formatVolume(latestCandle.getVolume()), textX, textY);

        // Percentage Change Calculation
        double percentageChange = ((latestCandle.getClosePrice() - latestCandle.getOpenPrice()) / latestCandle.getOpenPrice()) * 100;
        textY += 20;
        textX = 20;
        graphicsContext.fillText("%Change: " + String.format("%.2f", percentageChange) + "%", textX, textY);

        // Spread Calculation (Bid-Ask)
        double bidPrice = latestCandle.getClosePrice() - 0.0001; // Example bid
        double askPrice = latestCandle.getClosePrice() + 0.0001; // Example ask
        double spread = askPrice - bidPrice;
        textY += 150;
        graphicsContext.fillText("Spread: " + formatPrice(spread), 20, textY);

        // Bot & Trading Session Information
        textY = 80;
        graphicsContext.fillText(" Bot: " + telegramBot.getUsername(), textX, textY);
        textX += 150;
        graphicsContext.fillText(" Session: " + getSessionType(secondsPerCandle), textX, textY);
        textX += 200;
        graphicsContext.fillText(" Mode: " + tradeMode, textX, textY);

        // Trading Signal
        textX += 200;
        graphicsContext.fillText("Signal: " + determineSignal(latestCandle), textX, textY);
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

    private void initializeMomentumScrolling() {
        // Track mouse drag start
        canvas.setOnMousePressed(event -> {
            lastMouseX = event.getX();
            velocityX = 0; // Reset velocity
            lastDragTime = Instant.now();
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
        canvas.setOnMouseReleased(event -> applyMomentumScrolling(velocityX));
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
        if (Math.abs(initialVelocity) < 1) return; // Stop if velocity is too low

        Timeline timeline = new Timeline();
        double deceleration = 0.95; // Factor to slow down velocity
        DoubleProperty velocityProperty = new SimpleDoubleProperty(initialVelocity);

        KeyFrame keyFrame = new KeyFrame(Duration.millis(16), event -> {
            double newVelocity = velocityProperty.get() * deceleration;
            if (Math.abs(newVelocity) < 1) {
                timeline.stop(); // Stop when speed is too low
            } else {
                velocityProperty.set(newVelocity);
                moveXAxisByPixels(newVelocity * 0.016); // Apply movement per frame (60 FPS)
            }
        });

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(16), String.valueOf(keyFrame)));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void drawGridLines() {

        graphicsContext.setFill(Color.BLACK);
        graphicsContext.fillRect(0, 0, chartWidth, chartHeight);
        graphicsContext.setStroke(Color.rgb(189, 189, 189, 0.6));
        graphicsContext.setLineWidth(1.5);

        // Ensure xAxis includes latest data before drawing

        updateXAxisBoundsWithAnimation();
        // Draw horizontal grid lines (y-axis)
        for (Axis.TickMark<Number> tickMark : yAxis.getTickMarks()) {
            graphicsContext.strokeLine(0, tickMark.getPosition(), canvas.getWidth(), tickMark.getPosition());
        }

        // Draw vertical grid lines (x-axis)
        for (Axis.TickMark<Number> tickMark : xAxis.getTickMarks()) {
            double tickX = tickMark.getPosition();
            graphicsContext.strokeLine(tickX, 0, tickX, canvas.getHeight());
        }

        logger.info("Grid lines drawn with updated xAxis bounds.");
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
        drawChartContents();
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
        if (liveSyncing) {
            candleData.add(candleData.size(), inProgressCandle.snapshot());
        }

        xAxis.setUpperBound(candleData.getLast().getOpenTime() + secondsPerCandle);
        xAxis.setLowerBound((candleData.getLast().getOpenTime() + secondsPerCandle) -
                (int) (Math.floor(canvas.getWidth() / candleWidth) * secondsPerCandle));

        currZoomLevel = new ZoomLevel(0, candleWidth, secondsPerCandle, canvas.widthProperty(),
                getXAxisFormatterForRange(xAxis.getUpperBound() - xAxis.getLowerBound()),
                candleData.getFirst().getOpenTime());
        zoomLevelMap.put(0, currZoomLevel);
        xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());

        if (currZoomLevel.getExtremaForCandleRangeMap() == null) {
            return;
        }
        putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(),
                candleData,
                currZoomLevel.getNumVisibleCandles());
        putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                (candleData.size() - currZoomLevel.getNumVisibleCandles() - (liveSyncing ? 1 : 0)),
                candleData.size()));
        setYAndExtraAxisBounds();
        data.putAll(candleData.stream().collect(Collectors.toMap(CandleData::getOpenTime, Function.identity())));
        drawChartContents();
        progressIndicator.setVisible(false);
        updateInProgressCandleTask.setReady(true);
    }

    private void initializeZoomLevel() {
        if (currZoomLevel == null) {
            logger.info("Initializing currZoomLevel with default values...");
            currZoomLevel = new ZoomLevel(
                    0, // Default zoom level
                    candleWidth, // Current candle width
                    secondsPerCandle, // Current seconds per candle
                    canvas.widthProperty(), // Canvas width
                    new InstantAxisFormatter(formatter), // Axis formatter
                    candleWidth // Default candle width
            );
        }
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
            canvas.setWidth(chartWidth - 100);
            canvas.setHeight(chartHeight - 100);

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
                        candleDataPager.getCandleDataPreProcessor()).whenComplete((result, throwable) -> {
                    currZoomLevel.getExtremaForCandleRangeMap().clear();
                    List<CandleData> candleData = new ArrayList<>(data.values());
                    putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(),
                            candleData, currZoomLevel.getNumVisibleCandles());
                    putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(),
                            candleData.subList(candleData.size() - currZoomLevel.getNumVisibleCandles(), candleData.size()));
                    Platform.runLater(() -> {
                        xAxis.setLowerBound(newLowerBoundX);
                        setYAndExtraAxisBounds();
                        layoutChart();
                        drawChartContents();
                        progressIndicator.setVisible(false);
                        paging = false;
                    });
                });
            } else {
                currZoomLevel.getExtremaForCandleRangeMap().clear();
                List<CandleData> candleData = new ArrayList<>(data.values());
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

            if (data.isEmpty()) {
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
                                int currentTill = (int) Instant.now().getEpochSecond();
                                CompletableFuture<List<Trade>> tradesFuture;
                                try {
                                    tradesFuture = exchange.fetchRecentTradesUntil(
                                            tradePair, Instant.ofEpochSecond(inProgressCandleData.getCurrentTill()));
                                } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                                    throw new RuntimeException(e);
                                }

                                tradesFuture.whenComplete((trades, exception) -> {
                                    if (exception == null) {
                                        inProgressCandle.setOpenPrice(inProgressCandleData.getOpenPrice());
                                        inProgressCandle.setCurrentTill(currentTill);

                                        if (trades.isEmpty()) {
                                            // No trading activity happened in addition to the sub-candles from above.


                                            logger.info(
                                                    "No trades have happened in addition to the sub-candles from above. " +
                                                            "This is likely due to an exchange having multiple trades every second. " +
                                                            "This is likely due to the candle duration being too large. "
                                            );

                                            TelegramClient.sendMessage(chatId,
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
                                            inProgressCandle.setHighPriceSoFar(Math.max(trades.stream().mapToDouble(
                                                            Trade::getPrice).max().getAsDouble(),
                                                    inProgressCandleData.getHighPriceSoFar()));
                                            inProgressCandle.setLowPriceSoFar(Math.max(trades.stream().mapToDouble(
                                                            Trade::getPrice).min().stream().sum(),
                                                    inProgressCandleData.getLowPriceSoFar()));
                                            inProgressCandle.setVolumeSoFar(inProgressCandleData.getVolumeSoFar() +
                                                    trades.stream().mapToDouble(
                                                            Trade::getAmount).sum());
                                            inProgressCandle.setClosePriceSoFar(trades.getLast().getPrice());
                                        }
                                        Platform.runLater(() -> setInitialState(candleData));
                                    } else {
                                        logger.error("error fetching recent trades until: {}", inProgressCandleData.getCurrentTill(), exception);

                                    }
                                });
                            } else {
                                // No trades have happened during the current candle so far.

                                logger.info(
                                        "No trades have happened during the current candle so far. " +
                                                "This is likely due to an exchange having multiple trades every second. " +
                                                "This is likely due to the candle duration being too large. "
                                );
                                TelegramClient.sendMessage(chatId,
                                        "No trades have happened during the current candle so far. " +
                                                "This is likely due to an exchange having multiple trades every second. " +
                                                "This is likely due to the candle duration being too large. "
                                );
                                inProgressCandle.setIsPlaceholder(true);
                                inProgressCandle.setVolumeSoFar(0);
                                inProgressCandle.setCurrentTill((int) (secondsIntoCurrentCandle +
                                        (candleData.getLast().getOpenTime() + secondsPerCandle)));
                                Platform.runLater(() -> setInitialState(candleData));
                            }
                        } else {
                            logger.error("error fetching in-progress candle data: ", throwable);
                        }
                    });
                } else {
                    setInitialState(candleData);
                }
            } else {
                int slidingWindowSize = currZoomLevel.getNumVisibleCandles();

                // In order to compute the y-axis extrema for the new data in the page, we have to include the
                // first numVisibleCandles from the previous page (otherwise the sliding window will not be able
                // to reach all the way).
                Map<Integer, CandleData> extremaData = new TreeMap<>(data.subMap((int) currZoomLevel.getMinXValue(), (int) (currZoomLevel.getMinXValue() + ((long) currZoomLevel.getNumVisibleCandles() *
                        secondsPerCandle))));
                List<CandleData> newDataPlusOffset = new ArrayList<>(candleData);
                newDataPlusOffset.addAll(extremaData.values());
                putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), newDataPlusOffset,
                        slidingWindowSize);
                data.putAll(candleData.stream().collect(Collectors.toMap(CandleData::getOpenTime,
                        Function.identity())));
                currZoomLevel.setMinXValue(candleData.getFirst().getOpenTime());
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
                moveAlongX(deltaX);
                scrollDeltaXSum = 0;
            }
            mousePrevX = event.getScreenX();
            mousePrevY = event.getScreenY();
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
            int attempts = 0;
            final int MAX_RETRIES = 3;
            final int RETRY_DELAY_MS = 2000; // 2 seconds

            while (currZoomLevel == null && attempts < MAX_RETRIES) {
                logger.warn("currZoomLevel is null! Retrying... Attempt {}", attempts + 1);
                attempts++;

                try {
                    Thread.sleep(RETRY_DELAY_MS); // Wait before retrying
                    initializeZoomLevel(); // Replace with your actual initialization method
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Retry interrupted while initializing zoom level", e);
                    return;
                }
            }

            if (currZoomLevel == null) {
                logger.error("Failed to initialize currZoomLevel after " + MAX_RETRIES + " attempts.");
                return;
            }

            int newCandleWidth = currZoomLevel.getCandleWidth() - multiplier;
            if (newCandleWidth <= 1) {
                new Messages(Alert.AlertType.ERROR, "Can't go below one pixel for candle width.");
                return;
            }

            adjustYAxisScaling();
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
            updateChartWithZoom(newCandleWidth, zoomDirection);
        }

    }
}