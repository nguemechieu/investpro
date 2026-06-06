package org.investpro.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.Account;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.SupportedExchange;
import org.investpro.exchange.contracts.CredentialProvider;
import org.investpro.exchange.factory.ExchangeFactory;
import org.investpro.i18n.LocalizationService;
import org.investpro.i18n.SupportedLanguage;
import org.investpro.service.AuthResult;
import org.investpro.service.ResetTokenResult;
import org.investpro.service.UserAuthService;
import org.investpro.ui.theme.MarketConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import static org.investpro.i18n.LocalizationService.t;

/**
 * Modern onboarding view for InvestPro.
 *
 * <p>
 * Flow:
 * </p>
 * <ol>
 * <li>User login or account creation</li>
 * <li>Market and venue selection</li>
 * <li>Exchange credentials and trading mode</li>
 * <li>Configuration save and terminal launch</li>
 * </ol>
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class OnboardingDesk extends StackPane {

    private static final int DEFAULT_WIDTH = 1540;
    private static final int DEFAULT_HEIGHT = 780;

    private static final String BG = "#020617";

    private static final String CARD = "rgba(15, 23, 42, 0.94)";
    private static final String BORDER = "rgba(71, 85, 105, 0.88)";
    private static final String TEXT = "#e2e8f0";
    private static final String MUTED = "#94a3b8";
    private static final String ACCENT = "#38bdf8";
    private static final String PRIMARY = "#2563eb";
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
    private final AtomicBoolean launchTransitionStarted = new AtomicBoolean(false);
    private StackPane activeLoadingOverlay;

    private MarketConfiguration configuration;

    public OnboardingDesk(Consumer<MarketConfiguration> onReady) {
        this.onReady = Objects.requireNonNull(onReady, "onReady must not be null");

        setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinSize(980, 640);
        setStyle("-fx-background-color: " + BG + ";");
        getStyleClass().add("onboarding-view");
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
        rememberMeCheckBox.setMinHeight(34);
        rememberMeCheckBox.setWrapText(false);
        rememberMeCheckBox.setVisible(true);
        rememberMeCheckBox.setManaged(true);
        rememberMeCheckBox.setDisable(false);
        rememberMeCheckBox.setMouseTransparent(false);
        rememberMeCheckBox.setFocusTraversable(true);
        rememberMeCheckBox.setMaxWidth(Region.USE_PREF_SIZE);

        Hyperlink forgotPasswordButton = new Hyperlink("Forgot Password?");
        forgotPasswordButton.setOnAction(event -> showForgotPasswordDialog(statusLabelForInline()));

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

        GridPane form = new GridPane();
        form.setAlignment(Pos.TOP_CENTER);
        form.setHgap(12);
        form.setVgap(14);
        form.setMaxWidth(420);

        ColumnConstraints fullWidth = new ColumnConstraints();
        fullWidth.setHgrow(Priority.ALWAYS);
        fullWidth.setFillWidth(true);
        form.getColumnConstraints().add(fullWidth);

        usernameField.setMaxWidth(Double.MAX_VALUE);
        passwordField.setMaxWidth(Double.MAX_VALUE);
        emailField.setMaxWidth(Double.MAX_VALUE);
        buttonBox.setMaxWidth(Double.MAX_VALUE);
        validation.setMaxWidth(Double.MAX_VALUE);

        int row = 0;

        form.add(usernameField, 0, row++);
        form.add(passwordField, 0, row++);
        form.add(emailField, 0, row++);

        form.add(spacer(), 0, row++);

        form.add(rememberMeCheckBox, 0, row++);

        form.add(spacer(), 0, row++);
        form.add(buttonBox, 0, row++);
        form.add(validation, 0, row);
        form.add(spacer(), 0, row++);
        form.add(forgotPasswordButton, 1, row + 1);

        VBox card = new VBox(20, badge("SECURE TERMINAL ACCESS"), title, prompt, form);
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(460);
        card.setStyle(CARD_STYLE);

        return createShell(card, true, () -> getChildren().setAll(createLoginStep()));
    }

    private @NotNull BorderPane createShell(@NotNull VBox card, boolean showLanguageSelector,
            Runnable onLanguageChanged) {
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
        glow.setStyle(
                """
                        -fx-background-color: radial-gradient(center 50% 50%, radius 65%, rgba(56,189,248,0.22), rgba(37,99,235,0.08), transparent);
                        -fx-background-radius: 999;
                        """);

        StackPane logoStage = new StackPane(glow);
        logoStage.setAlignment(Pos.CENTER);

        try {
            Image backgroundImage = new Image(
                    Objects.requireNonNull(getClass().getResourceAsStream("/images/Invest.png")));
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
        spacer.setMinHeight(4);
        spacer.setPrefHeight(4);
        spacer.setMaxHeight(4);
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

        applySavedConfigurationSelection();

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
        selectedTradingModeChoiceBox.setValue(loadSavedTradingMode());
        styleChoiceBox(selectedTradingModeChoiceBox);
        boolean isIbkr = selectedExchange == SupportedExchange.INTERACTIVE_BROKERS;
        if (isIbkr) {
            showIbkrControlPanelCredentialsStep(selectedExchange, selectedExchangeName);
            return;
        }

        TextField apiKeyField = new TextField();
        styleInputField(apiKeyField, apiKeyPrompt(selectedExchange));

        PasswordField apiSecretField = getPasswordField(selectedExchange);

        TextField accountIdField = new TextField();
        styleInputField(accountIdField,
                selectedExchange == SupportedExchange.OANDA
                        ? "OANDA Account ID"
                        : "Account ID (optional)");
        TextField ibkrTwoFactorCodeField = new TextField();
        styleInputField(ibkrTwoFactorCodeField, "Two-Factor Code (optional note)");
        ibkrTwoFactorCodeField.setVisible(false);
        ibkrTwoFactorCodeField.setManaged(false);
        boolean showAccountId = selectedExchange == SupportedExchange.OANDA || selectedExchange == SupportedExchange.STELLAR_NETWORK || selectedExchange == SupportedExchange.SOLONA_NETWORK;
        accountIdField.setVisible(showAccountId);
        accountIdField.setManaged(showAccountId);

        TextField ibkrClientPortalUrlField = new TextField();
        styleInputField(ibkrClientPortalUrlField, "IBKR Client Portal URL (optional)");
        ibkrClientPortalUrlField.setVisible(false);
        ibkrClientPortalUrlField.setManaged(false);

        TextField ibkrHostField = new TextField();
        styleInputField(ibkrHostField, "IB Gateway Host (default: 127.0.0.1)");
        ibkrHostField.setVisible(false);
        ibkrHostField.setManaged(false);

        TextField ibkrPaperPortField = new TextField();
        styleInputField(ibkrPaperPortField, "Paper Port (default: 4002)");
        ibkrPaperPortField.setVisible(false);
        ibkrPaperPortField.setManaged(false);

        TextField ibkrLivePortField = new TextField();
        styleInputField(ibkrLivePortField, "Live Port (default: 4001)");
        ibkrLivePortField.setVisible(false);
        ibkrLivePortField.setManaged(false);

        TextField ibkrClientIdField = new TextField();
        styleInputField(ibkrClientIdField, "Client ID (default: 1)");
        ibkrClientIdField.setVisible(false);
        ibkrClientIdField.setManaged(false);

        styleInputField(telegramToken, "Telegram Bot Token (optional)");
        styleInputField(openAiField, "OpenAI API Key (optional)");

        loadRememberedExchangeCredentials(selectedExchangeName, apiKeyField, apiSecretField, accountIdField);
        loadSavedOptionalTokens();

        GridPane credGrid = formGrid();
        credGrid.addRow(0, createLabel(apiKeyLabel(selectedExchange)), apiKeyField);

        int row = 1;
        if (selectedExchange != SupportedExchange.OANDA && selectedExchange != SupportedExchange.SOLONA_NETWORK) {
            credGrid.addRow(row++,
                    createLabel(selectedExchange == SupportedExchange.STELLAR_NETWORK
                            ? "Secret Seed"
                            : "API Secret"),
                    apiSecretField);
        }
        if (showAccountId) {
            String accountLabel = selectedExchange == SupportedExchange.STELLAR_NETWORK || selectedExchange == SupportedExchange.SOLONA_NETWORK
                            ? "Public Account"
                            : "Account ID";
            credGrid.addRow(row++, createLabel(accountLabel), accountIdField);
        }

        credGrid.addRow(row++, createLabel("Telegram"), telegramToken);
        credGrid.addRow(row++, createLabel("OpenAI"), openAiField);
        credGrid.addRow(row, createLabel("Trading Mode"), selectedTradingModeChoiceBox);

        CheckBox rememberCredentialsCheckBox = new CheckBox("Remember credentials");
        rememberCredentialsCheckBox.setStyle(checkBoxStyle());
        rememberCredentialsCheckBox.setVisible(true);

        Button continueButton = createPrimaryButton("Continue");
        continueButton.setPrefWidth(138);
        Button backButton = createSecondaryButton("Back");
        backButton.setPrefWidth(138);
        Button helpButton = createSecondaryButton("Format Help");
        helpButton.setPrefWidth(138);
        helpButton.setVisible(true);
        helpButton.setManaged(true);

        Label validation = inlineValidationLabel();

        continueButton.setOnAction(event -> handleExchangeCredentials(
                selectedExchange,
                apiKeyField,
                apiSecretField,
                accountIdField,
                ibkrTwoFactorCodeField,
                ibkrClientPortalUrlField,
                ibkrHostField,
                ibkrPaperPortField,
                ibkrLivePortField,
                ibkrClientIdField,
                rememberCredentialsCheckBox,
                selectedExchangeName,
                validation));
        backButton.setOnAction(event -> showConfigurationStep());
        helpButton.setOnAction(event -> showHelpDialog(selectedExchange, getExchangeInfoText(selectedExchange)));

        Label title = new Label("Exchange Credentials");
        title.setStyle(titleStyle(30));

        Label subtitle = new Label(
                "Connect with paper trading first. Use live mode only after validating strategies and risk controls.");
        subtitle.setStyle(subtitleStyle());
        subtitle.setWrapText(true);

        Label info = createExchangeInfo(selectedExchange);

        ScrollPane credentialScroll = getScrollPane(credGrid, false);

        HBox buttonBox = new HBox(12, backButton, continueButton);
        buttonBox.getChildren().add(helpButton);
        buttonBox.setAlignment(Pos.CENTER);

        VBox card = new VBox(14, badge("SECURE BROKER CONNECTION"), title, subtitle, info, credentialScroll,
                rememberCredentialsCheckBox, buttonBox, validation);
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(540);
        card.setStyle(CARD_STYLE);

        fadeTo(createShell(card, false, null));
    }

    private void showIbkrControlPanelCredentialsStep(
            SupportedExchange selectedExchange,
            String selectedExchangeName) {
        TextField accountIdField = new TextField();
        styleInputField(accountIdField, "IBKR Account ID (optional for paper)");

        TextField clientPortalUrlField = new TextField();
        styleInputField(clientPortalUrlField, "Client Portal URL (optional)");

        TextField hostField = new TextField();
        styleInputField(hostField, "Host");

        TextField paperPortField = new TextField();
        styleInputField(paperPortField, "Paper port");

        TextField livePortField = new TextField();
        styleInputField(livePortField, "Live port");

        TextField clientIdField = new TextField();
        styleInputField(clientIdField, "Client ID");

        ChoiceBox<String> authModeChoiceBox = new ChoiceBox<>();
        authModeChoiceBox.getItems().setAll("TWS / IB Gateway", "Client Portal Gateway");
        authModeChoiceBox.setValue(loadSavedIbkrAuthModeDisplay());
        styleChoiceBox(authModeChoiceBox);

        loadRememberedIbkrAccount(selectedExchangeName, accountIdField);
        loadSavedIbkrSettings(clientPortalUrlField, hostField, paperPortField, livePortField, clientIdField);

        GridPane controlGrid = formGrid();
        int row = 0;
        controlGrid.addRow(row++, createLabel("Auth mode"), authModeChoiceBox);
        controlGrid.addRow(row++, createLabel("Account ID"), accountIdField);
        controlGrid.addRow(row++, createLabel("Client Portal URL"), clientPortalUrlField);
        controlGrid.addRow(row++, createLabel("Host"), hostField);
        controlGrid.addRow(row++, createLabel("Paper port"), paperPortField);
        controlGrid.addRow(row++, createLabel("Live port"), livePortField);
        controlGrid.addRow(row++, createLabel("Client ID"), clientIdField);
        controlGrid.addRow(row, createLabel("Trading Mode"), selectedTradingModeChoiceBox);

        Label title = new Label("Exchange Credentials");
        title.setStyle(titleStyle(30));

        Label subtitle = new Label(
                "Connect to your local IBKR Gateway session. The trading desk opens only after InvestPro verifies the selected gateway endpoint.");
        subtitle.setStyle(subtitleStyle());
        subtitle.setWrapText(true);

        Label info = createExchangeInfo(selectedExchange);
        Label validation = inlineValidationLabel();

        Button continueButton = createPrimaryButton("Connect");
        continueButton.setPrefWidth(138);
        Button backButton = createSecondaryButton("Back");
        backButton.setPrefWidth(138);

        continueButton.setOnAction(event -> handleIbkrControlPanelCredentials(
                selectedExchange,
                selectedExchangeName,
                authModeChoiceBox,
                accountIdField,
                clientPortalUrlField,
                hostField,
                paperPortField,
                livePortField,
                clientIdField,
                validation));
        backButton.setOnAction(event -> showConfigurationStep());

        HBox buttonBox = new HBox(12, backButton, continueButton);
        buttonBox.setAlignment(Pos.CENTER);

        ScrollPane credentialScroll = getScrollPane(controlGrid, true);
        VBox card = new VBox(14, badge("IBKR GATEWAY"), title, subtitle, info, credentialScroll, buttonBox,
                validation);
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(540);
        card.setStyle(CARD_STYLE);

        fadeTo(createShell(card, false, null));
    }

    private @NonNull ScrollPane getScrollPane(GridPane credGrid, boolean isIbkr) {
        ScrollPane credentialScroll = new ScrollPane(credGrid);
        credentialScroll.setFitToWidth(true);
        credentialScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        credentialScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        credentialScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        credentialScroll.setPannable(true);
        credentialScroll.setPrefViewportHeight(isIbkr ? 340 : 420);
        credentialScroll.setMaxHeight(isIbkr ? 360 : 430);
        return credentialScroll;
    }

    private String apiKeyPrompt(SupportedExchange selectedExchange) {
        if (selectedExchange == SupportedExchange.INTERACTIVE_BROKERS) {
            return "IBKR Username";
        }
        if (selectedExchange == SupportedExchange.OANDA) {
            return "OANDA Token (Bearer Token)";
        }
        if (selectedExchange == SupportedExchange.STELLAR_NETWORK) {
            return "Stellar public account ID (G...)";
        }
        if (selectedExchange == SupportedExchange.SOLONA_NETWORK) {
            return "Solona wallet address";
        }
        return "API Key";
    }

    private String apiKeyLabel(SupportedExchange selectedExchange) {
        if (selectedExchange == SupportedExchange.INTERACTIVE_BROKERS) {
            return "Username";
        }
        if (selectedExchange == SupportedExchange.OANDA) {
            return "Token";
        }
        if (selectedExchange == SupportedExchange.STELLAR_NETWORK) {
            return "Public Account";
        }
        if (selectedExchange == SupportedExchange.SOLONA_NETWORK) {
            return "Wallet Address";
        }
        return "API Key";
    }

    private void handleExchangeCredentials(SupportedExchange selectedExchange,
            TextField apiKeyField,
            PasswordField apiSecretField,
            TextField accountIdField,
            TextField ibkrTwoFactorCodeField,
            TextField ibkrClientPortalUrlField,
            TextField ibkrHostField,
            TextField ibkrPaperPortField,
            TextField ibkrLivePortField,
            TextField ibkrClientIdField,
            CheckBox rememberCheckBox,
            String selectedExchangeName,
            Label validation) {
        String apiKey = apiKeyField.getText().trim();
        String apiSecret = apiSecretField.getText().trim();
        String accountId = accountIdField.getText().trim();
        String ibkrTwoFactorCode = ibkrTwoFactorCodeField.getText().trim();
        String tradingMode = selectedTradingModeChoiceBox.getValue();

        if (tradingMode == null || tradingMode.isBlank()) {
            tradingMode = "PAPER";
        }

        if (selectedExchange == SupportedExchange.OANDA) {
            if (apiKey.isBlank()) {
                validation.setText("OANDA token is required.");
                return;
            }
            apiSecret = accountId;
        } else if (selectedExchange == SupportedExchange.INTERACTIVE_BROKERS) {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                validation.setText("IBKR username and password are required.");
                return;
            }

            String normalizedTradingMode = safe(tradingMode).toUpperCase(Locale.ROOT);
            if (("LIVE".equals(normalizedTradingMode) || "LIVE TRADING".equals(normalizedTradingMode))
                    && accountId.isBlank()) {
                validation.setText("IBKR Account ID is required for live trading mode.");
                return;
            }

            applyIbkrRuntimeProperties(ibkrClientPortalUrlField.getText(), ibkrHostField.getText(),
                    ibkrPaperPortField.getText(), ibkrLivePortField.getText(), ibkrClientIdField.getText());
        } else if (selectedExchange == SupportedExchange.STELLAR_NETWORK) {
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                validation.setText("Stellar public account and secret seed are required.");
                return;
            }
            accountId = apiKey;
        } else if (selectedExchange == SupportedExchange.SOLONA_NETWORK) {
            if (apiKey.isBlank()) {
                validation.setText("Solona wallet address is required.");
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

        AuthResult authResult = authenticateExchange(selectedExchange.getFactoryKey(), apiKey, apiSecret, accountId,
                ibkrTwoFactorCode, tradingMode, Map.of());
        if (!authResult.success()) {
            validation.setStyle("-fx-text-fill: " + DANGER + "; -fx-font-size: 11;");
            validation.setText(authResult.message());
            return;
        }

        validation.setStyle("-fx-text-fill: " + SUCCESS + "; -fx-font-size: 11;");
        validation.setText("Authentication successful!");

        if (rememberCheckBox.isSelected()) {
            saveRememberedExchangeCredentials(selectedExchangeName, apiKey, apiSecret, accountId,
                    telegramToken.getText().trim());
            if (selectedExchange == SupportedExchange.INTERACTIVE_BROKERS) {
                saveIbkrSettings(ibkrClientPortalUrlField.getText(), ibkrHostField.getText(),
                        ibkrPaperPortField.getText(), ibkrLivePortField.getText(), ibkrClientIdField.getText());
            }
        }

        saveConfiguration(configuration);
        showLoadingOverlay();
    }

    private void handleIbkrControlPanelCredentials(
            SupportedExchange selectedExchange,
            String selectedExchangeName,
            ChoiceBox<String> authModeChoiceBox,
            TextField accountIdField,
            TextField clientPortalUrlField,
            TextField hostField,
            TextField paperPortField,
            TextField livePortField,
            TextField clientIdField,
            Label validation) {
        String tradingMode = selectedTradingModeChoiceBox.getValue();
        if (tradingMode == null || tradingMode.isBlank()) {
            tradingMode = "PAPER TRADING";
        }

        String normalizedTradingMode = safe(tradingMode).toUpperCase(Locale.ROOT);
        String accountId = accountIdField.getText().trim();
        if (("LIVE".equals(normalizedTradingMode) || "LIVE TRADING".equals(normalizedTradingMode))
                && accountId.isBlank()) {
            validation.setText("IBKR Account ID is required for live trading mode.");
            return;
        }

        String authMode = ibkrAuthModeValue(authModeChoiceBox.getValue());
        String selectedPort = normalizedTradingMode.startsWith("LIVE")
                ? parseIntOrDefault(livePortField.getText(), 4001)
                : parseIntOrDefault(paperPortField.getText(), 4002);
        String sanitizedClientPortalUrl = sanitizeIbkrClientPortalUrl(clientPortalUrlField.getText());
        Map<String, String> ibkrParams = new LinkedHashMap<>();
        putCredential(ibkrParams, "IBKR_AUTH_MODE", authMode);
        putCredential(ibkrParams, "IBKR_ACCOUNT_ID", accountId);
        putCredential(ibkrParams, "IBKR_ENVIRONMENT", normalizedTradingMode.startsWith("LIVE") ? "live" : "paper");
        putCredential(ibkrParams, "IBKR_SANDBOX", String.valueOf(!normalizedTradingMode.startsWith("LIVE")));
        putCredential(ibkrParams, "IBKR_CLIENT_PORTAL_URL", sanitizedClientPortalUrl);
        putCredential(ibkrParams, "IBKR_HOST", hostField.getText());
        putCredential(ibkrParams, "IBKR_PORT", selectedPort);
        putCredential(ibkrParams, "IBKR_PAPER_PORT", parseIntOrDefault(paperPortField.getText(), 4002));
        putCredential(ibkrParams, "IBKR_LIVE_PORT", parseIntOrDefault(livePortField.getText(), 4001));
        putCredential(ibkrParams, "IBKR_CLIENT_ID", parseIntOrDefault(clientIdField.getText(), 1));
        putCredential(ibkrParams, "IBK_AUTH_MODE", authMode);
        putCredential(ibkrParams, "IBK_ACCOUNT_ID", accountId);
        putCredential(ibkrParams, "IBK_HOST", hostField.getText());
        putCredential(ibkrParams, "IBK_PORT", selectedPort);
        putCredential(ibkrParams, "IBK_CLIENT_ID", parseIntOrDefault(clientIdField.getText(), 1));

        applyIbkrRuntimeProperties(sanitizedClientPortalUrl, hostField.getText(), paperPortField.getText(),
                livePortField.getText(), clientIdField.getText(), authMode);

        configuration = new MarketConfiguration(
                usernameField.getText().trim(),
                marketTypeBox.getValue(),
                venueBox.getValue(),
                selectedExchange.getFactoryKey(),
                "",
                "",
                accountId,
                "",
                "",
                null,
                null,
                tradingMode);

        validation.setStyle("-fx-text-fill: " + WARNING + "; -fx-font-size: 11;");
        validation.setText("Connecting to IBKR session...");

        AuthResult gatewayCheck = validateIbkrGatewayEndpoint(authMode, hostField.getText(), selectedPort,
                sanitizedClientPortalUrl);
        if (!gatewayCheck.success()) {
            validation.setStyle("-fx-text-fill: " + DANGER + "; -fx-font-size: 11;");
            validation.setText(gatewayCheck.message());
            return;
        }

        AuthResult authResult = authenticateExchange(selectedExchange.getFactoryKey(), "", "", accountId,
                "", tradingMode, ibkrParams);
        if (!authResult.success()) {
            validation.setStyle("-fx-text-fill: " + DANGER + "; -fx-font-size: 11;");
            validation.setText(authResult.message());
            return;
        }

        validation.setStyle("-fx-text-fill: " + SUCCESS + "; -fx-font-size: 11;");
        validation.setText("IBKR session connected.");

        saveRememberedIbkrAccount(selectedExchangeName, accountId);
        saveIbkrSettings(sanitizedClientPortalUrl, hostField.getText(), paperPortField.getText(),
                livePortField.getText(), clientIdField.getText(), authModeChoiceBox.getValue());
        saveConfiguration(configuration);
        showLoadingOverlay();
    }

    private @NotNull Label createExchangeInfo(SupportedExchange exchange) {
        Label info = new Label(getExchangeInfoText(exchange));
        info.setStyle("""
                -fx-font-size: 11px;
                -fx-text-fill: #94a3b8;
                -fx-padding: 12;
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
        if (selectedExchange == SupportedExchange.INTERACTIVE_BROKERS) {
            return """
                    Interactive Brokers
                    Username and Password: used for your profile and runtime settings
                    Authentication flow: sign in through the local Gateway browser page and complete 2FA there
                    Brokerage session: initialized through /iserver/auth/ssodh/init after browser login
                    Account ID: required for live order routing
                    Client Portal URL: defaults to https://localhost:5000/v1/api
                    Gateway Host/Ports/Client ID: configure TWS or IB Gateway socket session
                    Paper mode note: use your dedicated paper username for paper login
                    Tip: if a competing session exists (TWS/Mobile), close it before API trading.""";
        }
        if (selectedExchange == SupportedExchange.STELLAR_NETWORK) {
            return """
                    Stellar Network
                    Public Account: G... account ID
                    Secret Seed: S... secret seed
                    Tip: XLM is native and issued assets require trusted issuers.""";
        }
        if (selectedExchange == SupportedExchange.SOLONA_NETWORK) {
            return """
                    Solona Network
                    Wallet Address: public base-58 wallet address
                    RPC: defaults to mainnet unless solona.network or solona.rpcUrl is configured
                    Tip: this adapter supports RPC connectivity and balances; swap trading remains disabled.""";
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
        styleInputField(apiSecretField,
                selectedExchange == SupportedExchange.STELLAR_NETWORK
                        ? "Secret Seed (S...)"
                        : selectedExchange == SupportedExchange.INTERACTIVE_BROKERS
                                ? "IBKR Password"
                                : "API Secret");
        boolean visible = selectedExchange != SupportedExchange.OANDA
                && selectedExchange != SupportedExchange.SOLONA_NETWORK;
        apiSecretField.setVisible(visible);
        apiSecretField.setManaged(visible);
        return apiSecretField;
    }

    private void showLoadingOverlay() {
        if (!launchTransitionStarted.compareAndSet(false, true)) {
            return;
        }

        Label overlayStatus = new Label("Saving settings...");
        overlayStatus.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 13;");

        ProgressBar overlayProgress = new ProgressBar(0);
        overlayProgress.setPrefWidth(430);
        overlayProgress.setStyle("-fx-accent: " + ACCENT + ";");

        VBox overlay = buildOverlayBox(overlayStatus, overlayProgress);

        StackPane loadingPane = new StackPane(overlay);
        loadingPane.setStyle("-fx-background-color: rgba(2, 6, 23, 0.78);");
        activeLoadingOverlay = loadingPane;
        getChildren().add(loadingPane);

        Timeline timeline = new Timeline(
                frameFor(overlayStatus, overlayProgress, 0.20, "Saving configuration..."),
                frameFor(overlayStatus, overlayProgress, 0.42, "Connecting to market venue..."),
                frameFor(overlayStatus, overlayProgress, 0.65, "Loading exchange instruments..."),
                frameFor(overlayStatus, overlayProgress, 0.84, "Preparing trading workstation..."),
                frameFor(overlayStatus, overlayProgress, 1.0, "Market data is ready."));
        timeline.setOnFinished(event -> {
            PauseTransition pause = new PauseTransition(Duration.millis(420));
            pause.setOnFinished(pauseEvent -> dispatchReadyTransition());
            pause.play();
        });
        timeline.play();
    }

    private void dispatchReadyTransition() {
        if (configuration == null) {
            resetLoadingState("Configuration is missing. Please try again.");
            return;
        }

        Runnable transition = () -> {
            try {
                onReady.accept(configuration);
            } catch (Exception exception) {
                log.error("Failed to open trading desk from onboarding", exception);
                resetLoadingState("Failed to open trading desk. Please try again.");
            }
        };

        if (Platform.isFxApplicationThread()) {
            transition.run();
        } else {
            Platform.runLater(transition);
        }
    }

    private void resetLoadingState(String message) {
        if (message != null && !message.isBlank()) {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: " + DANGER + "; -fx-font-size: 12;");
        }

        if (activeLoadingOverlay != null) {
            getChildren().remove(activeLoadingOverlay);
            activeLoadingOverlay = null;
        }

        launchTransitionStarted.set(false);
    }

    private static @NonNull VBox buildOverlayBox(Label overlayStatus, ProgressBar overlayProgress) {
        Label title = new Label("Preparing Terminal");
        title.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 18px; -fx-font-weight: 900;");

        Label subtitle = new Label("InvestPro is loading your workspace.");
        subtitle.setStyle("-fx-text-fill: " + MUTED + "; -fx-font-size: 12;");

        VBox overlay = new VBox(16, title, subtitle, overlayStatus, overlayProgress);
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
        return overlay;
    }

    private static @NotNull KeyFrame frameFor(Label label, ProgressBar bar, double progress, String message) {
        return new KeyFrame(Duration.millis(2400 * progress),
                event -> label.setText(message),
                new KeyValue(bar.progressProperty(), progress));
    }

    private void showForgotPasswordDialog(Label validation) {
        TextInputDialog lookupDialog = new TextInputDialog(usernameField.getText());

        lookupDialog.setTitle("Forgot Your Password?");
        lookupDialog.setHeaderText("Find your InvestPro account");
        lookupDialog.setContentText("Username or email:");
        lookupDialog.setGraphic(null);

        DialogPane dialogPane = lookupDialog.getDialogPane();
        dialogPane.setPrefSize(420, 260);
        dialogPane.setStyle("""
                -fx-background-color: #0f172a;
                -fx-text-fill: #e2e8f0;
                """);

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
                    AuthResult result = authService.resetPassword(accountField.getText(), tokenField.getText(),
                            password);
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
        Preferences preferences = Preferences.userNodeForPackage(OnboardingDesk.class);
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

    @Contract(value = "!null -> param1", pure = true)
    private @NonNull String safe(String value) {
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

    private void applySavedConfigurationSelection() {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingDesk.class);

        String savedMarketType = preferences.get("marketType", "Crypto");
        String savedVenue = preferences.get("venue", "US");
        String savedExchange = preferences.get("exchange", SupportedExchange.COINBASE.getFactoryKey());

        marketTypeBox.getSelectionModel().select(
                marketTypeBox.getItems().contains(savedMarketType) ? savedMarketType : "Crypto");
        venueBox.getSelectionModel().select(
                venueBox.getItems().contains(savedVenue) ? savedVenue : "US");

        String savedExchangeDisplayName = resolveExchangeDisplayName(savedExchange);
        exchangeBox.getSelectionModel().select(
                exchangeBox.getItems().contains(savedExchangeDisplayName)
                        ? savedExchangeDisplayName
                        : SupportedExchange.COINBASE.getDisplayName());
    }

    private String resolveExchangeDisplayName(String savedExchange) {
        if (savedExchange == null || savedExchange.isBlank()) {
            return SupportedExchange.COINBASE.getDisplayName();
        }

        try {
            return SupportedExchange.fromFactoryKey(savedExchange).getDisplayName();
        } catch (IllegalArgumentException ignored) {
            try {
                return SupportedExchange.fromDisplayName(savedExchange).getDisplayName();
            } catch (IllegalArgumentException ignoredAgain) {
                return SupportedExchange.COINBASE.getDisplayName();
            }
        }
    }

    private @NonNull String loadSavedTradingMode() {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingDesk.class);
        String savedTradingMode = preferences.get("tradingMode", "PAPER TRADING");
        if ("LIVE".equalsIgnoreCase(savedTradingMode)) {
            return "LIVE";
        }
        return "PAPER TRADING";
    }

    private void loadSavedOptionalTokens() {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingDesk.class);

        if (telegramToken.getText() == null || telegramToken.getText().isBlank()) {
            telegramToken.setText(preferences.get("telegramToken", ""));
        }

        if (openAiField.getText() == null || openAiField.getText().isBlank()) {
            openAiField.setText(preferences.get("open_ai_api_key", ""));
        }
    }

    private void saveRememberedExchangeCredentials(String exchange, String apiKey, String apiSecret, String accountId,
            String token) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingDesk.class);
        preferences.put("exchange_api_key_%s".formatted(exchange), safe(apiKey));
        preferences.put("exchange_api_secret_%s".formatted(exchange), safe(apiSecret));
        preferences.put("exchange_account_id_%s".formatted(exchange), safe(accountId));
        preferences.put("telegram_token_%s".formatted(exchange), safe(token));
        flushPreferences(preferences);
    }

    private void loadRememberedExchangeCredentials(String exchange,
            @NonNull TextField apiKeyField,
            @NonNull PasswordField apiSecretField,
            @NonNull TextField accountIdField) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingDesk.class);
        apiKeyField.setText(preferences.get("exchange_api_key_%s".formatted(exchange), ""));
        apiSecretField.setText(preferences.get("exchange_api_secret_%s".formatted(exchange), ""));
        accountIdField.setText(preferences.get("exchange_account_id_%s".formatted(exchange), ""));
        telegramToken.setText(preferences.get("telegram_token_%s".formatted(exchange), ""));
    }

    private void saveRememberedIbkrAccount(String exchange, String accountId) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingDesk.class);
        preferences.put("exchange_account_id_%s".formatted(exchange), safe(accountId));
        flushPreferences(preferences);
    }

    private void loadRememberedIbkrAccount(String exchange, @NonNull TextField accountIdField) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingDesk.class);
        accountIdField.setText(preferences.get("exchange_account_id_%s".formatted(exchange), ""));
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
            String twoFactorCode,
            String tradingMode,
            Map<String, String> exchangeParams) {
        String exchangeId = normalizeExchangeId(selectedExchange);

        CredentialProvider credentialProvider = createCredentialProvider(exchangeId, apiKey, apiSecret, accountId,
                twoFactorCode, tradingMode, exchangeParams);
        ExchangeFactory exchangeFactory = new ExchangeFactory(credentialProvider);

        Exchange exchange = exchangeFactory.create(exchangeId);
        exchange.setUserSelectedTradingMode(tradingMode);
        exchange.connect();

        return exchange;
    }



    private @NotNull CredentialProvider createCredentialProvider(String exchangeId,
            String apiKey,
            String apiSecret,
            String accountId,
            String twoFactorCode,
            String tradingMode,
            Map<String, String> exchangeParams) {
        try {
            Class<?> providerClass = Class.forName("org.investpro.exchange.providers.UiCredentialProvider");
            Object instance = providerClass
                    .getConstructor(String.class, String.class, String.class, String.class, String.class,
                            String.class, Map.class)
                    .newInstance(exchangeId, apiKey, apiSecret, accountId, tradingMode, twoFactorCode,
                            exchangeParams == null ? Map.of() : exchangeParams);
            if (instance instanceof CredentialProvider credentialProvider) {
                return credentialProvider;
            }
        } catch (Throwable throwable) {
            log.warn("UiCredentialProvider is unavailable at runtime; using onboarding fallback provider");
        }

        Map<String, String> values = new LinkedHashMap<>();
        String prefix = normalizeCredentialPrefix(exchangeId);

        putCredential(values, prefix + "_API_KEY", apiKey);
        putCredential(values, prefix + "_API_SECRET", apiSecret);
        putCredential(values, prefix + "_ACCOUNT_ID", accountId);
        putCredential(values, prefix + "_TWO_FACTOR_CODE", twoFactorCode);
        putCredential(values, prefix + "_TRADING_MODE", tradingMode);
        if (exchangeParams != null) {
            exchangeParams.forEach((key, value) -> putCredential(values, key, value));
        }

        switch (prefix) {
            case "coinbase" -> {
                putCredential(values, "COINBASE_API_KEY", apiKey);
                putCredential(values, "COINBASE_API_SECRET", apiSecret);
                putCredential(values, "COINBASE_KEY_NAME", apiKey);
                putCredential(values, "COINBASE_PRIVATE_KEY", apiSecret);
            }
            case "binance" -> {
                putCredential(values, "BINANCE_API_KEY", apiKey);
                putCredential(values, "BINANCE_API_SECRET", apiSecret);
            }
            case "binance_us" -> {
                putCredential(values, "BINANCE_US_API_KEY", apiKey);
                putCredential(values, "BINANCE_US_API_SECRET", apiSecret);
            }
            case "oanda" -> {
                putCredential(values, "OANDA_API_KEY", apiKey);
                putCredential(values, "OANDA_API_SECRET", apiSecret);
                putCredential(values, "OANDA_ACCOUNT_ID", accountId);
            }
            case "alpaca" -> {
                putCredential(values, "ALPACA_API_KEY", apiKey);
                putCredential(values, "ALPACA_API_SECRET", apiSecret);
            }
            case "bitfinex" -> {
                putCredential(values, "BITFINEX_API_KEY", apiKey);
                putCredential(values, "BITFINEX_API_SECRET", apiSecret);
            }
            case "interactive_brokers" -> {
                putCredential(values, "IBKR_API_KEY", apiKey);
                putCredential(values, "IBKR_API_SECRET", apiSecret);
                putCredential(values, "IBKR_ACCOUNT_ID", accountId);
                putCredential(values, "IBKR_USERNAME", apiKey);
                putCredential(values, "IBKR_PASSWORD", apiSecret);
                putCredential(values, "IBKR_TWO_FACTOR_CODE", twoFactorCode);
                putCredential(values, "IBKR_CLIENT_PORTAL_URL", firstParam(exchangeParams,
                        "IBKR_CLIENT_PORTAL_URL", System.getProperty("investpro.ibkr.clientPortalUrl")));
                putCredential(values, "IBKR_HOST", firstParam(exchangeParams,
                        "IBKR_HOST", System.getProperty("investpro.ibkr.host")));
                putCredential(values, "IBKR_PORT", firstParam(exchangeParams,
                        "IBKR_PORT", System.getProperty("investpro.ibkr.port")));
                putCredential(values, "IBKR_PAPER_PORT", firstParam(exchangeParams,
                        "IBKR_PAPER_PORT", System.getProperty("investpro.ibkr.paperPort")));
                putCredential(values, "IBKR_LIVE_PORT", firstParam(exchangeParams,
                        "IBKR_LIVE_PORT", System.getProperty("investpro.ibkr.livePort")));
                putCredential(values, "IBKR_CLIENT_ID", firstParam(exchangeParams,
                        "IBKR_CLIENT_ID", System.getProperty("investpro.ibkr.clientId")));
                putCredential(values, "IBKR_AUTH_MODE", firstParam(exchangeParams,
                        "IBKR_AUTH_MODE", System.getProperty("investpro.ibkr.authMode")));
                putCredential(values, "IBKR_ENVIRONMENT", firstParam(exchangeParams,
                        "IBKR_ENVIRONMENT", tradingMode));
                putCredential(values, "IBKR_SANDBOX", firstParam(exchangeParams,
                        "IBKR_SANDBOX", String.valueOf(!"LIVE".equalsIgnoreCase(safe(tradingMode)))));
                putCredential(values, "IBK_API_KEY", apiKey);
                putCredential(values, "IBK_API_SECRET", apiSecret);
                putCredential(values, "IBK_ACCOUNT_ID", accountId);
                putCredential(values, "IBK_USERNAME", apiKey);
                putCredential(values, "IBK_PASSWORD", apiSecret);
                putCredential(values, "IBK_TWO_FACTOR_CODE", twoFactorCode);
                putCredential(values, "IBK_AUTH_MODE", firstParam(exchangeParams,
                        "IBK_AUTH_MODE", values.get("IBKR_AUTH_MODE")));
                putCredential(values, "IBK_HOST", firstParam(exchangeParams, "IBK_HOST", values.get("IBKR_HOST")));
                putCredential(values, "IBK_PORT", firstParam(exchangeParams, "IBK_PORT", values.get("IBKR_PORT")));
                putCredential(values, "IBK_CLIENT_ID", firstParam(exchangeParams,
                        "IBK_CLIENT_ID", values.get("IBKR_CLIENT_ID")));
            }
            case "stellar_network", "stellar" -> {
                putCredential(values, "STELLAR_PUBLIC_KEY",
                        accountId != null && !accountId.isBlank() ? accountId : apiKey);
                putCredential(values, "STELLAR_SECRET_KEY", apiSecret);
                putCredential(values, "STELLAR_NETWORK", tradingMode);
            }

            case "schwab"->  {
                putCredential(values, "SCHWAB_API_KEY", apiKey);
            putCredential(values, "SCHWAB_API_SECRET", apiSecret);
            }

            default -> {
                // No additional aliases required.
            }
        }

        return key -> {
            if (key == null || key.isBlank()) {
                return Optional.empty();
            }
            String value = values.get(key);
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
        };
    }

    private String normalizeCredentialPrefix(String exchangeId) {
        String normalized = exchangeId == null ? ""
                : exchangeId.trim().toLowerCase(Locale.ROOT)
                        .replace('-', '_')
                        .replaceAll("[^a-z0-9_]", "");

        return switch (normalized) {
            case "binanceus", "binance_us" -> "binance_us";
            case "interactivebrokers", "interactive_brokers", "ibkr", "ibk" -> "interactive_brokers";
            case "stellarnetwork", "stellar_network" -> "stellar_network";
            default -> normalized;
        };
    }

    private void putCredential(Map<String, String> values, String key, String value) {
        if (key != null && value != null && !value.isBlank()) {
            values.put(key, value.trim());
        }
    }

    private String firstParam(Map<String, String> values, String key, String fallback) {
        if (values == null || key == null) {
            return fallback;
        }
        String value = values.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }



    private @NotNull AuthResult authenticateExchange(String selectedExchange,
            String apiKey,
            String apiSecret,
            String accountId,
            String twoFactorCode,
            String tradingMode,
            Map<String, String> exchangeParams) {
        try {
            Exchange exchange = createExchange(selectedExchange, apiKey, apiSecret, accountId, twoFactorCode,
                    tradingMode, exchangeParams);
            AuthResult authResult = exchange.AuthCheckResult(selectedExchange);
            if (authResult != null && !authResult.success()) {
                return authResult;
            }

            AuthResult accountValidation = validateAccountAccess(exchange, selectedExchange);
            if (!accountValidation.success()) {
                return accountValidation;
            }

            if (authResult != null) {
                return authResult;
            }

            if (Boolean.TRUE.equals(exchange.isConnected())) {
                return AuthResult.success("%s connected successfully.".formatted(selectedExchange));
            }
            return AuthResult.failure("%s did not confirm a connection.".formatted(selectedExchange));
        } catch (Throwable throwable) {
            log.warn("Broker authentication failed for {}", selectedExchange, throwable);
            return AuthResult
                    .failure("Authentication failed for %s: %s".formatted(selectedExchange, rootMessage(throwable)));
        }
    }

    private @NotNull AuthResult validateAccountAccess(@NotNull Exchange exchange, String selectedExchange) {
        CompletableFuture<Account> accountFuture = exchange.fetchAccount();
        if (accountFuture == null) {
            return AuthResult.failure(
                    "Authentication failed for %s: broker adapter cannot validate account access yet."
                            .formatted(selectedExchange));
        }

        try {
            Account account = accountFuture.orTimeout(20, TimeUnit.SECONDS).join();
            if (account == null) {
                return AuthResult.failure(
                        "Authentication failed for %s: broker returned no account data.".formatted(selectedExchange));
            }
            return AuthResult.success("Account access verified.");
        } catch (RuntimeException exception) {
            String detail = rootMessage(exception);
            String normalizedDetail = detail.toLowerCase(Locale.ROOT);
            if ("interactive-brokers".equalsIgnoreCase(selectedExchange)
                    && normalizedDetail.contains("unable to verify ibkr authentication status")) {
                detail = detail
                        + " Check Client Portal Gateway login in browser and confirm the Client Portal URL (localhost vs 127.0.0.1).";
            }
            if ("interactive-brokers".equalsIgnoreCase(selectedExchange)
                    && (normalizedDetail.contains("closedchannelexception")
                            || normalizedDetail.contains("tls handshake")
                            || normalizedDetail.contains("ssl"))) {
                detail = "Client Portal Gateway closed the connection during authentication. "
                        + "Open https://localhost:5000, complete login/2FA, and retry. "
                        + "If it is already open, restart Client Portal Gateway and retry.";
            }
            return AuthResult.failure("Authentication failed for %s: %s".formatted(selectedExchange, detail));
        }
    }

    private @NonNull String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return current == null ? "Unknown error" : current.getClass().getSimpleName();
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

        return switch (normalized) {
            case "binance" -> "binance";
            case "binanceus", "binance_us", "binance_us_spot" -> "binance-us";
            case "coinbase", "coinbaseadvanced", "coinbase_advanced",
                    "coinbaseadvancedtrade",
                    "coinbase_advanced_trade", "coinbasepro", "coinbase_pro" ->
                "coinbase";
            case "oanda", "oanda_fx", "oanda_forex" -> "oanda";
            case "alpaca", "alpaca_stocks", "alpaca_equities" -> "alpaca";
            case "bitfinex" -> "bitfinex";
            case "bitfinexus", "bitfinex_us" -> "bitfinex-us";
            case "interactivebrokers", "interactive_brokers", "ibkr", "ibk" -> "interactive-brokers";
            case "kraken" -> "kraken";
            case "ig", "bittrex", "bitmex", "kucoin", "kucoinus", "kucoin_us", "bitstamp", "poloniex" ->
                normalized.replace("_", "-");
            case "stellar", "stellar_network", "stellarnetwork" -> "stellar-network";
            case "solona", "solona_network", "solonanetwork", "sol" -> "solona-network";
            default -> throw new IllegalArgumentException("Unsupported exchange: " + value);
        };

    }

    private void applyIbkrRuntimeProperties(String clientPortalUrl,
            String host,
            String paperPort,
            String livePort,
            String clientId) {
        applyIbkrRuntimeProperties(clientPortalUrl, host, paperPort, livePort, clientId, null);
    }

    private void applyIbkrRuntimeProperties(String clientPortalUrl,
            String host,
            String paperPort,
            String livePort,
            String clientId,
            String authMode) {
        String sanitizedClientPortalUrl = sanitizeIbkrClientPortalUrl(clientPortalUrl);
        putSystemPropertyIfNotBlank("investpro.ibkr.clientPortalUrl", sanitizedClientPortalUrl);
        putSystemPropertyIfNotBlank("investpro.ibkr.host", host);
        putSystemPropertyIfNotBlank("investpro.ibkr.paperPort", parseIntOrDefault(paperPort, 4002));
        putSystemPropertyIfNotBlank("investpro.ibkr.livePort", parseIntOrDefault(livePort, 4001));
        putSystemPropertyIfNotBlank("investpro.ibkr.clientId", parseIntOrDefault(clientId, 1));
        putSystemPropertyIfNotBlank("investpro.ibkr.authMode", authMode);
    }

    private void saveIbkrSettings(String clientPortalUrl, String host, String paperPort, String livePort,
            String clientId) {
        saveIbkrSettings(clientPortalUrl, host, paperPort, livePort, clientId, "TWS / IB Gateway");
    }

    private void saveIbkrSettings(String clientPortalUrl, String host, String paperPort, String livePort,
            String clientId, String authModeDisplay) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingDesk.class);
        preferences.put("ibkr_client_portal_url", safe(sanitizeIbkrClientPortalUrl(clientPortalUrl)));
        preferences.put("ibkr_host", safe(host));
        preferences.put("ibkr_paper_port", parseIntOrDefault(paperPort, 4002));
        preferences.put("ibkr_live_port", parseIntOrDefault(livePort, 4001));
        preferences.put("ibkr_client_id", parseIntOrDefault(clientId, 1));
        preferences.put("ibkr_auth_mode", safe(authModeDisplay));
        flushPreferences(preferences);
    }

    private void loadSavedIbkrSettings(TextField clientPortalUrlField, TextField hostField, TextField paperPortField,
            TextField livePortField, TextField clientIdField) {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingDesk.class);

        clientPortalUrlField.setText(preferences.get("ibkr_client_portal_url", ""));
        hostField.setText(preferences.get("ibkr_host", "127.0.0.1"));
        paperPortField.setText(preferences.get("ibkr_paper_port", "4002"));
        livePortField.setText(preferences.get("ibkr_live_port", "4001"));
        clientIdField.setText(preferences.get("ibkr_client_id", "1"));

        applyIbkrRuntimeProperties(clientPortalUrlField.getText(), hostField.getText(), paperPortField.getText(),
                livePortField.getText(), clientIdField.getText(), ibkrAuthModeValue(loadSavedIbkrAuthModeDisplay()));
    }

    private String loadSavedIbkrAuthModeDisplay() {
        Preferences preferences = Preferences.userNodeForPackage(OnboardingDesk.class);
        String saved = preferences.get("ibkr_auth_mode", "TWS / IB Gateway");
        return "Client Portal Gateway".equalsIgnoreCase(saved) ? "Client Portal Gateway" : "TWS / IB Gateway";
    }

    private String ibkrAuthModeValue(String displayValue) {
        if ("Client Portal Gateway".equalsIgnoreCase(safe(displayValue))) {
            return "client-portal";
        }
        return "gateway";
    }

    private AuthResult validateIbkrGatewayEndpoint(
            String authMode,
            String host,
            String port,
            String clientPortalUrl) {
        if ("client-portal".equalsIgnoreCase(authMode)) {
            URI uri = parseUriOrNull(firstNonBlank(clientPortalUrl, "https://localhost:5000/v1/api"));
            String portalHost = uri == null || uri.getHost() == null ? "localhost" : uri.getHost();
            int portalPort = uri == null || uri.getPort() <= 0 ? 5000 : uri.getPort();
            if (canOpenSocket(portalHost, portalPort)) {
                return AuthResult.success("Client Portal Gateway endpoint is reachable.");
            }
            return AuthResult.failure("""
                    IBKR Client Portal Gateway is not reachable at %s:%d.
                    Start IBKR Client Portal Gateway, open https://localhost:5000 in a browser, sign in, complete 2FA, then retry.
                    Keep Client Portal URL as https://localhost:5000/v1/api unless you changed the gateway port.""".formatted(
                    portalHost,
                    portalPort));
        }

        String gatewayHost = firstNonBlank(host, "127.0.0.1");
        int gatewayPort = parsePositiveInt(port);
        if (canOpenSocket(gatewayHost, gatewayPort)) {
            return AuthResult.success("IB Gateway endpoint is reachable.");
        }
        return AuthResult.failure("""
                IB Gateway/TWS is not reachable at %s:%d.
                Start TWS or IB Gateway, log in to the correct paper/live account, enable API socket clients, and confirm the port.
                Default IB Gateway ports are 4002 for paper and 4001 for live. TWS often uses 7497 for paper and 7496 for live.""".formatted(
                gatewayHost,
                gatewayPort));
    }

    private boolean canOpenSocket(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(firstNonBlank(host, "127.0.0.1"), port), 1500);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private URI parseUriOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URI.create(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(safe(value).trim());
            return parsed > 0 ? parsed : 4002;
        } catch (NumberFormatException exception) {
            return 4002;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String parseIntOrDefault(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return String.valueOf(fallback);
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return String.valueOf(parsed > 0 ? parsed : fallback);
        } catch (NumberFormatException ignored) {
            return String.valueOf(fallback);
        }
    }

    private String sanitizeIbkrClientPortalUrl(String clientPortalUrl) {
        if (clientPortalUrl == null || clientPortalUrl.isBlank()) {
            return "";
        }

        String normalized = clientPortalUrl.trim();
        try {
            URI uri = URI.create(normalized);
            int port = uri.getPort();
            if (port == 4001 || port == 4002) {
                log.warn("Ignoring IBKR Client Portal URL '{}' because {} is a socket API port. "
                        + "Use https://localhost:5000/v1/api for Client Portal.", normalized, port);
                return "";
            }
            return normalized;
        } catch (Exception exception) {
            log.warn("Ignoring invalid IBKR Client Portal URL '{}'", normalized);
            return "";
        }
    }

    private void putSystemPropertyIfNotBlank(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        System.setProperty(key, value.trim());
    }
}
