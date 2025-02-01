package org.investpro;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableNumberValue;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.investpro.CandleStickChartUtils.getXAxisFormatterForRange;
import static org.investpro.CandleStickChartUtils.putSlidingWindowExtrema;
import static org.investpro.ChartColors.DarkTheme.BEAR_CANDLE_BORDER_COLOR;
import static org.investpro.ChartColors.DarkTheme.BULL_CANDLE_BORDER_COLOR;
import static org.investpro.ChartColors.LightTheme.BEAR_CANDLE_FILL_COLOR;
import static org.investpro.ChartColors.LightTheme.BULL_CANDLE_FILL_COLOR;
import static org.investpro.Side.BUY;


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
 * a {@link }. To enforce this usage, the constructors for this class are package-private.
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
 * old approach, the drawing of the volume bars and the panning and zooming capabilities were all extremely ad-hoc and
 * buggy. For example, the panning was simulated by using a ScrollPane which functioned very poorly when paging in
 * new candles (as the bounds of the pane were changing while scrolling was happening, so "jumps" would occur).
 * Also, to implement panning and zooming, we needed access to all the chart's internal data (and then some),
 * and so the encapsulation of the chart's data by the Chart class was being completely bypassed.
 *
 * @author NOEL NGUEMECHIEU
 */
@Getter
@Setter
public class CandleStickChart extends Region {

    public static final Logger logger = LoggerFactory.getLogger(CandleStickChart.class);

    private final CandleDataPager candleDataPager;
    private final CandleStickChartOptions chartOptions;

    static {
        new DecimalFormat("#.0000");
    }

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("UTC"));

    private final Exchange exchange;
    private final TradePair tradePair;
    private final boolean liveSyncing;
    private final Map<Integer, ZoomLevel> zoomLevelMap;
    private final ScheduledExecutorService updateInProgressCandleExecutor;
    private final UpdateInProgressCandleTask updateInProgressCandleTask;
    private final StableTicksAxis xAxis;
    private final StableTicksAxis yAxis;
    private final StableTicksAxis extraAxis;
    private final ProgressIndicator progressIndicator;
    private final Line extraAxisExtension;

    private final Font canvasNumberFont;
    private final int secondsPerCandle;
    private Canvas canvas;
    private GraphicsContext graphicsContext;
    private int candleWidth = 10;
    private double mousePrevX = -1;
    private double mousePrevY = -1;
    private double scrollDeltaXSum;


    private final NavigableMap<Integer, CandleData> data;
    private int inProgressCandleLastDraw = -1;
    private volatile ZoomLevel currZoomLevel;
    private volatile boolean paging;
    Button zoomInBtn = new Button("Zoom In");

    TradingAI tradingAI;
    TradeHistory tradeHistory;
    List<Trade> currentCandleTrades;
    private double chartWidth = 1000;
    private double chartHeight = 600;
    Button zoomOutBtn = new Button("Zoom Out");
    int panX;
    int panY;
    private InProgressCandle inProgressCandle;
    private boolean showTrades = true;
    // Track zoom & pan levels
    private double zoomFactor = 1.0;
    private double panOffset = 0;
    private boolean showGrid = true;  // Toggle grid on/off

    CandleStickChart(Exchange exchange, TradePair tradePair, CandleDataSupplier candleDataSupplier,
                     boolean liveSyncing, int secondsPerCandle, ObservableNumberValue containerWidth,
                     ObservableNumberValue containerHeight) throws IOException {
        logger.debug(String.valueOf(this));
        Objects.requireNonNull(exchange);
        Objects.requireNonNull(candleDataSupplier);
        this.tradePair = tradePair;
        Objects.requireNonNull(containerWidth);
        Objects.requireNonNull(containerHeight);
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalArgumentException("CandleStickChart must be constructed on the JavaFX Application Thread but was called from %s".formatted(Thread.currentThread()));
        }
        this.exchange = exchange;


        this.secondsPerCandle = secondsPerCandle;
        this.liveSyncing = liveSyncing;
        zoomLevelMap = new ConcurrentHashMap<>();
        candleDataPager = new CandleDataPager(this, candleDataSupplier);
        data = Collections.synchronizedNavigableMap(new TreeMap<>(Integer::compare));
        try {
            for (CandleData candleData : candleDataSupplier.get().get()) {
                data.put(data.size(), candleData);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        chartOptions = new CandleStickChartOptions();
        canvasNumberFont = Font.font(FXUtils.getMonospacedFont(), 15);

        progressIndicator = new ProgressIndicator(-1);
        getStyleClass().add("candle-chart");
        xAxis = new StableTicksAxis(
                0,
                secondsPerCandle
        );
        yAxis = new StableTicksAxis(0, 1000000);
        yAxis.setSide(Side.RIGHT);
        extraAxis = new StableTicksAxis(0, 1000000);
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
        extraAxis.setAnimated(false);
        xAxis.setAutoRanging(false);
        xAxis.setForceZeroInRange(false);
        yAxis.setAutoRanging(false);
        xAxis.setForceZeroInRange(false);
        extraAxis.setAutoRanging(false);
        xAxis.setSide(Side.BOTTOM);
        extraAxis.setSide(Side.LEFT);
        xAxis.setForceZeroInRange(false);
        yAxis.setForceZeroInRange(false);
        xAxis.setTickLabelFormatter(
                new InstantAxisFormatter(formatter)
        );
        xAxis.setForceZeroInRange(true);
        yAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getCounterCurrency()));
        yAxis.setForceZeroInRange(true);

        extraAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getBaseCurrency()));
        Font axisFont = Font.font(FXUtils.getMonospacedFont(), 14);
        yAxis.setTickLabelFont(axisFont);
        xAxis.setTickLabelFont(axisFont);
        extraAxis.setTickLabelFont(axisFont);
        Text loadingText = new Text("");
        loadingText.setFill(Color.BLUE);

        VBox loadingIndicatorContainer = new VBox(progressIndicator);
        loadingIndicatorContainer.setPadding(new Insets(20));
        loadingIndicatorContainer.setAlignment(Pos.CENTER);
        progressIndicator.setPrefSize(40, 40);
        loadingIndicatorContainer.setAlignment(Pos.CENTER);
        loadingIndicatorContainer.setMouseTransparent(true);
        loadingText.setFont(axisFont);
        loadingText.setFill(Color.WHITE);
        loadingText.setMouseTransparent(true);

        // We want to extend the extra axis (volume) visually so that it encloses the chart area.
        extraAxisExtension = new Line();

        Paint lineColor = Color.rgb(95, 195, 195);
        extraAxisExtension.setFill(lineColor);
        extraAxisExtension.setStroke(lineColor);
        extraAxisExtension.setSmooth(false);
        extraAxisExtension.setStrokeWidth(2);

        extraAxisExtension.setStartX(0);
        extraAxisExtension.setStartY(0);
        extraAxisExtension.setEndX(0);
        extraAxisExtension.setEndY(chartHeight);
        extraAxisExtension.setStrokeWidth(2);

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
                    logger.error("websocket client: " + exchange.getWebsocketClient().getURI().getHost() +
                            " was not initialized after 10 seconds");
                } else {
                    if (exchange.getWebsocketClient().supportsStreamingTrades(tradePair)) {
                        exchange.getWebsocketClient().streamLiveTrades(tradePair, updateInProgressCandleTask);
                    }

                    updateInProgressCandleExecutor.scheduleAtFixedRate(updateInProgressCandleTask, 5, 5, SECONDS);
                }
            });
        } else {
            inProgressCandle = null;
            updateInProgressCandleTask = null;
            updateInProgressCandleExecutor = null;
        }
        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);
        final ChangeListener<Number> sizeListener = new SizeChangeListener(gotFirstSize, containerWidth,
                containerHeight);
        containerWidth.addListener(sizeListener);
        containerHeight.addListener(sizeListener);








        // When the application starts up and tries to initialize a candle stick chart the size can
        // fluctuate. So we wait to get the "final" size before laying out the chart. After we get
        // the size, we remove this listener from the gotFirstSize property.
        ChangeListener<? super Boolean> gotFirstSizeChangeListener = new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                double numberOfVisibleWholeCandles = Math.floor(containerWidth.getValue().doubleValue() / candleWidth);
                chartWidth = (numberOfVisibleWholeCandles * candleWidth) - 60 + ((double) candleWidth / 2);
                chartWidth = (Math.floor(containerWidth.getValue().doubleValue() / candleWidth) * candleWidth) - 60 +
                        ((double) candleWidth / 2);
                chartHeight = containerHeight.getValue().doubleValue();
                canvas = new Canvas(chartWidth - 100, chartHeight - 100);

                canvas.setLayoutY(
                        (containerHeight.getValue().doubleValue() - chartHeight) / 2);
                canvas.setLayoutX((containerWidth.getValue().doubleValue() - chartWidth) / 2);
                canvas.widthProperty().addListener(sizeListener);
                canvas.heightProperty().addListener(sizeListener);
                extraAxisExtension.setTranslateX(-chartWidth + 37.8 + 70);
                extraAxis.setTranslateX(-chartWidth + 37.8 + 10);
                yAxis.setTranslateX(chartWidth - 37.8);
                StackPane chartStackPane = new StackPane(canvas, loadingIndicatorContainer);
                chartStackPane.setTranslateX(64); // Only necessary when wrapped in StackPane...why?
                getChildren().addFirst(chartStackPane);
                canvas.setOnMouseEntered(_ -> canvas.getScene().setCursor(Cursor.HAND));
                canvas.setOnMouseExited(_ -> canvas.getScene().setCursor(Cursor.DEFAULT));


                layoutChart();

                CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier()).thenAccept(candleDataPager.getCandleDataPreProcessor());

                drawChart();


