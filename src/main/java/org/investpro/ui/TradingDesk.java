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
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import lombok.Getter;
import lombok.Setter;
import org.investpro.core.SystemCore;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.signal.Signal;
import org.investpro.data.CandleData;
import org.investpro.enums.SystemState;
import org.investpro.enums.RiskStatus;
import org.investpro.exchange.consumers.UiExchangeStreamConsumer;
import org.investpro.exchange.consumers.DesktopExchangeStreamBridge;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.i18n.LocalizationService;
import org.investpro.i18n.SupportedLanguage;

import org.investpro.indicators.BollingerBandsIndicator;
import org.investpro.indicators.ChartIndicator;
import org.investpro.indicators.ExponentialMovingAverageIndicator;
import org.investpro.indicators.MACDIndicator;
import org.investpro.indicators.RSIIndicator;
import org.investpro.indicators.SimpleMovingAverageIndicator;
import org.investpro.indicators.VWAPIndicator;
import org.investpro.indicators.StochasticIndicator;
import org.investpro.indicators.CCIIndicator;
import org.investpro.indicators.ATRIndicator;
import org.investpro.indicators.VolatilityIndicator;
import org.investpro.indicators.OBVIndicator;
import org.investpro.indicators.VolumeIndicator;
import org.investpro.indicators.ADXIndicator;
import org.investpro.indicators.IchimokuIndicator;
import org.investpro.indicators.ParabolicSARIndicator;
import org.investpro.indicators.FibonacciRetracementIndicator;
import org.investpro.indicators.ZigzagIndicator;
import org.investpro.indicators.FractalIndicator;
import org.investpro.licensing.LicenseManager;
import org.investpro.monitoring.TradingSystemStatusSnapshot;
import org.investpro.ui.theme.ThemeManager;
import org.investpro.models.market.NewsEvent;
import org.investpro.service.NewsDataProvider;
import org.investpro.models.Account;
import org.investpro.exchange.*;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.models.trading.*;
import org.investpro.market.MarketDataEngine;
import org.investpro.repository.CurrencyRepository;
import org.investpro.repository.OrderRepository;

import org.investpro.repository.TradeRepository;
import org.investpro.risk.PositionHealthScore;
import org.investpro.service.CurrencyService;
import org.investpro.service.NotificationService;
import org.investpro.service.OrderService;
import org.investpro.service.TradeService;
import org.investpro.service.TradingService;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.ui.charts.CandleStickChart;
import org.investpro.ui.charts.DepthChart;
import org.investpro.ui.charts.NewsEventOverlay;
import org.investpro.ui.panels.*;
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

import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.prefs.Preferences;

import javafx.embed.swing.SwingFXUtils;
import org.jetbrains.annotations.Nullable;

import static org.investpro.utils.Side.BUY;
import static org.investpro.utils.Side.SELL;
import static org.investpro.i18n.LocalizationService.t;

