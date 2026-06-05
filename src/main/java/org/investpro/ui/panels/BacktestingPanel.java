package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.backtesting.BacktestingService;
import org.investpro.backtesting.InstitutionalBacktestMetrics;
import org.investpro.core.SystemCore;
import org.investpro.core.agents.symbol.SymbolAgentManager;
import org.investpro.core.agents.symbol.SymbolAgentState;
import org.investpro.data.CandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.i18n.LocalizationService;
import org.investpro.models.trading.TradePair;
import org.investpro.persistence.repository.HistoricalDataRepository;
import org.investpro.persistence.repository.HistoricalDataRepositoryImpl;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.StrategySelectionService;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.TradingStrategy;
import org.investpro.strategy.impl.UnifiedStrategy;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.UniversalTradabilityService;
import org.investpro.utils.HistoricalDataPrefetcher;
import org.investpro.utils.ORDER_TYPES;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.investpro.utils.Side.BUY;
import static org.investpro.utils.Side.HOLD;
import static org.investpro.utils.Side.SELL;

/**
 * Backtesting Panel.
 */
@Slf4j
@Getter
@Setter
public class BacktestingPanel extends StackPane {

    private ComboBox<String> strategyCombo;
    private ComboBox<TradePair> symbolCombo;
    private ComboBox<Timeframe> timeframeCombo;
    private ComboBox<String> orderTypeCombo;

    private DatePicker startDatePicker;
    private DatePicker endDatePicker;

    private Spinner<Integer> barCountSpinner;
    private Spinner<Double> initialBalanceSpinner;

    private Label statusLabel;
    private ProgressBar progressBar;

    private TableView<BacktestTrade> tradesTable;

    private Label totalReturnLabel;
    private Label winRateLabel;
    private Label maxDrawdownLabel;
    private Label sharpeLabel;
    private Label profitFactorLabel;
    private Label dataQualityLabel;

    private LineChart<Number, Number> priceActionChart;
    private AreaChart<Number, Number> equityCurveChart;

    private Button runBacktestButton;
    private Button compareBtn;
    private Button exportBtn;
    private Button assignResultButton;

    private InstitutionalBacktestMetrics currentMetrics;
    private BacktestInput lastSuccessfulBacktestInput;
    private BacktestReportPanel reportPanel;

    private SymbolAgentManager symbolAgentManager;
    private final HistoricalDataRepository historicalDataRepository;
    private final BacktestingService backtestingService;
    private SystemCore systemCore;
    private final AtomicBoolean backtestRunning = new AtomicBoolean(false);

    private static final double DEFAULT_INITIAL_EQUITY = 10_000.0;
    private static final double DEFAULT_QUANTITY = 1.0;
    private static final int MAX_BARS_HELD = 50;
    private static final DateTimeFormatter PRICE_CHART_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Extra bars needed after strategy warmup so the strategy has enough room
     * to actually enter/exit trades.
     */
    private static final int MIN_TESTING_BARS_AFTER_WARMUP = 100;

    /**
     * Minimum absolute candles for any meaningful test.
     */
    private static final int MIN_ABSOLUTE_TEST_BARS = 150;

    public BacktestingPanel(SystemCore systemCore) {
        this(HistoricalDataRepositoryImpl.getInstance(), systemCore);

    }

    public BacktestingPanel(HistoricalDataRepository historicalDataRepository, SystemCore systemCore) {

        this.historicalDataRepository = historicalDataRepository != null ? historicalDataRepository
                : HistoricalDataRepositoryImpl.getInstance();
        this.backtestingService = new BacktestingService();

        this.systemCore = systemCore;
        this.symbolAgentManager = systemCore == null ? null : systemCore.getSymbolAgentManager();
        setPadding(new Insets(16));

        setStyle("-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff;");
        getStyleClass().add("backtest-panel");

        setupUI();
        LocalizationService.applyTranslations(this);
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
        section.setStyle("-fx-background-color: #16213e; " + "-fx-border-color: #3b82f6; " + "-fx-border-width: 1; "
                + "-fx-border-radius: 6; " + "-fx-background-radius: 6;");

        Label sectionTitle = new Label("Backtest Configuration");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        strategyCombo = new ComboBox<>();
        List<String> strategies = loadStrategyNames();
        strategyCombo.setItems(FXCollections.observableArrayList(strategies));
        strategyCombo.setValue(defaultStrategySelection(strategies));
        strategyCombo.setPrefHeight(35);
        HBox strategyBox = createLabeledInput("Strategy:", strategyCombo);

        symbolCombo = new ComboBox<>();
        symbolCombo.setPromptText("Loading symbols...");
        symbolCombo.setDisable(true);
        symbolCombo.setPrefHeight(35);
        HBox symbolBox = createLabeledInput("Symbol:", symbolCombo);
        loadSymbolsAsync();

        timeframeCombo = new ComboBox<>();
        timeframeCombo.getItems().setAll(loadSupportedTimeframes());
        timeframeCombo.setValue(timeframeCombo.getItems().contains(Timeframe.H1) ? Timeframe.H1
                : timeframeCombo.getItems().isEmpty() ? null : timeframeCombo.getItems().getFirst());
        timeframeCombo.setPrefHeight(35);
        timeframeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Timeframe timeframe) {
                return timeframe == null ? "" : displayTimeframe(timeframe);
            }

            @Override
            public Timeframe fromString(String string) {
                return timeframeCombo.getValue();
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

        initialBalanceSpinner = new Spinner<>(10.0, 100_000_000.0, DEFAULT_INITIAL_EQUITY, 100.0);
        initialBalanceSpinner.setPrefHeight(35);
        initialBalanceSpinner.setEditable(true);
        initialBalanceSpinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
        HBox initialBalanceBox = createLabeledInput("Initial Balance:", initialBalanceSpinner);

        runBacktestButton = new Button("Run Backtest");
        runBacktestButton.setStyle(primaryButtonStyle("#10b981"));
        runBacktestButton.setOnAction(event -> runBacktestAsync());

        Button prefetchButton = new Button("Prefetch Data");
        prefetchButton.setStyle(primaryButtonStyle("#8b5cf6"));
        prefetchButton.setOnAction(event -> prefetchHistoricalDataAsync());

        compareBtn = new Button("Compare");
        compareBtn.setStyle(primaryButtonStyle("#3b82f6"));
        compareBtn.setDisable(true);
        compareBtn.setOnAction(event -> compareResults());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(primaryButtonStyle("#ef4444"));
        cancelBtn.setOnAction(event -> {
            backtestRunning.set(false);
            statusLabel.setText("Cancel requested...");
            setRunningUi(false);
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
        buttonBox.getChildren().addAll(runBacktestButton, prefetchButton, compareBtn, cancelBtn);

        section.getChildren().addAll(sectionTitle, strategyBox, symbolBox, timeframeBox, orderTypeBox, startDateBox,
                endDateBox, barCountBox, initialBalanceBox, buttonBox, statusLabel, dataQualityLabel, progressBar);

        return section;
    }

    private VBox createResultsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: #16213e; " + "-fx-border-color: #10b981; " + "-fx-border-width: 1; "
                + "-fx-border-radius: 6; " + "-fx-background-radius: 6;");

        Label sectionTitle = new Label("Backtest Results");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        HBox metricsBox = createMetricsBox();

        priceActionChart = createPriceActionChart();
        equityCurveChart = createEquityCurveChart();

        tradesTable = createTradesTable();
        ScrollPane tradesScrollPane = new ScrollPane(tradesTable);
        tradesScrollPane.setFitToWidth(true);
        tradesScrollPane.setPrefHeight(240);

        exportBtn = new Button("Export Results");
        exportBtn.setStyle(primaryButtonStyle("#6366f1"));
        exportBtn.setDisable(true);
        exportBtn.setOnAction(event -> exportResults());

        assignResultButton = new Button("Assign Strategy");
        assignResultButton.setStyle(primaryButtonStyle("#10b981"));
        assignResultButton.setDisable(true);
        assignResultButton.setOnAction(event -> assignTestedStrategy());

        HBox resultActions = new HBox(10, assignResultButton, exportBtn);
        resultActions.setAlignment(Pos.CENTER_LEFT);

        Label equityLabel = new Label("Equity Curve");
        equityLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");

        Label priceActionLabel = new Label("Bot Trade Visualizer");
        priceActionLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");

        Label tradesLabel = new Label("Trade Details");
        tradesLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");

        // Create report panel
        reportPanel = new BacktestReportPanel();

        // Create tabs for traditional and professional views
        Tab standardTab = new Tab("Standard View", createStandardResultsView(metricsBox, priceActionLabel,
                priceActionChart, equityLabel, equityCurveChart, tradesLabel, tradesScrollPane));
        standardTab.setClosable(false);

        Tab professionalTab = new Tab("Professional Report", reportPanel);
        professionalTab.setClosable(false);

        TabPane resultsTabPane = new TabPane(standardTab, professionalTab);
        resultsTabPane.setStyle("-fx-background-color: #0f3460;");

        section.getChildren().addAll(sectionTitle, resultsTabPane, resultActions);

        VBox.setVgrow(resultsTabPane, Priority.ALWAYS);

        return section;
    }