// Zoom Handling (Mouse Scroll)
                canvas.setOnScroll(event -> {
                    double zoomAmount = event.getDeltaY() > 0 ? 1.1 : 0.9;
                    zoomFactor *= zoomAmount;
                    zoomFactor = Math.max(0.5, Math.min(3, zoomFactor));  // Limit zoom range
                    drawChart();
                });

// Panning (Dragging)
                canvas.setOnMousePressed(event -> mousePrevX = event.getX());
                canvas.setOnMouseDragged(event -> {
                    double dx = event.getX() - mousePrevX;
                    mousePrevX = event.getX();
                    panOffset -= dx / canvas.getWidth();
                    panOffset = Math.max(0, Math.min(1, panOffset));  // Keep within bounds
                    drawChart();
                });

                // Toolbar Buttons
                Button panLeftBtn = new Button("←");
                panLeftBtn.setOnAction(e -> panX -= 20);

                Button panRightBtn = new Button("→");
                panRightBtn.setOnAction(e -> panX += 20);

                Button panUpBtn = new Button("↑");
                panUpBtn.setOnAction(e -> panY -= 20);

                Button panDownBtn = new Button("↓");
                panDownBtn.setOnAction(e -> panY += 20);

                Button toggleGridBtn = new Button("Toggle Grid");
                toggleGridBtn.setOnAction(e -> {
                    showGrid = !showGrid;
                    drawChart();
                });

                ToolBar toolbar = new ToolBar(zoomInBtn, zoomOutBtn, panLeftBtn, panRightBtn, panUpBtn, panDownBtn, toggleGridBtn);

                toolbar.setTranslateX(500);
                getChildren().addAll(toolbar);
