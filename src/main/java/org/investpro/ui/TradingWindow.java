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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import org.investpro.core.chat.News;
import org.investpro.core.chat.NewsDataProvider;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.signal.Signal;
import org.investpro.core.bot.SmartBot;
import org.investpro.data.Account;
import org.investpro.exchange.Alpaca;
import org.investpro.exchange.Binance;
import org.investpro.exchange.BinanceUs;
import org.investpro.exchange.Bitfinex;
import org.investpro.exchange.Coinbase;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.InteractiveBrokers;

import org.investpro.exchange.Oanda;
import org.investpro.models.currency.CryptoCurrency;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.*;
import org.investpro.repository.CurrencyRepository;
import org.investpro.repository.OrderRepository;
import org.investpro.repository.RepositoryFactory;
import org.investpro.repository.TradeRepository;
import org.investpro.service.CurrencyService;
import org.investpro.service.OrderService;
import org.investpro.service.TradeService;
import org.investpro.service.TradingService;
import org.investpro.ui.charts.CandleStickChart;
import org.investpro.ui.charts.CandleStickChartDisplay;
import org.investpro.utils.DraggableTab;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.prefs.Preferences;

import javafx.embed.swing.SwingFXUtils;

@Getter
@Setter
public class TradingWindow extends BorderPane {

    private static final Logger logger = LoggerFactory.getLogger(TradingWindow.class);

    private static final double DEFAULT_WIDTH = 1540;
    private static final double DEFAULT_HEIGHT = 820;
    private static final double LEFT_PANEL_WIDTH = 310;
    private static final double CONSOLE_HEIGHT = 250;
    private static final int MARKET_WATCH_TICKER_REFRESH_LIMIT = 40;
    private static final String ENV_TELEGRAM_TOKEN = "INVESTPRO_TELEGRAM_BOT_TOKEN";
    private static final DateTimeFormatter SNAPSHOT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String[] SUPPORTED_EXCHANGES = {
            "COINBASE", "BINANCE US", "BINANCE", "OANDA", "BITFINEX", "ALPACA", "INTERACTIVE BROKERS",
            "SCHWAB", "BITMEX", "BITSTAMP", "BITTREX"
    };


    private final ComboBox<String> exchangeSelector = new ComboBox<>();
    private final ComboBox<TradePair> symbolSelector = new ComboBox<>();
    private final ComboBox<String> botSymbolScopeSelector = new ComboBox<>();

    private final Button connectButton = new Button("Connect");
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
    private final PositionsDataManager positionsDataManager = new PositionsDataManager();
    private final TextArea accountSummaryArea = new TextArea();
    private final Label longPnLLabel = new Label("Long P&L: $0.00");
    private final Label shortPnLLabel = new Label("Short P&L: $0.00");
    private final Label totalPnLLabel = new Label("Total P&L: $0.00");
    private final Label connectionStatusLabel = new Label("Disconnected");
    private final Label symbolCountLabel = new Label("Symbols: 0");
    private final Circle connectionIndicator = new Circle(6, Color.ORANGERED);
    private final TextArea journalArea = new TextArea();
    private final ObservableList<String> agentActivityItems = FXCollections.observableArrayList("Agent activity will appear here.");
    private final ObservableList<String> signalItems = FXCollections.observableArrayList("Signal engine idle.", "No signals loaded.");
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
    private final Preferences preferences = Preferences.userNodeForPackage(TradingWindow.class);
    // Auto-refresh scheduling
    private final ScheduledExecutorService autoRefreshExecutor = Executors.newScheduledThreadPool(3, r -> {
        Thread t = new Thread(r, "TradingWindow-AutoRefresh");
        t.setDaemon(true);
        return t;
    });
    private SplitPane mainVerticalWorkbench;
    private SplitPane horizontalWorkbench;
    private VBox systemConsole;
    private boolean consoleVisible = true;
    private Exchange exchange;
    private SmartBot smartBot;
    private boolean smartBotEventsSubscribed;
    private boolean botTradingEnabled;
    private String configuredApiKey = "";
    private String configuredApiSecret = "";
    private String telegramToken = "";
    private boolean initialized;
    private OrderBook currentOrderBook = new OrderBook();
    private Tab selectedTab;

    public TradingWindow() {
        this(null, RepositoryFactory.createTradeRepository(), RepositoryFactory.createOrderRepository(), RepositoryFactory.createCurrencyRepository());
    }

    public TradingWindow(MarketConfiguration configuration)
            throws ParseException, IOException, InterruptedException, ClassNotFoundException {
        this(configuration, RepositoryFactory.createTradeRepository(), RepositoryFactory.createOrderRepository(), RepositoryFactory.createCurrencyRepository());
    }

    public TradingWindow(
            MarketConfiguration configuration,
            TradeRepository tradeRepository,
            OrderRepository orderRepository,
            CurrencyRepository currencyRepository
    ) {
        super();
        this.tradeRepository = Objects.requireNonNull(tradeRepository, "tradeRepository must not be null");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.currencyRepository = Objects.requireNonNull(currencyRepository, "currencyRepository must not be null");

        this.tradeService = new TradeService(this.tradeRepository);
        this.orderService = new OrderService(this.orderRepository);
        this.currencyService = new CurrencyService(this.currencyRepository);
        this.tradingService = new TradingService(this.tradeService, this.orderService, this.currencyService);

        initialize(configuration);
    }

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

