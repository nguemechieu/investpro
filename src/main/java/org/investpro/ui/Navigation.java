package org.investpro.ui;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.investpro.config.AppConfig;
import org.investpro.config.AppConfigKeys;
import org.investpro.i18n.LocalizationService;

/**
 * Navigation panel for switching between exchanges and managing exchange
 * connections.
 * Provides UI controls for selecting different crypto exchanges and viewing
 * exchange status.
 */
@Slf4j
@Getter
@Setter
public class Navigation extends Stage {
    private static final String[] AVAILABLE_EXCHANGES = {
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

    private ComboBox<String> exchangeSelector;
    private Label currentExchangeLabel;
    private Label connectionStatusLabel;
    private Runnable onExchangeChanged;

    public Navigation() {
        initializeUI();
    }

    /**
     * Initialize the navigation UI with exchange selector and controls
     */
    private void initializeUI() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #0f172a; -fx-text-fill: #e5e7eb;");

        // Title
        Label titleLabel = new Label("EXCHANGE NAVIGATOR");
        titleLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

        // Current Exchange Display
        String defaultExchangeForLabel = AppConfig.get(AppConfigKeys.DEFAULT_EXCHANGE, "OANDA");
        currentExchangeLabel = new Label("Current: " + defaultExchangeForLabel);
        currentExchangeLabel.setStyle("-fx-font-size: 12; -fx-padding: 8; -fx-background-color: #1e293b; "
                + "-fx-border-color: #334155; -fx-border-width: 1; -fx-border-radius: 4;");

        // Connection Status
        connectionStatusLabel = new Label("Status: Disconnected");
        connectionStatusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #ef4444;");

        // Exchange Selector
        Label selectorLabel = new Label("Select Exchange:");
        selectorLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");

        exchangeSelector = new ComboBox<>();
        exchangeSelector.getItems().setAll(AVAILABLE_EXCHANGES);
        // Use DEFAULT_EXCHANGE from AppConfig instead of hardcoding the first element
        String defaultExchange = AppConfig.get(AppConfigKeys.DEFAULT_EXCHANGE, "OANDA");
        if (AVAILABLE_EXCHANGES.length > 0) {
            if (java.util.Arrays.asList(AVAILABLE_EXCHANGES).contains(defaultExchange)) {
                exchangeSelector.setValue(defaultExchange);
            } else {
                // Fall back to first element if default is not in available list
                exchangeSelector.setValue(AVAILABLE_EXCHANGES[0]);
            }
        }
        exchangeSelector.setPrefWidth(200);
        exchangeSelector.setStyle("-fx-font-size: 11;");
        exchangeSelector.setOnAction(event -> handleExchangeSelection());

        // Navigation Buttons
        VBox exchangeButtonsBox = createExchangeButtonsBox();

        // Control Buttons
        HBox controlButtonsBox = createControlButtonsBox();

        // Separator
        Separator separator = new Separator();

        // Add all components
        root.getChildren().addAll(
                titleLabel,
                new Separator(),
                currentExchangeLabel,
                connectionStatusLabel,
                new Separator(),
                selectorLabel,
                exchangeSelector,
                new Label("Quick Navigation:"),
                exchangeButtonsBox,
                separator,
                controlButtonsBox);

        VBox.setVgrow(root, Priority.ALWAYS);

        // Set stage properties
        this.setTitle("Exchange Navigator");
        this.setWidth(250);
        this.setHeight(500);
        this.setScene(new javafx.scene.Scene(root));
        root.setStyle("-fx-base: #0f172a;");
        LocalizationService.applyTranslations(root);
    }

