package org.investpro;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class TradingWindow extends Region {

    private static final Logger logger = LoggerFactory.getLogger(TradingWindow.class);
    private static final String CONFIG_FILE = "config.properties";
    private static final String CONFIG_FILE2 = "config2.properties";

    private Exchange exchange;
    private final ComboBox<String> comboBox = new ComboBox<>();
    private final TextField apiKeyTextField = new TextField();
    private final TextField secretKeyTextField = new TextField();

    public TradingWindow() {
        setPadding(new Insets(10));
        loginPage();
        setStyle(
                "-fx-background-color: #333333;" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 10;" +
                        "-fx-border-width: 2;" +
                        "-fx-border-color: #555555;" +
                        "-fx-border-radius: 5;"
        );
    }

    private static void saveProperties2(@NotNull Properties properties) {
        try (FileWriter writer = new FileWriter(CONFIG_FILE2)) {
            properties.store(writer, "User settings");

        } catch (IOException e) {
            logger.error("Error saving properties to file", e);
        }
    }

    private static void saveProperties(@NotNull Properties properties) {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            properties.store(writer, "User settings");

        } catch (IOException e) {
            logger.error("Error saving properties to file", e);
        }
    }

    private static @NotNull Properties loadProperties2() {
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(CONFIG_FILE2)) {
            properties.load(reader);
            logger.info("Properties loaded from file");
        } catch (FileNotFoundException e) {
            logger.warn("Config file not found, starting with default settings");
        } catch (IOException e) {
            logger.error("Error loading properties from file", e);
        }
        return properties;
    }

    private void loginPage() {
        getChildren().forEach(child -> child.setVisible(false));
        GridPane loginGrid = new GridPane();
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

        CheckBox rememberMeCheck = new CheckBox("Remember Me");
        loginGrid.add(rememberMeCheck, 1, 3);

        rememberMeCheck.setOnAction(_ -> {
            if (rememberMeCheck.isSelected()) {
                Properties properties = loadProperties();
                properties.setProperty("LOGIN_USERNAME", loginUsernameField.getText());
                properties.setProperty("LOGIN_PASSWORD", loginPasswordField.getText());
                saveProperties(properties);
            }
        });

        Button loginSubmitButton = new Button("Login");
        Button signUpButton = new Button("Sign Up");
        signUpButton.setOnAction(_ -> createSignUpPage());
        Button forgotPasswordButton = new Button("Forgot Password?");
        forgotPasswordButton.setTranslateY(300);
        forgotPasswordButton.setOnAction(_ -> resetPasswordPage());

        loginGrid.add(loginSubmitButton, 3, 3);
        loginGrid.add(signUpButton, 0, 3);
        loginGrid.add(forgotPasswordButton, 1, 4);

        Properties properties = loadProperties();
        if (!properties.getProperty("LOGIN_USERNAME", "").isEmpty() && !properties.getProperty("LOGIN_PASSWORD", "").isEmpty()) {
            loginUsernameField.setText(properties.getProperty("LOGIN_USERNAME"));
            loginPasswordField.setText(properties.getProperty("LOGIN_PASSWORD"));
        }

        loginSubmitButton.setOnAction(_ -> handleLogin(loginUsernameField.getText(), loginPasswordField.getText()));
        forgotPasswordButton.setOnAction(_ -> resetPasswordPage());

        loginGrid.setTranslateX(600);
        loginGrid.setTranslateY(200);
        getChildren().add(loginGrid);
    }

    private void resetPasswordPage() {
        getChildren().forEach(child -> child.setVisible(false));
        GridPane resetPasswordGrid = new GridPane();
        resetPasswordGrid.setPadding(new Insets(10));
        resetPasswordGrid.setHgap(10);
        resetPasswordGrid.setVgap(10);

        Label emailLabel = new Label("Email:");
        TextField emailField = new TextField();
        resetPasswordGrid.add(emailLabel, 0, 1);
        resetPasswordGrid.add(emailField, 1, 1);

        Label newPasswordLabel = new Label("New Password:");
        PasswordField newPasswordField = new PasswordField();
        resetPasswordGrid.add(newPasswordLabel, 0, 2);
        resetPasswordGrid.add(newPasswordField, 1, 2);

        Button resetSubmitButton = new Button("Reset Password");
        resetPasswordGrid.add(resetSubmitButton, 1, 3);

        Button goBackButton = new Button("Go Back");
        resetPasswordGrid.add(goBackButton, 0, 4);
        goBackButton.setOnAction(_ -> loginPage());

        resetSubmitButton.setOnAction(_ -> handlePasswordReset(emailField.getText(), newPasswordField.getText()));
        resetPasswordGrid.setTranslateX(600);
        resetPasswordGrid.setTranslateY(200);
        getChildren().add(resetPasswordGrid);
    }

    private void createSignUpPage() {
        getChildren().forEach(child -> child.setVisible(false));
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

    private void handlePasswordReset(@NotNull String email, String newPassword) {
        if (email.isEmpty() || newPassword.isEmpty()) {
            showAlert("Email and New Password cannot be empty.");
            return;
        }

        if (!isValidEmail(email)) {
            showAlert("Please enter a valid email address.");
            return;
        }

        try {
//            Query query = db1.entityManager.createNativeQuery("SELECT * FROM users WHERE email = :email");
//            query.setParameter("email", email);
//
//            if (query.getResultList().isEmpty()) {
//                showAlert(Alert.AlertType.WARNING, "Error", "Email not found.");
//                return;
//            }
//
//            db1.entityManager.getTransaction().begin();
//            db1.entityManager.createNativeQuery(
//                            "UPDATE users SET password = :password WHERE email = :email")
//                    .setParameter("password", newPassword)
//                    .setParameter("email", email)
//                    .executeUpdate();
//            db1.entityManager.getTransaction().commit();
//            showAlert(Alert.AlertType.INFORMATION, "Success", "Password reset successfully. Please log in.");
//            loginPage();
        } catch (Exception e) {
//            logger.error("Error resetting password", e);
//            if (db1.entityManager.getTransaction().isActive()) {
//                db1.entityManager.getTransaction().rollback();
//            }
//            showAlert(Alert.AlertType.ERROR, "Error", "An error occurred during password reset.");
        }
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\." +
                "[a-zA-Z0-9_+&*-]+)*@" +
                "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

        Pattern pat = Pattern.compile(emailRegex);
        Matcher matcher = pat.matcher(email);
        return matcher.matches();
    }

    private void showAlert(String s) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Error");
        alert.setContentText(s);
        alert.showAndWait();
    }

    private boolean handleSignUp(@NotNull String username, @NotNull String password, @NotNull String email) {
        if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
            showAlert("Username, Password, and Email cannot be empty.");
            return false;
        }
