package org.investpro.ui;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import org.investpro.core.EmailNotifier;
import org.investpro.core.SystemCore;
import org.investpro.core.TelegramNotifier;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.signal.Signal;

import org.investpro.models.market.NewsEvent;
import org.investpro.service.NewsDataProvider;
import org.investpro.data.Account;
import org.investpro.data.CandleData;
import org.investpro.exchange.*;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.models.trading.*;
import org.investpro.repository.CurrencyRepository;
import org.investpro.repository.OrderRepository;
import org.investpro.repository.RepositoryFactory;
import org.investpro.repository.TradeRepository;
import org.investpro.risk.PositionHealthScore;
import org.investpro.service.CurrencyService;
import org.investpro.service.NotificationService;
import org.investpro.service.OrderService;
import org.investpro.service.TradeService;
import org.investpro.service.TradingService;
import org.investpro.ui.charts.CandleStickChart;
import org.investpro.ui.charts.DepthChart;
import org.investpro.ui.charts.NewsEventOverlay;
import org.investpro.ui.panels.ManualTradePanel;
import org.investpro.ui.panels.NewsCalendarPanel;
import org.investpro.utils.DraggableTab;
import org.investpro.utils.ZoomDirection;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.prefs.Preferences;

import javafx.embed.swing.SwingFXUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.investpro.utils.Side.BUY;
import static org.investpro.utils.Side.SELL;

/**
 * Main InvestPro / TradeAdviser trading terminal window.
 *
 * This version is wired to SystemCore:
 * - TradingWindow owns the UI.
 * - SystemCore owns SmartBot, streaming, strategy engine, risk, AI, execution,
 * and notifications.
 * - SmartBot is no longer controlled directly from the UI.
 */
@Slf4j
@Getter
@Setter
public class TradingWindow extends BorderPane {

    private static final Logger logger = LoggerFactory.getLogger(TradingWindow.class);

    private static final double DEFAULT_WIDTH = 1540;
    private static final double DEFAULT_HEIGHT = 820;
    private static final double LEFT_PANEL_WIDTH = 310;
    private static final double CONSOLE_HEIGHT = 250;
    private static final String ENV_TELEGRAM_TOKEN = "INVESTPRO_TELEGRAM_BOT_TOKEN";
    private static final DateTimeFormatter SNAPSHOT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Keep this list aligned with createExchange(...).
     * Do not show unsupported brokers until their adapters exist.
     */
    private static final String[] SUPPORTED_EXCHANGES = {
            "COINBASE",
            "BINANCE US",
            "BINANCE",
            "OANDA",
            "BITFINEX",
            "ALPACA",
            "INTERACTIVE BROKERS"
    };

    private final ComboBox<String> exchangeSelector = new ComboBox<>();
    private final ComboBox<TradePair> symbolSelector = new ComboBox<>();
    private final ComboBox<String> timeframeSelector = new ComboBox<>();
    private final ComboBox<String> botSymbolScopeSelector = new ComboBox<>();
    private final ComboBox<String> orderTypeSelector = new ComboBox<>();

    private final Button connectButton = new Button("Connect");
    private final Label connectedBrokerLabel = new Label("Connected");
    private final StackPane connectControl = new StackPane();
    private final Button refreshSymbolsButton = new Button("Refresh Symbols");
    private final Button addChartButton = new Button("Open Chart");
    private final Button botTradeButton = new Button("Bot Trade");
    private final Button buyButton = new Button("BUY");
    private final Button sellButton = new Button("SELL");
    private final Button cancelAllButton = new Button("Cancel All");

    private final TabPane chartTabPane = new TabPane();
    private final TabPane terminalTabPane = new TabPane();

    private final ObservableList<TradePair> marketWatchItems = FXCollections.observableArrayList();
    private final TableView<TradePair> marketWatchTable = new TableView<>(marketWatchItems);

    private final ObservableList<OrderBook.PriceLevel> orderBookBids = FXCollections.observableArrayList();
    private final ObservableList<OrderBook.PriceLevel> orderBookAsks = FXCollections.observableArrayList();
    private final TableView<OrderBook.PriceLevel> orderBookBidsTable = new TableView<>(orderBookBids);
    private final TableView<OrderBook.PriceLevel> orderBookAsksTable = new TableView<>(orderBookAsks);

    private final ObservableList<Order> positionsItems = FXCollections.observableArrayList();
    private final TableView<Order> positionsTable = new TableView<>(positionsItems);

    private final ObservableList<Position> accountPositionItems = FXCollections.observableArrayList();
    private final ObservableList<OpenOrder> accountOpenOrderItems = FXCollections.observableArrayList();
    private final ObservableList<Trade> accountTradeItems = FXCollections.observableArrayList();
    private final ObservableList<Order> accountHistoryItems = FXCollections.observableArrayList();

    private final ObservableList<PositionHealthScore> positionHealthItems = FXCollections.observableArrayList();
    private final TableView<PositionHealthScore> positionHealthTable = new TableView<>(positionHealthItems);

    private final PositionsDataManager positionsDataManager = new PositionsDataManager();

    private final TextArea accountSummaryArea = new TextArea();
    private final Label longPnLLabel = new Label("Long P&L: $0.00");
    private final Label shortPnLLabel = new Label("Short P&L: $0.00");
    private final Label totalPnLLabel = new Label("Total P&L: $0.00");
    private final Label balanceValueLabel = valueLabel("$0.00", "#10b981");
    private final Label availableValueLabel = valueLabel("$0.00", "#10b981");
    private final Label equityValueLabel = valueLabel("$0.00", "#3b82f6");
    private final Label marginUsedValueLabel = valueLabel("$0.00", "#ef4444");
    private final Label freeMarginValueLabel = valueLabel("$0.00", "#f59e0b");

    private final Label connectionStatusLabel = new Label("Disconnected");
    private final Label symbolCountLabel = new Label("Symbols: 0");
    private final Circle connectionIndicator = new Circle(6, Color.ORANGERED);

    private final TextArea journalArea = new TextArea();
    private final ObservableList<String> agentActivityItems = FXCollections
            .observableArrayList("Agent activity will appear here.");
    private final ObservableList<String> signalItems = FXCollections.observableArrayList("Signal engine idle.",
            "No signals loaded.");

    private final ListView<String> expertsListView = new ListView<>();
    private final ListView<String> alertsListView = new ListView<>();
    private final ListView<String> signalListView = new ListView<>();
    private final ListView<String> mailboxListView = new ListView<>();

    private final TradeRepository tradeRepository;
    private final OrderRepository orderRepository;
    private final CurrencyRepository currencyRepository;
    private final TradeService tradeService;
    private final OrderService orderService;
    private final CurrencyService currencyService;
    private final TradingService tradingService;
    private final NotificationService notificationService;
    private final Preferences preferences = Preferences.userNodeForPackage(TradingWindow.class);

