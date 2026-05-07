package org.investpro.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.investpro.exchange.Coinbase;
import org.investpro.exchange.infrastructure.BotTradingConfig;
import org.investpro.models.trading.TradePair;
import org.investpro.ui.panels.TradingProfile;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * UI panel for configuring and controlling bot trading.
 */
public class BotTradingPanel extends VBox {
    private final Coinbase exchange;
    private final BotTradingConfig botConfig;
    private final CheckBox enabledCheckbox;
    private final ListView<String> symbolsListView;
    private final ComboBox<BotTradingConfig.SymbolTradingMode> symbolModeCombo;
    private final Spinner<Double> tradeSizeSpinner;
    private final Spinner<Double> stopLossSpinner;
    private final Spinner<Double> takeProfitSpinner;
    private final Spinner<Double> minProfitSpinner;
    private final Spinner<Double> maxPortfolioRiskSpinner;
    private final Spinner<Integer> minTimeBetweenTradesSpinner;
    private final TextField allowedSignalsField;
    private final Spinner<Double> leverageSpinner;
    private final ComboBox<BotTradingConfig.MarginMode> marginModeCombo;
    private final Spinner<Double> maxLeverageRiskSpinner;
    private final Spinner<Double> backtestRiskSpinner;
    private final Spinner<Double> backtestBalanceSpinner;
    private final Spinner<Double> backtestMaxDrawdownSpinner;
    private final CheckBox backtestUseRealFeesCheckbox;
    private final ComboBox<BotTradingConfig.StreamingMode> streamingModeCombo;
    private final CheckBox streamingEnabledCheckbox;
    private final Spinner<Integer> streamingUpdateIntervalSpinner;
    private final CheckBox useWebsocketsCheckbox;
    private final Spinner<Integer> maxWebsocketConnectionsSpinner;
    private final Spinner<Integer> maxOpenPositionsSpinner;
    private final Spinner<Double> maxDailyLossesSpinner;
    private final Spinner<Double> positionSizePercentSpinner;
    private final ComboBox<BotTradingConfig.PositionSizingStrategy> positionSizingCombo;
    private final CheckBox strictMoneyManagementCheckbox;
    private final CheckBox dynamicPositionSizingCheckbox;
    private final Spinner<Double> profitTakingSpinner;
    private final Spinner<Integer> trailingStopUpdateIntervalSpinner;
    private final CheckBox partialProfitTakingCheckbox;
    private final TextField signalInputField;
    private final Label statusLabel;
    private Consumer<String> onSignalSent;

