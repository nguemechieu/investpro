package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.i18n.LocalizationService;
import org.investpro.strategy.StrategyCatalog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Strategy Analysis Panel.
 *
 * This panel is now data-wired around AnalysisSnapshot.
 *
 * It supports:
 * - Real strategy selection from StrategyCatalog
 * - Real symbol/pair selection from the active exchange
 * - Real metric selection
 * - Performance metrics
 * - Risk metrics
 * - Drawdown chart
 * - Equity curve chart
 * - Return distribution chart
 * - Correlation matrix
 *
 * Data source:
 * The panel first tries to read analysis data from SystemCore using reflection.
 * This keeps the UI compile-safe while your backend analysis service is still evolving.
 *
 * Recommended future service shape:
 *
 * systemCore.getStrategyAnalysisService().analyze(strategyName, pairSymbol)
 *
 * The returned object may expose getters or fields such as:
 * - getSharpeRatio()
 * - getSortinoRatio()
 * - getCalmarRatio()
 * - getProfitFactor()
 * - getRecoveryFactor()
 * - getPerformanceScore()
 * - getMaxDrawdown()
 * - getAnnualVolatility()
 * - getValueAtRisk95()
 * - getConditionalValueAtRisk()
 * - getMaxConsecutiveLosses()
 * - getRecoveryDays()
 * - getEquityCurve()
 * - getDrawdownSeries()
 * - getReturnBuckets()
 * - getCorrelations()
 */
@Slf4j
@Getter
@Setter
public class AnalysisPanel extends VBox {

    private final SystemCore systemCore;
    private final AnalysisDataProvider dataProvider;

    private ComboBox<String> strategyCombo;
    private ComboBox<String> pairCombo;
    private ComboBox<String> metricCombo;

    private Label statusLabel;
    private Label performanceScoreLabel;
    private Label metricsSummaryLabel;

    private Label sharpeValueLabel;
    private Label sortinoValueLabel;
    private Label calmarValueLabel;
    private Label profitFactorValueLabel;
    private Label recoveryFactorValueLabel;

    private Label maxDrawdownValueLabel;
    private Label volatilityValueLabel;
    private Label valueAtRiskValueLabel;
    private Label conditionalValueAtRiskValueLabel;
    private Label maxConsecutiveLossValueLabel;
    private Label recoveryTimeValueLabel;

    private BarChart<String, Number> performanceChart;
    private LineChart<Number, Number> drawdownChart;
    private LineChart<Number, Number> equityCurveChart;
    private BarChart<String, Number> distributionChart;

    private GridPane correlationGrid;

    private AnalysisSnapshot currentSnapshot = AnalysisSnapshot.empty();

    public AnalysisPanel(@NotNull SystemCore systemCore) {
        this.systemCore = Objects.requireNonNull(systemCore, "systemCore must not be null");
        this.dataProvider = new AnalysisDataProvider(systemCore);

        setPadding(new Insets(16));
        setSpacing(12);
        setStyle("-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff;");
        getStyleClass().add("analysis-panel");

        setupUI();
        loadInitialData();

        LocalizationService.applyTranslations(this);
    }

