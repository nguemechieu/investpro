package org.investpro.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.investpro.config.AppConfigKeys;
import org.investpro.exchange.infrastructure.ENUM_EXCHANGE_LIST;
import org.investpro.i18n.LocalizationService;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Professional exchange navigation panel for InvestPro.
 *
 * <p>Allows users to select an exchange, quickly switch between major venues,
 * and view connection status. Styling is intentionally delegated to CSS where possible.</p>
 */
@Slf4j
@Getter
@Setter
public class Navigation extends StackPane {

    private static final List<ENUM_EXCHANGE_LIST> AVAILABLE_EXCHANGES =
            Arrays.stream(ENUM_EXCHANGE_LIST.values()).toList();

    private ComboBox<String> exchangeSelector;
    private Label currentExchangeLabel;
    private Label connectionStatusLabel;
    private Label titleLabel;

    /**
     * Legacy callback kept for compatibility with older code.
     */
    private Runnable onExchangeChanged;

    /**
     * Better callback that receives the selected exchange name.
     */
    private Consumer<String> onExchangeSelected;

    public Navigation() {
        initializeUI();
    }

    private void initializeUI() {
        getStyleClass().add("exchange-navigation");

        VBox root = new VBox(12);
        root.setAlignment(Pos.TOP_LEFT);
        root.setPadding(new Insets(14));
        root.getStyleClass().addAll("pro-panel", "exchange-navigation-panel");

        titleLabel = new Label("EXCHANGE NAVIGATOR");
        titleLabel.getStyleClass().addAll("panel-title", "exchange-navigation-title");

        String defaultExchange = resolveDefaultExchange();

        currentExchangeLabel = new Label("Current: " + defaultExchange);
        currentExchangeLabel.getStyleClass().addAll("exchange-current-label", "desk-metric-value");

        connectionStatusLabel = new Label("Status: Disconnected");
        connectionStatusLabel.getStyleClass().addAll("connection-status", "disconnected");

        Label selectorLabel = new Label("Select Exchange");
        selectorLabel.getStyleClass().addAll("panel-meta", "exchange-selector-label");

        exchangeSelector = new ComboBox<>();
        exchangeSelector.getStyleClass().addAll("terminal-combo-box", "exchange-selector");
        exchangeSelector.setMaxWidth(Double.MAX_VALUE);
        exchangeSelector.setPrefWidth(220);
        exchangeSelector.setTooltip(new Tooltip("Choose the exchange or broker used by InvestPro."));

        exchangeSelector.getItems().setAll(
                AVAILABLE_EXCHANGES.stream()
                        .map(Enum::name)
                        .toList()
        );

        exchangeSelector.setValue(defaultExchange);
        exchangeSelector.setOnAction(event -> {
            String selected = exchangeSelector.getValue();
            if (selected != null && !selected.isBlank()) {
                selectExchange(selected);
            }
        });

        VBox exchangeButtonsBox = createExchangeButtonsBox();

        HBox controlButtonsBox = createControlButtonsBox();
        controlButtonsBox.setMaxWidth(Double.MAX_VALUE);

        Label quickNavLabel = new Label("Quick Navigation");
        quickNavLabel.getStyleClass().addAll("panel-meta", "exchange-quick-nav-label");

        root.getChildren().setAll(
                titleLabel,
                new Separator(),
                currentExchangeLabel,
                connectionStatusLabel,
                new Separator(),
                selectorLabel,
                exchangeSelector,
                quickNavLabel,
                exchangeButtonsBox,
                new Separator(),
                controlButtonsBox
        );

        VBox.setVgrow(exchangeButtonsBox, Priority.NEVER);

        LocalizationService.applyTranslations(root);
        getChildren().setAll(root);
    }

    private VBox createExchangeButtonsBox() {
        VBox buttonsContainer = new VBox(6);
        buttonsContainer.getStyleClass().add("exchange-buttons-container");
        buttonsContainer.setMaxWidth(Double.MAX_VALUE);

        HBox row1 = new HBox(6);
        row1.setMaxWidth(Double.MAX_VALUE);
        row1.getChildren().addAll(
                createExchangeButton("BINANCE", "binance-btn"),
                createExchangeButton("COINBASE", "coinbase-btn")
        );

        HBox row2 = new HBox(6);
        row2.setMaxWidth(Double.MAX_VALUE);
        row2.getChildren().addAll(
                createExchangeButton("OANDA", "oanda-btn"),
                createExchangeButton("BITFINEX", "bitfinex-btn")
        );

        HBox row3 = new HBox(6);
        row3.setMaxWidth(Double.MAX_VALUE);
        row3.getChildren().addAll(
                createExchangeButton("BINANCE_US", "binance-us-btn")
        );

        makeButtonsGrow(row1);
        makeButtonsGrow(row2);
        makeButtonsGrow(row3);

        buttonsContainer.getChildren().addAll(row1, row2, row3);
        return buttonsContainer;
    }