    public BotTradingPanel(Coinbase exchange) {
        this.exchange = exchange;
        this.botConfig = exchange.getBotConfig();

        this.enabledCheckbox = new CheckBox("Enable Bot Trading");
        this.symbolsListView = new ListView<>();
        this.symbolModeCombo = new ComboBox<>(FXCollections.observableArrayList(BotTradingConfig.SymbolTradingMode.values()));
        this.tradeSizeSpinner = doubleSpinner(0.001, 100.0, botConfig.getTradeSize(), 0.1);
        this.stopLossSpinner = doubleSpinner(0.0, 100.0, botConfig.getStopLoss(), 0.25);
        this.takeProfitSpinner = doubleSpinner(0.0, 100.0, botConfig.getTakeProfit(), 0.25);
        this.minProfitSpinner = doubleSpinner(0.0, 100.0, botConfig.getMinProfitPercent(), 0.1);
        this.maxPortfolioRiskSpinner = doubleSpinner(0.0, 100.0, botConfig.getMaxPortfolioRiskPercent(), 0.25);
        this.minTimeBetweenTradesSpinner = intSpinner(1000, 3_600_000, (int) botConfig.getMinTimeBetweenTrades(), 1000);
        this.allowedSignalsField = new TextField();
        this.leverageSpinner = doubleSpinner(1.0, 100.0, botConfig.getLeverage(), 0.5);
        this.marginModeCombo = new ComboBox<>(FXCollections.observableArrayList(BotTradingConfig.MarginMode.values()));
        this.maxLeverageRiskSpinner = doubleSpinner(0.0, 100.0, botConfig.getMaxLeverageRisk(), 0.25);
        this.backtestRiskSpinner = doubleSpinner(0.1, 5.0, botConfig.getBacktestRiskPercentPerTrade(), 0.1);
        this.backtestBalanceSpinner = doubleSpinner(100.0, 10_000_000.0, botConfig.getBacktestStartingBalance(), 500.0);
        this.backtestMaxDrawdownSpinner = doubleSpinner(1.0, 100.0, botConfig.getBacktestMaxDrawdownPercent(), 0.5);
        this.backtestUseRealFeesCheckbox = new CheckBox("Use real fees");
        this.streamingModeCombo = new ComboBox<>(FXCollections.observableArrayList(BotTradingConfig.StreamingMode.values()));
        this.streamingEnabledCheckbox = new CheckBox("Enable streaming");
        this.streamingUpdateIntervalSpinner = intSpinner(100, 3_600_000, (int) botConfig.getStreamingUpdateInterval(), 100);
        this.useWebsocketsCheckbox = new CheckBox("Use WebSockets when available");
        this.maxWebsocketConnectionsSpinner = intSpinner(1, 20, botConfig.getMaxWebsocketConnections(), 1);
        this.maxOpenPositionsSpinner = intSpinner(1, 500, botConfig.getMaxOpenPositions(), 1);
        this.maxDailyLossesSpinner = doubleSpinner(0.0, 100.0, botConfig.getMaxDailyLosses(), 0.25);
        this.positionSizePercentSpinner = doubleSpinner(0.1, 50.0, botConfig.getPositionSizePercent(), 0.1);
        this.positionSizingCombo = new ComboBox<>(FXCollections.observableArrayList(BotTradingConfig.PositionSizingStrategy.values()));
        this.strictMoneyManagementCheckbox = new CheckBox("Strict money management");
        this.dynamicPositionSizingCheckbox = new CheckBox("Dynamic position sizing");
        this.profitTakingSpinner = doubleSpinner(0.0, 100.0, botConfig.getProfitTakingPercent(), 1.0);
        this.trailingStopUpdateIntervalSpinner = intSpinner(1000, 3_600_000, (int) botConfig.getTrailingStopUpdateInterval(), 1000);
        this.partialProfitTakingCheckbox = new CheckBox("Partial profit taking");
        this.signalInputField = new TextField();
        this.statusLabel = new Label();

        setupUI();
        loadControlsFromConfig();
        applyTradingProfile(TradingProfile.load());
        setupListeners();
        refreshSymbolsList();
    }

    private void setupUI() {
        setSpacing(12);
        setPadding(new Insets(14));
        getStyleClass().add("bot-trading-panel");
        setStyle("-fx-background-color: #0a0e17; -fx-border-color: #263246; -fx-border-width: 1;");

        Label titleLabel = new Label("Bot Trading Control");
        titleLabel.setStyle("-fx-text-fill: #e5edf7; -fx-font-size: 16px; -fx-font-weight: bold;");

        HBox header = new HBox(14, enabledCheckbox, statusLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        enabledCheckbox.setStyle("-fx-text-fill: #e5edf7;");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                tab("Trading", createTradingSettings()),
                tab("Risk", createRiskSettings()),
                tab("Backtest", createBacktestSettings()),
                tab("Streaming", createStreamingSettings()),
                tab("Signals", createSignalSettings()));
        VBox.setVgrow(tabs, Priority.ALWAYS);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        Button applyButton = primaryButton("Apply Settings");
        applyButton.setOnAction(event -> applySettings(true));
        Button resetButton = secondaryButton("Reset Defaults");
        resetButton.setOnAction(event -> resetSettings());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        actions.getChildren().addAll(applyButton, resetButton, spacer);

        getChildren().addAll(titleLabel, new Separator(), header, tabs, actions);
    }