    private void setupUI() {
        Label titleLabel = new Label("Strategy Analysis");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #94a3b8;");

        HBox header = new HBox(12, titleLabel, createSpacer(), statusLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox configBox = createConfigurationBar();

        TabPane analysisTabPane = createAnalysisTabs();

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");
        content.getChildren().add(analysisTabPane);

        scrollPane.setContent(content);

        getChildren().addAll(header, configBox, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    private HBox createConfigurationBar() {
        HBox configBox = new HBox(12);
        configBox.setPadding(new Insets(10));
        configBox.setStyle(
                "-fx-background-color: #16213e;" +
                        "-fx-border-color: #3b82f6;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;"
        );
        configBox.setAlignment(Pos.CENTER_LEFT);

        Label strategyLabel = createMutedLabel("Strategy:");
        strategyCombo = new ComboBox<>();
        strategyCombo.setPrefWidth(220);
        strategyCombo.setPromptText("Select strategy");
        strategyCombo.setOnAction(e -> updateAnalysis());

        Label pairLabel = createMutedLabel("Pair:");
        pairCombo = new ComboBox<>();
        pairCombo.setPrefWidth(180);
        pairCombo.setPromptText("Select pair");
        pairCombo.setOnAction(e -> updateAnalysis());

        Label metricLabel = createMutedLabel("Metric:");
        metricCombo = new ComboBox<>();
        metricCombo.setPrefWidth(200);
        metricCombo.getItems().addAll(
                "Performance Score",
                "Sharpe Ratio",
                "Sortino Ratio",
                "Calmar Ratio",
                "Profit Factor",
                "Recovery Factor",
                "Max Drawdown",
                "Volatility",
                "Value at Risk"
        );
        metricCombo.getSelectionModel().selectFirst();
        metricCombo.setOnAction(e -> updateAnalysis());

        Button analyzeBtn = new Button("🔍 Analyze");
        analyzeBtn.setStyle(
                "-fx-padding: 8px 20px;" +
                        "-fx-font-size: 12px;" +
                        "-fx-background-color: #10b981;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 6;"
        );
        analyzeBtn.setOnAction(e -> performAnalysis());

        Button refreshBtn = new Button("↻ Refresh");
        refreshBtn.setStyle(
                "-fx-padding: 8px 16px;" +
                        "-fx-font-size: 12px;" +
                        "-fx-background-color: #2563eb;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 6;"
        );
        refreshBtn.setOnAction(e -> loadInitialData());

        configBox.getChildren().addAll(
                strategyLabel,
                strategyCombo,
                pairLabel,
                pairCombo,
                metricLabel,
                metricCombo,
                createSpacer(),
                refreshBtn,
                analyzeBtn
        );

        return configBox;
    }

    private @NotNull TabPane createAnalysisTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-control-inner-background: #0f3460;");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab performanceTab = new Tab("Performance", createPerformanceTab());
        Tab riskTab = new Tab("Risk Analysis", createRiskTab());
        Tab equityTab = new Tab("Equity Curve", createEquityCurveTab());
        Tab distributionTab = new Tab("Return Distribution", createDistributionTab());
        Tab correlationTab = new Tab("Correlation", createCorrelationTab());

        tabPane.getTabs().addAll(
                performanceTab,
                riskTab,
                equityTab,
                distributionTab,
                correlationTab
        );

        return tabPane;
    }

    private VBox createPerformanceTab() {
        VBox content = createTabContainer();

        HBox scoreBox = new HBox(20);
        scoreBox.setPadding(new Insets(12));
        scoreBox.setAlignment(Pos.CENTER_LEFT);
        scoreBox.setStyle(
                "-fx-background-color: #0f3460;" +
                        "-fx-border-color: #10b981;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;"
        );

        performanceScoreLabel = new Label("Performance Score: N/A");
        performanceScoreLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #10b981;");

        metricsSummaryLabel = new Label("Sharpe: N/A | Sortino: N/A | Calmar: N/A | Profit Factor: N/A");
        metricsSummaryLabel.setStyle("-fx-text-fill: #a0aec0;");

        scoreBox.getChildren().addAll(performanceScoreLabel, metricsSummaryLabel);

        GridPane quickMetricsGrid = createMetricsGrid("#10b981");

        sharpeValueLabel = addMetricRow(quickMetricsGrid, 0, "Sharpe Ratio", "N/A", Color.web("#a0aec0"));
        sortinoValueLabel = addMetricRow(quickMetricsGrid, 1, "Sortino Ratio", "N/A", Color.web("#a0aec0"));
        calmarValueLabel = addMetricRow(quickMetricsGrid, 2, "Calmar Ratio", "N/A", Color.web("#a0aec0"));
        profitFactorValueLabel = addMetricRow(quickMetricsGrid, 3, "Profit Factor", "N/A", Color.web("#a0aec0"));
        recoveryFactorValueLabel = addMetricRow(quickMetricsGrid, 4, "Recovery Factor", "N/A", Color.web("#a0aec0"));

        performanceChart = createPerformanceChart();

        content.getChildren().addAll(scoreBox, quickMetricsGrid, performanceChart);
        VBox.setVgrow(performanceChart, Priority.ALWAYS);

        return content;
    }

    private VBox createRiskTab() {
        VBox content = createTabContainer();

        GridPane metricsGrid = createMetricsGrid("#ef4444");

        maxDrawdownValueLabel = addMetricRow(metricsGrid, 0, "Max Drawdown", "N/A", Color.web("#ef4444"));
        volatilityValueLabel = addMetricRow(metricsGrid, 1, "Volatility Annual", "N/A", Color.web("#f59e0b"));
        valueAtRiskValueLabel = addMetricRow(metricsGrid, 2, "Value at Risk 95%", "N/A", Color.web("#f59e0b"));
        conditionalValueAtRiskValueLabel = addMetricRow(metricsGrid, 3, "Conditional VaR", "N/A", Color.web("#ef4444"));
        maxConsecutiveLossValueLabel = addMetricRow(metricsGrid, 4, "Max Consecutive Losses", "N/A", Color.web("#ef4444"));
        recoveryTimeValueLabel = addMetricRow(metricsGrid, 5, "Recovery Time", "N/A", Color.web("#a0aec0"));

        Label drawdownLabel = createSectionTitle("Drawdown Over Time");

        drawdownChart = createDrawdownChart();

        content.getChildren().addAll(metricsGrid, drawdownLabel, drawdownChart);
        VBox.setVgrow(drawdownChart, Priority.ALWAYS);

        return content;
    }

    private VBox createEquityCurveTab() {
        VBox content = createTabContainer();

        Label label = createSectionTitle("Equity Curve");

        equityCurveChart = createEquityCurveChart();

        content.getChildren().addAll(label, equityCurveChart);
        VBox.setVgrow(equityCurveChart, Priority.ALWAYS);

        return content;
    }

    private VBox createDistributionTab() {
        VBox content = createTabContainer();

        Label infoLabel = new Label("Return distribution by bucket");
        infoLabel.setStyle("-fx-text-fill: #a0aec0;");

        distributionChart = createDistributionChart();

        content.getChildren().addAll(infoLabel, distributionChart);
        VBox.setVgrow(distributionChart, Priority.ALWAYS);

        return content;
    }

    private VBox createCorrelationTab() {
        VBox content = createTabContainer();

        Label infoLabel = new Label("Correlation matrix by available symbols. Values range from -1.0 to 1.0.");
        infoLabel.setStyle("-fx-text-fill: #a0aec0;");

        correlationGrid = new GridPane();
        correlationGrid.setHgap(8);
        correlationGrid.setVgap(8);
        correlationGrid.setPadding(new Insets(12));
        correlationGrid.setStyle(
                "-fx-background-color: #0f3460;" +
                        "-fx-border-color: #3b82f6;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;"
        );

        ScrollPane scrollPane = new ScrollPane(correlationGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        content.getChildren().addAll(infoLabel, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        return content;
    }

    private void loadInitialData() {
        setStatus("Loading analysis inputs...");

        CompletableFuture
                .supplyAsync(() -> {
                    List<String> strategies = dataProvider.loadStrategies();
                    List<String> pairs = dataProvider.loadPairs();
                    return new InitialData(strategies, pairs);
                })
                .whenComplete((data, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        log.error("Failed to load analysis inputs", error);
                        setStatus("Failed to load analysis inputs");
                        showAlert("Error", "Failed to load analysis inputs: " + error.getMessage());
                        return;
                    }

                    strategyCombo.setItems(FXCollections.observableArrayList(data.strategies()));
                    pairCombo.setItems(FXCollections.observableArrayList(data.pairs()));

                    if (!strategyCombo.getItems().isEmpty()) {
                        strategyCombo.getSelectionModel().selectFirst();
                    }

                    if (!pairCombo.getItems().isEmpty()) {
                        pairCombo.getSelectionModel().selectFirst();
                    }

                    setStatus("Inputs loaded");

                    updateAnalysis();
                }));
    }

    private void updateAnalysis() {
        String strategy = strategyCombo == null ? null : strategyCombo.getValue();
        String pair = pairCombo == null ? null : pairCombo.getValue();

        if (strategy == null || strategy.isBlank()) {
            clearAnalysis("Select a strategy");
            return;
        }

        if (pair == null || pair.isBlank()) {
            clearAnalysis("Select a pair");
            return;
        }

        setStatus("Loading analysis for " + strategy + " / " + pair + "...");

        CompletableFuture
                .supplyAsync(() -> dataProvider.loadAnalysis(strategy, pair))
                .whenComplete((snapshot, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        log.error("Failed to update analysis", error);
                        clearAnalysis("Analysis unavailable");
                        setStatus("Analysis failed");
                        return;
                    }

                    currentSnapshot = snapshot == null ? AnalysisSnapshot.empty() : snapshot;
                    renderSnapshot(currentSnapshot);
                    setStatus(currentSnapshot.hasRealData()
                            ? "Analysis loaded"
                            : "No completed backtest/analysis data found yet");
                }));
    }