    private final ScheduledExecutorService autoRefreshExecutor = Executors.newScheduledThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "TradingWindow-AutoRefresh");
        thread.setDaemon(true);
        return thread;
    });

    private SplitPane mainVerticalWorkbench;
    private SplitPane horizontalWorkbench;
    private VBox systemConsole;
    private boolean consoleVisible = true;

    private Exchange exchange;
    private final Map<String, BrokerSession> brokerSessions = new HashMap<>();
    private SystemCore systemCore;
    private boolean systemCoreEventsSubscribed;
    private final NewsDataProvider newsDataProvider = new NewsDataProvider();

    private boolean botTradingEnabled;
    private boolean brokerAccessGranted;
    private String configuredApiKey = "";
    private String configuredApiSecret = "";
    private String telegramToken = "";
    private boolean initialized;

    private OrderBook currentOrderBook = new OrderBook();
    private DepthChart depthChart;
    private NewsCalendarPanel newsCalendarPanel;
    private MarketInfoPanel marketInfoPanel;
    private ManualTradePanel manualTradePanel;

    private record BrokerSession(Exchange exchange, boolean accessGranted, Account account) {
    }

    public TradingWindow() {
        this(null,
                RepositoryFactory.createTradeRepository(),
                RepositoryFactory.createOrderRepository(),
                RepositoryFactory.createCurrencyRepository());
    }

    public TradingWindow(MarketConfiguration configuration)
            throws ParseException, IOException, InterruptedException, ClassNotFoundException {
        this(configuration,
                RepositoryFactory.createTradeRepository(),
                RepositoryFactory.createOrderRepository(),
                RepositoryFactory.createCurrencyRepository());
    }

    public TradingWindow(
            MarketConfiguration configuration,
            TradeRepository tradeRepository,
            OrderRepository orderRepository,
            CurrencyRepository currencyRepository) {
        super();

        this.tradeRepository = Objects.requireNonNull(tradeRepository, "tradeRepository must not be null");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.currencyRepository = Objects.requireNonNull(currencyRepository, "currencyRepository must not be null");

        this.tradeService = new TradeService(this.tradeRepository);
        this.orderService = new OrderService(this.orderRepository);
        this.currencyService = new CurrencyService(this.currencyRepository);
        this.tradingService = new TradingService(this.tradeService, this.orderService, this.currencyService);

        // Initialize NotificationService (disabled by default, can be enabled via
        // settings)
        this.notificationService = NotificationService.disabled();

        initialize(configuration);
    }

    private NewsEventOverlay newsEventOverlay;

    private void initialize(MarketConfiguration configuration) {
        if (initialized) {
            return;
        }
        initialized = true;

        setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinSize(1200, 690);
        setFocusTraversable(true);
        getStyleClass().add("trading-window-pro");

        configuredApiKey = configuration == null ? "" : safe(configuration.apiKey());
        configuredApiSecret = configuration == null ? "" : safe(configuration.apiSecret());
        telegramToken = resolveTelegramToken(configuration);

        configureButtonStyles();
        configureSelectors(configuration);
        configureButtons();
        configureChartArea();
        configureMarketWatchTable();
        configurePositionsTable();
        configureAccountSummaryArea();

        positionsDataManager.setStatusCallback(this::journal);

        setTop(createTopSection());
        setCenter(createMainWorkbench());
        setBottom(createStatusBar());

        if (preferences.getBoolean("remember_console_visible", true)
                && !preferences.getBoolean("console_visible", true)) {
            toggleConsoleVisibility();
        }

        setupKeyboardShortcuts();
        createInitialExchange(configuration);
        loadSymbolsForSelectedExchange();
        openInitialChartIfAvailable();

        if (hasExchangeCredentials(exchangeSelector.getSelectionModel().getSelectedItem())
                || hasConfiguredCredentials()) {
            proceedWithConnection();
        }

        startAutoRefreshTasks();

        // Load sample news calendar on startup
        newsDataProvider.loadSampleCalendar();

        logger.info("TradingWindow initialized.");
    }

    private String resolveTelegramToken(MarketConfiguration configuration) {
        String fromConfig = configuration == null ? "" : safe(configuration.telegramToken());
        return fromConfig.isBlank() ? safe(System.getenv(ENV_TELEGRAM_TOKEN)) : fromConfig;
    }

    private void configureButtonStyles() {
        buyButton.getStyleClass().add("buy-button");
        sellButton.getStyleClass().add("sell-button");
        cancelAllButton.getStyleClass().add("danger-button");
        connectButton.getStyleClass().add("primary-button");
        connectedBrokerLabel.getStyleClass().add("primary-button");
        refreshSymbolsButton.getStyleClass().add("terminal-button");
        addChartButton.getStyleClass().add("terminal-button");
        botTradeButton.getStyleClass().add("terminal-button");

        connectControl.getChildren().setAll(connectButton, connectedBrokerLabel);
        connectControl.setMinWidth(108);

        connectedBrokerLabel.setMinWidth(108);
        connectedBrokerLabel.setAlignment(Pos.CENTER);
        connectedBrokerLabel.setVisible(false);
        connectedBrokerLabel.setManaged(false);

        buyButton.setMinWidth(78);
        sellButton.setMinWidth(78);
        botTradeButton.setMinWidth(96);
        cancelAllButton.setMinWidth(94);
    }

    private void setupKeyboardShortcuts() {
        setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.BACK_QUOTE) {
                toggleConsoleVisibility();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.N) {
                openSelectedSymbolChart();
                event.consume();
            } else if (event.getCode() == KeyCode.F5) {
                loadSymbolsForSelectedExchange();
                refreshAccountWorkspace();
                event.consume();
            }
        });
    }

    private VBox createTopSection() {
        return new VBox(createMenuBar(), createMainToolBar());
    }

    private MenuBar createMenuBar() {
        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(
                menuItem("New Chart", new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                        this::openSelectedSymbolChart),
                menuItem("Refresh Symbols", new KeyCodeCombination(KeyCode.F5), this::loadSymbolsForSelectedExchange),
                new SeparatorMenuItem(),
                menuItem("Save Chart", new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                        this::saveActiveChartSnapshot),
                menuItem("Export Chart", new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN),
                        this::saveActiveChartSnapshot),
                new SeparatorMenuItem(),
                menuItem("Exit", new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN), () -> {
                    shutdown();
                    Platform.exit();
                }));

        Menu editMenu = new Menu("Edit");
        editMenu.getItems().addAll(
                menuItem("Undo", new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN),
                        () -> withFocusedTextInput(TextInputControl::undo)),
                menuItem("Redo", new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN),
                        () -> withFocusedTextInput(TextInputControl::redo)),
                new SeparatorMenuItem(),
                menuItem("Cut", new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN),
                        () -> withFocusedTextInput(TextInputControl::cut)),
                menuItem("Copy", new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN),
                        () -> withFocusedTextInput(TextInputControl::copy)),
                menuItem("Paste", new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN),
                        () -> withFocusedTextInput(TextInputControl::paste)));

        Menu viewMenu = new Menu("View");
        viewMenu.getItems().addAll(
                menuItem("Show Charts", null, chartTabPane::requestFocus),
                menuItem("Show Market Watch", null, marketWatchTable::requestFocus),
                menuItem("Show Terminal", null, () -> {
                    if (!consoleVisible)
                        toggleConsoleVisibility();
                }),
                menuItem("Hide Terminal", null, () -> {
                    if (consoleVisible)
                        toggleConsoleVisibility();
                }),
                menuItem("Detach Terminal", null, this::detachConsoleWindow),
                new SeparatorMenuItem(),
                menuItem("Zoom In", new KeyCodeCombination(KeyCode.PLUS, KeyCombination.CONTROL_DOWN),
                        () -> withActiveChart(chart -> chart.changeZoom(ZoomDirection.IN))),
                menuItem("Zoom Out", new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN),
                        () -> withActiveChart(chart -> chart.changeZoom(ZoomDirection.OUT))));

        Menu chartsMenu = new Menu("Charts");
        chartsMenu.getItems().addAll(
                menuItem("Candlestick", null, this::openSelectedSymbolChart),
                menuItem("Fit Active Chart", null, () -> withActiveChart(CandleStickChart::fitChart)),
                menuItem("Refresh Active Chart", null, () -> withActiveChart(CandleStickChart::refreshChart)),
                menuItem("Toggle Crosshair", null, () -> withActiveChart(CandleStickChart::toggleCrosshair)));

        Menu toolsMenu = new Menu("Tools");
        toolsMenu.getItems().addAll(
                menuItem("Connect Exchange", null, this::connectSelectedExchange),
                menuItem("Toggle Bot Trading", null, this::toggleBotTrading),
                new SeparatorMenuItem(),
                menuItem("Refresh Market Data", null, this::loadSymbolsForSelectedExchange),
                menuItem("Refresh Account", null, this::refreshAccountWorkspace),
                menuItem("Refresh Local Positions", null, this::refreshPositions),
                menuItem("Cancel All Orders", null, this::cancelAllOrders));

        Menu settingsMenu = new Menu("Settings");
        settingsMenu.getItems().addAll(
                menuItem("Application Settings", null, this::showSettingsDialog),
                menuItem("Exchange Credentials", null, this::showSettingsDialog),
                new SeparatorMenuItem(),
                menuItem("Reset Password", null, this::openPasswordReset));

        Menu windowMenu = new Menu("Window");
        windowMenu.getItems().addAll(
                menuItem("Close All Charts", null, this::closeAllCharts),
                menuItem("Detach Console", null, this::detachConsoleWindow));

        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().addAll(
                menuItem("Help", new KeyCodeCombination(KeyCode.F1),
                        () -> showInfo("Help", "InvestPro Help - F5 refreshes data, Ctrl+` toggles terminal.")),
                new SeparatorMenuItem(),
                menuItem("About InvestPro", null, () -> showInfo("About InvestPro",
                        "InvestPro - Professional Trading Terminal\nVersion: 1.0.0\nDeveloper: NOEL NGUEMECHIEU\n© 2020-2026 TradeAdviser.LLC")));

        return new MenuBar(fileMenu, editMenu, viewMenu, chartsMenu, toolsMenu, settingsMenu, windowMenu, helpMenu);
    }

    private MenuItem menuItem(String text, KeyCodeCombination accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        item.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
        return item;
    }

    @Contract(" -> new")
    private @NotNull ToolBar createMainToolBar() {
        exchangeSelector.setPrefWidth(180);
        symbolSelector.setPrefWidth(220);
        timeframeSelector.setPrefWidth(120);

        Label brand = new Label("InvestPro");
        brand.getStyleClass().add("terminal-brand");

        Label modeBadge = new Label("LIVE TERMINAL");
        modeBadge.getStyleClass().add("terminal-mode-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToolBar toolBar = new ToolBar(
                brand,
                modeBadge,
                new Separator(Orientation.VERTICAL),
                new Label("Broker"),
                exchangeSelector,
                connectControl,
                new Separator(Orientation.VERTICAL),
                new Label("Symbol"),
                symbolSelector,
                new Label("Timeframe"),
                timeframeSelector,
                refreshSymbolsButton,
                addChartButton,
                new Separator(Orientation.VERTICAL),
                new Label("Order Type"),
                orderTypeSelector,
                buyButton,
                sellButton,
                cancelAllButton,
                new Separator(Orientation.VERTICAL),
                spacer,
                new Label("AI Trading Terminal"));
        toolBar.getStyleClass().add("main-trading-toolbar");
        return toolBar;
    }

    private SplitPane createMainWorkbench() {
        horizontalWorkbench = new SplitPane();
        horizontalWorkbench.setOrientation(Orientation.HORIZONTAL);
        horizontalWorkbench.getStyleClass().add("workbench-split");
        horizontalWorkbench.getItems().setAll(createLeftSidebar(), createChartWorkspace());
        horizontalWorkbench.setDividerPositions(0.22);

        systemConsole = createTradingConsole();

        mainVerticalWorkbench = new SplitPane();
        mainVerticalWorkbench.setOrientation(Orientation.VERTICAL);
        mainVerticalWorkbench.getStyleClass().add("workbench-split");
        mainVerticalWorkbench.getItems().setAll(horizontalWorkbench, systemConsole);
        mainVerticalWorkbench.setDividerPositions(0.72);
        return mainVerticalWorkbench;
    }

    private @NotNull Node createLeftSidebar() {
        SplitPane leftSplit = new SplitPane();
        leftSplit.setOrientation(Orientation.VERTICAL);
        leftSplit.getStyleClass().add("left-rail");
        leftSplit.getItems().setAll(createMarketWatchPane(), createNavigatorTabs());
        leftSplit.setDividerPositions(0.62);
        leftSplit.setPrefWidth(LEFT_PANEL_WIDTH);
        leftSplit.setMinWidth(260);
        return leftSplit;
    }

    private @NotNull VBox createMarketWatchPane() {
        Label title = new Label("Market Watch");
        title.getStyleClass().add("panel-title");
        Label count = new Label();
        count.getStyleClass().add("panel-meta");
        count.textProperty().bind(symbolCountLabel.textProperty());

        Button openSelectedButton = new Button("Open");
        openSelectedButton.getStyleClass().add("terminal-button");
        openSelectedButton.setOnAction(event -> openSelectedFromMarketWatch());

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("terminal-button");
        refreshButton.setOnAction(event -> loadSymbolsForSelectedExchange());

        Button detachButton = new Button("Detach");
        detachButton.getStyleClass().add("terminal-button");
        detachButton.setOnAction(event -> detachMarketWatch());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, count, spacer, openSelectedButton, refreshButton, detachButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("panel-header");

        ScrollPane chartsScrollPane = new ScrollPane();
        chartsScrollPane.setFitToWidth(true);
        VBox chartsContainer = new VBox(8);
        chartsContainer.setPadding(new Insets(8));
        chartsScrollPane.setContent(chartsContainer);

        marketWatchItems.addListener((javafx.collections.ListChangeListener<TradePair>) change -> {
            chartsContainer.getChildren().clear();
            for (TradePair pair : marketWatchItems) {
                chartsContainer.getChildren().add(createMiniChartRow(pair));
            }
        });

        TabPane watchTabs = new TabPane();
        watchTabs.getStyleClass().add("compact-tabs");
        watchTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        watchTabs.getTabs().setAll(
                new Tab("Symbols", marketWatchTable),
                new Tab("Quick View", chartsScrollPane));

        VBox box = new VBox(8, header, watchTabs);
        box.setPadding(new Insets(8));
        box.getStyleClass().addAll("market-watch", "pro-panel");
        VBox.setVgrow(watchTabs, Priority.ALWAYS);
        return box;
    }

    private HBox createMiniChartRow(TradePair pair) {
        Label symbolLabel = new Label(pair == null ? "-" : pair.toString('/'));
        symbolLabel.getStyleClass().add("symbol-name");
        symbolLabel.setMinWidth(88);

        Label bidLabel = new Label("Bid: %s".formatted(pair == null ? "-" : price(pair.getBid())));
        bidLabel.getStyleClass().add("symbol-bid");

        Label askLabel = new Label("Ask: %s".formatted(pair == null ? "-" : price(pair.getAsk())));
        askLabel.getStyleClass().add("symbol-ask");

        Button viewButton = new Button("View");
        viewButton.getStyleClass().add("terminal-button");
        viewButton.setOnAction(event -> {
            if (pair != null) {
                symbolSelector.getSelectionModel().select(pair);
                openSelectedSymbolChart();
            }
        });

        HBox row = new HBox(10, symbolLabel, bidLabel, askLabel, viewButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6));
        row.getStyleClass().add("mini-symbol-row");
        return row;
    }

    private @NotNull TabPane createNavigatorTabs() {
        TabPane navigatorTabs = new TabPane();
        navigatorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        ListView<String> navigationView = new ListView<>(FXCollections.observableArrayList(
                "Market Info", "Balance", "Equity", "Margin", "Free Margin"));

        ListView<String> overviewView = new ListView<>(FXCollections.observableArrayList(
                "Upcoming events", "Economic calendar", "System announcements"));

        depthChart = new DepthChart();
        depthChart.update(currentOrderBook == null ? new OrderBook() : currentOrderBook);

        // Initialize news calendar and manual trade panels
        newsCalendarPanel = new NewsCalendarPanel(newsDataProvider);

        newsEventOverlay = new NewsEventOverlay(newsDataProvider, 400, 400);
        newsCalendarPanel.getChildren().add(newsEventOverlay);
        TradePair selectedPair = symbolSelector.getValue();
        if (selectedPair != null && exchange != null) {
            manualTradePanel = new ManualTradePanel(exchange, selectedPair, this::refreshAccountWorkspace,
                    notificationService);
        }
        marketInfoPanel = new MarketInfoPanel();
        marketInfoPanel.updateForPair(selectedPair);

        navigatorTabs.getTabs().setAll(
                createFixedTab("MarketInfo", marketInfoPanel),
                createFixedTab("Overview", overviewView),
                createFixedTab("Balances", createAccountBalancesView()),
                createFixedTab("Depth", depthChart),
                createFixedTab("News Calendar", newsCalendarPanel),
                manualTradePanel != null ? createFixedTab("Manual Trade", manualTradePanel)
                        : createFixedTab("Manual Trade", new Label("Select a trading pair first")));
        navigatorTabs.getStyleClass().add("compact-tabs");
        return navigatorTabs;
    }

    private @NotNull VBox createAccountBalancesView() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(12));
        Label title = new Label("Balances");
        title.getStyleClass().add("panel-title");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);
        grid.getStyleClass().add("account-metrics");
        grid.addRow(0, metricLabel("Total Balance"), balanceValueLabel);
        grid.addRow(1, metricLabel("Available Balance"), availableValueLabel);
        grid.addRow(2, metricLabel("Equity"), equityValueLabel);
        grid.addRow(3, metricLabel("Margin Used"), marginUsedValueLabel);
        grid.addRow(4, metricLabel("Free Margin"), freeMarginValueLabel);

        updateAccountBalance();
        container.getChildren().setAll(title, grid);
        container.getStyleClass().add("pro-panel");
        return container;
    }

    private @NotNull Label metricLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("metric-label");
        return label;
    }

    private @NotNull Label valueLabel(String text, String color) {
        Label label = new Label(text);
        label.getStyleClass().addAll("metric-value", "value-label");
        if (color != null && !color.isEmpty()) {
            label.setStyle("-fx-text-fill: %s;".formatted(color));
        }
        return label;
    }

    private void updateAccountBalance() {
        Exchange currentExchange = exchange;
        if (!hasBrokerAccess() || currentExchange == null) {
            balanceValueLabel.setText("$0.00");
            availableValueLabel.setText("$0.00");
            equityValueLabel.setText("$0.00");
            marginUsedValueLabel.setText("$0.00");
            freeMarginValueLabel.setText("$0.00");
            return;
        }

        safeAccountDouble(() -> currentExchange.fetchTotalBalance("USD"))
                .thenCombine(safeAccountDouble(() -> currentExchange.fetchAvailableBalance("USD")),
                        (balance, available) -> new double[] { balance, available })
                .thenCombine(safeAccountDouble(currentExchange::fetchEquity),
                        (values, equity) -> new double[] { values[0], values[1], equity })
                .thenCombine(safeAccountDouble(currentExchange::fetchMarginUsed),
                        (values, marginUsed) -> new double[] { values[0], values[1], values[2], marginUsed })
                .thenCombine(safeAccountDouble(currentExchange::fetchFreeMargin),
                        (values, freeMargin) -> new double[] { values[0], values[1], values[2], values[3], freeMargin })
                .thenAccept(values -> runOnFx(() -> {
                    balanceValueLabel.setText("$%s".formatted(money(values[0])));
                    availableValueLabel.setText("$%s".formatted(money(values[1])));
                    equityValueLabel.setText("$%s".formatted(money(values[2])));
                    marginUsedValueLabel.setText("$%s".formatted(money(values[3])));
                    freeMarginValueLabel.setText("$%s".formatted(money(values[4])));
                }))
                .exceptionally(exception -> {
                    logger.warn("Failed to update account balances", exception);
                    return null;
                });
    }

    private CompletableFuture<Double> safeAccountDouble(
            java.util.function.Supplier<CompletableFuture<Double>> supplier) {
        try {
            CompletableFuture<Double> future = supplier.get();
            if (future == null) {
                return CompletableFuture.completedFuture(0.0);
            }
            return future.exceptionally(exception -> {
                logger.debug("Account metric unavailable", exception);
                return 0.0;
            });
        } catch (Exception exception) {
            logger.debug("Account metric unavailable", exception);
            return CompletableFuture.completedFuture(0.0);
        }
    }

    private @NotNull Node createChartWorkspace() {
        BorderPane workspace = new BorderPane();
        workspace.getStyleClass().add("chart-workspace");

        Label title = new Label("Workspace Charts");
        title.getStyleClass().add("workspace-title");
        Label context = new Label("Multi-symbol market workspace");
        context.getStyleClass().add("workspace-subtitle");
        VBox titleBlock = new VBox(1, title, context);

        Button fitButton = new Button("Fit");
        fitButton.getStyleClass().add("terminal-button");
        fitButton.setOnAction(event -> withActiveChart(CandleStickChart::fitChart));

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("terminal-button");
        refreshButton.setOnAction(event -> withActiveChart(CandleStickChart::refreshChart));

        Button crosshairButton = new Button("Crosshair");
        crosshairButton.getStyleClass().add("terminal-button");
        crosshairButton.setOnAction(event -> withActiveChart(CandleStickChart::toggleCrosshair));

        botSymbolScopeSelector.getItems().setAll("Selected Symbol", "Watchlist", "Best Today");
        botSymbolScopeSelector.getSelectionModel().select(preferences.get("bot_symbol_scope", "Selected Symbol"));
        botSymbolScopeSelector.setPrefWidth(135);
        botSymbolScopeSelector.setOnAction(event -> saveAppState());

        botTradeButton.setOnAction(event -> toggleBotTrading());
        refreshBotTradeButton();

        Button closeAllButton = new Button("Close All");
        closeAllButton.getStyleClass().add("terminal-button");
        closeAllButton.setOnAction(event -> closeAllCharts());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8,
                titleBlock,
                spacer,
                fitButton,
                refreshButton,
                crosshairButton,
                new Label("Bot"),
                botSymbolScopeSelector,
                botTradeButton,
                closeAllButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("workspace-header");

        chartTabPane.setSide(Side.TOP);
        chartTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        chartTabPane.getStyleClass().add("chart-tabs");

        BorderPane workspacePane = new BorderPane();
        workspacePane.getStyleClass().add("workspace-pane");
        workspacePane.setCenter(chartTabPane);

        workspace.setTop(header);
        workspace.setCenter(workspacePane);
        return workspace;
    }

    private @NotNull VBox createTradingConsole() {
        Label title = new Label("Terminal");
        title.getStyleClass().add("terminal-title");
        VBox titleBlock = new VBox(1, title);

        Button detachButton = new Button("Detach");
        detachButton.getStyleClass().add("terminal-button");
        detachButton.setOnAction(event -> detachConsoleWindow());

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add("terminal-button");
        closeButton.setOnAction(event -> toggleConsoleVisibility());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, titleBlock, spacer, detachButton, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("panel-header");

        terminalTabPane.setSide(Side.TOP);
        terminalTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        terminalTabPane.getTabs().setAll(
                createFixedTab("Account", accountSummaryArea),
                createFixedTab("Trades", buildAccountTradesTable()),
                createFixedTab("History", buildOrderHistoryTable()),
                createDetachableTerminalTab("Positions", createPositionsTab().getContent()),
                createDetachableTerminalTab("Risk Monitor", createPositionRiskMonitorTab().getContent()),
                createDetachableTerminalTab("Signals", createSignalTab().getContent()),
                createDetachableTerminalTab("News", createNewsTab().getContent()),
                createDetachableTerminalTab("Alerts", createAlertsTab().getContent()),
                createDetachableTerminalTab("Mailbox", createMailboxTab().getContent()),
                createDetachableTerminalTab("Chat", createChatTab().getContent()),
                createDetachableTerminalTab("Browser", createMarketTab().getContent()),
                createDetachableTerminalTab("Agents", createAgentsTab().getContent()),
                createDetachableTerminalTab("Journal", createJournalTab().getContent()));

        VBox console = new VBox(6, header, terminalTabPane);
        console.setPadding(new Insets(8));
        console.setPrefHeight(CONSOLE_HEIGHT);
        console.getStyleClass().addAll("system-console", "bottom-terminal");
        VBox.setVgrow(terminalTabPane, Priority.ALWAYS);
        return console;
    }

    private void toggleConsoleVisibility() {
        if (mainVerticalWorkbench == null || systemConsole == null) {
            return;
        }

        if (consoleVisible) {
            mainVerticalWorkbench.getItems().remove(systemConsole);
            consoleVisible = false;
        } else {
            if (!mainVerticalWorkbench.getItems().contains(systemConsole)) {
                mainVerticalWorkbench.getItems().add(systemConsole);
            }
            mainVerticalWorkbench.setDividerPositions(0.68);
            consoleVisible = true;
        }

        mainVerticalWorkbench.layout();
        if (horizontalWorkbench != null) {
            horizontalWorkbench.layout();
        }
        saveAppState();
    }

    private void detachConsoleWindow() {
        if (mainVerticalWorkbench == null || systemConsole == null
                || !mainVerticalWorkbench.getItems().contains(systemConsole)) {
            return;
        }

        mainVerticalWorkbench.getItems().remove(systemConsole);
        consoleVisible = false;
        mainVerticalWorkbench.layout();

        Stage stage = new Stage();
        stage.setTitle("InvestPro - System Console");
        stage.setScene(new Scene(systemConsole, 980, 540));

        if (getScene() != null && getScene().getWindow() != null) {
            stage.setX(getScene().getWindow().getX() + 100);
            stage.setY(getScene().getWindow().getY() + 100);
        }

        stage.setOnCloseRequest(event -> reattachConsole());
        stage.show();
        journal("Console detached.");
        saveAppState();
    }

    private void reattachConsole() {
        if (mainVerticalWorkbench == null || systemConsole == null) {
            return;
        }

        if (!mainVerticalWorkbench.getItems().contains(systemConsole)) {
            mainVerticalWorkbench.getItems().add(systemConsole);
        }
        mainVerticalWorkbench.setDividerPositions(0.68);
        consoleVisible = true;
        journal("Console re-attached.");
        saveAppState();
    }

    private DraggableTab createDetachableTerminalTab(String title, Node content) {
        DraggableTab tab = new DraggableTab(title, content);
        tab.setClosable(true);
        return tab;
    }

    private Tab createFixedTab(String title, Node content) {
        DraggableTab tab = new DraggableTab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private Tab createPositionsTab() {
        Label title = new Label("Local Open Positions");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refreshPositions());

        Button closeAll = new Button("Close All");
        closeAll.setOnAction(event -> closeAllPositions());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8, title, spacer, refresh, closeAll);
        HBox pnl = new HBox(16, longPnLLabel, shortPnLLabel, totalPnLLabel);
        pnl.setPadding(new Insets(8));

        VBox content = new VBox(8, header, pnl, positionsTable);
        content.setPadding(new Insets(8));
        VBox.setVgrow(positionsTable, Priority.ALWAYS);
        return createFixedTab("Positions", content);
    }

    private @NotNull Tab createNewsTab() {
        ListView<NewsEvent> newsListView = new ListView<>();
        List<NewsEvent> newsData;
        try {
            newsData = new NewsDataProvider().getUpcomingNewsEvents();
        } catch (Exception exception) {
            logger.warn("Unable to load news.", exception);
            newsData = Collections.emptyList();
        }
        newsListView.setItems(FXCollections.observableArrayList(newsData));
        VBox content = new VBox(newsListView);
        VBox.setVgrow(newsListView, Priority.ALWAYS);
        return createFixedTab("News Events", content);
    }

    private @NotNull Tab createAlertsTab() {
        alertsListView.setItems(
                FXCollections.observableArrayList("No active alerts.", "Create alerts from chart or order panel."));
        VBox content = new VBox(alertsListView);
        VBox.setVgrow(alertsListView, Priority.ALWAYS);
        return createFixedTab("Alerts", content);
    }

    private @NotNull Tab createMailboxTab() {
        mailboxListView.setItems(
                FXCollections.observableArrayList("Mailbox is empty.", "Broker/system messages will appear here."));
        VBox content = new VBox(mailboxListView);
        VBox.setVgrow(mailboxListView, Priority.ALWAYS);
        return createFixedTab("Mailbox", content);
    }

    private @NotNull Tab createChatTab() {
        ListView<String> chatListView = new ListView<>();
        HBox inputArea = new HBox(5);
        inputArea.setPadding(new Insets(8));
        inputArea.getStyleClass().add("chat-input");

        TextField messageInput = new TextField();
        messageInput.setPromptText("Type message...");

        Button sendButton = new Button("Send");
        sendButton.getStyleClass().add("send-button");
        sendButton.setPrefWidth(70);

        ObservableList<String> chatMessages = FXCollections
                .observableArrayList("Chat initialized - Ready to receive messages");
        chatListView.setItems(chatMessages);

        sendButton.setOnAction(event -> {
            String message = safe(messageInput.getText());
            if (!message.isBlank()) {
                chatMessages.add("[You]: %s".formatted(message));
                chatListView.scrollTo(chatMessages.size() - 1);
                messageInput.clear();
                messageInput.requestFocus();
            }
        });
        messageInput.setOnAction(event -> sendButton.fire());

        HBox.setHgrow(messageInput, Priority.ALWAYS);
        inputArea.getChildren().addAll(messageInput, sendButton);

        VBox content = new VBox(chatListView, inputArea);
        VBox.setVgrow(chatListView, Priority.ALWAYS);
        content.setStyle("-fx-background-color: #0f172a;");
        return createFixedTab("Chat", content);
    }

    private @NotNull Tab createMarketTab() {
        Label marketInfo = new Label(
                "Market Information\n\nClick 'Open in Browser' to view market data in your default browser.");
        marketInfo.setStyle("-fx-font-size: 14; -fx-text-fill: #64748b; -fx-padding: 20;");
        marketInfo.setWrapText(true);

        TextField searchField = new TextField();
        searchField.setPromptText("Search market information (opens in browser)...");

        Button openBrowserButton = new Button("Open in Browser");

        openBrowserButton.setOnAction(_ -> {
            try {
                String query = safe(searchField.getText());
                if (query.isBlank()) {
                    openInSystemBrowser("https://www.google.com");
                } else {
                    openInSystemBrowser("https://www.google.com/search?q=%s".formatted(query.replace(" ", "+")));
                }
            } catch (Exception exception) {
                showWarning("Browser", "Could not open browser: %s".formatted(exception.getMessage()));
            }
        });

        searchField.setOnAction(event -> openBrowserButton.fire());

        HBox toolbar = new HBox(8, searchField, openBrowserButton);
        toolbar.setPadding(new Insets(8));
        HBox.setHgrow(searchField, Priority.ALWAYS);

        VBox content = new VBox(6, toolbar, marketInfo);
        VBox.setVgrow(marketInfo, Priority.ALWAYS);
        return createFixedTab("Market", content);
    }

    private @NotNull Tab createSignalTab() {
        signalListView.setItems(signalItems);
        VBox content = new VBox(signalListView);
        VBox.setVgrow(signalListView, Priority.ALWAYS);
        return createFixedTab("Signals", content);
    }

    private @NotNull Tab createAgentsTab() {
        expertsListView.setItems(agentActivityItems);
        VBox content = new VBox(expertsListView);
        VBox.setVgrow(expertsListView, Priority.ALWAYS);
        return createFixedTab("Agents", content);
    }

    private @NotNull Tab createPositionRiskMonitorTab() {
        positionHealthTable.setItems(positionHealthItems);
        positionHealthTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        positionHealthTable.setPlaceholder(new Label("No position health data."));

        TableColumn<PositionHealthScore, String> statusCol = tableColumn("Status",
                score -> String.valueOf(score.getStatus()), 100);
        TableColumn<PositionHealthScore, String> scoreCol = tableColumn("Health",
                score -> number(score.getOverallScore()), 85);
        TableColumn<PositionHealthScore, String> pnlCol = tableColumn("PnL", score -> number(score.getPnlScore()), 75);
        TableColumn<PositionHealthScore, String> riskCol = tableColumn("Risk", score -> number(score.getRiskScore()),
                75);
        TableColumn<PositionHealthScore, String> technicalCol = tableColumn("Technical",
                score -> number(score.getTechnicalScore()), 90);
        TableColumn<PositionHealthScore, String> liquidityCol = tableColumn("Liquidity",
                score -> number(score.getLiquidityScore()), 90);
        TableColumn<PositionHealthScore, String> portfolioCol = tableColumn("Portfolio",
                score -> number(score.getPortfolioScore()), 90);
        TableColumn<PositionHealthScore, String> summaryCol = tableColumn("Summary", PositionHealthScore::getSummary,
                320);

        positionHealthTable.getColumns().setAll(statusCol, scoreCol, pnlCol, riskCol, technicalCol, liquidityCol,
                portfolioCol, summaryCol);

        VBox content = new VBox(positionHealthTable);
        VBox.setVgrow(positionHealthTable, Priority.ALWAYS);
        return createFixedTab("Risk Monitor", content);
    }

    private @NotNull Tab createJournalTab() {
        journalArea.setEditable(false);
        journalArea.setWrapText(true);
        journalArea.setText("InvestPro system journal initialized.\nLayout loaded successfully.\n");
        VBox content = new VBox(journalArea);
        VBox.setVgrow(journalArea, Priority.ALWAYS);
        return createFixedTab("Journal", content);
    }

    private void configureMarketWatchTable() {
        configureMarketWatchTableView(marketWatchTable);
        // Add selection listener to load order book when a symbol is selected
        marketWatchTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadOrderBook(newVal);
            }
        });
    }

    private void configureMarketWatchTableView(TableView<TradePair> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No symbols loaded"));
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.getColumns().setAll(
                tableColumn("Symbol", pair -> pair == null ? "" : pair.toString('/'), 110),
                tableColumn("Price", pair -> pair == null ? "" : marketPrice(midPrice(pair)), 95),
                tableColumn("Bid", pair -> pair == null ? "" : marketPrice(pair.getBid()), 95),
                tableColumn("Ask", pair -> pair == null ? "" : marketPrice(pair.getAsk()), 95),
                tableColumn("Spread", pair -> pair == null ? "" : marketPrice(pair.getSpread()), 80),
                tableColumn("Change %", pair -> pair == null ? "" : percent(pair.getChangePercent()), 85),
                tableColumn("Volume", pair -> pair == null ? "" : compactMarketNumber(pair.getVolume()), 95));

        table.setRowFactory(view -> {
            TableRow<TradePair> row = new TableRow<>() {
                @Override
                protected void updateItem(TradePair item, boolean empty) {
                    super.updateItem(item, empty);
                    if (!empty && item != null && item.getChangePercent() != 0) {
                        setStyle(item.getChangePercent() > 0 ? "-fx-text-fill: #10b981;" : "-fx-text-fill: #ef4444;");
                    } else {
                        setStyle("");
                    }
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    symbolSelector.getSelectionModel().select(row.getItem());
                    openSelectedSymbolChart();
                }
            });
            return row;
        });
    }

    private double midPrice(TradePair pair) {
        if (pair == null) {
            return 0.0;
        }
        double bid = pair.getBid();
        double ask = pair.getAsk();
        if (bid > 0 && ask > 0) {
            return (bid + ask) / 2.0;
        }
        return pair.getLast();
    }

    private void detachMarketWatch() {
        TableView<TradePair> detachedTable = new TableView<>(marketWatchItems);
        configureMarketWatchTableView(detachedTable);

        Button openButton = new Button("Open");
        openButton.getStyleClass().add("terminal-button");
        openButton.setOnAction(event -> {
            TradePair selected = detachedTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                symbolSelector.getSelectionModel().select(selected);
                openSelectedSymbolChart();
            }
        });

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("terminal-button");
        refreshButton.setOnAction(event -> loadSymbolsForSelectedExchange());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, new Label("Market Watch"), symbolCountLabelSnapshot(), spacer, openButton,
                refreshButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8));

        VBox root = new VBox(8, header, detachedTable);
        root.setPadding(new Insets(8));
        VBox.setVgrow(detachedTable, Priority.ALWAYS);

        Stage stage = new Stage();
        stage.setTitle("Market Watch");
        stage.setScene(new Scene(root, 980, 560));
        stage.show();
    }

    private Label symbolCountLabelSnapshot() {
        Label label = new Label();
        label.textProperty().bind(symbolCountLabel.textProperty());
        label.getStyleClass().add("panel-meta");
        return label;
    }

    private void configurePositionsTable() {
        positionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        positionsTable.setPlaceholder(new Label("No open positions"));
        positionsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        positionsTable.getColumns().setAll(
                tableColumn("Symbol", order -> safe(order.getSymbol()), 90),
                tableColumn("Type", order -> safe(order.getType()), 70),
                tableColumn("Qty", order -> number(order.getQuantity()), 90),
                tableColumn("Entry", order -> price(order.getPrice()), 90),
                tableColumn("P&L", order -> money(order.getProfit()), 90),
                tableColumn("Status", order -> safe(order.getStatus()), 90));
    }

    private void configureAccountSummaryArea() {
        accountSummaryArea.setEditable(false);
        accountSummaryArea.setWrapText(true);
        accountSummaryArea.setStyle(
                "-fx-control-inner-background: #0f172a; " +
                        "-fx-text-fill: #f1f5f9; " +
                        "-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                        "-fx-font-size: 11px; " +
                        "-fx-padding: 10px; " +
                        "-fx-focus-color: transparent; " +
                        "-fx-faint-focus-color: transparent;");
        accountSummaryArea.setText("""
                ═════════════════════════════════════════════════
                                ACCOUNT SUMMARY
                ═════════════════════════════════════════════════

                Waiting for connection...
                Select an exchange and click Connect to view account information.

                ═════════════════════════════════════════════════
                """);
    }

    private @NotNull TableView<Trade> buildAccountTradesTable() {
        TableView<Trade> table = new TableView<>(accountTradeItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No account trades"));
        table.getColumns().setAll(
                tableColumn("Time", trade -> dateTime(trade.getTimestamp()), 140),
                tableColumn("Type", trade -> String.valueOf(trade.getTransactionType()), 70),
                tableColumn("Size", trade -> number(trade.getAmount()), 80),
                tableColumn("Symbol", trade -> trade.getTradePair() == null ? "" : trade.getTradePair().getSymbol(),
                        100),
                tableColumn("Price", trade -> price(trade.getPrice()), 90),
                tableColumn("SL", trade -> formatPrice(trade.getStopLoss()), 80),
                tableColumn("TP", trade -> formatPrice(trade.getTakeProfit()), 80),
                tableColumn("Commission", trade -> money(trade.getFee()), 90),
                tableColumn("Swap", trade -> money(trade.getSwap()), 80),
                tableColumn("Profit", trade -> formatProfit(trade.getProfit()), 90));
        return table;
    }

    private @NotNull TableView<Order> buildOrderHistoryTable() {
        TableView<Order> table = new TableView<>(accountHistoryItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No account history"));
        table.getColumns().setAll(
                tableColumn("Date", order -> String.valueOf(order.getDate()), 150),
                tableColumn("Type", Order::getType, 90),
                tableColumn("Side", order -> String.valueOf(order.getSide()), 70),
                tableColumn("Symbol", Order::getSymbol, 110),
                tableColumn("Quantity", order -> number(order.getQuantity()), 90),
                tableColumn("Price", order -> price(order.getPrice()), 90),
                tableColumn("Status", Order::getStatus, 90));
        return table;
    }

    private <T> @NotNull TableColumn<T, String> tableColumn(String title, Function<T, String> mapper, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue() == null ? "" : safe(mapper.apply(cell.getValue()))));
        column.setPrefWidth(width);
        return column;
    }

    private @NotNull HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(6, 10, 6, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusBar.getChildren().setAll(
                new Label("Status:"),
                connectionIndicator,
                connectionStatusLabel,
                new Separator(Orientation.VERTICAL),
                symbolCountLabel,
                spacer,
                new Label("Trading Console"));
        return statusBar;
    }

    private void updateConnectionStatus() {
        boolean connected;
        try {
            connected = hasBrokerAccess();
        } catch (Exception exception) {
            connected = false;
            logger.debug("Unable to determine connection status.", exception);
        }
        connectionIndicator.setFill(connected ? Color.LIMEGREEN : Color.ORANGERED);
        connectionStatusLabel.setText(connected ? "Connected" : "Disconnected");
        updateConnectControl(connected);
        symbolCountLabel.setText("Symbols: %d".formatted(marketWatchItems.size()));
    }

    private void updateConnectControl(boolean connected) {
        if (connected) {
            connectedBrokerLabel.setText("Connected");
            connectedBrokerLabel.setStyle(
                    "-fx-padding: 8 15; -fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold;");
            connectedBrokerLabel.setVisible(true);
            connectedBrokerLabel.setManaged(true);
            connectButton.setVisible(false);
            connectButton.setManaged(false);
        } else {
            connectButton.setText("Connect");
            connectButton.setStyle(
                    "-fx-padding: 8 15; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
            connectButton.setVisible(true);
            connectButton.setManaged(true);
            connectedBrokerLabel.setVisible(false);
            connectedBrokerLabel.setManaged(false);
        }
        connectButton.setDisable(false);
    }

    private boolean hasBrokerAccess() {
        return brokerAccessGranted && exchange != null && Boolean.TRUE.equals(exchange.isConnected());
    }

    private boolean hasOrderSubmissionAccess() {
        try {
            return hasBrokerAccess() && exchange != null && exchange.canSubmitOrders();
        } catch (Exception exception) {
            logger.debug("Unable to check order submission access", exception);
            return false;
        }
    }

    private boolean hasConfiguredCredentials() {
        String selectedExchange = safe(exchangeSelector.getSelectionModel().getSelectedItem());
        if ("OANDA".equalsIgnoreCase(selectedExchange)) {
            return !safe(configuredApiKey).isBlank();
        }
        return !safe(configuredApiKey).isBlank() && !safe(configuredApiSecret).isBlank();
    }

    private boolean canUseAutoRefreshExecutor() {
        return !autoRefreshExecutor.isShutdown() && !autoRefreshExecutor.isTerminated();
    }

    private void configureSelectors(MarketConfiguration configuration) {
        exchangeSelector.getItems().setAll(SUPPORTED_EXCHANGES);
        String configuredExchange = configuration == null ? preferences.get("selected_exchange", "")
                : safe(configuration.exchange());

        if (!configuredExchange.isBlank() && exchangeSelector.getItems().contains(configuredExchange)) {
            exchangeSelector.getSelectionModel().select(configuredExchange);
        } else {
            exchangeSelector.getSelectionModel().selectFirst();
        }

        exchangeSelector.setOnAction(event -> onExchangeChanged());

        symbolSelector.setOnAction(event -> {
            TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
            if (selected != null) {
                marketWatchTable.getSelectionModel().select(selected);
                loadOrderBook(selected);
                updateManualTradePanelForSymbol(selected);
                if (marketInfoPanel != null) {
                    marketInfoPanel.updateForPair(selected);
                }
                saveAppState();
            }
        });

        configureOrderTypeSelector();
        configureTimeframeSelector();
    }

    private void configureOrderTypeSelector() {
        orderTypeSelector.getItems().setAll("MARKET", "LIMIT", "STOP", "BRACKET");
        String savedOrderType = preferences.get("selected_order_type", "MARKET");
        orderTypeSelector.getSelectionModel()
                .select(orderTypeSelector.getItems().contains(savedOrderType) ? savedOrderType : "MARKET");
        orderTypeSelector.setOnAction(event -> {
            String selected = orderTypeSelector.getSelectionModel().getSelectedItem();
            if (selected != null) {
                preferences.put("selected_order_type", selected);
                journal("Order type changed to: " + selected);
            }
        });
        orderTypeSelector.setPrefWidth(100);
    }

    private void configureTimeframeSelector() {
        timeframeSelector.getItems().setAll("1m", "5m", "15m", "30m", "1h", "2h", "4h", "1d", "1w", "1M");
        String savedTimeframe = preferences.get("selected_timeframe", "1h");
        timeframeSelector.getSelectionModel()
                .select(timeframeSelector.getItems().contains(savedTimeframe) ? savedTimeframe : "1h");
        timeframeSelector.setOnAction(event -> {
            String selected = timeframeSelector.getSelectionModel().getSelectedItem();
            if (selected != null) {
                preferences.put("selected_timeframe", selected);
                onTimeframeChanged(selected);
                saveAppState();
                journal("Timeframe changed to: " + selected);
            }
        });
    }

    private void onTimeframeChanged(String timeframe) {
        chartTabPane.getTabs().forEach(tab -> {
            if (tab.getContent() instanceof ChartContainer container) {
                Integer seconds = org.investpro.utils.CandleAggregator.TIMEFRAME_SECONDS.get(timeframe);
                if (seconds != null && seconds > 0) {
                    container.setSecondsPerCandle(seconds);
                }
            } else if (tab.getContent() instanceof CandleStickChart chart) {
                chart.refreshChart();
            }
        });
    }

    private void configureButtons() {
        refreshSymbolsButton.setOnAction(event -> loadSymbolsForSelectedExchange());
        addChartButton.setOnAction(event -> openSelectedSymbolChart());
        connectButton.setOnAction(event -> connectSelectedExchange());
        buyButton.setOnAction(event -> submitMarketOrder(BUY));
        sellButton.setOnAction(event -> submitMarketOrder(SELL));
        cancelAllButton.setOnAction(event -> cancelAllOrders());
    }

    private void configureChartArea() {
        chartTabPane.setSide(Side.TOP);
        chartTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        chartTabPane.setMinHeight(0);
        DraggableTab.registerTabPane(chartTabPane);
    }

    private void createInitialExchange(MarketConfiguration configuration) {
        String selectedExchange = exchangeSelector.getSelectionModel().getSelectedItem();

        if (configuration == null && hasExchangeCredentials(selectedExchange)) {
            loadExchangeCredentials(selectedExchange);
        }

        exchange = createExchange(
                selectedExchange,
                configuration == null ? configuredApiKey : safe(configuration.apiKey()),
                configuration == null ? configuredApiSecret : safe(configuration.apiSecret()));

        setTelegramToken(telegramToken);
        brokerAccessGranted = false;
        updateConnectionStatus();
    }

    public void setTelegramToken(String telegramToken) {
        this.telegramToken = safe(telegramToken);
        if (exchange != null && !this.telegramToken.isBlank()) {
            try {
                exchange.setTokens(this.telegramToken);
            } catch (Exception exception) {
                logger.debug("Unable to set Telegram token on exchange.", exception);
            }
        }
    }

    private void onExchangeChanged() {
        String selectedExchange = exchangeSelector.getSelectionModel().getSelectedItem();

        stopActiveStreaming();
        brokerAccessGranted = false;

        if (systemCore != null) {
            try {
                systemCore.stop();
            } catch (Exception exception) {
                logger.debug("Failed to stop SystemCore bot during exchange change", exception);
            }
            systemCore = null;
            systemCoreEventsSubscribed = false;
        }

        disablePositionAutoRefresh();

        BrokerSession existingSession = brokerSessions.get(safe(selectedExchange));

        if (existingSession != null && existingSession.accessGranted()) {
            exchange = existingSession.exchange();
            brokerAccessGranted = true;
        } else {
            if (hasExchangeCredentials(selectedExchange)) {
                loadExchangeCredentials(selectedExchange);
            } else {
                configuredApiKey = "";
                configuredApiSecret = "";
                telegramToken = "";
            }
            exchange = createExchange(selectedExchange, configuredApiKey, configuredApiSecret);
        }

        setTelegramToken(telegramToken);

        accountPositionItems.clear();
        accountOpenOrderItems.clear();
        accountTradeItems.clear();
        accountHistoryItems.clear();
        positionHealthItems.clear();
        accountSummaryArea.clear();

        loadSymbolsForSelectedExchange();
        updateConnectionStatus();
        journal("Exchange changed to %s".formatted(selectedExchange));
        saveAppState();

        if (brokerAccessGranted) {
            if (existingSession != null && existingSession.account() != null) {
                updateAccountSummary(existingSession.account());
            }
            systemCore = createSystemCore(exchange);
            systemCoreEventsSubscribed = false;
            enablePositionAutoRefresh(exchange, 5000);
            refreshAccountWorkspace();
        } else if (hasExchangeCredentials(selectedExchange)) {
            proceedWithConnection();
        } else {
            showExchangeCredentialDialog(selectedExchange);
        }
    }

    private void connectSelectedExchange() {
        String selectedExchange = exchangeSelector.getSelectionModel().getSelectedItem();

        if (selectedExchange == null || selectedExchange.isBlank()) {
            showWarning("Connection", "No exchange selected.");
            return;
        }

        if (!hasExchangeCredentials(selectedExchange)) {
            showExchangeCredentialDialog(selectedExchange);
            return;
        }

        loadExchangeCredentials(selectedExchange);
        exchange = createExchange(selectedExchange, configuredApiKey, configuredApiSecret);
        setTelegramToken(telegramToken);
        proceedWithConnection();
    }

    private void proceedWithConnection() {
        if (exchange == null) {
            showWarning("Connection", "No exchange selected.");
            return;
        }

        brokerAccessGranted = false;
        updateConnectionStatus();
        connectButton.setDisable(true);
        connectButton.setText("Validating...");

        CompletableFuture
                .supplyAsync(() -> {
                    exchange.connect();
                    CompletableFuture<Account> accountFuture = exchange.fetchAccount();
                    if (accountFuture == null) {
                        throw new IllegalStateException("This broker adapter cannot validate credentials yet.");
                    }
                    return accountFuture;
                })
                .thenCompose(Function.identity())
                .thenAccept(account -> runOnFx(() -> completeConnectionValidation(account)))
                .exceptionally(exception -> {
                    runOnFx(() -> rejectConnectionValidation(exception));
                    return null;
                });
    }

    private void completeConnectionValidation(Account account) {
        if (account == null) {
            rejectConnectionValidation(new IllegalStateException("Broker returned no account for these credentials."));
            return;
        }

        brokerAccessGranted = true;
        brokerSessions.put(safe(exchangeSelector.getValue()), new BrokerSession(exchange, true, account));

        systemCore = createSystemCore(exchange);
        systemCoreEventsSubscribed = false;

        connectButton.setDisable(false);
        journal("Credentials validated for %s".formatted(exchangeSelector.getValue()));

        updateAccountSummary(account);
        updateAccountBalance();
        publishConnectionSignal();
        enablePositionAutoRefresh(exchange, 5000);
        refreshAccountWorkspace();

        TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
        if (selected != null) {
            loadOrderBook(selected);
        }

        updateConnectionStatus();
    }

    private void rejectConnectionValidation(Throwable throwable) {
        brokerAccessGranted = false;
        brokerSessions.remove(safe(exchangeSelector.getValue()));

        if (systemCore != null) {
            try {
                systemCore.stop();
            } catch (Exception exception) {
                logger.debug("Failed to stop SystemCore after validation failure", exception);
            }
            systemCore = null;
            systemCoreEventsSubscribed = false;
        }

        connectButton.setDisable(false);

        try {
            if (exchange != null) {
                exchange.stopAllStreams();
                exchange.disconnectStream();
                exchange.disconnect();
            }
        } catch (Exception exception) {
            logger.debug("Failed to disconnect after credential validation failure", exception);
        }

        accountSummaryArea.setText("Broker access blocked. Check credentials and try again.");
        updateConnectionStatus();

        logger.warn("Broker credential validation failed for {}", exchangeSelector.getValue(), throwable);
        showWarning(
                "Connection Failed",
                "Broker credentials were rejected for %s: %s".formatted(exchangeSelector.getValue(),
                        rootMessage(throwable)));
    }

    private void submitMarketOrder(org.investpro.utils.Side side) {
        if (!hasBrokerAccess()) {
            showWarning("Order", "Connect to an exchange before submitting orders.");
            return;
        }

        if (!hasOrderSubmissionAccess()) {
            showWarning("Order", "%s is connected, but this adapter is not ready for order submission."
                    .formatted(exchange.getDisplayName()));
            return;
        }

        TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Order", "Select a symbol before submitting an order.");
            return;
        }

        String orderType = orderTypeSelector.getSelectionModel().getSelectedItem();
        if (orderType == null || orderType.isBlank()) {
            showWarning("Order", "Select an order type before submitting an order.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("%s %s Order".formatted(side, orderType));
        dialog.setHeaderText("%s %s".formatted(side, selected.toString('/')));
        dialog.setContentText("Amount:");

        dialog.showAndWait().ifPresent(amountText -> {
            double amount;
            try {
                amount = Double.parseDouble(amountText.trim());
            } catch (NumberFormatException exception) {
                showWarning("Order", "Amount must be a number.");
                return;
            }
            if (amount <= 0) {
                showWarning("Order", "Amount must be greater than zero.");
                return;
            }

            submitOrderByType(orderType, selected, side, amount);
        });
    }

    private void submitOrderByType(String orderType, TradePair tradePair, org.investpro.utils.Side side,
            double amount) {
        switch (orderType) {
            case "MARKET" -> submitMarketOrderInternal(tradePair, side, amount);
            case "LIMIT" -> showLimitOrderDialog(tradePair, side, amount);
            case "STOP" -> showStopOrderDialog(tradePair, side, amount);
            case "BRACKET" -> showBracketOrderDialog(tradePair, side, amount);
            default -> showWarning("Order", "Unknown order type: " + orderType);
        }
    }

    private void submitMarketOrderInternal(TradePair tradePair, org.investpro.utils.Side side, double amount) {
        exchange.createMarketOrder(tradePair, side, amount)
                .thenAccept(orderId -> runOnFx(() -> {
                    journal("%s market order submitted for %s: %s".formatted(side, tradePair.toString('/'), orderId));
                    refreshAccountWorkspace();
                }))
                .exceptionally(exception -> {
                    runOnFx(() -> showWarning("Order Failed", "Order failed: %s".formatted(rootMessage(exception))));
                    return null;
                });
    }

    private void showLimitOrderDialog(TradePair tradePair, org.investpro.utils.Side side, double amount) {
        TextInputDialog priceDialog = new TextInputDialog("0.00");
        priceDialog.setTitle("Limit Order Price");
        priceDialog.setHeaderText("Enter limit price for %s %s".formatted(side, tradePair.toString('/')));
        priceDialog.setContentText("Price:");

        priceDialog.showAndWait().ifPresent(priceText -> {
            double price;
            try {
                price = Double.parseDouble(priceText.trim());
            } catch (NumberFormatException exception) {
                showWarning("Order", "Price must be a number.");
                return;
            }
            if (price <= 0) {
                showWarning("Order", "Price must be greater than zero.");
                return;
            }

            exchange.createLimitOrder(tradePair, side, amount, price)
                    .thenAccept(orderId -> runOnFx(() -> {
                        journal("%s limit order submitted for %s at $%.2f: %s".formatted(side, tradePair.toString('/'),
                                price, orderId));
                        refreshAccountWorkspace();
                    }))
                    .exceptionally(exception -> {
                        runOnFx(() -> showWarning("Order Failed",
                                "Order failed: %s".formatted(rootMessage(exception))));
                        return null;
                    });
        });
    }

    private void showStopOrderDialog(TradePair tradePair, org.investpro.utils.Side side, double amount) {
        TextInputDialog stopDialog = new TextInputDialog("0.00");
        stopDialog.setTitle("Stop Order Price");
        stopDialog.setHeaderText("Enter stop price for %s %s".formatted(side, tradePair.toString('/')));
        stopDialog.setContentText("Stop Price:");

        stopDialog.showAndWait().ifPresent(stopText -> {
            double stopPrice;
            try {
                stopPrice = Double.parseDouble(stopText.trim());
            } catch (NumberFormatException exception) {
                showWarning("Order", "Stop price must be a number.");
                return;
            }
            if (stopPrice <= 0) {
                showWarning("Order", "Stop price must be greater than zero.");
                return;
            }

            exchange.createStopOrder(tradePair, side, amount, stopPrice)
                    .thenAccept(orderId -> runOnFx(() -> {
                        journal("%s stop order submitted for %s at $%.2f: %s".formatted(side, tradePair.toString('/'),
                                stopPrice, orderId));
                        refreshAccountWorkspace();
                    }))
                    .exceptionally(exception -> {
                        runOnFx(() -> showWarning("Order Failed",
                                "Order failed: %s".formatted(rootMessage(exception))));
                        return null;
                    });
        });
    }

    private void showBracketOrderDialog(TradePair tradePair, org.investpro.utils.Side side, double amount) {
        TextInputDialog takeProfitDialog = new TextInputDialog("0.00");
        takeProfitDialog.setTitle("Bracket Order - Take Profit");
        takeProfitDialog.setHeaderText("Enter take profit price for %s %s".formatted(side, tradePair.toString('/')));
        takeProfitDialog.setContentText("Take Profit Price:");

        takeProfitDialog.showAndWait().ifPresent(takeProfitText -> {
            double takeProfitPrice;
            try {
                takeProfitPrice = Double.parseDouble(takeProfitText.trim());
            } catch (NumberFormatException exception) {
                showWarning("Order", "Take profit price must be a number.");
                return;
            }
            if (takeProfitPrice <= 0) {
                showWarning("Order", "Take profit price must be greater than zero.");
                return;
            }

            TextInputDialog stopLossDialog = new TextInputDialog("0.00");
            stopLossDialog.setTitle("Bracket Order - Stop Loss");
            stopLossDialog.setHeaderText("Enter stop loss price for %s %s".formatted(side, tradePair.toString('/')));
            stopLossDialog.setContentText("Stop Loss Price:");

            stopLossDialog.showAndWait().ifPresent(stopLossText -> {
                double stopLossPrice;
                try {
                    stopLossPrice = Double.parseDouble(stopLossText.trim());
                } catch (NumberFormatException exception) {
                    showWarning("Order", "Stop loss price must be a number.");
                    return;
                }
                if (stopLossPrice <= 0) {
                    showWarning("Order", "Stop loss price must be greater than zero.");
                    return;
                }

                // Use current market price as entry price
                double entryPrice = midPrice(tradePair);
                if (entryPrice <= 0) {
                    showWarning("Order", "Cannot determine current market price for bracket order.");
                    return;
                }

                exchange.createBracketOrder(tradePair, side, amount, entryPrice, stopLossPrice, takeProfitPrice)
                        .thenAccept(orderId -> runOnFx(() -> {
                            journal("%s bracket order submitted for %s - Entry: $%.2f, TP: $%.2f, SL: $%.2f: %s"
                                    .formatted(side, tradePair.toString('/'), entryPrice, takeProfitPrice,
                                            stopLossPrice, orderId));
                            refreshAccountWorkspace();
                        }))
                        .exceptionally(exception -> {
                            runOnFx(() -> showWarning("Order Failed",
                                    "Order failed: %s".formatted(rootMessage(exception))));
                            return null;
                        });
            });
        });
    }

    private void loadOrderBook(TradePair tradePair) {
        if (!hasBrokerAccess() || exchange == null || tradePair == null) {
            return;
        }

        exchange.fetchOrderBook(tradePair)
                .thenAccept(this::displayOrderBook)
                .exceptionally(exception -> {
                    logger.debug("Failed to fetch orderbook for %s".formatted(tradePair), exception);
                    return null;
                });
    }

    private void displayOrderBook(OrderBook orderBook) {
        runOnFx(() -> {
            if (orderBook == null) {
                orderBookBids.clear();
                orderBookAsks.clear();
                currentOrderBook = new OrderBook();
                if (depthChart != null) {
                    depthChart.update(currentOrderBook);
                }
                return;
            }

            currentOrderBook = orderBook;
            orderBookBids.setAll(orderBook.getBids() == null ? List.of() : orderBook.getBids());
            orderBookAsks.setAll(orderBook.getAsks() == null ? List.of() : orderBook.getAsks());

            if (depthChart != null) {
                depthChart.update(orderBook);
            }
        });
    }

    private void updateManualTradePanelForSymbol(TradePair selectedPair) {
        if (selectedPair == null || exchange == null) {
            return;
        }

        // Create or update manual trade panel for the selected pair
        manualTradePanel = new ManualTradePanel(exchange, selectedPair, this::refreshAccountWorkspace,
                notificationService);

        // Update the Manual Trade tab in navigator
        TabPane navigatorTabs = null;
        for (var node : this.getChildren()) {
            if (node instanceof TabPane && node.getStyleClass().contains("compact-tabs")) {
                navigatorTabs = (TabPane) node;
                break;
            }
        }

        if (navigatorTabs != null) {
            for (Tab tab : navigatorTabs.getTabs()) {
                if ("Manual Trade".equals(tab.getText())) {
                    tab.setContent(manualTradePanel);
                    break;
                }
            }
        }
    }

    private SystemCore createSystemCore(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("exchange cannot be null");
        }

        Properties config = new Properties();
        config.setProperty("telegram_token", safe(telegramToken));

        String openAiKey = safe(System.getenv("OPENAI_API_KEY"));
        if (!openAiKey.isBlank()) {
            config.setProperty("ai.provider", "openai");
            config.setProperty("openai.api_key", openAiKey);
        } else {
            config.setProperty("ai.provider", "local");
        }

        return new SystemCore(exchange, config);
    }

    private void ensureSystemCoreStarted(TradePair selectedPair) {
        if (exchange == null) {
            throw new IllegalStateException("Exchange is not initialized.");
        }
        if (selectedPair == null) {
            throw new IllegalArgumentException("selectedPair cannot be null.");
        }

        boolean coreMissing = systemCore == null;
        boolean botMissing = !coreMissing && systemCore.getSmartBot() == null;
        boolean botNotStarted = !coreMissing && systemCore.getSmartBot() != null
                && !systemCore.getSmartBot().isStarted();

        if (coreMissing || botMissing || botNotStarted) {
            systemCore = createSystemCore(exchange);
            systemCore.start(tradingService, selectedPair);
            systemCore.getSmartBot().setSelectedTradePair(selectedPair);

            systemCore.getSmartBot().setAiReasoningEnabled(true);
            systemCoreEventsSubscribed = false;
        }

        if (!systemCoreEventsSubscribed) {
            systemCore.getSmartBot().getEventBus().subscribeAll(this::displayAgentEvent);
            systemCoreEventsSubscribed = true;
        }
    }

    private List<TradePair> selectedBotSymbols() {
        String scope = safe(botSymbolScopeSelector.getValue());
        return switch (scope) {
            case "Watchlist" -> List.copyOf(marketWatchItems);
            case "Best Today" -> bestSymbolsOfTheDay();
            default -> {
                TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
                yield selected == null ? List.of() : List.of(selected);
            }
        };
    }

    private List<TradePair> bestSymbolsOfTheDay() {
        return marketWatchItems.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingDouble((TradePair pair) -> Math.abs(pair.getChangePercent())).reversed()
                        .thenComparing(Comparator.comparingDouble(TradePair::getVolume).reversed()))
                .limit(10)
                .toList();
    }

    private void displayAgentEvent(AgentEvent event) {
        if (event == null) {
            return;
        }
        runOnFx(() -> {
            String line = formatAgentEvent(event);
            appendBounded(agentActivityItems, line, 300);
            if (event.payload() instanceof Signal || event.type().contains("SIGNAL")) {
                appendBounded(signalItems, formatSignalEvent(event), 200);
            }
        });
    }

    private void publishConnectionSignal() {
        String symbol = symbolSelector.getSelectionModel().getSelectedItem() == null
                ? "-"
                : symbolSelector.getSelectionModel().getSelectedItem().toString('/');
        String message = "[%s] %s connected. Active symbol: %s".formatted(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                exchange == null ? "Broker" : exchange.getDisplayName(),
                symbol);
        appendBounded(signalItems, message, 200);
        appendAgentActivity("Broker session validated for %s.".formatted(exchangeSelector.getValue()));
    }

    private String formatAgentEvent(AgentEvent event) {
        return "[%s] %s | %s | %s".formatted(
                DateTimeFormatter.ofPattern("HH:mm:ss").format(event.timestamp().atZone(ZoneId.systemDefault())),
                event.type(),
                event.source(),
                event.payload() == null ? "" : event.payload().toString());
    }

    private String formatSignalEvent(AgentEvent event) {
        if (event.payload() instanceof Signal signal) {
            return "[%s] %s %s confidence %.0f%% via %s".formatted(
                    DateTimeFormatter.ofPattern("HH:mm:ss")
                            .format(signal.getTimestamp().atZone(ZoneId.systemDefault())),
                    signal.getTradePair() == null ? "-" : signal.getTradePair().toString('/'),
                    signal.getSide(),
                    signal.getConfidence() * 100.0,
                    signal.getStrategyName());
        }
        return formatAgentEvent(event);
    }

    private void appendAgentActivity(String message) {
        runOnFx(() -> appendBounded(agentActivityItems,
                "[%s] %s".formatted(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), safe(message)),
                300));
    }

    private void appendBounded(ObservableList<String> items, String value, int maxItems) {
        if (items == null || value == null) {
            return;
        }
        if (items.size() == 1 && items.get(0).contains("will appear here")) {
            items.clear();
        }
        items.add(0, value);
        while (items.size() > maxItems) {
            items.remove(items.size() - 1);
        }
    }

    private void refreshBotTradeButton() {
        botTradeButton.setText(botTradingEnabled ? "Stop Bot" : "Bot Trade");
        botTradeButton.setStyle(botTradingEnabled
                ? "-fx-padding: 6 12; -fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold;"
                : "-fx-padding: 6 12; -fx-background-color: #334155; -fx-text-fill: white; -fx-font-weight: bold;");
    }

    private void saveAppState() {
        try {
            String selectedExchange = safe(exchangeSelector.getValue());
            TradePair selectedSymbol = symbolSelector.getSelectionModel().getSelectedItem();
            preferences.put("selected_exchange", selectedExchange);
            preferences.put("bot_symbol_scope", safe(botSymbolScopeSelector.getValue()));
            preferences.putBoolean("console_visible", consoleVisible);
            preferences.putBoolean("bot_trading_enabled", botTradingEnabled);
            if (selectedSymbol != null && !selectedExchange.isBlank()) {
                preferences.put("selected_symbol_" + selectedExchange, selectedSymbol.toString('/'));
            }
            preferences.sync();
        } catch (Exception exception) {
            logger.debug("Failed to save app state", exception);
        }
    }

    public void stopActiveStreaming() {
        CandleStickChart chart = getActiveChart();
        if (chart != null) {
            chart.setAutoTradeEnabled(false);
        }

        botTradingEnabled = false;

        if (systemCore != null) {
            try {
                systemCore.stopStreaming();
                if (systemCore.getSmartBot() != null
                        && systemCore.getSmartBot().isStarted()
                        && systemCore.getSmartBot().isAutoTradingEnabled()) {
                    systemCore.setAutoTradingEnabled(false);

                }
            } catch (Exception exception) {
                logger.debug("Failed to stop SystemCore streaming", exception);
            }
        }

        refreshBotTradeButton();
        saveAppState();
    }

    private void loadSymbolsForSelectedExchange() {
        marketWatchItems.clear();
        symbolSelector.getItems().clear();

        if (exchange == null) {
            updateConnectionStatus();
            return;
        }

        List<TradePair> tradePairs;
        try {
            tradePairs = exchange.getTradePairSymbol();
        } catch (Exception exception) {
            logger.error("Failed to load trade pairs from {}", exchangeSelector.getValue(), exception);
            tradePairs = fallbackMarketWatchPairs();
            showWarning("API Error", "Failed to load live trade pairs from %s: %s. Showing default symbols."
                    .formatted(exchangeSelector.getValue(), rootMessage(exception)));
        }

        if (tradePairs == null || tradePairs.isEmpty()) {
            tradePairs = fallbackMarketWatchPairs();
        }

        if (tradePairs.isEmpty()) {
            showWarning("No Symbols",
                    "Unable to fetch or build trade pairs for %s.".formatted(exchangeSelector.getValue()));
            updateConnectionStatus();
            return;
        }

        marketWatchItems.setAll(tradePairs);
        symbolSelector.getItems().setAll(tradePairs);

        String rememberedSymbol = preferences.get("selected_symbol_" + safe(exchangeSelector.getValue()), "");
        TradePair rememberedPair = tradePairs.stream()
                .filter(pair -> Objects.equals(pair.toString('/'), rememberedSymbol))
                .findFirst()
                .orElse(null);

        TradePair selected = rememberedPair != null ? rememberedPair : tradePairs.getFirst();
        symbolSelector.getSelectionModel().select(selected);
        marketWatchTable.getSelectionModel().select(selected);
        symbolCountLabel.setText("Symbols: %d".formatted(tradePairs.size()));

        updateConnectionStatus();

    }

    private List<TradePair> fallbackMarketWatchPairs() {
        try {
            List<TradePair> pairs = new ArrayList<>();
            pairs.add(new TradePair("BTC", "USD"));
            pairs.add(new TradePair("ETH", "USD"));
            pairs.add(new TradePair("SOL", "USD"));
            pairs.add(new TradePair("EUR", "USD"));
            pairs.add(new TradePair("AAPL", "USD"));
            return pairs;
        } catch (Exception exception) {
            logger.debug("Unable to build fallback pairs", exception);
            return List.of();
        }
    }

    private void openInitialChartIfAvailable() {
        TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openSelectedSymbolChart();
        }
    }

    private void openSelectedFromMarketWatch() {
        TradePair selected = marketWatchTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            symbolSelector.getSelectionModel().select(selected);
            openSelectedSymbolChart();
        }
    }

    private void openSelectedSymbolChart() {
        TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Chart", "Select a symbol before opening a chart.");
            return;
        }
        if (exchange == null) {
            showWarning("Chart", "No exchange is available.");
            return;
        }

        String title = selected.toString('/');

        for (Tab tab : chartTabPane.getTabs()) {
            if (Objects.equals(tab.getText(), title)) {
                chartTabPane.getSelectionModel().select(tab);
                return;
            }
        }

        ChartContainer container = new ChartContainer(exchange, selected, true, telegramToken, tradingService);
        container.setOnChartError(this::journal);
        container.setOnAutoTradeAction(() -> startBotFromChart(selected, container));

        Tab tab = createDetachableTerminalTab(title, container);
        tab.setOnClosed(_ -> container.dispose());
        chartTabPane.getTabs().add(tab);
        chartTabPane.getSelectionModel().select(tab);
        saveAppState();
    }

    private void startBotFromChart(TradePair selected, ChartContainer container) {
        symbolSelector.getSelectionModel().select(selected);

        try {
            if (!hasBrokerAccess()) {
                showWarning("Bot Trading", "Validate broker credentials before starting the bot.");
                return;
            }
            if (!hasOrderSubmissionAccess()) {
                showWarning("Bot Trading",
                        "%s is connected, but this adapter cannot submit orders.".formatted(exchange.getDisplayName()));
                return;
            }

            ensureSystemCoreStarted(selected);
            systemCore.setAutoTradingEnabled(true);
            systemCore.startStreaming(selected, SystemCore.StreamingMode.EVERYTHING);

            CandleStickChart chart = container.getChart();
            if (chart != null) {
                chart.setAutoTradeEnabled(true);
            }

            botTradingEnabled = true;
            appendAgentActivity("SystemCore bot enabled from chart toolbar for " + selected.toString('/'));
            refreshBotTradeButton();
            saveAppState();
        } catch (Exception exception) {
            logger.error("Failed to start chart auto trading", exception);
            showWarning("Bot Trading", "Could not start bot trading: %s".formatted(rootMessage(exception)));
        }
    }

    private CandleStickChart getActiveChart() {
        Tab selected = chartTabPane.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getContent() == null) {
            return null;
        }
        if (selected.getContent() instanceof ChartContainer container) {
            return container.getChart();
        }
        if (selected.getContent() instanceof CandleStickChart chart) {
            return chart;
        }
        return null;
    }

    private void withActiveChart(java.util.function.Consumer<CandleStickChart> consumer) {
        CandleStickChart chart = getActiveChart();
        if (chart != null && consumer != null) {
            consumer.accept(chart);
        }
    }

    private void closeAllCharts() {
        for (Tab tab : new ArrayList<>(chartTabPane.getTabs())) {
            if (tab.getContent() instanceof ChartContainer container) {
                container.dispose();
            } else if (tab.getContent() instanceof CandleStickChart chart) {
                chart.dispose();
            }
        }
        chartTabPane.getTabs().clear();
    }

    private void saveActiveChartSnapshot() {
        Node node = null;
        Tab selectedTab = chartTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            node = selectedTab.getContent();
        }
        if (node == null) {
            showWarning("Snapshot", "No active chart to save.");
            return;
        }
        try {
            WritableImage image = node.snapshot(null, null);
            String fileName = "InvestPro-%s.png".formatted(SNAPSHOT_FORMAT.format(LocalDateTime.now()));
            File output = new File(System.getProperty("user.home"), fileName);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", output);
            journal("Chart snapshot saved: " + output.getAbsolutePath());
        } catch (IOException exception) {
            showWarning("Snapshot", "Failed to save snapshot: %s".formatted(exception.getMessage()));
        }
    }

    protected void refreshAccountWorkspace() {
        if (!hasBrokerAccess() || exchange == null) {
            return;
        }
        updateAccountBalance();
        try {
            exchange.fetchAccount()
                    .thenAccept(account -> runOnFx(() -> updateAccountSummary(account)))
                    .exceptionally(exception -> {
                        logger.debug("Failed to refresh account", exception);
                        return null;
                    });
        } catch (Exception exception) {
            logger.debug("Account refresh unavailable", exception);
        }

        // Fetch all positions
        try {
            exchange.fetchAllPositions()
                    .thenAccept(positions -> runOnFx(() -> updateAccountPositions(positions)))
                    .exceptionally(exception -> {
                        logger.debug("Failed to fetch positions", exception);
                        return null;
                    });
        } catch (Exception exception) {
            logger.debug("Fetch positions unavailable", exception);
        }

        // Fetch all trades
        try {
            exchange.fetchAccountTrades(null)
                    .thenAccept(trades -> runOnFx(() -> updateAccountTrades(trades)))
                    .exceptionally(exception -> {
                        logger.debug("Failed to fetch trades", exception);
                        return null;
                    });
        } catch (Exception exception) {
            logger.debug("Fetch trades unavailable", exception);
        }

        // Fetch risk monitoring data (position health scores)
        try {
            exchange.fetchAllPositions()
                    .thenAccept(positions -> runOnFx(() -> updatePositionHealthScores(positions)))
                    .exceptionally(exception -> {
                        logger.debug("Failed to fetch risk data", exception);
                        return null;
                    });
        } catch (Exception exception) {
            logger.debug("Fetch risk data unavailable", exception);
        }
    }

    private void updateAccountPositions(List<Position> positions) {
        if (positions == null) {
            accountPositionItems.clear();
            return;
        }
        // Replace all positions with fetched data
        accountPositionItems.setAll(positions);
        journal("Positions updated: " + positions.size() + " position(s)");
    }

    private void updateAccountTrades(List<Trade> trades) {
        if (trades == null) {
            accountTradeItems.clear();
            return;
        }
        // Calculate profit locally for each trade before displaying
        List<Trade> tradesWithProfit = trades.stream()
                .peek(this::calculateTradeProfit)
                .toList();
        // Replace all trades with fetched data (reverse order to show newest first)
        accountTradeItems.setAll(tradesWithProfit.reversed());
        journal("Trades updated: " + tradesWithProfit.size() + " trade(s)");
    }

    private void calculateTradeProfit(Trade trade) {
        if (trade == null) {
            return;
        }

        // Calculate trade P&L locally based on entry price, fees, and swap costs
        // This provides a transparent view of actual costs incurred in each trade
        double profit = 0;

        if (trade.getPrice() > 0 && trade.getAmount() > 0) {
            // Calculate P&L based on notional value minus costs
            double notionalValue = trade.getPrice() * trade.getAmount();
            double totalCosts = trade.getFee() + Math.abs(trade.getSwap());

            // For BUY trades: profit = notional value - costs
            // For SELL trades: profit = notional value - costs
            // (costs are always negative impacts on profit)
            profit = notionalValue - totalCosts;

            // Adjust sign based on transaction type to reflect actual P&L
            if (trade.getTransactionType() != null && trade.getTransactionType().isSell()) {
                // For SELL: subtract costs from the proceeds
                profit = notionalValue - totalCosts;
            } else if (trade.getTransactionType() != null && trade.getTransactionType().isBuy())

            {
                // For BUY: represent as investment made
                profit = notionalValue - totalCosts;
            }
        } else {
            // If price/amount unavailable, just show negative impact of costs
            profit = -(trade.getFee() + Math.abs(trade.getSwap()));
        }

        // Set the calculated profit (will be displayed in Profit column)
        trade.setProfit(profit);
    }

    private void updatePositionHealthScores(List<Position> positions) {
        if (positions == null) {
            positionHealthItems.clear();
            return;
        }
        // Generate position health scores from positions
        List<PositionHealthScore> healthScores = positions.stream()
                .map(this::createPositionHealthScore)
                .toList();
        positionHealthItems.setAll(healthScores);
        journal("Risk monitor updated: " + positions.size() + " position(s) analyzed");
    }

    private PositionHealthScore createPositionHealthScore(Position position) {
        if (position == null) {
            return new PositionHealthScore();
        }
        // Create a health score based on position data
        PositionHealthScore.PositionHealthScoreBuilder builder = PositionHealthScore.builder();

        double profit = position.getUnrealizedPnl();
        double score = calculateHealthScore(profit);

        builder.overallScore(score);
        builder.status(PositionHealthScore.statusFromScore(score));
        builder.pnlScore(Math.min(1.0, Math.max(0.0, (profit + 1000) / 2000)));
        builder.riskScore(profit > 0 ? 0.8 : 0.5);
        builder.summary("Position: " + position.getTradePair() + " - P&L: " + String.format("%.2f", profit));
        builder.calculatedAt(LocalDateTime.now());

        return builder.build();
    }

    private double calculateHealthScore(double profit) {
        // Simple health calculation based on profit/loss
        // Returns value between 0.0 and 1.0
        if (profit > 500) {
            return 0.95;
        } else if (profit > 200) {
            return 0.85;
        } else if (profit > 0) {
            return 0.75;
        } else if (profit > -100) {
            return 0.60;
        } else if (profit > -300) {
            return 0.40;
        } else {
            return 0.15;
        }
    }

    private void updateAccountSummary(Account account) {
        if (account == null) {
            accountSummaryArea.setText("Account data unavailable.");
            return;
        }
        accountSummaryArea.setText("""
                ═════════════════════════════════════════════════
                                ACCOUNT SUMMARY
                ═════════════════════════════════════════════════

                Broker: %s
                Account: %s
                Connected: %s

                Updated: %s
                ═════════════════════════════════════════════════
                """.formatted(
                exchange == null ? "-" : exchange.getDisplayName(),
                account,
                hasBrokerAccess(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
    }

    private void refreshPositions() {
        positionsDataManager.refreshLocalPositions(positionsItems);
        updatePositionPnlLabels();
    }

    private void closeAllPositions() {
        positionsItems.clear();
        updatePositionPnlLabels();
        journal("Local positions cleared.");
    }

    private void updatePositionPnlLabels() {
        double longPnl = positionsItems.stream()
                .filter(order -> "BUY"
                        .equalsIgnoreCase(safe(order.getSide() == null ? "" : order.getSide().toString())))
                .mapToDouble(Order::getProfit)
                .sum();
        double shortPnl = positionsItems.stream()
                .filter(order -> "SELL"
                        .equalsIgnoreCase(safe(order.getSide() == null ? "" : order.getSide().toString())))
                .mapToDouble(Order::getProfit)
                .sum();
        double total = longPnl + shortPnl;
        longPnLLabel.setText("Long P&L: $%s".formatted(money(longPnl)));
        shortPnLLabel.setText("Short P&L: $%s".formatted(money(shortPnl)));
        totalPnLLabel.setText("Total P&L: $%s".formatted(money(total)));
    }

    private void cancelAllOrders() {
        if (!hasBrokerAccess()) {
            showWarning("Orders", "Connect to an exchange first.");
            return;
        }
        try {
            exchange.cancelAllOrders()
                    .thenRun(() -> runOnFx(() -> {
                        journal("Cancel-all orders request sent.");
                        refreshAccountWorkspace();
                    }))
                    .exceptionally(exception -> {
                        runOnFx(() -> showWarning("Orders", "Cancel-all failed: %s".formatted(rootMessage(exception))));
                        return null;
                    });
        } catch (Exception exception) {
            showWarning("Orders", "Cancel-all is not supported by this adapter yet.");
        }
    }

    private void enablePositionAutoRefresh(Exchange exchange, long intervalMillis) {
        if (!canUseAutoRefreshExecutor()) {
            return;
        }
        autoRefreshExecutor.scheduleAtFixedRate(() -> {
            if (hasBrokerAccess()) {
                refreshAccountWorkspace();
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void disablePositionAutoRefresh() {
        // This executor is shared by the window. Individual scheduled tasks are
        // defensive and check connection state.
    }

    private void startAutoRefreshTasks() {
        if (!canUseAutoRefreshExecutor()) {
            return;
        }
        autoRefreshExecutor.scheduleAtFixedRate(() -> runOnFx(this::updateConnectionStatus), 2, 5, TimeUnit.SECONDS);
        autoRefreshExecutor.scheduleAtFixedRate(() -> {
            TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
            if (selected != null && hasBrokerAccess()) {
                loadOrderBook(selected);
            }
        }, 3, 8, TimeUnit.SECONDS);
        // Refresh account data (positions, trades, history) every 120 seconds (less
        // frequent for stability)
        autoRefreshExecutor.scheduleAtFixedRate(() -> {
            if (hasBrokerAccess()) {
                runOnFx(this::refreshAccountWorkspace);
            }
        }, 10, 120, TimeUnit.SECONDS);
    }

    private void showSettingsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("InvestPro Settings");
        dialog.setHeaderText("Application and broker settings");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        TextField apiKeyField = new TextField(configuredApiKey);
        PasswordField secretField = new PasswordField();
        secretField.setText(configuredApiSecret);
        TextField telegramField = new TextField(telegramToken);

        grid.addRow(0, new Label("API Key"), apiKeyField);
        grid.addRow(1, new Label("API Secret / Account ID"), secretField);
        grid.addRow(2, new Label("Telegram Token"), telegramField);

        Button saveButton = new Button("Save");
        saveButton.setOnAction(event -> {
            configuredApiKey = safe(apiKeyField.getText());
            configuredApiSecret = safe(secretField.getText());
            setTelegramToken(telegramField.getText());
            saveExchangeCredentials(safe(exchangeSelector.getValue()));
            journal("Settings saved.");
            dialog.close();
        });

        VBox box = new VBox(10, grid, saveButton);
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait();
    }

    private void openPasswordReset() {
        new PasswordReset();
    }

    private void showExchangeCredentialDialog(String selectedExchange) {
        if (selectedExchange == null || selectedExchange.isBlank()) {
            showWarning("Credentials", "No exchange selected.");
            return;
        }

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Exchange Credentials");
        dialog.setHeaderText("Enter credentials for " + selectedExchange);

        ButtonType connectType = new ButtonType("Save & Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(connectType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        TextField apiKeyField = new TextField(configuredApiKey);
        PasswordField secretField = new PasswordField();
        secretField.setText(configuredApiSecret);
        TextField telegramField = new TextField(telegramToken);

        boolean oanda = "OANDA".equalsIgnoreCase(selectedExchange);
        apiKeyField.setPromptText(oanda ? "OANDA Token" : "API Key");
        secretField.setPromptText(oanda ? "Account ID optional" : "API Secret");
        telegramField.setPromptText("Telegram Token optional");

        grid.addRow(0, new Label(oanda ? "Token" : "API Key"), apiKeyField);
        grid.addRow(1, new Label(oanda ? "Account ID" : "API Secret"), secretField);
        grid.addRow(2, new Label("Telegram Token"), telegramField);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(buttonType -> {
            if (buttonType == connectType) {
                configuredApiKey = safe(apiKeyField.getText());
                configuredApiSecret = safe(secretField.getText());
                telegramToken = safe(telegramField.getText());
                saveExchangeCredentials(selectedExchange);
                return true;
            }
            return false;
        });

        dialog.showAndWait().ifPresent(saved -> {
            if (saved) {
                exchange = createExchange(selectedExchange, configuredApiKey, configuredApiSecret);
                setTelegramToken(telegramToken);
                proceedWithConnection();
            }
        });
    }

    private boolean hasExchangeCredentials(String exchangeName) {
        loadExchangeCredentials(exchangeName);
        if ("OANDA".equalsIgnoreCase(exchangeName)) {
            return !configuredApiKey.isBlank();
        }
        return !configuredApiKey.isBlank() && !configuredApiSecret.isBlank();
    }

    private void loadExchangeCredentials(String exchangeName) {
        String key = safe(exchangeName);
        configuredApiKey = preferences.get("exchange_api_key_" + key, configuredApiKey);
        configuredApiSecret = preferences.get("exchange_api_secret_" + key, configuredApiSecret);
        telegramToken = preferences.get("telegram_token_" + key, telegramToken);
    }

    private void saveExchangeCredentials(String exchangeName) {
        String key = safe(exchangeName);
        if (key.isBlank()) {
            return;
        }
        preferences.put("exchange_api_key_" + key, configuredApiKey);
        preferences.put("exchange_api_secret_" + key, configuredApiSecret);
        preferences.put("telegram_token_" + key, telegramToken);
    }

    @Contract("null, _, _ -> new")
    private @NotNull Exchange createExchange(String exchangeName, String apiKey, String apiSecret) {
        String name = safe(exchangeName).toUpperCase();
        return switch (name) {
            case "BINANCE US" -> new BinanceUs(apiKey, apiSecret);
            case "BINANCE" -> new Binance(apiKey, apiSecret);
            case "OANDA" -> new Oanda(apiKey, apiSecret);
            case "BITFINEX" -> new Bitfinex(apiKey, apiSecret);
            case "ALPACA" -> new Alpaca(apiKey, apiSecret);
            case "INTERACTIVE BROKERS", "INTERACTIVE_BROKERS", "IBKR" -> new InteractiveBrokers(apiKey, apiSecret);
            case "COINBASE", "" -> new Coinbase(apiKey, apiSecret);
            default -> {
                logger.warn("Exchange {} is not implemented yet. Falling back to Coinbase.", exchangeName);
                yield new Coinbase(apiKey, apiSecret);
            }
        };
    }

    private void openInSystemBrowser(String url) throws IOException {
        if (!Desktop.isDesktopSupported()) {
            showWarning("Browser", "Desktop browser integration is not supported on this system.");
            return;
        }
        Desktop.getDesktop().browse(URI.create(url));
    }

    private void withFocusedTextInput(java.util.function.Consumer<TextInputControl> action) {
        Node focusOwner = getScene() == null ? null : getScene().getFocusOwner();
        if (focusOwner instanceof TextInputControl control && action != null) {
            action.accept(control);
        }
    }

    private void runOnFx(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void journal(String message) {
        runOnFx(() -> journalArea.appendText("[%s] %s%n".formatted(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                safe(message))));
    }

    private String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String price(double value) {
        return value <= 0 || !Double.isFinite(value) ? "-" : String.format("%.5f", value);
    }

    private String marketPrice(double value) {
        return value <= 0 || !Double.isFinite(value) ? "-" : String.format("%.5f", value);
    }

    private String formatPrice(double value) {
        return value <= 0 || !Double.isFinite(value) ? "-" : String.format("%.5f", value);
    }

    private String money(double value) {
        return !Double.isFinite(value) ? "0.00" : String.format("%.2f", value);
    }

    private String number(double value) {
        return !Double.isFinite(value) ? "0" : String.format("%.4f", value);
    }

    private String percent(double value) {
        return !Double.isFinite(value) ? "0.00%" : String.format("%.2f%%", value);
    }

    private String compactMarketNumber(double value) {
        if (!Double.isFinite(value))
            return "-";
        if (Math.abs(value) >= 1_000_000)
            return String.format("%.2fM", value / 1_000_000.0);
        if (Math.abs(value) >= 1_000)
            return String.format("%.2fK", value / 1_000.0);
        return String.format("%.2f", value);
    }

    private String formatProfit(double value) {
        if (!Double.isFinite(value)) {
            return "$0.00";
        }
        return (value >= 0 ? "+$" : "-$") + String.format("%.2f", Math.abs(value));
    }

    private String dateTime(Object timestamp) {
        return timestamp == null ? "" : String.valueOf(timestamp);
    }

    /**
     * Kept for compatibility with the existing UI code.
     */
    private static final class PositionsDataManager {
        private java.util.function.Consumer<String> statusCallback;

        void setStatusCallback(java.util.function.Consumer<String> callback) {
            this.statusCallback = callback;
        }

        void refreshLocalPositions(ObservableList<Order> target) {
            if (statusCallback != null) {
                statusCallback.accept("Local positions refreshed.");
            }
        }
    }

    public void updateStreamingStatus(String status) {
        connectionStatusLabel.setText(status == null || status.isBlank() ? "Streaming" : status);
    }

    public void appendJournal(String message) {
        journal(message);
    }

    public void updateTickerFromStream(TradePair tradePair, Ticker ticker) {
        if (tradePair == null || ticker == null) {
            return;
        }

        // Update the TradePair with ticker data
        tradePair.setBid(ticker.getBidPrice());
        tradePair.setAsk(ticker.getAskPrice());
        tradePair.setLast(ticker.getLastPrice());
        tradePair.setVolume(ticker.getVolume());
        tradePair.setUpdatedAt(Instant.now());

        int index = marketWatchItems.indexOf(tradePair);

        if (index >= 0) {
            // Force UI update by setting the item
            TradePair updated = marketWatchItems.get(index);
            updated.setBid(ticker.getBidPrice());
            updated.setAsk(ticker.getAskPrice());
            updated.setLast(ticker.getLastPrice());
            updated.setVolume(ticker.getVolume());
            updated.setUpdatedAt(Instant.now());
            marketWatchItems.set(index, updated);
        }

        symbolCountLabel.setText("Symbols: %d".formatted(marketWatchItems.size()));
    }

    public void updateTradeFromStream(TradePair tradePair, Trade trade) {
        if (trade != null) {
            accountTradeItems.add(0, trade);

            while (accountTradeItems.size() > 500) {
                accountTradeItems.remove(accountTradeItems.size() - 1);
            }
        }
    }

    public void updateOrderBookFromStream(TradePair tradePair, OrderBook orderBook) {
        displayOrderBook(orderBook);
    }

    public void updateCandleFromStream(TradePair tradePair, CandleData candleData) {
        CandleStickChart chart = getActiveChart();

        if (chart != null) {
            chart.refreshChart();
        }
    }

    public void updateAccountFromStream(Account account) {
        updateAccountSummary(account);
        updateAccountBalance();
    }

    public void updateOpenOrderFromStream(OpenOrder order) {
        if (order == null) {
            return;
        }

        accountOpenOrderItems.addFirst(order);

        while (accountOpenOrderItems.size() > 500) {
            accountOpenOrderItems.removeLast();
        }
    }

    public void updatePositionFromStream(Position position) {
        if (position == null) {
            return;
        }

        accountPositionItems.addFirst(position);

        while (accountPositionItems.size() > 500) {
            accountPositionItems.removeLast();
        }
    }

    /*
     * TradingWindow UI-independent streaming patch
     *
     * Goal:
     * - Desktop UI updates must NOT depend on SmartBot/SystemCore bot streaming.
     * - UI stream is owned directly by TradingWindow using
     * DesktopExchangeStreamBridge.
     * - Bot stream is owned by SystemCore only when auto-trading is enabled.
     *
     * Apply these changes inside TradingWindow.java.
     */

    // ============================================================================
    // 1. Add these fields near your other TradingWindow fields
    // ============================================================================

    private ExchangeStreamSubscription desktopStreamSubscription;
    private DesktopExchangeStreamBridge desktopStreamBridge;
    private boolean desktopStreaming;

    // ============================================================================
    // 3. Add these UI-only stream lifecycle methods inside TradingWindow
    // ============================================================================

    private void startDesktopStream(TradePair tradePair) {
        if (!hasBrokerAccess()) {
            return;
        }

        if (exchange == null || tradePair == null) {
            return;
        }

        /*
         * Never run the UI stream while bot trading is active.
         * During bot trading, SystemCore owns the execution stream.
         */
        if (botTradingEnabled) {
            return;
        }

        stopDesktopStream();

        desktopStreamBridge = new DesktopExchangeStreamBridge(this);
        desktopStreamSubscription = new ExchangeStreamSubscription();
        desktopStreamSubscription.setTradePairs(Set.of(tradePair));
        desktopStreamSubscription.setTicker(true);
        desktopStreamSubscription.setTrades(true);
        desktopStreamSubscription.setCandles(true);
        desktopStreamSubscription.setOrderBook(true);
        desktopStreamSubscription.setAccount(true);
        desktopStreamSubscription.setOrders(true);
        desktopStreamSubscription.setFills(true);
        desktopStreamSubscription.setPositions(true);
        desktopStreamSubscription.setBalances(true);

        try {
            exchange.stream(desktopStreamSubscription, desktopStreamBridge);
            desktopStreaming = true;
            updateStreamingStatus("UI stream: " + tradePair.toString('/'));
            appendJournal("Desktop UI stream started for " + tradePair.toString('/'));
        } catch (Exception exception) {
            desktopStreaming = false;
            desktopStreamSubscription = null;
            desktopStreamBridge = null;
            appendJournal("Desktop UI stream failed: " + rootMessage(exception));
        }
    }

    private void stopDesktopStream() {
        if (exchange != null && desktopStreamSubscription != null) {
            try {
                exchange.stopStreaming(desktopStreamSubscription);
            } catch (Exception exception) {
                logger.debug("Failed to stop desktop UI stream", exception);
            }
        }

        desktopStreamSubscription = null;
        desktopStreamBridge = null;
        desktopStreaming = false;
    }

    // ============================================================================
    // 4. In completeConnectionValidation(...), start UI-only stream after
    // connection
    // ============================================================================

    /*
     * Find this existing block:
     *
     * TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
     * if (selected != null) {
     * loadOrderBook(selected);
     * }
     * updateConnectionStatus();
     *
     * Replace it with this:
     */

    // ============================================================================
    // 5. Replace toggleBotTrading() with this bot/UI-safe version
    // ============================================================================

    private void toggleBotTrading() {
        if (botTradingEnabled) {
            botTradingEnabled = false;

            if (systemCore != null) {
                try {
                    systemCore.setAutoTradingEnabled(false);
                    systemCore.stopStreaming();
                } catch (Exception exception) {
                    logger.debug("Failed to stop SystemCore streaming", exception);
                }
            }

            withActiveChart(chart -> chart.setAutoTradeEnabled(false));
            appendAgentActivity("SystemCore bot auto trading disabled.");
            refreshBotTradeButton();
            saveAppState();

            /*
             * After bot stream stops, restart UI-only stream so the desktop keeps updating.
             */
            TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
            if (selected != null && hasBrokerAccess()) {
                startDesktopStream(selected);
            }

            return;
        }

        if (!hasBrokerAccess()) {
            showWarning("Bot Trading", "Validate broker credentials before starting the bot.");
            return;
        }

        if (!hasOrderSubmissionAccess()) {
            showWarning(
                    "Bot Trading",
                    "%s is connected, but this adapter cannot submit orders."
                            .formatted(exchange.getDisplayName()));
            return;
        }

        List<TradePair> symbols = selectedBotSymbols();
        if (symbols.isEmpty()) {
            showWarning("Bot Trading", "No symbols are available for the selected bot scope.");
            return;
        }

        try {
            TradePair primarySymbol = symbols.getFirst();

            /*
             * Prevent duplicate exchange streams:
             * - UI-only stream OFF
             * - SystemCore bot stream ON
             */
            stopDesktopStream();

            ensureSystemCoreStarted(primarySymbol);
            systemCore.setAutoTradingEnabled(true);
            systemCore.startStreaming(symbols, SystemCore.StreamingMode.EVERYTHING);

            withActiveChart(chart -> chart.setAutoTradeEnabled(true));

            botTradingEnabled = true;

            appendAgentActivity(
                    "SystemCore bot enabled for %d symbol(s) using %s."
                            .formatted(symbols.size(), botSymbolScopeSelector.getValue()));

            refreshBotTradeButton();
            saveAppState();

        } catch (Exception exception) {
            logger.error("Failed to toggle bot trading", exception);
            showWarning(
                    "Bot Trading",
                    "Could not start bot trading: %s".formatted(rootMessage(exception)));
        }
    }

    // ============================================================================
    // 9. Update shutdown() to stop both UI stream and bot stream
    // ============================================================================

    /*
     * Add stopDesktopStream() at the beginning of shutdown():
     */

    public void shutdown() {
        journal("Shutting down TradingWindow.");

        stopDesktopStream();
        stopActiveStreaming();

        if (systemCore != null) {
            try {
                systemCore.stop();
            } catch (Exception exception) {
                logger.debug("Failed to stop SystemCore bot", exception);
            }

            try {
                systemCore.disconnect();
            } catch (Exception exception) {
                logger.debug("Failed to disconnect SystemCore", exception);
            }

            systemCore = null;
            systemCoreEventsSubscribed = false;
        }

        disablePositionAutoRefresh();
        closeAllCharts();

        try {
            autoRefreshExecutor.shutdown();
            if (!autoRefreshExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                autoRefreshExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            autoRefreshExecutor.shutdownNow();
            logger.debug("Auto-refresh executor interrupted during shutdown", exception);
        }

        try {
            if (exchange != null) {
                exchange.stopAllStreams();
                exchange.disconnectStream();
                exchange.disconnect();
            }
        } catch (Exception exception) {
            logger.warn("Failed to close exchange cleanly", exception);
        }
    }

}