    private Tab tab(String title, VBox content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return new Tab(title, scrollPane);
    }

    private VBox createTradingSettings() {
        GridPane grid = settingsGrid();
        addRow(grid, 0, "Symbol mode", symbolModeCombo);
        addRow(grid, 1, "Trade size", tradeSizeSpinner);
        addRow(grid, 2, "Max open positions", maxOpenPositionsSpinner);
        addRow(grid, 3, "Position sizing", positionSizingCombo);
        addRow(grid, 4, "Position size %", positionSizePercentSpinner);
        addRow(grid, 5, "Min time between trades (ms)", minTimeBetweenTradesSpinner);

        Label symbolsLabel = sectionLabel("Configured Symbols");
        symbolsListView.setPrefHeight(140);
        symbolsListView.setStyle("-fx-background-color: #0f1724; -fx-border-color: #2a3548;");

        return section(grid, symbolsLabel, symbolsListView);
    }

    private VBox createRiskSettings() {
        GridPane grid = settingsGrid();
        addRow(grid, 0, "Stop loss %", stopLossSpinner);
        addRow(grid, 1, "Take profit %", takeProfitSpinner);
        addRow(grid, 2, "Minimum profit %", minProfitSpinner);
        addRow(grid, 3, "Max portfolio risk %", maxPortfolioRiskSpinner);
        addRow(grid, 4, "Max daily loss %", maxDailyLossesSpinner);
        addRow(grid, 5, "Leverage", leverageSpinner);
        addRow(grid, 6, "Margin mode", marginModeCombo);
        addRow(grid, 7, "Max leverage risk %", maxLeverageRiskSpinner);
        addRow(grid, 8, "Profit taking %", profitTakingSpinner);
        addRow(grid, 9, "Trailing stop update (ms)", trailingStopUpdateIntervalSpinner);
        addRow(grid, 10, "Strict management", strictMoneyManagementCheckbox);
        addRow(grid, 11, "Dynamic sizing", dynamicPositionSizingCheckbox);
        addRow(grid, 12, "Partial profit taking", partialProfitTakingCheckbox);
        return section(grid);
    }

    private VBox createBacktestSettings() {
        GridPane grid = settingsGrid();
        addRow(grid, 0, "Risk per trade %", backtestRiskSpinner);
        addRow(grid, 1, "Starting balance", backtestBalanceSpinner);
        addRow(grid, 2, "Max drawdown %", backtestMaxDrawdownSpinner);
        addRow(grid, 3, "Fees", backtestUseRealFeesCheckbox);
        return section(grid);
    }

    private VBox createStreamingSettings() {
        GridPane grid = settingsGrid();
        addRow(grid, 0, "Streaming mode", streamingModeCombo);
        addRow(grid, 1, "Streaming enabled", streamingEnabledCheckbox);
        addRow(grid, 2, "Update interval (ms)", streamingUpdateIntervalSpinner);
        addRow(grid, 3, "WebSockets", useWebsocketsCheckbox);
        addRow(grid, 4, "Max WebSocket connections", maxWebsocketConnectionsSpinner);
        return section(grid);
    }

    private VBox createSignalSettings() {
        GridPane grid = settingsGrid();
        allowedSignalsField.setPromptText("BUY, SELL, CLOSE. Leave empty to allow all signals.");
        addRow(grid, 0, "Allowed signals", allowedSignalsField);

        signalInputField.setPromptText("BUY, SELL, or CLOSE");
        Button sendSignalButton = primaryButton("Send Signal");
        sendSignalButton.setOnAction(event -> sendSignal());
        HBox signalBox = new HBox(10, signalInputField, sendSignalButton);
        HBox.setHgrow(signalInputField, Priority.ALWAYS);

        VBox box = section(grid, sectionLabel("Manual Signal"), signalBox);
        return box;
    }

