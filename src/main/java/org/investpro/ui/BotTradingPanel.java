package org.investpro.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.investpro.exchange.infrastructure.BotTradingConfig;
import org.investpro.exchange.Coinbase;
import org.investpro.models.trading.TradePair;

import java.util.function.Consumer;

/**
 * UI panel for configuring and controlling bot trading
 */
public class BotTradingPanel extends VBox {
    private final Coinbase exchange;
    private final BotTradingConfig botConfig;
    private final CheckBox enabledCheckbox;
    private final ListView<String> symbolsListView;
    private final Spinner<Double> tradeSizeSpinner;
    private final Spinner<Double> stopLossSpinner;
    private final Spinner<Double> takeProfitSpinner;
    private final TextField signalInputField;
    private final Label statusLabel;
    private Consumer<String> onSignalSent;
    
    public BotTradingPanel(Coinbase exchange) {
        this.exchange = exchange;
        this.botConfig = exchange.getBotConfig();
        
        // Create components
        this.enabledCheckbox = new CheckBox("Enable Bot Trading");
        this.symbolsListView = new ListView<>();
        this.tradeSizeSpinner = new Spinner<>(0.001, 100.0, 1.0, 0.1);
        this.stopLossSpinner = new Spinner<>(0.0, 100.0, 0.0, 1.0);
        this.takeProfitSpinner = new Spinner<>(0.0, 100.0, 0.0, 1.0);
        this.signalInputField = new TextField();
        this.statusLabel = new Label("Bot Status: DISABLED");
        
        setupUI();
        setupListeners();
        refreshSymbolsList();
    }
    