    private void performAnalysis() {
        String strategy = strategyCombo.getValue();
        String pair = pairCombo.getValue();

        if (strategy == null || strategy.isBlank()) {
            showAlert("Missing Strategy", "Please select a strategy.");
            return;
        }

        if (pair == null || pair.isBlank()) {
            showAlert("Missing Pair", "Please select a trading pair.");
            return;
        }

        updateAnalysis();

        if (!currentSnapshot.hasRealData()) {
            showAlert(
                    "Analysis Data Not Found",
                    "No real analysis data was found yet for:\n\nStrategy: "
                            + strategy
                            + "\nPair: "
                            + pair
                            + "\n\nRun a backtest first, then this panel will display the computed metrics."
            );
        } else {
            showAlert("Analysis Complete", "Analysis loaded for " + strategy + " on " + pair + ".");
        }
    }

    private void renderSnapshot(@NotNull AnalysisSnapshot snapshot) {
        performanceScoreLabel.setText("Performance Score: " + formatScore(snapshot.performanceScore()));

        metricsSummaryLabel.setText(
                "Sharpe: " + formatNumber(snapshot.sharpeRatio()) +
                        " | Sortino: " + formatNumber(snapshot.sortinoRatio()) +
                        " | Calmar: " + formatNumber(snapshot.calmarRatio()) +
                        " | Profit Factor: " + formatNumber(snapshot.profitFactor())
        );

        sharpeValueLabel.setText(formatNumber(snapshot.sharpeRatio()));
        sortinoValueLabel.setText(formatNumber(snapshot.sortinoRatio()));
        calmarValueLabel.setText(formatNumber(snapshot.calmarRatio()));
        profitFactorValueLabel.setText(formatNumber(snapshot.profitFactor()));
        recoveryFactorValueLabel.setText(formatNumber(snapshot.recoveryFactor()));

        maxDrawdownValueLabel.setText(formatPercent(snapshot.maxDrawdown()));
        volatilityValueLabel.setText(formatPercent(snapshot.annualVolatility()));
        valueAtRiskValueLabel.setText(formatPercent(snapshot.valueAtRisk95()));
        conditionalValueAtRiskValueLabel.setText(formatPercent(snapshot.conditionalValueAtRisk()));
        maxConsecutiveLossValueLabel.setText(formatInteger(snapshot.maxConsecutiveLosses()));
        recoveryTimeValueLabel.setText(formatDays(snapshot.recoveryDays()));

        updatePerformanceChart(snapshot);
        updateDrawdownChart(snapshot);
        updateEquityCurveChart(snapshot);
        updateDistributionChart(snapshot);
        updateCorrelationGrid(snapshot);
    }

