package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.core.agents.symbol.SymbolAgentManager;
import org.investpro.core.agents.symbol.SymbolAgentState;
import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;
import org.investpro.repository.HistoricalDataRepository;
import org.investpro.repository.HistoricalDataRepositoryImpl;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.TradingStrategy;
import org.investpro.strategy.impl.UnifiedStrategy;
import org.investpro.timeframe.Timeframe;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.investpro.utils.MARKET_TYPES.STOP_LIMIT;
import static org.investpro.utils.Side.BUY;
import static org.investpro.utils.Side.HOLD;
import static org.investpro.utils.Side.SELL;

/**
 * Backtesting Panel.
 * <p>
 * Executes historical strategy backtests using:
 * - selected TradePair
 * - selected strategy
 * - selected timeframe
 * - selected order type: MARKET, LIMIT, STOP, STOP_LIMIT
 * <p>
 * Important:
 * This panel does not place live orders.
 * It only simulates strategy behavior against historical candle data.
 */
@Slf4j
@Getter
@Setter
public class BacktestingPanel extends VBox {

    private ComboBox<String> strategyCombo;
    private ComboBox<TradePair> symbolCombo;
    private ComboBox<String> timeframeCombo;
    private ComboBox<String> orderTypeCombo;

    private DatePicker startDatePicker;
    private DatePicker endDatePicker;

    private Label statusLabel;
    private ProgressBar progressBar;

    private TableView<BacktestTrade> tradesTable;

    private Label totalReturnLabel;
    private Label winRateLabel;
    private Label maxDrawdownLabel;
    private Label sharpeLabel;
    private Label profitFactorLabel;

    private AreaChart<Number, Number> equityCurveChart;

    private final SymbolAgentManager symbolAgentManager;
    private final HistoricalDataRepository historicalDataRepository;
    private final SystemCore systemCore;
    private final AtomicBoolean backtestRunning = new AtomicBoolean(false);

    private static final double INITIAL_EQUITY = 10_000.0;
    private static final double DEFAULT_QUANTITY = 1.0;
    private static final int MAX_BARS_HELD = 50;

    public BacktestingPanel() {
        this(null, null, null);
    }

    public BacktestingPanel(
            SymbolAgentManager symbolAgentManager,
            HistoricalDataRepository historicalDataRepository) {
        this(symbolAgentManager, historicalDataRepository, null);
    }

    public BacktestingPanel(SystemCore systemCore) {
        this(
                systemCore != null ? systemCore.getSmartBot().getSymbolAgentManager() : null,
                HistoricalDataRepositoryImpl.getInstance(),
                systemCore);
    }

    public BacktestingPanel(
            SymbolAgentManager symbolAgentManager,
            HistoricalDataRepository historicalDataRepository,
            SystemCore systemCore) {
        this.symbolAgentManager = symbolAgentManager;
        this.historicalDataRepository = historicalDataRepository != null
                ? historicalDataRepository
                : HistoricalDataRepositoryImpl.getInstance();
        this.systemCore = systemCore;

        setPadding(new Insets(16));
        setSpacing(12);
        setStyle("-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff;");
        getStyleClass().add("backtest-panel");

        setupUI();
    }

    private void setupUI() {
        Label titleLabel = new Label("Strategy Backtest Engine");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        VBox configBox = createConfigurationSection();
        VBox resultsBox = createResultsSection();

        VBox content = new VBox(12);
        content.setPadding(new Insets(12));
        content.setStyle("-fx-background-color: #16213e;");
        content.getChildren().addAll(configBox, new Separator(), resultsBox);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);

        getChildren().addAll(titleLabel, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    private VBox createConfigurationSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #16213e; " +
                        "-fx-border-color: #3b82f6; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-radius: 6;");

        Label sectionTitle = new Label("Backtest Configuration");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        strategyCombo = new ComboBox<>();
        List<String> strategies = loadStrategyNames();
        strategyCombo.setItems(FXCollections.observableArrayList(strategies));
        strategyCombo.setValue(strategies.isEmpty() ? "Trend Following" : strategies.getFirst());
        strategyCombo.setPrefHeight(35);
        HBox strategyBox = createLabeledInput("Strategy:", strategyCombo);

