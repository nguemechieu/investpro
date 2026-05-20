package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.i18n.LocalizationService;
import org.investpro.models.trading.Trade;
import org.investpro.research.LiveTradingMetricsTracker;
import org.investpro.strategy.StrategyCatalog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Institutional-grade strategy analysis panel.
 * <p>
 * This panel is designed as a professional analytics workstation for InvestPro.
 * It does not depend on one fixed backend service shape. Instead, it tries to
 * read strategy analytics from SystemCore, analysis services, backtest
 * services,
 * performance services, or fields/getters using reflection.
 * <p>
 * Covered analytics:
 * - institutional scorecard
 * - performance and risk metrics
 * - drawdown and recovery profile
 * - equity curve
 * - return distribution
 * - rolling risk
 * - factor exposure
 * - market regime diagnostics
 * - stress testing
 * - Monte Carlo-style path simulation
 * - correlation matrix
 * - execution quality and slippage
 * - professional analyst notes
 */
@Slf4j
@Getter
@Setter
public class AnalysisPanel extends VBox {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String BG = "#0b1020";
    private static final String PANEL = "#111827";
    private static final String PANEL_2 = "#16213e";
    private static final String BORDER = "#263449";
    private static final String TEXT = "#f8fafc";
    private static final String MUTED = "#94a3b8";
    private static final String BLUE = "#3b82f6";
    private static final String GREEN = "#10b981";
    private static final String RED = "#ef4444";
    private static final String AMBER = "#f59e0b";
    private static final String CYAN = "#06b6d4";
    private static final String PURPLE = "#8b5cf6";

    private static final String NOT_AVAILABLE = "N/A";

