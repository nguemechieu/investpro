package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.NotificationMessage;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.service.NotificationService;
import org.investpro.utils.Side;

import java.util.concurrent.CompletableFuture;

/**
 * Manual Trade Panel for users to place orders directly.
 * Supports market, limit, stop, and bracket orders with full control.
 */
@Slf4j
public class ManualTradePanel extends VBox {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ManualTradePanel.class);

    private final Exchange exchange;
    private final TradePair tradePair;
    private final Runnable onTradeSubmitted;
    private final NotificationService notificationService;

    // Order Type Selection
    private final ComboBox<String> orderTypeCombo = new ComboBox<>();

    // Common Fields
    private final Spinner<Double> quantitySpinner;
    private final TextArea noteArea = new TextArea();

    // Market Order Fields
    private final Label marketInfoLabel = new Label("Market order will execute immediately at market price");

    // Limit Order Fields
    private final Spinner<Double> limitPriceSpinner;

    // Stop Order Fields
    private final Spinner<Double> stopPriceSpinner;

    // Bracket Order Fields
    private final Spinner<Double> entryPriceSpinner;
    private final Spinner<Double> stopLossSpinner;
    private final Spinner<Double> takeProfitSpinner;

    // Order details
    private final Label estimatedCostLabel = new Label("Est. Cost: --");
    private final Label riskRewardLabel = new Label("Risk/Reward: --");

    // Buttons
    private final Button buyButton = new Button("BUY");
    private final Button sellButton = new Button("SELL");
    private final Button resetButton = new Button("RESET");

    public ManualTradePanel(Exchange exchange, TradePair tradePair, Runnable onTradeSubmitted,
            NotificationService notificationService) {
        this.exchange = exchange;
        this.tradePair = tradePair;
        this.onTradeSubmitted = onTradeSubmitted;
        this.notificationService = notificationService != null ? notificationService : NotificationService.disabled();

        // Initialize spinners
        this.quantitySpinner = new Spinner<>(0.0, 1000.0, 1.0, 0.1);
        this.limitPriceSpinner = new Spinner<>(0.0, 100000.0, 0.0, 0.01);
        this.stopPriceSpinner = new Spinner<>(0.0, 100000.0, 0.0, 0.01);
        this.entryPriceSpinner = new Spinner<>(0.0, 100000.0, 0.0, 0.01);
        this.stopLossSpinner = new Spinner<>(0.0, 100000.0, 0.0, 0.01);
        this.takeProfitSpinner = new Spinner<>(0.0, 100000.0, 0.0, 0.01);

        setupUI();
    }

    private void setupUI() {
        setSpacing(12);
        setPadding(new Insets(15));
        setStyle("-fx-border-color: #1a1a1a; -fx-background-color: #0a0a0a;");

        // Title
        Label titleLabel = new Label("MANUAL TRADE PANEL");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #00ff00;");

        Label pairLabel = new Label("Pair: " + tradePair.getSymbol());
        pairLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #888;");

        VBox titleBox = new VBox(5);
        titleBox.getChildren().addAll(titleLabel, pairLabel);
        getChildren().add(titleBox);

        getChildren().add(new Separator());

        // Order Type Selection
        getChildren().add(createOrderTypeSection());

        getChildren().add(new Separator());

        // Common Fields (Quantity, Notes)
        getChildren().add(createCommonFieldsSection());

        getChildren().add(new Separator());

        // Order-Specific Fields
        getChildren().add(createOrderSpecificSection());

        getChildren().add(new Separator());

        // Risk/Reward Info
        VBox infoBox = new VBox(5);
        infoBox.setStyle("-fx-border-color: #333; -fx-border-radius: 3; -fx-padding: 8;");
        estimatedCostLabel.setStyle("-fx-text-fill: #ffaa00;");
        riskRewardLabel.setStyle("-fx-text-fill: #ff6666;");
        infoBox.getChildren().addAll(estimatedCostLabel, riskRewardLabel);
        getChildren().add(infoBox);

        getChildren().add(new Separator());

        // Buttons
        getChildren().add(createButtonSection());

        // Event Handlers
        setupEventHandlers();
    }

    private VBox createOrderTypeSection() {
        orderTypeCombo.getItems().addAll("MARKET", "LIMIT", "STOP", "BRACKET");
        orderTypeCombo.setValue("MARKET");
        orderTypeCombo.setStyle("-fx-font-size: 11;");

        Label label = new Label("Order Type:");
        label.setStyle("-fx-text-fill: #cccccc;");

        VBox box = new VBox(5);
        box.getChildren().addAll(label, orderTypeCombo);
        return box;
    }

    private VBox createCommonFieldsSection() {
        VBox box = new VBox(8);

        // Quantity
        Label quantityLabel = new Label("Quantity:");
        quantityLabel.setStyle("-fx-text-fill: #cccccc;");
        quantitySpinner.setPrefWidth(Double.MAX_VALUE);
        quantitySpinner.setStyle("-fx-font-size: 11;");
        box.getChildren().addAll(quantityLabel, quantitySpinner);

        // Notes
        Label notesLabel = new Label("Notes (optional):");
        notesLabel.setStyle("-fx-text-fill: #cccccc;");
        noteArea.setPrefHeight(60);
        noteArea.setWrapText(true);
        noteArea.setStyle("-fx-font-size: 10; -fx-control-inner-background: #1a1a1a; -fx-text-fill: #cccccc;");
        box.getChildren().addAll(notesLabel, noteArea);

        return box;
    }

    private VBox createOrderSpecificSection() {
        VBox mainBox = new VBox(10);

        // Market Order
        VBox marketBox = createMarketOrderSection();

        // Limit Order
        VBox limitBox = createLimitOrderSection();

        // Stop Order
        VBox stopBox = createStopOrderSection();

        // Bracket Order
        VBox bracketBox = createBracketOrderSection();

        // Stack them and show/hide based on selection
        mainBox.getChildren().addAll(marketBox, limitBox, stopBox, bracketBox);

        orderTypeCombo.setOnAction(event -> {
            String selectedType = orderTypeCombo.getValue();
            marketBox.setVisible("MARKET".equals(selectedType));
            limitBox.setVisible("LIMIT".equals(selectedType));
            stopBox.setVisible("STOP".equals(selectedType));
            bracketBox.setVisible("BRACKET".equals(selectedType));
        });

        // Initialize visibility
        limitBox.setVisible(false);
        stopBox.setVisible(false);
        bracketBox.setVisible(false);

        return mainBox;
    }

    private VBox createMarketOrderSection() {
        VBox box = new VBox(5);
        box.setStyle("-fx-border-color: #333; -fx-border-radius: 3; -fx-padding: 8;");
        marketInfoLabel.setStyle("-fx-text-fill: #88ff88; -fx-font-size: 10;");
        box.getChildren().add(marketInfoLabel);
        return box;
    }

    private VBox createLimitOrderSection() {
        VBox box = new VBox(8);
        box.setStyle("-fx-border-color: #333; -fx-border-radius: 3; -fx-padding: 8;");

        Label label = new Label("Limit Price:");
        label.setStyle("-fx-text-fill: #cccccc;");
        limitPriceSpinner.setPrefWidth(Double.MAX_VALUE);
        limitPriceSpinner.setStyle("-fx-font-size: 11;");

        box.getChildren().addAll(label, limitPriceSpinner);
        return box;
    }

    private VBox createStopOrderSection() {
        VBox box = new VBox(8);
        box.setStyle("-fx-border-color: #333; -fx-border-radius: 3; -fx-padding: 8;");

        Label label = new Label("Stop Price:");
        label.setStyle("-fx-text-fill: #cccccc;");
        stopPriceSpinner.setPrefWidth(Double.MAX_VALUE);
        stopPriceSpinner.setStyle("-fx-font-size: 11;");

        box.getChildren().addAll(label, stopPriceSpinner);
        return box;
    }

    private VBox createBracketOrderSection() {
        VBox box = new VBox(8);
        box.setStyle("-fx-border-color: #333; -fx-border-radius: 3; -fx-padding: 8;");

        // Entry Price
        Label entryLabel = new Label("Entry Price:");
        entryLabel.setStyle("-fx-text-fill: #cccccc;");
        entryPriceSpinner.setPrefWidth(Double.MAX_VALUE);
        entryPriceSpinner.setStyle("-fx-font-size: 11;");

        // Stop Loss
        Label slLabel = new Label("Stop Loss:");
        slLabel.setStyle("-fx-text-fill: #ff6666;");
        stopLossSpinner.setPrefWidth(Double.MAX_VALUE);
        stopLossSpinner.setStyle("-fx-font-size: 11;");

        // Take Profit
        Label tpLabel = new Label("Take Profit:");
        tpLabel.setStyle("-fx-text-fill: #88ff88;");
        takeProfitSpinner.setPrefWidth(Double.MAX_VALUE);
        takeProfitSpinner.setStyle("-fx-font-size: 11;");

        box.getChildren().addAll(
                entryLabel, entryPriceSpinner,
                slLabel, stopLossSpinner,
                tpLabel, takeProfitSpinner);
        return box;
    }

    private HBox createButtonSection() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER);

        buyButton.setStyle("-fx-font-size: 12; -fx-padding: 10; -fx-font-weight: bold; " +
                "-fx-background-color: #004400; -fx-text-fill: #00ff00;");
        buyButton.setPrefWidth(120);

        sellButton.setStyle("-fx-font-size: 12; -fx-padding: 10; -fx-font-weight: bold; " +
                "-fx-background-color: #440000; -fx-text-fill: #ff0000;");
        sellButton.setPrefWidth(120);

        resetButton.setStyle("-fx-font-size: 11; -fx-padding: 8; -fx-background-color: #333;");
        resetButton.setPrefWidth(100);

        box.getChildren().addAll(buyButton, sellButton, resetButton);
        return box;
    }

    private void setupEventHandlers() {
        buyButton.setOnAction(event -> submitOrder(Side.BUY));
        sellButton.setOnAction(event -> submitOrder(Side.SELL));
        resetButton.setOnAction(event -> resetForm());

        // Update cost estimate when quantity changes
        quantitySpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateEstimates());
        limitPriceSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateEstimates());
        stopPriceSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateEstimates());
        entryPriceSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateEstimates());
        stopLossSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateEstimates());
        takeProfitSpinner.valueProperty().addListener((obs, oldVal, newVal) -> updateEstimates());
    }

    private void submitOrder(Side side) {
        String orderType = orderTypeCombo.getValue();
        double quantity = quantitySpinner.getValue();

        if (quantity <= 0) {
            showAlert("Error", "Quantity must be greater than 0");
            return;
        }

        try {
            switch (orderType) {
                case "MARKET" -> submitMarketOrder(side, quantity);
                case "LIMIT" -> submitLimitOrder(side, quantity);
                case "STOP" -> submitStopOrder(side, quantity);
                case "BRACKET" -> submitBracketOrder(side, quantity);
            }
        } catch (Exception e) {
            showAlert("Error", "Failed to submit order: " + e.getMessage());
            log.error("Order submission failed", e);
        }
    }

    private void submitMarketOrder(Side side, double quantity) {
        CompletableFuture<String> future = exchange.createMarketOrder(tradePair, side, quantity);
        future.thenAccept(orderId -> {
            log.info("Market order placed: {} {} {} - Order ID: {}", side, quantity, tradePair.getSymbol(), orderId);
            showAlert("Success", "Market order placed successfully\nOrder ID: " + orderId);
            notificationService.notify(NotificationMessage.trade(
                    "Market Order Placed",
                    "Market order placed for " + quantity + " " + tradePair.getSymbol() + " at " + side + "\nOrder ID: "
                            + orderId));
            onTradeSubmitted.run();
            resetForm();
        }).exceptionally(ex -> {
            log.error("Market order failed", ex);
            showAlert("Error", "Failed to place market order: " + ex.getMessage());
            notificationService.notify(NotificationMessage.warning(
                    "Market Order Failed",
                    "Failed to place market order for " + tradePair.getSymbol() + ": " + ex.getMessage()));
            return null;
        });
    }

    private void submitLimitOrder(Side side, double quantity) {
        double limitPrice = limitPriceSpinner.getValue();
        if (limitPrice <= 0) {
            showAlert("Error", "Limit price must be greater than 0");
            return;
        }

        CompletableFuture<String> future = exchange.createLimitOrder(tradePair, side, quantity, limitPrice);
        future.thenAccept(orderId -> {
            log.info("Limit order placed: {} {} @ {} - Order ID: {}", side, quantity, limitPrice, orderId);
            showAlert("Success", "Limit order placed successfully\nOrder ID: " + orderId);
            notificationService.notify(NotificationMessage.trade(
                    "Limit Order Placed",
                    "Limit order placed for " + quantity + " " + tradePair.getSymbol() + " @ " + limitPrice + " " + side
                            + "\nOrder ID: " + orderId));
            onTradeSubmitted.run();
            resetForm();
        }).exceptionally(ex -> {
            log.error("Limit order failed", ex);
            showAlert("Error", "Failed to place limit order: " + ex.getMessage());
            notificationService.notify(NotificationMessage.warning(
                    "Limit Order Failed",
                    "Failed to place limit order for " + tradePair.getSymbol() + ": " + ex.getMessage()));
            return null;
        });
    }

    private void submitStopOrder(Side side, double quantity) {
        double stopPrice = stopPriceSpinner.getValue();
        if (stopPrice <= 0) {
            showAlert("Error", "Stop price must be greater than 0");
            return;
        }

        CompletableFuture<String> future = exchange.createStopOrder(tradePair, side, quantity, stopPrice);
        future.thenAccept(orderId -> {
            log.info("Stop order placed: {} {} @ {} - Order ID: {}", side, quantity, stopPrice, orderId);
            showAlert("Success", "Stop order placed successfully\nOrder ID: " + orderId);
            notificationService.notify(NotificationMessage.trade(
                    "Stop Order Placed",
                    "Stop order placed for " + quantity + " " + tradePair.getSymbol() + " @ " + stopPrice + " " + side
                            + "\nOrder ID: " + orderId));
            onTradeSubmitted.run();
            resetForm();
        }).exceptionally(ex -> {
            log.error("Stop order failed", ex);
            showAlert("Error", "Failed to place stop order: " + ex.getMessage());
            notificationService.notify(NotificationMessage.warning(
                    "Stop Order Failed",
                    "Failed to place stop order for " + tradePair.getSymbol() + ": " + ex.getMessage()));
            return null;
        });
    }

    private void submitBracketOrder(Side side, double quantity) {
        double entryPrice = entryPriceSpinner.getValue();
        double stopLoss = stopLossSpinner.getValue();
        double takeProfit = takeProfitSpinner.getValue();

        if (entryPrice <= 0 || stopLoss <= 0 || takeProfit <= 0) {
            showAlert("Error", "All prices must be greater than 0");
            return;
        }

        if (side == Side.BUY && (stopLoss >= entryPrice || takeProfit <= entryPrice)) {
            showAlert("Error", "For BUY: Stop Loss < Entry Price < Take Profit");
            return;
        }

        if (side == Side.SELL && (stopLoss <= entryPrice || takeProfit >= entryPrice)) {
            showAlert("Error", "For SELL: Take Profit < Entry Price < Stop Loss");
            return;
        }

        CompletableFuture<String> future = exchange.createBracketOrder(
                tradePair, side, quantity, entryPrice, stopLoss, takeProfit);

        future.thenAccept(orderId -> {
            log.info("Bracket order placed: {} {} @ {} SL:{} TP:{} - Order ID: {}",
                    side, quantity, entryPrice, stopLoss, takeProfit, orderId);
            showAlert("Success", "Bracket order placed successfully\nOrder ID: " + orderId);
            notificationService.notify(NotificationMessage.trade(
                    "Bracket Order Placed",
                    "Bracket order placed for " + quantity + " " + tradePair.getSymbol() + " @ " + entryPrice + " "
                            + side
                            + "\nStop Loss: " + stopLoss + " | Take Profit: " + takeProfit
                            + "\nOrder ID: " + orderId));
            onTradeSubmitted.run();
            resetForm();
        }).exceptionally(ex -> {
            log.error("Bracket order failed", ex);
            showAlert("Error", "Failed to place bracket order: " + ex.getMessage());
            notificationService.notify(NotificationMessage.warning(
                    "Bracket Order Failed",
                    "Failed to place bracket order for " + tradePair.getSymbol() + ": " + ex.getMessage()));
            return null;
        });
    }

    private void updateEstimates() {
        double quantity = quantitySpinner.getValue();
        String orderType = orderTypeCombo.getValue();

        double price = 0;
        switch (orderType) {
            case "LIMIT" -> price = limitPriceSpinner.getValue();
            case "STOP" -> price = stopPriceSpinner.getValue();
            case "BRACKET" -> price = entryPriceSpinner.getValue();
        }

        if (price > 0) {
            double cost = quantity * price;
            estimatedCostLabel.setText(String.format("Est. Cost: %.2f", cost));
        }

        if ("BRACKET".equals(orderType)) {
            double entry = entryPriceSpinner.getValue();
            double sl = stopLossSpinner.getValue();
            double tp = takeProfitSpinner.getValue();

            if (entry > 0 && sl > 0 && tp > 0) {
                double risk = Math.abs(entry - sl) * quantity;
                double reward = Math.abs(tp - entry) * quantity;
                double ratio = reward > 0 ? reward / risk : 0;
                riskRewardLabel.setText(String.format("Risk/Reward: 1:%.2f", ratio));
            }
        }
    }

    private void resetForm() {
        quantitySpinner.getValueFactory().setValue(1.0);
        limitPriceSpinner.getValueFactory().setValue(0.0);
        stopPriceSpinner.getValueFactory().setValue(0.0);
        entryPriceSpinner.getValueFactory().setValue(0.0);
        stopLossSpinner.getValueFactory().setValue(0.0);
        takeProfitSpinner.getValueFactory().setValue(0.0);
        noteArea.clear();
        orderTypeCombo.setValue("MARKET");
        estimatedCostLabel.setText("Est. Cost: --");
        riskRewardLabel.setText("Risk/Reward: --");
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
