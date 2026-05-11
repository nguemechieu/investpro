package org.investpro.ui;

import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
 * Professional onboarding view for InvestPro application with modern UI design.
 * Features split-pane layout with background image and card-based form design.
 * Guides users through login, market configuration, and exchange credential
 * setup.
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
    private static final String CARD_STYLE = "-fx-background-color: rgba(30, 41, 59, 0.95); " +
            "-fx-border-color: #475569; -fx-border-width: 1; -fx-border-radius: 8; " +
            "-fx-background-radius: 8; -fx-padding: 32; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 0, 2);";
    private static final String INPUT_STYLE = "-fx-control-inner-background: #0f172a; -fx-text-fill: #f1f5f9; " +
            "-fx-prompt-text-fill: #64748b; -fx-border-color: #334155; -fx-border-width: 1; -fx-padding: 10 12; " +
            "-fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 11;";
    private static final String BUTTON_PRIMARY = "-fx-padding: 12 24; -fx-background-color: #3b82f6; " +
            "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11; -fx-border-radius: 4; " +
            "-fx-background-radius: 4; -fx-cursor: hand;";
    private static final String BUTTON_SECONDARY = "-fx-padding: 12 24; -fx-background-color: #1e40af; " +
            "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11; -fx-border-radius: 4; " +
            "-fx-background-radius: 4; -fx-cursor: hand;";
    private static final String BUTTON_SUCCESS = "-fx-padding: 12 24; -fx-background-color: #10b981; " +
            "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11; -fx-border-radius: 4; " +
            "-fx-background-radius: 4; -fx-cursor: hand;";

    public OnboardingView(Consumer<MarketConfiguration> onReady) {
        this.onReady = Objects.requireNonNull(onReady);
        setPrefSize(1540, 780);

        OnboardingStyles.labelStyle(11, "#cbd5e1;", "16");

        loadRememberedCredentials();
        getChildren().setAll(createLoginStep());
    }

    private @NotNull BorderPane createLoginStep() {
        rememberMeCheckBox.setText(t("onboarding.rememberMe"));

        // Create background with image
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: #0f172a;");

        // Add background image on the left side
        VBox leftPanel = createBackgroundPanel();
        pane.setLeft(leftPanel);

        // Create card-based form on the right
        Label appName = new Label(t("onboarding.loginTitle"));
        appName.setStyle("-fx-font-size: 36px; -fx-font-weight: 700; -fx-text-fill: #3b82f6;");

        Label prompt = new Label(t("onboarding.loginPrompt"));
        prompt.setStyle("-fx-font-size: 14px; -fx-text-fill: #cbd5e1; -fx-wrap-text: true;");

        // Styled input fields
        styleInputField(usernameField, t("onboarding.username"));
        styleInputField(passwordField, t("onboarding.password"));
        styleInputField(emailField, t("onboarding.email"));
        emailField.setVisible(false);
        emailField.setManaged(false);

        rememberMeCheckBox.setStyle("-fx-text-fill: #f1f5f9; -fx-font-size: 11; -fx-padding: 4 0 4 0; " +
                "-fx-border-color: #475569; -fx-border-radius: 3; -fx-padding: 4 8 4 4;");
        rememberMeCheckBox.setPrefHeight(32);
        rememberMeCheckBox.setMinWidth(140);
        rememberMeCheckBox.setWrapText(false);

        Button forgotPasswordButton = createButton("Forgot Password?", BUTTON_SECONDARY);
        HBox rememberBox = createControlsRow(rememberMeCheckBox, forgotPasswordButton);
        rememberBox.setSpacing(14);

        Button loginButton = createButton(t("onboarding.logIn"), BUTTON_PRIMARY);
        Button createButton = createButton(t("onboarding.createAccount"), BUTTON_SUCCESS);
        HBox buttonBox = new HBox(12, loginButton, createButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setSpacing(12);

        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
        validation.setAlignment(Pos.CENTER);
        validation.setWrapText(true);

        forgotPasswordButton.setOnAction(event -> showForgotPasswordDialog(validation));

        createButton.setOnAction(event -> handleCreateAccount(validation, emailField));
        loginButton.setOnAction(event -> handleLogin(validation));

        VBox form = new VBox(14, usernameField, passwordField, emailField, rememberBox, buttonBox, validation);
        form.setAlignment(Pos.TOP_CENTER);
        form.setStyle("-fx-padding: 0;");

        VBox content = new VBox(20, appName, prompt, form);
        content.setAlignment(Pos.TOP_CENTER);
        content.setMaxWidth(420);
        content.setStyle(CARD_STYLE);

        VBox rightPanel = new VBox(content);
        rightPanel.setAlignment(Pos.CENTER);
        rightPanel.setPadding(new Insets(48));
        rightPanel.setStyle("-fx-background-color: rgba(15, 23, 42, 0.5);");
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        HBox mainContent = new HBox(leftPanel, rightPanel);
        mainContent.setPrefWidth(1540);
        mainContent.setStyle("-fx-background-color: #0f172a;");

        pane.setCenter(mainContent);
        pane.setTop(createLanguageSelector(() -> getChildren().setAll(createLoginStep())));
        return pane;
    }

    private @NotNull VBox createBackgroundPanel() {
        VBox leftPanel = new VBox();
        leftPanel.setPrefWidth(540);
        leftPanel.setMinWidth(420);
        leftPanel.setStyle("-fx-background-color: #1e293b;");
        leftPanel.setAlignment(Pos.CENTER);
        leftPanel.setPadding(new Insets(40));

        try {
            Image backgroundImage = new Image(getClass().getResourceAsStream("/images/Invest.png"));
            ImageView imageView = new ImageView(backgroundImage);
            imageView.setFitWidth(420);
            imageView.setFitHeight(500);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            VBox imageContainer = new VBox(imageView);
            imageContainer.setAlignment(Pos.CENTER);
            imageContainer.setStyle("-fx-padding: 20;");
            leftPanel.getChildren().add(imageContainer);

            Label branding = new Label("InvestPro Trading");
            branding.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

            Label tagline = new Label("Advanced Multi-Venue Trading Terminal");
            tagline.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");

            VBox brandingBox = new VBox(8, branding, tagline);
            brandingBox.setAlignment(Pos.CENTER);
            brandingBox.setStyle("-fx-padding: 20 0 0 0;");
            leftPanel.getChildren().add(brandingBox);
        } catch (Exception e) {
            log.warn("Failed to load background image", e);
            Label fallbackLabel = new Label("InvestPro");
            fallbackLabel.setStyle("-fx-font-size: 48px; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");
            leftPanel.getChildren().add(fallbackLabel);
        }

        return leftPanel;
    }

    private void styleInputField(TextField field, String prompt) {
        field.setPromptText(prompt);
        field.setStyle(INPUT_STYLE);
        field.setPrefHeight(40);
    }

    private @NotNull Button createButton(String text, String style) {
        Button button = new Button(text);
        button.setStyle(style);
        button.setPrefHeight(40);
        button.setMinWidth(100);
        return button;
    }

    private @NotNull HBox createControlsRow(Control... controls) {
        HBox box = new HBox(12, controls);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void handleCreateAccount(Label validation, TextField emailField) {
        if (!emailField.isVisible()) {
            emailField.setVisible(true);
            emailField.setManaged(true);
            validation.setStyle("-fx-text-fill: #fbbf24; -fx-font-size: 11;");
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
    }

    private void handleLogin(Label validation) {
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

        // Dynamically populate exchanges
        java.util.Arrays.stream(SupportedExchange.values())
                .map(SupportedExchange::getDisplayName)
                .forEach(exchangeBox.getItems()::add);

        marketTypeBox.getSelectionModel().select("Crypto");
        venueBox.getSelectionModel().select("US");
        exchangeBox.getSelectionModel().select(SupportedExchange.COINBASE.getDisplayName());

        styleComboBox(marketTypeBox);
        styleComboBox(venueBox);
        styleComboBox(exchangeBox);

        exchangeBox.setOnAction(event -> {
            if (marketTypeBox.getValue() != null && venueBox.getValue() != null && exchangeBox.getValue() != null) {
                showExchangeCredentialsStep();
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setAlignment(Pos.CENTER);

        Label marketTypeLabel = createLabel("Market Type");
        Label venueLabel = createLabel("Venue");
        Label exchangeLabel = createLabel("Exchange");

        grid.addRow(0, marketTypeLabel, marketTypeBox);
        grid.addRow(1, venueLabel, venueBox);
        grid.addRow(2, exchangeLabel, exchangeBox);

        Button loadMarketButton = createButton("Load Market", BUTTON_PRIMARY);
        loadMarketButton.setPrefWidth(180);
        HBox loadButtonBox = new HBox(loadMarketButton);
        loadButtonBox.setAlignment(Pos.CENTER);

        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
        validation.setAlignment(Pos.CENTER);
        validation.setWrapText(true);

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
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #cbd5e1; -fx-wrap-text: true;");

        VBox content = new VBox(20, title, subtitle, grid, loadButtonBox, validation);
        content.setAlignment(Pos.TOP_CENTER);
        content.setMaxWidth(420);
        content.setStyle(CARD_STYLE);

        VBox rightPanel = new VBox(content);
        rightPanel.setAlignment(Pos.CENTER);
        rightPanel.setPadding(new Insets(48));
        rightPanel.setStyle("-fx-background-color: rgba(15, 23, 42, 0.5);");
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: #0f172a;");
        pane.setLeft(createBackgroundPanel());
        HBox mainContent = new HBox(pane.getLeft(), rightPanel);
        pane.setCenter(mainContent);
        fadeTo(pane);
    }

    private void styleComboBox(ComboBox<?> comboBox) {
        comboBox.setPrefHeight(40);
        comboBox.setStyle("-fx-control-inner-background: #0f172a; -fx-text-fill: #f1f5f9; " +
                "-fx-border-color: #334155; -fx-border-width: 1; -fx-border-radius: 4; " +
                "-fx-background-radius: 4; -fx-font-size: 11;");
    }

    private @NotNull Label createLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12; -fx-font-weight: bold;");
        return label;
    }

    final ChoiceBox<String> selectedTradingModeChoiseBox = new ChoiceBox<>();

    private void showExchangeCredentialsStep() {
        String selectedExchangeName = exchangeBox.getValue();
        SupportedExchange selectedExchange = SupportedExchange.fromDisplayName(selectedExchangeName);

        selectedTradingModeChoiceBox.getItems().clear();
        selectedTradingModeChoiceBox.getItems().addAll("LIVE", "PAPER TRADING");
        selectedTradingModeChoiceBox.setValue("PAPER TRADING");
        selectedTradingModeChoiceBox.setStyle("-fx-font-size: 11;");

        TextField apiKeyField = new TextField();
        styleInputField(apiKeyField,
                selectedExchange == SupportedExchange.OANDA ? "OANDA Token (Bearer Token)" : "API Key");

        PasswordField apiSecretField = getPasswordField(selectedExchange);

        TextField accountIdField = new TextField();
        accountIdField.setPromptText("Account ID (auto-detected if blank)");
        styleInputField(accountIdField, "");
        accountIdField.setVisible(selectedExchange == SupportedExchange.OANDA);
        accountIdField.setManaged(selectedExchange == SupportedExchange.OANDA);

        styleInputField(telegramToken, "Telegram Bot Token (optional)");
        styleInputField(openAiField, "OpenAI API Key (optional)");

        loadRememberedExchangeCredentials(selectedExchangeName, apiKeyField, apiSecretField, accountIdField);

        GridPane credGrid = new GridPane();
        credGrid.setHgap(16);
        credGrid.setVgap(12);
        credGrid.setAlignment(Pos.CENTER);

        Label apiKeyLabel = createLabel(selectedExchange == SupportedExchange.OANDA ? "Token" : "API Key");
        credGrid.addRow(0, apiKeyLabel, apiKeyField);

        if (selectedExchange != SupportedExchange.OANDA) {
            credGrid.addRow(1, createLabel("API Secret"), apiSecretField);
        }

        if (selectedExchange == SupportedExchange.OANDA) {
            credGrid.addRow(1, createLabel("Account ID"), accountIdField);
        }

        credGrid.addRow(2, createLabel("Telegram"), telegramToken);
        credGrid.addRow(3, createLabel("OpenAI"), openAiField);

        HBox modeBox = new HBox(10, createLabel("Trading Mode"), selectedTradingModeChoiceBox);
        modeBox.setAlignment(Pos.CENTER_LEFT);
        credGrid.addRow(4, new Label(""), modeBox);

        CheckBox rememberCredentialsCheckBox = new CheckBox("Remember credentials");
        rememberCredentialsCheckBox.setStyle("-fx-text-fill: #f1f5f9; -fx-font-size: 11;");

        Button continueButton = createButton("Continue", BUTTON_PRIMARY);
        continueButton.setPrefWidth(140);

        Button backButton = createButton("Back", BUTTON_SECONDARY);
        backButton.setPrefWidth(140);

        Label validation = new Label();
        validation.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
        validation.setAlignment(Pos.CENTER);
        validation.setWrapText(true);

        continueButton.setOnAction(event -> handleExchangeCredentials(
                selectedExchange, apiKeyField, apiSecretField, accountIdField,
                rememberCredentialsCheckBox, selectedExchangeName, validation));

        backButton.setOnAction(event -> showConfigurationStep());

        Label title = new Label("Exchange Credentials");
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: 700; -fx-text-fill: #3b82f6;");

        Label subtitle = new Label("Enter your exchange API credentials to connect your account.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #cbd5e1; -fx-wrap-text: true;");

        Label info = createExchangeInfo(selectedExchange);

        Button helpButton = createButton("? Format Help", BUTTON_SECONDARY);
        helpButton.setPrefWidth(140);
        helpButton.setCursor(Cursor.HAND);

        String infoText = getExchangeInfoText(selectedExchange);
        helpButton.setOnAction(event -> showHelpDialog(selectedExchange, infoText));

        HBox buttonBox = new HBox(12, backButton, continueButton, helpButton);
        buttonBox.setAlignment(Pos.CENTER);

        VBox content = new VBox(18, title, subtitle, info, credGrid, rememberCredentialsCheckBox, buttonBox,
                validation);
        content.setAlignment(Pos.TOP_CENTER);
        content.setMaxWidth(500);
        content.setStyle(CARD_STYLE);

        VBox rightPanel = new VBox(content);
        rightPanel.setAlignment(Pos.CENTER);
        rightPanel.setPadding(new Insets(48));
        rightPanel.setStyle("-fx-background-color: rgba(15, 23, 42, 0.5);");
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: #0f172a;");
        pane.setLeft(createBackgroundPanel());
        HBox mainContent = new HBox(pane.getLeft(), rightPanel);
        pane.setCenter(mainContent);
        fadeTo(pane);
    }

    private void handleExchangeCredentials(SupportedExchange selectedExchange, TextField apiKeyField,
            PasswordField apiSecretField, TextField accountIdField, CheckBox rememberCheckBox,
            String selectedExchangeName, Label validation) {
        // Validate inputs
        if (selectedExchange == SupportedExchange.OANDA) {
            if (apiKeyField.getText().isBlank()) {
                validation.setText("Token is required.");
                return;
            }
            configuration = new MarketConfiguration(
                    usernameField.getText().trim(),
                    marketTypeBox.getValue(),
                    venueBox.getValue(),
                    selectedExchange.getFactoryKey(),
                    apiKeyField.getText().trim(),
                    accountIdField.getText().trim(),
                    accountIdField.getText().trim(),
                    telegramToken.getText().trim(),
                    openAiField.getText().trim(),
                    null, null,
                    selectedTradingModeChoiceBox.getValue());
        } else {
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
                    openAiField.getText().trim(),
                    null, null,
                    selectedTradingModeChoiceBox.getValue());
        }

        validation.setStyle("-fx-text-fill: #fbbf24; -fx-font-size: 11;");
        validation.setText("Authenticating with %s...".formatted(selectedExchange.getDisplayName()));

        AuthResult authResult = authenticateExchange(
                selectedExchange.getFactoryKey(),
                apiKeyField.getText().trim(),
                selectedExchange == SupportedExchange.OANDA ? accountIdField.getText().trim()
                        : apiSecretField.getText().trim(),
                accountIdField.getText().trim(),
                selectedTradingModeChoiceBox.getValue());

        if (!authResult.success()) {
            validation.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11;");
            validation.setText(authResult.message());
            return;
        }

        validation.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 11;");
        validation.setText("Authentication successful!");

        if (rememberCheckBox.isSelected()) {
            saveRememberedExchangeCredentials(selectedExchangeName,
                    apiKeyField.getText().trim(),
                    apiSecretField.getText().trim(),
                    accountIdField.getText().trim(),
                    telegramToken.getText().trim());
        }

        saveConfiguration(configuration);
        showLoadingOverlay();
    }

    private @NotNull Label createExchangeInfo(SupportedExchange exchange) {
        Label info = new Label(getExchangeInfoText(exchange));
        info.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8; -fx-wrap-text: true;");
        info.setMaxWidth(500);
        return info;
    }

    private @NotNull String getExchangeInfoText(SupportedExchange selectedExchange) {
        if (selectedExchange == SupportedExchange.COINBASE) {
            return "API Key: Organization ID format (organizations/xxxxx/apiKeys/xxxxx)\n" +
                    "API Secret: EC Private Key in PEM format\nGet credentials at: https://coinbase.com/settings/api";
        } else if (selectedExchange == SupportedExchange.OANDA) {
            return "Token: Bearer token from Account Settings\n" +
                    "Account ID: Optional (auto-detected if left blank)\n" +
                    "Get credentials at: https://www.oanda.com/account/tpa/personal-token";
        } else if (selectedExchange == SupportedExchange.BINANCE || selectedExchange == SupportedExchange.BINANCE_US) {
            return "API Key: Public key from API Management\n" +
                    "API Secret: Secret key from API Management\n" +
                    "Get credentials at: https://www.binance.com/en/user/settings/api-management";
        } else if (selectedExchange == SupportedExchange.BITFINEX) {
            return "API Key: Public key from Settings → API\n" +
                    "API Secret: Secret key from Settings → API\n" +
                    "Get credentials at: https://www.bitfinex.com/api";
        } else if (selectedExchange == SupportedExchange.ALPACA) {
            return "API Key: From Dashboard → API Keys\n" +
                    "API Secret: From Dashboard → API Keys\n" +
                    "Get credentials at: https://app.alpaca.markets/";
        } else {
            return "Enter your API Key and API Secret for " + selectedExchange.getDisplayName();
        }
    }

    private void showHelpDialog(SupportedExchange exchange, String infoText) {
        Alert helpDialog = new Alert(Alert.AlertType.INFORMATION);
        helpDialog.setTitle("Credential Format Help - " + exchange);
        helpDialog.setHeaderText("How to find your " + exchange + " credentials");
        helpDialog.setContentText(infoText);
        helpDialog.showAndWait();
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

        if (normalized.equals("binance") || normalized.equals("binanceus") ||
                normalized.equals("binance_us") || normalized.equals("binance_us_spot")) {
            return "binanceus";
        } else if (normalized.equals("coinbase") || normalized.equals("coinbaseadvanced") ||
                normalized.equals("coinbase_advanced") || normalized.equals("coinbaseadvancedtrade") ||
                normalized.equals("coinbase_advanced_trade")) {
            return "coinbase";
        } else if (normalized.equals("oanda") || normalized.equals("oanda_fx") ||
                normalized.equals("oanda_forex")) {
            return "oanda";
        } else if (normalized.equals("alpaca") || normalized.equals("alpaca_stocks") ||
                normalized.equals("alpaca_equities")) {
            return "alpaca";
        } else if (normalized.equals("bitfinex")) {
            return "bitfinex";
        } else if (normalized.equals("kraken")) {
            return "kraken";
        } else if (normalized.equals("stellar") || normalized.equals("stellar_network") ||
                normalized.equals("stellarnetwork")) {
            return "stellar-network";
        } else {
            throw new IllegalArgumentException("Unsupported exchange: " + value);
        }
    }
}