    /**
     * Create quick navigation buttons for major exchanges
     */
    private VBox createExchangeButtonsBox() {
        VBox buttonsContainer = new VBox(5);
        buttonsContainer.setPadding(new Insets(5));

        // Row 1
        HBox row1 = new HBox(5);
        row1.getChildren().addAll(
                createExchangeButton("BINANCE", "binance-btn"),
                createExchangeButton("COINBASE", "coinbase-btn"));
        HBox.setHgrow(row1.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(row1.getChildren().get(1), Priority.ALWAYS);

        // Row 2
        HBox row2 = new HBox(5);
        row2.getChildren().addAll(
                createExchangeButton("OANDA", "oanda-btn"),
                createExchangeButton("BITFINEX", "bitfinex-btn"));
        HBox.setHgrow(row2.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(row2.getChildren().get(1), Priority.ALWAYS);

        // Row 3
        HBox row3 = new HBox(5);
        Button binanceUsBtn = createExchangeButton("BINANCE US", "binance-us-btn");
        row3.getChildren().add(binanceUsBtn);
        HBox.setHgrow(binanceUsBtn, Priority.ALWAYS);

        buttonsContainer.getChildren().addAll(row1, row2, row3);
        return buttonsContainer;
    }

    /**
     * Create a single exchange navigation button
     */
    private Button createExchangeButton(String exchangeName, String styleId) {
        Button button = new Button(exchangeName);
        button.setId(styleId);
        button.setPrefHeight(35);
        button.setStyle("-fx-font-size: 10; -fx-padding: 8; -fx-background-color: #1e293b; "
                + "-fx-text-fill: #e5e7eb; -fx-border-color: #3b82f6; -fx-border-width: 1; "
                + "-fx-border-radius: 4; -fx-cursor: hand;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-font-size: 10; -fx-padding: 8; "
                + "-fx-background-color: #3b82f6; -fx-text-fill: #ffffff; -fx-border-color: #2563eb; "
                + "-fx-border-width: 1; -fx-border-radius: 4; -fx-cursor: hand;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-font-size: 10; -fx-padding: 8; "
                + "-fx-background-color: #1e293b; -fx-text-fill: #e5e7eb; -fx-border-color: #3b82f6; "
                + "-fx-border-width: 1; -fx-border-radius: 4; -fx-cursor: hand;"));
        button.setOnAction(event -> selectExchange(exchangeName));
        return button;
    }

    /**
     * Create control buttons for exchange operations
     */
    private HBox createControlButtonsBox() {
        HBox controlBox = new HBox(8);
        controlBox.setPadding(new Insets(8));

        Button connectButton = new Button("Connect");
        connectButton.setPrefWidth(100);
        connectButton.setStyle("-fx-font-size: 11; -fx-padding: 8; -fx-background-color: #10b981; "
                + "-fx-text-fill: #ffffff; -fx-border-radius: 4; -fx-cursor: hand;");
        connectButton.setOnAction(event -> connectToExchange());

        Button disconnectButton = new Button("Disconnect");
        disconnectButton.setPrefWidth(100);
        disconnectButton.setStyle("-fx-font-size: 11; -fx-padding: 8; -fx-background-color: #ef4444; "
                + "-fx-text-fill: #ffffff; -fx-border-radius: 4; -fx-cursor: hand;");
        disconnectButton.setOnAction(event -> disconnectFromExchange());

        controlBox.getChildren().addAll(connectButton, disconnectButton);
        return controlBox;
    }

    /**
     * Handle exchange selection from combo box
     */
    private void handleExchangeSelection() {
        String selected = exchangeSelector.getValue();
        selectExchange(selected);
    }

    /**
     * Select a specific exchange and update UI
     */
    private void selectExchange(String exchangeName) {
        exchangeSelector.setValue(exchangeName);
        currentExchangeLabel.setText("Current: " + exchangeName);
        log.info("Exchange selected: {}", exchangeName);

        // Call the callback if set
        if (onExchangeChanged != null) {
            onExchangeChanged.run();
        }
    }

    /**
     * Connect to the currently selected exchange
     */
    private void connectToExchange() {
        String currentExchange = exchangeSelector.getValue();
        log.info("Connecting to exchange: {}", currentExchange);
        connectionStatusLabel.setText("Status: Connecting...");
        connectionStatusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #f59e0b;");
        // Connection logic would be handled by the trading window
    }

    /**
     * Disconnect from the currently selected exchange
     */
    private void disconnectFromExchange() {
        String currentExchange = exchangeSelector.getValue();
        log.info("Disconnecting from exchange: {}", currentExchange);
        connectionStatusLabel.setText("Status: Disconnected");
        connectionStatusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #ef4444;");
    }

    /**
     * Get the currently selected exchange
     */
    public String getSelectedExchange() {
        return exchangeSelector.getValue();
    }

    /**
     * Set the callback for when exchange changes
     */
    public void setOnExchangeChanged(Runnable callback) {
        this.onExchangeChanged = callback;
    }

    /**
     * Update connection status indicator
     */
    public void setConnectionStatus(boolean connected) {
        if (connected) {
            connectionStatusLabel.setText("Status: Connected");
            connectionStatusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #10b981;");
        } else {
            connectionStatusLabel.setText("Status: Disconnected");
            connectionStatusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #ef4444;");
        }
    }

    /**
     * Update the current exchange display label
     */
    public void setCurrentExchange(String exchangeName) {
        currentExchangeLabel.setText("Current: " + exchangeName);
    }
}
