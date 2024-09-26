package org.investpro;

import io.github.cdimascio.dotenv.Dotenv;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class TradingWindow extends AnchorPane {

    private static final Logger logger = LoggerFactory.getLogger(TradingWindow.class);
    private static final String CONFIG_FILE = "config.properties"; // File for storing user settings
    Exchange exchange;

    public TradingWindow() {
        getStyleClass().add("trading-window");
        logger.info("TradingWindow {}", this);
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

        // Load previously saved settings for the last used exchange
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
                Properties saveProperties = loadProperties();  // Load existing properties

                saveProperties.setProperty("EXCHANGE_%s_API_KEY".formatted(selectedExchange), apiKeyTextField.getText());
                saveProperties.setProperty("EXCHANGE_%s_SECRET_KEY".formatted(selectedExchange), secretKeyTextField.getText());
                saveProperties.setProperty("LAST_USED_EXCHANGE", selectedExchange);

                try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                    saveProperties.store(writer, "User Settings");
                    logger.info("User settings saved to file for {}", selectedExchange);
                } catch (IOException e) {
                    logger.error("Failed to save properties to file", e);
                }
            }
        });

        Button cancelBtn = new Button("Close");
        cancelBtn.getStyleClass().add("button");
        cancelBtn.setOnAction(_ -> Platform.exit());
        gridPane.add(cancelBtn, 1, 3);

        Button loginBtn = new Button("Start");
        loginBtn.getStyleClass().add("button");

        AtomicInteger count = new AtomicInteger();
        loginBtn.setOnAction(_ -> {
            if (apiKeyTextField.getText().isEmpty() || secretKeyTextField.getText().isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("Please enter your API KEY and SECRET KEY");
                alert.showAndWait();
                return;
            }

            if (comboBox.getValue() == null || Objects.equals(comboBox.getValue(), "Select your exchange")) return;

            switch (comboBox.getSelectionModel().getSelectedItem()) {
                case "BINANCE US" -> {
                    try {
                        exchange = new BinanceUs(apiKeyTextField.getText(), secretKeyTextField.getText());
                    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                        throw new RuntimeException(e);
                    }
                }
                case "BINANCE" -> {
                    try {
                        exchange = new Binance(apiKeyTextField.getText(), secretKeyTextField.getText(), TradePair.of("BTC", "USD"));
                    } catch (ClassNotFoundException | SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
                case "OANDA" -> exchange = new Oanda(apiKeyTextField.getText(), apiKeyTextField.getText());
                case "COINBASE" -> {
                    Dotenv dotenv = Dotenv.load();
                    String privateKeyPEM = dotenv.get("COINBASE_PRIVATE_API_KEY");
                    if (privateKeyPEM != null) {
                        privateKeyPEM = privateKeyPEM.replace("\\n", "\n");
                    }
                    String name = dotenv.get("COINBASE_API_KEY_NAME");

                    try {
                        exchange = new Coinbase(name, privateKeyPEM);
                    } catch (UnsupportedEncodingException | NoSuchAlgorithmException | InvalidKeyException e) {
                        throw new RuntimeException(e);
                    }
                }
                default -> throw new IllegalStateException("Unexpected value: %s".formatted(comboBox.getSelectionModel().getSelectedItem()));
            }

            Stage st = new Stage();

            st.setTitle("%s @InvestPro Copyright 2020-%s".formatted(comboBox.getSelectionModel().getSelectedItem(), new Date()));
            try {
                DisplayExchange display = new DisplayExchange(exchange);
                display.getStyleClass().add("display-exchange");

                Label versionLabel = new Label("Version: %s".formatted(InvestPro.class.getPackage().getImplementationVersion()));
                versionLabel.getStyleClass().add("label");
                versionLabel.setTranslateX(10);
                versionLabel.setTranslateY(10);
                getChildren().add(versionLabel);

                Label exchangeNameLabel = new Label("Exchange: %s".formatted(comboBox.getSelectionModel().getSelectedItem()));
                exchangeNameLabel.getStyleClass().add("exchange-name-label");
                exchangeNameLabel.setTranslateX(0);
                exchangeNameLabel.setTranslateY(count.get() * 30);
                count.getAndIncrement();

                Scene scene = new Scene(display);
                scene.getStylesheets().add(Objects.requireNonNull(TradingWindow.class.getResource("/app.css")).toExternalForm());
                st.setScene(scene);
                st.setOnCloseRequest(_ -> Platform.exit());
                st.setResizable(true);
                st.show();

            } catch (IOException | InterruptedException | ParseException | SQLException | ClassNotFoundException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        gridPane.add(loginBtn, 2, 3);

        setMaxWidth(1540);
        setMaxHeight(780);
        getChildren().add(gridPane);
        gridPane.setTranslateY(getMaxHeight() / 3);
        gridPane.setTranslateX(getMaxWidth() / 3);
    }

    // Method to load properties (user settings) from the file
    private @NotNull Properties loadProperties() {
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
