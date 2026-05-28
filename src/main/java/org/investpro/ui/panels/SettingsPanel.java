package org.investpro.ui.panels;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.i18n.LocalizationService;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import java.util.Properties;
import java.util.prefs.Preferences;

import static org.investpro.i18n.LocalizationService.t;

/**
 * System Settings Panel.
 *
 * <p>This panel controls system-level configuration and safety settings:</p>
 * <ul>
 *     <li>Require backtest before live trading</li>
 *     <li>Require paper trading before live trading</li>
 *     <li>Auto-assign best strategy after evaluation</li>
 *     <li>Minimum strategy score</li>
 *     <li>Small account mode and micro-unit execution</li>
 *     <li>Open/close and reverse-order protection</li>
 *     <li>Symbol cooldown</li>
 *     <li>Market streaming mode</li>
 *     <li>OpenAI, Telegram, and email notification settings</li>
 * </ul>
 *
 * <p>This panel never places orders directly. It saves configuration and applies it to {@link SystemCore}.</p>
 */
@Slf4j
@Getter
@Setter
public class SettingsPanel extends StackPane {

    private static final Preferences PREFS = Preferences.userNodeForPackage(SettingsPanel.class);

    private static final String SETTINGS_PREFIX = "investpro";
    private static final String COLOR_BG = "#0f172a";
    private static final String COLOR_PANEL = "#16213e";
    private static final String COLOR_INPUT = "#0f3460";
    private static final String COLOR_TEXT = "#ffffff";
    private static final String COLOR_MUTED = "#a0aec0";
    private static final String COLOR_SUCCESS = "#10b981";
    private static final String COLOR_ERROR = "#ef4444";
    private static final String COLOR_INFO = "#60a5fa";
    private static final String COLOR_WARNING = "#fbbf24";

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

    private CheckBox enableStreamingCheckbox;
    private ComboBox<SystemCore.StreamingMode> streamingModeCombo;
    private Label streamingModeDescriptionLabel;
    private Button startStreamingButton;
    private Button stopStreamingButton;
    private Label streamingStatusLabel;

    private CheckBox enableOpenAiCheckbox;
    private PasswordField openaiApiKeyField;
    private ComboBox<String> openaiModelCombo;
    private Spinner<Double> temperatureSpinner;
    private Spinner<Integer> maxTokensSpinner;
    private Button testOpenAIButton;

    private CheckBox enableTelegramCheckbox;
    private PasswordField telegramBotTokenField;
    private TextField telegramChatIdField;
    private Button testTelegramButton;

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

        setStyle("-fx-background-color: " + COLOR_BG + ";");
        getStyleClass().add("settings-panel");