    private VBox createStandardResultsView(HBox metricsBox, Label priceActionLabel,
            LineChart<Number, Number> priceActionChart, Label equityLabel, AreaChart<Number, Number> equityCurveChart,
            Label tradesLabel, ScrollPane tradesScrollPane) {
        VBox view = new VBox(10);
        view.setPadding(new Insets(8));
        view.setStyle("-fx-background-color: #0f3460;");

        view.getChildren().addAll(metricsBox, priceActionLabel, priceActionChart, equityLabel, equityCurveChart,
                tradesLabel, tradesScrollPane);

        VBox.setVgrow(priceActionChart, Priority.ALWAYS);
        VBox.setVgrow(equityCurveChart, Priority.SOMETIMES);
        VBox.setVgrow(tradesScrollPane, Priority.ALWAYS);

        return view;
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
        Label label = new Label(metricText(name, value));
        label.setStyle("-fx-padding: 8px; -fx-text-fill: #10b981; -fx-font-size: 13px; -fx-font-weight: bold;");
        label.setWrapText(true);
        return label;
    }

    private String metricText(String name, String value) {
        return name + "\n" + value;
    }

    private LineChart<Number, Number> createPriceActionChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Timestamp");
        xAxis.setForceZeroInRange(false);
        xAxis.setTickLabelRotation(-30);
        xAxis.setTickLabelFormatter(new GregorianTimestampAxisFormatter());
        xAxis.setStyle("-fx-text-fill: #a0aec0; -fx-tick-label-fill: #a0aec0;");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price");
        yAxis.setSide(javafx.geometry.Side.RIGHT);
        yAxis.setForceZeroInRange(false);
        yAxis.setStyle("-fx-text-fill: #a0aec0; -fx-tick-label-fill: #a0aec0;");

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Price Action with Bot Entries and Exits");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setPrefHeight(360);
        chart.setStyle("-fx-background-color: #0f3460;");

        return chart;
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

        TableColumn<BacktestTrade, ORDER_TYPES> orderTypeCol = new TableColumn<>("Order Type");
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

        table.getColumns().addAll(dateCol, sideCol, orderTypeCol, entryCol, exitCol, pnlCol, returnCol, reasonCol);