//
//        try {
//            Query query = db1.entityManager.createNativeQuery("SELECT * FROM users WHERE username = :username");
//            query.setParameter("username", username);
//
//            if (!query.getResultList().isEmpty()) {
//                new Messages(Alert.AlertType.WARNING, "Username already exists.");
//                return false;
//            }
//
//            db1.entityManager.getTransaction().begin();
//            db1.entityManager.createNativeQuery(
//                            "INSERT INTO users (username, password, email) VALUES (:username, :password, :email)")
//                    .setParameter("username", username)
//                    .setParameter("password", password)
//                    .setParameter("email", email)
//                    .executeUpdate();
//            db1.entityManager.getTransaction().commit();
//
//            showAlert(Alert.AlertType.INFORMATION, "Success", "Registration successful. Please log in.");
//            return true;
//        } catch (Exception e) {
//            logger.error("Error during sign-up", e);
//            if (db1.entityManager.getTransaction().isActive()) {
//                db1.entityManager.getTransaction().rollback();
//            }
//            showAlert(Alert.AlertType.ERROR, "Error", "An error occurred during sign-up.");
        return false;
//        }
    }

    private void handleLogin(@NotNull String username, String password) {
        try {
//            Query res = db1.entityManager.createNativeQuery("SELECT * FROM users WHERE username = :username AND password = :password");
//            res.setParameter("username", username);
//            res.setParameter("password", password);
//
//            if (res.getResultList().isEmpty()) {
//                new Messages(Alert.AlertType.WARNING, "Invalid credentials.");
//                return;
//            }

            launchTradingWindow();
        } catch (Exception e) {
            logger.error("Error during login", e);
            new Messages(Alert.AlertType.ERROR, "An error occurred during login.");
        }
    }

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

    private void launchTradingWindow() {
        getStyleClass().add("trading-window");
        logger.info("Initializing TradingWindow");
        setPrefSize(1540, 780);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);

        Label exchangeLabel = new Label("EXCHANGES  :");
        gridPane.add(exchangeLabel, 0, 2);

        comboBox.setValue("Select your exchange");
        comboBox.getItems().addAll("BINANCE US", "BINANCE", "OANDA", "COINBASE", "BITFINEX", "BITMEX", "POLONIEX");
        gridPane.add(comboBox, 1, 2);
        comboBox.getStyleClass().add("combo-box");

        apiKeyTextField.setMaxSize(Double.MAX_VALUE, 20);
        apiKeyTextField.setPrefWidth(300);

        Label apiKeyLabel = new Label("API KEY :");
        gridPane.add(apiKeyLabel, 0, 0);
        gridPane.add(apiKeyTextField, 1, 0);

        secretKeyTextField.setMaxSize(Double.MAX_VALUE, 20);
        secretKeyTextField.setPrefWidth(300);

        Label secretKeyLabel = new Label("SECRET KEY :");
        gridPane.add(secretKeyLabel, 0, 1);
        gridPane.add(secretKeyTextField, 1, 1);

        comboBox.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> {
            if ("OANDA".equals(newValue)) {
                apiKeyLabel.setText("ACCOUNT NUMBER :");
                secretKeyLabel.setText("API KEY :");
            } else {
                apiKeyLabel.setText("API KEY :");
                secretKeyLabel.setText("SECRET KEY :");
            }

        });

        Label rememberMeLabel = new Label("Remember Me :");
        gridPane.add(rememberMeLabel, 0, 4);

        CheckBox rememberMeButton = new CheckBox();
        gridPane.add(rememberMeButton, 1, 4);

        rememberMeButton.setOnAction(_ -> {
            if (rememberMeButton.isSelected()) {
                String selectedExchange = comboBox.getValue();
                Properties props = loadProperties2();

                props.setProperty(
                        "LAST_USED_EXCHANGE", selectedExchange
                );

                props.setProperty("EXCHANGE_" + selectedExchange + "_API_KEY",

                        (!Objects.equals(apiKeyTextField.getText(), "")) ? apiKeyTextField.getText() : props.getProperty("EXCHANGE_" + selectedExchange + "_API_KEY")

                );
                props.setProperty("EXCHANGE_" + selectedExchange + "_SECRET_KEY", (!Objects.equals(secretKeyTextField.getText(), "") ?
                        secretKeyTextField.getText() : props.getProperty("EXCHANGE_" + selectedExchange + "_SECRET_KEY")));

                saveProperties2(props);
                logger.info("Settings saved for {}", selectedExchange);
            }
        });

        Button cancelBtn = new Button("Close");
        cancelBtn.getStyleClass().add("button");
        cancelBtn.setOnAction(_ -> Platform.exit());
        gridPane.add(cancelBtn, 1, 3);

        Button startBtn = new Button("Start");
        startBtn.getStyleClass().add("button");

        startBtn.setOnAction(_ -> {
            Properties properties = loadProperties2();
            String lastUsedExchange = properties.getProperty("LAST_USED_EXCHANGE", "");

            if (!lastUsedExchange.isEmpty() && properties.getProperty("EXCHANGE_" + lastUsedExchange + "_API_KEY") != null) {
                apiKeyTextField.setText(properties.getProperty("EXCHANGE_" + lastUsedExchange + "_API_KEY"));
                secretKeyTextField.setText(properties.getProperty("EXCHANGE_" + lastUsedExchange + "_SECRET_KEY"));
            }

            if (apiKeyTextField.getText().isEmpty() || secretKeyTextField.getText().isEmpty()) {
                showAlert("Please enter your API KEY and SECRET KEY");
                return;
            }

            if (comboBox.getValue() == null || Objects.equals(comboBox.getValue(), "Select your exchange")) {
                showAlert("Please select your exchange");
                return;
            }

            try {
                switch (comboBox.getSelectionModel().getSelectedItem()) {
                    case "BINANCE US" -> exchange = new BinanceUS(apiKeyTextField.getText(), secretKeyTextField.getText());
                    case "BINANCE" -> exchange = new Binance(apiKeyTextField.getText(), secretKeyTextField.getText());
                    case "OANDA" -> exchange = new Oanda(apiKeyTextField.getText(), secretKeyTextField.getText());
                    case "COINBASE" -> exchange = new Coinbase(apiKeyTextField.getText(), secretKeyTextField.getText());
                    default ->
                            throw new RuntimeException("Unexpected value: %s".formatted(comboBox.getSelectionModel().getSelectedItem()));
                }
                logger.info("Exchange instance created for {}", comboBox.getValue());
                logger.info("Starting trading window for {}", comboBox.getValue());
                //  getChildren().forEach(child -> child.setVisible(false));

                DisplayExchange display = new DisplayExchange(exchange);
                StackPane root = new StackPane(display);
                Scene scene = new Scene(root, 1540, 780);
                scene.getStylesheets().add(Objects.requireNonNull(TradingWindow.class.getResource("/app.css")).toExternalForm());
                Stage stage = new Stage();
                stage.setTitle("Trading Window - " + comboBox.getValue());
                stage.setScene(scene);
                stage.show();

            } catch (Exception e) {
                new Messages(Alert.AlertType.ERROR, "Error starting trading window: " + e.getMessage());
            }
        });

        gridPane.add(startBtn, 2, 3);

        setMaxWidth(1540);
        setMaxHeight(780);
        getChildren().forEach(child -> child.setVisible(false));
        gridPane.setTranslateY(getMaxHeight() / 3);
        gridPane.setTranslateX(getMaxWidth() / 3);
        getChildren().add(gridPane);
    }
}
