package org.investpro.ui;

import javafx.scene.control.*;
import lombok.extern.slf4j.Slf4j;

import org.investpro.exchange.*;
import org.investpro.exchange.contracts.CredentialProvider;
import org.investpro.exchange.factory.ExchangeFactory;
import org.investpro.exchange.providers.UiCredentialProvider;
import org.investpro.i18n.LocalizationService;
import org.investpro.i18n.SupportedLanguage;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Cursor;
import javafx.util.Duration;

import org.investpro.service.AuthResult;
import org.investpro.service.ResetTokenResult;
import org.investpro.service.UserAuthService;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import static org.investpro.i18n.LocalizationService.t;

/**
 * Onboarding view for InvestPro application.
 * Guides users through login, market configuration, and exchange credential
 * setup.
 * Uses centralized styling configuration from OnboardingStyles.
 *
 * @author NOEL NGUEMECHIEU
 */
@Slf4j
public class OnboardingView extends StackPane {
    private final Consumer<MarketConfiguration> onReady;
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField emailField = new TextField();
    private final CheckBox rememberMeCheckBox = new CheckBox(t("onboarding.rememberMe"));
    private final ComboBox<String> marketTypeBox = new ComboBox<>();
    private final ComboBox<String> venueBox = new ComboBox<>();
    private final ComboBox<String> exchangeBox = new ComboBox<>();
    private final Label statusLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final UserAuthService authService = new UserAuthService();
    private MarketConfiguration configuration;
    private final TextField telegramToken = new TextField();
    private final TextField openAiField = new TextField();
    private final ChoiceBox<String> selectedTradingModeChoiceBox = new ChoiceBox<>();

    public OnboardingView(Consumer<MarketConfiguration> onReady) {
        this.onReady = Objects.requireNonNull(onReady);
        setPrefSize(1540, 780);

        OnboardingStyles.labelStyle(11, "#cbd5e1;", "16");

        loadRememberedCredentials();
        getChildren().setAll(createLoginStep());
    }

    private @NotNull BorderPane createLoginStep() {
        rememberMeCheckBox.setText(t("onboarding.rememberMe"));

        Label appName = new Label(t("onboarding.loginTitle"));
        appName.setStyle("-fx-font-size: 44px; -fx-font-weight: 700; -fx-text-fill: #3b82f6;");

        Label prompt = new Label(t("onboarding.loginPrompt"));
        prompt.setStyle("-fx-font-size: 16px; -fx-text-fill: #cbd5e1;");

        usernameField.setPromptText(t("onboarding.username"));
        usernameField.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        passwordField.setPromptText(t("onboarding.password"));
        passwordField.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        emailField.setPromptText(t("onboarding.email"));
        emailField.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");
        emailField.setVisible(false);
        emailField.setManaged(false);

        rememberMeCheckBox.setStyle("-fx-text-fill: #f1f5f9;");
        rememberMeCheckBox.setVisible(true);
        rememberMeCheckBox.setManaged(true);

        Button forgetButton = new Button(t("onboarding.forget"));
        forgetButton.setStyle("-fx-padding: 4 12; -fx-background-color: #1e40af; -fx-text-fill: white;");
        forgetButton.setOnAction(event -> forgetCredentials());

        Button forgotPasswordButton = new Button(t("onboarding.forgotPassword"));
        forgotPasswordButton.setStyle(
                "-fx-padding: 4 12; -fx-background-color: transparent; -fx-border-color: #475569; -fx-text-fill: #bfdbfe;");

        HBox rememberBox = new HBox(10, rememberMeCheckBox, forgetButton, forgotPasswordButton);
        rememberBox.setAlignment(Pos.CENTER);

        Button loginButton = new Button(t("onboarding.logIn"));
        loginButton.setStyle(
                "-fx-padding: 10 20; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");

        Button createButton = new Button(t("onboarding.createAccount"));
        createButton.setStyle(
                "-fx-padding: 10 20; -fx-background-color: #10b981; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");

        HBox buttonBox = new HBox(10, loginButton, createButton);
        buttonBox.setAlignment(Pos.CENTER);

        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ef4444;");
        validation.setAlignment(Pos.CENTER);
        forgotPasswordButton.setOnAction(event -> showForgotPasswordDialog(validation));

        createButton.setOnAction(event -> {
            if (!emailField.isVisible()) {
                emailField.setVisible(true);
                emailField.setManaged(true);
                validation.setStyle("-fx-text-fill: #fbbf24;");
                validation.setText(t("onboarding.createHint"));
                return;
            }

            char[] password = passwordField.getText().toCharArray();
            AuthResult result = authService.register(
                    usernameField.getText(),
                    emailField.getText(),
                    password);
            Arrays.fill(password, '\0');
            validation.setStyle("-fx-text-fill: %s;".formatted(result.success() ? "#22c55e" : "#ef4444"));
            validation.setText(result.message());
            if (result.success()) {
                if (rememberMeCheckBox.isSelected()) {
                    authService.rememberUser(usernameField.getText());
                }
                showConfigurationStep();
            }
        });

        loginButton.setOnAction(event -> {
            char[] password = passwordField.getText().toCharArray();
            AuthResult result = authService.signIn(usernameField.getText(), password);
            Arrays.fill(password, '\0');
            validation.setStyle("-fx-text-fill: %s;".formatted(result.success() ? "#22c55e" : "#ef4444"));
            validation.setText(result.message());
            if (!result.success()) {
                return;
            }
            if (rememberMeCheckBox.isSelected()) {
                authService.rememberUser(usernameField.getText());
            } else {
                authService.forgetRememberedUser();
            }
            passwordField.clear();
            showConfigurationStep();
        });

        VBox form = new VBox(12, usernameField, passwordField, emailField, rememberBox, buttonBox, validation);
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(360);

        VBox content = new VBox(18, appName, prompt, form);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(500);

        StackPane centerPane = new StackPane(content);
        centerPane.setAlignment(Pos.CENTER);

        BorderPane pane = new BorderPane(centerPane);
        pane.setTop(createLanguageSelector(() -> getChildren().setAll(createLoginStep())));
        pane.setStyle("-fx-background-color: #0f172a;");
        return pane;
    }