    private void clearAnalysis(String message) {
        currentSnapshot = AnalysisSnapshot.empty();

        if (performanceScoreLabel != null) {
            performanceScoreLabel.setText("Performance Score: N/A");
        }

        if (metricsSummaryLabel != null) {
            metricsSummaryLabel.setText("Sharpe: N/A | Sortino: N/A | Calmar: N/A | Profit Factor: N/A");
        }

        for (Label label : List.of(
                safeLabel(sharpeValueLabel),
                safeLabel(sortinoValueLabel),
                safeLabel(calmarValueLabel),
                safeLabel(profitFactorValueLabel),
                safeLabel(recoveryFactorValueLabel),
                safeLabel(maxDrawdownValueLabel),
                safeLabel(volatilityValueLabel),
                safeLabel(valueAtRiskValueLabel),
                safeLabel(conditionalValueAtRiskValueLabel),
                safeLabel(maxConsecutiveLossValueLabel),
                safeLabel(recoveryTimeValueLabel)
        )) {
            label.setText("N/A");
        }

        if (performanceChart != null) {
            performanceChart.getData().clear();
        }

        if (drawdownChart != null) {
            drawdownChart.getData().clear();
        }

        if (equityCurveChart != null) {
            equityCurveChart.getData().clear();
        }

        if (distributionChart != null) {
            distributionChart.getData().clear();
        }

        if (correlationGrid != null) {
            correlationGrid.getChildren().clear();
        }

        setStatus(message);
    }

    private Label safeLabel(@Nullable Label label) {
        return label == null ? new Label() : label;
    }

