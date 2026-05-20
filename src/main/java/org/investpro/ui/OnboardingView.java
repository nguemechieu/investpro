package org.investpro.ui;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.SupportedExchange;
import org.investpro.exchange.contracts.CredentialProvider;
import org.investpro.exchange.contracts.ExchangeIdentity;
import org.investpro.exchange.factory.ExchangeFactory;
import org.investpro.exchange.providers.UiCredentialProvider;
import org.investpro.i18n.LocalizationService;
import org.investpro.i18n.SupportedLanguage;
import org.investpro.service.AuthResult;
import org.investpro.service.ResetTokenResult;
import org.investpro.service.UserAuthService;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import static org.investpro.i18n.LocalizationService.t;

/**
 * Modern onboarding view for InvestPro.
 *
 * <p>Flow:</p>
 * <ol>
 *     <li>User login or account creation</li>
 *     <li>Market and venue selection</li>
 *     <li>Exchange credentials and trading mode</li>
 *     <li>Configuration save and terminal launch</li>
 * </ol>
 */
@Slf4j
public class OnboardingView extends StackPane {

    private static final int DEFAULT_WIDTH = 1540;
    private static final int DEFAULT_HEIGHT = 780;

    private static final String BG = "#020617";
    private static final String SURFACE = "#0f172a";
    private static final String SURFACE_2 = "#111827";
    private static final String PANEL = "rgba(15, 23, 42, 0.86)";
    private static final String CARD = "rgba(15, 23, 42, 0.94)";
    private static final String BORDER = "rgba(71, 85, 105, 0.88)";
    private static final String TEXT = "#e2e8f0";
    private static final String MUTED = "#94a3b8";
    private static final String MUTED_2 = "#64748b";
    private static final String ACCENT = "#38bdf8";
    private static final String PRIMARY = "#2563eb";
    private static final String PRIMARY_HOVER = "#1d4ed8";
    private static final String SUCCESS = "#10b981";
    private static final String WARNING = "#f59e0b";
    private static final String DANGER = "#ef4444";

    private static final String CARD_STYLE = """
            -fx-background-color: %s;
            -fx-border-color: %s;
            -fx-border-width: 1;
            -fx-border-radius: 24;
            -fx-background-radius: 24;
            -fx-padding: 34;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.42), 28, 0.22, 0, 12);
            """.formatted(CARD, BORDER);

    private static final String INPUT_STYLE = """
            -fx-control-inner-background: #020617;
            -fx-background-color: #020617;
            -fx-text-fill: #e2e8f0;
            -fx-prompt-text-fill: #64748b;
            -fx-border-color: #334155;
            -fx-border-width: 1;
            -fx-border-radius: 12;
            -fx-background-radius: 12;
            -fx-padding: 10 12;
            -fx-font-size: 12;
            """;

    private static final String COMBO_STYLE = """
            -fx-background-color: #020617;
            -fx-control-inner-background: #020617;
            -fx-text-fill: #e2e8f0;
            -fx-prompt-text-fill: #64748b;
            -fx-border-color: #334155;
            -fx-border-width: 1;
            -fx-border-radius: 12;
            -fx-background-radius: 12;
            -fx-font-size: 12;
            """;

    private final Consumer<MarketConfiguration> onReady;
    private final UserAuthService authService = new UserAuthService();

    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField emailField = new TextField();
    private final CheckBox rememberMeCheckBox = new CheckBox("REMEMBER ME");

    private final ComboBox<String> marketTypeBox = new ComboBox<>();
    private final ComboBox<String> venueBox = new ComboBox<>();
    private final ComboBox<String> exchangeBox = new ComboBox<>();
    private final ChoiceBox<String> selectedTradingModeChoiceBox = new ChoiceBox<>();

    private final Label statusLabel = new Label();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final TextField telegramToken = new TextField();
    private final PasswordField openAiField = new PasswordField();

    private MarketConfiguration configuration;

    public OnboardingView(Consumer<MarketConfiguration> onReady) {
        this.onReady = Objects.requireNonNull(onReady, "onReady must not be null");

        setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinSize(980, 640);
        setStyle("-fx-background-color: " + BG + ";");
        getStyleClass().add("onboarding-view");

        OnboardingStyles.labelStyle(11, "#cbd5e1;", "16");

        loadRememberedCredentials();
        getChildren().setAll(createLoginStep());
    }

