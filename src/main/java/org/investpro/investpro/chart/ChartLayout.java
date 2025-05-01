package org.investpro.investpro.chart;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.*;
import org.investpro.investpro.ai.*;
import org.investpro.investpro.model.Candle;
import org.investpro.investpro.model.TradePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class ChartLayout extends StackPane {

    private final CandleStickChart chart;
    private final Group drawingLayer;
    private CandleStickChart candleStickChart;
    private Button exportButton;

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


    public ChartLayout(Exchange exchange, TradePair tradePair, CandleDataSupplier candleDataSupplier, boolean liveSyncing, int secondsPerCandle, ReadOnlyDoubleProperty widthProperty, ReadOnlyDoubleProperty heightProperty, TelegramClient telegramClient) {

        this.exchange = exchange;
        this.drawingLayer = new Group();

        this.dataLoader = new CandleChartDataLoader(exchange);
        this.themeManager = new ThemeManager(new Scene(this));
        this.liveUpdateManager = new LiveUpdateManager(
                () -> dataLoader.fetchLatestCandle(currentSymbol, currentInterval),
                this::addLiveCandle
        );

        this.optimizer = new ChartPerformanceOptimizer(getCandleStickChart());
        optimizer.setMaxCandles(500);

        liveUpdateManager.start(60_000);

        StableTicksAxis xAxis = new StableTicksAxis();
        StableTicksAxis yAxis = new StableTicksAxis();
        chart = new CandleStickChart(xAxis, yAxis);
        Pane container = new Pane(chart);

        this.indicatorManager = new IndicatorManager(chart);
        this.navigator = new CandleChartNavigator(chart, container);

        this.paginationManager = new PaginationManager(
                chart,
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
        HBox topRow = new HBox(chartToolbar, chartSettingsPanel);
        layout.getChildren().addAll(topRow, this);

        indicatorManager.addSimpleMovingAverage(loadedCandles, 20);
        indicatorManager.addRSI(loadedCandles, 14);
        this.alertsManager = new ChartAlertsManager(chart, drawingLayer, this::getLatestPrice);
        alertsManager.startChecking();
        alertsManager.addAlertLine(30000.0);
        aiManager = new CandleChartAIManager(chart, loadedCandles);
        aiManager.analyzeCandles();

        List<Double> features = InvestProFeatureExtractor.extractFeatures(loadedCandles);
        InvestProAIPredictor predictorClient = new InvestProAIPredictor();
        InvestProAIPredictor.PredictionResult result = predictorClient.predict(features);

        System.out.println("Prediction: " + result.prediction());
        System.out.println("Confidence: " + result.confidence());


        LineChart<Number, Number> equityCurveChart = new LineChart<>(xAxis, yAxis);
        InvestProAIPaperTradingBot bot = new InvestProAIPaperTradingBot(equityCurveChart);

        List<Candle> recentCandles = loadedCandles;
        bot.onNewCandle(recentCandles);
        bot.onCloseCandle(lastCandle);

        bot.printSummary();
        initializeChart();
    }


    private void initializeChart() {
        // Configure axes, style, and base series
    }

    private @NotNull Double getLatestPrice() {
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

    public void loadInitialCandles(String symbol, Instant startTime, Instant endTime, String interval) {
        List<Candle> candles = dataLoader.getCandleData(symbol, startTime, endTime, interval);
        populateCandles(candles);
    }

    private void addLiveCandle(Candle candle) {
        if (candle == null) return;

        XYChart.Series<String, Number> series;
        if (chart.getData().isEmpty()) {
            series = new XYChart.Series<>();
            chart.getData().add(series);
        } else {
            series = chart.getData().get(0);
        }

        XYChart.Data<String, Number> data = createCandleData(candle);
        series.getData().add(data);
        optimizer.optimize();
    }

    public void loadMoreCandles(String symbol, Instant olderThan, String interval, int limit) {
        List<Candle> candles = dataLoader.loadMoreCandles(symbol, olderThan, interval, limit);
        appendCandles(candles);
    }

    private void populateCandles(List<Candle> candles) {
        chart.getData().clear();
        if (candles == null || candles.isEmpty()) return;

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Candle candle : candles) {
            XYChart.Data<String, Number> data = createCandleData(candle);
            series.getData().add(data);
        }
        chart.getData().add(series);
    }

    private @NotNull XYChart.Data<String, Number> createCandleData(@NotNull Candle candle) {
        double currentScale = getScaleX();
        double adjustedCandleWidth = baseCandleWidth / currentScale;

        XYChart.Data<String, Number> data = new XYChart.Data<>(candle.getTime().toString(), candle.getClose());
        data.setNode(new CandleNode(candle, adjustedCandleWidth));
        return data;
    }

    private void appendCandles(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;

        XYChart.Series<String, Number> series;
        if (chart.getData().isEmpty()) {
            series = new XYChart.Series<>();
            chart.getData().add(series);
        } else {
            series = chart.getData().get(0);
        }

        for (Candle candle : candles) {
            XYChart.Data<String, Number> data = createCandleData(candle);
            series.getData().add(data);
        }
    }

    private void changeCandleWidth(double width) {
        this.baseCandleWidth = width;
        refreshChart();
    }

    private void toggleIndicators() {
        // Add logic to toggle visibility of indicators like RSI or MA
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

    public @NotNull CandleStickChartOptions getChartOptions() {
        return new CandleStickChartOptions();
    }

    public void changeZoom(@Nullable ZoomDirection zoomDirection) {
    }

    public void setFullScreen(boolean b) {
    }

    public void exportAsPDF() {
    }

    public void print() {
    }

    public void shareLink() {
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

    public void setTooltip(Tooltip tooltip) {
    }
}

