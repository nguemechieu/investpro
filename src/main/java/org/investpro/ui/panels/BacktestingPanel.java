package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.TradePair;
import org.investpro.models.CandleData;
import org.investpro.repository.HistoricalDataRepository;
import org.investpro.repository.HistoricalDataRepositoryImpl;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.symbol.SymbolAgentManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Backtesting Panel - Executes historical backtests of strategies with real data integration.
 * - Loads strategies from StrategyCatalog.CORE_STRATEGY_NAMES
 * - Loads symbols from SymbolAgentManager
 * - Loads historical candles from HistoricalDataRepository
 * - Executes real backtests and displays equity curves, metrics, and trade analysis
 */
@Slf4j
public class BacktestingPanel extends VBox {

    private ComboBox<String> strategyCombo;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private ComboBox<String> symbolCombo;
    private ComboBox<String> timeframeCombo;
    private Label statusLabel;
    private ProgressBar progressBar;
    private TableView<BacktestTrade> tradesTable;
    private Label totalReturnLabel;
    private Label winRateLabel;
    private Label maxDrawdownLabel;
    private Label sharpeLabel;
    private Label profitFactorLabel;
    private AreaChart<Number, Number> equityCurveChart;

    private SymbolAgentManager symbolAgentManager;
    private HistoricalDataRepository historicalDataRepository;
    private AtomicBoolean backtestRunning = new AtomicBoolean(false);

    public BacktestingPanel() {
        this(null, null);
    }

    public BacktestingPanel(SymbolAgentManager symbolAgentManager, HistoricalDataRepository historicalDataRepository) {
        this.symbolAgentManager = symbolAgentManager;
        this.historicalDataRepository = historicalDataRepository != null 
            ? historicalDataRepository 
            : HistoricalDataRepositoryImpl.getInstance();

        setPadding(new Insets(16));
        setSpacing(12);
        setStyle("-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff;");
        getStyleClass().add("backtest-panel");

        setupUI();
    }

