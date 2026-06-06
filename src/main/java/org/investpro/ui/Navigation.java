package org.investpro.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.investpro.config.AppConfigKeys;
import org.investpro.i18n.LocalizationService;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.PluginRegistry;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Professional trading-desk navigation surface for InvestPro.
 *
 * <p>
 * Combines venue selection, connection state, and high-frequency workflow
 * shortcuts in one compact panel. TradingDesk owns the actual actions; this
 * class stays focused on navigation intent and presentation.
 * </p>
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class Navigation extends StackPane {

    private static final List<String> LEGACY_EXCHANGES = List.of(
            "COINBASE",
            "BINANCE_US",
            "BINANCE",
            "OANDA",
            "BITFINEX",
            "ALPACA",
            "INTERACTIVE_BROKERS",
            "STELLAR_NETWORK",
            "SOLONA_NETWORK");

    private ComboBox<String> exchangeSelector;
    private Label currentExchangeLabel;
    private Label connectionStatusLabel;
    private Label titleLabel;
    private Label subtitleLabel;
    private Label sessionModeLabel;

    /**
     * Legacy callback kept for compatibility with older code.
     */
    private Runnable onExchangeChanged;

    /**
     * Better callback that receives the selected exchange name.
     */
    private Consumer<String> onExchangeSelected;

    private Consumer<String> onConnectRequested;
    private Consumer<String> onDisconnectRequested;

    /**
     * Requests that TradingDesk open a workflow or panel by id.
     */
    private Consumer<String> onNavigationRequested;
    private boolean syncingExchangeState;
    private final Map<String, Button> venueButtons = new HashMap<>();

    public Navigation() {
        initializeUI();
    }

    private void initializeUI() {
        getStyleClass().add("trading-navigation");

        VBox root = new VBox(12);
        root.setFillWidth(true);
        root.setPadding(new Insets(14));
        root.getStyleClass().addAll("pro-panel", "trading-navigation-panel");

        root.getChildren().setAll(
                createHeader(),
                createQuickCommandRail(),
                createExchangeCard(),
                createWorkflowSection(),
                createMarketSection(),
            createAccountsSection(),
                createBrokerSection(),
                createSystemSection());

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("trading-navigation-scroll");

        LocalizationService.applyTranslations(root);
        getChildren().setAll(scrollPane);
    }

    private VBox createHeader() {
        titleLabel = new Label("Trading Desk");
        titleLabel.getStyleClass().add("trading-navigation-title");

        subtitleLabel = new Label("Command navigation");
        subtitleLabel.getStyleClass().add("trading-navigation-subtitle");

        sessionModeLabel = new Label("Mode: " + resolveTradingMode());
        sessionModeLabel.getStyleClass().add("trading-navigation-chip");

        HBox titleRow = new HBox(8, titleLabel, new Region(), sessionModeLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleRow.getChildren().get(1), Priority.ALWAYS);

        VBox header = new VBox(4, titleRow, subtitleLabel);
        header.getStyleClass().add("trading-navigation-header");
        return header;
    }

    private VBox createExchangeCard() {
        String defaultExchange = resolveDefaultExchange();

        currentExchangeLabel = new Label(displayExchangeName(defaultExchange));
        currentExchangeLabel.getStyleClass().add("trading-navigation-exchange-name");

        connectionStatusLabel = new Label("Disconnected");
        connectionStatusLabel.getStyleClass().addAll("trading-navigation-status", "disconnected");

        HBox statusRow = new HBox(8, currentExchangeLabel, new Region(), connectionStatusLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusRow.getChildren().get(1), Priority.ALWAYS);

        Label selectorLabel = new Label("Venue");
        selectorLabel.getStyleClass().add("trading-navigation-label");

        exchangeSelector = new ComboBox<>();
        exchangeSelector.getStyleClass().addAll("terminal-combo-box", "trading-navigation-selector");
        exchangeSelector.setMaxWidth(Double.MAX_VALUE);
        exchangeSelector.setTooltip(new Tooltip("Choose the exchange or broker used by InvestPro."));
        exchangeSelector.getItems().setAll(
                discoverExchangeIds());
        exchangeSelector.setValue(defaultExchange);
        exchangeSelector.setOnAction(event -> {
            if (syncingExchangeState) {
                return;
            }
            String selected = exchangeSelector.getValue();
            if (selected != null && !selected.isBlank()) {
                selectExchange(selected);
            }
        });

        GridPane venueGrid = new GridPane();
        venueGrid.setHgap(6);
        venueGrid.setVgap(6);
        venueGrid.getStyleClass().add("trading-navigation-venue-grid");
        venueGrid.add(createExchangeButton("OANDA"), 0, 0);
        venueGrid.add(createExchangeButton("COINBASE"), 1, 0);
        venueGrid.add(createExchangeButton("BINANCE"), 0, 1);
        venueGrid.add(createExchangeButton("BINANCE_US"), 1, 1);
        venueGrid.add(createExchangeButton("BITFINEX"), 0, 2);
        venueGrid.add(createExchangeButton("SOLONA_NETWORK"), 1, 2);

        Button connectButton = createUtilityButton("Connect", "Connect to the selected venue.");
        connectButton.getStyleClass().add("success-button");
        connectButton.setOnAction(event -> connectToExchange());

        Button disconnectButton = createUtilityButton("Disconnect", "Disconnect from the selected venue.");
        disconnectButton.getStyleClass().add("danger-button");
        disconnectButton.setOnAction(event -> disconnectFromExchange());

        HBox connectionActions = new HBox(8, connectButton, disconnectButton);
        connectionActions.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(connectButton, Priority.ALWAYS);
        HBox.setHgrow(disconnectButton, Priority.ALWAYS);

        VBox card = new VBox(10,
                sectionTitle("Venue Control"),
                statusRow,
                selectorLabel,
                exchangeSelector,
                venueGrid,
                connectionActions);
        card.getStyleClass().add("trading-navigation-card");
        updateVenueButtonState(defaultExchange);
        return card;
    }

    private HBox createQuickCommandRail() {
        Button tradeDesk = createQuickCommandButton("Trade", "Open order ticket", "order-panel");
        Button marketWatch = createQuickCommandButton("Markets", "Open market watch", "market-watch");
        Button ibkr = createQuickCommandButton("IBKR", "Open IBKR workspace", "ibkr");
        Button ops = createQuickCommandButton("Ops", "Open operations center", "operations-center");

        HBox rail = new HBox(8, tradeDesk, marketWatch, ibkr, ops);
        rail.setAlignment(Pos.CENTER_LEFT);
        rail.getStyleClass().add("trading-navigation-quick-rail");
        return rail;
    }

    private Button createQuickCommandButton(String text, String tooltip, String panelId) {
        Button button = new Button(text);
        button.getStyleClass().add("trading-navigation-quick-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(event -> requestNavigation(panelId));
        return button;
    }

    private VBox createWorkflowSection() {
        VBox section = createSection("Trading Workflow");
        section.getChildren().addAll(
                createNavButton("Order Ticket", "Place and manage orders", "order-panel", true),
                createNavButton("Account Activity", "Balances, positions, orders", "account-activity", false),
                createNavButton("Strategy Lab", "Test and compare strategies", "strategy-lab", false),
                createNavButton("Assignments", "Map strategies to symbols", "strategy-assignment", false),
                createNavButton("Backtesting", "Run historical simulations", "backtesting", false),
                createNavButton("Analysis", "Open research and diagnostics", "analysis", false));
        return section;
    }

    private VBox createMarketSection() {
        VBox section = createSection("Market Intelligence");
        section.getChildren().addAll(
                createNavButton("Market Watch", "Symbol-level status board", "market-watch", false),
                createNavButton("Market Info", "Contract and session details", "market-info", false),
                createNavButton("News Calendar", "Macro and event calendar", "news-calendar", false),
                createNavButton("Data Window", "OHLCV details for selected symbol", "data-window", false));
        return section;
    }

    private VBox createBrokerSection() {
        VBox section = createSection("Broker Integrations");
        section.getChildren().addAll(
                createNavButton("IBKR Control", "Configure and verify IB Gateway connectivity", "ibkr-connection", false));
        return section;
    }

        private VBox createAccountsSection() {
        VBox section = createSection("Accounts");
        section.getChildren().addAll(
            createNavButton("Portfolio", "Account-level balance and asset overview", "portfolio", false),
            createNavButton("Positions", "Open positions across connected venues", "positions", false),
            createNavButton("Deposits & Withdrawals", "Funding movement and settlement events",
                "deposits-withdrawals", false),
            createNavButton("Transfer Funds", "Move capital between brokers and wallets", "transfer-funds",
                true),
            createNavButton("Account Management", "Broker credentials, KYC, and profile controls",
                "account-management", false));
        return section;
        }

    private VBox createSystemSection() {
        VBox section = createSection("Operations");
        section.getChildren().addAll(
                createNavButton("Operations Center", "Feed, engine, risk, logs", "operations-center", true),
                createNavButton("Plugin Manager", "Discovered extensions and providers", "plugin-manager", false),
                createNavButton("System Status", "Runtime and agent health", "trading-system-status", false),
                createNavButton("Resource Monitor", "CPU, memory, and runtime load", "resource-monitor", false),
                createNavButton("Settings", "Configure accounts and preferences", "settings", false));
        return section;
    }

    private VBox createSection(String title) {
        VBox section = new VBox(8);
        section.getStyleClass().add("trading-navigation-section");
        section.getChildren().add(sectionTitle(title));
        return section;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("trading-navigation-section-title");
        return label;
    }

    private Button createExchangeButton(String exchangeName) {
        Button button = new Button(displayExchangeName(exchangeName));
        button.getStyleClass().add("trading-navigation-venue-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setTooltip(new Tooltip("Switch to " + displayExchangeName(exchangeName)));
        String normalizedExchange = normalizeExchangeName(exchangeName);
        venueButtons.put(normalizedExchange, button);
        button.setOnAction(event -> selectExchange(normalizedExchange));
        GridPane.setHgrow(button, Priority.ALWAYS);
        return button;
    }

    private Button createUtilityButton(String text, String tooltip) {
        Button button = new Button(text);
        button.getStyleClass().add("trading-navigation-utility-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private Button createNavButton(String title, String description, String panelId, boolean emphasized) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("trading-navigation-action-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("trading-navigation-action-description");
        descriptionLabel.setWrapText(true);

        VBox text = new VBox(2, titleLabel, descriptionLabel);
        text.setAlignment(Pos.CENTER_LEFT);

        Label arrow = new Label(">");
        arrow.getStyleClass().add("trading-navigation-action-arrow");

        HBox content = new HBox(10, text, new Region(), arrow);
        content.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(content.getChildren().get(1), Priority.ALWAYS);

        Button button = new Button();
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("trading-navigation-action");
        if (emphasized) {
            button.getStyleClass().add("primary-action");
        }
        button.setTooltip(new Tooltip(title + ": " + description));
        button.setOnAction(event -> requestNavigation(panelId));
        return button;
    }

    private void requestNavigation(String panelId) {
        if (panelId == null || panelId.isBlank()) {
            return;
        }

        log.info("Navigation requested: {}", panelId);

        if (onNavigationRequested != null) {
            onNavigationRequested.accept(panelId);
        }
    }

    private void selectExchange(String exchangeName) {
        if (exchangeName == null || exchangeName.isBlank()) {
            return;
        }

        String normalizedExchange = normalizeExchangeName(exchangeName);

        if (!exchangeSelector.getItems().contains(normalizedExchange)) {
            log.warn("Exchange '{}' is not available in selector items: {}", normalizedExchange,
                    exchangeSelector.getItems());
            return;
        }

        if (!Objects.equals(exchangeSelector.getValue(), normalizedExchange)) {
            exchangeSelector.setValue(normalizedExchange);
        }

        currentExchangeLabel.setText(displayExchangeName(normalizedExchange));
        updateVenueButtonState(normalizedExchange);

        log.info("Exchange selected: {}", normalizedExchange);

        if (onExchangeSelected != null) {
            onExchangeSelected.accept(normalizedExchange);
        }

        if (onExchangeChanged != null) {
            onExchangeChanged.run();
        }
    }

    private void connectToExchange() {
        String currentExchange = getSelectedExchange();

        if (currentExchange == null || currentExchange.isBlank()) {
            setConnectionStatus(false);
            log.warn("Cannot connect because no exchange is selected.");
            return;
        }

        log.info("Connecting to exchange: {}", currentExchange);

        connectionStatusLabel.setText("Connecting");
        connectionStatusLabel.getStyleClass().removeAll("connected", "disconnected", "connecting");
        connectionStatusLabel.getStyleClass().add("connecting");

        if (onConnectRequested != null) {
            onConnectRequested.accept(currentExchange);
        }
    }

    private void disconnectFromExchange() {
        String currentExchange = getSelectedExchange();
        log.info("Disconnecting from exchange: {}", currentExchange);
        if (onDisconnectRequested != null) {
            onDisconnectRequested.accept(currentExchange);
        }
        setConnectionStatus(false);
    }

    public String getSelectedExchange() {
        return exchangeSelector == null ? null : exchangeSelector.getValue();
    }

    public void setConnectionStatus(boolean connected) {
        Runnable update = () -> {
            if (connectionStatusLabel == null) {
                return;
            }

            connectionStatusLabel.getStyleClass().removeAll("connected", "disconnected", "connecting");

            if (connected) {
                connectionStatusLabel.setText("Connected");
                connectionStatusLabel.getStyleClass().add("connected");
            } else {
                connectionStatusLabel.setText("Disconnected");
                connectionStatusLabel.getStyleClass().add("disconnected");
            }
        };

        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    public void syncExchangeState(String exchangeName, boolean connected) {
        if (exchangeName == null || exchangeName.isBlank()) {
            setConnectionStatus(connected);
            return;
        }

        Runnable update = () -> {
            String normalizedExchange = normalizeExchangeName(exchangeName);
            if (!exchangeSelector.getItems().contains(normalizedExchange)) {
                exchangeSelector.getItems().add(normalizedExchange);
            }
            syncingExchangeState = true;
            try {
                exchangeSelector.setValue(normalizedExchange);
            } finally {
                syncingExchangeState = false;
            }
            currentExchangeLabel.setText(displayExchangeName(normalizedExchange));
            updateVenueButtonState(normalizedExchange);
            setConnectionStatus(connected);
        };

        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    public void setCurrentExchange(String exchangeName) {
        if (exchangeName == null || exchangeName.isBlank()) {
            return;
        }

        Runnable update = () -> selectExchange(exchangeName);

        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    private String resolveDefaultExchange() {
        String configured = AppConfig.get(AppConfigKeys.DEFAULT_EXCHANGE, "");
        String normalized = normalizeExchangeName(configured);

        boolean exists = discoverExchangeIds().stream()
                .anyMatch(name -> name.equalsIgnoreCase(normalized));

        if (exists) {
            return normalized;
        }

        List<String> exchanges = discoverExchangeIds();
        return exchanges.isEmpty() ? "" : exchanges.getFirst();
    }

    private List<String> discoverExchangeIds() {
        LinkedHashSet<String> exchanges = new LinkedHashSet<>();
        try {
            PluginRegistry.loadDefault().exchangeProviders().stream()
                    .filter(ExchangeProvider::enabledByDefault)
                    .map(ExchangeProvider::id)
                    .map(this::normalizeExchangeName)
                    .forEach(exchanges::add);
        } catch (Exception exception) {
            log.warn("Unable to discover exchange providers. Using legacy navigation list.", exception);
        }
        exchanges.addAll(LEGACY_EXCHANGES);
        return List.copyOf(exchanges);
    }

    private String resolveTradingMode() {
        String mode = System.getProperty("investpro.trading.mode", "PAPER");
        return mode == null || mode.isBlank() ? "PAPER" : mode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeExchangeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.trim()
                .replace(" ", "_")
                .replace("-", "_")
                .toUpperCase(Locale.ROOT);

        if ("BINANCEUS".equals(normalized)) {
            return "BINANCE_US";
        }

        return normalized;
    }

    private String displayExchangeName(String exchangeName) {
        if (exchangeName == null || exchangeName.isBlank()) {
            return "";
        }

        return exchangeName.trim()
                .replace("_", " ");
    }

    private void updateVenueButtonState(String selectedExchange) {
        String normalized = normalizeExchangeName(selectedExchange);
        venueButtons.forEach((exchange, button) -> {
            button.getStyleClass().remove("selected");
            if (exchange.equals(normalized)) {
                button.getStyleClass().add("selected");
            }
        });
    }
}
