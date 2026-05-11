package org.investpro.ui.panels;

import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.i18n.LocalizationService;
import org.jetbrains.annotations.NotNull;

import jakarta.mail.Message;
import jakarta.mail.Session;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.prefs.Preferences;

import static org.investpro.i18n.LocalizationService.t;

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
    private Button testOpenAIButton;

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
        LocalizationService.applyTranslations(this);
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
        String botToken = telegramBotTokenField.getText().trim();
        String chatId = telegramChatIdField.getText().trim();

        if (botToken.isEmpty() || chatId.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, t("common.validationError"), "Please enter bot token and chat ID");
            return;
        }

        // Disable button and show loading state
        testTelegramButton.setDisable(true);
        testTelegramButton.setText("Testing...");
        statusLabel.setText("Testing Telegram connection...");

        // Run test in background thread
        Thread testThread = new Thread(() -> {
            try {
                // Test 1: Verify bot token format (should be numbers:string)
                if (!botToken.matches("^\\d+:[a-zA-Z0-9_-]+$")) {
                    throw new IllegalArgumentException("Invalid bot token format");
                }

                // Test 2: Verify chat ID is numeric
                try {
                    Long.parseLong(chatId);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Chat ID must be a number");
                }

                // Test 3: Make HTTP request to Telegram Bot API
                String apiUrl = "https://api.telegram.org/bot" + botToken + "/getMe";
                java.net.URL url = URI.create(apiUrl).toURL();
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();

                if (responseCode == 200) {
                    // Test 4: Try sending a test message
                    String sendUrl = "https://api.telegram.org/bot" + botToken + "/sendMessage";
                    java.net.URL sendUrlObj = URI.create(sendUrl).toURL();
                    java.net.HttpURLConnection sendConn = (java.net.HttpURLConnection) sendUrlObj.openConnection();
                    sendConn.setRequestMethod("POST");
                    sendConn.setConnectTimeout(5000);
                    sendConn.setReadTimeout(5000);
                    sendConn.setDoOutput(true);
                    sendConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    String payload = "chat_id=" + chatId + "&text=" + java.net.URLEncoder.encode(
                            "✅ InvestPro Telegram connection test successful!", StandardCharsets.UTF_8);
                    try (java.io.OutputStream os = sendConn.getOutputStream()) {
                        byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    int sendResponseCode = sendConn.getResponseCode();
                    if (sendResponseCode == 200) {
                        Platform.runLater(() -> {
                            showAlert(Alert.AlertType.INFORMATION, t("common.success"),
                                    "✅ Telegram connection successful!\n" +
                                            "Bot verified and test message sent to chat.");
                            statusLabel.setText("Telegram connection test successful");
                            statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11;");
                        });
                    } else {
                        throw new RuntimeException("Failed to send test message (code: " + sendResponseCode + ")");
                    }
                } else if (responseCode == 401) {
                    throw new RuntimeException("Invalid bot token (401 Unauthorized)");
                } else {
                    throw new RuntimeException("Telegram API error (code: " + responseCode + ")");
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    log.error("Telegram test failed", e);
                    showAlert(Alert.AlertType.ERROR, t("common.connectionFailed"),
                            "Telegram connection failed:\n" + e.getMessage());
                    statusLabel.setText("Telegram connection test failed: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
                });
            } finally {
                Platform.runLater(() -> {
                    testTelegramButton.setDisable(false);
                    testTelegramButton.setText("Test Connection");
                });
            }
        });

        testThread.setName("telegram-test-thread");
        testThread.setDaemon(true);
        testThread.start();
    }

    Properties props = new Properties();

    private void testEmailConnection() {
        String smtpServer = smtpServerField.getText().trim();
        int smtpPort = smtpPortSpinner.getValue();
        String emailAddress = emailAddressField.getText().trim();
        String password = emailPasswordField.getText();
        boolean useTls = enableTlsCheckbox.isSelected();

        if (smtpServer.isEmpty() || emailAddress.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, t("common.validationError"),
                    "Please fill in all required fields: SMTP Server, Email, and Password");
            return;
        }

        // Validate email format
        if (!emailAddress.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            showAlert(Alert.AlertType.WARNING, t("common.validationError"),
                    "Invalid email address format");
            return;
        }

        // Disable button and show loading state
        testEmailButton.setDisable(true);
        testEmailButton.setText("Testing...");
        statusLabel.setText("Testing Email connection...");

        // Run test in background thread
        Thread testThread = new Thread(() -> {
            try {
                // Test SMTP connection using JavaMail API

                props.put("mail.smtp.host", smtpServer);
                props.put("mail.smtp.port", String.valueOf(smtpPort));
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", String.valueOf(useTls));
                props.put("mail.smtp.starttls.required", String.valueOf(useTls));
                props.put("mail.smtp.socketFactory.port", String.valueOf(smtpPort));
                if (useTls) {
                    props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                }
                props.put("mail.smtp.connectiontimeout", "5000");
                props.put("mail.smtp.timeout", "5000");

                // Create session with authentication
                Session session = Session.getInstance(props,
                        new jakarta.mail.Authenticator() {
                            @Override
                            protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                                return new jakarta.mail.PasswordAuthentication(emailAddress, password);
                            }
                        });

                session.setDebug(false);

                // Create test message
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(emailAddress));
                message.setRecipients(Message.RecipientType.TO,
                        InternetAddress.parse(emailAddress));
                message.setSubject("InvestPro Email Test");
                message.setText("""
                        ✅ InvestPro email connection test successful!

                        This is a test message from InvestPro trading platform.
                        Email notifications are now configured and ready to use.""");

                // Send test message
                Transport.send(message);

                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, t("common.success"),
                            "✅ Email connection successful!\n" +
                                    "Test message sent to: " + emailAddress);
                    statusLabel.setText("Email connection test successful");
                    statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11;");
                });

            } catch (jakarta.mail.AuthenticationFailedException e) {
                Platform.runLater(() -> {
                    log.error("Email authentication failed", e);
                    showAlert(Alert.AlertType.ERROR, t("common.connectionFailed"),
                            "Authentication failed. Check your email and password.");
                    statusLabel.setText("Email test failed: Authentication error");
                    statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
                });
            } catch (jakarta.mail.MessagingException e) {
                Platform.runLater(() -> {
                    log.error("Email test failed", e);
                    String errorMsg = e.getMessage();
                    if (errorMsg.contains("Connection refused")) {
                        errorMsg = "Cannot connect to SMTP server. Check server address and port.";
                    } else if (errorMsg.contains("timeout")) {
                        errorMsg = "Connection timeout. SMTP server is not responding.";
                    }
                    showAlert(Alert.AlertType.ERROR, t("common.connectionFailed"),
                            "Email connection failed:\n" + errorMsg);
                    statusLabel.setText("Email test failed: " + errorMsg);
                    statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    log.error("Email test failed", e);
                    showAlert(Alert.AlertType.ERROR, t("common.connectionFailed"),
                            "Unexpected error:\n" + e.getMessage());
                    statusLabel.setText("Email test failed: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
                });
            } finally {
                Platform.runLater(() -> {
                    testEmailButton.setDisable(false);
                    testEmailButton.setText("Test Connection");
                });
            }
        });

        testThread.setName("email-test-thread");
        testThread.setDaemon(true);
        testThread.start();
    }

    private void testOpenAIConnection() {
        String apiKey = openaiApiKeyField.getText().trim();
        String model = openaiModelCombo.getValue();

        if (apiKey.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, t("common.validationError"),
                    "Please enter your OpenAI API key");
            return;
        }

        if (!apiKey.startsWith("sk-")) {
            showAlert(Alert.AlertType.WARNING, t("common.validationError"),
                    "Invalid API key format (should start with 'sk-')");
            return;
        }

        // Disable button and show loading state
        testOpenAIButton.setDisable(true);
        testOpenAIButton.setText("Testing...");
        statusLabel.setText("Testing OpenAI connection...");
        statusLabel.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 11;");

        // Run test in background thread
        Thread testThread = new Thread(() -> {
            try {
                // Create HTTP request to OpenAI API
                String apiUrl = "https://api.openai.com/v1/models/" + model;
                java.net.URL url = URI.create(apiUrl).toURL();
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();

                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();

                if (responseCode == 200) {
                    // Successfully authenticated and model exists
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.INFORMATION, t("common.success"),
                                "✅ OpenAI connection successful!\n" +
                                        "API key validated and model '" + model + "' is accessible.");
                        statusLabel.setText("OpenAI connection test successful");
                        statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11;");
                    });
                } else if (responseCode == 401) {
                    throw new RuntimeException("Invalid API key (401 Unauthorized). Check your API key.");
                } else if (responseCode == 404) {
                    throw new RuntimeException("Model not found (404). Check your model name or subscription.");
                } else if (responseCode == 429) {
                    throw new RuntimeException("Rate limited (429). Too many requests. Wait and try again.");
                } else {
                    throw new RuntimeException("OpenAI API error (code: " + responseCode + ")");
                }

                conn.disconnect();

            } catch (java.net.MalformedURLException e) {
                Platform.runLater(() -> {
                    log.error("OpenAI test failed - invalid URL", e);
                    showAlert(Alert.AlertType.ERROR, t("common.connectionFailed"),
                            "Invalid model name or URL format.");
                    statusLabel.setText("OpenAI test failed: Invalid URL");
                    statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
                });
            } catch (java.io.IOException e) {
                Platform.runLater(() -> {
                    log.error("OpenAI test failed", e);
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("timeout")) {
                        errorMsg = "Connection timeout. Check your internet connection.";
                    } else if (errorMsg == null) {
                        errorMsg = "Network error - unable to reach OpenAI API";
                    }
                    showAlert(Alert.AlertType.ERROR, t("common.connectionFailed"),
                            "OpenAI connection failed:\n" + errorMsg);
                    statusLabel.setText("OpenAI test failed: " + errorMsg);
                    statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    log.error("OpenAI test failed", e);
                    showAlert(Alert.AlertType.ERROR, t("common.connectionFailed"),
                            "OpenAI connection failed:\n" + e.getMessage());
                    statusLabel.setText("OpenAI test failed: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
                });
            } finally {
                Platform.runLater(() -> {
                    testOpenAIButton.setDisable(false);
                    testOpenAIButton.setText("Test Connection");
                });
            }
        });

        testThread.setName("openai-test-thread");
        testThread.setDaemon(true);
        testThread.start();
    }

    private void updateStreamingModeDescription(SystemCore.StreamingMode mode) {
        if (mode != null) {
            streamingModeDescriptionLabel.setText(mode.description);
        }
    }

    private void handleStartStreaming() {
        if (systemCore == null) {
            showAlert(Alert.AlertType.WARNING, t("common.error"), "SystemCore is not available");
            statusLabel.setText("Error: SystemCore not initialized");
            statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
            return;
        }

        if (!enableStreamingCheckbox.isSelected()) {
            showAlert(Alert.AlertType.WARNING, t("common.validationError"),
                    "Please enable Live Market Streaming first");
            return;
        }

        SystemCore.StreamingMode mode = streamingModeCombo.getValue();
        if (mode == null) {
            mode = SystemCore.StreamingMode.SAFE_DEFAULT;
            streamingModeCombo.setValue(mode);
        }

        // Show loading state
        startStreamingButton.setDisable(true);
        startStreamingButton.setText("⏳ Starting...");
        statusLabel.setText("Starting streaming in " + mode.name() + " mode...");
        statusLabel.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 11;");

        // Run in background thread to avoid blocking UI
        SystemCore.StreamingMode finalMode = mode;
        SystemCore.StreamingMode finalMode1 = mode;
        Thread streamThread = new Thread(() -> {
            try {
                if (systemCore.getSelectedTradePair() == null) {
                    Platform.runLater(() -> {
                        statusLabel.setText("No trading pair selected");
                        statusLabel.setStyle("-fx-text-fill: #fbbf24; -fx-font-size: 11;");
                        showAlert(Alert.AlertType.INFORMATION, t("common.info"),
                                "Please select a trading symbol first.\nStreaming mode set to " + finalMode.name());
                        systemCore.switchStreamingMode(finalMode);
                    });
                } else {
                    systemCore.startStreaming(systemCore.getSelectedTradePair(), finalMode1);
                    Platform.runLater(() -> {
                        updateStreamingStatusUI(true);
                        statusLabel.setText("✅ Streaming started with mode: " + finalMode1.name());
                        statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11;");
                        log.info("Streaming started: pair={}, mode={}", systemCore.getSelectedTradePair(), finalMode1);
                    });
                }
            } catch (Exception e) {
                log.error("Error starting stream", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
                    showAlert(Alert.AlertType.ERROR, t("common.error"),
                            "Failed to start streaming:\n" + e.getMessage());
                    updateStreamingStatusUI(false);
                });
            } finally {
                Platform.runLater(() -> {
                    startStreamingButton.setDisable(systemCore.isStreaming() || !enableStreamingCheckbox.isSelected());
                    startStreamingButton.setText("▶ Start Streaming");
                });
            }
        });

        streamThread.setName("stream-start-thread");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    private void handleStopStreaming() {
        if (systemCore == null) {
            showAlert(Alert.AlertType.WARNING, t("common.error"), "SystemCore is not available");
            statusLabel.setText("Error: SystemCore not initialized");
            statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
            return;
        }

        // Show loading state
        stopStreamingButton.setDisable(true);
        stopStreamingButton.setText("⏳ Stopping...");
        statusLabel.setText("Stopping streaming...");
        statusLabel.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 11;");

        // Run in background thread
        Thread stopThread = new Thread(() -> {
            try {
                systemCore.stopStreaming();
                Platform.runLater(() -> {
                    updateStreamingStatusUI(false);
                    statusLabel.setText("✅ Streaming stopped");
                    statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11;");
                    log.info("Streaming stopped");
                });
            } catch (Exception e) {
                log.error("Error stopping stream", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
                    showAlert(Alert.AlertType.ERROR, t("common.error"),
                            "Failed to stop streaming:\n" + e.getMessage());
                });
            } finally {
                Platform.runLater(() -> {
                    stopStreamingButton.setDisable(true);
                    stopStreamingButton.setText("⏸ Stop Streaming");
                });
            }
        });

        stopThread.setName("stream-stop-thread");
        stopThread.setDaemon(true);
        stopThread.start();
    }

    private void updateStreamingUI() {
        if (systemCore != null) {
            updateStreamingStatusUI(systemCore.isStreaming());
        }
    }

    private void updateStreamingStatusUI(boolean isStreaming) {
        streamingStatusLabel.setText(isStreaming ? t("settings.streamingActive") : t("settings.notStreaming"));
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
        try {
            statusLabel.setText("Saving settings...");
            statusLabel.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 11;");

            SystemSafetySettings settings = buildSettingsFromUi();
            settings.save();

            applySettingsToSystemCore(settings);
            saveStreamingSettings();
            saveOpenAiSettings();
            saveTelegramSettings();
            saveEmailSettings();

            statusLabel.setText("✅ Settings saved successfully");
            statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11;");
            log.info("System settings saved: {}", settings);

            showAlert(
                    Alert.AlertType.INFORMATION,
                    t("settings.savedTitle"),
                    t("settings.savedMessage") + "\n\nAll settings have been persisted to disk.");
        } catch (Exception e) {
            log.error("Error saving settings", e);
            statusLabel.setText("Error saving settings: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
            showAlert(Alert.AlertType.ERROR, t("common.error"),
                    "Failed to save settings:\n" + e.getMessage());
        }
    }

    private void saveStreamingSettings() {
        try {
            SystemCore.StreamingMode mode = streamingModeCombo.getValue();
            if (mode != null) {
                PREFS.put("streamingMode", mode.name());
            }
            PREFS.putBoolean("enableStreaming", enableStreamingCheckbox.isSelected());
            // Flush preferences to disk to ensure persistence
            PREFS.flush();
            log.info("Streaming settings saved and flushed to disk");
        } catch (Exception e) {
            log.error("Error saving streaming settings", e);
            throw new RuntimeException("Failed to save streaming settings: " + e.getMessage(), e);
        }
    }

    private void applySettings() {
        try {
            statusLabel.setText("Applying settings...");
            statusLabel.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 11;");

            SystemSafetySettings settings = buildSettingsFromUi();
            applySettingsToSystemCore(settings);

            // Save streaming settings
            saveStreamingSettings();
            saveOpenAiSettings();
            saveTelegramSettings();
            saveEmailSettings();

            statusLabel.setText("✅ Settings applied to system");
            statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11;");
            log.info("System settings applied: {}", settings);

            showAlert(Alert.AlertType.INFORMATION, t("common.info"),
                    "Settings applied successfully!\n\nThese settings are now active and will take effect immediately.");
        } catch (Exception e) {
            log.error("Error applying settings", e);
            statusLabel.setText("Error applying settings: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
            showAlert(Alert.AlertType.ERROR, t("common.error"),
                    "Failed to apply settings:\n" + e.getMessage());
        }
    }

    private void resetSettings() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(t("settings.resetTitle"));
        confirm.setHeaderText(t("settings.resetHeader"));
        confirm.setContentText(t("settings.resetMessage") + "\n\nThis action cannot be undone.");
        confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.CANCEL);

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.YES) {
            statusLabel.setText("Reset cancelled");
            return;
        }

        try {
            statusLabel.setText("Resetting to defaults...");
            statusLabel.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 11;");

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

            statusLabel.setText("✅ All settings reset to defaults");
            statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 11;");
            log.info("System settings reset to defaults");

            showAlert(Alert.AlertType.INFORMATION, t("common.success"),
                    "All settings have been reset to their default values.");
        } catch (Exception e) {
            log.error("Error resetting settings", e);
            statusLabel.setText("Error resetting settings: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
            showAlert(Alert.AlertType.ERROR, t("common.error"),
                    "Failed to reset settings:\n" + e.getMessage());
        }
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

        statusLabel.setText(t("settings.loaded"));
        log.info("System settings loaded: {}", settings);
    }

    private void saveOpenAiSettings() {
        try {
            PREFS.putBoolean("openaiEnabled", enableOpenAiCheckbox.isSelected());
            PREFS.put("openaiApiKey", openaiApiKeyField.getText());
            PREFS.put("openaiModel", openaiModelCombo.getValue());
            PREFS.putDouble("openaiTemperature", temperatureSpinner.getValue());
            PREFS.putInt("openaiMaxTokens", maxTokensSpinner.getValue());
            // Flush preferences to disk to ensure persistence
            PREFS.flush();
            log.info("OpenAI settings saved and flushed to disk");
        } catch (Exception e) {
            log.error("Error saving OpenAI settings", e);
            throw new RuntimeException("Failed to save OpenAI settings: " + e.getMessage(), e);
        }
    }

    private void loadOpenAiSettings() {
        enableOpenAiCheckbox.setSelected(PREFS.getBoolean("openaiEnabled", false));
        openaiApiKeyField.setText(PREFS.get("openaiApiKey", ""));
        openaiModelCombo.setValue(PREFS.get("openaiModel", "gpt-4o"));
        temperatureSpinner.getValueFactory().setValue(PREFS.getDouble("openaiTemperature", 0.7));
        maxTokensSpinner.getValueFactory().setValue(PREFS.getInt("openaiMaxTokens", 1000));
    }

    private void saveTelegramSettings() {
        try {
            PREFS.putBoolean("telegramEnabled", enableTelegramCheckbox.isSelected());
            PREFS.put("telegramBotToken", telegramBotTokenField.getText());
            PREFS.put("telegramChatId", telegramChatIdField.getText());
            // Flush preferences to disk to ensure persistence
            PREFS.flush();
            log.info("Telegram settings saved and flushed to disk");
        } catch (Exception e) {
            log.error("Error saving Telegram settings", e);
            throw new RuntimeException("Failed to save Telegram settings: " + e.getMessage(), e);
        }
    }

    private void loadTelegramSettings() {
        enableTelegramCheckbox.setSelected(PREFS.getBoolean("telegramEnabled", false));
        telegramBotTokenField.setText(PREFS.get("telegramBotToken", ""));
        telegramChatIdField.setText(PREFS.get("telegramChatId", ""));
    }

    private void saveEmailSettings() {
        try {
            PREFS.putBoolean("emailEnabled", enableEmailCheckbox.isSelected());
            PREFS.put("smtpServer", smtpServerField.getText());
            PREFS.putInt("smtpPort", smtpPortSpinner.getValue());
            PREFS.put("emailAddress", emailAddressField.getText());
            PREFS.put("emailPassword", emailPasswordField.getText());
            PREFS.putBoolean("emailUseTls", enableTlsCheckbox.isSelected());
            // Flush preferences to disk to ensure persistence
            PREFS.flush();
            log.info("Email settings saved and flushed to disk");
        } catch (Exception e) {
            log.error("Error saving Email settings", e);
            throw new RuntimeException("Failed to save Email settings: " + e.getMessage(), e);
        }
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