    private GridPane settingsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        return grid;
    }

    private VBox section(Object... children) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: #101827; -fx-border-color: #263246; -fx-border-radius: 6; -fx-background-radius: 6;");
        for (Object child : children) {
            box.getChildren().add((javafx.scene.Node) child);
        }
        return box;
    }

    private void addRow(GridPane grid, int row, String label, Control control) {
        Label labelControl = new Label(label);
        labelControl.setMinWidth(190);
        labelControl.setStyle("-fx-text-fill: #9aa7ba; -fx-font-weight: bold;");
        control.setMaxWidth(Double.MAX_VALUE);
        control.setStyle("-fx-background-color: #0f1724; -fx-text-fill: #e5edf7;");
        GridPane.setHgrow(control, Priority.ALWAYS);
        grid.add(labelControl, 0, row);
        grid.add(control, 1, row);
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #e5edf7; -fx-font-weight: bold;");
        return label;
    }

    private Button primaryButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #00D9FF; -fx-text-fill: #001018; -fx-font-weight: bold; -fx-padding: 8 16;");
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: #1f2a3a; -fx-text-fill: #e5edf7; -fx-font-weight: bold; -fx-padding: 8 16;");
        return button;
    }

    private Spinner<Double> doubleSpinner(double min, double max, double value, double step) {
        Spinner<Double> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, clamp(value, min, max), step));
        spinner.setEditable(true);
        spinner.setPrefWidth(180);
        return spinner;
    }

    private Spinner<Integer> intSpinner(int min, int max, int value, int step) {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, Math.max(min, Math.min(max, value)), step));
        spinner.setEditable(true);
        spinner.setPrefWidth(180);
        return spinner;
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private void setupListeners() {
        enabledCheckbox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            botConfig.setEnabled(newValue);
            exchange.autoTrading(newValue, null);
            botConfig.saveToPreferences();
            updateStatus();
        });
    }

    private void loadControlsFromConfig() {
        enabledCheckbox.setSelected(botConfig.isEnabled());
        symbolModeCombo.setValue(botConfig.getSymbolTradingMode());
        marginModeCombo.setValue(botConfig.getMarginMode());
        streamingModeCombo.setValue(botConfig.getStreamingMode());
        positionSizingCombo.setValue(botConfig.getPositionSizingStrategy());
        backtestUseRealFeesCheckbox.setSelected(botConfig.isBacktestUseRealFees());
        streamingEnabledCheckbox.setSelected(botConfig.isStreamingEnabled());
        useWebsocketsCheckbox.setSelected(botConfig.isUseWebsockets());
        strictMoneyManagementCheckbox.setSelected(botConfig.isEnableStrictMoneyManagement());
        dynamicPositionSizingCheckbox.setSelected(botConfig.isEnableDynamicPositionSizing());
        partialProfitTakingCheckbox.setSelected(botConfig.isEnablePartialProfitTaking());
        allowedSignalsField.setText(String.join(", ", new TreeSet<>(botConfig.getAllowedSignals())));
        updateStatus();
    }

    private void applyTradingProfile(TradingProfile profile) {
        double dailyLossPercent = profile.maxPositionSize() <= 0.0
                ? botConfig.getMaxDailyLosses()
                : (profile.dailyLossLimit() / profile.maxPositionSize()) * 100.0;

        enabledCheckbox.setSelected(profile.autoTradingEnabled());
        setSpinnerValue(maxOpenPositionsSpinner, profile.maxOpenPositions());
        setSpinnerValue(maxDailyLossesSpinner, clamp(dailyLossPercent, 0.0, 100.0));
        setSpinnerValue(positionSizePercentSpinner, profile.riskProfile().getMaxPositionSizePercent());
        setSpinnerValue(maxPortfolioRiskSpinner, profile.riskProfile().getMaxPortfolioHeatPercent());
        setSpinnerValue(leverageSpinner, profile.riskProfile().getMaxLeverage());
        setSpinnerValue(maxLeverageRiskSpinner, profile.riskProfile().getMaxDrawdownThresholdPercent());
        setSpinnerValue(backtestRiskSpinner, profile.maxRiskPerTradePercent());
        setSpinnerValue(backtestBalanceSpinner, Math.max(backtestBalanceSpinner.getValue(), profile.maxPositionSize()));
        setSpinnerValue(backtestMaxDrawdownSpinner, profile.maxDrawdownPercent());
        strictMoneyManagementCheckbox.setSelected(profile.capitalProtection() != org.investpro.enums.CapitalProtection.NONE);
        dynamicPositionSizingCheckbox.setSelected(profile.systemDesign() == org.investpro.enums.SystemDesign.HYBRID_SYSTEM
                || profile.systemDesign() == org.investpro.enums.SystemDesign.QUANTITATIVE_MODELS);
        updateStatus();
    }

    private void sendSignal() {
        String signal = signalInputField.getText().trim().toUpperCase(Locale.ROOT);

        if (signal.isEmpty()) {
            showWarning("Please enter a signal");
            return;
        }

        applySettings(false);

        if (!botConfig.isEnabled()) {
            showWarning("Bot trading is disabled");
            return;
        }

        if (botConfig.getTradingSymbols().isEmpty()) {
            showWarning("No symbols configured for bot trading");
            return;
        }

        if (!botConfig.isSignalAllowed(signal)) {
            showWarning("Signal is not allowed by the current settings");
            return;
        }

        exchange.processTradeSignal(signal);
        signalInputField.clear();

        if (onSignalSent != null) {
            onSignalSent.accept(signal);
        }

        showInfo("Signal sent: " + signal);
    }

    private void applySettings(boolean showConfirmation) {
        botConfig.setEnabled(enabledCheckbox.isSelected());
        botConfig.setSymbolTradingMode(symbolModeCombo.getValue());
        botConfig.setTradeSize(tradeSizeSpinner.getValue());
        botConfig.setStopLoss(stopLossSpinner.getValue());
        botConfig.setTakeProfit(takeProfitSpinner.getValue());
        botConfig.setMinProfitPercent(minProfitSpinner.getValue());
        botConfig.setMaxPortfolioRiskPercent(maxPortfolioRiskSpinner.getValue());
        botConfig.setMinTimeBetweenTrades(minTimeBetweenTradesSpinner.getValue());
        botConfig.setAllowedSignals(parseAllowedSignals());
        botConfig.setLeverage(leverageSpinner.getValue());
        botConfig.setMarginMode(marginModeCombo.getValue());
        botConfig.setMaxLeverageRisk(maxLeverageRiskSpinner.getValue());
        botConfig.setBacktestRiskPercentPerTrade(backtestRiskSpinner.getValue());
        botConfig.setBacktestStartingBalance(backtestBalanceSpinner.getValue());
        botConfig.setBacktestMaxDrawdownPercent(backtestMaxDrawdownSpinner.getValue());
        botConfig.setBacktestUseRealFees(backtestUseRealFeesCheckbox.isSelected());
        botConfig.setStreamingMode(streamingModeCombo.getValue());
        botConfig.setStreamingEnabled(streamingEnabledCheckbox.isSelected());
        botConfig.setStreamingUpdateInterval(streamingUpdateIntervalSpinner.getValue());
        botConfig.setUseWebsockets(useWebsocketsCheckbox.isSelected());
        botConfig.setMaxWebsocketConnections(maxWebsocketConnectionsSpinner.getValue());
        botConfig.setMaxOpenPositions(maxOpenPositionsSpinner.getValue());
        botConfig.setMaxDailyLosses(maxDailyLossesSpinner.getValue());
        botConfig.setPositionSizePercent(positionSizePercentSpinner.getValue());
        botConfig.setPositionSizingStrategy(positionSizingCombo.getValue());
        botConfig.setEnableStrictMoneyManagement(strictMoneyManagementCheckbox.isSelected());
        botConfig.setEnableDynamicPositionSizing(dynamicPositionSizingCheckbox.isSelected());
        botConfig.setProfitTakingPercent(profitTakingSpinner.getValue());
        botConfig.setTrailingStopUpdateInterval(trailingStopUpdateIntervalSpinner.getValue());
        botConfig.setEnablePartialProfitTaking(partialProfitTakingCheckbox.isSelected());
        botConfig.saveToPreferences();
        updateStatus();
        if (showConfirmation) {
            showInfo("Bot settings applied");
        }
    }

    private Set<String> parseAllowedSignals() {
        Set<String> signals = new TreeSet<>();
        String rawSignals = allowedSignalsField.getText();
        if (rawSignals == null || rawSignals.isBlank()) {
            return signals;
        }

        Arrays.stream(rawSignals.split(","))
                .map(signal -> signal.trim().toUpperCase(Locale.ROOT))
                .filter(signal -> !signal.isBlank())
                .forEach(signals::add);
        return signals;
    }

    private void resetSettings() {
        botConfig.resetToDefaults();
        setSpinnerValue(tradeSizeSpinner, botConfig.getTradeSize());
        setSpinnerValue(stopLossSpinner, botConfig.getStopLoss());
        setSpinnerValue(takeProfitSpinner, botConfig.getTakeProfit());
        setSpinnerValue(minProfitSpinner, botConfig.getMinProfitPercent());
        setSpinnerValue(maxPortfolioRiskSpinner, botConfig.getMaxPortfolioRiskPercent());
        setSpinnerValue(minTimeBetweenTradesSpinner, (int) botConfig.getMinTimeBetweenTrades());
        setSpinnerValue(leverageSpinner, botConfig.getLeverage());
        setSpinnerValue(maxLeverageRiskSpinner, botConfig.getMaxLeverageRisk());
        setSpinnerValue(backtestRiskSpinner, botConfig.getBacktestRiskPercentPerTrade());
        setSpinnerValue(backtestBalanceSpinner, botConfig.getBacktestStartingBalance());
        setSpinnerValue(backtestMaxDrawdownSpinner, botConfig.getBacktestMaxDrawdownPercent());
        setSpinnerValue(streamingUpdateIntervalSpinner, (int) botConfig.getStreamingUpdateInterval());
        setSpinnerValue(maxWebsocketConnectionsSpinner, botConfig.getMaxWebsocketConnections());
        setSpinnerValue(maxOpenPositionsSpinner, botConfig.getMaxOpenPositions());
        setSpinnerValue(maxDailyLossesSpinner, botConfig.getMaxDailyLosses());
        setSpinnerValue(positionSizePercentSpinner, botConfig.getPositionSizePercent());
        setSpinnerValue(profitTakingSpinner, botConfig.getProfitTakingPercent());
        setSpinnerValue(trailingStopUpdateIntervalSpinner, (int) botConfig.getTrailingStopUpdateInterval());
        loadControlsFromConfig();
        refreshSymbolsList();
        showInfo("Bot settings reset to defaults");
    }

    private <T> void setSpinnerValue(Spinner<T> spinner, T value) {
        spinner.getValueFactory().setValue(value);
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
            statusLabel.setText("Bot Status: ENABLED");
            statusLabel.setStyle("-fx-text-fill: #29d391; -fx-font-size: 12px; -fx-padding: 5;");
        } else {
            statusLabel.setText("Bot Status: DISABLED");
            statusLabel.setStyle("-fx-text-fill: #ffb020; -fx-font-size: 12px; -fx-padding: 5;");
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
        botConfig.saveToPreferences();
        refreshSymbolsList();
    }
}
