package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
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

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.investpro.utils.MARKET_TYPES.STOP_LIMIT;
import static org.investpro.utils.Side.BUY;
import static org.investpro.utils.Side.HOLD;
import static org.investpro.utils.Side.SELL;

/**
 * Backtesting Panel.
 *
 * Executes historical strategy backtests using:
 * - selected TradePair
 * - selected strategy
 * - selected timeframe
 * - selected order type: MARKET, LIMIT, STOP_LIMIT
 *
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
    private ComboBox<Timeframe> timeframeCombo;
    private ComboBox<String> orderTypeCombo;

    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private Spinner<Integer> barCountSpinner;

    private Label statusLabel;
    private ProgressBar progressBar;

    private TableView<BacktestTrade> tradesTable;

    private Label totalReturnLabel;
    private Label winRateLabel;
    private Label maxDrawdownLabel;
    private Label sharpeLabel;
    private Label profitFactorLabel;
    private Label dataQualityLabel;

    private AreaChart<Number, Number> equityCurveChart;

    private Button compareBtn;
    private Button exportBtn;

    private final SymbolAgentManager symbolAgentManager;
    private final HistoricalDataRepository historicalDataRepository;
    private final SystemCore systemCore;
    private final AtomicBoolean backtestRunning = new AtomicBoolean(false);

    private static final double INITIAL_EQUITY = 10_000.0;
    private static final double DEFAULT_QUANTITY = 1.0;
    private static final int MAX_BARS_HELD = 50;

    /**
     * Extra bars needed after strategy warmup so the strategy has enough room
     * to actually enter/exit trades.
     */
    private static final int MIN_TESTING_BARS_AFTER_WARMUP = 100;

    /**
     * Minimum absolute candles for any meaningful test.
     */
    private static final int MIN_ABSOLUTE_TEST_BARS = 150;

    public BacktestingPanel() {
        this(null, null, null);
    }

    public BacktestingPanel(SystemCore systemCore) {
        this(
                systemCore != null && systemCore.getSmartBot() != null
                        ? systemCore.getSmartBot().getSymbolAgentManager()
                        : null,
                HistoricalDataRepositoryImpl.getInstance(),
                systemCore
        );
    }

    public BacktestingPanel(
            SymbolAgentManager symbolAgentManager,
            HistoricalDataRepository historicalDataRepository,
            SystemCore systemCore
    ) {
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

    private @NotNull VBox createConfigurationSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #16213e; " +
                        "-fx-border-color: #3b82f6; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-radius: 6;"
        );

        Label sectionTitle = new Label("Backtest Configuration");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        strategyCombo = new ComboBox<>();
        List<String> strategies = loadStrategyNames();
        strategyCombo.setItems(FXCollections.observableArrayList(strategies));
        strategyCombo.setValue(strategies.isEmpty() ? "Trend Following" : strategies.get(0));
        strategyCombo.setPrefHeight(35);
        HBox strategyBox = createLabeledInput("Strategy:", strategyCombo);

        symbolCombo = new ComboBox<>();
        loadSymbols();
        symbolCombo.setPrefHeight(35);
        HBox symbolBox = createLabeledInput("Symbol:", symbolCombo);

        timeframeCombo = new ComboBox<>();
        timeframeCombo.getItems().setAll(loadSupportedTimeframes());
        timeframeCombo.setValue(
                timeframeCombo.getItems().contains(Timeframe.H1)
                        ? Timeframe.H1
                        : timeframeCombo.getItems().isEmpty()
                        ? null
                        : timeframeCombo.getItems().get(0)
        );
        timeframeCombo.setPrefHeight(35);
        timeframeCombo.setCellFactory(comboBox -> new ListCell<>() {
            @Override
            protected void updateItem(Timeframe item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayTimeframe(item));
            }
        });
        timeframeCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Timeframe item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayTimeframe(item));
            }
        });
        HBox timeframeBox = createLabeledInput("Timeframe:", timeframeCombo);

        orderTypeCombo = new ComboBox<>();
        orderTypeCombo.setItems(FXCollections.observableArrayList("MARKET", "LIMIT", "STOP_LIMIT"));
        orderTypeCombo.setValue("MARKET");
        orderTypeCombo.setPrefHeight(35);
        HBox orderTypeBox = createLabeledInput("Order Type:", orderTypeCombo);

        startDatePicker = new DatePicker(LocalDate.now().minusYears(2));
        startDatePicker.setPrefHeight(35);
        HBox startDateBox = createLabeledInput("Start Date:", startDatePicker);

        endDatePicker = new DatePicker(LocalDate.now());
        endDatePicker.setPrefHeight(35);
        HBox endDateBox = createLabeledInput("End Date:", endDatePicker);

        barCountSpinner = new Spinner<>(MIN_ABSOLUTE_TEST_BARS, 100_000, 2_000, 100);
        barCountSpinner.setPrefHeight(35);
        barCountSpinner.setEditable(true);
        barCountSpinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
        HBox barCountBox = createLabeledInput("Number of Bars:", barCountSpinner);

        Button runBacktestBtn = new Button("▶ Run Backtest");
        runBacktestBtn.setStyle(primaryButtonStyle("#10b981"));
        runBacktestBtn.setOnAction(event -> runBacktestAsync());

        compareBtn = new Button("📊 Compare");
        compareBtn.setStyle(primaryButtonStyle("#3b82f6"));
        compareBtn.setDisable(true);
        compareBtn.setOnAction(event -> compareResults());

        Button cancelBtn = new Button("✕ Cancel");
        cancelBtn.setStyle(primaryButtonStyle("#ef4444"));
        cancelBtn.setOnAction(event -> {
            backtestRunning.set(false);
            statusLabel.setText("Cancel requested...");
        });

        statusLabel = new Label("Ready to backtest");
        statusLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 12px;");

        dataQualityLabel = new Label("Data quality: waiting for test");
        dataQualityLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

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
                barCountBox,
                buttonBox,
                statusLabel,
                dataQualityLabel,
                progressBar
        );

        return section;
    }

    private VBox createResultsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #16213e; " +
                        "-fx-border-color: #10b981; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-radius: 6;"
        );

        Label sectionTitle = new Label("Backtest Results");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        HBox metricsBox = createMetricsBox();

        equityCurveChart = createEquityCurveChart();

        tradesTable = createTradesTable();
        ScrollPane tradesScrollPane = new ScrollPane(tradesTable);
        tradesScrollPane.setFitToWidth(true);
        tradesScrollPane.setPrefHeight(240);

        exportBtn = new Button("⬇ Export Results");
        exportBtn.setStyle(primaryButtonStyle("#6366f1"));
        exportBtn.setDisable(true);
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
                exportBtn
        );

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
        reasonCol.setPrefWidth(240);

        table.getColumns().addAll(
                dateCol,
                sideCol,
                orderTypeCol,
                entryCol,
                exitCol,
                pnlCol,
                returnCol,
                reasonCol
        );

        return table;
    }

    private @NotNull List<String> loadStrategyNames() {
        try {
            List<String> strategies = new ArrayList<>(StrategyCatalog.availableStrategyNames());

            if (!strategies.isEmpty()) {
                return strategies;
            }
        } catch (Exception exception) {
            log.debug("StrategyCatalog.availableStrategyNames() unavailable: {}", exception.getMessage());
        }

        try {
            return new ArrayList<>(StrategyCatalog.CORE_STRATEGY_NAMES);
        } catch (Exception exception) {
            return List.of("Trend Following", "Mean Reversion", "Breakout");
        }
    }

    private List<Timeframe> loadSupportedTimeframes() {
        if (systemCore != null) {
            try {
                if (systemCore.getExchange() != null && systemCore.getExchange().getSupportedTimeframes() != null) {
                    List<Timeframe> supported = new ArrayList<>(systemCore.getExchange().getSupportedTimeframes());

                    if (!supported.isEmpty()) {
                        return supported;
                    }
                }
            } catch (Exception exception) {
                log.warn("Failed to load supported timeframes from exchange: {}", exception.getMessage());
            }
        }

        return List.of(
                Timeframe.M1,
                Timeframe.M5,
                Timeframe.M15,
                Timeframe.M30,
                Timeframe.H1,
                Timeframe.H4,
                Timeframe.D1
        );
    }

    private void loadSymbols() {
        List<TradePair> symbols = new ArrayList<>();

        SymbolAgentManager manager = symbolAgentManager;

        if (manager == null && systemCore != null) {
            try {
                if (systemCore.getSmartBot() != null) {
                    manager = systemCore.getSmartBot().getSymbolAgentManager();
                }
            } catch (Exception exception) {
                log.debug("Unable to read SymbolAgentManager from SystemCore: {}", exception.getMessage());
            }
        }

        if (manager != null) {
            try {
                symbols.addAll(
                        manager.getAllStates()
                                .stream()
                                .map(SymbolAgentState::getSymbol)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList()
                );
            } catch (Exception exception) {
                log.warn("Failed to load symbols from SymbolAgentManager: {}", exception.getMessage());
            }
        }

        if (symbols.isEmpty() && systemCore != null) {
            try {
                if (systemCore.getExchange() != null && systemCore.getExchange().getTradablePairs() != null) {
                    symbols.addAll(systemCore.getExchange().getTradablePairs());
                }
            } catch (Exception exception) {
                log.warn("Failed to load symbols from exchange: {}", exception.getMessage());
            }
        }

        if (symbols.isEmpty()) {
            try {
                symbols.addAll(List.of(
                        TradePair.of("BTC", "USD"),
                        TradePair.of("ETH", "USD"),
                        TradePair.of("EUR", "USD"),
                        TradePair.of("GBP", "USD"),
                        TradePair.of("USD", "JPY")
                ));
            } catch (Exception exception) {
                log.warn("Failed to create fallback TradePair list: {}", exception.getMessage());
            }
        }

        symbolCombo.setItems(FXCollections.observableArrayList(symbols));

        if (!symbols.isEmpty()) {
            symbolCombo.setValue(symbols.get(0));
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

        if (exportBtn != null) {
            exportBtn.setDisable(true);
        }
        if (compareBtn != null) {
            compareBtn.setDisable(true);
        }

        Thread backTestThread = new Thread(() -> {
            try {
                runBacktest();
            } catch (Exception exception) {
                log.error("Backtest error", exception);
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + exception.getMessage());
                    dataQualityLabel.setText("Data quality: failed");
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
            Timeframe selectedTimeframe = timeframeCombo.getValue();
            MARKET_TYPES orderType = resolveOrderType(orderTypeCombo.getValue());

            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();

            if (strategyName == null || strategyName.isBlank() || selectedPair == null || selectedTimeframe == null) {
                Platform.runLater(() -> {
                    statusLabel.setText("Please select strategy, symbol, and timeframe");
                    progressBar.setProgress(0);
                    backtestRunning.set(false);
                });
                return;
            }

            if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
                Platform.runLater(() -> {
                    statusLabel.setText("Invalid date range");
                    progressBar.setProgress(0);
                    backtestRunning.set(false);
                });
                return;
            }

            String timeframeCode = selectedTimeframe.getCode();
            int requestedBars = Math.max(MIN_ABSOLUTE_TEST_BARS, barCountSpinner.getValue());

            Platform.runLater(() -> statusLabel.setText(
                    "Loading historical data for " + displayTradePair(selectedPair) + " " + timeframeCode
            ));

            List<CandleData> candles = loadEnoughCandles(
                    selectedPair,
                    selectedTimeframe,
                    startDate,
                    endDate,
                    requestedBars
            );

            TradingStrategy strategy = resolveStrategy(strategyName);
            int warmupBars = strategy == null ? 50 : safeWarmupBars(strategy);

            DataReadiness readiness = evaluateDataReadiness(candles, warmupBars, requestedBars);

            Platform.runLater(() -> dataQualityLabel.setText(readiness.message()));

            if (!readiness.ready()) {
                Platform.runLater(() -> {
                    statusLabel.setText(readiness.message());
                    progressBar.setProgress(0);
                    backtestRunning.set(false);
                });
                return;
            }

            if (candles.size() > requestedBars) {
                candles = new ArrayList<>(candles.subList(candles.size() - requestedBars, candles.size()));
            }

            List<CandleData> finalCandles = candles;
            Platform.runLater(() -> statusLabel.setText(
                    "Executing strategy backtest with " + finalCandles.size() + " candles..."
            ));

            List<BacktestTrade> trades = executeBacktest(
                    strategyName,
                    selectedPair,
                    timeframeCode,
                    orderType,
                    candles
            );

            Map<String, Object> metrics = calculateMetrics(trades);

            Platform.runLater(() -> {
                tradesTable.setItems(FXCollections.observableArrayList(trades));
                updateEquityChart(trades);
                updateMetrics(metrics);

                if (exportBtn != null) {
                    exportBtn.setDisable(false);
                }
                if (compareBtn != null) {
                    compareBtn.setDisable(false);
                }

                statusLabel.setText(
                        "Backtest complete for "
                                + displayTradePair(selectedPair)
                                + " using "
                                + strategyName
                                + " / "
                                + orderType
                                + " ("
                                + trades.size()
                                + " trades)"
                );

                progressBar.setProgress(1.0);
                backtestRunning.set(false);
            });

        } catch (Exception exception) {
            log.error("Backtest execution error", exception);
            Platform.runLater(() -> {
                statusLabel.setText("Error: " + exception.getMessage());
                dataQualityLabel.setText("Data quality: error");
                progressBar.setProgress(0);
                backtestRunning.set(false);
            });
        }
    }

    /**
     * Load requested historical candles. If the selected range does not provide enough candles,
     * automatically expands the request range backward.
     */
    private List<CandleData> loadEnoughCandles(
            TradePair pair,
            Timeframe timeframe,
            LocalDate startDate,
            LocalDate endDate,
            int requestedBars
    ) {
        String timeframeCode = timeframe.getCode();

        List<CandleData> candles = loadHistoricalCandles(
                pair,
                startDate.atStartOfDay(),
                endDate.atTime(23, 59, 59),
                timeframeCode
        );

        if (candles.size() >= requestedBars) {
            return candles;
        }

        int estimatedDaysNeeded = estimateDaysNeeded(timeframe, requestedBars);
        LocalDate expandedStart = endDate.minusDays(Math.max(estimatedDaysNeeded, 365));

        Platform.runLater(() -> statusLabel.setText(
                "Not enough data in selected range. Expanding lookup to " + expandedStart + "..."
        ));

        List<CandleData> expandedCandles = loadHistoricalCandles(
                pair,
                expandedStart.atStartOfDay(),
                endDate.atTime(23, 59, 59),
                timeframeCode
        );

        if (expandedCandles.size() > candles.size()) {
            candles = expandedCandles;
        }

        if (candles.size() < requestedBars) {
            int sampleCount = Math.max(requestedBars, MIN_ABSOLUTE_TEST_BARS + 100);

            Platform.runLater(() -> statusLabel.setText(
                    "Historical data still insufficient. Using sample data with " + sampleCount + " bars."
            ));

            candles = generateSampleData(sampleCount, 100.0, timeframe);
        }

        return candles;
    }

    private List<CandleData> loadHistoricalCandles(
            TradePair pair,
            LocalDateTime start,
            LocalDateTime end,
            String timeframeCode
    ) {
        try {
            Optional<List<CandleData>> historicalCandles = historicalDataRepository.getHistoricalData(
                    pair,
                    start,
                    end,
                    timeframeCode
            );

            if (historicalCandles.isPresent() && !historicalCandles.get().isEmpty()) {
                return historicalCandles.get()
                        .stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingLong(this::candleTimestamp))
                        .toList();
            }

        } catch (Exception exception) {
            log.warn("Failed to load historical candles: {}", exception.getMessage());
        }

        return List.of();
    }

    private int estimateDaysNeeded(Timeframe timeframe, int bars) {
        if (timeframe == null) {
            return 365;
        }

        String code = timeframe.getCode() == null ? "" : timeframe.getCode().toLowerCase(Locale.ROOT);

        return switch (code) {
            case "1m", "m1" -> Math.max(7, bars / 1_000 + 7);
            case "5m", "m5" -> Math.max(14, bars / 300 + 14);
            case "15m", "m15" -> Math.max(30, bars / 100 + 30);
            case "30m", "m30" -> Math.max(60, bars / 50 + 60);
            case "1h", "h1" -> Math.max(120, bars / 24 + 120);
            case "4h", "h4" -> Math.max(365, bars / 6 + 365);
            case "1d", "d1" -> Math.max(730, bars + 365);
            default -> 730;
        };
    }

    private DataReadiness evaluateDataReadiness(
            List<CandleData> candles,
            int warmupBars,
            int requestedBars
    ) {
        int actualBars = candles == null ? 0 : candles.size();
        int minimumRequired = Math.max(MIN_ABSOLUTE_TEST_BARS, warmupBars + MIN_TESTING_BARS_AFTER_WARMUP);

        if (actualBars < minimumRequired) {
            return new DataReadiness(
                    false,
                    "Not enough data for proper testing. Need at least "
                            + minimumRequired
                            + " candles. Available: "
                            + actualBars
                            + "."
            );
        }

        if (actualBars < requestedBars) {
            return new DataReadiness(
                    true,
                    "Data quality: usable but below requested bars. Requested "
                            + requestedBars
                            + ", available "
                            + actualBars
                            + "."
            );
        }

        return new DataReadiness(
                true,
                "Data quality: ready. Available candles: "
                        + actualBars
                        + ", warmup: "
                        + warmupBars
                        + ", requested: "
                        + requestedBars
                        + "."
        );
    }

    private List<BacktestTrade> executeBacktest(
            String strategyName,
            TradePair selectedPair,
            String timeframeCode,
            MARKET_TYPES orderType,
            List<CandleData> candles
    ) {
        List<BacktestTrade> trades = new ArrayList<>();

        if (strategyName == null || strategyName.isBlank()) {
            log.warn("Cannot run backtest: missing strategy name");
            return trades;
        }

        if (selectedPair == null) {
            log.warn("Cannot run backtest: missing TradePair");
            return trades;
        }

        if (candles == null || candles.size() < MIN_ABSOLUTE_TEST_BARS) {
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
                updateProgress(i, candles.size());
                continue;
            }

            StrategyContext context = buildStrategyContext(
                    selectedPair,
                    timeframe,
                    window,
                    current.closePrice()
            );

            StrategySignal signal;

            try {
                signal = strategy.generateSignal(context);
            } catch (Exception exception) {
                log.warn(
                        "Strategy {} failed during backtest for {} at candle {}: {}",
                        strategyName,
                        displayTradePair(selectedPair),
                        i,
                        exception.getMessage()
                );
                updateProgress(i, candles.size());
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
                            current
                    );

                    if (fillPrice.isEmpty()) {
                        updateProgress(i, candles.size());
                        continue;
                    }

                    entryPrice = fillPrice.get();
                    stopLoss = getSignalStopLoss(signal);
                    takeProfit = getSignalTakeProfit(signal);

                    positionSide = signal.getSide();
                    entryDate = candleDate(current);
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
                        i
                );

                if (exit.exit()) {
                    double exitPrice = exit.exitPrice();
                    double profit = calculateProfit(positionSide, entryPrice, exitPrice, quantity);
                    double returnPercent = entryPrice > 0.0
                            ? (profit / (entryPrice * quantity)) * 100.0
                            : 0.0;

                    trades.add(new BacktestTrade(
                            entryDate == null ? candleDate(current) : entryDate,
                            positionSide,
                            orderType,
                            entryPrice,
                            exitPrice,
                            quantity,
                            cleanNumber(profit),
                            cleanNumber(returnPercent),
                            exit.reason()
                    ));

                    inPosition = false;
                    positionSide = HOLD;
                    entryPrice = 0.0;
                    stopLoss = 0.0;
                    takeProfit = 0.0;
                    entryDate = null;
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
            double currentPrice
    ) {
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
            case "STOP_LIMIT", "STOP LIMIT" -> STOP_LIMIT;
            default -> MARKET_TYPES.MARKET;
        };
    }

    private Optional<Double> resolveEntryFillPrice(
            MARKET_TYPES orderType,
            Side side,
            StrategySignal signal,
            CandleData candle
    ) {
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

    private @NotNull ExitDecision shouldExitPosition(
            Side positionSide,
            @NotNull CandleData current,
            StrategySignal latestSignal,
            double stopLoss,
            double takeProfit,
            int entryIndex,
            int currentIndex
    ) {
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

    private double getSignalStopLoss(StrategySignal signal) {
        if (signal == null) {
            return 0.0;
        }

        try {
            return signal.getStopLossPrice();
        } catch (Exception ignored) {
            try {
                return signal.getStopLossPrice();
            } catch (Exception ignoredAgain) {
                return 0.0;
            }
        }
    }

    private double getSignalTakeProfit(StrategySignal signal) {
        if (signal == null) {
            return 0.0;
        }

        try {
            return signal.getTakeProfitPrice();
        } catch (Exception ignored) {
            try {
                return signal.getTakeProfitPrice();
            } catch (Exception ignoredAgain) {
                return 0.0;
            }
        }
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

    private LocalDate candleDate(CandleData candle) {
        if (candle == null) {
            return LocalDate.now();
        }

        try {
            long timestamp = candleTimestamp(candle);

            if (timestamp > 0 && timestamp < 10_000_000_000L) {
                timestamp *= 1000L;
            }

            return Instant.ofEpochMilli(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        } catch (Exception ignored) {
            return LocalDate.now();
        }
    }

    private long candleTimestamp(CandleData candle) {
        if (candle == null) {
            return 0L;
        }

        try {
            return candle.timestamp().getEpochSecond();
        } catch (Exception ignored) {
            return 0L;
        }
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

    private String displayTimeframe(Timeframe timeframe) {
        if (timeframe == null) {
            return "";
        }

        try {
            return timeframe.getDisplayName();
        } catch (Exception ignored) {
            try {
                return timeframe.getCode();
            } catch (Exception ignoredAgain) {
                return timeframe.name();
            }
        }
    }

    private @NotNull List<CandleData> generateSampleData(int count, double startPrice, Timeframe timeframe) {
        List<CandleData> candles = new ArrayList<>();
        double price = startPrice;
        long timestampSeconds = System.currentTimeMillis() / 1000L;
        long stepSeconds = timeframeStepSeconds(timeframe);

        for (int i = 0; i < count; i++) {
            double change = (Math.random() - 0.48) * 2.0;
            price = Math.max(0.0001, price + change);

            double open = price - Math.random();
            double close = price + Math.random();
            double high = Math.max(open, close) + Math.random();
            double low = Math.max(0.0001, Math.min(open, close) - Math.random());

            candles.add(new CandleData(
                    open,
                    close,
                    high,
                    low,
                    (int) (timestampSeconds + (i * stepSeconds)),
                    1_000 + Math.random() * 5_000
            ));
        }

        return candles;
    }

    private long timeframeStepSeconds(Timeframe timeframe) {
        if (timeframe == null || timeframe.getCode() == null) {
            return 3600;
        }

        return switch (timeframe.getCode().toLowerCase(Locale.ROOT)) {
            case "1m", "m1" -> 60;
            case "5m", "m5" -> 300;
            case "15m", "m15" -> 900;
            case "30m", "m30" -> 1800;
            case "4h", "h4" -> 14_400;
            case "1d", "d1" -> 86_400;
            default -> 3600;
        };
    }

    private @NotNull Map<String, Object> calculateMetrics(List<BacktestTrade> trades) {
        Map<String, Object> metrics = new HashMap<>();

        if (trades == null || trades.isEmpty()) {
            metrics.put("totalReturn", 0.0);
            metrics.put("winRate", 0.0);
            metrics.put("maxDrawdown", 0.0);
            metrics.put("sharpeRatio", 0.0);
            metrics.put("profitFactor", 0.0);
            return metrics;
        }

        double totalProfit = 0.0;
        int winCount = 0;
        int lossCount = 0;
        double grossProfit = 0.0;
        double grossLoss = 0.0;

        double equity = INITIAL_EQUITY;
        double peakEquity = INITIAL_EQUITY;
        double maxDrawdownPercent = 0.0;

        for (BacktestTrade trade : trades) {
            if (trade == null) {
                continue;
            }

            double profit = cleanNumber(trade.getProfit());

            totalProfit += profit;
            equity += profit;

            if (equity > peakEquity) {
                peakEquity = equity;
            }

            double drawdownPercent = peakEquity > 0.0
                    ? ((peakEquity - equity) / peakEquity) * 100.0
                    : 0.0;

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

        double profitFactor = 0.0;
        if (grossLoss > 0.0) {
            profitFactor = grossProfit / grossLoss;
        } else if (grossProfit > 0.0) {
            profitFactor = 10.0;
        }

        double sharpeApproximation = 0.0;
        if (maxDrawdownPercent > 0.0) {
            sharpeApproximation = returnPercent / maxDrawdownPercent;
        } else if (returnPercent > 0.0) {
            sharpeApproximation = 10.0;
        }

        metrics.put("totalReturn", cleanNumber(returnPercent));
        metrics.put("winRate", cleanNumber(winRate));
        metrics.put("maxDrawdown", cleanNumber(maxDrawdownPercent));
        metrics.put("sharpeRatio", cleanNumber(sharpeApproximation));
        metrics.put("profitFactor", cleanNumber(profitFactor));

        return metrics;
    }

    private void updateMetrics(@NotNull Map<String, Object> metrics) {
        totalReturnLabel.setText(formatMetricValue(metrics.get("totalReturn"), true, false));
        winRateLabel.setText(formatMetricValue(metrics.get("winRate"), true, false));
        maxDrawdownLabel.setText(formatMetricValue(metrics.get("maxDrawdown"), true, true));
        sharpeLabel.setText(formatMetricValue(metrics.get("sharpeRatio"), false, false));
        profitFactorLabel.setText(formatMetricValue(metrics.get("profitFactor"), false, false));
    }

    private @NotNull String formatMetricValue(Object value, boolean isPercent, boolean forceNegativePrefix) {
        double numValue = cleanNumber(asDouble(value));

        if (isPercent) {
            if (forceNegativePrefix && numValue > 0) {
                return String.format(Locale.ROOT, "-%.2f%%", numValue);
            }

            return String.format(Locale.ROOT, "%.2f%%", numValue);
        }

        return String.format(Locale.ROOT, "%.2f", numValue);
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return 0.0;
    }

    private double cleanNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }

        return value;
    }

    private void updateEquityChart(List<BacktestTrade> trades) {
        equityCurveChart.getData().clear();

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Equity");

        double equity = INITIAL_EQUITY;

        series.getData().add(new XYChart.Data<>(0, equity));

        if (trades != null) {
            for (int i = 0; i < trades.size(); i++) {
                BacktestTrade trade = trades.get(i);
                if (trade == null) {
                    continue;
                }

                equity += cleanNumber(trade.getProfit());
                series.getData().add(new XYChart.Data<>(i + 1, equity));
            }
        }

        equityCurveChart.getData().add(series);
    }

    private void exportResults() {
        try {
            if (tradesTable.getItems().isEmpty()) {
                showAlert("No Results", "No trades to export. Please run a backtest first.", Alert.AlertType.WARNING);
                return;
            }

            StringBuilder csvContent = buildCsvContent();

            TradePair symbol = symbolCombo.getValue();
            String strategy = strategyCombo.getValue();
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

            String safeSymbol = symbol != null
                    ? symbol.toString().replace("/", "").replace("\\", "").replace(":", "")
                    : "unknown";

            String safeStrategy = strategy == null
                    ? "strategy"
                    : strategy.replaceAll("[^a-zA-Z0-9._-]", "_");

            String filename = String.format(
                    "backtest_%s_%s_%s.csv",
                    safeSymbol,
                    safeStrategy,
                    timestamp
            );

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Backtest Results");
            fileChooser.setInitialFileName(filename);
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );

            File file = fileChooser.showSaveDialog(this.getScene().getWindow());

            if (file != null) {
                java.nio.file.Files.writeString(file.toPath(), csvContent.toString());
                statusLabel.setText("✓ Results exported to: " + file.getName());
                showAlert(
                        "Export Successful",
                        "Backtest results exported to:\n" + file.getAbsolutePath(),
                        Alert.AlertType.INFORMATION
                );
                log.info("Backtest results exported to: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Failed to export results", e);
            showAlert("Export Error", "Failed to export results: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private @NotNull StringBuilder buildCsvContent() {
        StringBuilder csvContent = new StringBuilder();
        csvContent.append("Date,Side,Order Type,Entry Price,Exit Price,Quantity,Profit,Return %,Reason\n");

        for (BacktestTrade trade : tradesTable.getItems()) {
            if (trade == null) {
                continue;
            }

            csvContent.append(String.format(
                    Locale.ROOT,
                    "%s,%s,%s,%.5f,%.5f,%.2f,%.2f,%.2f,%s\n",
                    trade.getDate(),
                    trade.getSide(),
                    trade.getOrderType(),
                    trade.getPrice(),
                    trade.getExitPrice(),
                    trade.getQuantity(),
                    trade.getProfit(),
                    trade.getReturnPercent(),
                    sanitizeCsv(trade.getReason())
            ));
        }

        csvContent.append("\n\nMetrics Summary\n");
        csvContent.append(String.format("Total Return,%s\n", totalReturnLabel.getText()));
        csvContent.append(String.format("Win Rate,%s\n", winRateLabel.getText()));
        csvContent.append(String.format("Max Drawdown,%s\n", maxDrawdownLabel.getText()));
        csvContent.append(String.format("Sharpe Ratio,%s\n", sharpeLabel.getText()));
        csvContent.append(String.format("Profit Factor,%s\n", profitFactorLabel.getText()));

        return csvContent;
    }

    private String sanitizeCsv(String value) {
        if (value == null) {
            return "";
        }

        return value.replace(",", " ").replace("\n", " ").trim();
    }

    private void compareResults() {
        try {
            if (tradesTable.getItems().isEmpty()) {
                showAlert(
                        "No Results",
                        "No backtest results to compare. Please run a backtest first.",
                        Alert.AlertType.WARNING
                );
                return;
            }

            Stage comparisonStage = new Stage();
            comparisonStage.setTitle("Backtest Comparison Results");
            comparisonStage.setWidth(700);
            comparisonStage.setHeight(900);
            comparisonStage.initStyle(javafx.stage.StageStyle.DECORATED);

            VBox mainBox = new VBox(15);
            mainBox.setPadding(new Insets(20));
            mainBox.setStyle("-fx-background-color: #0f172a;");

            Label titleLabel = new Label("Backtest Performance Analysis");
            titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

            VBox paramBox = createParametersSection();
            VBox metricsBox = createMetricsSection();
            VBox tradeBox = createTradeSummarySection();

            Button closeBtn = new Button("✕ Close");
            closeBtn.setStyle(primaryButtonStyle("#ef4444"));
            closeBtn.setOnAction(e -> comparisonStage.close());

            HBox buttonBox = new HBox(10);
            buttonBox.setAlignment(Pos.CENTER);
            buttonBox.getChildren().add(closeBtn);

            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setContent(new VBox(15, paramBox, metricsBox, tradeBox));
            scrollPane.setFitToWidth(true);
            scrollPane.setStyle("-fx-background-color: #0f172a; -fx-control-inner-background: #0f172a;");

            mainBox.getChildren().addAll(
                    titleLabel,
                    new Separator(),
                    scrollPane,
                    new Separator(),
                    buttonBox
            );

            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            Scene scene = new Scene(mainBox);
            comparisonStage.setScene(scene);
            comparisonStage.show();

            log.info("Backtest comparison displayed for: {}", symbolCombo.getValue());
        } catch (Exception e) {
            log.error("Failed to display comparison", e);
            showAlert("Comparison Error", "Failed to display comparison: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private VBox createParametersSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #1e293b; " +
                        "-fx-border-color: #3b82f6; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-radius: 6;"
        );

        Label sectionTitle = new Label("Test Parameters");
        sectionTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #60a5fa;");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);

        Timeframe tf = timeframeCombo.getValue();

        addParameterRow(grid, 0, "Symbol:", symbolCombo.getValue() != null ? displayTradePair(symbolCombo.getValue()) : "N/A");
        addParameterRow(grid, 1, "Strategy:", strategyCombo.getValue());
        addParameterRow(grid, 2, "Timeframe:", tf == null ? "N/A" : displayTimeframe(tf));
        addParameterRow(grid, 3, "Number of Bars:", String.valueOf(barCountSpinner.getValue()));
        addParameterRow(grid, 4, "Data Quality:", dataQualityLabel.getText());

        section.getChildren().addAll(sectionTitle, grid);
        return section;
    }

    private VBox createMetricsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #1e293b; " +
                        "-fx-border-color: #10b981; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-radius: 6;"
        );

        Label sectionTitle = new Label("Performance Metrics");
        sectionTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #10b981;");

        GridPane grid = new GridPane();
        grid.setHgap(30);
        grid.setVgap(12);

        addMetricRow(grid, 0, "Total Return:", validateDisplayValue(totalReturnLabel.getText(), "0.00%"), "#10b981");
        addMetricRow(grid, 1, "Win Rate:", validateDisplayValue(winRateLabel.getText(), "0.00%"), "#3b82f6");
        addMetricRow(grid, 2, "Max Drawdown:", validateDisplayValue(maxDrawdownLabel.getText(), "0.00%"), "#f59e0b");
        addMetricRow(grid, 3, "Sharpe Ratio:", validateDisplayValue(sharpeLabel.getText(), "0.00"), "#8b5cf6");
        addMetricRow(grid, 4, "Profit Factor:", validateDisplayValue(profitFactorLabel.getText(), "0.00"), "#ec4899");

        section.getChildren().addAll(sectionTitle, grid);
        return section;
    }

    private String validateDisplayValue(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        if (value.contains("NaN") || value.contains("Infinity")) {
            return defaultValue;
        }

        try {
            String numPart = value.replaceAll("[^0-9.\\-]", "");
            if (numPart.isEmpty()) {
                return defaultValue;
            }

            Double.parseDouble(numPart);
            return value;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private VBox createTradeSummarySection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle(
                "-fx-background-color: #1e293b; " +
                        "-fx-border-color: #f59e0b; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-radius: 6;"
        );

        Label sectionTitle = new Label("Trade Summary");
        sectionTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #f59e0b;");

        GridPane grid = new GridPane();
        grid.setHgap(30);
        grid.setVgap(12);

        List<BacktestTrade> trades = tradesTable == null || tradesTable.getItems() == null
                ? List.of()
                : tradesTable.getItems()
                .stream()
                .filter(Objects::nonNull)
                .toList();

        int totalTrades = trades.size();

        long winningTrades = trades.stream()
                .filter(trade -> trade.getProfit() > 0)
                .count();

        long losingTrades = trades.stream()
                .filter(trade -> trade.getProfit() < 0)
                .count();

        double avgProfit = cleanNumber(
                trades.stream()
                        .mapToDouble(BacktestTrade::getProfit)
                        .average()
                        .orElse(0.0)
        );

        double bestTrade = cleanNumber(
                trades.stream()
                        .mapToDouble(BacktestTrade::getProfit)
                        .max()
                        .orElse(0.0)
        );

        double worstTrade = cleanNumber(
                trades.stream()
                        .mapToDouble(BacktestTrade::getProfit)
                        .min()
                        .orElse(0.0)
        );

        addMetricRow(grid, 0, "Total Trades:", String.valueOf(totalTrades), "#ffffff");
        addMetricRow(grid, 1, "Winning Trades:", String.valueOf(winningTrades), "#10b981");
        addMetricRow(grid, 2, "Losing Trades:", String.valueOf(losingTrades), "#ef4444");
        addMetricRow(grid, 3, "Average Profit:", String.format(Locale.ROOT, "%.2f", avgProfit), "#3b82f6");
        addMetricRow(grid, 4, "Best Trade:", String.format(Locale.ROOT, "%.2f", bestTrade), "#10b981");
        addMetricRow(grid, 5, "Worst Trade:", String.format(Locale.ROOT, "%.2f", worstTrade), "#ef4444");

        section.getChildren().addAll(sectionTitle, grid);
        return section;
    }

    private void addParameterRow(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label valueNode = new Label(value == null ? "" : value);
        valueNode.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 11px;");

        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }

    private void addMetricRow(GridPane grid, int row, String label, String value, String color) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label valueNode = new Label(value == null ? "" : value);
        valueNode.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-font-weight: bold;");

        HBox rowBox = new HBox(10);
        rowBox.setAlignment(Pos.CENTER_LEFT);
        rowBox.getChildren().addAll(labelNode, valueNode);

        grid.add(rowBox, row % 2, row / 2);
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private @NotNull HBox createLabeledInput(String label, @NotNull Node input) {
        Label labelNode = new Label(label);
        labelNode.setMinWidth(120);
        labelNode.setStyle("-fx-text-fill: #ffffff;");

        input.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(labelNode, input);
        HBox.setHgrow(input, Priority.ALWAYS);

        return box;
    }

    private String primaryButtonStyle(String color) {
        return "-fx-padding: 10px 25px; " +
                "-fx-font-size: 14px; " +
                "-fx-background-color: " + color + "; " +
                "-fx-text-fill: white; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand;";
    }

    private record DataReadiness(boolean ready, String message) {
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