//package org.investpro;
//
//import javafx.application.Platform;
//import javafx.beans.property.BooleanProperty;
//import javafx.beans.property.SimpleBooleanProperty;
//import javafx.beans.value.ChangeListener;
//import javafx.beans.value.ObservableNumberValue;
//import javafx.geometry.Insets;
//import javafx.geometry.Pos;
//import javafx.scene.canvas.Canvas;
//import javafx.scene.canvas.GraphicsContext;
//import javafx.scene.control.Alert;
//import javafx.scene.control.Button;
//import javafx.scene.control.Label;
//import javafx.scene.control.ProgressIndicator;
//import javafx.scene.input.ScrollEvent;
//import javafx.scene.layout.StackPane;
//import javafx.scene.layout.VBox;
//import javafx.scene.paint.Color;
//import javafx.scene.layout.Region;
//import javafx.scene.paint.Paint;
//import javafx.scene.shape.Line;
//import javafx.scene.text.Font;
//import javafx.scene.text.Text;
//import javafx.scene.text.TextAlignment;
//import javafx.util.Pair;
//import lombok.Getter;
//import lombok.Setter;
//import org.jetbrains.annotations.Contract;
//import org.jetbrains.annotations.NotNull;
//import weka.core.Attribute;
//import weka.core.DenseInstance;
//import weka.core.Instances;
//
//import java.io.IOException;
//import java.security.InvalidKeyException;
//import java.security.NoSuchAlgorithmException;
//import java.text.DecimalFormat;
//import java.time.Instant;
//import java.time.ZoneId;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.stream.Collectors;
//
//import static javafx.geometry.Side.*;
//import static org.investpro.CandleStickChartUtils.*;
//import static org.investpro.Exchange.logger;
//
//@Getter
//@Setter
//public class CandleStickChart extends Region {
//    private final CandleDataSupplier candleDataSupplier;
//    private final Canvas canvas;
//    private final GraphicsContext gc;
//    private final NavigableMap<Integer, CandleData> data;
//    private final UpdateInProgressCandleTask updateInProgressCandleTask;
//    private final ConcurrentHashMap<Integer, ZoomLevel> zoomLevelMap;
//    private final ProgressIndicator progressIndicator;
//    private final Font canvasNumberFont;
//    private final ScheduledExecutorService updateInProgressCandleExecutor;
//
//    private int candleWidth = 10;
//    private double chartWidth;
//    private double chartHeight;
//    private int secondsPerCandle;
//    private final double zoomFactor = 1.2;
//    Exchange exchange;
//    TradePair tradePair;
//    boolean liveSyncing ;
//    private boolean paging;
//    private StableTicksAxis xAxis;
//    private StableTicksAxis yAxis;
//    private StableTicksAxis extraAxis;
//    Line extraAxisExtension;
//    private ZoomLevel currZoomLevel;
//    private CandleDataPager candleDataPager;
//    private List<Trade> currentCandleTrades;
//    private InProgressCandle inProgressCandle;
//
//
//    public CandleStickChart(
//            Exchange exchange, TradePair tradePair, CandleDataSupplier candleDataSupplier,
//            boolean liveSyncing, int secondsPerCandle, @NotNull ObservableNumberValue containerWidth,
//            @NotNull ObservableNumberValue containerHeight) throws ExecutionException, InterruptedException {
//
//
//        this.candleDataSupplier = candleDataSupplier;
//        this.inProgressCandle = new InProgressCandle();
//
//
//        this.exchange=exchange;
//        this.tradePair=tradePair;
//        this.liveSyncing=liveSyncing;
//
//        this.secondsPerCandle = secondsPerCandle;
//        this.chartWidth = containerWidth.doubleValue();
//        this.chartHeight = containerHeight.doubleValue();
//        this.canvas = new Canvas(chartWidth/2, chartHeight/2);
//
//        this.gc = canvas.getGraphicsContext2D();
//
//        progressIndicator = new ProgressIndicator(-1);
//
//
//        Label inProgressLabel = new Label("Loading..");
//
//        VBox vbox = new VBox(inProgressLabel, progressIndicator);
//
//
//
//
//
//
//        // Bind canvas size to container size
//        canvas.widthProperty().bind(containerWidth);
//        canvas.heightProperty().bind(containerHeight);
//
//
//
//        zoomLevelMap = new ConcurrentHashMap<>();
//        candleDataPager = new CandleDataPager(this, candleDataSupplier);
//        this.data = Collections.synchronizedNavigableMap(new TreeMap<>(Integer::compare));
//
//
//        for (CandleData data1 :candleDataSupplier.get().get()){
//
//            data.put(data.size(),data1);
//        }
//
//        canvasNumberFont = Font.font(FXUtils.getMonospacedFont(), 15);
//
//
//        getStyleClass().add("candle-chart");
//        xAxis =   new StableTicksAxis(0, 1000000);
//        xAxis.setPrefWidth(canvas.getWidth());
//        yAxis = new StableTicksAxis(0, 1000000);
//        yAxis.setPrefHeight(canvas.getHeight());
//        yAxis.setTranslateX(canvas.getWidth());
//        yAxis.setSide(RIGHT);
//        extraAxis = new StableTicksAxis(0, 1000000);
//        xAxis.setAnimated(false);
//        yAxis.setAnimated(false);
//        extraAxis.setAnimated(false);
//        xAxis.setAutoRanging(false);
//        xAxis.setForceZeroInRange(false);
//        yAxis.setAutoRanging(false);
//        xAxis.setForceZeroInRange(false);
//        extraAxis.setAutoRanging(false);
//        xAxis.setSide(BOTTOM);
//        extraAxis.setSide(LEFT);
//        xAxis.setForceZeroInRange(false);
//        yAxis.setForceZeroInRange(false);
//        xAxis.setTickLabelFormatter(InstantAxisFormatter.of(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
//        xAxis.setForceZeroInRange(true);
//        yAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getCounterCurrency()));
//        yAxis.setForceZeroInRange(true);
//
//        extraAxis.setTickLabelFormatter(new MoneyAxisFormatter(tradePair.getBaseCurrency()));
//        Font axisFont = Font.font(FXUtils.getMonospacedFont(), 14);
//        yAxis.setTickLabelFont(axisFont);
//        xAxis.setTickLabelFont(axisFont);
//        extraAxis.setTickLabelFont(axisFont);
//        Text loadingText = new Text("");
//        loadingText.setFill(Color.BLUE);
//
//        VBox loadingIndicatorContainer = new VBox(progressIndicator, loadingText);
//        loadingIndicatorContainer.setPadding(new Insets(20));
//        loadingIndicatorContainer.setAlignment(Pos.CENTER);
//        progressIndicator.setPrefSize(40, 40);
//        loadingIndicatorContainer.setAlignment(Pos.CENTER);
//        loadingIndicatorContainer.setMouseTransparent(true);
//        loadingText.setFont(axisFont);
//        loadingText.setFill(Color.WHITE);
//        loadingText.setMouseTransparent(true);
//        if (progressIndicator.isVisible()) {
//            loadingText.setText("Loading...");
//            loadingText.setStroke(Color.GREEN);
//        }
//
//
//        // We want to extend the extra axis (volume) visually so that it encloses the chart area.
//        extraAxisExtension = new Line();
//
//        Paint lineColor = Color.rgb(95, 195, 195);
//        extraAxisExtension.setFill(lineColor);
//        extraAxisExtension.setStroke(lineColor);
//        extraAxisExtension.setSmooth(false);
//        extraAxisExtension.setStrokeWidth(2);
//
//        extraAxisExtension.setStartX(0);
//        extraAxisExtension.setStartY(0);
//        extraAxisExtension.setEndX(0);
//        extraAxisExtension.setEndY(canvas.getHeight());
//        extraAxisExtension.setStrokeWidth(2);
//
//        StackPane stackPane = new StackPane(xAxis,yAxis,extraAxis,extraAxisExtension,canvas,loadingIndicatorContainer);
//        stackPane.getStyleClass().add("stack-pane");
//        stackPane.setPadding(new Insets(10, 10, 10, 10));
//        stackPane.setAlignment(Pos.CENTER);
//        vbox.setAlignment(Pos.CENTER);
//        stackPane.setPrefSize(chartWidth/2, chartHeight/2);
//
//
//        BooleanProperty gotFirstSize = new SimpleBooleanProperty(false);
//        final ChangeListener<Number> sizeListener = new SizeChangeListener(gotFirstSize, containerWidth,
//                containerHeight);
//        containerWidth.addListener(sizeListener);
//        containerHeight.addListener(sizeListener);
//
//
//        updateInProgressCandleTask = new UpdateInProgressCandleTask();
//        updateInProgressCandleExecutor = Executors.newSingleThreadScheduledExecutor(
//                new LogOnExceptionThreadFactory("UPDATE-CURRENT-CANDLE"));
//
//        addInteractivity();
//        drawChart();
//        gridToggleButton.setOnAction(e -> toggleGrid());
//        canvas.setOnScroll(event -> {
//            double zoomFactor = (event.getDeltaY() > 0) ? 1.1 : 0.9;
//            updateZoom(zoomFactor);
//        });
//
//
//        gridToggleButton.setOnAction(e -> toggleGrid());
//        gridToggleButton.setTranslateX(canvas.getWidth()/2);
//        getChildren().add(gridToggleButton);
//        getChildren().add(canvas);
//
//        //  updateChart(data);
//    }
//
//    private void drawChart() {
//        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
//
//        canvas.getGraphicsContext2D().setStroke(Color.BLACK);
//        canvas.getGraphicsContext2D().setLineWidth(1);
//        canvas.getGraphicsContext2D().fillText(
//                tradePair.toString('/'),10,10
//        );
//        drawGrid();
//        if (!data.isEmpty()) {
//            drawCandles();
//        }
//
//    }
//    Button gridToggleButton = new Button("Toggle Grid");
//
//    private boolean showGrid = true; // Toggle Grid ON/OFF
//    private double zoomLevel = 1.0;  // Default zoom level
//
//    private void drawGrid() {
//        if (!showGrid) return; // If grid is toggled OFF, exit method
//
//        gc.setLineWidth(1);
//
//        // Define colors for major and minor grid lines
//        Color majorGridColor = Color.rgb(70, 70, 70, 0.7);  // Darker for major grid lines
//        Color minorGridColor = Color.rgb(50, 50, 50, 0.3);  // Lighter for minor grid lines
//
//        double width = canvas.getWidth();
//        double height = canvas.getHeight();
//
//        // Dynamically adjust grid spacing based on zoom level
//        int majorSpacing = (int) (100 * zoomLevel);
//        int minorSpacing = (int) (50 * zoomLevel);
//
//        // Ensure minimum spacing to prevent excessive lines
//        majorSpacing = Math.max(majorSpacing, 50);
//        minorSpacing = Math.max(minorSpacing, 25);
//
//        // Draw vertical grid lines + Time labels
//        for (int x = 0; x < width; x += minorSpacing) {
//            gc.setStroke((x % majorSpacing == 0) ? majorGridColor : minorGridColor);
//            gc.strokeLine(x, 0, x, height);
//
//            // Draw time labels for major grid lines
//            if (x % majorSpacing == 0) {
//                String timeLabel = getTimeLabel(x);
//                gc.setFill(Color.WHITE);
//                gc.fillText(timeLabel, x + 5, height - 5);
//            }
//        }
//
//        // Draw horizontal grid lines + Price labels
//        for (int y = 0; y < height; y += minorSpacing) {
//            gc.setStroke((y % majorSpacing == 0) ? majorGridColor : minorGridColor);
//            gc.strokeLine(0, y, width, y);
//
//            // Draw price labels for major grid lines
//            if (y % majorSpacing == 0) {
//                String priceLabel = getPriceLabel(y);
//                gc.setFill(Color.WHITE);
//                gc.fillText(priceLabel, 5, y - 5);
//            }
//        }
//
//    }
//
//    // Helper method to format time labels
//    private String getTimeLabel(double x) {
//        double timestamp = x / zoomLevel; // Adjust based on zoom
//        Instant instant = Instant.ofEpochSecond((long) timestamp);
//        return DateTimeFormatter.ofPattern("HH:mm").format(instant.atZone(ZoneId.systemDefault()));
//    }
//
//    // Helper method to format price labels
//    private String getPriceLabel(double y) {
//
//        yAxis.setUserData(data.values().iterator().next());
//        yAxis.setVisible(true);
//
//        double price = yAxis.getUpperBound() - ((y / canvas.getHeight()) * (yAxis.getUpperBound() - yAxis.getLowerBound()));
//        return new DecimalFormat("#.000").format(price);
//    }
//
//    // Method to toggle grid visibility (MT4-like behavior)
//    private void toggleGrid() {
//        showGrid = !showGrid;
//        drawGrid(); // Redraw chart with updated grid visibility
//    }
//
//    // Method to update zoom level and adjust grid spacing
//    private void updateZoom(double factor) {
//        zoomLevel *= factor;
//        drawGrid(); // Redraw grid with new zoom level
//    }
//
//
//    private void drawCandles() {
//        double maxPrice = data.values().stream().mapToDouble(CandleData::getHighPrice).max().orElse(100);
//        double minPrice = data.values().stream().mapToDouble(CandleData::getLowPrice).min().orElse(0);
//        double priceRange = maxPrice - minPrice;
//        double pixelsPerPriceUnit = priceRange > 0 ? (canvas.getHeight() / priceRange) : 1;
//
//        int index = 0;
//        for (CandleData candle : data.values()) {
//            boolean isBullish = candle.getClosePrice() >= candle.getOpenPrice();
//            Color candleColor = isBullish ? Color.GREEN : Color.RED;
//
//            double openY = (maxPrice - candle.getOpenPrice()) * pixelsPerPriceUnit;
//            double closeY = (maxPrice - candle.getClosePrice()) * pixelsPerPriceUnit;
//            double highY = (maxPrice - candle.getHighPrice()) * pixelsPerPriceUnit;
//            double lowY = (maxPrice - candle.getLowPrice()) * pixelsPerPriceUnit;
//
//
//            canvas.getGraphicsContext2D().setStroke(Color.rgb(255, 255, 255,0.6));
//            double x = canvas.getWidth() - ((data.size() - index) * candleWidth);
//
//            gc.setStroke(candleColor);
//            gc.setLineWidth(2);
//            gc.strokeLine(x + (double) candleWidth / 2, highY, x + (double) candleWidth / 2, lowY);
//
//            gc.setFill(candleColor);
//            gc.fillRect(x, Math.min(openY, closeY), candleWidth - 1, Math.abs(openY - closeY));
//            index++;
//
//
//        }
//    }
//    private void addInteractivity() {
//        canvas.setOnScroll((ScrollEvent event) -> {
//            if (event.getDeltaY() > 0) {
//                candleWidth = (int) Math.min(candleWidth * zoomFactor, 30);
//            } else {
//                candleWidth = (int) Math.max(candleWidth / zoomFactor, 2);
//            }
//            drawChart();
//        });
//    }
//
//    public void updateChart(NavigableMap<Integer, CandleData> newData) {
//        Platform.runLater(() -> {
//            if (newData != null && !newData.isEmpty()) {
//                data.clear();
//                data.putAll(newData);
//                drawChart();
//            }
//        });
//    }
//
//    public @NotNull CandleStickChartOptions getChartOptions() {
//        return new CandleStickChartOptions();}
//    protected void changeZoom(ZoomDirection zoomDirection) {
//        final int multiplier = zoomDirection == ZoomDirection.IN ? -1 : 1;
//
//
//        if (currZoomLevel == null) {
//            logger.error("currZoomLevel was null!");
//            return;
//        }
//        int newCandleWidth = currZoomLevel.getCandleWidth() - multiplier;
//        if (newCandleWidth <= 1) {
//            // Can't go below one pixel for candle width.
//            Alert dat = new Alert(Alert.AlertType.ERROR, " Can't go below one pixel for candle width.");
//
//            dat.showAndWait();
//            return;
//        }
//
//        int newLowerBoundX = (int) (xAxis.getUpperBound() - ((int) (canvas.getWidth() /
//                newCandleWidth) * secondsPerCandle));
//        if (newLowerBoundX > data.lastEntry().getValue().getOpenTime() - (2 * secondsPerCandle)) {
//            // We've reached the end of the data. We can't go back further without having more data.
//            Alert dat = new Alert(Alert.AlertType.ERROR, " Can't go back further without having more data.");
//            dat.showAndWait();
//            return;
//        }
//
//        final int nextZoomLevelId = ZoomLevel.getNextZoomLevelId(currZoomLevel, zoomDirection);
//        double currMinXValue = currZoomLevel.getMinXValue();
//
//        if (!zoomLevelMap.containsKey(nextZoomLevelId)) {
//            // We can use the minXValue of the current zoom level here because, given a sequence of zoom-levels
//            // z(0), z(1), ... z(n) that the chart has gone through, z(x).minXValue <= z(y).minXValue for all x > y.
//            // That is, if we are currently at a max/min zoom-level in zoomLevelMap, there is no other zoom-level that
//            // has a lower minXValue (assuming we did not start at the maximum or minimum zoom level).
//            ZoomLevel newZoomLevel = new ZoomLevel(nextZoomLevelId, newCandleWidth, secondsPerCandle,
//                    canvas.widthProperty(), getXAxisFormatterForRange(xAxis.getUpperBound() - newLowerBoundX),
//                    currMinXValue);
//
//            int numCandlesToSkip = Math.max((((int) xAxis.getUpperBound()) -
//                    data.lastEntry().getValue().getOpenTime()) / secondsPerCandle, 0);
//
//            // If there is less than numVisibleCandles on the screen, we want to be sure and check against what the
//            // lower bound *would be* if we had the full amount. Otherwise, we won't be able to calculate the correct
//            // extrema because the window size will be greater than the number of candles we have data for.
//            if (newLowerBoundX - (numCandlesToSkip * secondsPerCandle) < currZoomLevel.getMinXValue()) {
//                // We need to try and request more data so that we can properly zoom out to this level.
//                paging = true;
//                progressIndicator.setVisible(true);
//                CompletableFuture.supplyAsync(candleDataPager.getCandleDataSupplier()).thenAccept(
//                        candleDataPager.getCandleDataPreProcessor()).whenComplete((_, _) -> {
//                    List<CandleData> candleData = new ArrayList<>(data.values());
//                    putSlidingWindowExtrema(newZoomLevel.getExtremaForCandleRangeMap(),
//                            candleData, (int) newZoomLevel.getNumVisibleCandles());
//                    putExtremaForRemainingElements(newZoomLevel.getExtremaForCandleRangeMap(),
//                            candleData.subList(candleData.size() - (int) Math.floor(
//                                    newZoomLevel.getNumVisibleCandles()), candleData.size()));
//                    zoomLevelMap.put(nextZoomLevelId, newZoomLevel);
//                    currZoomLevel = newZoomLevel;
//                    Platform.runLater(() -> {
//                        xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());
//                        candleWidth = currZoomLevel.getCandleWidth();
//                        xAxis.setLowerBound(newLowerBoundX);
//                        setYAndExtraAxisBounds();
//                        drawChart();
//                        progressIndicator.setVisible(false);
//                        paging = false;
//                    });
//                });
//                return;
//            } else {
//                List<CandleData> candleData = new ArrayList<>(data.values());
//                putSlidingWindowExtrema(newZoomLevel.getExtremaForCandleRangeMap(),
//                        candleData, (int) newZoomLevel.getNumVisibleCandles());
//                putExtremaForRemainingElements(newZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
//                        candleData.size() - (int) Math.floor(newZoomLevel.getNumVisibleCandles()),
//                        candleData.size()));
//                zoomLevelMap.put(nextZoomLevelId, newZoomLevel);
//                currZoomLevel = newZoomLevel;
//            }
//        } else {
//            //  In this case, we only need to compute the extrema for any new live syncing data that has
//            //  happened since the last time we were at this zoom level.
//            currZoomLevel = zoomLevelMap.get(nextZoomLevelId);
//            List<CandleData> candleData = new ArrayList<>(data.values());
//            putSlidingWindowExtrema(currZoomLevel.getExtremaForCandleRangeMap(), candleData,
//                    (int) currZoomLevel.getNumVisibleCandles());
//            putExtremaForRemainingElements(currZoomLevel.getExtremaForCandleRangeMap(), candleData.subList(
//                    candleData.size() - (int) Math.floor(currZoomLevel.getNumVisibleCandles()), candleData.size()));
//            xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());
//            candleWidth = currZoomLevel.getCandleWidth();
//            xAxis.setLowerBound(newLowerBoundX);
//        }
//
//        xAxis.setTickLabelFormatter(currZoomLevel.getXAxisFormatter());
//        candleWidth = currZoomLevel.getCandleWidth();
//        xAxis.setLowerBound(newLowerBoundX);
//        setYAndExtraAxisBounds();
//
//
//        drawChart();
//    }
//
//    public static void putExtremaForRemainingElements(
//            Map<Integer, Pair<Extrema, Extrema>> extrema,
//            final List<CandleData> candleData) {
//
//        Objects.requireNonNull(extrema, "Extrema map must not be null");
//        Objects.requireNonNull(candleData, "CandleData list must not be null");
//
//        if (candleData.isEmpty()) {
//            throw new IllegalArgumentException("CandleData must not be empty");
//        }
//
//        // Initialize min/max values for price and volume
//        double minVolume = Double.MAX_VALUE;
//        double maxVolume = Double.MIN_VALUE;
//        double minPrice = Double.MAX_VALUE;
//        double maxPrice = Double.MIN_VALUE;
//
//        // Iterate from the last element to the first (Reverse Order)
//        for (int i = candleData.size() - 1; i >= 0; i--) {
//            CandleData candle = candleData.get(i);
//
//            // Update extrema for volume
//            minVolume = Math.min(candle.getVolume(), minVolume);
//            maxVolume = Math.max(candle.getVolume(), maxVolume);
//
//            // Update extrema for price (Low & High)
//            minPrice = Math.min(candle.getLowPrice(), minPrice);
//            maxPrice = Math.max(candle.getHighPrice(), maxPrice);
//
//            // Store extrema for this candle's open time
//            extrema.put(
//                    candle.getOpenTime(),
//                    new Pair<>(
//                            new Extrema(minVolume, maxVolume),  // Volume Extrema
//                            new Extrema(minPrice, maxPrice)    // Price Extrema
//                    )
//            );
//        }
//    }
//
//
//    /**
//     * Sets the y-axis and extra axis bounds using only the x-axis lower bound.
//     */
//    private void setYAndExtraAxisBounds() {
//        logger.info("xAxis lower bound:%d".formatted((int) xAxis.getLowerBound()));
//        final double idealBufferSpaceMultiplier = 0.35;
//        if (!currZoomLevel.getExtremaForCandleRangeMap().containsKey((int) xAxis.getLowerBound())) {
//            //  Does this *always* represent a coding error on our end, or can this happen during
//            // normal chart functioning, and could we handle it more gracefully?
//            logger.error("The extrema map did not contain extrema for x-value: %d".formatted((int) xAxis.getLowerBound()));
//            logger.error("extrema map: %s".formatted(new TreeMap<>(currZoomLevel.getExtremaForCandleRangeMap())));
//        }
//
//        // The y-axis and extra axis extrema are obtained using a key offset by minus one candle duration. This makes
//        // the chart work correctly. I don't fully understand the logic behind it, so I am leaving a note for
//        // my future self.
//        Pair<Extrema, Extrema> extremaForRange = currZoomLevel.getExtremaForCandleRangeMap().get(
//                (int) xAxis.getLowerBound() - secondsPerCandle);
//        if (extremaForRange == null) {
//            logger.error("extremaForRange was null!%d".formatted(secondsPerCandle));
//
//            return;
//
//        }
//        Double yAxisMax = extremaForRange.getValue().getMax();
//        Double yAxisMin = extremaForRange.getValue().getMin();
//        double yAxisDelta = yAxisMax - yAxisMin;
//        yAxis.setUpperBound(yAxisMax + (yAxisDelta * idealBufferSpaceMultiplier));
//        yAxis.setLowerBound(Math.max(0, yAxisMin - (yAxisDelta * idealBufferSpaceMultiplier)));
//
//        extraAxis.setUpperBound(currZoomLevel.getExtremaForCandleRangeMap().get(
//                (int) xAxis.getLowerBound() - secondsPerCandle).getKey().getMax());
//    }
//
//
//
//    private double cartesianToScreenCoords(double yCoordinate) {
//        return -yCoordinate + canvas.getHeight();
//    }
//
//    public CandleDataPager getCandlePageConsumer() {
//
//        return null;// new CandleDataPager(this, candleDataSupplier);
//    }
//
//    private class UpdateInProgressCandleTask extends LiveTradesConsumer implements Runnable {
//        private final BlockingQueue<Trade> liveTradesQueue;
//        @Setter
//        private boolean ready;
//
//        UpdateInProgressCandleTask() {
//            liveTradesQueue = new LinkedBlockingQueue<>();
//        }
//
//
//
//
//
//
//
//        @Override
//        public void accept(Trade trade) {
//            liveTradesQueue.add(trade);
//        }
//
//        @Override
//        public void acceptTrades(@NotNull List<Trade> trades) {
//            liveTradesQueue.addAll(trades);
//        }
//
//        Label infos = new Label();
//
//        @Contract(pure = true)
//        private @NotNull String getTimeframe(int secondsPerCandle) {
//            return switch (secondsPerCandle) {
//                case 60 -> "1m";
//                case 300 -> "5m";
//                case 900 -> "15m";
//                case 3600 -> "1h";
//                case 21600 -> "6h";
//                case 86400 -> "1d";
//                case 604800 -> "1w";
//                case 604800*2->"2W";
//                case 604800*3->"3W";
//
//                case 604800*4->"1mo";
//
//                case 31536000 -> "1y";
//                default -> throw new IllegalStateException("Unsupported timeframe: " + secondsPerCandle);
//            };
//        }
//
//        @Override
//        public void run() {
//            if (!ready) {
//                logger.info("No live trades received not updating in-progress candle!");
//                return;
//            }
//
//
//            int currentTill = (int) Instant.now().getEpochSecond();
//            List<Trade> liveTrades = new ArrayList<>();
//            liveTradesQueue.drainTo(liveTrades);
//
//
//
//            // Get rid of trades we already know about
//            List<Trade> newTrades = liveTrades.stream().filter(trade -> trade.getTimestamp().getEpochSecond() >
//                    inProgressCandle.getCurrentTill()).toList();
//
//            // Partition the trades between the current in-progress candle and the candle after that (which we may
//            // have entered after the last update).
//            Map<Boolean, List<Trade>> candlePartitionedNewTrades = newTrades.stream().collect(
//                    Collectors.partitioningBy(trade -> trade.getTimestamp().getEpochSecond() >=
//                            inProgressCandle.getOpenTime() + secondsPerCandle));
//
//            // Update the in-progress candle with new trades partitioned in the in-progress candle's duration
//            currentCandleTrades = candlePartitionedNewTrades.get(false);
//
//            if (!currentCandleTrades.isEmpty()) {
//                inProgressCandle.setHighPriceSoFar(Math.max(currentCandleTrades.stream().mapToDouble(Trade::getPrice).max().getAsDouble(),
//                        inProgressCandle.getHighPriceSoFar()));
//                inProgressCandle.setLowPriceSoFar(Math.max(currentCandleTrades.stream().mapToDouble(Trade::getPrice).min().stream().sum(),
//                        inProgressCandle.getLowPriceSoFar()));
//                inProgressCandle.setVolumeSoFar(inProgressCandle.getVolumeSoFar() +
//                        currentCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
//                inProgressCandle.setCloseTime(currentTill);
//                inProgressCandle.setLastPrice(currentCandleTrades.getLast()
//                        .getPrice());
//                data.put(inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
//            }
//
//            gc.setFill(Color.WHITE);
//            List<Trade> nextCandleTrades = candlePartitionedNewTrades.get(true);
//            if (Instant.now().getEpochSecond() >= inProgressCandle.getOpenTime() + secondsPerCandle) {
//                // Reset in-progress candle
//                inProgressCandle.setOpenTime(inProgressCandle.getOpenTime() + secondsPerCandle);
//                inProgressCandle.setOpenPrice(inProgressCandle.getLastPrice());
//
//                if (!nextCandleTrades.isEmpty()) {
//                    inProgressCandle.setIsPlaceholder(false);
//                    inProgressCandle.setHighPriceSoFar(nextCandleTrades.stream().mapToDouble(Trade::getPrice).max().stream().sum());
//                    inProgressCandle.setLowPriceSoFar(currentCandleTrades.stream().mapToDouble(Trade::getPrice).min().stream().sum());
//                    inProgressCandle.setVolumeSoFar(nextCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
//                    inProgressCandle.setLastPrice(nextCandleTrades.getFirst().getPrice());
//                    inProgressCandle.setCloseTime((int) nextCandleTrades.getFirst().getTimestamp().getEpochSecond());
//                } else {
//                    inProgressCandle.setIsPlaceholder(true);
//                    inProgressCandle.setHighPriceSoFar(inProgressCandle.getLastPrice());
//                    inProgressCandle.setLowPriceSoFar(inProgressCandle.getLastPrice());
//                    inProgressCandle.setVolumeSoFar(nextCandleTrades.stream().mapToDouble(Trade::getAmount).sum());
//                    inProgressCandle.setLastPrice(nextCandleTrades.getFirst().getPrice());
//                    inProgressCandle.setCloseTime((int) nextCandleTrades.getFirst().getTimestamp().getEpochSecond());
//                }
//
//                data.put(inProgressCandle.getOpenTime(), inProgressCandle.snapshot());
//            }
//
//            gc.setFill(Color.WHITE);
//            gc.setTextAlign(TextAlignment.CENTER);
//            gc.fillText("Time :%s".formatted(Date.from(Instant.ofEpochSecond(data.lastEntry().getValue().getOpenTime()))), canvas.getWidth() / 6, canvas.getHeight() / 7);
//            gc.fillText("TimeFrame  :%s".formatted(getTimeframe(secondsPerCandle)), 120, 20);
//            gc.fillText("O :%s".formatted(data.lastEntry().getValue().getOpenPrice()), 320, 20);
//            gc.fillText("H :%s".formatted(data.lastEntry().getValue().getHighPrice()), 420, 20);
//            gc.fillText("L :%s".formatted(data.lastEntry().getValue().getLowPrice()), 520, 20);
//            gc.fillText("C :%s".formatted(data.lastEntry().getValue().getClosePrice()), 820, 20);
//            gc.fillText("V :%s".formatted(data.lastEntry().getValue().getVolume()), 1020, 20);
//
//            gc.setFill(Color.WHITE);
//
//            drawChart();
//            //Execute AI Training and Trade Analysis
//
//
//            // Set up the attributes for the Instances (Open, High, Low, Close, Volume)
//            ArrayList<Attribute> attributes = new ArrayList<>();
//            attributes.add(new Attribute("open"));
//            attributes.add(new Attribute("high"));
//            attributes.add(new Attribute("low"));
//            attributes.add(new Attribute("close"));
//            attributes.add(new Attribute("volume"));
//
//            ArrayList<String> classValues = new ArrayList<>();
//            classValues.add("BUY");
//            classValues.add("SELL");
//            classValues.add("HOLD");
//            attributes.add(new Attribute("class", classValues));
//
//            // Create an empty dataset with these attributes
//            Instances trainingData = new Instances("MarketData", attributes, 0);
//            trainingData.setClassIndex(Math.max((trainingData.numAttributes() - 1), 0));
//
//            TradingAI tradingAI = new TradingAI(trainingData);
//            TradeHistory tradeHistory = new TradeHistory();
//
//            for (int i = 0; i < 100; i++) {
//                DenseInstance instance = new DenseInstance(attributes.size());
//                instance.setValue(attributes.get(0), data.lastEntry().getValue().getOpenPrice()); // open
//                instance.setValue(attributes.get(1), data.lastEntry().getValue().getHighPrice()); // high
//                instance.setValue(attributes.get(2), data.lastEntry().getValue().getLowPrice());  // low
//                instance.setValue(attributes.get(3), data.lastEntry().getValue().getClosePrice()); // close
//                instance.setValue(attributes.get(4), data.lastEntry().getValue().getVolume());      // volume
//
//
//                // class: BUY, SELL, HOLD
//
//                trainingData.add(instance);
//
//                logger.info(
//                        "trainingData {}", trainingData
//                );
//
//                SIGNAL signal = tradingAI.getSignal(
//                        data.lastEntry().getValue().getOpenPrice(),
//                        data.lastEntry().getValue().getHighPrice(),
//                        data.lastEntry().getValue().getLowPrice(),
//                        data.lastEntry().getValue().getClosePrice(),
//                        data.lastEntry().getValue().getVolume()); // signal: BUY, SELL, HOLD
//
//
//                if (signal != null) {
//                    double price = 0;
//                    double size = 0.01;
//                    switch (signal) {
//                        case BUY:
//                            try {
//
//                                infos.setText(
//                                        "SIGNAL:BUY"
//                                );
//                                exchange.createOrder(tradePair, org.investpro.Side.BUY,
//                                        ENUM_ORDER_TYPE.STOP_LOSS, price, size, new Date(), 100, 100);
//                            } catch (IOException | NoSuchAlgorithmException | InterruptedException |
//                                     InvalidKeyException | ExecutionException e) {
//                                throw new RuntimeException(e);
//                            }
//                            break;
//                        case SELL:
//                            infos.setText(
//                                    "SIGNAL:SELL"
//                            );
//                            try {
//                                exchange.createOrder(tradePair, org.investpro.Side.SELL,
//                                        ENUM_ORDER_TYPE.STOP_LOSS, price, size, new Date(), 100, 100);
//                            } catch (IOException | NoSuchAlgorithmException | InterruptedException |
//                                     InvalidKeyException | ExecutionException e) {
//                                throw new RuntimeException(e);
//                            }
//                            break;
//                        case HOLD:
//
//
//                            infos.setText("Signal:HOLD");
//
//
//                    }
//
//                    List<Trade> tr = tradeHistory.getTradesByPair(tradePair);
//                    logger.info(
//                            "TradesHistory {}", tr
//                    );
//                    getChildren().add(infos);
//                }
//
//
//            }
//        }
//    }
//}