        return table;
    }

    private @NotNull List<String> loadStrategyNames() {
        try {
            LinkedHashSet<String> strategies = new LinkedHashSet<>(StrategyCatalog.availableStrategyNames());
            strategies.addAll(StrategyRegistry.getInstance().listStrategyNames());

            // Add user-developed strategies if any are registered
            try {
                List<TradingStrategy> userStrategies = StrategyRegistry.getInstance().getUserStrategies();
                for (TradingStrategy userStrategy : userStrategies) {
                    try {
                        String userStrategyId = String.valueOf(userStrategy.getId());
                        String displayName = "[User] " + userStrategyId;
                        strategies.add(displayName);
                    } catch (Exception e) {
                        log.debug("Error adding user strategy to backtest list: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.debug("Could not load user strategies from StrategyRegistry: {}", e.getMessage());
            }

            if (!strategies.isEmpty()) {
                return new ArrayList<>(strategies);
            }
        } catch (Exception exception) {
            log.debug("StrategyCatalog.availableStrategyNames() unavailable: {}", exception.getMessage());
        }

        try {
            return new ArrayList<>(StrategyCatalog.CORE_STRATEGY_NAMES);
        } catch (Exception exception) {
            return List.of(StrategyCatalog.defaultStrategyName(), "Mean Reversion", "Breakout");
        }
    }

    private String defaultStrategySelection(List<String> strategies) {
        if (strategies == null || strategies.isEmpty()) {
            return StrategyCatalog.defaultStrategyName();
        }
        return strategies.stream().filter(StrategyCatalog.defaultStrategyName()::equalsIgnoreCase).findFirst()
                .orElse(strategies.getFirst());
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

        return List.of(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.M30, Timeframe.H1, Timeframe.H4,
                Timeframe.D1);
    }

    private void loadSymbolsAsync() {
        CompletableFuture.supplyAsync(this::resolveBacktestingSymbols)
                .thenAccept(symbols -> Platform.runLater(() -> applyLoadedSymbols(symbols)))
                .exceptionally(exception -> {
                    log.warn("Failed to load backtesting symbols asynchronously: {}", exception.getMessage());
                    Platform.runLater(() -> applyLoadedSymbols(loadFallbackSymbols()));
                    return null;
                });
    }

    private List<TradePair> resolveBacktestingSymbols() {
        List<TradePair> symbols = new ArrayList<>();
        SymbolAgentManager manager = symbolAgentManager;

        if (systemCore != null && systemCore.getExchange() != null) {
            try {
                List<TradePair> exchangePairs = systemCore.getExchange().getTradePairSymbol();
                if (exchangePairs != null) {
                    symbols.addAll(exchangePairs);
                }
            } catch (Exception exception) {
                log.warn("Failed to load trade pair symbols from exchange: {}", exception.getMessage());
            }
        }

        if (manager != null) {
            try {
                symbols.addAll(manager.getAllStates().stream().map(SymbolAgentState::getSymbol)
                        .distinct().toList());
            } catch (Exception exception) {
                log.warn("Failed to load symbols from SymbolAgentManager: {}", exception.getMessage());
            }
        }

        if (symbols.isEmpty() && systemCore != null && systemCore.getExchange() != null) {
            try {
                List<TradePair> tradablePairs = systemCore.getExchange().getTradablePairs();
                if (tradablePairs != null) {
                    symbols.addAll(tradablePairs);
                }
            } catch (Exception exception) {
                log.warn("Failed to load symbols from exchange: {}", exception.getMessage());
            }
        }

        if (symbols.isEmpty()) {
            symbols.addAll(loadFallbackSymbols());
        }

        symbols = symbols.stream().filter(Objects::nonNull).distinct().toList();

        if (!symbols.isEmpty() && systemCore != null && systemCore.getExchange() != null) {
            try {
                UniversalTradabilityService tradabilityService = new UniversalTradabilityService(
                        systemCore.getExchange(), null);

                List<SymbolTradability> statuses = tradabilityService.getTradability(symbols).get();
                Set<String> marketDataSymbols = new HashSet<>();
                for (SymbolTradability status : statuses) {
                    if (status != null && status.tradePair() != null && status.marketDataAllowed()) {
                        marketDataSymbols.add(status.tradePair().toString('/').toUpperCase(Locale.ROOT));
                    }
                }

                symbols = symbols.stream()
                        .filter(pair -> marketDataSymbols.contains(pair.toString('/').toUpperCase(Locale.ROOT)))
                        .toList();
            } catch (Exception exception) {
                log.warn("Failed to apply tradability filter for backtesting symbols: {}", exception.getMessage());
            }
        }

        return symbols;
    }

    private List<TradePair> loadFallbackSymbols() {
        try {
            return List.of(TradePair.fromSymbol("BTC_USD"), TradePair.fromSymbol("ETH_USD"),
                    TradePair.fromSymbol("EUR_USD"), TradePair.fromSymbol("GBP_USD"), TradePair.fromSymbol("USD_JPY"));
        } catch (Exception exception) {
            log.warn("Failed to create fallback TradePair list: {}", exception.getMessage());
            return List.of();
        }
    }

    private void applyLoadedSymbols(List<TradePair> symbols) {
        List<TradePair> safeSymbols = symbols == null ? List.of()
                : symbols.stream().filter(Objects::nonNull).distinct().toList();

        symbolCombo.setItems(FXCollections.observableArrayList(safeSymbols));
        if (!safeSymbols.isEmpty()) {
            symbolCombo.setValue(safeSymbols.getFirst());
        }

        symbolCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TradePair pair) {
                return pair == null ? "" : displayTradePair(pair);
            }

            @Override
            public TradePair fromString(String string) {
                return symbolCombo.getValue();
            }
        });

        symbolCombo.setDisable(false);
        symbolCombo.setPromptText("Select a symbol");
    }

    /**
     * Pre-fetch historical data from API and cache for backtesting.
     */
    private void prefetchHistoricalDataAsync() {
        if (backtestRunning.get()) {
            statusLabel.setText("Operation already running...");
            return;
        }

        TradePair selectedPair = symbolCombo.getValue();
        Timeframe selectedTimeframe = timeframeCombo.getValue();
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        if (selectedPair == null || selectedTimeframe == null || startDate == null || endDate == null) {
            statusLabel.setText("Please select symbol, timeframe, and date range");
            return;
        }

        if (endDate.isBefore(startDate)) {
            statusLabel.setText("Invalid date range: end date must be after start date");
            return;
        }

        backtestRunning.set(true);
        statusLabel.setText("Pre-fetching historical data from API...");
        progressBar.setProgress(-1);
        setRunningUi(true);

        Thread prefetchThread = new Thread(() -> {
            try {
                prefetchHistoricalData(selectedPair, selectedTimeframe, startDate, endDate);
            } catch (Exception exception) {
                log.error("Data prefetch error", exception);
                Platform.runLater(() -> {
                    statusLabel.setText("Prefetch error: " + exception.getMessage());
                    progressBar.setProgress(0);
                    backtestRunning.set(false);
                    setRunningUi(false);
                });
            }
        });

        prefetchThread.setName("data-prefetch-worker");
        prefetchThread.setDaemon(true);
        prefetchThread.start();
    }

    /**
     * Perform historical data prefetch and caching.
     */
    private void prefetchHistoricalData(@NotNull TradePair pair, @NotNull Timeframe timeframe,
            @NotNull LocalDate startDate, @NotNull LocalDate endDate) {

        try {
            String timeframeCode = timeframe.getCode();
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.atTime(23, 59, 59);

            Platform.runLater(() -> statusLabel.setText("Fetching " + displayTradePair(pair) + " " + timeframeCode
                    + " data from " + startDate + " to " + endDate + "..."));

            if (systemCore == null || systemCore.getExchange() == null) {
                Platform.runLater(() -> {
                    statusLabel.setText(
                            "No live exchange is available. Backtests can still run with cached or sample data.");
                    dataQualityLabel.setText("Data quality: cached/sample data only");
                    progressBar.setProgress(0);
                    backtestRunning.set(false);
                    setRunningUi(false);
                });
                return;
            }

            // Create pre-fetcher for the active exchange.
            HistoricalDataPrefetcher preFetcher = HistoricalDataPrefetcher.forCurrentExchange(systemCore.getExchange(),
                    historicalDataRepository);

            // Fetch and cache data with progress updates
            List<CandleData> fetchedData = preFetcher.fetchAndCacheDataSync(pair, start, end, timeframeCode,
                    progress -> Platform.runLater(() -> {
                        if (progress >= 0) {
                            progressBar.setProgress(progress / 100.0);
                            statusLabel.setText("Fetching data... " + progress + "%");
                        } else {
                            statusLabel.setText("Prefetch completed with errors");
                        }
                    }));

            Platform.runLater(() -> {
                if (fetchedData.isEmpty()) {
                    statusLabel.setText("No data fetched. The pair may not be available on Binance.");
                    dataQualityLabel.setText("Data quality: failed");
                } else {
                    backtestingService.storeHistoricalData(pair, start, end, timeframeCode, fetchedData);
                    statusLabel.setText("Successfully cached " + fetchedData.size() + " real candles for "
                            + displayTradePair(pair) + " " + timeframeCode + ". Ready for backtesting!");
                    dataQualityLabel
                            .setText("Data quality: real historical data cached (" + fetchedData.size() + " candles)");
                    progressBar.setProgress(1.0);
                }
                backtestRunning.set(false);
                setRunningUi(false);
            });

        } catch (Exception exception) {
            log.error("Prefetch failed", exception);
            Platform.runLater(() -> {
                statusLabel.setText("Prefetch failed: " + exception.getMessage());
                progressBar.setProgress(0);
                backtestRunning.set(false);
                setRunningUi(false);
            });
        }
    }

    private void runBacktestAsync() {
        if (backtestRunning.get()) {
            statusLabel.setText("Backtest already running...");
            return;
        }

        BacktestInput input = readBacktestInput();
        if (input.isValid()) {
            statusLabel.setText(input.validationMessage());
            progressBar.setProgress(0);
            return;
        }

        backtestRunning.set(true);
        lastSuccessfulBacktestInput = null;
        statusLabel.setText("Loading data...");
        progressBar.setProgress(-1);
        setRunningUi(true);

        Thread backTestThread = new Thread(() -> {
            try {
                runBacktest(input);
            } catch (Exception exception) {
                log.error("Backtest error", exception);
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + exception.getMessage());
                    dataQualityLabel.setText("Data quality: failed");
                    progressBar.setProgress(0);
                    backtestRunning.set(false);
                    setRunningUi(false);
                });
            }
        });

        backTestThread.setName("strategy-backtest-worker");
        backTestThread.setDaemon(true);
        backTestThread.start();
    }

    private BacktestInput readBacktestInput() {
        String strategyName = strategyCombo.getValue();
        TradePair selectedPair = symbolCombo.getValue();
        Timeframe selectedTimeframe = timeframeCombo.getValue();
        ORDER_TYPES orderType = resolveOrderType(orderTypeCombo.getValue());
        double initialBalance = readDoubleSpinner(initialBalanceSpinner);
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();
        int requestedBars = Math.max(MIN_ABSOLUTE_TEST_BARS, readIntegerSpinner(barCountSpinner));

        if (strategyName == null || strategyName.isBlank() || selectedPair == null || selectedTimeframe == null) {
            return BacktestInput.invalid("Please select strategy, symbol, and timeframe");
        }

        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return BacktestInput.invalid("Invalid date range");
        }

        return new BacktestInput(strategyName, selectedPair, selectedTimeframe, orderType, initialBalance, startDate,
                endDate, requestedBars, "");
    }

    private void runBacktest(@NotNull BacktestInput input) {
        try {
            String strategyName = input.strategyName();
            TradePair selectedPair = input.selectedPair();
            Timeframe selectedTimeframe = input.selectedTimeframe();
            ORDER_TYPES orderType = input.orderType();
            double initialBalance = input.initialBalance();
            LocalDate startDate = input.startDate();
            LocalDate endDate = input.endDate();
            int requestedBars = input.requestedBars();
            String timeframeCode = selectedTimeframe.getCode();

            Platform.runLater(() -> statusLabel.setText("Updating historical data from exchange for "
                    + displayTradePair(selectedPair) + " " + timeframeCode));

            refreshHistoricalDataCache(input);

            Platform.runLater(() -> statusLabel.setText(
                    "Loading saved historical data for " + displayTradePair(selectedPair) + " " + timeframeCode));

            List<CandleData> candles = loadEnoughCandles(selectedPair, selectedTimeframe, startDate, endDate,
                    requestedBars);

            TradingStrategy strategy = resolveStrategy(strategyName);
            int warmupBars = strategy == null ? 50 : safeWarmupBars(strategy);

            DataReadiness readiness = evaluateDataReadiness(candles, warmupBars, requestedBars);

            Platform.runLater(() -> dataQualityLabel.setText(readiness.message()));

            if (!readiness.ready()) {
                Platform.runLater(() -> {
                    statusLabel.setText(readiness.message());
                    progressBar.setProgress(0);
                    backtestRunning.set(false);
                    setRunningUi(false);
                });
                return;
            }

            if (candles.size() > requestedBars) {
                candles = new ArrayList<>(candles.subList(candles.size() - requestedBars, candles.size()));
            }

            List<CandleData> finalCandles = candles;
            Platform.runLater(() -> statusLabel.setText("Executing strategy backtest with " + finalCandles.size()
                    + " candles and initial balance $" + String.format(Locale.ROOT, "%.2f", initialBalance) + "..."));

            List<BacktestTrade> trades = executeBacktest(strategyName, selectedPair, timeframeCode, orderType, candles,
                    initialBalance);

            InstitutionalBacktestMetrics metrics = calculateInstitutionalMetrics(trades, initialBalance);

            Platform.runLater(() -> {
                tradesTable.setItems(FXCollections.observableArrayList(trades));
                updatePriceActionChart(finalCandles, trades);
                updateEquityChart(trades, initialBalance);
                updateMetrics(metrics);
                lastSuccessfulBacktestInput = input;

                boolean hasTrades = !trades.isEmpty();
                if (exportBtn != null) {
                    exportBtn.setDisable(!hasTrades);
                }
                if (compareBtn != null) {
                    compareBtn.setDisable(!hasTrades);
                }
                if (assignResultButton != null) {
                    assignResultButton.setDisable(false);
                }

                statusLabel.setText("Backtest complete for " + displayTradePair(selectedPair) + " using " + strategyName
                        + " / " + orderType + " | Initial Balance: $"
                        + String.format(Locale.ROOT, "%.2f", initialBalance) + " | Trades: " + trades.size());

                progressBar.setProgress(1.0);
                backtestRunning.set(false);
                setRunningUi(false);
            });

        } catch (Exception exception) {
            log.error("Backtest execution error", exception);
            Platform.runLater(() -> {
                statusLabel.setText("Error: " + exception.getMessage());
                dataQualityLabel.setText("Data quality: error");
                progressBar.setProgress(0);
                backtestRunning.set(false);
                setRunningUi(false);
            });
        }
    }

    private void refreshHistoricalDataCache(@NotNull BacktestInput input) {
        if (systemCore == null || systemCore.getExchange() == null) {
            log.info("Skipping historical data refresh because no exchange is available");
            Platform.runLater(() -> dataQualityLabel.setText("Data quality: cached data only - no exchange connected"));
            return;
        }

        try {
            Timeframe timeframe = input.selectedTimeframe();
            String timeframeCode = timeframe.getCode();
            LocalDate endDate = input.endDate();
            int estimatedDaysNeeded = estimateDaysNeeded(timeframe, input.requestedBars());
            LocalDate fetchStartDate = input.startDate().isBefore(endDate.minusDays(estimatedDaysNeeded))
                    ? input.startDate()
                    : endDate.minusDays(estimatedDaysNeeded);

            LocalDateTime fetchStart = fetchStartDate.atStartOfDay();
            LocalDateTime fetchEnd = endDate.atTime(23, 59, 59);

            HistoricalDataPrefetcher preFetcher = HistoricalDataPrefetcher.forCurrentExchange(systemCore.getExchange(),
                    historicalDataRepository);

            List<CandleData> fetched = preFetcher.fetchAndCacheDataSync(input.selectedPair(), fetchStart, fetchEnd,
                    timeframeCode, progress -> Platform.runLater(() -> {
                        if (progress >= 0) {
                            progressBar.setProgress(progress / 100.0);
                            statusLabel.setText("Updating historical data... " + progress + "%");
                        }
                    }));

            Platform.runLater(() -> {
                if (fetched.isEmpty()) {
                    dataQualityLabel.setText("Data quality: using existing cached data - exchange returned no candles");
                } else {
                    backtestingService.storeHistoricalData(input.selectedPair(), fetchStart, fetchEnd, timeframeCode,
                            fetched);
                    dataQualityLabel.setText(
                            "Data quality: saved/updated " + fetched.size() + " exchange candles before backtest");
                }
            });
        } catch (Exception exception) {
            log.warn("Historical data refresh failed; continuing with cached data: {}", exception.getMessage());
            Platform.runLater(() -> dataQualityLabel
                    .setText("Data quality: cached data - refresh failed: " + exception.getMessage()));
        }
    }

    private double getInitialBalance() {
        double value = readDoubleSpinner(initialBalanceSpinner);

        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0) {
            return DEFAULT_INITIAL_EQUITY;
        }

        return value;
    }

    private int readIntegerSpinner(Spinner<Integer> spinner) {
        if (spinner == null) {
            return 2000;
        }

        try {
            String text = spinner.getEditor() == null ? "" : spinner.getEditor().getText();
            if (text != null && !text.isBlank()) {
                return Integer.parseInt(text.trim().replace(",", ""));
            }
        } catch (Exception ignored) {
            // fallback below
        }

        return spinner.getValue() == null ? 2000 : spinner.getValue();
    }

    private double readDoubleSpinner(Spinner<Double> spinner) {
        if (spinner == null) {
            return BacktestingPanel.DEFAULT_INITIAL_EQUITY;
        }

        try {
            String text = spinner.getEditor() == null ? "" : spinner.getEditor().getText();
            if (text != null && !text.isBlank()) {
                return Double.parseDouble(text.trim().replace(",", ""));
            }
        } catch (Exception ignored) {
            // fallback below
        }

        return spinner.getValue() == null ? BacktestingPanel.DEFAULT_INITIAL_EQUITY : spinner.getValue();
    }

    private void setRunningUi(boolean running) {
        if (runBacktestButton != null) {
            runBacktestButton.setDisable(running);
        }
        if (exportBtn != null && running) {
            exportBtn.setDisable(true);
        }
        if (compareBtn != null && running) {
            compareBtn.setDisable(true);
        }
        if (assignResultButton != null) {
            assignResultButton.setDisable(running || lastSuccessfulBacktestInput == null);
        }
    }

    /**
     * Load requested historical candles. If the selected range does not provide
     * enough candles,
     * automatically expands the request range backward.
     */
    private List<CandleData> loadEnoughCandles(TradePair pair, Timeframe timeframe, LocalDate startDate,
            LocalDate endDate, int requestedBars) {
        String timeframeCode = timeframe.getCode();

        List<CandleData> candles = loadHistoricalCandles(pair, startDate.atStartOfDay(), endDate.atTime(23, 59, 59),
                timeframeCode);

        if (candles.size() >= requestedBars) {
            return candles;
        }

        int estimatedDaysNeeded = estimateDaysNeeded(timeframe, requestedBars);
        LocalDate expandedStart = endDate.minusDays(Math.max(estimatedDaysNeeded, 365));

        Platform.runLater(() -> statusLabel
                .setText("Not enough data in selected range. Expanding lookup to " + expandedStart + "..."));

        List<CandleData> expandedCandles = loadHistoricalCandles(pair, expandedStart.atStartOfDay(),
                endDate.atTime(23, 59, 59), timeframeCode);

        if (expandedCandles.size() > candles.size()) {
            candles = expandedCandles;
        }

        if (candles.size() < requestedBars) {
            int sampleCount = Math.max(requestedBars, MIN_ABSOLUTE_TEST_BARS + 100);

            Platform.runLater(() -> statusLabel
                    .setText("Historical data still insufficient. Using sample data with " + sampleCount + " bars."));

            candles = generateSampleData(sampleCount, timeframe);
        }

        return candles;
    }

    private List<CandleData> loadHistoricalCandles(TradePair pair, LocalDateTime start, LocalDateTime end,
            String timeframeCode) {
        try {
            Optional<List<CandleData>> storedCandles = backtestingService.getStoredHistoricalData(pair, start, end,
                    timeframeCode);

            if (storedCandles.isPresent() && !storedCandles.get().isEmpty()) {
                return storedCandles.get().stream().filter(Objects::nonNull)
                        .sorted(Comparator.comparingLong(this::candleTimestamp)).toList();
            }
        } catch (Exception exception) {
            log.debug("BacktestingService lookup failed, falling back to repository: {}", exception.getMessage());
        }

        try {
            Optional<List<CandleData>> historicalCandles = historicalDataRepository.getHistoricalData(pair, start, end,
                    timeframeCode);

            if (historicalCandles.isPresent() && !historicalCandles.get().isEmpty()) {
                return historicalCandles.get().stream().filter(Objects::nonNull)
                        .sorted(Comparator.comparingLong(this::candleTimestamp)).toList();
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

    private DataReadiness evaluateDataReadiness(List<CandleData> candles, int warmupBars, int requestedBars) {
        int actualBars = candles == null ? 0 : candles.size();
        int minimumRequired = Math.max(MIN_ABSOLUTE_TEST_BARS, warmupBars + MIN_TESTING_BARS_AFTER_WARMUP);

        if (actualBars < minimumRequired) {
            return new DataReadiness(false, "Not enough data for proper testing. Need at least " + minimumRequired
                    + " candles. Available: " + actualBars + ".");
        }

        if (actualBars < requestedBars) {
            return new DataReadiness(true, "Data quality: usable but below requested bars. Requested " + requestedBars
                    + ", available " + actualBars + ".");
        }

        return new DataReadiness(true, "Data quality: ready. Available candles: " + actualBars + ", warmup: "
                + warmupBars + ", requested: " + requestedBars + ".");
    }

    private List<BacktestTrade> executeBacktest(String strategyName, TradePair selectedPair, String timeframeCode,
            ORDER_TYPES orderType, List<CandleData> candles, double initialBalance) {
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

            StrategyContext context = buildStrategyContext(selectedPair, timeframe, window, current.closePrice());

            StrategySignal signal;

            try {
                signal = strategy.generateSignal(context);
            } catch (Exception exception) {
                log.warn("Strategy {} failed during backtest for {} at candle {}: {}", strategyName,
                        displayTradePair(selectedPair), i, exception.getMessage());
                updateProgress(i, candles.size());
                continue;
            }

            if (signal == null || signal.getSide() == null) {
                updateProgress(i, candles.size());
                continue;
            }

            if (!inPosition) {
                if (signal.getSide() == BUY || signal.getSide() == SELL) {
                    Optional<Double> fillPrice = resolveEntryFillPrice(orderType, signal.getSide(), signal, current);

                    if (fillPrice.isEmpty()) {
                        updateProgress(i, candles.size());
                        continue;
                    }

                    entryPrice = fillPrice.get();
                    stopLoss = getSignalStopLoss(signal);
                    takeProfit = getSignalTakeProfit(signal);
                    quantity = resolveBacktestQuantity(initialBalance, signal);

                    positionSide = signal.getSide();
                    entryDate = candleDate(current);
                    entryIndex = i;
                    inPosition = true;
                }
            } else {
                ExitDecision exit = shouldExitPosition(positionSide, current, signal, stopLoss, takeProfit, entryIndex,
                        i);

                if (exit.exit()) {
                    double exitPrice = exit.exitPrice();
                    double profit = calculateProfit(positionSide, entryPrice, exitPrice, quantity);
                    double returnPercent = entryPrice > 0.0 ? (profit / (entryPrice * quantity)) * 100.0 : 0.0;

                    trades.add(new BacktestTrade(entryDate == null ? candleDate(current) : entryDate, positionSide,
                            orderType, entryPrice, exitPrice, quantity, cleanNumber(profit), cleanNumber(returnPercent),
                            exit.reason(), entryIndex, i));

                    inPosition = false;
                    positionSide = HOLD;
                    entryPrice = 0.0;
                    stopLoss = 0.0;
                    takeProfit = 0.0;
                    quantity = DEFAULT_QUANTITY;
                    entryDate = null;
                    entryIndex = -1;
                }
            }

            updateProgress(i, candles.size());
        }

        return trades;
    }

    private double resolveBacktestQuantity(double initialBalance, StrategySignal signal) {
        if (initialBalance < 100.0) {
            return 1.0;
        }

        try {
            if (signal != null && signal.getAmount() > 0.0) {
                return signal.getAmount();
            }
        } catch (Exception ignored) {
            // fallback below
        }

        return DEFAULT_QUANTITY;
    }

    private TradingStrategy resolveStrategy(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            return null;
        }

        // Handle user strategies with "[User] " prefix
        if (strategyName.startsWith("[User] ")) {
            String userStrategyId = strategyName.substring("[User] ".length()).trim();
            try {
                Optional<TradingStrategy> userStrategy = StrategyRegistry.getInstance().findById(userStrategyId);
                if (userStrategy.isPresent()) {
                    return userStrategy.get();
                }
            } catch (Exception e) {
                log.debug("Could not resolve user strategy '{}': {}", userStrategyId, e.getMessage());
            }
        }

        // Try registry lookup (built-in and catalog strategies)
        try {
            TradingStrategy strategy = StrategyRegistry.getInstance().getStrategy(strategyName);

            if (strategy != null) {
                return strategy;
            }
        } catch (Exception exception) {
            log.debug("Could not resolve strategy from registry: {}", exception.getMessage());
        }

        // Fallback to UnifiedStrategy for catalog variants
        try {
            return new UnifiedStrategy(strategyName);
        } catch (Exception exception) {
            log.warn("Could not create UnifiedStrategy for {}: {}", strategyName, exception.getMessage());
            return null;
        }
    }

    private StrategyContext buildStrategyContext(TradePair pair, Timeframe timeframe, List<CandleData> candles,
            double currentPrice) {
        return StrategyContext.builder().symbol(pair).timeframe(timeframe).candles(candles).currentPrice(currentPrice)
                .build();
    }

    private ORDER_TYPES resolveOrderType(String orderTypeText) {
        if (orderTypeText == null || orderTypeText.isBlank()) {
            return ORDER_TYPES.MARKET;
        }

        String normalized = orderTypeText.trim().toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "LIMIT" -> ORDER_TYPES.LIMIT;
            case "STOP_LIMIT", "STOP LIMIT" -> ORDER_TYPES.STOP_LIMIT;
            default -> ORDER_TYPES.MARKET;
        };
    }

    private Optional<Double> resolveEntryFillPrice(ORDER_TYPES orderType, Side side, StrategySignal signal,
            CandleData candle) {
        double requestedEntry = signal.getEntryPrice() > 0.0 ? signal.getEntryPrice() : candle.closePrice();

        if (orderType == ORDER_TYPES.MARKET) {
            return Optional.of(candle.closePrice());
        }

        if (orderType == ORDER_TYPES.LIMIT) {
            if (side == BUY && candle.lowPrice() <= requestedEntry) {
                return Optional.of(requestedEntry);
            }

            if (side == SELL && candle.highPrice() >= requestedEntry) {
                return Optional.of(requestedEntry);
            }

            return Optional.empty();
        }

        if (orderType == ORDER_TYPES.STOP_LIMIT) {
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

    private @NotNull ExitDecision shouldExitPosition(Side positionSide, @NotNull CandleData current,
            StrategySignal latestSignal, double stopLoss, double takeProfit, int entryIndex, int currentIndex) {
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

            return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
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
            try {
                return Long.parseLong(String.valueOf(candle.timestamp()));
            } catch (Exception ignoredAgain) {
                return 0L;
            }
        }
    }

    private double candleXValue(List<CandleData> candles, int index) {
        if (candles == null || index < 0 || index >= candles.size()) {
            return -1.0;
        }
        return candleEpochMillis(candles.get(index));
    }

    private double candleEpochMillis(CandleData candle) {
        long timestamp = candleTimestamp(candle);
        if (timestamp <= 0L) {
            return -1.0;
        }
        if (timestamp < 10_000_000_000L) {
            timestamp *= 1000L;
        }
        return timestamp;
    }

    private static final class GregorianTimestampAxisFormatter extends StringConverter<Number> {
        @Override
        public String toString(Number value) {
            if (value == null) {
                return "";
            }
            long epochMillis = value.longValue();
            if (epochMillis <= 0L) {
                return "";
            }
            if (epochMillis < 10_000_000_000L) {
                epochMillis *= 1000L;
            }
            try {
                return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(PRICE_CHART_TIME_FORMAT);
            } catch (Exception ignored) {
                return "";
            }
        }

        @Override
        public Number fromString(String value) {
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

    private @NotNull List<CandleData> generateSampleData(int count, Timeframe timeframe) {
        List<CandleData> candles = new ArrayList<>();
        double price = 100.0;
        long timestampSeconds = System.currentTimeMillis() / 1000L;
        long stepSeconds = timeframeStepSeconds(timeframe);

        for (int i = 0; i < count; i++) {
            double change = (Math.random() - 0.48) * 2.0;
            price = Math.max(0.0001, price + change);

            double open = price - Math.random();
            double close = price + Math.random();
            double high = Math.max(open, close) + Math.random();
            double low = Math.max(0.0001, Math.min(open, close) - Math.random());

            candles.add(new CandleData(open, close, high, low, (int) (timestampSeconds + (i * stepSeconds)),
                    1_000 + Math.random() * 5_000));
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

    private InstitutionalBacktestMetrics calculateInstitutionalMetrics(List<BacktestTrade> trades,
            double initialBalance) {
        double startingEquity = initialBalance > 0.0 ? initialBalance : DEFAULT_INITIAL_EQUITY;
        this.currentMetrics = new InstitutionalBacktestMetrics(trades, startingEquity);
        return currentMetrics;
    }

    private void updateMetrics(InstitutionalBacktestMetrics metrics) {
        if (metrics == null) {
            totalReturnLabel.setText(metricText("Total Return", "--"));
            winRateLabel.setText(metricText("Win Rate", "--"));
            maxDrawdownLabel.setText(metricText("Max Drawdown", "--"));
            sharpeLabel.setText(metricText("Sharpe Ratio", "--"));
            profitFactorLabel.setText(metricText("Profit Factor", "--"));
            return;
        }

        totalReturnLabel.setText(metricText("Total Return", String.format("%.2f%%", metrics.getTotalReturnPercent())));
        winRateLabel.setText(metricText("Win Rate", String.format("%.1f%%", metrics.getWinRate())));
        maxDrawdownLabel.setText(metricText("Max Drawdown", String.format("-%.2f%%", metrics.getMaxDrawdownPercent())));
        sharpeLabel.setText(metricText("Sharpe Ratio", String.format("%.2f", metrics.getSharpeRatio())));
        profitFactorLabel.setText(metricText("Profit Factor", String.format("%.2f", metrics.getProfitFactor())));

        // Display advanced metrics if available
        if (reportPanel != null) {
            reportPanel.displayReport(metrics);
        }
    }

    private void updatePriceActionChart(List<CandleData> candles, List<BacktestTrade> trades) {
        if (priceActionChart == null) {
            return;
        }

        priceActionChart.getData().clear();

        if (candles == null || candles.isEmpty()) {
            return;
        }

        int firstIndex = Math.max(0, candles.size() - 700);
        XYChart.Series<Number, Number> priceSeries = new XYChart.Series<>();
        priceSeries.setName("Price");

        for (int i = firstIndex; i < candles.size(); i++) {
            CandleData candle = candles.get(i);
            if (candle == null || candle.closePrice() <= 0.0) {
                continue;
            }
            priceSeries.getData().add(new XYChart.Data<>(candleEpochMillis(candle), candle.closePrice()));
        }

        priceActionChart.getData().add(priceSeries);

        if (trades != null) {
            for (BacktestTrade trade : trades) {
                if (trade == null || trade.getEntryIndex() < firstIndex || trade.getExitIndex() < firstIndex) {
                    continue;
                }

                double entryX = candleXValue(candles, trade.getEntryIndex());
                double exitX = candleXValue(candles, trade.getExitIndex());

                addTradeMarker(entryX, trade.getPrice(), trade.getSide() == BUY ? "BUY" : "SELL",
                        trade.getSide() == BUY ? "#22c55e" : "#ef4444",
                        "%s entry @ %.5f".formatted(trade.getSide(), trade.getPrice()));

                addTradeMarker(exitX, trade.getExitPrice(), "EXIT", trade.getProfit() >= 0.0 ? "#38bdf8" : "#f59e0b",
                        "Exit @ %.5f | P&L %.2f | %s".formatted(trade.getExitPrice(), trade.getProfit(),
                                trade.getReason()));

                addTradeConnector(trade, entryX, exitX);
            }
        }

        Platform.runLater(() -> stylePriceActionChart(priceSeries));
    }

    private void addTradeMarker(double xValue, double price, String name, String color, String tooltipText) {
        if (xValue <= 0.0 || price <= 0.0) {
            return;
        }

        XYChart.Series<Number, Number> marker = new XYChart.Series<>();
        marker.setName(name);
        XYChart.Data<Number, Number> point = new XYChart.Data<>(xValue, price);
        marker.getData().add(point);
        priceActionChart.getData().add(marker);

        Platform.runLater(() -> {
            if (point.getNode() != null) {
                point.getNode().setStyle("-fx-background-color: " + color + ", white; " + "-fx-background-radius: 7px; "
                        + "-fx-padding: 6px;");
                Tooltip.install(point.getNode(), new Tooltip(tooltipText));
            }
            if (marker.getNode() != null) {
                marker.getNode().lookupAll(".chart-series-line")
                        .forEach(node -> node.setStyle("-fx-stroke: transparent;"));
            }
        });
    }

    private void addTradeConnector(BacktestTrade trade, double entryX, double exitX) {
        if (trade.getEntryIndex() < 0 || trade.getExitIndex() < 0 || entryX <= 0.0 || exitX <= 0.0) {
            return;
        }

        XYChart.Series<Number, Number> connector = new XYChart.Series<>();
        connector.setName("Trade");
        connector.getData().add(new XYChart.Data<>(entryX, trade.getPrice()));
        connector.getData().add(new XYChart.Data<>(exitX, trade.getExitPrice()));
        priceActionChart.getData().add(connector);

        Platform.runLater(() -> {
            String color = trade.getProfit() >= 0.0 ? "#22c55e" : "#ef4444";
            if (connector.getNode() != null) {
                connector.getNode().lookupAll(".chart-series-line")
                        .forEach(node -> node.setStyle("-fx-stroke: " + color + "; -fx-stroke-width: 1.5px;"));
            }
            for (XYChart.Data<Number, Number> point : connector.getData()) {
                if (point.getNode() != null) {
                    point.getNode().setStyle("-fx-background-color: transparent; -fx-padding: 0;");
                }
            }
        });
    }

    private void stylePriceActionChart(XYChart.Series<Number, Number> priceSeries) {
        if (priceSeries.getNode() != null) {
            priceSeries.getNode().lookupAll(".chart-series-line")
                    .forEach(node -> node.setStyle("-fx-stroke: #e5e7eb; -fx-stroke-width: 1.6px;"));
        }

        for (XYChart.Data<Number, Number> point : priceSeries.getData()) {
            if (point.getNode() != null) {
                point.getNode().setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            }
        }
    }

    private double cleanNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }

        return value;
    }

    private void updateEquityChart(List<BacktestTrade> trades, double initialBalance) {
        equityCurveChart.getData().clear();

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Equity");

        double equity = initialBalance > 0.0 ? initialBalance : DEFAULT_INITIAL_EQUITY;

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

    private String metricDisplayValue(Label metricLabel) {
        if (metricLabel == null || metricLabel.getText() == null) {
            return "";
        }
        String[] lines = metricLabel.getText().split("\\R");
        return lines.length == 0 ? "" : lines[lines.length - 1].trim();
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

            String safeSymbol = symbol != null ? symbol.toString().replace("/", "").replace("\\", "").replace(":", "")
                    : "unknown";

            String safeStrategy = strategy == null ? "strategy" : strategy.replaceAll("[^a-zA-Z0-9._-]", "_");

            String filename = String.format("backtest_%s_%s_%s.csv", safeSymbol, safeStrategy, timestamp);

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Backtest Results");
            fileChooser.setInitialFileName(filename);
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                    new FileChooser.ExtensionFilter("All Files", "*.*"));

            File file = fileChooser.showSaveDialog(this.getScene().getWindow());

            if (file != null) {
                java.nio.file.Files.writeString(file.toPath(), csvContent.toString());
                statusLabel.setText("✓ Results exported to: " + file.getName());
                showAlert("Export Successful", "Backtest results exported to:\n" + file.getAbsolutePath(),
                        Alert.AlertType.INFORMATION);
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

            csvContent.append(String.format(Locale.ROOT, "%s,%s,%s,%.5f,%.5f,%.2f,%.2f,%.2f,%s\n", trade.getDate(),
                    trade.getSide(), trade.getOrderType(), trade.getPrice(), trade.getExitPrice(), trade.getQuantity(),
                    trade.getProfit(), trade.getReturnPercent(), sanitizeCsv(trade.getReason())));
        }

        csvContent.append("\n\nMetrics Summary\n");
        csvContent.append(String.format(Locale.ROOT, "Initial Balance,$%.2f\n", getInitialBalance()));
        csvContent.append(String.format("Total Return,%s\n", metricDisplayValue(totalReturnLabel)));
        csvContent.append(String.format("Win Rate,%s\n", metricDisplayValue(winRateLabel)));
        csvContent.append(String.format("Max Drawdown,%s\n", metricDisplayValue(maxDrawdownLabel)));
        csvContent.append(String.format("Sharpe Ratio,%s\n", metricDisplayValue(sharpeLabel)));
        csvContent.append(String.format("Profit Factor,%s\n", metricDisplayValue(profitFactorLabel)));

        return csvContent;
    }

    private String sanitizeCsv(String value) {
        if (value == null) {
            return "";
        }

        return value.replace(",", " ").replace("\n", " ").trim();
    }

    private void assignTestedStrategy() {
        BacktestInput input = lastSuccessfulBacktestInput;
        if (input == null || input.isValid()) {
            showAlert("No Backtest Selected", "Run a backtest before assigning a strategy.", Alert.AlertType.WARNING);
            return;
        }

        String symbol = displayTradePair(input.selectedPair());
        String strategyId = assignmentStrategyId(input.strategyName());
        double score = currentMetrics == null ? 0.0 : currentMetrics.getConfidenceScore();
        int trades = currentMetrics == null ? 0 : currentMetrics.getTotalTrades();
        double totalReturn = currentMetrics == null ? 0.0 : currentMetrics.getTotalReturnPercent();
        double winRate = currentMetrics == null ? 0.0 : currentMetrics.getWinRate();
        double drawdown = currentMetrics == null ? 0.0 : currentMetrics.getMaxDrawdownPercent();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Assign Backtested Strategy");
        confirm.setHeaderText("Assign this strategy to " + symbol + " / " + input.selectedTimeframe().getCode() + "?");
        confirm.setContentText("Strategy: " + strategyId + "\n" + "Trades: " + trades + "\n" + "Return: "
                + String.format(Locale.ROOT, "%.2f%%", totalReturn) + "\n" + "Win Rate: "
                + String.format(Locale.ROOT, "%.1f%%", winRate) + "\n" + "Max Drawdown: "
                + String.format(Locale.ROOT, "%.2f%%", drawdown) + "\n" + "Score: "
                + String.format(Locale.ROOT, "%.1f", score));

        confirm.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) {
                return;
            }

            try {
                StrategyAssignment assignment = StrategySelectionService.getInstance().manuallyAssign(symbol,
                        input.selectedTimeframe(), strategyId, true,
                        "Assigned from Backtesting panel after user-reviewed backtest: " + String.format(Locale.ROOT,
                                "return %.2f%%, win rate %.1f%%, max drawdown %.2f%%, trades %d", totalReturn, winRate,
                                drawdown, trades),
                        score);

                statusLabel.setText("Assigned " + assignment.getStrategyId() + " to " + symbol + " "
                        + input.selectedTimeframe().getCode());
                showAlert("Strategy Assigned", assignment.getStrategyId() + " is now assigned to " + symbol + " / "
                        + input.selectedTimeframe().getCode() + ".", Alert.AlertType.INFORMATION);
            } catch (Exception exception) {
                log.error("Failed to assign backtested strategy", exception);
                showAlert("Assignment Failed", exception.getMessage(), Alert.AlertType.ERROR);
            }
        });
    }

    private String assignmentStrategyId(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            return StrategyCatalog.defaultStrategyName();
        }
        if (strategyName.startsWith("[User] ")) {
            return strategyName.substring("[User] ".length()).trim();
        }
        return strategyName.trim();
    }

    private void compareResults() {
        try {
            if (tradesTable.getItems().isEmpty()) {
                showAlert("No Results", "No backtest results to compare. Please run a backtest first.",
                        Alert.AlertType.WARNING);
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

            Button closeBtn = new Button("Close");
            closeBtn.setStyle(primaryButtonStyle("#ef4444"));
            closeBtn.setOnAction(e -> comparisonStage.close());

            HBox buttonBox = new HBox(10);
            buttonBox.setAlignment(Pos.CENTER);
            buttonBox.getChildren().add(closeBtn);

            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setContent(new VBox(15, paramBox, metricsBox, tradeBox));
            scrollPane.setFitToWidth(true);
            scrollPane.setStyle("-fx-background-color: #0f172a; -fx-control-inner-background: #0f172a;");

            mainBox.getChildren().addAll(titleLabel, new Separator(), scrollPane, new Separator(), buttonBox);

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
        section.setStyle("-fx-background-color: #1e293b; " + "-fx-border-color: #3b82f6; " + "-fx-border-width: 1; "
                + "-fx-border-radius: 6; " + "-fx-background-radius: 6;");

        Label sectionTitle = new Label("Test Parameters");
        sectionTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #60a5fa;");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);

        Timeframe tf = timeframeCombo.getValue();

        addParameterRow(grid, 0, "Symbol:",
                symbolCombo.getValue() != null ? displayTradePair(symbolCombo.getValue()) : "N/A");
        addParameterRow(grid, 1, "Strategy:", strategyCombo.getValue());
        addParameterRow(grid, 2, "Timeframe:", tf == null ? "N/A" : displayTimeframe(tf));
        addParameterRow(grid, 3, "Number of Bars:", String.valueOf(barCountSpinner.getValue()));
        addParameterRow(grid, 4, "Initial Balance:", String.format(Locale.ROOT, "$%.2f", getInitialBalance()));
        addParameterRow(grid, 5, "Data Quality:", dataQualityLabel.getText());

        section.getChildren().addAll(sectionTitle, grid);
        return section;
    }

    private VBox createMetricsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: #1e293b; " + "-fx-border-color: #10b981; " + "-fx-border-width: 1; "
                + "-fx-border-radius: 6; " + "-fx-background-radius: 6;");

        Label sectionTitle = new Label("Performance Metrics");
        sectionTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #10b981;");

        GridPane grid = new GridPane();
        grid.setHgap(30);
        grid.setVgap(12);

        addMetricRow(grid, 0, "Total Return:", validateDisplayValue(metricDisplayValue(totalReturnLabel), "0.00%"),
                "#10b981");
        addMetricRow(grid, 1, "Win Rate:", validateDisplayValue(metricDisplayValue(winRateLabel), "0.00%"), "#3b82f6");
        addMetricRow(grid, 2, "Max Drawdown:", validateDisplayValue(metricDisplayValue(maxDrawdownLabel), "0.00%"),
                "#f59e0b");
        addMetricRow(grid, 3, "Sharpe Ratio:", validateDisplayValue(metricDisplayValue(sharpeLabel), "0.00"),
                "#8b5cf6");
        addMetricRow(grid, 4, "Profit Factor:", validateDisplayValue(metricDisplayValue(profitFactorLabel), "0.00"),
                "#ec4899");

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
        section.setStyle("-fx-background-color: #1e293b; " + "-fx-border-color: #f59e0b; " + "-fx-border-width: 1; "
                + "-fx-border-radius: 6; " + "-fx-background-radius: 6;");

        Label sectionTitle = new Label("Trade Summary");
        sectionTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #f59e0b;");

        GridPane grid = new GridPane();
        grid.setHgap(30);
        grid.setVgap(12);

        List<BacktestTrade> trades = tradesTable == null || tradesTable.getItems() == null ? List.of()
                : tradesTable.getItems().stream().filter(Objects::nonNull).toList();

        int totalTrades = trades.size();

        long winningTrades = trades.stream().filter(trade -> trade.getProfit() > 0).count();

        long losingTrades = trades.stream().filter(trade -> trade.getProfit() < 0).count();

        double avgProfit = cleanNumber(trades.stream().mapToDouble(BacktestTrade::getProfit).average().orElse(0.0));

        double bestTrade = cleanNumber(trades.stream().mapToDouble(BacktestTrade::getProfit).max().orElse(0.0));

        double worstTrade = cleanNumber(trades.stream().mapToDouble(BacktestTrade::getProfit).min().orElse(0.0));

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
        return "-fx-padding: 10px 25px; " + "-fx-font-size: 14px; " + "-fx-background-color: " + color + "; "
                + "-fx-text-fill: white; " + "-fx-background-radius: 6; " + "-fx-cursor: hand;";
    }

    private record DataReadiness(boolean ready, String message) {
    }

    private record ExitDecision(boolean exit, double exitPrice, String reason) {
    }

    private record BacktestInput(String strategyName, TradePair selectedPair, Timeframe selectedTimeframe,
            ORDER_TYPES orderType, double initialBalance, LocalDate startDate, LocalDate endDate, int requestedBars,
            String validationMessage) {

        static BacktestInput invalid(String validationMessage) {
            return new BacktestInput(null, null, null, ORDER_TYPES.MARKET, DEFAULT_INITIAL_EQUITY, null, null,
                    MIN_ABSOLUTE_TEST_BARS, validationMessage);
        }

        boolean isValid() {
            return validationMessage != null && !validationMessage.isBlank();
        }
    }

    /**
     * BacktestTrade stores one completed simulated trade.
     */
    @Data
    @AllArgsConstructor
    public static class BacktestTrade {
        private LocalDate date;
        private Side side;
        private ORDER_TYPES orderType;
        private double price;
        private double exitPrice;
        private double quantity;
        private double profit;
        private double returnPercent;
        private String reason;
        private int entryIndex;
        private int exitIndex;
    }
}