    private BarChart<String, Number> createPerformanceChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Metric");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Value");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Performance Metrics");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setStyle("-fx-background-color: #0f3460;");
        chart.setMinHeight(320);

        return chart;
    }

    private LineChart<Number, Number> createDrawdownChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Point");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Drawdown %");

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Drawdown Over Time");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setStyle("-fx-background-color: #0f3460;");
        chart.setMinHeight(320);

        return chart;
    }

    private LineChart<Number, Number> createEquityCurveChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Point");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Equity");

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Equity Curve");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setStyle("-fx-background-color: #0f3460;");
        chart.setMinHeight(360);

        return chart;
    }

    private BarChart<String, Number> createDistributionChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Return Bucket");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Frequency");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Return Distribution");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setStyle("-fx-background-color: #0f3460;");
        chart.setMinHeight(320);

        return chart;
    }

    private void updatePerformanceChart(@NotNull AnalysisSnapshot snapshot) {
        performanceChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Performance");

        addBarIfPresent(series, "Sharpe", snapshot.sharpeRatio());
        addBarIfPresent(series, "Sortino", snapshot.sortinoRatio());
        addBarIfPresent(series, "Calmar", snapshot.calmarRatio());
        addBarIfPresent(series, "Profit Fct", snapshot.profitFactor());
        addBarIfPresent(series, "Recovery", snapshot.recoveryFactor());

        performanceChart.getData().add(series);
    }

    private void updateDrawdownChart(@NotNull AnalysisSnapshot snapshot) {
        drawdownChart.getData().clear();

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Drawdown");

        List<Double> values = snapshot.drawdownSeries();

        for (int i = 0; i < values.size(); i++) {
            series.getData().add(new XYChart.Data<>(i, values.get(i)));
        }

        drawdownChart.getData().add(series);
    }

    private void updateEquityCurveChart(@NotNull AnalysisSnapshot snapshot) {
        equityCurveChart.getData().clear();

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Equity");

        List<Double> values = snapshot.equityCurve();

        for (int i = 0; i < values.size(); i++) {
            series.getData().add(new XYChart.Data<>(i, values.get(i)));
        }

        equityCurveChart.getData().add(series);
    }

    private void updateDistributionChart(@NotNull AnalysisSnapshot snapshot) {
        distributionChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Frequency");

        snapshot.returnBuckets().forEach((bucket, value) ->
                series.getData().add(new XYChart.Data<>(bucket, value))
        );

        distributionChart.getData().add(series);
    }

    private void updateCorrelationGrid(@NotNull AnalysisSnapshot snapshot) {
        correlationGrid.getChildren().clear();

        List<String> assets = snapshot.correlationAssets();

        if (assets.isEmpty()) {
            Label empty = new Label("No correlation data available.");
            empty.setStyle("-fx-text-fill: #94a3b8; -fx-padding: 8;");
            correlationGrid.add(empty, 0, 0);
            return;
        }

        correlationGrid.add(createCorrelationHeader("Asset"), 0, 0);

        for (int col = 0; col < assets.size(); col++) {
            correlationGrid.add(createCorrelationHeader(assets.get(col)), col + 1, 0);
        }

        for (int row = 0; row < assets.size(); row++) {
            String rowAsset = assets.get(row);
            correlationGrid.add(createCorrelationHeader(rowAsset), 0, row + 1);

            for (int col = 0; col < assets.size(); col++) {
                String colAsset = assets.get(col);
                Double value = snapshot.correlationValue(rowAsset, colAsset);

                Label cell = new Label(value == null ? "N/A" : String.format(Locale.US, "%.2f", value));
                cell.setMinWidth(72);
                cell.setAlignment(Pos.CENTER);
                cell.setStyle(
                        "-fx-padding: 8;" +
                                "-fx-border-color: #374151;" +
                                "-fx-text-fill: #ffffff;" +
                                "-fx-font-weight: bold;"
                );

                correlationGrid.add(cell, col + 1, row + 1);
            }
        }
    }

    private Label createCorrelationHeader(String text) {
        Label label = new Label(text);
        label.setMinWidth(72);
        label.setAlignment(Pos.CENTER);
        label.setStyle(
                "-fx-padding: 8;" +
                        "-fx-border-color: #475569;" +
                        "-fx-text-fill: #38bdf8;" +
                        "-fx-font-weight: bold;"
        );
        return label;
    }

    private void addBarIfPresent(XYChart.Series<String, Number> series, String name, Double value) {
        if (value != null && !value.isNaN() && !value.isInfinite()) {
            series.getData().add(new XYChart.Data<>(name, value));
        }
    }

    private Label addMetricRow(GridPane grid, int row, String metric, String value, Color color) {
        Label metricLabel = new Label(metric);
        metricLabel.setStyle("-fx-text-fill: #a0aec0;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: " + colorToHex(color) + "; -fx-font-weight: bold;");

        grid.add(metricLabel, 0, row);
        grid.add(valueLabel, 1, row);

        return valueLabel;
    }

    private GridPane createMetricsGrid(String borderColor) {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #0f3460;" +
                        "-fx-border-color: " + borderColor + ";" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;"
        );
        return grid;
    }

    private VBox createTabContainer() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");
        return content;
    }

    private Label createMutedLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #a0aec0;");
        return label;
    }

    private Label createSectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        return label;
    }

    private Region createSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text);
        }
    }

    private String formatNumber(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return "N/A";
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private String formatScore(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return "N/A";
        }
        return String.format(Locale.US, "%.1f/10", value);
    }

    private String formatPercent(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return "N/A";
        }
        return String.format(Locale.US, "%.2f%%", value);
    }

    private String formatInteger(Integer value) {
        if (value == null) {
            return "N/A";
        }
        return String.valueOf(value);
    }

    private String formatDays(Integer value) {
        if (value == null) {
            return "N/A";
        }
        return value + " days";
    }

    private String colorToHex(Color color) {
        return String.format(
                "#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255)
        );
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private record InitialData(List<String> strategies, List<String> pairs) {
    }

    /**
     * One object that carries everything the UI needs.
     */
    public record AnalysisSnapshot(
            String strategyName,
            String pairSymbol,
            LocalDateTime generatedAt,

            Double performanceScore,
            Double sharpeRatio,
            Double sortinoRatio,
            Double calmarRatio,
            Double profitFactor,
            Double recoveryFactor,

            Double maxDrawdown,
            Double annualVolatility,
            Double valueAtRisk95,
            Double conditionalValueAtRisk,
            Integer maxConsecutiveLosses,
            Integer recoveryDays,

            List<Double> equityCurve,
            List<Double> drawdownSeries,
            Map<String, Integer> returnBuckets,
            Map<String, Map<String, Double>> correlations,

            boolean realData
    ) {

        public static AnalysisSnapshot empty() {
            return new AnalysisSnapshot(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    Map.of(),
                    Map.of(),
                    false
            );
        }

        public boolean hasRealData() {
            return realData;
        }

        public List<String> correlationAssets() {
            if (correlations == null || correlations.isEmpty()) {
                return List.of();
            }

            Set<String> assets = new TreeSet<>(correlations.keySet());

            for (Map<String, Double> row : correlations.values()) {
                if (row != null) {
                    assets.addAll(row.keySet());
                }
            }

            return new ArrayList<>(assets);
        }

        public Double correlationValue(String left, String right) {
            if (correlations == null) {
                return null;
            }

            Map<String, Double> row = correlations.get(left);
            if (row != null && row.containsKey(right)) {
                return row.get(right);
            }

            Map<String, Double> reverseRow = correlations.get(right);
            if (reverseRow != null && reverseRow.containsKey(left)) {
                return reverseRow.get(left);
            }

            if (Objects.equals(left, right)) {
                return 1.0;
            }

            return null;
        }
    }

    /**
     * Reflection-based bridge between the JavaFX panel and your evolving backend.
     * <p>
     * This lets your UI compile now, while allowing you to later add:
     * <p>
     * systemCore.getStrategyAnalysisService().analyze(strategyName, pairSymbol)
     * <p>
     * without rewriting the UI.
     */
        private record AnalysisDataProvider(SystemCore systemCore) {

        public List<String> loadStrategies() {
                try {
                    List<String> names = new ArrayList<>(StrategyCatalog.availableStrategyNames());

                    return names.stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .distinct()
                            .sorted()
                            .collect(Collectors.toList());

                } catch (Exception e) {
                    log.warn("Unable to load strategies from StrategyCatalog: {}", e.getMessage());
                    return List.of();
                }
            }

            public List<String> loadPairs() {
                try {
                    Object exchange = invokeNoArg(systemCore, "getExchange");
                    if (exchange == null) {
                        return List.of();
                    }

                    Object rawPairs = firstNonNull(
                            () -> invokeNoArg(exchange, "getTradePairSymbol"),
                            () -> invokeNoArg(exchange, "getTradePairs"),
                            () -> invokeNoArg(exchange, "getSymbols"),
                            () -> invokeNoArg(exchange, "getAvailablePairs")
                    );

                    if (rawPairs == null) {
                        return List.of();
                    }

                    if (rawPairs instanceof Collection<?> collection) {
                        return collection.stream()
                                .filter(Objects::nonNull)
                                .map(this::formatPair)
                                .filter(s -> !s.isBlank())
                                .distinct()
                                .sorted()
                                .collect(Collectors.toList());
                    }

                    if (rawPairs.getClass().isArray()) {
                        int length = Array.getLength(rawPairs);
                        List<String> result = new ArrayList<>();

                        for (int i = 0; i < length; i++) {
                            Object item = Array.get(rawPairs, i);
                            if (item != null) {
                                result.add(formatPair(item));
                            }
                        }

                        return result.stream()
                                .filter(s -> !s.isBlank())
                                .distinct()
                                .sorted()
                                .collect(Collectors.toList());
                    }

                    return List.of(formatPair(rawPairs));

                } catch (Exception e) {
                    log.warn("Unable to load pairs from active exchange: {}", e.getMessage());
                    return List.of();
                }
            }

            public AnalysisSnapshot loadAnalysis(String strategyName, String pairSymbol) {
                Object analysis = findAnalysisObject(strategyName, pairSymbol);

                if (analysis == null) {
                    return AnalysisSnapshot.empty();
                }

                return new AnalysisSnapshot(
                        strategyName,
                        pairSymbol,
                        LocalDateTime.now(),

                        readDouble(analysis, "performanceScore", "score", "rating"),
                        readDouble(analysis, "sharpeRatio", "sharpe"),
                        readDouble(analysis, "sortinoRatio", "sortino"),
                        readDouble(analysis, "calmarRatio", "calmar"),
                        readDouble(analysis, "profitFactor"),
                        readDouble(analysis, "recoveryFactor"),

                        readDouble(analysis, "maxDrawdown", "maximumDrawdown"),
                        readDouble(analysis, "annualVolatility", "volatilityAnnual", "volatility"),
                        readDouble(analysis, "valueAtRisk95", "var95", "valueAtRisk"),
                        readDouble(analysis, "conditionalValueAtRisk", "cvar", "cvar95"),
                        readInteger(analysis, "maxConsecutiveLosses", "maxConsecutiveLoss", "consecutiveLosses"),
                        readInteger(analysis, "recoveryDays", "recoveryTimeDays"),

                        readDoubleList(analysis, "equityCurve", "equitySeries", "balanceCurve"),
                        readDoubleList(analysis, "drawdownSeries", "drawdowns"),
                        readStringIntegerMap(analysis, "returnBuckets", "returnDistribution"),
                        readCorrelationMap(analysis, "correlations", "correlationMatrix"),

                        true
                );
            }

            private Object findAnalysisObject(String strategyName, String pairSymbol) {
                Object direct = firstNonNull(
                        () -> invoke(systemCore, "analyzeStrategy", strategyName, pairSymbol),
                        () -> invoke(systemCore, "getStrategyAnalysis", strategyName, pairSymbol),
                        () -> invoke(systemCore, "getAnalysisSnapshot", strategyName, pairSymbol)
                );

                if (direct != null) {
                    return direct;
                }

                Object service = firstNonNull(
                        () -> invokeNoArg(systemCore, "getStrategyAnalysisService"),
                        () -> invokeNoArg(systemCore, "getAnalysisService"),
                        () -> invokeNoArg(systemCore, "getBacktestAnalysisService"),
                        () -> invokeNoArg(systemCore, "getPerformanceAnalytics")
                );

                if (service == null) {
                    return null;
                }

                return firstNonNull(
                        () -> invoke(service, "analyze", strategyName, pairSymbol),
                        () -> invoke(service, "analyzeStrategy", strategyName, pairSymbol),
                        () -> invoke(service, "getAnalysis", strategyName, pairSymbol),
                        () -> invoke(service, "getSnapshot", strategyName, pairSymbol),
                        () -> invoke(service, "latest", strategyName, pairSymbol)
                );
            }

            private String formatPair(Object pair) {
                Object slash = invoke(pair, "toString", '/');
                if (slash != null) {
                    return slash.toString();
                }

                String value = pair.toString();

                if (value.contains("_")) {
                    return value.replace("_", "/");
                }

                return value;
            }

            private Double readDouble(Object target, String... names) {
                Object value = readAny(target, names);
                return toDouble(value);
            }

            private Integer readInteger(Object target, String... names) {
                Object value = readAny(target, names);

                if (value instanceof Number number) {
                    return number.intValue();
                }

                if (value instanceof String string) {
                    try {
                        return Integer.parseInt(string.trim());
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }

                return null;
            }

            private List<Double> readDoubleList(Object target, String... names) {
                Object value = readAny(target, names);

                if (value == null) {
                    return List.of();
                }

                if (value instanceof Collection<?> collection) {
                    return collection.stream()
                            .map(this::toDouble)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                }

                if (value.getClass().isArray()) {
                    int length = Array.getLength(value);
                    List<Double> result = new ArrayList<>();

                    for (int i = 0; i < length; i++) {
                        Double number = toDouble(Array.get(value, i));
                        if (number != null) {
                            result.add(number);
                        }
                    }

                    return result;
                }

                return List.of();
            }

            private Map<String, Integer> readStringIntegerMap(Object target, String... names) {
                Object value = readAny(target, names);

                if (!(value instanceof Map<?, ?> map)) {
                    return Map.of();
                }

                Map<String, Integer> result = new LinkedHashMap<>();

                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }

                    Integer intValue = null;

                    if (entry.getValue() instanceof Number number) {
                        intValue = number.intValue();
                    } else {
                        try {
                            intValue = Integer.parseInt(entry.getValue().toString());
                        } catch (NumberFormatException ignored) {
                            // ignore invalid bucket
                        }
                    }

                    if (intValue != null) {
                        result.put(entry.getKey().toString(), intValue);
                    }
                }

                return result;
            }

            private Map<String, Map<String, Double>> readCorrelationMap(Object target, String... names) {
                Object value = readAny(target, names);

                if (!(value instanceof Map<?, ?> outerMap)) {
                    return Map.of();
                }

                Map<String, Map<String, Double>> result = new LinkedHashMap<>();

                for (Map.Entry<?, ?> outerEntry : outerMap.entrySet()) {
                    if (outerEntry.getKey() == null || !(outerEntry.getValue() instanceof Map<?, ?> innerMap)) {
                        continue;
                    }

                    Map<String, Double> row = new LinkedHashMap<>();

                    for (Map.Entry<?, ?> innerEntry : innerMap.entrySet()) {
                        if (innerEntry.getKey() == null) {
                            continue;
                        }

                        Double number = toDouble(innerEntry.getValue());

                        if (number != null) {
                            row.put(innerEntry.getKey().toString(), number);
                        }
                    }

                    result.put(outerEntry.getKey().toString(), row);
                }

                return result;
            }

            private Object readAny(Object target, String... names) {
                for (String name : names) {
                    Object value = firstNonNull(
                            () -> invokeNoArg(target, "get" + capitalize(name)),
                            () -> invokeNoArg(target, "is" + capitalize(name)),
                            () -> invokeNoArg(target, name),
                            () -> readField(target, name)
                    );

                    if (value != null) {
                        return value;
                    }
                }

                return null;
            }

            private Double toDouble(Object value) {
                if (value == null) {
                    return null;
                }

                if (value instanceof Number number) {
                    double d = number.doubleValue();

                    if (Double.isFinite(d)) {
                        return d;
                    }

                    return null;
                }

                if (value instanceof String string) {
                    try {
                        double d = Double.parseDouble(
                                string.trim()
                                        .replace("%", "")
                                        .replace(",", "")
                        );

                        if (Double.isFinite(d)) {
                            return d;
                        }

                        return null;
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }

                return null;
            }

            private String capitalize(String input) {
                if (input == null || input.isBlank()) {
                    return input;
                }

                return input.substring(0, 1).toUpperCase(Locale.ROOT) + input.substring(1);
            }

            private Object readField(Object target, String fieldName) {
                if (target == null || fieldName == null) {
                    return null;
                }

                Class<?> type = target.getClass();

                while (type != null) {
                    try {
                        Field field = type.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        return field.get(target);
                    } catch (Exception ignored) {
                        type = type.getSuperclass();
                    }
                }

                return null;
            }

            private Object invokeNoArg(Object target, String methodName) {
                if (target == null || methodName == null) {
                    return null;
                }

                try {
                    Method method = target.getClass().getMethod(methodName);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (Exception ignored) {
                    return null;
                }
            }

            private Object invoke(Object target, String methodName, Object... args) {
                if (target == null || methodName == null) {
                    return null;
                }

                Method[] methods = target.getClass().getMethods();

                for (Method method : methods) {
                    if (!method.getName().equals(methodName)) {
                        continue;
                    }

                    if (method.getParameterCount() != args.length) {
                        continue;
                    }

                    try {
                        method.setAccessible(true);
                        return method.invoke(target, args);
                    } catch (Exception ignored) {
                        // try next overload
                    }
                }

                return null;
            }

            @SafeVarargs
            private Object firstNonNull(SupplierWithException<Object>... suppliers) {
                for (SupplierWithException<Object> supplier : suppliers) {
                    try {
                        Object value = supplier.get();
                        if (value != null) {
                            return value;
                        }
                    } catch (Exception ignored) {
                        // continue
                    }
                }

                return null;
            }

            @FunctionalInterface
            private interface SupplierWithException<T> {
                T get() throws Exception;
            }
        }
}