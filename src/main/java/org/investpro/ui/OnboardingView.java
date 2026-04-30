package org.investpro.ui;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Onboarding view for InvestPro application.
 * Guides users through login, market configuration, and exchange credential setup.
 * Uses centralized styling configuration from OnboardingStyles.
 * 
 * @author NOEL NGUEMECHIEU
 */
public class OnboardingView extends StackPane {
    private final Consumer<MarketConfiguration> onReady;
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField emailField = new TextField();
    private final ComboBox<String> marketTypeBox = new ComboBox<>();
    private final ComboBox<String> venueBox = new ComboBox<>();
    private final ComboBox<String> exchangeBox = new ComboBox<>();
    private final Label statusLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar(0);
    private MarketConfiguration configuration;
    private final TextField telegramToken =new TextField();

    public OnboardingView(Consumer<MarketConfiguration> onReady) {
        this.onReady = Objects.requireNonNull(onReady);
        setPrefSize(1540, 780);
        setStyle("-fx-background-color: #0f172a;");
        loadRememberedCredentials();
        getChildren().setAll(createLoginStep());
    }

    private BorderPane createLoginStep() {
        Label appName = new Label("InvestPro");
        appName.setStyle("-fx-font-size: 44px; -fx-font-weight: 700; -fx-text-fill: #3b82f6;");

        Label prompt = new Label("Log in to your account or create one to start trading.");
        prompt.setStyle("-fx-font-size: 16px; -fx-text-fill: #cbd5e1;");

        usernameField.setPromptText("Username");
        usernameField.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");
        
        passwordField.setPromptText("Password");
        passwordField.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");
        
        emailField.setPromptText("Email for new accounts");
        emailField.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");
        emailField.setVisible(false);
        emailField.setManaged(false);

        CheckBox rememberMeCheckBox = new CheckBox("Remember me");
        rememberMeCheckBox.setStyle("-fx-text-fill: #f1f5f9;");
        
        Button forgetButton = new Button("Forget");
        forgetButton.setStyle("-fx-padding: 4 12; -fx-background-color: #1e40af; -fx-text-fill: white;");
        forgetButton.setOnAction(_ -> forgetCredentials());

        HBox rememberBox = new HBox(10, rememberMeCheckBox, forgetButton);
        rememberBox.setAlignment(Pos.CENTER);

        Button loginButton = new Button("Log In");
        loginButton.setStyle("-fx-padding: 10 20; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
        
        Button createButton = new Button("Create Account");
        createButton.setStyle("-fx-padding: 10 20; -fx-background-color: #10b981; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
        
        HBox buttonBox = new HBox(10, loginButton, createButton);
        buttonBox.setAlignment(Pos.CENTER);
        
        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ef4444;");
        validation.setAlignment(Pos.CENTER);

        createButton.setOnAction(_ -> {
            emailField.setVisible(true);
            emailField.setManaged(true);
            validation.setText("Enter an email, username, and password, then continue.");
        });

        loginButton.setOnAction(_ -> {
            if (usernameField.getText().isBlank() || passwordField.getText().isBlank()) {
                validation.setText("Username and password are required.");
                return;
            }
            if (rememberMeCheckBox.isSelected()) {
                saveRememberedCredentials(usernameField.getText());
            }
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
                "INTERACTIVE BROKERS", "SCHWAB", "BITMEX", "BITSTAMP", "BITTREX"
        );
        marketTypeBox.getSelectionModel().select("Crypto");
        venueBox.getSelectionModel().select("US");
        exchangeBox.getSelectionModel().select("COINBASE");

        // Auto-load credentials when exchange changes
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
        loadMarketButton.setStyle("-fx-padding: 10 20; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold;");
        
        HBox loadButtonBox = new HBox(loadMarketButton);
        loadButtonBox.setAlignment(Pos.CENTER);
        
        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ef4444;");
        validation.setAlignment(Pos.CENTER);
        loadMarketButton.setOnAction(_ -> {
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
        apiKeyField.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");
        
        if (selectedExchange.equals("OANDA")) {
            apiKeyField.setPromptText("OANDA Token (Bearer Token)");
        } else {
            apiKeyField.setPromptText("API Key");
        }
        
        PasswordField apiSecretField = new PasswordField();
        apiSecretField.setPromptText("API Secret");
        apiSecretField.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");
        
        // For OANDA, API Secret field should be hidden
        apiSecretField.setVisible(!selectedExchange.equals("OANDA"));
        apiSecretField.setManaged(!selectedExchange.equals("OANDA"));
        
        TextField accountIdField = new TextField();
        accountIdField.setPromptText("Account ID (auto-detected if blank)");
        accountIdField.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");
        accountIdField.setVisible(selectedExchange.equals("OANDA"));
        accountIdField.setManaged(selectedExchange.equals("OANDA"));

        // Telegram Token field (optional)
        telegramToken.setPromptText("Telegram Bot Token (optional)");
        telegramToken.setStyle("-fx-control-inner-background: #1e293b; -fx-text-fill: #f1f5f9; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #475569; -fx-border-width: 1; -fx-padding: 8;");

        // Load remembered credentials for this exchange if they exist
        loadRememberedExchangeCredentials(selectedExchange, apiKeyField, apiSecretField, accountIdField);

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

        CheckBox rememberCredentialsCheckBox = new CheckBox("Remember these credentials");
        rememberCredentialsCheckBox.setStyle("-fx-text-fill: #f1f5f9;");
        
        HBox rememberBox = new HBox(10, rememberCredentialsCheckBox);
        rememberBox.setAlignment(Pos.CENTER);

        Button continueButton = new Button("Continue");
        continueButton.setStyle("-fx-padding: 10 20; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold;");
        
        Button backButton = new Button("Back");
        backButton.setStyle("-fx-padding: 10 20; -fx-background-color: #1e40af; -fx-text-fill: white; -fx-font-weight: bold;");
        
        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ef4444;");
        validation.setAlignment(Pos.CENTER);

        continueButton.setOnAction(_ -> {
            // For OANDA, only token is required (Account ID is optional - can be auto-detected)
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
                        accountIdField.getText().trim(),  // Use account ID as apiSecret
                        accountIdField.getText().trim(),
                        telegramToken.getText().trim(),
                        null,  // openaiApiKey - optional
                        null,  // openaiModel - optional
                        null   // openaiOrgId - optional
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
                        null,  // openaiApiKey - optional
                        null,  // openaiModel - optional
                        null   // openaiOrgId - optional
                );
            }
            
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

        backButton.setOnAction(_ -> showConfigurationStep());

        Label title = new Label("Exchange Credentials");
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: 700; -fx-text-fill: #3b82f6;");

        Label subtitle = new Label("Enter your exchange API credentials to connect your account.");
        subtitle.setStyle("-fx-font-size: 15px; -fx-text-fill: #cbd5e1;");
        
        Label info = new Label();
        info.setStyle("-fx-font-size: 12px; -fx-text-fill: #94a3b8;");
        info.setWrapText(true);
        info.setMaxWidth(500);
        
        if (selectedExchange.equals("OANDA")) {
            info.setText("For OANDA: Enter your Bearer Token (API v3 authentication). Account ID is optional and will be auto-detected from your account.");
        } else {
            info.setText("Enter your API Key and Secret for " + selectedExchange + ".");
        }

        HBox buttonBox = new HBox(10, backButton, continueButton);
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
        overlay.setStyle("-fx-background-color: #1e293b; -fx-border-color: #475569; -fx-border-radius: 8; -fx-background-radius: 8; -fx-border-width: 1;");

        StackPane loadingPane = new StackPane(overlay);
        loadingPane.setStyle("-fx-background-color: rgba(15, 23, 42, 0.85);");
        getChildren().add(loadingPane);

        Timeline timeline = new Timeline(
                frame(0.20, "Saving configuration..."),
                frame(0.42, "Connecting to market venue..."),
                frame(0.65, "Loading exchange instruments..."),
                frame(0.84, "Preparing trading terminal..."),
                frame(1.0, "Market data is ready.")
        );
        timeline.setOnFinished(_ -> {
            PauseTransition pause = new PauseTransition(Duration.millis(450));
            pause.setOnFinished(_ -> onReady.accept(configuration));
            pause.play();
        });
        timeline.play();
    }

    private KeyFrame frame(double progress, String message) {
        return new KeyFrame(Duration.millis(2600 * progress),
                _ -> statusLabel.setText(message),
                new KeyValue(progressBar.progressProperty(), progress));
    }

    private void saveConfiguration(MarketConfiguration configuration) {
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
        fadeOut.setOnFinished(_ -> {
            getChildren().setAll(next);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(220), this);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
        });
        fadeOut.play();
    }

    private void saveRememberedCredentials(String username) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingView.class);
        preferences.put("remembered_username", username);
        preferences.putBoolean("remember_me_enabled", true);
    }

    private void loadRememberedCredentials() {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingView.class);
        if (preferences.getBoolean("remember_me_enabled", false)) {
            String savedUsername = preferences.get("remembered_username", "");
            if (!savedUsername.isEmpty()) {
                usernameField.setText(savedUsername);
            }
        }
    }

    private void forgetCredentials() {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingView.class);
        preferences.putBoolean("remember_me_enabled", false);
        preferences.remove("remembered_username");
        // Clear all exchange credentials
        for (String exchange : new String[]{"COINBASE", "BINANCE", "BINANCE US", "OANDA", "BITFINEX", "BITMEX", "BITSTAMP", "BITTREX"}) {
            preferences.remove("exchange_api_key_" + exchange);
            preferences.remove("exchange_api_secret_" + exchange);
            preferences.remove("exchange_account_id_" + exchange);
        }
        usernameField.clear();
        passwordField.clear();
    }

    private void saveRememberedExchangeCredentials(String exchange, String apiKey, String apiSecret, String accountId, String token) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingView.class);
        preferences.put("exchange_api_key_" + exchange, apiKey);
        preferences.put("exchange_api_secret_" + exchange, apiSecret);
        preferences.put("exchange_account_id_" + exchange, accountId);
        preferences.put("telegram_token_" + exchange, token);
    }

    private void loadRememberedExchangeCredentials(String exchange, TextField apiKeyField, PasswordField apiSecretField, TextField accountIdField) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingView.class);
        String savedApiKey = preferences.get("exchange_api_key_" + exchange, "");
        String savedApiSecret = preferences.get("exchange_api_secret_" + exchange, "");
        String savedAccountId = preferences.get("exchange_account_id_" + exchange, "");
        String savedTelegramToken = preferences.get("telegram_token_" + exchange, "");
        
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
}