        setupUi();
        LocalizationService.applyTranslations(this);
        loadSettings();
    }

    private void setupUi() {
        Label titleLabel = new Label("⚙  System Settings");
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: " + COLOR_TEXT + ";");

        HBox header = new HBox(titleLabel);
        header.setPadding(new Insets(16, 16, 8, 16));
        header.setStyle("-fx-background-color: " + COLOR_BG + ";");

        VBox systemSafetySection = createSystemSafetySection();
        VBox streamingSection = createStreamingSection();
        VBox openAiSection = createOpenAiSection();
        VBox telegramSection = createTelegramSection();
        VBox emailSection = createEmailSection();

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().addAll(
                sectionTab("🛡  Safety & Execution", systemSafetySection),
                sectionTab("📡  Streaming", streamingSection),
                sectionTab("🤖  OpenAI", openAiSection),
                sectionTab("✈  Telegram", telegramSection),
                sectionTab("✉  Email", emailSection)
        );

        statusLabel = new Label("Ready");
        setStatusNeutral("Ready");

        HBox buttonBox = createButtonBox();
        VBox footer = new VBox(6, new Separator(), buttonBox, statusLabel);
        footer.setPadding(new Insets(8, 16, 12, 16));
        footer.setStyle("-fx-background-color: " + COLOR_BG + ";");

        VBox root = new VBox(header, tabPane, footer);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        AnchorPane anchor = new AnchorPane(root);
        AnchorPane.setTopAnchor(root, 0.0);
        AnchorPane.setBottomAnchor(root, 0.0);
        AnchorPane.setLeftAnchor(root, 0.0);
        AnchorPane.setRightAnchor(root, 0.0);
        anchor.setStyle("-fx-background-color: " + COLOR_BG + ";");

        getChildren().add(anchor);
    }

    private Tab sectionTab(String title, VBox content) {
        content.setPadding(new Insets(16));
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: " + COLOR_BG + "; -fx-background: " + COLOR_BG + ";");
        return new Tab(title, sp);
    }

    private VBox createSystemSafetySection() {
        Label sectionTitle = sectionTitle("System Safety & Strategy Evaluation", COLOR_ERROR);
        GridPane grid = sectionGrid(COLOR_ERROR);

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

        grid.add(new Separator(), 0, 5, 2, 1);

        grid.add(smallAccountModeCheckbox, 0, 6, 2, 1);
        addRow(grid, 7, "Small Account Threshold ($):", smallAccountThresholdSpinner);
        addRow(grid, 8, "Small Account Units:", smallAccountUnitsSpinner);

        grid.add(preventOpenCloseSameCycleCheckbox, 0, 9, 2, 1);
        grid.add(preventInstantReverseCheckbox, 0, 10, 2, 1);
        addRow(grid, 11, "Symbol Cooldown Seconds:", symbolCooldownSecondsSpinner);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createStreamingSection() {
        Label sectionTitle = sectionTitle("Market Data Streaming", "#3b82f6");
        GridPane grid = sectionGrid("#3b82f6");

        enableStreamingCheckbox = styledCheckBox("Enable Live Market Streaming", false);

        streamingModeCombo = new ComboBox<>();
        if (systemCore != null) {
            streamingModeCombo.getItems().addAll(systemCore.getAvailableStreamingModes());
        }
        if (streamingModeCombo.getItems().isEmpty()) {
            streamingModeCombo.getItems().add(SystemCore.StreamingMode.SAFE_DEFAULT);
        }
        streamingModeCombo.setValue(SystemCore.StreamingMode.SAFE_DEFAULT);
        streamingModeCombo.setDisable(true);
        streamingModeCombo.setStyle(inputStyle());
        streamingModeCombo.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> updateStreamingModeDescription(newVal));

        streamingModeDescriptionLabel = new Label();
        streamingModeDescriptionLabel.setWrapText(true);
        streamingModeDescriptionLabel.setPrefHeight(50);
        streamingModeDescriptionLabel.setStyle("-fx-text-fill: " + COLOR_INFO + "; -fx-font-size: 11;");
        updateStreamingModeDescription(SystemCore.StreamingMode.SAFE_DEFAULT);

        startStreamingButton = new Button("▶ Start Streaming");
        startStreamingButton.setStyle(buttonStyle(COLOR_SUCCESS));
        startStreamingButton.setDisable(true);
        startStreamingButton.setOnAction(event -> handleStartStreaming());

        stopStreamingButton = new Button("⏸ Stop Streaming");
        stopStreamingButton.setStyle(buttonStyle(COLOR_ERROR));
        stopStreamingButton.setDisable(true);
        stopStreamingButton.setOnAction(event -> handleStopStreaming());

        streamingStatusLabel = new Label("Not streaming");
        streamingStatusLabel.setStyle("-fx-text-fill: " + COLOR_MUTED + "; -fx-font-size: 11;");

        enableStreamingCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            streamingModeCombo.setDisable(!newVal);
            updateStreamingUI();
        });

        HBox streamControlBox = new HBox(10, startStreamingButton, stopStreamingButton);
        streamControlBox.setAlignment(Pos.CENTER_LEFT);

        grid.add(enableStreamingCheckbox, 0, 0, 2, 1);
        addRow(grid, 1, "Streaming Mode:", streamingModeCombo);
        grid.add(streamingModeDescriptionLabel, 0, 2, 2, 1);
        grid.add(new Separator(), 0, 3, 2, 1);
        grid.add(streamControlBox, 0, 4, 2, 1);
        addRow(grid, 5, "Status:", streamingStatusLabel);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createOpenAiSection() {
        Label sectionTitle = sectionTitle("OpenAI Configuration", "#8b5cf6");
        GridPane grid = sectionGrid("#8b5cf6");

        enableOpenAiCheckbox = styledCheckBox("Enable OpenAI Integration", false);

        openaiApiKeyField = new PasswordField();
        openaiApiKeyField.setStyle(inputStyle());
        openaiApiKeyField.setPromptText("sk-...");

        openaiModelCombo = new ComboBox<>();
        openaiModelCombo.getItems().addAll("gpt-4o", "gpt-4.1", "gpt-4.1-mini", "gpt-4", "gpt-3.5-turbo");
        openaiModelCombo.setValue("gpt-4o");
        openaiModelCombo.setStyle(inputStyle());

        temperatureSpinner = doubleSpinner(0.0, 2.0, 0.7, 0.1);
        maxTokensSpinner = intSpinner(1, 16_000, 1000, 100);

        testOpenAIButton = new Button("Test Connection");
        testOpenAIButton.setStyle(buttonStyle("#8b5cf6"));
        testOpenAIButton.setOnAction(event -> testOpenAIConnection());

        grid.add(enableOpenAiCheckbox, 0, 0, 2, 1);
        addRow(grid, 1, "API Key:", openaiApiKeyField);
        addRow(grid, 2, "Model:", openaiModelCombo);
        addRow(grid, 3, "Temperature (0-2):", temperatureSpinner);
        addRow(grid, 4, "Max Tokens:", maxTokensSpinner);
        grid.add(testOpenAIButton, 1, 5);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createTelegramSection() {
        Label sectionTitle = sectionTitle("Telegram Notifications", COLOR_INFO);
        GridPane grid = sectionGrid(COLOR_INFO);

        enableTelegramCheckbox = styledCheckBox("Enable Telegram Notifications", false);

        telegramBotTokenField = new PasswordField();
        telegramBotTokenField.setStyle(inputStyle());
        telegramBotTokenField.setPromptText("Bot token from @BotFather");

        telegramChatIdField = new TextField();
        telegramChatIdField.setStyle(inputStyle());
        telegramChatIdField.setPromptText("Your chat ID");

        testTelegramButton = new Button("Test Connection");
        testTelegramButton.setStyle(buttonStyle(COLOR_INFO));
        testTelegramButton.setOnAction(event -> testTelegramConnection());

        grid.add(enableTelegramCheckbox, 0, 0, 2, 1);
        addRow(grid, 1, "Bot Token:", telegramBotTokenField);
        addRow(grid, 2, "Chat ID:", telegramChatIdField);
        grid.add(testTelegramButton, 1, 3);

        return new VBox(8, sectionTitle, grid);
    }

    private VBox createEmailSection() {
        Label sectionTitle = sectionTitle("Email Notifications", "#ec4899");
        GridPane grid = sectionGrid("#ec4899");

        enableEmailCheckbox = styledCheckBox("Enable Email Notifications", false);

        smtpServerField = new TextField();
        smtpServerField.setStyle(inputStyle());
        smtpServerField.setPromptText("smtp.gmail.com");

        smtpPortSpinner = intSpinner(1, 65535, 587, 1);

        emailAddressField = new TextField();
        emailAddressField.setStyle(inputStyle());
        emailAddressField.setPromptText("your-email@gmail.com");

        emailPasswordField = new PasswordField();
        emailPasswordField.setStyle(inputStyle());
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
        String botToken = telegramBotTokenField.getText().trim();
        String chatId = telegramChatIdField.getText().trim();

        if (botToken.isEmpty() || chatId.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, t("common.validationError"), "Please enter bot token and chat ID");
            return;
        }

        if (!botToken.matches("^\\d+:[a-zA-Z0-9_-]+$")) {
            showAlert(Alert.AlertType.WARNING, t("common.validationError"), "Invalid bot token format");
            return;
        }

        runButtonTask(
                testTelegramButton,
                "Testing...",
                "Testing Telegram connection...",
                () -> {
                    String apiUrl = "https://api.telegram.org/bot" + botToken + "/getMe";
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 401) {
                        throw new RuntimeException("Invalid bot token (401 Unauthorized)");
                    }
                    if (responseCode != 200) {
                        throw new RuntimeException("Telegram API error (code: " + responseCode + ")");
                    }

                    String sendUrl = "https://api.telegram.org/bot" + botToken + "/sendMessage";
                    java.net.HttpURLConnection sendConn = (java.net.HttpURLConnection) URI.create(sendUrl).toURL().openConnection();
                    sendConn.setRequestMethod("POST");
                    sendConn.setConnectTimeout(5000);
                    sendConn.setReadTimeout(5000);
                    sendConn.setDoOutput(true);
                    sendConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    String payload = "chat_id=" + java.net.URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                            + "&text=" + java.net.URLEncoder.encode(
                            "✅ InvestPro Telegram connection test successful!", StandardCharsets.UTF_8);

                    try (java.io.OutputStream os = sendConn.getOutputStream()) {
                        os.write(payload.getBytes(StandardCharsets.UTF_8));
                    }

                    int sendResponseCode = sendConn.getResponseCode();
                    if (sendResponseCode != 200) {
                        throw new RuntimeException("Failed to send test message (code: " + sendResponseCode + ")");
                    }
                },
                () -> {
                    showAlert(Alert.AlertType.INFORMATION, t("common.success"),
                            "✅ Telegram connection successful!\nBot verified and test message sent to chat.");
                    setStatusSuccess("Telegram connection test successful");
                },
                exception -> {
                    log.error("Telegram test failed", exception);
                    showAlert(Alert.AlertType.ERROR, t("common.connectionFailed"),
                            "Telegram connection failed:\n" + exception.getMessage());
                    setStatusError("Telegram connection test failed: " + exception.getMessage());
                });
    }

    private void testEmailConnection() {
        String smtpServer = smtpServerField.getText().trim();
        int smtpPort = smtpPortSpinner.getValue();
        String emailAddress = emailAddressField.getText().trim();
        String password = emailPasswordField.getText();
        boolean useTls = enableTlsCheckbox.isSelected();

        if (smtpServer.isEmpty() || emailAddress.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, t("common.validationError"),
                    "Please fill in SMTP Server, Email, and Password");
            return;
        }

        if (!emailAddress.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            showAlert(Alert.AlertType.WARNING, t("common.validationError"), "Invalid email address format");
            return;
        }

        runButtonTask(
                testEmailButton,
                "Testing...",
                "Testing Email connection...",
                () -> {
                    Properties mailProps = new Properties();
                    mailProps.put("mail.smtp.host", smtpServer);
                    mailProps.put("mail.smtp.port", String.valueOf(smtpPort));
                    mailProps.put("mail.smtp.auth", "true");
                    configureSmtpTls(mailProps, smtpServer, smtpPort, useTls);
                    mailProps.put("mail.smtp.connectiontimeout", "5000");
                    mailProps.put("mail.smtp.timeout", "5000");
                    mailProps.put("mail.smtp.writetimeout", "5000");

                    Session session = Session.getInstance(mailProps, new jakarta.mail.Authenticator() {
                        @Override
                        protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                            return new jakarta.mail.PasswordAuthentication(emailAddress, password);
                        }
                    });
                    session.setDebug(false);

                    Message message = new MimeMessage(session);
                    message.setFrom(new InternetAddress(emailAddress));
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailAddress));
                    message.setSubject("InvestPro Email Test");
                    message.setText("""
                            ✅ InvestPro email connection test successful!

                            This is a test message from InvestPro.
                            Email notifications are now configured and ready to use.
                            """);

                    Transport.send(message);
                },
                () -> {
                    showAlert(Alert.AlertType.INFORMATION, t("common.success"),
                            "✅ Email connection successful!\nTest message sent to: " + emailAddress);
                    setStatusSuccess("Email connection test successful");
                },
                exception -> {
                    log.error("Email test failed", exception);
                    String errorMsg = exception.getMessage() == null ? "Unknown email error" : exception.getMessage();
                    if (errorMsg.contains("Connection refused")) {
                        errorMsg = "Cannot connect to SMTP server. Check server address and port.";
                    } else if (errorMsg.toLowerCase().contains("timeout")) {
                        errorMsg = "Connection timeout. SMTP server is not responding.";
                    } else if (errorMsg.toLowerCase().contains("could not convert socket to tls")
                            || errorMsg.toLowerCase().contains("starttls")) {
                        errorMsg = "SMTP TLS negotiation failed. Use port 587 for STARTTLS, or port 465 for SSL/TLS. "
                                + "Also verify the SMTP server supports TLS and that your app password is correct.";
                    }
                    showAlert(Alert.AlertType.ERROR, t("common.connectionFailed"), "Email connection failed:\n" + errorMsg);
                    setStatusError("Email test failed: " + errorMsg);
                });
    }

    private void configureSmtpTls(Properties mailProps, String smtpServer, int smtpPort, boolean useTls) {
        boolean implicitSsl = smtpPort == 465;
        boolean startTls = useTls && !implicitSsl;

        mailProps.put("mail.smtp.ssl.enable", String.valueOf(implicitSsl));
        mailProps.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        mailProps.put("mail.smtp.starttls.required", String.valueOf(startTls));
        mailProps.put("mail.smtp.ssl.trust", smtpServer);
        mailProps.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
    }

    private void testOpenAIConnection() {
        String apiKey = openaiApiKeyField.getText().trim();
        String model = openaiModelCombo.getValue();

        if (apiKey.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, t("common.validationError"), "Please enter your OpenAI API key");
            return;
        }

        if (!apiKey.startsWith("sk-")) {
            showAlert(Alert.AlertType.WARNING, t("common.validationError"), "Invalid API key format. It should start with 'sk-'.");
            return;
        }

        runButtonTask(
                testOpenAIButton,
                "Testing...",
                "Testing OpenAI connection...",
                () -> {
                    String apiUrl = "https://api.openai.com/v1/models/" + model;
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        return;
                    }
                    if (responseCode == 401) {
                        throw new RuntimeException("Invalid API key (401 Unauthorized). Check your API key.");
                    }
                    if (responseCode == 404) {
                        throw new RuntimeException("Model not found (404). Check your model name or subscription.");
                    }
                    if (responseCode == 429) {
                        throw new RuntimeException("Rate limited (429). Too many requests. Wait and try again.");
                    }
                    throw new RuntimeException("OpenAI API error (code: " + responseCode + ")");
                },
                () -> {
                    showAlert(Alert.AlertType.INFORMATION, t("common.success"),
                            "✅ OpenAI connection successful!\nAPI key validated and model '" + model + "' is accessible.");
                    setStatusSuccess("OpenAI connection test successful");
                },
                exception -> {
                    log.error("OpenAI test failed", exception);
                    showAlert(Alert.AlertType.ERROR, t("common.connectionFailed"),
                            "OpenAI connection failed:\n" + exception.getMessage());
                    setStatusError("OpenAI test failed: " + exception.getMessage());
                });
    }

    private void runButtonTask(Button button,
                               String busyText,
                               String statusText,
                               ThrowingRunnable task,
                               Runnable onSuccess,
                               java.util.function.Consumer<Exception> onFailure) {
        String originalText = button.getText();
        button.setDisable(true);
        button.setText(busyText);
        setStatusInfo(statusText);

        Thread thread = new Thread(() -> {
            try {
                task.run();
                Platform.runLater(onSuccess);
            } catch (Exception exception) {
                Platform.runLater(() -> onFailure.accept(exception));
            } finally {
                Platform.runLater(() -> {
                    button.setDisable(false);
                    button.setText(originalText);
                });
            }
        });
        thread.setName("settings-task-" + originalText.toLowerCase().replace(' ', '-'));
        thread.setDaemon(true);
        thread.start();
    }

    private void updateStreamingModeDescription(SystemCore.StreamingMode mode) {
        streamingModeDescriptionLabel.setText(mode == null ? "" : mode.description);
    }

    private void handleStartStreaming() {
        if (systemCore == null) {
            showAlert(Alert.AlertType.WARNING, t("common.error"), "SystemCore is not available");
            setStatusError("Error: SystemCore not initialized");
            return;
        }

        if (!enableStreamingCheckbox.isSelected()) {
            showAlert(Alert.AlertType.WARNING, t("common.validationError"), "Please enable Live Market Streaming first");
            return;
        }

        SystemCore.StreamingMode mode = streamingModeCombo.getValue();
        if (mode == null) {
            mode = SystemCore.StreamingMode.SAFE_DEFAULT;
            streamingModeCombo.setValue(mode);
        }

        SystemCore.StreamingMode finalMode = mode;
        runButtonTask(
                startStreamingButton,
                "⏳ Starting...",
                "Starting streaming in " + finalMode.name() + " mode...",
                () -> {
                    if (systemCore.getSelectedTradePair() == null) {
                        systemCore.switchStreamingMode(finalMode);
                    } else {
                        systemCore.startStreaming(systemCore.getSelectedTradePair(), finalMode);
                    }
                },
                () -> {
                    updateStreamingStatusUI(systemCore.isStreaming());
                    if (systemCore.getSelectedTradePair() == null) {
                        setStatusWarning("Streaming mode set, but no trading pair is selected");
                        showAlert(Alert.AlertType.INFORMATION, t("common.info"),
                                "Please select a trading symbol first.\nStreaming mode set to " + finalMode.name());
                    } else {
                        setStatusSuccess("✅ Streaming started with mode: " + finalMode.name());
                        log.info("Streaming started: pair={}, mode={}", systemCore.getSelectedTradePair(), finalMode);
                    }
                },
                exception -> {
                    log.error("Error starting stream", exception);
                    updateStreamingStatusUI(false);
                    setStatusError("Error: " + exception.getMessage());
                    showAlert(Alert.AlertType.ERROR, t("common.error"),
                            "Failed to start streaming:\n" + exception.getMessage());
                });
    }

    private void handleStopStreaming() {
        if (systemCore == null) {
            showAlert(Alert.AlertType.WARNING, t("common.error"), "SystemCore is not available");
            setStatusError("Error: SystemCore not initialized");
            return;
        }

        runButtonTask(
                stopStreamingButton,
                "⏳ Stopping...",
                "Stopping streaming...",
                systemCore::stopStreaming,
                () -> {
                    updateStreamingStatusUI(false);
                    setStatusSuccess("✅ Streaming stopped");
                    log.info("Streaming stopped");
                },
                exception -> {
                    log.error("Error stopping stream", exception);
                    setStatusError("Error: " + exception.getMessage());
                    showAlert(Alert.AlertType.ERROR, t("common.error"),
                            "Failed to stop streaming:\n" + exception.getMessage());
                });
    }

    private void updateStreamingUI() {
        updateStreamingStatusUI(systemCore != null && systemCore.isStreaming());
    }

    private void updateStreamingStatusUI(boolean isStreaming) {
        streamingStatusLabel.setText(isStreaming ? t("settings.streamingActive") : t("settings.notStreaming"));
        streamingStatusLabel.setStyle("-fx-text-fill: " + (isStreaming ? COLOR_SUCCESS : COLOR_MUTED) + "; -fx-font-size: 11;");
        startStreamingButton.setDisable(isStreaming || !enableStreamingCheckbox.isSelected());
        stopStreamingButton.setDisable(!isStreaming);
    }

    private HBox createButtonBox() {
        Button saveButton = new Button("Save Settings");
        saveButton.setStyle(buttonStyle(COLOR_SUCCESS));
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
        try {
            setStatusInfo("Saving settings...");

            SystemSafetySettings settings = buildSettingsFromUi();
            settings.save();

            applySettingsToSystemCore(settings);
            saveStreamingSettings();
            saveOpenAiSettings();
            saveTelegramSettings();
            saveEmailSettings();
            flushPreferences();

            setStatusSuccess("✅ Settings saved successfully");
            log.info("System settings saved: {}", settings);

            showAlert(Alert.AlertType.INFORMATION, t("settings.savedTitle"),
                    t("settings.savedMessage") + "\n\nAll settings have been persisted to disk.");
        } catch (Exception e) {
            log.error("Error saving settings", e);
            setStatusError("Error saving settings: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, t("common.error"), "Failed to save settings:\n" + e.getMessage());
        }
    }

    private void applySettings() {
        try {
            setStatusInfo("Applying settings...");

            SystemSafetySettings settings = buildSettingsFromUi();
            applySettingsToSystemCore(settings);
            saveStreamingSettings();
            saveOpenAiSettings();
            saveTelegramSettings();
            saveEmailSettings();
            flushPreferences();

            setStatusSuccess("✅ Settings applied to system");
            log.info("System settings applied: {}", settings);

            showAlert(Alert.AlertType.INFORMATION, t("common.info"),
                    "Settings applied successfully!\n\nThese settings are now active.");
        } catch (Exception e) {
            log.error("Error applying settings", e);
            setStatusError("Error applying settings: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, t("common.error"), "Failed to apply settings:\n" + e.getMessage());
        }
    }

    private void resetSettings() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(t("settings.resetTitle"));
        confirm.setHeaderText(t("settings.resetHeader"));
        confirm.setContentText(t("settings.resetMessage") + "\n\nThis action cannot be undone.");
        confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.CANCEL);

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.YES) {
            setStatusNeutral("Reset cancelled");
            return;
        }

        try {
            setStatusInfo("Resetting to defaults...");

            SystemSafetySettings defaults = SystemSafetySettings.defaults();
            applySettingsToUi(defaults);
            defaults.save();
            applySettingsToSystemCore(defaults);

            streamingModeCombo.setValue(SystemCore.StreamingMode.SAFE_DEFAULT);
            enableStreamingCheckbox.setSelected(false);
            saveStreamingSettings();
            updateStreamingUI();

            enableOpenAiCheckbox.setSelected(false);
            openaiApiKeyField.clear();
            openaiModelCombo.setValue("gpt-4o");
            temperatureSpinner.getValueFactory().setValue(0.7);
            maxTokensSpinner.getValueFactory().setValue(1000);
            saveOpenAiSettings();

            enableTelegramCheckbox.setSelected(false);
            telegramBotTokenField.clear();
            telegramChatIdField.clear();
            saveTelegramSettings();

            enableEmailCheckbox.setSelected(false);
            smtpServerField.clear();
            smtpPortSpinner.getValueFactory().setValue(587);
            emailAddressField.clear();
            emailPasswordField.clear();
            enableTlsCheckbox.setSelected(true);
            saveEmailSettings();

            flushPreferences();
            setStatusSuccess("✅ All settings reset to defaults");
            log.info("System settings reset to defaults");
            showAlert(Alert.AlertType.INFORMATION, t("common.success"), "All settings have been reset to their default values.");
        } catch (Exception e) {
            log.error("Error resetting settings", e);
            setStatusError("Error resetting settings: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, t("common.error"), "Failed to reset settings:\n" + e.getMessage());
        }
    }

    private void loadSettings() {
        SystemSafetySettings settings = SystemSafetySettings.load();
        applySettingsToUi(settings);
        applySettingsToSystemCore(settings);

        loadStreamingSettings();
        loadOpenAiSettings();
        loadTelegramSettings();
        loadEmailSettings();

        setStatusNeutral(t("settings.loaded"));
        log.info("System settings loaded: {}", settings);
    }

    private void saveStreamingSettings() {
        SystemCore.StreamingMode mode = streamingModeCombo.getValue();
        if (mode != null) {
            PREFS.put("streamingMode", mode.name());
        }
        PREFS.putBoolean("enableStreaming", enableStreamingCheckbox.isSelected());
        log.debug("Streaming settings saved");
    }

    private void loadStreamingSettings() {
        String savedStreamingMode = PREFS.get("streamingMode", SystemCore.StreamingMode.SAFE_DEFAULT.name());
        try {
            streamingModeCombo.setValue(SystemCore.StreamingMode.valueOf(savedStreamingMode));
        } catch (IllegalArgumentException e) {
            streamingModeCombo.setValue(SystemCore.StreamingMode.SAFE_DEFAULT);
        }
        enableStreamingCheckbox.setSelected(PREFS.getBoolean("enableStreaming", false));
        updateStreamingUI();
    }

    private void saveOpenAiSettings() {
        PREFS.putBoolean("openaiEnabled", enableOpenAiCheckbox.isSelected());
        PREFS.put("openaiApiKey", openaiApiKeyField.getText());
        PREFS.put("openaiModel", openaiModelCombo.getValue());
        PREFS.putDouble("openaiTemperature", temperatureSpinner.getValue());
        PREFS.putInt("openaiMaxTokens", maxTokensSpinner.getValue());
        log.debug("OpenAI settings saved");
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
        log.debug("Telegram settings saved");
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
        log.debug("Email settings saved");
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
            log.warn("SystemCore does not support applySystemSettings(SystemSafetySettings): {}", exception.getMessage());
        }
    }

    /**
     * Applies system settings using InvestPro-neutral keys.
     *
     * <p>All previous investpro.* keys have been removed.</p>
     */
    private void applySettingsAsSystemProperties(@NotNull SystemSafetySettings settings) {
        System.setProperty(SETTINGS_PREFIX + ".strategy.requireBacktestBeforeLive", String.valueOf(settings.requireBacktestBeforeLive()));
        System.setProperty(SETTINGS_PREFIX + ".strategy.requirePaperTradingBeforeLive", String.valueOf(settings.requirePaperTradingBeforeLive()));
        System.setProperty(SETTINGS_PREFIX + ".strategy.autoAssignBest", String.valueOf(settings.autoAssignBestStrategy()));
        System.setProperty(SETTINGS_PREFIX + ".strategy.minScore", String.valueOf(settings.minStrategyScore()));
        System.setProperty(SETTINGS_PREFIX + ".strategy.topCandidates", String.valueOf(settings.topStrategiesToPaperTrade()));
        System.setProperty("investpro.strategy.autoAssignBest", String.valueOf(settings.autoAssignBestStrategy()));
        System.setProperty("investpro.strategy.minScore", String.valueOf(settings.minStrategyScore()));
        System.setProperty("investpro.strategy.minStrategyScore", String.valueOf(settings.minStrategyScore()));

        System.setProperty(SETTINGS_PREFIX + ".execution.smallAccountMode", String.valueOf(settings.smallAccountModeEnabled()));
        System.setProperty(SETTINGS_PREFIX + ".execution.smallAccountThreshold", String.valueOf(settings.smallAccountThreshold()));
        System.setProperty(SETTINGS_PREFIX + ".execution.smallAccountTradeUnits", String.valueOf(settings.smallAccountUnits()));
        System.setProperty(SETTINGS_PREFIX + ".execution.preventOpenCloseSameCycle", String.valueOf(settings.preventOpenCloseSameCycle()));
        System.setProperty(SETTINGS_PREFIX + ".execution.preventInstantReverse", String.valueOf(settings.preventInstantReverse()));
        System.setProperty(SETTINGS_PREFIX + ".execution.symbolCooldownSeconds", String.valueOf(settings.symbolCooldownSeconds()));
    }

    private void addRow(@NonNull GridPane grid, int row, String label, Control control) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-text-fill: " + COLOR_MUTED + ";");
        control.setStyle(inputStyle());
        grid.add(labelNode, 0, row);
        grid.add(control, 1, row);
    }

    private void addRow(@NonNull GridPane grid, int row, String label, Label valueLabel) {
        Label labelNode = new Label(label);
        labelNode.setStyle("-fx-text-fill: " + COLOR_MUTED + ";");
        grid.add(labelNode, 0, row);
        grid.add(valueLabel, 1, row);
    }

    private Label sectionTitle(String text, String color) {
        Label sectionTitle = new Label(text);
        sectionTitle.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        return sectionTitle;
    }

    private GridPane sectionGrid(String borderColor) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setStyle("-fx-background-color: " + COLOR_PANEL + "; "
                + "-fx-border-color: " + borderColor + "; "
                + "-fx-border-width: 1; "
                + "-fx-border-radius: 6; "
                + "-fx-background-radius: 6;");
        return grid;
    }

    private CheckBox styledCheckBox(String text, boolean selected) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.setSelected(selected);
        checkBox.setStyle("-fx-text-fill: " + COLOR_MUTED + ";");
        return checkBox;
    }

    private Spinner<Double> doubleSpinner(double min, double max, double initial, double step) {
        Spinner<Double> spinner = new Spinner<>(min, max, initial, step);
        spinner.setEditable(true);
        spinner.getEditor().setStyle(inputStyle());
        return spinner;
    }

    private Spinner<Integer> intSpinner(int min, int max, int initial, int step) {
        Spinner<Integer> spinner = new Spinner<>(min, max, initial, step);
        spinner.setEditable(true);
        spinner.getEditor().setStyle(inputStyle());
        return spinner;
    }

    private String inputStyle() {
        return "-fx-control-inner-background: " + COLOR_INPUT + "; -fx-text-fill: " + COLOR_TEXT + ";";
    }

    private String buttonStyle(String color) {
        return "-fx-padding: 8 24; "
                + "-fx-background-color: " + color + "; "
                + "-fx-text-fill: white; "
                + "-fx-font-size: 12; "
                + "-fx-background-radius: 6; "
                + "-fx-cursor: hand;";
    }

    private void setStatusInfo(String text) {
        setStatus(text, COLOR_INFO);
    }

    private void setStatusSuccess(String text) {
        setStatus(text, COLOR_SUCCESS);
    }

    private void setStatusError(String text) {
        setStatus(text, COLOR_ERROR);
    }

    private void setStatusWarning(String text) {
        setStatus(text, COLOR_WARNING);
    }

    private void setStatusNeutral(String text) {
        setStatus(text, COLOR_MUTED);
    }

    private void setStatus(String text, String color) {
        if (statusLabel == null) {
            return;
        }
        statusLabel.setText(text);
        statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11;");
    }

    private void flushPreferences() {
        try {
            PREFS.flush();
        } catch (Exception exception) {
            throw new RuntimeException("Failed to flush settings to disk: " + exception.getMessage(), exception);
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
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
