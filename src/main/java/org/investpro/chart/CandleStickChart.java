package org.investpro.chart;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.investpro.CandleChartAIManager;
import org.investpro.*;
import org.investpro.ai.InvestProAIPaperTradingBot;
import org.investpro.ai.InvestProAIPredictor;
import org.investpro.ai.InvestProFeatureExtractor;
import org.investpro.model.Candle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class CandleStickChart extends XYChart<String, Number> {
    private Pane drawingLayer;
    private ChartSettingsPanel chartSettingsPanel;
    private List<Candle> loadedCandles;
    private String currentInterval;
    private String currentSymbol;
    private PaginationManager paginationManager;
    private IndicatorManager indicatorManager;

    private Exchange exchange;
    private CandleChartDataLoader dataLoader;
    private CandleChartNavigator navigator;
    private double baseCandleWidth = 5.0;
    private LiveUpdateManager liveUpdateManager;
    private ChartToolbar chartToolbar;
    private Instant currentStartTime;
    private Instant currentEndTime;
    private ChartPerformanceOptimizer optimizer;
    private ThemeManager themeManager;
    private ChartAlertsManager alertsManager;
    private Candle lastCandle;
    private CandleChartAIManager aiManager;

    public CandleStickChart(Exchange exchange) {
        super(new DefaultXAxis(), new DefaultYAxis());

        this.exchange = exchange;
        this.drawingLayer = new Pane();

        this.dataLoader = new CandleChartDataLoader(exchange);
        themeManager = new ThemeManager(new Scene(this));
        this.liveUpdateManager = new LiveUpdateManager(
                () -> dataLoader.fetchLatestCandle(currentSymbol, currentInterval),
                this::addLiveCandle
        );
        this.indicatorManager = new IndicatorManager(this);

        this.optimizer = new ChartPerformanceOptimizer(this);
        optimizer.setMaxCandles(500); // keep only 500 candles max

        liveUpdateManager.start(60_000); // every 60 seconds for "1m" candles

        Pane container = new Pane(this); // wrap chart inside Pane
        this.navigator = new CandleChartNavigator(this, container);
        this.paginationManager = new PaginationManager(
                this,
                (oldestTime, limit) -> dataLoader.loadMoreCandles(currentSymbol, oldestTime, currentInterval, limit),
                this::appendCandles
        );


        this.chartToolbar = new ChartToolbar(
                this::resetZoom,
                this::refreshChart,
                this::changeInterval
        );
        this.chartSettingsPanel = new ChartSettingsPanel(
                this::changeCandleWidth,
                optimizer::setMaxCandles,
                themeManager::toggleTheme,
                this::toggleIndicators,
                this::resetAllSettings
        );

        VBox layout = new VBox();
        HBox topRow = new HBox();
        topRow.getChildren().addAll(chartToolbar, chartSettingsPanel); // side by side
        layout.getChildren().addAll(topRow, this); // toolbar + settings at top

        indicatorManager.addSimpleMovingAverage(loadedCandles, 20); // 20-period SMA
        indicatorManager.addRSI(loadedCandles, 14); // 14-period RSI
        this.alertsManager = new ChartAlertsManager(this, drawingLayer, this::getLatestPrice);
        alertsManager.startChecking();
        alertsManager.addAlertLine(30000.0); // example: set alert at price 30,000
        aiManager = new CandleChartAIManager(this, loadedCandles);
        aiManager.analyzeCandles();
        List<Double> features = InvestProFeatureExtractor.extractFeatures(loadedCandles);
        InvestProAIPredictor predictorClient = new InvestProAIPredictor();

        InvestProAIPredictor.PredictionResult result = predictorClient.predict(features);

        System.out.println("Prediction: " + result.prediction());
        System.out.println("Confidence: " + result.confidence());

        initializeChart();
        // Create equity curve chart
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        LineChart<Number, Number> equityCurveChart = new LineChart<>(xAxis, yAxis);

// Create bot
        InvestProAIPaperTradingBot bot = new InvestProAIPaperTradingBot(equityCurveChart);

// Feed it candles
        List<Candle> recentCandles = loadedCandles;
        bot.onNewCandle(recentCandles);
        bot.onCloseCandle(
                lastCandle
        );

        bot.printSummary();

    }

    private @NotNull Double getLatestPrice() {
        // Could be your latest real-time candle close price
        return lastCandle != null ? lastCandle.getClose().doubleValue() : 0.0;
    }

    private void resetZoom() {
        setScaleX(1.0);
        setScaleY(1.0);
        setTranslateX(0);
        setTranslateY(0);
    }

    public void refreshChart() {
        loadInitialCandles(currentSymbol, currentStartTime, currentEndTime, currentInterval);
    }

    private void changeInterval(String newInterval) {
        this.currentInterval = newInterval;
        refreshChart();
    }

    private void initializeChart() {
        // Initialize axes, styles, etc.
    }

    public void loadInitialCandles(String symbol, Instant startTime, Instant endTime, String interval) {
        List<Candle> candles = dataLoader.getCandleData(symbol, startTime, endTime, interval);
        populateCandles(candles);
    }

    private void addLiveCandle(Candle candle) {
        if (candle == null) return;

        Series<String, Number> series;
        if (getData().isEmpty()) {
            series = new Series<>();
            getData().add(series);
        } else {
            series = getData().getFirst();
        }

        Data<String, Number> data = createCandleData(candle);
        series.getData().add(data);

        optimizer.optimize(); // ✨ Optimize after adding!
    }


    public void loadMoreCandles(String symbol, Instant olderThan, String interval, int limit) {
        List<Candle> candles = dataLoader.loadMoreCandles(symbol, olderThan, interval, limit);
        appendCandles(candles);
    }

    private void populateCandles(List<Candle> candles) {
        getData().clear();
        if (candles == null || candles.isEmpty()) return;

        Series<String, Number> series = new Series<>();
        for (Candle candle : candles) {
            Data<String, Number> data = createCandleData(candle);
            series.getData().add(data);
        }
        getData().add(series);
    }

    private @NotNull Data<String, Number> createCandleData(@NotNull Candle candle) {
        double currentScale = getScaleX(); // dynamic zoom factor
        double adjustedCandleWidth = baseCandleWidth / currentScale;

        Data<String, Number> data = new Data<>(candle.getTime().toString(), candle.getClose());
        data.setNode(new CandleNode(candle, adjustedCandleWidth));
        return data;
    }

    private void appendCandles(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;

        Series<String, Number> series;
        if (getData().isEmpty()) {
            series = new Series<>();
            getData().add(series);
        } else {
            series = getData().get(0); // Assume single series
        }

        for (Candle candle : candles) {
            Data<String, Number> data = createCandleData(candle);
            series.getData().add(data);
        }
    }


    private void changeCandleWidth(double width) {
        this.baseCandleWidth = width;
        refreshChart();
    }

    private void toggleIndicators() {
        // Could show/hide MA, RSI dynamically
    }

    private void resetAllSettings() {
        setScaleX(1.0);
        setScaleY(1.0);
        setTranslateX(0);
        setTranslateY(0);
        optimizer.setMaxCandles(500);
        this.baseCandleWidth = 5.0;
        themeManager.applyLightTheme();
        refreshChart();
    }

    @Override
    protected void dataItemAdded(Series<String, Number> series, int itemIndex, Data<String, Number> item) {

    }

    @Override
    protected void dataItemRemoved(Data<String, Number> item, Series<String, Number> series) {

    }

    @Override
    protected void dataItemChanged(Data<String, Number> item) {

    }

    @Override
    protected void seriesAdded(Series<String, Number> series, int seriesIndex) {

    }

    @Override
    protected void seriesRemoved(Series<String, Number> series) {

    }

    @Override
    protected void layoutPlotChildren() {

    }

    @Override
    public Node getStyleableNode() {
        return super.getStyleableNode();
    }

    public void changeZoom(@Nullable ZoomDirection zoomDirection) {
    }

    public void setFullScreen(boolean b) {
    }

    public void exportAsPDF() {
    }

    public void changeNavigation(NavigationDirection navigationDirection) {
    }

    public void scroll(NavigationDirection navigationDirection) {
    }

    public void captureScreenshot() {
    }

    public boolean isShowGrid() {
        return true;
    }

    public void setShowGrid(boolean b) {
    }

    public boolean isAutoScroll() {
        return true;
    }

    public void setAutoScroll(boolean b) {
    }

    public void shareLink() {
    }

    public void print() {
    }

    public void setTooltip(Tooltip tooltip) {
    }

    public @NotNull CandleStickChartOptions getChartOptions() {

        return new CandleStickChartOptions();
    }
}
