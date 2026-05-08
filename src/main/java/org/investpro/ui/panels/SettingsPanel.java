package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.jetbrains.annotations.NotNull;

import java.util.prefs.Preferences;

/**
 * System Settings Panel.
 *
 * This panel controls system-level safety settings:
 * - require backtest before live trading
 * - require paper trading before live trading
 * - auto-assign best strategy
 * - minimum strategy score
 * - small account mode
 * - 1-unit trading under small-account threshold
 * - prevent open/close same cycle
 * - prevent instant reverse orders
 * - symbol cooldown
 *
 * This panel does not place orders.
 * It only saves configuration and applies it to SystemCore.
 */
@Slf4j
@Getter
@Setter
public class SettingsPanel extends VBox {

    private static final Preferences PREFS = Preferences.userNodeForPackage(SettingsPanel.class);

    private final SystemCore systemCore;

    private CheckBox requireBacktestBeforeLiveCheckbox;
    private CheckBox requirePaperTradingBeforeLiveCheckbox;
    private CheckBox autoAssignBestStrategyCheckbox;

    private Spinner<Double> minStrategyScoreSpinner;
    private Spinner<Integer> topStrategiesToPaperTradeSpinner;

    private CheckBox smallAccountModeCheckbox;
    private Spinner<Double> smallAccountThresholdSpinner;
    private Spinner<Double> smallAccountUnitsSpinner;

    private CheckBox preventOpenCloseSameCycleCheckbox;
    private CheckBox preventInstantReverseCheckbox;
    private Spinner<Integer> symbolCooldownSecondsSpinner;

    // Streaming mode settings
    private CheckBox enableStreamingCheckbox;
    private ComboBox<SystemCore.StreamingMode> streamingModeCombo;
    private Label streamingModeDescriptionLabel;
    private Button startStreamingButton;
    private Button stopStreamingButton;
    private Label streamingStatusLabel;

    private Label statusLabel;

    public SettingsPanel(SystemCore systemCore) {
        this.systemCore = systemCore;

        setPadding(new Insets(16));
        setSpacing(12);
        setStyle("-fx-background-color: #1a1a2e; -fx-text-fill: #ffffff;");
        getStyleClass().add("settings-panel");

        setupUi();
        loadSettings();
    }

