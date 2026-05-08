package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;

import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Order Panel - Interactive order creation and placement interface.
 * Allows users to place BUY/SELL orders with various order types (MARKET,
 * LIMIT, etc.)
 * Features bid/ask visualization, risk management with take-profit/stop-loss,
 * and expiration control.
 */
@Slf4j
@Getter
@Setter
public class OrderPanel extends BorderPane {

    private ComboBox<String> symbolCombo;
    private ComboBox<OpenOrder.OrderType> orderTypeCombo;
    private ComboBox<Side> sideCombo;
    private Spinner<Double> volumeSpinner;
    private Spinner<Double> takeProfitSpinner;
    private Spinner<Double> stopLossSpinner;
    private TextArea commentArea;
    private TextField priceField;
    private DatePicker expirationDatePicker;
    private Label currentPriceLabel;
    private Label bidAskLabel;
    private Button executeButton;
    private Button cancelButton;

    private double currentPrice = 0.0;
    private double bidPrice = 0.0;
    private double askPrice = 0.0;

    public OrderPanel(List<String> availableSymbols, TradePair selectedSymbol, OrderBook orderBook) {
        setPrefSize(1000, 700);
        setStyle("-fx-background-color: #1a1a2e; -fx-border-color: #374151; -fx-border-width: 1;");

        // Update current prices from order book
        if (orderBook != null && !orderBook.getBids().isEmpty() && !orderBook.getAsks().isEmpty()) {
            this.bidPrice = orderBook.getBids().getFirst().getPrice();
            this.askPrice = orderBook.getAsks().getFirst().getPrice();
            this.currentPrice = (bidPrice + askPrice) / 2;
        }

        setupUI(availableSymbols, selectedSymbol);
    }

    private void setupUI(List<String> availableSymbols, TradePair selectedSymbol) {
        // Header
        Label titleLabel = new Label("Place Order");
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        HBox headerBox = new HBox(titleLabel);
        headerBox.setPadding(new Insets(16));
        headerBox.setStyle("-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 0 0 1 0;");
        setTop(headerBox);

        // Main Content Area
        HBox mainContent = new HBox(16);
        mainContent.setPadding(new Insets(16));
        mainContent.setStyle("-fx-background-color: #1a1a2e;");

        // Left panel - Chart and Bid/Ask Info
        VBox leftPanel = createLeftPanel(selectedSymbol);
        HBox.setHgrow(leftPanel, Priority.ALWAYS);

        // Right panel - Order form
        VBox rightPanel = createRightPanel(availableSymbols, selectedSymbol);
        rightPanel.setPrefWidth(350);

        mainContent.getChildren().addAll(leftPanel, createDivider(), rightPanel);
        setCenter(mainContent);

        // Bottom panel - Buttons
        HBox bottomBox = createBottomPanel();
        setBottom(bottomBox);
    }

    private VBox createLeftPanel(TradePair selectedSymbol) {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(12));
        panel.setStyle(
                "-fx-background-color: #0f3460; -fx-border-color: #374151; -fx-border-width: 1; -fx-border-radius: 6;");

        // Title
        Label titleLabel = new Label("Market Information");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Symbol display
        Label symbolLabel = new Label(selectedSymbol != null ? selectedSymbol.getSymbol() : "N/A");
        symbolLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

        // Price information
        currentPriceLabel = new Label(String.format("Current: $%.2f", currentPrice));
        currentPriceLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #a0aec0;");

        // Bid/Ask information
        bidAskLabel = new Label(String.format("Bid: $%.2f | Ask: $%.2f | Spread: $%.4f",
                bidPrice, askPrice, Math.abs(askPrice - bidPrice)));
        bidAskLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #10b981;");

        // Placeholder for chart
        VBox chartArea = new VBox();
        chartArea.setStyle("-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1;");
        chartArea.setPrefHeight(300);
        chartArea.setAlignment(Pos.CENTER);
        Label chartPlaceholder = new Label("📊 Bid/Ask Chart\n(Real-time chart would display here)");
        chartPlaceholder.setStyle("-fx-font-size: 14px; -fx-text-fill: #a0aec0;");
        chartArea.getChildren().add(chartPlaceholder);
        VBox.setVgrow(chartArea, Priority.ALWAYS);

