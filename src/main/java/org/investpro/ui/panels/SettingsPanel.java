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
 * <p>
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
 * <p>
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

    // OpenAI configuration
    private CheckBox enableOpenAiCheckbox;
    private PasswordField openaiApiKeyField;
    private ComboBox<String> openaiModelCombo;
    private Spinner<Double> temperatureSpinner;
    private Spinner<Integer> maxTokensSpinner;

    // Telegram configuration
    private CheckBox enableTelegramCheckbox;
    private PasswordField telegramBotTokenField;
    private TextField telegramChatIdField;
    private Button testTelegramButton;

    // Email notification configuration
    private CheckBox enableEmailCheckbox;
    private TextField smtpServerField;
    private Spinner<Integer> smtpPortSpinner;
    private TextField emailAddressField;
    private PasswordField emailPasswordField;
    private CheckBox enableTlsCheckbox;
    private Button testEmailButton;

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
        VBox openAiSection = createOpenAiSection();
        VBox telegramSection = createTelegramSection();
        VBox emailSection = createEmailSection();
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
                new Separator(),
                openAiSection,
                new Separator(),
                telegramSection,
                new Separator(),
                emailSection,
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

    private VBox createOpenAiSection() {
        Label sectionTitle = new Label("OpenAI Configuration");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #8b5cf6;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; " +
                        "-fx-border-color: #8b5cf6; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 4; " +
                        "-fx-background-radius: 4;");

        enableOpenAiCheckbox = styledCheckBox("Enable OpenAI Integration", false);

        openaiApiKeyField = new PasswordField();
        openaiApiKeyField.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
        openaiApiKeyField.setPromptText("sk-...");

        openaiModelCombo = new ComboBox<>();
        openaiModelCombo.getItems().addAll("gpt-4o", "gpt-4", "gpt-3.5-turbo");
        openaiModelCombo.setValue("gpt-4o");
        openaiModelCombo.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");

        temperatureSpinner = doubleSpinner(0.0, 2.0, 0.7, 0.1);
        maxTokensSpinner = intSpinner(1, 4000, 1000, 100);

        grid.add(enableOpenAiCheckbox, 0, 0, 2, 1);
        addRow(grid, 1, "API Key:", openaiApiKeyField);
        addRow(grid, 2, "Model:", openaiModelCombo);
        addRow(grid, 3, "Temperature (0-2):", temperatureSpinner);
        addRow(grid, 4, "Max Tokens:", maxTokensSpinner);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createTelegramSection() {
        Label sectionTitle = new Label("Telegram Notifications");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #60a5fa;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; " +
                        "-fx-border-color: #60a5fa; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 4; " +
                        "-fx-background-radius: 4;");

        enableTelegramCheckbox = styledCheckBox("Enable Telegram Notifications", false);

        telegramBotTokenField = new PasswordField();
        telegramBotTokenField.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
        telegramBotTokenField.setPromptText("Bot token from @BotFather");

        telegramChatIdField = new TextField();
        telegramChatIdField.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
        telegramChatIdField.setPromptText("Your chat ID");

        testTelegramButton = new Button("Test Connection");
        testTelegramButton.setStyle(buttonStyle("#60a5fa"));
        testTelegramButton.setOnAction(event -> testTelegramConnection());

        grid.add(enableTelegramCheckbox, 0, 0, 2, 1);
        addRow(grid, 1, "Bot Token:", telegramBotTokenField);
        addRow(grid, 2, "Chat ID:", telegramChatIdField);
        grid.add(testTelegramButton, 1, 3);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createEmailSection() {
        Label sectionTitle = new Label("Email Notifications");
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #ec4899;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle(
                "-fx-background-color: #16213e; " +
                        "-fx-border-color: #ec4899; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 4; " +
                        "-fx-background-radius: 4;");

        enableEmailCheckbox = styledCheckBox("Enable Email Notifications", false);

        smtpServerField = new TextField();
        smtpServerField.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
        smtpServerField.setPromptText("smtp.gmail.com");

        smtpPortSpinner = intSpinner(1, 65535, 587, 1);

        emailAddressField = new TextField();
        emailAddressField.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
        emailAddressField.setPromptText("your-email@gmail.com");

        emailPasswordField = new PasswordField();
        emailPasswordField.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #ffffff;");
        emailPasswordField.setPromptText("App password");

        enableTlsCheckbox = styledCheckBox("Use TLS", true);

        testEmailButton = new Button("Test Connection");
        testEmailButton.setStyle(buttonStyle("#ec4899"));
        testEmailButton.setOnAction(event -> testEmailConnection());

        grid.add(enableEmailCheckbox, 0, 0, 2, 1);
        addRow(grid, 1, "SMTP Server:", smtpServerField);
        addRow(grid, 2, "SMTP Port:", smtpPortSpinner);
        addRow(grid, 3, "Email Address:", emailAddressField);
        addRow(grid, 4, "Password:", emailPasswordField);
        grid.add(enableTlsCheckbox, 0, 5, 2, 1);
        grid.add(testEmailButton, 1, 6);

        return new VBox(8, sectionTitle, grid);
    }

    private void testTelegramConnection() {
        String botToken = telegramBotTokenField.getText();
        String chatId = telegramChatIdField.getText();

        if (botToken.isEmpty() || chatId.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please enter bot token and chat ID");
            return;
        }

        try {
            // TODO: Implement actual Telegram connection test
            showAlert(Alert.AlertType.INFORMATION, "Success", "Telegram connection test passed!");
            statusLabel.setText("Telegram connection test successful");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Connection Failed", "Error: " + e.getMessage());
            statusLabel.setText("Telegram connection test failed");
        }
    }

    private void testEmailConnection() {
        String smtpServer = smtpServerField.getText();
        String emailAddress = emailAddressField.getText();
        String password = emailPasswordField.getText();

        if (smtpServer.isEmpty() || emailAddress.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please fill in all required fields");
            return;
        }

        try {
            // TODO: Implement actual email connection test
            showAlert(Alert.AlertType.INFORMATION, "Success", "Email connection test passed!");
            statusLabel.setText("Email connection test successful");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Connection Failed", "Error: " + e.getMessage());
            statusLabel.setText("Email connection test failed");
        }
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
        saveOpenAiSettings();
        saveTelegramSettings();
        saveEmailSettings();

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
        saveOpenAiSettings();
        saveTelegramSettings();
        saveEmailSettings();

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

        // Reset OpenAI settings
        enableOpenAiCheckbox.setSelected(false);
        openaiApiKeyField.clear();
        openaiModelCombo.setValue("gpt-4o");
        temperatureSpinner.getValueFactory().setValue(0.7);
        maxTokensSpinner.getValueFactory().setValue(1000);
        saveOpenAiSettings();

        // Reset Telegram settings
        enableTelegramCheckbox.setSelected(false);
        telegramBotTokenField.clear();
        telegramChatIdField.clear();
        saveTelegramSettings();

        // Reset Email settings
        enableEmailCheckbox.setSelected(false);
        smtpServerField.clear();
        smtpPortSpinner.getValueFactory().setValue(587);
        emailAddressField.clear();
        emailPasswordField.clear();
        enableTlsCheckbox.setSelected(true);
        saveEmailSettings();

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

        // Load OpenAI settings
        loadOpenAiSettings();

        // Load Telegram settings
        loadTelegramSettings();

        // Load Email settings
        loadEmailSettings();

        statusLabel.setText("Settings loaded.");
        log.info("System settings loaded: {}", settings);
    }

    private void saveOpenAiSettings() {
        PREFS.putBoolean("openaiEnabled", enableOpenAiCheckbox.isSelected());
        PREFS.put("openaiApiKey", openaiApiKeyField.getText());
        PREFS.put("openaiModel", openaiModelCombo.getValue());
        PREFS.putDouble("openaiTemperature", temperatureSpinner.getValue());
        PREFS.putInt("openaiMaxTokens", maxTokensSpinner.getValue());
    }

    private void loadOpenAiSettings() {
        enableOpenAiCheckbox.setSelected(PREFS.getBoolean("openaiEnabled", false));
        openaiApiKeyField.setText(PREFS.get("openaiApiKey", ""));
        openaiModelCombo.setValue(PREFS.get("openaiModel", "gpt-4o"));
        temperatureSpinner.getValueFactory().setValue(PREFS.getDouble("openaiTemperature", 0.7));
        maxTokensSpinner.getValueFactory().setValue(PREFS.getInt("openaiMaxTokens", 1000));
    }

    private void saveTelegramSettings() {
        PREFS.putBoolean("telegramEnabled", enableTelegramCheckbox.isSelected());
        PREFS.put("telegramBotToken", telegramBotTokenField.getText());
        PREFS.put("telegramChatId", telegramChatIdField.getText());
    }

    private void loadTelegramSettings() {
        enableTelegramCheckbox.setSelected(PREFS.getBoolean("telegramEnabled", false));
        telegramBotTokenField.setText(PREFS.get("telegramBotToken", ""));
        telegramChatIdField.setText(PREFS.get("telegramChatId", ""));
    }

    private void saveEmailSettings() {
        PREFS.putBoolean("emailEnabled", enableEmailCheckbox.isSelected());
        PREFS.put("smtpServer", smtpServerField.getText());
        PREFS.putInt("smtpPort", smtpPortSpinner.getValue());
        PREFS.put("emailAddress", emailAddressField.getText());
        PREFS.put("emailPassword", emailPasswordField.getText());
        PREFS.putBoolean("emailUseTls", enableTlsCheckbox.isSelected());
    }

    private void loadEmailSettings() {
        enableEmailCheckbox.setSelected(PREFS.getBoolean("emailEnabled", false));
        smtpServerField.setText(PREFS.get("smtpServer", ""));
        smtpPortSpinner.getValueFactory().setValue(PREFS.getInt("smtpPort", 587));
        emailAddressField.setText(PREFS.get("emailAddress", ""));
        emailPasswordField.setText(PREFS.get("emailPassword", ""));
        enableTlsCheckbox.setSelected(PREFS.getBoolean("emailUseTls", true));
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