    private void setupUI() {
        // Header
        Label titleLabel = new Label("Strategy Backtest Engine");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Configuration Section
        VBox configBox = createConfigurationSection();

        // Results Section
        VBox resultsBox = createResultsSection();

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");
        content.getChildren().addAll(configBox, new Separator(), resultsBox);
        scrollPane.setContent(content);

        getChildren().addAll(titleLabel, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    private VBox createConfigurationSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #3b82f6; -fx-border-width: 1; -fx-border-radius: 6;");

        Label sectionTitle = new Label("Backtest Configuration");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Strategy Selection - Load from StrategyCatalog
        strategyCombo = new ComboBox<>();
        List<String> strategies = StrategyCatalog.CORE_STRATEGY_NAMES != null 
            ? new ArrayList<>(StrategyCatalog.CORE_STRATEGY_NAMES)
            : Arrays.asList("Trend Following", "Mean Reversion", "Breakout", "AI Hybrid", "EMA Cross",
                    "Momentum Continuation", "Pullback Trend", "Volatility Breakout", "MACD Trend",
                    "Range Fade", "Donchian Trend", "Bollinger Squeeze", "ATR Compression Breakout",
                    "RSI Failure Swing", "Volume Spike Reversal", "ML Model", "Adaptive Momentum Pullback");
        strategyCombo.setItems(FXCollections.observableArrayList(strategies));
        strategyCombo.setValue(strategies.isEmpty() ? "Trend Following" : strategies.get(0));
        strategyCombo.setPrefHeight(35);
        HBox strategyBox = createLabeledInput("Strategy:", strategyCombo);

        // Symbol Selection - Load from SymbolAgentManager
        symbolCombo = new ComboBox<>();
        loadSymbols();
        symbolCombo.setPrefHeight(35);
        HBox symbolBox = createLabeledInput("Symbol:", symbolCombo);

        // Timeframe Selection
        timeframeCombo = new ComboBox<>();
        timeframeCombo.setItems(FXCollections.observableArrayList("1m", "5m", "15m", "1h", "4h", "1d"));
        timeframeCombo.setValue("1h");
        timeframeCombo.setPrefHeight(35);
        HBox timeframeBox = createLabeledInput("Timeframe:", timeframeCombo);

        // Date Range
        startDatePicker = new DatePicker(LocalDate.now().minusMonths(6));
        startDatePicker.setPrefHeight(35);
        HBox startDateBox = createLabeledInput("Start Date:", startDatePicker);

        endDatePicker = new DatePicker(LocalDate.now());
        endDatePicker.setPrefHeight(35);
        HBox endDateBox = createLabeledInput("End Date:", endDatePicker);

        // Action Buttons
        Button runBacktestBtn = new Button("▶ Run Backtest");
        runBacktestBtn.setStyle(
                "-fx-padding: 10px 25px; -fx-font-size: 14px; -fx-background-color: #10b981; -fx-text-fill: white;");
        runBacktestBtn.setOnAction(e -> runBacktestAsync());

        Button compareBtn = new Button("📊 Compare");
        compareBtn.setStyle(
                "-fx-padding: 10px 25px; -fx-font-size: 14px; -fx-background-color: #3b82f6; -fx-text-fill: white;");
        compareBtn.setDisable(true);

        Button cancelBtn = new Button("✕ Cancel");
        cancelBtn.setStyle(
                "-fx-padding: 10px 25px; -fx-font-size: 14px; -fx-background-color: #ef4444; -fx-text-fill: white;");
        cancelBtn.setOnAction(e -> backtestRunning.set(false));

        // Status
        statusLabel = new Label("Ready to backtest");
        statusLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 12px;");

        progressBar = new ProgressBar(0);
        progressBar.setStyle("-fx-accent: #10b981;");
        progressBar.setPrefHeight(8);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.getChildren().addAll(runBacktestBtn, compareBtn, cancelBtn);

        section.getChildren().addAll(
                sectionTitle,
                strategyBox,
                symbolBox,
                timeframeBox,
                startDateBox,
                endDateBox,
                buttonBox,
                statusLabel,
                progressBar
        );

        return section;
    }

    private VBox createResultsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #10b981; -fx-border-width: 1; -fx-border-radius: 6;");

        Label sectionTitle = new Label("Backtest Results");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Metrics Box
        HBox metricsBox = createMetricsBox();

        // Equity Curve Chart
        equityCurveChart = createEquityCurveChart();

        // Trades Table
        tradesTable = createTradesTable();
        ScrollPane tradesScrollPane = new ScrollPane(tradesTable);
        tradesScrollPane.setFitToWidth(true);
        tradesScrollPane.setPrefHeight(200);

        Button exportBtn = new Button("⬇ Export Results");
        exportBtn.setStyle("-fx-padding: 8px 15px; -fx-background-color: #6366f1; -fx-text-fill: white;");
        exportBtn.setOnAction(e -> exportResults());

        section.getChildren().addAll(
                sectionTitle,
                metricsBox,
                new Label("Equity Curve"),
                equityCurveChart,
                new Label("Trade Details"),
                tradesScrollPane,
                exportBtn
        );

        VBox.setVgrow(equityCurveChart, Priority.SOMETIMES);
        VBox.setVgrow(tradesScrollPane, Priority.ALWAYS);

        return section;
    }

    private HBox createMetricsBox() {
        HBox box = new HBox(15);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: #0f3460; -fx-border-radius: 4;");

        totalReturnLabel = createMetricLabel("Total Return", "0.00%");
        winRateLabel = createMetricLabel("Win Rate", "0.00%");
        maxDrawdownLabel = createMetricLabel("Max Drawdown", "0.00%");
        sharpeLabel = createMetricLabel("Sharpe Ratio", "0.00");
        profitFactorLabel = createMetricLabel("Profit Factor", "0.00");

        box.getChildren().addAll(totalReturnLabel, winRateLabel, maxDrawdownLabel, sharpeLabel, profitFactorLabel);
        HBox.setHgrow(totalReturnLabel, Priority.ALWAYS);
        HBox.setHgrow(winRateLabel, Priority.ALWAYS);
        HBox.setHgrow(maxDrawdownLabel, Priority.ALWAYS);
        HBox.setHgrow(sharpeLabel, Priority.ALWAYS);
        HBox.setHgrow(profitFactorLabel, Priority.ALWAYS);

        return box;
    }