    private void setupUI() {
        setStyle("-fx-border-color: #263246; " +
                "-fx-border-width: 1; " +
                "-fx-background-color: #0a0e17; " +
                "-fx-padding: 10;");
        setSpacing(12);
        setPadding(new Insets(10));
        
        // Title
        Label titleLabel = new Label("Bot Trading Control");
        titleLabel.setStyle("-fx-text-fill: #e5edf7; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        // Status
        statusLabel.setStyle("-fx-text-fill: #FF9500; -fx-font-size: 12px; -fx-padding: 5;");
        
        // Enable/Disable
        enabledCheckbox.setStyle("-fx-text-fill: #e5edf7; -fx-padding: 5;");
        enabledCheckbox.setSelected(botConfig.isEnabled());
        
        HBox enableBox = new HBox(15);
        enableBox.getChildren().addAll(enabledCheckbox, statusLabel);
        
        // Configured Symbols
        Label symbolsLabel = new Label("Trading Symbols:");
        symbolsLabel.setStyle("-fx-text-fill: #9aa7ba; -fx-font-weight: bold;");
        
        symbolsListView.setPrefHeight(120);
        symbolsListView.setStyle("-fx-background-color: #0f1724; " +
                "-fx-text-fill: #e5edf7; " +
                "-fx-border-color: #2a3548;");
        
        // Trade Settings
        Label settingsLabel = new Label("Trade Settings:");
        settingsLabel.setStyle("-fx-text-fill: #9aa7ba; -fx-font-weight: bold;");
        
        HBox tradeSizeBox = createLabeledControl("Trade Size:", tradeSizeSpinner);
        tradeSizeSpinner.setPrefWidth(120);
        tradeSizeSpinner.setStyle("-fx-background-color: #0f1724; -fx-text-fill: #e5edf7;");
        
        HBox stopLossBox = createLabeledControl("Stop Loss %:", stopLossSpinner);
        stopLossSpinner.setPrefWidth(120);
        stopLossSpinner.setStyle("-fx-background-color: #0f1724; -fx-text-fill: #e5edf7;");
        
        HBox takeProfitBox = createLabeledControl("Take Profit %:", takeProfitSpinner);
        takeProfitSpinner.setPrefWidth(120);
        takeProfitSpinner.setStyle("-fx-background-color: #0f1724; -fx-text-fill: #e5edf7;");
        
        // Signal Input
        Label signalLabel = new Label("Manual Signal (BUY/SELL/CLOSE):");
        signalLabel.setStyle("-fx-text-fill: #9aa7ba; -fx-font-weight: bold;");
        
        signalInputField.setPromptText("Enter signal (e.g., BUY, SELL, CLOSE)");
        signalInputField.setStyle("-fx-background-color: #0f1724; " +
                "-fx-text-fill: #e5edf7; " +
                "-fx-border-color: #2a3548; " +
                "-fx-padding: 8;");
        
        Button sendSignalButton = new Button("Send Signal");
        sendSignalButton.setStyle("-fx-background-color: #00D9FF; " +
                "-fx-text-fill: #000; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 8 16;");
        sendSignalButton.setPrefWidth(120);
        sendSignalButton.setOnAction(e -> sendSignal());
        
        HBox signalBox = new HBox(10);
        signalBox.getChildren().addAll(signalInputField, sendSignalButton);
        HBox.setHgrow(signalInputField, Priority.ALWAYS);
        
        // Apply Settings Button
        Button applyButton = new Button("Apply Settings");
        applyButton.setStyle("-fx-background-color: #00D9FF; " +
                "-fx-text-fill: #000; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 10 20;");
        applyButton.setPrefWidth(150);
        applyButton.setOnAction(e -> applySettings());
        
        // Add all components
        getChildren().addAll(
            titleLabel,
            new Separator(),
            enableBox,
            new Separator(),
            symbolsLabel,
            symbolsListView,
            new Separator(),
            settingsLabel,
            tradeSizeBox,
            stopLossBox,
            takeProfitBox,
            new Separator(),
            signalLabel,
            signalBox,
            new Separator(),
            applyButton
        );
    }
    
    private HBox createLabeledControl(String label, Control control) {
        HBox box = new HBox(10);
        box.setStyle("-fx-alignment: CENTER_LEFT;");
        Label labelControl = new Label(label);
        labelControl.setStyle("-fx-text-fill: #9aa7ba; -fx-min-width: 120;");
        box.getChildren().addAll(labelControl, control);
        HBox.setHgrow(control, Priority.ALWAYS);
        return box;
    }
    
    private void setupListeners() {
        enabledCheckbox.selectedProperty().addListener((obs, old, newVal) -> {
            exchange.autoTrading(newVal, null);
            updateStatus();
        });
        
        tradeSizeSpinner.valueProperty().addListener((obs, old, newVal) -> {
            botConfig.setTradeSize(newVal);
        });
        
        stopLossSpinner.valueProperty().addListener((obs, old, newVal) -> {
            botConfig.setStopLoss(newVal);
        });
        
        takeProfitSpinner.valueProperty().addListener((obs, old, newVal) -> {
            botConfig.setTakeProfit(newVal);
        });
    }
    
    private void sendSignal() {
        String signal = signalInputField.getText().trim().toUpperCase();
        
        if (signal.isEmpty()) {
            showWarning("Please enter a signal");
            return;
        }
        
        if (!botConfig.isEnabled()) {
            showWarning("Bot trading is disabled");
            return;
        }
        
        if (botConfig.getTradingSymbols().isEmpty()) {
            showWarning("No symbols configured for bot trading");
            return;
        }
        
        exchange.processTradeSignal(signal);
        signalInputField.clear();
        
        if (onSignalSent != null) {
            onSignalSent.accept(signal);
        }
        
        showInfo("Signal sent: " + signal);
    }
    
    private void applySettings() {
        double size = tradeSizeSpinner.getValue();
        double sl = stopLossSpinner.getValue();
        double tp = takeProfitSpinner.getValue();
        
        botConfig.setTradeSize(size);
        botConfig.setStopLoss(sl);
        botConfig.setTakeProfit(tp);
        
        showInfo("Bot settings applied");
    }
    
    private void refreshSymbolsList() {
        ObservableList<String> items = FXCollections.observableArrayList();
        
        for (TradePair symbol : botConfig.getTradingSymbols()) {
            items.add(symbol.toString());
        }
        
        if (items.isEmpty()) {
            items.add("No symbols configured");
        }
        
        symbolsListView.setItems(items);
    }
    
    private void updateStatus() {
        if (botConfig.isEnabled()) {
            statusLabel.setText("Bot Status: ENABLED ✓");
            statusLabel.setStyle("-fx-text-fill: #00FF00; -fx-font-size: 12px; -fx-padding: 5;");
        } else {
            statusLabel.setText("Bot Status: DISABLED");
            statusLabel.setStyle("-fx-text-fill: #FF9500; -fx-font-size: 12px; -fx-padding: 5;");
        }
    }
    
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }
    
    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }
    
    public void setOnSignalSent(Consumer<String> callback) {
        this.onSignalSent = callback;
    }
    
    public void updateSymbolsList(java.util.List<TradePair> symbols) {
        botConfig.setTradingSymbols(symbols);
        refreshSymbolsList();
    }
}