    private final SystemCore systemCore;
    private final AnalysisDataProvider dataProvider;
    private final LiveTradingMetricsTracker liveMetricsTracker = new LiveTradingMetricsTracker();
    private final ScheduledExecutorService botStatusMonitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "analysis-bot-status-monitor");
        thread.setDaemon(true);
        return thread;
    });

    private boolean showingLiveMetrics = false;
    private boolean wasBotRunning = false;
    private String currentLiveStrategy = null;
    private String currentLiveSymbol = null;

    private ComboBox<String> strategyCombo;
    private ComboBox<String> pairCombo;
    private ComboBox<String> metricCombo;

    private Label statusLabel;
    private Label generatedAtLabel;
    private Label modeIndicatorLabel;

    private Label institutionalScoreLabel;
    private Label institutionalScoreHintLabel;
    private Label performanceScoreLabel;
    private Label riskScoreLabel;
    private Label executionScoreLabel;
    private Label liquidityScoreLabel;
    private Label regimeScoreLabel;

    private Label sharpeValueLabel;
    private Label sortinoValueLabel;
    private Label calmarValueLabel;
    private Label profitFactorValueLabel;
    private Label recoveryFactorValueLabel;
    private Label expectancyValueLabel;
    private Label winRateValueLabel;
    private Label totalTradesValueLabel;

    private Label maxDrawdownValueLabel;
    private Label volatilityValueLabel;
    private Label valueAtRiskValueLabel;
    private Label conditionalValueAtRiskValueLabel;
    private Label maxConsecutiveLossValueLabel;
    private Label recoveryTimeValueLabel;
    private Label tailRatioValueLabel;
    private Label ulcerIndexValueLabel;
    private Label kellyValueLabel;

    private Label alphaValueLabel;
    private Label betaValueLabel;
    private Label informationRatioValueLabel;
    private Label skewnessValueLabel;
    private Label kurtosisValueLabel;
    private Label exposureValueLabel;
    private Label turnoverValueLabel;

    private Label avgSlippageValueLabel;
    private Label spreadCostValueLabel;
    private Label marketImpactValueLabel;
    private Label fillQualityValueLabel;

    private BarChart<String, Number> scoreChart;
    private BarChart<String, Number> performanceChart;
    private LineChart<Number, Number> drawdownChart;
    private LineChart<Number, Number> equityCurveChart;
    private BarChart<String, Number> distributionChart;
    private BarChart<String, Number> factorExposureChart;
    private BarChart<String, Number> stressChart;
    private LineChart<Number, Number> monteCarloChart;
    private LineChart<Number, Number> rollingSharpeChart;
    private LineChart<Number, Number> rollingVolatilityChart;
    private PieChart regimePieChart;

    private GridPane correlationGrid;
    private TextArea analystNotesArea;

    private AnalysisSnapshot currentSnapshot = AnalysisSnapshot.empty();

    public AnalysisPanel(@NotNull SystemCore systemCore) {
        this.systemCore = Objects.requireNonNull(systemCore, "systemCore must not be null");
        this.dataProvider = new AnalysisDataProvider(systemCore);

        setPadding(new Insets(16));
        setSpacing(12);
        setStyle("-fx-background-color: " + BG + "; -fx-text-fill: " + TEXT + ";");
        getStyleClass().add("analysis-panel");

        setupUI();
        loadInitialData();

        // Start monitoring bot status to switch between backtesting and live metrics
        startBotStatusMonitoring();

        LocalizationService.applyTranslations(this);
    }

    private void setupUI() {
        Label titleLabel = new Label("Institutional Strategy Analysis");
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + TEXT + ";");

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: " + MUTED + ";");

        generatedAtLabel = new Label("Generated: N/A");
        generatedAtLabel.setStyle("-fx-text-fill: " + MUTED + ";");

        modeIndicatorLabel = new Label("📊 BACKTEST MODE");
        modeIndicatorLabel.setStyle("-fx-text-fill: " + BLUE + "; -fx-font-weight: bold; -fx-font-size: 12px;");

        VBox titleBlock = new VBox(3, titleLabel, generatedAtLabel);
        HBox header = new HBox(12, titleBlock, createSpacer(), modeIndicatorLabel, statusLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox configBox = createConfigurationBar();
        TabPane analysisTabs = createAnalysisTabs();

        ScrollPane scrollPane = new ScrollPane(analysisTabs);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        getChildren().addAll(header, configBox, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    private @NotNull HBox createConfigurationBar() {
        HBox configBox = new HBox(12);
        configBox.setPadding(new Insets(10));
        configBox.setAlignment(Pos.CENTER_LEFT);
        configBox.setStyle(panelStyle(BLUE));

        strategyCombo = new ComboBox<>();
        strategyCombo.setPrefWidth(240);
        strategyCombo.setPromptText("Select strategy");
        strategyCombo.setOnAction(e -> updateAnalysis());

        pairCombo = new ComboBox<>();
        pairCombo.setPrefWidth(190);
        pairCombo.setPromptText("Select symbol");
        pairCombo.setOnAction(e -> updateAnalysis());

        metricCombo = new ComboBox<>();
        metricCombo.setPrefWidth(220);
        metricCombo.getItems().addAll(
                "Institutional Score",
                "Sharpe Ratio",
                "Sortino Ratio",
                "Calmar Ratio",
                "Profit Factor",
                "Expected Value",
                "Max Drawdown",
                "VaR / CVaR",
                "Execution Quality",
                "Regime Fitness");
        metricCombo.getSelectionModel().selectFirst();
        metricCombo.setOnAction(e -> updateAnalysis());

        Button refreshBtn = actionButton("↻ Refresh", BLUE);
        refreshBtn.setOnAction(e -> loadInitialData());

        Button analyzeBtn = actionButton("🔍 Analyze", GREEN);
        analyzeBtn.setOnAction(e -> performAnalysis());

        configBox.getChildren().addAll(
                createMutedLabel("Strategy:"), strategyCombo,
                createMutedLabel("Symbol:"), pairCombo,
                createMutedLabel("Focus:"), metricCombo,
                createSpacer(), refreshBtn, analyzeBtn);

        return configBox;
    }

    private @NotNull TabPane createAnalysisTabs() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-control-inner-background: " + PANEL_2 + ";");

        tabPane.getTabs().addAll(
                fixedTab("Institutional Desk", createInstitutionalTab()),
                fixedTab("Performance", createPerformanceTab()),
                fixedTab("Risk", createRiskTab()),
                fixedTab("Equity Curve", createEquityCurveTab()),
                fixedTab("Returns", createDistributionTab()),
                fixedTab("Factors", createFactorTab()),
                fixedTab("Regime", createRegimeTab()),
                fixedTab("Stress Test", createStressTab()),
                fixedTab("Monte Carlo", createMonteCarloTab()),
                fixedTab("Correlation", createCorrelationTab()),
                fixedTab("Execution", createExecutionTab()),
                fixedTab("Notes", createAnalystNotesTab()));

        return tabPane;
    }

    private Tab fixedTab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private VBox createInstitutionalTab() {
        VBox content = createTabContainer();

        HBox scoreCards = new HBox(12);
        scoreCards.setAlignment(Pos.CENTER_LEFT);

        institutionalScoreLabel = createScoreLabel("Institutional Score", CYAN);
        performanceScoreLabel = createScoreLabel("Performance", GREEN);
        riskScoreLabel = createScoreLabel("Risk", RED);
        executionScoreLabel = createScoreLabel("Execution", BLUE);
        liquidityScoreLabel = createScoreLabel("Liquidity", PURPLE);
        regimeScoreLabel = createScoreLabel("Regime Fit", AMBER);

        scoreCards.getChildren().addAll(
                wrapCard(institutionalScoreLabel),
                wrapCard(performanceScoreLabel),
                wrapCard(riskScoreLabel),
                wrapCard(executionScoreLabel),
                wrapCard(liquidityScoreLabel),
                wrapCard(regimeScoreLabel));

        institutionalScoreHintLabel = new Label(
                "A composite score built from performance quality, downside risk, execution cost, liquidity, and regime fit.");
        institutionalScoreHintLabel.setWrapText(true);
        institutionalScoreHintLabel.setStyle("-fx-text-fill: " + MUTED + ";");

        scoreChart = createBarChart("Institutional Score Components", "Component", "Score", 320);

        content.getChildren().addAll(scoreCards, institutionalScoreHintLabel, scoreChart);
        VBox.setVgrow(scoreChart, Priority.ALWAYS);
        return content;
    }

    private VBox createPerformanceTab() {
        VBox content = createTabContainer();

        GridPane quickMetricsGrid = createMetricsGrid(GREEN);
        sharpeValueLabel = addMetricRow(quickMetricsGrid, 0, "Sharpe Ratio", Color.web(GREEN));
        sortinoValueLabel = addMetricRow(quickMetricsGrid, 1, "Sortino Ratio", Color.web(GREEN));
        calmarValueLabel = addMetricRow(quickMetricsGrid, 2, "Calmar Ratio", Color.web(GREEN));
        profitFactorValueLabel = addMetricRow(quickMetricsGrid, 3, "Profit Factor", Color.web(GREEN));
        recoveryFactorValueLabel = addMetricRow(quickMetricsGrid, 4, "Recovery Factor", Color.web(GREEN));
        expectancyValueLabel = addMetricRow(quickMetricsGrid, 5, "Expected Value / Trade", Color.web(CYAN));
        winRateValueLabel = addMetricRow(quickMetricsGrid, 6, "Win Rate", Color.web(CYAN));
        totalTradesValueLabel = addMetricRow(quickMetricsGrid, 7, "Total Trades", Color.web(MUTED));

        performanceChart = createBarChart("Performance Metrics", "Metric", "Value", 320);

        content.getChildren().addAll(quickMetricsGrid, performanceChart);
        VBox.setVgrow(performanceChart, Priority.ALWAYS);
        return content;
    }

    private VBox createRiskTab() {
        VBox content = createTabContainer();

        GridPane metricsGrid = createMetricsGrid(RED);
        maxDrawdownValueLabel = addMetricRow(metricsGrid, 0, "Max Drawdown", Color.web(RED));
        volatilityValueLabel = addMetricRow(metricsGrid, 1, "Annual Volatility", Color.web(AMBER));
        valueAtRiskValueLabel = addMetricRow(metricsGrid, 2, "Value at Risk 95%", Color.web(AMBER));
        conditionalValueAtRiskValueLabel = addMetricRow(metricsGrid, 3, "Conditional VaR", Color.web(RED));
        maxConsecutiveLossValueLabel = addMetricRow(metricsGrid, 4, "Max Consecutive Losses", Color.web(RED));
        recoveryTimeValueLabel = addMetricRow(metricsGrid, 5, "Recovery Time", Color.web(MUTED));
        tailRatioValueLabel = addMetricRow(metricsGrid, 6, "Tail Ratio", Color.web(PURPLE));
        ulcerIndexValueLabel = addMetricRow(metricsGrid, 7, "Ulcer Index", Color.web(PURPLE));
        kellyValueLabel = addMetricRow(metricsGrid, 8, "Kelly Fraction", Color.web(CYAN));

        drawdownChart = createLineChart("Drawdown Over Time", "Drawdown %", 340);
        rollingSharpeChart = createLineChart("Rolling Sharpe", "Sharpe", 280);
        rollingVolatilityChart = createLineChart("Rolling Volatility", "Volatility %", 280);

        content.getChildren().addAll(metricsGrid, drawdownChart, rollingSharpeChart, rollingVolatilityChart);
        VBox.setVgrow(drawdownChart, Priority.ALWAYS);
        return content;
    }

    private VBox createEquityCurveTab() {
        VBox content = createTabContainer();
        equityCurveChart = createLineChart("Equity Curve", "Equity", 420);
        content.getChildren().add(equityCurveChart);
        VBox.setVgrow(equityCurveChart, Priority.ALWAYS);
        return content;
    }

    private VBox createDistributionTab() {
        VBox content = createTabContainer();
        Label infoLabel = createMutedLabel(
                "Return distribution by bucket. Watch for fat left tails, skew, and unstable outliers.");
        distributionChart = createBarChart("Return Distribution", "Return Bucket", "Frequency", 360);
        content.getChildren().addAll(infoLabel, distributionChart);
        VBox.setVgrow(distributionChart, Priority.ALWAYS);
        return content;
    }

    private VBox createFactorTab() {
        VBox content = createTabContainer();
        GridPane factorMetrics = createMetricsGrid(PURPLE);
        alphaValueLabel = addMetricRow(factorMetrics, 0, "Alpha", Color.web(GREEN));
        betaValueLabel = addMetricRow(factorMetrics, 1, "Beta", Color.web(BLUE));
        informationRatioValueLabel = addMetricRow(factorMetrics, 2, "Information Ratio", Color.web(CYAN));
        skewnessValueLabel = addMetricRow(factorMetrics, 3, "Skewness", Color.web(AMBER));
        kurtosisValueLabel = addMetricRow(factorMetrics, 4, "Kurtosis", Color.web(AMBER));
        exposureValueLabel = addMetricRow(factorMetrics, 5, "Gross Exposure", Color.web(MUTED));
        turnoverValueLabel = addMetricRow(factorMetrics, 6, "Turnover", Color.web(MUTED));

        factorExposureChart = createBarChart("Factor Exposure", "Factor", "Exposure", 360);
        content.getChildren().addAll(factorMetrics, factorExposureChart);
        VBox.setVgrow(factorExposureChart, Priority.ALWAYS);
        return content;
    }

    private VBox createRegimeTab() {
        VBox content = createTabContainer();
        Label info = createMutedLabel(
                "Regime diagnostics estimate where the strategy works best: trend, chop, high volatility, low liquidity, or risk-off environments.");
        regimePieChart = new PieChart();
        regimePieChart.setTitle("Regime Contribution");
        regimePieChart.setLabelsVisible(true);
        regimePieChart.setLegendVisible(true);
        regimePieChart.setMinHeight(360);
        regimePieChart.setStyle("-fx-background-color: " + PANEL_2 + ";");
        content.getChildren().addAll(info, regimePieChart);
        VBox.setVgrow(regimePieChart, Priority.ALWAYS);
        return content;
    }

    private VBox createStressTab() {
        VBox content = createTabContainer();
        Label info = createMutedLabel(
                "Institutional stress tests estimate sensitivity to shocks such as volatility expansion, gap moves, liquidity drain, spread widening, and correlated selloffs.");
        stressChart = createBarChart("Stress Scenario P&L", "Scenario", "P&L / Return", 380);
        content.getChildren().addAll(info, stressChart);
        VBox.setVgrow(stressChart, Priority.ALWAYS);
        return content;
    }

    private VBox createMonteCarloTab() {
        VBox content = createTabContainer();
        Label info = createMutedLabel(
                "Monte Carlo paths are generated from the observed equity/return structure when backend paths are unavailable. This is a risk visualization, not a prediction.");
        monteCarloChart = createLineChart("Monte Carlo Equity Paths", "Equity", 420);
        content.getChildren().addAll(info, monteCarloChart);
        VBox.setVgrow(monteCarloChart, Priority.ALWAYS);
        return content;
    }

    private VBox createCorrelationTab() {
        VBox content = createTabContainer();
        Label infoLabel = createMutedLabel(
                "Correlation matrix by available symbols. Values range from -1.0 to 1.0. High correlation reduces true diversification.");

        correlationGrid = new GridPane();
        correlationGrid.setHgap(8);
        correlationGrid.setVgap(8);
        correlationGrid.setPadding(new Insets(12));
        correlationGrid.setStyle(panelStyle(BLUE));

        ScrollPane scrollPane = new ScrollPane(correlationGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        content.getChildren().addAll(infoLabel, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return content;
    }

    private VBox createExecutionTab() {
        VBox content = createTabContainer();
        GridPane executionGrid = createMetricsGrid(BLUE);
        avgSlippageValueLabel = addMetricRow(executionGrid, 0, "Avg Slippage", Color.web(AMBER));
        spreadCostValueLabel = addMetricRow(executionGrid, 1, "Spread Cost", Color.web(AMBER));
        marketImpactValueLabel = addMetricRow(executionGrid, 2, "Market Impact", Color.web(RED));
        fillQualityValueLabel = addMetricRow(executionGrid, 3, "Fill Quality", Color.web(GREEN));

        TextArea executionNotes = new TextArea();
        executionNotes.setEditable(false);
        executionNotes.setWrapText(true);
        executionNotes.setPrefRowCount(10);
        executionNotes.setText(
                "Execution analysis estimates the hidden drag from spread, slippage, latency, liquidity, and market impact. Institutional desks treat execution quality as part of alpha preservation.");
        executionNotes.setStyle(textAreaStyle());

        content.getChildren().addAll(executionGrid, executionNotes);
        return content;
    }

    private VBox createAnalystNotesTab() {
        VBox content = createTabContainer();
        analystNotesArea = new TextArea();
        analystNotesArea.setEditable(false);
        analystNotesArea.setWrapText(true);
        analystNotesArea.setStyle(textAreaStyle());
        analystNotesArea.setPrefRowCount(22);
        content.getChildren().add(analystNotesArea);
        VBox.setVgrow(analystNotesArea, Priority.ALWAYS);
        return content;
    }

    private void loadInitialData() {
        setStatus("Loading analysis inputs...");

        CompletableFuture
                .supplyAsync(() -> new InitialData(dataProvider.loadStrategies(), dataProvider.loadPairs()))
                .whenComplete((data, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        log.error("Failed to load analysis inputs", error);
                        setStatus("Failed to load analysis inputs");
                        showAlert("Error", "Failed to load analysis inputs: " + rootMessage(error));
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
            clearAnalysis("Select a symbol");
            return;
        }

        setStatus("Loading analysis for " + strategy + " / " + pair + "...");

        CompletableFuture
                .supplyAsync(() -> {
                    // Load live metrics if bot is currently trading, otherwise load backtesting
                    // results
                    if (showingLiveMetrics && strategy.equals(currentLiveStrategy) && pair.equals(currentLiveSymbol)) {
                        return liveMetricsToAnalysisSnapshot();
                    } else {
                        return dataProvider.loadAnalysis(strategy, pair);
                    }
                })
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
                            ? (showingLiveMetrics ? "Live trading metrics loaded" : "Institutional analysis loaded")
                            : "No completed institutional analysis data found yet");
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
            showAlert("Missing Symbol", "Please select a symbol.");
            return;
        }

        updateAnalysis();

        if (!currentSnapshot.hasRealData()) {
            showAlert(
                    "Analysis Data Not Found",
                    "No real institutional analysis data was found yet for:\n\nStrategy: "
                            + strategy
                            + "\nSymbol: "
                            + pair
                            + "\n\nRun a backtest, paper-trading evaluation, or strategy lab analysis first. This panel will then display computed institutional metrics.");
        } else {
            showAlert("Analysis Complete", "Institutional analysis loaded for " + strategy + " on " + pair + ".");
        }
    }

    private void renderSnapshot(@NotNull AnalysisSnapshot snapshot) {
        generatedAtLabel.setText("Generated: "
                + (snapshot.generatedAt() == null ? "N/A" : DATE_TIME_FORMATTER.format(snapshot.generatedAt())));

        institutionalScoreLabel.setText(scoreText("Institutional Score", snapshot.institutionalScore()));
        performanceScoreLabel.setText(scoreText("Performance", snapshot.performanceScore()));
        riskScoreLabel.setText(scoreText("Risk", snapshot.riskScore()));
        executionScoreLabel.setText(scoreText("Execution", snapshot.executionScore()));
        liquidityScoreLabel.setText(scoreText("Liquidity", snapshot.liquidityScore()));
        regimeScoreLabel.setText(scoreText("Regime Fit", snapshot.regimeScore()));

        sharpeValueLabel.setText(formatNumber(snapshot.sharpeRatio()));
        sortinoValueLabel.setText(formatNumber(snapshot.sortinoRatio()));
        calmarValueLabel.setText(formatNumber(snapshot.calmarRatio()));
        profitFactorValueLabel.setText(formatNumber(snapshot.profitFactor()));
        recoveryFactorValueLabel.setText(formatNumber(snapshot.recoveryFactor()));
        expectancyValueLabel.setText(formatCurrency(snapshot.expectancyPerTrade()));
        winRateValueLabel.setText(formatPercent(snapshot.winRate()));
        totalTradesValueLabel.setText(formatInteger(snapshot.totalTrades()));

        maxDrawdownValueLabel.setText(formatPercent(snapshot.maxDrawdown()));
        volatilityValueLabel.setText(formatPercent(snapshot.annualVolatility()));
        valueAtRiskValueLabel.setText(formatPercent(snapshot.valueAtRisk95()));
        conditionalValueAtRiskValueLabel.setText(formatPercent(snapshot.conditionalValueAtRisk()));
        maxConsecutiveLossValueLabel.setText(formatInteger(snapshot.maxConsecutiveLosses()));
        recoveryTimeValueLabel.setText(formatDays(snapshot.recoveryDays()));
        tailRatioValueLabel.setText(formatNumber(snapshot.tailRatio()));
        ulcerIndexValueLabel.setText(formatNumber(snapshot.ulcerIndex()));
        kellyValueLabel.setText(formatPercent(snapshot.kellyFraction()));

        alphaValueLabel.setText(formatPercent(snapshot.alpha()));
        betaValueLabel.setText(formatNumber(snapshot.beta()));
        informationRatioValueLabel.setText(formatNumber(snapshot.informationRatio()));
        skewnessValueLabel.setText(formatNumber(snapshot.skewness()));
        kurtosisValueLabel.setText(formatNumber(snapshot.kurtosis()));
        exposureValueLabel.setText(formatPercent(snapshot.grossExposure()));
        turnoverValueLabel.setText(formatPercent(snapshot.turnover()));

        avgSlippageValueLabel.setText(formatBps(snapshot.averageSlippageBps()));
        spreadCostValueLabel.setText(formatBps(snapshot.spreadCostBps()));
        marketImpactValueLabel.setText(formatBps(snapshot.marketImpactBps()));
        fillQualityValueLabel.setText(formatScore(snapshot.fillQualityScore()));

        updateScoreChart(snapshot);
        updatePerformanceChart(snapshot);
        updateDrawdownChart(snapshot);
        updateEquityCurveChart(snapshot);
        updateDistributionChart(snapshot);
        updateFactorExposureChart(snapshot);
        updateRegimeChart(snapshot);
        updateStressChart(snapshot);
        updateMonteCarloChart(snapshot);
        updateRollingCharts(snapshot);
        updateCorrelationGrid(snapshot);
        updateAnalystNotes(snapshot);
    }

    private void clearAnalysis(String message) {
        currentSnapshot = AnalysisSnapshot.empty();

        List<Label> labels = List.of(
                safeLabel(institutionalScoreLabel), safeLabel(performanceScoreLabel), safeLabel(riskScoreLabel),
                safeLabel(executionScoreLabel), safeLabel(liquidityScoreLabel), safeLabel(regimeScoreLabel),
                safeLabel(sharpeValueLabel), safeLabel(sortinoValueLabel), safeLabel(calmarValueLabel),
                safeLabel(profitFactorValueLabel), safeLabel(recoveryFactorValueLabel), safeLabel(expectancyValueLabel),
                safeLabel(winRateValueLabel), safeLabel(totalTradesValueLabel), safeLabel(maxDrawdownValueLabel),
                safeLabel(volatilityValueLabel), safeLabel(valueAtRiskValueLabel),
                safeLabel(conditionalValueAtRiskValueLabel),
                safeLabel(maxConsecutiveLossValueLabel), safeLabel(recoveryTimeValueLabel),
                safeLabel(tailRatioValueLabel),
                safeLabel(ulcerIndexValueLabel), safeLabel(kellyValueLabel), safeLabel(alphaValueLabel),
                safeLabel(betaValueLabel), safeLabel(informationRatioValueLabel), safeLabel(skewnessValueLabel),
                safeLabel(kurtosisValueLabel), safeLabel(exposureValueLabel), safeLabel(turnoverValueLabel),
                safeLabel(avgSlippageValueLabel), safeLabel(spreadCostValueLabel), safeLabel(marketImpactValueLabel),
                safeLabel(fillQualityValueLabel));

        for (Label label : labels) {
            label.setText("N/A");
        }

        clearCharts();
        if (analystNotesArea != null) {
            analystNotesArea.setText("No analysis loaded.");
        }
        setStatus(message);
    }

    private void clearCharts() {
        Arrays.asList(scoreChart, performanceChart, distributionChart, factorExposureChart, stressChart)
                .forEach(chart -> {
                    if (chart != null) {
                        chart.getData().clear();
                    }
                });

        Arrays.asList(drawdownChart, equityCurveChart, monteCarloChart, rollingSharpeChart, rollingVolatilityChart)
                .forEach(chart -> {
                    if (chart != null) {
                        chart.getData().clear();
                    }
                });

        if (regimePieChart != null) {
            regimePieChart.getData().clear();
        }

        if (correlationGrid != null) {
            correlationGrid.getChildren().clear();
        }
    }

    private void updateScoreChart(@NotNull AnalysisSnapshot snapshot) {
        scoreChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Score");
        addBarIfPresent(series, "Institutional", snapshot.institutionalScore());
        addBarIfPresent(series, "Performance", snapshot.performanceScore());
        addBarIfPresent(series, "Risk", snapshot.riskScore());
        addBarIfPresent(series, "Execution", snapshot.executionScore());
        addBarIfPresent(series, "Liquidity", snapshot.liquidityScore());
        addBarIfPresent(series, "Regime", snapshot.regimeScore());
        scoreChart.getData().add(series);
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
        addBarIfPresent(series, "Info Ratio", snapshot.informationRatio());
        performanceChart.getData().add(series);
    }

    private void updateDrawdownChart(@NotNull AnalysisSnapshot snapshot) {
        drawdownChart.getData().clear();
        drawdownChart.getData().add(seriesFromList("Drawdown", snapshot.drawdownSeries()));
    }

    private void updateEquityCurveChart(@NotNull AnalysisSnapshot snapshot) {
        equityCurveChart.getData().clear();
        equityCurveChart.getData().add(seriesFromList("Equity", snapshot.equityCurve()));
    }

    private void updateDistributionChart(@NotNull AnalysisSnapshot snapshot) {
        distributionChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Frequency");
        snapshot.returnBuckets().forEach((bucket, value) -> series.getData().add(new XYChart.Data<>(bucket, value)));
        distributionChart.getData().add(series);
    }

    private void updateFactorExposureChart(@NotNull AnalysisSnapshot snapshot) {
        factorExposureChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Exposure");
        snapshot.factorExposures().forEach((factor, value) -> addBarIfPresent(series, factor, value));
        factorExposureChart.getData().add(series);
    }

    private void updateRegimeChart(@NotNull AnalysisSnapshot snapshot) {
        regimePieChart.getData().clear();
        Map<String, Double> regimes = snapshot.regimeWeights();
        if (regimes.isEmpty()) {
            regimePieChart.getData().add(new PieChart.Data("No regime data", 1.0));
            return;
        }
        regimes.forEach((name, weight) -> {
            if (weight != null && Double.isFinite(weight) && weight > 0.0) {
                regimePieChart.getData().add(new PieChart.Data(name, weight));
            }
        });
    }

    private void updateStressChart(@NotNull AnalysisSnapshot snapshot) {
        stressChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Scenario P&L");
        snapshot.stressScenarios().forEach((scenario, value) -> addBarIfPresent(series, scenario, value));
        stressChart.getData().add(series);
    }

    private void updateMonteCarloChart(@NotNull AnalysisSnapshot snapshot) {
        monteCarloChart.getData().clear();
        List<List<Double>> paths = snapshot.monteCarloPaths();
        if (paths.isEmpty()) {
            paths = createSyntheticMonteCarloPaths(snapshot.equityCurve(), snapshot.returns());
        }
        int limit = Math.min(paths.size(), 12);
        for (int i = 0; i < limit; i++) {
            monteCarloChart.getData().add(seriesFromList("Path " + (i + 1), paths.get(i)));
        }
    }

    private void updateRollingCharts(@NotNull AnalysisSnapshot snapshot) {
        rollingSharpeChart.getData().clear();
        rollingVolatilityChart.getData().clear();
        rollingSharpeChart.getData().add(seriesFromList("Rolling Sharpe", snapshot.rollingSharpe()));
        rollingVolatilityChart.getData().add(seriesFromList("Rolling Volatility", snapshot.rollingVolatility()));
    }

    private void updateCorrelationGrid(@NotNull AnalysisSnapshot snapshot) {
        correlationGrid.getChildren().clear();
        List<String> assets = snapshot.correlationAssets();

        if (assets.isEmpty()) {
            Label empty = new Label("No correlation data available.");
            empty.setStyle("-fx-text-fill: " + MUTED + "; -fx-padding: 8;");
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
                cell.setMinWidth(74);
                cell.setAlignment(Pos.CENTER);
                cell.setStyle("-fx-padding: 8; -fx-border-color: " + BORDER + "; -fx-text-fill: " + TEXT
                        + "; -fx-font-weight: bold;");
                correlationGrid.add(cell, col + 1, row + 1);
            }
        }
    }

    private void updateAnalystNotes(@NotNull AnalysisSnapshot snapshot) {
        if (analystNotesArea == null) {
            return;
        }
        analystNotesArea.setText(buildAnalystNotes(snapshot));
    }

    private XYChart.Series<Number, Number> seriesFromList(String name, List<Double> values) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(name);
        if (values == null) {
            return series;
        }
        for (int i = 0; i < values.size(); i++) {
            Double value = values.get(i);
            if (isFinite(value)) {
                series.getData().add(new XYChart.Data<>(i, value));
            }
        }
        return series;
    }

    private List<List<Double>> createSyntheticMonteCarloPaths(List<Double> equity, List<Double> returns) {
        if (equity == null || equity.size() < 2) {
            return List.of();
        }

        List<Double> cleanReturns = returns == null || returns.isEmpty()
                ? deriveReturnsFromEquity(equity)
                : returns.stream().filter(AnalysisPanel::isFinite).toList();

        if (cleanReturns.isEmpty()) {
            return List.of();
        }

        double start = equity.getLast();
        if (!isFinite(start) || start <= 0.0) {
            start = 10_000.0;
        }

        Random random = new Random(42);
        List<List<Double>> paths = new ArrayList<>();
        int pathCount = 10;
        int points = Math.min(120, Math.max(30, cleanReturns.size()));

        for (int p = 0; p < pathCount; p++) {
            List<Double> path = new ArrayList<>();
            double value = start;
            path.add(value);
            for (int i = 1; i < points; i++) {
                double ret = cleanReturns.get(random.nextInt(cleanReturns.size())) / 100.0;
                value = Math.max(0.0, value * (1.0 + ret));
                path.add(value);
            }
            paths.add(path);
        }
        return paths;
    }

    private List<Double> deriveReturnsFromEquity(List<Double> equity) {
        List<Double> result = new ArrayList<>();
        for (int i = 1; i < equity.size(); i++) {
            double prev = equity.get(i - 1);
            double curr = equity.get(i);
            if (prev != 0.0 && isFinite(prev) && isFinite(curr)) {
                result.add(((curr - prev) / Math.abs(prev)) * 100.0);
            }
        }
        return result;
    }

    private String buildAnalystNotes(@NotNull AnalysisSnapshot snapshot) {
        if (!snapshot.hasRealData()) {
            return """
                    No real analysis data is loaded yet. Run a backtest or paper-trading evaluation first.

                    Institutional workflow suggestion:
                    1. Run multi-period backtests.
                    2. Split results by market regime.
                    3. Validate transaction costs and slippage.
                    4. Stress test adverse conditions.
                    5. Paper trade before live allocation.""";
        }

        return "Institutional Analysis Notes\n" +
                "============================\n\n" +
                "Strategy: " + nullSafe(snapshot.strategyName()) + '\n' +
                "Symbol: " + nullSafe(snapshot.pairSymbol()) + '\n' +
                "Composite Score: " + formatScore(snapshot.institutionalScore()) + "\n\n" +
                "Performance Quality\n" +
                "- Sharpe: " + formatNumber(snapshot.sharpeRatio()) + '\n' +
                "- Sortino: " + formatNumber(snapshot.sortinoRatio()) + '\n' +
                "- Profit Factor: " + formatNumber(snapshot.profitFactor()) + '\n' +
                "- Expected Value / Trade: " + formatCurrency(snapshot.expectancyPerTrade()) + "\n\n" +
                "Risk Profile\n" +
                "- Max Drawdown: " + formatPercent(snapshot.maxDrawdown()) + '\n' +
                "- VaR 95%: " + formatPercent(snapshot.valueAtRisk95()) + '\n' +
                "- CVaR: " + formatPercent(snapshot.conditionalValueAtRisk()) + '\n' +
                "- Recovery Time: " + formatDays(snapshot.recoveryDays()) + "\n\n" +
                "Execution / Capacity\n" +
                "- Avg Slippage: " + formatBps(snapshot.averageSlippageBps()) + '\n' +
                "- Spread Cost: " + formatBps(snapshot.spreadCostBps()) + '\n' +
                "- Market Impact: " + formatBps(snapshot.marketImpactBps()) + '\n' +
                "- Liquidity Score: " + formatScore(snapshot.liquidityScore()) + "\n\n" +
                "Interpretation\n" +
                "- A strong strategy is not only profitable; it must survive costs, adverse regimes, fat tails, and correlated risk.\n"
                +
                "- Watch for high profit factor with low trade count; that can be fragile.\n" +
                "- Watch for high Sharpe but weak CVaR; that may hide tail risk.\n" +
                "- Watch for high returns with poor execution score; alpha may disappear live.\n";
    }

    private BarChart<String, Number> createBarChart(String title, String xLabel, String yLabel, double minHeight) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel(xLabel);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setMinHeight(minHeight);
        chart.setStyle("-fx-background-color: " + PANEL_2 + ";");
        return chart;
    }

    private LineChart<Number, Number> createLineChart(String title, String yLabel, double minHeight) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Point");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setMinHeight(minHeight);
        chart.setStyle("-fx-background-color: " + PANEL_2 + ";");
        return chart;
    }

    private Label addMetricRow(GridPane grid, int row, String metric, Color color) {
        Label metricLabel = new Label(metric);
        metricLabel.setStyle("-fx-text-fill: " + MUTED + ";");

        Label valueLabel = new Label(NOT_AVAILABLE);
        valueLabel.setStyle("-fx-text-fill: " + colorToHex(color) + "; -fx-font-weight: bold;");

        grid.add(metricLabel, 0, row);
        grid.add(valueLabel, 1, row);
        return valueLabel;
    }

    private GridPane createMetricsGrid(String borderColor) {
        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(12);
        grid.setPadding(new Insets(12));
        grid.setStyle(panelStyle(borderColor));
        return grid;
    }

    private VBox createTabContainer() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: " + PANEL + ";");
        return content;
    }

    private Label createMutedLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + MUTED + ";");
        return label;
    }

    private Label createScoreLabel(String title, String color) {
        Label label = new Label(title + "\n" + NOT_AVAILABLE);
        label.setAlignment(Pos.CENTER);
        label.setMinWidth(150);
        label.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        return label;
    }

    private VBox wrapCard(Node node) {
        VBox card = new VBox(node);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12));
        card.setMinWidth(158);
        card.setStyle(panelStyle(BORDER));
        return card;
    }

    private Button actionButton(String text, String color) {
        Button button = new Button(text);
        button.setStyle("-fx-padding: 8px 16px; -fx-font-size: 12px; -fx-background-color: " + color
                + "; -fx-text-fill: white; -fx-background-radius: 6;");
        return button;
    }

    private Label createCorrelationHeader(String text) {
        Label label = new Label(text);
        label.setMinWidth(74);
        label.setAlignment(Pos.CENTER);
        label.setStyle("-fx-padding: 8; -fx-border-color: #475569; -fx-text-fill: #38bdf8; -fx-font-weight: bold;");
        return label;
    }

    private Region createSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void addBarIfPresent(XYChart.Series<String, Number> series, String name, Double value) {
        if (isFinite(value)) {
            series.getData().add(new XYChart.Data<>(name, value));
        }
    }

    private Label safeLabel(@Nullable Label label) {
        return label == null ? new Label() : label;
    }

    private void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text);
        }
    }

    private String panelStyle(String borderColor) {
        return "-fx-background-color: " + PANEL_2 + ";"
                + "-fx-border-color: " + borderColor + ";"
                + "-fx-border-width: 1;"
                + "-fx-border-radius: 8;"
                + "-fx-background-radius: 8;";
    }

    private String textAreaStyle() {
        return "-fx-control-inner-background: " + PANEL_2 + ";"
                + "-fx-text-fill: " + TEXT + ";"
                + "-fx-font-family: 'Consolas', 'Monaco', monospace;"
                + "-fx-font-size: 12px;"
                + "-fx-highlight-fill: " + BLUE + ";"
                + "-fx-highlight-text-fill: white;";
    }

    private String scoreText(String title, Double score) {
        return title + "\n" + formatScore(score);
    }

    private String formatNumber(Double value) {
        if (!isFinite(value)) {
            return "N/A";
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private String formatScore(Double value) {
        if (!isFinite(value)) {
            return "N/A";
        }
        return String.format(Locale.US, "%.1f/10", value);
    }

    private String formatPercent(Double value) {
        if (!isFinite(value)) {
            return "N/A";
        }
        return String.format(Locale.US, "%.2f%%", value);
    }

    private String formatCurrency(Double value) {
        if (!isFinite(value)) {
            return "N/A";
        }
        return String.format(Locale.US, "$%.2f", value);
    }

    private String formatBps(Double value) {
        if (!isFinite(value)) {
            return "N/A";
        }
        return String.format(Locale.US, "%.2f bps", value);
    }

    private String formatInteger(Integer value) {
        return value == null ? "N/A" : String.valueOf(value);
    }

    private String formatDays(Integer value) {
        return value == null ? "N/A" : value + " days";
    }

    private String colorToHex(Color color) {
        return String.format(
                "#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    private static boolean isFinite(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite();
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
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
     * One immutable object carrying the complete institutional analysis payload.
     */
    public record AnalysisSnapshot(
            String strategyName,
            String pairSymbol,
            LocalDateTime generatedAt,

            Double institutionalScore,
            Double performanceScore,
            Double riskScore,
            Double executionScore,
            Double liquidityScore,
            Double regimeScore,
            Double fillQualityScore,

            Double sharpeRatio,
            Double sortinoRatio,
            Double calmarRatio,
            Double profitFactor,
            Double recoveryFactor,
            Double expectancyPerTrade,
            Double winRate,
            Integer totalTrades,

            Double maxDrawdown,
            Double annualVolatility,
            Double valueAtRisk95,
            Double conditionalValueAtRisk,
            Integer maxConsecutiveLosses,
            Integer recoveryDays,
            Double tailRatio,
            Double ulcerIndex,
            Double kellyFraction,

            Double alpha,
            Double beta,
            Double informationRatio,
            Double skewness,
            Double kurtosis,
            Double grossExposure,
            Double turnover,

            Double averageSlippageBps,
            Double spreadCostBps,
            Double marketImpactBps,

            List<Double> equityCurve,
            List<Double> drawdownSeries,
            List<Double> returns,
            List<Double> rollingSharpe,
            List<Double> rollingVolatility,
            List<List<Double>> monteCarloPaths,
            Map<String, Integer> returnBuckets,
            Map<String, Map<String, Double>> correlations,
            Map<String, Double> factorExposures,
            Map<String, Double> regimeWeights,
            Map<String, Double> stressScenarios,
            boolean realData) {
        public static AnalysisSnapshot empty() {
            return new AnalysisSnapshot(
                    null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    null, null, null,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), false);
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
            return Objects.equals(left, right) ? 1.0 : null;
        }
    }

    /**
     * Convert live trading metrics to AnalysisSnapshot for display.
     */
    private AnalysisSnapshot liveMetricsToAnalysisSnapshot() {
        // For now, return basic snapshot with live metrics
        // Full computation would be complex; this is a placeholder for real-time
        // display
        return AnalysisSnapshot.empty();
    }

    /**
     * Monitor bot status and automatically switch between backtesting and live
     * metrics.
     * - Shows backtesting metrics by default
     * - When bot starts trading (autoTradingEnabled = true), switches to live
     * metrics
     * - When bot stops, reverts back to backtesting metrics
     */
    private void startBotStatusMonitoring() {
        botStatusMonitor.scheduleAtFixedRate(this::updateBotMetricMode, 0, 1, TimeUnit.SECONDS);
    }

    private void updateBotMetricMode() {
        boolean isBotRunning = systemCore != null && systemCore.isAutoTradingEnabled();

        if (isBotRunning && !wasBotRunning) {
            Platform.runLater(this::switchToLiveMetrics);
            wasBotRunning = true;
            log.info("Bot started - switching to LIVE trading metrics");
        } else if (!isBotRunning && wasBotRunning) {
            Platform.runLater(this::switchToBacktestMetrics);
            wasBotRunning = false;
            log.info("Bot stopped - switching back to BACKTEST metrics");
        }
    }

    /**
     * Switch to showing LIVE bot trading metrics.
     */
    private void switchToLiveMetrics() {
        if (showingLiveMetrics)
            return;

        showingLiveMetrics = true;

        // Get currently selected strategy and symbol
        String strategy = strategyCombo.getValue();
        String symbol = pairCombo.getValue();

        if (strategy != null && symbol != null) {
            currentLiveStrategy = strategy;
            currentLiveSymbol = symbol;

            // Initialize live tracking
            try {
                liveMetricsTracker.startTracking(
                        systemCore != null && systemCore.getExchange().fetchAccount()!= null
                                ? systemCore.getExchange().getUserAccountDetails().getAvailableBalance()
                                : 0.0);
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            // Update UI to show live mode
            modeIndicatorLabel.setText("🤖 LIVE TRADING MODE");
            modeIndicatorLabel.setStyle("-fx-text-fill: " + GREEN + "; -fx-font-weight: bold; -fx-font-size: 12px;");
            statusLabel.setText("Live Trading Active");

            // Refresh analysis to show live metrics
            updateAnalysis();

            log.info("Switched to LIVE metrics for {} / {}", strategy, symbol);
        }
    }

    /**
     * Switch back to showing BACKTEST metrics.
     */
    private void switchToBacktestMetrics() {
        if (!showingLiveMetrics)
            return;

        showingLiveMetrics = false;

        // Stop live tracking
        liveMetricsTracker.stopTracking();

        // Update UI to show backtest mode
        modeIndicatorLabel.setText("📊 BACKTEST MODE");
        modeIndicatorLabel.setStyle("-fx-text-fill: " + BLUE + "; -fx-font-weight: bold; -fx-font-size: 12px;");
        statusLabel.setText("Ready");

        // Keep the previously selected live strategy/symbol in dropdown
        // but now display historical backtest results
        if (currentLiveStrategy != null && currentLiveSymbol != null) {
            strategyCombo.setValue(currentLiveStrategy);
            pairCombo.setValue(currentLiveSymbol);
        }

        // Refresh analysis to show backtest metrics
        updateAnalysis();

        log.info("Switched back to BACKTEST metrics");
    }

    /**
     * Set selected strategy and symbol for analysis.
     */
    public void selectStrategyAndSymbol(String strategy, String symbol) {
        if (strategyCombo != null && strategy != null && !strategy.isBlank()) {
            strategyCombo.setValue(strategy);
        }
        if (pairCombo != null && symbol != null && !symbol.isBlank()) {
            pairCombo.setValue(symbol);
        }
    }

    /**
     * Record a trade in live metrics tracking.
     */
    @SuppressWarnings("unused")
    public void recordLiveTrading(String strategy, String symbol, Trade trade) {
        if (showingLiveMetrics && strategy.equals(currentLiveStrategy) && symbol.equals(currentLiveSymbol)) {
            liveMetricsTracker.recordTrade(trade);
            // Refresh to show updated metrics
            Platform.runLater(this::updateAnalysis);
        }
    }

    /**
     * Reflection-based bridge to the evolving backend.
     */
    private record AnalysisDataProvider(SystemCore systemCore) {

        public List<String> loadStrategies() {
            try {
                return new ArrayList<>(StrategyCatalog.availableStrategyNames()).stream()
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
                        () -> invokeNoArg(exchange, "getAvailablePairs"));

                return normalizePairList(rawPairs);
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

            List<Double> equityCurve = readDoubleList(analysis, "equityCurve", "equitySeries", "balanceCurve");
            List<Double> returns = readDoubleList(analysis, "returns", "returnSeries", "tradeReturns");

            if (returns.isEmpty() && equityCurve.size() > 1) {
                returns = deriveReturnsFromEquity(equityCurve);
            }

            Map<String, Integer> buckets = readReturnBuckets(analysis);
            if (buckets.isEmpty() && !returns.isEmpty()) {
                buckets = buildReturnBuckets(returns);
            }

            Double performanceScore = readDouble(analysis, "performanceScore", "score", "rating");
            Double riskScore = readDouble(analysis, "riskScore", "downsideScore");
            Double executionScore = readDouble(analysis, "executionScore", "executionQualityScore");
            Double liquidityScore = readDouble(analysis, "liquidityScore", "capacityScore");
            Double regimeScore = readDouble(analysis, "regimeScore", "regimeFitness", "regimeFitScore");
            Double institutionalScore = readDouble(analysis, "institutionalScore", "compositeScore", "deskScore");

            if (institutionalScore == null) {
                institutionalScore = compositeScore(performanceScore, riskScore, executionScore, liquidityScore,
                        regimeScore);
            }

            return new AnalysisSnapshot(
                    strategyName,
                    pairSymbol,
                    LocalDateTime.now(),

                    institutionalScore,
                    performanceScore,
                    riskScore,
                    executionScore,
                    liquidityScore,
                    regimeScore,
                    readDouble(analysis, "fillQualityScore", "fillQuality"),

                    readDouble(analysis, "sharpeRatio", "sharpe"),
                    readDouble(analysis, "sortinoRatio", "sortino"),
                    readDouble(analysis, "calmarRatio", "calmar"),
                    readDouble(analysis, "profitFactor"),
                    readDouble(analysis, "recoveryFactor"),
                    readDouble(analysis, "expectancyPerTrade", "expectedValue", "evPerTrade"),
                    readDouble(analysis, "winRate", "winRatePercent"),
                    readInteger(analysis, "totalTrades", "tradeCount", "numberOfTrades"),

                    readDouble(analysis, "maxDrawdown", "maximumDrawdown"),
                    readDouble(analysis, "annualVolatility", "volatilityAnnual", "volatility"),
                    readDouble(analysis, "valueAtRisk95", "var95", "valueAtRisk"),
                    readDouble(analysis, "conditionalValueAtRisk", "cvar", "cvar95"),
                    readInteger(analysis, "maxConsecutiveLosses", "maxConsecutiveLoss", "consecutiveLosses"),
                    readInteger(analysis, "recoveryDays", "recoveryTimeDays"),
                    readDouble(analysis, "tailRatio", "gainLossTailRatio"),
                    readDouble(analysis, "ulcerIndex"),
                    readDouble(analysis, "kellyFraction", "kelly", "kellyPercent"),

                    readDouble(analysis, "alpha"),
                    readDouble(analysis, "beta"),
                    readDouble(analysis, "informationRatio"),
                    readDouble(analysis, "skewness", "skew"),
                    readDouble(analysis, "kurtosis"),
                    readDouble(analysis, "grossExposure", "exposure"),
                    readDouble(analysis, "turnover"),

                    readDouble(analysis, "averageSlippageBps", "avgSlippageBps", "slippageBps"),
                    readDouble(analysis, "spreadCostBps", "spreadBps"),
                    readDouble(analysis, "marketImpactBps", "impactBps"),

                    equityCurve,
                    readDoubleList(analysis, "drawdownSeries", "drawdowns"),
                    returns,
                    readDoubleList(analysis, "rollingSharpe", "rollingSharpeRatio"),
                    readDoubleList(analysis, "rollingVolatility", "rollingVol"),
                    readMonteCarloPaths(analysis),
                    buckets,
                    readCorrelationMap(analysis),
                    readStringDoubleMap(analysis, "factorExposures", "factorLoadings", "exposures"),
                    readStringDoubleMap(analysis, "regimeWeights", "regimeDistribution", "regimes"),
                    readStringDoubleMap(analysis, "stressScenarios", "stressResults", "scenarioPnl"),
                    true);
        }

        private Object findAnalysisObject(String strategyName, String pairSymbol) {
            Object direct = firstNonNull(
                    () -> invoke(systemCore, "analyzeStrategy", strategyName, pairSymbol),
                    () -> invoke(systemCore, "getStrategyAnalysis", strategyName, pairSymbol),
                    () -> invoke(systemCore, "getAnalysisSnapshot", strategyName, pairSymbol),
                    () -> invoke(systemCore, "getInstitutionalAnalysis", strategyName, pairSymbol));

            if (direct != null) {
                return direct;
            }

            Object service = firstNonNull(
                    () -> invokeNoArg(systemCore, "getStrategyAnalysisService"),
                    () -> invokeNoArg(systemCore, "getAnalysisService"),
                    () -> invokeNoArg(systemCore, "getBacktestAnalysisService"),
                    () -> invokeNoArg(systemCore, "getPerformanceAnalytics"),
                    () -> invokeNoArg(systemCore, "getInstitutionalAnalyticsService"));

            if (service == null) {
                return null;
            }

            return firstNonNull(
                    () -> invoke(service, "analyze", strategyName, pairSymbol),
                    () -> invoke(service, "analyzeStrategy", strategyName, pairSymbol),
                    () -> invoke(service, "getAnalysis", strategyName, pairSymbol),
                    () -> invoke(service, "getSnapshot", strategyName, pairSymbol),
                    () -> invoke(service, "latest", strategyName, pairSymbol),
                    () -> invoke(service, "institutionalReport", strategyName, pairSymbol));
        }

        private List<String> normalizePairList(Object rawPairs) {
            if (rawPairs == null) {
                return List.of();
            }

            List<String> result = new ArrayList<>();
            if (rawPairs instanceof Collection<?> collection) {
                for (Object item : collection) {
                    result.add(formatPair(item));
                }
            } else if (rawPairs.getClass().isArray()) {
                int length = Array.getLength(rawPairs);
                for (int i = 0; i < length; i++) {
                    result.add(formatPair(Array.get(rawPairs, i)));
                }
            } else {
                result.add(formatPair(rawPairs));
            }

            return result.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }

        private String formatPair(Object pair) {
            if (pair == null) {
                return "";
            }
            Object slash = invoke(pair, "toString", '/');
            if (slash != null) {
                return slash.toString();
            }
            String value = pair.toString();
            return value.contains("_") ? value.replace("_", "/") : value;
        }

        private Double compositeScore(Double... scores) {
            List<Double> valid = new ArrayList<>();
            for (Double score : scores) {
                if (isFinite(score)) {
                    valid.add(score);
                }
            }
            if (valid.isEmpty()) {
                return null;
            }
            return valid.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        }

        private List<Double> deriveReturnsFromEquity(List<Double> equity) {
            List<Double> result = new ArrayList<>();
            for (int i = 1; i < equity.size(); i++) {
                Double prev = equity.get(i - 1);
                Double curr = equity.get(i);
                if (isFinite(prev) && isFinite(curr) && Math.abs(prev) > 1e-9) {
                    result.add(((curr - prev) / Math.abs(prev)) * 100.0);
                }
            }
            return result;
        }

        private Map<String, Integer> buildReturnBuckets(List<Double> returns) {
            Map<String, Integer> buckets = new LinkedHashMap<>();
            buckets.put("<-5%", 0);
            buckets.put("-5:-2%", 0);
            buckets.put("-2:0%", 0);
            buckets.put("0:2%", 0);
            buckets.put("2:5%", 0);
            buckets.put(">5%", 0);

            for (Double r : returns) {
                if (!isFinite(r)) {
                    continue;
                }
                String key;
                if (r < -5.0) {
                    key = "<-5%";
                } else if (r < -2.0) {
                    key = "-5:-2%";
                } else if (r < 0.0) {
                    key = "-2:0%";
                } else if (r < 2.0) {
                    key = "0:2%";
                } else if (r < 5.0) {
                    key = "2:5%";
                } else {
                    key = ">5%";
                }
                buckets.put(key, buckets.get(key) + 1);
            }
            return buckets;
        }

        private Double readDouble(Object target, String... names) {
            return toDouble(readAny(target, names));
        }

        private Integer readInteger(Object target, String... names) {
            Object value = readAny(target, names);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String string) {
                try {
                    return Integer.parseInt(string.trim().replace(",", ""));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }

        private List<Double> readDoubleList(Object target, String... names) {
            Object value = readAny(target, names);
            return normalizeDoubleList(value);
        }

        private List<List<Double>> readMonteCarloPaths(Object target) {
            Object value = readAny(target, "monteCarloPaths", "simulationPaths");
            if (value == null) {
                return List.of();
            }
            List<List<Double>> result = new ArrayList<>();
            if (value instanceof Collection<?> outer) {
                for (Object item : outer) {
                    List<Double> row = normalizeDoubleList(item);
                    if (!row.isEmpty()) {
                        result.add(row);
                    }
                }
            } else if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    List<Double> row = normalizeDoubleList(Array.get(value, i));
                    if (!row.isEmpty()) {
                        result.add(row);
                    }
                }
            }
            return result;
        }

        private List<Double> normalizeDoubleList(Object value) {
            if (value == null) {
                return List.of();
            }
            List<Double> result = new ArrayList<>();
            if (value instanceof Collection<?> collection) {
                for (Object item : collection) {
                    Double number = toDouble(item);
                    if (number != null) {
                        result.add(number);
                    }
                }
            } else if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    Double number = toDouble(Array.get(value, i));
                    if (number != null) {
                        result.add(number);
                    }
                }
            }
            return result;
        }

        private Map<String, Integer> readReturnBuckets(Object target) {
            Object value = readAny(target, "returnBuckets", "returnDistribution");
            if (!(value instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, Integer> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Integer intValue = toInteger(entry.getValue());
                if (entry.getKey() != null && intValue != null) {
                    result.put(entry.getKey().toString(), intValue);
                }
            }
            return result;
        }

        private Map<String, Double> readStringDoubleMap(Object target, String... names) {
            Object value = readAny(target, names);
            if (!(value instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, Double> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Double number = toDouble(entry.getValue());
                if (entry.getKey() != null && number != null) {
                    result.put(entry.getKey().toString(), number);
                }
            }
            return result;
        }

        private Map<String, Map<String, Double>> readCorrelationMap(Object target) {
            Object value = readAny(target, "correlations", "correlationMatrix");
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
                    Double number = toDouble(innerEntry.getValue());
                    if (innerEntry.getKey() != null && number != null) {
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
                        () -> readField(target, name));
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        private Double toDouble(Object value) {
            switch (value) {
                case null -> {
                    return null;
                }
                case Number number -> {
                    double d = number.doubleValue();
                    return Double.isFinite(d) ? d : null;
                }
                case String string -> {
                    try {
                        double d = Double.parseDouble(string.trim().replace("%", "").replace(",", ""));
                        return Double.isFinite(d) ? d : null;
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
                default -> {
                }
            }
            return null;
        }

        private Integer toInteger(Object value) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String string) {
                try {
                    return Integer.parseInt(string.trim().replace(",", ""));
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
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method.invoke(target, args);
                } catch (Exception ignored) {
                    // Try next overload.
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
                    // Continue.
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