        // Start automatic data refresh
        startAutoRefreshTasks();

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
        refreshSymbolsButton.getStyleClass().add("terminal-button");
        addChartButton.getStyleClass().add("terminal-button");
        botTradeButton.getStyleClass().add("terminal-button");

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
                menuItem("New Chart", new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN), this::openSelectedSymbolChart),
                menuItem("Refresh Symbols", new KeyCodeCombination(KeyCode.F5), this::loadSymbolsForSelectedExchange),
                new SeparatorMenuItem(),
                menuItem("Save Chart", new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN), this::saveActiveChartSnapshot),
                menuItem("Export Chart", new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN), this::saveActiveChartSnapshot),
                new SeparatorMenuItem(),
                menuItem("Exit", new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN), () -> {
                    shutdown();
                    Platform.exit();
                })
        );

        Menu editMenu = new Menu("Edit");
        editMenu.getItems().addAll(
                menuItem("Undo", new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN), () -> withFocusedTextInput(TextInputControl::undo)),
                menuItem("Redo", new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN), () -> withFocusedTextInput(TextInputControl::redo)),
                new SeparatorMenuItem(),
                menuItem("Cut", new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN), () -> withFocusedTextInput(TextInputControl::cut)),
                menuItem("Copy", new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN), () -> withFocusedTextInput(TextInputControl::copy)),
                menuItem("Paste", new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN), () -> withFocusedTextInput(TextInputControl::paste))
        );

        Menu viewMenu = new Menu("View");
        viewMenu.getItems().addAll(
                menuItem("Show Charts", null, chartTabPane::requestFocus),
                menuItem("Show Market Watch", null, marketWatchTable::requestFocus),
                menuItem("Show Terminal", null, () -> {
                    if (!consoleVisible) toggleConsoleVisibility();
                }),
                menuItem("Hide Terminal", null, () -> {
                    if (consoleVisible) toggleConsoleVisibility();
                }),
                menuItem("Detach Terminal", null, this::detachConsoleWindow),
                new SeparatorMenuItem(),
                menuItem("Zoom In", new KeyCodeCombination(KeyCode.PLUS, KeyCombination.CONTROL_DOWN), () -> withActiveChart(chart -> chart.changeZoom(org.investpro.utils.ZoomDirection.IN))),
                menuItem("Zoom Out", new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN), () -> withActiveChart(chart -> chart.changeZoom(org.investpro.utils.ZoomDirection.OUT)))
        );

        Menu chartsMenu = new Menu("Charts");
        chartsMenu.getItems().addAll(
                menuItem("Candlestick", null, this::openSelectedSymbolChart),
                menuItem("Fit Active Chart", null, () -> withActiveChart(CandleStickChart::fitChart)),
                menuItem("Refresh Active Chart", null, () -> withActiveChart(CandleStickChart::refreshChart)),
                menuItem("Toggle Crosshair", null, () -> withActiveChart(CandleStickChart::toggleCrosshair))
        );

        Menu toolsMenu = new Menu("Tools");
        toolsMenu.getItems().addAll(
                menuItem("Connect Exchange", null, this::connectSelectedExchange),
                menuItem("Toggle Bot Trading", null, this::toggleBotTrading),
                new SeparatorMenuItem(),
                menuItem("Refresh Market Data", null, this::loadSymbolsForSelectedExchange),
                menuItem("Refresh Account", null, this::refreshAccountWorkspace),
                menuItem("Refresh Local Positions", null, this::refreshPositions),
                menuItem("Cancel All Orders", null, this::cancelAllOrders)
        );

        Menu settingsMenu = new Menu("Settings");
        settingsMenu.getItems().addAll(
                menuItem("Application Settings", null, this::showSettingsDialog),
                menuItem("Exchange Credentials", null, () -> showSettingsDialog())
        );

        Menu windowMenu = new Menu("Window");
        windowMenu.getItems().addAll(
                menuItem("Close All Charts", null, this::closeAllCharts),
                menuItem("Detach Console", null, this::detachConsoleWindow)
        );

        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().addAll(
                menuItem("Help", new KeyCodeCombination(KeyCode.F1), () -> showInfo("Help", "InvestPro Help - F5 refreshes data, Ctrl+` toggles terminal.")),
                new SeparatorMenuItem(),
                menuItem("About InvestPro", null, () -> showInfo("About InvestPro", "InvestPro - Professional Trading Terminal\nVersion: 1.0.0\nDeveloper: NOEL NGUEMECHIEU\n© 2020-2026 TradeAdviser.LLC"))
        );

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
                connectButton,
                new Separator(Orientation.VERTICAL),
                new Label("Symbol"),
                symbolSelector,
                refreshSymbolsButton,
                addChartButton,
                new Separator(Orientation.VERTICAL),
                buyButton,
                sellButton,
                cancelAllButton,
                new Separator(Orientation.VERTICAL),
                spacer,
                new Label("AI Trading Terminal")
        );
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
        watchTabs.getTabs().setAll(new Tab("Symbols", marketWatchTable), new Tab("Quick View", chartsScrollPane));

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

    private VBox createOrderBookPane() {
        // Configure Bid Table
        setupOrderBookTable(orderBookBidsTable, orderBookBids, true);

        // Configure Ask Table
        setupOrderBookTable(orderBookAsksTable, orderBookAsks, false);

        // Create layout with bids on left, asks on right
        Label bidsLabel = new Label("Bids");
        bidsLabel.getStyleClass().addAll("panel-title", "book-bid-title");

        Label asksLabel = new Label("Asks");
        asksLabel.getStyleClass().addAll("panel-title", "book-ask-title");

        VBox bidsContainer = new VBox(6, bidsLabel, orderBookBidsTable);
        VBox.setVgrow(orderBookBidsTable, Priority.ALWAYS);
        bidsContainer.setPadding(new Insets(8));

        VBox asksContainer = new VBox(6, asksLabel, orderBookAsksTable);
        VBox.setVgrow(orderBookAsksTable, Priority.ALWAYS);
        asksContainer.setPadding(new Insets(8));

        HBox orderBookContent = new HBox(8, bidsContainer, asksContainer);
        HBox.setHgrow(bidsContainer, Priority.ALWAYS);
        HBox.setHgrow(asksContainer, Priority.ALWAYS);

        VBox container = new VBox(orderBookContent);
        container.getStyleClass().addAll("order-book-pane", "pro-panel");
        VBox.setVgrow(orderBookContent, Priority.ALWAYS);
        return container;
    }

    private void setupOrderBookTable(TableView<OrderBook.PriceLevel> table,
                                     ObservableList<OrderBook.PriceLevel> items, boolean isBid) {
        table.setItems(items);
        table.getStyleClass().add(isBid ? "bid-table" : "ask-table");

        // Price Column
        TableColumn<OrderBook.PriceLevel, String> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(String.format("%.8f", cellData.getValue().getPrice())));
        priceCol.setPrefWidth(120);

        // Size Column
        TableColumn<OrderBook.PriceLevel, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(String.format("%.8f", cellData.getValue().getSize())));
        sizeCol.setPrefWidth(120);

        // Total Column
        TableColumn<OrderBook.PriceLevel, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(cellData -> {
            double total = cellData.getValue().getPrice() * cellData.getValue().getSize();
            return new ReadOnlyStringWrapper(String.format("%.8f", total));
        });
        totalCol.setPrefWidth(120);

        // Orders Count Column
        TableColumn<OrderBook.PriceLevel, String> ordersCol = new TableColumn<>("Orders");
        ordersCol.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(String.valueOf(cellData.getValue().getNumOrders())));
        ordersCol.setPrefWidth(80);

        table.getColumns().clear();
        table.getColumns().add(priceCol);
        table.getColumns().add(sizeCol);
        table.getColumns().add(totalCol);
        table.getColumns().add(ordersCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    private @NotNull TabPane createNavigatorTabs() {
        TabPane navigatorTabs = new TabPane();
        navigatorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        ListView<String> navigationView = new ListView<>(FXCollections.observableArrayList("Default Account", "Balance", "Equity", "Margin", "Free Margin"));
        ListView<String> overviewView = new ListView<>(FXCollections.observableArrayList("Upcoming events", "Economic calendar", "System announcements"));

        navigatorTabs.getTabs().setAll(
                createFixedTab("Overview", overviewView),
                createFixedTab("Balances", createAccountBalancesView()),
                createFixedTab("Depth", createOrderBookPane()));
        navigatorTabs.getStyleClass().add("compact-tabs");

        return navigatorTabs;
    }

    private @NotNull VBox createAccountBalancesView() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(12));
        Label title = new Label("Balances ");
        title.getStyleClass().add("panel-title");
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);
        grid.getStyleClass().add("account-metrics");

        Label balanceValue = valueLabel("$0.00", "#10b981");
        Label availableValue = valueLabel("$0.00", "#10b981");
        Label equityValue = valueLabel("$0.00", "#3b82f6");
        Label marginUsedValue = valueLabel("$0.00", "#ef4444");
        Label freeMarginValue = valueLabel("$0.00", "#f59e0b");

        grid.addRow(0, metricLabel("Total Balance"), balanceValue);
        grid.addRow(1, metricLabel("Available Balance"), availableValue);
        grid.addRow(2, metricLabel("Equity"), equityValue);
        grid.addRow(3, metricLabel("Margin Used"), marginUsedValue);
        grid.addRow(4, metricLabel("Free Margin"), freeMarginValue);

        updateAccountBalance(balanceValue, availableValue, equityValue, marginUsedValue, freeMarginValue);
        container.getChildren().setAll(title, grid);
        container.getStyleClass().add("pro-panel");
        return container;
    }

    private @NotNull Label metricLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("metric-label");
        return label;
    }

    private @NotNull Label valueLabel(String text,
                                      String color) {
        Label label = new Label(text);
        label.getStyleClass().add("metric-value");
        label.setStyle("-fx-text-fill: %s;".formatted(color));
        label.getStyleClass().add("value-label");
        return label;
    }

    private void updateAccountBalance(Label balanceValue, Label availableValue, Label equityValue, Label marginUsedValue, Label freeMarginValue) {
        Exchange currentExchange = exchange;
        if (currentExchange == null) {
            return;
        }

        currentExchange.fetchTotalBalance("USD").thenCombine(currentExchange.fetchAvailableBalance("USD"), (balance, available) -> new double[]{balance, available})
                .thenCombine(currentExchange.fetchEquity(), (values, equity) -> new double[]{values[0], values[1], equity})
                .thenCombine(currentExchange.fetchMarginUsed(), (values, marginUsed) -> new double[]{values[0], values[1], values[2], marginUsed})
                .thenCombine(currentExchange.fetchFreeMargin(), (values, freeMargin) -> new double[]{values[0], values[1], values[2], values[3], freeMargin})
                .thenAccept(values -> runOnFx(() -> {
                    balanceValue.setText("$%s".formatted(money(values[0])));
                    availableValue.setText("$%s".formatted(money(values[1])));
                    equityValue.setText("$%s".formatted(money(values[2])));
                    marginUsedValue.setText("$%s".formatted(money(values[3])));
                    freeMarginValue.setText("$%s".formatted(money(values[4])));
                }))
                .exceptionally(exception -> {
                    logger.warn("Failed to update account balances", exception);
                    return null;
                });
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
        fitButton.setOnAction(_ -> withActiveChart(CandleStickChart::fitChart));

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("terminal-button");
        refreshButton.setOnAction(_ -> withActiveChart(CandleStickChart::refreshChart));

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

        HBox header = new HBox(
                8,
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
        detachButton.setOnAction(_ -> detachConsoleWindow());
        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add("terminal-button");
        closeButton.setOnAction(_ -> toggleConsoleVisibility());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, titleBlock, spacer, detachButton, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("panel-header");


        terminalTabPane.setSide(Side.TOP);
        terminalTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        terminalTabPane.getTabs().setAll(
                createFixedTab("Account ", accountSummaryArea),
                createFixedTab("Orders ", buildAccountPositionsTable()),
                createFixedTab("History ", buildOrderHistoryTable()),
                createFixedTab("Trades ", buildAccountTradesTable()),
                createDetachableTerminalTab("Positions history", createPositionsTab().getContent()),
                createDetachableTerminalTab("Signals ", createSignalTab().getContent()),
                createDetachableTerminalTab("News ", createNewsTab().getContent()),
                createDetachableTerminalTab("Alerts ", createAlertsTab().getContent()),
                createDetachableTerminalTab("Mailbox ", createMailboxTab().getContent()),
                createDetachableTerminalTab("Chat ", createChatTab().getContent()),
                createDetachableTerminalTab("Browser ", createMarketTab().getContent()),
                createDetachableTerminalTab("Agents ", createAgentsTab().getContent()),
                createDetachableTerminalTab("Journal ", createJournalTab().getContent())
        );

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
        if (mainVerticalWorkbench == null || systemConsole == null || !mainVerticalWorkbench.getItems().contains(systemConsole)) {
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
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
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
        ListView<News> newsListView = new ListView<>();
        List<News> newsData;
        try {
            newsData = new NewsDataProvider().getNews();
        } catch (Exception exception) {
            logger.warn("Unable to load news.", exception);
            newsData = Collections.emptyList();
        }
        newsListView.setItems(FXCollections.observableArrayList(newsData));
        VBox content = new VBox(newsListView);
        VBox.setVgrow(newsListView, Priority.ALWAYS);
        return createFixedTab("News", content);
    }

    private @NotNull Tab createAlertsTab() {
        alertsListView.setItems(FXCollections.observableArrayList("No active alerts.", "Create alerts from chart or order panel."));
        VBox content = new VBox(alertsListView);
        VBox.setVgrow(alertsListView, Priority.ALWAYS);
        return createFixedTab("Alerts", content);
    }

    private @NotNull Tab createMailboxTab() {
        mailboxListView.setItems(FXCollections.observableArrayList("Mailbox is empty.", "Broker/system messages will appear here."));
        VBox content = new VBox(mailboxListView);
        VBox.setVgrow(mailboxListView, Priority.ALWAYS);
        return createFixedTab("Mailbox", content);
    }

    private @NotNull Tab createChatTab() {
        ListView<String> chatListView = new ListView<>();
        HBox inputArea = new HBox(5);
        inputArea.setPadding(new Insets(8));
        inputArea.setStyle("-fx-border-color: #334155; -fx-border-width: 1 0 0 0;");

        TextField messageInput = new TextField();
        messageInput.setPromptText("Type message...");
        messageInput.setStyle("-fx-padding: 8; -fx-font-size: 11;");

        Button sendButton = new Button("Send");
        sendButton.setStyle("-fx-padding: 6 12; -fx-background-color: #3b82f6; -fx-text-fill: #ffffff;");
        sendButton.setPrefWidth(70);

        ObservableList<String> chatMessages = FXCollections.observableArrayList();
        chatMessages.add("Chat initialized - Ready to receive messages");
        chatListView.setItems(chatMessages);

        sendButton.setOnAction(event -> {
            String message = messageInput.getText().trim();
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
        WebView marketView = new WebView();
        marketView.getEngine().load("https://www.google.com");
        TextField searchField = new TextField();
        searchField.setPromptText("Search market information...");
        Button openBrowserButton = new Button("Open in Browser");
        openBrowserButton.setOnAction(event -> {
            try {
                String currentUrl = marketView.getEngine().getLocation();
                if (currentUrl == null || currentUrl.isBlank()) {
                    showInfo("Browser", "No page loaded yet.");
                } else {
                    openInSystemBrowser(currentUrl);
                }
            } catch (Exception exception) {
                showWarning("Browser", "Could not open browser: %s".formatted(exception.getMessage()));
            }
        });
        searchField.setOnAction(event -> {
            String query = safe(searchField.getText());
            if (!query.isBlank()) {
                marketView.getEngine().load("https://www.google.com/search?q=%s".formatted(query.replace(" ", "+")));
            }
        });
        HBox toolbar = new HBox(8, searchField, openBrowserButton);
        toolbar.setPadding(new Insets(8));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        VBox content = new VBox(6, toolbar, marketView);
        VBox.setVgrow(marketView, Priority.ALWAYS);
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

    private @NotNull Tab createExpertsTab() {
        expertsListView.setItems(FXCollections.observableArrayList("Experts console ready.", "Automations and execution notes will appear here."));
        VBox content = new VBox(expertsListView);
        VBox.setVgrow(expertsListView, Priority.ALWAYS);
        return createFixedTab("Experts", content);
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
    }

    private void configureMarketWatchTableView(TableView<TradePair> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No symbols loaded"));
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        table.getColumns().setAll(
                tableColumn("Symbol", pair -> pair == null ? "" : pair.toString('/'), 105),
                tableColumn("Base", pair -> pair == null ? "" : pair.getBaseCode(), 70),
                tableColumn("Quote", pair -> pair == null ? "" : pair.getCounterCode(), 70),
                tableColumn("Bid", pair -> pair == null ? "" : marketPrice(pair.getBid()), 90),
                tableColumn("Ask", pair -> pair == null ? "" : marketPrice(pair.getAsk()), 90),
                tableColumn("Last", pair -> pair == null ? "" : marketPrice(pair.getLast()), 90),
                tableColumn("Spread", pair -> pair == null ? "" : marketPrice(pair.getSpread()), 80),
                tableColumn("Volume", pair -> pair == null ? "" : compactMarketNumber(pair.getVolume()), 95),
                tableColumn("Change %", pair -> pair == null ? "" : percent(pair.getChangePercent()), 85),
                tableColumn("High 24h", pair -> pair == null ? "" : marketPrice(pair.getHigh24h()), 90),
                tableColumn("Low 24h", pair -> pair == null ? "" : marketPrice(pair.getLow24h()), 90),
                tableColumn("Updated", pair -> pair == null ? "" : updatedAt(pair), 95)
        );

        table.setRowFactory(_ -> {
            TableRow<TradePair> row = new TableRow<>() {
                @Override
                protected void updateItem(TradePair item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item != null && item.getChangePercent() != 0) {
                        // Color code positive/negative changes
                        if (item.getChangePercent() > 0) {
                            setStyle("-fx-text-fill: #10b981;");
                        } else if (item.getChangePercent() < 0) {
                            setStyle("-fx-text-fill: #ef4444;");
                        }
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

    private void detachMarketWatch() {
        TableView<TradePair> detachedTable = new TableView<>(marketWatchItems);
        configureMarketWatchTableView(detachedTable);

        Button openButton = new Button("Open");
        openButton.getStyleClass().add("terminal-button");
        openButton.setOnAction(_ -> {
            TradePair selected = detachedTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                symbolSelector.getSelectionModel().select(selected);
                openSelectedSymbolChart();
            }
        });

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("terminal-button");
        refreshButton.setOnAction(_ -> loadSymbolsForSelectedExchange());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, new Label("Market Watch"), symbolCountLabelSnapshot(), spacer, openButton, refreshButton);
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
                tableColumn("Status", order -> safe(order.getStatus()), 90)
        );
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
                        "-fx-faint-focus-color: transparent;"
        );
        accountSummaryArea.setText(
                """
                        ═════════════════════════════════════════════════
                                        ACCOUNT SUMMARY
                        ═════════════════════════════════════════════════

                        Waiting for connection...
                        Select an exchange and click Connect to view account information.

                        ═════════════════════════════════════════════════
                        """
        );
    }

    private @NotNull TableView<Position> buildAccountPositionsTable() {
        TableView<Position> table = new TableView<>(accountPositionItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No broker positions"));
        table.getColumns().setAll(
                tableColumn("Symbol", position -> position.getTradePair() == null ? "" : position.getTradePair().getSymbol(), 110),
                tableColumn("Side", position -> String.valueOf(position.getSide()), 80),
                tableColumn("Units", position -> number(position.getQuantity()), 90),
                tableColumn("Entry", position -> price(position.getEntryPrice()), 90),
                tableColumn("Unrealized P/L", position -> money(position.getUnrealizedPnl()), 120),
                tableColumn("Realized P/L", position -> money(position.getRealizedPnl()), 110),
                tableColumn("Status", position -> position.isOpen() ? "OPEN" : "CLOSED", 80)
        );
        return table;
    }


    private @NotNull TableView<Trade> buildAccountTradesTable() {
        TableView<Trade> table = new TableView<>(accountTradeItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No account trades"));
        table.getColumns().setAll(
                tableColumn("ID", trade -> String.valueOf(trade.getLocalTradeId()), 90),
                tableColumn("Symbol", trade -> trade.getTradePair() == null ? "" : trade.getTradePair().getSymbol(), 110),
                tableColumn("Side", trade -> String.valueOf(trade.getTransactionType()), 70),
                tableColumn("Amount", trade -> number(trade.getAmount()), 90),
                tableColumn("Price", trade -> price(trade.getPrice()), 90),
                tableColumn("Fee", trade -> money(trade.getFee()), 80),
                tableColumn("Time", trade -> String.valueOf(trade.getTimestamp()), 160)
        );
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
                tableColumn("Status", Order::getStatus, 90)
        );
        return table;
    }

    private <T> @NotNull TableColumn<T, String> tableColumn(String title, Function<T, String> mapper, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue() == null ? "" : safe(mapper.apply(cell.getValue()))));
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
                new Label("Status:"), connectionIndicator, connectionStatusLabel,

                new Separator(Orientation.VERTICAL), symbolCountLabel,
                spacer, new Label("Trading Console")
        );
        return statusBar;
    }

    private void updateConnectionStatus() {
        boolean connected = false;
        try {
            connected = exchange != null && Boolean.TRUE.equals(exchange.isConnected());
        } catch (Exception exception) {
            logger.debug("Unable to determine connection status.", exception);
        }
        connectionIndicator.setFill(connected ? Color.LIMEGREEN : Color.ORANGERED);
        connectionStatusLabel.setText(connected ? "Connected" : "Disconnected");

        // Update Connect button appearance based on connection status
        if (connected) {
            connectButton.setText("Connected ✓");
            connectButton.setStyle("-fx-padding: 8 15; -fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
            connectButton.setDisable(false);
        } else {
            connectButton.setText("Connect");
            connectButton.setStyle("-fx-padding: 8 15; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
            connectButton.setDisable(false);
        }

        symbolCountLabel.setText("Symbols: %d".formatted(marketWatchItems.size()));
    }

    private void configureSelectors(MarketConfiguration configuration) {
        exchangeSelector.getItems().setAll(SUPPORTED_EXCHANGES);
        String configuredExchange = configuration == null ? preferences.get("selected_exchange", "") : safe(configuration.exchange());
        if (!configuredExchange.isBlank() && exchangeSelector.getItems().contains(configuredExchange)) {
            exchangeSelector.getSelectionModel().select(configuredExchange);
        } else {
            exchangeSelector.getSelectionModel().selectFirst();
        }

        exchangeSelector.setOnAction(_ -> onExchangeChanged());

        symbolSelector.setOnAction(_ -> {
            TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
            if (selected != null) {
                marketWatchTable.getSelectionModel().select(selected);
                loadOrderBook(selected);
                saveAppState();
            }
        });
    }

    private void configureButtons() {
        refreshSymbolsButton.setOnAction(event -> loadSymbolsForSelectedExchange());
        addChartButton.setOnAction(event -> openSelectedSymbolChart());
        connectButton.setOnAction(event -> connectSelectedExchange());
        buyButton.setOnAction(event -> submitMarketOrder(org.investpro.utils.Side.BUY));
        sellButton.setOnAction(event -> submitMarketOrder(org.investpro.utils.Side.SELL));
        cancelAllButton.setOnAction(event -> cancelAllOrders());
    }

    private void configureChartArea() {
        chartTabPane.setSide(Side.TOP);
        chartTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        chartTabPane.setMinHeight(0);

        DraggableTab.registerTabPane(chartTabPane);
    }

    private void createInitialExchange(MarketConfiguration configuration) {
        exchange = createExchange(
                exchangeSelector.getSelectionModel().getSelectedItem(),
                configuration == null ? configuredApiKey : safe(configuration.apiKey()),
                configuration == null ? configuredApiSecret : safe(configuration.apiSecret())
        );
        setTelegramToken(telegramToken);
        enablePositionAutoRefresh(exchange, 5000);
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

        // Load saved credentials for the selected exchange if they exist
        if (hasExchangeCredentials(selectedExchange)) {
            loadExchangeCredentials(selectedExchange);
        }

        stopActiveStreaming();
        if (smartBot != null) {
            smartBot.stop();
            smartBot = null;
            smartBotEventsSubscribed = false;
        }
        disablePositionAutoRefresh();
        exchange = createExchange(selectedExchange, configuredApiKey, configuredApiSecret);
        setTelegramToken(telegramToken);
        accountPositionItems.clear();
        accountOpenOrderItems.clear();
        accountTradeItems.clear();
        accountHistoryItems.clear();
        accountSummaryArea.clear();
        enablePositionAutoRefresh(exchange, 5000);
        loadSymbolsForSelectedExchange();
        updateConnectionStatus();
        journal("Exchange changed to %s".formatted(selectedExchange));
        saveAppState();

        // If no credentials exist for the new exchange, show credential dialog
        if (!hasExchangeCredentials(selectedExchange)) {
            showExchangeCredentialDialog(selectedExchange);
        }
    }

    private void connectSelectedExchange() {
        String selectedExchange = exchangeSelector.getSelectionModel().getSelectedItem();

        if (selectedExchange == null) {
            showWarning("Connection", "No exchange selected.");
            return;
        }

        // Check if credentials exist for this exchange
        if (!hasExchangeCredentials(selectedExchange)) {
            // Prompt for credentials
            showExchangeCredentialDialog(selectedExchange);
            return;
        }

        // Credentials exist, proceed with connection
        proceedWithConnection();
    }

    private void proceedWithConnection() {
        if (exchange == null) {
            showWarning("Connection", "No exchange selected.");
            return;
        }
        try {
            exchange.connect();
            journal("Connection requested for %s".formatted(exchangeSelector.getValue()));
            refreshAccountWorkspace();
            TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
            if (selected != null) {
                loadOrderBook(selected);
            }
        } catch (Exception exception) {
            logger.error("Unable to connect to exchange {}", exchangeSelector.getValue(), exception);
            showWarning("Connection Failed", "Unable to connect to %s: %s".formatted(exchangeSelector.getValue(), rootMessage(exception)));
        }
        updateConnectionStatus();
    }

    private void submitMarketOrder(org.investpro.utils.Side side) {
        if (exchange == null || !Boolean.TRUE.equals(exchange.isConnected())) {
            showWarning("Order", "Connect to an exchange before submitting orders.");
            return;
        }
        TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Order", "Select a symbol before submitting an order.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("%s Market Order".formatted(side));
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
            exchange.createMarketOrder(selected, side, amount)
                    .thenAccept(orderId -> runOnFx(() -> {
                        journal("%s market order submitted for %s: %s".formatted(side, selected.toString('/'), orderId));
                        refreshAccountWorkspace();
                    }))
                    .exceptionally(exception -> {
                        runOnFx(() -> showWarning("Order Failed", "Order failed: %s".formatted(rootMessage(exception))));
                        return null;
                    });
        });
    }

    private void loadOrderBook(TradePair tradePair) {
        if (exchange == null || !Boolean.TRUE.equals(exchange.isConnected())) {
            return;
        }

        // Fetch orderbook asynchronously
        exchange.fetchOrderBook(tradePair).thenAccept(orderBook -> Platform.runLater(() -> displayOrderBook(orderBook))).exceptionally(exception -> {
            logger.debug("Failed to fetch orderbook for %s".formatted(tradePair), exception);
            return null;
        });
    }

    private void displayOrderBook(OrderBook orderBook) {
        if (orderBook == null) {
            orderBookBids.clear();
            orderBookAsks.clear();
            currentOrderBook = null;
            return;
        }

        currentOrderBook = orderBook;

        // Update bids (reverse order - highest first)
        Platform.runLater(() -> {
            orderBookBids.setAll(orderBook.getBids());
            orderBookAsks.setAll(orderBook.getAsks());
        });
    }

    private void toggleBotTrading() {
        if (exchange == null) {
            showWarning("Bot Trading", "Select an exchange before starting the bot.");
            return;
        }

        if (botTradingEnabled) {
            botTradingEnabled = false;
            if (smartBot != null) {
                smartBot.setAutoTradingEnabled(false);
                smartBot.stopStreaming();
            }
            withActiveChart(chart -> chart.setAutoTradeEnabled(false));
            appendAgentActivity("SmartBot auto trading disabled.");
            refreshBotTradeButton();
            saveAppState();
            return;
        }

        List<TradePair> symbols = selectedBotSymbols();
        if (symbols.isEmpty()) {
            showWarning("Bot Trading", "No symbols are available for the selected bot scope.");
            return;
        }

        try {
            ensureSmartBotStarted(symbols.get(0));
            smartBot.setAutoTradingEnabled(true);
            smartBot.startStreaming(symbols, SmartBot.StreamingMode.EVERYTHING);
            withActiveChart(chart -> chart.setAutoTradeEnabled(true));
            botTradingEnabled = true;
            appendAgentActivity("SmartBot enabled for %d symbol(s) using %s.".formatted(symbols.size(), botSymbolScopeSelector.getValue()));
            refreshBotTradeButton();
            saveAppState();
        } catch (Exception exception) {
            logger.error("Failed to toggle bot trading", exception);
            showWarning("Bot Trading", "Could not start bot trading: %s".formatted(rootMessage(exception)));
        }
    }

    private void ensureSmartBotStarted(TradePair selectedPair) {
        if (smartBot == null || !smartBot.isStarted()) {
            smartBot = new SmartBot();
            smartBot.start(exchange, tradingService, selectedPair, telegramToken);
        }
        if (!smartBotEventsSubscribed) {
            smartBot.getEventBus().subscribeAll(this::displayAgentEvent);
            smartBotEventsSubscribed = true;
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

    private String formatAgentEvent(AgentEvent event) {
        return "[%s] %s | %s | %s".formatted(
                DateTimeFormatter.ofPattern("HH:mm:ss").format(event.timestamp().atZone(java.time.ZoneId.systemDefault())),
                event.type(),
                event.source(),
                event.payload() == null ? "" : event.payload().toString()
        );
    }

    private String formatSignalEvent(AgentEvent event) {
        if (event.payload() instanceof Signal signal) {
            return "[%s] %s %s confidence %.0f%% via %s".formatted(
                    DateTimeFormatter.ofPattern("HH:mm:ss").format(signal.getTimestamp().atZone(java.time.ZoneId.systemDefault())),
                    signal.getTradePair() == null ? "-" : signal.getTradePair().toString('/'),
                    signal.getSide(),
                    signal.getConfidence() * 100.0,
                    signal.getStrategyName()
            );
        }
        return formatAgentEvent(event);
    }

    private void appendAgentActivity(String message) {
        runOnFx(() -> appendBounded(agentActivityItems, "[%s] %s".formatted(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                safe(message)
        ), 300));
    }

    private void appendBounded(ObservableList<String> items, String value, int maxItems) {
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
        if (smartBot != null) {
            try {
                smartBot.stopStreaming();
                if (smartBot.isAutoTradingEnabled()) {
                    smartBot.setAutoTradingEnabled(false);
                }
            } catch (Exception exception) {
                logger.debug("Failed to stop SmartBot streaming", exception);
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
            showWarning("No Symbols", "Unable to fetch or build trade pairs for %s.".formatted(exchangeSelector.getValue()));
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
        if (rememberedPair != null) {
            symbolSelector.getSelectionModel().select(rememberedPair);
            marketWatchTable.getSelectionModel().select(rememberedPair);
        } else {
            symbolSelector.getSelectionModel().selectFirst();
        }
        journal("Loaded %d symbols from %s".formatted(tradePairs.size(), exchangeSelector.getValue()));
        updateConnectionStatus();
        hydrateMarketWatchTickers();
        saveAppState();
    }

    private List<TradePair> fallbackMarketWatchPairs() {
        String[][] defaults = {
                {"BTC", "USD"},
                {"ETH", "USD"},
                {"SOL", "USD"},
                {"XRP", "USD"},
                {"ADA", "USD"},
                {"DOGE", "USD"},
                {"BTC", "USDT"},
                {"ETH", "USDT"},
                {"BNB", "USDT"},
                {"EUR", "USD"}
        };

        List<TradePair> pairs = new ArrayList<>();
        for (String[] symbol : defaults) {
            try {
                pairs.add(new TradePair(
                        new CryptoCurrency(symbol[0], symbol[0], symbol[0], 8, symbol[0], symbol[0]),
                        new CryptoCurrency(symbol[1], symbol[1], symbol[1], 8, symbol[1], symbol[1])
                ));
            } catch (Exception exception) {
                logger.debug("Skipping fallback market-watch pair {}/{}", symbol[0], symbol[1], exception);
            }
        }

        return pairs;
    }

    private void openInitialChartIfAvailable() {
        if (!symbolSelector.getItems().isEmpty()) {
            openSelectedSymbolChart();
        }
    }

    private void refreshPositions() {
        if (exchange == null) {
            showInfo("Info", "Please connect to an exchange first.");
            return;
        }

        // Fetch all positions including both open and closed
        exchange.fetchOrderHistory(null, null)
                .thenAccept(allOrders -> runOnFx(() -> {
                    if (allOrders != null) {
                        positionsItems.setAll(allOrders);
                        calculatePnL(positionsItems);
                        journal("Loaded %d total positions (open and closed).".formatted(positionsItems.size()));
                    } else {
                        positionsItems.clear();
                        journal("No positions found.");
                    }
                }))
                .exceptionally(exception -> {
                    logger.error("Failed to load all positions", exception);
                    runOnFx(() -> showWarning("Positions", "Failed to load positions: %s".formatted(rootMessage(exception))));
                    return null;
                });
    }

    private void closeAllPositions() {
        if (exchange == null) {
            showInfo("Info", "Please connect to an exchange first.");
            return;
        }
        exchange.closeAllPositions()
                .thenAccept(result -> runOnFx(() -> {
                    journal("Close-all positions submitted: " + result);
                    refreshPositions();
                    refreshAccountWorkspace();
                }))
                .exceptionally(exception -> {
                    runOnFx(() -> showWarning("Close All", "Close-all failed: %s".formatted(rootMessage(exception))));
                    return null;
                });
    }

    private void refreshAccountWorkspace() {
        if (exchange == null) {
            showInfo("Info", "Please connect to an exchange first.");
            return;
        }
        TradePair selectedPair = symbolSelector.getSelectionModel().getSelectedItem();
        accountSummaryArea.setText("Loading account data...");
        journal("Refreshing broker account tabs.");

        exchange.fetchAccount()
                .thenAccept(summary -> runOnFx(() -> accountSummaryArea.setText(summary.toString())))
                .exceptionally(exception -> {
                    runOnFx(() -> accountSummaryArea.setText("Account summary failed: %s".formatted(rootMessage(exception))));
                    return null;
                });

        exchange.fetchAllPositions()
                .thenAccept(this::updatePositionsFromStream)
                .exceptionally(exception -> {
                    logger.warn("Broker positions refresh failed", exception);
                    runOnFx(() -> journal("Broker positions refresh failed: %s".formatted(rootMessage(exception))));
                    return null;
                });

        exchange.fetchAllOpenOrders()
                .thenAccept(openOrders -> runOnFx(() -> {
                    accountOpenOrderItems.setAll(openOrders == null ? Collections.emptyList() : openOrders);
                    journal("Loaded %d broker open orders.".formatted(accountOpenOrderItems.size()));
                }))
                .exceptionally(exception -> {
                    logger.warn("Broker open orders refresh failed", exception);
                    runOnFx(() -> journal("Broker open orders refresh failed: %s".formatted(rootMessage(exception))));
                    return null;
                });

        // Load all order history (not specific to a trading pair)
        exchange.fetchOrderHistory(null, null)
                .thenAccept(history -> runOnFx(() -> {
                    accountHistoryItems.setAll(history == null ? Collections.emptyList() : history);
                    journal("Loaded %d order history items.".formatted(accountHistoryItems.size()));
                }))
                .exceptionally(exception -> {
                    logger.warn("Account history refresh failed", exception);
                    runOnFx(() -> journal("Account history refresh failed: %s".formatted(rootMessage(exception))));
                    return null;
                });

        if (selectedPair != null) {
            exchange.fetchAccountTrades(selectedPair)
                    .thenAccept(trades -> runOnFx(() -> {
                        accountTradeItems.setAll(trades == null ? Collections.emptyList() : trades);
                        journal("Loaded %d account trades.".formatted(accountTradeItems.size()));
                    }))
                    .exceptionally(exception -> {
                        logger.warn("Account trades refresh failed", exception);
                        runOnFx(() -> journal("Account trades refresh failed: %s".formatted(rootMessage(exception))));
                        return null;
                    });
        }
    }


    private void calculatePnL(List<Order> positions) {
        double longPnL = 0.0;
        double shortPnL = 0.0;
        if (positions != null) {
            for (Order position : positions) {
                if (position == null) {
                    continue;
                }
                if ("BUY".equalsIgnoreCase(position.getType())) {
                    longPnL += position.getProfit();
                } else if ("SELL".equalsIgnoreCase(position.getType())) {
                    shortPnL += position.getProfit();
                }
            }
        }
        updatePnLLabels(longPnL, shortPnL);
    }

    private void calculateBrokerPnL(List<Position> positions) {
        double longPnL = 0.0;
        double shortPnL = 0.0;
        if (positions != null) {
            for (Position position : positions) {
                if (position == null) {
                    continue;
                }
                double pnl = position.getUnrealizedPnl() + position.getRealizedPnl();
                String side = String.valueOf(position.getSide());
                if ("BUY".equalsIgnoreCase(side) || "LONG".equalsIgnoreCase(side)) {
                    longPnL += pnl;
                } else if ("SELL".equalsIgnoreCase(side) || "SHORT".equalsIgnoreCase(side)) {
                    shortPnL += pnl;
                }
            }
        }
        updatePnLLabels(longPnL, shortPnL);
    }

    private void updatePnLLabels(double longPnL, double shortPnL) {
        double totalPnL = longPnL + shortPnL;
        longPnLLabel.setText("Long P&L: $" + money(longPnL));
        shortPnLLabel.setText("Short P&L: $" + money(shortPnL));
        totalPnLLabel.setText("Total P&L: $" + money(totalPnL));
        longPnLLabel.setStyle(longPnL >= 0 ? greenStyle() : redStyle());
        shortPnLLabel.setStyle(shortPnL >= 0 ? greenStyle() : redStyle());
        totalPnLLabel.setStyle(totalPnL >= 0 ? greenStyle() : redStyle());
    }

    private String greenStyle() {
        return "-fx-text-fill: #22c55e; -fx-font-weight: bold;";
    }

    private String redStyle() {
        return "-fx-text-fill: #ef4444; -fx-font-weight: bold;";
    }

    private void openSelectedFromMarketWatch() {
        TradePair selected = marketWatchTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("No Symbol Selected", "Please select a symbol from Market Watch.");
            return;
        }
        symbolSelector.getSelectionModel().select(selected);
        openSelectedSymbolChart();
    }

    private void openSelectedSymbolChart() {
        TradePair symbol = symbolSelector.getSelectionModel().getSelectedItem();
        if (symbol == null) {
            showWarning("No Symbol Selected", "Please select a trade pair first.");
            return;
        }
        if (exchange == null) {
            showWarning("No Exchange", "Please select an exchange first.");
            return;
        }
        String tabTitle = exchangeSelector.getValue() + " - " + symbol.toString('/');
        for (Tab tab : chartTabPane.getTabs()) {
            String existingTitle = tab instanceof DraggableTab draggableTab ? draggableTab.getTitle() : tab.getText();
            if (Objects.equals(existingTitle, tabTitle)) {
                chartTabPane.getSelectionModel().select(tab);
                return;
            }
        }
        CandleStickChartDisplay chartDisplay = new CandleStickChartDisplay(symbol, exchange, telegramToken, tradingService);

        // Configure chart for live data display with visible axes
        CandleStickChart chart = chartDisplay.getChart();
        if (chart != null) {
            // Fit chart to display all data
            chart.fitChart();
            // Jump to latest candle
            chart.jumpToLatestCandle();
            // Apply adaptive scaling for better visualization
            chart.applyAdaptiveScaling();
        }


        BorderPane content = new BorderPane(chartDisplay);
        content.setPadding(new Insets(4));
        content.setMinHeight(300);
        content.setPrefHeight(500);

        DraggableTab chartTab = new DraggableTab(tabTitle, content);
        chartTab.setClosable(true);
        chartTab.setOnClosed(event -> disposeChartContent(content));
        chartTabPane.getTabs().add(chartTab);
        chartTabPane.getSelectionModel().select(chartTab);
        journal("Opened chart: %s".formatted(tabTitle));
    }

    private void closeAllCharts() {
        for (Tab tab : List.copyOf(chartTabPane.getTabs())) {
            disposeChartContent(tab.getContent());
        }
        chartTabPane.getTabs().clear();
        journal("All charts closed.");
    }

    private void withActiveChart(java.util.function.Consumer<CandleStickChart> action) {
        CandleStickChart chart = getActiveChart();
        if (chart == null) {
            showWarning("Chart", "Open or select a chart first.");
            return;
        }
        action.accept(chart);
    }

    private void withFocusedTextInput(java.util.function.Consumer<TextInputControl> action) {
        if (getScene() != null && getScene().getFocusOwner() instanceof TextInputControl input) {
            action.accept(input);
        }
    }

    private @Nullable CandleStickChart getActiveChart() {
        Tab selectedTab = chartTabPane.getSelectionModel().getSelectedItem();
        return selectedTab == null ? null : findChart(selectedTab.getContent());
    }

    private CandleStickChart findChart(Node node) {
        if (node instanceof CandleStickChart chart) {
            return chart;
        }
        if (node instanceof CandleStickChartDisplay display) {
            return display.getChart();
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                CandleStickChart chart = findChart(child);
                if (chart != null) {
                    return chart;
                }
            }
        }
        return null;
    }

    private void disposeChartContent(Node node) {
        CandleStickChart chart = findChart(node);
        if (chart != null) {
            chart.dispose();
        }
    }

    private void saveActiveChartSnapshot() {
         selectedTab = chartTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == null || selectedTab.getContent() == null) {
            showWarning("Save Chart", "Open or select a chart first.");
            return;
        }
        try {
            File directory = new File("snapshots");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Unable to create snapshots directory.");
            }
            String name = "chart-%s.png".formatted(SNAPSHOT_FORMAT.format(LocalDateTime.now()));
            File file = new File(directory, name);
            WritableImage image = selectedTab.getContent().snapshot(null, null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            journal("Saved chart snapshot: %s".formatted(file.getAbsolutePath()));
            showInfo("Chart Saved", "Snapshot saved to:\n%s".formatted(file.getAbsolutePath()));
        } catch (Exception exception) {
            logger.error("Failed to save chart snapshot", exception);
            showWarning("Save Chart", "Failed to save chart: %s".formatted(rootMessage(exception)));
        }
    }

    public void appendJournal(String message) {
        runOnFx(() -> journal(message));
    }

    public void updateStreamingStatus(String message) {
        runOnFx(() -> connectionStatusLabel.setText(safe(message)));
    }

    public void updateTickerFromStream(TradePair pair, Ticker ticker) {
        if (pair == null || ticker == null) {
            return;
        }
        runOnFx(() -> {
            updateMarketWatchItem(pair, ticker);
            journal("Tick %s bid %s ask %s".formatted(pair.toString('/'), price(ticker.getBidPrice()), price(ticker.getAskPrice())));
        });
    }

    public void updatePositionsFromStream(List<Position> positions) {
        List<Position> safePositions = positions == null ? Collections.emptyList() : positions;
        runOnFx(() -> {
            positionsDataManager.updateFromStream(safePositions);
            accountPositionItems.setAll(safePositions);
            calculateBrokerPnL(safePositions);
            journal("Positions update: %d broker positions.".formatted(safePositions.size()));
        });
    }

    public void updateOpenOrdersFromStream(List<OpenOrder> orders) {
        List<OpenOrder> safeOrders = orders == null ? Collections.emptyList() : orders;
        runOnFx(() -> {
            accountOpenOrderItems.setAll(safeOrders);
            journal("Open orders update: %d broker orders.".formatted(safeOrders.size()));
        });
    }

    /**
     * Starts automatic data refresh tasks for market watch, orderbook, and account data.
     */
    private void startAutoRefreshTasks() {
        // Refresh market watch every 5 seconds
        autoRefreshExecutor.scheduleAtFixedRate(
                this::autoRefreshMarketWatch,
                2,      // Initial delay
                5,      // Interval
                TimeUnit.SECONDS
        );

        // Refresh orderbook every 3 seconds
        autoRefreshExecutor.scheduleAtFixedRate(
                this::autoRefreshOrderBook,
                1,      // Initial delay
                3,      // Interval
                TimeUnit.SECONDS
        );

        // Refresh account summary every 4 seconds
        autoRefreshExecutor.scheduleAtFixedRate(
                this::autoRefreshAccountData,
                1,      // Initial delay
                4,      // Interval
                TimeUnit.SECONDS
        );
    }

    /**
     * Auto-refresh market watch with latest ticker data for selected symbol.
     */
    private void autoRefreshMarketWatch() {
        try {
            if (exchange == null) {
                return;
            }

            hydrateMarketWatchTickers();
        } catch (Exception e) {
            logger.debug("Market watch auto-refresh failed", e);
        }
    }

    /**
     * Update a specific market watch item with new ticker data.
     */
    private void updateMarketWatchItem(TradePair pair, Ticker ticker) {
        if (pair == null || ticker == null) {
            return;
        }

        TradePair marketPair = findMarketWatchPair(pair);
        if (marketPair == null) {
            return;
        }

        applyTickerToTradePair(marketPair, ticker);
        int index = marketWatchItems.indexOf(marketPair);
        if (index >= 0) {
            marketWatchItems.set(index, marketPair);
            marketWatchTable.refresh();
        }
    }

    private void hydrateMarketWatchTickers() {
        if (!Platform.isFxApplicationThread()) {
            runOnFx(this::hydrateMarketWatchTickers);
            return;
        }

        if (exchange == null || marketWatchItems.isEmpty()) {
            return;
        }

        List<TradePair> pairs = marketWatchPairsToRefresh();
        if (pairs.isEmpty()) {
            return;
        }

        CompletableFuture<List<Ticker>> batchFuture = safeFetchTickers(pairs);
        batchFuture.thenAccept(tickers -> runOnFx(() -> applyTickerBatch(pairs, tickers)))
                .exceptionally(exception -> {
                    logger.debug("Batch ticker refresh failed", exception);
                    refreshTickersIndividually(pairs);
                    return null;
                });
    }

    private List<TradePair> marketWatchPairsToRefresh() {
        TradePair selectedPair = symbolSelector.getSelectionModel().getSelectedItem();
        List<TradePair> pairs = new java.util.ArrayList<>();

        if (selectedPair != null) {
            TradePair marketPair = findMarketWatchPair(selectedPair);
            if (marketPair != null) {
                pairs.add(marketPair);
            }
        }

        for (TradePair pair : marketWatchItems) {
            if (pair == null || pairs.contains(pair)) {
                continue;
            }
            pairs.add(pair);
            if (pairs.size() >= MARKET_WATCH_TICKER_REFRESH_LIMIT) {
                break;
            }
        }

        return pairs;
    }

    private CompletableFuture<List<Ticker>> safeFetchTickers(List<TradePair> pairs) {
        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return exchange.fetchTickers(pairs);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                }, autoRefreshExecutor)
                .thenCompose(future -> Objects.requireNonNullElseGet(future, () -> CompletableFuture.failedFuture(new UnsupportedOperationException("Batch ticker refresh is not supported."))))
                .thenApply(tickers -> {
                    if (tickers == null) {
                        throw new IllegalStateException("Batch ticker refresh returned no data.");
                    }
                    return tickers;
                });
    }

    private void refreshTickersIndividually(List<TradePair> pairs) {
        for (TradePair pair : pairs) {
            CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return exchange.fetchTicker(pair);
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    }, autoRefreshExecutor)
                    .thenCompose(future -> Objects.requireNonNullElseGet(future, () -> CompletableFuture.failedFuture(new UnsupportedOperationException("Ticker refresh is not supported."))))
                    .thenAccept(ticker -> runOnFx(() -> updateMarketWatchItem(pair, ticker)))
                    .exceptionally(exception -> {
                        logger.debug("Failed to fetch ticker for %s".formatted(pair), exception);
                        return null;
                    });
        }
    }

    private void applyTickerBatch(List<TradePair> pairs, List<Ticker> tickers) {
        if (pairs == null || tickers == null || tickers.isEmpty()) {
            return;
        }

        int count = Math.min(pairs.size(), tickers.size());
        for (int i = 0; i < count; i++) {
            TradePair pair = pairs.get(i);
            Ticker ticker = tickers.get(i);
            TradePair marketPair = findMarketWatchPair(pair);
            if (marketPair != null && ticker != null) {
                applyTickerToTradePair(marketPair, ticker);
            }
        }

        marketWatchTable.refresh();
    }

    private void applyTickerToTradePair(TradePair pair, Ticker ticker) {
        double last = ticker.getLastPrice() > 0 ? ticker.getLastPrice() : ticker.getMidPrice();
        pair.updateTicker(
                ticker.getBidPrice(),
                ticker.getAskPrice(),
                last,
                ticker.getVolume(),
                ticker.getChangePercent(),
                ticker.getHighPrice(),
                ticker.getLowPrice()
        );
    }

    private @Nullable TradePair findMarketWatchPair(TradePair pair) {
        if (pair == null) {
            return null;
        }

        for (TradePair candidate : marketWatchItems) {
            if (sameTradePair(candidate, pair)) {
                return candidate;
            }
        }

        return null;
    }

    private boolean sameTradePair(TradePair left, TradePair right) {
        return left != null
                && right != null
                && Objects.equals(left.getBaseCode(), right.getBaseCode())
                && Objects.equals(left.getCounterCode(), right.getCounterCode());
    }

    /**
     * Auto-refresh orderbook data for the selected symbol.
     */
    private void autoRefreshOrderBook() {
        try {
            if (exchange == null || !Boolean.TRUE.equals(exchange.isConnected())) {
                return;
            }

            TradePair selectedPair = symbolSelector.getSelectionModel().getSelectedItem();
            if (selectedPair == null) {
                return;
            }

            CompletableFuture<OrderBook> orderBookFuture = exchange.fetchOrderBook(selectedPair);
            if (orderBookFuture != null) {
                orderBookFuture.thenAccept(orderBook -> {
                    if (orderBook instanceof OrderBook ob) {
                        Platform.runLater(() -> displayOrderBook(ob));
                    }
                });
            }
        } catch (Exception e) {
            logger.debug("OrderBook auto-refresh failed", e);
        }
    }

    /**
     * Auto-refresh account summary data (positions, balances, P&L).
     */
    private void autoRefreshAccountData() {
        try {
            if (exchange == null || !Boolean.TRUE.equals(exchange.isConnected())) {
                return;
            }

            // Update account positions
            try {
                if (exchange.supportsPositions()) {
                    CompletableFuture<List<Position>> positionsFuture = exchange.fetchAllPositions();
                    if (positionsFuture != null) {
                        positionsFuture.thenAccept(positions -> {
                            if (positions != null) {
                                Platform.runLater(() -> updatePositionsFromStream(positions));
                            }
                        });
                    }
                }
            } catch (Exception e) {
                logger.debug("Failed to fetch positions", e);
            }

            // Update account info
            try {
                CompletableFuture<Account> accountFuture = exchange.fetchAccount();
                if (accountFuture != null) {
                    accountFuture.thenAccept(info -> {
                        if (info != null) {
                            Platform.runLater(() -> updateAccountSummary(info));
                        }
                    }).exceptionally(ex -> {
                        logger.debug("Failed to fetch account info from future", ex);
                        return null;
                    });
                }
            } catch (Exception e) {
                logger.debug("Failed to fetch account info", e);
            }
        } catch (Exception e) {
            logger.debug("Account data auto-refresh failed", e);
        }
    }

    /**
     * Update account summary text area with latest data, formatted nicely.
     */
    private void updateAccountSummary(Account account) {
        Platform.runLater(() -> {
            if (account != null) {
                String formatted = formatAccountSummary(account);
                accountSummaryArea.setText(formatted);
            }
        });
    }

    /**
     * Format account data into a readable display format.
     */
    private String formatAccountSummary(Account account) {
        StringBuilder sb = new StringBuilder();
        sb.append("═════════════════════════════════════════════════\n");
        sb.append("                ACCOUNT SUMMARY\n");
        sb.append("═════════════════════════════════════════════════\n\n");

        // User Info
        if (account.getUsername() != null && !account.getUsername().isBlank()) {
            sb.append("User Information:\n");
            sb.append("  • Username: ").append(account.getUsername()).append("\n");
            if (account.getFirstName() != null) {
                sb.append("  • Name: ").append(account.getFirstName());
                if (account.getLastName() != null) {
                    sb.append(" ").append(account.getLastName());
                }
                sb.append("\n");
            }
            if (account.getEmail() != null) {
                sb.append("  • Email: ").append(account.getEmail()).append("\n");
            }
            sb.append("\n");
        }

        // Account Details
        sb.append("Account Details:\n");
        if (account.getExchange() != null) {
            sb.append("  • Exchange: ").append(account.getExchange().getName()).append("\n");
        }
        if (account.getAccount() != null && !account.getAccount().isBlank()) {
            sb.append("  • Account ID: ").append(account.getAccount()).append("\n");
        }

        // Contact Info
        if ((account.getPhone() != null && !account.getPhone().isBlank()) ||
                (account.getAddress() != null && !account.getAddress().isBlank()) ||
                (account.getCity() != null && !account.getCity().isBlank())) {
            sb.append("\nContact Information:\n");
            if (account.getPhone() != null && !account.getPhone().isBlank()) {
                sb.append("  • Phone: ").append(account.getPhone()).append("\n");
            }
            if (account.getAddress() != null && !account.getAddress().isBlank()) {
                sb.append("  • Address: ").append(account.getAddress()).append("\n");
            }
            if (account.getCity() != null && !account.getCity().isBlank()) {
                sb.append("  • City: ").append(account.getCity()).append("\n");
            }
        }

        // Notification Settings
        if ((account.getTelegramToken() != null && !account.getTelegramToken().isBlank()) ||
                (account.getEmailNotification() != null && !account.getEmailNotification().isBlank())) {
            sb.append("\nNotification Settings:\n");
            if (account.getTelegramToken() != null && !account.getTelegramToken().isBlank()) {
                sb.append("  • Telegram: Configured ✓\n");
            }
            if (account.getEmailNotification() != null && !account.getEmailNotification().isBlank()) {
                sb.append("  • Email: ").append(account.getEmailNotification()).append("\n");
            }
        }

        sb.append("\n═════════════════════════════════════════════════\n");
        sb.append("Last Updated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");

        return sb.toString();
    }

    public void enablePositionAutoRefresh(Exchange exchange, long intervalMs) {
        if (exchange == null || intervalMs <= 0) {
            return;
        }
        positionsDataManager.startAutoRefresh(exchange, intervalMs);
        journal("Position auto-refresh enabled: %s seconds.".formatted(intervalMs / 1000.0));
    }

    public void disablePositionAutoRefresh() {
        try {
            positionsDataManager.stopAutoRefresh();
        } catch (Exception exception) {
            logger.debug("Failed to stop position auto-refresh", exception);
        }
    }


    private void cancelAllOrders() {
        journal("Cancel-all requested.");
        if (exchange == null) {
            showWarning("Cancel All", "No exchange selected.");
            return;
        }
        try {
            exchange.cancelALL();
            journal("Cancel-all sent to exchange.");
            refreshAccountWorkspace();
        } catch (Exception exception) {
            logger.error("Cancel-all failed", exception);
            showWarning("Cancel All", "Cancel-all failed: %s".formatted(rootMessage(exception)));
        }
    }

    private void openInSystemBrowser(String url) throws Exception {
        String target = safe(url);
        if (target.isBlank()) {
            showInfo("Browser", "No URL to open.");
            return;
        }
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            target = "https://%s".formatted(target);
        }
        if (!Desktop.isDesktopSupported()) {
            throw new UnsupportedOperationException("Desktop API is not supported on this system.");
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            throw new UnsupportedOperationException("Desktop browse action is not supported.");
        }
        desktop.browse(URI.create(target));
        journal("Opened URL in browser: %s".formatted(target));
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

    private void showSettingsDialog() {
        Stage settingsStage = new Stage();
        settingsStage.setTitle("InvestPro Settings");
        if (getScene() != null && getScene().getWindow() != null) {
            settingsStage.initOwner(getScene().getWindow());
        }
        settingsStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        ComboBox<String> exchangeBox = new ComboBox<>();
        exchangeBox.getItems().setAll(SUPPORTED_EXCHANGES);
        exchangeBox.getSelectionModel().select(safe(exchangeSelector.getValue()).isBlank() ? "COINBASE" : exchangeSelector.getValue());

        TextField apiKeyField = new TextField();
        PasswordField apiSecretField = new PasswordField();
        TextField accountIdField = new TextField();
        TextField venueField = new TextField();
        TextField telegramTokenField = new TextField();
        CheckBox restoreConsoleCheck = new CheckBox("Remember console visibility");
        restoreConsoleCheck.setSelected(preferences.getBoolean("remember_console_visible", true));

        Runnable loadFields = () -> {
            String name = safe(exchangeBox.getValue());
            apiKeyField.setText(preferences.get("exchange_api_key_" + name, ""));
            apiSecretField.setText(preferences.get("exchange_api_secret_" + name, ""));
            accountIdField.setText(preferences.get("exchange_account_id_" + name, ""));
            venueField.setText(preferences.get("exchange_venue_" + name, ""));
            telegramTokenField.setText(preferences.get("telegram_token_" + name, ""));
        };
        exchangeBox.setOnAction(event -> loadFields.run());
        loadFields.run();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Exchange"), exchangeBox);
        grid.addRow(1, new Label("API Key"), apiKeyField);
        grid.addRow(2, new Label("API Secret"), apiSecretField);
        grid.addRow(3, new Label("Account ID"), accountIdField);
        grid.addRow(4, new Label("Venue"), venueField);
        grid.addRow(5, new Label("Telegram Token"), telegramTokenField);
        grid.add(restoreConsoleCheck, 1, 6);

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().add("primary-button");
        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add("terminal-button");
        HBox actions = new HBox(8, closeButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        grid.add(actions, 1, 7);

        saveButton.setOnAction(event -> {
            String name = safe(exchangeBox.getValue());
            preferences.put("exchange_api_key_" + name, safe(apiKeyField.getText()));
            preferences.put("exchange_api_secret_" + name, safe(apiSecretField.getText()));
            preferences.put("exchange_account_id_" + name, safe(accountIdField.getText()));
            preferences.put("exchange_venue_" + name, safe(venueField.getText()));
            preferences.put("telegram_token_" + name, safe(telegramTokenField.getText()));
            preferences.putBoolean("remember_console_visible", restoreConsoleCheck.isSelected());
            try {
                preferences.sync();
            } catch (Exception exception) {
                logger.warn("Failed to sync settings", exception);
            }
            if (Objects.equals(name, exchangeSelector.getValue())) {
                loadExchangeCredentials(name);
            }
            saveAppState();
            journal("Settings saved for %s".formatted(name));
        });
        closeButton.setOnAction(event -> settingsStage.close());

        settingsStage.setScene(new Scene(grid, 520, 360));
        settingsStage.show();
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

    @Contract(pure = true)
    private @NotNull String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private @NotNull String number(double value) {
        return Double.isFinite(value) ? String.format("%.4f", value) : "0.0000";
    }

    private @NotNull String price(double value) {
        return Double.isFinite(value) ? String.format("%.5f", value) : "0.00000";
    }

    private @NotNull String marketPrice(double value) {
        return Double.isFinite(value) && value > 0.0 ? price(value) : "-";
    }

    private @NotNull String compactMarketNumber(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return "-";
        }

        double abs = Math.abs(value);
        if (abs >= 1_000_000_000.0) {
            return "%.2fB".formatted(value / 1_000_000_000.0);
        }
        if (abs >= 1_000_000.0) {
            return "%.2fM".formatted(value / 1_000_000.0);
        }
        if (abs >= 1_000.0) {
            return "%.2fK".formatted(value / 1_000.0);
        }
        return number(value);
    }

    private @NotNull String percent(double value) {
        if (!Double.isFinite(value) || value == 0.0) {
            return "-";
        }
        return "%+.2f%%".formatted(value);
    }

    private @NotNull String updatedAt(TradePair pair) {
        if (pair == null || pair.getUpdatedAt() == null || !pair.hasQuote()) {
            return "-";
        }
        return pair.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private @NotNull String money(double value) {
        return Double.isFinite(value) ? String.format("%.2f", value) : "0.00";
    }

    private @NotNull String rootMessage(Throwable throwable) {
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

    private void journal(String message) {
        String line = "[%s] %s%n".formatted(new Date(), message == null ? "" : message);
        if (Platform.isFxApplicationThread()) {
            journalArea.appendText(line);
        } else {
            Platform.runLater(() -> journalArea.appendText(line));
        }
        logger.info(message == null ? "" : message);
    }

    private void showInfo(String title, String content) {
        runOnFx(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void showWarning(String title, String content) {
        runOnFx(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    /**
     * Check if saved credentials exist for the given exchange.
     */
    private boolean hasExchangeCredentials(String exchange) {
        if (exchange == null || exchange.isBlank()) {
            return false;
        }

        try {
            String savedApiKey = preferences.get("exchange_api_key_%s".formatted(exchange), "");
            return !savedApiKey.isEmpty();
        } catch (Exception e) {
            logger.warn("Failed to check credentials for exchange: %s".formatted(exchange), e);
            return false;
        }
    }

    /**
     * Load saved credentials for the selected exchange from preferences.
     * If no saved credentials exist, keeps current values.
     */
    private void loadExchangeCredentials(String exchange) {
        if (exchange == null || exchange.isBlank()) {
            return;
        }

        try {
            String savedApiKey = preferences.get("exchange_api_key_%s".formatted(exchange), "");
            String savedApiSecret = preferences.get("exchange_api_secret_%s".formatted(exchange), "");
            String savedTelegramToken = preferences.get("telegram_token_%s".formatted(exchange), "");

            if (!savedApiKey.isEmpty()) {
                configuredApiKey = savedApiKey;
                journal("Loaded API Key for %s".formatted(exchange));
            }
            if (!savedApiSecret.isEmpty()) {
                configuredApiSecret = savedApiSecret;
                journal("Loaded API Secret for %s".formatted(exchange));
            }
            if (!savedTelegramToken.isEmpty()) {
                telegramToken = savedTelegramToken;
                journal("Loaded Telegram Token for %s".formatted(exchange));
            }
        } catch (Exception e) {
            logger.warn("Failed to load credentials for exchange: %s".formatted(exchange), e);
        }
    }

    /**
     * Show credential input dialog for the selected exchange.
     * Saves credentials to preferences and connects after user input.
     */
    private void showExchangeCredentialDialog(String exchange) {
        // Create a popup Stage window (400x400)
        Stage credentialStage = new Stage();
        credentialStage.setTitle("Enter Credentials - " + exchange);
        credentialStage.setWidth(400);
        credentialStage.setHeight(400);
        credentialStage.setResizable(false);
        credentialStage.initOwner(this.getScene().getWindow());
        credentialStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        credentialStage.setOnCloseRequest(e -> journal("Credential input cancelled for %s".formatted(exchange)));

        // Create credential input fields
        TextField apiKeyField = new TextField();
        apiKeyField.setPromptText("API Key");
        apiKeyField.setText(preferences.get("exchange_api_key_" + exchange, ""));
        apiKeyField.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        PasswordField apiSecretField = new PasswordField();
        apiSecretField.setPromptText("API Secret");
        apiSecretField.setText(preferences.get("exchange_api_secret_" + exchange, ""));
        apiSecretField.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        ComboBox<String> venueBox = new ComboBox<>();
        venueBox.getItems().setAll("US", "Global", "Spot", "Derivatives", "Paper Trading");
        String savedVenue = preferences.get("exchange_venue_" + exchange, "US");
        venueBox.getSelectionModel().select(venueBox.getItems().contains(savedVenue) ? savedVenue : "US");
        venueBox.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");
        venueBox.setPrefWidth(300);

        TextField accountIdField = new TextField();
        accountIdField.setPromptText("Account ID (optional)");
        accountIdField.setText(preferences.get("exchange_account_id_" + exchange, ""));
        accountIdField.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        TextField telegramTokenField = new TextField();
        telegramTokenField.setPromptText("Telegram Bot Token (optional)");
        telegramTokenField.setText(preferences.get("telegram_token_" + exchange, ""));
        telegramTokenField.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        Label messageLabel = new Label();
        messageLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
        messageLabel.setWrapText(true);

        // Create dialog content
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(12);
        gridPane.setPadding(new Insets(15));
        gridPane.setStyle("-fx-background-color: #0f172a;");

        Label titleLabel = new Label("Credentials: %s".formatted(exchange));
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");
        gridPane.add(titleLabel, 0, 0, 2, 1);

        gridPane.add(new Label("API Key"), 0, 1);
        gridPane.add(apiKeyField, 1, 1);
        apiKeyField.setPrefWidth(220);

        gridPane.add(new Label("API Secret"), 0, 2);
        gridPane.add(apiSecretField, 1, 2);
        apiSecretField.setPrefWidth(220);

        gridPane.add(new Label("Venue"), 0, 3);
        gridPane.add(venueBox, 1, 3);

        gridPane.add(new Label("Account ID"), 0, 4);
        gridPane.add(accountIdField, 1, 4);
        accountIdField.setPrefWidth(220);

        gridPane.add(new Label("Telegram"), 0, 5);
        gridPane.add(telegramTokenField, 1, 5);
        telegramTokenField.setPrefWidth(220);

        gridPane.add(messageLabel, 0, 6, 2, 1);

        // Create buttons
        Button saveButton = new Button("Save & Connect");
        saveButton.setStyle("-fx-padding: 8 15; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold;");
        saveButton.setPrefWidth(95);

        Button cancelButton = new Button("Cancel");
        cancelButton.setStyle("-fx-padding: 8 15; -fx-background-color: #1e40af; -fx-text-fill: white;");
        cancelButton.setPrefWidth(95);

        HBox buttonBox = new HBox(10, cancelButton, saveButton);
        buttonBox.setAlignment(Pos.CENTER);
        GridPane.setConstraints(buttonBox, 0, 7, 2, 1);
        gridPane.getChildren().add(buttonBox);

        // Handle button actions
        saveButton.setOnAction(event -> {
            if (apiKeyField.getText().isBlank() || apiSecretField.getText().isBlank()) {
                messageLabel.setText("API Key and Secret are required.");
                return;
            }

            // Save credentials to preferences
            try {
                preferences.put("exchange_api_key_" + exchange, apiKeyField.getText().trim());
                preferences.put("exchange_api_secret_" + exchange, apiSecretField.getText().trim());
                preferences.put("exchange_account_id_" + exchange, accountIdField.getText().trim());
                preferences.put("exchange_venue_" + exchange, safe(venueBox.getValue()));
                if (!telegramTokenField.getText().isBlank()) {
                    preferences.put("telegram_token_" + exchange, telegramTokenField.getText().trim());
                }
                preferences.sync();

                // Load the saved credentials
                loadExchangeCredentials(exchange);

                // Close dialog and proceed with connection
                credentialStage.close();
                journal("Credentials saved for " + exchange);
                proceedWithConnection();

            } catch (Exception e) {
                logger.error("Failed to save credentials", e);
                messageLabel.setText("Error: " + rootMessage(e));
            }
        });

        cancelButton.setOnAction(event -> {
            credentialStage.close();
            journal("Credential input cancelled for " + exchange);
        });

        ScrollPane scrollPane = new ScrollPane(gridPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #0f172a; -fx-control-inner-background: #0f172a;");

        Scene scene = new Scene(scrollPane);
        credentialStage.setScene(scene);
        credentialStage.show();
    }

    public void shutdown() {
        journal("Shutting down TradingWindow.");
        stopActiveStreaming();
        if (smartBot != null) {
            try {
                smartBot.stop();
            } catch (Exception exception) {
                logger.debug("Failed to stop SmartBot", exception);
            }
        }
        disablePositionAutoRefresh();
        closeAllCharts();

        // Stop auto-refresh tasks
        try {
            autoRefreshExecutor.shutdown();
            if (!autoRefreshExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                autoRefreshExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            autoRefreshExecutor.shutdownNow();
            logger.debug("Auto-refresh executor interrupted during shutdown", e);
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