        panel.getChildren().addAll(
                titleLabel,
                symbolLabel,
                currentPriceLabel,
                bidAskLabel,
                new Separator(),
                chartArea);

        return panel;
    }

    private VBox createRightPanel(List<String> availableSymbols, TradePair selectedSymbol) {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(12));
        panel.setStyle(
                "-fx-background-color: #0f3460; -fx-border-color: #374151; -fx-border-width: 1; -fx-border-radius: 6;");

        Label titleLabel = new Label("Order Details");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        // Symbol selection
        symbolCombo = new ComboBox<>();
        symbolCombo.getItems().addAll(availableSymbols);
        if (selectedSymbol != null) {
            symbolCombo.setValue(selectedSymbol.getSymbol());
        }
        symbolCombo.setPrefWidth(Double.MAX_VALUE);
        symbolCombo.setStyle("-fx-font-size: 12px;");
        HBox symbolBox = createLabeledControl("Symbol:", symbolCombo);

        // Volume
        volumeSpinner = new Spinner<>(0.0, 1000000.0, 1.0, 0.1);
        volumeSpinner.setEditable(true);
        volumeSpinner.setPrefWidth(Double.MAX_VALUE);
        HBox volumeBox = createLabeledControl("Volume:", volumeSpinner);

        // Take Profit
        takeProfitSpinner = new Spinner<>(0.0, 1000000.0, 0.0, 10.0);
        takeProfitSpinner.setEditable(true);
        takeProfitSpinner.setPrefWidth(Double.MAX_VALUE);
        HBox tpBox = createLabeledControl("Take Profit:", takeProfitSpinner);

        // Stop Loss
        stopLossSpinner = new Spinner<>(0.0, 1000000.0, 0.0, 10.0);
        stopLossSpinner.setEditable(true);
        stopLossSpinner.setPrefWidth(Double.MAX_VALUE);
        HBox slBox = createLabeledControl("Stop Loss:", stopLossSpinner);

        // Comment
        commentArea = new TextArea();
        commentArea.setWrapText(true);
        commentArea.setPrefHeight(70);
        commentArea.setStyle("-fx-font-size: 11px; -fx-control-inner-background: #16213e; -fx-text-fill: #ffffff;");
        commentArea.setPromptText("Optional comment...");
        Label commentLabel = new Label("Comment:");
        commentLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #a0aec0;");

        // Order Type
        orderTypeCombo = new ComboBox<>();
        orderTypeCombo.getItems().addAll(OpenOrder.OrderType.values());
        orderTypeCombo.setValue(OpenOrder.OrderType.MARKET);
        orderTypeCombo.setPrefWidth(Double.MAX_VALUE);
        orderTypeCombo.setStyle("-fx-font-size: 12px;");
        orderTypeCombo.setOnAction(event -> updateUIForOrderType());
        HBox orderTypeBox = createLabeledControl("Order Type:", orderTypeCombo);

        // Side (Buy/Sell)
        sideCombo = new ComboBox<>();
        sideCombo.getItems().addAll(Side.BUY, Side.SELL);
        sideCombo.setValue(Side.BUY);
        sideCombo.setPrefWidth(Double.MAX_VALUE);
        sideCombo.setStyle("-fx-font-size: 12px;");
        HBox sideBox = createLabeledControl("Side:", sideCombo);

        // Price field (hidden for market orders)
        priceField = new TextField();
        priceField.setText(String.format("%.2f", currentPrice));
        priceField.setPrefWidth(Double.MAX_VALUE);
        priceField.setStyle("-fx-font-size: 12px;");
        HBox priceBox = createLabeledControl("Price:", priceField);

        // Expiration date (hidden for market orders)
        expirationDatePicker = new DatePicker();
        expirationDatePicker.setValue(LocalDate.now().plusDays(1));
        expirationDatePicker.setPrefWidth(Double.MAX_VALUE);
        expirationDatePicker.setStyle("-fx-font-size: 12px;");
        HBox expirationBox = createLabeledControl("Expiration:", expirationDatePicker);

        // Add all controls to panel
        panel.getChildren().addAll(
                titleLabel,
                symbolBox,
                volumeBox,
                tpBox,
                slBox,
                commentLabel,
                commentArea,
                new Separator(),
                orderTypeBox,
                sideBox,
                priceBox,
                expirationBox);

        VBox.setVgrow(commentArea, Priority.NEVER);
        return panel;
    }

    private HBox createBottomPanel() {
        HBox bottomBox = new HBox(12);
        bottomBox.setPadding(new Insets(16));
        bottomBox.setAlignment(Pos.CENTER_RIGHT);
        bottomBox.setStyle("-fx-background-color: #16213e; -fx-border-color: #374151; -fx-border-width: 1 0 0 0;");

        executeButton = new Button("Buy");
        executeButton.setPrefWidth(120);
        executeButton.setPrefHeight(40);
        executeButton.setStyle(
                "-fx-background-color: #10b981; -fx-text-fill: white; " +
                        "-fx-font-size: 13px; -fx-font-weight: bold; " +
                        "-fx-border-radius: 4; -fx-padding: 10 20;");
        executeButton.setOnAction(event -> onExecuteOrder());

        cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(120);
        cancelButton.setPrefHeight(40);
        cancelButton.setStyle(
                "-fx-background-color: #ef4444; -fx-text-fill: white; " +
                        "-fx-font-size: 13px; -fx-font-weight: bold; " +
                        "-fx-border-radius: 4; -fx-padding: 10 20;");
        cancelButton.setOnAction(event -> onCancel());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomBox.getChildren().addAll(spacer, executeButton, cancelButton);
        return bottomBox;
    }

    private HBox createLabeledControl(String labelText, Control control) {
        Label label = new Label(labelText);
        label.setPrefWidth(80);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: #a0aec0;");
        HBox box = new HBox(8, label, control);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Separator createDivider() {
        Separator sep = new Separator();
        sep.setOrientation(javafx.geometry.Orientation.VERTICAL);
        sep.setStyle("-fx-opacity: 0.3;");
        return sep;
    }

    private void updateUIForOrderType() {
        OpenOrder.OrderType selectedType = orderTypeCombo.getValue();
        boolean isMarket = selectedType == OpenOrder.OrderType.MARKET;

        // Show/hide price field
        priceField.setDisable(isMarket);
        priceField.setStyle(isMarket ? "-fx-font-size: 12px; -fx-opacity: 0.5;" : "-fx-font-size: 12px;");

        // Show/hide expiration date
        expirationDatePicker.setDisable(isMarket);
        expirationDatePicker.setStyle(isMarket ? "-fx-font-size: 12px; -fx-opacity: 0.5;" : "-fx-font-size: 12px;");

        // Update button text and colors
        if (isMarket) {
            Side side = sideCombo.getValue();
            if (side == Side.BUY) {
                executeButton.setText("Buy");
                executeButton.setStyle(
                        "-fx-background-color: #10b981; -fx-text-fill: white; " +
                                "-fx-font-size: 13px; -fx-font-weight: bold; " +
                                "-fx-border-radius: 4; -fx-padding: 10 20;");
            } else {
                executeButton.setText("Sell");
                executeButton.setStyle(
                        "-fx-background-color: #ef4444; -fx-text-fill: white; " +
                                "-fx-font-size: 13px; -fx-font-weight: bold; " +
                                "-fx-border-radius: 4; -fx-padding: 10 20;");
            }
        } else {
            executeButton.setText("Place Order");
            executeButton.setStyle(
                    "-fx-background-color: #3b82f6; -fx-text-fill: white; " +
                            "-fx-font-size: 13px; -fx-font-weight: bold; " +
                            "-fx-border-radius: 4; -fx-padding: 10 20;");
        }

        // Update price field with current price for market orders
        if (isMarket) {
            priceField.setText(String.format("%.2f", currentPrice));
        }
    }

    private void onExecuteOrder() {
        try {
            String symbol = symbolCombo.getValue();
            double volume = volumeSpinner.getValue();
            double takeProfit = takeProfitSpinner.getValue();
            double stopLoss = stopLossSpinner.getValue();
            String comment = commentArea.getText();
            OpenOrder.OrderType orderType = orderTypeCombo.getValue();
            Side side = sideCombo.getValue();
            double price = Double.parseDouble(priceField.getText());
            LocalDate expirationDate = expirationDatePicker.getValue();

            if (symbol == null || symbol.isEmpty()) {
                showError("Please select a symbol");
                return;
            }

            if (volume <= 0) {
                showError("Volume must be greater than 0");
                return;
            }

            if (orderType != OpenOrder.OrderType.MARKET && price <= 0) {
                showError("Price must be greater than 0 for non-market orders");
                return;
            }

            // Check for news events if news filter is enabled
            String newsWarning = checkNewsEventConflict(symbol);
            if (newsWarning != null && !newsWarning.isEmpty()) {
                // Ask user if they want to proceed despite news events
                Alert newsAlert = new Alert(Alert.AlertType.WARNING);
                newsAlert.setTitle("News Event Warning");
                newsAlert.setHeaderText("Upcoming News Events Detected");
                newsAlert.setContentText(newsWarning + "\n\nDo you want to proceed with the order anyway?");

                ButtonType proceedButton = new ButtonType("Proceed Anyway");
                ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                newsAlert.getButtonTypes().setAll(proceedButton, cancelButton);

                Optional<ButtonType> result = newsAlert.showAndWait();
                if (result.isEmpty() || result.get() == cancelButton) {
                    log.info("Order cancelled due to news event conflict");
                    return;
                }
            }

            // Log order details
            log.info(
                    "Order placed - Symbol: {}, Type: {}, Side: {}, Volume: {}, Price: {}, TP: {}, SL: {}, Expiration: {}",
                    symbol, orderType, side, volume, price, takeProfit, stopLoss, expirationDate);

            showSuccess("Order placed successfully!\nSymbol: " + symbol + "\nSide: " + side + "\nVolume: " + volume);

        } catch (NumberFormatException e) {
            showError("Invalid price format");
        }
    }

    private void onCancel() {
        // Reset form
        volumeSpinner.getValueFactory().setValue(1.0);
        takeProfitSpinner.getValueFactory().setValue(0.0);
        stopLossSpinner.getValueFactory().setValue(0.0);
        commentArea.clear();
        priceField.setText(String.format("%.2f", currentPrice));
        expirationDatePicker.setValue(LocalDate.now().plusDays(1));
        log.info("Order form cleared");
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Order Error");
        alert.setHeaderText("Order Placement Failed");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Order Placed");
        alert.setHeaderText("Success");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void updateOrderBook(OrderBook orderBook) {
        if (orderBook != null && !orderBook.getBids().isEmpty() && !orderBook.getAsks().isEmpty()) {
            this.bidPrice = orderBook.getBids().getFirst().getPrice();
            this.askPrice = orderBook.getAsks().getFirst().getPrice();
            this.currentPrice = (bidPrice + askPrice) / 2;

            currentPriceLabel.setText(String.format("Current: $%.2f", currentPrice));
            bidAskLabel.setText(String.format("Bid: $%.2f | Ask: $%.2f | Spread: $%.4f",
                    bidPrice, askPrice, Math.abs(askPrice - bidPrice)));

            if (orderTypeCombo.getValue() == OpenOrder.OrderType.MARKET) {
                priceField.setText(String.format("%.2f", currentPrice));
            }
        }
    }

    /**
     * Check for upcoming news events that might conflict with order placement.
     * Returns a warning message if news events are nearby, null otherwise.
     */
    private String checkNewsEventConflict(String symbol) {
        // Extract currency from symbol (e.g., "BTC/USD" -> "USD")
        String currency = symbol.contains("/") ? symbol.split("/")[1] : symbol;

        // Placeholder for news event checking
        // In a real implementation, this would integrate with NewsDataProvider
        // and check for events near the current time based on the currency

        // Example: Check for upcoming news in the next 60 minutes for the currency
        Instant now = Instant.now();
        Instant nextHour = now.plusSeconds(3600);

        // This is a template - actual implementation would:
        // 1. Get NewsDataProvider instance
        // 2. Query for events in currency between now and nextHour
        // 3. Check filter window setting from TradingProfileSettingsPanel
        // 4. Return warning message if conflicts found

        // For now, return null (no warning)
        // TODO: Integrate with NewsDataProvider and check against actual news events
        return null;
    }
}
