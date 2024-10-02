package org.investpro;

import jakarta.persistence.Query;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class TradingWindow extends Region {

    private static final Logger logger = LoggerFactory.getLogger(TradingWindow.class);
    private static final DbHibernate db = new DbHibernate();
    private Stage loginStage;

    private Exchange exchange;

    public TradingWindow() {
        // Setup UI for login
        setPadding(new Insets(10));




        loginPage();
    }


    private static final String CONFIG_FILE = "config.properties"; // File for storing user settings


    /**
     * Set up the login page UI.
     */
    private void loginPage() {
        getChildren().forEach(child -> child.setVisible(false));
        // Creating a GridPane for the login form
        GridPane loginGrid = new GridPane();
     setStyle(
             "-fx-background-color: #0a3463;" +
             "-fx-padding: 10;" +
             "-fx-border-width: 2;" +
             "-fx-border-color: #0a3936;" +
             "-fx-border-radius: 5;" +
             "-fx-background-radius: 5;"

     );
        loginGrid.setPadding(new Insets(10));
        loginGrid.setHgap(10);
        loginGrid.setVgap(10);

        Label welcomeLabel = new Label("Welcome to InvestPro!");
        loginGrid.add(welcomeLabel, 0, 0, 2, 1);

        Label loginUsernameLabel = new Label("Username:");
        TextField loginUsernameField = new TextField();
        loginGrid.add(loginUsernameLabel, 0, 1);
        loginGrid.add(loginUsernameField, 1, 1);

        Label loginPasswordLabel = new Label("Password:");
        PasswordField loginPasswordField = new PasswordField();
        loginGrid.add(loginPasswordLabel, 0, 2);
        loginGrid.add(loginPasswordField, 1, 2);

        CheckBox remember_meCheck = new CheckBox();
        remember_meCheck.setText("Remember Me");
        loginGrid.add(remember_meCheck, 1, 3);

        remember_meCheck.setOnAction(
                _ -> {
                    if (remember_meCheck.isSelected()) {
                        Properties properties = new Properties();
                        properties.setProperty("LOGIN_USERNAME", loginUsernameField.getText());
                        properties.setProperty("LOGIN_PASSWORD", loginPasswordField.getText());
                        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                            properties.store(writer, "User settings");
                        } catch (IOException e) {
                            logger.error("Error saving login credentials", e);
                        }
                    }
                }
        );

        Button loginSubmitButton = new Button("Login");
        Button signUpButton = new Button("Sign Up");



        loginGrid.add(loginSubmitButton, 3, 3);
        loginGrid.add(signUpButton, 0, 3);

        // Event handlers for login and sign-up buttons
        signUpButton.setOnAction(_ -> createSignUpPage());
        loginSubmitButton.setOnAction(_ -> handleLogin(loginUsernameField.getText(), loginPasswordField.getText()));

        // Set up the login stage and show it


        loginGrid.setTranslateX(600);
        loginGrid.setTranslateY(200);

        getChildren().add(loginGrid);
    }

    /**
     * Set up the sign-up page UI.
     */
    private void createSignUpPage() {

        getChildren().forEach(child -> child.setVisible(false));//hide  previous grid
        GridPane signUpGrid = new GridPane();



        signUpGrid.setPadding(new Insets(10));
        signUpGrid.setHgap(10);
        signUpGrid.setVgap(10);

        Label signUpLabel = new Label("Sign Up");
        signUpGrid.add(signUpLabel, 0, 0, 2, 1);

        Label signUpUsernameLabel = new Label("Username:");
        TextField signUpUsernameField = new TextField();
        signUpGrid.add(signUpUsernameLabel, 0, 1);
        signUpGrid.add(signUpUsernameField, 1, 1);

        Label signUpPasswordLabel = new Label("Password:");
        PasswordField signUpPasswordField = new PasswordField();
        signUpGrid.add(signUpPasswordLabel, 0, 2);
        signUpGrid.add(signUpPasswordField, 1, 2);

        Label emailLabel = new Label("Email:");
        TextField emailField = new TextField();
        signUpGrid.add(emailLabel, 0, 3);
        signUpGrid.add(emailField, 1, 3);

        Button signUpSubmitButton = new Button("Sign Up");
        Button goBackButton = new Button("Go Back");
        signUpGrid.add(signUpSubmitButton, 3, 4);
        signUpGrid.add(goBackButton, 0, 4);



        goBackButton.setOnAction(_ -> loginPage());


        signUpSubmitButton.setOnAction(_ -> {
            if (handleSignUp(signUpUsernameField.getText(), signUpPasswordField.getText(), emailField.getText())) {

                loginPage();
            }
        });
        signUpGrid.setTranslateX(600);
        signUpGrid.setTranslateY(200);
        getChildren().add(signUpGrid);
    }

    /**
     * Handle user login. This method will verify user credentials.
     */
    private void handleLogin(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Username and Password cannot be empty.");
            return;
        }

        try {
            Query res = db.entityManager.createNativeQuery(
                    "SELECT * FROM users WHERE username = :username AND password = :password");
            res.setParameter("username", username);
            res.setParameter("password", password);

            if (res.getResultList().isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Error", "Invalid username or password.");
            } else {
                getChildren().forEach(child -> child.setVisible(false));
                launchTradingWindow();
            }
        } catch (Exception e) {
            logger.error("Error during login", e);
            showAlert(Alert.AlertType.ERROR, "Error", "An error occurred during login.");
        }
    }

    /**
     * Handle user sign-up. This method will insert a new user into the database.
     */
    private boolean handleSignUp(@NotNull String username, @NotNull String password, @NotNull String email) {
        if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Username, Password, and Email cannot be empty.");
            return false;
        }

        try {
            Query query = db.entityManager.createNativeQuery("SELECT * FROM users WHERE username = :username   AND password = :password");
            query.setParameter("username", username);
            query.setParameter("password", password);

            if (!query.getResultList().isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Error", "Username already exists.");
                return false;
            }

            db.entityManager.getTransaction().begin();
            db.entityManager.createNativeQuery(
                            "INSERT INTO users (id,username, password, email) VALUES (:id,:username, :password, :email)")
                    .setParameter("id", (UUID.randomUUID()).toString())
                    .setParameter("username", username)
                    .setParameter("password", password)
                    .setParameter("email", email)
                    .executeUpdate();
            db.entityManager.getTransaction().commit();
            showAlert(Alert.AlertType.INFORMATION, "Success", "Registration successful. Please log in.");
            return true;
        } catch (Exception e) {
            logger.error("Error during sign-up", e);
            showAlert(Alert.AlertType.ERROR, "Error", "An error occurred during sign-up.");
            return false;
        }
    }

    /**
     * Show alert messages for login and sign-up processes.
     */
    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Launch the trading window after successful login.
     */
    private void launchTradingWindow() {


        getStyleClass().add("trading-window");
        logger.info("Initializing TradingWindow");
        setPrefSize(1540, 780);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);

        Label exchangeLabel = new Label("EXCHANGES  :");
        gridPane.add(exchangeLabel, 0, 2);

        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.setValue("Select your exchange");
        comboBox.getItems().addAll("BINANCE US", "BINANCE", "OANDA", "COINBASE", "BITFINEX", "BITMEX", "POLONIEX");
        gridPane.add(comboBox, 1, 2);
        comboBox.getStyleClass().add("combo-box");

        TextField apiKeyTextField = new TextField();
        apiKeyTextField.setMaxSize(Double.MAX_VALUE, 20);
        apiKeyTextField.setPrefWidth(300);

        Label apikeyLabel = new Label("API KEY :");
        gridPane.add(apikeyLabel, 0, 0);
        gridPane.add(apiKeyTextField, 1, 0);

        TextField secretKeyTextField = new TextField();
        secretKeyTextField.setMaxSize(Double.MAX_VALUE, 20);
        secretKeyTextField.setPrefWidth(300);

        Label secretLabel = new Label("SECRET KEY :");
        gridPane.add(secretLabel, 0, 1);
        gridPane.add(secretKeyTextField, 1, 1);

        // Dynamic label change for OANDA
        comboBox.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> {
            if ("OANDA".equals(newValue)) {
                apikeyLabel.setText("ACCOUNT NUMBER :");
                secretLabel.setText("API KEY :");
            } else {
                apikeyLabel.setText("API KEY :");
                secretLabel.setText("SECRET KEY :");
            }
            // Load saved API and Secret Key when exchange changes
            loadExchangeSettings(newValue, apiKeyTextField, secretKeyTextField);
        });

        Label rememberMeLabel = new Label("Remember Me :");
        gridPane.add(rememberMeLabel, 0, 4);

        CheckBox rememberMeButton = new CheckBox();
        gridPane.add(rememberMeButton, 1, 4);

        Properties properties = loadProperties();
        String savedExchange = properties.getProperty("LAST_USED_EXCHANGE");
        if (savedExchange != null) {
            comboBox.setValue(savedExchange);
            loadExchangeSettings(savedExchange, apiKeyTextField, secretKeyTextField);
            rememberMeButton.setSelected(true);
        }

        rememberMeButton.setOnAction(_ -> {
            if (rememberMeButton.isSelected()) {
                String selectedExchange = comboBox.getValue();
                Properties saveProperties = loadProperties(); // Load existing properties

                saveProperties.setProperty("EXCHANGE_%s_API_KEY".formatted(selectedExchange), apiKeyTextField.getText());
                saveProperties.setProperty("EXCHANGE_%s_SECRET_KEY".formatted(selectedExchange), secretKeyTextField.getText());
                saveProperties.setProperty("LAST_USED_EXCHANGE", selectedExchange);

                try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                    saveProperties.store(writer, "User Settings");
                    logger.info("User settings saved for {}", selectedExchange);
                } catch (IOException e) {
                    logger.error("Failed to save properties to file", e);
                }
            }
        });

        Button cancelBtn = new Button("Close");
        cancelBtn.getStyleClass().add("button");
        cancelBtn.setOnAction(event -> Platform.exit());
        gridPane.add(cancelBtn, 1, 3);

        Button startBtn = new Button("Start");
        startBtn.getStyleClass().add("button");

        AtomicInteger count = new AtomicInteger();
        startBtn.setOnAction(_ -> {
            if (apiKeyTextField.getText().isEmpty() || secretKeyTextField.getText().isEmpty()) {
                new Messages("Error", "Please enter your API KEY and SECRET KEY");
                return;
            }

            if (comboBox.getValue() == null || Objects.equals(comboBox.getValue(), "Select your exchange")) {
                new Messages("Error", "Please select your exchange");
                return;
            }

            // Create exchange instance based on user selection
            try {
                switch (comboBox.getSelectionModel().getSelectedItem()) {
                    case "BINANCE US" -> exchange = new BinanceUS(apiKeyTextField.getText(), secretKeyTextField.getText());
                    case "BINANCE" -> exchange = new Binance(apiKeyTextField.getText(), secretKeyTextField.getText());
                    case "OANDA" -> exchange = new Oanda(apiKeyTextField.getText(), secretKeyTextField.getText());
                    case "COINBASE" -> exchange = new Coinbase(apiKeyTextField.getText(), secretKeyTextField.getText());
                    default -> throw new IllegalStateException("Unexpected value: %s".formatted(comboBox.getSelectionModel().getSelectedItem()));
                }




                DisplayExchange display = new DisplayExchange(exchange);
                Label versionLabel = new Label("Version: %s".formatted(InvestPro.class.getPackage().getImplementationVersion()));
                versionLabel.setTranslateX(10);
                versionLabel.setTranslateY(10);
                getChildren().forEach(child -> child.setVisible(false));
                getChildren().addAll(versionLabel,display);

                Scene scene = new Scene(display);
                scene.getStylesheets().add(Objects.requireNonNull(TradingWindow.class.getResource("/app.css")).toExternalForm());

                count.incrementAndGet();


            } catch (Exception e) {
                new Messages("Error", e.toString());
            }
        });
        gridPane.add(startBtn, 2, 3);

        setMaxWidth(1540);
        setMaxHeight(780);
        getChildren().forEach(child -> child.setVisible(false));
        gridPane.setTranslateY(getMaxHeight() / 3);
        gridPane.setTranslateX(getMaxWidth() / 3);
        getChildren().add(gridPane);

;
    }

    // Method to load properties (user settings) from the file
    private static @NotNull Properties loadProperties() {
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            properties.load(reader);
            logger.info("Properties loaded from file");
        } catch (FileNotFoundException e) {
            logger.warn("Config file not found, starting with default settings");
        } catch (IOException e) {
            logger.error("Error loading properties from file", e);
        }
        return properties;
    }

    // Method to load an API key and secret key for the selected exchange
    private void loadExchangeSettings(String exchange, TextField apiKeyTextField, TextField secretKeyTextField) {
        Properties properties = loadProperties();
        String apiKey = properties.getProperty("EXCHANGE_%s_API_KEY".formatted(exchange));
        String secretKey = properties.getProperty("EXCHANGE_%s_SECRET_KEY".formatted(exchange));

        if (apiKey != null) {
            apiKeyTextField.setText(apiKey);
            logger.info("Loaded API key for {}", exchange);
        } else {
            apiKeyTextField.clear();
        }

        if (secretKey != null) {
            secretKeyTextField.setText(secretKey);
            logger.info("Loaded secret key for {}", exchange);
        } else {
            secretKeyTextField.clear();
        }
    }


}