    private void setupUi() {
        Label titleLabel = new Label("System Settings");
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        VBox systemSafetySection = createSystemSafetySection();
        VBox streamingSection = createStreamingSection();
        HBox buttonBox = createButtonBox();

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 12;");

        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(12,
                titleLabel,
                new Separator(),
                systemSafetySection,
                new Separator(),
                streamingSection,
                new Separator());
        content.setPadding(new Insets(8));
        scrollPane.setContent(content);
        scrollPane.setFitToWidth(true);

        getChildren().addAll(
                scrollPane,
                new Separator(),
                buttonBox,
                statusLabel);

        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    private VBox createSystemSafetySection() {
        Label sectionTitle = new Label("System Safety & Strategy Evaluation");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #ef4444;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; " +
                        "-fx-border-color: #ef4444; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 4; " +
                        "-fx-background-radius: 4;");

        requireBacktestBeforeLiveCheckbox = styledCheckBox("Require Backtest Before Live Trading", true);
        requirePaperTradingBeforeLiveCheckbox = styledCheckBox("Require Paper Trading Before Live Trading", true);
        autoAssignBestStrategyCheckbox = styledCheckBox("Auto Assign Best Strategy After Evaluation", true);

        minStrategyScoreSpinner = doubleSpinner(0.0, 100.0, 60.0, 5.0);
        topStrategiesToPaperTradeSpinner = intSpinner(1, 50, 5, 1);

        smallAccountModeCheckbox = styledCheckBox("Enable Small Account Mode", true);
        smallAccountThresholdSpinner = doubleSpinner(0.0, 10_000.0, 100.0, 10.0);
        smallAccountUnitsSpinner = doubleSpinner(1.0, 100_000.0, 1.0, 1.0);

        preventOpenCloseSameCycleCheckbox = styledCheckBox("Prevent Open/Close In Same Cycle", true);
        preventInstantReverseCheckbox = styledCheckBox("Prevent Instant Reverse Orders", true);
        symbolCooldownSecondsSpinner = intSpinner(0, 3600, 30, 5);

        grid.add(requireBacktestBeforeLiveCheckbox, 0, 0, 2, 1);
        grid.add(requirePaperTradingBeforeLiveCheckbox, 0, 1, 2, 1);
        grid.add(autoAssignBestStrategyCheckbox, 0, 2, 2, 1);

        addRow(grid, 3, "Minimum Strategy Score:", minStrategyScoreSpinner);
        addRow(grid, 4, "Top Strategies For Paper Test:", topStrategiesToPaperTradeSpinner);

        Separator separator = new Separator();
        grid.add(separator, 0, 5, 2, 1);

        grid.add(smallAccountModeCheckbox, 0, 6, 2, 1);
        addRow(grid, 7, "Small Account Threshold ($):", smallAccountThresholdSpinner);
        addRow(grid, 8, "Small Account Units:", smallAccountUnitsSpinner);

        grid.add(preventOpenCloseSameCycleCheckbox, 0, 9, 2, 1);
        grid.add(preventInstantReverseCheckbox, 0, 10, 2, 1);
        addRow(grid, 11, "Symbol Cooldown Seconds:", symbolCooldownSecondsSpinner);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createStreamingSection() {
        Label sectionTitle = new Label("Market Data Streaming");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; " +
                        "-fx-border-color: #3b82f6; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 4; " +
                        "-fx-background-radius: 4;");

        enableStreamingCheckbox = styledCheckBox("Enable Live Market Streaming", false);
        enableStreamingCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            streamingModeCombo.setDisable(!newVal);
            startStreamingButton.setDisable(!newVal);
            updateStreamingUI();
        });

        streamingModeCombo = new ComboBox<>();
        streamingModeCombo.getItems().addAll(systemCore.getAvailableStreamingModes());
        streamingModeCombo.setValue(SystemCore.StreamingMode.SAFE_DEFAULT);
        streamingModeCombo.setDisable(true);
        streamingModeCombo.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> updateStreamingModeDescription(newVal));
        streamingModeCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        streamingModeDescriptionLabel = new Label();
        streamingModeDescriptionLabel.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 11; -fx-wrap-text: true;");
        streamingModeDescriptionLabel.setPrefHeight(50);
        updateStreamingModeDescription(SystemCore.StreamingMode.SAFE_DEFAULT);

        startStreamingButton = new Button("▶ Start Streaming");
        startStreamingButton.setStyle(buttonStyle("#10b981"));
        startStreamingButton.setDisable(true);
        startStreamingButton.setOnAction(event -> handleStartStreaming());

        stopStreamingButton = new Button("⏸ Stop Streaming");
        stopStreamingButton.setStyle(buttonStyle("#ef4444"));
        stopStreamingButton.setOnAction(event -> handleStopStreaming());

        streamingStatusLabel = new Label("Not streaming");
        streamingStatusLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11;");

        HBox streamControlBox = new HBox(10, startStreamingButton, stopStreamingButton);
        streamControlBox.setAlignment(Pos.CENTER_LEFT);

        grid.add(enableStreamingCheckbox, 0, 0, 2, 1);
        grid.add(new Label("Streaming Mode:"), 0, 1);
        grid.add(streamingModeCombo, 1, 1);
        grid.add(streamingModeDescriptionLabel, 0, 2, 2, 1);
        grid.add(new Separator(), 0, 3, 2, 1);
        grid.add(streamControlBox, 0, 4, 2, 1);
        grid.add(new Label("Status:"), 0, 5);
        grid.add(streamingStatusLabel, 1, 5);

        return new VBox(8, sectionTitle, grid);
    }

    private void updateStreamingModeDescription(SystemCore.StreamingMode mode) {
        if (mode != null) {
            streamingModeDescriptionLabel.setText(mode.description);
        }
    }

    private void handleStartStreaming() {
        try {
            if (systemCore == null) {
                showAlert(Alert.AlertType.WARNING, "Error", "SystemCore is not available");
                return;
            }

            SystemCore.StreamingMode mode = streamingModeCombo.getValue();
            if (mode == null) {
                mode = SystemCore.StreamingMode.SAFE_DEFAULT;
            }

            // Use the selected trading pair or default
            if (systemCore.getSelectedTradePair() != null) {
                systemCore.startStreaming(systemCore.getSelectedTradePair(), mode);
            } else {
                systemCore.switchStreamingMode(mode);
                showAlert(Alert.AlertType.INFORMATION, "Info",
                        "Streaming mode set to " + mode.name() + ". Please select a symbol to start streaming.");
                return;
            }

            updateStreamingStatusUI(true);
            statusLabel.setText("Streaming started with mode: " + mode.name());
            log.info("Started streaming in mode: {}", mode);
        } catch (Exception e) {
            log.error("Error starting stream", e);
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to start streaming: " + e.getMessage());
            updateStreamingStatusUI(false);
        }
    }

    private void handleStopStreaming() {
        try {
            if (systemCore == null) {
                showAlert(Alert.AlertType.WARNING, "Error", "SystemCore is not available");
                return;
            }

            systemCore.stopStreaming();
            updateStreamingStatusUI(false);
            statusLabel.setText("Streaming stopped");
            log.info("Stopped streaming");
        } catch (Exception e) {
            log.error("Error stopping stream", e);
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to stop streaming: " + e.getMessage());
        }
    }

    private void updateStreamingUI() {
        if (systemCore != null) {
            updateStreamingStatusUI(systemCore.isStreaming());
        }
    }

    private void updateStreamingStatusUI(boolean isStreaming) {
        streamingStatusLabel.setText(isStreaming ? "✓ Streaming active" : "Not streaming");
        streamingStatusLabel
                .setStyle("-fx-text-fill: " + (isStreaming ? "#10b981" : "#a0aec0") + "; -fx-font-size: 11;");
        startStreamingButton.setDisable(isStreaming || !enableStreamingCheckbox.isSelected());
        stopStreamingButton.setDisable(!isStreaming);
    }

    private HBox createButtonBox() {
        Button saveButton = new Button("Save Settings");
        saveButton.setStyle(buttonStyle("#10b981"));
        saveButton.setOnAction(event -> saveSettings());

        Button applyButton = new Button("Apply Now");
        applyButton.setStyle(buttonStyle("#3b82f6"));
        applyButton.setOnAction(event -> applySettings());

        Button resetButton = new Button("Reset Defaults");
        resetButton.setStyle(buttonStyle("#6366f1"));
        resetButton.setOnAction(event -> resetSettings());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox box = new HBox(12, spacer, saveButton, applyButton, resetButton);
        box.setAlignment(Pos.CENTER_RIGHT);

        return box;
    }

    private void saveSettings() {
        SystemSafetySettings settings = buildSettingsFromUi();
        settings.save();

        applySettingsToSystemCore(settings);
        saveStreamingSettings();

        statusLabel.setText("Settings saved and applied.");
        log.info("System settings saved: {}", settings);

        showAlert(
                Alert.AlertType.INFORMATION,
                "Settings Saved",
                "System settings were saved and applied successfully.");
    }

    private void saveStreamingSettings() {
        SystemCore.StreamingMode mode = streamingModeCombo.getValue();
        if (mode != null) {
            PREFS.put("streamingMode", mode.name());
        }
        PREFS.putBoolean("enableStreaming", enableStreamingCheckbox.isSelected());
    }

    private void applySettings() {
        SystemSafetySettings settings = buildSettingsFromUi();
        applySettingsToSystemCore(settings);

        // Save streaming settings
        saveStreamingSettings();

        statusLabel.setText("Settings applied.");
        log.info("System settings applied: {}", settings);
    }

    private void resetSettings() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Reset Settings");
        confirm.setHeaderText("Reset system settings?");
        confirm.setContentText("This will restore all settings to defaults.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        SystemSafetySettings defaults = SystemSafetySettings.defaults();
        applySettingsToUi(defaults);
        defaults.save();
        applySettingsToSystemCore(defaults);

        // Reset streaming settings to defaults
        streamingModeCombo.setValue(SystemCore.StreamingMode.SAFE_DEFAULT);
        enableStreamingCheckbox.setSelected(false);
        saveStreamingSettings();
        updateStreamingUI();

        statusLabel.setText("Settings reset to defaults.");
        log.info("System settings reset to defaults");
    }

    private void loadSettings() {
        SystemSafetySettings settings = SystemSafetySettings.load();
        applySettingsToUi(settings);
        applySettingsToSystemCore(settings);

        // Load streaming settings
        String savedStreamingMode = PREFS.get("streamingMode", SystemCore.StreamingMode.SAFE_DEFAULT.name());
        try {
            SystemCore.StreamingMode mode = SystemCore.StreamingMode.valueOf(savedStreamingMode);
            streamingModeCombo.setValue(mode);
        } catch (IllegalArgumentException e) {
            streamingModeCombo.setValue(SystemCore.StreamingMode.SAFE_DEFAULT);
        }
        enableStreamingCheckbox.setSelected(PREFS.getBoolean("enableStreaming", false));
        updateStreamingUI();

        statusLabel.setText("Settings loaded.");
        log.info("System settings loaded: {}", settings);
    }

    private SystemSafetySettings buildSettingsFromUi() {
        return new SystemSafetySettings(
                requireBacktestBeforeLiveCheckbox.isSelected(),
                requirePaperTradingBeforeLiveCheckbox.isSelected(),
                autoAssignBestStrategyCheckbox.isSelected(),
                minStrategyScoreSpinner.getValue(),
                topStrategiesToPaperTradeSpinner.getValue(),

                smallAccountModeCheckbox.isSelected(),
                smallAccountThresholdSpinner.getValue(),
                smallAccountUnitsSpinner.getValue(),

                preventOpenCloseSameCycleCheckbox.isSelected(),
                preventInstantReverseCheckbox.isSelected(),
                symbolCooldownSecondsSpinner.getValue());
    }

    private void applySettingsToUi(@NotNull SystemSafetySettings settings) {
        requireBacktestBeforeLiveCheckbox.setSelected(settings.requireBacktestBeforeLive());
        requirePaperTradingBeforeLiveCheckbox.setSelected(settings.requirePaperTradingBeforeLive());
        autoAssignBestStrategyCheckbox.setSelected(settings.autoAssignBestStrategy());

        minStrategyScoreSpinner.getValueFactory().setValue(settings.minStrategyScore());
        topStrategiesToPaperTradeSpinner.getValueFactory().setValue(settings.topStrategiesToPaperTrade());

        smallAccountModeCheckbox.setSelected(settings.smallAccountModeEnabled());
        smallAccountThresholdSpinner.getValueFactory().setValue(settings.smallAccountThreshold());
        smallAccountUnitsSpinner.getValueFactory().setValue(settings.smallAccountUnits());

        preventOpenCloseSameCycleCheckbox.setSelected(settings.preventOpenCloseSameCycle());
        preventInstantReverseCheckbox.setSelected(settings.preventInstantReverse());
        symbolCooldownSecondsSpinner.getValueFactory().setValue(settings.symbolCooldownSeconds());
    }

    private void applySettingsToSystemCore(@NotNull SystemSafetySettings settings) {
        applySettingsAsSystemProperties(settings);

        if (systemCore == null) {
            log.debug("SystemCore is null; settings saved as system properties only.");
            return;
        }

        try {
            systemCore.applySystemSettings(settings);
            log.info("Applied settings to SystemCore");
        } catch (Exception exception) {
            log.warn("SystemCore does not support applySystemSettings(SystemSafetySettings): {}",
                    exception.getMessage());
        }
    }

    private void applySettingsAsSystemProperties(@NotNull SystemSafetySettings settings) {
        System.setProperty(
                "tradeadviser.strategy.requireBacktestBeforeLive",
                String.valueOf(settings.requireBacktestBeforeLive()));
        System.setProperty(
                "tradeadviser.strategy.requirePaperTradingBeforeLive",
                String.valueOf(settings.requirePaperTradingBeforeLive()));
        System.setProperty(
                "tradeadviser.strategy.autoAssignBest",
                String.valueOf(settings.autoAssignBestStrategy()));
        System.setProperty(
                "tradeadviser.strategy.minScore",
                String.valueOf(settings.minStrategyScore()));
        System.setProperty(
                "tradeadviser.strategy.topCandidates",
                String.valueOf(settings.topStrategiesToPaperTrade()));

        System.setProperty(
                "tradeadviser.execution.smallAccountMode",
                String.valueOf(settings.smallAccountModeEnabled()));
        System.setProperty(
                "tradeadviser.execution.smallAccountThreshold",
                String.valueOf(settings.smallAccountThreshold()));
        System.setProperty(
                "tradeadviser.execution.smallAccountTradeUnits",
                String.valueOf(settings.smallAccountUnits()));
        System.setProperty(
                "tradeadviser.execution.preventOpenCloseSameCycle",
                String.valueOf(settings.preventOpenCloseSameCycle()));
        System.setProperty(
                "tradeadviser.execution.preventInstantReverse",
                String.valueOf(settings.preventInstantReverse()));
        System.setProperty(
                "tradeadviser.execution.symbolCooldownSeconds",
                String.valueOf(settings.symbolCooldownSeconds()));
    }

    private void addRow(GridPane grid, int row, String label, Control control) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-text-fill: #a0aec0;");

        control.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        grid.add(labelNode, 0, row);
        grid.add(control, 1, row);
    }

    private CheckBox styledCheckBox(String text, boolean selected) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.setSelected(selected);
        checkBox.setStyle("-fx-text-fill: #a0aec0;");
        return checkBox;
    }

    private Spinner<Double> doubleSpinner(double min, double max, double initial, double step) {
        Spinner<Double> spinner = new Spinner<>(min, max, initial, step);
        spinner.setEditable(true);
        spinner.getEditor().setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
        return spinner;
    }

    private Spinner<Integer> intSpinner(int min, int max, int initial, int step) {
        Spinner<Integer> spinner = new Spinner<>(min, max, initial, step);
        spinner.setEditable(true);
        spinner.getEditor().setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
        return spinner;
    }

    private String buttonStyle(String color) {
        return "-fx-padding: 8 24; " +
                "-fx-background-color: " + color + "; " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 12; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand;";
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * System safety settings used by StrategyExaminer, StrategyAssignmentService,
     * RiskAgent, and TradeExecutionCoordinator.
     */
    public record SystemSafetySettings(
            boolean requireBacktestBeforeLive,
            boolean requirePaperTradingBeforeLive,
            boolean autoAssignBestStrategy,
            double minStrategyScore,
            int topStrategiesToPaperTrade,

            boolean smallAccountModeEnabled,
            double smallAccountThreshold,
            double smallAccountUnits,

            boolean preventOpenCloseSameCycle,
            boolean preventInstantReverse,
            int symbolCooldownSeconds) {

        public static SystemSafetySettings defaults() {
            return new SystemSafetySettings(
                    true,
                    true,
                    true,
                    60.0,
                    5,

                    true,
                    100.0,
                    1.0,

                    true,
                    true,
                    30);
        }

        public static SystemSafetySettings load() {
            SystemSafetySettings d = defaults();

            return new SystemSafetySettings(
                    PREFS.getBoolean("requireBacktestBeforeLive", d.requireBacktestBeforeLive()),
                    PREFS.getBoolean("requirePaperTradingBeforeLive", d.requirePaperTradingBeforeLive()),
                    PREFS.getBoolean("autoAssignBestStrategy", d.autoAssignBestStrategy()),
                    PREFS.getDouble("minStrategyScore", d.minStrategyScore()),
                    PREFS.getInt("topStrategiesToPaperTrade", d.topStrategiesToPaperTrade()),

                    PREFS.getBoolean("smallAccountModeEnabled", d.smallAccountModeEnabled()),
                    PREFS.getDouble("smallAccountThreshold", d.smallAccountThreshold()),
                    PREFS.getDouble("smallAccountUnits", d.smallAccountUnits()),

                    PREFS.getBoolean("preventOpenCloseSameCycle", d.preventOpenCloseSameCycle()),
                    PREFS.getBoolean("preventInstantReverse", d.preventInstantReverse()),
                    PREFS.getInt("symbolCooldownSeconds", d.symbolCooldownSeconds()));
        }

        public void save() {
            PREFS.putBoolean("requireBacktestBeforeLive", requireBacktestBeforeLive);
            PREFS.putBoolean("requirePaperTradingBeforeLive", requirePaperTradingBeforeLive);
            PREFS.putBoolean("autoAssignBestStrategy", autoAssignBestStrategy);
            PREFS.putDouble("minStrategyScore", Math.max(0.0, minStrategyScore));
            PREFS.putInt("topStrategiesToPaperTrade", Math.max(1, topStrategiesToPaperTrade));

            PREFS.putBoolean("smallAccountModeEnabled", smallAccountModeEnabled);
            PREFS.putDouble("smallAccountThreshold", Math.max(0.0, smallAccountThreshold));
            PREFS.putDouble("smallAccountUnits", Math.max(1.0, smallAccountUnits));

            PREFS.putBoolean("preventOpenCloseSameCycle", preventOpenCloseSameCycle);
            PREFS.putBoolean("preventInstantReverse", preventInstantReverse);
            PREFS.putInt("symbolCooldownSeconds", Math.max(0, symbolCooldownSeconds));
        }
    }
}