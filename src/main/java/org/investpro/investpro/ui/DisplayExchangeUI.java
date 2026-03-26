package org.investpro.investpro.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.investpro.investpro.Browser;
import org.investpro.investpro.CandleStickChartOptions;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.FxLifecycle;
import org.investpro.investpro.Messages;
import org.investpro.investpro.model.TradePair;
import org.investpro.investpro.ui.chart.CandleStickChartContainer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class DisplayExchangeUI extends AnchorPane {
    private static final Logger logger = LoggerFactory.getLogger(DisplayExchangeUI.class);

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
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private final ComboBox<String> symbolPicker = new ComboBox<>();
    private final ComboBox<String> autotradeScopePicker = new ComboBox<>();
    private final ListView<String> marketWatchList = new ListView<>();
    private final TabPane chartTabPane = new TabPane();
    private final TabPane leftDockTabs = new TabPane();
    private final TabPane rightDockTabs = new TabPane();
    private final TabPane bottomDockTabs = new TabPane();
    private final Map<String, Button> timeframeButtons = new LinkedHashMap<>();
    private final Map<Stage, DetachedChartWindow> detachedChartWindows = new LinkedHashMap<>();

    private final Label marketCountValueLabel = createMetricValue("Loading...");
    private final Label chartCountValueLabel = createMetricValue("0");
    private final Label detachedCountValueLabel = createMetricValue("0");
    private final Label serviceValueLabel = createMetricValue("Connecting...");
    private final Label sessionBadge = createBadge("PAPER", "terminal-session-badge");
    private final Label licenseBadge = createBadge("TRIAL", "terminal-license-badge");
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

    private final CheckMenuItem volumeBarsMenuItem = new CheckMenuItem("Show Volume");
    private final CheckMenuItem bidAskLinesMenuItem = new CheckMenuItem("Show Bid / Ask Lines");
    private final CheckMenuItem sma20MenuItem = new CheckMenuItem("SMA 20");
    private final CheckMenuItem ema50MenuItem = new CheckMenuItem("EMA 50");
    private final CheckMenuItem bollingerMenuItem = new CheckMenuItem("Bollinger Bands");

    private final AccountSummaryUI accountSummaryUI;
    private final PositionsUI positionsUI;
    private final OrdersUI ordersUI;
    private final PendingOrdersUI pendingOrdersUI;
    private final CoinInfoUI coinInfoUI;
    private final NewsUI newsUI;
    private final Browser browserUI = new Browser();

    public DisplayExchangeUI(@NotNull Exchange exchange, String tokens) {
        this.exchange = exchange;
        this.tokens = tokens == null ? "" : tokens.trim();
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
        loadTradePairs();
        appendConsole("Terminal ready for " + exchange.getClass().getSimpleName() + ".");
    }

    private void configureInteractiveState() {
        chartTabPane.getStyleClass().addAll("terminal-chart-tabs", "terminal-dock-tabs");
        chartTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        chartTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> syncActiveChartState());

        marketWatchList.getStyleClass().add("terminal-market-watch");
        marketWatchList.setPlaceholder(new Label("Markets will appear here once the exchange responds."));
        marketWatchList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                String selectedPair = marketWatchList.getSelectionModel().getSelectedItem();
                if (selectedPair != null && !selectedPair.isBlank()) {
                    addChart(selectedPair);
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
        sma20MenuItem.setOnAction(event ->
                updateIndicatorSelection(options -> options.setShowSma20(sma20MenuItem.isSelected())));
        ema50MenuItem.setOnAction(event ->
                updateIndicatorSelection(options -> options.setShowEma50(ema50MenuItem.isSelected())));
        bollingerMenuItem.setOnAction(event ->
                updateIndicatorSelection(options -> options.setShowBollingerBands(bollingerMenuItem.isSelected())));

        updateSystemStatus(null);
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
                createDockTab("Strategy Scorecard", buildStrategyScorecardPanel()),
                createDockTab("Indicators", buildIndicatorPanel())
        );
        rightDockTabs.getTabs().setAll(
                createDockTab("Orderbook", wrapDockContent(coinInfoUI)),
                createDockTab("Risk Heatmap", wrapDockContent(accountSummaryUI)),
                createDockTab("System Status", buildSystemStatusPanel())
        );
        bottomDockTabs.getTabs().setAll(
                createDockTab("Positions", wrapDockContent(positionsUI)),
                createDockTab("Open Orders", wrapDockContent(pendingOrdersUI)),
                createDockTab("Trade Log", wrapDockContent(ordersUI)),
                createDockTab("News", wrapDockContent(newsUI)),
                createDockTab("Research", wrapDockContent(browserUI)),
                createDockTab("System Console", wrapDockContent(systemConsole))
        );

        styleDockTabs(leftDockTabs);
        styleDockTabs(rightDockTabs);
        styleDockTabs(bottomDockTabs);

        StackPane leftDock = dockShell(leftDockTabs, 300);
        StackPane rightDock = dockShell(rightDockTabs, 320);
        StackPane bottomDock = dockShell(bottomDockTabs, 220);

        SplitPane centerSplit = new SplitPane(leftDock, chartSurface, rightDock);
        centerSplit.getStyleClass().add("terminal-main-split");
        centerSplit.setDividerPositions(0.18, 0.80);
        centerSplit.setMinSize(0, 0);
        centerSplit.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        SplitPane workspaceSplit = new SplitPane(centerSplit, bottomDock);
        workspaceSplit.getStyleClass().add("terminal-bottom-split");
        workspaceSplit.setOrientation(Orientation.VERTICAL);
        workspaceSplit.setDividerPositions(0.74);
        workspaceSplit.setMinSize(0, 0);
        workspaceSplit.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(workspaceSplit, Priority.ALWAYS);
        return workspaceSplit;
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
        Label copy = createPanelCopy("Double-click a symbol to open it in the terminal. This mirrors Sopotek's quick symbol launch behavior.");

        Button openButton = createToolbarButton("Open Symbol");
        openButton.setOnAction(event -> {
            String selectedPair = marketWatchList.getSelectionModel().getSelectedItem();
            if (selectedPair != null && !selectedPair.isBlank()) {
                addChart(selectedPair);
            } else {
                showError("Choose a market from Market Watch first.");
            }
        });

        Button multiButton = createToolbarButton("Multi Chart");
        multiButton.setOnAction(event -> multiChartLayout());

        Button refreshButton = createToolbarButton("Refresh Markets");
        refreshButton.setOnAction(event -> loadTradePairs());

        HBox actions = new HBox(8, openButton, multiButton, refreshButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(12, title, copy, marketWatchList, actions);
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(marketWatchList, Priority.ALWAYS);
        return wrapDockContent(panel);
    }

    private Node buildStrategyScorecardPanel() {
        Button manualTradeButton = createToolbarButton("Manual Trade");
        manualTradeButton.setOnAction(event ->
                withActiveChart(CandleStickChartContainer::showTradeTicket, "Open a chart before creating a manual trade."));

        Button autoButton = createToolbarButton("AI Toggle");
        autoButton.setOnAction(event -> setAutoTrading(!isAnyTargetChartAutoTrading()));

        VBox panel = new VBox(
                12,
                createPanelTitle("Strategy Scorecard"),
                activeSymbolLabel,
                strategyHeadlineLabel,
                strategyBodyLabel,
                strategyMetaLabel,
                new HBox(8, manualTradeButton, autoButton)
        );
        panel.setMinSize(0, 0);
        panel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
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

    private MenuBar createMenuBar() {
        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(
                createMenuItem("Open Research", () -> selectDockTab(bottomDockTabs, "Research")),
                createMenuItem("System Console", () -> selectDockTab(bottomDockTabs, "System Console")),
                new SeparatorMenuItem(),
                createMenuItem("Exit", this::hideWindow)
        );

        Menu tradingMenu = new Menu("Trading");
        tradingMenu.getItems().addAll(
                createMenuItem("Start Auto Trading", () -> setAutoTrading(true)),
                createMenuItem("Stop Auto Trading", () -> setAutoTrading(false)),
                createMenuItem("Manual Trade", () ->
                        withActiveChart(CandleStickChartContainer::showTradeTicket, "Open a chart before placing a manual trade.")),
                new SeparatorMenuItem(),
                createMenuItem("Close All Charts", this::closeAllCharts),
                createMenuItem("Emergency Kill Switch", this::activateKillSwitch)
        );

        Menu strategyMenu = new Menu("Strategy");
        strategyMenu.getItems().addAll(
                createMenuItem("Strategy Scorecard", () -> selectDockTab(leftDockTabs, "Strategy Scorecard")),
                createMenuItem("Strategy Debug", () -> selectDockTab(bottomDockTabs, "System Console")),
                createMenuItem("Indicators", () -> selectDockTab(leftDockTabs, "Indicators"))
        );

        Menu chartsMenu = new Menu("Charts");
        chartsMenu.getItems().addAll(
                createMenuItem("New Chart", this::openSelectedSymbol),
                createMenuItem("Multi Chart", this::multiChartLayout),
                new SeparatorMenuItem(),
                createMenuItem("Detach Current Tab", this::detachCurrentChartTab),
                createMenuItem("Reattach Active Chart", this::reattachActiveChartWindow),
                createMenuItem("Tile Chart Windows", this::tileDetachedChartWindows),
                createMenuItem("Cascade Chart Windows", this::cascadeDetachedChartWindows),
                new SeparatorMenuItem(),
                createMenuItem("Indicator Controls", () -> selectDockTab(leftDockTabs, "Indicators")),
                sma20MenuItem,
                ema50MenuItem,
                bollingerMenuItem,
                new SeparatorMenuItem(),
                bidAskLinesMenuItem,
                volumeBarsMenuItem
        );

        Menu dataMenu = new Menu("Data");
        dataMenu.getItems().addAll(
                createMenuItem("Refresh Markets", this::loadTradePairs),
                createMenuItem("Refresh Chart", () ->
                        withActiveChart(CandleStickChartContainer::refreshChart, "Open a chart before refreshing it.")),
                createMenuItem("Jump To Latest", () ->
                        withActiveChart(CandleStickChartContainer::jumpToLatestCandle, "Open a chart before jumping to the latest candle.")),
                createMenuItem("Reload Balance", () -> selectDockTab(rightDockTabs, "Risk Heatmap"))
        );

        Menu riskMenu = new Menu("Risk");
        riskMenu.getItems().addAll(
                createMenuItem("Risk Heatmap", () -> selectDockTab(rightDockTabs, "Risk Heatmap")),
                createMenuItem("System Status", () -> selectDockTab(rightDockTabs, "System Status")),
                createMenuItem("Positions", () -> selectDockTab(bottomDockTabs, "Positions"))
        );

        Menu reviewMenu = new Menu("Review");
        reviewMenu.getItems().addAll(
                createMenuItem("Trade Log", () -> selectDockTab(bottomDockTabs, "Trade Log")),
                createMenuItem("Open Orders", () -> selectDockTab(bottomDockTabs, "Open Orders")),
                createMenuItem("News", () -> selectDockTab(bottomDockTabs, "News"))
        );

        Menu researchMenu = new Menu("Research");
        researchMenu.getItems().addAll(
                createMenuItem("Sopotek Pilot", () -> selectDockTab(bottomDockTabs, "Research")),
                createMenuItem("Orderbook", () -> selectDockTab(rightDockTabs, "Orderbook")),
                createMenuItem("Market Watch", () -> selectDockTab(leftDockTabs, "Market Watch"))
        );

        Menu toolsMenu = new Menu("Tools");
        toolsMenu.getItems().addAll(
                createMenuItem("System Console", () -> selectDockTab(bottomDockTabs, "System Console")),
                createMenuItem("System Status", () -> selectDockTab(rightDockTabs, "System Status")),
                createMenuItem("Indicators", () -> selectDockTab(leftDockTabs, "Indicators"))
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
                tradingMenu,
                strategyMenu,
                chartsMenu,
                dataMenu,
                riskMenu,
                reviewMenu,
                researchMenu,
                toolsMenu,
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
        Button manualTradeButton = createToolbarButton("Manual Trade");
        manualTradeButton.setOnAction(event ->
                withActiveChart(CandleStickChartContainer::showTradeTicket, "Open a chart before submitting a manual trade."));

        Button refreshButton = createToolbarButton("Refresh");
        refreshButton.setOnAction(event ->
                withActiveChart(CandleStickChartContainer::refreshChart, "Open a chart before refreshing it."));

        Button latestButton = createToolbarButton("Latest");
        latestButton.setOnAction(event ->
                withActiveChart(CandleStickChartContainer::jumpToLatestCandle, "Open a chart before jumping to the latest candle."));

        Button autoButton = createToolbarButton("AUTO");
        autoButton.getStyleClass().add("terminal-auto-button");
        autoButton.setOnAction(event -> setAutoTrading(!isAnyTargetChartAutoTrading()));

        HBox executionBox = createToolbarBox(
                manualTradeButton,
                refreshButton,
                latestButton
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

        Button openSymbolButton = createToolbarButton("Open Symbol");
        openSymbolButton.setOnAction(event -> openSelectedSymbol());

        return createToolbarBox(
                createToolbarLabel("Symbol"),
                symbolPicker,
                openSymbolButton
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
        Button screenshotButton = createToolbarButton("Screenshot");
        screenshotButton.setOnAction(event ->
                withActiveChart(CandleStickChartContainer::captureScreenshot, "Open a chart before capturing a screenshot."));

        Button killSwitchButton = createToolbarButton("Kill Switch");
        killSwitchButton.getStyleClass().add("terminal-kill-switch");
        killSwitchButton.setOnAction(event -> activateKillSwitch());

        VBox liveBox = new VBox(4, createToolbarLabel("Live Mode"), liveModeBar);
        liveBox.getStyleClass().add("terminal-live-box");
        liveBox.setAlignment(Pos.CENTER);
        liveBox.setPrefWidth(146);

        HBox utilityBox = createToolbarBox(
                connectionIndicator,
                screenshotButton,
                sessionBadge,
                licenseBadge,
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

    private void loadTradePairs() {
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

                FxLifecycle.runLaterIf(() -> !disposed.get(), () -> {
                    symbolPicker.setItems(FXCollections.observableArrayList(pairNames));
                    marketWatchList.getItems().setAll(pairNames);
                    if (!pairNames.isEmpty() && symbolPicker.getValue() == null) {
                        symbolPicker.getSelectionModel().selectFirst();
                    }
                    if (!pairNames.isEmpty() && marketWatchList.getSelectionModel().isEmpty()) {
                        marketWatchList.getSelectionModel().selectFirst();
                    }
                    marketCountValueLabel.setText(Integer.toString(pairNames.size()));
                    serviceValueLabel.setText(exchange.getExchangeMessage());
                    connectionIndicator.setText("CONNECTED");
                    updateSystemStatus(getActiveChartContainer());
                    appendConsole("Loaded " + pairNames.size() + " trade pairs.");
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

    private void openSelectedSymbol() {
        String selectedPair = symbolPicker.getSelectionModel().getSelectedItem();
        if (selectedPair == null || selectedPair.isBlank()) {
            showError("Choose a symbol before opening a chart.");
            return;
        }
        addChart(selectedPair);
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
        } catch (Exception e) {
            logger.error("Error adding chart", e);
            showError("Error adding chart: " + e.getMessage());
        }
    }

    private Tab createChartTab(String title, CandleStickChartContainer chartContainer) {
        Tab chartTab = new Tab(title, chartContainer);
        chartTab.setOnClosed(event -> {
            chartContainer.shutdown();
            chartTab.setContent(null);
            syncActiveChartState();
            appendConsole("Closed chart " + title + ".");
        });
        return chartTab;
    }

    private void multiChartLayout() {
        List<String> markets = new ArrayList<>(marketWatchList.getItems());
        if (markets.isEmpty()) {
            showError("Load markets before opening a multi-chart layout.");
            return;
        }

        int targetCount = Math.min(4, markets.size());
        for (int i = 0; i < targetCount; i++) {
            addChart(markets.get(i));
        }
        appendConsole("Built multi-chart layout for " + targetCount + " symbols.");
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
        }, "Open a chart before changing the timeframe.");
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
        detachedCountValueLabel.setText(Integer.toString(detachedChartWindows.size()));

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
            sma20MenuItem.setSelected(false);
            ema50MenuItem.setSelected(false);
            bollingerMenuItem.setSelected(false);
            updateSystemStatus(null);
            strategyHeadlineLabel.setText("Open a symbol to begin.");
            strategyBodyLabel.setText("The active chart, timeframe, indicators, and AI state will appear here.");
            strategyMetaLabel.setText("Manual execution stays available even when AI is idle.");
            return;
        }

        String tradePair = activeChart.getTradePair().toString('/');
        activeSymbolLabel.setText(tradePair);
        if (!Objects.equals(symbolPicker.getValue(), tradePair)) {
            symbolPicker.setValue(tradePair);
        }
        marketWatchList.getSelectionModel().select(tradePair);
        aiActivityLabel.setText(activeChart.isAiTradingEnabled() ? "AI Active" : "AI Idle");
        setActiveTimeframeButton(resolveTimeframeLabel(activeChart.getSecondsPerCandle()));

        CandleStickChartOptions options = activeChart.getChartOptions();
        volumeBarsMenuItem.setSelected(options.isShowVolume());
        bidAskLinesMenuItem.setSelected(activeChart.isBidAskLinesVisible());
        sma20MenuItem.setSelected(options.isShowSma20());
        ema50MenuItem.setSelected(options.isShowEma50());
        bollingerMenuItem.setSelected(options.isShowBollingerBands());

        indicatorHost.getChildren().setAll(activeChart.createChartOptionsPane());
        strategyHeadlineLabel.setText(tradePair + " • " + resolveTimeframeLabel(activeChart.getSecondsPerCandle()));
        strategyBodyLabel.setText(activeChart.isAiTradingEnabled()
                ? "AI trading is currently active for the selected chart."
                : "Manual mode is active. Use the toolbar or Trading menu to arm AI execution.");
        strategyMetaLabel.setText("Indicators: "
                + (options.isShowSma20() ? "SMA 20 " : "")
                + (options.isShowEma50() ? "EMA 50 " : "")
                + (options.isShowBollingerBands() ? "Bollinger " : "None"));
        updateSystemStatus(activeChart);
    }

    private void updateSystemStatus(CandleStickChartContainer activeChart) {
        systemStatusExchangeLabel.setText("Exchange: " + exchange.getClass().getSimpleName());
        systemStatusSymbolLabel.setText("Active Symbol: " + (activeChart == null ? "None" : activeChart.getTradePair().toString('/')));
        systemStatusChartsLabel.setText("Charts: " + chartTabPane.getTabs().size() + " attached / " + detachedChartWindows.size() + " detached");
        systemStatusConnectionLabel.setText("Service: " + serviceValueLabel.getText());
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
            targets.addAll(getAttachedCharts());
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
    }

    private void activateKillSwitch() {
        setAutoTrading(false);
        appendConsole("Emergency kill switch activated.");
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

        appendConsole("Detached chart " + title + ".");
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
        appendConsole("Reattached chart " + detachedWindow.title + ".");
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
            window.stage.setY(80 + (index / 3) * 260);
            window.stage.setWidth(420);
            window.stage.setHeight(250);
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

    private void selectDockTab(TabPane tabPane, String title) {
        for (Tab tab : tabPane.getTabs()) {
            if (Objects.equals(tab.getText(), title)) {
                tabPane.getSelectionModel().select(tab);
                return;
            }
        }
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

        closeAllCharts();
        accountSummaryUI.shutdown();
        positionsUI.shutdown();
        ordersUI.shutdown();
        pendingOrdersUI.shutdown();
        coinInfoUI.shutdown();
        newsUI.shutdown();

        executorService.shutdownNow();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
}