    private @NotNull BorderPane createLoginStep() {
        rememberMeCheckBox.setText(t("onboarding.rememberMe"));

        Label title = new Label(t("onboarding.loginTitle"));
        title.setStyle(titleStyle(34));

        Label prompt = new Label(t("onboarding.loginPrompt"));
        prompt.setStyle(subtitleStyle());
        prompt.setWrapText(true);
        prompt.setMaxWidth(420);

        styleInputField(usernameField, t("onboarding.username"));
        styleInputField(passwordField, t("onboarding.password"));
        styleInputField(emailField, t("onboarding.email"));
        emailField.setVisible(false);
        emailField.setManaged(false);

        rememberMeCheckBox.setStyle(checkBoxStyle());
        rememberMeCheckBox.setPrefHeight(34);
        rememberMeCheckBox.setWrapText(false);
        rememberMeCheckBox.setMaxWidth(200);
        rememberMeCheckBox.setMinWidth(100);
        rememberMeCheckBox.setVisible(false);
        rememberMeCheckBox.setManaged(false);


        Hyperlink forgotPasswordButton = new Hyperlink("Forgot Password?");
        forgotPasswordButton.setOnAction(event -> showForgotPasswordDialog(statusLabelForInline()));

        HBox rememberBox = new HBox(12, rememberMeCheckBox, spacer(), forgotPasswordButton);
        rememberBox.setAlignment(Pos.CENTER_LEFT);
        rememberBox.setMaxWidth(Double.MAX_VALUE);

        Label validation = inlineValidationLabel();
        Button loginButton = createPrimaryButton(t("onboarding.logIn"));
        Button createAccountButton = createSuccessButton(t("onboarding.createAccount"));

        loginButton.setOnAction(event -> handleLogin(validation));
        createAccountButton.setOnAction(event -> handleCreateAccount(validation, emailField));

        HBox buttonBox = new HBox(12, loginButton, createAccountButton);
        buttonBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(loginButton, Priority.ALWAYS);
        HBox.setHgrow(createAccountButton, Priority.ALWAYS);
        loginButton.setMaxWidth(Double.MAX_VALUE);
        createAccountButton.setMaxWidth(Double.MAX_VALUE);

        VBox form = new VBox(14, usernameField, passwordField, emailField, rememberBox, buttonBox, validation);
        form.setAlignment(Pos.TOP_CENTER);

        VBox card = new VBox(20, badge("SECURE TERMINAL ACCESS"), title, prompt, form);
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(460);
        card.setStyle(CARD_STYLE);

        return createShell(card, true, () -> getChildren().setAll(createLoginStep()));
    }

