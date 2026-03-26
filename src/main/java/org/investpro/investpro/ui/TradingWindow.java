package org.investpro.investpro.ui;

import jakarta.persistence.Query;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.investpro.investpro.AppFiles;
import org.investpro.investpro.CurrencyDataProvider;
import org.investpro.investpro.DatabaseConfiguration;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.ExchangeCredentialValidator;
import org.investpro.investpro.Messages;
import org.investpro.investpro.exchanges.BinanceUS;
import org.investpro.investpro.exchanges.Coinbase;
import org.investpro.investpro.exchanges.Oanda;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.investpro.investpro.InvestPro.CONFIG_FILE;
import static org.investpro.investpro.InvestPro.CONFIG_FILE2;
import static org.investpro.investpro.InvestPro.db1;
import static org.investpro.investpro.InvestPro.reinitializeDatabase;

public class TradingWindow extends StackPane {

    private static final Logger logger = LoggerFactory.getLogger(TradingWindow.class);
    private static final String STATUS_INFO = "status-info";
    private static final String STATUS_SUCCESS = "status-success";
    private static final String STATUS_WARNING = "status-warning";
    private static final String STATUS_ERROR = "status-error";

    private final ComboBox<String> comboBox = new ComboBox<>();
    private final TextField apiKeyTextField = new TextField();
    private final PasswordField secretKeyTextField = new PasswordField();
    private final PasswordField passphraseTextField = new PasswordField();
    private final TextField tokensField = new TextField();
    private final Label exchangeStatusLabel = createStatusBanner();
    private Properties properties = loadProperties();
    private Exchange exchange;

    public TradingWindow() {
        getStyleClass().add("app-shell");
        setPadding(new Insets(24));

        tokensField.setText(properties.getProperty(
                "TELEGRAM_BOT_TOKEN",
                System.getenv().getOrDefault("TELEGRAM_BOT_TOKEN", "")
        ));

        loginPage();
    }

    private static void saveProperties2(@NotNull Properties properties) {
        AppFiles.storeProperties(properties, CONFIG_FILE2, "User settings");
    }

    private static void saveProperties(@NotNull Properties properties) {
        AppFiles.storeProperties(properties, CONFIG_FILE, "User settings");
    }

    private static @NotNull Properties loadProperties2() {
        return AppFiles.loadProperties(CONFIG_FILE2, "config2.properties");
    }

    private static @NotNull Properties loadProperties() {
        return AppFiles.loadProperties(CONFIG_FILE, "config.properties");
    }