    private HBox createLanguageSelector(Runnable onChanged) {
        ComboBox<SupportedLanguage> languageBox = new ComboBox<>();
        languageBox.getItems().setAll(SupportedLanguage.values());
        languageBox.setValue(LocalizationService.getCurrentLanguage());
        languageBox.setPrefWidth(170);
        languageBox.setStyle(
                "-fx-background-color: #1e293b; -fx-text-fill: #f1f5f9; -fx-border-color: #475569;");
        languageBox.setOnAction(event -> {
            SupportedLanguage selected = languageBox.getValue();
            if (selected != null) {
                LocalizationService.setCurrentLanguage(selected);
                if (onChanged != null) {
                    onChanged.run();
                }
            }
        });

        HBox box = new HBox(8, new Label(LocalizationService.t("language.menu")), languageBox);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(14, 18, 0, 18));
        box.setStyle("-fx-text-fill: #cbd5e1;");
        return box;
    }

    private void showConfigurationStep() {
        marketTypeBox.getItems().setAll("Forex", "Crypto", "Stocks", "Futures", "Options", "ETFs", "Bonds");
        venueBox.getItems().setAll("US", "Global", "Spot", "Derivatives", "Paper Trading");

        // Dynamically populate exchanges from SupportedExchange enum
        java.util.Arrays.stream(SupportedExchange.values())
                .map(SupportedExchange::getDisplayName)
                .forEach(exchangeBox.getItems()::add);

        marketTypeBox.getSelectionModel().select("Crypto");
        venueBox.getSelectionModel().select("US");
        exchangeBox.getSelectionModel().select(SupportedExchange.COINBASE.getDisplayName());

        // Autoload credentials when exchange changes
        exchangeBox.setOnAction(event -> {
            if (marketTypeBox.getValue() != null && venueBox.getValue() != null && exchangeBox.getValue() != null) {
                showExchangeCredentialsStep();
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setAlignment(Pos.CENTER);
        grid.addRow(0, new Label("Market Type"), marketTypeBox);
        grid.addRow(1, new Label("Venue"), venueBox);
        grid.addRow(2, new Label("Exchange"), exchangeBox);

        Button loadMarketButton = new Button("Load Market");
        loadMarketButton.setStyle(
                "-fx-padding: 10 20; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold;");

        HBox loadButtonBox = new HBox(loadMarketButton);
        loadButtonBox.setAlignment(Pos.CENTER);

        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ef4444;");
        validation.setAlignment(Pos.CENTER);
        loadMarketButton.setOnAction(event -> {
            if (marketTypeBox.getValue() == null || venueBox.getValue() == null || exchangeBox.getValue() == null) {
                validation.setText("Select a market type, venue, and exchange.");
                return;
            }
            showExchangeCredentialsStep();
        });

        Label title = new Label("Market Configuration");
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: 700; -fx-text-fill: #3b82f6;");

        Label subtitle = new Label("Choose what you want to trade and where the terminal should connect.");
        subtitle.setStyle("-fx-font-size: 15px; -fx-text-fill: #cbd5e1;");

        VBox content = new VBox(18, title, subtitle, grid, loadButtonBox, validation);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(500);

        StackPane centerPane = new StackPane(content);
        centerPane.setAlignment(Pos.CENTER);

        BorderPane pane = new BorderPane(centerPane);
        pane.setStyle("-fx-background-color: #0f172a;");
        pane.setTop(loadMarketButton);
        fadeTo(pane);
    }

    final ChoiceBox<String> selectedTradingModeChoiseBox = new ChoiceBox<>();

    private void showExchangeCredentialsStep() {
        String selectedExchangeName = exchangeBox.getValue();
        SupportedExchange selectedExchange = SupportedExchange.fromDisplayName(selectedExchangeName);

        selectedTradingModeChoiceBox.getItems().clear();
        selectedTradingModeChoiceBox.getItems().addAll("LIVE", "PAPER TRADING");
        selectedTradingModeChoiceBox.setValue("PAPER TRADING");
        TextField apiKeyField = new TextField();
        apiKeyField.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        if (selectedExchange == SupportedExchange.OANDA) {
            apiKeyField.setPromptText("OANDA Token (Bearer Token)");
        } else {
            apiKeyField.setPromptText("API Key");
        }

        PasswordField apiSecretField = getPasswordField(selectedExchange);

        TextField accountIdField = new TextField();
        accountIdField.setPromptText("Account ID (auto-detected if blank)");
        accountIdField.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");
        accountIdField.setVisible(selectedExchange == SupportedExchange.OANDA);
        accountIdField.setManaged(selectedExchange == SupportedExchange.OANDA);

        // Telegram Token field (optional)
        telegramToken.setPromptText("Telegram Bot Token (optional)");
        telegramToken.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        // OpenAI field (optional)
        openAiField.setPromptText("OpenAI API Key (optional)");
        openAiField.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        // Load remembered credentials for this exchange if they exist
        loadRememberedExchangeCredentials(selectedExchangeName, apiKeyField, apiSecretField, accountIdField);

        HBox tradingModeBox = new HBox(20, selectedTradingModeChoiceBox);
        tradingModeBox.setAlignment(Pos.CENTER_LEFT);
        tradingModeBox.setStyle("-fx-padding: 8 0 0 0;");

        GridPane credGrid = new GridPane();
        credGrid.setHgap(14);
        credGrid.setVgap(14);
        credGrid.setAlignment(Pos.CENTER);

        Label apiKeyLabel = new Label(selectedExchange == SupportedExchange.OANDA ? "Token" : "API Key");
        credGrid.addRow(0, apiKeyLabel, apiKeyField);

        if (selectedExchange != SupportedExchange.OANDA) {
            credGrid.addRow(1, new Label("API Secret"), apiSecretField);
        }

        if (selectedExchange == SupportedExchange.OANDA) {
            credGrid.addRow(1, new Label("Account ID"), accountIdField);
        }

        credGrid.addRow(2, new Label("Telegram Token"), telegramToken);
        credGrid.addRow(3, new Label("OpenAI Key"), openAiField);
        credGrid.addRow(4, new Label("Mode"), tradingModeBox);

        CheckBox rememberCredentialsCheckBox = new CheckBox("Remember these credentials");
        rememberCredentialsCheckBox.setStyle("-fx-text-fill: #f1f5f9;");
        rememberCredentialsCheckBox.setPrefSize(40, 40);

        HBox rememberBox = new HBox(20, rememberCredentialsCheckBox);
        rememberBox.setAlignment(Pos.CENTER);

        Button continueButton = new Button("Continue");
        continueButton.setStyle(
                "-fx-padding: 20 30; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold;");

        Button backButton = new Button("Back");
        backButton.setStyle(
                "-fx-padding: 20 30; -fx-background-color: #1e40af; -fx-text-fill: white; -fx-font-weight: bold;");

        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ef4444;");
        validation.setAlignment(Pos.CENTER);

        continueButton.setOnAction(event -> {
            // Determine trading mode from radio button selection

            // For OANDA, only token is required (Account ID is optional - can be
            // auto-detected)
            if (selectedExchange == SupportedExchange.OANDA) {
                if (apiKeyField.getText().isBlank()) {
                    validation.setText(" Token is required.");
                    return;
                }
                // For OANDA, pass token as apiKey and accountId as apiSecret
                configuration = new MarketConfiguration(
                        usernameField.getText().trim(),
                        marketTypeBox.getValue(),
                        venueBox.getValue(),
                        selectedExchange.getFactoryKey(),
                        apiKeyField.getText().trim(),
                        accountIdField.getText().trim(), // Use account ID as apiSecret
                        accountIdField.getText().trim(),
                        telegramToken.getText().trim(),
                        openAiField.getText().trim(), // openaiApiKey - optional
                        null, // openaiModel - optional
                        null, // openaiOrgId - optional
                        selectedTradingModeChoiceBox.getValue() // Trading mode
                );
            } else {
                // For other exchanges, require both API Key and API Secret
                if (apiKeyField.getText().isBlank() || apiSecretField.getText().isBlank()) {
                    validation.setText("API Key and API Secret are required.");
                    return;
                }
                configuration = new MarketConfiguration(
                        usernameField.getText().trim(),
                        marketTypeBox.getValue(),
                        venueBox.getValue(),
                        selectedExchange.getFactoryKey(),
                        apiKeyField.getText().trim(),
                        apiSecretField.getText().trim(),
                        accountIdField.getText().trim(),
                        telegramToken.getText().trim(),
                        openAiField.getText().trim(), // openaiApiKey - optional
                        null, // openaiModel - optional
                        null, // openaiOrgId - optional
                        selectedTradingModeChoiceBox.getValue() // Trading mode
                );
            }

            // Authenticate with broker before proceeding
            validation.setStyle("-fx-text-fill: #fbbf24;");
            validation.setText("Authenticating with %s...".formatted(selectedExchange.getDisplayName()));

            String apiKey;
            apiKey = apiKeyField.getText().trim();
            String apiSecret = selectedExchange == SupportedExchange.OANDA ? accountIdField.getText().trim()
                    : apiSecretField.getText().trim();

            AuthResult authResult = authenticateExchange(
                    selectedExchange.getFactoryKey(),
                    apiKey,
                    apiSecret,
                    accountIdField.getText(),
                    selectedTradingModeChoiseBox.getValue());

            if (!authResult.success()) {
                validation.setStyle("-fx-text-fill: #ef4444;");
                validation.setText(authResult.message());
                return;
            }

            // Authentication successful - proceed with configuration
            validation.setStyle("-fx-text-fill: #22c55e;");
            validation.setText("Authentication successful!");

            // Save exchange credentials if checkbox is selected
            if (rememberCredentialsCheckBox.isSelected()) {
                saveRememberedExchangeCredentials(selectedExchangeName,
                        apiKeyField.getText().trim(),
                        apiSecretField.getText().trim(),
                        accountIdField.getText().trim(),
                        telegramToken.getText().trim());
            }

            saveConfiguration(configuration);
            showLoadingOverlay();
        });

        backButton.setOnAction(event -> showConfigurationStep());

        Label title = new Label("Exchange Credentials");
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: 700; -fx-text-fill: #3b82f6;");

        Label subtitle = new Label("Enter your exchange API credentials to connect your account.");
        subtitle.setStyle("-fx-font-size: 15px; -fx-text-fill: #cbd5e1;");

        Label info = new Label();
        info.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
        info.setWrapText(true);
        info.setMaxWidth(500);

        Button helpButton = new Button("? Format Help");
        helpButton.setStyle(
                "-fx-padding: 4 12; -fx-background-color: #1e40af; -fx-text-fill: #bfdbfe; -fx-border-color: #3b82f6; -fx-border-width: 1;");
        helpButton.setCursor(Cursor.HAND);

        String infoText = switch (selectedExchange) {
            case COINBASE -> """
                    Coinbase Advanced Trade API:
                    • API Key: Organization ID format (organizations/xxxxx/apiKeys/xxxxx)
                    • API Secret: EC Private Key in PEM format (-----BEGIN EC PRIVATE KEY-----)
                    Generate at: https://coinbase.com/settings/api""";
            case OANDA -> """
                    OANDA v3 API:
                    • Token: Bearer token from Account Settings
                    • Account ID: Optional (auto-detected if left blank)
                    Generate at: https://www.oanda.com/account/tpa/personal-token""";
            case BINANCE, BINANCE_US -> """
                    Binance API:
                    • API Key: Public key from API Management
                    • API Secret: Secret key from API Management
                    Generate at: https://www.binance.com/en/user/settings/api-management""";
            case BITFINEX -> """
                    Bitfinex API:
                    • API Key: Public key from Settings → API
                    • API Secret: Secret key from Settings → API
                    Generate at: https://www.bitfinex.com/api""";
            case ALPACA -> """
                    Alpaca Trading API:
                    • API Key: From Dashboard → API Keys
                    • API Secret: From Dashboard → API Keys
                    Generate at: https://app.alpaca.markets/""";
            case INTERACTIVE_BROKERS -> """
                    Interactive Brokers:
                    • API Key: Your IB account username or API key
                    • API Secret: Your IB account password or secret
                    Setup: Enable API at Account Management""";
            default -> "Enter your API Key and API Secret for " + selectedExchange.getDisplayName();
        };

        info.setText(infoText);

        helpButton.setOnAction(event -> {
            Alert helpDialog = new Alert(Alert.AlertType.INFORMATION);
            helpDialog.setTitle("Credential Format Help - " + selectedExchange);
            helpDialog.setHeaderText("How to find your " + selectedExchange + " credentials");
            helpDialog.setContentText(infoText);
            helpDialog.showAndWait();
        });

        HBox buttonBox = new HBox(10, backButton, continueButton, helpButton);
        buttonBox.setAlignment(Pos.CENTER);

        VBox content = new VBox(18, title, subtitle, info, credGrid, rememberBox, buttonBox, validation);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(500);

        StackPane centerPane = new StackPane(content);
        centerPane.setAlignment(Pos.CENTER);

        BorderPane pane = new BorderPane(centerPane);
        pane.setStyle("-fx-background-color: #0f172a;");
        fadeTo(pane);
    }

    private @NotNull PasswordField getPasswordField(SupportedExchange selectedExchange) {
        PasswordField apiSecretField = new PasswordField();
        apiSecretField.setPromptText("API Secret");
        apiSecretField.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        // For OANDA, API Secret field should be hidden
        apiSecretField.setVisible(selectedExchange != SupportedExchange.OANDA);
        apiSecretField.setManaged(selectedExchange != SupportedExchange.OANDA);
        return apiSecretField;
    }

    private void showLoadingOverlay() {
        statusLabel.setText("Saving settings...");
        statusLabel.setStyle("-fx-text-fill: #cbd5e1;");
        progressBar.setProgress(0);
        progressBar.setPrefWidth(420);

        Label title = new Label("Loading...");
        title.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 16px; -fx-font-weight: bold;");

        VBox overlay = new VBox(18, title, statusLabel, progressBar);
        overlay.setAlignment(Pos.CENTER);
        overlay.setPadding(new Insets(36));
        overlay.setMaxSize(520, 240);
        overlay.setStyle(
                "-fx-background-color: #1e293b; -fx-border-color: #475569; -fx-border-radius: 8; -fx-background-radius: 8; -fx-border-width: 1;");

        StackPane loadingPane = new StackPane(overlay);
        loadingPane.setStyle("-fx-background-color: rgba(15, 23, 42, 0.85);");
        getChildren().add(loadingPane);

        Timeline timeline = new Timeline(
                frame(0.20, "Saving configuration..."),
                frame(0.42, "Connecting to market venue..."),
                frame(0.65, "Loading exchange instruments..."),
                frame(0.84, "Preparing trading terminal..."),
                frame(1.0, "Market data is ready."));
        timeline.setOnFinished(event -> {
            PauseTransition pause = new PauseTransition(Duration.millis(450));
            pause.setOnFinished(pauseEvent -> onReady.accept(configuration));
            pause.play();
        });
        timeline.play();
    }

    @Contract("_, _ -> new")
    private @NotNull KeyFrame frame(double progress, String message) {
        return new KeyFrame(Duration.millis(2600 * progress),
                event -> statusLabel.setText(message),
                new KeyValue(progressBar.progressProperty(), progress));
    }

    private void showForgotPasswordDialog(Label validation) {
        TextInputDialog lookupDialog = new TextInputDialog(usernameField.getText());
        lookupDialog.setTitle("Forgot Password");
        lookupDialog.setHeaderText("Find your InvestPro account");
        lookupDialog.setContentText("Username or email:");
        lookupDialog.showAndWait().ifPresent(lookup -> {
            ResetTokenResult tokenResult = authService.beginPasswordReset(lookup);
            if (!tokenResult.success()) {
                validation.setStyle("-fx-text-fill: #ef4444;");
                validation.setText(tokenResult.message());
                return;
            }

            Alert tokenAlert = new Alert(Alert.AlertType.INFORMATION);
            tokenAlert.setTitle("Reset Token");
            tokenAlert.setHeaderText("Reset token created for %s".formatted(tokenResult.email()));
            tokenAlert.setContentText("Use this token within 30 minutes:\n\n%s".formatted(tokenResult.token()));
            tokenAlert.showAndWait();
            showResetPasswordDialog(lookup, tokenResult.token(), validation);
        });
    }

    private void showResetPasswordDialog(String lookup, String token, Label validation) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Enter your reset token and new password.");
        ButtonType resetButtonType = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(resetButtonType, ButtonType.CANCEL);

        TextField accountField = new TextField(lookup);
        accountField.setPromptText("Username or email");
        TextField tokenField = new TextField(token);
        tokenField.setPromptText("Reset token");
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New password");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm password");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Account"), accountField);
        grid.addRow(1, new Label("Token"), tokenField);
        grid.addRow(2, new Label("New Password"), newPasswordField);
        grid.addRow(3, new Label("Confirm"), confirmPasswordField);
        dialog.getDialogPane().setContent(grid);

        Platform.runLater(newPasswordField::requestFocus);
        dialog.showAndWait()
                .filter(button -> button == resetButtonType)
                .ifPresent(savedValue -> {
                    if (!newPasswordField.getText().equals(confirmPasswordField.getText())) {
                        validation.setStyle("-fx-text-fill: #ef4444;");
                        validation.setText("New password and confirmation do not match.");
                        return;
                    }
                    char[] password = newPasswordField.getText().toCharArray();
                    AuthResult result = authService.resetPassword(
                            accountField.getText(),
                            tokenField.getText(),
                            password);
                    Arrays.fill(password, '\0');
                    validation.setStyle("-fx-text-fill: %s;".formatted(result.success() ? "#22c55e" : "#ef4444"));
                    validation.setText(result.message());
                    if (result.success()) {
                        usernameField.setText(accountField.getText().trim());
                        passwordField.clear();
                    }
                });
    }

    private void saveConfiguration(@NotNull MarketConfiguration configuration) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingView.class);
        preferences.put("username", configuration.username());
        preferences.put("marketType", configuration.marketType());
        preferences.put("venue", configuration.venue());
        preferences.put("exchange", configuration.exchange());
        preferences.put("apiKey", configuration.apiKey());
        preferences.put("apiSecret", configuration.apiSecret());
        preferences.put("accountId", configuration.accountId());
        preferences.put("telegramToken", configuration.telegramToken());
    }

    private void fadeTo(BorderPane next) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(180), this);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> {
            getChildren().add(next);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(220), this);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
        });
        fadeOut.play();
    }

    private void loadRememberedCredentials() {
        rememberMeCheckBox.setSelected(authService.isRememberMeEnabled());
        authService.rememberedUsername().ifPresent(usernameField::setText);
    }

    private void forgetCredentials() {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingView.class);
        authService.forgetRememberedUser();
        // Clear all exchange credentials
        for (String exchange : new String[] { "COINBASE", "BINANCE", "BINANCE US", "OANDA", "BITFINEX", "ALPACA",
                "INTERACTIVE BROKERS", "BITMEX", "STELLAR-NETWORK", "BITSTAMP", "BITTREX" }) {
            preferences.remove("exchange_api_key_%s".formatted(exchange));
            preferences.remove("exchange_api_secret_%s".formatted(exchange));
            preferences.remove("exchange_account_id_%s".formatted(exchange));
            preferences.remove("exchange_venue_%s".formatted(exchange));
            preferences.remove("telegram_token_%s".formatted(exchange));
        }
        usernameField.clear();
        passwordField.clear();
        rememberMeCheckBox.setSelected(false);
    }

    private void saveRememberedExchangeCredentials(String exchange, String apiKey, String apiSecret, String accountId,
            String token) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingView.class);
        preferences.put("exchange_api_key_%s".formatted(exchange), apiKey);
        preferences.put("exchange_api_secret_%s".formatted(exchange), apiSecret);
        preferences.put("exchange_account_id_%s".formatted(exchange), accountId);
        preferences.put("telegram_token_%s".formatted(exchange), token);
    }

    private void loadRememberedExchangeCredentials(String exchange, TextField apiKeyField, PasswordField apiSecretField,
            TextField accountIdField) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingView.class);
        String savedApiKey = preferences.get("exchange_api_key_%s".formatted(exchange), "");
        String savedApiSecret = preferences.get("exchange_api_secret_%s".formatted(exchange), "");
        String savedAccountId = preferences.get("exchange_account_id_%s".formatted(exchange), "");
        String savedTelegramToken = preferences.get("telegram_token_%s".formatted(exchange), "");

        if (!savedApiKey.isEmpty()) {
            apiKeyField.setText(savedApiKey);
        }
        if (!savedApiSecret.isEmpty()) {
            apiSecretField.setText(savedApiSecret);
        }
        if (!savedAccountId.isEmpty()) {
            accountIdField.setText(savedAccountId);
        }
        if (!savedTelegramToken.isEmpty()) {
            telegramToken.setText(savedTelegramToken);
        }
    }

    private @NotNull Exchange createExchange(
            String selectedExchange,
            String apiKey,
            String apiSecret,
            String accountId,
            String tradingMode) {
        String exchangeId = normalizeExchangeId(selectedExchange);

        CredentialProvider credentialProvider = new UiCredentialProvider(
                exchangeId,
                apiKey,
                apiSecret,
                accountId,
                tradingMode);

        ExchangeFactory exchangeFactory = new ExchangeFactory(credentialProvider);

        Exchange exchange = exchangeFactory.create(exchangeId);
        exchange.connect();

        return exchange;
    }

    private @NotNull AuthResult authenticateExchange(
            String selectedExchange,
            String apiKey,
            String apiSecret,
            String accountId,
            String tradingMode) {
        try {
            Exchange exchange = createExchange(selectedExchange, apiKey, apiSecret, accountId, tradingMode);
            AuthResult authResult = exchange.AuthCheckResult(selectedExchange);
            if (authResult != null) {
                return authResult;
            }
            if (Boolean.TRUE.equals(exchange.isConnected())) {
                return AuthResult.success("%s connected successfully.".formatted(selectedExchange));
            }
            return AuthResult.failure("%s did not confirm a connection.".formatted(selectedExchange));
        } catch (Exception exception) {
            log.warn("Broker authentication failed for {}", selectedExchange, exception);
            return AuthResult.failure(
                    "Authentication failed for %s: %s".formatted(selectedExchange, rootMessage(exception)));
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank()
                ? "Unknown error"
                : message;
    }

    private @NotNull String normalizeExchangeId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Exchange name is required");
        }

        String normalized = value
                .trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replace(" ", "")
                .replace("-", "_");

        return switch (normalized) {
            case "binance", "binanceus", "binance_us", "binance_us_spot" -> "binanceus";

            case "coinbase",
                    "coinbaseadvanced",
                    "coinbase_advanced",
                    "coinbaseadvancedtrade",
                    "coinbase_advanced_trade" ->
                "coinbase";

            case "oanda", "oanda_fx", "oanda_forex" -> "oanda";

            case "alpaca", "alpaca_stocks", "alpaca_equities" -> "alpaca";

            case "bitfinex" -> "bitfinex";

            case "kraken" -> "kraken";

            case "stellar", "stellar_network", "stellarnetwork" -> "stellar-network";

            default -> throw new IllegalArgumentException("Unsupported exchange: " + value);
        };
    }
}
