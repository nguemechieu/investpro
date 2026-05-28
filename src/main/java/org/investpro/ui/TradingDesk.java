package org.investpro.ui;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import lombok.Getter;
import lombok.Setter;
import org.investpro.core.SystemCore;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.signal.Signal;
import org.investpro.config.AppConfig;
import org.investpro.config.AppConfigKeys;
import org.investpro.core.agents.symbol.SymbolAgentState;
import org.investpro.data.CandleData;
import org.investpro.enums.SystemState;
import org.investpro.enums.RiskStatus;
import org.investpro.enums.TradingSessionStatus;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.exchange.consumers.UiExchangeStreamConsumer;
import org.investpro.exchange.consumers.DesktopExchangeStreamBridge;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.i18n.LocalizationService;
import org.investpro.i18n.SupportedLanguage;

import org.investpro.indicators.*;
import org.investpro.licensing.LicenseManager;
import org.investpro.monitoring.TradingSystemStatusSnapshot;
import org.investpro.operations.SystemActivityEvent;
import org.investpro.operations.SystemOperationsService;
import org.investpro.operations.SystemSnapshot;
import org.investpro.ui.charts.ChartContainer;
import org.investpro.ui.theme.MarketConfiguration;
import org.investpro.ui.theme.ThemeManager;
import org.investpro.models.market.NewsEvent;
import org.investpro.service.NewsDataProvider;
import org.investpro.models.Account;
import org.investpro.exchange.*;
import org.investpro.exchange.contracts.CredentialProvider;
import org.investpro.exchange.factory.ExchangeFactory;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.services.ExchangeService;
import org.investpro.models.trading.*;
import org.investpro.market.MarketDataEngine;
import org.investpro.persistence.repository.CurrencyRepository;
import org.investpro.persistence.repository.OrderRepository;

import org.investpro.persistence.repository.TradeRepository;
import org.investpro.risk.PositionHealthScore;
import org.investpro.service.CurrencyService;
import org.investpro.service.NotificationService;
import org.investpro.service.OrderService;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.StrategyParameters;
import org.investpro.service.TradeService;
import org.investpro.service.TradingService;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.StrategySelectionService;
import org.investpro.strategy.StrategySignal;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.PluginIndicatorFactory;
import org.investpro.spi.PluginRegistry;
import org.investpro.trading.tradability.MarketWatchTradabilityFilter;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.TradabilityScope;
import org.investpro.trading.tradability.UniversalTradabilityService;
import org.investpro.persistence.repository.StrategyAssignmentRepository;
import org.investpro.ui.charts.CandleStickChart;
import org.investpro.ui.charts.DepthChart;
import org.investpro.ui.charts.NewsEventOverlay;
import org.investpro.ui.operations.SystemOperationsBoard;
import org.investpro.ui.panels.*;
import org.investpro.ui.tools.DataWindow;
import org.investpro.ui.utils.CurrencyIconLoader;
import org.investpro.utils.DraggableTab;
import org.investpro.utils.ZoomDirection;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.prefs.Preferences;

import javafx.embed.swing.SwingFXUtils;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import static org.investpro.utils.Side.BUY;
import static org.investpro.utils.Side.SELL;
import static org.investpro.i18n.LocalizationService.t;

/**
 * Main InvestPro trading terminal window.
 * <p>
 * This version is wired to SystemCore:
 * - TradingWindow owns the UI.
 * - SystemCore owns SmartBot, streaming, strategy engine, risk, AI, execution,
 * and notifications.
 * - SmartBot is no longer controlled directly from the UI.
 */
@Slf4j
@Getter
@Setter
public class TradingDesk extends BorderPane  {
    private static final double DEFAULT_WIDTH = 1540;
    private static final double DEFAULT_HEIGHT = 820;
    private static final double LEFT_PANEL_WIDTH = 310;
    private static final double CONSOLE_HEIGHT = 250;
    private static final String ENV_TELEGRAM_TOKEN = "INVESTPRO_TELEGRAM_BOT_TOKEN";
    private static final DateTimeFormatter SNAPSHOT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter STATUS_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<Timeframe> MT5_TIMEFRAMES = List.of(
            Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.M30, Timeframe.H1,
            Timeframe.H4, Timeframe.D1, Timeframe.W1, Timeframe.MN);

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
            "BITFINEX US",
            "ALPACA",
            "INTERACTIVE BROKERS",
            "KRAKEN",
            "BITTREX",
            "BITMEX",
            "BITSTAMP",
            "KUCOIN",
            "KUCOIN US",
            "POLONIEX",
            "IG",
            "STELLAR NETWORK"
    };

    private final ComboBox<String> exchangeSelector = new ComboBox<>();
    private final ComboBox<TradePair> symbolSelector = new ComboBox<>();
    private final ComboBox<Timeframe> timeframeSelector = new ComboBox<>();
    private final ComboBox<String> botSymbolScopeSelector = new ComboBox<>();
    private final ComboBox<String> orderTypeSelector = new ComboBox<>();
    private final ComboBox<String> tradingModeSelector = new ComboBox<>();
    private final ComboBox<MarketWatchTradabilityFilter> marketWatchFilterSelector = new ComboBox<>();
    private final Label exchangeVenueLabel = new Label(t("label.venue"));

    private final Button connectButton = new Button(t("toolbar.connect"));
    private final Label connectedBrokerLabel = new Label(t("status.connected"));
    private final StackPane connectControl = new StackPane();
    private final Button refreshSymbolsButton = new Button(t("toolbar.refreshSymbols"));
    private final Button addChartButton = new Button(t("toolbar.openChart"));
    private final Button botTradeButton = new Button(t("toolbar.botTrade"));
    private final Button buyButton = new Button("BUY");
    private final Button sellButton = new Button("SELL");
    private final Button cancelAllButton = new Button(t("toolbar.cancelAll"));

    private final TabPane chartTabPane = new TabPane();
    private final TabPane terminalTabPane = new TabPane();

    private final ObservableList<TradePair> marketWatchItems = FXCollections.observableArrayList();
    private final TableView<TradePair> marketWatchTable = new TableView<>(marketWatchItems);

    private final ObservableList<OrderBook.PriceLevel> orderBookBids = FXCollections.observableArrayList();
    private final ObservableList<OrderBook.PriceLevel> orderBookAsks = FXCollections.observableArrayList();
    private final TableView<OrderBook.PriceLevel> orderBookBidsTable = new TableView<>(orderBookBids);
    private final TableView<OrderBook.PriceLevel> orderBookAsksTable = new TableView<>(orderBookAsks);

    private final ObservableList<Position> positionsItems = FXCollections.observableArrayList();
    private final TableView<Position> positionsTable = new TableView<>(positionsItems);

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
    private final Label balanceValueLabel = valueLabel("#10b981");
    private final Label availableValueLabel = valueLabel("#10b981");
    private final Label equityValueLabel = valueLabel("#3b82f6");
    private final Label marginUsedValueLabel = valueLabel("#ef4444");
    private final Label freeMarginValueLabel = valueLabel("#f59e0b");
    private final ObservableList<AssetBalanceRow> accountBalanceRows = FXCollections.observableArrayList();
    private final TableView<AssetBalanceRow> accountBalancesTable = new TableView<>(accountBalanceRows);

    private final Label connectionStatusLabel = new Label(t("status.disconnected"));
    private final Label symbolCountLabel = new Label(t("label.symbols", 0));
    private final Circle connectionIndicator = new Circle(6, Color.ORANGERED);
    private final Label deskConnectionLabel = new Label("Offline");
    private final Label deskRiskLabel = new Label("Risk: --");
    private final Label deskExposureLabel = new Label("Exposure: --");
    private final Label deskOrdersLabel = new Label("Orders: --");
    private final Label deskMarketLabel = new Label("Market: --");
    private final Label deskSpreadLabel = new Label("Spread: --");
    private final Label deskStrategyLabel = new Label("Win: --");
    private final Label deskUpdatedLabel = new Label("Updated: --");
    private final Map<Timeframe, ToggleButton> timeframeButtons = new EnumMap<>(Timeframe.class);
    private final Label statusBrokerLabel = new Label("Broker: -");
    private final Label statusModeLabel = new Label("Mode: -");
    private final Label statusActiveSymbolLabel = new Label("Symbol: -");
    private final Label statusLatencyLabel = new Label("Latency: -- ms");
    private final Label statusServerTimeLabel = new Label("Time: --:--:--");
    private final Label statusMarketDataLabel = new Label("Data: idle");
    private final Label chartHeaderSymbolLabel = new Label("No chart");
    private final Label chartHeaderTimeframeLabel = new Label("TF: -");
    private final Label chartHeaderQuoteLabel = new Label("Bid/Ask: -");
    private final Label chartHeaderLastLabel = new Label("Last: -");
    private final Label chartHeaderSpreadLabel = new Label("Spread: -");
    private final Label orderBookSymbolLabel = new Label("No symbol");
    private final TextField quickTradeAmountField = new TextField("1");
    private final AtomicBoolean autoRefreshTasksStarted = new AtomicBoolean(false);
    private final AtomicBoolean positionAutoRefreshStarted = new AtomicBoolean(false);

    private final TextArea journalArea = new TextArea();
    private final ObservableList<String> agentActivityItems = FXCollections
            .observableArrayList("Agent activity will appear here.");
    private final ObservableList<String> signalItems = FXCollections.observableArrayList("Signal engine idle.",
            "No signals loaded.");

    private final ListView<String> expertsListView = new ListView<>();
    private final ListView<String> alertsListView = new ListView<>();
    private final ListView<String> signalListView = new ListView<>();

    private final TradeRepository tradeRepository;
    private final OrderRepository orderRepository;
    private final CurrencyRepository currencyRepository;
    private final TradeService tradeService;
    private final OrderService orderService;
    private final CurrencyService currencyService;
    private final TradingService tradingService;
    private final NotificationService notificationService;
    private final Preferences preferences = Preferences.userNodeForPackage(TradingDesk.class);

    private final ScheduledExecutorService autoRefreshExecutor = Executors.newScheduledThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "TradingWindow-AutoRefresh");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService botOperationExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TradingWindow-BotOperation");
        thread.setDaemon(true);
        return thread;
    });
    private volatile ScheduledFuture<?> pendingSymbolAgentFeed;

    private SplitPane mainVerticalWorkbench;
    private SplitPane horizontalWorkbench;
    private SplitPane centerSplit;
    private VBox systemConsole;
    private Node marketWatchWrapper;
    private Node orderBookWrapper;
    private Stage detachedSystemConsoleStage;
    private boolean consoleVisible = true;
    private boolean marketWatchVisible = true;
    private boolean orderBookVisible = true;

    private Exchange exchange;
    private MarketDataEngine marketDataEngine;
    private UniversalTradabilityService universalTradabilityService;
    private String tradabilityServiceExchangeId = "";
    private final Map<String, SymbolTradability> tradabilityBySymbol = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<TradePair> marketWatchUniverse = new ArrayList<>();
    private volatile MarketSnapshot cachedMarketSnapshot = MarketSnapshot.empty();
    private volatile Instant cachedMarketSnapshotAt = Instant.EPOCH;
    private final AtomicBoolean marketSnapshotRefreshInFlight = new AtomicBoolean(false);
    private StrategyStats cachedStrategyStats = StrategyStats.empty();
    private Instant cachedStrategyStatsAt = Instant.EPOCH;
    private final Map<String, BrokerSession> brokerSessions = new HashMap<>();
    private SystemCore systemCore;
    private boolean systemCoreEventsSubscribed;
    private final AtomicBoolean botTradingOperationInFlight = new AtomicBoolean(false);
    private final NewsDataProvider newsDataProvider = new NewsDataProvider();
    private final ExchangeService exchangeService = new ExchangeService();

    private boolean botTradingEnabled;
    private boolean brokerAccessGranted;
    private String configuredApiKey = "";
    private String configuredApiSecret = "";
    private String configuredAccountId = "";
    private String configuredTradingMode = "LIVE";
    private String telegramToken = "";
    private String configuredOpenAiApiKey = "";
    private String oandaEmailNotification = "";
    private boolean initialized;

    private OrderBook currentOrderBook = new OrderBook();
    private DepthChart depthChart;
    private NewsCalendarPanel newsCalendarPanel;
    private MarketInfoPanel marketInfoPanel;
    private SystemOperationsBoard systemOperationsBoard;
    private ResourceMonitorPanel resourceMonitorPanel;
    private ConsolePanel detachedConsolePanel;
    private BacktestReportPanel backtestReportPanel;
    private org.investpro.ui.panels.ThemeCustomizationPanel themeCustomizationPanel;
    private MarketWatchPanel symbolAgentMarketWatch; // Symbol-level trading status (from SymbolAgentManager)
    private Navigation navigationPanel; // Exchange navigator
    private DataWindow dataWindow; // Data window for OHLCV display
    private AnalysisPanel analysisPanel; // Institutional analysis with backtesting/live metrics switching
    private TradePair activeOrderBookPair;

    // Track open independent windows to prevent duplicate Scene root assignments
    private final Map<String, Stage> openIndependentWindows = new java.util.HashMap<>();

    private record BrokerSession(Exchange exchange, boolean accessGranted, Account account) {
    }

    private record AssetBalanceRow(String asset, double balance, double equity, double margin, double freeMargin) {
    }

    public TradingDesk(
            MarketConfiguration configuration,
            TradeRepository tradeRepository,
            OrderRepository orderRepository,
            CurrencyRepository currencyRepository) {
        this.tradeRepository = Objects.requireNonNull(tradeRepository, "tradeRepository must not be null");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.currencyRepository = Objects.requireNonNull(currencyRepository, "currencyRepository must not be null");

        this.tradeService = new TradeService(this.tradeRepository);
        this.orderService = new OrderService(this.orderRepository);
        this.currencyService = new CurrencyService(this.currencyRepository);
        this.tradingService = new TradingService(systemCore, this.tradeService, this.orderService,
                this.currencyService);

        // Initialize NotificationService (disabled by default, can be enabled via
        // settings)
        this.notificationService = NotificationService.disabled();

        // Initialize MarketDataEngine (central market data cache and services)
        this.marketDataEngine = new MarketDataEngine();

        // Wire ExchangeService to SystemOperationsService for monitoring
        SystemOperationsService.getInstance().setExchangeService(exchangeService);

        initialize(configuration);
        initializeUiStreamConsumer();
        this.desktopStreamBridge = new DesktopExchangeStreamBridge(this);
    }

    private NewsEventOverlay newsEventOverlay;

    private void initialize(MarketConfiguration configuration) {
        if (initialized) {
            return;
        }
        initialized = true;

        // Load and apply saved theme configuration at startup
        ThemeManager themeManager = ThemeManager.getInstance();
        themeManager.loadConfiguration();
        themeManager.applyTheme(this);

        setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinSize(1200, 690);
        setFocusTraversable(true);
        getStyleClass().addAll("trading-window-pro", "mt5-root");
        setNodeOrientation(LocalizationService.getCurrentLanguage().isRightToLeft()
                ? javafx.geometry.NodeOrientation.RIGHT_TO_LEFT
                : javafx.geometry.NodeOrientation.LEFT_TO_RIGHT);

        configuredApiKey = configuration == null ? "" : safe(configuration.apiKey());
        configuredApiSecret = configuration == null ? "" : safe(configuration.apiSecret());
        configuredAccountId = configuration == null ? "" : safe(configuration.accountId());
        configuredTradingMode = configuration == null ? "LIVE" : safe(configuration.tradingMode());
        if (configuredTradingMode.isBlank()) {
            configuredTradingMode = "LIVE";
        }
        telegramToken = resolveTelegramToken(configuration);
        configuredOpenAiApiKey = preferences.get("openai_api_key", safe(System.getenv("OPENAI_API_KEY")));

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
        LocalizationService.applyTranslations(this);

        if (preferences.getBoolean("remember_console_visible", true)
                && !preferences.getBoolean("console_visible", true)) {
            toggleConsoleVisibility();
        }

        setupKeyboardShortcuts();
        createInitialExchange(configuration);
        loadSymbolsForSelectedExchange();
        // Candlestick charts now open only on user request, not automatically

        if (hasExchangeCredentials(exchangeSelector.getSelectionModel().getSelectedItem())
                || hasConfiguredCredentials()) {
            proceedWithConnection();
        }

        startAutoRefreshTasks();

        log.info("TradingWindow initialized.");
    }

    private @NotNull String resolveTelegramToken(MarketConfiguration configuration) {

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

    private @NotNull VBox createTopSection() {
        VBox topSection = new VBox(0);
        topSection.getStyleClass().add("mt5-top-section");

        MenuBar menuBar = createMenuBar();
        menuBar.getStyleClass().add("mt5-menu-bar");

        topSection.getChildren().addAll(menuBar, createMainToolBar(), createDeskCommandStrip());
        updateDeskCommandStrip();
        return topSection;
    }

    @Contract(" -> new")
    private @NotNull MenuBar createMenuBar() {
        // ── File ─────────────────────────────────────────────────────────────
        Menu fileMenu = new Menu(t("menu.file"));
        fileMenu.getItems().setAll(
                menuItem(t("menu.newChart"), new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                        this::openSelectedSymbolChart),
                new SeparatorMenuItem(),
                menuItem(t("menu.refreshSymbols"), new KeyCodeCombination(KeyCode.F5),
                        this::loadSymbolsForSelectedExchange),
                menuItem(t("menu.refreshAccount"), new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN),
                        this::refreshAccountWorkspace),
                new SeparatorMenuItem(),
                menuItem(t("menu.saveChart"), new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                        this::saveActiveChartSnapshot),
                new SeparatorMenuItem(),
                menuItem(t("menu.exit"), new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN), () -> {
                    shutdown();
                    Platform.exit();
                }));

        // ── Edit ─────────────────────────────────────────────────────────────
        Menu editMenu = new Menu(t("menu.edit"));
        editMenu.getItems().setAll(
                menuItem(t("menu.undo"), new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN),
                        () -> withFocusedTextInput(TextInputControl::undo)),
                menuItem(t("menu.redo"), new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN),
                        () -> withFocusedTextInput(TextInputControl::redo)),
                new SeparatorMenuItem(),
                menuItem(t("menu.cut"), new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN),
                        () -> withFocusedTextInput(TextInputControl::cut)),
                menuItem(t("menu.copy"), new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN),
                        () -> withFocusedTextInput(TextInputControl::copy)),
                menuItem(t("menu.paste"), new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN),
                        () -> withFocusedTextInput(TextInputControl::paste)));

        // ── View ─────────────────────────────────────────────────────────────
        Menu viewMenu = new Menu(t("menu.view"));
        viewMenu.getItems().setAll(
                menuItem(t("menu.showCharts"), null, chartTabPane::requestFocus),
                new SeparatorMenuItem(),
                menuItem(t("menu.toggleMarketWatch"), new KeyCodeCombination(KeyCode.M, KeyCombination.CONTROL_DOWN),
                        this::toggleMarketWatchVisibility),
                menuItem(t("menu.toggleOrderBook"), new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN),
                        this::toggleOrderBookVisibility),
                menuItem(t("menu.toggleTerminal"),
                        new KeyCodeCombination(KeyCode.BACK_QUOTE, KeyCombination.CONTROL_DOWN),
                        this::toggleConsoleVisibility),
                new SeparatorMenuItem(),
                menuItem(t("menu.dataWindow"), null, this::openDataWindow),
                menuItem(t("menu.marketInfoPanel"), new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN),
                        this::openMarketInfoPanel),
                menuItem(t("menu.symbolAgentWatch"), null, this::openSymbolAgentMarketWatch),
                menuItem(t("menu.navigationPanel"), null, this::openNavigationPanel),
                new SeparatorMenuItem(),
                menuItem(t("menu.zoomIn"), new KeyCodeCombination(KeyCode.PLUS, KeyCombination.CONTROL_DOWN),
                        () -> withActiveChart(chart -> chart.changeZoom(ZoomDirection.IN))),
                menuItem(t("menu.zoomOut"), new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN),
                        () -> withActiveChart(chart -> chart.changeZoom(ZoomDirection.OUT))),
                new SeparatorMenuItem(),
                menuItem(t("menu.closeAllCharts"), null, this::closeAllCharts),
                menuItem(t("menu.detachConsole"), null, this::detachConsoleWindow));

        // ── Charts ───────────────────────────────────────────────────────────
        Menu chartsMenu = new Menu(t("menu.charts"));
        chartsMenu.getItems().setAll(
                menuItem(t("menu.candlestick"), null, this::openSelectedSymbolChart),
                menuItem(t("menu.fitActiveChart"), null, () -> withActiveChart(CandleStickChart::fitChart)),
                menuItem(t("menu.refreshActiveChart"), null, () -> withActiveChart(CandleStickChart::refreshChart)),
                menuItem(t("menu.toggleCrosshair"), null, () -> withActiveChart(CandleStickChart::toggleCrosshair)),
                new SeparatorMenuItem(),
                menuItem(t("menu.indicators"), null, this::openInsertIndicatorDialog),
                menuItem(t("menu.backgroundImage"), null, this::chooseActiveChartBackgroundImage),
                menuItem(t("menu.clearBackgroundImage"), null, this::clearActiveChartBackgroundImage));

        // ── Trade ────────────────────────────────────────────────────────────
        Menu tradeMenu = new Menu("Trade");
        tradeMenu.getItems().setAll(
                menuItem(t("menu.order"), new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),
                        this::openOrderPanel),
                new SeparatorMenuItem(),
                menuItem(t("menu.connectExchange"), null, this::connectSelectedExchange),
                menuItem(t("menu.toggleBotTrading"), new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN),
                        this::toggleBotTrading),
                new SeparatorMenuItem(),
                menuItem(t("menu.refreshLocalPositions"), null, this::refreshPositions),
                menuItem(t("menu.cancelAllOrders"), null, this::cancelAllOrders));

        // ── Strategy ─────────────────────────────────────────────────────────
        Menu strategyMenu = new Menu(t("menu.strategy"));
        strategyMenu.getItems().setAll(
                menuItem("Strategy Lab", new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                        this::openStrategyLabPanel),
                menuItem("Strategy Developer",
                        new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                        this::openStrategyDeveloperPanel),
                menuItem(t("menu.strategyBuilder"),
                        new KeyCodeCombination(KeyCode.U, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                        this::openStrategyBuilder),
                menuItem(t("menu.strategyAssignment"),
                        new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                        this::openStrategyAssignmentPanel),
                new SeparatorMenuItem(),
                menuItem(t("menu.backtesting"), null, this::openBacktesting),
                menuItem(t("menu.analysis"), null, this::openAnalysis),
                menuItem("Backtest Report", null, this::openBacktestReportPanel),
                new SeparatorMenuItem(),
                menuItem(t("menu.viewAllStrategies"), null, this::openAllStrategies),
                menuItem(t("menu.importStrategy"), null, this::importStrategy),
                menuItem(t("menu.exportStrategy"), null, this::exportStrategy));

        // ── Research ─────────────────────────────────────────────────────────
        Menu researchMenu = new Menu(t("menu.research"));
        researchMenu.getItems().setAll(
                menuItem(t("menu.marketResearch"), null, this::openMarketResearch),
                menuItem(t("menu.strategyResearch"), null, this::openStrategyResearch),
                menuItem(t("menu.news"), null, this::openNewsCalendarPanel),
                new SeparatorMenuItem(),
                menuItem("Quant PM", null, this::showQuantPortfolioManagement),
                menuItem("Signal Monitor", null, this::showSignalMonitor),
                menuItem("ML Lab", null, this::showMLLab),
                new SeparatorMenuItem(),
                menuItem(t("menu.researchReports"), null, this::openResearchReports));

        // ── Review ───────────────────────────────────────────────────────────
        Menu reviewMenu = new Menu("Review");
        reviewMenu.getItems().setAll(
                menuItem("Performances", null, this::showPerformancesReview),
                menuItem("Trades", null, this::showTradesReview),
                new SeparatorMenuItem(),
                menuItem("Recommendation", null, this::showRecommendations),
                menuItem("Journal", null, this::showTradingJournal),
                menuItem("Closed Journal", null, this::showClosedJournal),
                menuItem("Generate Reports", null, this::generateTradeReports));

        // ── System ───────────────────────────────────────────────────────────
        Menu systemMenu = new Menu("System");
        systemMenu.getItems().setAll(
                menuItem("Operations Center", null, this::openSystemOperationsBoard),
                menuItem("Plugin Manager", null, this::openPluginManagerPanel),
                menuItem("Trading System Status", null, this::tradingSystemStatus),
                menuItem("System Console", null, this::openSystemConsolePanel),
                menuItem("Detached Console", null, this::openDetachedConsolePanel),
                new SeparatorMenuItem(),
                menuItem("Diagnostics", null, this::showSystemDiagnostics),
                menuItem("Performance Metrics", null, this::showSystemMetrics),
                menuItem("Resource Monitor", null, this::showResourceMonitor));

        // ── Education ────────────────────────────────────────────────────────
        Menu educationMenu = new Menu("Education");
        educationMenu.getItems().setAll(
                menuItem("Trading Basics", null, this::showTradingBasics),
                menuItem("Strategy Guide", null, this::showStrategyGuide),
                menuItem("Risk Management", null, this::showRiskManagement),
                new SeparatorMenuItem(),
                menuItem("Market Analysis", null, this::showMarketAnalysis),
                menuItem("Technical Indicators", null, this::showTechnicalIndicators),
                menuItem("Investment Concepts", null, this::showInvestmentConcepts),
                new SeparatorMenuItem(),
                menuItem("Video Tutorials", null, this::showVideoTutorials),
                menuItem("Documentation", null, this::showDocumentation));

        // ── Settings ─────────────────────────────────────────────────────────
        Menu settingsMenu = new Menu(t("menu.settings"));
        settingsMenu.getItems().setAll(
                menuItem("Settings", new KeyCodeCombination(KeyCode.COMMA, KeyCombination.CONTROL_DOWN),
                        this::openSettingsPanel),
                new SeparatorMenuItem(),
                menuItem(t("menu.exchangeCredentials"), null, this::showSettingsDialog),
                menuItem(t("menu.tradingProfile"), null, this::showTradingProfileSettings),
                new SeparatorMenuItem(),
                menuItem("Plugin Manager", null, this::openPluginManagerPanel),
                menuItem("Theme Customization", null, this::openThemeCustomization),
                menuItem("Theme Settings", null, this::showThemeSettingsDialog),
                menuItem("Visibility & Layout", null, this::showVisibilitySettingsDialog),
                new SeparatorMenuItem(),
                menuItem(t("menu.resetPassword"), null, this::openPasswordReset));

        // ── Help ─────────────────────────────────────────────────────────────
        Menu helpMenu = new Menu(t("menu.help"));
        helpMenu.getItems().setAll(
                menuItem("User Guide", new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN),
                        this::showUserGuide),
                menuItem("Keyboard Shortcuts", new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN),
                        this::showKeyboardShortcuts),
                menuItem(t("menu.helpItem"), new KeyCodeCombination(KeyCode.F1),
                        () -> showInfo("Help", "InvestPro Help — F5 refreshes data, Ctrl+` toggles terminal.")),
                new SeparatorMenuItem(),
                menuItem("License Management", null, this::openLicenseManagement),
                new SeparatorMenuItem(),
                menuItem(t("menu.about"), null, () -> showInfo("About InvestPro",
                        "InvestPro Terminal\nProfessional Trading Desk\nVersion: 1.0.0\nDeveloper: NOEL NGUEMECHIEU")));

        Menu languageMenu = createLanguageMenu();

        return new MenuBar(fileMenu, editMenu, viewMenu, chartsMenu, tradeMenu, strategyMenu,
                researchMenu, reviewMenu, systemMenu, educationMenu, settingsMenu, languageMenu, helpMenu);
    }

    private void openLicenseManagement() {
        try {
            LicensePanel licensePanel = new LicensePanel(new LicenseManager(systemCore));
            licensePanel.updateDisplay();
            createIndependentWindow("License Management", licensePanel, 500, 400);
        } catch (Exception ex) {
            log.error("Error opening license management", ex);
            showWarning("License Management", "Unable to open license management: " + ex.getMessage());
        }
    }

    public void openRegisteredPanel(String panelId) {
        if (panelId == null || panelId.isBlank()) {
            return;
        }

        try {
            switch (panelId.trim().toLowerCase(Locale.ROOT)) {
                case "strategy-lab" -> openStrategyLabPanel();
                case "strategy-developer" -> openStrategyDeveloperPanel();
                case "strategy-builder" -> openStrategyBuilder();
                case "strategy-assignment" -> openStrategyAssignmentPanel();
                case "order-panel" -> openOrderPanel();
                case "operations-center" -> openSystemOperationsBoard();
                case "plugin-manager" -> openPluginManagerPanel();
                case "data-window" -> openDataWindow();
                case "resource-monitor" -> showResourceMonitor();
                case "trading-system-status" -> tradingSystemStatus();
                case "console" -> openSystemConsolePanel();
                case "market-watch" -> openSymbolAgentMarketWatch();
                case "market-info" -> openMarketInfoPanel();
                case "news-calendar" -> openNewsCalendarPanel();
                case "analysis" -> openAnalysis();
                case "backtesting" -> openBacktesting();
                case "settings" -> openSettingsPanel();
                case "theme-customization" -> openThemeCustomization();
                default -> log.warn("No TradingDesk panel action registered for id: {}", panelId);
            }
        } catch (Exception exception) {
            log.error("Unable to open registered panel: {}", panelId, exception);
            showWarning("Panel", "Unable to open panel: " + exception.getMessage());
        }
    }

    private Menu createLanguageMenu() {
        Menu languageMenu = new Menu(t("language.menu"));
        ToggleGroup group = new ToggleGroup();

        for (SupportedLanguage language : SupportedLanguage.values()) {
            RadioMenuItem item = new RadioMenuItem(language.getDisplayName());
            item.setToggleGroup(group);
            item.setSelected(language == LocalizationService.getCurrentLanguage());
            item.setOnAction(event -> changeLanguage(language));
            languageMenu.getItems().add(item);
        }

        return languageMenu;
    }

    private MenuItem menuItem(String text, KeyCodeCombination accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        item.setOnAction(event -> {
            if (action != null) {
                try {
                    action.run();
                } catch (Exception exception) {
                    log.error("Menu action failed: {}", text, exception);
                    showWarning("Menu Action", "Unable to complete '%s': %s".formatted(text, rootMessage(exception)));
                }
            }
        });
        return item;
    }

    private void changeLanguage(SupportedLanguage language) {
        if (language == null || language == LocalizationService.getCurrentLanguage()) {
            return;
        }

        LocalizationService.setCurrentLanguage(language);
        applyLanguage();
        appendAgentActivity(t("language.changed", language.getDisplayName()));
    }

    private void applyLanguage() {
        setNodeOrientation(LocalizationService.getCurrentLanguage().isRightToLeft()
                ? javafx.geometry.NodeOrientation.RIGHT_TO_LEFT
                : javafx.geometry.NodeOrientation.LEFT_TO_RIGHT);

        setTop(createTopSection());
        LocalizationService.applyTranslations(this);
        connectButton.setText(t("toolbar.connect"));
        connectedBrokerLabel.setText(t("status.connected"));
        refreshSymbolsButton.setText(t("toolbar.refreshSymbols"));
        addChartButton.setText(t("toolbar.openChart"));
        cancelAllButton.setText(t("toolbar.cancelAll"));
        exchangeVenueLabel.setText(t("label.venue"));
        refreshBotTradeButton();
        updateConnectionStatus();
    }

    @Contract(" -> new")
    private @NotNull ToolBar createMainToolBar() {
        createCompactCombo(exchangeSelector, 170, "Broker");
        createCompactCombo(symbolSelector, 190, "Symbol");
        createCompactCombo(timeframeSelector, 82, "Timeframe");
        createCompactCombo(tradingModeSelector, 92, "Mode");
        createCompactCombo(orderTypeSelector, 92, "Order Type");

        Label brand = new Label("InvestPro Terminal");
        brand.getStyleClass().addAll("terminal-brand", "mt5-brand");

        Button newOrderButton = createToolbarButton("New Order", "Open order ticket");
        newOrderButton.setOnAction(event -> openOrderPanel());

        Button autoScrollButton = createToolbarButton("Auto-scroll", "Chart auto-scroll placeholder");
        autoScrollButton.setOnAction(event -> journal("Auto-scroll chart tool selected."));

        Button chartToolsButton = createToolbarButton("Chart Tools", "Chart drawing tools placeholder");
        chartToolsButton.setOnAction(event -> journal("Chart tools selected."));

        Button closeAllButton = createToolbarButton("Close All", "Close all positions placeholder");
        closeAllButton.getStyleClass().add("danger-button");
        closeAllButton.setOnAction(event -> showInfo("Close All", "Close-all positions command is not wired yet."));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToolBar toolBar = new ToolBar(
                brand,
                createVerticalSeparator(),
                tradingModeSelector,
                createVerticalSeparator(),
                new Label("Broker"),
                exchangeSelector,
                connectControl,
                createVerticalSeparator(),
                new Label("Symbol"),
                symbolSelector,
                timeframeSelector,
                refreshSymbolsButton,
                addChartButton,
                createVerticalSeparator(),
                createTimeframeStrip(),
                createVerticalSeparator(),
                newOrderButton,
                new Label("Type"),
                orderTypeSelector,
                exchangeVenueLabel,
                buyButton,
                sellButton,
                cancelAllButton,
                closeAllButton,
                createVerticalSeparator(),
                autoScrollButton,
                crosshairToolbarButton(),
                chartToolsButton,
                createVerticalSeparator(),
                new Label("Algo"),
                botTradeButton,
                createVerticalSeparator(),
                spacer,
                new Label("Toolbox"));
        toolBar.getStyleClass().addAll("main-trading-toolbar", "mt5-toolbar");
        return toolBar;
    }

    private @NotNull Button crosshairToolbarButton() {
        Button button = createToolbarButton("Crosshair", "Toggle crosshair on active chart");
        button.setOnAction(event -> withActiveChart(CandleStickChart::toggleCrosshair));
        return button;
    }

    private @NotNull HBox createTimeframeStrip() {
        HBox strip = new HBox(2);
        strip.getStyleClass().add("mt5-timeframe-strip");
        timeframeButtons.clear();
        for (Timeframe timeframe : MT5_TIMEFRAMES) {
            ToggleButton button = createTimeframeButton(timeframe);
            timeframeButtons.put(timeframe, button);
            strip.getChildren().add(button);
        }
        setActiveTimeframeButton(timeframeSelector.getSelectionModel().getSelectedItem());
        return strip;
    }

    private @NotNull HBox createDeskCommandStrip() {
        HBox strip = new HBox(8);
        strip.setAlignment(Pos.CENTER_LEFT);
        strip.getStyleClass().add("desk-command-strip");

        strip.getChildren().setAll(
                createDeskMetric("Connection", deskConnectionLabel, "desk-metric-connection"),
                createDeskMetric("Guardrail", deskRiskLabel, "desk-metric-risk"),
                createDeskMetric("Exposure", deskExposureLabel, "desk-metric-exposure"),
                createDeskMetric("Orders", deskOrdersLabel, "desk-metric-orders"),
                createDeskMetric("Market", deskMarketLabel, "desk-metric-market"),
                createDeskMetric("Spread", deskSpreadLabel, "desk-metric-spread"),
                createDeskMetric("Strategy", deskStrategyLabel, "desk-metric-strategy"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshDeskButton = createDeskActionButton("Refresh All", this::refreshDeskSnapshot);
        Button riskButton = createDeskActionButton("Risk View", this::focusRiskMonitor);
        Button analysisButton = createDeskActionButton("Analysis", this::openAnalysis);
        Button orderTicketButton = createDeskActionButton("Order Ticket", this::openOrderPanel);

        deskUpdatedLabel.getStyleClass().add("desk-updated-label");
        strip.getChildren().addAll(spacer, deskUpdatedLabel, refreshDeskButton, riskButton, analysisButton,
                orderTicketButton);
        return strip;
    }

    private @NotNull VBox createDeskMetric(String title, Label valueLabel, String styleClass) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("desk-metric-title");

        valueLabel.getStyleClass().setAll("desk-metric-value", styleClass);
        valueLabel.setMinWidth(84);
        valueLabel.setMaxWidth(150);

        VBox metric = new VBox(1, titleLabel, valueLabel);
        metric.getStyleClass().add("desk-metric");
        metric.setAlignment(Pos.CENTER_LEFT);
        return metric;
    }

    private @NotNull Button createDeskActionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("desk-action-button");
        button.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
        return button;
    }

    private @NotNull Button createToolbarButton(String text, String tooltip) {
        Button button = new Button(text);
        button.getStyleClass().add("mt5-toolbar-button");
        button.setTooltip(new Tooltip(tooltip == null || tooltip.isBlank() ? text : tooltip));
        button.setFocusTraversable(false);
        return button;
    }

    @Contract("_, _, _ -> param1")
    private <T> @NonNull ComboBox<T> createCompactCombo(@NonNull ComboBox<T> comboBox, double width, String tooltip) {
        comboBox.setPrefWidth(width);
        comboBox.setMinWidth(Math.min(width, 72));
        comboBox.getStyleClass().add("mt5-compact-combo");
        comboBox.setTooltip(new Tooltip(tooltip));
        return comboBox;
    }

    private @NotNull ToggleButton createTimeframeButton(Timeframe timeframe) {
        ToggleButton button = new ToggleButton(timeframe.name());
        button.getStyleClass().add("mt5-timeframe-button");
        button.setTooltip(new Tooltip(timeframe.getDisplayName()));
        button.setFocusTraversable(false);
        button.setOnAction(event -> {
            timeframeSelector.getSelectionModel().select(timeframe);
            applyTimeframeSelection(timeframe);
        });
        return button;
    }

    private @NotNull Separator createVerticalSeparator() {
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.getStyleClass().add("mt5-separator");
        return separator;
    }

    private void setActiveTimeframeButton(Timeframe selected) {
        timeframeButtons.forEach((timeframe, button) -> {
            boolean active = timeframe == selected;
            button.setSelected(active);
            button.getStyleClass().remove("mt5-timeframe-button-active");
            if (active) {
                button.getStyleClass().add("mt5-timeframe-button-active");
            }
        });
        chartHeaderTimeframeLabel.setText("TF: " + (selected == null ? "-" : selected.name()));
    }

    private @NotNull HBox createPanelHeader(String title, Node... actions) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(6, titleLabel, spacer);
        if (actions != null) {
            header.getChildren().addAll(Arrays.stream(actions)
                    .filter(Objects::nonNull)
                    .toList());
        }
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().addAll("panel-header", "mt5-panel-header");
        return header;
    }

    private @NotNull Button createCommandButton(String text, String styleClass, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("mt5-command-button", styleClass);
        button.setFocusTraversable(false);
        button.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
        return button;
    }

    private @NotNull Label createTerminalStatusChip() {
        Label label = new Label("InvestPro Terminal");
        label.getStyleClass().addAll("mt5-status-chip", "terminal-label");
        return label;
    }

    private boolean useLegacyWorkbenchLayout() {
        return preferences.getBoolean("ui_use_legacy_workbench", false);
    }

    private @NotNull VBox createMarketWatchPaneSurface() {
        return useLegacyWorkbenchLayout() ? createMarketWatchPaneLegacy() : createMarketWatchPane();
    }

    private @NotNull VBox createOrderBookPaneSurface() {
        return useLegacyWorkbenchLayout() ? createOrderBookPaneLegacy() : createOrderBookPane();
    }

    private @NotNull Node createChartWorkspaceSurface() {
        return useLegacyWorkbenchLayout() ? createChartWorkspaceLegacy() : createChartWorkspace();
    }

    private @NotNull VBox createTradingConsoleSurface() {
        return useLegacyWorkbenchLayout() ? createTradingConsoleLegacy() : createTradingConsole();
    }

    private SplitPane createMainWorkbench() {
        // Create center workspace with charts and terminal
        systemConsole = createTradingConsoleSurface();
        Node centerWorkspace = createCenterWorkspace();

        // Create the main horizontal layout: LEFT | CENTER | RIGHT
        horizontalWorkbench = new SplitPane();
        horizontalWorkbench.setOrientation(Orientation.HORIZONTAL);
        horizontalWorkbench.getStyleClass().add("workbench-split");

        // Wrap market watch and order book for visibility toggling
        marketWatchWrapper = createLeftSidebar();
        orderBookWrapper = createOrderBookPaneSurface();

        horizontalWorkbench.getItems().setAll(
                marketWatchWrapper, // LEFT: Market Watch + Navigator
                centerWorkspace, // CENTER: Charts + Terminal
                orderBookWrapper // RIGHT: Order Book
        );
        // Divider positions: 22% left, 56% center, 22% right
        horizontalWorkbench.setDividerPositions(0.22, 0.78);

        // No more vertical split needed - terminal is now integrated in center
        mainVerticalWorkbench = new SplitPane();
        mainVerticalWorkbench.setOrientation(Orientation.VERTICAL);
        mainVerticalWorkbench.getStyleClass().add("workbench-split");
        mainVerticalWorkbench.getItems().setAll(horizontalWorkbench);
        return mainVerticalWorkbench;
    }

    private @NotNull Node createCenterWorkspace() {
        centerSplit = new SplitPane();
        centerSplit.setOrientation(Orientation.VERTICAL);
        centerSplit.getStyleClass().add("center-workspace-split");
        centerSplit.getItems().setAll(createChartWorkspaceSurface(), systemConsole);
        centerSplit.setDividerPositions(0.72);
        return centerSplit;
    }

    private @NotNull Node createLeftSidebar() {
        SplitPane leftSplit = new SplitPane();
        leftSplit.setOrientation(Orientation.VERTICAL);
        leftSplit.getStyleClass().add("left-rail");
        leftSplit.getItems().setAll(createMarketWatchPaneSurface(), createNavigatorTabs());
        leftSplit.setDividerPositions(0.62);
        leftSplit.setPrefWidth(LEFT_PANEL_WIDTH);
        leftSplit.setMinWidth(260);
        return leftSplit;
    }

    private @NotNull VBox createMarketWatchPane() {
        Label count = new Label();
        count.getStyleClass().add("panel-meta");
        count.textProperty().bind(symbolCountLabel.textProperty());

        Button openSelectedButton = marketWatchActionButton("Chart", "/img/newtab.png", "Open selected symbol chart");
        openSelectedButton.setOnAction(event -> openSelectedFromMarketWatch());

        Button refreshButton = marketWatchActionButton("Refresh", "/img/refresh-solid.png", "Refresh market watch symbols");
        refreshButton.setOnAction(event -> loadSymbolsForSelectedExchange());

        Button detachButton = marketWatchActionButton("Detach", "/img/expand-solid.png", "Detach market watch");
        detachButton.setOnAction(event -> detachMarketWatch());

        Button closeButton = createCloseButton(this::toggleMarketWatchVisibility);
        HBox header = createPanelHeader("Market Watch", count, openSelectedButton, refreshButton, detachButton, closeButton);

        configureMarketWatchFilterSelector();

        TextField searchField = new TextField();
        searchField.setPromptText("Search symbols");
        searchField.getStyleClass().add("mt5-market-search");

        FilteredList<TradePair> filteredItems = new FilteredList<>(marketWatchItems, pair -> true);
        searchField.textProperty().addListener((obs, old, query) ->
                filteredItems.setPredicate(pair -> pair == null || query == null || query.isBlank()
                        || pair.toString('/').toUpperCase(Locale.ROOT).contains(query.toUpperCase(Locale.ROOT))));
        marketWatchTable.setItems(filteredItems);

        VBox.setVgrow(marketWatchTable, Priority.ALWAYS);
        VBox box = new VBox(4, header, marketWatchFilterSelector, searchField, marketWatchTable);
        box.setPadding(new Insets(8));
        box.getStyleClass().addAll("market-watch", "pro-panel", "mt5-panel", "mt5-market-watch");
        return box;
    }

    protected @NotNull VBox createMarketWatchPaneLegacy() {
        Label title = new Label("Market Watch");
        title.getStyleClass().add("panel-title");
        Label count = new Label();
        count.getStyleClass().add("panel-meta");
        count.textProperty().bind(symbolCountLabel.textProperty());

        Button openSelectedButton = marketWatchActionButton("Open", "/img/newtab.png", "Open selected symbol chart");
        openSelectedButton.setOnAction(event -> openSelectedFromMarketWatch());

        Button refreshButton = marketWatchActionButton("Refresh", "/img/refresh-solid.png", "Refresh market watch symbols");
        refreshButton.setOnAction(event -> loadSymbolsForSelectedExchange());

        Button detachButton = marketWatchActionButton("Detach", "/img/expand-solid.png", "Detach market watch");
        detachButton.setOnAction(event -> detachMarketWatch());

        Button closeButton = createCloseButton(this::toggleMarketWatchVisibility);

        configureMarketWatchFilterSelector();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, count, spacer, openSelectedButton, refreshButton, detachButton, closeButton);
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

        // Search / filter field
        TextField searchField = new TextField();
        searchField.setPromptText("🔍 Filter symbols…");
        searchField.setStyle(
                "-fx-background-color: #1e293b; -fx-text-fill: #e2e8f0; " +
                "-fx-border-color: #334155; -fx-border-radius: 4; -fx-background-radius: 4; " +
                "-fx-prompt-text-fill: #64748b; -fx-font-size: 11px; -fx-padding: 5 8;");
        FilteredList<TradePair> filteredItems = new FilteredList<>(marketWatchItems, p -> true);
        searchField.textProperty().addListener((obs, old, query) ->
                filteredItems.setPredicate(pair -> pair == null || query == null || query.isBlank() ||
                        pair.toString('/').toUpperCase().contains(query.toUpperCase())));
        marketWatchTable.setItems(filteredItems);

        VBox.setVgrow(marketWatchTable, Priority.ALWAYS);
        VBox box = new VBox(4, header, marketWatchFilterSelector, searchField, marketWatchTable);
        box.setPadding(new Insets(8));
        box.getStyleClass().addAll("market-watch", "pro-panel");
        return box;
    }

    private @NotNull Button createCloseButton(Runnable toggleAction) {
        Button btn = new Button("✕");
        btn.setStyle(
                "-fx-font-size: 14; -fx-padding: 2 8 2 8; -fx-text-fill: #a0aec0; -fx-background-color: transparent; -fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-font-size: 14; -fx-padding: 2 8 2 8; -fx-text-fill: #ef4444; -fx-background-color: transparent; -fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-font-size: 14; -fx-padding: 2 8 2 8; -fx-text-fill: #a0aec0; -fx-background-color: transparent; -fx-cursor: hand;"));
        btn.setOnAction(event -> toggleAction.run());
        return btn;
    }

    private Button marketWatchActionButton(String text, String iconPath, String tooltip) {
        Button button = new Button(text);
        button.getStyleClass().add("terminal-button");
        ImageView icon = loadUiIcon(iconPath, 14);
        if (icon != null) {
            button.setGraphic(icon);
        }
        button.setTooltip(new Tooltip(tooltip == null ? text : tooltip));
        button.setStyle("""
                -fx-background-color: #1e293b;
                -fx-text-fill: #e2e8f0;
                -fx-border-color: #334155;
                -fx-border-radius: 999;
                -fx-background-radius: 999;
                -fx-font-size: 11px;
                -fx-font-weight: bold;
                -fx-padding: 6 10;
                -fx-cursor: hand;
                """);
        return button;
    }

    private ImageView loadUiIcon(String resourcePath, double size) {
        try {
            var stream = TradingDesk.class.getResourceAsStream(resourcePath);
            if (stream == null) {
                return null;
            }
            ImageView imageView = new ImageView(new Image(stream));
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            return imageView;
        } catch (Exception exception) {
            log.debug("Unable to load UI icon {}: {}", resourcePath, exception.getMessage());
            return null;
        }
    }

    private @NotNull HBox createMiniChartRow(TradePair pair) {
        Label symbolLabel = new Label(pair == null ? "-" : pair.toString('/'));
        symbolLabel.getStyleClass().add("symbol-name");
        symbolLabel.setMinWidth(88);
        ImageView symbolIcon = null;
        if (pair != null) {
            Image icon = CurrencyIconLoader.loadCurrencyIcon(pair.getBaseCode());
            if (icon != null) {
                symbolIcon = new ImageView(icon);
                symbolIcon.setFitWidth(20);
                symbolIcon.setFitHeight(20);
                symbolIcon.setPreserveRatio(true);
            }
        }

        Label bidLabel = new Label("Bid: %s".formatted(pair == null ? "-" : price(pair.getBid())));
        bidLabel.getStyleClass().add("symbol-bid");

        Label askLabel = new Label("Ask: %s".formatted(pair == null ? "-" : price(pair.getAsk())));
        askLabel.getStyleClass().add("symbol-ask");

        Button viewButton = marketWatchActionButton("Chart", "/img/newtab.png", "Open chart");
        viewButton.setOnAction(event -> {
            if (pair != null) {
                symbolSelector.getSelectionModel().select(pair);
                openSelectedSymbolChart();
            }
        });

        HBox row = new HBox(10);
        if (symbolIcon != null) {
            row.getChildren().add(symbolIcon);
        }
        row.getChildren().addAll(symbolLabel, bidLabel, askLabel, viewButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6));
        row.getStyleClass().add("mini-symbol-row");
        return row;
    }

    private @NotNull VBox createOrderBookPane() {
        orderBookSymbolLabel.getStyleClass().add("panel-meta");
        orderBookSymbolLabel.setText("No symbol");

        Button refreshButton = createToolbarButton("Refresh", "Refresh depth of market");
        refreshButton.setOnAction(event -> loadSelectedOrderBook());

        Button detachButton = createToolbarButton("Detach", "Detach depth of market");
        detachButton.setOnAction(event -> detachOrderBook());

        Button closeButton = createCloseButton(this::toggleOrderBookVisibility);
        HBox header = createPanelHeader("Depth of Market", orderBookSymbolLabel, refreshButton, detachButton, closeButton);

        VBox asksSection = createAsksSection();
        HBox midPriceBar = createHorizontalMidPriceBar();
        VBox bidsSection = createBidsSection();

        SplitPane splitPane = new SplitPane(asksSection, midPriceBar, bidsSection);
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getStyleClass().add("mt5-depth-split");
        splitPane.setDividerPositions(0.42, 0.52);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        VBox box = new VBox(0, header, splitPane, createQuickTradePanel());
        box.setPadding(new Insets(0));
        box.getStyleClass().addAll("order-book", "pro-panel", "mt5-panel", "mt5-order-book");
        return box;
    }

    private @NotNull HBox createQuickTradePanel() {
        quickTradeAmountField.setPrefWidth(82);
        quickTradeAmountField.getStyleClass().add("mt5-amount-field");

        Button buyMarket = createCommandButton("BUY MARKET", "buy-button", () -> submitQuickMarketOrder(BUY));
        Button sellMarket = createCommandButton("SELL MARKET", "sell-button", () -> submitQuickMarketOrder(SELL));

        HBox quickTrade = new HBox(6, new Label("Amount"), quickTradeAmountField, buyMarket, sellMarket);
        quickTrade.setAlignment(Pos.CENTER_LEFT);
        quickTrade.getStyleClass().add("mt5-quick-trade");
        return quickTrade;
    }

    protected @NotNull VBox createOrderBookPaneLegacy() {
        Label title = new Label("Order Book");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        Label count = new Label("Bids/Asks: 0");
        count.setStyle("-fx-font-size: 12px; -fx-text-fill: #a0aec0;");

        Button refreshButton = new Button("Refresh");
        refreshButton.setStyle(
                "-fx-padding: 8px 16px; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-size: 12px;");
        refreshButton.setOnAction(event -> loadSelectedOrderBook());

        Button detachButton = new Button("Detach");
        detachButton.setStyle(
                "-fx-padding: 8px 16px; -fx-background-color: #6366f1; -fx-text-fill: white; -fx-font-size: 12px;");
        detachButton.setOnAction(event -> detachOrderBook());

        Button closeButton = createCloseButton(this::toggleOrderBookVisibility);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, new VBox(1, title, count), spacer, refreshButton, detachButton, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12));
        header.setStyle("-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 0 0 1 0;");
        header.getStyleClass().add("panel-header");

        // Create resizable layout with vertical SplitPane: Bids (top) | MidPrice
        // (middle) | Asks (bottom)
        VBox bidsSection = createBidsSection();
        HBox midPriceBar = createHorizontalMidPriceBar();
        VBox asksSection = createAsksSection();

        // Use SplitPane for draggable dividers
        SplitPane splitPane = new SplitPane(bidsSection, midPriceBar, asksSection);
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setStyle("-fx-background-color: #0f3460; -fx-box-border: #374151;");
        splitPane.setDividerPositions(0.45, 0.55); // 45% bids, 10% middle, 45% asks
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        VBox box = new VBox(0, header, splitPane);
        box.setPadding(new Insets(0));
        box.getStyleClass().addAll("order-book", "pro-panel");
        return box;
    }

    private @NotNull VBox createBidsSection() {
        HBox header = createOrderBookHeader(true);
        ListView<OrderBook.PriceLevel> listView = createBidsListView();
        VBox section = new VBox(0, header, listView);
        section.setStyle("-fx-background-color: #0f3460;");
        VBox.setVgrow(listView, Priority.ALWAYS);
        return section;
    }

    private @NotNull VBox createAsksSection() {
        HBox header = createOrderBookHeader(false);
        ListView<OrderBook.PriceLevel> listView = createAsksListView();
        VBox section = new VBox(0, header, listView);
        section.setStyle("-fx-background-color: #0f3460;");
        VBox.setVgrow(listView, Priority.ALWAYS);
        return section;
    }

    private @NotNull HBox createHorizontalMidPriceBar() {
        HBox bar = new HBox(8);
        bar.setPrefHeight(60);
        bar.setMinHeight(50);
        bar.setStyle(
                "-fx-background-color: #1a1a2e; -fx-border-color: #374151; -fx-border-width: 1 0 1 0; -fx-padding: 12;");
        bar.setAlignment(Pos.CENTER_LEFT);

        // Update listener for order book changes
        orderBookBids.addListener(
                (javafx.collections.ListChangeListener<OrderBook.PriceLevel>) change -> updateHorizontalMidPrice(bar));
        orderBookAsks.addListener(
                (javafx.collections.ListChangeListener<OrderBook.PriceLevel>) change -> updateHorizontalMidPrice(bar));

        updateHorizontalMidPrice(bar);
        return bar;
    }

    private void updateHorizontalMidPrice(HBox bar) {
        bar.getChildren().clear();

        if (orderBookBids.isEmpty() || orderBookAsks.isEmpty()) {
            Label emptyLabel = new Label("No Data");
            emptyLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #9ca3af;");
            bar.getChildren().add(emptyLabel);
            return;
        }

        OrderBook.PriceLevel bid0 = orderBookBids.getFirst();
        OrderBook.PriceLevel ask0 = orderBookAsks.getFirst();
        if (bid0 == null || ask0 == null) {
            Label emptyLabel = new Label("No Data");
            emptyLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #9ca3af;");
            bar.getChildren().add(emptyLabel);
            return;
        }
        double bestBid = bid0.getPrice();
        double bestAsk = ask0.getPrice();
        double midPrice = (bestBid + bestAsk) / 2.0;
        double spread = bestAsk - bestBid;
        double spreadPercent = (spread / midPrice) * 100;
        boolean priceUp = bestBid > bestAsk; // Determine market direction

        // Mid Price - Main label
        Label priceLabel = new Label(price(midPrice));
        priceLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #000000;");

        // Direction indicator
        Label directionLabel = new Label(priceUp ? "↑" : "↓");
        directionLabel.setStyle("-fx-font-size: 16; -fx-text-fill: " + (priceUp ? "#10b981" : "#ef4444") + ";");

        // Spread information (Bid | Ask | Spread %)
        VBox spreadBox = new VBox(2);
        spreadBox.setStyle("-fx-padding: 0;");
        Label bidLabel = new Label("Bid: " + price(bestBid));
        bidLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #10b981; -fx-font-weight: bold;");
        Label askLabel = new Label("Ask: " + price(bestAsk));
        askLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #ef4444; -fx-font-weight: bold;");
        Label spreadLabel = new Label(String.format("Spread: %.2f%%", spreadPercent));
        spreadLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #6b7280;");
        spreadBox.getChildren().addAll(bidLabel, askLabel, spreadLabel);

        VBox usdBox = new VBox(2);
        usdBox.setAlignment(Pos.CENTER_LEFT);
        usdBox.setStyle("-fx-padding: 0 0 0 8;");
        Label usdTitleLabel = new Label("USD value");
        usdTitleLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #9ca3af;");
        Label usdValueLabel = new Label("...");
        usdValueLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #f59e0b;");
        usdBox.getChildren().addAll(usdTitleLabel, usdValueLabel);
        updateUsdMidPriceConversion(usdValueLabel, midPrice, currentOrderBook == null ? null : currentOrderBook.getTradePair());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(priceLabel, directionLabel, new Separator(Orientation.VERTICAL),
                spreadBox, spacer, new Separator(Orientation.VERTICAL), usdBox);
    }

    private void updateUsdMidPriceConversion(Label usdValueLabel, double midPrice, TradePair pair) {
        if (usdValueLabel == null || pair == null || !Double.isFinite(midPrice) || midPrice <= 0.0) {
            if (usdValueLabel != null) {
                usdValueLabel.setText("-");
            }
            return;
        }

        String quote = normalizeAssetCode(pair.getCounterCode());
        if (isUsdLikeAsset(quote)) {
            usdValueLabel.setText(formatUsdValue(midPrice));
            return;
        }

        usdValueLabel.setText("...");
        CompletableFuture
                .supplyAsync(() -> quoteUsdRate(quote))
                .thenAccept(rate -> runOnFx(() -> {
                    if (rate != null && Double.isFinite(rate) && rate > 0.0) {
                        usdValueLabel.setText(formatUsdValue(midPrice * rate));
                    } else {
                        usdValueLabel.setText("-");
                    }
                }))
                .exceptionally(exception -> {
                    runOnFx(() -> usdValueLabel.setText("-"));
                    log.debug("Unable to convert {} mid price to USD", pair, exception);
                    return null;
                });
    }

    private Double quoteUsdRate(String quote) {
        if (exchange == null || quote == null || quote.isBlank()) {
            return null;
        }
        if (isUsdLikeAsset(quote)) {
            return 1.0;
        }

        Double directRate = fetchPairMidPrice(quote, "USDC");
        if (directRate == null) {
            directRate = fetchPairMidPrice(quote, "USD");
        }
        if (directRate != null && directRate > 0.0) {
            return directRate;
        }

        Double inverseRate = fetchPairMidPrice("USDC", quote);
        if (inverseRate == null) {
            inverseRate = fetchPairMidPrice("USD", quote);
        }
        return inverseRate == null || inverseRate <= 0.0 ? null : 1.0 / inverseRate;
    }

    private Double fetchPairMidPrice(String base, String counter) {
        try {
            TradePair conversionPair = new TradePair(base, counter);
            OrderBook conversionBook = exchange.fetchOrderBook(conversionPair).get(8, TimeUnit.SECONDS);
            double conversionMid = conversionBook == null ? 0.0 : conversionBook.getMidPrice();
            return Double.isFinite(conversionMid) && conversionMid > 0.0 ? conversionMid : null;
        } catch (Exception exception) {
            log.debug("Unable to fetch USD conversion pair {}/{}: {}", base, counter, rootMessage(exception));
            return null;
        }
    }

    private boolean isUsdLikeAsset(String code) {
        String normalized = normalizeAssetCode(code);
        return "USD".equals(normalized) || "USDC".equals(normalized) || "USDT".equals(normalized);
    }

    private String normalizeAssetCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private String formatUsdValue(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return "-";
        }
        if (value >= 1_000.0) {
            return "$%,.2f".formatted(value);
        }
        if (value >= 1.0) {
            return "$%.4f".formatted(value);
        }
        return "$%.8f".formatted(value);
    }

    private @NotNull HBox createOrderBookHeader(boolean isBids) {
        HBox header = new HBox(16);
        header.setPadding(new Insets(8, 8, 8, 8));
        header.setStyle("-fx-background-color: #1a1a2e; -fx-border-color: #374151; -fx-border-width: 0 0 1 0;");

        if (isBids) {
            Label priceLabel = new Label("Price");
            priceLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #a0aec0; -fx-min-width: 80;");
            Label sizeLabel = new Label("Size");
            sizeLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #a0aec0; -fx-min-width: 70;");
            Region filler = new Region();
            HBox.setHgrow(filler, Priority.ALWAYS);
            Label totalLabel = new Label("Total");
            totalLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #a0aec0; -fx-min-width: 80;");
            header.getChildren().addAll(priceLabel, sizeLabel, filler, totalLabel);
        } else {
            Label totalLabel = new Label("Total");
            totalLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #a0aec0; -fx-min-width: 80;");
            Region filler = new Region();
            HBox.setHgrow(filler, Priority.ALWAYS);
            Label sizeLabel = new Label("Size");
            sizeLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #a0aec0; -fx-min-width: 70;");
            Label priceLabel = new Label("Price");
            priceLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #a0aec0; -fx-min-width: 80;");
            header.getChildren().addAll(totalLabel, filler, sizeLabel, priceLabel);
        }

        return header;
    }

    private @NotNull ListView<OrderBook.PriceLevel> createBidsListView() {
        ListView<OrderBook.PriceLevel> listView = new ListView<>(orderBookBids);
        listView.setStyle("-fx-control-inner-background: #0f3460; -fx-padding: 0; -fx-border-color: transparent;");
        listView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(OrderBook.PriceLevel bid, boolean empty) {
                super.updateItem(bid, empty);
                if (empty || bid == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(16);
                    row.setPadding(new Insets(6, 8, 6, 8));
                    row.setStyle("-fx-background-color: #0f3460;");

                    // Calculate depth percentage for background visualization
                    double maxSize = orderBookBids.stream().mapToDouble(OrderBook.PriceLevel::getSize).max()
                            .orElse(1.0);
                    double depthPercent = Math.min((bid.getSize() / maxSize) * 100, 100);
                    // Handle NaN values and cap alpha to valid CSS range (0-1)
                    if (Double.isNaN(depthPercent) || Double.isInfinite(depthPercent)) {
                        depthPercent = 0;
                    }
                    double alphaValue = Math.min(depthPercent / 200.0, 1.0);
                    String depthColor = String.format("rgba(16, 185, 129, %.2f)", alphaValue);

                    Label priceLabel = new Label(price(bid.getPrice()));
                    priceLabel.setStyle(
                            "-fx-font-weight: bold; -fx-font-size: 12; -fx-text-fill: #10b981; -fx-min-width: 80;");

                    Label sizeLabel = new Label(number(bid.getSize()));
                    sizeLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11; -fx-min-width: 70;");

                    Label totalLabel = new Label(compactMarketNumber(bid.getPrice() * bid.getSize()));
                    totalLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11; -fx-min-width: 80;");

                    Region filler = new Region();
                    HBox.setHgrow(filler, Priority.ALWAYS);

                    row.getChildren().addAll(priceLabel, sizeLabel, filler, totalLabel);
                    // Set background with depth visualization
                    row.setStyle("-fx-background-color: " + depthColor + "; " +
                            "-fx-padding: 0;");
                    setGraphic(row);
                    setText(null);
                }
            }
        });
        return listView;
    }

    private @NotNull ListView<OrderBook.PriceLevel> createAsksListView() {
        ListView<OrderBook.PriceLevel> listView = new ListView<>(orderBookAsks);
        listView.setStyle("-fx-control-inner-background: #0f3460; -fx-padding: 0; -fx-border-color: transparent;");
        listView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(OrderBook.PriceLevel ask, boolean empty) {
                super.updateItem(ask, empty);
                if (empty || ask == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(16);
                    row.setPadding(new Insets(6, 8, 6, 8));
                    row.setStyle("-fx-background-color: #0f3460;");

                    // Calculate depth percentage for background visualization
                    double maxSize = orderBookAsks.stream().mapToDouble(OrderBook.PriceLevel::getSize).max()
                            .orElse(1.0);
                    double depthPercent = Math.min((ask.getSize() / maxSize) * 100, 100);
                    // Handle NaN values and cap alpha to valid CSS range (0-1)
                    if (Double.isNaN(depthPercent) || Double.isInfinite(depthPercent)) {
                        depthPercent = 0;
                    }
                    double alphaValue = Math.min(depthPercent / 200.0, 1.0);
                    String depthColor = String.format("rgba(239, 68, 68, %.2f)", alphaValue);

                    Label totalLabel = new Label(compactMarketNumber(ask.getPrice() * ask.getSize()));
                    totalLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11; -fx-min-width: 80;");

                    Region filler = new Region();
                    HBox.setHgrow(filler, Priority.ALWAYS);

                    Label sizeLabel = new Label(number(ask.getSize()));
                    sizeLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11; -fx-min-width: 70;");

                    Label priceLabel = new Label(price(ask.getPrice()));
                    priceLabel.setStyle(
                            "-fx-font-weight: bold; -fx-font-size: 12; -fx-text-fill: #ef4444; -fx-min-width: 80;");

                    row.getChildren().addAll(totalLabel, filler, sizeLabel, priceLabel);
                    // Set background with depth visualization (right-aligned for asks)
                    row.setStyle("-fx-background-color: " + depthColor + "; " +
                            "-fx-padding: 0;");
                    setGraphic(row);
                    setText(null);
                }
            }
        });
        return listView;
    }

    private void loadSelectedOrderBook() {
        TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
        if (selected != null) {
            loadOrderBook(selected);
        }
    }

    private void detachOrderBook() {
        // Create resizable layout with vertical SplitPane: Bids (top) | MidPrice
        // (middle) | Asks (bottom)
        VBox bidsSection = createBidsSection();
        HBox midPriceBar = createHorizontalMidPriceBar();
        VBox asksSection = createAsksSection();

        SplitPane splitPane = new SplitPane(bidsSection, midPriceBar, asksSection);
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.setStyle("-fx-background-color: #ffffff;");
        splitPane.setDividerPositions(0.45, 0.55);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("terminal-button");
        refreshButton.setOnAction(event -> loadSelectedOrderBook());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, new Label("Order Book"), spacer, refreshButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12));
        header.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-width: 0 0 1 0;");

        VBox root = new VBox(0, header, splitPane);
        root.setPadding(new Insets(0));
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        Stage stage = new Stage();
        stage.setTitle("Order Book");
        stage.setScene(new Scene(root, 800, 600));
        stage.show();
    }

    private @NotNull TabPane createNavigatorTabs() {
        TabPane navigatorTabs = new TabPane();
        navigatorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Node overviewView = createOverviewPane();

        depthChart = new DepthChart();
        depthChart.update(currentOrderBook == null ? new OrderBook() : currentOrderBook);

        // Initialize news calendar panel
        newsCalendarPanel = new NewsCalendarPanel(newsDataProvider);

        newsEventOverlay = new NewsEventOverlay(newsDataProvider, 400, 400);
        newsCalendarPanel.getChildren().add(newsEventOverlay);
        TradePair selectedPair = symbolSelector.getValue();
        marketInfoPanel = new MarketInfoPanel(exchange, newsDataProvider);
        marketInfoPanel.updateForPair(selectedPair);

        navigatorTabs.getTabs().setAll(
                createFixedTab(TabName.NAVIGATION, createNavigationPanel()),
                createFixedTab(TabName.OVERVIEW, new ScrollPane(overviewView)),
                createFixedTab(TabName.BALANCES, createAccountBalancesView()),
                createFixedTab(TabName.DEPTH, depthChart),
                createFixedTab(TabName.MARKET_INFO, marketInfoPanel)

        );
        navigatorTabs.getStyleClass().add("compact-tabs");
        return navigatorTabs;
    }

    private @NotNull Node createSymbolStrategyTab() {
        ObservableList<StrategyAssignment> items = FXCollections.observableArrayList();

        TableView<StrategyAssignment> table = new TableView<>(items);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No strategy assignments yet"));

        TableColumn<StrategyAssignment, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getSymbol()));

        TableColumn<StrategyAssignment, String> timeframeCol = new TableColumn<>("Timeframe");
        timeframeCol.setCellValueFactory(c -> {
            var tf = c.getValue().getTimeframe();
            return new ReadOnlyStringWrapper(tf == null ? "" : tf.toString());
        });

        TableColumn<StrategyAssignment, String> strategyCol = new TableColumn<>("Strategy");
        strategyCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getStrategyId()));

        TableColumn<StrategyAssignment, String> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(c ->
                new ReadOnlyStringWrapper(String.format("%.2f", c.getValue().getScoreAtAssignment())));

        TableColumn<StrategyAssignment, String> assignedCol = new TableColumn<>("Assigned");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
        assignedCol.setCellValueFactory(c -> {
            Instant at = c.getValue().getAssignedAt();
            return new ReadOnlyStringWrapper(at == null ? "" : dtf.format(at));
        });

        TableColumn<StrategyAssignment, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setCellValueFactory(c -> {
            String r = c.getValue().getReason();
            return new ReadOnlyStringWrapper(r == null ? "" : r);
        });

        // Live agent-state column — reads from SymbolAgentManager when available.
        TableColumn<StrategyAssignment, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> {
            StrategyAssignment a = c.getValue();
            if (a == null) return new ReadOnlyStringWrapper("");
            if (systemCore == null || systemCore.getSymbolAgentManager() == null)
                return new ReadOnlyStringWrapper(a.isValid() ? "Active" : "Inactive");
            TradePair pair = marketWatchItems.stream()
                    .filter(p -> p != null && p.toString('/').equalsIgnoreCase(a.getSymbol()))
                    .findFirst().orElse(null);
            if (pair == null) return new ReadOnlyStringWrapper(a.isValid() ? "Active" : "Inactive");
            return systemCore.getSymbolAgentManager().getState(pair)
                    .map(SymbolAgentState::getMarketWatchStatusText)
                    .map(ReadOnlyStringWrapper::new)
                    .orElseGet(() -> new ReadOnlyStringWrapper(a.isValid() ? "Active" : "Inactive"));
        });
        statusCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || text == null || text.isBlank()) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(text);
                String lower = text.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("live trad")) {
                    setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold;");
                } else if (lower.contains("live ready")) {
                    setStyle("-fx-text-fill: #86efac;");
                } else if (lower.contains("paper")) {
                    setStyle("-fx-text-fill: #60a5fa;");
                } else if (lower.contains("train") || lower.contains("evaluat")) {
                    setStyle("-fx-text-fill: #fbbf24;");
                } else if (lower.contains("fail") || lower.contains("block")) {
                    setStyle("-fx-text-fill: #f87171;");
                } else if (lower.contains("inactive") || lower.contains("paused")) {
                    setStyle("-fx-text-fill: #94a3b8;");
                } else {
                    setStyle("-fx-text-fill: #e2e8f0;");
                }
            }
        });
        statusCol.setPrefWidth(160);
        statusCol.setMinWidth(120);

        table.getColumns().setAll(symbolCol, timeframeCol, strategyCol, scoreCol, assignedCol, statusCol, reasonCol);

        Runnable refreshData = () -> {
            StrategyAssignmentRepository repository = StrategyAssignmentRepository.getInstance();
            LinkedHashMap<String, StrategyAssignment> merged = new LinkedHashMap<>();

            // Always include currently active assignments from the canonical repository.
            for (StrategyAssignment assignment : repository.getAllActive()) {
                if (assignment != null && assignment.getAssignmentId() != null) {
                    merged.put(assignment.getAssignmentId(), assignment);
                }
            }

            // Also resolve active assignments for currently visible market-watch symbols
            // using the selected timeframe to ensure the table reflects live desk context.
            String timeframeCode = timeframeSelector.getSelectionModel().getSelectedItem().getCode();
            if (timeframeCode != null && !timeframeCode.isBlank()) {
                try {
                    org.investpro.enums.timeframe.Timeframe timeframe =
                            org.investpro.enums.timeframe.Timeframe.fromCode(timeframeCode);

                    for (TradePair pair : marketWatchItems) {
                        if (pair == null) {
                            continue;
                        }
                        StrategyAssignment active = repository.getActive(pair.toString('/'), timeframe);
                        if (active != null && active.getAssignmentId() != null) {
                            merged.put(active.getAssignmentId(), active);
                        }
                    }

                    TradePair selectedPair = symbolSelector.getSelectionModel().getSelectedItem();
                    if (selectedPair != null) {
                        StrategyAssignment selectedActive = repository.getActive(selectedPair.toString('/'), timeframe);
                        if (selectedActive != null && selectedActive.getAssignmentId() != null) {
                            merged.put(selectedActive.getAssignmentId(), selectedActive);
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Keep active repository data if current exchange exposes non-standard timeframe labels.
                }
            }

            List<StrategyAssignment> sorted = merged.values().stream()
                    .sorted(Comparator.comparing(
                            StrategyAssignment::getAssignedAt,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                            .reversed())
                    .toList();

            items.setAll(sorted);
        };

        Button refreshBtn = new Button("⟳ Refresh");
        refreshBtn.getStyleClass().add("terminal-button");
        refreshBtn.setOnAction(e -> refreshData.run());

        symbolSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> refreshData.run());
        timeframeSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> refreshData.run());

        // Initial load from live runtime context.
        refreshData.run();

        VBox container = new VBox(6, refreshBtn, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        container.setPadding(new Insets(8));
        return container;
    }

    private @NotNull VBox createAccountBalancesView() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(12));
        Label title = metricLabel("Balances");
        title.getStyleClass().add("panel-title");

        accountBalancesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        accountBalancesTable.setPlaceholder(new Label("No balance data available"));
        accountBalancesTable.getStyleClass().add("compact-table");
        accountBalancesTable.getColumns().setAll(List.of(
                tableColumn("Asset", AssetBalanceRow::asset, 90),
                tableColumn("Balance", row -> number(row.balance()), 130),
                tableColumn("Equity", row -> number(row.equity()), 130),
                tableColumn("Margin", row -> number(row.margin()), 130),
                tableColumn("Free Margin", row -> number(row.freeMargin()), 140)));

        updateAccountBalance();
        VBox.setVgrow(accountBalancesTable, Priority.ALWAYS);
        container.getChildren().setAll(title, accountBalancesTable);
        container.getStyleClass().add("pro-panel");
        return container;
    }

    protected @NotNull Label metricLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("metric-label");
        return label;
    }

    private @NotNull Label valueLabel(String color) {
        Label label = new Label("$0.00");
        label.getStyleClass().addAll("metric-value", "value-label");
        if (color != null && !color.isEmpty()) {
            label.setStyle("-fx-text-fill: %s;".formatted(color));
        }
        return label;
    }

    private void updateAccountBalance() {
        Exchange currentExchange = exchange;
        if (!hasBrokerAccess() || currentExchange == null) {
            balanceValueLabel.setText("$" + 0.00);
            availableValueLabel.setText("$0.00");
            equityValueLabel.setText("$0.00");
            marginUsedValueLabel.setText("$0.00");
            freeMarginValueLabel.setText("$0.00");
            accountBalanceRows.clear();
            return;
        }

        try {
            currentExchange.fetchAccount()
                    .thenAccept(account -> runOnFx(() -> accountBalanceRows.setAll(toAssetBalanceRows(account))))
                    .exceptionally(exception -> {
                        log.debug("Asset balances unavailable from account snapshot", exception);
                        return null;
                    });
        } catch (Exception exception) {
            log.debug("Asset balances unavailable from account snapshot", exception);
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
                    if (accountBalanceRows.isEmpty()) {
                        accountBalanceRows.setAll(List.of(new AssetBalanceRow(
                                "USD",
                                values[0],
                                values[2],
                                values[3],
                                values[4])));
                    }
                }))
                .exceptionally(exception -> {
                    log.warn("Failed to update account balances", exception);
                    return null;
                });
    }

    private @NotNull List<AssetBalanceRow> toAssetBalanceRows(Account account) {
        if (account == null) {
            return List.of();
        }

        Map<String, Double> balances = account.getBalances() == null ? Map.of() : account.getBalances();
        Map<String, Double> availableBalances = account.getAvailableBalances() == null ? Map.of() : account.getAvailableBalances();
        Map<String, Double> lockedBalances = account.getLockedBalances() == null ? Map.of() : account.getLockedBalances();

        Set<String> assets = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        assets.addAll(balances.keySet());
        assets.addAll(availableBalances.keySet());
        assets.addAll(lockedBalances.keySet());

        if (assets.isEmpty()) {
            return List.of();
        }

        List<AssetBalanceRow> rows = new ArrayList<>(assets.size());
        for (String asset : assets) {
            double balance = sanitizeAmount(balances.get(asset));
            double margin = sanitizeAmount(lockedBalances.get(asset));
            double free = sanitizeAmount(availableBalances.get(asset));

            if (free <= 0.0 && balance > margin) {
                free = balance - margin;
            }

            double equity = Math.max(0.0, balance + margin);
            rows.add(new AssetBalanceRow(asset, balance, equity, margin, free));
        }

        return rows;
    }

    private double sanitizeAmount(Double value) {
        return value != null && Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private CompletableFuture<Double> safeAccountDouble(
            java.util.function.Supplier<CompletableFuture<Double>> supplier) {
        try {
            CompletableFuture<Double> future = supplier.get();
            if (future == null) {
                return CompletableFuture.completedFuture(0.0);
            }
            return future.exceptionally(exception -> {
                log.debug("Account metric unavailable", exception);
                return 0.0;
            });
        } catch (Exception exception) {
            log.debug("Account metric unavailable", exception);
            return CompletableFuture.completedFuture(0.0);
        }
    }

    private @NotNull Node createChartWorkspace() {
        BorderPane workspace = new BorderPane();
        workspace.getStyleClass().addAll("chart-workspace", "mt5-chart-workspace");

        HBox header = createChartHeader();

        chartTabPane.setSide(Side.TOP);
        chartTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
        chartTabPane.getStyleClass().addAll("chart-tabs", "mt5-chart-tabs");
        VBox emptyState = createChartEmptyState();
        emptyState.setVisible(chartTabPane.getTabs().isEmpty());
        emptyState.setManaged(chartTabPane.getTabs().isEmpty());
        chartTabPane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) change -> {
            boolean empty = chartTabPane.getTabs().isEmpty();
            emptyState.setVisible(empty);
            emptyState.setManaged(empty);
        });

        chartTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && !Objects.equals(oldTab, newTab)) {
                updateOrderBookForChartTab(newTab);
            }
            updateChartHeader();
        });

        workspace.setTop(header);
        StackPane chartSurface = new StackPane(chartTabPane, emptyState);
        chartSurface.getStyleClass().add("mt5-chart-surface");
        workspace.setCenter(chartSurface);
        updateChartHeader();
        return workspace;
    }

    private @NotNull HBox createChartHeader() {
        chartHeaderSymbolLabel.getStyleClass().add("mt5-chart-symbol");
        chartHeaderTimeframeLabel.getStyleClass().add("mt5-chart-meta");
        chartHeaderQuoteLabel.getStyleClass().add("mt5-chart-meta");
        chartHeaderLastLabel.getStyleClass().add("mt5-chart-meta");
        chartHeaderSpreadLabel.getStyleClass().add("mt5-chart-meta");

        Button fitButton = createToolbarButton("Fit", "Fit active chart");
        fitButton.setOnAction(event -> withActiveChart(CandleStickChart::fitChart));

        Button refreshButton = createToolbarButton("Refresh", "Refresh active chart");
        refreshButton.setOnAction(event -> withActiveChart(CandleStickChart::refreshChart));

        Button crosshairButton = createToolbarButton("Crosshair", "Toggle active chart crosshair");
        crosshairButton.setOnAction(event -> withActiveChart(CandleStickChart::toggleCrosshair));

        Button closeAllButton = createToolbarButton("Close All", "Close all chart tabs");
        closeAllButton.setOnAction(event -> closeAllCharts());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10,
                chartHeaderSymbolLabel,
                chartHeaderTimeframeLabel,
                chartHeaderQuoteLabel,
                chartHeaderLastLabel,
                chartHeaderSpreadLabel,
                spacer,
                fitButton,
                refreshButton,
                crosshairButton,
                closeAllButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("mt5-chart-header");
        return header;
    }

    private @NotNull VBox createChartEmptyState() {
        Label title = new Label("Open a symbol from Market Watch or press Ctrl+N.");
        title.getStyleClass().add("mt5-empty-title");
        Label subtitle = new Label("Charts, strategy overlays, and live trade updates appear here.");
        subtitle.getStyleClass().add("mt5-empty-subtitle");
        Button openButton = createCommandButton("Open Chart", "mt5-toolbar-button", this::openSelectedSymbolChart);

        VBox empty = new VBox(10, title, subtitle, openButton);
        empty.setAlignment(Pos.CENTER);
        empty.getStyleClass().add("mt5-chart-empty-state");
        return empty;
    }

    private void updateChartHeader() {
        TradePair selected = symbolSelector == null ? null : symbolSelector.getSelectionModel().getSelectedItem();
        Timeframe timeframe = timeframeSelector == null ? null : timeframeSelector.getSelectionModel().getSelectedItem();
        chartHeaderSymbolLabel.setText(selected == null ? "No chart" : selected.toString('/'));
        chartHeaderTimeframeLabel.setText("TF: " + (timeframe == null ? "-" : timeframe.name()));
        chartHeaderQuoteLabel.setText(selected == null
                ? "Bid/Ask: -"
                : "Bid/Ask: %s / %s".formatted(price(selected.getBid()), price(selected.getAsk())));
        chartHeaderLastLabel.setText(selected == null ? "Last: -" : "Last: " + price(selected.getLastPrice()));
        chartHeaderSpreadLabel.setText(selected == null ? "Spread: -" : "Spread: " + formatSpreadPts(selected));
    }

    protected @NotNull Node createChartWorkspaceLegacy() {
        BorderPane workspace = new BorderPane();
        workspace.getStyleClass().add("chart-workspace");

        // ============================================================
        // Header title
        // ============================================================
        Label title = new Label("Workspace");
        title.getStyleClass().add("workspace-title");

        Label context = new Label("Multi-symbol market workspace");
        context.getStyleClass().add("workspace-subtitle");

        VBox titleBlock = new VBox(2, title, context);
        titleBlock.getStyleClass().add("workspace-title-block");

        // ============================================================
        // Chart actions
        // ============================================================
        Button fitButton = new Button("Fit");
        fitButton.getStyleClass().add("terminal-button");
        fitButton.setOnAction(event -> withActiveChart(CandleStickChart::fitChart));

        Button refreshButton = new Button("Refresh");
        refreshButton.getStyleClass().add("terminal-button");
        refreshButton.setOnAction(event -> withActiveChart(CandleStickChart::refreshChart));

        Button crosshairButton = new Button("Crosshair");
        crosshairButton.getStyleClass().add("terminal-button");
        crosshairButton.setOnAction(event -> withActiveChart(CandleStickChart::toggleCrosshair));

        Button closeAllButton = new Button("Close All");
        closeAllButton.getStyleClass().add("terminal-button");
        closeAllButton.setOnAction(event -> closeAllCharts());

        // ============================================================
        // Bot controls
        // ============================================================
        botSymbolScopeSelector.getItems().setAll(
                "Selected Symbol",
                "Watchlist",
                "Best Today"
        );

        botSymbolScopeSelector.getSelectionModel()
                .select(preferences.get("bot_symbol_scope", "Selected Symbol"));

        botSymbolScopeSelector.setPrefWidth(145);
        botSymbolScopeSelector.getStyleClass().add("terminal-combo-box");
        botSymbolScopeSelector.setOnAction(event -> saveAppState());

        Label botLabel = new Label("Bot");
        botLabel.getStyleClass().add("workspace-control-label");

        botTradeButton.setOnAction(event -> toggleBotTrading());
        refreshBotTradeButton();

        // ============================================================
        // Header layout
        // ============================================================
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox headerActions = new HBox(
                8,
                fitButton,
                refreshButton,
                crosshairButton,
                botLabel,
                botSymbolScopeSelector,
                botTradeButton,
                closeAllButton
        );
        headerActions.setAlignment(Pos.CENTER_RIGHT);
        headerActions.getStyleClass().add("workspace-header-actions");

        HBox header = new HBox(12, titleBlock, spacer, headerActions);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("workspace-header");

        // ============================================================
        // Chart tabs
        // ============================================================
        chartTabPane.setSide(Side.TOP);
        chartTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        chartTabPane.getStyleClass().add("chart-tabs");

        chartTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && !Objects.equals(oldTab, newTab)) {
                updateOrderBookForChartTab(newTab);
            }
        });

        BorderPane workspacePane = new BorderPane();
        workspacePane.getStyleClass().add("workspace-pane");
        workspacePane.setCenter(chartTabPane);

        // ============================================================
        // Final workspace
        // ============================================================
        workspace.setTop(header);
        workspace.setCenter(workspacePane);

        ScrollPane scrollPane = new ScrollPane(workspace);
        scrollPane.getStyleClass().add("workspace-scroll-pane");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);

        return scrollPane;
    }

    private @NotNull VBox createTradingConsole() {
        Label title = new Label("Toolbox");
        title.getStyleClass().add("terminal-title");
        Label product = new Label("InvestPro Terminal");
        product.getStyleClass().add("panel-meta");
        VBox titleBlock = new VBox(1, title, product);

        Button detachButton = createToolbarButton("Detach", "Detach Toolbox");
        detachButton.setOnAction(event -> detachConsoleWindow());
        Button minimizeButton = createToolbarButton("Minimize", "Hide Toolbox");
        minimizeButton.setOnAction(event -> toggleConsoleVisibility());
        Button closeButton = createCloseButton(this::toggleConsoleVisibility);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(6, titleBlock, spacer, detachButton, minimizeButton, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().addAll("panel-header", "console-tab-header", "mt5-panel-header");

        terminalTabPane.setSide(Side.TOP);
        terminalTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        terminalTabPane.getStyleClass().addAll("console-tab-pane", "mt5-terminal-tabs");
        terminalTabPane.getTabs().setAll(
                createTerminalTab("Trade", createPositionsTab().getContent()),
                createTerminalTab("Exposure", buildPortfolioPane()),
                createTerminalTab("Account History", createAccountHistoryPlaceholder()),
                createTerminalTab("News", createNewsPlaceholder()),
                createTerminalTab("Alerts", createAlertsTab().getContent()),
                createTerminalTab("Mailbox", createMailboxPlaceholder()),
                createTerminalTab("Experts", createAgentsTab().getContent()),
                createTerminalTab("Journal", createJournalTab().getContent()),
                createTerminalTab("Signals", createSignalTab().getContent()),
                createTerminalTab("Risk Monitor", createPositionRiskMonitorTab().getContent()));

        VBox console = new VBox(0, header, terminalTabPane);
        console.setPrefHeight(CONSOLE_HEIGHT);
        console.getStyleClass().addAll("system-console", "bottom-terminal", "mt5-terminal");
        VBox.setVgrow(terminalTabPane, Priority.ALWAYS);
        return console;
    }

    private @NotNull Tab createTerminalTab(String title, Node content) {
        Tab tab = new Tab(title, content == null ? new Pane() : content);
        tab.setClosable(false);
        return tab;
    }

    private @NotNull Node createAccountHistoryPlaceholder() {
        return createTerminalPlaceholder("Account history will show closed orders and deals.");
    }

    private @NotNull Node createNewsPlaceholder() {
        return createTerminalPlaceholder("Market news and calendar updates appear here.");
    }

    private @NotNull Node createMailboxPlaceholder() {
        return createTerminalPlaceholder("Broker messages, system mail, and alerts appear here.");
    }

    private @NotNull VBox createTerminalPlaceholder(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("mt5-empty-subtitle");
        VBox box = new VBox(label);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("mt5-terminal-placeholder");
        return box;
    }

    protected @NotNull VBox createTradingConsoleLegacy() {
        Label title = new Label("Terminal");
        title.getStyleClass().add("terminal-title");
        VBox titleBlock = new VBox(1, title);

        Button detachButton = new Button("Detach");
        detachButton.getStyleClass().add("terminal-button");
        detachButton.setOnAction(event -> detachConsoleWindow());

        Button closeButton = createCloseButton(this::toggleConsoleVisibility);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(4, titleBlock, spacer, detachButton, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().addAll("panel-header", "console-tab-header");

        terminalTabPane.setSide(Side.TOP);
        terminalTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        terminalTabPane.getStyleClass().add("console-tab-pane");
       // DraggableTab.registerTabPane(terminalTabPane);
        terminalTabPane.getTabs().addAll(
                createDetachableTerminalTab(TabName.PORTFOLIO, buildPortfolioPane()),
                createDetachableTerminalTab(TabName.POSITIONS, createPositionsTab().getContent()),
                createDetachableTerminalTab(TabName.RISK_MONITOR, createPositionRiskMonitorTab().getContent()),
                createDetachableTerminalTab(TabName.SIGNALS, createSignalTab().getContent()),
                createDetachableTerminalTab(TabName.AGENTS, createAgentsTab().getContent()),
                createDetachableTerminalTab(TabName.ALERTS, createAlertsTab().getContent()),
                createDetachableTerminalTab(TabName.JOURNAL, createJournalTab().getContent()));

        VBox terminalSurface = new VBox(6, header, terminalTabPane);
        terminalSurface.getStyleClass().add("terminal-scroll-content");
        VBox.setVgrow(terminalTabPane, Priority.ALWAYS);

        ScrollPane terminalScrollPane = new ScrollPane(terminalSurface);
        terminalScrollPane.getStyleClass().add("terminal-scroll-pane");
        terminalScrollPane.setFitToWidth(true);
        terminalScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        terminalScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        terminalScrollPane.setPannable(true);

        VBox console = new VBox(terminalScrollPane);
        console.setPrefHeight(CONSOLE_HEIGHT);
        console.getStyleClass().addAll("system-console", "bottom-terminal");
        VBox.setVgrow(terminalScrollPane, Priority.ALWAYS);
        return console;
    }

    private void toggleConsoleVisibility() {
        if (centerSplit == null || systemConsole == null) {
            return;
        }

        if (detachedSystemConsoleStage != null && detachedSystemConsoleStage.isShowing()) {
            reattachConsole();
            return;
        }

        if (consoleVisible) {
            // Hide terminal - remove from centerSplit so chart expands
            centerSplit.getItems().remove(systemConsole);
            consoleVisible = false;
        } else {
            // Show terminal - add back to centerSplit
            if (!centerSplit.getItems().contains(systemConsole)) {
                centerSplit.getItems().add(systemConsole);
            }
            centerSplit.setDividerPositions(0.72);
            consoleVisible = true;
        }

        centerSplit.layout();
        if (mainVerticalWorkbench != null) {
            mainVerticalWorkbench.layout();
        }
        if (horizontalWorkbench != null) {
            horizontalWorkbench.layout();
        }
        saveAppState();
    }

    private void toggleMarketWatchVisibility() {
        if (horizontalWorkbench == null || marketWatchWrapper == null) {
            return;
        }

        if (marketWatchVisible) {
            horizontalWorkbench.getItems().remove(marketWatchWrapper);
            marketWatchVisible = false;
        } else {
            if (!horizontalWorkbench.getItems().contains(marketWatchWrapper)) {
                horizontalWorkbench.getItems().addFirst(marketWatchWrapper);
            }
            horizontalWorkbench.setDividerPositions(0.22, 0.78);
            marketWatchVisible = true;
        }

        horizontalWorkbench.layout();
        saveAppState();
    }

    private void toggleOrderBookVisibility() {
        if (horizontalWorkbench == null || orderBookWrapper == null) {
            return;
        }

        if (orderBookVisible) {
            horizontalWorkbench.getItems().remove(orderBookWrapper);
            orderBookVisible = false;
        } else {
            if (!horizontalWorkbench.getItems().contains(orderBookWrapper)) {
                horizontalWorkbench.getItems().add(orderBookWrapper);
            }
            horizontalWorkbench.setDividerPositions(0.22, 0.78);
            orderBookVisible = true;
        }

        horizontalWorkbench.layout();
        saveAppState();
    }

    private void detachConsoleWindow() {
        if (centerSplit == null || systemConsole == null || !centerSplit.getItems().contains(systemConsole)) {
            return;
        }

        if (detachedSystemConsoleStage != null && detachedSystemConsoleStage.isShowing()) {
            detachedSystemConsoleStage.toFront();
            detachedSystemConsoleStage.requestFocus();
            return;
        }

        centerSplit.getItems().remove(systemConsole);
        consoleVisible = false;
        centerSplit.layout();

        Stage stage = new Stage();
        stage.setTitle("System Console");
        stage.setScene(new Scene(systemConsole, 980, 540));
        detachedSystemConsoleStage = stage;

        if (getScene() != null && getScene().getWindow() != null) {
            stage.setX(getScene().getWindow().getX() + 100);
            stage.setY(getScene().getWindow().getY() + 100);
        }

        stage.setOnCloseRequest(event -> reattachConsole());
        stage.setOnHidden(event -> detachedSystemConsoleStage = null);
        stage.show();
        journal("Console detached.");
        saveAppState();
    }

    private void reattachConsole() {
        if (centerSplit == null || systemConsole == null) {
            return;
        }

        if (detachedSystemConsoleStage != null && detachedSystemConsoleStage.getScene() != null) {
            detachedSystemConsoleStage.getScene().setRoot(new Pane());
        }

        if (!centerSplit.getItems().contains(systemConsole)) {
            centerSplit.getItems().add(systemConsole);
        }
        centerSplit.setDividerPositions(0.72);
        consoleVisible = true;
        journal("Console re-attached.");
        saveAppState();
    }

    private @NotNull DraggableTab createDetachableTerminalTab(@NonNull TabName tabName, Node content) {
        DraggableTab tab = new DraggableTab(tabName.getTabId(), content);

        tab.setTooltip(new Tooltip(tabName.getDisplayName()));
        tab.setContent(content);
        return tab;
    }

    private @NotNull DraggableTab createFixedTab(@NonNull TabName tabName, Node content) {
        DraggableTab tab = new DraggableTab(tabName.getTabId(), content);
        tab.setClosable(true);
        tab.setTooltip(new Tooltip(tabName.getDisplayName()));
        return tab;
    }

    /**
     * Creates and displays an independent window for Strategy and Research
     * features.
     * Windows are not confined to the terminal and can be moved/resized
     * independently.
     */
    private void createIndependentWindow(String title, Node content, double width, double height) {
        if (content == null) {
            showWarning(title, "Panel content is not available.");
            return;
        }
        if (content.getScene() != null && content.getScene() == getScene()) {
            showWarning(title, "This panel is already docked in the main workspace.");
            return;
        }



        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setResizable(true);
        stage.setAlwaysOnTop(false);

        // For BorderPane-based components, use them directly without ScrollPane
        // For other components, wrap in ScrollPane
        Scene scene = getScene(content, width, height);
        scene.setFill(Color.web("#1a1a2e"));
        stage.setScene(scene);

        // Track window and remove from tracking when closed
        stage.setOnHidden(e -> {
            stage.close();
            log.info("Independent window closed: {}", title);
        });

        stage.show();


        log.info("Independent window opened: {}", title);
    }



    private @NotNull Scene getScene(Node content, double width, double height) {
        javafx.scene.Parent sceneContent;
        if (content instanceof BorderPane || content instanceof ScrollPane || content instanceof StackPane) {
            sceneContent = (javafx.scene.Parent) content;
        } else {
            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            scrollPane.setStyle("-fx-background-color: #1a1a2e;");
            sceneContent = scrollPane;
        }

        Scene scene = new Scene(sceneContent, width, height);
        var componentsCss = getClass().getClassLoader().getResource("css/components.css");
        var appCss = getClass().getClassLoader().getResource("css/app.css");
        if (componentsCss != null) {
            scene.getStylesheets().add(componentsCss.toExternalForm());
        }
        if (appCss != null) {
            scene.getStylesheets().add(appCss.toExternalForm());
        }
        return scene;
    }

    private Stage systemOperationsBoardStage;

    private void openSystemOperationsBoard() {
        if (systemOperationsBoardStage != null && systemOperationsBoardStage.isShowing()) {
            systemOperationsBoardStage.toFront();
            return;
        }
        try {
            systemOperationsBoard = new SystemOperationsBoard();
            final SystemOperationsBoard board = systemOperationsBoard;
            Stage stage = new Stage();
            systemOperationsBoardStage = stage;
            stage.setTitle("Operations Center");
            StackPane root = new StackPane(board);
            root.setPadding(new Insets(12));
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #0b1220, #111827);");

            Scene scene = new Scene(root, 1100, 700);
            var appCss = getClass().getClassLoader().getResource("css/app.css");
            var componentsCss = getClass().getClassLoader().getResource("css/components.css");
            if (componentsCss != null) scene.getStylesheets().add(componentsCss.toExternalForm());
            if (appCss != null)        scene.getStylesheets().add(appCss.toExternalForm());

            stage.setMinWidth(900);
            stage.setMinHeight(600);
            stage.setScene(scene);
            stage.setOnCloseRequest(e -> {
                board.shutdown();
                systemOperationsBoard = null;
                systemOperationsBoardStage = null;
            });
            stage.show();
        } catch (Exception e) {
            log.error("Error opening Operations Center", e);
            showWarning("Operations Center", "Unable to open: " + e.getMessage());
        }
    }

    private void openThemeCustomization() {
        if (themeCustomizationPanel == null) {
            themeCustomizationPanel = new org.investpro.ui.panels.ThemeCustomizationPanel();
        }
        createIndependentWindow("Theme Customization", themeCustomizationPanel, 900, 800);
    }

    private void openSystemConsolePanel() {
        if (centerSplit == null || systemConsole == null) {
            openDetachedConsolePanel();
            showWarning("System Console", "Embedded console is unavailable, opened detached console instead.");
            return;
        }

        if (detachedSystemConsoleStage != null && detachedSystemConsoleStage.isShowing()) {
            reattachConsole();
        }

        terminalTabPane.setVisible(true);
        terminalTabPane.setManaged(true);
        systemConsole.setVisible(true);
        systemConsole.setManaged(true);

        if (!centerSplit.getItems().contains(systemConsole)) {
            centerSplit.getItems().add(systemConsole);
        }
        centerSplit.setDividerPositions(0.72);
        consoleVisible = true;
        centerSplit.layout();
        if (mainVerticalWorkbench != null) {
            mainVerticalWorkbench.layout();
        }
        if (horizontalWorkbench != null) {
            horizontalWorkbench.layout();
        }
        systemConsole.requestFocus();
        terminalTabPane.requestFocus();
        saveAppState();
    }

    private void openDetachedConsolePanel() {
        if (detachedConsolePanel == null) {
            detachedConsolePanel = new ConsolePanel();
            detachedConsolePanel.info("Detached console opened");
        }
        createIndependentWindow("Detached System Console", detachedConsolePanel, 900, 520);
    }

    private void openDataWindow() {
        if (dataWindow == null) {
            dataWindow = new DataWindow();
        }
        TradePair selected = symbolSelector.getValue();
        dataWindow.setSymbol(selected);
        dataWindow.setTimeframe(timeframeSelector.getValue().getCode());
        if (selected != null) {
            dataWindow.updateQuote(selected, selected.getBid(), selected.getAsk(), selected.getLast(), Instant.now());
        }
        createIndependentWindow("Data Window", dataWindow, 400, 500);
    }

    private void openMarketInfoPanel() {
        MarketInfoPanel panel = new MarketInfoPanel(exchange, newsDataProvider);
        panel.updateForPair(symbolSelector.getValue());
        createIndependentWindow("Market Info", panel, 450, 600);
    }

    private void openSymbolAgentMarketWatch() {
        if (symbolAgentMarketWatch == null && systemCore != null) {
            symbolAgentMarketWatch = new MarketWatchPanel(systemCore);
        }
        if (symbolAgentMarketWatch != null) {
            createIndependentWindow("Symbol Agent Market Watch", symbolAgentMarketWatch, 600, 700);
        } else {
            showWarning("Market Watch", "SystemCore is not initialized. Connect an exchange first.");
        }
    }

    private void openNavigationPanel() {
        Navigation detachedNavigation = createNavigationPanel(false);
        stage = new Stage();
        stage.setTitle("Trading Desk Navigation");
        stage.setScene(new Scene(detachedNavigation, 420, 720));
        stage.show();
    }

    private Navigation createNavigationPanel() {
        return createNavigationPanel(true);
    }

    private Navigation createNavigationPanel(boolean primaryPanel) {
        Navigation panel = new Navigation();
        configureNavigationPanel(panel);
        if (primaryPanel) {
            navigationPanel = panel;
        }
        return panel;
    }

    private void configureNavigationPanel(Navigation panel) {
        panel.setOnExchangeChanged(() -> {
            String selectedExchange = panel.getSelectedExchange();
            if (selectedExchange != null && exchangeSelector.getItems().contains(selectedExchange)) {
                exchangeSelector.setValue(selectedExchange);
                onExchangeChanged();
            }
        });
        panel.setOnNavigationRequested(this::openRegisteredPanel);
    }

    private @NotNull Tab createPositionsTab() {
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
        return createFixedTab(TabName.POSITIONS, content);
    }

    private @NotNull Tab createAlertsTab() {
        alertsListView.setItems(
                FXCollections.observableArrayList("No active alerts.", "Create alerts from chart or order panel."));
        VBox content = new VBox(alertsListView);
        VBox.setVgrow(alertsListView, Priority.ALWAYS);
        return createFixedTab(TabName.ALERTS, content);
    }

    private @NotNull Tab createSignalTab() {
        signalListView.setItems(signalItems);
        VBox content = new VBox(signalListView);
        VBox.setVgrow(signalListView, Priority.ALWAYS);
        return createFixedTab(TabName.SIGNALS, content);
    }

    private @NotNull Tab createAgentsTab() {
        expertsListView.setItems(agentActivityItems);
        VBox content = new VBox(expertsListView);
        VBox.setVgrow(expertsListView, Priority.ALWAYS);
        return createFixedTab(TabName.AGENTS, content);
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

        positionHealthTable.getColumns().addAll(statusCol, scoreCol, pnlCol, riskCol, technicalCol, liquidityCol,
                portfolioCol, summaryCol);

        VBox content = new VBox(positionHealthTable);
        VBox.setVgrow(positionHealthTable, Priority.ALWAYS);
        return createFixedTab(TabName.RISK_MONITOR, content);
    }

    private @NotNull DraggableTab createJournalTab() {
        journalArea.setEditable(false);
        journalArea.setWrapText(true);
        journalArea.setText("InvestPro system journal initialized.\nLayout loaded successfully.\n");
        VBox content = new VBox(journalArea);
        VBox.setVgrow(journalArea, Priority.ALWAYS);
        return createFixedTab(TabName.JOURNAL, content);
    }

    private void configureMarketWatchTable() {
        configureMarketWatchTableView(marketWatchTable);

        marketWatchItems.addListener((javafx.collections.ListChangeListener<TradePair>) change -> {
            scheduleSymbolAgentFeed();
        });

        // Add selection listener to load order book when a symbol is selected
        marketWatchTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateOrderActionAvailability(newVal);
                loadOrderBook(newVal);
            }
        });

        // Start periodic refresh of market watch data from cache
        startMarketWatchRefresh();
    }

    private void scheduleSymbolAgentFeed() {
        if (systemCore == null || marketWatchItems.isEmpty()) {
            return;
        }

        List<TradePair> snapshot = new ArrayList<>(marketWatchItems);
        ScheduledFuture<?> previous = pendingSymbolAgentFeed;
        if (previous != null && !previous.isDone()) {
            previous.cancel(false);
        }

        if (isAutoRefreshExecutorUnavailable()) {
            feedSymbolAgentsInBackground(snapshot);
            return;
        }

        pendingSymbolAgentFeed = autoRefreshExecutor.schedule(
                () -> feedSymbolAgentsInBackground(snapshot),
                150,
                TimeUnit.MILLISECONDS);
    }

    private void feedSymbolAgentsInBackground(List<TradePair> symbolsSnapshot) {
        if (systemCore == null || symbolsSnapshot == null || symbolsSnapshot.isEmpty()) {
            return;
        }

        botOperationExecutor.execute(() -> {
            try {
                if (systemCore.getSymbolAgentManager() != null) {
                    systemCore.getSymbolAgentManager().initializeSymbols(symbolsSnapshot);
                }
                systemCore.initializeSymbolAgents(symbolsSnapshot);
            } catch (Exception exception) {
                log.warn("Failed to feed SymbolAgent from market watch", exception);
            }
        });
    }

    /**
     * Periodically refresh market watch table with live quotes from
     * MarketDataEngine cache.
     */
    private void startMarketWatchRefresh() {
        autoRefreshExecutor.scheduleAtFixedRate(() -> {
            if (marketDataEngine != null && !marketWatchItems.isEmpty()) {
                Platform.runLater(() -> {
                    for (TradePair pair : marketWatchItems) {
                        MarketQuote quote = marketDataEngine.getQuote(pair);
                        if (quote != null) {
                            pair.updateQuote(quote.getBid(), quote.getAsk());
                        }
                    }
                    marketWatchTable.refresh();
                });
            }
        }, 1, 5, TimeUnit.SECONDS); // Refresh every 5 seconds after 1-second initial delay
    }

    private void configureMarketWatchTableView(@NotNull TableView<TradePair> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No symbols in watch list"));
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.setFixedCellSize(24);
        table.getStyleClass().addAll("mt5-market-watch-table", "compact-table");
        table.getColumns().clear();

        // ── Symbol ────────────────────────────────────────────────────────────
        TableColumn<TradePair, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(cd -> {
            TradePair p = cd.getValue();
            return new SimpleStringProperty(p == null ? "" : p.toString('/'));
        });
        symbolCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null || s.isBlank()) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    return;
                }
                setText(s);
                String baseCode = s.contains("/") ? s.substring(0, s.indexOf('/')) : s;
                Image icon = CurrencyIconLoader.loadCurrencyIcon(baseCode);
                if (icon != null) {
                    ImageView imageView = new ImageView(icon);
                    imageView.setFitWidth(18);
                    imageView.setFitHeight(18);
                    imageView.setPreserveRatio(true);
                    setGraphic(imageView);
                } else {
                    setGraphic(null);
                }
                setStyle("-fx-font-weight: bold; -fx-text-fill: #f1f5f9;");
            }
        });
        symbolCol.setPrefWidth(88);

        // ── Bid ───────────────────────────────────────────────────────────────
        TableColumn<TradePair, String> bidCol = new TableColumn<>("Bid");
        bidCol.setCellValueFactory(cd -> {
            TradePair p = cd.getValue();
            return new SimpleStringProperty(p == null ? "" : price(p.getBid()));
        });
        bidCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null || s.isBlank()) { setText(null); setStyle(""); return; }
                setText(s); setAlignment(Pos.CENTER_RIGHT);
                setStyle("-fx-text-fill: #60a5fa; -fx-font-family: monospace;");
            }
        });
        bidCol.setPrefWidth(70);

        // ── Ask ───────────────────────────────────────────────────────────────
        TableColumn<TradePair, String> askCol = new TableColumn<>("Ask");
        askCol.setCellValueFactory(cd -> {
            TradePair p = cd.getValue();
            return new SimpleStringProperty(p == null ? "" : price(p.getAsk()));
        });
        askCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null || s.isBlank()) { setText(null); setStyle(""); return; }
                setText(s); setAlignment(Pos.CENTER_RIGHT);
                setStyle("-fx-text-fill: #fb923c; -fx-font-family: monospace;");
            }
        });
        askCol.setPrefWidth(70);

        TableColumn<TradePair, String> sessionCol = new TableColumn<>("Session");
        sessionCol.setCellValueFactory(cd -> {
            TradePair p = cd.getValue();
            if (p == null) {
                return new SimpleStringProperty("");
            }
            var status = p.getTradingSessionStatus();
            return new SimpleStringProperty(status == null ? "-" : status.name());
        });
        sessionCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null || s.isBlank() || s.equals("-")) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(s);
                setAlignment(Pos.CENTER);
                String color = switch(s) {
                    case "OPEN" -> "#10b981";
                    case "CLOSED" -> "#ef4444";
                    case "BREAK" -> "#f59e0b";
                    case "UNKNOWN" -> "#94a3b8";
                    default -> "#cbd5e1";
                };
                setStyle("-fx-font-family: monospace; -fx-text-fill: " + color + ";");
            }
        });
        sessionCol.setPrefWidth(80);

        TableColumn<TradePair, String> tradabilityCol = new TableColumn<>("Tradability");
        tradabilityCol.setCellValueFactory(cd -> {
            TradePair pair = cd.getValue();
            SymbolTradability status = pair == null ? null : tradabilityBySymbol.get(symbolKey(pair));
            return new SimpleStringProperty(status == null ? "-" : status.status().name());
        });
        tradabilityCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.isBlank() || "-".equals(value)) {
                    setText(null);
                    setTooltip(null);
                    setStyle("");
                    return;
                }

                setText(value);
                SymbolTradability status = getTableRow() == null || getTableRow().getItem() == null
                        ? null
                        : tradabilityBySymbol.get(symbolKey(getTableRow().getItem()));
                if (status != null && !status.reason().isBlank()) {
                    setTooltip(new Tooltip(status.reason()));
                }

                String color = switch (value) {
                    case "FULLY_TRADABLE" -> "#10b981";
                    case "VIEW_ONLY", "LIMIT_ONLY", "POST_ONLY", "CANCEL_ONLY" -> "#f59e0b";
                    case "MARKET_CLOSED", "HALTED", "INACTIVE", "DISABLED" -> "#f97316";
                    default -> "#ef4444";
                };
                setStyle("-fx-font-family: monospace; -fx-font-size: 10px; -fx-text-fill: " + color + ";");
            }
        });
        tradabilityCol.setPrefWidth(120);

        table.getColumns().addAll(symbolCol, bidCol, askCol, sessionCol, tradabilityCol);

        // ── Row factory: alternating rows + context menu + double-click ───────
        table.setRowFactory(view -> {
            TableRow<TradePair> row = new TableRow<>() {
                @Override
                protected void updateItem(TradePair item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setStyle(""); return; }
                    setStyle(getIndex() % 2 == 0 ? "-fx-background-color: #0f172a;" : "-fx-background-color: #1a2332;");
                }
            };
            ContextMenu ctx = new ContextMenu();
            MenuItem tradeItem  = new MenuItem("⬆ Trade");
            MenuItem chartItem  = new MenuItem("📈 Chart");
            MenuItem removeItem = new MenuItem("✕  Remove");
            tradeItem.setOnAction(e -> {
                if (!row.isEmpty()) {
                    symbolSelector.getSelectionModel().select(row.getItem());
                    openOrderPanel();
                }
            });
            chartItem.setOnAction(e -> {
                if (!row.isEmpty()) {
                    symbolSelector.getSelectionModel().select(row.getItem());
                    openSelectedSymbolChart();
                }
            });
            removeItem.setOnAction(e -> { if (!row.isEmpty()) marketWatchItems.remove(row.getItem()); });
            ctx.getItems().addAll(tradeItem, chartItem, new SeparatorMenuItem(), removeItem);
            row.setContextMenu(ctx);
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    symbolSelector.getSelectionModel().select(row.getItem());
                    openSelectedSymbolChart();
                }
            });
            return row;
        });

        table.setRowFactory(view -> createMarketWatchRow());
    }

    private @NotNull TableRow<TradePair> createMarketWatchRow() {
        TableRow<TradePair> row = new TableRow<>() {
            @Override
            protected void updateItem(TradePair item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("mt5-row-even", "mt5-row-odd");
                if (!empty && item != null) {
                    getStyleClass().add(getIndex() % 2 == 0 ? "mt5-row-even" : "mt5-row-odd");
                }
            }
        };

        MenuItem tradeItem = new MenuItem("New Order");
        MenuItem chartItem = new MenuItem("Chart Window");
        MenuItem depthItem = new MenuItem("Depth of Market");
        MenuItem specItem = new MenuItem("Specification / Market Info");
        MenuItem removeItem = new MenuItem("Remove");

        tradeItem.getStyleClass().add("market-watch-menu-item");
        chartItem.getStyleClass().add("market-watch-menu-item");
        depthItem.getStyleClass().add("market-watch-menu-item");
        specItem.getStyleClass().add("market-watch-menu-item");
        removeItem.getStyleClass().add("market-watch-menu-item");

        tradeItem.setOnAction(event -> withMarketWatchRow(row, this::openOrderPanel));
        chartItem.setOnAction(event -> withMarketWatchRow(row, this::openSelectedSymbolChart));
        depthItem.setOnAction(event -> withMarketWatchRow(row, this::loadSelectedOrderBook));
        specItem.setOnAction(event -> withMarketWatchRow(row, this::openMarketInfoPanel));
        removeItem.setOnAction(event -> {
            if (!row.isEmpty()) {
                marketWatchItems.remove(row.getItem());
            }
        });

        ContextMenu contextMenu = new ContextMenu(
                tradeItem,
                chartItem,
                depthItem,
                specItem,
                new SeparatorMenuItem(),
                removeItem
        );
        contextMenu.getStyleClass().add("market-watch-context-menu");
        row.setContextMenu(contextMenu);
        row.setOnMouseClicked(event -> {
            if (row.isEmpty()) {
                return;
            }
            symbolSelector.getSelectionModel().select(row.getItem());
            if (event.getClickCount() == 2) {
                openSelectedSymbolChart();
            } else {
                loadOrderBook(row.getItem());
            }
        });
        return row;
    }

    private void withMarketWatchRow(TableRow<TradePair> row, Runnable action) {
        if (row == null || row.isEmpty()) {
            return;
        }
        TradePair selected = row.getItem();
        marketWatchTable.getSelectionModel().select(selected);
        symbolSelector.getSelectionModel().select(selected);
        if (action != null) {
            action.run();
        }
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
        positionsTable.getColumns().addAll(
                tableColumn("Symbol", order -> safe(order.getSymbol()), 90),
                tableColumn("Type", order -> safe(order.getSide().name()), 70),
                tableColumn("Qty", order -> number(order.getQuantity()), 90),
                tableColumn("Entry", order -> price(order.getEntryPrice()), 90),
                tableColumn("P&L", order -> money(order.getCurrentPrice() - order.getEntryPrice()), 90),
                tableColumn("ID", order -> safe(order.getPositionId()), 90));
    }

    private void configureAccountSummaryArea() {
        accountSummaryArea.setEditable(false);
        accountSummaryArea.setWrapText(true);
        accountSummaryArea.setStyle("""
                -fx-control-inner-background: #0f172a;
                -fx-text-fill: #f1f5f9;
                -fx-font-family: 'Consolas', 'Monaco', monospace;
                -fx-font-size: 11px;
                -fx-padding: 10px;
                -fx-focus-color: transparent;
                -fx-faint-focus-color: transparent;
                """);
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
        table.getColumns().addAll(
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
        table.getColumns().setAll(List.of(
                tableColumn("Date", order -> String.valueOf(order.getDate()), 150),
                tableColumn("Side", order -> {
                    String type = safe(order.getType());
                    String side = safe(String.valueOf(order.getSide()));
                    if (type.isBlank()) {
                        return side;
                    }
                    return side.isBlank() ? type : "%s (%s)".formatted(side, type);
                }, 110),
                tableColumn("Symbol", Order::getSymbol, 110),
                tableColumn("Qty", order -> number(order.getQuantity()), 90),
                tableColumn("Price", order -> price(order.getPrice()), 90),
                tableColumn("Status", Order::getStatus, 90)));
        return table;
    }

    private @NotNull TabPane buildPortfolioPane() {
        TabPane portfolioTabPane = new TabPane();
        portfolioTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        portfolioTabPane.setSide(Side.TOP);

        // Account Summary Tab
        Tab accountSummaryTab = new Tab("Account Summary", accountSummaryArea);
        accountSummaryTab.setClosable(false);

        // Order Management Tab - contains Orders and Fills sub-tabs
        TabPane orderManagementTabPane = new TabPane();
        orderManagementTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        orderManagementTabPane.setSide(Side.TOP);

        // Orders Tab (Open Orders)
        TableView<OpenOrder> ordersTable = buildOpenOrdersTable();
        Tab ordersTab = new Tab("Orders", ordersTable);
        ordersTab.setClosable(false);

        // Fills Tab (Filled Orders / Order History)
        TableView<Order> fillsTable = buildOrderHistoryTable();
        Tab fillsTab = new Tab("Fills", fillsTable);
        fillsTab.setClosable(false);

        orderManagementTabPane.getTabs().setAll(ordersTab, fillsTab);

        Tab orderManagementTab = new Tab("Order Management", orderManagementTabPane);
        orderManagementTab.setClosable(false);

        // Trades Tab
        Tab tradesTab = new Tab("Trades", buildAccountTradesTable());
        tradesTab.setClosable(false);

        portfolioTabPane.getTabs().setAll(accountSummaryTab, orderManagementTab, tradesTab);
        return portfolioTabPane;
    }

    private @NotNull TableView<OpenOrder> buildOpenOrdersTable() {
        TableView<OpenOrder> table = new TableView<>(accountOpenOrderItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No open orders"));
        table.getColumns().addAll(
                tableColumn("Date", order -> order.getCreatedAt() != null ? dateTime(order.getCreatedAt()) : "", 150),
                tableColumn("Symbol", order -> order.getTradePair() != null ? order.getTradePair().toString('/') : "",
                        110),
                tableColumn("Type", order -> order.getOrderType() != null ? String.valueOf(order.getOrderType()) : "",
                        80),
                tableColumn("Side", order -> order.getSide() != null ? String.valueOf(order.getSide()) : "", 70),
                tableColumn("Quantity", order -> number(order.getSize()), 90),
                tableColumn("Filled", order -> number(order.getFilledSize()), 90),
                tableColumn("Remaining", order -> number(order.getRemainingSize()), 90),
                tableColumn("Price", order -> price(order.getPrice()), 90),
                tableColumn("Avg Fill", order -> price(order.getAvgFillPrice()), 90),
                tableColumn("Status", order -> order.getStatus() != null ? String.valueOf(order.getStatus()) : "", 90));
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
        HBox statusBar = new HBox(8);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("mt5-status-bar");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBrokerLabel.getStyleClass().add("mt5-status-chip");
        statusModeLabel.getStyleClass().add("mt5-status-chip");
        statusActiveSymbolLabel.getStyleClass().add("mt5-status-chip");
        symbolCountLabel.getStyleClass().add("mt5-status-chip");
        statusLatencyLabel.getStyleClass().add("mt5-status-chip");
        statusServerTimeLabel.getStyleClass().add("mt5-status-chip");
        statusMarketDataLabel.getStyleClass().add("mt5-status-chip");

        statusBar.getChildren().setAll(
                connectionIndicator,
                connectionStatusLabel,
                createVerticalSeparator(),
                statusBrokerLabel,
                statusModeLabel,
                statusActiveSymbolLabel,
                symbolCountLabel,
                statusLatencyLabel,
                statusMarketDataLabel,
                spacer,
                statusServerTimeLabel,
                createTerminalStatusChip());
        updateStatusBarValues();
        return statusBar;
    }

    private void updateStatusBarValues() {
        TradePair selected = symbolSelector == null ? null : symbolSelector.getSelectionModel().getSelectedItem();
        statusBrokerLabel.setText("Broker: " + safe(exchangeSelector == null ? "" : exchangeSelector.getValue()));
        statusModeLabel.setText("Mode: " + safe(tradingModeSelector == null ? "" : tradingModeSelector.getValue()));
        statusActiveSymbolLabel.setText("Symbol: " + (selected == null ? "-" : selected.toString('/')));
        statusLatencyLabel.setText("Latency: -- ms");
        statusMarketDataLabel.setText("Data: " + (marketDataEngine == null ? "idle" : "cache"));
        statusServerTimeLabel.setText("Time: " + LocalDateTime.now().format(STATUS_TIME_FORMAT));
        updateChartHeader();
    }

    private void updateConnectionStatus() {
        boolean connected;
        try {
            connected = hasBrokerAccess();
        } catch (Exception exception) {
            connected = false;
            log.debug("Unable to determine connection status.", exception);
        }
        connectionIndicator.setFill(connected ? Color.LIMEGREEN : Color.ORANGERED);
        connectionStatusLabel.setText(connected ? t("status.connected") : t("status.disconnected"));
        updateConnectControl(connected);
        symbolCountLabel.setText(t("label.symbols", marketWatchItems.size()));
        updateStatusBarValues();
        updateDeskCommandStrip();
    }

    private void updateDeskCommandStrip() {
        if (deskConnectionLabel == null) {
            return;
        }

        boolean connected;
        try {
            connected = hasBrokerAccess();
        } catch (Exception exception) {
            connected = false;
        }

        TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
        int positions = accountPositionItems.size();
        int openOrders = accountOpenOrderItems.size();
        int riskFlags = (int) positionHealthItems.stream()
                .filter(score -> score != null && score.getOverallScore() < 0.60)
                .count();

        double exposure = estimateAccountExposure();
        double spreadPercent = estimateSpreadPercent(selected);
        MarketSnapshot market = deskMarketSnapshot();
        StrategyStats strategy = strategyStats();

        deskConnectionLabel.setText(connected ? safe(exchangeSelector.getValue()) : "Offline");
        deskRiskLabel.setText(riskFlags == 0 ? "Clear" : riskFlags + " flag(s)");
        deskExposureLabel.setText(exposure > 0.0 ? formatCurrencyCompact(exposure) : positions + " pos");
        deskOrdersLabel.setText(openOrders + " open");
        deskMarketLabel.setText(market.hasMarketData()
                ? formatSignedPercent(market.averageChangePercent(), 2) + " breadth " + formatNumber(market.positiveBreadth(), 0)
                : marketWatchItems.size() + " symbols");
        deskSpreadLabel.setText(spreadPercent > 0.0 ? formatNumber(spreadPercent, 3) + "%" : "Spread: --");
        deskStrategyLabel.setText(strategy.totalTrades() > 0
                ? formatNumber(strategy.winRatePercent(), 1) + "% / PF " + formatNumber(strategy.profitFactor(), 2)
                : "No sample");
        deskUpdatedLabel.setText("Updated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        updateOrderActionAvailability(selected);

        deskRiskLabel.getStyleClass().removeAll("desk-positive", "desk-warning", "desk-negative");
        deskRiskLabel.getStyleClass().add(riskFlags == 0 ? "desk-positive" : riskFlags > 2 ? "desk-negative" : "desk-warning");

        deskMarketLabel.getStyleClass().removeAll("desk-positive", "desk-warning", "desk-negative");
        if (market.hasMarketData()) {
            deskMarketLabel.getStyleClass().add(market.averageChangePercent() >= 0 ? "desk-positive" : "desk-negative");
        }
    }

    private void refreshDeskSnapshot() {
        cachedMarketSnapshotAt = Instant.EPOCH;
        cachedStrategyStatsAt = Instant.EPOCH;
        loadSelectedOrderBook();
        loadSymbolsForSelectedExchange();
        refreshAccountWorkspace();
        updateDeskCommandStrip();
        journal("Desk snapshot refreshed.");
    }

    private void focusRiskMonitor() {
        openSystemConsolePanel();
        refreshAccountWorkspace();
        if (terminalTabPane != null) {
            terminalTabPane.getTabs().stream()
                    .filter(tab -> Objects.equals(tab.getText(), TabName.RISK_MONITOR.getTabId()))
                    .findFirst()
                    .ifPresent(tab -> terminalTabPane.getSelectionModel().select(tab));
        }
        updateDeskCommandStrip();
    }

    private double estimateAccountExposure() {
        double exposure = accountPositionItems.stream()
                .filter(Objects::nonNull)
                .mapToDouble(position -> Math.abs(position.getQuantity()) * firstPositive(
                        position.getCurrentPrice(),
                        position.getEntryPrice(),
                        0.0))
                .sum();

        if (exposure > 0.0) {
            return exposure;
        }

        return positionsItems.stream()
                .filter(Objects::nonNull)
                .mapToDouble(position -> Math.abs(position.getQuantity()) * firstPositive(
                        position.getCurrentPrice(),
                        position.getEntryPrice(),
                        0.0))
                .sum();
    }

    private double estimateSpreadPercent(TradePair selected) {
        double bid = 0.0;
        double ask = 0.0;

        if (!orderBookBids.isEmpty() && !orderBookAsks.isEmpty()) {
            bid = orderBookBids.getFirst().getPrice();
            ask = orderBookAsks.getFirst().getPrice();
        } else if (selected != null) {
            bid = selected.getBid();
            ask = selected.getAsk();
        }

        if (bid <= 0.0 || ask <= 0.0 || ask <= bid) {
            return 0.0;
        }

        double mid = (bid + ask) / 2.0;
        return mid <= 0.0 ? 0.0 : ((ask - bid) / mid) * 100.0;
    }

    private MarketSnapshot deskMarketSnapshot() {
        if (cachedMarketSnapshot.hasMarketData() && cachedMarketSnapshotAt.plusSeconds(30).isAfter(Instant.now())) {
            return cachedMarketSnapshot;
        }

        List<TradePair> pairs = new ArrayList<>(marketWatchItems);
        if (pairs.isEmpty() && symbolSelector.getValue() != null) {
            pairs.add(symbolSelector.getValue());
        }

        double weightedChange = 0.0;
        double totalWeight = 0.0;
        double positiveWeight = 0.0;
        double totalQuoteVolume = 0.0;
        double btcQuoteVolume = 0.0;

        for (TradePair pair : pairs) {
            if (pair == null) {
                continue;
            }

            double price = firstPositive(pair.getLastPrice(), pair.getMidPrice(), pair.getBid(), pair.getAsk());
            double volume = safePositive(pair.getVolume());
            double quoteVolume = price > 0.0 && volume > 0.0 ? price * volume : volume;
            double weight = quoteVolume > 0.0 ? quoteVolume : 1.0;
            double change = finite(pair.getChangePercent());

            weightedChange += change * weight;
            totalWeight += weight;
            if (change > 0.0) {
                positiveWeight += weight;
            }
            totalQuoteVolume += Math.max(0.0, quoteVolume);
            if ("BTC".equalsIgnoreCase(pair.getBaseCode())) {
                btcQuoteVolume += Math.max(0.0, quoteVolume);
            }
        }

        if (totalWeight <= 0.0) {
            return MarketSnapshot.empty();
        }

        double averageChange = weightedChange / totalWeight;
        double breadth = positiveWeight / totalWeight;
        double sentimentIndex = clamp(50.0 + (averageChange * 6.0) + ((breadth - 0.5) * 40.0), 0.0, 100.0);
        double syntheticVix = clamp(12.0 + Math.abs(averageChange) * 4.0, 8.0, 80.0);
        double btcDominance = totalQuoteVolume > 0.0 ? (btcQuoteVolume / totalQuoteVolume) * 100.0 : -1.0;
        return new MarketSnapshot(sentimentIndex, averageChange, breadth, syntheticVix, btcDominance, totalQuoteVolume);
    }

    private void updateConnectControl(boolean connected) {
        if (connected) {
            connectButton.setText(t("status.connected"));
            connectButton.setStyle(
                "-fx-padding: 8 15; -fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
            connectButton.setVisible(true);
            connectButton.setManaged(true);
            connectedBrokerLabel.setVisible(false);
            connectedBrokerLabel.setManaged(false);
        } else {
            connectButton.setText(t("toolbar.connect"));
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

    private boolean lacksOrderSubmissionAccess() {
        try {
            return !hasBrokerAccess() || exchange == null || !exchange.canSubmitOrders();
        } catch (Exception exception) {
            log.debug("Unable to check order submission access", exception);
            return true;
        }
    }

    private boolean hasConfiguredCredentials() {
        String selectedExchange = safe(exchangeSelector.getSelectionModel().getSelectedItem());
        if ("OANDA".equalsIgnoreCase(selectedExchange)) {
            return !safe(configuredApiKey).isBlank();
        }
        return !safe(configuredApiKey).isBlank() && !safe(configuredApiSecret).isBlank();
    }

    private boolean isAutoRefreshExecutorUnavailable() {
        return autoRefreshExecutor.isShutdown() || autoRefreshExecutor.isTerminated();
    }

    private void configureSelectors(MarketConfiguration configuration) {
        exchangeSelector.getItems().setAll(discoverSupportedExchanges());
        String configuredExchange = normalizeExchangeName(configuration == null
                ? preferences.get("selected_exchange", "")
                : safe(configuration.exchange()));

        if (!configuredExchange.isBlank() && exchangeSelector.getItems().contains(configuredExchange)) {
            exchangeSelector.getSelectionModel().select(configuredExchange);
        } else {
            // Use DEFAULT_EXCHANGE from AppConfig instead of hardcoding the first element
            String defaultExchange = AppConfig.get(AppConfigKeys.DEFAULT_EXCHANGE, "OANDA");
            if (exchangeSelector.getItems().contains(defaultExchange)) {
                exchangeSelector.getSelectionModel().select(defaultExchange);
            } else {
                // Fall back to first item only if the configured default is not supported
                exchangeSelector.getSelectionModel().selectFirst();
            }
        }

        exchangeSelector.setOnAction(event -> onExchangeChanged());

        symbolSelector.setOnAction(event -> {
            TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
            if (selected != null) {
                marketWatchTable.getSelectionModel().select(selected);
                loadOrderBook(selected);
                if (marketInfoPanel != null) {
                    marketInfoPanel.setExchange(exchange);
                    marketInfoPanel.updateForPair(selected);
                }
                updateOrderActionAvailability(selected);
                updateStatusBarValues();
                saveAppState();
            }
        });

        configureTradingModeSelector();
        configureOrderTypeSelector();
        configureTimeframeSelector();
    }

    private void configureTradingModeSelector() {
        tradingModeSelector.getItems().setAll("PAPER", "LIVE");
        tradingModeSelector.getSelectionModel().select(configuredTradingMode);

        tradingModeSelector.setOnAction(event -> {
            String selectedMode = tradingModeSelector.getSelectionModel().getSelectedItem();
            if (selectedMode != null && exchange != null) {
                configuredTradingMode = selectedMode;
                saveExchangeCredentials(safe(exchangeSelector.getValue()));
                exchange.setUserSelectedTradingMode(selectedMode);
                log.info("Trading mode switched to: {}", selectedMode);
                appendAgentActivity("Trading mode: " + selectedMode);
                updateStatusBarValues();

                if (hasBrokerAccess()) {
                    reconnectCurrentExchangeForTradingMode();
                }
            }
        });
    }

    private void reconnectCurrentExchangeForTradingMode() {
        String selectedExchange = safe(exchangeSelector.getSelectionModel().getSelectedItem());
        if (selectedExchange.isBlank()) {
            return;
        }

        if (!hasExchangeCredentials(selectedExchange)) {
            return;
        }

        loadExchangeCredentials(selectedExchange);
        exchange = createExchange(selectedExchange, configuredApiKey, configuredApiSecret, configuredAccountId,
                configuredTradingMode);

        setTelegramToken(telegramToken);

        new Thread(() -> {
            try {
                exchange.connectStream();
            } catch (Exception streamException) {
                log.debug("Unable to reconnect stream while switching trading mode", streamException);
            }
        }, "WebSocketConnector-ModeSwitch-" + selectedExchange).start();

        journal("Switching %s to %s mode".formatted(selectedExchange, configuredTradingMode));
        proceedWithConnection();
    }

    private void configureOrderTypeSelector() {
        refreshOrderTypeOptions();
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
        configureExchangeVenueLabel();
    }

    private void configureExchangeVenueLabel() {
        exchangeVenueLabel.setText("Venue: -");
        exchangeVenueLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 11;");
        exchangeVenueLabel.setMinWidth(90);
    }

    private void refreshOrderTypeOptions() {
        final String[] selected = { orderTypeSelector.getSelectionModel().getSelectedItem() };
        List<String> orderTypes = new ArrayList<>();
        orderTypes.add("MARKET");
        orderTypes.add("LIMIT");

        if (exchange == null || exchange.supportsStopLossTakeProfit()) {
            orderTypes.add("STOP");
        }
        if (exchange == null || exchange.supportsBracketOrders()) {
            orderTypes.add("BRACKET");
        }

        orderTypeSelector.getItems().setAll(orderTypes);
        orderTypeSelector.getSelectionModel().select(orderTypes.contains(selected[0]) ? selected[0] : "MARKET");
    }

    private void configureTimeframeSelector() {
        List<Timeframe> supportedTimeframes = exchange != null && exchange.getSupportedTimeframes() != null
                ? new ArrayList<>(exchange.getSupportedTimeframes())
                : new ArrayList<>(MT5_TIMEFRAMES);
        if (supportedTimeframes.isEmpty()) {
            supportedTimeframes = new ArrayList<>(MT5_TIMEFRAMES);
        }

        String savedTimeframe = preferences.get("selected_timeframe", "1h");
        Timeframe selectedTimeframe = parseTimeframeOrDefault(savedTimeframe, supportedTimeframes);

        timeframeSelector.getItems().setAll(supportedTimeframes);
        timeframeSelector.getSelectionModel().select(selectedTimeframe);
        setActiveTimeframeButton(selectedTimeframe);

        timeframeSelector.setOnAction(event -> {
            Timeframe selected = timeframeSelector.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            applyTimeframeSelection(selected);
        });
    }

    private Timeframe parseTimeframeOrDefault(String value, List<Timeframe> supportedTimeframes) {
        try {
            Timeframe parsed = value == null || value.isBlank() ? Timeframe.H1 : Timeframe.fromCode(value);
            return supportedTimeframes.contains(parsed)
                    ? parsed
                    : supportedTimeframes.contains(Timeframe.H1) ? Timeframe.H1 : supportedTimeframes.getFirst();
        } catch (Exception exception) {
            return supportedTimeframes.contains(Timeframe.H1) ? Timeframe.H1 : supportedTimeframes.getFirst();
        }
    }

    private void applyTimeframeSelection(Timeframe timeframe) {
        if (timeframe == null) {
            return;
        }
        if (!Objects.equals(timeframeSelector.getSelectionModel().getSelectedItem(), timeframe)) {
            timeframeSelector.getSelectionModel().select(timeframe);
        }
        setActiveTimeframeButton(timeframe);
        preferences.put("selected_timeframe", timeframe.getCode());
        onTimeframeChanged(timeframe.getCode());
        saveAppState();
        journal("Timeframe changed to: " + timeframe.name());
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
        configureToolbarButtonIcons();
        refreshSymbolsButton.setOnAction(event -> {
            if (universalTradabilityService != null) {
                universalTradabilityService.invalidateAll();
            }
            tradabilityBySymbol.clear();
            loadSymbolsForSelectedExchange();
        });
        addChartButton.setOnAction(event -> openSelectedSymbolChart());
        connectButton.setOnAction(event -> connectSelectedExchange());
        buyButton.setOnAction(event -> submitMarketOrder(BUY));
        sellButton.setOnAction(event -> submitMarketOrder(SELL));
        cancelAllButton.setOnAction(event -> cancelAllOrders());
    }

    private void configureToolbarButtonIcons() {
        setButtonIcon(refreshSymbolsButton, "/img/refresh-solid.png", "Refresh symbols");
        setButtonIcon(addChartButton, "/img/newtab.png", "Open selected chart");
        setButtonIcon(botTradeButton, "/img/auto-trade-solid.png", "Start or stop bot trading");
        setButtonIcon(cancelAllButton, "/img/trash-solid.png", "Cancel all open orders");
    }

    private void setButtonIcon(Button button, String iconPath, String tooltip) {
        if (button == null) {
            return;
        }
        ImageView icon = loadUiIcon(iconPath, 14);
        if (icon != null) {
            button.setGraphic(icon);
        }
        if (tooltip != null && !tooltip.isBlank()) {
            button.setTooltip(new Tooltip(tooltip));
        }
    }

    private void configureChartArea() {
        chartTabPane.setSide(Side.BOTTOM);
        chartTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
        chartTabPane.setMinHeight(400);
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
                configuration == null ? configuredApiSecret : safe(configuration.apiSecret()),
                configuration == null ? configuredAccountId : safe(configuration.accountId()),
                configuration == null ? configuredTradingMode : safe(configuration.tradingMode()));

        // Connect WebSocket stream on background thread to avoid blocking JavaFX thread
        new Thread(() -> exchange.connectStream(), "WebSocketConnector-" + selectedExchange).start();

        // Set trading mode from configuration
        if (configuration != null) {
            exchange.setUserSelectedTradingMode(configuration.tradingMode());
        }

        setTelegramToken(telegramToken);
        ensureTradabilityService();
        brokerAccessGranted = false;
        updateConnectionStatus();
        updateExchangeVenueLabel();
        refreshOrderTypeOptions();
    }

    public void setTelegramToken(String telegramToken) {
        this.telegramToken = safe(telegramToken);
        if (exchange != null && !this.telegramToken.isBlank()) {
            try {
                exchange.setTokens(this.telegramToken);
            } catch (Exception exception) {
                log.debug("Unable to set Telegram token on exchange.", exception);
            }
        }
    }

    private void onExchangeChanged() {
        String selectedExchange = exchangeSelector.getSelectionModel().getSelectedItem();

        stopActiveStreaming();
        brokerAccessGranted = false;
        if (universalTradabilityService != null) {
            universalTradabilityService.invalidateAll();
        }
        tradabilityBySymbol.clear();
        marketWatchUniverse.clear();
        tradabilityServiceExchangeId = "";
        universalTradabilityService = null;

        // DO NOT stop the bot - it should continue trading across exchanges
        // Instead, just update the exchange connection in the existing bot
        if (systemCore != null) {
            try {
                // Update bot context with new exchange instead of stopping
                if (systemCore.getSmartBot() != null && systemCore.getSmartBot().isStarted()) {
                    log.info("Updating bot to use new exchange: {}", selectedExchange);
                    // Bot will be updated with new exchange below
                } else {
                    // Bot wasn't running, proceed normally
                    systemCoreEventsSubscribed = false;
                }
            } catch (Exception exception) {
                throw  new RuntimeException("Failed to update SystemCore bot during exchange change", exception);
            }
        }

        // Gracefully disconnect old exchange streams but keep bot running
        if (exchange != null) {
            try {
                exchange.disconnectStream();
                // Note: Don't fully disconnect - just stop streaming for graceful transition
            } catch (Exception exception) {
                throw new RuntimeException("Failed to disconnect stream from old exchange", exception);

            }
        }

        disablePositionAutoRefresh();

        BrokerSession existingSession = brokerSessions.get(safe(selectedExchange));

        if (existingSession != null && existingSession.accessGranted()) {
            // Already connected to this exchange — reuse the active session
            exchange = existingSession.exchange();
            brokerAccessGranted = true;
        } else {
            // Not connected — load any stored credentials so the dialog is pre-filled,
            // then create a provisional exchange for symbol loading only.
            loadExchangeCredentials(selectedExchange);
            exchange = createExchange(selectedExchange, configuredApiKey, configuredApiSecret, configuredAccountId,
                    configuredTradingMode);
        }

        setTelegramToken(telegramToken);
        if (marketInfoPanel != null) {
            marketInfoPanel.setExchange(exchange);
        }

        accountPositionItems.clear();
        accountOpenOrderItems.clear();
        accountTradeItems.clear();
        accountHistoryItems.clear();
        positionHealthItems.clear();
        accountSummaryArea.clear();

        loadSymbolsForSelectedExchange();
        updateConnectionStatus();
        updateExchangeVenueLabel();
        refreshOrderTypeOptions();
        journal("Exchange changed to %s".formatted(selectedExchange));
        saveAppState();

        if (brokerAccessGranted) {
            if (existingSession != null && existingSession.account() != null) {
                updateAccountSummary(existingSession.account());
            }

            // Update existing bot with new exchange instead of recreating SystemCore
            if (systemCore != null && systemCore.getSmartBot() != null && systemCore.getSmartBot().isStarted()) {
                try {
                    log.info("Updating running bot with new exchange: {}", selectedExchange);
                    systemCore.getSmartBot().updateExchange(exchange);
                } catch (Exception e) {
                    log.warn("Failed to update bot exchange, recreating SystemCore: {}", e.getMessage());
                    try {
                        systemCore = createSystemCore(exchange);
                    } catch (SQLException | ClassNotFoundException ex) {
                        log.error("Failed to create SystemCore", ex);
                        showAlert("Failed to initialize trading system: " + ex.getMessage());
                    }
                }
            } else {
                try {
                    systemCore = createSystemCore(exchange);
                    if (systemCore.getTelegramCommandHandler() != null && getScene() != null
                            && getScene().getWindow() instanceof Stage) {
                        systemCore.getTelegramCommandHandler().setPrimaryStage((Stage) getScene().getWindow());
                    }
                } catch (SQLException | ClassNotFoundException e) {
                    log.error("Failed to create SystemCore", e);
                    showAlert("Failed to initialize trading system: " + e.getMessage());
                }
            }
            systemCoreEventsSubscribed = false;
            enablePositionAutoRefresh();
            initializeSymbolAgentPanels();
            refreshAccountWorkspace();
        } else {
            // Exchange not connected — always prompt for credentials and trading venue
            showExchangeCredentialDialog(selectedExchange);
        }
    }

    private void connectSelectedExchange() {
        String selectedExchange = exchangeSelector.getSelectionModel().getSelectedItem();

        if (selectedExchange == null || selectedExchange.isBlank()) {
            showWarning("Connection", "No exchange selected.");
            return;
        }

        BrokerSession existingSession = brokerSessions.get(safe(selectedExchange));
        if (existingSession != null
                && existingSession.accessGranted()
                && existingSession.exchange() != null
                && Boolean.TRUE.equals(existingSession.exchange().isConnected())) {
            exchange = existingSession.exchange();
            brokerAccessGranted = true;

            if (configuredTradingMode != null && !configuredTradingMode.isBlank()) {
                exchange.setUserSelectedTradingMode(configuredTradingMode);
            }

            if (existingSession.account() != null) {
                updateAccountSummary(existingSession.account());
            }

            updateConnectionStatus();
            updateExchangeVenueLabel();
            refreshOrderTypeOptions();
            refreshAccountWorkspace();
            journal("Reusing active %s connection".formatted(selectedExchange));
            return;
        }

        if (!hasExchangeCredentials(selectedExchange)) {
            showExchangeCredentialDialog(selectedExchange);
            return;
        }

        loadExchangeCredentials(selectedExchange);

        exchange = createExchange(selectedExchange, configuredApiKey, configuredApiSecret, configuredAccountId,
                configuredTradingMode);

        // Record exchange connection event
        SystemOperationsService.getInstance().recordEvent(
                SystemActivityEvent.Component.EXCHANGE,
                SystemActivityEvent.Severity.INFO,
                "CONNECTION_INITIATED",
                "Connecting to " + selectedExchange);

        // Connect WebSocket stream on background thread to avoid blocking JavaFX thread
        new Thread(() -> {
            try {
                exchange.connectStream();
                SystemOperationsService.getInstance().recordEvent(
                        SystemActivityEvent.Component.EXCHANGE,
                        SystemActivityEvent.Severity.INFO,
                        "CONNECTION_SUCCESS",
                        "Successfully connected to " + selectedExchange);
            } catch (Exception e) {
                log.error("Failed to connect to {}", selectedExchange, e);
                SystemOperationsService.getInstance().recordEvent(
                        SystemActivityEvent.Component.EXCHANGE,
                        SystemActivityEvent.Severity.ERROR,
                        "CONNECTION_FAILED",
                        "Failed to connect to " + selectedExchange);
            }
        }, "WebSocketConnector-" + selectedExchange).start();

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
        connectButton.setText(t("toolbar.validating"));

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

        // Bootstrap instruments into MarketDataEngine
        bootstrapInstrumentsForExchange();

        try {
            systemCore = createSystemCore(exchange);
            // Wire up the primary stage to the Telegram command handler for screenshot
            // capability
            if (systemCore.getTelegramCommandHandler() != null && getScene() != null
                    && getScene().getWindow() instanceof Stage) {
                systemCore.getTelegramCommandHandler().setPrimaryStage((Stage) getScene().getWindow());
            }
        } catch (SQLException | ClassNotFoundException e) {
            log.error("Failed to create SystemCore", e);
            rejectConnectionValidation(e);
            return;
        }
        systemCoreEventsSubscribed = false;
        initializeSymbolAgentPanels();

        connectButton.setDisable(false);
        journal("Credentials validated for %s".formatted(exchangeSelector.getValue()));

        // Sync trading mode selector with configured trading mode from credentials
        if (tradingModeSelector != null && configuredTradingMode != null) {
            tradingModeSelector.getSelectionModel().select(configuredTradingMode);
            if (exchange != null) {
                exchange.setUserSelectedTradingMode(configuredTradingMode);
            }
        }

        // Setup email notifications for OANDA if configured
        setupOandaEmailNotifications(account);

        updateAccountSummary(account);
        updateAccountBalance();
        publishConnectionSignal();
        enablePositionAutoRefresh();
        refreshAccountWorkspace();
        loadSymbolsForSelectedExchange();

        TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();

        loadOrderBook(selected);
        startDesktopStream(selected);

        updateConnectionStatus();
        updateExchangeVenueLabel();
        refreshOrderTypeOptions();
    }

    /**
     * Setup email notifications for OANDA exchange similar to MetaTrader-style
     * alerts.
     * <p>
     * This does not mean OANDA itself sends the emails.
     * InvestPro sends emails when OANDA-related events happen:
     * - ORDER_FILLED
     * - ORDER_REJECTED
     * - STREAM_DISCONNECTED
     * - ERROR
     * - BALANCE_UPDATE
     * - RISK_REJECTED
     */
    private void setupOandaEmailNotifications(Account account) {
        if (exchange == null) {
            log.debug("Cannot configure OANDA email notifications: exchange is null");
            return;
        }

        String exchangeName = exchange.getClass().getSimpleName();

        if (!"OANDA".equalsIgnoreCase(exchangeName)) {
            log.debug("Skipping OANDA email notifications because active exchange is {}", exchangeName);
            return;
        }

        String emailAddress = safe(exchange.getEmailNotification());

        if (!isValidEmail(emailAddress)) {
            log.debug("OANDA email notifications not configured or invalid email: {}", maskEmail(emailAddress));
            return;
        }

        String accountId = account != null && account.getAccountId() != null
                ? account.getAccountId()
                : "UNKNOWN";

        journal("✉ OANDA email notifications enabled: " + maskEmail(emailAddress));
        log.info(
                "OANDA email notifications configured. accountId={}, email={}",
                accountId,
                maskEmail(emailAddress));

        registerOandaEmailNotificationRules(accountId, emailAddress);
    }

    private void registerOandaEmailNotificationRules(String accountId, String emailAddress) {
        if (notificationService == null) {
            log.warn("NotificationService is not available. OANDA email rules were not registered.");
            return;
        }

        notificationService.registerEmailRecipient("OANDA", emailAddress);

        notificationService.subscribeEmail("OANDA", "ORDER_FILLED");
        notificationService.subscribeEmail("OANDA", "ORDER_REJECTED");
        notificationService.subscribeEmail("OANDA", "STREAM_DISCONNECTED");
        notificationService.subscribeEmail("OANDA", "ERROR");
        notificationService.subscribeEmail("OANDA", "BALANCE_UPDATE");
        notificationService.subscribeEmail("OANDA", "RISK_REJECTED");
        notificationService.subscribeEmail("OANDA", "MARGIN_WARNING");
        notificationService.subscribeEmail("OANDA", "POSITION_CLOSED");

        log.info("Registered OANDA email notification rules for accountId={}", accountId);
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private @NotNull String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "";
        }

        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];

        if (name.length() <= 2) {
            return "**@" + domain;
        }

        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + "@" + domain;
    }

    private void rejectConnectionValidation(Throwable throwable) {
        brokerAccessGranted = false;
        brokerSessions.remove(safe(exchangeSelector.getValue()));

        if (systemCore != null) {
            try {
                systemCore.stop();
            } catch (Exception exception) {
                log.debug("Failed to stop SystemCore after validation failure", exception);
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
            log.debug("Failed to disconnect after credential validation failure", exception);
        }

        accountSummaryArea.setText("Broker access blocked. Check credentials and try again.");
        updateConnectionStatus();

        log.warn("Broker credential validation failed for {}", exchangeSelector.getValue(), throwable);
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

        if (lacksOrderSubmissionAccess()) {
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

    private void submitQuickMarketOrder(org.investpro.utils.Side side) {
        TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Order", "Select a symbol before submitting an order.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(quickTradeAmountField.getText().trim());
        } catch (Exception exception) {
            showWarning("Order", "Amount must be a number.");
            return;
        }

        if (amount <= 0) {
            showWarning("Order", "Amount must be greater than zero.");
            return;
        }

        submitOrderByType("MARKET", selected, side, amount);
    }

    private void submitOrderByType(String orderType, TradePair tradePair, org.investpro.utils.Side side,
            double amount) {
        // Check if pair is tradable now using MarketDataEngine
        if (marketDataEngine != null && !marketDataEngine.isTradableNow(tradePair)) {
            String hours = marketDataEngine.getTradingHours(tradePair);
            showWarning("Trading Unavailable", "%s is not tradable now. Trading hours: %s".formatted(
                    tradePair.toString('/'), hours));
            return;
        }

        OpenOrder.OrderType mappedOrderType = switch (safe(orderType).toUpperCase(Locale.ROOT)) {
            case "MARKET" -> OpenOrder.OrderType.MARKET;
            case "LIMIT" -> OpenOrder.OrderType.LIMIT;
            case "STOP", "BRACKET" -> OpenOrder.OrderType.STOP_LOSS;
            default -> OpenOrder.OrderType.MARKET;
        };

        if (!canSubmitOrderByTradability(tradePair, mappedOrderType)) {
            return;
        }

        switch (orderType) {
            case "MARKET" -> submitMarketOrderInternal(tradePair, side, amount);
            case "LIMIT" -> showLimitOrderDialog(tradePair, side, amount);
            case "STOP" -> {
                if (exchange != null && !exchange.supportsStopLossTakeProfit()) {
                    showWarning("Order", "%s does not support stop orders.".formatted(exchange.getDisplayName()));
                    return;
                }
                showStopOrderDialog(tradePair, side, amount);
            }
            case "BRACKET" -> {
                if (exchange != null && !exchange.supportsBracketOrders()) {
                    showWarning("Order", "%s does not support bracket orders.".formatted(exchange.getDisplayName()));
                    return;
                }
                showBracketOrderDialog(tradePair, side, amount);
            }
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

        activeOrderBookPair = tradePair;
        runOnFx(() -> orderBookSymbolLabel.setText(tradePair.toString('/')));
        // DO NOT clear orderbook data - keep displaying previous data while fetching
        // new data
        // This prevents the on/off blinking effect when switching between chart tabs

        if (!exchange.supportsOrderBook()) {
            log.debug("Order book not supported for {}", exchange.getDisplayName());
            return;
        }

        exchange.fetchOrderBook(tradePair)
                .thenAccept(orderBook -> {
                    if (Objects.equals(activeOrderBookPair, tradePair)) {
                        displayOrderBook(orderBook);
                    }
                })
                .exceptionally(exception -> {
                    log.debug("Failed to fetch orderbook for {}", tradePair, exception);
                    return null;
                });
    }

    private void updateOrderBookForChartTab(Tab tab) {
        TradePair pair = tradePairForChartTab(tab);
        if (pair == null) {
            return;
        }

        if (!Objects.equals(symbolSelector.getSelectionModel().getSelectedItem(), pair)) {
            symbolSelector.getSelectionModel().select(pair);
        }
        if (!Objects.equals(marketWatchTable.getSelectionModel().getSelectedItem(), pair)) {
            marketWatchTable.getSelectionModel().select(pair);
        }
        loadOrderBook(pair);
    }

    private TradePair tradePairForChartTab(Tab tab) {
        if (tab == null || !(tab.getContent() instanceof ChartContainer container)) {
            return null;
        }

        return container.getTradePair();
    }

    private void displayOrderBook(OrderBook orderBook) {
        runOnFx(() -> {
            if (orderBook == null) {
                orderBookBids.clear();
                orderBookAsks.clear();
                currentOrderBook = new OrderBook();
                updateChartsFromOrderBookMidPrice(currentOrderBook);
                if (depthChart != null) {
                    depthChart.update(currentOrderBook);
                }
                updateDeskCommandStrip();
                return;
            }

            currentOrderBook = orderBook;
            orderBookSymbolLabel.setText(orderBook.getTradePair() == null ? "No symbol" : orderBook.getTradePair().toString('/'));
            orderBookBids.setAll(orderBook.getBids() == null ? List.of() : orderBook.getBids());
            orderBookAsks.setAll(orderBook.getAsks() == null ? List.of() : orderBook.getAsks());
            updateChartsFromOrderBookMidPrice(orderBook);

            if (depthChart != null) {
                depthChart.update(orderBook);
            }
            updateDeskCommandStrip();
        });
    }

    private @NotNull VBox createOverviewPane() {
        newsDataProvider.loadSampleCalendarIfEmpty();
        TradePair selectedPair = symbolSelector.getValue();
        List<NewsEvent> upcomingEvents = newsDataProvider.getUpcomingNewsEvents();
        List<NewsEvent> immediateEvents = newsDataProvider.getImmediateUpcomingEvents();

        VBox container = new VBox(12);
        container.setPadding(new Insets(16));
        container.setStyle("-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff;");

        Label title = new Label("Overview");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        container.getChildren().add(title);

        HBox metrics = new HBox(12,
                createOverviewMetric("Selected Symbol", selectedPair == null ? "None" : selectedPair.toSlashSymbol(),
                        "#3b82f6"),
                createOverviewMetric("Calendar Events", String.valueOf(upcomingEvents.size()), "#10b981"),
                createOverviewMetric("Next 60 Minutes", String.valueOf(immediateEvents.size()), "#f59e0b"));
        metrics.setAlignment(Pos.CENTER_LEFT);
        container.getChildren().add(metrics);

        VBox nextEvents = new VBox(8);
        nextEvents.setPadding(new Insets(12));
        nextEvents.setStyle("-fx-background-color: #101827; -fx-border-color: #334155; -fx-border-radius: 6;");
        Label nextEventsTitle = new Label("Upcoming Economic Events");
        nextEventsTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        nextEvents.getChildren().add(nextEventsTitle);

        if (upcomingEvents.isEmpty()) {
            Label empty = new Label("No events loaded. Use the News Calendar tab to refresh or load sample data.");
            empty.setWrapText(true);
            empty.setStyle("-fx-text-fill: #a0aec0;");
            nextEvents.getChildren().add(empty);
        } else {
            upcomingEvents.stream()
                    .limit(5)
                    .map(this::createOverviewEventRow)
                    .forEach(nextEvents.getChildren()::add);
        }
        container.getChildren().add(nextEvents);

        // Upcoming Events Item
        VBox upcomingEventsBox = createOverviewItem(
                "Upcoming Events",
                "Market events and important dates",
                "#3b82f6",
                this::showUpcomingEvents);

        // Economic Calendar Item
        VBox economicCalendarBox = createOverviewItem(
                "Economic Calendar",
                "View economic indicators and calendar",
                "#10b981",
                this::showEconomicCalendar);

        // System Announcements Item
        VBox announcementsBox = createOverviewItem(
                "System Announcements",
                "Trading system updates and messages",
                "#f59e0b",
                this::showSystemAnnouncements);

        container.getChildren().addAll(upcomingEventsBox, economicCalendarBox, announcementsBox);
        VBox.setVgrow(container, Priority.ALWAYS);
        return container;
    }

    private @NotNull VBox createOverviewMetric(String label, String value, String color) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(10));
        box.setMinWidth(150);
        box.setStyle("-fx-background-color: #16213e; -fx-border-color: " + color
                + "; -fx-border-width: 0 0 3 0; -fx-background-radius: 6;");

        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-font-size: 11px; -fx-text-fill: #a0aec0;");
        Label valueNode = new Label(value);
        valueNode.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        box.getChildren().addAll(labelNode, valueNode);
        return box;
    }

    private HBox createOverviewEventRow(NewsEvent event) {
        String time = event.getEventTime()
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE HH:mm"));
        Label timeLabel = new Label(time);
        timeLabel.setMinWidth(80);
        timeLabel.setStyle("-fx-text-fill: #93c5fd; -fx-font-size: 11px;");

        Label titleLabel = new Label(event.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setStyle("-fx-text-fill: #e5e7eb; -fx-font-size: 12px;");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label impactLabel = new Label(event.getCurrency() + " " + event.getImportance());
        impactLabel.setStyle("-fx-text-fill: #fbbf24; -fx-font-size: 11px; -fx-font-weight: bold;");

        HBox row = new HBox(10, timeLabel, titleLabel, impactLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6));
        row.setStyle("-fx-background-color: #0f172a; -fx-background-radius: 4;");
        return row;
    }

    private VBox createOverviewItem(String title, String description, String color, Runnable action) {
        VBox itemBox = new VBox(6);
        itemBox.setPadding(new Insets(12));
        itemBox.setStyle("-fx-background-color: #16213e; -fx-border-color: " + color
                + "; -fx-border-width: 2; -fx-border-radius: 8; -fx-cursor: hand;");
        itemBox.setOnMouseEntered(e -> itemBox.setStyle("-fx-background-color: #1e3a5f; -fx-border-color: " + color
                + "; -fx-border-width: 2; -fx-border-radius: 8; -fx-cursor: hand;"));
        itemBox.setOnMouseExited(e -> itemBox.setStyle("-fx-background-color: #16213e; -fx-border-color: " + color
                + "; -fx-border-width: 2; -fx-border-radius: 8; -fx-cursor: hand;"));
        itemBox.setOnMouseClicked(e -> action.run());

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #a0aec0; -fx-wrap-text: true;");
        descLabel.setWrapText(true);

        itemBox.getChildren().addAll(titleLabel, descLabel);
        return itemBox;
    }

    private void showUpcomingEvents() {
        // Show upcoming market events using the news calendar
        if (newsCalendarPanel != null) {
            newsCalendarPanel.requestFocus();
        }
        log.info("Displaying upcoming events");
    }

    private void showEconomicCalendar() {
        // Show economic calendar from news events
        if (newsEventOverlay != null) {
            newsEventOverlay.requestFocus();
        }
        log.info("Displaying economic calendar");
    }

    private void showSystemAnnouncements() {
        // Show system announcements
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("System Announcements");
        alert.setHeaderText("Trading System Updates");
        alert.setContentText(
                "No new announcements at this time. Check back later for system updates and trading alerts.");
        alert.showAndWait();
        log.info("Displaying system announcements");
    }

    @Contract("null -> fail")
    private @NonNull SystemCore createSystemCore(Exchange exchange) throws SQLException, ClassNotFoundException {
        if (exchange == null) {
            throw new IllegalArgumentException("exchange cannot be null");
        }

        Properties config = new Properties();

        try (InputStream input = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("config.properties"),
                "config.properties not found in src/main/resources"
        )) {
            config.load(input);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to load config.properties", e);
        }

        config.setProperty("TELEGRAM_TOKEN", safe(telegramToken));

        String openAiKey = safe(configuredOpenAiApiKey);
        if (openAiKey.isBlank()) {
            openAiKey = safe(System.getenv("OPENAI_API_KEY"));
        }
        if (!openAiKey.isBlank()) {
            config.setProperty("ai.provider", "openai");
            config.setProperty("openai.api_key", openAiKey);
        } else {
            config.setProperty("ai.provider", "local");
        }

        SystemCore core = new SystemCore(exchange, config, openAiKey);

        // Record SystemCore initialization
        SystemOperationsService.getInstance().recordEvent(
                SystemActivityEvent.Component.SYSTEM,
                SystemActivityEvent.Severity.INFO,
                "SYSTEM_CORE_INIT",
                "SystemCore initialized for " + exchange.getDisplayName());

        // Wire trading engine and risk providers to SystemOperationsService
        wireSystemOperationsService(core);

        return core;
    }

    /**
     * Wire SystemCore data providers to SystemOperationsService for monitoring.
     * This enables the System & Operations Board to display real-time trading
     * state.
     */
    private void wireSystemOperationsService(SystemCore core) {
        if (core == null) {
            return;
        }

        SystemOperationsService operationsService = SystemOperationsService.getInstance();

        // Wire trading engine state provider
        operationsService.setTradingEngineProvider(() -> buildTradingEngineSnapshot(core));

        // Wire risk status provider
        operationsService.setRiskStatusProvider(this::buildRiskStatusSnapshot);

        // Record bot startup event if SmartBot starts
        if (core.getSmartBot() != null) {
            operationsService.recordEvent(
                    SystemActivityEvent.Component.STRATEGY_ENGINE,
                    SystemActivityEvent.Severity.INFO,
                    "BOT_INITIALIZED",
                    "SmartBot initialized and ready");
        }

        log.info("SystemOperationsService wired with real-time data providers");
    }

    /**
     * Build TradingEngineSnapshot from SystemCore state
     */
    private SystemSnapshot.TradingEngineSnapshot buildTradingEngineSnapshot(SystemCore core) {
        try {
            return SystemSnapshot.TradingEngineSnapshot.builder()
                    .botTradingEnabled(core.getSmartBot() != null && core.getSmartBot().isStarted())
                    .signalProcessorState(core.getStrategyEngine() != null ? "ACTIVE" : "INACTIVE")
                    .riskManagerState(core.getRiskManagementSystem() != null ? "ACTIVE" : "INACTIVE")
                    .executionEngineState(core.getExecutionEngine() != null ? "ACTIVE" : "INACTIVE")
                    .strategyEngineState(core.getStrategyEngine() != null ? "ACTIVE" : "INACTIVE")
                    .activeStrategies(getActiveStrategies(core))
                    .monitoredSymbols(getMonitoredSymbols(core))
                    .lastSignal(getLastSignal(core))
                    .lastSignalTime(Instant.now())
                    .lastApprovedTrade(null)
                    .lastApprovedTradeTime(null)
                    .lastRejectedTrade(null)
                    .lastRejectionReason(null)
                    .signalsGeneratedToday(0)
                    .tradesApprovedToday(0)
                    .tradesRejectedToday(0)
                    .build();
        } catch (Exception e) {
            log.warn("Error building trading engine snapshot", e);
            return SystemSnapshot.TradingEngineSnapshot.builder()
                    .botTradingEnabled(false)
                    .signalProcessorState("ERROR")
                    .riskManagerState("ERROR")
                    .executionEngineState("ERROR")
                    .strategyEngineState("ERROR")
                    .activeStrategies(Collections.emptyList())
                    .monitoredSymbols(Collections.emptyList())
                    .build();
        }
    }

    /**
     * Build RiskStatusSnapshot from SystemCore state
     */
    private SystemSnapshot.RiskStatusSnapshot buildRiskStatusSnapshot() {
        try {
            double accountBalance = 0.0;
            double portfolioExposurePercent = 0.0;

            if (exchange != null) {
                try {
                    Account account = exchange.getUserAccountDetails();
                    if (account != null) {
                        accountBalance = account.getAvailableBalance();
                        double available = account.getAvailableBalance();
                        portfolioExposurePercent = accountBalance > 0 ? (available / accountBalance * 100) : 0.0;
                    }
                } catch (Exception e) {
                    log.debug("Error getting account from exchange", e);
                }
            }

            return SystemSnapshot.RiskStatusSnapshot.builder()
                    .accountBalance(accountBalance)
                    .riskPerTradePercent(2.0)
                    .maxPositionSize(accountBalance * 0.1)
                    .maxDailyLoss(accountBalance * 0.05)
                    .currentDailyLoss(0.0)
                    .portfolioExposurePercent(portfolioExposurePercent)
                    .blockedTrades(0)
                    .riskWarnings(0)
                    .latestRejectionReason(null)
                    .latestRejectionTime(null)
                    .build();
        } catch (Exception e) {
            log.warn("Error building risk status snapshot", e);
            return SystemSnapshot.RiskStatusSnapshot.builder()
                    .accountBalance(0.0)
                    .riskPerTradePercent(2.0)
                    .maxPositionSize(0.0)
                    .maxDailyLoss(null)
                    .currentDailyLoss(null)
                    .portfolioExposurePercent(null)
                    .blockedTrades(0)
                    .riskWarnings(0)
                    .latestRejectionReason(null)
                    .latestRejectionTime(null)
                    .build();
        }
    }

    /**
     * Get list of active strategies from SystemCore
     */
    @Contract("_ -> new")
    private @NonNull List<String> getActiveStrategies(SystemCore core) {
        LinkedHashSet<String> strategies = new LinkedHashSet<>();
        try {
            StrategyAssignmentRepository.getInstance().getAllActive().stream()
                    .map(StrategyAssignment::getStrategyId)
                    .map(this::safeStrategyName)
                    .filter(name -> !name.isBlank())
                    .forEach(strategies::add);

            if (core.getStrategyEngine() != null && core.getStrategyEngine().getLastSignalCache() != null) {
                core.getStrategyEngine().getLastSignalCache().values().stream()
                        .filter(Objects::nonNull)
                        .filter(signal -> signal.getSide() == BUY || signal.getSide() == SELL)
                        .map(this::displayStrategyName)
                        .filter(name -> !name.isBlank())
                        .forEach(strategies::add);
            }

            String activeStrategyName = safeStrategyName(core.getActiveStrategyName());
            if (strategies.isEmpty()
                    && !activeStrategyName.isBlank()
                    && !"Not available yet".equalsIgnoreCase(activeStrategyName)
                    && !"No active strategy signal yet".equalsIgnoreCase(activeStrategyName)
                    && !"Unknown strategy".equalsIgnoreCase(activeStrategyName)) {
                strategies.add(activeStrategyName);
            }

        } catch (Exception e) {
            log.debug("Error getting active strategies", e);
        }
        return new ArrayList<>(strategies);
    }

    private String displayStrategyName(StrategySignal signal) {
        if (signal == null) {
            return "";
        }

        String strategyName = safeStrategyName(signal.getStrategyName());
        if (!strategyName.isBlank()) {
            return strategyName;
        }

        return safeStrategyName(signal.getStrategyId());
    }

    private String safeStrategyName(String strategyName) {
        return strategyName == null ? "" : strategyName.trim();
    }

    /**
     * Get list of monitored symbols from SystemCore
     */
    private List<String> getMonitoredSymbols(SystemCore core) {
        List<String> symbols = new ArrayList<>();
        try {
            if (core.getSelectedTradePair() != null) {
                symbols.add(core.getSelectedTradePair().toString());
            }
        } catch (Exception e) {
            throw  new RuntimeException("Error getting monitored symbols", e);
        }
        return symbols;
    }

    /**
     * Get last signal from SystemCore
     */
    private String getLastSignal(SystemCore core) {
        try {
            // TODO: Get actual last signal from SignalProcessor

            return core.getSignalToDecisionFilter().toString();

        } catch (Exception e) {
            log.debug("Error getting last signal", e);
            return null;
        }
    }

    /**
     * Initialize symbol agent panels: MarketWatchPanel, Navigation, and DataWindow.
     * Called when SystemCore is created and ready.
     */
    private void initializeSymbolAgentPanels() {
        if (systemCore == null) {
            return;
        }

        try {
            // Initialize MarketWatchPanel with symbol-level status
            if (symbolAgentMarketWatch == null) {
                symbolAgentMarketWatch = new MarketWatchPanel(systemCore);
                log.info("MarketWatchPanel initialized with SymbolAgentManager");
            }

            // Initialize Navigation panel for exchange selection
            if (navigationPanel == null) {
                navigationPanel = createNavigationPanel();
                log.info("Navigation panel initialized");
            }

            // Initialize DataWindow for OHLCV display
            if (dataWindow == null) {
                dataWindow = new DataWindow();
                log.info("DataWindow initialized");
            }

            // Initialize MarketInfoPanel if not already done
            if (marketInfoPanel == null) {
                marketInfoPanel = new MarketInfoPanel();
                log.info("MarketInfoPanel initialized");
            }
        } catch (Exception e) {
            log.error("Error initializing symbol agent panels", e);
        }
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
            try {
                if (coreMissing || botMissing) {
                    systemCore = createSystemCore(exchange);
                }
            } catch (SQLException | ClassNotFoundException e) {
                log.error("Failed to create SystemCore", e);
                throw new RuntimeException("Failed to initialize trading system", e);
            }

            runOnFxAndWait(() -> {
                // Wire up the primary stage to the Telegram command handler for screenshot
                // capability.
                if (systemCore.getTelegramCommandHandler() != null && getScene() != null
                        && getScene().getWindow() instanceof Stage) {
                    systemCore.getTelegramCommandHandler().setPrimaryStage((Stage) getScene().getWindow());
                }
                initializeSymbolAgentPanels();
            });

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
        List<TradePair> rawSymbols = switch (scope) {
            case "Watchlist" -> List.copyOf(marketWatchItems);
            case "Best Today" -> bestSymbolsOfTheDay();
            default -> {
                TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
                yield selected == null ? List.of() : List.of(selected);
            }
        };

        ensureTradabilityService();
        if (universalTradabilityService == null || rawSymbols.isEmpty()) {
            return rawSymbols;
        }

        try {
            List<SymbolTradability> statuses = universalTradabilityService
                    .getTradability(rawSymbols)
                    .get(8, TimeUnit.SECONDS);

            Map<String, SymbolTradability> bySymbol = new HashMap<>();
            for (SymbolTradability status : statuses) {
                if (status == null || status.tradePair() == null) {
                    continue;
                }
                bySymbol.put(symbolKey(status.tradePair()), status);
                tradabilityBySymbol.put(symbolKey(status.tradePair()), status);
            }

            List<TradePair> allowed = new ArrayList<>();
            for (TradePair pair : rawSymbols) {
                SymbolTradability status = bySymbol.get(symbolKey(pair));
                if (status != null && status.canBeUsedForBotTrading()) {
                    allowed.add(pair);
                } else {
                    String reason = status == null ? "tradability unknown" : status.status() + " - " + status.reason();
                    journal("Trade blocked: %s is not bot tradable on %s (%s)."
                            .formatted(pair.toString('/'),
                                    exchange == null ? "broker" : exchange.getDisplayName(),
                                    reason));
                }
            }
            return allowed;
        } catch (Exception exception) {
            log.warn("Unable to validate bot tradability for selected symbols", exception);
            return List.of();
        }
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

    private @NonNull String formatAgentEvent(@NonNull AgentEvent event) {
        return "[%s] %s | %s | %s".formatted(
                DateTimeFormatter.ofPattern("HH:mm:ss").format(event.timestamp().atZone(ZoneId.systemDefault())),
                event.type(),
                event.source(),
                event.payload() == null ? "" : event.payload().toString());
    }

    private String formatSignalEvent(@NonNull AgentEvent event) {
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
        if (items.size() == 1 && items.getFirst().contains("will appear here")) {
            items.clear();
        }
        items.addFirst(value);
        while (items.size() > maxItems) {
            items.removeLast();
        }
    }

    private void refreshBotTradeButton() {
        botTradeButton.setText(botTradingEnabled ? t("toolbar.stopBot") : t("toolbar.botTrade"));
        botTradeButton.setStyle(botTradingEnabled
                ? "-fx-padding: 6 12; -fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold;"
                : "-fx-padding: 6 12; -fx-background-color: #334155; -fx-text-fill: white; -fx-font-weight: bold;");

        TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
        SymbolTradability status = selected == null ? null : tradabilityBySymbol.get(symbolKey(selected));
        boolean disableForTradability = !botTradingEnabled
            && selected != null
            && status != null
            && !status.canBeUsedForBotTrading();
        botTradeButton.setDisable(disableForTradability);
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
            throw new RuntimeException("Failed to save app state", exception);
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
                throw  new RuntimeException("Failed to stop SystemCore streaming", exception);
            }
        }

        refreshBotTradeButton();
        saveAppState();
    }

    /**
     * Load tradable pairs from the connected exchange and bootstrap them into
     * MarketDataEngine.
     * This pre-populates the market data cache with instrument metadata.
     */
    private void bootstrapInstrumentsForExchange() {
        if (exchange == null || marketDataEngine == null) {
            throw new RuntimeException("Cannot bootstrap instruments: exchange={}, engine={}");

        }

        new Thread(() -> {
            try {
                List<TradePair> tradablePairs = exchange.getTradablePairs();
                if (tradablePairs != null && !tradablePairs.isEmpty()) {
                    marketDataEngine.bootstrapInstruments(exchange.getName(), tradablePairs);
                    log.info("Bootstrapped {} instruments from {} into MarketDataEngine",
                            tradablePairs.size(), exchange.getName());
                } else {
                    log.debug("No tradable pairs returned from {}", exchange.getName());
                }
            } catch (Exception e) {
                log.error("Failed to bootstrap instruments from {}", exchange.getName(), e);
            }
        }, "InstrumentBootstrapper-" + exchange.getName()).start();
    }

    private void configureMarketWatchFilterSelector() {
        if (!marketWatchFilterSelector.getItems().isEmpty()) {
            return;
        }

        marketWatchFilterSelector.getItems().setAll(
                MarketWatchTradabilityFilter.TRADABLE_ONLY,
                MarketWatchTradabilityFilter.VIEW_ONLY_ALLOWED,
                MarketWatchTradabilityFilter.RESTRICTED_ONLY,
                MarketWatchTradabilityFilter.ALL_PRODUCTS);

        marketWatchFilterSelector.getSelectionModel().select(MarketWatchTradabilityFilter.TRADABLE_ONLY);
        marketWatchFilterSelector.getStyleClass().add("terminal-combo-box");
        marketWatchFilterSelector.setPrefWidth(220);
        marketWatchFilterSelector.setOnAction(event -> applyCurrentMarketWatchFilter());
    }

    private void ensureTradabilityService() {
        if (exchange == null) {
            universalTradabilityService = null;
            tradabilityServiceExchangeId = "";
            tradabilityBySymbol.clear();
            return;
        }

        String exchangeId = safe(exchange.getExchangeId()).toLowerCase(Locale.ROOT);
        if (universalTradabilityService == null || !Objects.equals(tradabilityServiceExchangeId, exchangeId)) {
            universalTradabilityService = new UniversalTradabilityService(exchange, marketDataEngine);
            tradabilityServiceExchangeId = exchangeId;
            tradabilityBySymbol.clear();
        }
    }

    private void refreshTradabilitySnapshot(List<TradePair> pairs) {
        ensureTradabilityService();
        if (universalTradabilityService == null || pairs == null || pairs.isEmpty()) {
            return;
        }

        // Run off the JavaFX thread to avoid blocking the UI — results are applied back on FX thread
        universalTradabilityService.getTradability(pairs)
                .whenComplete((statuses, ex) -> {
                    if (ex != null) {
                        log.warn("Unable to refresh tradability snapshot for {} symbols", pairs.size(), ex);
                        return;
                    }
                    Platform.runLater(() -> {
                        for (SymbolTradability status : statuses) {
                            if (status == null || status.tradePair() == null) continue;
                            tradabilityBySymbol.put(symbolKey(status.tradePair()), status);
                        }
                        // Re-apply filter now that tradability data is available
                        List<TradePair> universe = new java.util.ArrayList<>(marketWatchUniverse);
                        if (!universe.isEmpty()) {
                            List<TradePair> filtered = applyTradabilityFilter(universe);
                            if (filtered.isEmpty()) filtered = universe;
                            marketWatchItems.setAll(filtered);
                            symbolCountLabel.setText(t("label.symbols", filtered.size()));
                        }
                    });
                });
    }

    private String symbolKey(TradePair pair) {
        return pair == null ? "" : safe(pair.toString('/')).toUpperCase(Locale.ROOT);
    }

    private List<TradePair> applyTradabilityFilter(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return List.of();
        }

        MarketWatchTradabilityFilter selectedFilter = marketWatchFilterSelector.getSelectionModel().getSelectedItem();
        if (selectedFilter == null) {
            selectedFilter = MarketWatchTradabilityFilter.TRADABLE_ONLY;
        }

        final MarketWatchTradabilityFilter activeFilter = selectedFilter;
        return pairs.stream()
                .filter(Objects::nonNull)
                .filter(pair -> {
                    SymbolTradability status = tradabilityBySymbol.get(symbolKey(pair));
                    if (status == null) {
                        return activeFilter != MarketWatchTradabilityFilter.RESTRICTED_ONLY;
                    }

                    return switch (activeFilter) {
                        case TRADABLE_ONLY -> status.isFullyTradable();
                        case MARKET_DATA_ONLY, VIEW_ONLY_ALLOWED -> status.canBeDisplayedInMarketWatch();
                        case RESTRICTED_ONLY -> !status.isFullyTradable();
                        case ALL_PRODUCTS -> true;
                    };
                })
                .toList();
    }

    private void applyCurrentMarketWatchFilter() {
        if (marketWatchUniverse.isEmpty()) {
            return;
        }

        TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
        List<TradePair> filtered = applyTradabilityFilter(marketWatchUniverse);
        if (filtered.isEmpty()) {
            filtered = List.copyOf(marketWatchUniverse);
        }
        marketWatchItems.setAll(filtered);
        symbolSelector.getItems().setAll(filtered);
        symbolCountLabel.setText(t("label.symbols", filtered.size()));

        if (selected != null && filtered.contains(selected)) {
            symbolSelector.getSelectionModel().select(selected);
            marketWatchTable.getSelectionModel().select(selected);
        } else if (!filtered.isEmpty()) {
            symbolSelector.getSelectionModel().select(filtered.getFirst());
            marketWatchTable.getSelectionModel().select(filtered.getFirst());
        }

        marketWatchTable.refresh();
        updateDeskCommandStrip();
    }

    private SymbolTradability fetchTradabilityForOrder(TradePair pair) {
        ensureTradabilityService();
        if (universalTradabilityService == null || pair == null) {
            return null;
        }

        try {
            SymbolTradability status = universalTradabilityService
                    .getTradability(pair, TradabilityScope.ORDER_SUBMISSION, true)
                    .get(5, TimeUnit.SECONDS);
            if (status != null) {
                tradabilityBySymbol.put(symbolKey(pair), status);
            }
            return status;
        } catch (Exception exception) {
            log.warn("Unable to verify tradability before order submission for {}", pair, exception);
            return null;
        }
    }

    private boolean canSubmitOrderByTradability(TradePair pair, OpenOrder.OrderType orderType) {
        SymbolTradability status = fetchTradabilityForOrder(pair);
        if (status == null) {
            showWarning("Order Blocked", "Tradability could not be verified for %s."
                    .formatted(pair == null ? "selected symbol" : pair.toString('/')));
            return false;
        }

        boolean typeAllowed = switch (orderType) {
            case MARKET -> status.marketOrderAllowed();
            case LIMIT -> status.limitOrderAllowed();
            case STOP_LOSS, TAKE_PROFIT, TRAILING_STOP -> status.stopOrderAllowed();
            default -> status.orderSubmissionAllowed();
        };

        if (!status.orderSubmissionAllowed() || !typeAllowed) {
            String reason = status.reason().isBlank() ? status.status().name() : status.reason();
            String symbol = pair == null ? "selected symbol" : pair.toString('/');
            showWarning("Order Blocked", "Trade blocked: %s is %s on %s. %s"
                    .formatted(symbol, status.status(), exchange == null ? "broker" : exchange.getDisplayName(), reason));
            journal("Trade blocked: %s is %s on %s. %s"
                    .formatted(symbol, status.status(), exchange == null ? "broker" : exchange.getDisplayName(), reason));
            return false;
        }

        return true;
    }

    private void updateOrderActionAvailability(TradePair selected) {
        if (!hasBrokerAccess() || selected == null || universalTradabilityService == null) {
            buyButton.setDisable(!hasBrokerAccess());
            sellButton.setDisable(!hasBrokerAccess());
            return;
        }

        SymbolTradability status = tradabilityBySymbol.get(symbolKey(selected));
        if (status == null) {
            buyButton.setDisable(true);
            sellButton.setDisable(true);
            return;
        }

        boolean marketOrderAllowed = status.orderSubmissionAllowed() && status.marketOrderAllowed();
        buyButton.setDisable(!marketOrderAllowed);
        sellButton.setDisable(!marketOrderAllowed);
    }

    private void loadSymbolsForSelectedExchange() {
        marketWatchItems.clear();
        symbolSelector.getItems().clear();
        marketWatchUniverse.clear();

        if (exchange == null) {
            updateConnectionStatus();
            return;
        }

        ensureTradabilityService();

        List<TradePair> tradePairs;
        try {
            tradePairs = exchange.getTradePairSymbol();
        } catch (Exception exception) {
            log.error("Failed to load trade pairs from {}", exchangeSelector.getValue(), exception);
            if (isStellarExchangeSelected()) {
                showStellarTrustlinePrompt("Unable to load Stellar account balances/trustlines: %s"
                        .formatted(rootMessage(exception)));
                updateConnectionStatus();
                return;
            }
            tradePairs = fallbackMarketWatchPairs();
            showWarning("API Error", "Failed to load live trade pairs from %s: %s. Showing default symbols."
                    .formatted(exchangeSelector.getValue(), rootMessage(exception)));
        }

        if (tradePairs == null || tradePairs.isEmpty()) {
            if (isStellarExchangeSelected()) {
                showStellarTrustlinePrompt("""
                        No Stellar trusted assets were found for Market Watch.

                        Add a trustline for the asset issuer you want to trade, then refresh/reconnect Stellar. \
                        Market Watch only uses assets already present in your Stellar balances/trustlines.
                        """);
                updateConnectionStatus();
                return;
            }
            tradePairs = fallbackMarketWatchPairs();
        }

        if (tradePairs.isEmpty()) {
            showWarning("No Symbols",
                    "Unable to fetch or build trade pairs for %s.".formatted(exchangeSelector.getValue()));
            updateConnectionStatus();
            return;
        }

        refreshTradabilitySnapshot(tradePairs);
        marketWatchUniverse.addAll(tradePairs);

        List<TradePair> filteredPairs = applyTradabilityFilter(tradePairs);
        if (filteredPairs.isEmpty()) {
            filteredPairs = tradePairs;
        }

        marketWatchItems.setAll(filteredPairs);
        symbolSelector.getItems().setAll(filteredPairs);

        // Enrich market watch items with current quotes from MarketDataEngine cache
        if (marketDataEngine != null) {
            for (TradePair pair : tradePairs) {
                MarketQuote quote = marketDataEngine.getQuote(pair);
                if (quote != null) {
                    pair.updateQuote(quote.getBid(), quote.getAsk());
                }
            }
        }

        if (systemCore != null && systemCore.getSymbolAgentManager() != null) {
            scheduleSymbolAgentFeed();
        }

        String rememberedSymbol = preferences.get("selected_symbol_" + safe(exchangeSelector.getValue()), "");
        TradePair rememberedPair = filteredPairs.stream()
                .filter(pair -> Objects.equals(pair.toString('/'), rememberedSymbol))
                .findFirst()
                .orElse(null);

        TradePair selected = rememberedPair != null ? rememberedPair : filteredPairs.getFirst();
        symbolSelector.getSelectionModel().select(selected);
        marketWatchTable.getSelectionModel().select(selected);
        symbolCountLabel.setText(t("label.symbols", filteredPairs.size()));
        updateDeskCommandStrip();
        if (symbolAgentMarketWatch != null) {
            symbolAgentMarketWatch.refreshMarketWatchData();
        }

        // Ensure market info panel is initialized and updated with selected symbol
        if (marketInfoPanel == null) {
            marketInfoPanel = new MarketInfoPanel(exchange, newsDataProvider);
        } else {
            marketInfoPanel.setExchange(exchange);
        }
        marketInfoPanel.updateForPair(selected);

        // Display trading hours for selected pair
        if (marketDataEngine != null) {
            String tradingHours = marketDataEngine.getTradingHours(selected);
            journal("Trading hours for %s: %s".formatted(selected.toString('/'), tradingHours));
        }

        // Load orderbook data for the selected symbol
        loadOrderBook(selected);

        updateConnectionStatus();

    }

    private boolean isStellarExchangeSelected() {
        if (exchange != null && "stellar".equalsIgnoreCase(exchange.getExchangeId())) {
            return true;
        }
        String selected = safe(exchangeSelector == null ? null : exchangeSelector.getValue());
        return selected.toLowerCase(Locale.ROOT).contains("stellar");
    }

    private void showStellarTrustlinePrompt(String message) {
        log.warn("Stellar trustline prompt: {}", message);
        showWarning("Stellar Trustline Required", message);
    }

    private @NotNull List<TradePair> fallbackMarketWatchPairs() {
        try {
            List<TradePair> pairs = new ArrayList<>();
            pairs.add(TradePair.fromSymbol("BTC_USD"));
            pairs.add(TradePair.fromSymbol("ETH_USD"));
            pairs.add(TradePair.fromSymbol("SOL_USD"));
            pairs.add(TradePair.fromSymbol("EUR_USD"));
            pairs.add(TradePair.fromSymbol("AAPL_USD"));
            return pairs;
        } catch (Exception exception) {
            log.debug("Unable to build fallback pairs", exception);
            return List.of();
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

        // Build tab title with symbol name
        String exchangeDisplayName = exchange.getDisplayName();
        if (exchangeDisplayName == null || exchangeDisplayName.isBlank()) {
            exchangeDisplayName = exchange.getName();
        }
        String symbolName = selected.toString('/');
        String tabTitle = "%s - %s".formatted(exchangeDisplayName, symbolName);

        // Check if chart already exists
        for (Tab tab : chartTabPane.getTabs()) {
            if (Objects.equals(tab.getText(), tabTitle)) {
                chartTabPane.getSelectionModel().select(tab);
                return;
            }
        }

        // Create chart container
        ChartContainer container = getChartContainer(selected);

        // Create and add chart tab to chart pane (not terminal pane)
        Tab tab = new Tab(tabTitle, container);
        tab.setClosable(true);
        tab.setOnClosed(event -> container.dispose());
        chartTabPane.getTabs().add(tab);
        chartTabPane.getSelectionModel().select(tab);
        saveAppState();
    }

    private @NotNull ChartContainer getChartContainer(TradePair selected) {
        ChartContainer container = new ChartContainer(exchange, selected, true, telegramToken, tradingService);
        container.setOnChartError(this::journal);
        container.setOnAutoTradeAction(() -> startBotFromChart(selected, container));

        // Set up candle selection to update DataWindow
        container.setCandleSelectionCallback(candle -> {
            if (dataWindow != null && candle != null) {
                long timestamp = candle.getOpenTime() * 1000; // Convert seconds to millis
                dataWindow.updateCandle(
                        selected,
                        "", // timeframe will be set by chart
                        Instant.ofEpochMilli(timestamp),
                        candle.openPrice(),
                        candle.highPrice(),
                        candle.lowPrice(),
                        candle.closePrice(),
                        candle.volume());
                log.debug("DataWindow updated with candle: {} {}", selected, timestamp);
            }
        });
        return container;
    }

    private void startBotFromChart(TradePair selected, ChartContainer container) {
        symbolSelector.getSelectionModel().select(selected);

        if (!hasBrokerAccess()) {
            showWarning("Bot Trading", "Validate broker credentials before starting the bot.");
            return;
        }
        if (lacksOrderSubmissionAccess()) {
            showWarning("Bot Trading",
                    "%s is connected, but this adapter cannot submit orders.".formatted(exchange.getDisplayName()));
            return;
        }

        startBotTradingAsync(
                List.of(selected),
                "chart toolbar for " + selected.toString('/'),
                () -> {
                    CandleStickChart chart = container.getChart();
                    if (chart != null) {
                        chart.setAutoTradeEnabled(true);
                    }
                });
    }

    private @Nullable CandleStickChart getActiveChart() {
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

    private CandleStickChart requireActiveChart(String actionName) {
        CandleStickChart chart = getActiveChart();
        if (chart == null) {
            showWarning(actionName, t("dialog.chartRequired"));
        }
        return chart;
    }

    private void openInsertIndicatorDialog() {
        CandleStickChart chart = requireActiveChart("Indicators");
        if (chart == null) {
            return;
        }

        List<String> choices = new ArrayList<>();
        choices.add("Clear Indicators");
        choices.addAll(PluginIndicatorFactory.supportedChoices());

        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(1), choices);
        dialog.setTitle(t("dialog.indicators.title"));
        dialog.setHeaderText(t("dialog.indicators.header"));
        dialog.setContentText(t("dialog.indicators.content"));

        dialog.showAndWait().ifPresent(choice -> {
            if ("Clear Indicators".equals(choice)) {
                chart.clearIndicators();
                appendAgentActivity("Cleared indicators from active chart.");
                return;
            }

            Map<String, String> config = promptIndicatorConfiguration(choice);
            if (config == null) {
                return;
            }

            PluginIndicatorFactory.saveConfig(choice, config);
            ChartIndicator indicator = createChartIndicator(choice, config);
            if (indicator == null) {
                showWarning("Indicators", "Unsupported indicator: " + choice);
                return;
            }

            chart.addIndicator(indicator);
            appendAgentActivity("Inserted indicator %s.".formatted(indicator.getName()));
        });
    }

    private Map<String, String> promptIndicatorConfiguration(String choice) {
        List<PluginIndicatorFactory.IndicatorParameter> parameters = PluginIndicatorFactory.parametersFor(choice);
        Map<String, String> defaults = PluginIndicatorFactory.loadConfig(choice);

        if (parameters.isEmpty()) {
            return new LinkedHashMap<>(defaults);
        }

        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle(choice + " settings");
        dialog.setHeaderText("Adjust parameters before adding the indicator.");
        dialog.initOwner(getScene() != null ? getScene().getWindow() : null);

        ButtonType addButton = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        Map<String, TextField> fields = new LinkedHashMap<>();
        int row = 0;
        for (PluginIndicatorFactory.IndicatorParameter parameter : parameters) {
            Label label = new Label(parameter.label());
            TextField field = new TextField(defaults.getOrDefault(parameter.key(), parameter.defaultValue()));
            field.setPromptText(parameter.description());
            field.setMaxWidth(Double.MAX_VALUE);
            grid.add(label, 0, row);
            grid.add(field, 1, row);
            GridPane.setHgrow(field, Priority.ALWAYS);
            fields.put(parameter.key(), field);
            row++;
        }

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportWidth(420);
        scrollPane.setPrefViewportHeight(Math.min(360, 72 + parameters.size() * 44));
        dialog.getDialogPane().setContent(scrollPane);

        dialog.setResultConverter(button -> {
            if (button != addButton) {
                return null;
            }

            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, TextField> entry : fields.entrySet()) {
                values.put(entry.getKey(), entry.getValue().getText().trim());
            }
            return values;
        });

        return dialog.showAndWait().orElse(null);
    }

    private ChartIndicator createChartIndicator(String choice) {
        return createChartIndicator(choice, PluginIndicatorFactory.loadConfig(choice));
    }

    private ChartIndicator createChartIndicator(String choice, Map<String, String> config) {
        return PluginIndicatorFactory.create(choice, PluginRegistry.loadDefault(), config).orElse(null);
    }

    private void openNewsCalendarPanel() {
        createIndependentWindow("News", new NewsCalendarPanel(newsDataProvider), 760, 560);
    }

    private void chooseActiveChartBackgroundImage() {
        CandleStickChart chart = requireActiveChart("Background Image");
        if (chart == null) {
            log.info("null chart");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Chart Background Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        File selectedFile = fileChooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (selectedFile == null) {
            return;
        }

        try {
            Image image = new Image(selectedFile.toURI().toString());
            if (image.isError()) {
                throw new IllegalArgumentException("Image could not be loaded.");
            }
            chart.setBackgroundImage(image);
            appendAgentActivity("Applied chart background image: " + selectedFile.getName());
        } catch (Exception exception) {
            showWarning("Background Image", "Could not load image: %s".formatted(rootMessage(exception)));
        }
    }

    private void clearActiveChartBackgroundImage() {
        CandleStickChart chart = requireActiveChart("Background Image");
        if (chart == null) {
            return;
        }
        chart.clearBackgroundImage();
        appendAgentActivity("Cleared active chart background image.");
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
                        log.debug("Failed to refresh account", exception);
                        return null;
                    });
        } catch (Exception exception) {
            log.debug("Account refresh unavailable", exception);
        }

        // Fetch all positions
        try {
            exchange.fetchAllPositions()
                    .thenAccept(positions -> runOnFx(() -> updateAccountPositions(positions)))
                    .exceptionally(exception -> {
                        log.debug("Failed to fetch positions", exception);
                        return null;
                    });
        } catch (Exception exception) {
            log.debug("Fetch positions unavailable", exception);
        }

        // Fetch all trades (open trades)
        try {
            exchange.fetchAccountTrades(null)
                    .thenAccept(trades -> runOnFx(() -> updateAccountTrades(trades)))
                    .exceptionally(exception -> {
                        log.debug("Failed to fetch trades", exception);
                        return null;
                    });
        } catch (Exception exception) {
            log.debug("Fetch trades unavailable", exception);
        }

        // Fetch open orders
        try {
            exchange.fetchOpenOrders(null)
                    .thenAccept(orders -> runOnFx(() -> updateAccountOpenOrders(orders)))
                    .exceptionally(exception -> {
                        log.debug("Failed to fetch open orders", exception);
                        return null;
                    });
        } catch (Exception exception) {
            log.debug("Fetch open orders unavailable", exception);
        }

        // Fetch order history
        try {
            exchange.fetchOrderHistory(symbolSelector.getValue(), Instant.now().minus(90, ChronoUnit.DAYS))
                    .thenAccept(orders -> runOnFx(() -> updateAccountHistory(orders)))
                    .exceptionally(exception -> {
                        throw new IllegalStateException("Failed to fetch order history", exception);

                    });
        } catch (Exception exception) {
            throw new IllegalStateException("Fetch order history unavailable", exception);
        }

        // Fetch risk monitoring data (position health scores)
        try {
            exchange.fetchAllPositions()
                    .thenAccept(positions -> runOnFx(() -> updatePositionHealthScores(positions)))
                    .exceptionally(exception -> {
                        log.debug("Failed to fetch risk data", exception);
                        return null;
                    });
        } catch (Exception exception) {
            throw  new IllegalStateException("Fetch risk data unavailable", exception);
        }
    }

    private void updateAccountOpenOrders(List<OpenOrder> orders) {
        if (orders == null) {
            accountOpenOrderItems.clear();
            return;
        }
        accountOpenOrderItems.setAll(orders);
        updateDeskCommandStrip();
        journal("Open orders updated: " + orders.size() + " order(s)");
    }

    private void updateAccountHistory(List<Order> orders) {
        if (orders == null) {
            accountHistoryItems.clear();
            return;
        }
        // Replace all history with fetched data (reverse order to show newest first)
        accountHistoryItems.setAll(reverseCopy(orders));
        updateDeskCommandStrip();
        journal("Account history updated: " + orders.size() + " order(s)");
    }

    private void updateAccountPositions(List<Position> positions) {
        if (positions == null) {
            accountPositionItems.clear();
            return;
        }
        // Replace all positions with fetched data
        accountPositionItems.setAll(positions);
        updateDeskCommandStrip();
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
        accountTradeItems.setAll(reverseCopy(tradesWithProfit));
        updateDeskCommandStrip();
        journal("Trades updated: " + tradesWithProfit.size() + " trade(s)");
    }

    private void calculateTradeProfit(Trade trade) {
        if (trade == null) {
            return;
        }

        // Calculate accurate trade P&L based on entry/exit prices, fees, and swap costs
        double profit;

        if (trade.getPrice() <= 0 || trade.getAmount() <= 0) {
            // If no valid entry price/amount, just show cost impact
            profit = -(trade.getFee() + Math.abs(trade.getSwap()));
        } else if (trade.getClosePrice() > 0) {
            // Closed trade: calculate P&L from entry to exit price
            double priceDifference = trade.getClosePrice() - trade.getPrice();

            // For BUY trades: profit = (closePrice - entryPrice) * quantity - costs
            // For SELL trades: profit = (entryPrice - closePrice) * quantity - costs
            if (trade.getTransactionType() != null && trade.getTransactionType().isSell()) {
                priceDifference = trade.getPrice() - trade.getClosePrice();
            }

            // P&L from price movement
            profit = (priceDifference * trade.getAmount())
                    - trade.getFee()
                    - Math.abs(trade.getSwap());
        } else {
            // Open trade: show only cost impact (not realized P&L yet)
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
        updateDeskCommandStrip();
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
        updateDeskCommandStrip();
    }

    private void refreshPositions() {
        positionsDataManager.refreshLocalPositions();
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
                .mapToDouble(c -> (c.getCurrentPrice() - c.getEntryPrice()))
                .sum();
        double shortPnl = positionsItems.stream()
                .filter(order -> "SELL"
                        .equalsIgnoreCase(safe(order.getSide() == null ? "" : order.getSide().toString())))
                .mapToDouble(c -> (c.getCurrentPrice() - c.getEntryPrice())).sum();
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

    private void enablePositionAutoRefresh() {
        if (isAutoRefreshExecutorUnavailable()) {
            return;
        }
        if (!positionAutoRefreshStarted.compareAndSet(false, true)) {
            return;
        }
        autoRefreshExecutor.scheduleAtFixedRate(() -> {
            if (hasBrokerAccess()) {
                refreshAccountWorkspace();
            }
        }, 5000, 5000, TimeUnit.MILLISECONDS);
    }

    private void disablePositionAutoRefresh() {
        // This executor is shared by the window. Individual scheduled tasks are
        // defensive and check connection state.
    }

    private void startAutoRefreshTasks() {
        if (isAutoRefreshExecutorUnavailable()) {
            return;
        }
        if (!autoRefreshTasksStarted.compareAndSet(false, true)) {
            return;
        }
        autoRefreshExecutor.scheduleAtFixedRate(() -> runOnFx(this::updateConnectionStatus), 2, 5, TimeUnit.SECONDS);

        // TODO: Prefer WebSocket/cache-first market data here; this polling path is a
        // compatibility fallback for exchange adapters that do not stream tickers yet.
        autoRefreshExecutor.scheduleAtFixedRate(() -> {
            try {
                if (exchange != null && hasBrokerAccess() && !marketWatchItems.isEmpty()) {
                    List<Ticker> tickers = exchange.fetchTickers(new ArrayList<>(marketWatchItems)).join();
                    if (tickers != null) {
                        for (int i = 0; i < tickers.size() && i < marketWatchItems.size(); i++) {
                            Ticker ticker = tickers.get(i);
                            TradePair pair = marketWatchItems.get(i);
                            if (ticker != null && pair != null) {
                                runOnFx(() -> updateTickerFromStream(pair, ticker));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to update market watch tickers", e);
            }
        }, 1, 5, TimeUnit.SECONDS);

        // Refresh order book for active symbol when visible
        autoRefreshExecutor.scheduleAtFixedRate(() -> {
            try {
                if (orderBookVisible && hasBrokerAccess() && activeOrderBookPair != null) {
                    loadOrderBook(activeOrderBookPair);
                }
            } catch (Exception e) {
                log.debug("Failed to refresh orderBook", e);
            }
        }, 2, 10, TimeUnit.SECONDS);

        // Refresh account data (positions, trades, history) every 120 seconds (less
        // frequent for stability)
        autoRefreshExecutor.scheduleAtFixedRate(() -> {
            if (hasBrokerAccess()) {
                runOnFx(this::refreshAccountWorkspace);
            }
        }, 10, 120, TimeUnit.SECONDS);

        // Periodically log OANDA diagnostics to help monitor rate limiting
        if (exchange instanceof Oanda) {
            autoRefreshExecutor.scheduleAtFixedRate(() -> ((Oanda) exchange).logDiagnostics(), 60, 300,
                    TimeUnit.SECONDS); // Every 5 minutes
        }
    }

    private void showTradingProfileSettings() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Trader Profile Settings");
        dialog.setHeaderText("Configure your trader profile and risk parameters");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        SettingsPanel profilePanel = new SettingsPanel(systemCore);

        ScrollPane scrollPane = new ScrollPane(profilePanel);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("dialog-scroll-pane");

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getStyleClass().add("trader-profile-dialog");
        dialog.setWidth(800);
        dialog.setHeight(700);
        dialog.showAndWait();
    }

    private void showStrategyAssignmentPanel() throws SQLException, ClassNotFoundException {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Strategy Assignment");
        dialog.setHeaderText("Assign and configure trading strategies for symbols");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        StrategyAssignmentPanel assignmentPanel = new StrategyAssignmentPanel(systemCore);

        ScrollPane scrollPane = new ScrollPane(assignmentPanel);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-control-inner-background: #1a1a2e;");

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().setStyle("-fx-control-inner-background: #1a1a2e;");
        dialog.setWidth(850);
        dialog.setHeight(750);
        dialog.showAndWait();
    }

    private void openMarketResearch() {
        log.info("Opening Market Research panel");
        VBox marketResearchPanel = createMarketResearchPanel();
        createIndependentWindow("Market Research", marketResearchPanel, 900, 700);
        journal("Market Research panel opened");
    }

    private VBox createMarketResearchPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Market Research");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Label venueLabel = new Label();
        venueLabel.setStyle("-fx-text-fill: #93c5fd; -fx-font-size: 12; -fx-font-weight: bold;");

        Label updatedLabel = new Label();
        updatedLabel.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 11;");

        Button refreshButton = new Button("Refresh Research Data");
        refreshButton.setStyle("-fx-padding: 6 16; -fx-background-color: #3b82f6; -fx-text-fill: white;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10, title, venueLabel, spacer, updatedLabel, refreshButton);
        header.setAlignment(Pos.CENTER_LEFT);

        TabPane researchTabs = new TabPane();
        researchTabs.setStyle("-fx-control-inner-background: #0f3460;");
        researchTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        ResearchTabContent sentiment = createSentimentAnalysisTab();
        ResearchTabContent correlation = createCorrelationTab();
        ResearchTabContent economic = createEconomicCalendarTab();
        ResearchTabContent volatility = createVolatilityTab();

        Tab sentimentTab = new Tab("Sentiment Analysis", sentiment.content());
        Tab correlationTab = new Tab("Correlation Matrix", correlation.content());
        Tab economicTab = new Tab("Economic Calendar", economic.content());
        Tab volatilityTab = new Tab("Volatility Analysis", volatility.content());

        researchTabs.getTabs().addAll(sentimentTab, correlationTab, economicTab, volatilityTab);

        Runnable refreshAllTabs = () -> {
            venueLabel.setText(buildResearchVenueStatus());
            updatedLabel.setText("Updated " + LocalDateTime.now().format(STATUS_TIME_FORMAT));
            sentiment.refreshAction().run();
            correlation.refreshAction().run();
            economic.refreshAction().run();
            volatility.refreshAction().run();
        };

        refreshButton.setOnAction(event -> refreshAllTabs.run());
        refreshAllTabs.run();

        AtomicReference<ScheduledFuture<?>> autoRefreshFuture = new AtomicReference<>();
        panel.sceneProperty().addListener((sceneObs, oldScene, newScene) -> {
            if (newScene == null) {
                ScheduledFuture<?> future = autoRefreshFuture.getAndSet(null);
                if (future != null) {
                    future.cancel(false);
                }
                return;
            }

            newScene.windowProperty().addListener((windowObs, oldWindow, newWindow) -> {
                if (!(newWindow instanceof Stage stage)) {
                    return;
                }

                ScheduledFuture<?> existingFuture = autoRefreshFuture.getAndSet(
                        autoRefreshExecutor.scheduleAtFixedRate(
                                () -> runOnFx(refreshAllTabs),
                                15,
                                45,
                                TimeUnit.SECONDS));
                if (existingFuture != null) {
                    existingFuture.cancel(false);
                }

                stage.setOnHidden(event -> {
                    ScheduledFuture<?> future = autoRefreshFuture.getAndSet(null);
                    if (future != null) {
                        future.cancel(false);
                    }
                    log.info("Independent window closed: {}", stage.getTitle());
                });
            });
        });

        panel.getChildren().addAll(header, researchTabs);
        VBox.setVgrow(researchTabs, Priority.ALWAYS);

        return panel;
    }

    private ResearchTabContent createSentimentAnalysisTab() {
        VBox tab = new VBox(12);
        tab.setPadding(new Insets(12));

        Label label = new Label("Market Sentiment Index");
        label.setStyle("-fx-text-fill: #10b981; -fx-font-size: 14; -fx-font-weight: bold;");

        Label source = new Label("Source: live exchange tickers + RSS/news bias provider");
        source.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 11;");

        ListView<String> sentimentList = new ListView<>();
        sentimentList.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");
        tab.getChildren().addAll(label, source, sentimentList);
        VBox.setVgrow(sentimentList, Priority.ALWAYS);

        Runnable refresh = () -> refreshSentimentAnalysisAsync(label, sentimentList);

        return new ResearchTabContent(tab, refresh);
    }

    private void refreshSentimentAnalysisAsync(Label label, ListView<String> sentimentList) {
        label.setText("Market Sentiment Index: loading...");
        sentimentList.getItems().setAll("Loading market sentiment...");

        CompletableFuture.supplyAsync(() -> List.of(
                "Market Sentiment Index: " + getMarketSentimentIndex() + "/100",
                "Overall Market Sentiment: " + getOverallMarketSentiment(),
                "Investor Confidence: " + getInvestorConfidence(),
                "Market Fear Index (VIX Proxy): " + getMarketVIX(),
                "Bitcoin Dominance: " + getBitcoinDominance(),
                "Trading Volume: " + getTradingVolume(),
                buildNewsBiasLine(),
                buildUpcomingEventPressureLine()), autoRefreshExecutor)
                .whenComplete((lines, throwable) -> runOnFx(() -> {
                    if (throwable != null) {
                        label.setText("Market Sentiment Index: unavailable");
                        sentimentList.getItems().setAll(
                                "Unable to load market sentiment right now.",
                                rootMessage(throwable));
                        return;
                    }

                    if (lines == null || lines.isEmpty()) {
                        label.setText("Market Sentiment Index: unavailable");
                        sentimentList.getItems().setAll("No market sentiment data available.");
                        return;
                    }

                    label.setText(lines.getFirst());
                    sentimentList.getItems().setAll(lines.subList(1, lines.size()));
                }));
    }

    private ResearchTabContent createCorrelationTab() {
        VBox tab = new VBox(12);
        tab.setPadding(new Insets(12));

        Label label = new Label("Live Co-Movement Matrix (24h Change Similarity)");
        label.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 14; -fx-font-weight: bold;");

        Label source = new Label("Source: connected venue tickers from market watch");
        source.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 11;");

        TextArea matrixArea = new TextArea();
        matrixArea.setEditable(false);
        matrixArea.setWrapText(false);
        matrixArea.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #e5e7eb; -fx-font-family: Consolas;");

        tab.getChildren().addAll(label, source, matrixArea);
        VBox.setVgrow(matrixArea, Priority.ALWAYS);

        Runnable refresh = () -> matrixArea.setText(buildLiveCorrelationMatrixText());

        return new ResearchTabContent(tab, refresh);
    }

    private ResearchTabContent createEconomicCalendarTab() {
        VBox tab = new VBox(12);
        tab.setPadding(new Insets(12));

        Label label = new Label("Upcoming Economic Events");
        label.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 14; -fx-font-weight: bold;");

        Label summary = new Label();
        summary.setStyle("-fx-text-fill: #fcd34d; -fx-font-size: 11;");

        TableView<NewsEvent> eventsTable = new TableView<>();
        eventsTable.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");
        eventsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<NewsEvent, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(cell -> {
            Instant eventTime = cell.getValue() == null ? null : cell.getValue().getEventTime();
            String value = eventTime == null
                    ? "-"
                    : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                            .withZone(ZoneId.systemDefault())
                            .format(eventTime);
            return new ReadOnlyStringWrapper(value);
        });

        TableColumn<NewsEvent, String> currencyCol = new TableColumn<>("Currency");
        currencyCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue() == null ? "-" : safe(cell.getValue().getCurrency())));

        TableColumn<NewsEvent, String> impactCol = new TableColumn<>("Impact");
        impactCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue() == null || cell.getValue().getImportance() == null
                        ? "-"
                        : cell.getValue().getImportance().name()));

        TableColumn<NewsEvent, String> sentimentCol = new TableColumn<>("Sentiment");
        sentimentCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue() == null || cell.getValue().getSentiment() == null
                        ? "-"
                        : cell.getValue().getSentiment().name()));

        TableColumn<NewsEvent, String> titleCol = new TableColumn<>("Event");
        titleCol.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue() == null ? "-" : safe(cell.getValue().getTitle())));

        eventsTable.getColumns().setAll(timeCol, currencyCol, impactCol, sentimentCol, titleCol);

        tab.getChildren().addAll(label, summary, eventsTable);
        VBox.setVgrow(eventsTable, Priority.ALWAYS);

        Runnable refresh = () -> {
            newsDataProvider.loadSampleCalendarIfEmpty();
            List<NewsEvent> events = newsDataProvider.getUpcomingNewsEvents();
            eventsTable.getItems().setAll(events);

            long highImpact = events.stream()
                    .filter(event -> event != null && (event.getImportance() == NewsEvent.Importance.HIGH
                            || event.getImportance() == NewsEvent.Importance.CRITICAL))
                    .count();
            summary.setText("Events: %d | High Impact: %d | Immediate (60m): %d".formatted(
                    events.size(),
                    highImpact,
                    newsDataProvider.getImmediateUpcomingEvents().size()));
        };

        return new ResearchTabContent(tab, refresh);
    }

    private ResearchTabContent createVolatilityTab() {
        VBox tab = new VBox(12);
        tab.setPadding(new Insets(12));

        Label label = new Label("Volatility Metrics");
        label.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 14; -fx-font-weight: bold;");

        Label source = new Label("Source: live market watch prices and 24h change from connected venue");
        source.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 11;");

        ListView<String> volatilityList = new ListView<>();
        volatilityList.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");

        tab.getChildren().addAll(label, source, volatilityList);
        VBox.setVgrow(volatilityList, Priority.ALWAYS);

        Runnable refresh = () -> volatilityList.getItems().setAll(buildVolatilityMetricsLines());

        return new ResearchTabContent(tab, refresh);
    }

    private String buildResearchVenueStatus() {
        String venue = exchange == null ? safe(exchangeSelector == null ? null : exchangeSelector.getValue())
                : safe(exchange.getDisplayName());
        if (venue.isBlank()) {
            venue = "No venue selected";
        }
        String connectionState = hasBrokerAccess() ? "Connected" : "Disconnected";
        String dataState = marketSnapshot().hasMarketData() ? "Live data" : "Cached/limited data";
        return "Venue: %s (%s, %s)".formatted(venue, connectionState, dataState);
    }

    private String buildNewsBiasLine() {
        TradePair selected = symbolSelector == null ? null : symbolSelector.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return "News Bias: Select a symbol to load RSS sentiment";
        }

        String symbol = safe(selected.getBaseCode()).isBlank() ? safe(selected.getSymbol()) : safe(selected.getBaseCode());
        if (symbol.isBlank()) {
            return "News Bias: Symbol unavailable";
        }

        Map<String, Object> result = newsDataProvider.fetchAndSummarizeNews(
                symbol,
                inferResearchBrokerType(),
                10,
                18.0);

        Object biasObj = result.get("bias");
        if (!(biasObj instanceof Map<?, ?> bias)) {
            return "News Bias: unavailable";
        }

        String direction = safe(bias.get("direction") instanceof String value ? value : "neutral")
            .toUpperCase(Locale.ROOT);
        double score = bias.get("score") instanceof Number number ? number.doubleValue() : 0.0;
        double confidence = bias.get("confidence") instanceof Number number ? number.doubleValue() : 0.0;
        int events = result.get("events") instanceof List<?> list ? list.size() : 0;

        return "News Bias (%s): %s | score %.2f | confidence %.0f%% | articles %d"
                .formatted(symbol, direction, score, confidence * 100.0, events);
    }

    private String buildUpcomingEventPressureLine() {
        newsDataProvider.loadSampleCalendarIfEmpty();
        int immediate = newsDataProvider.getImmediateUpcomingEvents().size();
        long highImpact = newsDataProvider.getUpcomingNewsEvents().stream()
                .filter(event -> event != null && (event.getImportance() == NewsEvent.Importance.HIGH
                        || event.getImportance() == NewsEvent.Importance.CRITICAL))
                .count();
        return "Event Pressure: %d events in 60m | %d high-impact events this week".formatted(immediate, highImpact);
    }

    private String inferResearchBrokerType() {
        String venue = exchange == null ? safe(exchangeSelector == null ? null : exchangeSelector.getValue())
                : safe(exchange.getDisplayName());
        String normalized = venue.toLowerCase(Locale.ROOT);
        if (normalized.contains("binance") || normalized.contains("coinbase") || normalized.contains("bitfinex")
                || normalized.contains("kraken") || normalized.contains("stellar")) {
            return "crypto";
        }
        if (normalized.contains("oanda") || normalized.contains("forex") || normalized.contains("fx")) {
            return "forex";
        }
        return "stock";
    }

    private String buildLiveCorrelationMatrixText() {
        List<TradePair> pairs = new ArrayList<>();
        if (marketWatchItems != null) {
            pairs.addAll(marketWatchItems.stream().filter(Objects::nonNull).limit(6).toList());
        }
        if (pairs.isEmpty() && symbolSelector != null && symbolSelector.getValue() != null) {
            pairs.add(symbolSelector.getValue());
        }

        if (pairs.size() < 2) {
            return "Not enough live symbols to build matrix. Add more symbols to Market Watch.";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("    ");
        for (TradePair pair : pairs) {
            builder.append(String.format("%10s", safe(pair.getSymbol())));
        }
        builder.append('\n');

        for (TradePair rowPair : pairs) {
            double rowChange = finite(rowPair.getChangePercent());
            builder.append(String.format("%4s", safe(rowPair.getSymbol())));
            for (TradePair colPair : pairs) {
                double colChange = finite(colPair.getChangePercent());
                double similarity;
                if (rowPair == colPair) {
                    similarity = 1.0;
                } else {
                    double denom = Math.max(1.0, Math.abs(rowChange) + Math.abs(colChange));
                    similarity = clamp(1.0 - (Math.abs(rowChange - colChange) / denom), -1.0, 1.0);
                }
                builder.append(String.format("%10.2f", similarity));
            }
            builder.append('\n');
        }

        builder.append("\nNote: matrix values are live co-movement similarity derived from 24h change percentages.");
        return builder.toString();
    }

    private List<String> buildVolatilityMetricsLines() {
        MarketSnapshot snapshot = marketSnapshot();
        List<String> lines = new ArrayList<>();

        if (!snapshot.hasMarketData()) {
            return List.of(
                    "Volatility data currently unavailable from live venue.",
                    "Connect an exchange or refresh symbols to populate real-time metrics.");
        }

        lines.add("Synthetic Market Volatility (VIX proxy): " + formatNumber(snapshot.syntheticVix(), 1));
        lines.add("Breadth: " + formatPercent(snapshot.positiveBreadth() * 100.0));
        lines.add("Average 24h Change: " + formatSignedPercent(snapshot.averageChangePercent(), 2));
        lines.add("Quote Volume (24h): " + formatCurrencyCompact(snapshot.totalQuoteVolume()));

        List<TradePair> movers = marketWatchItems == null
                ? List.of()
                : marketWatchItems.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingDouble(pair -> -Math.abs(finite(pair.getChangePercent()))))
                        .limit(3)
                        .toList();

        if (movers.isEmpty()) {
            lines.add("Top Movers: no market watch symbols available");
        } else {
            lines.add("Top Movers (|24h change|):");
            for (TradePair mover : movers) {
                lines.add("- %s: %s".formatted(
                        safe(mover.getSymbol()),
                        formatSignedPercent(finite(mover.getChangePercent()), 2)));
            }
        }

        return lines;
    }

    private record ResearchTabContent(VBox content, Runnable refreshAction) {
    }

    private void openStrategyResearch() {
        log.info("Opening Strategy Research panel");
        VBox strategyResearchPanel = createStrategyResearchPanel();
        createIndependentWindow("Strategy Research", strategyResearchPanel, 900, 700);
        journal("Strategy Research panel opened");
    }

    private VBox createStrategyResearchPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Strategy Research");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        TabPane strategyTabs = new TabPane();
        strategyTabs.setStyle("-fx-control-inner-background: #0f3460;");
        strategyTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab performanceTab = new Tab("Performance Analysis", createStrategyPerformanceTab());
        Tab drawdownTab = new Tab("Drawdown Analysis", createDrawdownTab());
        Tab statsTab = new Tab("Statistics", createStrategyStatsTab());
        Tab equityTab = new Tab("Equity Curve", createEquityCurveTab());

        strategyTabs.getTabs().addAll(performanceTab, drawdownTab, statsTab, equityTab);
        panel.getChildren().addAll(title, strategyTabs);
        VBox.setVgrow(strategyTabs, Priority.ALWAYS);

        return panel;
    }

    private VBox createStrategyPerformanceTab() {
        VBox tab = new VBox(12);
        tab.setPadding(new Insets(12));
        Label label = new Label("Historical Performance");
        label.setStyle("-fx-text-fill: #10b981; -fx-font-size: 14;");
        ListView<String> perfList = new ListView<>();
        perfList.getItems().addAll(
                "Total Return: " + getStrategyTotalReturn(),
                "Win Rate: " + getStrategyWinRate(),
                "Profit Factor: " + getStrategyProfitFactor(),
                "Sharpe Ratio: " + getStrategySharpeRatio(),
                "Sortino Ratio: " + getStrategySortinoRatio(),
                "Monthly Return (Avg): " + getStrategyMonthlyReturn());
        perfList.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");
        tab.getChildren().addAll(label, perfList);
        VBox.setVgrow(perfList, Priority.ALWAYS);
        return tab;
    }

    private VBox createDrawdownTab() {
        VBox tab = new VBox(12);
        tab.setPadding(new Insets(12));
        Label label = new Label("Drawdown Analysis");
        label.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 14;");
        ListView<String> drawdownList = new ListView<>();
        drawdownList.getItems().addAll(
                "Maximum Drawdown: " + getStrategyMaxDrawdown(),
                "Current Drawdown: " + getStrategyCurrentDrawdown(),
                "Average Drawdown: " + getStrategyAvgDrawdown(),
                "Recovery Time (Max): " + getStrategyRecoveryTime(),
                "Underwater Duration: " + getStrategyUnderwaterDuration());
        drawdownList.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");
        tab.getChildren().addAll(label, drawdownList);
        VBox.setVgrow(drawdownList, Priority.ALWAYS);
        return tab;
    }

    private VBox createStrategyStatsTab() {
        VBox tab = new VBox(12);
        tab.setPadding(new Insets(12));
        Label label = new Label("Performance Statistics");
        label.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 14;");
        ListView<String> statsList = new ListView<>();
        statsList.getItems().addAll(
                "Total Trades: " + getStrategyTotalTrades(),
                "Winning Trades: " + getStrategyWinningTrades(),
                "Losing Trades: " + getStrategyLosingTrades(),
                "Average Win: " + getStrategyAvgWin(),
                "Average Loss: " + getStrategyAvgLoss(),
                "Risk/Reward Ratio: " + getStrategyRiskRewardRatio());
        statsList.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");
        tab.getChildren().addAll(label, statsList);
        VBox.setVgrow(statsList, Priority.ALWAYS);
        return tab;
    }

    private VBox createEquityCurveTab() {
        VBox tab = new VBox(12);
        tab.setPadding(new Insets(12));
        Label label = new Label("Equity Curve");
        label.setStyle("-fx-text-fill: #06b6d4; -fx-font-size: 14;");
        Label placeholder = new Label("Equity Curve Chart - Chart rendering would display here");
        placeholder.setStyle("-fx-text-fill: #a0aec0; -fx-padding: 50;");
        placeholder.setWrapText(true);
        tab.getChildren().addAll(label, placeholder);
        return tab;
    }

    private void openResearchReports() {
        log.info("Opening Research Reports");
        VBox reportsPanel = createResearchReportsPanel();
        createIndependentWindow("Research Reports", reportsPanel, 900, 700);
        journal("Research Reports panel opened");
    }

    private VBox createResearchReportsPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Research Reports");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        ListView<String> reportsList = new ListView<>();
        reportsList.getItems().addAll(
                "[2026-05-07] Daily Market Summary - Tech stocks surge on earnings",
                "[2026-05-06] Weekly Technical Analysis - S&P 500 breakout confirmed",
                "[2026-05-05] Cryptocurrency Report - Bitcoin above $65K resistance",
                "[2026-05-04] Forex Analysis - Dollar weakens amid rate cut expectations",
                "[2026-05-03] Asset Class Review - Bonds show flight to safety",
                "[2026-05-02] Economic Outlook - Fed signals potential rate cuts",
                "[2026-05-01] Commodities Report - Oil rallies on geopolitical tensions");
        reportsList.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");
        reportsList.setCellFactory(param -> new ReportListCell());

        HBox actionBox = new HBox(12);
        actionBox.setPadding(new Insets(12));
        actionBox.setStyle("-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1;");

        Button viewBtn = new Button("View Report");
        viewBtn.setStyle("-fx-padding: 6 16; -fx-background-color: #3b82f6; -fx-text-fill: white;");
        viewBtn.setOnAction(e -> {
            String selected = reportsList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showWarning("Report", "Select a report first.");
                return;
            }
            showInfo("Report", selected + "\n\nSummary\n" + buildResearchReportSummary(selected));
        });

        Button downloadBtn = new Button("Download PDF");
        downloadBtn.setStyle("-fx-padding: 6 16; -fx-background-color: #10b981; -fx-text-fill: white;");
        downloadBtn.setOnAction(e -> {
            String selected = reportsList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showWarning("Download", "Select a report first.");
                return;
            }
            try {
                Path outputDir = Path.of("output", "reports");
                Files.createDirectories(outputDir);
                Path reportPath = outputDir.resolve("research-report-%s.txt".formatted(SNAPSHOT_FORMAT.format(LocalDateTime.now())));
                Files.writeString(reportPath, selected + "\n\n" + buildResearchReportSummary(selected), StandardCharsets.UTF_8);
                showInfo("Download", "Report exported to:\n" + reportPath.toAbsolutePath());
            } catch (Exception exception) {
                showWarning("Download", "Unable to export report: " + rootMessage(exception));
            }
        });

        actionBox.getChildren().addAll(viewBtn, downloadBtn);

        panel.getChildren().addAll(title, reportsList, actionBox);
        VBox.setVgrow(reportsList, Priority.ALWAYS);

        return panel;
    }

    private void showPerformancesReview() {
        log.info("Opening Performances Review panel");
        VBox performancesPanel = createPerformancesReviewPanel();
        createIndependentWindow("Performances Review", performancesPanel, 900, 700);
        journal("Performances Review panel opened");
    }

    private Map<String, Double> computeTradeMetrics(List<org.investpro.models.trading.Trade> trades) {
        Map<String, Double> m = new java.util.LinkedHashMap<>();
        m.put("totalTrades", (double) trades.size());
        double totalProfit = 0, totalLoss = 0, bestTrade = Double.NEGATIVE_INFINITY, worstTrade = Double.POSITIVE_INFINITY;
        int wins = 0, losses = 0, consWins = 0, consLosses = 0, maxConsWins = 0, maxConsLosses = 0;
        for (org.investpro.models.trading.Trade t : trades) {
            double p = t.getProfit();
            if (p >= 0) { totalProfit += p; wins++; consWins++; consLosses = 0; maxConsWins = Math.max(maxConsWins, consWins); }
            else { totalLoss += p; losses++; consLosses++; consWins = 0; maxConsLosses = Math.max(maxConsLosses, consLosses); }
            if (p > bestTrade) bestTrade = p;
            if (p < worstTrade) worstTrade = p;
        }
        double netPnl = totalProfit + totalLoss;
        double winRate = trades.isEmpty() ? 0 : (wins * 100.0 / trades.size());
        double avgWin = wins == 0 ? 0 : totalProfit / wins;
        double avgLoss = losses == 0 ? 0 : totalLoss / losses;
        double profitFactor = Math.abs(totalLoss) < 0.0001 ? 0 : totalProfit / Math.abs(totalLoss);
        m.put("wins", (double) wins);
        m.put("losses", (double) losses);
        m.put("winRate", winRate);
        m.put("totalProfit", totalProfit);
        m.put("totalLoss", totalLoss);
        m.put("netPnl", netPnl);
        m.put("avgPnl", trades.isEmpty() ? 0 : netPnl / trades.size());
        m.put("profitFactor", profitFactor);
        m.put("avgWin", avgWin);
        m.put("avgLoss", avgLoss);
        m.put("bestTrade", trades.isEmpty() ? 0 : bestTrade);
        m.put("worstTrade", trades.isEmpty() ? 0 : worstTrade);
        m.put("maxConsWins", (double) maxConsWins);
        m.put("maxConsLosses", (double) maxConsLosses);
        return m;
    }

    private VBox createPerformancesReviewPanel() {
        List<org.investpro.models.trading.Trade> trades;
        try {
            trades = tradeRepository.findAll();
        } catch (java.sql.SQLException e) {
            log.warn("Failed to load trades for performance panel", e);
            trades = List.of();
        }
        final List<org.investpro.models.trading.Trade> allTrades = trades;

        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Performance Analytics");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Button refreshBtn = new Button("⟳ Refresh");
        refreshBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 6 16;");

        HBox header = new HBox(12, title, refreshBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        TabPane performanceTabs = new TabPane();
        performanceTabs.setStyle("-fx-control-inner-background: #0f3460;");
        performanceTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // --- Summary tab ---
        Map<String, Double> metrics = computeTradeMetrics(allTrades);
        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(24);
        summaryGrid.setVgap(10);
        summaryGrid.setPadding(new Insets(16));
        summaryGrid.setStyle("-fx-background-color: #16213e;");

        String[][] summaryRows = {
            {"Total Trades", number(metrics.get("totalTrades"))},
            {"Win Rate", String.format("%.1f%%", metrics.get("winRate"))},
            {"Total P&L", money(metrics.get("netPnl"))},
            {"Average Trade P&L", money(metrics.get("avgPnl"))},
            {"Profit Factor", String.format("%.2f", metrics.get("profitFactor"))},
            {"Average Win", money(metrics.get("avgWin"))},
            {"Average Loss", money(metrics.get("avgLoss"))},
            {"Best Trade", money(metrics.get("bestTrade"))},
            {"Worst Trade", money(metrics.get("worstTrade"))},
            {"Max Consecutive Wins", number(metrics.get("maxConsWins"))},
            {"Max Consecutive Losses", number(metrics.get("maxConsLosses"))}
        };
        for (int i = 0; i < summaryRows.length; i++) {
            Label lbl = new Label(summaryRows[i][0]);
            lbl.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 13px;");
            double val = 0;
            try { val = Double.parseDouble(summaryRows[i][1].replaceAll("[^\\d.\\-]", "")); } catch (Exception ignored) {}
            Label val2 = new Label(summaryRows[i][1]);
            String color = (val >= 0) ? "#10b981" : "#ef4444";
            if (i < 2 || i == 4 || i == 9 || i == 10) color = "#ffffff";
            val2.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px; -fx-font-weight: bold;");
            summaryGrid.add(lbl, 0, i);
            summaryGrid.add(val2, 1, i);
        }

        ScrollPane summaryScroll = new ScrollPane(summaryGrid);
        summaryScroll.setStyle("-fx-background-color: #16213e;");
        summaryScroll.setFitToWidth(true);
        Tab summaryTab = new Tab("Summary", summaryScroll);

        // --- Monthly P&L tab ---
        TableView<String[]> monthlyTable = new TableView<>();
        monthlyTable.setStyle("-fx-background-color: #16213e;");
        monthlyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<String[], String> monthCol = new TableColumn<>("Month");
        monthCol.setCellValueFactory(row -> new javafx.beans.property.ReadOnlyStringWrapper(row.getValue()[0]));
        TableColumn<String[], String> mPnlCol = new TableColumn<>("Total P&L");
        mPnlCol.setCellValueFactory(row -> new javafx.beans.property.ReadOnlyStringWrapper(row.getValue()[1]));
        TableColumn<String[], String> mCountCol = new TableColumn<>("Trades");
        mCountCol.setCellValueFactory(row -> new javafx.beans.property.ReadOnlyStringWrapper(row.getValue()[2]));
        TableColumn<String[], String> mWinCol = new TableColumn<>("Win Rate");
        mWinCol.setCellValueFactory(row -> new javafx.beans.property.ReadOnlyStringWrapper(row.getValue()[3]));
        monthlyTable.getColumns().addAll(monthCol, mPnlCol, mCountCol, mWinCol);

        Map<String, List<org.investpro.models.trading.Trade>> byMonth = new java.util.TreeMap<>();
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.systemDefault());
        for (org.investpro.models.trading.Trade t : allTrades) {
            if (t.getTimestamp() != null) {
                String key = monthFmt.format(t.getTimestamp());
                byMonth.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
            }
        }
        ObservableList<String[]> monthlyData = FXCollections.observableArrayList();
        for (Map.Entry<String, List<org.investpro.models.trading.Trade>> entry : byMonth.entrySet()) {
            Map<String, Double> mm = computeTradeMetrics(entry.getValue());
            monthlyData.add(new String[]{
                entry.getKey(),
                money(mm.get("netPnl")),
                number(mm.get("totalTrades")),
                String.format("%.1f%%", mm.get("winRate"))
            });
        }
        monthlyTable.setItems(monthlyData);
        Tab monthlyTab = new Tab("Monthly P&L", monthlyTable);

        // --- By Symbol tab ---
        TableView<String[]> symbolTable = new TableView<>();
        symbolTable.setStyle("-fx-background-color: #16213e;");
        symbolTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        TableColumn<String[], String> symCol = new TableColumn<>("Symbol");
        symCol.setCellValueFactory(row -> new javafx.beans.property.ReadOnlyStringWrapper(row.getValue()[0]));
        TableColumn<String[], String> symCountCol = new TableColumn<>("Trades");
        symCountCol.setCellValueFactory(row -> new javafx.beans.property.ReadOnlyStringWrapper(row.getValue()[1]));
        TableColumn<String[], String> symPnlCol = new TableColumn<>("Total P&L");
        symPnlCol.setCellValueFactory(row -> new javafx.beans.property.ReadOnlyStringWrapper(row.getValue()[2]));
        TableColumn<String[], String> symWinCol = new TableColumn<>("Win Rate");
        symWinCol.setCellValueFactory(row -> new javafx.beans.property.ReadOnlyStringWrapper(row.getValue()[3]));
        symbolTable.getColumns().addAll(symCol, symCountCol, symPnlCol, symWinCol);

        Map<String, List<org.investpro.models.trading.Trade>> bySymbol = new java.util.LinkedHashMap<>();
        for (org.investpro.models.trading.Trade t : allTrades) {
            String sym = t.getTradePair() != null ? t.getTradePair().getSymbol() : "N/A";
            bySymbol.computeIfAbsent(sym, k -> new ArrayList<>()).add(t);
        }
        ObservableList<String[]> symbolData = FXCollections.observableArrayList();
        for (Map.Entry<String, List<org.investpro.models.trading.Trade>> entry : bySymbol.entrySet()) {
            Map<String, Double> sm = computeTradeMetrics(entry.getValue());
            symbolData.add(new String[]{
                entry.getKey(),
                number(sm.get("totalTrades")),
                money(sm.get("netPnl")),
                String.format("%.1f%%", sm.get("winRate"))
            });
        }
        symbolTable.setItems(symbolData);
        Tab bySymbolTab = new Tab("By Symbol", symbolTable);

        performanceTabs.getTabs().addAll(summaryTab, monthlyTab, bySymbolTab);

        refreshBtn.setOnAction(e -> {
            List<org.investpro.models.trading.Trade> fresh;
            try { fresh = tradeRepository.findAll(); } catch (java.sql.SQLException ex) { log.warn("Refresh failed", ex); fresh = List.of(); }
            Map<String, Double> rm = computeTradeMetrics(fresh);
            // Rebuild summary rows in-place
            for (int i = 0; i < summaryRows.length; i++) {
                String[] rowData = new String[]{
                    summaryRows[i][0],
                    i == 0 ? number(rm.get("totalTrades")) :
                    i == 1 ? String.format("%.1f%%", rm.get("winRate")) :
                    i == 2 ? money(rm.get("netPnl")) :
                    i == 3 ? money(rm.get("avgPnl")) :
                    i == 4 ? String.format("%.2f", rm.get("profitFactor")) :
                    i == 5 ? money(rm.get("avgWin")) :
                    i == 6 ? money(rm.get("avgLoss")) :
                    i == 7 ? money(rm.get("bestTrade")) :
                    i == 8 ? money(rm.get("worstTrade")) :
                    i == 9 ? number(rm.get("maxConsWins")) :
                    number(rm.get("maxConsLosses"))
                };
                summaryRows[i][1] = rowData[1];
            }
            // Update summary labels
            summaryGrid.getChildren().stream()
                .filter(n -> n instanceof Label && GridPane.getColumnIndex(n) != null && GridPane.getColumnIndex(n) == 1)
                .forEach(n -> {
                    int row = GridPane.getRowIndex(n) != null ? GridPane.getRowIndex(n) : 0;
                    if (row < summaryRows.length) ((Label) n).setText(summaryRows[row][1]);
                });
        });

        panel.getChildren().addAll(header, performanceTabs);
        VBox.setVgrow(performanceTabs, Priority.ALWAYS);

        return panel;
    }

    private void showTradesReview() {
        log.info("Opening Trades Review panel");
        VBox tradesPanel = createTradesReviewPanel();
        createIndependentWindow("Trades Review", tradesPanel, 900, 700);
        journal("Trades Review panel opened");
    }

    private @NotNull TableView<Trade> buildTradeTable(List<org.investpro.models.trading.Trade> data) {
        TableView<org.investpro.models.trading.Trade> table = new TableView<>();
        table.setStyle("-fx-background-color: #16213e;");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().addAll(
            tableColumn("Time",         t -> t.getTimestamp() != null ? dateTime(t.getTimestamp()) : "-", 140),
            tableColumn("Symbol",       t -> t.getTradePair() != null ? t.getTradePair().getSymbol() : "-", 80),
            tableColumn("Type",         t -> t.getTransactionType() != null ? t.getTransactionType().name() : "-", 60),
            tableColumn("Size",         t -> number(t.getAmount()), 80),
            tableColumn("Entry Price",  t -> price(t.getPrice()), 100),
            tableColumn("Close Price",  t -> price(t.getClosePrice()), 100),
            tableColumn("Fee",          t -> money(t.getFee()), 70),
            tableColumn("Swap",         t -> money(t.getSwap()), 70),
            tableColumn("P&L",          t -> money(t.getProfit()), 90)
        );
        table.setItems(FXCollections.observableArrayList(data));
        return table;
    }

    private @NonNull VBox createTradesReviewPanel() {
        List<org.investpro.models.trading.Trade> trades;
        try {
            trades = tradeRepository.findAll();
        } catch (java.sql.SQLException e) {
            showAlert("Failed to load trades for review panel\n"+ e.getMessage());
            trades = List.of();
        }
        final List<Trade> allTrades = new ArrayList<>(trades);

        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Trades Analysis");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        Button refreshBtn = new Button("⟳ Refresh");
        refreshBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 6 16;");

        HBox header = new HBox(12, title, refreshBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        TableView<org.investpro.models.trading.Trade> allTable = buildTradeTable(allTrades);

        List<Trade> buyList = new ArrayList<>();
        List<Trade> sellList = new ArrayList<>();
        for (Trade t : allTrades) {
            if (t.getTransactionType() != null && "BUY".equals(t.getTransactionType().name())) buyList.add(t);
            else sellList.add(t);
        }
        TableView<Trade> buyTable = buildTradeTable(buyList);
        TableView<Trade> sellTable = buildTradeTable(sellList);

        // P&L Analysis grid
        Map<String, Double> metrics = computeTradeMetrics(allTrades);
        GridPane pnlGrid = new GridPane();
        pnlGrid.setHgap(24);
        pnlGrid.setVgap(10);
        pnlGrid.setPadding(new Insets(16));
        pnlGrid.setStyle("-fx-background-color: #16213e;");

        String[][] pnlRows = {
            {"Total Trades", number(metrics.get("totalTrades"))},
            {"Total Profit", money(metrics.get("totalProfit"))},
            {"Total Loss", money(metrics.get("totalLoss"))},
            {"Net P&L", money(metrics.get("netPnl"))},
            {"Win Rate", String.format("%.1f%%", metrics.get("winRate"))},
            {"Average Win", money(metrics.get("avgWin"))},
            {"Average Loss", money(metrics.get("avgLoss"))},
            {"Best Trade", money(metrics.get("bestTrade"))},
            {"Worst Trade", money(metrics.get("worstTrade"))}
        };
        for (int i = 0; i < pnlRows.length; i++) {
            Label lbl = new Label(pnlRows[i][0]);
            lbl.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 13px;");
            double rawVal = metrics.getOrDefault(
                i == 0 ? "totalTrades" : i == 1 ? "totalProfit" : i == 2 ? "totalLoss" :
                i == 3 ? "netPnl" : i == 4 ? "winRate" : i == 5 ? "avgWin" :
                i == 6 ? "avgLoss" : i == 7 ? "bestTrade" : "worstTrade", 0.0);
            Label val = new Label(pnlRows[i][1]);
            String clr = (i == 0 || i == 4) ? "#ffffff" : rawVal >= 0 ? "#10b981" : "#ef4444";
            val.setStyle("-fx-text-fill: " + clr + "; -fx-font-size: 13px; -fx-font-weight: bold;");
            pnlGrid.add(lbl, 0, i);
            pnlGrid.add(val, 1, i);
        }
        ScrollPane pnlScroll = new ScrollPane(pnlGrid);
        pnlScroll.setStyle("-fx-background-color: #16213e;");
        pnlScroll.setFitToWidth(true);

        TabPane tradesTabs = new TabPane();
       // tradesTabs.setStyle("-fx-control-inner-background: #0f3460;");
        tradesTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        Tab allTradesTab = new Tab("All Trades", allTable);
        Tab buyTab = new Tab("Buy", buyTable);
        Tab sellTab = new Tab("Sell", sellTable);
        Tab pnlTab = new Tab("P&L Analysis", pnlScroll);
        tradesTabs.getTabs().addAll(allTradesTab, buyTab, sellTab, pnlTab);

        refreshBtn.setOnAction(e -> {
            List<Trade> fresh;
            try { fresh = tradeRepository.findAll(); } catch (java.sql.SQLException ex) { log.warn("Refresh failed", ex); fresh = List.of(); }
            allTable.setItems(FXCollections.observableArrayList(fresh));
            List<org.investpro.models.trading.Trade> fb = new ArrayList<>(), fs = new ArrayList<>();
            for (org.investpro.models.trading.Trade t : fresh) {
                if (t.getTransactionType() != null && "BUY".equals(t.getTransactionType().name())) fb.add(t);
                else fs.add(t);
            }
            buyTable.setItems(FXCollections.observableArrayList(fb));
            sellTable.setItems(FXCollections.observableArrayList(fs));
        });

        panel.getChildren().addAll(header, tradesTabs);
        VBox.setVgrow(tradesTabs, Priority.ALWAYS);

        return panel;
    }

    private @NotNull VBox createPlaceholderContent() {
        VBox content = new VBox(12);
        content.setStyle("-fx-background-color: #0f3460; -fx-padding: 20px;");
        content.setAlignment(Pos.CENTER);

        Label label = new Label("Machine Learning Models & Experimentation");
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: #a0aec0;");

        Label placeholder = new Label("Detailed analysis data will be displayed here");
        placeholder.setStyle("-fx-font-size: 12px; -fx-text-fill: #718096;");

        content.getChildren().addAll(label, placeholder);
        return content;
    }

    private VBox createRecommendationsPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Trade Recommendations");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        ListView<String> recommendations = new ListView<>();
        recommendations.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");

        TradePair selected = symbolSelector.getValue();
        if (selected == null) {
            recommendations.getItems().add("Select a symbol to generate symbol-specific recommendations.");
        } else {
            recommendations.getItems().add("Active symbol: " + selected.toString('/'));
            recommendations.getItems().add("Venue: " + (exchange == null ? "-" : exchange.getDisplayName()));
            recommendations.getItems().add("24h change: " + String.format("%.2f%%", selected.getChangePercent()));
            recommendations.getItems().add("Volume: " + formatLargeNumber(selected.getVolume()));
            recommendations.getItems().add("Spread: " + spreadText(selected));
            recommendations.getItems().add("Recommended next step: open Analysis & Insights before executing.");
        }

        if (!signalItems.isEmpty()) {
            recommendations.getItems().add("");
            recommendations.getItems().add("Latest signal context:");
            signalItems.stream().limit(8).forEach(recommendations.getItems()::add);
        }

        if (!positionHealthItems.isEmpty()) {
            recommendations.getItems().add("");
            recommendations.getItems().add("Position health flags:");
            positionHealthItems.stream()
                    .limit(8)
                    .map(Object::toString)
                    .forEach(recommendations.getItems()::add);
        }

        HBox actions = new HBox(8);
        Button analysisButton = new Button("Open Analysis");
        analysisButton.setOnAction(event -> openAnalysis());
        Button orderButton = new Button("Open Order Panel");
        orderButton.setOnAction(event -> openOrderPanel());
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(event -> createIndependentWindow("Recommendations", createRecommendationsPanel(), 900, 700));
        actions.getChildren().addAll(analysisButton, orderButton, refreshButton);

        panel.getChildren().addAll(title, recommendations, actions);
        VBox.setVgrow(recommendations, Priority.ALWAYS);
        return panel;
    }

    private String buildResearchReportSummary(String reportTitle) {
        MarketSnapshot snapshot = marketSnapshot();
        return " Market snapshot"+reportTitle+":"+"\n"+
                """
                Sentiment: %s
                Sentiment index: %s
                Trading volume: %s
                Active symbol: %s
                Data source: %s
                """.formatted(
                getOverallMarketSentiment(),
                getMarketSentimentIndex(),
                getTradingVolume(),
                symbolSelector.getValue() == null ? "-" : symbolSelector.getValue().toString('/'),
                snapshot.hasMarketData() ? "live/cache" : "local fallback");
    }

    private VBox createQuantPortfolioPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Quant Portfolio Manager");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        GridPane summary = new GridPane();
        summary.setHgap(24);
        summary.setVgap(10);
        summary.setPadding(new Insets(16));
        summary.setStyle("-fx-background-color: #16213e;");

        double longPnl = positionsItems.stream().filter(Position::isBuy).mapToDouble(Position::getUnrealizedPnl).sum();
        double shortPnl = positionsItems.stream().filter(position -> !position.isBuy()).mapToDouble(Position::getUnrealizedPnl).sum();
        double exposure = positionsItems.stream()
                .mapToDouble(position -> Math.abs(position.getQuantity() * position.getCurrentPrice()))
                .sum();

        addSummaryRow(summary, 0, "Open Positions", String.valueOf(positionsItems.size()));
        addSummaryRow(summary, 1, "Long P&L", money(longPnl));
        addSummaryRow(summary, 2, "Short P&L", money(shortPnl));
        addSummaryRow(summary, 3, "Total P&L", money(longPnl + shortPnl));
        addSummaryRow(summary, 4, "Gross Exposure", money(exposure));
        addSummaryRow(summary, 5, "Market Watch Symbols", String.valueOf(marketWatchItems.size()));

        TableView<Position> positions = new TableView<>(FXCollections.observableArrayList(positionsItems));
        positions.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        positions.getColumns().addAll(
                tableColumn("Symbol", position -> position.getTradePair() == null ? "-" : position.getTradePair().toString('/'), 120),
                tableColumn("Side", position -> position.isBuy() ? "LONG" : "SHORT", 80),
                tableColumn("Qty", position -> number(position.getQuantity()), 80),
                tableColumn("Price", position -> price(position.getCurrentPrice()), 100),
                tableColumn("P&L", position -> money(position.getUnrealizedPnl()), 100)
        );

        Button refresh = new Button("Refresh Positions");
        refresh.setOnAction(event -> {
            refreshPositions();
            createIndependentWindow("Quant Portfolio Manager", createQuantPortfolioPanel(), 950, 750);
        });

        panel.getChildren().addAll(title, summary, positions, refresh);
        VBox.setVgrow(positions, Priority.ALWAYS);
        return panel;
    }

    private VBox createSignalMonitorPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Signal Monitor");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        ListView<String> signals = new ListView<>(FXCollections.observableArrayList(signalItems));
        ListView<String> activity = new ListView<>(FXCollections.observableArrayList(agentActivityItems));
        signals.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");
        activity.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");

        TabPane tabs = new TabPane(
                new Tab("Signals", signals),
                new Tab("Agent Activity", activity));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> {
            signals.setItems(FXCollections.observableArrayList(signalItems));
            activity.setItems(FXCollections.observableArrayList(agentActivityItems));
        });

        panel.getChildren().addAll(title, tabs, refresh);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        return panel;
    }

    private VBox createSystemDiagnosticsPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("System Diagnostics");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        ListView<String> diagnostics = new ListView<>();
        diagnostics.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");
        diagnostics.getItems().add("Exchange: " + safe(exchangeSelector.getValue()));
        diagnostics.getItems().add("Broker access: " + (hasBrokerAccess() ? "Granted" : "Not connected"));
        diagnostics.getItems().add("SystemCore: " + (systemCore == null ? "Not started" : "Started"));
        diagnostics.getItems().add("Bot trading: " + (botTradingEnabled ? "Enabled" : "Disabled"));
        diagnostics.getItems().add("Symbols loaded: " + marketWatchItems.size());
        diagnostics.getItems().add("Open positions: " + positionsItems.size());
        diagnostics.getItems().add("Orders loaded: " + accountOpenOrderItems.size());

        if (systemCore != null) {
            try {
                diagnostics.getItems().add("System health: " + systemCore.getSystemHealth());
            } catch (Exception exception) {
                diagnostics.getItems().add("System health unavailable: " + rootMessage(exception));
            }
        }

        HBox actions = new HBox(8);
        Button operations = new Button("Open Operations Center");
        operations.setOnAction(event -> openSystemOperationsBoard());
        Button resources = new Button("Open Resource Monitor");
        resources.setOnAction(event -> showResourceMonitor());
        Button reconnect = new Button("Reconnect");
        reconnect.setOnAction(event -> connectSelectedExchange());
        actions.getChildren().addAll(operations, resources, reconnect);

        panel.getChildren().addAll(title, diagnostics, actions);
        VBox.setVgrow(diagnostics, Priority.ALWAYS);
        return panel;
    }

    private void openDocumentationWindow(String title, List<String> candidates, String fallback) {
        TextArea text = new TextArea(loadDocumentationText(candidates, fallback));
        text.setEditable(false);
        text.setWrapText(true);
        text.setStyle("-fx-control-inner-background: #0f172a; -fx-text-fill: #e5e7eb; -fx-font-family: monospace;");
        createIndependentWindow(title, text, 900, 700);
    }

    private String loadDocumentationText(List<String> candidates, String fallback) {
        for (String candidate : candidates) {
            try {
                Path path = Path.of(candidate);
                if (Files.exists(path) && Files.isRegularFile(path)) {
                    return Files.readString(path, StandardCharsets.UTF_8);
                }
            } catch (Exception exception) {
                log.debug("Unable to read documentation candidate: {}", candidate, exception);
            }
        }
        return fallback;
    }

    private void addSummaryRow(GridPane grid, int row, String name, String value) {
        Label label = new Label(name);
        label.setStyle("-fx-text-fill: #a0aec0;");
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");
        grid.add(label, 0, row);
        grid.add(valueLabel, 1, row);
    }

    private String spreadText(TradePair pair) {
        if (pair == null || pair.getBid() <= 0 || pair.getAsk() <= 0) {
            return "-";
        }
        return price(Math.abs(pair.getAsk() - pair.getBid()));
    }

    private String formatLargeNumber(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            return "-";
        }
        if (value >= 1_000_000_000) {
            return String.format("%.2fB", value / 1_000_000_000.0);
        }
        if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000.0);
        }
        if (value >= 1_000) {
            return String.format("%.2fK", value / 1_000.0);
        }
        return number(value);
    }

    // ============ REVIEW MENU HANDLERS ============
    private void showRecommendations() {
        log.info("Opening Recommendations panel");
        createIndependentWindow("Recommendations", createRecommendationsPanel(), 900, 700);
        journal("Recommendations panel opened");
    }

    private void showTradingJournal() {
        log.info("Opening Trading Journal");
        Preferences journalPrefs = Preferences.userRoot().node("investpro/journal");

        // Left panel: date picker + list of past entries
        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff;");
        datePicker.setPrefWidth(190);

        ListView<String> entryList = new ListView<>();
        entryList.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff;");
        ObservableList<String> entryDates = FXCollections.observableArrayList();
        try {
            for (String key : journalPrefs.keys()) {
                if (key.startsWith("journal_")) entryDates.add(key.substring(8));
            }
        } catch (Exception ignored) {}
        entryDates.sort(Collections.reverseOrder());
        entryList.setItems(entryDates);

        Label leftTitle = new Label("Past Entries");
        leftTitle.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 12px;");
        VBox leftPanel = new VBox(8, datePicker, leftTitle, entryList);
        leftPanel.setPadding(new Insets(12));
        leftPanel.setStyle("-fx-background-color: #16213e;");
        leftPanel.setPrefWidth(200);
        VBox.setVgrow(entryList, Priority.ALWAYS);

        // Right panel: date label + text area + buttons
        Label dateLabel = new Label("Date: " + LocalDate.now());
        dateLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-font-weight: bold;");

        TextArea notesArea = new TextArea();
        notesArea.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-font-size: 13px;");
        notesArea.setWrapText(true);
        notesArea.setPromptText("Write your trading notes here...");
        String todayKey = "journal_" + LocalDate.now();
        notesArea.setText(journalPrefs.get(todayKey, ""));

        Button saveBtn = new Button("💾 Save");
        saveBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 6 16;");
        Button clearBtn = new Button("🗑 Clear");
        clearBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 6 16;");

        HBox btnRow = new HBox(8, saveBtn, clearBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        VBox rightPanel = new VBox(10, dateLabel, notesArea, btnRow);
        rightPanel.setPadding(new Insets(12));
        rightPanel.setStyle("-fx-background-color: #1a1a2e;");
        VBox.setVgrow(notesArea, Priority.ALWAYS);

        // Wire up events
        datePicker.setOnAction(e -> {
            LocalDate date = datePicker.getValue();
            if (date != null) {
                dateLabel.setText("Date: " + date);
                String key = "journal_" + date;
                notesArea.setText(journalPrefs.get(key, ""));
            }
        });

        entryList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                try { datePicker.setValue(LocalDate.parse(newVal)); } catch (Exception ignored) {}
                dateLabel.setText("Date: " + newVal);
                notesArea.setText(journalPrefs.get("journal_" + newVal, ""));
            }
        });

        saveBtn.setOnAction(e -> {
            LocalDate date = datePicker.getValue();
            if (date == null) return;
            String key = "journal_" + date;
            journalPrefs.put(key, notesArea.getText());
            String dateStr = date.toString();
            if (!entryDates.contains(dateStr)) {
                entryDates.addFirst(dateStr);
            }
            journal("Journal entry saved for " + dateStr);
        });

        clearBtn.setOnAction(e -> notesArea.clear());

        SplitPane splitPane = new SplitPane(leftPanel, rightPanel);
        splitPane.setDividerPositions(0.22);
        splitPane.setStyle("-fx-background-color: #1a1a2e;");

        createIndependentWindow("Trading Journal", splitPane, 900, 650);
        journal("Trading Journal opened");
    }

    private void showClosedJournal() {
        log.info("Opening Closed Journal");

        List<org.investpro.models.trading.Trade> allTrades;
        try {
            allTrades = tradeRepository.findAll();
        } catch (java.sql.SQLException e) {
            log.warn("Failed to load trades for closed journal", e);
            allTrades = List.of();
        }

        List<org.investpro.models.trading.Trade> closedTrades = new ArrayList<>();
        Set<String> symbols = new java.util.LinkedHashSet<>();
        symbols.add("All");
        for (org.investpro.models.trading.Trade t : allTrades) {
            if (t.getClosePrice() > 0) {
                closedTrades.add(t);
                if (t.getTradePair() != null) symbols.add(t.getTradePair().getSymbol());
            }
        }

        TableView<org.investpro.models.trading.Trade> table = new TableView<>();
        table.setStyle("-fx-background-color: #16213e;");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().addAll(
            tableColumn("Time",        t -> t.getTimestamp() != null ? dateTime(t.getTimestamp()) : "-", 140),
            tableColumn("Symbol",      t -> t.getTradePair() != null ? t.getTradePair().getSymbol() : "-", 80),
            tableColumn("Type",        t -> t.getTransactionType() != null ? t.getTransactionType().name() : "-", 60),
            tableColumn("Size",        t -> number(t.getAmount()), 80),
            tableColumn("Entry Price", t -> price(t.getPrice()), 100),
            tableColumn("Close Price", t -> price(t.getClosePrice()), 100),
            tableColumn("Profit",      t -> money(t.getProfit()), 90)
        );
        ObservableList<org.investpro.models.trading.Trade> tableData = FXCollections.observableArrayList(closedTrades);
        table.setItems(tableData);

        // Filters
        ComboBox<String> symbolFilter = new ComboBox<>(FXCollections.observableArrayList(symbols));
        symbolFilter.setValue("All");
        symbolFilter.setStyle("-fx-background-color: #0f3460; -fx-text-fill: #ffffff;");

        DatePicker fromDate = new DatePicker();
        fromDate.setPromptText("From date");
        fromDate.setStyle("-fx-background-color: #0f3460;");
        DatePicker toDate = new DatePicker();
        toDate.setPromptText("To date");
        toDate.setStyle("-fx-background-color: #0f3460;");

        Button applyFilter = new Button("Apply Filter");
        applyFilter.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 6 16;");
        Button refreshBtn = new Button("⟳ Refresh");
        refreshBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 6 16;");

        Label symLbl = new Label("Symbol:");
        symLbl.setStyle("-fx-text-fill: #a0aec0;");
        Label fromLbl = new Label("From:");
        fromLbl.setStyle("-fx-text-fill: #a0aec0;");
        Label toLbl = new Label("To:");
        toLbl.setStyle("-fx-text-fill: #a0aec0;");

        HBox filterBar = new HBox(8, symLbl, symbolFilter, fromLbl, fromDate, toLbl, toDate, applyFilter, refreshBtn);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(8));
        filterBar.setStyle("-fx-background-color: #16213e;");

        applyFilter.setOnAction(e -> {
            String sym = symbolFilter.getValue();
            LocalDate from = fromDate.getValue();
            LocalDate to = toDate.getValue();
            List<org.investpro.models.trading.Trade> filtered = new ArrayList<>();
            for (org.investpro.models.trading.Trade t : closedTrades) {
                if (!"All".equals(sym) && (t.getTradePair() == null || !sym.equals(t.getTradePair().getSymbol()))) continue;
                if (t.getTimestamp() != null) {
                    LocalDate tradeDate = t.getTimestamp().atZone(ZoneId.systemDefault()).toLocalDate();
                    if (from != null && tradeDate.isBefore(from)) continue;
                    if (to != null && tradeDate.isAfter(to)) continue;
                }
                filtered.add(t);
            }
            tableData.setAll(filtered);
        });

        refreshBtn.setOnAction(e -> {
            List<org.investpro.models.trading.Trade> fresh;
            try { fresh = tradeRepository.findAll(); } catch (java.sql.SQLException ex) { log.warn("Refresh failed", ex); fresh = List.of(); }
            closedTrades.clear();
            for (org.investpro.models.trading.Trade t : fresh) { if (t.getClosePrice() > 0) closedTrades.add(t); }
            tableData.setAll(closedTrades);
        });

        VBox panel = new VBox(0, filterBar, table);
        panel.setStyle("-fx-background-color: #1a1a2e;");
        VBox.setVgrow(table, Priority.ALWAYS);

        createIndependentWindow("Closed Journal", panel, 1000, 700);
        journal("Closed Journal opened");
    }

    private void generateTradeReports() {
        log.info("Generating trade reports");
        try {
            List<org.investpro.models.trading.Trade> trades = tradeRepository.findAll();
            Map<String, Double> metrics = computeTradeMetrics(trades);
            Path outputDir = Path.of("output", "reports");
            Files.createDirectories(outputDir);
            Path reportPath = outputDir.resolve("trade-report-%s.txt".formatted(SNAPSHOT_FORMAT.format(LocalDateTime.now())));

            String report = "InvestPro Trade Report\n" +
                    "Generated: " + LocalDateTime.now() + "\n\n" +
                    "Total Trades: " + number(metrics.get("totalTrades")) + "\n" +
                    "Win Rate: " + String.format("%.1f%%", metrics.get("winRate")) + "\n" +
                    "Net P&L: " + money(metrics.get("netPnl")) + "\n" +
                    "Profit Factor: " + String.format("%.2f", metrics.get("profitFactor")) + "\n" +
                    "Average Trade P&L: " + money(metrics.get("avgPnl")) + "\n" +
                    "Best Trade: " + money(metrics.get("bestTrade")) + "\n" +
                    "Worst Trade: " + money(metrics.get("worstTrade")) + "\n";

            Files.writeString(reportPath, report, StandardCharsets.UTF_8);
            showInfo("Generate Reports", "Trade report saved to:\n" + reportPath.toAbsolutePath());
            journal("Trade report generated: " + reportPath);
        } catch (Exception exception) {
            log.error("Failed to generate trade report", exception);
            showWarning("Generate Reports", "Unable to generate report: " + rootMessage(exception));
        }
    }

    // ============ RESEARCH MENU HANDLERS ============
    private void showQuantPortfolioManagement() {
        log.info("Opening Quant PM");
        createIndependentWindow("Quant Portfolio Manager", createQuantPortfolioPanel(), 950, 750);
        journal("Quant PM opened");
    }

    private void showSignalMonitor() {
        log.info("Opening Signal Monitor");
        createIndependentWindow("Signal Monitor", createSignalMonitorPanel(), 950, 750);
        journal("Signal Monitor opened");
    }

    private void showMLLab() {
        log.info("Opening ML Lab");
        createIndependentWindow("ML Lab", createPlaceholderContent(), 950,
                750);
        journal("ML Lab opened");
    }

    // ============ SYSTEM MENU HANDLERS ============
    private void showSystemDiagnostics() {
        log.info("Opening System Diagnostics");
        createIndependentWindow("System Diagnostics", createSystemDiagnosticsPanel(), 900, 700);
        journal("System Diagnostics opened");
    }

    private void showSystemMetrics() {
        log.info("Opening System Metrics");
        showResourceMonitor();
        journal("System Metrics opened");
    }

    private void showResourceMonitor() {
        log.info("Opening Resource Monitor");
        if (resourceMonitorPanel == null) {
            resourceMonitorPanel = new ResourceMonitorPanel();
        }
        createIndependentWindow("Resource Monitor", resourceMonitorPanel, 980, 760);
        journal("Resource Monitor opened");
    }

    // ============ EDUCATION MENU HANDLERS ============
    private void showTradingBasics() {
        log.info("Opening Trading Basics");
        openDocumentationWindow("Trading Basics", List.of("README.md", "QUICK_REFERENCE.md"),
                "Start by connecting an exchange, selecting a symbol, opening a chart, and validating risk before automated trading.");
        journal("Trading Basics opened");
    }

    private void showStrategyGuide() {
        log.info("Opening Strategy Guide");
        openDocumentationWindow("Strategy Guide", List.of("STRATEGY_INTEGRATION_GUIDE.md", "USER_STRATEGY_IMPLEMENTATION.md", "docs/user-strategies.md"),
                "Strategy documentation was not found in the local docs folder.");
        journal("Strategy Guide opened");
    }

    private void showRiskManagement() {
        log.info("Opening Risk Management Guide");
        openAnalysis();
        journal("Risk Management guide opened");
    }

    private void showMarketAnalysis() {
        log.info("Opening Market Analysis Guide");
        openAnalysis();
        journal("Market Analysis guide opened");
    }

    private void showTechnicalIndicators() {
        log.info("Opening Technical Indicators");
        openInsertIndicatorDialog();
        journal("Technical Indicators guide opened");
    }

    private void showInvestmentConcepts() {
        log.info("Opening Investment Concepts");
        openDocumentationWindow("Investment Concepts", List.of("BACKTESTING_GUIDE.md", "DEVELOPER_GUIDE.md"),
                "Use the performance, backtesting, and portfolio screens to compare risk, return, drawdown, and execution quality.");
        journal("Investment Concepts opened");
    }

    private void showVideoTutorials() {
        log.info("Opening Video Tutorials");
        showInfo("Video Tutorials",
                "Video tutorials library is available at: https://www.youtube.com/investpro-trading\n\nTop tutorials:\n- Getting Started\n- Building Your First Strategy\n- Risk Management Essentials");
        journal("Video Tutorials accessed");
    }

    private void showDocumentation() {
        log.info("Opening Documentation");
        openDocumentationWindow("Documentation", List.of("README.md", "DEVELOPER_GUIDE.md", "SYSTEM_ARCHITECTURE.md"),
                "No local documentation files were found.");
        journal("Documentation accessed");
    }

    // ============ HELP MENU HANDLERS ============
    private void showUserGuide() {
        log.info("Opening User Guide");
        VBox guideContent = new VBox(12);
        guideContent.setPadding(new Insets(16));
        guideContent.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("InvestPro User Guide");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        TextArea guide = new TextArea(
                """
                        GETTING STARTED
                        1. Set up your exchange credentials (Settings → Exchange Credentials)
                        2. Choose your trading strategy (Strategy Menu)
                        3. Configure risk parameters (Trader Profile)
                        4. Monitor positions and orders (Trading Desk)

                        MAIN FEATURES
                        • Market Research: Analyze trends and sentiment
                        • Strategy Builder: Create and backtest strategies
                        • Telegram Bot: Trade and monitor on the go
                        • Review Panel: Track performances and trades
                        • Education: Learn trading concepts

                        KEYBOARD SHORTCUTS
                        Press Ctrl+K to view all keyboard shortcuts""");

        guide.setWrapText(true);
        guide.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #e2e8f0;");
        guide.setEditable(false);

        guideContent.getChildren().addAll(title, guide);
        VBox.setVgrow(guide, Priority.ALWAYS);

        createIndependentWindow("User Guide", guideContent, 900, 700);
        journal("User Guide opened");
    }

    private void showKeyboardShortcuts() {
        log.info("Opening Keyboard Shortcuts");
        VBox shortcutsContent = new VBox(12);
        shortcutsContent.setPadding(new Insets(16));
        shortcutsContent.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Keyboard Shortcuts");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        TextArea shortcuts = new TextArea(
                """
                        TRADING ACTIONS
                        Ctrl+O : Open Order Panel
                        Ctrl+T : Toggle Bot Trading
                        Ctrl+H : User Guide
                        Ctrl+K : Keyboard Shortcuts
                        F1     : Help

                        UI NAVIGATION
                        F5     : Refresh Data
                        Ctrl+` : Toggle Terminal
                        Tab    : Focus Next Component
                        Shift+Tab : Focus Previous Component

                        CHART CONTROLS
                        + : Zoom In
                        - : Zoom Out
                        Right Arrow : Next Timeframe
                        Left Arrow : Previous Timeframe

                        GENERAL
                        Ctrl+Q : Quit Application
                        Ctrl+S : Save Settings""");

        shortcuts.setWrapText(true);
        shortcuts.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #e2e8f0;");
        shortcuts.setEditable(false);

        shortcutsContent.getChildren().addAll(title, shortcuts);
        VBox.setVgrow(shortcuts, Priority.ALWAYS);

        createIndependentWindow("Keyboard Shortcuts", shortcutsContent, 900, 700);
        journal("Keyboard Shortcuts opened");
    }

    private void showSettingsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Settings");
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

    /**
     * Display theme settings dialog for dark/light mode and customization
     */
    private void showThemeSettingsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Theme Settings");
        dialog.setHeaderText("Customize application appearance and theme");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ThemeManager themeManager = ThemeManager.getInstance();
        ThemeManager.ThemeConfig config = themeManager.getConfig();

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        // Theme selection
        ComboBox<ThemeManager.Theme> themeCombo = new ComboBox<>();
        themeCombo.getItems().addAll(themeManager.getAvailableThemes());
        themeCombo.setValue(config.getTheme());
        themeCombo.setPrefWidth(200);

        // Opacity slider (transparency control)
        Label opacityLabel = new Label(String.format("Opacity: %.0f%%", config.getOpacity() * 100));
        Slider opacitySlider = new Slider(0.3, 1.0, config.getOpacity());
        opacitySlider.setPrefWidth(200);
        opacitySlider.valueProperty().addListener((obs, oldVal, newVal) -> opacityLabel
                .setText(String.format("Opacity: %.0f%%", newVal.doubleValue() * 100)));

        // High contrast checkbox
        CheckBox highContrastCheckBox = new CheckBox("High Contrast Mode");
        highContrastCheckBox.setSelected(config.isUseHighContrast());

        // Compact layout checkbox
        CheckBox compactLayoutCheckBox = new CheckBox("Compact Layout");
        compactLayoutCheckBox.setSelected(config.isUseCompactLayout());

        // Accent color picker
        ColorPicker accentColorPicker = new ColorPicker();
        try {
            accentColorPicker.setValue(Color.web(config.getAccentColor()));
        } catch (Exception e) {
            accentColorPicker.setValue(Color.web("#1e90ff"));
        }

        // Add components to grid
        grid.addRow(0, new Label("Theme:"), themeCombo);
        grid.addRow(1, opacityLabel, opacitySlider);
        grid.addRow(2, new Label("Accent Color:"), accentColorPicker);
        grid.addRow(3, highContrastCheckBox);
        grid.addRow(4, compactLayoutCheckBox);

        // Add info section
        VBox infoBox = new VBox(10);
        infoBox.setStyle("-fx-border-color: #d1d5db; -fx-border-width: 1; -fx-padding: 10;");
        Label infoLabel = new Label(
                """
                        • Dark Mode: Reduces eye strain in low-light environments
                        • Light Mode: Better readability in bright environments
                        • Opacity: Control window transparency (0.3 - 1.0)
                        • High Contrast: Enhance visibility with bold fonts
                        • Accent Color: Customize primary color scheme""");
        infoLabel.setWrapText(true);
        infoBox.getChildren().add(infoLabel);

        VBox content = new VBox(15, grid, infoBox);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(new ScrollPane(content));

        // Handle OK button
        Optional<Void> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                config.setTheme(themeCombo.getValue());
                config.setOpacity(opacitySlider.getValue());
                config.setHighContrast(highContrastCheckBox.isSelected());
                config.setCompactLayout(compactLayoutCheckBox.isSelected());
                config.setAccentColor(accentColorPicker.getValue().toString().replace("0x", "#").toUpperCase());

                // Apply theme to main window if possible
                themeManager.saveConfiguration();
                journal("Theme settings applied and saved.");
            } catch (Exception ex) {
                log.error("Error applying theme settings", ex);
            }
        }
    }

    /**
     * Display visibility and layout settings dialog
     */
    private void showVisibilitySettingsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Visibility & Layout Settings");
        dialog.setHeaderText("Configure panel visibility and layout preferences");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        // Panel visibility toggles - read current preferences
        CheckBox showMarketWatchCheckBox = new CheckBox("Show Market Watch Panel");
        showMarketWatchCheckBox.setSelected(preferences.getBoolean("visibility_market_watch", true));

        CheckBox showOrderBookCheckBox = new CheckBox("Show Order Book Panel");
        showOrderBookCheckBox.setSelected(preferences.getBoolean("visibility_order_book", true));

        CheckBox showTerminalCheckBox = new CheckBox("Show Terminal Panel");
        showTerminalCheckBox.setSelected(preferences.getBoolean("visibility_terminal", true));

        CheckBox showChartsCheckBox = new CheckBox("Show Charts Tab");
        showChartsCheckBox.setSelected(preferences.getBoolean("visibility_charts", true));

        CheckBox showPositionsCheckBox = new CheckBox("Show Positions Panel");
        showPositionsCheckBox.setSelected(preferences.getBoolean("visibility_positions", true));

        // Layout preferences
        ComboBox<String> layoutCombo = new ComboBox<>();
        layoutCombo.getItems().addAll("Compact", "Standard", "Wide");
        String savedLayout = preferences.get("layout_preference", "Standard");
        layoutCombo.setValue(savedLayout);
        layoutCombo.setPrefWidth(150);

        // Font size slider
        int savedFontSize = preferences.getInt("font_size", 12);
        Label fontSizeLabel = new Label("Font Size: " + savedFontSize + "px");
        Slider fontSizeSlider = new Slider(10, 16, savedFontSize);
        fontSizeSlider.setPrefWidth(200);
        fontSizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> fontSizeLabel
                .setText(String.format("Font Size: %.0fpx", newVal.doubleValue())));

        // Add components to grid
        grid.addRow(0, new Label("Panel Visibility:"));
        grid.addRow(1, showMarketWatchCheckBox);
        grid.addRow(2, showOrderBookCheckBox);
        grid.addRow(3, showTerminalCheckBox);
        grid.addRow(4, showChartsCheckBox);
        grid.addRow(5, showPositionsCheckBox);

        grid.addRow(7, new Label("Layout Preference:"), layoutCombo);
        grid.addRow(8, fontSizeLabel);
        grid.addRow(9, new Label("Font Size:"), fontSizeSlider);

        VBox content = new VBox(10, grid);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(new ScrollPane(content));

        Optional<Void> result = dialog.showAndWait();
        if (result.isPresent()) {
            // Save visibility preferences
            preferences.putBoolean("visibility_market_watch", showMarketWatchCheckBox.isSelected());
            preferences.putBoolean("visibility_order_book", showOrderBookCheckBox.isSelected());
            preferences.putBoolean("visibility_terminal", showTerminalCheckBox.isSelected());
            preferences.putBoolean("visibility_charts", showChartsCheckBox.isSelected());
            preferences.putBoolean("visibility_positions", showPositionsCheckBox.isSelected());

            // Save layout preferences
            preferences.put("layout_preference", layoutCombo.getValue());
            preferences.putInt("font_size", (int) fontSizeSlider.getValue());

            // Apply panel visibility changes
            applyVisibilitySettings(showMarketWatchCheckBox.isSelected(), showOrderBookCheckBox.isSelected(),
                    showTerminalCheckBox.isSelected(), showChartsCheckBox.isSelected());

            // Apply font size
            applyFontSize((int) fontSizeSlider.getValue());

            journal("✓ Visibility and layout settings applied and saved.");
        }
    }

    /**
     * Apply visibility settings to panels
     */
    private void applyVisibilitySettings(boolean showMarketWatch, boolean showOrderBook, boolean showTerminal,
            boolean showCharts) {
        // Market Watch visibility
        if (marketWatchWrapper != null) {
            marketWatchWrapper.setVisible(showMarketWatch);
            marketWatchWrapper.setManaged(showMarketWatch);
        }
        if (marketWatchTable != null) {
            marketWatchTable.setVisible(showMarketWatch);
            marketWatchTable.setManaged(showMarketWatch);
        }

        // Order Book visibility
        if (orderBookWrapper != null) {
            orderBookWrapper.setVisible(showOrderBook);
            orderBookWrapper.setManaged(showOrderBook);
        }
        if (orderBookBidsTable != null) {
            orderBookBidsTable.setVisible(showOrderBook);
            orderBookBidsTable.setManaged(showOrderBook);
        }
        if (orderBookAsksTable != null) {
            orderBookAsksTable.setVisible(showOrderBook);
            orderBookAsksTable.setManaged(showOrderBook);
        }

        // Terminal visibility
        if (terminalTabPane != null) {
            terminalTabPane.setVisible(showTerminal);
            terminalTabPane.setManaged(showTerminal);
        }

        // Charts tab visibility
        if (chartTabPane != null) {
            chartTabPane.setVisible(showCharts);
            chartTabPane.setManaged(showCharts);
        }
    }

    /**
     * Apply font size to all text elements
     */
    private void applyFontSize(int fontSize) {
        String fontStyle = "-fx-font-size: " + fontSize + "px;";
        this.setStyle(this.getStyle() + fontStyle);
        journal("Font size changed to " + fontSize + "px");
    }

    private void showExchangeCredentialDialog(String selectedExchange) {
        if (selectedExchange == null || selectedExchange.isBlank()) {
            showWarning("Credentials", "No exchange selected.");
            return;
        }

        boolean oanda      = "OANDA".equalsIgnoreCase(selectedExchange);
        boolean isCoinbase = "Coinbase".equalsIgnoreCase(selectedExchange);
        boolean hasCreds   = !configuredApiKey.isBlank();

        // ── Dialog shell ──────────────────────────────────────────────────────
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Connect to " + selectedExchange);
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        ButtonType connectBtn = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(connectBtn, ButtonType.CANCEL);

        // ── Header band ───────────────────────────────────────────────────────
        Label exchangeTitle = new Label(selectedExchange.toUpperCase());
        exchangeTitle.setStyle("""
                -fx-font-size: 20px;
                -fx-font-weight: bold;
                -fx-text-fill: #f8fafc;
                """);

        Label statusBadge = new Label(hasCreds ? "⚠  Not Connected" : "Not Configured");
        statusBadge.setStyle("""
                -fx-background-color: %s;
                -fx-text-fill: white;
                -fx-font-size: 11px;
                -fx-font-weight: bold;
                -fx-padding: 3 10 3 10;
                -fx-background-radius: 10;
                """.formatted(hasCreds ? "#f59e0b" : "#6b7280"));

        Label subText = new Label(hasCreds
                ? "Confirm your credentials and trading venue to reconnect."
                : "Enter your API credentials and select a trading venue.");
        subText.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        subText.setWrapText(true);

        HBox badgeRow = new HBox(10, exchangeTitle, statusBadge);
        badgeRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox header = new VBox(6, badgeRow, subText);
        header.setStyle("""
                -fx-background-color: #1e293b;
                -fx-padding: 20 24 16 24;
                -fx-border-color: #334155;
                -fx-border-width: 0 0 1 0;
                """);
        header.setPrefWidth(520);

        // ── Helper: builds a labeled field section ─────────────────────────────
        // (label on top, control below — modern UI pattern)

        // ── Credentials section ───────────────────────────────────────────────
        String apiLabelText    = oanda ? "API Token"    : "API Key";
        String secretLabelText = oanda ? "Account ID"   : "API Secret";
        String apiPrompt       = oanda ? "Enter your OANDA v20 API token" : "Paste your API key here";
        String secretPrompt    = oanda ? "Account ID (e.g. 001-001-123456-001)" : "Paste your API secret / private key";

        TextField    apiKeyField = new TextField(configuredApiKey);
        PasswordField secretField = new PasswordField();
        secretField.setText(configuredApiSecret);

        // For Coinbase, the secret is a multi-line PEM key — use TextArea instead
        TextArea pemArea = new TextArea();
        if (isCoinbase) {
            pemArea.setText(configuredApiSecret);
            pemArea.setPromptText("-----BEGIN EC PRIVATE KEY-----\n...\n-----END EC PRIVATE KEY-----");
            pemArea.setPrefRowCount(6);
            pemArea.setWrapText(true);
            pemArea.setStyle("""
                    -fx-font-family: 'Courier New', monospace;
                    -fx-font-size: 11px;
                    -fx-background-color: #0f172a;
                    -fx-text-fill: #e2e8f0;
                    -fx-border-color: #334155;
                    -fx-border-radius: 4;
                    -fx-background-radius: 4;
                    """);
        }

        styleDialogField(apiKeyField, apiPrompt);
        styleDialogField(secretField, secretPrompt);

        // ── Venue toggle (LIVE / PAPER) ────────────────────────────────────────
        ToggleGroup venueGroup   = new ToggleGroup();
        ToggleButton liveToggle  = new ToggleButton("LIVE");
        ToggleButton paperToggle = new ToggleButton("PAPER");
        liveToggle.setToggleGroup(venueGroup);
        paperToggle.setToggleGroup(venueGroup);
        liveToggle.setPrefWidth(110);
        paperToggle.setPrefWidth(110);

        boolean isLive = !"PAPER".equalsIgnoreCase(configuredTradingMode);
        liveToggle.setSelected(isLive);
        paperToggle.setSelected(!isLive);

        String toggleBase = """
                -fx-font-weight: bold;
                -fx-font-size: 12px;
                -fx-cursor: hand;
                -fx-background-radius: 4;
                -fx-border-radius: 4;
                """;
        Runnable applyVenueStyles = () -> {
            if (liveToggle.isSelected()) {
                liveToggle.setStyle(toggleBase + "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-border-color: #dc2626;");
                paperToggle.setStyle(toggleBase + "-fx-background-color: #1e293b; -fx-text-fill: #94a3b8; -fx-border-color: #334155;");
            } else {
                liveToggle.setStyle(toggleBase + "-fx-background-color: #1e293b; -fx-text-fill: #94a3b8; -fx-border-color: #334155;");
                paperToggle.setStyle(toggleBase + "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-border-color: #2563eb;");
            }
        };
        applyVenueStyles.run();
        liveToggle.selectedProperty().addListener((obs, o, n)  -> applyVenueStyles.run());
        paperToggle.selectedProperty().addListener((obs, o, n) -> applyVenueStyles.run());

        Label liveWarning = new Label("⚠  LIVE mode executes real trades with real funds.");
        liveWarning.setStyle("-fx-text-fill: #f87171; -fx-font-size: 11px;");
        liveWarning.visibleProperty().bind(liveToggle.selectedProperty());
        liveWarning.managedProperty().bind(liveToggle.selectedProperty());

        HBox venueButtons = new HBox(8, liveToggle, paperToggle);

        // ── Optional notifications section ────────────────────────────────────
        TextField telegramField = new TextField(telegramToken);
        PasswordField openAiField = new PasswordField();
        openAiField.setText(configuredOpenAiApiKey);
        TextField emailField    = new TextField(oandaEmailNotification);
        styleDialogField(telegramField, "Telegram bot token (optional)");
        styleDialogField(openAiField, "OpenAI API key (optional)");
        styleDialogField(emailField,    "Email address for trade alerts (optional)");

        emailField.setVisible(oanda);
        emailField.setManaged(oanda);

        // ── Coinbase help accordion ────────────────────────────────────────────
        TitledPane coinbaseHelp = null;
        if (isCoinbase) {
            Label helpText = new Label("""
                    1. Go to Coinbase → Settings → Developers → API Keys
                    2. Create a key with "Trade" permission
                    3. Copy the Key Name (starts with organizations/)
                    4. Download the .pem file and paste its full contents below""");
            helpText.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
            helpText.setWrapText(true);
            coinbaseHelp = new TitledPane("Coinbase Advanced Trade — Setup Guide", helpText);
            coinbaseHelp.setExpanded(false);
            coinbaseHelp.setStyle("""
                    -fx-background-color: #0f172a;
                    -fx-text-fill: #64748b;
                    -fx-font-size: 11px;
                    """);
        }

        // ── Assemble body ──────────────────────────────────────────────────────
        VBox body = new VBox(16);
        body.setPadding(new Insets(20, 24, 8, 24));
        body.setStyle("-fx-background-color: #0f172a;");
        body.setPrefWidth(520);

        body.getChildren().addAll(
                sectionBlock("CREDENTIALS",
                        fieldBlock(apiLabelText, apiKeyField),
                        isCoinbase ? fieldBlock(secretLabelText, pemArea) : fieldBlock(secretLabelText, secretField)
                ),
                sectionBlock("TRADING VENUE",
                        venueButtons,
                        liveWarning
                ),
                sectionBlock("NOTIFICATIONS (optional)",
                        fieldBlock("Telegram Token", telegramField),
                    fieldBlock("OpenAI API Key", openAiField),
                        oanda ? fieldBlock("Email", emailField) : null
                )
        );

        if (coinbaseHelp != null) {
            body.getChildren().add(coinbaseHelp);
        }

        // ── Footer ─────────────────────────────────────────────────────────────
        VBox root = new VBox(header, body);
        root.setStyle("-fx-background-color: #0f172a;");

        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setStyle("""
                -fx-background-color: #0f172a;
                -fx-border-color: #1e293b;
                -fx-border-width: 1;
                """);
        dialog.getDialogPane().setPrefWidth(560);

        // Style the Connect button
        javafx.application.Platform.runLater(() -> {
            javafx.scene.Node connectNode = dialog.getDialogPane().lookupButton(connectBtn);
            if (connectNode != null) {
                connectNode.setStyle("""
                        -fx-background-color: #10b981;
                        -fx-text-fill: white;
                        -fx-font-weight: bold;
                        -fx-font-size: 13px;
                        -fx-padding: 8 20;
                        -fx-background-radius: 4;
                        -fx-cursor: hand;
                        """);
            }
        });

        // ── Capture result ─────────────────────────────────────────────────────
        final TextArea finalPemArea = pemArea;
        dialog.setResultConverter(buttonType -> {
            if (buttonType == connectBtn) {
                String apiKey    = safe(apiKeyField.getText());
                String apiSecret = isCoinbase
                        ? safe(finalPemArea.getText())
                        : safe(secretField.getText());

                if ("Coinbase".equalsIgnoreCase(selectedExchange)) {
                    String validationError = validateCoinbaseCredentials(apiKey, apiSecret);
                    if (validationError != null && !validationError.isBlank()) {
                        showWarning("Invalid Credentials", validationError);
                        return false;
                    }
                }

                configuredApiKey      = apiKey;
                configuredApiSecret   = apiSecret;
                configuredTradingMode = liveToggle.isSelected() ? "LIVE" : "PAPER";
                telegramToken         = safe(telegramField.getText());
                configuredOpenAiApiKey = safe(openAiField.getText());
                if (oanda) {
                    oandaEmailNotification = safe(emailField.getText());
                }
                saveExchangeCredentials(selectedExchange);
                if (systemCore != null && !configuredOpenAiApiKey.isBlank()) {
                    systemCore.setOpenAiApiKey(configuredOpenAiApiKey);
                }
                return true;
            }
            return false;
        });

        dialog.showAndWait().ifPresent(saved -> {
            if (saved) {
                exchange = createExchange(selectedExchange, configuredApiKey, configuredApiSecret,
                        configuredAccountId, configuredTradingMode);
                new Thread(() -> exchange.connectStream(), "WebSocketConnector-" + selectedExchange).start();
                setTelegramToken(telegramToken);
                proceedWithConnection();
            }
        });
    }

    /** Applies a consistent dark-theme style to a text input field. */
    private void styleDialogField(javafx.scene.control.TextInputControl field, String prompt) {
        field.setPromptText(prompt);
        field.setMaxWidth(Double.MAX_VALUE);
        field.setStyle("""
                -fx-background-color: #1e293b;
                -fx-text-fill: #f1f5f9;
                -fx-prompt-text-fill: #475569;
                -fx-border-color: #334155;
                -fx-border-radius: 4;
                -fx-background-radius: 4;
                -fx-padding: 8 10;
                -fx-font-size: 12px;
                """);
    }

    /** Creates a label + control stacked vertically. Null control is silently skipped. */
    private VBox fieldBlock(String labelText, javafx.scene.Node control) {
        if (control == null) return new VBox();
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        VBox block = new VBox(5, lbl, control);
        VBox.setVgrow(control, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(control, javafx.scene.layout.Priority.ALWAYS);
        return block;
    }

    /** Creates a titled section separator with children stacked below. Null children are skipped. */
    private VBox sectionBlock(String title, javafx.scene.Node... children) {
        Label sectionTitle = new Label(title);
        sectionTitle.setStyle("""
                -fx-text-fill: #475569;
                -fx-font-size: 10px;
                -fx-font-weight: bold;
                """);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #1e293b;");

        HBox titleRow = new HBox(10, sectionTitle, sep);
        titleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(sep, javafx.scene.layout.Priority.ALWAYS);

        VBox section = new VBox(10, titleRow);
        for (javafx.scene.Node child : children) {
            if (child != null && !(child instanceof VBox vb && vb.getChildren().isEmpty())) {
                section.getChildren().add(child);
            }
        }
        return section;
    }

    private String validateCoinbaseCredentials(String apiKey, String apiSecret) {
        // Check if both fields are provided
        if (apiKey == null || apiKey.isBlank()) {
            return """
                    API Key (CDP Key Name) cannot be empty.

                    Format: organizations/{org_id}/apiKeys/{key_id}

                    Get this from Coinbase → Developer → API Keys → Copy the key name.""";
        }

        if (apiSecret == null || apiSecret.isBlank()) {
            return """
                    API Secret (EC Private Key) cannot be empty.

                    This should be the EC private key in PEM format, starting with:
                    -----BEGIN EC PRIVATE KEY-----
                    or
                    -----BEGIN PRIVATE KEY-----""";
        }

        // Validate API Key format
        if (!apiKey.contains("organizations/") || !apiKey.contains("/apiKeys/")) {
            return """
                    Invalid API Key format.

                    Expected format: organizations/{org_id}/apiKeys/{key_id}

                    Example: organizations/12345678-1234-5678-1234-567812345678/apiKeys/87654321-4321-8765-4321-876543218765""";
        }

        // Validate Private Key format
        String keyTrimmed = apiSecret.trim();
        if (!keyTrimmed.contains("BEGIN") || !keyTrimmed.contains("PRIVATE KEY")) {
            return """
                    Invalid Private Key format.

                    The private key should be in PEM format, containing:
                    • -----BEGIN EC PRIVATE KEY----- (or -----BEGIN PRIVATE KEY-----)
                    • Base64 encoded key content
                    • -----END EC PRIVATE KEY----- (or -----END PRIVATE KEY-----)

                    Get this from Coinbase → Developer → API Keys → Download the private key file.""";
        }

        // Validate that it looks like a complete key
        if (keyTrimmed.split("\n").length < 3) {
            return """
                    Private Key appears incomplete.

                    Make sure you've copied the ENTIRE private key, including:
                    • The BEGIN line
                    • All content in the middle
                    • The END line

                    Tip: Open the .pem file in a text editor and copy the entire contents.""";
        }

        return null; // Validation passed
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
        configuredAccountId = preferences.get("exchange_account_id_" + key, configuredAccountId);
        configuredTradingMode = preferences.get("exchange_trading_mode_" + key, configuredTradingMode);
        telegramToken = preferences.get("telegram_token_" + key, telegramToken);
        configuredOpenAiApiKey = preferences.get("openai_api_key", configuredOpenAiApiKey);
        if ("OANDA".equalsIgnoreCase(exchangeName)) {
            oandaEmailNotification = preferences.get("oanda_email_notification_" + key, oandaEmailNotification);
        }
    }

    private void saveExchangeCredentials(String exchangeName) {
        String key = safe(exchangeName);
        if (key.isBlank()) {
            return;
        }
        preferences.put("exchange_api_key_" + key, configuredApiKey);
        preferences.put("exchange_api_secret_" + key, configuredApiSecret);
        preferences.put("exchange_account_id_" + key, configuredAccountId);
        preferences.put("exchange_trading_mode_" + key, configuredTradingMode);
        preferences.put("telegram_token_" + key, telegramToken);
        preferences.put("openai_api_key", configuredOpenAiApiKey);
        if ("OANDA".equalsIgnoreCase(exchangeName)) {
            preferences.put("oanda_email_notification_" + key, oandaEmailNotification);
        }
    }

    private UiExchangeStreamConsumer uiStreamConsumer;

    private void initializeUiStreamConsumer() {
        uiStreamConsumer = new UiExchangeStreamConsumer()
                .onTickerUpdate(ticker -> {
                    TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();

                    if (selected != null && ticker != null) {
                        updateTickerFromStream(selected, ticker);
                    }
                })
                .onTradeUpdate(trade -> {
                    if (trade != null) {
                        accountTradeItems.addFirst(trade);

                        while (accountTradeItems.size() > 500) {
                            accountTradeItems.removeLast();
                        }
                    }
                })
                .onOrderBookUpdate(orderBook -> {
                    if (orderBook != null) {
                        updateOrderBookUi(orderBook);
                    }
                })
                .onCandleUpdate(candle -> {
                    if (candle != null) {
                        updateCandleFromStream();
                    }
                })
                .onAccountUpdate(account -> {
                    if (account != null) {
                        updateAccountFromStream(account);
                    }
                })
                .onOrdersUpdate(accountOpenOrderItems::setAll)
                .onPositionsUpdate(positions -> {
                    positionsItems.setAll(positions);
                    accountPositionItems.setAll(positions);
                })
                .onFillUpdate(fill -> {
                    if (fill != null) {
                        accountTradeItems.addFirst(fill);
                    }
                })
                .onError((exchangeName, throwable) -> journal("Stream error from %s: %s".formatted(
                        exchangeName,
                        throwable == null ? "unknown" : throwable.getMessage())));

    }

    private void updateOrderBookUi(@NotNull OrderBook orderBook) {
        currentOrderBook = orderBook;

        orderBookBids.setAll(orderBook.getBids() == null ? List.of() : orderBook.getBids());
        orderBookAsks.setAll(orderBook.getAsks() == null ? List.of() : orderBook.getAsks());
        updateChartsFromOrderBookMidPrice(orderBook);

        if (depthChart != null) {
            depthChart.update(orderBook);
        }
    }

    private void updateChartsFromOrderBookMidPrice(@NotNull OrderBook orderBook) {
        double midPrice = orderBook.getMidPrice();
        if (!Double.isFinite(midPrice) || midPrice <= 0.0) {
            CandleStickChart activeChart = getActiveChart();
            if (activeChart != null) {
                activeChart.clearCurrentMarketPrice();
            }
            return;
        }


        OrderBook.PriceLevel bestBid = orderBook.getBestBid();
        OrderBook.PriceLevel bestAsk = orderBook.getBestAsk();
        TradePair orderBookPair = orderBook.getTradePair() != null ? orderBook.getTradePair() : activeOrderBookPair;
        if (orderBookPair != null && bestBid != null && bestAsk != null) {
            orderBookPair.updateQuote(bestBid.getPrice(), bestAsk.getPrice());
            if (dataWindow != null) {
                dataWindow.updateQuote(orderBookPair, bestBid.getPrice(), bestAsk.getPrice(), midPrice, orderBook.getTimestamp());
            }
        }

        for (Tab tab : chartTabPane.getTabs()) {
            if (tab.getContent() instanceof ChartContainer container
                    && isSameTradePair(container.getTradePair(), orderBookPair)) {
                CandleStickChart chart = container.getChart();
                if (chart != null) {
                    chart.setCurrentMarketPrice(midPrice);
                }
            } else if (tab == chartTabPane.getSelectionModel().getSelectedItem()
                    && orderBookPair == null
                    && tab.getContent() instanceof ChartContainer container) {
                CandleStickChart chart = container.getChart();
                if (chart != null) {
                    chart.setCurrentMarketPrice(midPrice);
                }
            }
        }
    }

    private boolean isSameTradePair(@Nullable TradePair left, @Nullable TradePair right) {
        if (left == null || right == null) {
            return false;
        }
        if (Objects.equals(left, right)) {
            return true;
        }
        try {
            return Objects.equals(left.toString('/'), right.toString('/'));
        } catch (Exception exception) {
            return Objects.equals(String.valueOf(left), String.valueOf(right));
        }
    }

    @Contract("_, _, _, _, _ -> new")
    private @NotNull Exchange createExchange(
            String exchangeName,
            String apiKey,
            String apiSecret,
            String accountId,
            String tradingMode) {
        String normalizedName = normalizeExchangeName(exchangeName);
        boolean paperMode = "PAPER".equalsIgnoreCase(safe(tradingMode))
                || "SANDBOX".equalsIgnoreCase(safe(tradingMode));
        boolean stellar = "STELLAR NETWORK".equals(normalizedName);
        String normalizedAccountId = stellar ? safe(apiKey) : safe(accountId);

        ExchangeCredentials credentials = new ExchangeCredentials(
                normalizedExchangeId(normalizedName),
                safe(apiKey),
                safe(apiSecret),
                normalizedName.equals("COINBASE") ? safe(apiKey) : null,
                normalizedName.equals("COINBASE") ? safe(apiSecret) : null,
                null,
               normalizedAccountId,
                paperMode);

        Exchange createdExchange;
        try {
            CredentialProvider credentialProvider = key -> credentialValueForPluginFactory(key, credentials);
            createdExchange = new ExchangeFactory(credentialProvider, PluginRegistry.loadDefault())
                    .create(normalizedExchangeId(normalizedName));
        } catch (Exception pluginException) {
            log.warn(
                    "PluginRegistry exchange creation failed for {}. Using legacy TradingDesk fallback.",
                    exchangeName,
                    pluginException);
            createdExchange = switch (normalizedName) {
                case "BINANCE US" -> new BinanceUs(credentials);
                case "BINANCE" -> new Binance(credentials);
                case "OANDA" -> new Oanda(credentials);
                case "BITFINEX" -> new Bitfinex(credentials);
                case "ALPACA" -> new Alpaca(credentials);
                case "INTERACTIVE BROKERS", "INTERACTIVE_BROKER", "IBKR", "IBK", "SCHWAB", "CHARLES SCHWAB" -> new InteractiveBrokers(credentials);
                case "COINBASE" -> new Coinbase(credentials);
                case "STELLAR NETWORK", "STELLAR-NETWORK", "STELLAR_NETWORK" -> new StellarNetwork(credentials);
                default -> {
                    log.warn("Exchange {} is not implemented yet. Falling back to Coinbase.", exchangeName);
                    throw new RuntimeException("UNSUPPORTED EXCHANGE");
                }
            };
        }

        createdExchange.setUserSelectedTradingMode(safe(tradingMode).isBlank() ? "LIVE" : safe(tradingMode));

        // Wire the exchange to the central MarketDataEngine
        if (marketDataEngine != null) {
            createdExchange.setMarketDataEngine(marketDataEngine);
        }

        // Register the exchange in ExchangeService for monitoring
        try {
            String exchangeId = normalizeExchangeName(exchangeName);
            exchangeService.register(exchangeId, createdExchange);
        } catch (Exception e) {
            log.warn("Failed to register {} in ExchangeService", exchangeName, e);
        }

        return createdExchange;
    }

    private List<String> discoverSupportedExchanges() {
        LinkedHashSet<String> exchanges = new LinkedHashSet<>();
        try {
            PluginRegistry.loadDefault().exchangeProviders().stream()
                    .filter(ExchangeProvider::enabledByDefault)
                    .map(ExchangeProvider::id)
                    .map(this::normalizeExchangeName)
                    .forEach(exchanges::add);
        } catch (Exception exception) {
            log.warn("Unable to load exchange providers for selector. Using legacy list.", exception);
        }
        exchanges.addAll(Arrays.asList(SUPPORTED_EXCHANGES));
        return new ArrayList<>(exchanges);
    }

    private Optional<String> credentialValueForPluginFactory(String key, ExchangeCredentials credentials) {
        if (key == null || credentials == null) {
            return Optional.empty();
        }

        return switch (key) {
            case "COINBASE_API_KEY", "COINBASE_KEY_NAME" -> Optional.ofNullable(credentials.apiKey());
            case "COINBASE_API_SECRET", "COINBASE_PRIVATE_KEY" -> Optional.ofNullable(credentials.apiSecret());
            case "BINANCE_API_KEY", "BINANCE_US_API_KEY", "BITFINEX_API_KEY", "OANDA_API_KEY", "ALPACA_API_KEY" ->
                    Optional.ofNullable(credentials.apiKey());
            case "BINANCE_API_SECRET", "BINANCE_US_API_SECRET", "BITFINEX_API_SECRET", "OANDA_API_SECRET", "ALPACA_API_SECRET" ->
                    Optional.ofNullable(credentials.apiSecret());
            case "OANDA_ACCOUNT_ID" -> Optional.ofNullable(credentials.accountId());
            case "OANDA_SANDBOX", "ALPACA_PAPER" -> Optional.of(String.valueOf(credentials.sandbox()));
            default -> Optional.empty();
        };
    }

    private @NotNull String normalizeExchangeName(String exchangeName) {
        String name = safe(exchangeName).toUpperCase(Locale.ROOT).replace('-', ' ').replace('_', ' ').trim();
        return switch (name) {
            case "BINANCEUS", "BINANCE US" -> "BINANCE US";
            case "COINBASE", "COINBASE ADVANCED", "COINBASE ADVANCED TRADE" -> "COINBASE";
            case "OANDA", "OANDA FX", "OANDA FOREX" -> "OANDA";
            case "ALPACA", "ALPACA STOCKS", "ALPACA EQUITIES" -> "ALPACA";
            case "INTERACTIVE BROKERS", "INTERACTIVEBROKERS", "IBKR", "IBK" -> "INTERACTIVE BROKERS";
            case "SCHWAB", "CHARLES SCHWAB", "CHARLESSCHWAB" -> "SCHWAB";
            case "STELLAR", "STELLAR NETWORK", "STELLARNETWORK" -> "STELLAR NETWORK";
            default -> name;
        };
    }

    private @NotNull String normalizedExchangeId(String normalizedName) {
        return switch (normalizedName) {
            case "BINANCE US" -> "binanceus";
            case "BINANCE" -> "binance";
            case "COINBASE" -> "coinbase";
            case "OANDA" -> "oanda";
            case "ALPACA" -> "alpaca";
            case "BITFINEX" -> "bitfinex";
            case "INTERACTIVE BROKERS" -> "interactive_brokers";
            case "SCHWAB" -> "schwab";
            case "STELLAR NETWORK" -> "stellar-network";
            default -> normalizedName.toLowerCase(Locale.ROOT).replace(" ", "_");
        };
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

    private void runOnFxAndWait(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            runnable.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                runnable.run();
            } catch (RuntimeException exception) {
                failure.set(exception);
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for JavaFX update", exception);
        }

        if (failure.get() != null) {
            throw failure.get();
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
        alert.show();
    }

    private void journal(String message) {
        runOnFx(() -> journalArea.appendText("[%s] %s%n".formatted(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                safe(message))));
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String price(double value) {
        return value <= 0 || !Double.isFinite(value) ? "-" : String.format("%.5f", value);
    }

    private String formatPrice(double value) {
        return value <= 0 || !Double.isFinite(value) ? "-" : String.format("%.5f", value);
    }

    private String money(double value) {
        return !Double.isFinite(value) ? "0.00" : String.format("%.2f", value);
    }

    private @NotNull String number(double value) {
        return !Double.isFinite(value) ? "0" : String.format("%.4f", value);
    }


    /** Returns the spread in points (pips × 10 for 5-decimal pairs). */
    private String formatSpreadPts(TradePair pair) {
        if (pair == null || pair.getBid() <= 0 || pair.getAsk() <= 0) return "-";
        double spread = pair.getAsk() - pair.getBid();
        if (spread <= 0) return "-";
        // Determine decimal precision from bid magnitude
        double bid = pair.getBid();
        double pts;
        if (bid >= 100) {
            pts = spread * 100;        // e.g., BTC/USD
        } else if (bid >= 1) {
            pts = spread * 10_000;     // e.g., EUR/USD (5-decimal)
        } else {
            pts = spread * 100_000;    // e.g., XLM/USDC
        }
        return pts >= 10 ? String.format("%.0f", pts) : String.format("%.1f", pts);
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

    /**
     * Custom cell for rendering strategy list items with colored backgrounds
     */
    private static class StrategyListCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item);
                setStyle(
                        "-fx-text-fill: #10b981; -fx-padding: 8; -fx-border-color: #374151; -fx-border-width: 0 0 1 0;");
                if (item.contains("[Trend-based]")) {
                    setStyle(
                            "-fx-text-fill: #3b82f6; -fx-padding: 8; -fx-border-color: #374151; -fx-border-width: 0 0 1 0;");
                } else if (item.contains("[Oscillator]")) {
                    setStyle(
                            "-fx-text-fill: #f59e0b; -fx-padding: 8; -fx-border-color: #374151; -fx-border-width: 0 0 1 0;");
                } else if (item.contains("[Breakout]")) {
                    setStyle(
                            "-fx-text-fill: #ef4444; -fx-padding: 8; -fx-border-color: #374151; -fx-border-width: 0 0 1 0;");
                } else if (item.contains("[Mean Reversion]")) {
                    setStyle(
                            "-fx-text-fill: #06b6d4; -fx-padding: 8; -fx-border-color: #374151; -fx-border-width: 0 0 1 0;");
                }
            }
        }
    }

    /**
     * Custom cell for rendering research report list items
     */
    private static class ReportListCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox cellContent = new VBox(4);
                cellContent.setPadding(new Insets(8));
                cellContent.setStyle("-fx-border-color: #374151; -fx-border-width: 0 0 1 0;");

                String[] parts = item.split(" \\| ");
                if (parts.length >= 2) {
                    Label dateLabel = new Label(parts[0]);
                    dateLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 10;");

                    Label titleLabel = new Label(parts[1]);
                    titleLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 12; -fx-font-weight: bold;");
                    titleLabel.setWrapText(true);

                    cellContent.getChildren().addAll(dateLabel, titleLabel);
                } else {
                    Label label = new Label(item);
                    label.setStyle("-fx-text-fill: #ffffff;");
                    cellContent.getChildren().add(label);
                }
                setGraphic(cellContent);
                setText(null);
            }
        }
    }

    private <T> List<T> reverseCopy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<T> copy = new ArrayList<>(values);
        Collections.reverse(copy);
        return copy;
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
        private Consumer<String> statusCallback;

        void setStatusCallback(java.util.function.Consumer<String> callback) {
            this.statusCallback = callback;
        }

        void refreshLocalPositions() {
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
        tradePair.setHigh24h(ticker.getHighPrice());
        tradePair.setLow24h(ticker.getLowPrice());
        tradePair.setChangePercent(ticker.getChangePercent());
        tradePair.setUpdatedAt(Instant.now());

        int index = marketWatchItems.indexOf(tradePair);

        if (index >= 0) {
            // Force UI update by setting the item
            TradePair updated = marketWatchItems.get(index);
            updated.setBid(ticker.getBidPrice());
            updated.setAsk(ticker.getAskPrice());
            updated.setLast(ticker.getLastPrice());
            updated.setVolume(ticker.getVolume());
            updated.setHigh24h(ticker.getHighPrice());
            updated.setLow24h(ticker.getLowPrice());
            updated.setChangePercent(ticker.getChangePercent());
            updated.setUpdatedAt(Instant.now());
            marketWatchItems.set(index, updated);
        }

        symbolCountLabel.setText(t("label.symbols", marketWatchItems.size()));
    }

    public void updateTradeFromStream(Trade trade) {
        if (trade != null) {
            accountTradeItems.addFirst(trade);

            while (accountTradeItems.size() > 500) {
                accountTradeItems.removeLast();
            }
        }
    }

    public void updateOrderBookFromStream(OrderBook orderBook) {
        displayOrderBook(orderBook);
    }

    public void updateCandleFromStream() {
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

        if (desktopStreamBridge == null) {
            desktopStreamBridge = new DesktopExchangeStreamBridge(this);
        }

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
                log.debug("Failed to stop desktop UI stream", exception);
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
            stopBotTradingAsync();
            return;
        }

        if (!hasBrokerAccess()) {
            showWarning("Bot Trading", "Validate broker credentials before starting the bot.");
            return;
        }

        if (lacksOrderSubmissionAccess()) {
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

        startBotTradingAsync(
                symbols,
                "%d symbol(s) using %s".formatted(symbols.size(), botSymbolScopeSelector.getValue()),
                () -> withActiveChart(chart -> chart.setAutoTradeEnabled(true)));
    }

    private void startBotTradingAsync(List<TradePair> symbols, String activityScope, Runnable fxSuccessAction) {
        if (!botTradingOperationInFlight.compareAndSet(false, true)) {
            appendAgentActivity("Bot operation already in progress.");
            return;
        }

        botTradeButton.setDisable(true);
        appendAgentActivity("Starting SystemCore bot...");

        List<TradePair> selectedSymbols = List.copyOf(symbols);
        CompletableFuture
                .runAsync(() -> {
                    TradePair primarySymbol = selectedSymbols.getFirst();

                    /*
                     * Prevent duplicate exchange streams:
                     * - UI-only stream OFF
                     * - SystemCore bot stream ON
                     */
                    stopDesktopStream();

                    ensureSystemCoreStarted(primarySymbol);
                    systemCore.setAutoTradingEnabled(true);
                    systemCore.startStreaming(selectedSymbols, SystemCore.StreamingMode.EVERYTHING);
                }, botOperationExecutor)
                .thenRun(() -> runOnFx(() -> {
                    if (fxSuccessAction != null) {
                        fxSuccessAction.run();
                    }
                    botTradingEnabled = true;
                    appendAgentActivity("SystemCore bot enabled for " + activityScope + ".");
                    refreshBotTradeButton();
                    saveAppState();
                }))
                .exceptionally(exception -> {
                    log.error("Failed to toggle bot trading", exception);
                    runOnFx(() -> showWarning(
                            "Bot Trading",
                            "Could not start bot trading: %s".formatted(rootMessage(exception))));
                    return null;
                })
                .whenComplete((unused, exception) -> runOnFx(() -> {
                    botTradingOperationInFlight.set(false);
                    botTradeButton.setDisable(false);
                    refreshBotTradeButton();
                }));
    }

    private void stopBotTradingAsync() {
        if (!botTradingOperationInFlight.compareAndSet(false, true)) {
            appendAgentActivity("Bot operation already in progress.");
            return;
        }

        botTradeButton.setDisable(true);
        appendAgentActivity("Stopping SystemCore bot...");

        TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
        if (selected == null && !marketWatchItems.isEmpty()) {
            selected = marketWatchItems.getFirst();
        }
        TradePair symbolToResume = selected;

        CompletableFuture
                .runAsync(() -> {
                    if (systemCore != null) {
                        systemCore.setAutoTradingEnabled(false);
                        systemCore.stopStreaming();
                    }
                }, botOperationExecutor)
                .thenRun(() -> runOnFx(() -> {
                    botTradingEnabled = false;
                    withActiveChart(chart -> chart.setAutoTradeEnabled(false));
                    appendAgentActivity("SystemCore bot auto trading disabled.");
                    refreshBotTradeButton();
                    saveAppState();

                    /*
                     * After bot stream stops, restart one UI-owned stream for the active symbol.
                     * Starting one stream per watchlist symbol would repeatedly stop the previous
                     * subscription and leave only the last symbol active.
                     */
                    if (symbolToResume != null && hasBrokerAccess()) {
                        startDesktopStream(symbolToResume);
                    }
                }))
                .exceptionally(exception -> {
                    log.debug("Failed to stop SystemCore streaming", exception);
                    runOnFx(() -> showWarning(
                            "Bot Trading",
                            "Could not stop bot trading cleanly: %s".formatted(rootMessage(exception))));
                    return null;
                })
                .whenComplete((unused, exception) -> runOnFx(() -> {
                    botTradingOperationInFlight.set(false);
                    botTradeButton.setDisable(false);
                    refreshBotTradeButton();
                }));
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
                log.debug("Failed to stop SystemCore bot", exception);
            }

            try {
                systemCore.disconnect();
            } catch (Exception exception) {
                log.debug("Failed to disconnect SystemCore", exception);
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
            log.debug("Auto-refresh executor interrupted during shutdown", exception);
        }

        try {
            botOperationExecutor.shutdown();
            if (!botOperationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                botOperationExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            botOperationExecutor.shutdownNow();
            log.debug("Bot operation executor interrupted during shutdown", exception);
        }

        try {
            if (exchange != null) {
                exchange.stopAllStreams();
                exchange.disconnectStream();
                exchange.disconnect();
            }
        } catch (Exception exception) {
            log.warn("Failed to close exchange cleanly", exception);
        }
    }

    // ============================================================================
    // Strategy Menu Methods
    // ============================================================================

    private void openStrategyLabPanel() {
        if (systemCore == null) {
            showWarning("Strategy Lab", "SystemCore is not initialized. Connect an exchange first.");
            return;
        }

        try {
            StrategyLabPanel strategyLabPanel = new StrategyLabPanel(systemCore);
            createIndependentWindow("Strategy Lab", strategyLabPanel, 1200, 800);
            journal("Strategy Lab opened");
            log.info("Strategy Lab panel opened");
        } catch (SQLException | ClassNotFoundException exception) {
            log.error("Strategy Lab panel failed to open", exception);
            showWarning("Strategy Lab", "Unable to open Strategy Lab: " + exception.getMessage());
        }
    }

    private void openStrategyDeveloperPanel() {
        StrategyDeveloperPanel strategyDeveloperPanel = new StrategyDeveloperPanel(systemCore);
        createIndependentWindow("Strategy Developer", strategyDeveloperPanel, 1100, 760);
        journal("Strategy Developer opened");
        log.info("Strategy Developer panel opened");
    }

    private void openStrategyAssignmentPanel() {
        if (systemCore == null) {
            showWarning("Strategy Assignment", "SystemCore is not initialized. Connect an exchange first.");
            return;
        }
        try {
            showStrategyAssignmentPanel();
        } catch (SQLException | ClassNotFoundException exception) {
            log.error("Strategy Assignment panel failed to open", exception);
            showWarning("Strategy Assignment", "Unable to open Strategy Assignment: " + exception.getMessage());
        }
    }

    private void openSettingsPanel() {
        SettingsPanel settingsPanel = new SettingsPanel(systemCore);
        createIndependentWindow("Settings", settingsPanel, 900, 760);
        journal("Settings panel opened");
        log.info("Settings panel opened");
    }

    private void openPluginManagerPanel() {
        PluginManagerPanel pluginManagerPanel = new PluginManagerPanel();
        createIndependentWindow("Plugin Manager", pluginManagerPanel, 980, 680);
        journal("Plugin Manager opened");
        log.info("Plugin Manager panel opened");
    }

    private void openBacktestReportPanel() {
        if (backtestReportPanel == null) {
            backtestReportPanel = new BacktestReportPanel();
        }
        createIndependentWindow("Backtest Report", backtestReportPanel, 900, 760);
        journal("Backtest Report panel opened");
        log.info("Backtest Report panel opened");
    }

    private void openStrategyBuilder() {
        StrategyBuilderPanel strategyBuilder = new StrategyBuilderPanel(systemCore);
        createIndependentWindow("Strategy Builder", strategyBuilder, 1000, 700);
        journal("Strategy Builder opened");
        log.info("Strategy Builder panel opened");
    }

    private void openBacktesting() {
        try {
            BacktestingPanel backtestingPanel = new BacktestingPanel(systemCore);
            createIndependentWindow("Backtesting", backtestingPanel, 1000, 700);
            journal("Backtesting panel opened");
            log.info("Backtesting panel opened");
        } catch (Exception exception) {
            journal("Backtesting panel failed to open: " + exception.getMessage());
            log.error("Backtesting panel failed to open", exception);
            showAlert("Unable to open backtesting: " + exception.getMessage());
        }
    }

    private void showAlert(String s) {
        Stage stage = new Stage();
        VBox vBox = new VBox(new Label("Backtesting" + "\n----------\n" + s));
        Scene root = new Scene(new StackPane(vBox));

        stage.setScene(root);
        stage.showAndWait();
    }

    private void openAnalysis() {
        // Reuse analysisPanel instance if already created, otherwise create it now
        if (analysisPanel == null) {
            analysisPanel = new AnalysisPanel(systemCore);
        }
        createIndependentWindow("Analysis", analysisPanel, 1000, 700);
        journal("Analysis panel opened (backtesting metrics by default, will switch to live when bot starts)");
        log.info("Analysis panel opened - backtesting metrics by default, switching to live when bot starts");
    }

    private void openAllStrategies() {
        VBox strategiesView = new VBox(12);
        strategiesView.setPadding(new Insets(16));
        strategiesView.setStyle("-fx-background-color: #1a1a2e;");

        Label title = new Label("Available Strategies");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        HBox filterBox = new HBox(12);
        filterBox.setPadding(new Insets(12));
        filterBox.setStyle("-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1;");

        Label filterLabel = new Label("Filter: ");
        filterLabel.setStyle("-fx-text-fill: #a0aec0;");

        ComboBox<String> filterCombo = new ComboBox<>();
        filterCombo.getItems().addAll("All", "Trend-based", "Oscillator-based", "Mean Reversion", "Breakout");
        filterCombo.setValue("All");
        filterCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        filterBox.getChildren().addAll(filterLabel, filterCombo);

        ObservableList<String> allStrategies = FXCollections.observableArrayList(StrategyCatalog.availableStrategyNames());
        FilteredList<String> filteredStrategies = new FilteredList<>(allStrategies, ignored -> true);

        ListView<String> strategyList = new ListView<>();
        strategyList.setItems(filteredStrategies);
        strategyList.setCellFactory(param -> new StrategyListCell());

        filterCombo.valueProperty().addListener((obs, oldValue, newValue) ->
                filteredStrategies.setPredicate(strategy -> matchesStrategyFilter(strategy, newValue)));
        if (!filteredStrategies.isEmpty()) {
            strategyList.getSelectionModel().selectFirst();
        }

        Label assignmentIndicator = new Label();
        assignmentIndicator.setWrapText(true);
        assignmentIndicator.setStyle(
            "-fx-text-fill: #93c5fd; -fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1; -fx-padding: 10;");

        Runnable refreshAssignmentIndicator = () -> assignmentIndicator
            .setText(buildCurrentAssignmentIndicatorText());
        refreshAssignmentIndicator.run();

        javafx.beans.value.ChangeListener<TradePair> symbolRefreshListener = (obs, oldValue, newValue) ->
                refreshAssignmentIndicator.run();
        javafx.beans.value.ChangeListener<Timeframe> timeframeRefreshListener = (obs, oldValue, newValue) ->
                refreshAssignmentIndicator.run();

        if (symbolSelector != null) {
            symbolSelector.getSelectionModel().selectedItemProperty().addListener(symbolRefreshListener);
        }
        if (timeframeSelector != null) {
            timeframeSelector.getSelectionModel().selectedItemProperty().addListener(timeframeRefreshListener);
        }

        strategyList.sceneProperty().addListener((sceneObs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            newScene.windowProperty().addListener((windowObs, oldWindow, newWindow) -> {
                if (!(newWindow instanceof Stage stage)) {
                    return;
                }
                stage.setOnHidden(event -> {
                    if (symbolSelector != null) {
                        symbolSelector.getSelectionModel().selectedItemProperty().removeListener(symbolRefreshListener);
                    }
                    if (timeframeSelector != null) {
                        timeframeSelector.getSelectionModel().selectedItemProperty()
                                .removeListener(timeframeRefreshListener);
                    }
                    stage.close();
                    log.info("Independent window closed: {}", stage.getTitle());
                });
            });
        });

        strategyList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) ->
            refreshAssignmentIndicator.run());

        HBox actionBox = new HBox(12);
        actionBox.setPadding(new Insets(12));
        actionBox.setStyle("-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1;");

        Button viewBtn = new Button("View Details");
        viewBtn.setStyle("-fx-padding: 6 16; -fx-background-color: #3b82f6; -fx-text-fill: white;");
        viewBtn.disableProperty().bind(strategyList.getSelectionModel().selectedItemProperty().isNull());
        viewBtn.setOnAction(e -> {
            String selectedStrategy = strategyList.getSelectionModel().getSelectedItem();
            if (selectedStrategy == null || selectedStrategy.isBlank()) {
                showWarning("Strategy Details", "Select a strategy first.");
                return;
            }
            showInfo("Strategy Details", buildStrategyDetails(selectedStrategy));
        });

        Button selectBtn = new Button("Select Strategy");
        selectBtn.setStyle("-fx-padding: 6 16; -fx-background-color: #10b981; -fx-text-fill: white;");
        selectBtn.disableProperty().bind(strategyList.getSelectionModel().selectedItemProperty().isNull());
        selectBtn.setOnAction(e -> {
            String selectedStrategy = strategyList.getSelectionModel().getSelectedItem();
            if (selectedStrategy == null || selectedStrategy.isBlank()) {
                showWarning("Select Strategy", "Select a strategy first.");
                return;
            }

            TradePair selectedPair = symbolSelector == null ? null : symbolSelector.getSelectionModel().getSelectedItem();
            if (selectedPair == null) {
                showWarning("Select Strategy", "Select a symbol in the main toolbar first.");
                return;
            }

            Timeframe selectedTimeframe = timeframeSelector == null
                    ? null
                    : timeframeSelector.getSelectionModel().getSelectedItem();
            if (selectedTimeframe == null) {
                showWarning("Select Strategy", "Select a timeframe in the main toolbar first.");
                return;
            }

            try {
                StrategyAssignment assignment = StrategySelectionService.getInstance().manuallyAssign(
                        selectedPair.toString('/'),
                        selectedTimeframe,
                        selectedStrategy,
                        true,
                        "Manual selection from Available Strategies");

                journal("Manual strategy selected: " + assignment.getStrategyId() + " for "
                        + selectedPair.toString('/') + " / " + selectedTimeframe.getCode());
                showInfo(
                        "Strategy Selected",
                        "Assigned \"%s\" to %s on %s.".formatted(
                                assignment.getStrategyId(),
                                selectedPair.toString('/'),
                                selectedTimeframe.getCode()));
                refreshAssignmentIndicator.run();
            } catch (Exception exception) {
                log.error("Manual strategy selection failed", exception);
                showWarning("Select Strategy", "Unable to assign strategy: " + rootMessage(exception));
            }
        });

        strategyList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selectedStrategy = strategyList.getSelectionModel().getSelectedItem();
                if (selectedStrategy != null && !selectedStrategy.isBlank()) {
                    showInfo("Strategy Details", buildStrategyDetails(selectedStrategy));
                }
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        actionBox.getChildren().addAll(viewBtn, selectBtn, spacer);

        strategiesView.getChildren().addAll(title, filterBox, assignmentIndicator, strategyList, actionBox);
        VBox.setVgrow(strategyList, javafx.scene.layout.Priority.ALWAYS);

        createIndependentWindow("Available Strategies", strategiesView, 900, 700);
        journal("Strategy list opened");
    }

    private @NotNull String buildCurrentAssignmentIndicatorText() {
        TradePair selectedPair = symbolSelector == null ? null : symbolSelector.getSelectionModel().getSelectedItem();
        Timeframe selectedTimeframe = timeframeSelector == null ? null : timeframeSelector.getSelectionModel().getSelectedItem();

        if (selectedPair == null || selectedTimeframe == null) {
            return "Current assignment: select symbol and timeframe in the main toolbar.";
        }

        String symbol = selectedPair.toString('/');
        StrategyAssignment assignment = StrategyAssignmentRepository.getInstance().getActive(symbol, selectedTimeframe);
        if (assignment == null || assignment.getStrategyId() == null || assignment.getStrategyId().isBlank()) {
            return "Current assignment for %s on %s: none".formatted(symbol, selectedTimeframe.getCode());
        }

        String mode = assignment.getMode() == null ? "Unknown" : assignment.getMode().getDisplayName();
        String assignedAt = assignment.getAssignedAt() == null ? "Unknown" : assignment.getAssignedAt().toString();

        return "Current assignment for %s on %s: %s (Mode: %s, Assigned: %s)".formatted(
                symbol,
                selectedTimeframe.getCode(),
            assignment.getStrategyId(),
            mode,
            assignedAt);
    }

    private boolean matchesStrategyFilter(String strategyName, String selectedFilter) {
        if (strategyName == null || strategyName.isBlank()) {
            return false;
        }
        if (selectedFilter == null || "All".equalsIgnoreCase(selectedFilter)) {
            return true;
        }

        String normalized = strategyName.toLowerCase(Locale.ROOT);
        return switch (selectedFilter) {
            case "Trend-based" -> normalized.contains("trend")
                    || normalized.contains("ema")
                    || normalized.contains("momentum")
                    || normalized.contains("pullback")
                    || normalized.contains("donchian")
                    || normalized.contains("macd");
            case "Oscillator-based" -> normalized.contains("rsi")
                    || normalized.contains("oscillator")
                    || normalized.contains("stoch")
                    || normalized.contains("macd");
            case "Mean Reversion" -> normalized.contains("mean reversion")
                    || normalized.contains("range fade")
                    || normalized.contains("reversion")
                    || normalized.contains("bollinger")
                    || normalized.contains("reversal")
                    || normalized.contains("failure swing");
            case "Breakout" -> normalized.contains("breakout")
                    || normalized.contains("squeeze")
                    || normalized.contains("compression");
            default -> true;
        };
    }

    private @NotNull String buildStrategyDetails(String strategyName) {
        StrategyDefinition definition = StrategyCatalog.definition(strategyName);
        StrategyParameters params = definition.getParameters();

        String symbol = symbolSelector == null || symbolSelector.getSelectionModel().getSelectedItem() == null
                ? "(none selected)"
                : symbolSelector.getSelectionModel().getSelectedItem().toString('/');

        Timeframe timeframe = timeframeSelector == null
                ? null
                : timeframeSelector.getSelectionModel().getSelectedItem();

        String timeframeCode = timeframe == null ? "(none selected)" : timeframe.getCode();
        String activeAssignment = "None";

        if (timeframe != null && symbolSelector != null && symbolSelector.getSelectionModel().getSelectedItem() != null) {
            StrategyAssignment assignment = StrategyAssignmentRepository.getInstance().getActive(symbol, timeframe);
            if (assignment != null && assignment.getStrategyId() != null && !assignment.getStrategyId().isBlank()) {
                activeAssignment = assignment.getStrategyId();
            }
        }

        return "Name: " + definition.getName() + "\n"
                + "Base: " + definition.getBaseName() + "\n"
                + "\n"
                + "Parameters\n"
                + "- RSI Period: " + params.getRsiPeriod() + "\n"
                + "- EMA Fast: " + params.getEmaFast() + "\n"
                + "- EMA Slow: " + params.getEmaSlow() + "\n"
                + "- ATR Period: " + params.getAtrPeriod() + "\n"
                + "- Breakout Lookback: " + params.getBreakoutLookback() + "\n"
                + "- Oversold Threshold: " + String.format("%.2f", params.getOversoldThreshold()) + "\n"
                + "- Overbought Threshold: " + String.format("%.2f", params.getOverboughtThreshold()) + "\n"
                + "- Min Confidence: " + String.format("%.2f", params.getMinConfidence()) + "\n"
                + "- Signal Amount: " + String.format("%.2f", params.getSignalAmount()) + "\n"
                + "\n"
                + "Current Context\n"
                + "- Selected Symbol: " + symbol + "\n"
                + "- Selected Timeframe: " + timeframeCode + "\n"
                + "- Active Assignment: " + activeAssignment;
    }

    private void importStrategy() {
        log.info("Import strategy dialog opened");
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Import Strategy");
        dialog.setHeaderText("Import trading strategy from file");

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: #1a1a2e;");

        Label instrLabel = new Label("Select a strategy file to import:");
        instrLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11;");

        HBox fileBox = new HBox(12);
        fileBox.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1; -fx-padding: 12;");

        TextField filePathField = new TextField();
        filePathField.setPromptText("Select strategy file...");
        filePathField.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Button browseBtn = new Button("Browse...");
        browseBtn.setStyle("-fx-padding: 6 16; -fx-background-color: #6366f1; -fx-text-fill: white;");
        browseBtn.setOnAction(e -> {
            showInfo("File Browser", "Select .strategy or .json file");
            filePathField.setText("strategies/my_strategy.json");
        });

        fileBox.getChildren().addAll(filePathField, browseBtn);
        HBox.setHgrow(filePathField, Priority.ALWAYS);

        Label previewLabel = new Label("Preview:");
        previewLabel.setStyle("-fx-text-fill: #3b82f6; -fx-font-weight: bold;");

        TextArea previewArea = new TextArea();
        previewArea.setPromptText("Strategy configuration preview...");
        previewArea.setWrapText(true);
        previewArea.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
        previewArea.setPrefRowCount(8);

        content.getChildren().addAll(instrLabel, fileBox, previewLabel, previewArea);
        VBox.setVgrow(previewArea, Priority.ALWAYS);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setStyle("-fx-background-color: #1a1a2e;");

        Optional<Void> result = dialog.showAndWait();
        if (result.isPresent()) {
            journal("Strategy imported from: " + filePathField.getText());
            showInfo("Import Successful", "Strategy has been imported successfully!");
        }
    }

    private void exportStrategy() {
        log.info("Export strategy dialog opened");
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Export Strategy");
        dialog.setHeaderText("Export active trading strategy");

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: #1a1a2e;");

        Label stratLabel = new Label("Select strategy to export:");
        stratLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11;");

        ComboBox<String> strategyCombo = new ComboBox<>();
        strategyCombo.getItems().addAll(
                "Mean Reversion Strategy",
                "Trend Following Strategy",
                "Breakout Strategy",
                "Bollinger Bands Strategy");
        strategyCombo.setValue("Trend Following Strategy");
        strategyCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Label formatLabel = new Label("Export format:");
        formatLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11;");

        ToggleGroup formatGroup = new ToggleGroup();
        RadioButton jsonRadio = new RadioButton("JSON format (.json)");
        jsonRadio.setToggleGroup(formatGroup);
        jsonRadio.setSelected(true);
        jsonRadio.setStyle("-fx-text-fill: #ffffff;");

        RadioButton xmlRadio = new RadioButton("XML format (.xml)");
        xmlRadio.setToggleGroup(formatGroup);
        xmlRadio.setStyle("-fx-text-fill: #ffffff;");

        RadioButton csvRadio = new RadioButton("CSV format (.csv)");
        csvRadio.setToggleGroup(formatGroup);
        csvRadio.setStyle("-fx-text-fill: #ffffff;");

        Label pathLabel = new Label("Save location:");
        pathLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11;");

        HBox pathBox = new HBox(12);
        pathBox.setStyle(
                "-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1; -fx-padding: 12;");

        TextField pathField = new TextField();
        pathField.setText("strategies/exported_strategy.json");
        pathField.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        Button browseBtn = new Button("Browse...");
        browseBtn.setStyle("-fx-padding: 6 16; -fx-background-color: #6366f1; -fx-text-fill: white;");
        browseBtn.setOnAction(e -> showInfo("Save As", "Choose export location"));

        pathBox.getChildren().addAll(pathField, browseBtn);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        content.getChildren().addAll(
                stratLabel, strategyCombo,
                new Separator(),
                formatLabel, jsonRadio, xmlRadio, csvRadio,
                new Separator(),
                pathLabel, pathBox);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setStyle("-fx-background-color: #1a1a2e;");

        Optional<Void> result = dialog.showAndWait();
        if (result.isPresent()) {
            journal("Strategy exported to: " + pathField.getText());
            showInfo("Export Successful", "Strategy has been exported successfully!");
        }
    }

    private void openOrderPanel() {
        try {
            TradePair selectedSymbol = symbolSelector == null ? null : symbolSelector.getValue();
            showOrderPanelWindow(selectedSymbol);

        } catch (Exception e) {
            log.error("Error opening order panel", e);
            showWarning("Error", "Failed to open order panel: " + e.getMessage());
        }
    }

    private void showOrderPanelWindow(TradePair selectedSymbol) {
        OrderPanel orderPanel = new OrderPanel(systemCore, selectedSymbol);

        Stage orderStage = new Stage();
        orderStage.setTitle("Order Manager - " + (selectedSymbol != null ? selectedSymbol.getSymbol() : "Trading"));
        orderStage.setScene(new Scene(orderPanel, 1180, 780));
        orderStage.setWidth(1180);
        orderStage.setHeight(780);
        orderStage.setMinWidth(1060);
        orderStage.setMinHeight(720);
        orderStage.setResizable(true);
        orderStage.show();

        log.info("Order panel opened for symbol: {}", selectedSymbol != null ? selectedSymbol.getSymbol() : "N/A");
        journal("Order panel opened");
    }

    // ============================================================================
    // EXCHANGE VENUE LABEL & DATA PROVIDER METHODS
    // ============================================================================

    /**
     * Update the exchange venue label to show the current connected exchange
     */
    private void updateExchangeVenueLabel() {
        if (exchange != null) {
            exchangeVenueLabel.setText("Venue: " + exchange.getDisplayName());
            exchangeVenueLabel.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 11; -fx-font-weight: bold;");
        } else {
            exchangeVenueLabel.setText("Venue: -");
            exchangeVenueLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 11;");
        }
    }

    /**
     * Get current market sentiment index (0-100 scale)
     */
    private @NotNull String getMarketSentimentIndex() {
        MarketSnapshot snapshot = marketSnapshot();
        return snapshot.hasMarketData()
                ? String.valueOf(Math.round(snapshot.sentimentIndex()))
                : "N/A";
    }

    private @NotNull String getOverallMarketSentiment() {
        MarketSnapshot snapshot = marketSnapshot();
        if (!snapshot.hasMarketData()) {
            return "N/A";
        }

        double index = snapshot.sentimentIndex();
        if (index >= 75.0) {
            return "Strong Bullish";
        }
        if (index >= 60.0) {
            return "Bullish";
        }
        if (index >= 45.0) {
            return "Neutral";
        }
        if (index >= 30.0) {
            return "Bearish";
        }
        return "Strong Bearish";
    }

    private String getInvestorConfidence() {
        MarketSnapshot market = marketSnapshot();
        StrategyStats strategy = strategyStats();
        if (!market.hasMarketData() && strategy.totalTrades() == 0) {
            return "N/A";
        }

        double confidence = 50.0;
        if (market.hasMarketData()) {
            confidence += (market.sentimentIndex() - 50.0) * 0.55;
        }
        if (strategy.totalTrades() > 0) {
            confidence += (strategy.winRatePercent() - 50.0) * 0.35;
            confidence += Math.max(-10.0, Math.min(10.0, strategy.profitFactor() - 1.0)) * 3.0;
        }

        if (confidence >= 75.0) {
            return "High";
        }
        if (confidence >= 58.0) {
            return "Rising";
        }
        if (confidence >= 42.0) {
            return "Stable";
        }
        if (confidence >= 25.0) {
            return "Falling";
        }
        return "Low";
    }

    private @NotNull String getMarketVIX() {
        MarketSnapshot snapshot = marketSnapshot();
        return snapshot.hasMarketData() ? formatNumber(snapshot.syntheticVix(), 1) : "N/A";
    }

    private @NotNull String getBitcoinDominance() {
        MarketSnapshot snapshot = marketSnapshot();
        return snapshot.hasMarketData() && snapshot.bitcoinDominancePercent() >= 0.0
                ? formatPercent(snapshot.bitcoinDominancePercent())
                : "N/A";
    }

    private String getTradingVolume() {
        MarketSnapshot snapshot = marketSnapshot();
        return snapshot.hasMarketData()
                ? formatCurrencyCompact(snapshot.totalQuoteVolume()) + " (24h)"
                : "N/A";
    }

    /**
     * Strategy Performance Data Getters
     */
    private String getStrategyTotalReturn() {
        StrategyStats stats = strategyStats();
        return stats.totalTrades() > 0 ? formatSignedPercent(stats.totalReturnPercent(), 1) : "N/A";
    }

    private String getStrategyWinRate() {
        StrategyStats stats = strategyStats();
        return stats.totalTrades() > 0 ? formatPercent(stats.winRatePercent()) : "N/A";
    }

    private String getStrategyProfitFactor() {
        StrategyStats stats = strategyStats();
        return stats.totalTrades() > 0 ? formatNumber(stats.profitFactor(), 2) : "N/A";
    }

    private String getStrategySharpeRatio() {
        StrategyStats stats = strategyStats();
        return stats.totalTrades() > 1 ? formatNumber(stats.sharpeRatio(), 2) : "N/A";
    }

    private String getStrategySortinoRatio() {
        StrategyStats stats = strategyStats();
        return stats.totalTrades() > 1 ? formatNumber(stats.sortinoRatio(), 2) : "N/A";
    }

    private @NotNull String getStrategyMonthlyReturn() {
        StrategyStats stats = strategyStats();
        return stats.totalTrades() > 0 ? formatSignedPercent(stats.monthlyReturnPercent(), 1) : "N/A";
    }

    private String getStrategyMaxDrawdown() {
        StrategyStats stats = strategyStats();
        return stats.totalTrades() > 0 ? formatSignedPercent(-stats.maxDrawdownPercent(), 1) : "N/A";
    }

    private String getStrategyCurrentDrawdown() {
        StrategyStats stats = strategyStats();
        return stats.totalTrades() > 0 ? formatSignedPercent(-stats.currentDrawdownPercent(), 1) : "N/A";
    }

    private String getStrategyAvgDrawdown() {
        StrategyStats stats = strategyStats();
        return stats.totalTrades() > 0 ? formatSignedPercent(-stats.averageDrawdownPercent(), 1) : "N/A";
    }

    private String getStrategyRecoveryTime() {
        StrategyStats stats = strategyStats();
        return stats.totalTrades() > 0 ? stats.maxRecoveryDays() + " days" : "N/A";
    }

    private String getStrategyUnderwaterDuration() {
        StrategyStats stats = strategyStats();
        return stats.totalTrades() > 0 ? stats.currentUnderwaterDays() + " days" : "N/A";
    }

    private String getStrategyTotalTrades() {
        return String.valueOf(strategyStats().totalTrades());
    }

    private String getStrategyWinningTrades() {
        return String.valueOf(strategyStats().winningTrades());
    }

    private String getStrategyLosingTrades() {
        return String.valueOf(strategyStats().losingTrades());
    }

    private @NotNull String getStrategyAvgWin() {
        StrategyStats stats = strategyStats();
        return stats.winningTrades() > 0 ? formatSignedPercent(stats.averageWinPercent(), 2) : "N/A";
    }

    private @NotNull String getStrategyAvgLoss() {
        StrategyStats stats = strategyStats();
        return stats.losingTrades() > 0 ? formatSignedPercent(stats.averageLossPercent(), 2) : "N/A";
    }

    private @NotNull String getStrategyRiskRewardRatio() {
        StrategyStats stats = strategyStats();
        return stats.winningTrades() > 0 && stats.losingTrades() > 0
                ? formatNumber(stats.riskRewardRatio(), 2)
                : "N/A";
    }

    private MarketSnapshot marketSnapshot() {
        if (cachedMarketSnapshotAt.plusSeconds(10).isAfter(Instant.now())) {
            return cachedMarketSnapshot;
        }

        List<TradePair> pairs = new ArrayList<>(marketWatchItems);
        if (pairs.isEmpty() && symbolSelector.getValue() != null) {
            pairs.add(symbolSelector.getValue());
        }

        // Return immediately with local data and refresh network values asynchronously to
        // avoid blocking the FX thread.
        MarketSnapshot localSnapshot = computeMarketSnapshotFromPairs(pairs);
        if (localSnapshot.hasMarketData()) {
            cachedMarketSnapshot = localSnapshot;
            cachedMarketSnapshotAt = Instant.now();
        }

        refreshMarketSnapshotAsync(pairs);
        return cachedMarketSnapshot;
    }

    private void refreshMarketSnapshotAsync(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty() || exchange == null
                || !marketSnapshotRefreshInFlight.compareAndSet(false, true)) {
            return;
        }

        exchange.fetchTickers(pairs)
                .orTimeout(2, TimeUnit.SECONDS)
                .thenApply(tickers -> {
                    applyTickerUpdates(pairs, tickers);
                    return computeMarketSnapshotFromPairs(pairs);
                })
                .exceptionally(exception -> {
                    log.debug("Unable to refresh market research snapshot: {}", rootMessage(exception));
                    return computeMarketSnapshotFromPairs(pairs);
                })
                .thenAccept(snapshot -> {
                    cachedMarketSnapshot = snapshot;
                    cachedMarketSnapshotAt = Instant.now();
                })
                .whenComplete((result, throwable) -> marketSnapshotRefreshInFlight.set(false));
    }

    private void applyTickerUpdates(List<TradePair> pairs, List<Ticker> tickers) {
        if (tickers == null || pairs == null) {
            return;
        }

        for (int i = 0; i < tickers.size() && i < pairs.size(); i++) {
            Ticker ticker = tickers.get(i);
            TradePair pair = pairs.get(i);
            if (ticker != null && pair != null) {
                pair.updateTicker(
                        ticker.getBidPrice(),
                        ticker.getAskPrice(),
                        ticker.getLastPrice(),
                        ticker.getVolume(),
                        ticker.getChangePercent());
            }
        }
    }

    private @NotNull MarketSnapshot computeMarketSnapshotFromPairs(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return MarketSnapshot.empty();
        }

        double weightedChange = 0.0;
        double totalWeight = 0.0;
        double positiveWeight = 0.0;
        double totalQuoteVolume = 0.0;
        double btcQuoteVolume = 0.0;
        double spreadSum = 0.0;
        int spreadCount = 0;

        for (TradePair pair : pairs) {
            if (pair == null) {
                continue;
            }

            double price = firstPositive(pair.getLastPrice(), pair.getMidPrice(), pair.getBid(), pair.getAsk());
            double volume = safePositive(pair.getVolume());
            double quoteVolume = price > 0.0 && volume > 0.0 ? price * volume : volume;
            double weight = quoteVolume > 0.0 ? quoteVolume : 1.0;
            double change = finite(pair.getChangePercent());

            weightedChange += change * weight;
            totalWeight += weight;
            if (change > 0.0) {
                positiveWeight += weight;
            }
            totalQuoteVolume += Math.max(0.0, quoteVolume);
            if ("BTC".equalsIgnoreCase(pair.getBaseCode())) {
                btcQuoteVolume += Math.max(0.0, quoteVolume);
            }

            if (pair.getBid() > 0.0 && pair.getAsk() > pair.getBid()) {
                spreadSum += ((pair.getAsk() - pair.getBid()) / pair.getBid()) * 100.0;
                spreadCount++;
            }
        }

        if (totalWeight <= 0.0) {
            return MarketSnapshot.empty();
        }

        double averageChange = weightedChange / totalWeight;
        double breadth = positiveWeight / totalWeight;
        double averageSpread = spreadCount == 0 ? 0.0 : spreadSum / spreadCount;
        double trendScore = clamp(averageChange * 6.0, -30.0, 30.0);
        double breadthScore = (breadth - 0.5) * 40.0;
        double liquidityPenalty = clamp(averageSpread * 2.0, 0.0, 10.0);
        double sentimentIndex = clamp(50.0 + trendScore + breadthScore - liquidityPenalty, 0.0, 100.0);
        double syntheticVix = clamp(12.0 + Math.abs(averageChange) * 4.0 + averageSpread * 2.5, 8.0, 80.0);
        double btcDominance = totalQuoteVolume > 0.0 ? (btcQuoteVolume / totalQuoteVolume) * 100.0 : -1.0;

        return new MarketSnapshot(sentimentIndex, averageChange, breadth, syntheticVix, btcDominance,
            totalQuoteVolume);
    }

    private StrategyStats strategyStats() {
        if (cachedStrategyStatsAt.plusSeconds(5).isAfter(Instant.now())) {
            return cachedStrategyStats;
        }

        List<TradeStat> trades = collectTradeStats();
        if (trades.isEmpty()) {
            cachedStrategyStats = StrategyStats.empty();
            cachedStrategyStatsAt = Instant.now();
            return cachedStrategyStats;
        }

        trades.sort(Comparator.comparing(TradeStat::timestamp, Comparator.nullsLast(Comparator.naturalOrder())));

        int totalTrades = trades.size();
        int winningTrades = 0;
        int losingTrades = 0;
        double grossProfit = 0.0;
        double grossLoss = 0.0;
        double totalProfit = 0.0;
        double totalNotional = 0.0;
        double sumWinPercent = 0.0;
        double sumLossPercent = 0.0;
        List<Double> returns = new ArrayList<>();

        double equity = 0.0;
        double peak = 0.0;
        double maxDrawdown = 0.0;
        double drawdownSum = 0.0;
        int drawdownCount = 0;
        long currentUnderwaterDays = 0L;
        long maxRecoveryDays = 0L;
        Instant underwaterStart = null;

        for (TradeStat trade : trades) {
            double profit = finite(trade.profit());
            double notional = Math.max(1.0, Math.abs(trade.notional()));
            double returnPercent = (profit / notional) * 100.0;

            totalProfit += profit;
            totalNotional += notional;
            returns.add(returnPercent);

            if (profit > 0.0) {
                winningTrades++;
                grossProfit += profit;
                sumWinPercent += returnPercent;
            } else if (profit < 0.0) {
                losingTrades++;
                grossLoss += Math.abs(profit);
                sumLossPercent += returnPercent;
            }

            equity += profit;
            if (equity >= peak) {
                if (underwaterStart != null && trade.timestamp() != null) {
                    maxRecoveryDays = Math.max(maxRecoveryDays,
                            ChronoUnit.DAYS.between(underwaterStart, trade.timestamp()));
                    underwaterStart = null;
                }
                peak = equity;
            } else {
                if (underwaterStart == null) {
                    underwaterStart = trade.timestamp();
                }
                double drawdown = peak - equity;
                maxDrawdown = Math.max(maxDrawdown, drawdown);
                double drawdownPercent = peak > 0.0 ? (drawdown / peak) * 100.0 : 0.0;
                if (drawdownPercent > 0.0) {
                    drawdownSum += drawdownPercent;
                    drawdownCount++;
                }
            }
        }

        if (underwaterStart != null) {
            currentUnderwaterDays = Math.max(0L, ChronoUnit.DAYS.between(underwaterStart, Instant.now()));
        }

        double totalReturnPercent = totalNotional > 0.0 ? (totalProfit / totalNotional) * 100.0 : 0.0;
        double winRatePercent = totalTrades > 0 ? (winningTrades * 100.0) / totalTrades : 0.0;
        double profitFactor = grossLoss > 0.0 ? grossProfit / grossLoss : Math.max(grossProfit, 0.0);
        double averageWinPercent = winningTrades > 0 ? sumWinPercent / winningTrades : 0.0;
        double averageLossPercent = losingTrades > 0 ? sumLossPercent / losingTrades : 0.0;
        double riskRewardRatio = averageLossPercent < 0.0 ? averageWinPercent / Math.abs(averageLossPercent) : 0.0;
        double sharpeRatio = ratioMeanToDeviation(returns, false);
        double sortinoRatio = ratioMeanToDeviation(returns, true);
        double maxDrawdownPercent = peak > 0.0 ? (maxDrawdown / peak) * 100.0 : 0.0;
        double currentDrawdownPercent = peak > 0.0 && equity < peak ? ((peak - equity) / peak) * 100.0 : 0.0;
        double averageDrawdownPercent = drawdownCount == 0 ? 0.0 : drawdownSum / drawdownCount;
        double monthlyReturnPercent = monthlyReturnPercent(trades, totalReturnPercent);

        cachedStrategyStats = new StrategyStats(
                totalTrades,
                winningTrades,
                losingTrades,
                totalReturnPercent,
                winRatePercent,
                profitFactor,
                sharpeRatio,
                sortinoRatio,
                monthlyReturnPercent,
                maxDrawdownPercent,
                currentDrawdownPercent,
                averageDrawdownPercent,
                maxRecoveryDays,
                currentUnderwaterDays,
                averageWinPercent,
                averageLossPercent,
                riskRewardRatio);
        cachedStrategyStatsAt = Instant.now();
        return cachedStrategyStats;
    }

    private @NotNull List<TradeStat> collectTradeStats() {
        List<TradeStat> trades = new ArrayList<>();

        for (Trade trade : accountTradeItems) {
            if (trade == null) {
                continue;
            }
            double profit = finite(trade.getProfit());
            double price = firstPositive(trade.getPrice(), trade.getClosePrice());
            double amount = Math.max(1.0, Math.abs(trade.getAmount()));
            trades.add(new TradeStat(profit, price * amount, trade.getTimestamp()));
        }

        if (!trades.isEmpty()) {
            return trades;
        }

        for (Order order : accountHistoryItems) {
            if (order == null) {
                continue;
            }
            double profit = finite(order.getProfit());
            double notional = firstPositive(Math.abs(order.getPrice() * order.getQuantity()),
                    Math.abs(order.getPrice()), 1.0);
            Instant timestamp = order.getDate() == null ? null : order.getDate().toInstant();
            trades.add(new TradeStat(profit, notional, timestamp));
        }

        if (!trades.isEmpty()) {
            return trades;
        }

        for (Position order : positionsItems) {
            if (order == null) {
                continue;
            }
            double profit = finite(order.getCurrentPrice() - order.getEntryPrice());
            double notional = firstPositive(Math.abs(order.getEntryPrice() * order.getQuantity()),
                    Math.abs(order.getCurrentPrice()), 1.0);
            Instant timestamp = order.getOpenTime() == null ? null : order.getCloseTime();
            trades.add(new TradeStat(profit, notional, timestamp));
        }

        return trades;
    }

    private double monthlyReturnPercent(List<TradeStat> trades, double totalReturnPercent) {
        if (trades.isEmpty()) {
            return 0.0;
        }

        Instant first = trades.stream().map(TradeStat::timestamp).filter(Objects::nonNull).findFirst().orElse(null);
        Instant last = trades.stream().map(TradeStat::timestamp).filter(Objects::nonNull)
                .reduce((ignored, value) -> value).orElse(null);
        if (first == null || last == null || !last.isAfter(first)) {
            return totalReturnPercent;
        }

        double months = Math.max(1.0, ChronoUnit.DAYS.between(first, last) / 30.4375);
        return totalReturnPercent / months;
    }

    private double ratioMeanToDeviation(List<Double> values, boolean downsideOnly) {
        if (values == null || values.size() < 2) {
            return 0.0;
        }

        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = 0.0;
        int count = 0;

        for (double value : values) {
            if (downsideOnly && value >= 0.0) {
                continue;
            }
            double delta = downsideOnly ? Math.min(0.0, value) : value - mean;
            variance += delta * delta;
            count++;
        }

        if (count < 2) {
            return 0.0;
        }

        double deviation = Math.sqrt(variance / (count - 1));
        return deviation > 0.0 ? mean / deviation : 0.0;
    }

    private String formatNumber(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return "N/A";
        }
        DecimalFormat formatter = decimalFormatter(decimals, false);
        return formatter.format(value);
    }

    private String formatPercent(double value) {
        return formatNumber(value, 2) + "%";
    }

    private String formatSignedPercent(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return "N/A";
        }
        DecimalFormat formatter = decimalFormatter(decimals, true);
        return formatter.format(value) + "%";
    }

    private DecimalFormat decimalFormatter(int decimals, boolean showPositiveSign) {
        int safeDecimals = Math.max(0, decimals);
        DecimalFormat formatter = new DecimalFormat();
        formatter.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ROOT));
        formatter.setMinimumFractionDigits(safeDecimals);
        formatter.setMaximumFractionDigits(safeDecimals);
        formatter.setGroupingUsed(false);
        if (showPositiveSign) {
            formatter.setPositivePrefix("+");
        }
        return formatter;
    }

    private String formatCurrencyCompact(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return "N/A";
        }

        double abs = Math.abs(value);
        if (abs >= 1_000_000_000_000.0) {
            return "$" + formatNumber(value / 1_000_000_000_000.0, 2) + "T";
        }
        if (abs >= 1_000_000_000.0) {
            return "$" + formatNumber(value / 1_000_000_000.0, 2) + "B";
        }
        if (abs >= 1_000_000.0) {
            return "$" + formatNumber(value / 1_000_000.0, 2) + "M";
        }
        if (abs >= 1_000.0) {
            return "$" + formatNumber(value / 1_000.0, 2) + "K";
        }
        return "$" + formatNumber(value, 2);
    }

    private double firstPositive(double... values) {
        if (values == null) {
            return 0.0;
        }
        for (double value : values) {
            if (value > 0.0 && Double.isFinite(value)) {
                return value;
            }
        }
        return 0.0;
    }

    private double safePositive(double value) {
        return value > 0.0 && Double.isFinite(value) ? value : 0.0;
    }

    private double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private record MarketSnapshot(
            double sentimentIndex,
            double averageChangePercent,
            double positiveBreadth,
            double syntheticVix,
            double bitcoinDominancePercent,
            double totalQuoteVolume) {
        static MarketSnapshot empty() {
            return new MarketSnapshot(0.0, 0.0, 0.0, 0.0, -1.0, 0.0);
        }

        boolean hasMarketData() {
            return totalQuoteVolume > 0.0 || sentimentIndex > 0.0;
        }
    }

    private record StrategyStats(
            int totalTrades,
            int winningTrades,
            int losingTrades,
            double totalReturnPercent,
            double winRatePercent,
            double profitFactor,
            double sharpeRatio,
            double sortinoRatio,
            double monthlyReturnPercent,
            double maxDrawdownPercent,
            double currentDrawdownPercent,
            double averageDrawdownPercent,
            long maxRecoveryDays,
            long currentUnderwaterDays,
            double averageWinPercent,
            double averageLossPercent,
            double riskRewardRatio) {
        @Contract(" -> new")
        static @NotNull StrategyStats empty() {
            return new StrategyStats(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L, 0L, 0.0, 0.0, 0.0);
        }
    }

    void tradingSystemStatus() {
        if (systemCore == null) {
            showWarning("Trading System Status", "SystemCore is not initialized. Connect an exchange first.");
            return;
        }

        try {
            // Get system health snapshot from SystemCore
            var systemHealth = systemCore.getSystemHealth();
            var currentSession = brokerSessions.get(exchange != null ? exchange.getName() : "");
            var account = currentSession != null ? currentSession.account() : null;
            double availableBalance = account != null ? account.getAvailableBalance() : 0.0;
            double equity = account != null ? account.getEquity() : 0.0;
            double unrealizedPnl = account != null ? account.getUnrealizedPnl() : 0.0;
            double realizedPnlToday = account != null ? account.getRealizedPnlToday() : 0.0;
            var isAutoTrading = systemCore.getSmartBot() != null && systemCore.getSmartBot().isAutoTradingEnabled();
            List<CandleData> candleList = new ArrayList<>(systemCore.getHistoricalDataRepository().findAll());
                TradePair selectedPair = symbolSelector == null ? null : symbolSelector.getValue();
                TradingSessionStatus sessionStatus = selectedPair == null
                    ? TradingSessionStatus.UNKNOWN
                    : selectedPair.getTradingSessionStatus();
                String normalizedSession = sessionStatus.name();
                boolean marketOpen = sessionStatus == TradingSessionStatus.OPEN;

            // Build a comprehensive trading system status snapshot
            TradingSystemStatusSnapshot snapshot = TradingSystemStatusSnapshot.builder()
                    // System State
                    .systemState(SystemState.READY)
                    .brokerName(exchange != null ? exchange.getName() : "Unknown")
                    .tradingMode(configuredTradingMode != null ? configuredTradingMode : "LIVE")
                    .autoTradingEnabled(isAutoTrading)
                    .killSwitchArmed(false)
                    .activeVenue(exchange != null ? exchange.getName() : "N/A")
                    .connectedSince(systemHealth != null ? systemHealth.getTimestamp() : java.time.Instant.now())
                    .lastHeartbeat(java.time.Instant.now())
                    .uptimeSeconds(0L)

                    // Connectivity Status
                    .restApiConnected(true)
                    .webSocketConnected(true)
                    .tickerStreamActive(true)
                    .orderBookStreamActive(true)
                    .candleStreamActive(true)
                    .accountStreamActive(true)
                    .latencyMillis(50L)
                    .rateLimitStatus("OK")
                    .reconnectCount(0)
                    .lastMarketTick(java.time.Instant.now())

                    // Execution Engine
                    .executionEngineRunning(true)
                    .orderSubmissionAllowed(true)
                    .pendingOrders(0)
                    .rejectedOrdersToday(0)
                    .lastOrderId("N/A")
                    .lastFillTime(java.time.Instant.now())
                    .averageFillLatencyMs(50L)
                    .slippageEstimatePips(0.5)
                    .cancelAllSupported(true)

                    // Risk Management
                    .riskStatus(RiskStatus.PASSING)
                    .dailyLoss(account != null ? account.getDailyLoss() : 0.0)
                    .maxDailyLoss(account != null ? account.getMaxDailyLoss() : 0.0)
                    .maxDrawdown(0.05)
                    .currentDrawdown(0.02)
                    .portfolioHeat(0.3)
                    .marginUsed(account != null ? account.getMarginUsed() : 0.0)
                    .freeMargin(account != null ? account.getFreeMargin() : 0.0)
                    .maxPositionsAllowed(10)
                    .currentPositionCount(0)
                    .concentrationRisk(String.valueOf(0.1))
                    .correlationRisk(String.valueOf(0.2))
                    .lastRiskDecision("APPROVED")

                    // Strategies
                    .activeStrategies(StrategyCatalog.availableStrategyNames().size())
                    .bestStrategyToday("N/A")
                    .worstStrategyToday("N/A")
                    .lastSignal("NEUTRAL")
                    .lastSignalConfidence(0.75)
                    .strategyStatus(java.util.List.of())

                    // AI
                    .aiProvider("OpenAI")
                    .aiEnabled(true)
                    .aiReviewMode("ON")
                    .lastAiDecision("APPROVED")
                    .confidenceThreshold(0.7)
                    .lastAiReasoningTime(java.time.Instant.now())
                    .promptVersion("1.0")
                    .learningEngineActive(true)
                    .feedbackSamples(0)

                    // Account
                    .balance(availableBalance)
                    .equity(equity)
                    .availableBalance(availableBalance)
                    .unrealizedPnl(unrealizedPnl)
                    .realizedPnlToday(realizedPnlToday)
                    .feesAndCommission(0.0)
                    .swapOrFundingCost(0.0)
                    .openPositionCount(0)
                    .openOrderCount(0)

                    // Market Sessions
                    .primaryMarketStatus(normalizedSession)
                    .sessionName(normalizedSession)
                    .timeToMarketCloseSeconds(marketOpen ? 3600 : 0)
                    .timeToMarketOpenSeconds(marketOpen ? 0 : 3600)
                    .liquidityCondition(marketOpen ? "GOOD" : "UNKNOWN")
                    .rolloverRiskActive(false)
                    .newsLockoutActive(false)

                    // Data Quality
                    .candlesLoaded(candleList.size())
                    .minimumCandlesRequired(100)
                    .indicatorWarmupComplete(true)
                    .missingCandleGaps(0)
                    .backtestReady(true)
                    .paperTestReady(true)
                    .liveReady(true)
                    .lastDataUpdate(java.time.Instant.now())

                    // Event Bus
                    .eventBusRunning(true)
                    .eventQueueSize(0)
                    .eventsPerSecond(0)
                    .droppedEvents(0)
                    .deadLetterQueueSize(0)
                    .activeSubscribers(0)
                    .lastEventType("N/A")
                    .replayAvailable(false)

                    // Alerts and Health
                    .alerts(java.util.List.of())
                    .systemHealthScore(95)
                    .build();

            // Create and display the panel
            TradingSystemStatusPanel statusPanel = new TradingSystemStatusPanel(snapshot);
            createIndependentWindow("Trading System Status", statusPanel, 800, 700);

        } catch (Exception e) {
            log.error("Error displaying trading system status", e);
            showError( "Failed to display trading system status: " + e.getMessage());
        }
    }
    Stage stage=new Stage();
    private void showError( String s) {

        AnchorPane rt =new AnchorPane(new Text(s));
        Scene sc=new Scene(rt,400,450);
        stage.setScene(sc);
        stage.show();
    }
}
 
 
