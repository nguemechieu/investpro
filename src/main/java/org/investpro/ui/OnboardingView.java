package org.investpro.ui;

import lombok.extern.slf4j.Slf4j;

import org.investpro.exchange.*;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.Cursor;
import javafx.util.Duration;

import org.investpro.service.UserAuthService;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

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
    private final CheckBox rememberMeCheckBox = new CheckBox("Remember me");
    private final ComboBox<String> marketTypeBox = new ComboBox<>();
    private final ComboBox<String> venueBox = new ComboBox<>();
    private final ComboBox<String> exchangeBox = new ComboBox<>();
    private final Label statusLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final UserAuthService authService = new UserAuthService();
    private MarketConfiguration configuration;
    private final TextField telegramToken = new TextField();

    public OnboardingView(Consumer<MarketConfiguration> onReady) {
        this.onReady = Objects.requireNonNull(onReady);
        setPrefSize(1540, 780);
        setStyle("-fx-background-color: #0f172a;");
        loadRememberedCredentials();
        getChildren().setAll(createLoginStep());
    }

    private @NotNull BorderPane createLoginStep() {
        Label appName = new Label("InvestPro");
        appName.setStyle("-fx-font-size: 44px; -fx-font-weight: 700; -fx-text-fill: #3b82f6;");

        Label prompt = new Label("Log in to your account or create one to start trading.");
        prompt.setStyle("-fx-font-size: 16px; -fx-text-fill: #cbd5e1;");

        usernameField.setPromptText("Username");
        usernameField.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        passwordField.setPromptText("Password");
        passwordField.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        emailField.setPromptText("Email for new accounts");
        emailField.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");
        emailField.setVisible(false);
        emailField.setManaged(false);

        rememberMeCheckBox.setStyle("-fx-text-fill: #f1f5f9;");
        rememberMeCheckBox.setVisible(true);
        rememberMeCheckBox.setManaged(true);

        Button forgetButton = new Button("Forget");
        forgetButton.setStyle("-fx-padding: 4 12; -fx-background-color: #1e40af; -fx-text-fill: white;");
        forgetButton.setOnAction(event -> forgetCredentials());

        Button forgotPasswordButton = new Button("Forgot Password");
        forgotPasswordButton.setStyle(
                "-fx-padding: 4 12; -fx-background-color: transparent; -fx-border-color: #475569; -fx-text-fill: #bfdbfe;");

        HBox rememberBox = new HBox(10, rememberMeCheckBox, forgetButton, forgotPasswordButton);
        rememberBox.setAlignment(Pos.CENTER);

        Button loginButton = new Button("Log In");
        loginButton.setStyle(
                "-fx-padding: 10 20; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");

        Button createButton = new Button("Create Account");
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
                validation.setText("Enter an email, username, and password, then create the account.");
                return;
            }

            char[] password = passwordField.getText().toCharArray();
            UserAuthService.AuthResult result = authService.register(
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
            UserAuthService.AuthResult result = authService.signIn(usernameField.getText(), password);
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
        pane.setStyle("-fx-background-color: #0f172a;");
        return pane;
    }

    private void showConfigurationStep() {
        marketTypeBox.getItems().setAll("Crypto", "Stocks", "Futures", "Forex", "Options", "ETFs", "Bonds");
        venueBox.getItems().setAll("US", "Global", "Spot", "Derivatives", "Paper Trading");
        exchangeBox.getItems().setAll(
                "COINBASE", "BINANCE US", "BINANCE", "OANDA", "BITFINEX",
                "ALPACA", "INTERACTIVE BROKERS", "SCHWAB", "BITMEX", "BITSTAMP", "BITTREX");
        marketTypeBox.getSelectionModel().select("Crypto");
        venueBox.getSelectionModel().select("US");
        exchangeBox.getSelectionModel().select("COINBASE");

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
        fadeTo(pane);
    }

    private void showExchangeCredentialsStep() {
        String selectedExchange = exchangeBox.getValue();

        TextField apiKeyField = new TextField();
        apiKeyField.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        if (selectedExchange.equals("OANDA")) {
            apiKeyField.setPromptText("OANDA Token (Bearer Token)");
        } else {
            apiKeyField.setPromptText("API Key");
        }

        PasswordField apiSecretField = new PasswordField();
        apiSecretField.setPromptText("API Secret");
        apiSecretField.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        // For OANDA, API Secret field should be hidden
        apiSecretField.setVisible(!selectedExchange.equals("OANDA"));
        apiSecretField.setManaged(!selectedExchange.equals("OANDA"));

        TextField accountIdField = new TextField();
        accountIdField.setPromptText("Account ID (auto-detected if blank)");
        accountIdField.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");
        accountIdField.setVisible(selectedExchange.equals("OANDA"));
        accountIdField.setManaged(selectedExchange.equals("OANDA"));

        // Telegram Token field (optional)
        telegramToken.setPromptText("Telegram Bot Token (optional)");
        telegramToken.setStyle(
                "-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        // Load remembered credentials for this exchange if they exist
        loadRememberedExchangeCredentials(selectedExchange, apiKeyField, apiSecretField, accountIdField);

        // Trading mode selection
        ToggleGroup tradingModeGroup = new ToggleGroup();
        RadioButton liveRadioButton = new RadioButton("Live Trading");
        liveRadioButton.setStyle("-fx-text-fill: #f1f5f9; -fx-font-size: 12;");
        liveRadioButton.setToggleGroup(tradingModeGroup);
        liveRadioButton.setSelected(true); // Default to Live Trading

        RadioButton paperRadioButton = new RadioButton("Paper Trading");
        paperRadioButton.setStyle("-fx-text-fill: #f1f5f9; -fx-font-size: 12;");
        paperRadioButton.setToggleGroup(tradingModeGroup);

        HBox tradingModeBox = new HBox(20, liveRadioButton, paperRadioButton);
        tradingModeBox.setAlignment(Pos.CENTER_LEFT);
        tradingModeBox.setStyle("-fx-padding: 8 0 0 0;");

        GridPane credGrid = new GridPane();
        credGrid.setHgap(14);
        credGrid.setVgap(14);
        credGrid.setAlignment(Pos.CENTER);

        Label apiKeyLabel = new Label(selectedExchange.equals("OANDA") ? "Token" : "API Key");
        credGrid.addRow(0, apiKeyLabel, apiKeyField);

        if (!selectedExchange.equals("OANDA")) {
            credGrid.addRow(1, new Label("API Secret"), apiSecretField);
        }

        if (selectedExchange.equals("OANDA")) {
            credGrid.addRow(1, new Label("Account ID"), accountIdField);
        }

        credGrid.addRow(2, new Label("Telegram Token"), telegramToken);
        credGrid.addRow(3, new Label("Trading Mode"), tradingModeBox);

        CheckBox rememberCredentialsCheckBox = new CheckBox("Remember these credentials");
        rememberCredentialsCheckBox.setStyle("-fx-text-fill: #f1f5f9;");

        HBox rememberBox = new HBox(10, rememberCredentialsCheckBox);
        rememberBox.setAlignment(Pos.CENTER);

        Button continueButton = new Button("Continue");
        continueButton.setStyle(
                "-fx-padding: 10 20; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold;");

        Button backButton = new Button("Back");
        backButton.setStyle(
                "-fx-padding: 10 20; -fx-background-color: #1e40af; -fx-text-fill: white; -fx-font-weight: bold;");

        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ef4444;");
        validation.setAlignment(Pos.CENTER);

        continueButton.setOnAction(event -> {
            // Determine trading mode from radio button selection
            String selectedTradingMode = paperRadioButton.isSelected() ? "PAPER" : "LIVE";

            // For OANDA, only token is required (Account ID is optional - can be
            // auto-detected)
            if (selectedExchange.equals("OANDA")) {
                if (apiKeyField.getText().isBlank()) {
                    validation.setText("OANDA Token is required.");
                    return;
                }
                // For OANDA, pass token as apiKey and accountId as apiSecret
                configuration = new MarketConfiguration(
                        usernameField.getText().trim(),
                        marketTypeBox.getValue(),
                        venueBox.getValue(),
                        exchangeBox.getValue(),
                        apiKeyField.getText().trim(),
                        accountIdField.getText().trim(), // Use account ID as apiSecret
                        accountIdField.getText().trim(),
                        telegramToken.getText().trim(),
                        null, // openaiApiKey - optional
                        null, // openaiModel - optional
                        null, // openaiOrgId - optional
                        selectedTradingMode // Trading mode
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
                        exchangeBox.getValue(),
                        apiKeyField.getText().trim(),
                        apiSecretField.getText().trim(),
                        accountIdField.getText().trim(),
                        telegramToken.getText().trim(),
                        null, // openaiApiKey - optional
                        null, // openaiModel - optional
                        null, // openaiOrgId - optional
                        selectedTradingMode // Trading mode
                );
            }

            // Authenticate with broker before proceeding
            validation.setStyle("-fx-text-fill: #fbbf24;");
            validation.setText("Authenticating with %s...".formatted(selectedExchange));

            String apiKey;
            apiKey = apiKeyField.getText().trim();
            String apiSecret = selectedExchange.equals("OANDA") ? accountIdField.getText().trim()
                    : apiSecretField.getText().trim();

            AuthenticationResult authResult = authenticateWithBroker(selectedExchange, apiKey, apiSecret);

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
                saveRememberedExchangeCredentials(selectedExchange,
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
            case "COINBASE" -> """
                    Coinbase Advanced Trade API:
                    • API Key: Organization ID format (organizations/xxxxx/apiKeys/xxxxx)
                    • API Secret: EC Private Key in PEM format (-----BEGIN EC PRIVATE KEY-----)
                    Generate at: https://coinbase.com/settings/api""";
            case "OANDA" -> """
                    OANDA v3 API:
                    • Token: Bearer token from Account Settings
                    • Account ID: Optional (auto-detected if left blank)
                    Generate at: https://www.oanda.com/account/tpa/personal-token""";
            case "BINANCE", "BINANCE US" -> """
                    Binance API:
                    • API Key: Public key from API Management
                    • API Secret: Secret key from API Management
                    Generate at: https://www.binance.com/en/user/settings/api-management""";
            case "BITFINEX" -> """
                    Bitfinex API:
                    • API Key: Public key from Settings → API
                    • API Secret: Secret key from Settings → API
                    Generate at: https://www.bitfinex.com/api""";
            case "ALPACA" -> """
                    Alpaca Trading API:
                    • API Key: From Dashboard → API Keys
                    • API Secret: From Dashboard → API Keys
                    Generate at: https://app.alpaca.markets/""";
            case "INTERACTIVE BROKERS" -> """
                    Interactive Brokers:
                    • API Key: Your IB account username or API key
                    • API Secret: Your IB account password or secret
                    Setup: Enable API at Account Management""";
            default -> "Enter your API Key and API Secret for " + selectedExchange;
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

    private void showLoadingOverlay() {
        statusLabel.setText("Saving settings...");
        statusLabel.setStyle("-fx-text-fill: #cbd5e1;");
        progressBar.setProgress(0);
        progressBar.setPrefWidth(420);

        Label title = new Label("Loading Market");
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
            UserAuthService.ResetTokenResult tokenResult = authService.beginPasswordReset(lookup);
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
                    UserAuthService.AuthResult result = authService.resetPassword(
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
            getChildren().setAll(next);
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
                "INTERACTIVE BROKERS", "BITMEX", "BITSTAMP", "BITTREX" }) {
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

    /**
     * Authenticates with the broker using the provided credentials.
     * Creates an exchange instance and attempts to connect.
     *
     * @param exchangeName the name of the exchange
     * @param apiKey       the API key or token
     * @param apiSecret    the API secret (optional for some exchanges)
     * @return a result object containing success status and message
     */
    private AuthenticationResult authenticateWithBroker(String exchangeName, String apiKey, String apiSecret) {
        try {

            // Create exchange instance with provided credentials
            Exchange exchange = createExchange(exchangeName, apiKey, apiSecret);

            // Attempt to authenticate
            return attemptExchangeAuthentication(exchangeName, exchange);

        } catch (RuntimeException e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Authentication failed";

            // Log the full error for debugging
            log.error("Authentication failed for {}: {}", exchangeName, errorMessage, e);

            // Extract and show exchange-specific error details
            String userMessage = parseExchangeError(exchangeName, errorMessage);
            return new AuthenticationResult(false, userMessage);
        } catch (Exception e) {
            log.error("Unexpected error during authentication for {}: {}", exchangeName, e.getMessage(), e);
            return new AuthenticationResult(false,
                    "Unexpected error during authentication: %s".formatted(e.getMessage()));
        }
    }

    /**
     * Parses exchange-specific error messages and returns a user-friendly message.
     * This method extracts the actual error from the exchange API response.
     */
    private @NotNull String parseExchangeError(String exchangeName, @NotNull String errorMessage) {
        // Check for HTTP status codes and common errors
        if (errorMessage.contains("401") || errorMessage.contains("Unauthorized")) {
            log.warn("Authentication failed - 401 Unauthorized for {}", exchangeName);
            return "Invalid credentials. Status: 401 Unauthorized. Check your API key and secret.";
        }

        if (errorMessage.contains("403") || errorMessage.contains("Forbidden")) {
            log.warn("Authentication failed - 403 Forbidden for {}", exchangeName);
            return "Access forbidden. Status: 403. Your API key may not have the required permissions.";
        }

        if (errorMessage.contains("429") || errorMessage.contains("Too Many Requests")) {
            return "Too many authentication attempts. Please wait a moment and try again.";
        }

        if (errorMessage.contains("timeout") || errorMessage.contains("Timeout")) {
            return "Connection timeout. Check your network and try again.";
        }

        if (errorMessage.contains("refused") || errorMessage.contains("Connection refused")) {
            return "Connection refused. %s might be temporarily unavailable.".formatted(exchangeName);
        }

        // Try to extract JSON error from the response (common in REST APIs)
        if (errorMessage.contains("HTTP") && errorMessage.contains(":")) {
            // Format: "Coinbase HTTP 401 for https://...: {json error response}"
            int httpIndex = errorMessage.indexOf("HTTP");
            if (httpIndex >= 0) {
                String httpPart = errorMessage.substring(httpIndex);
                log.debug("Extracted HTTP error details: {}", httpPart);

                // Check if there's a JSON error body
                if (httpPart.contains("{")) {
                    try {
                        int jsonStart = httpPart.indexOf("{");
                        int jsonEnd = httpPart.lastIndexOf("}");
                        if (jsonEnd > jsonStart) {
                            String jsonError = httpPart.substring(jsonStart, jsonEnd + 1);
                            log.debug("Extracted JSON error from response: {}", jsonError);

                            // Try to extract "message" field if it's JSON
                            if (jsonError.contains("\"message\"")) {
                                int msgStart = jsonError.indexOf("\"message\"");
                                int colonIndex = jsonError.indexOf(":", msgStart);
                                int quoteStart = jsonError.indexOf("\"", colonIndex);
                                int quoteEnd = jsonError.indexOf("\"", quoteStart + 1);
                                if (quoteStart >= 0 && quoteEnd > quoteStart) {
                                    String message = jsonError.substring(quoteStart + 1, quoteEnd);
                                    return "Exchange error: " + message;
                                }
                            }

                            // If not standard JSON, show the whole error body (truncated)
                            if (jsonError.length() > 200) {
                                return "Exchange error: " + jsonError.substring(0, 200) + "...";
                            } else {
                                return "Exchange error: " + jsonError;
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Could not parse JSON error response", e);
                    }
                }

                return "Exchange error: " + httpPart.substring(0, Math.min(150, httpPart.length()));
            }
        }

        // For other runtime exceptions, show the full message but cap length
        if (errorMessage.length() > 300) {
            return "Authentication failed: " + errorMessage.substring(0, 300) + "...";
        }

        return "Authentication failed: " + errorMessage;
    }

    /**
     * Attempt to connect and verify credentials
     */
    private AuthenticationResult attemptExchangeAuthentication(String exchangeName, Exchange exchange) {
        try {
            if (exchange instanceof Coinbase coinbase) {
                coinbase.getUserAccountDetails();
                return new AuthenticationResult(true, "Successfully authenticated with %s".formatted(exchangeName));
            }

            exchange.connect();

            // Check if connection was successful
            Boolean isConnected = exchange.isConnected();
            if (isConnected == null || !isConnected) {
                exchange.disconnect();
                return new AuthenticationResult(false,
                        "Connection to %s failed. Please verify your credentials.".formatted(exchangeName));
            }

            exchange.disconnect();
            return new AuthenticationResult(true, "Successfully authenticated with %s".formatted(exchangeName));
        } catch (RuntimeException e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Authentication failed";
            log.error("Exchange connection failed for {}: {}", exchangeName, errorMessage, e);
            String userMessage = parseExchangeError(exchangeName, errorMessage);
            return new AuthenticationResult(false, userMessage);
        }
    }

    /**
     * Creates an exchange instance based on the exchange name and credentials.
     */
    private @Nullable Exchange createExchange(String exchangeName, String apiKey, String apiSecret) {
        String name = safe(exchangeName).toUpperCase();
        return switch (name) {
            case "BINANCE US" -> new BinanceUs(apiKey, apiSecret);
            case "BINANCE" -> new Binance(apiKey, apiSecret);
            case "OANDA" -> new Oanda(apiKey, apiSecret);
            case "BITFINEX" -> new Bitfinex(apiKey, apiSecret);
            case "ALPACA" -> new Alpaca(apiKey, apiSecret);
            case "INTERACTIVE BROKERS", "IBKR" -> new InteractiveBrokers(apiKey, apiSecret);
            case "COINBASE" -> new Coinbase(apiKey, apiSecret);
            default -> null;
        };
    }

    /**
     * Safely trims a string, returning empty string if null.
     */
    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Result of a broker authentication attempt.
     */
    private record AuthenticationResult(boolean success, String message) {
    }

    // /**
    // * Get available venues for a broker.
    // */
    // public java.util.List<String> getAvailableVenues(String brokerName) {
    // return switch (brokerName.toUpperCase()) {
    // case "COINBASE" -> java.util.Arrays.asList("Spot", "US Futures",
    // "International Perpetuals");
    // case "OANDA" -> List.of("FX/CFD");
    // case "BINANCE" -> java.util.Arrays.asList("Spot", "Futures");
    // case "BINANCE US" -> List.of("Spot");
    // case "BITFINEX" -> java.util.Arrays.asList("Spot", "Derivatives");
    // case "ALPACA" -> java.util.Arrays.asList("Stocks", "Crypto");
    // case "INTERACTIVE BROKERS", "IBKR" -> Arrays.asList("Stocks", "Forex");
    // default -> List.of("Default");
    // };
    // }
    //
    // /**
    // * Parse venue name string to BrokerVenue enum.
    // */
    // private BrokerVenue parseVenue(String brokerName, String venueName) {
    // String broker = brokerName.toUpperCase();
    // String venue = venueName.toUpperCase();
    //
    // return switch (broker) {
    // case "COINBASE" -> switch (venue) {
    // case "SPOT" -> BrokerVenue.COINBASE_SPOT;
    // case "US FUTURES", "FUTURES" -> BrokerVenue.COINBASE_US_FUTURES;
    // case "INTERNATIONAL PERPETUALS", "PERPETUALS" ->
    // BrokerVenue.COINBASE_INTERNATIONAL_PERPETUALS;
    // default -> BrokerVenue.UNKNOWN;
    // };
    // case "OANDA" -> BrokerVenue.OANDA_FX_CFD;
    // case "BINANCE" -> switch (venue) {
    // case "SPOT" -> BrokerVenue.BINANCE_SPOT;
    // case "FUTURES" -> BrokerVenue.BINANCE_FUTURES;
    // default -> BrokerVenue.UNKNOWN;
    // };
    // default -> BrokerVenue.UNKNOWN;
    // };
    //
    // }
}
