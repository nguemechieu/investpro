package org.investpro.investpro.ui;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.investpro.investpro.*;
import org.investpro.investpro.ai.InvestProAIBacktester;
import org.investpro.investpro.ai.PredictorRuntimeManager;
import org.investpro.investpro.ai.StrategyRecommendationEngine;
import org.investpro.investpro.models.Account;
import org.investpro.investpro.models.Position;
import org.investpro.investpro.models.TradePair;
import org.investpro.investpro.ui.charts.CandleStickChartContainer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class DisplayExchangeUI extends AnchorPane{
    private static final Logger logger = LoggerFactory.getLogger(DisplayExchangeUI.class);
    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.0000");
    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Map<String, Integer> TIMEFRAME_SECONDS = Map.ofEntries(
            Map.entry("1m", 60),
            Map.entry("5m", 300),
            Map.entry("15m", 900),
            Map.entry("30m", 1800),
            Map.entry("1h", 3600),
            Map.entry("4h", 14400),
            Map.entry("1d", 86400),
            Map.entry("1w", 604800),
            Map.entry("1mn", 2592000)
    );

    private final Exchange exchange;
    private final String tokens;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService workspaceRefreshExecutor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private static final List<String> STRATEGY_OPTIONS = List.of(
            "AI Hybrid",
            "Momentum Breakout",
            "Trend Follow",
            "Mean Reversion",
            "Scalper",
            "Market Making"
    );

    private final ComboBox<String> symbolPicker = new ComboBox<>();
    private final ComboBox<String> autotradeScopePicker = new ComboBox<>();
    private final ComboBox<TradingMode> sessionModePicker = new ComboBox<>();
    private final TableView<MarketWatchRow> marketWatchTable = new TableView<>();
    private final TabPane chartTabPane = new TabPane();
    private final TabPane leftDockTabs = new TabPane();
    private final TabPane rightDockTabs = new TabPane();
    private final Map<String, Button> timeframeButtons = new LinkedHashMap<>();
    private final Map<Stage, DetachedChartWindow> detachedChartWindows = new LinkedHashMap<>();
    private final Map<Stage, DetachedDockWindow> detachedDockWindows = new LinkedHashMap<>();

    private final Label marketCountValueLabel = createMetricValue("Loading...");
    private final Label chartCountValueLabel = createMetricValue("0");
    private final Label detachedCountValueLabel = createMetricValue("0");
    private final Label serviceValueLabel = createMetricValue("Connecting...");
    private final Label sessionBadge = createBadge("PAPER", "terminal-session-badge");
    private final Label aiActivityLabel = createBadge("AI Idle", "terminal-ai-badge");
    private final Label connectionIndicator = createBadge("CONNECTING", "terminal-connection-badge");
    private final Label activeSymbolLabel = new Label("No chart selected");
    private final Label strategyHeadlineLabel = new Label("Open a symbol to begin.");
    private final Label strategyBodyLabel = new Label("The active chart, timeframe, indicators, and AI state will appear here.");
    private final Label strategyMetaLabel = new Label("Manual execution stays available even when AI is idle.");
    private final Label systemStatusExchangeLabel = new Label();
    private final Label systemStatusSymbolLabel = new Label();
    private final Label systemStatusChartsLabel = new Label();
    private final Label systemStatusConnectionLabel = new Label();
    private final Label indicatorHintLabel = new Label("Open a chart to reveal indicator controls.");
    private final Label emptyStateLabel = new Label("Open a symbol to populate the Sopotek-style terminal.");
    private final VBox indicatorHost = new VBox();
    private final TextArea systemConsole = new TextArea();
    private final ProgressBar liveModeBar = new ProgressBar();

    // MT4-style price display
    private final Label priceTickerLabel = createBadge("--", "price-ticker");
    private final Label bidAskLabel = createBadge("Bid: -- | Ask: --", "price-info");

    private final CheckMenuItem volumeBarsMenuItem = new CheckMenuItem("Show Volume");
    private final CheckMenuItem bidAskLinesMenuItem = new CheckMenuItem("Show Bid / Ask Lines");
    private final CheckMenuItem allIndicatorsMenuItem = new CheckMenuItem("All Indicators");
    private final CheckMenuItem sma20MenuItem = new CheckMenuItem("SMA 20");
    private final CheckMenuItem ema50MenuItem = new CheckMenuItem("EMA 50");
    private final CheckMenuItem bollingerMenuItem = new CheckMenuItem("Bollinger Bands");
    private final CheckMenuItem rsi14MenuItem = new CheckMenuItem("RSI 14");
    private final CheckMenuItem macdMenuItem = new CheckMenuItem("MACD");
    private final CheckMenuItem stochastic14MenuItem = new CheckMenuItem("Stochastic 14");
    private final ObservableList<SignalMonitorRow> signalMonitorRows = FXCollections.observableArrayList();
    private final ObservableList<AgentTimelineRow> agentTimelineRows = FXCollections.observableArrayList();
    private final ObservableList<MetricRow> quantPmRows = FXCollections.observableArrayList();
    private final ObservableList<PositionAnalysisRow> positionAnalysisRows = FXCollections.observableArrayList();
    private final ObservableList<StrategyInsightRow> strategyInsightRows = FXCollections.observableArrayList();
    private final ObservableList<MarketWatchRow> marketWatchRows = FXCollections.observableArrayList();
    private final ObservableList<StrategyAssignmentRow> strategyAssignmentRows = FXCollections.observableArrayList();
    private final Map<String, String> signalSignatureBySymbol = new LinkedHashMap<>();
    private final Map<String, Double[]> marketSnapshotCache = new LinkedHashMap<>();
    private final Map<String, Stage> detachedToolWindows = new LinkedHashMap<>();
    private final Map<String, StrategyAssignmentRow> strategyAssignmentsBySymbol = new LinkedHashMap<>();
    private final Set<String> watchedSymbols = new LinkedHashSet<>();
    private final TradingMode initialTradingMode;
    private final AtomicBoolean autoAssignmentInFlight = new AtomicBoolean(false);

    private final AccountSummaryUI accountSummaryUI;
    private final PositionsUI positionsUI;
    private final OrdersUI ordersUI;
    private final PendingOrdersUI pendingOrdersUI;
    private final CoinInfoUI coinInfoUI;
    private final NewsUI newsUI;
    private final Browser browserUI = new Browser();

    public DisplayExchangeUI(@NotNull Exchange exchange, String tokens, TradingMode tradingMode) {
        this.exchange = exchange;
        this.tokens = tokens == null ? "" : tokens.trim();
        this.initialTradingMode = tradingMode == null ? TradingMode.PAPER : tradingMode;
        this.accountSummaryUI = new AccountSummaryUI(exchange);
        this.positionsUI = new PositionsUI(exchange);
        this.ordersUI = new OrdersUI(exchange);
        this.pendingOrdersUI = new PendingOrdersUI(exchange);
        this.coinInfoUI = new CoinInfoUI(exchange);
        this.newsUI = new NewsUI(exchange);

        initializeUI();
    }

    private void initializeUI() {
        getStyleClass().add("terminal-root");

        VBox root = new VBox(
                createMenuBar(),
                createPrimaryToolbar(),
                createSecondaryToolbar(),
                createWorkspaceSplit()
        );
        root.getStyleClass().add("terminal-shell");
        root.setPadding(new Insets(16));
        root.setSpacing(12);
        root.setFillWidth(true);
        root.setMinSize(0, 0);
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        AnchorPane.setTopAnchor(root, 0.0);
        AnchorPane.setRightAnchor(root, 0.0);
        AnchorPane.setBottomAnchor(root, 0.0);
        AnchorPane.setLeftAnchor(root, 0.0);
        getChildren().setAll(root);

        configureInteractiveState();
        warmUpPredictor();
        loadTradePairs(false);
        startWorkspaceRefreshLoop();
        logAgentEvent("SYSTEM", "desk_boot", "Sopotek-style trading desk initialized.");
        appendConsole("Terminal ready for " + exchange.getClass().getSimpleName() + ".");
    }

    private void configureInteractiveState() {
        chartTabPane.getStyleClass().addAll("terminal-chart-tabs", "terminal-dock-tabs");
        chartTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        chartTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> syncActiveChartState());

        marketWatchTable.setItems(marketWatchRows);
        marketWatchTable.getStyleClass().add("terminal-data-table");
        marketWatchTable.setEditable(true);
        marketWatchTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        marketWatchTable.setPlaceholder(new Label("Markets will appear here once the exchange responds."));
        TableColumn<MarketWatchRow, Boolean> watchColumn = new TableColumn<>("Watch");
        watchColumn.setCellValueFactory(cell -> cell.getValue().watchedProperty());
        watchColumn.setCellFactory(CheckBoxTableCell.forTableColumn(watchColumn));
        watchColumn.setEditable(true);
        watchColumn.setSortable(false);
        marketWatchTable.getColumns().setAll(
                watchColumn,
                textColumn("Symbol", MarketWatchRow::symbol),
                textColumn("Bid", MarketWatchRow::bid),
                textColumn("Ask", MarketWatchRow::ask),
                textColumn("AI Training", MarketWatchRow::aiTraining)
        );
        marketWatchTable.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() >= 1) {
                MarketWatchRow selected = marketWatchTable.getSelectionModel().getSelectedItem();
                if (selected != null && selected.symbol() != null && !selected.symbol().isBlank()) {
                    openOrFocusChart(selected.symbol());
                }
            }
        });

        liveModeBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        liveModeBar.setMaxWidth(Double.MAX_VALUE);
        liveModeBar.getStyleClass().add("terminal-live-progress");

        systemConsole.setEditable(false);
        systemConsole.setWrapText(true);
        systemConsole.getStyleClass().add("terminal-console");
        systemConsole.setPrefRowCount(8);

        activeSymbolLabel.getStyleClass().add("terminal-panel-title");
        strategyHeadlineLabel.getStyleClass().add("terminal-panel-title");
        strategyBodyLabel.getStyleClass().add("terminal-panel-copy");
        strategyMetaLabel.getStyleClass().add("terminal-panel-meta");
        indicatorHintLabel.getStyleClass().add("terminal-panel-copy");
        emptyStateLabel.getStyleClass().add("terminal-empty-state");
        emptyStateLabel.setWrapText(true);
        emptyStateLabel.setMaxWidth(420);

        systemStatusExchangeLabel.getStyleClass().add("terminal-status-line");
        systemStatusSymbolLabel.getStyleClass().add("terminal-status-line");
        systemStatusChartsLabel.getStyleClass().add("terminal-status-line");
        systemStatusConnectionLabel.getStyleClass().add("terminal-status-line");

        autotradeScopePicker.getItems().setAll("All Symbols", "Selected Symbol", "Watchlist");
        autotradeScopePicker.getSelectionModel().select("Selected Symbol");

        sessionModePicker.getItems().setAll(TradingMode.values());
        sessionModePicker.getStyleClass().add("terminal-combo-box");
        sessionModePicker.setMinWidth(110);
        sessionModePicker.getSelectionModel().select(initialTradingMode);
        sessionModePicker.valueProperty().addListener(( obs, oldValue, newValue) -> {
            if (newValue != null && newValue != oldValue) {
                applyTradingMode(newValue, true);
            }
        });
        applyTradingMode(initialTradingMode, false);

        volumeBarsMenuItem.setOnAction(event ->
                withActiveChart(chart -> {
                    chart.toggleVolumeBars();
                    syncActiveChartState();
                }, "Open a chart before toggling volume bars."));
        bidAskLinesMenuItem.setOnAction(event ->
                withActiveChart(chart -> {
                    chart.toggleBidAskLines();
                    syncActiveChartState();
                }, "Open a chart before toggling bid/ask lines."));
        allIndicatorsMenuItem.setOnAction(event ->
                updateIndicatorSelection(options -> options.setAllIndicatorsVisible(allIndicatorsMenuItem.isSelected())));
        sma20MenuItem.setOnAction(event ->
                updateIndicatorSelection(options -> options.setShowSma20(sma20MenuItem.isSelected())));
        ema50MenuItem.setOnAction(event ->
                updateIndicatorSelection(options -> options.setShowEma50(ema50MenuItem.isSelected())));
        bollingerMenuItem.setOnAction(event ->
                updateIndicatorSelection(options -> options.setShowBollingerBands(bollingerMenuItem.isSelected())));
        rsi14MenuItem.setOnAction(event ->
                updateIndicatorSelection(options -> options.setShowRsi14(rsi14MenuItem.isSelected())));
        macdMenuItem.setOnAction(event ->
                updateIndicatorSelection(options -> options.setShowMacd(macdMenuItem.isSelected())));
        stochastic14MenuItem.setOnAction(event ->
                updateIndicatorSelection(options -> options.setShowStochastic14(stochastic14MenuItem.isSelected())));

        updateSystemStatus(null);
        seedWorkspaceModels();
    }

    private void warmUpPredictor() {
        aiActivityLabel.setText("AI Starting");
        executorService.submit(() -> {
            boolean healthy = PredictorRuntimeManager.ensureAvailable(Duration.ofSeconds(15));
            FxLifecycle.runLaterIf(() -> !disposed.get(), () -> {
                aiActivityLabel.setText(healthy ? "AI Ready" : "AI Offline");
                appendConsole(healthy
                        ? "AI predictor is ready on localhost:" + Integer.getInteger("investpro.ai.port", 50051) + "."
                        : "AI predictor is offline. Open AI Training for a health check.");
                logAgentEvent("SYSTEM", "ai_startup", healthy
                        ? "Local AI predictor is ready."
                        : "Local AI predictor did not become ready.");
            });
        });
    }

    private Node createWorkspaceSplit() {
        chartTabPane.setMinSize(0, 0);
        chartTabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        StackPane chartSurface = new StackPane(chartTabPane, emptyStateLabel);
        chartSurface.getStyleClass().add("terminal-center-card");
        chartSurface.setMinSize(0, 0);
        chartSurface.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        leftDockTabs.getTabs().setAll(
                createDockTab("Market Watch", buildMarketWatchPanel()),
                createDockTab("AI Signal Monitor", buildSignalMonitorPanel()),
                createDockTab("Strategy Scorecard", buildStrategyScorecardPanel()),
                createDockTab("Indicators", buildIndicatorPanel()),
                createDockTab("AI Training", buildAiTrainingPanel())
        );
        rightDockTabs.getTabs().setAll(
                createDockTab("Orderbook", wrapDockContent(coinInfoUI)),
                createDockTab("Terminal", buildTerminalPanel()),
                createDockTab("Positions", wrapDockContent(positionsUI)),
                createDockTab("Orders", wrapDockContent(pendingOrdersUI)),
                createDockTab("Risk", wrapDockContent(accountSummaryUI)),
                createDockTab("Quant PM", buildQuantPmPanel())
        );

        styleDockTabs(leftDockTabs);
        styleDockTabs(rightDockTabs);

        StackPane leftDock = dockShell(leftDockTabs, 300);
        StackPane rightDock = dockShell(rightDockTabs, 320);

        SplitPane centerSplit = new SplitPane(leftDock, chartSurface, rightDock);
        centerSplit.getStyleClass().add("terminal-main-split");
        centerSplit.setDividerPositions(0.18, 0.80);
        centerSplit.setMinSize(0, 0);
        centerSplit.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(centerSplit, Priority.ALWAYS);
        return centerSplit;
    }

    private void styleDockTabs(TabPane tabPane) {
        tabPane.getStyleClass().add("terminal-dock-tabs");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setMinSize(0, 0);
        tabPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    private StackPane dockShell(Node content, double prefWidth) {
        StackPane shell = new StackPane(content);
        shell.getStyleClass().add("terminal-dock-shell");
        shell.setMinSize(0, 0);
        shell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        shell.setPrefWidth(prefWidth);
        return shell;
    }

    private Tab createDockTab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private StackPane wrapDockContent(Node content) {
        StackPane wrapper = new StackPane(content);
        wrapper.getStyleClass().add("terminal-dock-card");
        wrapper.setPadding(new Insets(14));
        wrapper.setMinSize(0, 0);
        wrapper.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        if (content instanceof Region region) {
            region.setMinSize(0, 0);
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }
        return wrapper;
    }

    private Node buildMarketWatchPanel() {
        Label title = createPanelTitle("Market Watch");
        Label copy = createPanelCopy("Sopotek-style symbol board with live quote slots, AI training state, and one-click chart focus.");

        Button openButton = createToolbarButton("Open");
        openButton.setOnAction(event -> {
            MarketWatchRow selectedRow = marketWatchTable.getSelectionModel().getSelectedItem();
            if (selectedRow != null && selectedRow.symbol() != null && !selectedRow.symbol().isBlank()) {
                openOrFocusChart(selectedRow.symbol());
            } else {
                showError("Choose a market from Market Watch first.");
            }
        });

        Button signalButton = createToolbarButton("Signals");
        signalButton.setOnAction(event -> openSignalMonitorWindow());

        Button refreshButton = createToolbarButton("Refresh");
        refreshButton.setOnAction(event -> loadTradePairs(true));

        FlowPane actions = createActionRow(openButton, signalButton, refreshButton);

        VBox panel = new VBox(12, title, copy, marketWatchTable, actions);
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(marketWatchTable, Priority.ALWAYS);
        return wrapDockContent(panel);
    }

    private Node buildSignalMonitorPanel() {
        Label title = createPanelTitle("AI Signal Monitor");
        Label copy = createPanelCopy("Live chart bias, AI state, and signal posture for the open Sopotek-style workspace.");
        TableView<SignalMonitorRow> table = createSignalMonitorTable();
        table.setItems(signalMonitorRows);

        Button strategyButton = createToolbarButton("Strategy");
        strategyButton.setOnAction(event -> openStrategyAssignerWindow());

        Button trainButton = createToolbarButton("AI Training");
        trainButton.setOnAction(event -> openAiTrainingWindow());

        VBox panel = new VBox(12, title, copy, table, createActionRow(strategyButton, trainButton));
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        return wrapDockContent(panel);
    }

    private Node buildStrategyScorecardPanel() {
        Button manualTradeButton = createToolbarButton("Trade");
        manualTradeButton.setOnAction(event ->
                withActiveChart(CandleStickChartContainer::showTradeTicket, "Open a chart before creating a manual trade."));

        Button autoButton = createToolbarButton("AI Toggle");
        autoButton.setOnAction(event -> setAutoTrading(!isAnyTargetChartAutoTrading()));

        Button trainingButton = createToolbarButton("AI Train");
        trainingButton.setOnAction(event -> openAiTrainingWindow());

        Button strategyButton = createToolbarButton("Strategy");
        strategyButton.setOnAction(event -> openStrategyAssignerWindow());

        VBox panel = new VBox(
                12,
                createPanelTitle("Strategy Scorecard"),
                activeSymbolLabel,
                strategyHeadlineLabel,
                strategyBodyLabel,
                strategyMetaLabel,
                createActionRow(manualTradeButton, autoButton, strategyButton, trainingButton)
        );
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return wrapDockContent(panel);
    }

    private Node buildStrategyDebugPanel() {
        Label title = createPanelTitle("Strategy Debug");
        Label copy = createPanelCopy("Indicator posture, loaded-candle depth, and execution mode for the charts currently open in the desk.");
        TableView<StrategyInsightRow> table = createStrategyInsightTable();
        table.setItems(strategyInsightRows);

        Button recommendationsButton = createToolbarButton("Signals");
        recommendationsButton.setOnAction(event -> openSignalMonitorWindow());

        Button aiTrainingButton = createToolbarButton("AI Train");
        aiTrainingButton.setOnAction(event -> openAiTrainingWindow());

        Button strategyButton = createToolbarButton("Strategy");
        strategyButton.setOnAction(event -> openStrategyAssignerWindow());

        VBox panel = new VBox(12, title, copy, table, createActionRow(recommendationsButton, strategyButton, aiTrainingButton));
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        return wrapDockContent(panel);
    }

    private Node buildIndicatorPanel() {
        indicatorHost.setSpacing(10);
        indicatorHost.setFillWidth(true);
        indicatorHost.getChildren().setAll(indicatorHintLabel);
        VBox.setVgrow(indicatorHost, Priority.ALWAYS);

        VBox panel = new VBox(
                12,
                createPanelTitle("Indicators"),
                createPanelCopy("This panel mirrors the active chart's indicator switches so you can manage overlays without hunting for controls."),
                indicatorHost
        );
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return wrapDockContent(panel);
    }

    private Node buildSystemStatusPanel() {
        HBox metrics = new HBox(
                10,
                createMetricCard("Markets", marketCountValueLabel),
                createMetricCard("Charts", chartCountValueLabel),
                createMetricCard("Detached", detachedCountValueLabel),
                createMetricCard("Service", serviceValueLabel)
        );
        metrics.getStyleClass().add("terminal-metric-strip");
        metrics.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(
                10,
                createPanelTitle("System Status"),
                metrics,
                systemStatusExchangeLabel,
                systemStatusSymbolLabel,
                systemStatusChartsLabel,
                systemStatusConnectionLabel
        );
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return wrapDockContent(panel);
    }

    private Node buildTerminalPanel() {
        VBox panel = new VBox(
                10,
                createPanelTitle("Terminal"),
                systemStatusExchangeLabel,
                systemStatusSymbolLabel,
                systemStatusChartsLabel,
                systemStatusConnectionLabel,
                systemConsole
        );
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(systemConsole, Priority.ALWAYS);
        return wrapDockContent(panel);
    }

    private Node buildAgentTimelinePanel() {
        Label title = createPanelTitle("Live Agent Timeline");
        Label copy = createPanelCopy("Watch the live multi-agent flow across symbols, from signal posture and strategy routing through chart state and AI execution.");
        TextField filterField = new TextField();
        filterField.setPromptText("Filter by symbol, event, strategy, timeframe, or detail");
        filterField.getStyleClass().add("terminal-combo-box");

        FilteredList<AgentTimelineRow> filteredRows = new FilteredList<>(agentTimelineRows, row -> true);
        TableView<AgentTimelineRow> table = createAgentTimelineTable();
        table.setItems(filteredRows);

        Label assignmentLabel = createPanelCopy("Current Assignment: Select an event or symbol to inspect the active routing.");
        Label detailLabel = createPanelCopy("Live Agent Detail: Select an event to inspect the chart, stage, and strategy context.");
        assignmentLabel.setWrapText(true);
        detailLabel.setWrapText(true);

        Runnable refreshFilter = () -> {
            String query = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase();
            filteredRows.setPredicate(row -> query.isBlank()
                    || row.symbol().toLowerCase().contains(query)
                    || row.kind().toLowerCase().contains(query)
                    || row.actorEvent().toLowerCase().contains(query)
                    || row.stage().toLowerCase().contains(query)
                    || row.strategy().toLowerCase().contains(query)
                    || row.timeframe().toLowerCase().contains(query)
                    || row.detail().toLowerCase().contains(query));
        };
        filterField.textProperty().addListener((obs, oldValue, newValue) -> refreshFilter.run());
        refreshFilter.run();

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, row) -> {
            if (row == null) {
                assignmentLabel.setText("Current Assignment: Select an event or symbol to inspect the active routing.");
                detailLabel.setText("Live Agent Detail: Select an event to inspect the chart, stage, and strategy context.");
                return;
            }
            StrategyAssignmentRow assignment = strategyAssignmentsBySymbol.get(row.symbol());
            assignmentLabel.setText("Current Assignment: "
                    + (assignment == null
                    ? "No explicit strategy assignment for " + row.symbol() + "."
                    : row.symbol() + " -> " + assignment.strategy() + " / " + assignment.timeframe() + " / " + assignment.status()));
            detailLabel.setText("Live Agent Detail: " + row.kind() + " | " + row.actorEvent() + " | " + row.stage()
                    + " | " + row.strategy() + " | " + row.timeframe() + " | " + row.detail());
        });

        Button openChartButton = createToolbarButton("Open");
        openChartButton.setOnAction(event -> {
            AgentTimelineRow selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && selected.symbol() != null && !selected.symbol().isBlank() && !"SYSTEM".equals(selected.symbol())) {
                openOrFocusChart(selected.symbol());
            } else {
                showError("Select a symbol in Live Agent first.");
            }
        });

        Button assignButton = createToolbarButton("Strategy");
        assignButton.setOnAction(event -> {
            AgentTimelineRow selected = table.getSelectionModel().getSelectedItem();
            openStrategyAssignerWindow(selected == null ? resolvePreferredSymbol() : selected.symbol());
        });

        Button trainingButton = createToolbarButton("AI Train");
        trainingButton.setOnAction(event -> openAiTrainingWindow());

        Button clearButton = createToolbarButton("Clear");
        clearButton.setOnAction(event -> agentTimelineRows.clear());

        VBox panel = new VBox(
                12,
                title,
                copy,
                filterField,
                table,
                assignmentLabel,
                detailLabel,
                createActionRow(openChartButton, assignButton, trainingButton, clearButton)
        );
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        return wrapDockContent(panel);
    }

    private Node buildQuantPmPanel() {
        Label title = createPanelTitle("Quant PM");
        Label copy = createPanelCopy("Portfolio analytics, exposure posture, and desk-wide AI coverage similar to Sopotek's quant portfolio manager.");
        TableView<MetricRow> table = createMetricTable();
        table.setItems(quantPmRows);

        Button positionButton = createToolbarButton("Positions");
        positionButton.setOnAction(event -> openPositionAnalysisWindow());

        Button riskButton = createToolbarButton("Risk Workspace");
        riskButton.setOnAction(event -> applyWorkspacePreset("risk"));

        VBox panel = new VBox(12, title, copy, table, createActionRow(positionButton, riskButton));
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        return wrapDockContent(panel);
    }

    private Node buildPositionAnalysisPanel() {
        Label title = createPanelTitle("Position Analysis");
        Label copy = createPanelCopy("Open exposure, direction bias, and per-position notes for the current broker/account session.");
        TableView<PositionAnalysisRow> table = createPositionAnalysisTable();
        table.setItems(positionAnalysisRows);

        Button quantButton = createToolbarButton("Quant PM");
        quantButton.setOnAction(event -> openQuantPmWindow());

        VBox panel = new VBox(12, title, copy, table, quantButton);
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        return wrapDockContent(panel);
    }

    private MenuBar createMenuBar() {
        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(
                createMenuItem("New Chart", this::openSelectedSymbol),
                createMenuItem("Close Chart", this::closeCurrentChart),
                createMenuItem("Close All Charts", this::closeAllCharts),
                new SeparatorMenuItem(),
                createMenuItem("Exit", this::hideWindow)
        );

        Menu viewMenu = new Menu("View");
        viewMenu.getItems().addAll(
                createMenuItem("Market Watch", () -> selectDockTab(leftDockTabs, "Market Watch")),
                createMenuItem("Navigator", () -> selectDockTab(leftDockTabs, "Strategy Scorecard")),
                createMenuItem("Terminal", () -> selectDockTab(rightDockTabs, "Terminal")),
                createMenuItem("Orders", () -> selectDockTab(rightDockTabs, "Orders")),
                createMenuItem("Positions", () -> selectDockTab(rightDockTabs, "Positions")),
                createMenuItem("Risk", () -> selectDockTab(rightDockTabs, "Risk")),
                createMenuItem("AI Signal Monitor", () -> selectDockTab(leftDockTabs, "AI Signal Monitor")),
                createMenuItem("AI Training", () -> selectDockTab(leftDockTabs, "AI Training"))
        );

        Menu chartsMenu = new Menu("Charts");
        chartsMenu.getItems().addAll(
                createMenuItem("New Chart", this::openSelectedSymbol),
                createMenuItem("Multi Chart", this::multiChartLayout),
                new SeparatorMenuItem(),
                createMenuItem("Detach Current Tab", this::detachCurrentChartTab),
                createMenuItem("Reattach Active Chart", this::reattachActiveChartWindow),
                createMenuItem("Fit Active Chart", () ->
                        withActiveChart(CandleStickChartContainer::fitChart, "Open a chart before fitting it.")),
                createMenuItem("Tile Chart Windows", this::tileDetachedChartWindows),
                createMenuItem("Cascade Chart Windows", this::cascadeDetachedChartWindows),
                new SeparatorMenuItem(),
                bidAskLinesMenuItem,
                volumeBarsMenuItem
        );

        Menu insertMenu = new Menu("Insert");
        insertMenu.getItems().addAll(
                createMenuItem("Indicators", () -> selectDockTab(leftDockTabs, "Indicators")),
                allIndicatorsMenuItem,
                new SeparatorMenuItem(),
                sma20MenuItem,
                ema50MenuItem,
                bollingerMenuItem,
                rsi14MenuItem,
                macdMenuItem,
                stochastic14MenuItem
        );

        Menu toolsMenu = new Menu("Tools");
        toolsMenu.getItems().addAll(
                createMenuItem("Refresh Markets", () -> loadTradePairs(true)),
                createMenuItem("Refresh Chart", () ->
                        withActiveChart(CandleStickChartContainer::refreshChart, "Open a chart before refreshing it.")),
                createMenuItem("Jump To Latest", () ->
                        withActiveChart(CandleStickChartContainer::jumpToLatestCandle, "Open a chart before jumping to the latest candle.")),
                new SeparatorMenuItem(),
                createMenuItem("Manual Trade", () ->
                        withActiveChart(CandleStickChartContainer::showTradeTicket, "Open a chart before placing a manual trade.")),
                createMenuItem("Start Auto Trading", () -> setAutoTrading(true)),
                createMenuItem("Stop Auto Trading", () -> setAutoTrading(false)),
                createMenuItem("Emergency Kill Switch", this::activateKillSwitch),
                new SeparatorMenuItem(),
                createMenuItem("Strategy Assigner", this::openStrategyAssignerWindow),
                createMenuItem("Auto Assign Best", () -> scheduleAutoAssignment(true)),
                createMenuItem("AI Training", this::openAiTrainingWindow)
        );

        Menu windowMenu = new Menu("Window");
        windowMenu.getItems().addAll(
                createMenuItem("Trading Workspace", () -> applyWorkspacePreset("trading")),
                createMenuItem("Risk Workspace", () -> applyWorkspacePreset("risk")),
                createMenuItem("Review Workspace", () -> applyWorkspacePreset("review")),
                new SeparatorMenuItem(),
                createMenuItem("Tile Chart Windows", this::tileDetachedChartWindows),
                createMenuItem("Cascade Chart Windows", this::cascadeDetachedChartWindows)
        );

        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().addAll(
                createMenuItem("About", () -> new Messages(
                        Alert.AlertType.INFORMATION,
                        "InvestPro now uses a Sopotek-style dashboard and terminal shell while keeping InvestPro's exchange and chart workflows."
                )),
                createMenuItem("Close Desk", this::hideWindow)
        );

        MenuBar menuBar = new MenuBar(
                fileMenu,
                viewMenu,
                insertMenu,
                chartsMenu,
                toolsMenu,
                windowMenu,
                helpMenu
        );
        menuBar.getStyleClass().add("terminal-menu-bar");
        return menuBar;
    }

    private Node createPrimaryToolbar() {
        HBox row = new HBox(
                10,
                buildSymbolToolbarBox(),
                buildTimeframeToolbarBox(),
                createGrowingSpacer(),
                buildUtilityToolbarBox()
        );
        row.getStyleClass().add("terminal-toolbar-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node createSecondaryToolbar() {
        Button manualTradeButton = createToolbarButton("Trade");
        manualTradeButton.setOnAction(event ->
                withActiveChart(CandleStickChartContainer::showTradeTicket, "Open a chart before submitting a manual trade."));

        Button refreshButton = createToolbarButton("Refresh");
        refreshButton.setOnAction(event ->
                withActiveChart(CandleStickChartContainer::refreshChart, "Open a chart before refreshing it."));

        Button latestButton = createToolbarButton("Latest");
        latestButton.setOnAction(event ->
                withActiveChart(CandleStickChartContainer::jumpToLatestCandle, "Open a chart before jumping to the latest candle."));

        Button fitButton = createToolbarButton("Fit");
        fitButton.setOnAction(event ->
                withActiveChart(CandleStickChartContainer::fitChart, "Open a chart before fitting it."));

        Button detachButton = createToolbarButton("Detach");
        detachButton.setOnAction(event -> detachCurrentChartTab());

        Button reattachButton = createToolbarButton("Reattach");
        reattachButton.setOnAction(event -> reattachActiveChartWindow());

        Button autoButton = createToolbarButton("AUTO");
        autoButton.getStyleClass().add("terminal-auto-button");
        autoButton.setOnAction(event -> setAutoTrading(!isAnyTargetChartAutoTrading()));

        HBox executionBox = createToolbarBox(
                manualTradeButton,
                refreshButton,
                latestButton,
                fitButton,
                detachButton,
                reattachButton
        );

        Label scopeLabel = createToolbarLabel("Scope");
        HBox aiBox = createToolbarBox(scopeLabel, autotradeScopePicker, autoButton);
        HBox.setHgrow(autotradeScopePicker, Priority.NEVER);

        HBox row = new HBox(10, executionBox, createGrowingSpacer(), aiBox);
        row.getStyleClass().add("terminal-toolbar-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox buildSymbolToolbarBox() {
        symbolPicker.setPromptText("Select symbol");
        symbolPicker.getStyleClass().add("terminal-combo-box");
        symbolPicker.setMinWidth(180);
        symbolPicker.setMaxWidth(240);

        Button openSymbolButton = createToolbarButton("Open");
        openSymbolButton.setOnAction(event -> openSelectedSymbol());

        // MT4-style price ticker
        priceTickerLabel.getStyleClass().add("mt4-price-label");
        priceTickerLabel.setMinWidth(120);
        priceTickerLabel.setPrefWidth(120);

        bidAskLabel.getStyleClass().add("mt4-bid-ask-label");
        bidAskLabel.setMinWidth(180);
        bidAskLabel.setPrefWidth(180);

        return createToolbarBox(
                createToolbarLabel("Symbol"),
                symbolPicker,
                openSymbolButton,
                priceTickerLabel,
                bidAskLabel
        );
    }

    private HBox buildTimeframeToolbarBox() {
        HBox timeframeBox = createToolbarBox(createToolbarLabel("Timeframe"));
        for (String timeframe : List.of("1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w", "1mn")) {
            Button button = new Button(timeframe);
            button.getStyleClass().add("terminal-timeframe-button");
            button.setOnAction(event -> applyTimeframe(timeframe));
            timeframeButtons.put(timeframe, button);
            timeframeBox.getChildren().add(button);
        }
        return timeframeBox;
    }

    private HBox buildUtilityToolbarBox() {
        Button screenshotButton = createToolbarButton("Shot");
        screenshotButton.setOnAction(event ->
                withActiveChart(CandleStickChartContainer::captureScreenshot, "Open a chart before capturing a screenshot."));

        Button killSwitchButton = createToolbarButton("Kill");
        killSwitchButton.getStyleClass().add("terminal-kill-switch");
        killSwitchButton.setOnAction(event -> activateKillSwitch());

        VBox liveBox = new VBox(4, createToolbarLabel("Live Mode"), liveModeBar);
        liveBox.getStyleClass().add("terminal-live-box");
        liveBox.setAlignment(Pos.CENTER);
        liveBox.setPrefWidth(146);

        HBox utilityBox = createToolbarBox(
                connectionIndicator,
                screenshotButton,
                createToolbarLabel("Mode"),
                sessionModePicker,
                sessionBadge,
                liveBox,
                killSwitchButton,
                aiActivityLabel
        );
        utilityBox.setAlignment(Pos.CENTER_LEFT);
        return utilityBox;
    }

    private HBox createToolbarBox(Node... nodes) {
        HBox box = new HBox(8, nodes);
        box.getStyleClass().add("terminal-toolbar-box");
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private FlowPane createActionRow(Node... nodes) {
        FlowPane row = new FlowPane(8, 8, nodes);
        row.getStyleClass().add("terminal-action-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private Region createGrowingSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private Label createToolbarLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("terminal-toolbar-label");
        return label;
    }

    private Button createToolbarButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("terminal-toolbar-button");
        button.setTooltip(new Tooltip(text));
        button.setMinHeight(34);
        button.setMinWidth(Region.USE_PREF_SIZE);
        return button;
    }

    private Label createBadge(String text, String styleClass) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("terminal-badge", styleClass);
        return badge;
    }

    private Label createMetricValue(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("terminal-metric-value");
        return label;
    }

    private void applyTradingMode(TradingMode tradingMode, boolean announce) {
        TradingMode mode = tradingMode == null ? TradingMode.PAPER : tradingMode;
        TradingSession.setMode(mode);
        sessionBadge.setText(mode.badgeLabel());
        if (announce) {
            appendConsole("Switched desk routing to " + mode.label() + " mode.");
            logAgentEvent("SYSTEM", "session_mode", "Switched routing to " + mode.label() + " mode.");
        }
        updateSystemStatus(getActiveChartContainer());
    }

    private Label createPanelTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("terminal-panel-title");
        label.setWrapText(true);
        return label;
    }

    private Label createPanelCopy(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("terminal-panel-copy");
        label.setWrapText(true);
        return label;
    }

    private VBox createMetricCard(String title, Label valueLabel) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("terminal-metric-title");

        VBox card = new VBox(4, titleLabel, valueLabel);
        card.getStyleClass().add("terminal-metric-card");
        return card;
    }

    private MenuItem createMenuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    private void loadTradePairs(boolean forceRefresh) {
        String exchangeName = exchange.getClass().getSimpleName();
        List<String> cachedPairNames = forceRefresh ? List.of() : loadCachedPairNames(exchangeName);
        if (!cachedPairNames.isEmpty()) {
            applyPairNames(cachedPairNames, "cached");
            return;
        }

        serviceValueLabel.setText(exchange.getExchangeMessage() + " | loading markets");
        connectionIndicator.setText("CONNECTING");
        executorService.submit(() -> {
            try {
                List<String> pairNames = exchange.getTradePairs()
                        .stream()
                        .map(pair -> pair.toString('/'))
                        .distinct()
                        .sorted()
                        .toList();
                cachePairNames(exchangeName, pairNames);

                FxLifecycle.runLaterIf(() -> !disposed.get(), () -> {
                    applyPairNames(pairNames, forceRefresh ? "refreshed" : "exchange");
                });
            } catch (Exception e) {
                logger.error("Failed to load trade pairs", e);
                FxLifecycle.runLaterIf(() -> !disposed.get(), () -> {
                    marketCountValueLabel.setText("Unavailable");
                    serviceValueLabel.setText("Trade pairs unavailable");
                    connectionIndicator.setText("DISCONNECTED");
                });
                showError("Failed to load trade pairs: " + e.getMessage());
            }
        });
    }

    private List<String> loadCachedPairNames(String exchangeName) {
        try {
            if (InvestPro.db1 == null) {
                return List.of();
            }
            return InvestPro.db1.loadMarketSymbols(exchangeName)
                    .stream()
                    .filter(symbol -> symbol != null && symbol.contains("/"))
                    .distinct()
                    .sorted()
                    .toList();
        } catch (RuntimeException e) {
            logger.debug("Unable to load cached market symbols for {}", exchangeName, e);
            return List.of();
        }
    }

    private void cachePairNames(String exchangeName, List<String> pairNames) {
        try {
            if (InvestPro.db1 != null && pairNames != null && !pairNames.isEmpty()) {
                InvestPro.db1.saveMarketSymbols(exchangeName, pairNames);
            }
        } catch (RuntimeException e) {
            logger.debug("Unable to cache market symbols for {}", exchangeName, e);
        }
    }

    private void applyPairNames(List<String> pairNames, String source) {
        symbolPicker.setItems(FXCollections.observableArrayList(pairNames));
        marketWatchRows.setAll(buildMarketWatchRows(pairNames));
        if (!pairNames.isEmpty() && symbolPicker.getValue() == null) {
            symbolPicker.getSelectionModel().selectFirst();
        }
        if (!marketWatchRows.isEmpty() && marketWatchTable.getSelectionModel().isEmpty()) {
            marketWatchTable.getSelectionModel().selectFirst();
        }
        marketCountValueLabel.setText(Integer.toString(pairNames.size()));
        serviceValueLabel.setText(exchange.getExchangeMessage());
        connectionIndicator.setText("CONNECTED");
        updateSystemStatus(getActiveChartContainer());
        appendConsole("Loaded " + pairNames.size() + " trade pairs from " + source + ".");
        openInitialChart(pairNames);
    }

    private void openInitialChart(List<String> pairNames) {
        if (pairNames == null || pairNames.isEmpty() || !chartTabPane.getTabs().isEmpty() || !detachedChartWindows.isEmpty()) {
            return;
        }

        String preferredSymbol = resolvePreferredStartupSymbol(pairNames);
        if (preferredSymbol != null && !preferredSymbol.isBlank()) {
            openOrFocusChart(preferredSymbol);
        }
    }

    private String resolvePreferredStartupSymbol(List<String> pairNames) {
        String currentSelection = symbolPicker.getValue();
        if (currentSelection != null && pairNames.contains(currentSelection)) {
            return currentSelection;
        }
        for (String candidate : List.of("BTC/USD", "BTC/USDT", "ETH/USD", "ETH/USDT")) {
            if (pairNames.contains(candidate)) {
                return candidate;
            }
        }
        return pairNames.getFirst();
    }

    private void openSelectedSymbol() {
        String selectedPair = symbolPicker.getSelectionModel().getSelectedItem();
        if (selectedPair == null || selectedPair.isBlank()) {
            showError("Choose a symbol before opening a chart.");
            return;
        }
        openOrFocusChart(selectedPair);
    }

    private void openOrFocusChart(String tradePairStr) {
        Tab existingTab = findAttachedChartTab(tradePairStr);
        if (existingTab != null) {
            chartTabPane.getSelectionModel().select(existingTab);
            syncActiveChartState();
            logAgentEvent(tradePairStr, "chart_focus", "Focused attached chart.");
            return;
        }

        DetachedChartWindow detachedWindow = findDetachedChartWindow(tradePairStr);
        if (detachedWindow != null) {
            detachedWindow.stage.show();
            detachedWindow.stage.toFront();
            detachedWindow.stage.requestFocus();
            logAgentEvent(tradePairStr, "chart_focus", "Focused detached chart window.");
            return;
        }

        addChart(tradePairStr);
    }

    private void addChart(String tradePairStr) {
        try {
            String[] pair = tradePairStr.split("/");
            if (pair.length != 2) {
                throw new IllegalArgumentException("Invalid trade pair format: " + tradePairStr);
            }

            CandleStickChartContainer chartContainer = new CandleStickChartContainer(
                    exchange,
                    new TradePair(pair[0], pair[1]),
                    tokens
            );
            chartContainer.setMinSize(0, 0);
            chartContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

            Tab chartTab = createChartTab(tradePairStr, chartContainer);
            chartTabPane.getTabs().add(chartTab);
            chartTabPane.getSelectionModel().select(chartTab);
            syncActiveChartState();
            appendConsole("Opened chart " + tradePairStr + ".");
            logAgentEvent(tradePairStr, "chart_open", "Opened chart in terminal workspace.");
        } catch (Exception e) {
            logger.error("Error adding chart", e);
            showError("Error adding chart: " + e.getMessage());
        }
    }

    private Tab createChartTab(String title, CandleStickChartContainer chartContainer) {
        Tab chartTab = new Tab(title, chartContainer);
        chartTab.setContextMenu(new ContextMenu(createMenuItem("Detach Chart", () -> {
            chartTabPane.getSelectionModel().select(chartTab);
            detachCurrentChartTab();
        })));
        chartTab.setOnClosed(event -> {
            chartContainer.shutdown();
            chartTab.setContent(null);
            syncActiveChartState();
            appendConsole("Closed chart " + title + ".");
        });
        return chartTab;
    }

    private void multiChartLayout() {
        List<String> markets = marketWatchRows.stream().map(MarketWatchRow::symbol).filter(Objects::nonNull).toList();
        if (markets.isEmpty()) {
            showError("Load markets before opening a multi-chart layout.");
            return;
        }

        int targetCount = Math.min(4, markets.size());
        for (int i = 0; i < targetCount; i++) {
            openOrFocusChart(markets.get(i));
        }
        appendConsole("Built multi-chart layout for " + targetCount + " symbols.");
        logAgentEvent("SYSTEM", "multi_chart", "Built a multi-chart layout for " + targetCount + " symbols.");
    }

    private void applyTimeframe(String timeframe) {
        Integer seconds = TIMEFRAME_SECONDS.get(timeframe);
        if (seconds == null) {
            return;
        }
        withActiveChart(chart -> {
            chart.setSecondsPerCandle(seconds);
            setActiveTimeframeButton(timeframe);
            appendConsole("Switched " + chart.getTradePair().toString('/') + " to " + timeframe + ".");
            logAgentEvent(chart.getTradePair().toString('/'), "timeframe", "Changed timeframe to " + timeframe + ".");
        }, "Open a chart before changing the timeframe.");
    }

    private Tab findAttachedChartTab(String tradePairStr) {
        for (Tab tab : chartTabPane.getTabs()) {
            if (Objects.equals(tab.getText(), tradePairStr)) {
                return tab;
            }
        }
        return null;
    }

    private DetachedChartWindow findDetachedChartWindow(String tradePairStr) {
        for (DetachedChartWindow window : detachedChartWindows.values()) {
            if (Objects.equals(window.title, tradePairStr)) {
                return window;
            }
        }
        return null;
    }

    private void setActiveTimeframeButton(String activeTimeframe) {
        timeframeButtons.forEach((timeframe, button) -> {
            button.getStyleClass().remove("active");
            if (Objects.equals(timeframe, activeTimeframe) && !button.getStyleClass().contains("active")) {
                button.getStyleClass().add("active");
            }
        });
    }

    private String resolveTimeframeLabel(int secondsPerCandle) {
        for (Map.Entry<String, Integer> entry : TIMEFRAME_SECONDS.entrySet()) {
            if (entry.getValue() == secondsPerCandle) {
                return entry.getKey();
            }
        }
        return null;
    }

    private CandleStickChartContainer getActiveChartContainer() {
        Tab selectedTab = chartTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null && selectedTab.getContent() instanceof CandleStickChartContainer chartContainer) {
            return chartContainer;
        }
        return null;
    }

    private void withActiveChart(Consumer<CandleStickChartContainer> action, String warningMessage) {
        CandleStickChartContainer activeChart = getActiveChartContainer();
        if (activeChart == null) {
            showError(warningMessage);
            return;
        }
        action.accept(activeChart);
    }

    private void updateIndicatorSelection(Consumer<CandleStickChartOptions> action) {
        withActiveChart(chart -> {
            action.accept(chart.getChartOptions());
            chart.refreshChart();
            syncActiveChartState();
        }, "Open a chart before changing indicators.");
    }

    private void syncActiveChartState() {
        int chartCount = chartTabPane.getTabs().size();
        chartCountValueLabel.setText(Integer.toString(chartCount));
        detachedCountValueLabel.setText(Integer.toString(detachedChartWindows.size() + detachedDockWindows.size()));

        boolean hasCharts = chartCount > 0;
        chartTabPane.setVisible(hasCharts);
        chartTabPane.setManaged(hasCharts);
        emptyStateLabel.setVisible(!hasCharts);
        emptyStateLabel.setManaged(!hasCharts);

        CandleStickChartContainer activeChart = getActiveChartContainer();
        if (activeChart == null) {
            activeSymbolLabel.setText("No chart selected");
            aiActivityLabel.setText("AI Idle");
            setActiveTimeframeButton(null);
            indicatorHost.getChildren().setAll(indicatorHintLabel);
            volumeBarsMenuItem.setSelected(false);
            bidAskLinesMenuItem.setSelected(false);
            allIndicatorsMenuItem.setSelected(false);
            sma20MenuItem.setSelected(false);
            ema50MenuItem.setSelected(false);
            bollingerMenuItem.setSelected(false);
            rsi14MenuItem.setSelected(false);
            macdMenuItem.setSelected(false);
            stochastic14MenuItem.setSelected(false);
            updateSystemStatus(null);
            strategyHeadlineLabel.setText("Open a symbol to begin.");
            strategyBodyLabel.setText("The active chart, timeframe, indicators, and AI state will appear here.");
            strategyMetaLabel.setText("Manual execution stays available even when AI is idle.");
            updatePriceDisplay(null, null);
            return;
        }

        String tradePair = activeChart.getTradePair().toString('/');
        activeSymbolLabel.setText(tradePair);
        if (!Objects.equals(symbolPicker.getValue(), tradePair)) {
            symbolPicker.setValue(tradePair);
        }
        selectMarketWatchRow(tradePair);
        aiActivityLabel.setText(activeChart.isAiTradingEnabled() ? "AI Active" : "AI Idle");
        setActiveTimeframeButton(resolveTimeframeLabel(activeChart.getSecondsPerCandle()));

        CandleStickChartOptions options = activeChart.getChartOptions();
        volumeBarsMenuItem.setSelected(options.isShowVolume());
        bidAskLinesMenuItem.setSelected(activeChart.isBidAskLinesVisible());
        allIndicatorsMenuItem.setSelected(options.areAllIndicatorsVisible());
        sma20MenuItem.setSelected(options.isShowSma20());
        ema50MenuItem.setSelected(options.isShowEma50());
        bollingerMenuItem.setSelected(options.isShowBollingerBands());
        rsi14MenuItem.setSelected(options.isShowRsi14());
        macdMenuItem.setSelected(options.isShowMacd());
        stochastic14MenuItem.setSelected(options.isShowStochastic14());

        indicatorHost.getChildren().setAll(activeChart.createChartOptionsPane());
        StrategyAssignmentRow assignment = strategyAssignmentsBySymbol.get(tradePair);
        strategyHeadlineLabel.setText(tradePair + " | " + resolveTimeframeLabel(activeChart.getSecondsPerCandle())
                + (assignment == null ? "" : " | " + assignment.strategy()));
        strategyBodyLabel.setText(activeChart.isAiTradingEnabled()
                ? "AI trading is currently active for the selected chart."
                : assignment == null
                ? "Manual mode is active. Use the toolbar or Trading menu to arm AI execution."
                : "Assigned strategy: " + assignment.strategy() + " / " + assignment.timeframe() + " / " + assignment.status() + ".");
        strategyMetaLabel.setText("Indicators: " + summarizeIndicators(options)
                + (assignment == null ? "" : " | Note: " + assignment.note()));
        updateSystemStatus(activeChart);
        updatePriceDisplay(activeChart, tradePair);
    }

    private void updateSystemStatus(CandleStickChartContainer activeChart) {
        systemStatusExchangeLabel.setText("Exchange: " + exchange.getClass().getSimpleName()
                + " | Mode: " + TradingSession.getMode().label());
        systemStatusSymbolLabel.setText("Active Symbol: " + (activeChart == null ? "None" : activeChart.getTradePair().toString('/')));
        systemStatusChartsLabel.setText("Charts: " + chartTabPane.getTabs().size()
                + " attached / " + detachedChartWindows.size()
                + " detached, panels: " + detachedDockWindows.size() + " detached");
        systemStatusConnectionLabel.setText("Service: " + serviceValueLabel.getText());
    }

    private void updatePriceDisplay(CandleStickChartContainer activeChart, String tradePair) {
        if (activeChart == null || tradePair == null) {
            priceTickerLabel.setText("--");
            bidAskLabel.setText("Bid: -- | Ask: --");
            return;
        }

        // Get the latest price from market snapshot if available
        Double[] priceData = marketSnapshotCache.get(tradePair);
        if (priceData != null && priceData.length >= 2) {
            double bid = priceData[0];
            double ask = priceData[1];
            double mid = (bid + ask) / 2;

            priceTickerLabel.setText(String.format("%.4f", mid));
            bidAskLabel.setText(String.format("Bid: %.4f | Ask: %.4f", bid, ask));
        } else {
            priceTickerLabel.setText("--");
            bidAskLabel.setText("Bid: -- | Ask: --");
        }
    }

    private void scheduleAutoAssignment(boolean force) {
        if (!force && (symbolPicker.getItems() == null || symbolPicker.getItems().isEmpty())) {
            return;
        }
        if (!autoAssignmentInFlight.compareAndSet(false, true)) {
            return;
        }

        executorService.submit(() -> {
            try {
                StrategyRecommendationEngine engine = new StrategyRecommendationEngine(exchange);
                StrategyRecommendationEngine.StrategyRecommendation recommendation =
                        engine.recommend(new ArrayList<>(symbolPicker.getItems()), resolvePreferredSymbol());
                if (recommendation == null) {
                    return;
                }

                FxLifecycle.runLaterIf(() -> !disposed.get(), () -> applyAutoRecommendation(recommendation));
            } finally {
                autoAssignmentInFlight.set(false);
            }
        });
    }

    private void applyAutoRecommendation(StrategyRecommendationEngine.StrategyRecommendation recommendation) {
        StrategyAssignmentRow assignment = new StrategyAssignmentRow(
                recommendation.symbol(),
                recommendation.strategy(),
                recommendation.timeframe(),
                recommendation.status(),
                recommendation.note()
        );
        upsertStrategyAssignment(assignment);
        symbolPicker.getSelectionModel().select(recommendation.symbol());
        openOrFocusChart(recommendation.symbol());
        CandleStickChartContainer chart = findOpenChartBySymbol(recommendation.symbol());
        if (chart != null) {
            Integer seconds = TIMEFRAME_SECONDS.get(recommendation.timeframe());
            if (seconds != null) {
                chart.setSecondsPerCandle(seconds);
            }
        }
        appendConsole("Auto-assigned " + recommendation.strategy() + " to " + recommendation.symbol()
                + " on " + recommendation.timeframe() + ".");
        logAgentEvent(recommendation.symbol(), "strategy_auto_assign",
                "Auto-assigned " + recommendation.strategy() + " / " + recommendation.timeframe() + ".");
        syncActiveChartState();
        refreshWorkspaceModels();
    }

    private List<CandleStickChartContainer> resolveScopeTargets() {
        String scope = autotradeScopePicker.getSelectionModel().getSelectedItem();
        List<CandleStickChartContainer> targets = new ArrayList<>();
        if ("All Symbols".equals(scope)) {
            targets.addAll(getAttachedCharts());
            detachedChartWindows.values().forEach(window -> targets.add(window.chartContainer));
            return targets;
        }

        if ("Watchlist".equals(scope)) {
            Set<String> watched = new LinkedHashSet<>(watchedSymbols);
            for (CandleStickChartContainer chart : getAllOpenCharts()) {
                if (watched.contains(chart.getTradePair().toString('/'))) {
                    targets.add(chart);
                }
            }
            return targets;
        }

        CandleStickChartContainer activeChart = getActiveChartContainer();
        if (activeChart != null) {
            targets.add(activeChart);
        }
        return targets;
    }

    private List<CandleStickChartContainer> getAttachedCharts() {
        List<CandleStickChartContainer> charts = new ArrayList<>();
        for (Tab tab : chartTabPane.getTabs()) {
            if (tab.getContent() instanceof CandleStickChartContainer chartContainer) {
                charts.add(chartContainer);
            }
        }
        return charts;
    }

    private boolean isAnyTargetChartAutoTrading() {
        return resolveScopeTargets().stream().anyMatch(CandleStickChartContainer::isAiTradingEnabled);
    }

    private void setAutoTrading(boolean enabled) {
        List<CandleStickChartContainer> targets = resolveScopeTargets();
        if (targets.isEmpty()) {
            showError("Open at least one chart before changing AI trading.");
            return;
        }

        int changed = 0;
        for (CandleStickChartContainer target : targets) {
            if (target.isAiTradingEnabled() != enabled) {
                target.toggleAiTrading();
                changed++;
            }
        }

        syncActiveChartState();
        appendConsole((enabled ? "Enabled" : "Disabled") + " AI trading for " + Math.max(changed, targets.size()) + " chart(s).");
        logAgentEvent("SYSTEM", enabled ? "ai_enabled" : "ai_disabled",
                (enabled ? "Enabled" : "Disabled") + " AI trading for " + Math.max(changed, targets.size()) + " chart(s).");
    }

    private void activateKillSwitch() {
        setAutoTrading(false);
        appendConsole("Emergency kill switch activated.");
        logAgentEvent("SYSTEM", "kill_switch", "Disabled AI trading across the selected scope.");
    }

    private void detachCurrentChartTab() {
        Tab selectedTab = chartTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null || !(selectedTab.getContent() instanceof CandleStickChartContainer chartContainer)) {
            showError("Choose a chart tab before detaching it.");
            return;
        }

        String title = selectedTab.getText();
        chartTabPane.getTabs().remove(selectedTab);
        selectedTab.setContent(null);

        StackPane detachedRoot = new StackPane(chartContainer);
        detachedRoot.getStyleClass().add("terminal-detached-root");
        Scene scene = new Scene(detachedRoot, 1320, 820);
        scene.getStylesheets().add(Objects.requireNonNull(
                TradingWindow.class.getResource("/css/app.css")
        ).toExternalForm());

        Stage stage = new Stage();
        stage.setTitle("InvestPro Terminal - " + title);
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(700);

        DetachedChartWindow window = new DetachedChartWindow(title, chartContainer, stage);
        detachedChartWindows.put(stage, window);
        stage.setOnHidden(event -> handleDetachedWindowClosed(stage));
        stage.show();
        FxLifecycle.runLaterIf(() -> !disposed.get(), chartContainer::fitChart);

        appendConsole("Detached chart " + title + ".");
        logAgentEvent(title, "chart_detach", "Detached chart into its own window.");
        syncActiveChartState();
    }

    private void detachDockTab(Tab tab) {
        if (tab == null || tab.getTabPane() == null) {
            return;
        }

        TabPane sourcePane = tab.getTabPane();
        int sourceIndex = sourcePane.getTabs().indexOf(tab);
        Node content = tab.getContent();
        if (content == null) {
            showError("This panel cannot be detached right now.");
            return;
        }

        sourcePane.getTabs().remove(tab);
        tab.setContent(null);

        StackPane detachedRoot = new StackPane(content);
        detachedRoot.setPadding(new Insets(12));
        detachedRoot.getStyleClass().add("terminal-detached-root");

        Scene scene = new Scene(detachedRoot, 1040, 640);
        scene.getStylesheets().add(Objects.requireNonNull(
                TradingWindow.class.getResource("/css/app.css")
        ).toExternalForm());

        Stage stage = new Stage();
        stage.setTitle("InvestPro Terminal Panel - " + tab.getText());
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(420);

        DetachedDockWindow window = new DetachedDockWindow(tab, content, detachedRoot, sourcePane, sourceIndex, stage);
        detachedDockWindows.put(stage, window);
        stage.setOnHidden(event -> handleDetachedDockWindowClosed(stage));
        stage.show();

        appendConsole("Detached panel " + tab.getText() + ".");
        logAgentEvent("SYSTEM", "panel_detach", "Detached " + tab.getText() + " panel.");
        syncActiveChartState();
    }

    private void handleDetachedDockWindowClosed(Stage stage) {
        DetachedDockWindow window = detachedDockWindows.remove(stage);
        if (window == null) {
            return;
        }

        window.root.getChildren().remove(window.content);
        window.tab.setContent(window.content);

        int insertIndex = Math.max(0, Math.min(window.sourceIndex, window.sourcePane.getTabs().size()));
        window.sourcePane.getTabs().add(insertIndex, window.tab);
        window.sourcePane.getSelectionModel().select(window.tab);

        appendConsole("Reattached panel " + window.tab.getText() + ".");
        logAgentEvent("SYSTEM", "panel_reattach", "Reattached " + window.tab.getText() + " panel.");
        syncActiveChartState();
    }

    private void handleDetachedWindowClosed(Stage stage) {
        DetachedChartWindow detachedWindow = detachedChartWindows.remove(stage);
        if (detachedWindow == null) {
            return;
        }
        if (disposed.get()) {
            detachedWindow.chartContainer.shutdown();
            return;
        }

        Tab reattachedTab = createChartTab(detachedWindow.title, detachedWindow.chartContainer);
        chartTabPane.getTabs().add(reattachedTab);
        chartTabPane.getSelectionModel().select(reattachedTab);
        FxLifecycle.runLaterIf(() -> !disposed.get(), detachedWindow.chartContainer::fitChart);
        appendConsole("Reattached chart " + detachedWindow.title + ".");
        logAgentEvent(detachedWindow.title, "chart_reattach", "Reattached detached chart window.");
        syncActiveChartState();
    }

    private void reattachActiveChartWindow() {
        if (detachedChartWindows.isEmpty()) {
            showError("There is no detached chart window to reattach.");
            return;
        }
        Stage stage = detachedChartWindows.keySet().iterator().next();
        stage.hide();
    }

    private void tileDetachedChartWindows() {
        if (detachedChartWindows.isEmpty()) {
            showError("There are no detached chart windows to tile.");
            return;
        }

        int index = 0;
        for (DetachedChartWindow window : detachedChartWindows.values()) {
            window.stage.setX(80 + (index % 3) * 440);
            window.stage.setY(80 + (index %3) * 260);
            window.stage.setWidth(520);
            window.stage.setHeight(550);
            index++;
        }
        appendConsole("Tiled detached chart windows.");
    }

    private void cascadeDetachedChartWindows() {
        if (detachedChartWindows.isEmpty()) {
            showError("There are no detached chart windows to cascade.");
            return;
        }

        int index = 0;
        for (DetachedChartWindow window : detachedChartWindows.values()) {
            window.stage.setX(120 + (index * 34));
            window.stage.setY(110 + (index * 30));
            window.stage.setWidth(980);
            window.stage.setHeight(620);
            index++;
        }
        appendConsole("Cascaded detached chart windows.");
    }

    private void selectDockTab(@NotNull TabPane tabPane, String title) {
        for (Tab tab : tabPane.getTabs()) {
            if (Objects.equals(tab.getText(), title)) {
                tabPane.getSelectionModel().select(tab);
                return;
            }
        }
    }

    private void startWorkspaceRefreshLoop() {
        workspaceRefreshExecutor.scheduleAtFixedRate(() -> {
            if (disposed.get()) {
                return;
            }
            refreshWorkspaceModels();
        }, 1, 5, TimeUnit.SECONDS);
    }

    private void seedWorkspaceModels() {
        marketWatchRows.setAll(buildMarketWatchRows(symbolPicker.getItems()));
        signalMonitorRows.setAll(List.of(
                new SignalMonitorRow("No chart", "-", "Manual", "Neutral", "-", "-", "Open a symbol to start monitoring.")
        ));
        strategyInsightRows.setAll(List.of(
                new StrategyInsightRow("No chart", "-", "Idle", "None", "Open a symbol to inspect strategies.")
        ));
        quantPmRows.setAll(List.of(
                new MetricRow("Portfolio State", "Awaiting symbols", "Open charts and positions to activate Quant PM.")
        ));
        positionAnalysisRows.setAll(List.of(
                new PositionAnalysisRow("No positions", "-", "-", "-", "Exposure will appear here when the broker returns positions.")
        ));
    }

    private void refreshWorkspaceModels() {
        List<CandleStickChartContainer> charts = getAllOpenCharts();
        Map<String, CandleStickChartContainer> chartBySymbol = new LinkedHashMap<>();
        for (CandleStickChartContainer chart : charts) {
            chartBySymbol.put(chart.getTradePair().toString('/'), chart);
        }
        refreshMarketSnapshotCache(prioritizedSnapshotSymbols(symbolPicker.getItems(), chartBySymbol));
        List<SignalMonitorRow> nextSignals = new ArrayList<>();
        List<StrategyInsightRow> nextStrategies = new ArrayList<>();

        for (CandleStickChartContainer chart : charts) {
            String symbol = chart.getTradePair().toString('/');
            String timeframe = resolveTimeframeLabel(chart.getSecondsPerCandle());
            String mode = chart.isAiTradingEnabled() ? "AI" : "Manual";
            String bias = chart.getSignalBias();
            String price = formatPrice(chart.getLatestClosePrice());
            String candleDepth = Integer.toString(chart.getLoadedCandleCount());
            String status = chart.isAiTradingEnabled()
                    ? "Watching live AI execution"
                    : "Manual execution";
            nextSignals.add(new SignalMonitorRow(symbol, timeframe, mode, bias, price, candleDepth, status));
            nextStrategies.add(new StrategyInsightRow(
                    symbol,
                    timeframe,
                    bias,
                    summarizeIndicators(chart.getChartOptions()),
                    status
            ));

            String signature = String.join("|", timeframe, mode, bias, price);
            String previousSignature = signalSignatureBySymbol.put(symbol, signature);
            if (!Objects.equals(previousSignature, signature)) {
                logAgentEvent(symbol, "signal_update", mode + " / " + bias + " / " + price);
            }
        }

        if (nextSignals.isEmpty()) {
            nextSignals.add(new SignalMonitorRow("No chart", "-", "Manual", "Neutral", "-", "-", "Open a symbol to start monitoring."));
        }
        if (nextStrategies.isEmpty()) {
            nextStrategies.add(new StrategyInsightRow("No chart", "-", "Idle", "None", "Open a symbol to inspect strategies."));
        }

        List<Account> accounts = safeAccounts();
        List<Position> positions = safePositions();
        List<MetricRow> nextQuantRows = buildQuantMetrics(charts, accounts, positions);
        List<PositionAnalysisRow> nextPositionRows = buildPositionAnalysisRows(positions);
        List<MarketWatchRow> nextMarketWatchRows = buildMarketWatchRows(symbolPicker.getItems(), chartBySymbol);

        FxLifecycle.runLaterIf(() -> !disposed.get(), () -> {
            marketWatchRows.setAll(nextMarketWatchRows);
            selectMarketWatchRow(symbolPicker.getValue());
            signalMonitorRows.setAll(nextSignals);
            strategyInsightRows.setAll(nextStrategies);
            quantPmRows.setAll(nextQuantRows);
            positionAnalysisRows.setAll(nextPositionRows);
        });
    }

    private List<MarketWatchRow> buildMarketWatchRows(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        List<MarketWatchRow> rows = new ArrayList<>(symbols.size());
        String activeSymbol = symbolPicker.getValue();
        for (String symbol : symbols) {
            rows.add(createMarketWatchRow(symbol, activeSymbol, null));
        }
        rows.sort(marketWatchComparator());
        return rows;
    }

    private List<MarketWatchRow> buildMarketWatchRows(List<String> symbols,
                                                      Map<String, CandleStickChartContainer> chartBySymbol) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }

        List<MarketWatchRow> rows = new ArrayList<>(symbols.size());
        String activeSymbol = symbolPicker.getValue();
        for (String symbol : symbols) {
            CandleStickChartContainer chart = chartBySymbol.get(symbol);
            rows.add(createMarketWatchRow(symbol, activeSymbol, chart));
        }
        rows.sort(marketWatchComparator());
        return rows;
    }

    private MarketWatchRow createMarketWatchRow(String symbol,
                                                String activeSymbol,
                                                CandleStickChartContainer chart) {
        Double[] bidAsk = marketSnapshotCache.getOrDefault(symbol, new Double[]{Double.NaN, Double.NaN});
        String status = Objects.equals(symbol, activeSymbol) ? "Active"
                : chart != null ? "Open"
                : watchedSymbols.contains(symbol) ? "Watch"
                : "-";
        MarketWatchRow row = new MarketWatchRow(
                watchedSymbols.contains(symbol),
                symbol,
                formatPrice(bidAsk[0]),
                formatPrice(bidAsk[1]),
                describeAiTraining(symbol, chart)
        );
        row.statusProperty().set(status);
        row.watchedProperty().addListener((obs, oldValue, newValue) ->
                handleMarketWatchToggle(symbol, Boolean.TRUE.equals(newValue)));
        return row;
    }

    private Comparator<MarketWatchRow> marketWatchComparator() {
        String activeSymbol = symbolPicker.getValue();
        return Comparator
                .comparingInt((MarketWatchRow row) -> marketWatchPriority(row, activeSymbol))
                .thenComparing(MarketWatchRow::symbol, String.CASE_INSENSITIVE_ORDER);
    }

    private int marketWatchPriority(MarketWatchRow row, String activeSymbol) {
        if (Objects.equals(row.symbol(), activeSymbol)) {
            return 0;
        }
        if (row.watched()) {
            return 1;
        }
        if (Objects.equals(row.status(), "Open")) {
            return 2;
        }
        return 3;
    }

    private void handleMarketWatchToggle(String symbol, boolean watched) {
        boolean changed = watched ? watchedSymbols.add(symbol) : watchedSymbols.remove(symbol);
        if (!changed) {
            return;
        }
        appendConsole((watched ? "Added " : "Removed ") + symbol + (watched ? " to" : " from") + " the market watch list.");
        logAgentEvent(symbol, watched ? "watch_add" : "watch_remove",
                watched ? "Added symbol to market watch scope." : "Removed symbol from market watch scope.");
        FxLifecycle.runLaterIf(() -> !disposed.get(), () -> {
            marketWatchRows.sort(marketWatchComparator());
            selectMarketWatchRow(symbolPicker.getValue());
        });
    }

    private Set<String> prioritizedSnapshotSymbols(List<String> symbols,
                                                   Map<String, CandleStickChartContainer> chartBySymbol) {
        LinkedHashSet<String> prioritized = new LinkedHashSet<>();
        if (symbolPicker.getValue() != null && !symbolPicker.getValue().isBlank()) {
            prioritized.add(symbolPicker.getValue());
        }
        prioritized.addAll(watchedSymbols);
        prioritized.addAll(chartBySymbol.keySet());
        if (symbols != null) {
            for (String symbol : symbols) {
                prioritized.add(symbol);
                if (prioritized.size() >= 36) {
                    break;
                }
            }
        }
        return prioritized;
    }

    private void refreshMarketSnapshotCache(Set<String> symbols) {
        for (String symbol : symbols) {
            marketSnapshotCache.put(symbol, safeLatestPriceSnapshot(symbol));
        }
    }

    private Double[] safeLatestPriceSnapshot(String symbol) {
        try {
            String[] parts = symbol.split("/");
            if (parts.length != 2) {
                return new Double[]{Double.NaN, Double.NaN};
            }
            Double[] latest = exchange.getLatestPrice(new TradePair(parts[0], parts[1]));
            if (latest == null || latest.length < 2) {
                return new Double[]{Double.NaN, Double.NaN};
            }
            return new Double[]{latest[0], latest[1]};
        } catch (Exception ex) {
            return new Double[]{Double.NaN, Double.NaN};
        }
    }

    private String describeAiTraining(String symbol, CandleStickChartContainer chart) {
        StrategyAssignmentRow assignment = strategyAssignmentsBySymbol.get(symbol);
        if (chart != null && chart.isAiTradingEnabled()) {
            return "AI Live";
        }
        if (assignment != null) {
            return assignment.strategy() + " @ " + assignment.timeframe();
        }
        return "Ready";
    }

    private void selectMarketWatchRow(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        for (MarketWatchRow row : marketWatchRows) {
            if (Objects.equals(row.symbol(), symbol)) {
                marketWatchTable.getSelectionModel().select(row);
                marketWatchTable.scrollTo(row);
                return;
            }
        }
    }

    private String resolvePreferredSymbol() {
        MarketWatchRow selectedMarket = marketWatchTable.getSelectionModel().getSelectedItem();
        if (selectedMarket != null && selectedMarket.symbol() != null && !selectedMarket.symbol().isBlank()) {
            return selectedMarket.symbol();
        }
        CandleStickChartContainer activeChart = getActiveChartContainer();
        if (activeChart != null) {
            return activeChart.getTradePair().toString('/');
        }
        return symbolPicker.getValue();
    }

    private CandleStickChartContainer findOpenChartBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        for (CandleStickChartContainer chart : getAllOpenCharts()) {
            if (Objects.equals(chart.getTradePair().toString('/'), symbol)) {
                return chart;
            }
        }
        return null;
    }

    private String resolveTimeframeForSymbol(String symbol) {
        CandleStickChartContainer chart = findOpenChartBySymbol(symbol);
        if (chart != null) {
            String timeframe = resolveTimeframeLabel(chart.getSecondsPerCandle());
            if (timeframe != null) {
                return timeframe;
            }
        }
        StrategyAssignmentRow assignment = strategyAssignmentsBySymbol.get(symbol);
        if (assignment != null && assignment.timeframe() != null && !assignment.timeframe().isBlank()) {
            return assignment.timeframe();
        }
        return "1h";
    }

    private void upsertStrategyAssignment(StrategyAssignmentRow assignment) {
        strategyAssignmentsBySymbol.put(assignment.symbol(), assignment);
        int existingIndex = -1;
        for (int i = 0; i < strategyAssignmentRows.size(); i++) {
            if (Objects.equals(strategyAssignmentRows.get(i).symbol(), assignment.symbol())) {
                existingIndex = i;
                break;
            }
        }
        if (existingIndex >= 0) {
            strategyAssignmentRows.set(existingIndex, assignment);
        } else {
            strategyAssignmentRows.addFirst(assignment);
        }
        if ("AI Live".equalsIgnoreCase(assignment.status())) {
            watchedSymbols.add(assignment.symbol());
        }
        marketWatchRows.setAll(buildMarketWatchRows(symbolPicker.getItems(), buildChartMap()));
        selectMarketWatchRow(assignment.symbol());
    }

    private Map<String, CandleStickChartContainer> buildChartMap() {
        Map<String, CandleStickChartContainer> chartBySymbol = new LinkedHashMap<>();
        for (CandleStickChartContainer chart : getAllOpenCharts()) {
            chartBySymbol.put(chart.getTradePair().toString('/'), chart);
        }
        return chartBySymbol;
    }

    private List<CandleStickChartContainer> getAllOpenCharts() {
        List<CandleStickChartContainer> charts = new ArrayList<>(getAttachedCharts());
        for (DetachedChartWindow window : detachedChartWindows.values()) {
            charts.add(window.chartContainer);
        }
        return charts;
    }

    private List<Account> safeAccounts() {
        try {
            return exchange.getAccountSummary();
        } catch (RuntimeException ex) {
            logger.debug("Unable to refresh account summary for Quant PM", ex);
            return Collections.emptyList();
        }
    }

    private List<Position> safePositions() {
        try {
            return exchange.getPositions();
        } catch (Exception ex) {
            logger.debug("Unable to refresh positions for Quant PM", ex);
            return Collections.emptyList();
        }
    }

    private List<MetricRow> buildQuantMetrics(List<CandleStickChartContainer> charts,
                                              List<Account> accounts,
                                              List<Position> positions) {
        List<MetricRow> rows = new ArrayList<>();
        long aiCharts = charts.stream().filter(CandleStickChartContainer::isAiTradingEnabled).count();
        double totalEquity = accounts.stream().mapToDouble(Account::getEquity).sum();
        double totalBalance = accounts.stream().mapToDouble(Account::getBalance).sum();
        double totalNav = accounts.stream().mapToDouble(Account::getNAV).sum();
        double totalPl = positions.stream().mapToDouble(Position::getProfitOrLoss).sum();
        double grossExposure = positions.stream().mapToDouble(position -> Math.abs(position.getValue())).sum();
        long longPositions = positions.stream().filter(this::hasLongExposure).count();
        long shortPositions = positions.stream().filter(this::hasShortExposure).count();

        rows.add(new MetricRow("Desk Coverage", charts.size() + " charts", aiCharts + " with live AI armed"));
        rows.add(new MetricRow("Portfolio Equity", money(totalEquity), "Balance " + money(totalBalance) + " / NAV " + money(totalNav)));
        rows.add(new MetricRow("Gross Exposure", money(grossExposure), "Long " + longPositions + " / Short " + shortPositions));
        rows.add(new MetricRow("Net P/L", money(totalPl), positions.isEmpty() ? "No broker positions returned." : "Live position snapshot from the broker."));
        rows.add(new MetricRow("Signal Coverage", Integer.toString(signalMonitorRows.isEmpty() ? charts.size() : signalMonitorRows.size()), "Synchronized to the open charts in the Java desk."));
        return rows;
    }

    private List<PositionAnalysisRow> buildPositionAnalysisRows(List<Position> positions) {
        if (positions.isEmpty()) {
            return List.of(new PositionAnalysisRow("No positions", "-", "-", "-", "Broker returned no open exposure."));
        }

        List<PositionAnalysisRow> rows = new ArrayList<>();
        for (Position position : positions) {
            if (hasLongExposure(position)) {
                rows.add(new PositionAnalysisRow(
                        safeInstrument(position),
                        "Long",
                        money(Math.abs(position.getValue())),
                        money(position.getLongPosition().getPl()),
                        "Unrealized " + money(position.getLongPosition().getUnrealizedPL())
                ));
            }
            if (hasShortExposure(position)) {
                rows.add(new PositionAnalysisRow(
                        safeInstrument(position),
                        "Short",
                        money(Math.abs(position.getValue())),
                        money(position.getShortPosition().getPl()),
                        "Unrealized " + money(position.getShortPosition().getUnrealizedPL())
                ));
            }
        }
        return rows;
    }

    private void logAgentEvent(String symbol, String stage, String detail) {
        FxLifecycle.runLaterIf(() -> !disposed.get(), () -> {
            StrategyAssignmentRow assignment = strategyAssignmentsBySymbol.get(symbol);
            String strategy = assignment == null ? "-" : assignment.strategy();
            String timeframe = assignment == null ? resolveTimeframeForSymbol(symbol) : assignment.timeframe();
            agentTimelineRows.add(0, new AgentTimelineRow(
                    EVENT_TIME_FORMATTER.format(LocalDateTime.now()),
                    classifyEventKind(stage),
                    symbol,
                    assignment == null ? "Desk" : assignment.strategy(),
                    stage,
                    strategy,
                    timeframe,
                    detail
            ));
            if (agentTimelineRows.size() > 250) {
                agentTimelineRows.remove(250, agentTimelineRows.size());
            }
        });
    }

    private String classifyEventKind(String stage) {
        if (stage == null || stage.isBlank()) {
            return "Desk";
        }
        if (stage.startsWith("ai_") || stage.contains("signal")) {
            return "AI";
        }
        if (stage.startsWith("chart")) {
            return "Chart";
        }
        if (stage.startsWith("watch")) {
            return "Market";
        }
        if (stage.startsWith("strategy")) {
            return "Strategy";
        }
        if (stage.startsWith("workspace")) {
            return "Workspace";
        }
        return "Desk";
    }

    private void applyWorkspacePreset(String preset) {
        switch (preset) {
            case "research" -> {
                selectDockTab(leftDockTabs, "AI Signal Monitor");
                selectDockTab(rightDockTabs, "Orderbook");
            }
            case "risk" -> {
                selectDockTab(leftDockTabs, "Strategy Scorecard");
                selectDockTab(rightDockTabs, "Risk");
            }
            case "review" -> {
                selectDockTab(leftDockTabs, "AI Signal Monitor");
                selectDockTab(rightDockTabs, "Orders");
            }
            default -> {
                selectDockTab(leftDockTabs, "Market Watch");
                selectDockTab(rightDockTabs, "Terminal");
            }
        }
        appendConsole("Applied " + preset + " workspace.");
        logAgentEvent("SYSTEM", "workspace", "Applied " + preset + " workspace preset.");
    }

    private void openSignalMonitorWindow() {
        selectDockTab(leftDockTabs, "AI Signal Monitor");
    }

    private void openLiveAgentWindow() {
        showToolWindow("live_agent", "Live Agent Timeline", this::buildAgentTimelinePanel, 1100, 620);
    }

    private void openQuantPmWindow() {
        selectDockTab(rightDockTabs, "Quant PM");
    }

    private void openPositionAnalysisWindow() {
        showToolWindow("position_analysis", "Position Analysis", this::buildPositionAnalysisPanel, 1040, 620);
    }

    private void openPositionsWindow() {
        selectDockTab(rightDockTabs, "Positions");
    }

    private void openOpenOrdersWindow() {
        selectDockTab(rightDockTabs, "Orders");
    }

    private void openTradeLogWindow() {
        showToolWindow("trade_log", "Trade Log", () -> wrapDockContent(ordersUI), 1100, 680);
    }

    private void openNewsWindow() {
        showToolWindow("news", "News", () -> wrapDockContent(newsUI), 1040, 640);
    }

    private void openResearchWindow() {
        showToolWindow("research", "Research", () -> wrapDockContent(browserUI), 1200, 760);
    }

    private void openSystemConsoleWindow() {
        selectDockTab(rightDockTabs, "Terminal");
    }

    private void openSystemStatusWindow() {
        selectDockTab(rightDockTabs, "Terminal");
    }

    private void openAiTrainingWindow() {
        selectDockTab(leftDockTabs, "AI Training");
    }

    private void openStrategyAssignerWindow() {
        openStrategyAssignerWindow(resolvePreferredSymbol());
    }

    private void openStrategyAssignerWindow(String preferredSymbol) {
        final String key = "strategy_assigner";
        Stage existing = detachedToolWindows.get(key);
        if (existing != null) {
            applyStrategyAssignerSelection(existing, preferredSymbol);
            existing.show();
            existing.toFront();
            existing.requestFocus();
            return;
        }

        StrategyAssignerPane pane = createStrategyAssignerPane(preferredSymbol);
        StackPane root = new StackPane(pane.root());
        root.setPadding(new Insets(14));
        root.getStyleClass().add("terminal-detached-root");

        Scene scene = new Scene(root, 1040, 620);
        scene.getStylesheets().add(Objects.requireNonNull(
                TradingWindow.class.getResource("/css/app.css")
        ).toExternalForm());

        Stage stage = new Stage();
        stage.setTitle("InvestPro Terminal - Strategy Assigner");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(520);
        stage.getProperties().put("strategySymbolPicker", pane.symbolPicker());
        stage.getProperties().put("strategyAssignmentTable", pane.table());
        stage.setOnHidden(event -> detachedToolWindows.remove(key));
        detachedToolWindows.put(key, stage);
        stage.show();
        logAgentEvent(preferredSymbol == null || preferredSymbol.isBlank() ? "SYSTEM" : preferredSymbol,
                "strategy_assigner", "Opened Strategy Assigner.");
    }

    private StrategyAssignerPane createStrategyAssignerPane(String preferredSymbol) {
        Label title = createPanelTitle("Strategy Assigner");
        Label copy = createPanelCopy("Auto-route the strongest symbol, timeframe, and strategy from recent backtests, then adjust manually if you want tighter desk control.");
        ComboBox<String> strategySymbolPicker = new ComboBox<>();
        strategySymbolPicker.getStyleClass().add("terminal-combo-box");
        strategySymbolPicker.setItems(symbolPicker.getItems());
        strategySymbolPicker.setPromptText("Select symbol");
        String initialSymbol = preferredSymbol == null || preferredSymbol.isBlank() ? resolvePreferredSymbol() : preferredSymbol;
        if (initialSymbol != null && !initialSymbol.isBlank()) {
            strategySymbolPicker.getSelectionModel().select(initialSymbol);
        }

        ComboBox<String> strategyPicker = new ComboBox<>();
        strategyPicker.getStyleClass().add("terminal-combo-box");
        strategyPicker.getItems().setAll(STRATEGY_OPTIONS);
        strategyPicker.getSelectionModel().selectFirst();

        ComboBox<String> timeframePicker = new ComboBox<>();
        timeframePicker.getStyleClass().add("terminal-combo-box");
        timeframePicker.getItems().setAll(TIMEFRAME_SECONDS.keySet());
        timeframePicker.getSelectionModel().select("1h");

        ComboBox<String> statusPicker = new ComboBox<>();
        statusPicker.getStyleClass().add("terminal-combo-box");
        statusPicker.getItems().setAll("Training", "Assigned", "Monitoring", "AI Live");
        statusPicker.getSelectionModel().select("Training");

        TextField noteField = new TextField();
        noteField.getStyleClass().add("terminal-combo-box");
        noteField.setPromptText("Notes for the live agent, PM, or operator");

        TableView<StrategyAssignmentRow> table = createStrategyAssignmentTable();
        table.setItems(strategyAssignmentRows);

        Runnable syncSelection = () -> {
            String symbol = strategySymbolPicker.getValue();
            StrategyAssignmentRow assignment = symbol == null ? null : strategyAssignmentsBySymbol.get(symbol);
            if (assignment == null) {
                noteField.clear();
                strategyPicker.getSelectionModel().selectFirst();
                timeframePicker.getSelectionModel().select(resolveTimeframeForSymbol(symbol));
                statusPicker.getSelectionModel().select("Training");
                return;
            }
            strategyPicker.getSelectionModel().select(assignment.strategy());
            timeframePicker.getSelectionModel().select(assignment.timeframe());
            statusPicker.getSelectionModel().select(assignment.status());
            noteField.setText(assignment.note());
        };
        strategySymbolPicker.valueProperty().addListener((obs, oldValue, newValue) -> syncSelection.run());
        syncSelection.run();

        Button assignButton = createToolbarButton("Assign Strategy");
        assignButton.setOnAction(event -> {
            String symbol = strategySymbolPicker.getValue();
            if (symbol == null || symbol.isBlank()) {
                showError("Select a symbol before assigning a strategy.");
                return;
            }
            StrategyAssignmentRow assignment = new StrategyAssignmentRow(
                    symbol,
                    strategyPicker.getValue(),
                    timeframePicker.getValue(),
                    statusPicker.getValue(),
                    noteField.getText() == null || noteField.getText().isBlank() ? "Operator assigned strategy workflow." : noteField.getText().trim()
            );
            upsertStrategyAssignment(assignment);
            logAgentEvent(symbol, "strategy_assign",
                    "Assigned " + assignment.strategy() + " / " + assignment.timeframe() + " / " + assignment.status() + ".");
            appendConsole("Assigned " + assignment.strategy() + " to " + symbol + ".");
            refreshWorkspaceModels();
        });

        Button openChartButton = createToolbarButton("Open Chart");
        openChartButton.setOnAction(event -> {
            if (strategySymbolPicker.getValue() == null || strategySymbolPicker.getValue().isBlank()) {
                showError("Select a symbol before opening its chart.");
                return;
            }
            openOrFocusChart(strategySymbolPicker.getValue());
        });

        Button armAiButton = createToolbarButton("Arm AI");
        armAiButton.setOnAction(event -> {
            if (strategySymbolPicker.getValue() == null || strategySymbolPicker.getValue().isBlank()) {
                showError("Select a symbol before arming AI.");
                return;
            }
            openOrFocusChart(strategySymbolPicker.getValue());
            setAutoTrading(true);
        });

        Button autoAssignButton = createToolbarButton("Auto Assign Best");
        autoAssignButton.setOnAction(event -> scheduleAutoAssignment(true));

        VBox panel = new VBox(
                12,
                title,
                copy,
                new HBox(8,
                        createToolbarLabel("Symbol"), strategySymbolPicker,
                        createToolbarLabel("Strategy"), strategyPicker,
                        createToolbarLabel("TF"), timeframePicker,
                        createToolbarLabel("Status"), statusPicker),
                noteField,
                table,
                new HBox(8, assignButton, autoAssignButton, openChartButton, armAiButton)
        );
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        return new StrategyAssignerPane(wrapDockContent(panel), strategySymbolPicker, table);
    }

    @SuppressWarnings("unchecked")
    private void applyStrategyAssignerSelection(Stage stage, String preferredSymbol) {
        if (preferredSymbol == null || preferredSymbol.isBlank()) {
            return;
        }
        Object pickerNode = stage.getProperties().get("strategySymbolPicker");
        if (pickerNode instanceof ComboBox<?> rawPicker) {
            ((ComboBox<String>) rawPicker).getSelectionModel().select(preferredSymbol);
        }
        Object tableNode = stage.getProperties().get("strategyAssignmentTable");
        if (tableNode instanceof TableView<?> rawTable) {
            TableView<StrategyAssignmentRow> table = (TableView<StrategyAssignmentRow>) rawTable;
            for (StrategyAssignmentRow row : strategyAssignmentRows) {
                if (Objects.equals(row.symbol(), preferredSymbol)) {
                    table.getSelectionModel().select(row);
                    table.scrollTo(row);
                    break;
                }
            }
        }
    }

    private void showToolWindow(String key, String title, java.util.function.Supplier<Node> contentSupplier, double width, double height) {
        Stage existing = detachedToolWindows.get(key);
        if (existing != null) {
            existing.show();
            existing.toFront();
            existing.requestFocus();
            return;
        }

        StackPane root = new StackPane(contentSupplier.get());
        root.setPadding(new Insets(14));
        root.getStyleClass().add("terminal-detached-root");

        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(Objects.requireNonNull(
                TradingWindow.class.getResource("/css/app.css")
        ).toExternalForm());

        Stage stage = new Stage();
        stage.setTitle("InvestPro Terminal - " + title);
        stage.setScene(scene);
        stage.setMinWidth(Math.min(width, 860));
        stage.setMinHeight(Math.min(height, 480));
        stage.setOnHidden(event -> detachedToolWindows.remove(key));
        detachedToolWindows.put(key, stage);
        stage.show();
        logAgentEvent("SYSTEM", "tool_open", "Opened " + title + ".");
    }

    private Node buildAiTrainingPanel() {
        Label title = createPanelTitle("AI Training Desk");
        Label copy = createPanelCopy("Backtest the active chart against the Python predictor, check model health, auto-route the strongest symbol, and arm AI trading for the desk.");
        Label status = createPanelCopy("Predictor host: " + System.getProperty("investpro.ai.host", "localhost")
                + ":" + Integer.getInteger("investpro.ai.port", 50051));
        status.getStyleClass().add("terminal-status-line");

        Button healthButton = createToolbarButton("Health Check");
        healthButton.setOnAction(event -> executorService.submit(() -> {
            boolean healthy = PredictorRuntimeManager.ensureAvailable(Duration.ofSeconds(12));
            FxLifecycle.runLaterIf(() -> !disposed.get(), () -> {
                status.setText(healthy ? "Predictor healthy and reachable." : "Predictor unavailable. Check Python dependencies or launch the predictor.");
                logAgentEvent("SYSTEM", "ai_health", status.getText());
            });
        }));

        Button backtestButton = createToolbarButton("Backtest Active");
        backtestButton.setOnAction(event -> withActiveChart(chart -> executorService.submit(() -> {
            try {
                FxLifecycle.runLaterIf(() -> !disposed.get(),
                        () -> status.setText("Running AI backtest for " + chart.getTradePair().toString('/') + "..."));
                new InvestProAIBacktester().runBacktest(chart.getChart().getCandleData());
                FxLifecycle.runLaterIf(() -> !disposed.get(), () -> {
                    status.setText("Backtest complete. Results exported to backtest_results.csv.");
                    logAgentEvent(chart.getTradePair().toString('/'), "ai_backtest", "Completed AI backtest for active chart.");
                });
            } catch (RuntimeException ex) {
                FxLifecycle.runLaterIf(() -> !disposed.get(), () -> status.setText("AI backtest failed: " + ex.getMessage()));
            }
        }), "Open a chart before running AI training."));

        Button armAiButton = createToolbarButton("Arm AI");
        armAiButton.setOnAction(event -> setAutoTrading(true));

        Button stopAiButton = createToolbarButton("Stop AI");
        stopAiButton.setOnAction(event -> setAutoTrading(false));

        Button signalsButton = createToolbarButton("Signals");
        signalsButton.setOnAction(event -> openSignalMonitorWindow());

        Button strategyButton = createToolbarButton("Strategy Assigner");
        strategyButton.setOnAction(event -> openStrategyAssignerWindow());

        Button autoAssignButton = createToolbarButton("Auto Assign Best");
        autoAssignButton.setOnAction(event -> {
            status.setText("Scanning symbols and timeframes for the best backtested setup...");
            scheduleAutoAssignment(true);
        });

        VBox panel = new VBox(
                14,
                title,
                copy,
                status,
                new HBox(8, healthButton, backtestButton, autoAssignButton, armAiButton, stopAiButton, strategyButton, signalsButton)
        );
        panel.setPadding(new Insets(10));
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return wrapDockContent(panel);
    }

    private TableView<SignalMonitorRow> createSignalMonitorTable() {
        TableView<SignalMonitorRow> table = new TableView<>();
        table.getColumns().addAll(
                textColumn("Symbol", SignalMonitorRow::symbol),
                textColumn("TF", SignalMonitorRow::timeframe),
                textColumn("Mode", SignalMonitorRow::mode),
                textColumn("Bias", SignalMonitorRow::bias),
                textColumn("Last", SignalMonitorRow::lastPrice),
                textColumn("Candles", SignalMonitorRow::candles),
                textColumn("Status", SignalMonitorRow::status)
        );
        return styleTerminalTable(table, "No signal rows yet.");
    }

    private TableView<StrategyInsightRow> createStrategyInsightTable() {
        TableView<StrategyInsightRow> table = new TableView<>();
        table.getColumns().addAll(
                textColumn("Symbol", StrategyInsightRow::symbol),
                textColumn("TF", StrategyInsightRow::timeframe),
                textColumn("Bias", StrategyInsightRow::bias),
                textColumn("Indicators", StrategyInsightRow::indicators),
                textColumn("Execution", StrategyInsightRow::execution)
        );
        return styleTerminalTable(table, "Open a chart to inspect its strategy posture.");
    }

    private TableView<AgentTimelineRow> createAgentTimelineTable() {
        TableView<AgentTimelineRow> table = new TableView<>();
        table.getColumns().addAll(
                textColumn("Time", AgentTimelineRow::time),
                textColumn("Kind", AgentTimelineRow::kind),
                textColumn("Symbol", AgentTimelineRow::symbol),
                textColumn("Agent / Event", AgentTimelineRow::actorEvent),
                textColumn("Stage", AgentTimelineRow::stage),
                textColumn("Strategy", AgentTimelineRow::strategy),
                textColumn("TF", AgentTimelineRow::timeframe),
                textColumn("Detail", AgentTimelineRow::detail)
        );
        return styleTerminalTable(table, "Live agent events will appear here.");
    }

    private @NotNull TableView<StrategyAssignmentRow> createStrategyAssignmentTable() {
        TableView<StrategyAssignmentRow> table = new TableView<>();
        table.getColumns().addAll(
                textColumn("Symbol", StrategyAssignmentRow::symbol),
                textColumn("Strategy", StrategyAssignmentRow::strategy),
                textColumn("TF", StrategyAssignmentRow::timeframe),
                textColumn("Status", StrategyAssignmentRow::status),
                textColumn("Note", StrategyAssignmentRow::note)
        );
        return styleTerminalTable(table, "Assign a strategy to a symbol to activate live routing context.");
    }

    private TableView<MetricRow> createMetricTable() {
        TableView<MetricRow> table = new TableView<>();
        table.getColumns().addAll(
                textColumn("Metric", MetricRow::name),
                textColumn("Value", MetricRow::value),
                textColumn("Note", MetricRow::note)
        );
        return styleTerminalTable(table, "Quant PM will populate once the desk is live.");
    }

    private TableView<PositionAnalysisRow> createPositionAnalysisTable() {
        TableView<PositionAnalysisRow> table = new TableView<>();
        table.getColumns().addAll(
                textColumn("Symbol", PositionAnalysisRow::symbol),
                textColumn("Side", PositionAnalysisRow::side),
                textColumn("Exposure", PositionAnalysisRow::exposure),
                textColumn("P/L", PositionAnalysisRow::pnl),
                textColumn("Note", PositionAnalysisRow::note)
        );
        return styleTerminalTable(table, "Position analysis will appear here.");
    }

    private <T> TableView<T> styleTerminalTable(TableView<T> table, String emptyMessage) {
        table.getStyleClass().add("terminal-data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label(emptyMessage));
        VBox.setVgrow(table, Priority.ALWAYS);
        return table;
    }

    private <T> TableColumn<T, String> textColumn(String title, java.util.function.Function<T, String> mapper) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(mapper.apply(cell.getValue())));
        return column;
    }

    private String summarizeIndicators(CandleStickChartOptions options) {
        List<String> enabled = new ArrayList<>();
        if (options.isShowSma20()) {
            enabled.add("SMA20");
        }
        if (options.isShowEma50()) {
            enabled.add("EMA50");
        }
        if (options.isShowBollingerBands()) {
            enabled.add("Bollinger");
        }
        if (options.isShowRsi14()) {
            enabled.add("RSI14");
        }
        if (options.isShowMacd()) {
            enabled.add("MACD");
        }
        if (options.isShowStochastic14()) {
            enabled.add("Stochastic14");
        }
        return enabled.isEmpty() ? "None" : String.join(", ", enabled);
    }

    private boolean hasLongExposure(Position position) {
        return position.getLongPosition() != null && Math.abs(position.getLongPosition().getUnits()) > 0;
    }

    private boolean hasShortExposure(Position position) {
        return position.getShortPosition() != null && Math.abs(position.getShortPosition().getUnits()) > 0;
    }

    private String safeInstrument(@NotNull Position position) {
        return position.getInstrument() == null || position.getInstrument().isBlank() ? "Unknown" : position.getInstrument();
    }

    @Contract(pure = true)
    private @NotNull String money(double value) {
        return String.format("$%,.2f", value);
    }

    private String formatPrice(double value) {
        return Double.isFinite(value) ? PRICE_FORMAT.format(value) : "-";
    }

    private void closeCurrentChart() {
        Tab selectedTab = chartTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null) {
            showError("Open a chart before closing the current chart.");
            return;
        }
        chartTabPane.getTabs().remove(selectedTab);
        if (selectedTab.getContent() instanceof CandleStickChartContainer chartContainer) {
            chartContainer.shutdown();
        }
        selectedTab.setContent(null);
        appendConsole("Closed chart " + selectedTab.getText() + ".");
        logAgentEvent(selectedTab.getText(), "chart_close", "Closed current chart.");
        syncActiveChartState();
    }

    private void closeAllCharts() {
        List<Tab> attachedTabs = List.copyOf(chartTabPane.getTabs());
        for (Tab tab : attachedTabs) {
            if (tab.getContent() instanceof CandleStickChartContainer chartContainer) {
                chartContainer.shutdown();
            }
            tab.setContent(null);
        }
        chartTabPane.getTabs().clear();

        List<DetachedChartWindow> detachedWindows = new ArrayList<>(detachedChartWindows.values());
        detachedChartWindows.clear();
        for (DetachedChartWindow detachedWindow : detachedWindows) {
            detachedWindow.stage.setOnHidden(null);
            detachedWindow.stage.hide();
            detachedWindow.chartContainer.shutdown();
        }

        appendConsole("Closed all charts.");
        logAgentEvent("SYSTEM", "chart_close_all", "Closed all attached and detached chart windows.");
        syncActiveChartState();
    }

    private void appendConsole(String message) {
        String line = message.endsWith(System.lineSeparator()) ? message : message + System.lineSeparator();
        systemConsole.appendText(line);
    }

    private void showError(String message) {
        FxLifecycle.runLaterIf(() -> !disposed.get(), () -> new Messages(Alert.AlertType.ERROR, message));
    }

    private void hideWindow() {
        if (getScene() != null && getScene().getWindow() != null) {
            getScene().getWindow().hide();
        }
    }

    public void shutdown() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }


        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
            workspaceRefreshExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<Stage> toolStages = new ArrayList<>(detachedToolWindows.values());
        detachedToolWindows.clear();
        for (Stage stage : toolStages) {
            stage.hide();
        }

        List<Stage> dockStages = new ArrayList<>(detachedDockWindows.keySet());
        for (Stage stage : dockStages) {
            stage.hide();
        }

        PredictorRuntimeManager.shutdownStartedProcess();
        closeAllCharts();
        accountSummaryUI.shutdown();
        positionsUI.shutdown();
        ordersUI.shutdown();
        pendingOrdersUI.shutdown();
        coinInfoUI.shutdown();
        newsUI.shutdown();

        executorService.shutdownNow();
        workspaceRefreshExecutor.shutdownNow();
    }

    private record SignalMonitorRow(
            String symbol,
            String timeframe,
            String mode,
            String bias,
            String lastPrice,
            String candles,
            String status
    ) {
    }

    private record AgentTimelineRow(
            String time,
            String kind,
            String symbol,
            String actorEvent,
            String stage,
            String strategy,
            String timeframe,
            String detail
    ) {
    }

    private record MetricRow(
            String name,
            String value,
            String note
    ) {
    }

    private record PositionAnalysisRow(
            String symbol,
            String side,
            String exposure,
            String pnl,
            String note
    ) {
    }

    private record StrategyInsightRow(
            String symbol,
            String timeframe,
            String bias,
            String indicators,
            String execution
    ) {
    }

    private record StrategyAssignmentRow(
            String symbol,
            String strategy,
            String timeframe,
            String status,
            String note
    ) {
    }

    private record StrategyAssignerPane(
            Node root,
            ComboBox<String> symbolPicker,
            TableView<StrategyAssignmentRow> table
    ) {
    }

    private static final class MarketWatchRow {
        private final BooleanProperty watched;
        private final StringProperty symbol;
        private final StringProperty bid;
        private final StringProperty ask;
        private final StringProperty aiTraining;
        private final StringProperty status;

        private MarketWatchRow(boolean watched, String symbol, String bid, String ask, String aiTraining) {
            this.watched = new SimpleBooleanProperty(watched);
            this.symbol = new SimpleStringProperty(symbol);
            this.bid = new SimpleStringProperty(bid);
            this.ask = new SimpleStringProperty(ask);
            this.aiTraining = new SimpleStringProperty(aiTraining);
            this.status = new SimpleStringProperty("-");
        }

        private BooleanProperty watchedProperty() {
            return watched;
        }

        private boolean watched() {
            return watched.get();
        }

        private String symbol() {
            return symbol.get();
        }

        private String bid() {
            return bid.get();
        }

        private String ask() {
            return ask.get();
        }

        private String aiTraining() {
            return aiTraining.get();
        }

        private String status() {
            return status.get();
        }

        private StringProperty statusProperty() {
            return status;
        }
    }

    private static final class DetachedChartWindow {
        private final String title;
        private final CandleStickChartContainer chartContainer;
        private final Stage stage;

        private DetachedChartWindow(String title, CandleStickChartContainer chartContainer, Stage stage) {
            this.title = title;
            this.chartContainer = chartContainer;
            this.stage = stage;
        }
    }

    private static final class DetachedDockWindow {
        private final Tab tab;
        private final Node content;
        private final StackPane root;
        private final TabPane sourcePane;
        private final int sourceIndex;
        private final Stage stage;

        private DetachedDockWindow(Tab tab, Node content, StackPane root, TabPane sourcePane, int sourceIndex, Stage stage) {
            this.tab = tab;
            this.content = content;
            this.root = root;
            this.sourcePane = sourcePane;
            this.sourceIndex = sourceIndex;
            this.stage = stage;
        }
    }
}