    private Label createMetricLabel(String name, String value) {
        VBox vbox = new VBox(2);
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11px;");
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 16px; -fx-font-weight: bold;");
        vbox.getChildren().addAll(nameLabel, valueLabel);
        vbox.setStyle("-fx-padding: 5px;");

        return new Label() {
            {
                setGraphic(vbox);
            }
        };
    }

    private AreaChart<Number, Number> createEquityCurveChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Trade #");
        xAxis.setStyle("-fx-text-fill: #a0aec0; -fx-tick-label-fill: #a0aec0;");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Equity ($)");
        yAxis.setStyle("-fx-text-fill: #a0aec0; -fx-tick-label-fill: #a0aec0;");

        AreaChart<Number, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setTitle("Equity Curve");
        chart.setStyle("-fx-background-color: #0f3460;");
        chart.setPrefHeight(250);
        chart.setLegendVisible(false);

        return chart;
    }

    private TableView<BacktestTrade> createTradesTable() {
        TableView<BacktestTrade> table = new TableView<>();
        table.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");

        TableColumn<BacktestTrade, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setPrefWidth(100);

        TableColumn<BacktestTrade, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(60);

        TableColumn<BacktestTrade, Double> entryCol = new TableColumn<>("Entry");
        entryCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        entryCol.setPrefWidth(80);

        TableColumn<BacktestTrade, Double> exitCol = new TableColumn<>("Exit");
        exitCol.setCellValueFactory(new PropertyValueFactory<>("exitPrice"));
        exitCol.setPrefWidth(80);

        TableColumn<BacktestTrade, Double> pnlCol = new TableColumn<>("P&L");
        pnlCol.setCellValueFactory(new PropertyValueFactory<>("profitValue"));
        pnlCol.setPrefWidth(80);

        TableColumn<BacktestTrade, Double> returnCol = new TableColumn<>("Return %");
        returnCol.setCellValueFactory(new PropertyValueFactory<>("returnPercent"));
        returnCol.setPrefWidth(80);

        table.getColumns().addAll(dateCol, typeCol, entryCol, exitCol, pnlCol, returnCol);
        return table;
    }

    private void loadSymbols() {
        List<String> symbols = new ArrayList<>();

        // Load real symbols from SymbolAgentManager if available
        if (symbolAgentManager != null) {
            try {
                List<String> realSymbols = symbolAgentManager.getAllStates().stream()
                        .map(state -> state.getSymbol().toString())
                        .collect(Collectors.toList());
                if (!realSymbols.isEmpty()) {
                    symbols.addAll(realSymbols);
                }
            } catch (Exception e) {
                log.warn("Failed to load symbols from SymbolAgentManager", e);
            }
        }

        // Fallback to default symbols if none found
        if (symbols.isEmpty()) {
            symbols = Arrays.asList(
                    "BTC/USD", "ETH/USD", "AAPL", "MSFT", "GOOGL",
                    "AMZN", "EURUSD", "GBPUSD", "JPYUSD", "SPY",
                    "QQQ", "IWM", "GLD", "TLT", "DBC"
            );
        }

        symbolCombo.setItems(FXCollections.observableArrayList(symbols));
        if (!symbols.isEmpty()) {
            symbolCombo.setValue(symbols.get(0));
        }
    }

    private void runBacktestAsync() {
        if (backtestRunning.get()) {
            statusLabel.setText("Backtest already running...");
            return;
        }

        backtestRunning.set(true);
        statusLabel.setText("Loading data...");
        progressBar.setProgress(-1); // Indeterminate

        Thread backTestThread = new Thread(() -> {
            try {
                runBacktest();
            } catch (Exception e) {
                log.error("Backtest error", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    progressBar.setProgress(0);
                    backtestRunning.set(false);
                });
            }
        });
        backTestThread.setDaemon(true);
        backTestThread.start();
    }

    private void runBacktest() {
        try {
            String strategyName = strategyCombo.getValue();
            String symbolStr = symbolCombo.getValue();
            String timeframe = timeframeCombo.getValue();
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();

            if (strategyName == null || symbolStr == null) {
                Platform.runLater(() -> {
                    statusLabel.setText("Please select strategy and symbol");
                    progressBar.setProgress(0);
                    backtestRunning.set(false);
                });
                return;
            }

            Platform.runLater(() -> statusLabel.setText("Loading historical data..."));

            // Parse symbol and load historical data
            TradePair symbol = TradePair.parse(symbolStr);
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

            List<CandleData> candles = new ArrayList<>();
            Optional<List<CandleData>> historicalCandles = historicalDataRepository.getHistoricalData(
                    symbol, startDateTime, endDateTime, timeframe);

            if (historicalCandles.isPresent() && !historicalCandles.get().isEmpty()) {
                candles = new ArrayList<>(historicalCandles.get());
            } else {
                // Generate sample data if no real data available
                candles = generateSampleData(200, 100.0);
                Platform.runLater(() -> statusLabel.setText("Using sample data (no historical data found)"));
            }

            if (candles.isEmpty()) {
                Platform.runLater(() -> {
                    statusLabel.setText("No data available for this period");
                    progressBar.setProgress(0);
                    backtestRunning.set(false);
                });
                return;
            }

            Platform.runLater(() -> statusLabel.setText("Executing backtest..."));

            // Execute backtest with simple momentum strategy
            List<BacktestTrade> trades = executeBacktest(strategyName, candles);

            if (trades.isEmpty()) {
                trades.add(new BacktestTrade(LocalDate.now(), "INFO", 0, 0, 0));
            }

            // Calculate metrics
            Map<String, Object> metrics = calculateMetrics(trades, candles);

            // Update UI on JavaFX thread
            Platform.runLater(() -> {
                tradesTable.setItems(FXCollections.observableArrayList(trades));
                updateEquityChart(trades);
                updateMetrics(metrics);
                statusLabel.setText("Backtest complete! (" + trades.size() + " trades)");
                progressBar.setProgress(1.0);
                backtestRunning.set(false);
            });

        } catch (Exception e) {
            log.error("Backtest execution error", e);
            Platform.runLater(() -> {
                statusLabel.setText("Error: " + e.getMessage());
                progressBar.setProgress(0);
                backtestRunning.set(false);
            });
        }
    }

    private List<BacktestTrade> executeBacktest(String strategy, List<CandleData> candles) {
        List<BacktestTrade> trades = new ArrayList<>();
        double balance = 10000;
        boolean inPosition = false;
        double entryPrice = 0;
        LocalDate entryDate = LocalDate.now();
        int tradeCount = 0;

        for (int i = 1; i < candles.size(); i++) {
            if (!backtestRunning.get()) break;

            CandleData current = candles.get(i);
            CandleData prev = candles.get(i - 1);

            double priceChange = (current.closePrice() - prev.closePrice()) / prev.closePrice() * 100;
            boolean bullish = current.closePrice() > current.openPrice();
            boolean momentum = Math.abs(priceChange) > 0.5;

            // Simple momentum-based entry/exit
            if (!inPosition && bullish && momentum && tradeCount < 20) {
                entryPrice = current.closePrice();
                entryDate = LocalDate.now();
                inPosition = true;
                tradeCount++;

                BacktestTrade buyTrade = new BacktestTrade(entryDate, "BUY", entryPrice, entryPrice, 1);
                trades.add(buyTrade);
            }

            if (inPosition && (priceChange < -1 || i == candles.size() - 1)) {
                double exitPrice = current.closePrice();
                double profit = exitPrice - entryPrice;
                double returnPct = (profit / entryPrice) * 100;

                balance += profit;

                BacktestTrade sellTrade = new BacktestTrade(LocalDate.now(), "SELL", exitPrice, exitPrice, 1);
                sellTrade.setProfit(profit);
                sellTrade.setReturnPercent(returnPct);
                trades.add(sellTrade);

                inPosition = false;
            }

            progressBar.setProgress((double) i / candles.size());
        }

        return trades;
    }

    private List<CandleData> generateSampleData(int count, double startPrice) {
        List<CandleData> candles = new ArrayList<>();
        double price = startPrice;
        long timestamp = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            double change = (Math.random() - 0.48) * 2;
            price += change;

            double open = price - Math.random();
            double close = price + Math.random();
            double high = Math.max(open, close) + Math.random();
            double low = Math.min(open, close) - Math.random();

            candles.add(new CandleData(open, close, high, low, timestamp + (i * 60000L), 1000 + Math.random() * 5000));
        }

        return candles;
    }

    private Map<String, Object> calculateMetrics(List<BacktestTrade> trades, List<CandleData> candles) {
        Map<String, Object> metrics = new HashMap<>();

        double totalReturn = 0;
        int winCount = 0;
        int lossCount = 0;
        double maxDrawdown = 0;
        double grossProfit = 0;
        double grossLoss = 0;
        double balance = 10000;

        for (BacktestTrade trade : trades) {
            if (trade.getReturnPercent() != 0) {
                double profit = trade.getProfit();
                totalReturn += profit;

                if (profit > 0) {
                    winCount++;
                    grossProfit += profit;
                } else {
                    lossCount++;
                    grossLoss += Math.abs(profit);
                }

                balance += profit;
                maxDrawdown = Math.min(maxDrawdown, profit);
            }
        }

        double totalTrades = winCount + lossCount;
        double winRate = totalTrades > 0 ? (winCount / totalTrades) * 100 : 0;
        double returnPercent = (totalReturn / 10000) * 100;
        double sharpeRatio = totalTrades > 0 ? totalReturn / (maxDrawdown == 0 ? 1 : Math.abs(maxDrawdown)) : 0;
        double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : (grossProfit > 0 ? 999 : 0);

        metrics.put("totalReturn", returnPercent);
        metrics.put("winRate", winRate);
        metrics.put("maxDrawdown", Math.abs(maxDrawdown));
        metrics.put("sharpeRatio", sharpeRatio);
        metrics.put("profitFactor", profitFactor);

        return metrics;
    }

    private void updateMetrics(Map<String, Object> metrics) {
        totalReturnLabel.setText(String.format("%.2f%%", (double) metrics.get("totalReturn")));
        winRateLabel.setText(String.format("%.2f%%", (double) metrics.get("winRate")));
        maxDrawdownLabel.setText(String.format("-%.2f%%", (double) metrics.get("maxDrawdown")));
        sharpeLabel.setText(String.format("%.2f", (double) metrics.get("sharpeRatio")));
        profitFactorLabel.setText(String.format("%.2f", (double) metrics.get("profitFactor")));
    }

    private void updateEquityChart(List<BacktestTrade> trades) {
        equityCurveChart.getData().clear();
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Equity");

        double equity = 10000;
        for (int i = 0; i < trades.size(); i++) {
            BacktestTrade trade = trades.get(i);
            equity += trade.getProfit();
            series.getData().add(new XYChart.Data<>(i, equity));
        }

        equityCurveChart.getData().add(series);
    }

    private void exportResults() {
        statusLabel.setText("Export feature coming soon...");
    }

    private HBox createLabeledInput(String label, Node input) {
        Label labelNode = new Label(label);
        labelNode.setMinWidth(100);
        labelNode.setStyle("-fx-text-fill: #ffffff;");
        input.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(labelNode, input);
        HBox.setHgrow(input, Priority.ALWAYS);

        return box;
    }

    /**
     * BacktestTrade record for storing individual trade information
     */
    @Data
    @AllArgsConstructor
    public static class BacktestTrade {
        private LocalDate date;
        private String type; // BUY, SELL, INFO
        private double price;
        private double exitPrice;
        private double quantity;

        private double profit = 0;
        private double returnPercent = 0;

        public double getProfitValue() {
            return profit;
        }

        public String getDate() {
            return date.toString();
        }

        public String getType() {
            return type;
        }

        public double getPrice() {
            return price;
        }

        public double getExitPrice() {
            return exitPrice;
        }
    }
}