        symbolCombo = new ComboBox<>();
        loadSymbols();
        symbolCombo.setPrefHeight(35);
        HBox symbolBox = createLabeledInput("Symbol:", symbolCombo);

        timeframeCombo = new ComboBox<>();
        timeframeCombo.setItems(FXCollections.observableArrayList("1m", "5m", "15m", "30m", "1h", "4h", "1d"));
        timeframeCombo.setValue("1h");
        timeframeCombo.setPrefHeight(35);
        HBox timeframeBox = createLabeledInput("Timeframe:", timeframeCombo);

        orderTypeCombo = new ComboBox<>();
        orderTypeCombo.setItems(FXCollections.observableArrayList("MARKET", "LIMIT", "STOP", "STOP_LIMIT"));
        orderTypeCombo.setValue("MARKET");
        orderTypeCombo.setPrefHeight(35);
        HBox orderTypeBox = createLabeledInput("Order Type:", orderTypeCombo);

        startDatePicker = new DatePicker(LocalDate.now().minusMonths(6));
        startDatePicker.setPrefHeight(35);
        HBox startDateBox = createLabeledInput("Start Date:", startDatePicker);

        endDatePicker = new DatePicker(LocalDate.now());
        endDatePicker.setPrefHeight(35);
        HBox endDateBox = createLabeledInput("End Date:", endDatePicker);

        Button runBacktestBtn = new Button("▶ Run Backtest");
        runBacktestBtn.setStyle(
                "-fx-padding: 10px 25px; " +
                        "-fx-font-size: 14px; " +
                        "-fx-background-color: #10b981; " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 6; " +
                        "-fx-cursor: hand;");
        runBacktestBtn.setOnAction(event -> runBacktestAsync());

        Button compareBtn = new Button("📊 Compare");
        compareBtn.setStyle(
                "-fx-padding: 10px 25px; " +
                        "-fx-font-size: 14px; " +
                        "-fx-background-color: #3b82f6; " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 6; " +
                        "-fx-cursor: hand;");
        compareBtn.setDisable(true);