// Toggle Grid with KeyPress
                canvas.setOnKeyPressed(event -> {
                    if (event.getCode() == KeyCode.G) {
                        showGrid = !showGrid;
                        drawChart();
                    }
                });
                // Event Listeners
                canvas.setOnScroll(this::handleZoom);
                canvas.setOnKeyPressed(e -> handleKeyPress(e.getCode()));

                gotFirstSize.removeListener(this);
            }

            private void handleZoom(@NotNull ScrollEvent event) {

                if (event.getDeltaY() > 0) {

                    changeZoom(ZoomDirection.IN);
                } else {
                    changeZoom(ZoomDirection.OUT);
                }
            }
        };

        gotFirstSize.addListener(gotFirstSizeChangeListener);

        zoomOutBtn.setOnAction(e -> changeZoom(ZoomDirection.IN));

        zoomInBtn.setOnAction(e -> changeZoom(ZoomDirection.OUT));
        // Layout


        getChildren().addAll(xAxis, yAxis, extraAxis, extraAxisExtension);


    }

    private void handleKeyPress(KeyCode key) {

        ZoomDirection zoomDirection = ZoomDirection.values()[key.ordinal()];
        switch (key) {

            case LEFT -> panX -= 20;
            case RIGHT -> panX += 20;
            case UP -> panY -= 20;
            case DOWN -> panY += 20;

            case PLUS, EQUALS, MINUS -> changeZoom(zoomDirection);
            case G -> showGrid = !showGrid;
        }
        drawChart();
    }

    public void updateChart(List<CandleData> newCandles) {
        Platform.runLater(() -> {
            for (CandleData candle : newCandles) {
                data.put(candle.getOpenTime(), candle);
                SIGNAL signal = tradingAI.getSignal(
                        candle.getOpenPrice(), candle.getHighPrice(),
                        candle.getLowPrice(), candle.getClosePrice(), candle.getVolume()
                );
                candle.setSignal(signal);
            }
            drawChart();
        });
    }

    private void drawChart() {


        graphicsContext.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        graphicsContext.setFill(Color.BLACK);
        graphicsContext.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (showGrid) drawGrid();

        drawChartContents(); // Call the modular functio
        if (showTrades) drawTradeMarkers();
    }

    private void drawGrid() {

        graphicsContext.setStroke(Color.DARKGRAY);
        graphicsContext.setLineWidth(0.5);
        double chartWidth = canvas.getWidth();
        double chartHeight = canvas.getHeight();
        // X-Axis (Time-Based Grid)
        double timeStep = 10 * zoomFactor; // Dynamic spacing based on zoom
        for (double x = xAxis.getMax() % timeStep; x < chartWidth; x += timeStep) {
            graphicsContext.strokeLine(x, 0, x, chartHeight);
        }
        xAxis.setTickLabelGap(timeStep);
        xAxis.setUpperBound(chartHeight * timeStep);
        // Y-Axis (Price-Based Grid)
        double maxPrice = data.values().stream().mapToDouble(CandleData::getHighPrice).max().orElse(1);
        double minPrice = data.values().stream().mapToDouble(CandleData::getLowPrice).min().orElse(0);
        double priceStep = (maxPrice - minPrice) / 10; // 10 horizontal divisions

        yAxis.setUpperBound(chartHeight * priceStep);
        yAxis.setTickLabelGap(priceStep);
        for (double price = minPrice; price <= maxPrice; price += priceStep) {
            double y = mapPriceToY(price, minPrice, maxPrice - minPrice);
            graphicsContext.strokeLine(0, y, chartWidth, y);
        }
    }

    // Function to map price to screen Y-coordinate
    private double mapPriceToY(double price, double minPrice, double priceRange) {
        return canvas.getHeight() - ((price - minPrice) / priceRange * canvas.getHeight());
    }


    private void drawCandles(CandleData candle, int candleIndex, double halfCandleWidth, double pixelsPerMonetaryUnit, double lastClose) {
        if (data.isEmpty()) {
            graphicsContext.strokeText(
                    "NO DATA FOUND!",
                    canvas.getWidth() / 2, canvas.getHeight() / 2
            );
            return;
        }

        candleWidth = (int) (2 * halfCandleWidth);

        // **Ensure Latest Data is Displayed First**
        List<CandleData> candleList = new ArrayList<>(data.values());
        Collections.reverse(candleList); // Newest candles appear first

        // **Determine Price Range for Scaling**
        double maxPrice = candleList.stream().mapToDouble(CandleData::getHighPrice).max().orElse(100);
        double minPrice = candleList.stream().mapToDouble(CandleData::getLowPrice).min().orElse(0);
        double pixelsPerUnit = canvas.getHeight() / (maxPrice - minPrice);

        // **Align X-Axis: Most Recent on Right, Scroll Left for Older Data**
        double x = canvas.getWidth() - ((candleIndex + 1) * candleWidth);

        // **Candle Properties**
        boolean isBullish = candle.getClosePrice() >= candle.getOpenPrice();
        Color candleColor = isBullish ? Color.GREEN : Color.RED;

        // **Map Prices to Screen Coordinates**
        double openY = (maxPrice - candle.getOpenPrice()) * pixelsPerUnit;
        double closeY = (maxPrice - candle.getClosePrice()) * pixelsPerUnit;
        double highY = (maxPrice - candle.getHighPrice()) * pixelsPerUnit;
        double lowY = (maxPrice - candle.getLowPrice()) * pixelsPerUnit;

        // **Draw Candle Wick**
        graphicsContext.setStroke(candleColor);
        graphicsContext.setLineWidth(2);
        graphicsContext.strokeLine(x + halfCandleWidth, highY, x + halfCandleWidth, lowY);

        // **Draw Candle Body**
        graphicsContext.setFill(candleColor);
        graphicsContext.fillRect(x, Math.min(openY, closeY), candleWidth - 1, Math.abs(openY - closeY));

        // **Draw Trading Signals (Optional)**
        if (candle.getSignal() == SIGNAL.BUY) {
            graphicsContext.setFill(Color.YELLOW);
            graphicsContext.fillText("BUY", x, lowY - 5);
        } else if (candle.getSignal() == SIGNAL.SELL) {
            graphicsContext.setFill(Color.PURPLE);
            graphicsContext.fillText("SELL", x, highY + 15);
        }


    }


    private void drawTradeMarkers() {

        graphicsContext.setFill(Color.WHITE);
        graphicsContext.setFont(new Font(12));

        if (currentCandleTrades == null) return;
        for (Trade trade : currentCandleTrades) {
            double priceY = (data.lastEntry().getValue().getHighPrice() - trade.getPrice()) * 10;
            double x = canvas.getWidth() - 50;

            if (trade.getTransactionType() == BUY) {
                graphicsContext.setFill(Color.GREEN);
                graphicsContext.fillText("BUY", x, priceY);
            } else {
                graphicsContext.setFill(Color.RED);
                graphicsContext.fillText("SELL", x, priceY);
            }
        }

    }

    /**
     * Sets the bounds of the x-axis either one full candle to the right or left, depending on the sign
     * of deltaX. Currently, the size of deltaX does not matter (each call this method only moves
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
            throw new IllegalArgumentException("deltaX must be 1 or -1 but was: %d".formatted(deltaX));
        }
    }

    public static void putExtremaForRemainingElements(Map<Integer, Pair<Extrema, Extrema>> extrema,
                                                      final List<CandleData> candleData) {
        Objects.requireNonNull(extrema, "extrema map must not be null");
        Objects.requireNonNull(candleData, "candleData list must not be null");

        if (candleData.isEmpty()) {
            throw new IllegalArgumentException("candleData must not be empty");
        }

        double minVolume = Integer.MAX_VALUE;
        double maxVolume = Integer.MIN_VALUE;
        double minPrice = Integer.MAX_VALUE;
        double maxPrice = Integer.MIN_VALUE;

        // Iterate from the last element to the first
        for (int i = candleData.size() - 1; i >= 0; i--) {
            CandleData candle = candleData.get(i);

            // Update extrema
            minVolume = Math.min(candle.getVolume(), minVolume);
            maxVolume = Math.max(Math.ceil(candle.getVolume()), maxVolume);
            minPrice = Math.min(candle.getLowPrice(), minPrice);
            maxPrice = Math.max(Math.ceil(candle.getHighPrice()), maxPrice);

            // Store extrema for this open time
            extrema.put(candle.getOpenTime(), new Pair<>(
                    new Extrema(minVolume, maxVolume),
                    new Extrema(minPrice, maxPrice)
            ));
        }
    }

    private void layoutChart() {
        logger.info("CandleStickChart.layoutChart start");
        extraAxisExtension.setStartX(chartWidth - 37.5);
        extraAxisExtension.setEndX(chartWidth - 37.5);
        extraAxisExtension.setStartY(0);
        extraAxisExtension.setEndY((chartHeight - 100) * 0.75);
        graphicsContext = canvas.getGraphicsContext2D();

        graphicsContext.setFill(Color.BLACK);
        graphicsContext.fillRect(0, 0, chartWidth - 100, chartHeight - 100);
        double top = snappedTopInset();
        double left = snappedLeftInset();
        top = snapPositionY(top);
        left = snapPositionX(left);

        // try and work out width and height of axes
        double xAxisWidth;
        double xAxisHeight = 25; // guess x axis height to start with
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

    /**
     * Draws the chart contents on the canvas corresponding to the current x-axis, y-axis, and extra (volume) axis bounds.
     */
    private void drawChartContents() {
        int lastEntryTime = data.lastEntry().getValue().getOpenTime();
        int upperBound = (int) xAxis.getUpperBound();
        int numCandlesToSkip = Math.max((upperBound - lastEntryTime) / secondsPerCandle, 0);

        // Optimize live syncing logic
        if (liveSyncing && inProgressCandleLastDraw != inProgressCandle.getOpenTime()) {
            if (upperBound >= inProgressCandleLastDraw && upperBound < inProgressCandleLastDraw + (canvas.getWidth() * secondsPerCandle)) {
                moveAlongX(numCandlesToSkip == 0 ? 1 : 0); // Only move forward if new candle appears
            }
            inProgressCandleLastDraw = inProgressCandle.getOpenTime();
        }

        double monetaryUnitsPerPixel = (yAxis.getUpperBound() - yAxis.getLowerBound()) / canvas.getHeight();
        double pixelsPerMonetaryUnit = 1d / monetaryUnitsPerPixel;

        if (currZoomLevel == null) {
            currZoomLevel = new ZoomLevel(0, candleWidth, secondsPerCandle, canvas.widthProperty(), new InstantAxisFormatter(DateTimeFormatter.ISO_INSTANT), monetaryUnitsPerPixel);
        }

        // Get only visible candles
        int lowerBound = (int) (upperBound - (currZoomLevel.getNumVisibleCandles() * secondsPerCandle));
        NavigableMap<Integer, CandleData> candlesToDraw = data.subMap(lowerBound, true, upperBound - (numCandlesToSkip * secondsPerCandle), true);
        logger.info("Drawing " + candlesToDraw.size() + " candles.");

        // Draw Grid Lines (only if visible)
        if (chartOptions.isHorizontalGridLinesVisible() || chartOptions.isVerticalGridLinesVisible()) {
            drawGrid();
        }

        // Initialize candle drawing variables
        int candleIndex = numCandlesToSkip;
        double highestCandleValue = Double.MIN_VALUE;
        double lowestCandleValue = Double.MAX_VALUE;
        int volumeBarMaxHeight = 150;
        double volumeScale = volumeBarMaxHeight / extraAxis.getUpperBound();


        double halfCandleWidth = (double) candleWidth / 2;
        double lastClose = -1;

        // Iterate through candles efficiently
        for (CandleData candle : candlesToDraw.descendingMap().values()) {
            if (candleIndex < currZoomLevel.getNumVisibleCandles() + 2) {
                highestCandleValue = Math.max(highestCandleValue, candle.getHighPrice());
                lowestCandleValue = Math.min(lowestCandleValue, candle.getLowPrice());
            }

            //  drawSingleCandle(candle, candleIndex, halfCandleWidth, pixelsPerMonetaryUnit, lastClose);
            drawCandles(candle, candleIndex, halfCandleWidth, pixelsPerMonetaryUnit, lastClose);
            lastClose = candle.getClosePrice();


            candleIndex++;
        }

        // Adjust Y-Axis Scaling Smoothly
        smoothYAxisScaling(highestCandleValue, lowestCandleValue);

        // Draw bid/ask price lines
        drawBidAskLines();
    }

    /**
     * Smoothly adjusts the Y-axis to prevent excessive zoom-out on price spikes.
     */
    private void smoothYAxisScaling(double highest, double lowest) {
        double currentUpper = yAxis.getUpperBound();
        double currentLower = yAxis.getLowerBound();
        double margin = (highest - lowest) * 0.05; // 5% margin for smoother transitions

        double newUpper = highest + margin;
        double newLower = lowest - margin;

        if (Math.abs(newUpper - currentUpper) > 0.01 || Math.abs(newLower - currentLower) > 0.01) {
            yAxis.setUpperBound(newUpper);
            yAxis.setLowerBound(newLower);
        }
    }

    /**
     * Draws a single candle on the chart.
     */
    private void drawSingleCandle(@NotNull CandleData candle, int candleIndex, double halfCandleWidth, double pixelsPerMonetaryUnit, double lastClose) {
        Paint candleBorderColor;
        Paint candleFillColor;
        double candleOpenPrice = candle.getOpenPrice();
        if (chartOptions.isAlignOpenClose() && lastClose != -1) {
            candleOpenPrice = lastClose;
        }

        boolean openAboveClose = candleOpenPrice > candle.getClosePrice();
        candleBorderColor = openAboveClose ? BEAR_CANDLE_BORDER_COLOR : BULL_CANDLE_BORDER_COLOR;
        candleFillColor = openAboveClose ? BEAR_CANDLE_FILL_COLOR : BULL_CANDLE_FILL_COLOR;

        double candleYOrigin = cartesianToScreenCoords((openAboveClose ? candleOpenPrice : candle.getClosePrice()) - yAxis.getLowerBound()) * pixelsPerMonetaryUnit;
        double candleHeight = Math.abs(candleOpenPrice - candle.getClosePrice()) * pixelsPerMonetaryUnit;

        // Draw the candle body
        graphicsContext.setFill(candleFillColor);
        graphicsContext.fillRect(canvas.getWidth() - (candleIndex * candleWidth), candleYOrigin, candleWidth - 2, candleHeight);
        graphicsContext.setStroke(candleBorderColor);
        graphicsContext.strokeRect(canvas.getWidth() - (candleIndex * candleWidth), candleYOrigin, candleWidth - 2, candleHeight);

        // Draw wicks
        double candleHighValue = cartesianToScreenCoords((candle.getHighPrice() - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        double candleLowValue = cartesianToScreenCoords((candle.getLowPrice() - yAxis.getLowerBound()) * pixelsPerMonetaryUnit);
        graphicsContext.setStroke(candleBorderColor);
        graphicsContext.strokeLine(canvas.getWidth() - (candleIndex * candleWidth) + halfCandleWidth, candleYOrigin, canvas.getWidth() - (candleIndex * candleWidth) + halfCandleWidth, candleHighValue);
        graphicsContext.strokeLine(canvas.getWidth() - (candleIndex * candleWidth) + halfCandleWidth, candleYOrigin + candleHeight, canvas.getWidth() - (candleIndex * candleWidth) + halfCandleWidth, candleLowValue);
    }

    /**
     * Draws the live market price lines for bid and ask.
     */
    private void drawBidAskLines() {
        Random random = new Random();


        double bidPrice = data.lastEntry().getValue().getClosePrice() - random.nextDouble() * 0.05;
        double askPrice = data.lastEntry().getValue().getClosePrice() + random.nextDouble() * 0.05;

        double bidY = cartesianToScreenCoords((bidPrice - yAxis.getLowerBound()) * (1d / ((yAxis.getUpperBound() - yAxis.getLowerBound()) / canvas.getHeight())));
        double askY = cartesianToScreenCoords((askPrice - yAxis.getLowerBound()) * (1d / ((yAxis.getUpperBound() - yAxis.getLowerBound()) / canvas.getHeight())));

        graphicsContext.setStroke(Color.WHITE);
        graphicsContext.setLineWidth(1.5);
        graphicsContext.strokeLine(0, bidY, canvas.getWidth(), bidY);
        graphicsContext.strokeText("BID: " + String.format("%.5f", bidPrice), 10, bidY - 5);

        graphicsContext.setStroke(Color.YELLOW);
        graphicsContext.setLineWidth(1.5);
        graphicsContext.strokeLine(0, askY, canvas.getWidth(), askY);
        graphicsContext.strokeText("ASK: " + String.format("%.5f", askPrice), 10, askY - 5);
    }


    private double cartesianToScreenCoords(double yCoordinate) {
        return -yCoordinate + canvas.getHeight();
    }


    private void moveAlongX(int deltaX) {
        if (deltaX != 1 && deltaX != -1) {
            throw new RuntimeException("deltaX must be 1 or -1 but was: %d".formatted(deltaX));
        }

        CompletableFuture<Boolean> progressIndicatorVisibleFuture = new CompletableFuture<>();
        Platform.runLater(() -> progressIndicatorVisibleFuture.complete(progressIndicator.isVisible()));

        progressIndicatorVisibleFuture.thenAccept(progressIndicatorVisible -> {
            // Run on JavaFX thread
            if (!progressIndicatorVisible) {
                int desiredXLowerBound = (int) xAxis.getLowerBound() + (deltaX == 1 ? secondsPerCandle : -secondsPerCandle);

                // Prevent moving too far forward if only a few candles remain on the left
                int minCandlesRemaining = 3;
                if (desiredXLowerBound <= data.lastEntry().getValue().getOpenTime() - (minCandlesRemaining - 1) * secondsPerCandle) {
                    if (desiredXLowerBound <= currZoomLevel.getMinXValue()) {
                        // Request more data asynchronously
                        CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier())
                                .thenAccept(candleDataPager.getCandleDataPreProcessor())
                                .whenComplete((_, throwable) -> {
                                    if (throwable != null) {
                                        logger.error("Exception: ", throwable);
                                        return;
                                    }
                                    paging = true;
                                    Platform.runLater(() -> {
                                        progressIndicator.setVisible(true);
                                        setAxisBoundsForMove(deltaX);
                                        setYAndExtraAxisBounds();
                                        drawChart(); // **Redraw the chart after panning**
                                        progressIndicator.setVisible(false);
                                        paging = false;
                                    });
                                });
                    } else {
                        // No need to fetch more data, just adjust axis bounds and redraw
                        Platform.runLater(() -> {
                            setAxisBoundsForMove(deltaX);
                            setYAndExtraAxisBounds();
                            drawChart(); // **Redraw the chart after scrolling**
                        });
                    }
                }
            }
        });
    }


    /**
     * Sets the y-axis and extra axis bounds using only the x-axis lower bound.
     */
    private void setYAndExtraAxisBounds() {
        logger.info("xAxis lower bound:%d".formatted((int) xAxis.getLowerBound()));
        final double idealBufferSpaceMultiplier = 0.35;


        if (currZoomLevel == null) {
            InstantAxisFormatter yformat = new InstantAxisFormatter(formatter);
            currZoomLevel = new ZoomLevel(0, secondsPerCandle, candleWidth, canvas.widthProperty(), yformat, 0);
        }

        if (!currZoomLevel.getExtremaForCandleRangeMap().containsKey((int) xAxis.getLowerBound())) {
            //  Does this *always* represent a coding error on our end, or can this happen during
            // normal chart functioning, and could we handle it more gracefully?
            logger.error("The extrema map did not contain extrema for x-value: %d".formatted((int) xAxis.getLowerBound()));
            logger.error("extrema map: %s".formatted(new TreeMap<>(currZoomLevel.getExtremaForCandleRangeMap())));
            return;

        }

        // The y-axis and extra axis extrema are obtained using a key offset by minus one candle duration. This makes
        // the chart work correctly. I don't fully understand the logic behind it, so I am leaving a note for
        // my future self.
        Pair<Extrema, Extrema> extremaForRange = currZoomLevel.getExtremaForCandleRangeMap().get(
                (int) xAxis.getLowerBound() - secondsPerCandle);
        if (extremaForRange == null) {
            logger.error("extremaForRange was null!%d".formatted(secondsPerCandle));

            return;

        }
        Double yAxisMax = extremaForRange.getValue().getMax();
        Double yAxisMin = extremaForRange.getValue().getMin();
        double yAxisDelta = yAxisMax - yAxisMin;
        yAxis.setUpperBound(yAxisMax + (yAxisDelta * idealBufferSpaceMultiplier));
        yAxis.setLowerBound(Math.max(0, yAxisMin - (yAxisDelta * idealBufferSpaceMultiplier)));

        extraAxis.setUpperBound(currZoomLevel.getExtremaForCandleRangeMap().get(
                (int) xAxis.getLowerBound() - secondsPerCandle).getKey().getMax());
    }


    protected void changeZoom(ZoomDirection zoomDirection) {
        final int multiplier = zoomDirection == ZoomDirection.IN ? -1 : 1;
        if (currZoomLevel == null) {
            logger.error("currZoomLevel was null!");

            return;
        }
        int newCandleWidth = currZoomLevel.getCandleWidth() - multiplier;
        if (newCandleWidth <= 1) {
            // Can't go below one pixel for candle width.
            Alert dat = new Alert(Alert.AlertType.ERROR, " Can't go below one pixel for candle width.");

            dat.showAndWait();
            return;
        }

        int newLowerBoundX = (int) (xAxis.getUpperBound() - ((int) (canvas.getWidth() /
                newCandleWidth) * secondsPerCandle));
        if (newLowerBoundX > data.lastEntry().getValue().getOpenTime() - (2 * secondsPerCandle)) {
            // We've reached the end of the data. We can't go back further without having more data.
            Alert dat = new Alert(Alert.AlertType.ERROR, " Can't go back further without having more data.");
            dat.showAndWait();
            return;
        }

        final int nextZoomLevelId = ZoomLevel.getNextZoomLevelId(currZoomLevel, zoomDirection);
        double currMinXValue = currZoomLevel.getMinXValue();

        if (!zoomLevelMap.containsKey(nextZoomLevelId)) {
            // We can use the minXValue of the current zoom level here because, given a sequence of zoom-levels
            // z(0), z(1), ... z(n) that the chart has gone through, z(x).minXValue <= z(y).minXValue for all x > y.
            // That is, if we are currently at a max/min zoom-level in zoomLevelMap, there is no other zoom-level that
            // has a lower minXValue (assuming we did not start at the maximum or minimum zoom level).
            ZoomLevel newZoomLevel = new ZoomLevel(nextZoomLevelId, newCandleWidth, secondsPerCandle,
                    canvas.widthProperty(), getXAxisFormatterForRange(xAxis.getUpperBound() - newLowerBoundX),
                    currMinXValue);

            int numCandlesToSkip = Math.max((((int) xAxis.getUpperBound()) -
                    data.lastEntry().getValue().getOpenTime()) / secondsPerCandle, 0);

            // If there is less than numVisibleCandles on the screen, we want to be sure and check against what the
            // lower bound *would be* if we had the full amount. Otherwise, we won't be able to calculate the correct
            // extrema because the window size will be greater than the number of candles we have data for.
            if (newLowerBoundX - (numCandlesToSkip * secondsPerCandle) < currZoomLevel.getMinXValue()) {
                // We need to try and request more data so that we can properly zoom out to this level.
                paging = true;
                progressIndicator.setVisible(true);
                CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier()).thenAccept(
                        candleDataPager.getCandleDataPreProcessor()).whenComplete((_, _) -> {
                    List<CandleData> candleData = new ArrayList<>(data.values());
                    putSlidingWindowExtrema(newZoomLevel.getExtremaForCandleRangeMap(),
                            candleData, (int) newZoomLevel.getNumVisibleCandles());
                    putExtremaForRemainingElements(newZoomLevel.getExtremaForCandleRangeMap(),
                            candleData.subList(candleData.size() - (int) Math.floor(
                                    newZoomLevel.getNumVisibleCandles()), candleData.size()));
                    zoomLevelMap.put(nextZoomLevelId, newZoomLevel);
                    currZoomLevel = newZoomLevel;
                    Platform.runLater(() -> {
                        xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());
                        candleWidth = currZoomLevel.getCandleWidth();
                        xAxis.setLowerBound(newLowerBoundX);
                        setYAndExtraAxisBounds();

                        progressIndicator.setVisible(false);
                        paging = false;
                    });
                });
                return;
            } else {
                List<CandleData> candleData = new ArrayList<>(data.values());
                if (!zoomLevelMap.containsKey(nextZoomLevelId)) {

                    return;
                }

                putSlidingWindowExtrema(newZoomLevel.getExtremaForCandleRangeMap(),
                        candleData, (int) newZoomLevel.getNumVisibleCandles());
                putExtremaForRemainingElements(newZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                        candleData.size() - (int) Math.floor(newZoomLevel.getNumVisibleCandles()),
                        candleData.size()));
                zoomLevelMap.put(nextZoomLevelId, newZoomLevel);
                currZoomLevel = newZoomLevel;
            }
        } else {
            //  In this case, we only need to compute the extrema for any new live syncing data that has
            //  happened since the last time we were at this zoom level.
            currZoomLevel = zoomLevelMap.get(nextZoomLevelId);
            List<CandleData> candleData = new ArrayList<>(data.values());
            putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), candleData,
                    (int) currZoomLevel.getNumVisibleCandles());
            putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                    candleData.size() - (int) Math.floor(currZoomLevel.getNumVisibleCandles()), candleData.size()));
            xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());
            candleWidth = currZoomLevel.getCandleWidth();
            xAxis.setLowerBound(newLowerBoundX);
        }

        xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());
        candleWidth = currZoomLevel.getCandleWidth();
        xAxis.setLowerBound(newLowerBoundX);
        setYAndExtraAxisBounds();


    }

    CandleStickChartOptions getChartOptions() {
        return chartOptions;
    }

    Consumer<List<CandleData>> getCandlePageConsumer() {
        return new CandlePageConsumer();
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
    protected double computePrefWidth(double height) {
        return widthProperty().get();
    }

    @Override
    protected double computePrefHeight(double width) {
        return heightProperty().get();
    }

    private void setInitialState(List<CandleData> candleData) {
        if (liveSyncing) {
            candleData.add(candleData.size(), inProgressCandle.snapshot());
        }

        xAxis.setUpperBound(candleData.getLast().getOpenTime());
        xAxis.setLowerBound((candleData.getLast().getOpenTime()) -
                (int) (Math.floor(canvas.getWidth() / candleWidth) * secondsPerCandle));

        currZoomLevel = new ZoomLevel(0, candleWidth, secondsPerCandle, canvas.widthProperty(),
                getXAxisFormatterForRange(xAxis.getUpperBound() - xAxis.getLowerBound()),
                candleData.getFirst().getOpenTime());
        zoomLevelMap.put(0, currZoomLevel);
        xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());
        putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), candleData,
                (int) Math.round(currZoomLevel.getNumVisibleCandles()));
        putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
                candleData.size() - (int) Math.floor(currZoomLevel.getNumVisibleCandles() - (liveSyncing ? 1 : 0)),
                candleData.size()));
        setYAndExtraAxisBounds();
        data.putAll(candleData.stream().collect(Collectors.toMap(CandleData::getOpenTime, Function.identity())));
        drawChart();
        progressIndicator.setVisible(false);
        updateInProgressCandleTask.setReady(true);
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
            int newLowerBoundX = (int) (xAxis.getUpperBound() - ((int) currZoomLevel.getNumVisibleCandles() *
                    secondsPerCandle));
            if (newLowerBoundX < currZoomLevel.getMinXValue()) {
                // We need to try and request more data so that we can properly resize the chart.
                paging = true;
                progressIndicator.setVisible(true);
                CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier()).thenAccept(
                        candleDataPager.getCandleDataPreProcessor()).whenComplete((_, _) -> {
                    currZoomLevel.getExtremaForCandleRangeMap().clear();
                    List<CandleData> candleData = new ArrayList<>(data.values());
                    putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(),
                            candleData, (int) Math.round(currZoomLevel.getNumVisibleCandles()));
                    putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(),
                            candleData.subList(candleData.size() - (int) Math.floor(
                                    currZoomLevel.getNumVisibleCandles()), candleData.size()));
                    Platform.runLater(() -> {
                        xAxis.setLowerBound(newLowerBoundX);
                        setYAndExtraAxisBounds();
                        layoutChart();
                        drawChart();
                        progressIndicator.setVisible(false);
                        paging = false;
                    });
                });
            } else {
                currZoomLevel.getExtremaForCandleRangeMap().clear();
                List<CandleData> candleData = new ArrayList<>(data.values());
                putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(),
                        candleData, (int) Math.round(currZoomLevel.getNumVisibleCandles()));
                putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(),
                        candleData.subList(candleData.size() - (int) Math.floor(
                                currZoomLevel.getNumVisibleCandles()), candleData.size()));
                xAxis.setLowerBound(newLowerBoundX);
                setYAndExtraAxisBounds();
                layoutChart();
                drawChart();
            }
        }
    }

    @Getter
    @Setter
    protected class UpdateInProgressCandleTask extends LiveTradesConsumer implements Runnable {
        private final BlockingQueue<Trade> liveTradesQueue;

        private boolean ready;

        UpdateInProgressCandleTask() {
            liveTradesQueue = new LinkedBlockingQueue<>();
        }


        @Override
        public void accept(Trade trade) {
            liveTradesQueue.add(trade);
        }

        @Override
        public void acceptTrades(@NotNull List<Trade> trades) {
            liveTradesQueue.addAll(trades);
        }

        Label infos = new Label();
        @Override
        public void run() {
            if (!ready) {
                logger.info("No live trades received not updating in-progress candle!");
                return;
            }


            int currentTill = (int) Instant.now().getEpochSecond();
            List<Trade> liveTrades = new ArrayList<>();
            liveTradesQueue.drainTo(liveTrades);



            // Get rid of trades we already know about
            List<Trade> newTrades = liveTrades.stream().filter(trade -> trade.getTimestamp().getEpochSecond() >
                    inProgressCandle.getCurrentTill()).toList();
            currentCandleTrades = newTrades;

            // Partition the trades between the current in-progress candle and the candle after that (which we may
            // have entered after the last update).
            Map<Boolean, List<Trade>> candlePartitionedNewTrades = newTrades.stream().collect(
                    Collectors.partitioningBy(trade -> trade.getTimestamp().getEpochSecond() >=
                            inProgressCandle.getOpenTime() + secondsPerCandle));

            // Update the in-progress candle with new trades partitioned in the in-progress candle's duration
            currentCandleTrades = candlePartitionedNewTrades.get(false);

            if (!currentCandleTrades.isEmpty()) {
                inProgressCandle.setHighPriceSoFar(Math.max(currentCandleTrades.stream().mapToDouble(Trade::getPrice).max().getAsDouble(),
                        inProgressCandle.getHighPriceSoFar()));
                inProgressCandle.setLowPriceSoFar(Math.max(currentCandleTrades.stream().mapToDouble(Trade::getPrice).min().stream().sum(),
                        inProgressCandle.getLowPriceSoFar()));
                inProgressCandle.setVolumeSoFar(inProgressCandle.getVolumeSoFar() +
                        currentCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                inProgressCandle.setCloseTime(currentTill);
                inProgressCandle.setLastPrice(currentCandleTrades.getLast()
                        .getPrice());
                data.put(inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
            }

            graphicsContext.setFill(Color.WHITE);
            List<Trade> nextCandleTrades = candlePartitionedNewTrades.get(true);
            if (Instant.now().getEpochSecond() >= inProgressCandle.getOpenTime() + secondsPerCandle) {
                // Reset in-progress candle
                inProgressCandle.setOpenTime(inProgressCandle.getOpenTime() + secondsPerCandle);
                inProgressCandle.setOpenPrice(inProgressCandle.getLastPrice());

                if (!nextCandleTrades.isEmpty()) {
                    inProgressCandle.setIsPlaceholder(false);
                    inProgressCandle.setHighPriceSoFar(nextCandleTrades.stream().mapToDouble(Trade::getPrice).max().stream().sum());
                    inProgressCandle.setLowPriceSoFar(currentCandleTrades.stream().mapToDouble(Trade::getPrice).min().stream().sum());
                    inProgressCandle.setVolumeSoFar(nextCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                    inProgressCandle.setLastPrice(nextCandleTrades.getFirst().getPrice());
                    inProgressCandle.setCloseTime((int) nextCandleTrades.getFirst().getTimestamp().getEpochSecond());
                } else {
                    inProgressCandle.setIsPlaceholder(true);
                    inProgressCandle.setHighPriceSoFar(inProgressCandle.getLastPrice());
                    inProgressCandle.setLowPriceSoFar(inProgressCandle.getLastPrice());
                    inProgressCandle.setVolumeSoFar(nextCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
                    inProgressCandle.setLastPrice(nextCandleTrades.getFirst().getPrice());
                    inProgressCandle.setCloseTime((int) nextCandleTrades.getFirst().getTimestamp().getEpochSecond());
                }

                data.put(inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
            }
            drawChart();





        }
    }


    private class CandlePageConsumer implements Consumer<List<CandleData>> {
        @Override
        public void accept(List<CandleData> candleData) {


            if (Platform.isFxApplicationThread()) {

                throw new IllegalStateException("candle data paging must not happen on FX thread!");
            }

            if (candleData.isEmpty()) {
                logger.warn("candleData was empty");
                throw new IllegalStateException("candleData was empty");
            }

            if (candleData.getFirst().getOpenTime() >= candleData.get(1).getOpenTime()) {
                logger.error("Paged candle data must be in ascending order by x-value");
                throw new IllegalArgumentException("Paged candle data must be in ascending order by x-value");
            }

            if (data.isEmpty()) {
                if (liveSyncing) {
                    // We got the first page of candle data which does *not* include the current in-progress
                    // candle. Since we are live-syncing, we need to fetch the data for what has occurred so far in
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
                    // catch the data up to within 9 seconds of current time (or in this case, roughly within 0.25% of
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
                                // is too large. (Some exchanges have multiple trades every second.)
                                int currentTill = (int) Instant.now().getEpochSecond();
                                CompletableFuture<List<Trade>> tradesFuture;
                                try {
                                    tradesFuture = exchange.fetchRecentTradesUntil(
                                            tradePair, Instant.ofEpochSecond(inProgressCandleData.openTime()));
                                } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                                    throw new RuntimeException(e);
                                }

                                tradesFuture.whenComplete((trades, exception) -> {
                                    if (exception == null) {
                                        inProgressCandle.setOpenPrice(inProgressCandleData.openPrice());
                                        inProgressCandle.setCloseTime(currentTill);

                                        if (trades.isEmpty()) {
                                            // No trading activity happened in addition to the sub-candles from above.
                                            inProgressCandle.setHighPriceSoFar(
                                                    inProgressCandleData.highPriceSoFar());
                                            inProgressCandle.setLowPriceSoFar(inProgressCandleData.lowPriceSoFar());
                                            inProgressCandle.setVolumeSoFar(inProgressCandleData.volumeSoFar());
                                            inProgressCandle.setLastPrice(inProgressCandleData.closePrice());
                                        } else {
                                            // We need to factor in the trades that have happened after the
                                            // "currentTill" time of the in-progress candle.
                                            inProgressCandle.setHighPriceSoFar(Math.max(trades.stream().mapToDouble(
                                                            Trade::getPrice).max().getAsDouble(),
                                                    inProgressCandleData.highPriceSoFar()));
                                            inProgressCandle.setLowPriceSoFar(Math.max(trades.stream().mapToDouble(
                                                            Trade::getPrice).min().stream().sum(),
                                                    inProgressCandleData.lowPriceSoFar()));
                                            inProgressCandle.setVolumeSoFar(inProgressCandleData.volumeSoFar() +
                                                    trades.stream().mapToDouble(
                                                            Trade::getAmount).sum());
                                            inProgressCandle.setLastPrice(trades.getLast().getPrice());
                                        }
                                        Platform.runLater(() -> setInitialState(candleData));
                                    } else {
                                        logger.error("error fetching recent trades until: %s".formatted(inProgressCandleData), exception);
                                    }
                                });
                            } else {
                                // No trades have happened during the current candle so far.
                                inProgressCandle.setIsPlaceholder(true);
                                inProgressCandle.setVolumeSoFar(0);

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
                int slidingWindowSize = (int) currZoomLevel.getNumVisibleCandles();

                // To compute the y-axis extrema for the new data in the page, we have to include the
                // first numVisibleCandles from the previous page (otherwise the sliding window will not be able
                // to reach all the way).
                Map<Integer, CandleData> extremaData = new TreeMap<>(data.subMap((int) currZoomLevel.getMinXValue(),
                        (int) (currZoomLevel.getMinXValue() + (currZoomLevel.getNumVisibleCandles() *
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

}