    private @NotNull BorderPane createShell(@NotNull VBox card, boolean showLanguageSelector, Runnable onLanguageChanged) {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: linear-gradient(to bottom right, #020617, #0f172a, #111827);");

        VBox leftPanel = createBackgroundPanel();
        VBox rightPanel = new VBox(card);
        rightPanel.setAlignment(Pos.CENTER);
        rightPanel.setPadding(new Insets(48));
        rightPanel.setStyle("-fx-background-color: rgba(2, 6, 23, 0.45);");
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        HBox mainContent = new HBox(leftPanel, rightPanel);
        mainContent.setPrefWidth(DEFAULT_WIDTH);
        mainContent.setStyle("-fx-background-color: transparent;");
        pane.setCenter(mainContent);

        if (showLanguageSelector) {
            pane.setTop(createLanguageSelector(onLanguageChanged));
        }

        return pane;
    }

    private @NotNull VBox createBackgroundPanel() {
        VBox leftPanel = new VBox(22);
        leftPanel.setPrefWidth(560);
        leftPanel.setMinWidth(430);
        leftPanel.setAlignment(Pos.CENTER);
        leftPanel.setPadding(new Insets(44));
        leftPanel.setStyle("""
                -fx-background-color: linear-gradient(to bottom right, #0f172a, #111827, #020617);
                -fx-border-color: rgba(56, 189, 248, 0.18);
                -fx-border-width: 0 1 0 0;
                """);

        Region glow = new Region();
        glow.setPrefSize(360, 360);
        glow.setMaxSize(360, 360);
        glow.setStyle("""
                -fx-background-color: radial-gradient(center 50% 50%, radius 65%, rgba(56,189,248,0.22), rgba(37,99,235,0.08), transparent);
                -fx-background-radius: 999;
                """);

        StackPane logoStage = new StackPane(glow);
        logoStage.setAlignment(Pos.CENTER);

        try {
            Image backgroundImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/Invest.png")));
            ImageView imageView = new ImageView(backgroundImage);
            imageView.setFitWidth(390);
            imageView.setFitHeight(390);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            logoStage.getChildren().add(imageView);
        } catch (Exception e) {
            log.warn("Failed to load onboarding logo image", e);
            Label fallbackLabel = new Label("InvestPro");
            fallbackLabel.setStyle("-fx-font-size: 48px; -fx-font-weight: 800; -fx-text-fill: " + ACCENT + ";");
            logoStage.getChildren().add(fallbackLabel);
        }

        Label branding = new Label("InvestPro");
        branding.setStyle("-fx-font-size: 30px; -fx-font-weight: 800; -fx-text-fill: " + TEXT + ";");

        Label tagline = new Label("Multi-venue trading workstation for research, automation, and execution.");
        tagline.setStyle("-fx-font-size: 13px; -fx-text-fill: " + MUTED + ";");
        tagline.setWrapText(true);
        tagline.setMaxWidth(420);
        tagline.setAlignment(Pos.CENTER);

        HBox featureRow = new HBox(8,
                miniPill("Risk-first"),
                miniPill("Paper mode"),
                miniPill("Multi-asset"));
        featureRow.setAlignment(Pos.CENTER);

        VBox textBlock = new VBox(8, branding, tagline, featureRow);
        textBlock.setAlignment(Pos.CENTER);

        leftPanel.getChildren().addAll(logoStage, textBlock);
        return leftPanel;
    }

    private Label miniPill(String text) {
        Label pill = new Label(text);
        pill.setStyle("""
                -fx-padding: 6 10;
                -fx-background-color: rgba(15, 23, 42, 0.75);
                -fx-border-color: rgba(56, 189, 248, 0.34);
                -fx-border-radius: 999;
                -fx-background-radius: 999;
                -fx-text-fill: #bae6fd;
                -fx-font-size: 10;
                -fx-font-weight: bold;
                """);
        return pill;
    }

    private Label badge(String text) {
        Label badge = new Label(text);
        badge.setStyle("""
                -fx-padding: 7 12;
                -fx-background-color: rgba(8, 47, 73, 0.72);
                -fx-border-color: rgba(56, 189, 248, 0.65);
                -fx-border-radius: 999;
                -fx-background-radius: 999;
                -fx-text-fill: #7dd3fc;
                -fx-font-size: 10;
                -fx-font-weight: bold;
                """);
        return badge;
    }

    private void styleInputField(TextField field, String prompt) {
        field.setPromptText(prompt);
        field.setStyle(INPUT_STYLE);
        field.setPrefHeight(44);
        field.setMinHeight(44);
        field.setEditable(true);
        field.setFocusTraversable(true);
    }

    private void styleChoiceBox(ChoiceBox<?> choiceBox) {
        choiceBox.setPrefHeight(42);
        choiceBox.setStyle(COMBO_STYLE);
    }

    private void styleComboBox(ComboBox<?> comboBox) {
        comboBox.setPrefHeight(44);
        comboBox.setStyle(COMBO_STYLE);
    }

    private @NotNull Button createPrimaryButton(String text) {
        return createButton(text, PRIMARY);
    }

    private @NotNull Button createSuccessButton(String text) {
        return createButton(text, SUCCESS);
    }

    private @NotNull Button createSecondaryButton(String text) {
        return createButton(text, "#1e40af");
    }

    private @NotNull Button createGhostButton(String text) {
        Button button = new Button(text);
        button.setStyle("""
                -fx-padding: 8 10;
                -fx-background-color: transparent;
                -fx-text-fill: #93c5fd;
                -fx-font-size: 11;
                -fx-font-weight: bold;
                -fx-background-radius: 10;
                -fx-cursor: hand;
                """);
        button.setMinWidth(96);
        return button;
    }

    private @NotNull Button createButton(String text, String color) {
        Button button = new Button(text);
        button.setStyle(buttonStyle(color));
        button.setPrefHeight(42);
        button.setMinWidth(112);
        button.setCursor(Cursor.HAND);
        return button;
    }

    private String buttonStyle(String color) {
        return """
                -fx-padding: 10 18;
                -fx-background-color: %s;
                -fx-text-fill: white;
                -fx-font-weight: 800;
                -fx-font-size: 12;
                -fx-border-radius: 12;
                -fx-background-radius: 12;
                -fx-cursor: hand;
                """.formatted(color);
    }

    private Label inlineValidationLabel() {
        Label validation = new Label();
        validation.setStyle("-fx-text-fill: " + DANGER + "; -fx-font-size: 11;");
        validation.setAlignment(Pos.CENTER);
        validation.setWrapText(true);
        validation.setMaxWidth(420);
        return validation;
    }

    private Label statusLabelForInline() {
        Label validation = inlineValidationLabel();
        validation.setText("");
        return validation;
    }

    private Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private String titleStyle(int size) {
        return "-fx-font-size: " + size + "px; -fx-font-weight: 900; -fx-text-fill: " + TEXT + ";";
    }

    private String subtitleStyle() {
        return "-fx-font-size: 13px; -fx-text-fill: " + MUTED + "; -fx-line-spacing: 2;";
    }

    private String checkBoxStyle() {
        return "-fx-text-fill: " + TEXT + "; -fx-font-size: 11; -fx-padding: 4 0 4 0;";
    }

    private void handleCreateAccount(Label validation, TextField emailField) {
        if (!emailField.isVisible()) {
            emailField.setVisible(true);
            emailField.setManaged(true);
            validation.setStyle("-fx-text-fill: " + WARNING + "; -fx-font-size: 11;");
            validation.setText(t("onboarding.createHint"));
            return;
        }

        if (usernameField.getText().isBlank() || passwordField.getText().isBlank() || emailField.getText().isBlank()) {
            validation.setStyle("-fx-text-fill: " + DANGER + "; -fx-font-size: 11;");
            validation.setText("Username, password, and email are required.");
            return;
        }

        char[] password = passwordField.getText().toCharArray();
        AuthResult result = authService.register(usernameField.getText().trim(), emailField.getText().trim(), password);
        Arrays.fill(password, '\0');

        validation.setStyle("-fx-text-fill: %s; -fx-font-size: 11;".formatted(result.success() ? SUCCESS : DANGER));
        validation.setText(result.message());

        if (result.success()) {
            if (rememberMeCheckBox.isSelected()) {
                authService.rememberUser(usernameField.getText().trim());
            }
            passwordField.clear();
            showConfigurationStep();
        }
    }

    private void handleLogin(Label validation) {
        if (usernameField.getText().isBlank() || passwordField.getText().isBlank()) {
            validation.setStyle("-fx-text-fill: " + DANGER + "; -fx-font-size: 11;");
            validation.setText("Username and password are required.");
            return;
        }

        char[] password = passwordField.getText().toCharArray();
        AuthResult result = authService.signIn(usernameField.getText().trim(), password);
        Arrays.fill(password, '\0');

        validation.setStyle("-fx-text-fill: %s; -fx-font-size: 11;".formatted(result.success() ? SUCCESS : DANGER));
        validation.setText(result.message());

        if (!result.success()) {
            return;
        }

        if (rememberMeCheckBox.isSelected()) {
            authService.rememberUser(usernameField.getText().trim());
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
        languageBox.setPrefWidth(180);
        styleComboBox(languageBox);
        languageBox.setOnAction(event -> {
            SupportedLanguage selected = languageBox.getValue();
            if (selected != null) {
                LocalizationService.setCurrentLanguage(selected);
                if (onChanged != null) {
                    onChanged.run();
                }
            }
        });

        Label label = new Label(LocalizationService.t("language.menu"));
        label.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 11; -fx-font-weight: bold;");

        HBox box = new HBox(10, label, languageBox);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(16, 22, 0, 22));
        box.setStyle("-fx-background-color: transparent;");
        return box;
    }

    private void showConfigurationStep() {
        marketTypeBox.getItems().setAll("Crypto", "Forex", "Stocks", "Futures", "Options", "ETFs", "Bonds");
        venueBox.getItems().setAll("US", "Global", "Spot", "Derivatives", "Paper Trading");

        exchangeBox.getItems().clear();
        Arrays.stream(SupportedExchange.values())
                .map(SupportedExchange::getDisplayName)
                .forEach(exchangeBox.getItems()::add);

        marketTypeBox.getSelectionModel().select("Crypto");
        venueBox.getSelectionModel().select("US");
        exchangeBox.getSelectionModel().select(SupportedExchange.COINBASE.getDisplayName());

        styleComboBox(marketTypeBox);
        styleComboBox(venueBox);
        styleComboBox(exchangeBox);

        Label title = new Label("Market Configuration");
        title.setStyle(titleStyle(30));

        Label subtitle = new Label("Choose the market, venue, and exchange that InvestPro should connect to.");
        subtitle.setStyle(subtitleStyle());
        subtitle.setWrapText(true);

        GridPane grid = formGrid();
        grid.addRow(0, createLabel("Market Type"), marketTypeBox);
        grid.addRow(1, createLabel("Venue"), venueBox);
        grid.addRow(2, createLabel("Exchange"), exchangeBox);

        Label validation = inlineValidationLabel();
        Button loadMarketButton = createPrimaryButton("Load Market");
        loadMarketButton.setPrefWidth(180);
        loadMarketButton.setOnAction(event -> {
            if (marketTypeBox.getValue() == null || venueBox.getValue() == null || exchangeBox.getValue() == null) {
                validation.setText("Select a market type, venue, and exchange.");
                return;
            }
            showExchangeCredentialsStep();
        });

        HBox loadButtonBox = new HBox(loadMarketButton);
        loadButtonBox.setAlignment(Pos.CENTER);

        VBox card = new VBox(20, badge("MARKET ROUTING"), title, subtitle, grid, loadButtonBox, validation);
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(470);
        card.setStyle(CARD_STYLE);

        fadeTo(createShell(card, false, null));
    }

    private GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);
        grid.setAlignment(Pos.CENTER);
        return grid;
    }

    private @NotNull Label createLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12; -fx-font-weight: bold;");
        return label;
    }

    private void showExchangeCredentialsStep() {
        String selectedExchangeName = exchangeBox.getValue();
        SupportedExchange selectedExchange = SupportedExchange.fromDisplayName(selectedExchangeName);

        selectedTradingModeChoiceBox.getItems().setAll("PAPER TRADING", "LIVE");
        selectedTradingModeChoiceBox.setValue("PAPER TRADING");
        styleChoiceBox(selectedTradingModeChoiceBox);

        TextField apiKeyField = new TextField();
        styleInputField(apiKeyField, apiKeyPrompt(selectedExchange));

        PasswordField apiSecretField = getPasswordField(selectedExchange);

        TextField accountIdField = new TextField();
        styleInputField(accountIdField, selectedExchange == SupportedExchange.OANDA ? "OANDA Account ID" : "Account ID (optional)");
        boolean showAccountId = selectedExchange == SupportedExchange.OANDA || selectedExchange == SupportedExchange.STELLAR_NETWORK;
        accountIdField.setVisible(showAccountId);
        accountIdField.setManaged(showAccountId);

        styleInputField(telegramToken, "Telegram Bot Token (optional)");
        styleInputField(openAiField, "OpenAI API Key (optional)");

        loadRememberedExchangeCredentials(selectedExchangeName, apiKeyField, apiSecretField, accountIdField);

        GridPane credGrid = formGrid();
        credGrid.addRow(0, createLabel(apiKeyLabel(selectedExchange)), apiKeyField);

        int row = 1;
        if (selectedExchange != SupportedExchange.OANDA) {
            credGrid.addRow(row++, createLabel(selectedExchange == SupportedExchange.STELLAR_NETWORK ? "Secret Seed" : "API Secret"), apiSecretField);
        }
        if (showAccountId) {
            credGrid.addRow(row++, createLabel(selectedExchange == SupportedExchange.STELLAR_NETWORK ? "Public Account" : "Account ID"), accountIdField);
        }

        credGrid.addRow(row++, createLabel("Telegram"), telegramToken);
        credGrid.addRow(row++, createLabel("OpenAI"), openAiField);
        credGrid.addRow(row, createLabel("Trading Mode"), selectedTradingModeChoiceBox);

        CheckBox rememberCredentialsCheckBox = new CheckBox("Remember credentials");
        rememberCredentialsCheckBox.setStyle(checkBoxStyle());

        Button continueButton = createPrimaryButton("Continue");
        continueButton.setPrefWidth(138);
        Button backButton = createSecondaryButton("Back");
        backButton.setPrefWidth(138);
        Button helpButton = createSecondaryButton("Format Help");
        helpButton.setPrefWidth(138);

        Label validation = inlineValidationLabel();

        continueButton.setOnAction(event -> handleExchangeCredentials(
                selectedExchange,
                apiKeyField,
                apiSecretField,
                accountIdField,
                rememberCredentialsCheckBox,
                selectedExchangeName,
                validation));
        backButton.setOnAction(event -> showConfigurationStep());
        helpButton.setOnAction(event -> showHelpDialog(selectedExchange, getExchangeInfoText(selectedExchange)));

        Label title = new Label("Exchange Credentials");
        title.setStyle(titleStyle(30));

        Label subtitle = new Label("Connect with paper trading first. Use live mode only after validating strategies and risk controls.");
        subtitle.setStyle(subtitleStyle());
        subtitle.setWrapText(true);

        Label info = createExchangeInfo(selectedExchange);

        HBox buttonBox = new HBox(12, backButton, continueButton, helpButton);
        buttonBox.setAlignment(Pos.CENTER);

        VBox card = new VBox(18, badge("SECURE BROKER CONNECTION"), title, subtitle, info, credGrid,
                rememberCredentialsCheckBox, buttonBox, validation);
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(540);
        card.setStyle(CARD_STYLE);

        fadeTo(createShell(card, false, null));
    }

    private String apiKeyPrompt(SupportedExchange selectedExchange) {
        if (selectedExchange == SupportedExchange.OANDA) {
            return "OANDA Token (Bearer Token)";
        }
        if (selectedExchange == SupportedExchange.STELLAR_NETWORK) {
            return "Stellar public account ID (G...)";
        }
        return "API Key";
    }

    private String apiKeyLabel(SupportedExchange selectedExchange) {
        if (selectedExchange == SupportedExchange.OANDA) {
            return "Token";
        }
        if (selectedExchange == SupportedExchange.STELLAR_NETWORK) {
            return "Public Account";
        }
        return "API Key";
    }

    private void handleExchangeCredentials(SupportedExchange selectedExchange,
                                           TextField apiKeyField,
                                           PasswordField apiSecretField,
                                           TextField accountIdField,
                                           CheckBox rememberCheckBox,
                                           String selectedExchangeName,
                                           Label validation) {
        String apiKey = apiKeyField.getText().trim();
        String apiSecret = apiSecretField.getText().trim();
        String accountId = accountIdField.getText().trim();
        String tradingMode = selectedTradingModeChoiceBox.getValue();

        if (tradingMode == null || tradingMode.isBlank()) {
            tradingMode = "PAPER TRADING";
        }

        if (selectedExchange == SupportedExchange.OANDA) {
            if (apiKey.isBlank()) {
                validation.setText("OANDA token is required.");
                return;
            }
            apiSecret = accountId;
        } else if (selectedExchange == SupportedExchange.STELLAR_NETWORK) {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                validation.setText("Stellar public account and secret seed are required.");
                return;
            }
            accountId = apiKey;
        } else if (apiKey.isBlank() || apiSecret.isBlank()) {
            validation.setText("API Key and API Secret are required.");
            return;
        }

        configuration = new MarketConfiguration(
                usernameField.getText().trim(),
                marketTypeBox.getValue(),
                venueBox.getValue(),
                selectedExchange.getFactoryKey(),
                apiKey,
                apiSecret,
                accountId,
                telegramToken.getText().trim(),
                openAiField.getText().trim(),
                null,
                null,
                tradingMode);

        validation.setStyle("-fx-text-fill: " + WARNING + "; -fx-font-size: 11;");
        validation.setText("Authenticating with %s...".formatted(selectedExchange.getDisplayName()));

        AuthResult authResult = authenticateExchange(selectedExchange.getFactoryKey(), apiKey, apiSecret, accountId, tradingMode);
        if (!authResult.success()) {
            validation.setStyle("-fx-text-fill: " + DANGER + "; -fx-font-size: 11;");
            validation.setText(authResult.message());
            return;
        }

        validation.setStyle("-fx-text-fill: " + SUCCESS + "; -fx-font-size: 11;");
        validation.setText("Authentication successful!");

        if (rememberCheckBox.isSelected()) {
            saveRememberedExchangeCredentials(selectedExchangeName, apiKey, apiSecret, accountId, telegramToken.getText().trim());
        }

        saveConfiguration(configuration);
        showLoadingOverlay();
    }

    private @NotNull Label createExchangeInfo(SupportedExchange exchange) {
        Label info = new Label(getExchangeInfoText(exchange));
        info.setStyle("""
                -fx-font-size: 11px;
                -fx-text-fill: #94a3b8;
                -fx-wrap-text: true;
                -fx-padding: 12;
                -fx-background-color: rgba(2, 6, 23, 0.50);
                -fx-border-color: rgba(71, 85, 105, 0.65);
                -fx-border-radius: 12;
                -fx-background-radius: 12;
                """);
        info.setMaxWidth(520);
        return info;
    }

    private @NotNull String getExchangeInfoText(SupportedExchange selectedExchange) {
        if (selectedExchange == SupportedExchange.COINBASE) {
            return """
                    Coinbase Advanced Trade
                    API Key: organizations/{org_id}/apiKeys/{key_id}
                    API Secret: EC private key in PEM format
                    Tip: grant account/order permissions only when live trading is needed.""";
        }
        if (selectedExchange == SupportedExchange.OANDA) {
            return """
                    OANDA
                    Token: Bearer token from account settings
                    Account ID: optional if your adapter can auto-detect it
                    Tip: start with practice/paper mode before live trading.""";
        }
        if (selectedExchange == SupportedExchange.BINANCE || selectedExchange == SupportedExchange.BINANCE_US) {
            return """
                    Binance / Binance US
                    API Key: public key from API Management
                    API Secret: secret key from API Management
                    Tip: use WebSocket streams for market data to avoid REST rate limits.""";
        }
        if (selectedExchange == SupportedExchange.BITFINEX) {
            return """
                    Bitfinex
                    API Key: public key from Settings → API
                    API Secret: secret key from Settings → API""";
        }
        if (selectedExchange == SupportedExchange.ALPACA) {
            return """
                    Alpaca
                    API Key: from Dashboard → API Keys
                    API Secret: from Dashboard → API Keys
                    Tip: use Alpaca paper account first.""";
        }
        if (selectedExchange == SupportedExchange.STELLAR_NETWORK) {
            return """
                    Stellar Network
                    Public Account: G... account ID
                    Secret Seed: S... secret seed
                    Tip: XLM is native and issued assets require trusted issuers.""";
        }
        return "Enter your API credentials for " + selectedExchange.getDisplayName();
    }

    private void showHelpDialog(SupportedExchange exchange, String infoText) {
        Alert helpDialog = new Alert(Alert.AlertType.INFORMATION);
        helpDialog.setTitle("Credential Format Help - " + exchange.getDisplayName());
        helpDialog.setHeaderText("How to enter " + exchange.getDisplayName() + " credentials");
        helpDialog.setContentText(infoText);
        helpDialog.showAndWait();
    }

    private @NotNull PasswordField getPasswordField(SupportedExchange selectedExchange) {
        PasswordField apiSecretField = new PasswordField();
        styleInputField(apiSecretField, selectedExchange == SupportedExchange.STELLAR_NETWORK ? "Secret Seed (S...)" : "API Secret");
        apiSecretField.setVisible(selectedExchange != SupportedExchange.OANDA);
        apiSecretField.setManaged(selectedExchange != SupportedExchange.OANDA);
        return apiSecretField;
    }

    private void showLoadingOverlay() {
        statusLabel.setText("Saving settings...");
        statusLabel.setStyle("-fx-text-fill: " + TEXT + ";");
        progressBar.setProgress(0);
        progressBar.setPrefWidth(430);
        progressBar.setStyle("-fx-accent: " + ACCENT + ";");

        Label title = new Label("Preparing Terminal");
        title.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 18px; -fx-font-weight: 900;");

        Label subtitle = new Label("InvestPro is loading your workspace.");
        subtitle.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12;");

        VBox overlay = new VBox(16, title, subtitle, statusLabel, progressBar);
        overlay.setAlignment(Pos.CENTER);
        overlay.setPadding(new Insets(36));
        overlay.setMaxSize(540, 250);
        overlay.setStyle("""
                -fx-background-color: rgba(15, 23, 42, 0.98);
                -fx-border-color: rgba(56, 189, 248, 0.35);
                -fx-border-radius: 24;
                -fx-background-radius: 24;
                -fx-border-width: 1;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.48), 30, 0.20, 0, 14);
                """);

        StackPane loadingPane = new StackPane(overlay);
        loadingPane.setStyle("-fx-background-color: rgba(2, 6, 23, 0.78);");
        getChildren().add(loadingPane);

        Timeline timeline = new Timeline(
                frame(0.20, "Saving configuration..."),
                frame(0.42, "Connecting to market venue..."),
                frame(0.65, "Loading exchange instruments..."),
                frame(0.84, "Preparing trading workstation..."),
                frame(1.0, "Market data is ready."));
        timeline.setOnFinished(event -> {
            PauseTransition pause = new PauseTransition(Duration.millis(420));
            pause.setOnFinished(pauseEvent -> onReady.accept(configuration));
            pause.play();
        });
        timeline.play();
    }

    @Contract("_, _ -> new")
    private @NotNull KeyFrame frame(double progress, String message) {
        return new KeyFrame(Duration.millis(2400 * progress),
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
                validation.setStyle("-fx-text-fill: " + DANGER + ";");
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
                        validation.setStyle("-fx-text-fill: " + DANGER + ";");
                        validation.setText("New password and confirmation do not match.");
                        return;
                    }
                    char[] password = newPasswordField.getText().toCharArray();
                    AuthResult result = authService.resetPassword(accountField.getText(), tokenField.getText(), password);
                    Arrays.fill(password, '\0');
                    validation.setStyle("-fx-text-fill: %s;".formatted(result.success() ? SUCCESS : DANGER));
                    validation.setText(result.message());
                    if (result.success()) {
                        usernameField.setText(accountField.getText().trim());
                        passwordField.clear();
                    }
                });
    }

    private void saveConfiguration(@NotNull MarketConfiguration configuration) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingView.class);
        preferences.put("username", safe(configuration.username()));
        preferences.put("marketType", safe(configuration.marketType()));
        preferences.put("venue", safe(configuration.venue()));
        preferences.put("exchange", safe(configuration.exchange()));
        preferences.put("apiKey", safe(configuration.apiKey()));
        preferences.put("apiSecret", safe(configuration.apiSecret()));
        preferences.put("accountId", safe(configuration.accountId()));
        preferences.put("telegramToken", safe(configuration.telegramToken()));
        preferences.put("open_ai_api_key", safe(configuration.openaiApiKey()));
        preferences.put("open_ai_model", safe(configuration.openaiModel()));
        preferences.put("tradingMode", safe(configuration.selectedTradingMode()));
        flushPreferences(preferences);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void fadeTo(BorderPane next) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(150), this);
        fadeOut.setFromValue(getOpacity());
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

        for (String exchange : Arrays.stream(SupportedExchange.values()).map(SupportedExchange::getDisplayName).toList()) {
            preferences.remove("exchange_api_key_%s".formatted(exchange));
            preferences.remove("exchange_api_secret_%s".formatted(exchange));
            preferences.remove("exchange_account_id_%s".formatted(exchange));
            preferences.remove("exchange_venue_%s".formatted(exchange));
            preferences.remove("telegram_token_%s".formatted(exchange));
        }

        flushPreferences(preferences);
        usernameField.clear();
        passwordField.clear();
        rememberMeCheckBox.setSelected(false);
    }

    private void saveRememberedExchangeCredentials(String exchange, String apiKey, String apiSecret, String accountId,
                                                   String token) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingView.class);
        preferences.put("exchange_api_key_%s".formatted(exchange), safe(apiKey));
        preferences.put("exchange_api_secret_%s".formatted(exchange), safe(apiSecret));
        preferences.put("exchange_account_id_%s".formatted(exchange), safe(accountId));
        preferences.put("telegram_token_%s".formatted(exchange), safe(token));
        flushPreferences(preferences);
    }

    private void loadRememberedExchangeCredentials(String exchange,
                                                   TextField apiKeyField,
                                                   PasswordField apiSecretField,
                                                   TextField accountIdField) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingView.class);
        apiKeyField.setText(preferences.get("exchange_api_key_%s".formatted(exchange), ""));
        apiSecretField.setText(preferences.get("exchange_api_secret_%s".formatted(exchange), ""));
        accountIdField.setText(preferences.get("exchange_account_id_%s".formatted(exchange), ""));
        telegramToken.setText(preferences.get("telegram_token_%s".formatted(exchange), ""));
    }

    private void flushPreferences(Preferences preferences) {
        try {
            preferences.flush();
        } catch (Exception exception) {
            log.warn("Failed to flush onboarding preferences", exception);
        }
    }

    private @NotNull Exchange createExchange(String selectedExchange,
                                             String apiKey,
                                             String apiSecret,
                                             String accountId,
                                             String tradingMode) {
        String exchangeId = normalizeExchangeId(selectedExchange);

        CredentialProvider credentialProvider = new UiCredentialProvider(exchangeId, apiKey, apiSecret, accountId, tradingMode);
        ExchangeFactory exchangeFactory = new ExchangeFactory(credentialProvider);

        Exchange exchange = exchangeFactory.create(exchangeId);
        exchange.setUserSelectedTradingMode(tradingMode);
        exchange.connect();

        return exchange;
    }

    private @NotNull AuthResult authenticateExchange(String selectedExchange,
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
            return AuthResult.failure("Authentication failed for %s: %s".formatted(selectedExchange, rootMessage(exception)));
        }
    }

    private @NonNull String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank() ? "Unknown error" : message;
    }

    private @NotNull String normalizeExchangeId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Exchange name is required");
        }

        try {
            return SupportedExchange.fromDisplayName(value).getFactoryKey();
        } catch (IllegalArgumentException ignored) {
            // Fall through to accepting typed aliases and legacy saved values.
        }

        String normalized = value.trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replace(" ", "")
                .replace("-", "_");

        if (normalized.equals("binance")) {
            return "binance";
        }
        if (normalized.equals("binanceus") || normalized.equals("binance_us") || normalized.equals("binance_us_spot")) {
            return "binance-us";
        }
        if (normalized.equals("coinbase") || normalized.equals("coinbaseadvanced")
                || normalized.equals("coinbase_advanced") || normalized.equals("coinbaseadvancedtrade")
                || normalized.equals("coinbase_advanced_trade") || normalized.equals("coinbasepro")
                || normalized.equals("coinbase_pro")) {
            return "coinbase";
        }
        if (normalized.equals("oanda") || normalized.equals("oanda_fx") || normalized.equals("oanda_forex")) {
            return "oanda";
        }
        if (normalized.equals("alpaca") || normalized.equals("alpaca_stocks") || normalized.equals("alpaca_equities")) {
            return "alpaca";
        }
        if (normalized.equals("bitfinex")) {
            return "bitfinex";
        }
        if (normalized.equals("bitfinexus") || normalized.equals("bitfinex_us")) {
            return "bitfinex-us";
        }
        if (normalized.equals("interactivebrokers") || normalized.equals("interactive_brokers")
                || normalized.equals("ibkr") || normalized.equals("ibk")) {
            return "interactive-brokers";
        }
        if (normalized.equals("kraken")) {
            return "kraken";
        }
        if (normalized.equals("ig") || normalized.equals("bittrex") || normalized.equals("bitmex")
                || normalized.equals("kucoin") || normalized.equals("kucoinus") || normalized.equals("kucoin_us")
                || normalized.equals("bitstamp") || normalized.equals("poloniex")) {
            return normalized.replace("_", "-");
        }
        if (normalized.equals("stellar") || normalized.equals("stellar_network") || normalized.equals("stellarnetwork")) {
            return "stellar-network";
        }

        throw new IllegalArgumentException("Unsupported exchange: " + value);
    }
}
