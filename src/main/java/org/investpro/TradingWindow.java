package org.investpro;

import io.github.cdimascio.dotenv.Dotenv;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class TradingWindow extends AnchorPane {

    private static final Logger logger = LoggerFactory.getLogger(TradingWindow.class);
    private Exchange exchange;

    public TradingWindow() {
        getStyleClass().add("trading-window");

        LogUtils.logInfo(STR."TradingWindow \{this}");
        setPrefSize(1540, 800);
        Stage st = new Stage();
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
        gridPane.setVgap(20);
        TextField apiKeyTextField = new TextField();
        apiKeyTextField.setMaxSize(Double.MAX_VALUE, 20);
        apiKeyTextField.setPrefWidth(300);
        gridPane.setHgap(10);
        Label apikeyLabel = new Label("API KEY :");
        apikeyLabel.getStyleClass().add("label");
        gridPane.getStyleClass().add("gridPane");
        gridPane.add(apikeyLabel, 0, 0);
        gridPane.add(apiKeyTextField, 1, 0);
        gridPane.setVgap(20);
        TextField secretKeyTextField = new TextField();
        secretKeyTextField.getStyleClass().add("textfield");
        secretKeyTextField.setMaxSize(Double.MAX_VALUE, 20);
        secretKeyTextField.setPrefWidth(300);

        gridPane.setHgap(20);
        Label secretLabel = new Label("SECRET KEY :");
        secretLabel.getStyleClass().add("label");
        gridPane.add(secretLabel, 0, 1);
        gridPane.add(secretKeyTextField, 1, 1);
        Button cancelBtn = new Button("EXIT");
        cancelBtn.getStyleClass().add("button");
        cancelBtn.setOnAction(_ -> Platform.exit());
        gridPane.add(cancelBtn, 1, 3);
        Button loginBtn = new Button("LOAD ");
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
                case "BINANCE US" -> exchange = new BinanceUs(apiKeyTextField.getText(), secretKeyTextField.getText());
                case "BINANCE" -> {
                    try {
                        exchange = new Binance(apiKeyTextField.getText(), secretKeyTextField.getText(), TradePair.of("BTC", "USD"));
                    } catch (ClassNotFoundException | SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                case "OANDA" -> {
                    try {
                        exchange = new OandaExchange("001-001-2783446-006", "40789798294e32fbb7f34f0c7854ca07-1b06171885e2ee29ef23420c348266cb");
                    } catch (IOException | InterruptedException | ExecutionException | TimeoutException | SQLException |
                             ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
                case "COINBASE" -> {


                    // Load environment variables
                    Dotenv dotenv = Dotenv.load();
                    String privateKeyPEM = dotenv.get("COINBASE_PRIVATE_API_KEY").replace("\\n", "\n");
                    String name = dotenv.get("COINBASE_API_KEY_NAME");

                    try {
                        exchange = new Coinbase(name, privateKeyPEM);
                    } catch (UnsupportedEncodingException | NoSuchAlgorithmException | InvalidKeyException e) {
                        throw new RuntimeException(e);
                    }
                }

                default ->
                        throw new IllegalStateException(STR."Unexpected value: \{comboBox.getSelectionModel().getSelectedItem()}");
            }

            st.setTitle(STR."\{comboBox.getSelectionModel().getSelectedItem()}  @InvestPro                    Copyright 2020-" + new Date());
            try {

                DisplayExchange display = new DisplayExchange(exchange);
                display.getStyleClass().add("display-exchange");
                Label versionLabel = new Label(STR."Version: " + InvestPro.class.getPackage().getImplementationVersion());
                versionLabel.getStyleClass().add("label");
                versionLabel.setTranslateX(10);
                versionLabel.setTranslateY(10);
                getChildren().add(versionLabel);
                Label exchangeNameLabel = new Label(STR."Exchange: \{comboBox.getSelectionModel().getSelectedItem()}");
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
                alert.setHeaderText(null);
                alert.setContentText(e.getMessage());
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
}