        Button cancelBtn = getButton();

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
                orderTypeBox,
                startDateBox,
                endDateBox,
                buttonBox,
                statusLabel,
                progressBar);

        return section;
    }

    private @NotNull Button getButton() {
        Button cancelBtn = new Button("✕ Cancel");
        cancelBtn.setStyle(
                "-fx-padding: 10px 25px; " +
                        "-fx-font-size: 14px; " +
                        "-fx-background-color: #ef4444; " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 6; " +
                        "-fx-cursor: hand;");
        cancelBtn.setOnAction(event -> {
            backtestRunning.set(false);
            statusLabel.setText("Cancel requested...");
        });
        return cancelBtn;
    }

    private VBox createResultsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #16213e; " +
                        "-fx-border-color: #10b981; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-radius: 6;");

        Label sectionTitle = new Label("Backtest Results");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        HBox metricsBox = createMetricsBox();

        equityCurveChart = createEquityCurveChart();

        tradesTable = createTradesTable();
        ScrollPane tradesScrollPane = new ScrollPane(tradesTable);
        tradesScrollPane.setFitToWidth(true);
        tradesScrollPane.setPrefHeight(240);

        Button exportBtn = new Button("⬇ Export Results");
        exportBtn.setStyle(
                "-fx-padding: 8px 15px; " +
                        "-fx-background-color: #6366f1; " +
                        "-fx-text-fill: white; " +
                        "-fx-background-radius: 6; " +
                        "-fx-cursor: hand;");
        exportBtn.setOnAction(event -> exportResults());

        Label equityLabel = new Label("Equity Curve");
        equityLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");

        Label tradesLabel = new Label("Trade Details");
        tradesLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");

        section.getChildren().addAll(
                sectionTitle,
                metricsBox,
                equityLabel,
                equityCurveChart,
                tradesLabel,
                tradesScrollPane,
                exportBtn);

        VBox.setVgrow(equityCurveChart, Priority.SOMETIMES);
        VBox.setVgrow(tradesScrollPane, Priority.ALWAYS);

        return section;
    }

    private HBox createMetricsBox() {
        HBox box = new HBox(15);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 4;");

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

        Label label = new Label();
        label.setGraphic(vbox);

        return label;
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

        TableColumn<BacktestTrade, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateCol.setPrefWidth(100);

        TableColumn<BacktestTrade, Side> sideCol = new TableColumn<>("Side");
        sideCol.setCellValueFactory(new PropertyValueFactory<>("side"));
        sideCol.setPrefWidth(70);

        TableColumn<BacktestTrade, MARKET_TYPES> orderTypeCol = new TableColumn<>("Order Type");
        orderTypeCol.setCellValueFactory(new PropertyValueFactory<>("orderType"));
        orderTypeCol.setPrefWidth(90);

        TableColumn<BacktestTrade, Double> entryCol = new TableColumn<>("Entry");
        entryCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        entryCol.setPrefWidth(90);

        TableColumn<BacktestTrade, Double> exitCol = new TableColumn<>("Exit");
        exitCol.setCellValueFactory(new PropertyValueFactory<>("exitPrice"));
        exitCol.setPrefWidth(90);

        TableColumn<BacktestTrade, Double> pnlCol = new TableColumn<>("P&L");
        pnlCol.setCellValueFactory(new PropertyValueFactory<>("profit"));
        pnlCol.setPrefWidth(90);

        TableColumn<BacktestTrade, Double> returnCol = new TableColumn<>("Return %");
        returnCol.setCellValueFactory(new PropertyValueFactory<>("returnPercent"));
        returnCol.setPrefWidth(90);

        TableColumn<BacktestTrade, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setCellValueFactory(new PropertyValueFactory<>("reason"));
        reasonCol.setPrefWidth(220);

        table.getColumns().addAll(
                dateCol,
                sideCol,
                orderTypeCol,
                entryCol,
                exitCol,
                pnlCol,
                returnCol,
                reasonCol);

        return table;
    }

    private List<String> loadStrategyNames() {
        try {
            List<String> strategies = new ArrayList<>(StrategyCatalog.availableStrategyNames());

            if (!strategies.isEmpty()) {
                return strategies;
            }
        } catch (Exception exception) {
            log.debug("StrategyCatalog.availableStrategyNames() unavailable: {}", exception.getMessage());
        }

        return new ArrayList<>(StrategyCatalog.CORE_STRATEGY_NAMES);
    }

    private void loadSymbols() {
        List<TradePair> symbols = new ArrayList<>();

        // Try to get symbols from SymbolAgentManager
        SymbolAgentManager manager = symbolAgentManager;
        if (manager == null && systemCore != null) {
            manager = systemCore.getSmartBot().getSymbolAgentManager();
        }

        if (manager != null) {
            try {
                List<TradePair> realSymbols = manager.getAllStates()
                        .stream()
                        .map(SymbolAgentState::getSymbol)
                        .distinct()
                        .toList();

                if (!realSymbols.isEmpty()) {
                    symbols.addAll(realSymbols);
                    log.info("Loaded {} symbols from live market", symbols.size());
                }
            } catch (Exception exception) {
                log.warn("Failed to load symbols from SymbolAgentManager: {}", exception.getMessage());
            }
        }

        // Fallback to default symbols if none loaded from live market
        if (symbols.isEmpty()) {
            log.info("No symbols from live market, using default symbols");
            try {
                symbols = Arrays.asList(
                        TradePair.of("BTC", "USD"),
                        TradePair.of("ETH", "USD"),
                        TradePair.of("AAPL", "USD"),
                        TradePair.of("MSFT", "USD"),
                        TradePair.of("GOOGL", "USD"),
                        TradePair.of("AMZN", "USD"),
                        TradePair.of("EUR", "USD"),
                        TradePair.of("GBP", "USD"),
                        TradePair.of("JPY", "USD"),
                        TradePair.of("SPY", "USD"),
                        TradePair.of("QQQ", "USD"),
                        TradePair.of("IWM", "USD"),
                        TradePair.of("GLD", "USD"),
                        TradePair.of("TLT", "USD"),
                        TradePair.of("DBC", "USD"));
            } catch (Exception e) {
                log.warn("Failed to create default symbol list: {}", e.getMessage());
                // If we can't even create defaults, use an empty list
                symbols = new ArrayList<>();
            }
        }

        symbolCombo.setItems(FXCollections.observableArrayList(symbols));

        if (!symbols.isEmpty()) {
            symbolCombo.setValue(symbols.getFirst());
        }

        symbolCombo.setCellFactory(comboBox -> new ListCell<>() {
            @Override
            protected void updateItem(TradePair item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayTradePair(item));
            }
        });

        symbolCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(TradePair item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayTradePair(item));
            }
        });
    }

    private void runBacktestAsync() {
        if (backtestRunning.get()) {
            statusLabel.setText("Backtest already running...");
            return;
        }

        backtestRunning.set(true);
        statusLabel.setText("Loading data...");
        progressBar.setProgress(-1);

        Thread backTestThread = new Thread(() -> {
            try {
                runBacktest();
            } catch (Exception exception) {
                log.error("Backtest error", exception);
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + exception.getMessage());
                    progressBar.setProgress(0);
                    backtestRunning.set(false);
                });
            }
        });

        backTestThread.setName("strategy-backtest-worker");
        backTestThread.setDaemon(true);
        backTestThread.start();
    }

    private void runBacktest() {
        try {
            String strategyName = strategyCombo.getValue();
            TradePair selectedPair = symbolCombo.getValue();
            String timeframeCode = timeframeCombo.getValue();
            MARKET_TYPES orderType = resolveOrderType(orderTypeCombo.getValue());

            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();

            if (strategyName == null || strategyName.isBlank() || selectedPair == null) {
                Platform.runLater(() -> {
                    statusLabel.setText("Please select strategy and symbol");
                    progressBar.setProgress(0);
                    backtestRunning.set(false);
                });
                return;
            }

            Platform.runLater(
                    () -> statusLabel.setText("Loading historical data for " + displayTradePair(selectedPair)));

            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

            List<CandleData> candles;

            Optional<List<CandleData>> historicalCandles = historicalDataRepository.getHistoricalData(
                    selectedPair,
                    startDateTime,
                    endDateTime,
                    timeframeCode);

            if (historicalCandles.isPresent() && !historicalCandles.get().isEmpty()) {
                candles = new ArrayList<>(historicalCandles.get());
            } else {
                candles = generateSampleData(250, 100.0);
                Platform.runLater(() -> statusLabel.setText(
                        "Using sample data for " + displayTradePair(selectedPair)
                                + " because no historical data was found"));
            }

            if (candles.isEmpty()) {
                Platform.runLater(() -> {
                    statusLabel.setText("No data available for this period");
                    progressBar.setProgress(0);
                    backtestRunning.set(false);
                });
                return;
            }

            Platform.runLater(() -> statusLabel.setText("Executing strategy backtest..."));

            List<BacktestTrade> trades = executeBacktest(
                    strategyName,
                    selectedPair,
                    timeframeCode,
                    orderType,
                    candles);

            Map<String, Object> metrics = calculateMetrics(trades);

            Platform.runLater(() -> {
                tradesTable.setItems(FXCollections.observableArrayList(trades));
                updateEquityChart(trades);
                updateMetrics(metrics);

                statusLabel.setText(
                        "Backtest complete for "
                                + displayTradePair(selectedPair)
                                + " using "
                                + strategyName
                                + " / "
                                + orderType
                                + " ("
                                + trades.size()
                                + " trades)");

                progressBar.setProgress(1.0);
                backtestRunning.set(false);
            });

        } catch (Exception exception) {
            log.error("Backtest execution error", exception);
            Platform.runLater(() -> {
                statusLabel.setText("Error: " + exception.getMessage());
                progressBar.setProgress(0);
                backtestRunning.set(false);
            });
        }
    }

    private List<BacktestTrade> executeBacktest(
            String strategyName,
            TradePair selectedPair,
            String timeframeCode,
            MARKET_TYPES orderType,
            List<CandleData> candles) {
        List<BacktestTrade> trades = new ArrayList<>();

        if (strategyName == null || strategyName.isBlank()) {
            log.warn("Cannot run backtest: missing strategy name");
            return trades;
        }

        if (selectedPair == null) {
            log.warn("Cannot run backtest: missing TradePair");
            return trades;
        }

        if (candles == null || candles.size() < 50) {
            log.warn("Cannot run backtest: not enough candles");
            return trades;
        }

        TradingStrategy strategy = resolveStrategy(strategyName);

        if (strategy == null) {
            log.warn("Strategy not found: {}", strategyName);
            return trades;
        }

        Timeframe timeframe = resolveTimeframe(timeframeCode);

        boolean inPosition = false;
        Side positionSide = HOLD;

        double entryPrice = 0.0;
        double stopLoss = 0.0;
        double takeProfit = 0.0;
        double quantity = DEFAULT_QUANTITY;

        LocalDate entryDate = null;
        int entryIndex = -1;

        int warmupBars = Math.max(50, safeWarmupBars(strategy));

        for (int i = warmupBars; i < candles.size(); i++) {
            if (!backtestRunning.get()) {
                break;
            }

            List<CandleData> window = new ArrayList<>(candles.subList(0, i + 1));
            CandleData current = candles.get(i);

            if (current == null || current.closePrice() <= 0) {
                continue;
            }

            StrategyContext context = buildStrategyContext(
                    selectedPair,
                    timeframe,
                    window,
                    current.closePrice());

            StrategySignal signal;

            try {
                signal = strategy.generateSignal(context);
            } catch (Exception exception) {
                log.warn(
                        "Strategy {} failed during backtest for {} at candle {}: {}",
                        strategyName,
                        displayTradePair(selectedPair),
                        i,
                        exception.getMessage());
                continue;
            }

            if (signal == null || signal.getSide() == null) {
                updateProgress(i, candles.size());
                continue;
            }

            if (!inPosition) {
                if (signal.getSide() == BUY || signal.getSide() == SELL) {
                    Optional<Double> fillPrice = resolveEntryFillPrice(
                            orderType,
                            signal.getSide(),
                            signal,
                            current);

                    if (fillPrice.isEmpty()) {
                        updateProgress(i, candles.size());
                        continue;
                    }

                    entryPrice = fillPrice.get();
                    stopLoss = signal.getStopLossPrice();
                    takeProfit = signal.getTakeProfitPrice();

                    positionSide = signal.getSide();
                    entryDate = candleDate();
                    entryIndex = i;
                    inPosition = true;
                }
            } else {
                ExitDecision exit = shouldExitPosition(
                        positionSide,
                        current,
                        signal,
                        stopLoss,
                        takeProfit,
                        entryIndex,
                        i);

                if (exit.exit()) {
                    double exitPrice = exit.exitPrice();
                    double profit = calculateProfit(positionSide, entryPrice, exitPrice, quantity);
                    double returnPercent = entryPrice > 0.0
                            ? (profit / (entryPrice * quantity)) * 100.0
                            : 0.0;

                    trades.add(new BacktestTrade(
                            entryDate,
                            positionSide,
                            orderType,
                            entryPrice,
                            exitPrice,
                            quantity,
                            profit,
                            returnPercent,
                            exit.reason()));

                    inPosition = false;
                    positionSide = HOLD;
                    entryPrice = 0.0;
                    stopLoss = 0.0;
                    takeProfit = 0.0;
                    entryDate = LocalDate.now();
                    entryIndex = -1;
                }
            }

            updateProgress(i, candles.size());
        }

        return trades;
    }

    private TradingStrategy resolveStrategy(String strategyName) {
        try {
            TradingStrategy strategy = StrategyRegistry.getInstance().getStrategy(strategyName);

            if (strategy != null) {
                return strategy;
            }
        } catch (Exception exception) {
            log.debug("Could not resolve strategy from registry: {}", exception.getMessage());
        }

        try {
            return new UnifiedStrategy(strategyName);
        } catch (Exception exception) {
            log.warn("Could not create UnifiedStrategy for {}: {}", strategyName, exception.getMessage());
            return null;
        }
    }

    private StrategyContext buildStrategyContext(
            TradePair pair,
            Timeframe timeframe,
            List<CandleData> candles,
            double currentPrice) {
        return StrategyContext.builder()
                .symbol(pair)
                .timeframe(timeframe)
                .candles(candles)
                .currentPrice(currentPrice)
                .build();
    }

    private MARKET_TYPES resolveOrderType(String orderTypeText) {
        if (orderTypeText == null || orderTypeText.isBlank()) {
            return MARKET_TYPES.MARKET;
        }

        String normalized = orderTypeText.trim().toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "LIMIT" -> MARKET_TYPES.LIMIT;
            case "STOP_LIMIT" -> STOP_LIMIT;
            default -> MARKET_TYPES.MARKET;
        };
    }

    private Optional<Double> resolveEntryFillPrice(
            MARKET_TYPES orderType,
            Side side,
            StrategySignal signal,
            CandleData candle) {
        double requestedEntry = signal.getEntryPrice() > 0.0
                ? signal.getEntryPrice()
                : candle.closePrice();

        if (orderType == MARKET_TYPES.MARKET) {
            return Optional.of(candle.closePrice());
        }

        if (orderType == MARKET_TYPES.LIMIT) {
            if (side == BUY && candle.lowPrice() <= requestedEntry) {
                return Optional.of(requestedEntry);
            }

            if (side == SELL && candle.highPrice() >= requestedEntry) {
                return Optional.of(requestedEntry);
            }

            return Optional.empty();
        }

        if (orderType.equals(STOP_LIMIT)) {
            if (side == BUY && candle.highPrice() >= requestedEntry) {
                return Optional.of(requestedEntry);
            }

            if (side == SELL && candle.lowPrice() <= requestedEntry) {
                return Optional.of(requestedEntry);
            }

            return Optional.empty();
        }

        return Optional.of(candle.closePrice());
    }

    private ExitDecision shouldExitPosition(
            Side positionSide,
            CandleData current,
            StrategySignal latestSignal,
            double stopLoss,
            double takeProfit,
            int entryIndex,
            int currentIndex) {
        double high = current.highPrice();
        double low = current.lowPrice();
        double close = current.closePrice();

        if (positionSide == BUY) {
            if (stopLoss > 0.0 && low <= stopLoss) {
                return new ExitDecision(true, stopLoss, "Stop loss hit");
            }

            if (takeProfit > 0.0 && high >= takeProfit) {
                return new ExitDecision(true, takeProfit, "Take profit hit");
            }

            if (latestSignal != null && latestSignal.getSide() == SELL) {
                return new ExitDecision(true, close, "Opposite SELL signal");
            }
        }

        if (positionSide == SELL) {
            if (stopLoss > 0.0 && high >= stopLoss) {
                return new ExitDecision(true, stopLoss, "Stop loss hit");
            }

            if (takeProfit > 0.0 && low <= takeProfit) {
                return new ExitDecision(true, takeProfit, "Take profit hit");
            }

            if (latestSignal != null && latestSignal.getSide() == BUY) {
                return new ExitDecision(true, close, "Opposite BUY signal");
            }
        }

        if (entryIndex >= 0 && currentIndex - entryIndex >= MAX_BARS_HELD) {
            return new ExitDecision(true, close, "Max bars held");
        }

        return new ExitDecision(false, close, "");
    }

    private double calculateProfit(Side side, double entryPrice, double exitPrice, double quantity) {
        if (side == BUY) {
            return (exitPrice - entryPrice) * quantity;
        }

        if (side == SELL) {
            return (entryPrice - exitPrice) * quantity;
        }

        return 0.0;
    }

    private int safeWarmupBars(TradingStrategy strategy) {
        try {
            return Math.max(1, strategy.requiredWarmupBars());
        } catch (Exception ignored) {
            return 50;
        }
    }

    private Timeframe resolveTimeframe(String timeframeCode) {
        if (timeframeCode == null || timeframeCode.isBlank()) {
            return Timeframe.H1;
        }

        String normalized = timeframeCode.trim().toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "1M", "M1" -> Timeframe.M1;
            case "5M", "M5" -> Timeframe.M5;
            case "15M", "M15" -> Timeframe.M15;
            case "30M", "M30" -> Timeframe.M30;
            case "4H", "H4" -> Timeframe.H4;
            case "1D", "D1" -> Timeframe.D1;
            default -> Timeframe.H1;
        };
    }

    private void updateProgress(int index, int total) {
        if (total <= 0) {
            return;
        }

        double progress = (double) index / total;
        Platform.runLater(() -> progressBar.setProgress(progress));
    }

    private LocalDate candleDate() {
        return LocalDate.now();
    }

    private String displayTradePair(TradePair pair) {
        if (pair == null) {
            return "";
        }

        try {
            return pair.toString('/');
        } catch (Exception ignored) {
            return pair.toString();
        }
    }

    private @NotNull List<CandleData> generateSampleData(int count, double startPrice) {
        List<CandleData> candles = new ArrayList<>();
        double price = startPrice / 2;
        long timestamp = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            double change = (Math.random() - 0.48) * 2.0;
            price += change;

            double open = price - Math.random();
            double close = price + Math.random();
            double high = Math.max(open, close) + Math.random();
            double low = Math.min(open, close) - Math.random();

            candles.add(new CandleData(
                    open,
                    close,
                    high,
                    low,
                    (int) (timestamp + (i * 60_000L)),
                    1_000 + Math.random() * 5_000));
        }

        return candles;
    }

    private Map<String, Object> calculateMetrics(List<BacktestTrade> trades) {
        Map<String, Object> metrics = new HashMap<>();

        double totalProfit = 0.0;
        int winCount = 0;
        int lossCount = 0;
        double grossProfit = 0.0;
        double grossLoss = 0.0;

        double equity = INITIAL_EQUITY;
        double peakEquity = INITIAL_EQUITY;
        double maxDrawdownPercent = 0.0;

        for (BacktestTrade trade : trades) {
            double profit = trade.getProfit();

            totalProfit += profit;
            equity += profit;

            if (equity > peakEquity) {
                peakEquity = equity;
            }

            double drawdownPercent = (peakEquity - equity) / peakEquity * 100.0;

            maxDrawdownPercent = Math.max(maxDrawdownPercent, drawdownPercent);

            if (profit > 0.0) {
                winCount++;
                grossProfit += profit;
            } else if (profit < 0.0) {
                lossCount++;
                grossLoss += Math.abs(profit);
            }
        }

        double totalTrades = winCount + lossCount;
        double winRate = totalTrades > 0.0 ? (winCount / totalTrades) * 100.0 : 0.0;
        double returnPercent = (totalProfit / INITIAL_EQUITY) * 100.0;
        double profitFactor = grossLoss > 0.0 ? grossProfit / grossLoss : (grossProfit > 0.0 ? 999.0 : 0.0);
        double sharpeApproximation = maxDrawdownPercent > 0.0
                ? returnPercent / maxDrawdownPercent
                : returnPercent;

        metrics.put("totalReturn", returnPercent);
        metrics.put("winRate", winRate);
        metrics.put("maxDrawdown", maxDrawdownPercent);
        metrics.put("sharpeRatio", sharpeApproximation);
        metrics.put("profitFactor", profitFactor);

        return metrics;
    }

    private void updateMetrics(Map<String, Object> metrics) {
        totalReturnLabel.setText(String.format("%.2f%%", asDouble(metrics.get("totalReturn"))));
        winRateLabel.setText(String.format("%.2f%%", asDouble(metrics.get("winRate"))));
        maxDrawdownLabel.setText(String.format("-%.2f%%", asDouble(metrics.get("maxDrawdown"))));
        sharpeLabel.setText(String.format("%.2f", asDouble(metrics.get("sharpeRatio"))));
        profitFactorLabel.setText(String.format("%.2f", asDouble(metrics.get("profitFactor"))));
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return 0.0;
    }

    private void updateEquityChart(List<BacktestTrade> trades) {
        equityCurveChart.getData().clear();

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Equity");

        double equity = INITIAL_EQUITY;

        series.getData().add(new XYChart.Data<>(0, equity));

        for (int i = 0; i < trades.size(); i++) {
            BacktestTrade trade = trades.get(i);
            equity += trade.getProfit();
            series.getData().add(new XYChart.Data<>(i + 1, equity));
        }

        equityCurveChart.getData().add(series);
    }

    private void exportResults() {
        statusLabel.setText("Export feature coming soon...");
    }

    private @NotNull HBox createLabeledInput(String label, @NotNull Node input) {
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

    private record ExitDecision(boolean exit, double exitPrice, String reason) {
    }

    /**
     * BacktestTrade stores one completed simulated trade.
     */
    @Data
    @AllArgsConstructor
    public static class BacktestTrade {
        private LocalDate date;
        private Side side;
        private MARKET_TYPES orderType;
        private double price;
        private double exitPrice;
        private double quantity;
        private double profit;
        private double returnPercent;
        private String reason;
    }
}