    private void loginPage() {
        Label databaseStatusLabel = createStatusBanner();
        updateDatabaseStatusLabel(databaseStatusLabel);

        TextField loginUsernameField = createTextField("Username");
        PasswordField loginPasswordField = createPasswordField("Password");

        if (!properties.getProperty("LOGIN_USERNAME", "").isEmpty()
                && !properties.getProperty("LOGIN_PASSWORD", "").isEmpty()) {
            loginUsernameField.setText(properties.getProperty("LOGIN_USERNAME"));
            loginPasswordField.setText(properties.getProperty("LOGIN_PASSWORD"));
        }

        CheckBox rememberMeCheck = new CheckBox("Remember local account");
        rememberMeCheck.setSelected(!loginUsernameField.getText().isBlank());
        rememberMeCheck.setOnAction(event -> {
            if (rememberMeCheck.isSelected()) {
                properties.setProperty("LOGIN_USERNAME", loginUsernameField.getText());
                properties.setProperty("LOGIN_PASSWORD", loginPasswordField.getText());
            } else {
                properties.setProperty("LOGIN_USERNAME", "");
                properties.setProperty("LOGIN_PASSWORD", "");
            }
            saveProperties(properties);

            try {
                CurrencyDataProvider.registerCurrencies();
            } catch (Exception e) {
                logger.error("Unable to refresh currencies", e);
                new Messages(
                        Alert.AlertType.WARNING,
                        "Your login settings were saved, but currency sync failed. You can keep using the app."
                );
            }
        });

        Button loginSubmitButton = createPrimaryButton("Continue to Desk");
        loginSubmitButton.setDefaultButton(true);
        loginSubmitButton.setOnAction(event ->
                handleLogin(loginUsernameField.getText(), loginPasswordField.getText()));

        Button signUpButton = createSecondaryButton("Create Account");
        signUpButton.setOnAction(event -> createSignUpPage());

        Button forgotPasswordButton = createGhostButton("Forgot password");
        forgotPasswordButton.setOnAction(event -> resetPasswordPage());

        Button databaseSettingsButton = createGhostButton("Database Settings");
        databaseSettingsButton.setOnAction(event -> showDatabaseSettingsDialog(databaseStatusLabel));

        HBox actions = new HBox(12, signUpButton, forgotPasswordButton, loginSubmitButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        HBox databaseRow = new HBox(10, databaseStatusLabel, databaseSettingsButton);
        databaseRow.getStyleClass().add("inline-row");

        VBox card = createCard(
                "Sign in to InvestPro",
                "Use your local InvestPro account. The app can run on its embedded database by default or connect to your own JDBC database when you want full control.",
                createField("Username", loginUsernameField),
                createField("Password", loginPasswordField),
                rememberMeCheck,
                actions,
                databaseRow
        );

        showPage(createShell(
                "AI MULTI-EXCHANGE DESK",
                "Trade, analyze, and monitor from one command center.",
                "A Sopotek-inspired dark workspace for charts, market depth, account monitoring, and AI-assisted workflows.",
                card,
                "Local embedded storage is ready immediately.",
                "Binance US, OANDA, and Coinbase Advanced Trade are available.",
                "Each chart includes candlesticks, depth, market info, and order book views."
        ));
    }

    private void resetPasswordPage() {
        TextField emailField = createTextField("name@example.com");
        PasswordField newPasswordField = createPasswordField("New password");

        Button resetSubmitButton = createPrimaryButton("Reset Password");
        resetSubmitButton.setOnAction(event -> handlePasswordReset(emailField.getText(), newPasswordField.getText()));

        Button goBackButton = createGhostButton("Back");
        goBackButton.setOnAction(event -> loginPage());

        HBox actions = new HBox(12, goBackButton, resetSubmitButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = createCard(
                "Reset your password",
                "We will update the local InvestPro account stored in your selected database.",
                createField("Email", emailField),
                createField("New Password", newPasswordField),
                actions
        );

        showPage(createShell(
                "ACCOUNT RECOVERY",
                "Restore access without leaving the trading desk.",
                "Use this flow when you need to rotate a local InvestPro password before reconnecting exchanges and data services.",
                card,
                "Works with the local embedded database or your custom JDBC database.",
                "Keeps your exchange credentials separate from your local app account."
        ));
    }

    private void createSignUpPage() {
        TextField signUpUsernameField = createTextField("Choose a username");
        PasswordField signUpPasswordField = createPasswordField("Choose a password");
        TextField emailField = createTextField("name@example.com");

        Button signUpSubmitButton = createPrimaryButton("Create Account");
        signUpSubmitButton.setOnAction(event -> {
            if (handleSignUp(
                    signUpUsernameField.getText(),
                    signUpPasswordField.getText(),
                    emailField.getText()
            )) {
                loginPage();
            }
        });

        Button goBackButton = createGhostButton("Back");
        goBackButton.setOnAction(event -> loginPage());

        HBox actions = new HBox(12, goBackButton, signUpSubmitButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = createCard(
                "Create your local InvestPro account",
                "This account unlocks the InvestPro workspace and is separate from your exchange credentials.",
                createField("Username", signUpUsernameField),
                createField("Password", signUpPasswordField),
                createField("Email", emailField),
                actions
        );

        showPage(createShell(
                "SET UP YOUR DESK",
                "Create the account that unlocks your trading workspace.",
                "Once this local account is ready, you can connect supported exchanges, keep credentials separate, and run the app against local or custom database storage.",
                card,
                "Local-first onboarding keeps you moving even without MySQL.",
                "Exchange keys are verified before the trading desk opens."
        ));
    }

    private void showExchangeSetupPage() {
        properties = loadProperties();

        Label databaseStatusLabel = createStatusBanner();
        updateDatabaseStatusLabel(databaseStatusLabel);

        Label apiKeyLabel = new Label("API Key");
        apiKeyLabel.getStyleClass().add("form-label");
        Label secretKeyLabel = new Label("Secret Key");
        secretKeyLabel.getStyleClass().add("form-label");
        Label passphraseLabel = new Label("Passphrase");
        passphraseLabel.getStyleClass().add("form-label");
        Label credentialHelpLabel = new Label();
        credentialHelpLabel.getStyleClass().add("helper-text");
        credentialHelpLabel.setWrapText(true);

        comboBox.getItems().setAll("BINANCE US", "OANDA", "COINBASE");
        comboBox.setPromptText("Choose an exchange");
        comboBox.getStyleClass().add("input-field");
        comboBox.setTooltip(new Tooltip("Supported exchanges: Binance US, OANDA, and Coinbase Advanced Trade"));

        apiKeyTextField.setPromptText("Exchange API key");
        apiKeyTextField.getStyleClass().add("input-field");

        secretKeyTextField.setPromptText("Exchange API secret");
        secretKeyTextField.getStyleClass().add("input-field");

        passphraseTextField.setPromptText("Optional Coinbase key JSON");
        passphraseTextField.getStyleClass().add("input-field");

        tokensField.setPromptText("Optional Telegram bot token for AI alerts");
        tokensField.getStyleClass().add("input-field");

        CheckBox rememberExchangeCheck = new CheckBox("Remember exchange credentials on this machine");

        comboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            updateExchangeCredentialLabels(newValue, apiKeyLabel, secretKeyLabel, passphraseLabel, credentialHelpLabel);
            populateRememberedExchangeCredentials(newValue);
            rememberExchangeCheck.setSelected(hasSavedExchangeCredentials(newValue));
            setBannerState(exchangeStatusLabel, "", STATUS_INFO);
        });

        Properties rememberedExchangeProperties = loadProperties2();
        String rememberedExchange = rememberedExchangeProperties.getProperty("LAST_USED_EXCHANGE", "").trim();
        if (!rememberedExchange.isEmpty() && comboBox.getItems().contains(rememberedExchange)) {
            comboBox.setValue(rememberedExchange);
            populateRememberedExchangeCredentials(rememberedExchange);
            rememberExchangeCheck.setSelected(hasSavedExchangeCredentials(rememberedExchange));
        } else {
            updateExchangeCredentialLabels(null, apiKeyLabel, secretKeyLabel, passphraseLabel, credentialHelpLabel);
            rememberExchangeCheck.setSelected(false);
        }

        rememberExchangeCheck.setOnAction(event -> {
            String selectedExchange = comboBox.getValue();
            if (selectedExchange == null || selectedExchange.isBlank()) {
                setBannerState(exchangeStatusLabel, "Select an exchange before saving credentials.", STATUS_WARNING);
                rememberExchangeCheck.setSelected(false);
                return;
            }

            Properties props = loadProperties2();
            String prefix = "EXCHANGE_" + selectedExchange + "_";
            if (rememberExchangeCheck.isSelected()) {
                props.setProperty("LAST_USED_EXCHANGE", selectedExchange);
                props.setProperty(prefix + "API_KEY", apiKeyTextField.getText());
                props.setProperty(prefix + "SECRET_KEY", secretKeyTextField.getText());
                if ("COINBASE".equals(selectedExchange)) {
                    props.setProperty(prefix + "PASSPHRASE", passphraseTextField.getText());
                } else {
                    props.remove(prefix + "PASSPHRASE");
                }
                logger.info("Saved exchange credentials for {}", selectedExchange);
            } else {
                props.remove(prefix + "API_KEY");
                props.remove(prefix + "SECRET_KEY");
                props.remove(prefix + "PASSPHRASE");
                if (selectedExchange.equals(props.getProperty("LAST_USED_EXCHANGE"))) {
                    props.remove("LAST_USED_EXCHANGE");
                }
                logger.info("Cleared saved exchange credentials for {}", selectedExchange);
            }
            saveProperties2(props);
        });

        Button backBtn = createGhostButton("Back");
        backBtn.setOnAction(event -> loginPage());

        Button databaseSettingsButton = createGhostButton("Database Settings");
        databaseSettingsButton.setOnAction(event -> showDatabaseSettingsDialog(databaseStatusLabel));

        Button closeBtn = createSecondaryButton("Close");
        closeBtn.setOnAction(event -> Platform.exit());

        Button startBtn = createPrimaryButton("Verify and Open Desk");
        startBtn.setOnAction(event -> startTradingWindow(startBtn));

        HBox primaryActions = new HBox(12, backBtn, closeBtn, startBtn);
        primaryActions.setAlignment(Pos.CENTER_LEFT);

        HBox settingsRow = new HBox(10, databaseStatusLabel, databaseSettingsButton);
        settingsRow.getStyleClass().add("inline-row");

        VBox card = createCard(
                "Connect an exchange",
                "Verify your credentials before InvestPro opens the trading desk. Invalid keys or private keys are surfaced here before the workspace starts.",
                createField("Exchange", comboBox),
                createField(apiKeyLabel, apiKeyTextField),
                createField(secretKeyLabel, secretKeyTextField),
                createField(passphraseLabel, passphraseTextField),
                createField("Telegram Alerts (Optional)", tokensField),
                credentialHelpLabel,
                rememberExchangeCheck,
                exchangeStatusLabel,
                primaryActions,
                settingsRow
        );

        showPage(createShell(
                "CONNECT LIVE SERVICES",
                "Bring your exchange, market data, and AI alerts into one desk.",
                "InvestPro now opens into a cleaner trading workspace with chart tabs, depth view, order book, positions, orders, market data, and news modules.",
                card,
                "Each chart gets its own toolbar, timeframe controls, and market-depth subviews.",
                "Telegram alerts stay optional and can be reused by AI automation flows.",
                "Credentials are checked before the desk launches so incorrect keys are clearly flagged."
        ));
    }

    private void handlePasswordReset(@NotNull String email, String newPassword) {
        if (email.isEmpty() || newPassword.isEmpty()) {
            showAlert("Email and New Password cannot be empty.");
            return;
        }
        if (!ensureDatabaseAvailable()) {
            return;
        }

        if (!isValidEmail(email)) {
            showAlert("Please enter a valid email address.");
            return;
        }

        try {
            Query query = db1.getEntityManager().createNativeQuery("SELECT * FROM users WHERE email = :email");
            query.setParameter("email", email);

            if (query.getResultList().isEmpty()) {
                showAlert("Email not found.");
                return;
            }

            db1.getEntityManager().getTransaction().begin();
            db1.getEntityManager().createNativeQuery(
                            "UPDATE users SET password = :password WHERE email = :email")
                    .setParameter("password", newPassword)
                    .setParameter("email", email)
                    .executeUpdate();
            db1.getEntityManager().getTransaction().commit();
            new Messages(Alert.AlertType.INFORMATION, "Password reset successfully. Please log in.");
            loginPage();
        } catch (Exception e) {
            logger.error("Error resetting password", e);
            if (db1.getEntityManager().getTransaction().isActive()) {
                db1.getEntityManager().getTransaction().rollback();
            }
            showAlert("An error occurred during password reset.");
        }
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\."
                + "[a-zA-Z0-9_+&*-]+)*@"
                + "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

        Pattern pat = Pattern.compile(emailRegex);
        Matcher matcher = pat.matcher(email);
        return matcher.matches();
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void updateDatabaseStatusLabel(Label label) {
        if (db1 == null) {
            label.setText("Database unavailable");
            label.setTooltip(new Tooltip("Database unavailable"));
            return;
        }

        label.setText(db1.getDatabaseStatusText());
        label.setTooltip(new Tooltip(db1.getDatabaseDescription()));
    }

    private void showDatabaseSettingsDialog(Label databaseStatusLabel) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Database Settings");
        dialog.setHeaderText("Use the local embedded database or point InvestPro at your own JDBC database.");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        String localLabel = "Local embedded database";
        String customLabel = "Custom JDBC database";

        ComboBox<String> modeComboBox = new ComboBox<>();
        modeComboBox.getItems().addAll(localLabel, customLabel);
        boolean customSelected = DatabaseConfiguration.MODE_CUSTOM.equals(
                properties.getProperty("DB_MODE", DatabaseConfiguration.MODE_LOCAL).trim().toUpperCase(Locale.ROOT)
        );
        modeComboBox.setValue(customSelected ? customLabel : localLabel);

        TextField jdbcUrlField = new TextField(properties.getProperty("DB_URL", ""));
        TextField usernameField = new TextField(properties.getProperty("DB_USER", "root"));
        PasswordField passwordField = new PasswordField();
        passwordField.setText(properties.getProperty("DB_PASSWORD", ""));
        TextField dialectField = new TextField(properties.getProperty("DB_DIALECT", ""));
        TextField driverField = new TextField(properties.getProperty("DB_DRIVER", ""));
        Label helpLabel = new Label("Local mode stores data in " + AppFiles.resolveInAppHome("investpro-db"));

        grid.add(new Label("Mode:"), 0, 0);
        grid.add(modeComboBox, 1, 0);
        grid.add(new Label("JDBC URL:"), 0, 1);
        grid.add(jdbcUrlField, 1, 1);
        grid.add(new Label("Username:"), 0, 2);
        grid.add(usernameField, 1, 2);
        grid.add(new Label("Password:"), 0, 3);
        grid.add(passwordField, 1, 3);
        grid.add(new Label("Dialect (optional):"), 0, 4);
        grid.add(dialectField, 1, 4);
        grid.add(new Label("Driver (optional):"), 0, 5);
        grid.add(driverField, 1, 5);
        grid.add(helpLabel, 0, 6, 2, 1);

        Runnable updateFieldState = () -> {
            boolean usingCustomDatabase = customLabel.equals(modeComboBox.getValue());
            jdbcUrlField.setDisable(!usingCustomDatabase);
            usernameField.setDisable(!usingCustomDatabase);
            passwordField.setDisable(!usingCustomDatabase);
            dialectField.setDisable(!usingCustomDatabase);
            driverField.setDisable(!usingCustomDatabase);
            if (usingCustomDatabase) {
                helpLabel.setText("Example: jdbc:mysql://localhost:3306/investpro?useSSL=false&serverTimezone=UTC");
            } else {
                helpLabel.setText("Local mode stores data in " + AppFiles.resolveInAppHome("investpro-db"));
            }
        };

        modeComboBox.valueProperty().addListener((obs, oldValue, newValue) -> updateFieldState.run());
        updateFieldState.run();

        dialog.getDialogPane().setContent(grid);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            boolean useCustomDatabase = customLabel.equals(modeComboBox.getValue());
            if (useCustomDatabase && jdbcUrlField.getText().isBlank()) {
                helpLabel.setText("Enter a JDBC URL for the custom database.");
                event.consume();
                return;
            }

            Properties updatedProperties = loadProperties();
            updatedProperties.setProperty("DB_MODE", useCustomDatabase
                    ? DatabaseConfiguration.MODE_CUSTOM
                    : DatabaseConfiguration.MODE_LOCAL);
            updatedProperties.setProperty("DB_URL", jdbcUrlField.getText().trim());
            updatedProperties.setProperty("DB_USER", usernameField.getText().trim());
            updatedProperties.setProperty("DB_PASSWORD", passwordField.getText());
            updatedProperties.setProperty("DB_DIALECT", dialectField.getText().trim());
            updatedProperties.setProperty("DB_DRIVER", driverField.getText().trim());
            saveProperties(updatedProperties);

            properties = updatedProperties;
            boolean connected = reinitializeDatabase();
            updateDatabaseStatusLabel(databaseStatusLabel);

            if (!connected) {
                helpLabel.setText("The selected database could not be initialized.");
                event.consume();
                return;
            }

            if (useCustomDatabase && db1 != null && db1.getDatabaseConfiguration() != null
                    && db1.getDatabaseConfiguration().isLocal()) {
                new Messages(
                        Alert.AlertType.WARNING,
                        "The custom database could not be reached, so InvestPro switched back to the local embedded database."
                );
            } else if (db1 != null) {
                new Messages(Alert.AlertType.INFORMATION, "Using " + db1.getDatabaseDescription());
            }
        });