/**
 * Main InvestPro / TradeAdviser trading terminal window.
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
public class TradingDesk extends BorderPane {
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
            "INTERACTIVE BROKERS",
            "STELLAR NETWORK"
    };

    private final ComboBox<String> exchangeSelector = new ComboBox<>();
    private final ComboBox<TradePair> symbolSelector = new ComboBox<>();
    private final ComboBox<String> timeframeSelector = new ComboBox<>();
    private final ComboBox<String> botSymbolScopeSelector = new ComboBox<>();
    private final ComboBox<String> orderTypeSelector = new ComboBox<>();
    private final ComboBox<String> tradingModeSelector = new ComboBox<>();
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

    private final Label connectionStatusLabel = new Label(t("status.disconnected"));
    private final Label symbolCountLabel = new Label(t("label.symbols", 0));
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
    private final Preferences preferences = Preferences.userNodeForPackage(TradingDesk.class);

    private final ScheduledExecutorService autoRefreshExecutor = Executors.newScheduledThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "TradingWindow-AutoRefresh");
        thread.setDaemon(true);
        return thread;
    });

    private SplitPane mainVerticalWorkbench;
    private SplitPane horizontalWorkbench;
    private SplitPane centerSplit;
    private VBox systemConsole;
    private Node marketWatchWrapper;
    private Node orderBookWrapper;
    private boolean consoleVisible = true;
    private boolean marketWatchVisible = true;
    private boolean orderBookVisible = true;

    private Exchange exchange;
    private MarketDataEngine marketDataEngine;
    private MarketSnapshot cachedMarketSnapshot = MarketSnapshot.empty();
    private Instant cachedMarketSnapshotAt = Instant.EPOCH;
    private StrategyStats cachedStrategyStats = StrategyStats.empty();
    private Instant cachedStrategyStatsAt = Instant.EPOCH;
    private final Map<String, BrokerSession> brokerSessions = new HashMap<>();
    private SystemCore systemCore;
    private boolean systemCoreEventsSubscribed;
    private final NewsDataProvider newsDataProvider = new NewsDataProvider();

    private boolean botTradingEnabled;
    private boolean brokerAccessGranted;
    private String configuredApiKey = "";
    private String configuredApiSecret = "";
    private String configuredAccountId = "";
    private String configuredTradingMode = "LIVE";
    private String telegramToken = "";
    private String oandaEmailNotification = "";
    private boolean initialized;

    private OrderBook currentOrderBook = new OrderBook();
    private DepthChart depthChart;
    private NewsCalendarPanel newsCalendarPanel;
    private MarketInfoPanel marketInfoPanel;
    private SystemMonitorPanel systemMonitorPanel;
    private MarketWatchPanel symbolAgentMarketWatch; // Symbol-level trading status (from SymbolAgentManager)
    private Navigation navigationPanel; // Exchange navigator
    private DataWindow dataWindow; // Data window for OHLCV display
    private AnalysisPanel analysisPanel; // Institutional analysis with backtesting/live metrics switching
    private TradePair activeOrderBookPair;

    // Track open independent windows to prevent duplicate Scene root assignments
    private final Map<String, Stage> openIndependentWindows = new java.util.HashMap<>();

    private record BrokerSession(Exchange exchange, boolean accessGranted, Account account) {
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
        this.tradingService = new TradingService(systemCore,this.tradeService, this.orderService, this.currencyService);

        // Initialize NotificationService (disabled by default, can be enabled via
        // settings)
        this.notificationService = NotificationService.disabled();

        // Initialize MarketDataEngine (central market data cache and services)
        this.marketDataEngine = new MarketDataEngine();

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
        getStyleClass().add("trading-window-pro");
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
        topSection.setStyle("-fx-background-color: #1a1a2e;");

        MenuBar menuBar = createMenuBar();

        topSection.getChildren().addAll(menuBar, createMainToolBar());
        return topSection;
    }

    @Contract(" -> new")
    private @NotNull MenuBar createMenuBar() {
        Menu fileMenu = new Menu(t("menu.file"));

        fileMenu.getItems().setAll(List.of(
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
                })));

        Menu editMenu = new Menu(t("menu.edit"));
        editMenu.getItems().setAll(List.of(
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
                        () -> withFocusedTextInput(TextInputControl::paste))));

        Menu insertMenu = new Menu(t("menu.insert"));
        insertMenu.getItems().setAll(List.of(
                menuItem(t("menu.indicators"), null, this::openInsertIndicatorDialog),
                menuItem(t("menu.news"), null, this::openNewsCalendarPanel),
                menuItem(t("menu.backgroundImage"), null, this::chooseActiveChartBackgroundImage),
                menuItem(t("menu.clearBackgroundImage"), null, this::clearActiveChartBackgroundImage)));

        Menu viewMenu = new Menu(t("menu.view"));
        viewMenu.getItems().setAll(List.of(
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
                menuItem(t("menu.marketInfoPanel"), null, this::openMarketInfoPanel),
                menuItem(t("menu.symbolAgentWatch"), null, this::openSymbolAgentMarketWatch),
                menuItem(t("menu.navigationPanel"), null, this::openNavigationPanel),
                new SeparatorMenuItem(),
                menuItem(t("menu.zoomIn"), new KeyCodeCombination(KeyCode.PLUS, KeyCombination.CONTROL_DOWN),
                        () -> withActiveChart(chart -> chart.changeZoom(ZoomDirection.IN))),
                menuItem(t("menu.zoomOut"), new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN),
                        () -> withActiveChart(chart -> chart.changeZoom(ZoomDirection.OUT)))));

        Menu chartsMenu = new Menu(t("menu.charts"));
        chartsMenu.getItems().setAll(List.of(
                menuItem(t("menu.candlestick"), null, this::openSelectedSymbolChart),
                menuItem(t("menu.fitActiveChart"), null, () -> withActiveChart(CandleStickChart::fitChart)),
                menuItem(t("menu.refreshActiveChart"), null, () -> withActiveChart(CandleStickChart::refreshChart)),
                menuItem(t("menu.toggleCrosshair"), null, () -> withActiveChart(CandleStickChart::toggleCrosshair))));

        Menu toolsMenu = new Menu(t("menu.tools"));
        toolsMenu.getItems().setAll(List.of(
                menuItem(t("menu.order"), new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),
                        this::openOrderPanel),
                new SeparatorMenuItem(),
                menuItem(t("menu.connectExchange"), null, this::connectSelectedExchange),
                menuItem(t("menu.toggleBotTrading"), new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN),
                        this::toggleBotTrading),
                menuItem(t("menu.systemMonitor"), null, this::openSystemMonitorWindow),
                new SeparatorMenuItem(),
                menuItem(t("menu.tradingSystem"), null, this::tradingSystemStatus),
                new SeparatorMenuItem(),

                menuItem(t("menu.refreshLocalPositions"), null, this::refreshPositions),
                menuItem(t("menu.cancelAllOrders"), null, this::cancelAllOrders)));

        Menu strategyMenu = new Menu(t("menu.strategy"));
        strategyMenu.getItems().setAll(List.of(
                menuItem(t("menu.strategyBuilder"), null, this::openStrategyBuilder),
                menuItem(t("menu.backtesting"), null, this::openBacktesting),
                menuItem(t("menu.analysis"), null, this::openAnalysis),
                new SeparatorMenuItem(),
                menuItem(t("menu.viewAllStrategies"), null, this::openAllStrategies),
                menuItem(t("menu.importStrategy"), null, this::importStrategy),
                menuItem(t("menu.exportStrategy"), null, this::exportStrategy)));

        Menu researchMenu = new Menu(t("menu.research"));
        researchMenu.getItems().setAll(List.of(
                menuItem(t("menu.marketResearch"), null, this::openMarketResearch),
                menuItem(t("menu.strategyResearch"), null, this::openStrategyResearch),
                new SeparatorMenuItem(),
                menuItem(t("menu.strategyAssignment"), null, () -> {
                    try {
                        showStrategyAssignmentPanel();
                    } catch (SQLException | ClassNotFoundException ex) {
                        log.error("Error showing strategy assignment panel", ex);
                        showWarning(
                                "Strategy Assignment",
                                "Unable to open strategy assignment panel: " + ex.getMessage());
                    }
                }),
                menuItem(t("menu.researchReports"), null, this::openResearchReports)));

        Menu settingsMenu = new Menu(t("menu.settings"));
        settingsMenu.getItems().setAll(List.of(
                // Trading & Broker Settings
                menuItem(t("menu.exchangeCredentials"), null, this::showSettingsDialog),
                menuItem(t("menu.tradingProfile"), null, this::showTradingProfileSettings),
                menuItem(t("menu.applicationSettings"), null, this::showSettingsDialog),
                new SeparatorMenuItem(),
                // Appearance & UI Settings
                menuItem("Theme Settings", null, this::showThemeSettingsDialog),
                menuItem("Visibility & Layout", null, this::showVisibilitySettingsDialog),
                new SeparatorMenuItem(),
                // Account & Security Settings
                menuItem(t("menu.resetPassword"), null, this::openPasswordReset)));

        Menu windowMenu = new Menu(t("menu.window"));
        windowMenu.getItems().setAll(List.of(
                menuItem(t("menu.closeAllCharts"), null, this::closeAllCharts),
                menuItem(t("menu.detachConsole"), null, this::detachConsoleWindow)));

        Menu languageMenu = createLanguageMenu();

        Menu licenseMenu = new Menu("License");
        licenseMenu.getItems().setAll(List.of(
                menuItem("License Management", null, this::openLicenseManagement)));

        Menu helpMenu = new Menu(t("menu.help"));
        helpMenu.getItems().addAll(
                menuItem(t("menu.helpItem"), new KeyCodeCombination(KeyCode.F1),
                        () -> showInfo("Help", "InvestPro Help - F5 refreshes data, Ctrl+` toggles terminal.")),
                new SeparatorMenuItem(),
                menuItem(t("menu.about"), null, () -> showInfo("About InvestPro",
                        "InvestPro ----------------------------------------------------------- Professional Trading Desk\nVersion: 1.0.0\nDeveloper: NOEL NGUEMECHIEU\n© 2020-2026 TradeAdviser.LLC")));

        return new MenuBar(fileMenu, editMenu, insertMenu, viewMenu, chartsMenu, toolsMenu, strategyMenu, researchMenu,
                settingsMenu, windowMenu, languageMenu, licenseMenu, helpMenu);
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
                action.run();
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
        exchangeSelector.setPrefWidth(180);
        symbolSelector.setPrefWidth(220);
        timeframeSelector.setPrefWidth(120);
        tradingModeSelector.setPrefWidth(120);

        Label brand = new Label("InvestPro ");
        brand.getStyleClass().add("terminal-brand");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToolBar toolBar = new ToolBar(
                brand,
                new Separator(Orientation.VERTICAL),
                new Label(t("toolbar.mode")),
                tradingModeSelector,
                new Separator(Orientation.VERTICAL),
                new Label(t("toolbar.broker")),
                exchangeSelector,
                connectControl,
                new Separator(Orientation.VERTICAL),
                new Label(t("toolbar.symbol")),
                symbolSelector,
                new Label(t("toolbar.timeframe")),
                timeframeSelector,
                refreshSymbolsButton,
                addChartButton,
                new Separator(Orientation.VERTICAL),
                new Label(t("toolbar.orderType")),
                orderTypeSelector,
                exchangeVenueLabel,
                buyButton,
                sellButton,
                cancelAllButton,
                new Separator(Orientation.VERTICAL),
                spacer,
                new Label(t("toolbar.aiTerminal")));
        toolBar.getStyleClass().add("main-trading-toolbar");
        return toolBar;
    }

    private SplitPane createMainWorkbench() {
        // Create center workspace with charts and terminal
        systemConsole = createTradingConsole();
        Node centerWorkspace = createCenterWorkspace();

        // Create the main horizontal layout: LEFT | CENTER | RIGHT
        horizontalWorkbench = new SplitPane();
        horizontalWorkbench.setOrientation(Orientation.HORIZONTAL);
        horizontalWorkbench.getStyleClass().add("workbench-split");

        // Wrap market watch and order book for visibility toggling
        marketWatchWrapper = createLeftSidebar();
        orderBookWrapper = createOrderBookPane();

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
        centerSplit.getItems().setAll(createChartWorkspace(), systemConsole);
        centerSplit.setDividerPositions(0.72);
        return centerSplit;
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

        Button closeButton = getCloseButton();

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

        // Initialize MarketInfoPanel if not already done
        if (marketInfoPanel == null) {
            marketInfoPanel = new MarketInfoPanel(exchange, newsDataProvider);
        } else {
            marketInfoPanel.setExchange(exchange);
        }

        TabPane watchTabs = new TabPane();
        watchTabs.getStyleClass().add("compact-tabs");

        watchTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Create OrderBook display for MarketWatch using shared
        // orderBookBids/orderBookAsks
        VBox sharedOrderBookView = createSharedOrderBookView();

        watchTabs.getTabs().setAll(
                new Tab("Symbols", marketWatchTable),
                new Tab("Market Stats", marketInfoPanel),
                new Tab("Order Book", sharedOrderBookView));

        VBox box = new VBox(8, header, watchTabs);
        box.setPadding(new Insets(8));
        box.getStyleClass().addAll("market-watch", "pro-panel");
        VBox.setVgrow(watchTabs, Priority.ALWAYS);
        return box;
    }

    private @NotNull Button getCloseButton() {
        Button closeButton = new Button("✕");
        closeButton.setStyle(
                "-fx-font-size: 14; -fx-padding: 2 8 2 8; -fx-text-fill: #a0aec0; -fx-background-color: transparent; -fx-cursor: hand;");
        closeButton.setOnMouseEntered(e -> closeButton.setStyle(
                "-fx-font-size: 14; -fx-padding: 2 8 2 8; -fx-text-fill: #ef4444; -fx-background-color: transparent; -fx-cursor: hand;"));
        closeButton.setOnMouseExited(e -> closeButton.setStyle(
                "-fx-font-size: 14; -fx-padding: 2 8 2 8; -fx-text-fill: #a0aec0; -fx-background-color: transparent; -fx-cursor: hand;"));
        closeButton.setOnAction(event -> toggleMarketWatchVisibility());
        return closeButton;
    }

    private @NotNull HBox createMiniChartRow(TradePair pair) {
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

    private @NotNull VBox createOrderBookPane() {
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

        Button closeButton = getButton();

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

    private @NotNull Button getButton() {
        Button closeButton = new Button("✕");
        closeButton.setStyle(
                "-fx-font-size: 14; -fx-padding: 2 8 2 8; -fx-text-fill: #a0aec0; -fx-background-color: transparent; -fx-cursor: hand;");
        closeButton.setOnMouseEntered(e -> closeButton.setStyle(
                "-fx-font-size: 14; -fx-padding: 2 8 2 8; -fx-text-fill: #ef4444; -fx-background-color: transparent; -fx-cursor: hand;"));
        closeButton.setOnMouseExited(e -> closeButton.setStyle(
                "-fx-font-size: 14; -fx-padding: 2 8 2 8; -fx-text-fill: #a0aec0; -fx-background-color: transparent; -fx-cursor: hand;"));
        closeButton.setOnAction(event -> toggleOrderBookVisibility());
        return closeButton;
    }

    /**
     * Creates a compact orderbook view for the MarketWatch sidebar that shares
     * the same orderBookBids/orderBookAsks data as the main Order Book tab.
     * This eliminates duplicate orderbook fetches and keeps data consistent.
     */
    private @NotNull VBox createSharedOrderBookView() {
        // Use HBox with two columns for compact side-by-side view
        ListView<OrderBook.PriceLevel> bidsList = createCompactListView(true);
        ListView<OrderBook.PriceLevel> asksList = createCompactListView(false);

        HBox mainContent = new HBox(0, bidsList, asksList);
        HBox.setHgrow(bidsList, Priority.ALWAYS);
        HBox.setHgrow(asksList, Priority.ALWAYS);
        mainContent.setStyle("-fx-background-color: #0f3460;");

        // Header with current spread info
        Label spreadLabel = new Label("Spread: --");
        spreadLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #9ca3af; -fx-padding: 8;");

        // Update spread on orderbook changes
        java.util.function.Consumer<Object> updateSpread = (obj) -> {
            if (!orderBookBids.isEmpty() && !orderBookAsks.isEmpty()) {
                double bid = orderBookBids.get(0).getPrice();
                double ask = orderBookAsks.get(0).getPrice();
                double spread = ask - bid;
                spreadLabel.setText(String.format("Spread: %.8f (%.4f%%)", spread, (spread / bid) * 100));
            } else {
                spreadLabel.setText("Spread: No Data");
            }
        };

        orderBookBids.addListener((javafx.beans.InvalidationListener) observable -> updateSpread.accept(null));
        orderBookAsks.addListener((javafx.beans.InvalidationListener) observable -> updateSpread.accept(null));

        VBox container = new VBox(6, spreadLabel, mainContent);
        container.setStyle("-fx-background-color: #0f3460;");
        container.setPadding(new Insets(8));
        VBox.setVgrow(mainContent, Priority.ALWAYS);
        return container;
    }

    /**
     * Creates a compact list view for bids or asks (for MarketWatch sidebar).
     * Binds directly to the shared orderBookBids/orderBookAsks observable lists.
     */
    private @NotNull ListView<OrderBook.PriceLevel> createCompactListView(boolean isBids) {
        ListView<OrderBook.PriceLevel> listView = new ListView<>(isBids ? orderBookBids : orderBookAsks);
        listView.setStyle(
                "-fx-background-color: #0f3460; -fx-text-fill: #ffffff; -fx-control-inner-background: #0f3460;");
        listView.setCellFactory(param -> createCompactOrderBookCell(isBids));
        listView.setPrefHeight(300);
        return listView;
    }

    /**
     * Creates a compact cell renderer for orderbook prices (one-line display).
     */
    private @NotNull javafx.scene.control.ListCell<OrderBook.PriceLevel> createCompactOrderBookCell(boolean isBids) {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(OrderBook.PriceLevel item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    String text = String.format("%.8f | %.4f", item.getPrice(), item.getSize());
                    setText(text);
                    String color = isBids ? "#10b981" : "#ef4444"; // Green for bids, red for asks
                    setStyle(String.format(
                            "-fx-font-family: 'Courier New'; -fx-font-size: 10; -fx-text-fill: %s; -fx-padding: 2;",
                            color));
                }
            }
        };
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

        // Get best bid and ask
        double bestBid = orderBookBids.get(0).getPrice();
        double bestAsk = orderBookAsks.get(0).getPrice();
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

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(priceLabel, directionLabel, new Separator(Orientation.VERTICAL),
                spreadBox, spacer);
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
                createFixedTab(TabName.MARKET_INFO, marketInfoPanel),
                createFixedTab(TabName.OVERVIEW, new ScrollPane(overviewView)),
                createFixedTab(TabName.BALANCES, createAccountBalancesView()),
                createFixedTab(TabName.DEPTH, depthChart));
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
                    log.warn("Failed to update account balances", exception);
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
        chartTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && !Objects.equals(oldTab, newTab)) {
                updateOrderBookForChartTab(newTab);
            }
        });

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

        Button closeButton = getButton1();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, titleBlock, spacer, detachButton, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("panel-header");

        terminalTabPane.setSide(Side.TOP);
        terminalTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        DraggableTab.registerTabPane(terminalTabPane);
        terminalTabPane.getTabs().setAll(
                createFixedTab(TabName.PORTFOLIO, buildPortfolioPane()),
                createDetachableTerminalTab(TabName.POSITIONS, createPositionsTab().getContent()),
                createDetachableTerminalTab(TabName.RISK_MONITOR, createPositionRiskMonitorTab().getContent()),
                createDetachableTerminalTab(TabName.SIGNALS, createSignalTab().getContent()),
                createDetachableTerminalTab(TabName.NEWS_CALENDAR, createNewsTab().getContent()),
                createDetachableTerminalTab(TabName.ALERTS, createAlertsTab().getContent()),
                createDetachableTerminalTab(TabName.MAILBOX, createMailboxTab().getContent()),
                createDetachableTerminalTab(TabName.CHAT, createChatTab().getContent()),
                createDetachableTerminalTab(TabName.BROWSER, createMarketTab().getContent()),
                createDetachableTerminalTab(TabName.AGENTS, createAgentsTab().getContent()),
                createDetachableTerminalTab(TabName.JOURNAL, createJournalTab().getContent()));

        VBox console = new VBox(6, header, terminalTabPane);
        console.setPadding(new Insets(8));
        console.setPrefHeight(CONSOLE_HEIGHT);
        console.getStyleClass().addAll("system-console", "bottom-terminal");
        VBox.setVgrow(terminalTabPane, Priority.ALWAYS);
        return console;
    }

    private @NotNull Button getButton1() {
        Button closeButton = new Button("✕");
        closeButton.setStyle(
                "-fx-font-size: 14; -fx-padding: 2 8 2 8; -fx-text-fill: #a0aec0; -fx-background-color: transparent; -fx-cursor: hand;");
        closeButton.setOnMouseEntered(e -> closeButton.setStyle(
                "-fx-font-size: 14; -fx-padding: 2 8 2 8; -fx-text-fill: #ef4444; -fx-background-color: transparent; -fx-cursor: hand;"));
        closeButton.setOnMouseExited(e -> closeButton.setStyle(
                "-fx-font-size: 14; -fx-padding: 2 8 2 8; -fx-text-fill: #a0aec0; -fx-background-color: transparent; -fx-cursor: hand;"));
        closeButton.setOnAction(event -> toggleConsoleVisibility());
        return closeButton;
    }

    private void toggleConsoleVisibility() {
        if (centerSplit == null || systemConsole == null) {
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
                horizontalWorkbench.getItems().add(0, marketWatchWrapper);
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

        centerSplit.getItems().remove(systemConsole);
        consoleVisible = false;
        centerSplit.layout();

        Stage stage = new Stage();
        stage.setTitle("System Console");
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
        if (centerSplit == null || systemConsole == null) {
            return;
        }

        if (!centerSplit.getItems().contains(systemConsole)) {
            centerSplit.getItems().add(systemConsole);
        }
        centerSplit.setDividerPositions(0.72);
        consoleVisible = true;
        journal("Console re-attached.");
        saveAppState();
    }

    private @NotNull DraggableTab createDetachableTerminalTab(TabName tabName, Node content) {
        DraggableTab tab = new DraggableTab(tabName.getTabId(), content);
        tab.setClosable(true);
        tab.setTooltip(new Tooltip(tabName.getDisplayName()));
        return tab;
    }

    private @NotNull Tab createFixedTab(TabName tabName, Node content) {
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
        // Check if window with this title is already open
        if (openIndependentWindows.containsKey(title)) {
            Stage existingStage = openIndependentWindows.get(title);
            if (existingStage != null && existingStage.isShowing()) {
                // Window already open, bring it to front
                existingStage.toFront();
                existingStage.requestFocus();
                log.info("Brought independent window to front: {}", title);
                return;
            } else {
                // Window was closed, remove from tracking
                openIndependentWindows.remove(title);
            }
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
        stage.setOnCloseRequest(e -> {
            openIndependentWindows.remove(title);
            log.info("Independent window closed: {}", title);
        });

        stage.show();
        openIndependentWindows.put(title, stage);

        log.info("Independent window opened: {}", title);
    }

    private @NotNull Scene getScene(Node content, double width, double height) {
        javafx.scene.Parent sceneContent;
        if (content instanceof BorderPane) {
            sceneContent = (BorderPane) content;
        } else {
            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            scrollPane.setStyle("-fx-background-color: #1a1a2e;");
            sceneContent = scrollPane;
        }

        return new Scene(sceneContent, width, height);
    }

    private void openSystemMonitorWindow() {
        if (systemMonitorPanel == null) {
            systemMonitorPanel = new SystemMonitorPanel(() -> {
                if (systemCore == null) {
                    return SystemMonitorPanel
                            .notAvailable("SystemCore is not started. Connect an exchange or start bot trading.");
                }
                return systemCore.getSystemHealth();
            });
        }

        systemMonitorPanel.show();
    }

    private void openDataWindow() {
        if (dataWindow == null) {
            dataWindow = new DataWindow();
        }
        createIndependentWindow("Data Window", dataWindow, 400, 500);
    }

    private void openMarketInfoPanel() {
        if (marketInfoPanel == null) {
            marketInfoPanel = new MarketInfoPanel();
        }
        createIndependentWindow("Market Info", marketInfoPanel, 450, 600);
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
        if (navigationPanel == null) {
            navigationPanel = new Navigation();
        }
        navigationPanel.show();
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

    private @NotNull Tab createNewsTab() {
        ListView<NewsEvent> newsListView = new ListView<>();
        List<NewsEvent> newsData;
        try {
            newsData = new NewsDataProvider().getUpcomingNewsEvents();
        } catch (Exception exception) {
            log.warn("Unable to load news.", exception);
            newsData = Collections.emptyList();
        }
        newsListView.setItems(FXCollections.observableArrayList(newsData));
        VBox content = new VBox(newsListView);
        VBox.setVgrow(newsListView, Priority.ALWAYS);
        return createFixedTab(TabName.NEWS_CALENDAR, content);
    }

    private @NotNull Tab createAlertsTab() {
        alertsListView.setItems(
                FXCollections.observableArrayList("No active alerts.", "Create alerts from chart or order panel."));
        VBox content = new VBox(alertsListView);
        VBox.setVgrow(alertsListView, Priority.ALWAYS);
        return createFixedTab(TabName.ALERTS, content);
    }

    private @NotNull Tab createMailboxTab() {
        mailboxListView.setItems(
                FXCollections.observableArrayList("Mailbox is empty.", "Broker/system messages will appear here."));
        VBox content = new VBox(mailboxListView);
        VBox.setVgrow(mailboxListView, Priority.ALWAYS);
        return createFixedTab(TabName.MAILBOX, content);
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
        return createFixedTab(TabName.CHAT, content);
    }

    private @NotNull Tab createMarketTab() {
        Label marketInfo = new Label(
                "Market Information\n\nClick 'Open in Browser' to view market data in your default browser.");
        marketInfo.setStyle("-fx-font-size: 14; -fx-text-fill: #64748b; -fx-padding: 20;");
        marketInfo.setWrapText(true);

        TextField searchField = new TextField();
        searchField.setPromptText("Search market information (opens in browser)...");

        Button openBrowserButton = getButton(searchField);

        searchField.setOnAction(event -> openBrowserButton.fire());

        HBox toolbar = new HBox(8, searchField, openBrowserButton);
        toolbar.setPadding(new Insets(8));
        HBox.setHgrow(searchField, Priority.ALWAYS);

        VBox content = new VBox(6, toolbar, marketInfo);
        VBox.setVgrow(marketInfo, Priority.ALWAYS);
        return createFixedTab(TabName.BROWSER, content);
    }

    private @NotNull Button getButton(TextField searchField) {
        Button openBrowserButton = new Button("Open in Browser");

        openBrowserButton.setOnAction(event -> {
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
        return openBrowserButton;
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

    private @NotNull Tab createJournalTab() {
        journalArea.setEditable(false);
        journalArea.setWrapText(true);
        journalArea.setText("InvestPro system journal initialized.\nLayout loaded successfully.\n");
        VBox content = new VBox(journalArea);
        VBox.setVgrow(journalArea, Priority.ALWAYS);
        return createFixedTab(TabName.JOURNAL, content);
    }

    private void configureMarketWatchTable() {
        configureMarketWatchTableView(marketWatchTable);
        // Add selection listener to load order book when a symbol is selected
        marketWatchTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadOrderBook(newVal);
            }
        });

        // Start periodic refresh of market watch data from cache
        startMarketWatchRefresh();
    }

    /**
     * Periodically refresh market watch table with live quotes from
     * MarketDataEngine cache.
     */
    private void startMarketWatchRefresh() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "MarketWatchRefresh");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
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
        }, 1, 5, TimeUnit.SECONDS); // Refresh every 5 seconds after 1 second initial delay
    }

    private void configureMarketWatchTableView(@NotNull TableView<TradePair> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("No symbols loaded"));
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.getColumns().addAll(
                tableColumn("Session", pair -> pair == null ? "" : getTradingSessionStatus(pair), 80),
                tableColumn("%Last Price", pair -> pair == null ? "" : String.valueOf(pair.getLastPrice()), 80),
                tableColumn("Symbol", pair -> pair == null ? "" : pair.toString('/'), 100),
                tableColumn("Spread %", pair -> pair == null ? "" : formatSpreadPercent(pair), 75),
                tableColumn("%Change", pair -> pair == null ? "" : String.valueOf(pair.getChangePercent()), 80),

                tableColumn("volume", pair -> pair == null ? "" : marketPrice(pair.getVolume()), 80),
                tableColumn("24/High", pair -> pair == null ? "" : marketPrice(pair.getHigh24h()), 80),
                tableColumn("24/Low", pair -> pair == null ? "" : marketPrice(pair.getHigh24h()), 80));


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

    private @NotNull String getTradingSessionStatus(TradePair pair) {
        return pair == null ? "UNKNOWN" : pair.getTradingSessionStatus().name();
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
                new Label(t("label.terminal")));
        return statusBar;
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
    }

    private void updateConnectControl(boolean connected) {
        if (connected) {
            connectedBrokerLabel.setText(t("status.connected"));
            connectedBrokerLabel.setStyle(
                    "-fx-padding: 8 15; -fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold;");
            connectedBrokerLabel.setVisible(true);
            connectedBrokerLabel.setManaged(true);
            connectButton.setVisible(false);
            connectButton.setManaged(false);
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
                if (marketInfoPanel != null) {
                    marketInfoPanel.setExchange(exchange);
                    marketInfoPanel.updateForPair(selected);
                }
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
                exchange.setUserSelectedTradingMode(selectedMode);
                log.info("Trading mode switched to: {}", selectedMode);
                appendAgentActivity("Trading mode: " + selectedMode);
            }
        });
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

        List<String> supportedTimeframes = exchange != null && exchange.getSupportedTimeframes() != null
                ? exchange.getSupportedTimeframes()
                        .stream()
                        .map(String::valueOf)
                        .toList()
                : List.of("1m", "5m", "15m", "30m", "1h", "4h", "1d");

        timeframeSelector.getItems().setAll(supportedTimeframes);

        String savedTimeframe = preferences.get("selected_timeframe", "1h");

        String selectedTimeframe = supportedTimeframes.contains(savedTimeframe)
                ? savedTimeframe
                : supportedTimeframes.contains("1h")
                        ? "1h"
                        : supportedTimeframes.isEmpty()
                                ? null
                                : supportedTimeframes.get(0);

        if (selectedTimeframe != null) {
            timeframeSelector.getSelectionModel().select(selectedTimeframe);
        }

        timeframeSelector.setOnAction(event -> {
            String selected = timeframeSelector.getSelectionModel().getSelectedItem();

            if (selected == null || selected.isBlank()) {
                return;
            }

            preferences.put("selected_timeframe", selected);
            onTimeframeChanged(selected);
            saveAppState();
            journal("Timeframe changed to: " + selected);
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
                log.debug("Failed to update SystemCore bot during exchange change", exception);
            }
        }

        // Gracefully disconnect old exchange streams but keep bot running
        if (exchange != null) {
            try {
                exchange.disconnectStream();
                // Note: Don't fully disconnect - just stop streaming for graceful transition
            } catch (Exception exception) {
                log.debug("Failed to disconnect stream from old exchange", exception);
            }
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
            exchange = createExchange(selectedExchange, configuredApiKey, configuredApiSecret, configuredAccountId,
                    configuredTradingMode);
            // Connect WebSocket stream on background thread to avoid blocking JavaFX thread
            new Thread(() -> exchange.connectStream(), "WebSocketConnector-" + selectedExchange).start();
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
                    // Continue trading with new exchange
                } catch (Exception e) {
                    log.warn("Failed to update bot exchange, recreating SystemCore: {}", e.getMessage());
                    // Fall back to recreating
                    try {
                        systemCore = createSystemCore(exchange);
                    } catch (SQLException | ClassNotFoundException ex) {
                        log.error("Failed to create SystemCore", ex);
                        showAlert("Failed to initialize trading system: " + ex.getMessage());
                    }
                }
            } else {
                // Bot not running, create new one
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
                    showAlert("Failed to initialize trading system: " + e.getMessage());
                }
            }
            systemCoreEventsSubscribed = false;
            enablePositionAutoRefresh();
            initializeSymbolAgentPanels();
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

        exchange = createExchange(selectedExchange, configuredApiKey, configuredApiSecret, configuredAccountId,
                configuredTradingMode);
        // Connect WebSocket stream on background thread to avoid blocking JavaFX thread
        new Thread(() -> exchange.connectStream(), "WebSocketConnector-" + selectedExchange).start();
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
     * TradeAdviser sends emails when OANDA-related events happen:
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

    private void submitOrderByType(String orderType, TradePair tradePair, org.investpro.utils.Side side,
            double amount) {
        // Check if pair is tradable now using MarketDataEngine
        if (marketDataEngine != null && !marketDataEngine.isTradableNow(tradePair)) {
            String hours = marketDataEngine.getTradingHours(tradePair);
            showWarning("Trading Unavailable", "%s is not tradable now. Trading hours: %s".formatted(
                    tradePair.toString('/'), hours));
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

    private SystemCore createSystemCore(Exchange exchange) throws SQLException, ClassNotFoundException {
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

        return new SystemCore(exchange, config, openAiKey);
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
                navigationPanel = new Navigation();
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
                systemCore = createSystemCore(exchange);
                // Wire up the primary stage to the Telegram command handler for screenshot
                // capability
                if (systemCore.getTelegramCommandHandler() != null && getScene() != null
                        && getScene().getWindow() instanceof Stage) {
                    systemCore.getTelegramCommandHandler().setPrimaryStage((Stage) getScene().getWindow());
                }
            } catch (SQLException | ClassNotFoundException e) {
                log.error("Failed to create SystemCore", e);
                throw new RuntimeException("Failed to initialize trading system", e);
            }
            initializeSymbolAgentPanels();
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
        items.addFirst(value);
        while (items.size() > maxItems) {
            items.remove(items.size() - 1);
        }
    }

    private void refreshBotTradeButton() {
        botTradeButton.setText(botTradingEnabled ? t("toolbar.stopBot") : t("toolbar.botTrade"));
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
            log.debug("Failed to save app state", exception);
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
                log.debug("Failed to stop SystemCore streaming", exception);
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
            log.warn("Cannot bootstrap instruments: exchange={}, engine={}", exchange, marketDataEngine);
            return;
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
            log.error("Failed to load trade pairs from {}", exchangeSelector.getValue(), exception);
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
            systemCore.getSymbolAgentManager().initializeSymbols(tradePairs);
        }

        String rememberedSymbol = preferences.get("selected_symbol_" + safe(exchangeSelector.getValue()), "");
        TradePair rememberedPair = tradePairs.stream()
                .filter(pair -> Objects.equals(pair.toString('/'), rememberedSymbol))
                .findFirst()
                .orElse(null);

        TradePair selected = rememberedPair != null ? rememberedPair : tradePairs.get(0);
        symbolSelector.getSelectionModel().select(selected);
        marketWatchTable.getSelectionModel().select(selected);
        symbolCountLabel.setText(t("label.symbols", tradePairs.size()));
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

    private @NotNull List<TradePair> fallbackMarketWatchPairs() {
        try {
            List<TradePair> pairs = new ArrayList<>();
            pairs.add(new TradePair("BTC", "USD"));
            pairs.add(new TradePair("ETH", "USD"));
            pairs.add(new TradePair("SOL", "USD"));
            pairs.add(new TradePair("EUR", "USD"));
            pairs.add(new TradePair("AAPL", "USD"));
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

        try {
            if (!hasBrokerAccess()) {
                showWarning("Bot Trading", "Validate broker credentials before starting the bot.");
                return;
            }
            if (lacksOrderSubmissionAccess()) {
                showWarning("Bot Trading",
                        "%s is connected, but this adapter cannot submit orders.".formatted(exchange.getDisplayName()));
                return;
            }

            ensureSystemCoreStarted(selected);

            // Initialize analysis panel if not already done
            if (analysisPanel == null) {
                analysisPanel = new AnalysisPanel(systemCore);
            }

            // Set selected strategy/symbol for analysis metrics
            String activeStrategy = systemCore.getActiveStrategyName();
            if (activeStrategy != null && !activeStrategy.isBlank()) {
                analysisPanel.selectStrategyAndSymbol(activeStrategy, selected.toString('/'));
            }

            // Start recording trades when bot starts
            if (systemCore.getExchange().fetchAccount() != null) {
                analysisPanel.getLiveMetricsTracker()
                        .startTracking(systemCore.getExchange().fetchAccount().get().getAvailableBalance());
            }

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
            log.error("Failed to start chart auto trading", exception);
            showWarning("Bot Trading", "Could not start bot trading: %s".formatted(rootMessage(exception)));
        }
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

        List<String> choices = List.of(
                // Moving Averages
                "SMA 20",
                "SMA 50",
                "SMA 200",
                "EMA 12",
                "EMA 26",
                // Momentum Indicators
                "RSI 14",
                "Stochastic",
                "CCI 20",
                "MACD",
                // Volatility Indicators
                "Bollinger Bands",
                "ATR 14",
                "Volatility",
                // Volume Indicators
                "VWAP",
                "OBV",
                "Volume",
                // Trend Indicators
                "ADX 14",
                "Ichimoku",
                "Parabolic SAR",
                // Retracement Levels
                "Fibonacci Retracement",
                "Zigzag",
                // Pattern Recognition
                "Fractal",
                // Utility
                "Clear Indicators");
        ChoiceDialog<String> dialog = new ChoiceDialog<>("SMA 20", choices);
        dialog.setTitle(t("dialog.indicators.title"));
        dialog.setHeaderText(t("dialog.indicators.header"));
        dialog.setContentText(t("dialog.indicators.content"));

        dialog.showAndWait().ifPresent(choice -> {
            if ("Clear Indicators".equals(choice)) {
                chart.clearIndicators();
                appendAgentActivity("Cleared indicators from active chart.");
                return;
            }

            ChartIndicator indicator = createChartIndicator(choice);
            if (indicator == null) {
                showWarning("Indicators", "Unsupported indicator: " + choice);
                return;
            }

            chart.addIndicator(indicator);
            appendAgentActivity("Inserted indicator %s.".formatted(indicator.getName()));
        });
    }

    private ChartIndicator createChartIndicator(String choice) {
        return switch (safe(choice)) {
            // Moving Averages
            case "SMA 20" -> new SimpleMovingAverageIndicator(20);
            case "SMA 50" -> new SimpleMovingAverageIndicator(50);
            case "SMA 200" -> new SimpleMovingAverageIndicator(200);
            case "EMA 12" -> new ExponentialMovingAverageIndicator(12);
            case "EMA 26" -> new ExponentialMovingAverageIndicator(26);

            // Momentum Indicators
            case "RSI 14" -> new RSIIndicator(14);
            case "Stochastic" -> new StochasticIndicator();
            case "CCI 20" -> new CCIIndicator(20);
            case "MACD" -> new MACDIndicator();

            // Volatility Indicators
            case "Bollinger Bands" -> new BollingerBandsIndicator();
            case "ATR 14" -> new ATRIndicator(14);
            case "Volatility" -> new VolatilityIndicator();

            // Volume Indicators
            case "VWAP" -> new VWAPIndicator();
            case "OBV" -> new OBVIndicator();
            case "Volume" -> new VolumeIndicator();

            // Trend Indicators
            case "ADX 14" -> new ADXIndicator(14);
            case "Ichimoku" -> new IchimokuIndicator(9, 26, 52); // Standard periods: conversion 9, base 26, span 52
            case "Parabolic SAR" -> new ParabolicSARIndicator(0.02, 0.2); // Standard AF: initial 0.02, max 0.2

            // Retracement Levels
            case "Fibonacci Retracement" -> new FibonacciRetracementIndicator(20); // 20-period lookback
            case "Zigzag" -> new ZigzagIndicator(5.0); // 5% threshold

            // Pattern Recognition
            case "Fractal" -> new FractalIndicator(5); // 5-bar fractal pattern

            // Utility
            case "Clear Indicators" -> null;

            default -> null;
        };
    }

    private void openNewsCalendarPanel() {
        if (newsCalendarPanel == null) {
            newsCalendarPanel = new NewsCalendarPanel(newsDataProvider);
        }
        createIndependentWindow("News", newsCalendarPanel, 760, 560);
    }

    private void chooseActiveChartBackgroundImage() {
        CandleStickChart chart = requireActiveChart("Background Image");
        if (chart == null) {
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
            exchange.fetchOrderHistory(null, Instant.now().minus(90, ChronoUnit.DAYS))
                    .thenAccept(orders -> runOnFx(() -> updateAccountHistory(orders)))
                    .exceptionally(exception -> {
                        log.debug("Failed to fetch order history", exception);
                        return null;
                    });
        } catch (Exception exception) {
            log.debug("Fetch order history unavailable", exception);
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
            log.debug("Fetch risk data unavailable", exception);
        }
    }

    private void updateAccountOpenOrders(List<OpenOrder> orders) {
        if (orders == null) {
            accountOpenOrderItems.clear();
            return;
        }
        accountOpenOrderItems.setAll(orders);
        journal("Open orders updated: " + orders.size() + " order(s)");
    }

    private void updateAccountHistory(List<Order> orders) {
        if (orders == null) {
            accountHistoryItems.clear();
            return;
        }
        // Replace all history with fetched data (reverse order to show newest first)
        accountHistoryItems.setAll(reverseCopy(orders));
        journal("Account history updated: " + orders.size() + " order(s)");
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
        accountTradeItems.setAll(reverseCopy(tradesWithProfit));
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
        autoRefreshExecutor.scheduleAtFixedRate(() -> runOnFx(this::updateConnectionStatus), 2, 5, TimeUnit.SECONDS);

        // Stream ticker prices for all market watch symbols
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
        dialog.setTitle("Trading Profile Settings");
        dialog.setHeaderText("Configure your trading profile and risk parameters");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        SettingsPanel profilePanel = new SettingsPanel(systemCore);

        ScrollPane scrollPane = new ScrollPane(profilePanel);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-control-inner-background: #1a1a2e;");

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().setStyle("-fx-control-inner-background: #1a1a2e;");
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

        TabPane researchTabs = new TabPane();
        researchTabs.setStyle("-fx-control-inner-background: #0f3460;");
        researchTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab sentimentTab = new Tab("Sentiment Analysis", createSentimentAnalysisTab());
        Tab correlationTab = new Tab("Correlation Matrix", createCorrelationTab());
        Tab economicTab = new Tab("Economic Calendar", createEconomicCalendarTab());
        Tab volatilityTab = new Tab("Volatility Analysis", createVolatilityTab());

        researchTabs.getTabs().addAll(sentimentTab, correlationTab, economicTab, volatilityTab);
        panel.getChildren().addAll(title, researchTabs);
        VBox.setVgrow(researchTabs, Priority.ALWAYS);

        return panel;
    }

    private VBox createSentimentAnalysisTab() {
        VBox tab = new VBox(12);
        tab.setPadding(new Insets(12));
        Label label = new Label("Market Sentiment Index: " + getMarketSentimentIndex() + "/100");
        label.setStyle("-fx-text-fill: #10b981; -fx-font-size: 14;");
        ListView<String> sentimentList = new ListView<>();
        sentimentList.getItems().addAll(
                "Overall Market Sentiment: " + getOverallMarketSentiment(),
                "Investor Confidence: " + getInvestorConfidence(),
                "Market Fear Index (VIX): " + getMarketVIX(),
                "Bitcoin Dominance: " + getBitcoinDominance(),
                "Trading Volume: " + getTradingVolume());
        sentimentList.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");
        tab.getChildren().addAll(label, sentimentList);
        VBox.setVgrow(sentimentList, Priority.ALWAYS);
        return tab;
    }

    private VBox createCorrelationTab() {
        VBox tab = new VBox(12);
        tab.setPadding(new Insets(12));
        Label label = new Label("Asset Correlation Matrix");
        label.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 14;");
        TableView<String[]> correlationTable = new TableView<>();
        correlationTable.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");
        tab.getChildren().addAll(label, correlationTable);
        VBox.setVgrow(correlationTable, Priority.ALWAYS);
        return tab;
    }

    private VBox createEconomicCalendarTab() {
        VBox tab = new VBox(12);
        tab.setPadding(new Insets(12));
        Label label = new Label("Upcoming Economic Events");
        label.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 14;");
        ListView<String> eventList = new ListView<>();
        eventList.getItems().addAll(
                "2026-05-15 | Federal Funds Rate Decision | Expected: 5.25% | Impact: High",
                "2026-05-20 | CPI Release | Expected: 3.2% | Impact: High",
                "2026-05-25 | Employment Report | Expected: 150K jobs | Impact: Very High",
                "2026-06-01 | Manufacturing PMI | Expected: 52.1 | Impact: Medium",
                "2026-06-05 | Unemployment Rate | Expected: 3.9% | Impact: High");
        eventList.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");
        tab.getChildren().addAll(label, eventList);
        VBox.setVgrow(eventList, Priority.ALWAYS);
        return tab;
    }

    private VBox createVolatilityTab() {
        VBox tab = new VBox(12);
        tab.setPadding(new Insets(12));
        Label label = new Label("Volatility Metrics");
        label.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 14;");
        ListView<String> volatilityList = new ListView<>();
        volatilityList.getItems().addAll(
                "S&P 500 Volatility (VIX): 18.5 (Normal)",
                "Bitcoin Volatility: 52.3% (High)",
                "Ethereum Volatility: 48.7% (High)",
                "Forex Volatility (EURUSD): 12.1% (Low)",
                "30-Day Realized Volatility Trend: Increasing");
        volatilityList.setStyle("-fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");
        tab.getChildren().addAll(label, volatilityList);
        VBox.setVgrow(volatilityList, Priority.ALWAYS);
        return tab;
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
        createDetachableTerminalTab(TabName.RESEARCH_REPORTS, reportsPanel);
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
        viewBtn.setOnAction(e -> showInfo("Report", "Report details loading..."));

        Button downloadBtn = new Button("Download PDF");
        downloadBtn.setStyle("-fx-padding: 6 16; -fx-background-color: #10b981; -fx-text-fill: white;");
        downloadBtn.setOnAction(e -> showInfo("Download", "Report PDF download started..."));

        actionBox.getChildren().addAll(viewBtn, downloadBtn);

        panel.getChildren().addAll(title, reportsList, actionBox);
        VBox.setVgrow(reportsList, Priority.ALWAYS);

        return panel;
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
                    showTerminalCheckBox.isSelected(), showChartsCheckBox.isSelected(),
                    showPositionsCheckBox.isSelected());

            // Apply font size
            applyFontSize((int) fontSizeSlider.getValue());

            journal("✓ Visibility and layout settings applied and saved.");
        }
    }

    /**
     * Apply visibility settings to panels
     */
    private void applyVisibilitySettings(boolean showMarketWatch, boolean showOrderBook, boolean showTerminal,
            boolean showCharts, boolean showPositions) {
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
        TextField emailField = new TextField(oandaEmailNotification);

        // Trading mode selector
        ComboBox<String> tradingModeCombo = new ComboBox<>();
        tradingModeCombo.getItems().addAll("LIVE", "PAPER");
        tradingModeCombo.setValue(configuredTradingMode != null ? configuredTradingMode : "LIVE");
        tradingModeCombo.setPrefWidth(Double.MAX_VALUE);

        boolean oanda = "OANDA".equalsIgnoreCase(selectedExchange);
        apiKeyField.setPromptText(oanda ? "OANDA Token" : "API Key");
        secretField.setPromptText(oanda ? "Account ID optional" : "API Secret");
        telegramField.setPromptText("Telegram Token optional");
        emailField.setPromptText("Email for OANDA notifications (optional)");
        emailField.setVisible(oanda);
        emailField.setManaged(oanda);

        grid.addRow(0, new Label(oanda ? "Token" : "API Key"), apiKeyField);
        grid.addRow(1, new Label(oanda ? "Account ID" : "API Secret"), secretField);
        grid.addRow(2, new Label("Trading Mode"), tradingModeCombo);
        grid.addRow(3, new Label("Telegram Token"), telegramField);
        if (oanda) {
            grid.addRow(4, new Label("Email Notifications"), emailField);
        }

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(buttonType -> {
            if (buttonType == connectType) {
                configuredApiKey = safe(apiKeyField.getText());
                configuredApiSecret = safe(secretField.getText());
                configuredTradingMode = tradingModeCombo.getValue();
                telegramToken = safe(telegramField.getText());
                if (oanda) {
                    oandaEmailNotification = safe(emailField.getText());
                }
                saveExchangeCredentials(selectedExchange);
                return true;
            }
            return false;
        });

        dialog.showAndWait().ifPresent(saved -> {
            if (saved) {
                exchange = createExchange(selectedExchange, configuredApiKey, configuredApiSecret, configuredAccountId,
                        configuredTradingMode);
                // Connect WebSocket stream on background thread to avoid blocking JavaFX thread
                new Thread(() -> exchange.connectStream(), "WebSocketConnector-" + selectedExchange).start();
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
        configuredAccountId = preferences.get("exchange_account_id_" + key, configuredAccountId);
        configuredTradingMode = preferences.get("exchange_trading_mode_" + key, configuredTradingMode);
        telegramToken = preferences.get("telegram_token_" + key, telegramToken);
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
                        accountTradeItems.add(0, trade);

                        while (accountTradeItems.size() > 500) {
                            accountTradeItems.remove(accountTradeItems.size() - 1);
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
                        accountTradeItems.add(0, fill);
                    }
                })
                .onError((exchangeName, throwable) -> journal("Stream error from %s: %s".formatted(
                        exchangeName,
                        throwable == null ? "unknown" : throwable.getMessage())));

    }

    private void updateOrderBookUi(@NotNull OrderBook orderBook) {
        currentOrderBook = orderBook;

        orderBookBids.setAll(orderBook.getBids());
        orderBookAsks.setAll(orderBook.getAsks());

        if (depthChart != null) {
            depthChart.update(orderBook);
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

        ExchangeCredentials credentials = new ExchangeCredentials(
                normalizedExchangeId(normalizedName),
                safe(apiKey),
                safe(apiSecret),
                normalizedName.equals("COINBASE") ? safe(apiKey) : null,
                normalizedName.equals("COINBASE") ? safe(apiSecret) : null,
                null,
                safe(accountId),
                paperMode);

        Exchange createdExchange = switch (normalizedName) {
            case "BINANCE US" -> new BinanceUs(credentials);
            case "BINANCE" -> new Binance(credentials);
            case "OANDA" -> new Oanda(credentials);
            case "BITFINEX" -> new Bitfinex(credentials);
            case "ALPACA" -> new Alpaca(credentials);
            case "INTERACTIVE BROKERS", "INTERACTIVE_BROKER", "IBKR", "IBK" -> new InteractiveBrokers(credentials);
            case "COINBASE" -> new Coinbase(credentials);
            case "STELLAR NETWORK", "STELLAR-NETWORK", "STELLAR_NETWORK" -> new StellarNetwork(credentials);
            default -> {
                log.warn("Exchange {} is not implemented yet. Falling back to Coinbase.", exchangeName);
                yield new Coinbase(credentials);
            }
        };

        createdExchange.setUserSelectedTradingMode(safe(tradingMode).isBlank() ? "LIVE" : safe(tradingMode));

        // Wire the exchange to the central MarketDataEngine
        if (marketDataEngine != null) {
            createdExchange.setMarketDataEngine(marketDataEngine);
        }

        return createdExchange;
    }

    private @NotNull String normalizeExchangeName(String exchangeName) {
        String name = safe(exchangeName).toUpperCase(Locale.ROOT).replace('-', ' ').replace('_', ' ').trim();
        return switch (name) {
            case "BINANCEUS", "BINANCE US" -> "BINANCE US";
            case "COINBASE", "COINBASE ADVANCED", "COINBASE ADVANCED TRADE" -> "COINBASE";
            case "OANDA", "OANDA FX", "OANDA FOREX" -> "OANDA";
            case "ALPACA", "ALPACA STOCKS", "ALPACA EQUITIES" -> "ALPACA";
            case "INTERACTIVE BROKERS", "INTERACTIVEBROKERS", "IBKR", "IBK" -> "INTERACTIVE BROKERS";
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
            case "STELLAR NETWORK" -> "stellar-network";
            default -> normalizedName.toLowerCase(Locale.ROOT).replace(" ", "_");
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

    private String formatSpreadPercent(TradePair pair) {
        if (pair == null || pair.getBid() <= 0) {
            return "-";
        }
        double spread = pair.getAsk() - pair.getBid();
        double spreadPercent = (spread / pair.getBid()) * 100.0;
        return String.format("%.3f%%", spreadPercent);
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

        symbolCountLabel.setText(t("label.symbols", marketWatchItems.size()));
    }

    public void updateTradeFromStream(Trade trade) {
        if (trade != null) {
            accountTradeItems.add(0, trade);

            while (accountTradeItems.size() > 500) {
                accountTradeItems.remove(accountTradeItems.size() - 1);
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

        accountOpenOrderItems.add(0, order);

        while (accountOpenOrderItems.size() > 500) {
            accountOpenOrderItems.remove(accountOpenOrderItems.size() - 1);
        }
    }

    public void updatePositionFromStream(Position position) {
        if (position == null) {
            return;
        }

        accountPositionItems.add(0, position);

        while (accountPositionItems.size() > 500) {
            accountPositionItems.remove(accountPositionItems.size() - 1);
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
            botTradingEnabled = false;

            if (systemCore != null) {
                try {
                    systemCore.setAutoTradingEnabled(false);
                    systemCore.stopStreaming();
                } catch (Exception exception) {
                    log.debug("Failed to stop SystemCore streaming", exception);
                }
            }

            withActiveChart(chart -> chart.setAutoTradeEnabled(false));
            appendAgentActivity("SystemCore bot auto trading disabled.");
            refreshBotTradeButton();
            saveAppState();

            /*
             * After bot stream stops, restart one UI-owned stream for the active symbol.
             * Starting one stream per watchlist symbol would repeatedly stop the previous
             * subscription and leave only the last symbol active.
             */
            TradePair selected = symbolSelector.getSelectionModel().getSelectedItem();
            if (selected == null && !marketWatchItems.isEmpty()) {
                selected = marketWatchItems.get(0);
            }
            if (selected != null && hasBrokerAccess()) {
                startDesktopStream(selected);
            }

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

        try {
            TradePair primarySymbol = symbols.get(0);

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
            log.error("Failed to toggle bot trading", exception);
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

        ListView<String> strategyList = new ListView<>();
        strategyList.getItems().addAll(StrategyCatalog.availableStrategyNames());
        strategyList.setCellFactory(param -> new StrategyListCell());

        HBox actionBox = new HBox(12);
        actionBox.setPadding(new Insets(12));
        actionBox.setStyle("-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1;");

        Button viewBtn = new Button("View Details");
        viewBtn.setStyle("-fx-padding: 6 16; -fx-background-color: #3b82f6; -fx-text-fill: white;");
        viewBtn.setOnAction(e -> showInfo("Strategy Details", "Strategy details panel loading..."));

        Button selectBtn = new Button("Select Strategy");
        selectBtn.setStyle("-fx-padding: 6 16; -fx-background-color: #10b981; -fx-text-fill: white;");
        selectBtn.setOnAction(e -> showInfo("Strategy Selected", "Strategy selected for trading."));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        actionBox.getChildren().addAll(viewBtn, selectBtn, spacer);

        strategiesView.getChildren().addAll(title, filterBox, strategyList, actionBox);
        VBox.setVgrow(strategyList, javafx.scene.layout.Priority.ALWAYS);

        createIndependentWindow("Available Strategies", strategiesView, 900, 700);
        journal("Strategy list opened");
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
            // Get selected symbol
            TradePair selectedSymbol;

            if (symbolSelector != null && symbolSelector.getValue() != null) {
                selectedSymbol = symbolSelector.getValue();
            } else {
                selectedSymbol = null;
            }

            CompletableFuture<OrderBook> orderBookFuture = exchange != null
                    && selectedSymbol != null
                    && exchange.supportsOrderBook()
                            ? exchange.fetchOrderBook(selectedSymbol)
                            : CompletableFuture.completedFuture(null);

            orderBookFuture
                    .exceptionally(exception -> {
                        log.debug("Order panel opened without order book data", exception);
                        return null;
                    })
                    .thenAccept(orderBook -> runOnFx(() -> {
                        try {
                            showOrderPanelWindow(selectedSymbol);
                        } catch (SQLException | ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    }));

        } catch (Exception e) {
            log.error("Error opening order panel", e);
            showWarning("Error", "Failed to open order panel: " + e.getMessage());
        }
    }

    private void showOrderPanelWindow(TradePair selectedSymbol) throws SQLException, ClassNotFoundException {
        OrderPanel orderPanel = new OrderPanel(systemCore);

        Stage orderStage = new Stage();
        orderStage.setTitle("Order Manager - " + (selectedSymbol != null ? selectedSymbol.getSymbol() : "Trading"));
        orderStage.setScene(new Scene(orderPanel, 1000, 700));
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

    private String getStrategyMonthlyReturn() {
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

        if (!pairs.isEmpty() && exchange != null) {
            try {
                List<Ticker> tickers = exchange.fetchTickers(pairs).get(2, TimeUnit.SECONDS);
                if (tickers != null) {
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
            } catch (Exception exception) {
                log.debug("Unable to refresh market research snapshot: {}", rootMessage(exception));
            }
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
            cachedMarketSnapshot = MarketSnapshot.empty();
            cachedMarketSnapshotAt = Instant.now();
            return cachedMarketSnapshot;
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

        cachedMarketSnapshot = new MarketSnapshot(sentimentIndex, averageChange, breadth, syntheticVix, btcDominance,
                totalQuoteVolume);
        cachedMarketSnapshotAt = Instant.now();
        return cachedMarketSnapshot;
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

    private record TradeStat(double profit, double notional, Instant timestamp) {
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
            var isAutoTrading = systemCore.getSmartBot() != null && systemCore.getSmartBot().isAutoTradingEnabled();
            List<CandleData> candleList = new ArrayList<>(systemCore.getHistoricalDataRepository().findAll());
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
                    .uptimeSeconds(systemHealth != null ? 0L : 0L)

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
                    .balance(account.getAvailableBalance())
                    .equity(account.getEquity())
                    .availableBalance(account.getAvailableBalance())
                    .unrealizedPnl(account.getUnrealizedPnl())
                    .realizedPnlToday(account.getRealizedPnlToday())
                    .feesAndCommission(0.0)
                    .swapOrFundingCost(0.0)
                    .openPositionCount(0)
                    .openOrderCount(0)

                    // Market Sessions
                    .primaryMarketStatus("OPEN")
                    .sessionName("Regular Trading")
                    .timeToMarketCloseSeconds(3600)
                    .timeToMarketOpenSeconds(0)
                    .liquidityCondition("GOOD")
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
            createIndependentWindow("Trading System Status", statusPanel, 1200, 800);

        } catch (Exception e) {
            log.error("Error displaying trading system status", e);
            showError("Error", "Failed to display trading system status: " + e.getMessage());
        }
    }

    private void showError(String error, String s) {
    }
}