    private void makeButtonsGrow(HBox row) {
        for (javafx.scene.Node child : row.getChildren()) {
            HBox.setHgrow(child, Priority.ALWAYS);
            if (child instanceof Button button) {
                button.setMaxWidth(Double.MAX_VALUE);
            }
        }
    }

    private Button createExchangeButton(String exchangeName, String styleId) {
        Button button = new Button(displayExchangeName(exchangeName));
        button.setId(styleId);
        button.getStyleClass().addAll("desk-action-button", "exchange-nav-button");
        button.setPrefHeight(34);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setTooltip(new Tooltip("Switch to " + displayExchangeName(exchangeName)));
        button.setOnAction(event -> selectExchange(exchangeName));
        return button;
    }

    private HBox createControlButtonsBox() {
        HBox controlBox = new HBox(8);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        controlBox.getStyleClass().add("exchange-control-buttons");
        controlBox.setPadding(new Insets(4, 0, 0, 0));

        Button connectButton = new Button("Connect");
        connectButton.getStyleClass().addAll("success-button", "exchange-connect-button");
        connectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setTooltip(new Tooltip("Connect to the selected exchange."));
        connectButton.setOnAction(event -> connectToExchange());

        Button disconnectButton = new Button("Disconnect");
        disconnectButton.getStyleClass().addAll("danger-button", "exchange-disconnect-button");
        disconnectButton.setMaxWidth(Double.MAX_VALUE);
        disconnectButton.setTooltip(new Tooltip("Disconnect from the selected exchange."));
        disconnectButton.setOnAction(event -> disconnectFromExchange());

        HBox.setHgrow(connectButton, Priority.ALWAYS);
        HBox.setHgrow(disconnectButton, Priority.ALWAYS);

        controlBox.getChildren().addAll(connectButton, disconnectButton);
        return controlBox;
    }

    private void selectExchange(String exchangeName) {
        if (exchangeName == null || exchangeName.isBlank()) {
            return;
        }

        String normalizedExchange = normalizeExchangeName(exchangeName);

        if (!exchangeSelector.getItems().contains(normalizedExchange)) {
            log.warn("Exchange '{}' is not available in selector items: {}", normalizedExchange, exchangeSelector.getItems());
            return;
        }

        if (!Objects.equals(exchangeSelector.getValue(), normalizedExchange)) {
            exchangeSelector.setValue(normalizedExchange);
        }

        currentExchangeLabel.setText("Current: " + displayExchangeName(normalizedExchange));

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

        connectionStatusLabel.setText("Status: Connecting...");
        connectionStatusLabel.getStyleClass().removeAll("connected", "disconnected", "connecting");
        connectionStatusLabel.getStyleClass().add("connecting");

        /*
         * Real connection should be handled by TradingDesk/SystemCore.
         * This panel only updates local UI state and notifies callbacks.
         */
    }

    private void disconnectFromExchange() {
        String currentExchange = getSelectedExchange();
        log.info("Disconnecting from exchange: {}", currentExchange);
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
                connectionStatusLabel.setText("Status: Connected");
                connectionStatusLabel.getStyleClass().add("connected");
            } else {
                connectionStatusLabel.setText("Status: Disconnected");
                connectionStatusLabel.getStyleClass().add("disconnected");
            }
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
        String configured = AppConfig.get(AppConfigKeys.DEFAULT_EXCHANGE, "OANDA");
        String normalized = normalizeExchangeName(configured);

        boolean exists = AVAILABLE_EXCHANGES.stream()
                .map(Enum::name)
                .anyMatch(name -> name.equalsIgnoreCase(normalized));

        if (exists) {
            return normalized;
        }

        return AVAILABLE_EXCHANGES.isEmpty() ? "OANDA" : AVAILABLE_EXCHANGES.getFirst().name();
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
}