        dialog.showAndWait();
    }

    private boolean ensureDatabaseAvailable() {
        if (db1 == null || db1.getEntityManager() == null) {
            new Messages(
                    Alert.AlertType.ERROR,
                    "The database is unavailable. Open Database Settings and choose the local embedded database or configure your own JDBC database."
            );
            return false;
        }
        return true;
    }

    private boolean handleSignUp(@NotNull String username, @NotNull String password, @NotNull String email) {
        if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
            showAlert("Username, Password, and Email cannot be empty.");
            return false;
        }
        if (!ensureDatabaseAvailable()) {
            return false;
        }

        try {
            Query query = db1.getEntityManager().createNativeQuery("SELECT * FROM users WHERE username = :username");
            query.setParameter("username", username);

            if (!query.getResultList().isEmpty()) {
                new Messages(Alert.AlertType.WARNING, "Username already exists.");
                return false;
            }

            db1.getEntityManager().getTransaction().begin();
            db1.getEntityManager().createNativeQuery(
                            "INSERT INTO users (username, password, email) VALUES (:username, :password, :email)")
                    .setParameter("username", username)
                    .setParameter("password", password)
                    .setParameter("email", email)
                    .executeUpdate();
            db1.getEntityManager().getTransaction().commit();

            new Messages(Alert.AlertType.INFORMATION, "Registration successful. Please log in.");
            return true;
        } catch (Exception e) {
            logger.error("Error during sign-up", e);
            if (db1.getEntityManager().getTransaction().isActive()) {
                db1.getEntityManager().getTransaction().rollback();
            }
            showAlert("An error occurred during sign-up.");
            return false;
        }
    }

    private void handleLogin(@NotNull String username, String password) {
        if (!ensureDatabaseAvailable()) {
            return;
        }
        try {
            Query res = db1.getEntityManager().createNativeQuery(
                    "SELECT * FROM users WHERE username = :username AND password = :password"
            );
            res.setParameter("username", username);
            res.setParameter("password", password);
            if (res.getResultList().isEmpty()) {
                new Messages(Alert.AlertType.WARNING, "Invalid credentials.");
                return;
            }

            showExchangeSetupPage();
        } catch (Exception e) {
            logger.error("Error during login", e);
            new Messages(Alert.AlertType.ERROR, "An error occurred during login.");
        }
    }

    private void updateExchangeCredentialLabels(
            String exchangeName,
            Label apiKeyLabel,
            Label secretKeyLabel,
            Label passphraseLabel,
            Label credentialHelpLabel
    ) {
        boolean isCoinbase = "COINBASE".equals(exchangeName);
        passphraseLabel.setManaged(isCoinbase);
        passphraseLabel.setVisible(isCoinbase);
        passphraseTextField.setManaged(isCoinbase);
        passphraseTextField.setVisible(isCoinbase);

        if (exchangeName == null || exchangeName.isBlank()) {
            apiKeyLabel.setText("API Key");
            secretKeyLabel.setText("Secret Key");
            apiKeyTextField.setPromptText("Exchange API key");
            secretKeyTextField.setPromptText("Exchange API secret");
            credentialHelpLabel.setText("");
            passphraseTextField.clear();
        } else if ("OANDA".equals(exchangeName)) {
            apiKeyLabel.setText("Account Number");
            secretKeyLabel.setText("API Key");
            apiKeyTextField.setPromptText("OANDA account number");
            secretKeyTextField.setPromptText("OANDA API key");
            credentialHelpLabel.setText("Use your OANDA account number and API key.");
            passphraseTextField.clear();
        } else if (isCoinbase) {
            apiKeyLabel.setText("Key Name / ID");
            secretKeyLabel.setText("Private Key");
            passphraseLabel.setText("Key JSON (Optional)");
            apiKeyTextField.setPromptText("organizations/.../apiKeys/... or UUID");
            secretKeyTextField.setPromptText("Coinbase EC private key PEM");
            passphraseTextField.setPromptText("Optional full Coinbase key JSON");
            credentialHelpLabel.setText("Use Coinbase Advanced Trade credentials: paste the key name or UUID plus the EC private key PEM, or paste the full Coinbase key JSON.");
        } else {
            apiKeyLabel.setText("API Key");
            secretKeyLabel.setText("Secret Key");
            passphraseLabel.setText("Passphrase");
            apiKeyTextField.setPromptText("Binance US API key");
            secretKeyTextField.setPromptText("Binance US secret key");
            credentialHelpLabel.setText("Use your Binance US API key and secret key.");
            passphraseTextField.clear();
        }

        credentialHelpLabel.setManaged(!credentialHelpLabel.getText().isBlank());
        credentialHelpLabel.setVisible(!credentialHelpLabel.getText().isBlank());
    }

    private void populateRememberedExchangeCredentials(String exchangeName) {
        if (exchangeName == null || exchangeName.isBlank()) {
            apiKeyTextField.clear();
            secretKeyTextField.clear();
            passphraseTextField.clear();
            return;
        }

        Properties rememberedProperties = loadProperties2();
        apiKeyTextField.setText(rememberedProperties.getProperty("EXCHANGE_" + exchangeName + "_API_KEY", ""));
        secretKeyTextField.setText(rememberedProperties.getProperty("EXCHANGE_" + exchangeName + "_SECRET_KEY", ""));
        passphraseTextField.setText(rememberedProperties.getProperty("EXCHANGE_" + exchangeName + "_PASSPHRASE", ""));
    }

    private boolean hasSavedExchangeCredentials(String exchangeName) {
        if (exchangeName == null || exchangeName.isBlank()) {
            return false;
        }
        Properties rememberedProperties = loadProperties2();
        return !rememberedProperties.getProperty("EXCHANGE_" + exchangeName + "_API_KEY", "").isBlank()
                || !rememberedProperties.getProperty("EXCHANGE_" + exchangeName + "_SECRET_KEY", "").isBlank()
                || !rememberedProperties.getProperty("EXCHANGE_" + exchangeName + "_PASSPHRASE", "").isBlank();
    }

    private void startTradingWindow(@NotNull Button startButton) {
        String selectedExchange = comboBox.getValue();
        if (selectedExchange == null || selectedExchange.isBlank()) {
            setBannerState(exchangeStatusLabel, "Select an exchange before continuing.", STATUS_WARNING);
            return;
        }

        String apiKey = apiKeyTextField.getText().trim();
        String apiSecret = secretKeyTextField.getText().trim();
        String passphrase = passphraseTextField.getText().trim();
        boolean coinbaseSelected = "COINBASE".equals(selectedExchange);

        if ((!coinbaseSelected && (apiKey.isEmpty() || apiSecret.isEmpty()))
                || (coinbaseSelected && apiKey.isEmpty() && apiSecret.isEmpty() && passphrase.isEmpty())) {
            if ("OANDA".equals(selectedExchange)) {
                setBannerState(exchangeStatusLabel, "Enter your OANDA account number and API key.", STATUS_WARNING);
            } else if (coinbaseSelected) {
                setBannerState(exchangeStatusLabel, "Enter your Coinbase key id or key name plus the private key PEM, or paste the full Coinbase key JSON.", STATUS_WARNING);
            } else {
                setBannerState(exchangeStatusLabel, "Enter your API key and secret key.", STATUS_WARNING);
            }
            return;
        }

        properties.setProperty("TELEGRAM_BOT_TOKEN", tokensField.getText().trim());
        saveProperties(properties);

        startButton.setDisable(true);
        startButton.setText("Verifying...");
        setBannerState(exchangeStatusLabel, "Checking " + selectedExchange + " credentials...", STATUS_INFO);

        CompletableFuture
                .supplyAsync(() -> ExchangeCredentialValidator.validate(selectedExchange, apiKey, apiSecret, passphrase))
                .whenComplete((validationResult, throwable) -> Platform.runLater(() -> {
                    startButton.setDisable(false);
                    startButton.setText("Verify and Open Desk");

                    if (throwable != null) {
                        logger.error("Unexpected error while validating {} credentials", selectedExchange, throwable);
                        setBannerState(
                                exchangeStatusLabel,
                                "Unable to verify credentials right now. Please check your internet connection and try again.",
                                STATUS_ERROR
                        );
                        return;
                    }

                    if (!validationResult.isValid()) {
                        String bannerClass = validationResult.status() == ExchangeCredentialValidator.Status.INVALID_CREDENTIALS
                                ? STATUS_ERROR
                                : STATUS_WARNING;
                        setBannerState(exchangeStatusLabel, validationResult.message(), bannerClass);
                        return;
                    }

                    try {
                        exchange = createExchange(selectedExchange, apiKey, apiSecret, passphrase);
                        logger.info("Exchange instance created for {}", selectedExchange);
                        setBannerState(exchangeStatusLabel, "Credentials verified. Opening the trading desk...", STATUS_SUCCESS);
                        openTradingStage(selectedExchange);
                    } catch (Exception e) {
                        logger.error("Error starting trading window for {}", selectedExchange, e);
                        setBannerState(exchangeStatusLabel, "Error starting the desk: " + e.getMessage(), STATUS_ERROR);
                    }
                }));
    }

    private Exchange createExchange(String exchangeName, String apiKey, String apiSecret, String passphrase) {
        return switch (exchangeName) {
            case "BINANCE US", "BINANCEUS" -> new BinanceUS(apiKey, apiSecret);
            case "OANDA" -> new Oanda(apiKey, apiSecret);
            case "COINBASE" -> new Coinbase(apiKey, apiSecret, passphrase);
            default -> throw new IllegalArgumentException("Unexpected value: " + exchangeName);
        };
    }

    private void openTradingStage(String exchangeName) {
        logger.info("Opening trading desk for {}", exchangeName);

        DisplayExchangeUI display = new DisplayExchangeUI(exchange, tokensField.getText().trim());
        Scene scene = new Scene(display, 1530, 780);
        scene.getStylesheets().add(
                Objects.requireNonNull(TradingWindow.class.getResource("/css/app.css")).toExternalForm()
        );

        Stage stage = new Stage();
        stage.setTitle("InvestPro Desk - " + exchangeName);
        stage.setScene(scene);
        stage.setMinWidth(1320);
        stage.setMinHeight(760);
        stage.setOnCloseRequest(event -> display.shutdown());
        stage.setOnHidden(event -> display.shutdown());
        stage.show();
    }

    private void showPage(Node content) {
        getChildren().setAll(content);
        StackPane.setAlignment(content, Pos.CENTER);
    }

    private StackPane createShell(
            String eyebrow,
            String title,
            String subtitle,
            Node card,
            String... featurePoints
    ) {
        VBox hero = new VBox(18);
        hero.getStyleClass().addAll("auth-hero", "dashboard-hero-panel");

        Label eyebrowLabel = new Label(eyebrow);
        eyebrowLabel.getStyleClass().add("auth-eyebrow");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("hero-title");
        titleLabel.setWrapText(true);

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("hero-subtitle");
        subtitleLabel.setWrapText(true);

        HBox statPills = new HBox(
                12,
                createDashboardPill("Session", "Local"),
                createDashboardPill("Readiness", deriveShellState(title)),
                createDashboardPill("Coverage", "Charts + AI"),
                createDashboardPill("Region", "US")
        );
        statPills.getStyleClass().add("dashboard-pill-row");

        VBox features = new VBox(12);
        for (String featurePoint : featurePoints) {
            Label featureLabel = new Label("• " + featurePoint);
            features.getChildren().add(createDashboardStrip(featurePoint));
        }

        hero.getChildren().addAll(
                eyebrowLabel,
                titleLabel,
                subtitleLabel,
                statPills,
                createDashboardSummaryCard(title, subtitle),
                features
        );
        hero.setMaxWidth(640);

        VBox sideColumn = new VBox(18, card, createDashboardChecklistCard(featurePoints));
        sideColumn.getStyleClass().add("dashboard-side-column");
        sideColumn.setFillWidth(true);
        sideColumn.setMaxWidth(520);

        HBox shell = new HBox(24, hero, sideColumn);
        shell.getStyleClass().addAll("auth-shell", "dashboard-shell");
        shell.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(hero, Priority.ALWAYS);
        HBox.setHgrow(sideColumn, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(shell);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("dashboard-scroll");

        StackPane wrapper = new StackPane(scrollPane);
        wrapper.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return wrapper;
    }

    private VBox createCard(String title, String subtitle, Node... content) {
        VBox card = new VBox(16);
        card.getStyleClass().addAll("auth-card", "dashboard-connect-panel");
        card.setMaxWidth(520);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        titleLabel.setWrapText(true);

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("panel-subtitle");
        subtitleLabel.setWrapText(true);

        card.getChildren().addAll(titleLabel, subtitleLabel);
        card.getChildren().addAll(content);
        return card;
    }

    private VBox createDashboardPill(String labelText, String valueText) {
        Label label = new Label(labelText);
        label.getStyleClass().add("dashboard-pill-label");

        Label value = new Label(valueText);
        value.getStyleClass().add("dashboard-pill-value");

        VBox pill = new VBox(4, label, value);
        pill.getStyleClass().add("dashboard-pill");
        return pill;
    }

    private VBox createDashboardSummaryCard(String heading, String body) {
        Label sectionLabel = new Label("Session Preview");
        sectionLabel.getStyleClass().add("dashboard-summary-title");

        Label titleLabel = new Label(heading);
        titleLabel.getStyleClass().add("dashboard-summary-headline");
        titleLabel.setWrapText(true);

        Label bodyLabel = new Label(body);
        bodyLabel.getStyleClass().add("dashboard-summary-body");
        bodyLabel.setWrapText(true);

        VBox card = new VBox(10, sectionLabel, titleLabel, bodyLabel);
        card.getStyleClass().add("dashboard-summary-card");
        return card;
    }

    private HBox createDashboardStrip(String text) {
        Label title = new Label("Terminal Module");
        title.getStyleClass().add("dashboard-strip-title");

        Label body = new Label(text);
        body.getStyleClass().add("dashboard-strip-body");
        body.setWrapText(true);

        VBox content = new VBox(4, title, body);
        HBox strip = new HBox(content);
        strip.getStyleClass().add("dashboard-strip");
        strip.setAlignment(Pos.CENTER_LEFT);
        return strip;
    }

    private VBox createDashboardChecklistCard(String... items) {
        Label title = new Label("Launch Checklist");
        title.getStyleClass().add("dashboard-summary-title");

        VBox rows = new VBox(10);
        for (String item : items) {
            Label itemLabel = new Label(item);
            itemLabel.getStyleClass().add("dashboard-check-title");
            itemLabel.setWrapText(true);

            Label stateLabel = new Label("Ready");
            stateLabel.getStyleClass().add("dashboard-check-state");

            HBox row = new HBox(10, itemLabel, stateLabel);
            row.getStyleClass().add("dashboard-check-row");
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(itemLabel, Priority.ALWAYS);
            rows.getChildren().add(row);
        }

        VBox card = new VBox(12, title, rows);
        card.getStyleClass().add("dashboard-checklist-card");
        return card;
    }

    private String deriveShellState(String title) {
        String upperTitle = title.toUpperCase(Locale.ROOT);
        if (upperTitle.contains("CONNECT")) {
            return "Broker";
        }
        if (upperTitle.contains("RESET")) {
            return "Recovery";
        }
        if (upperTitle.contains("CREATE")) {
            return "Signup";
        }
        return "Login";
    }

    private VBox createField(String labelText, Control control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        return createField(label, control);
    }

    private VBox createField(Label label, Control control) {
        VBox field = new VBox(6, label, control);
        field.getStyleClass().add("form-field");
        VBox.setVgrow(control, Priority.NEVER);
        return field;
    }

    private TextField createTextField(String promptText) {
        TextField textField = new TextField();
        textField.setPromptText(promptText);
        textField.getStyleClass().add("input-field");
        return textField;
    }

    private PasswordField createPasswordField(String promptText) {
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText(promptText);
        passwordField.getStyleClass().add("input-field");
        return passwordField;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        return button;
    }

    private Button createGhostButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("ghost-button");
        return button;
    }

    private Label createStatusBanner() {
        Label label = new Label();
        label.getStyleClass().add("status-banner");
        label.setWrapText(true);
        label.setManaged(false);
        label.setVisible(false);
        return label;
    }

    private void setBannerState(Label label, String message, String bannerClass) {
        label.getStyleClass().removeAll(STATUS_INFO, STATUS_SUCCESS, STATUS_WARNING, STATUS_ERROR);
        boolean hasMessage = message != null && !message.isBlank();
        label.setText(hasMessage ? message : "");
        label.setManaged(hasMessage);
        label.setVisible(hasMessage);
        if (hasMessage) {
            label.getStyleClass().add(bannerClass);
        }
    }
}
