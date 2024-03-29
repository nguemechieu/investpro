package org.investpro;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Border;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Objects;

class TradingWindow extends AnchorPane {

    private static final Logger logger = LoggerFactory.getLogger(TradingWindow.class);
    private Exchange exchange;

    public TradingWindow() {
        logger.debug("TradingWindow constructor called");
        setPrefSize(1540, 800);
        setMinSize(1540, 780);
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.add(new Label("Exchange :"), 0, 2);
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.setValue("Select Exchange");
        comboBox.getItems().addAll("BINANCE US", "BINANCE", "OANDA", "COINBASE", "BITFINEX");
        gridPane.add(comboBox, 1, 2);
        gridPane.setVgap(10);
        TextField apiKeyTextField = new TextField();
        gridPane.setHgap(10);
        Label apikeyLabel = new Label("API KEY :");
        gridPane.add(apikeyLabel, 0, 0);
        gridPane.add(apiKeyTextField, 1, 0);
        gridPane.setVgap(10);
        TextField secretKeyTextField = new TextField();
        gridPane.setHgap(10);
        Label secretLabel = new Label("SECRET KEY :");
        gridPane.add(secretLabel, 0, 1);
        gridPane.add(secretKeyTextField, 1, 1);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(_ -> Platform.exit());
        gridPane.add(cancelBtn, 1, 3);
        Button loginBtn = new Button("Login");
        loginBtn.setOnAction(_ -> {
            if (apiKeyTextField.getText().isEmpty() || secretKeyTextField.getText().isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("Please enter your API KEY and SECRET KEY");
                alert.showAndWait();
                    }

            if (comboBox.getValue() == null) return;
            switch (comboBox.getSelectionModel().getSelectedItem()) {
                case "BINANCE US" -> exchange = new BinanceUs(apiKeyTextField.getText(), secretKeyTextField.getText());

                case "BINANCE" -> exchange = new Binance(apiKeyTextField.getText(), secretKeyTextField.getText());

                case "OANDA" -> {
                    exchange = new Oanda("001-001-2783446-006", "12dc5e966232a9d36ff6f944e14440ee-569a276c6217310f43d9fadd331702da");
                }
                case "COINBASE" -> exchange = new Coinbase(apiKeyTextField.getText(), secretKeyTextField.getText());

                case "BITFINEX" -> exchange = new Bitfinex(apiKeyTextField.getText(), secretKeyTextField.getText());

                case "BITMEX" -> exchange = new Bitmex(apiKeyTextField.getText(), secretKeyTextField.getText());
                default ->
                        throw new IllegalStateException(STR."Unexpected value: \{comboBox.getSelectionModel().getSelectedItem()}");
            }
            Stage st = new Stage();
            st.setTitle(STR."\{comboBox.getSelectionModel().getSelectedItem()}  @InvestPro                              Copyright 2020-" + new Date());
            st.setScene(new Scene(new DisplayExchange(exchange)));
            st.setResizable(true);
            st.show();
        });
        gridPane.add(loginBtn, 2, 3);
        setBorder(Border.stroke(Color.BLUE));
        setPadding(new Insets(10, 10, 10, 1));
        setMinWidth(700);
        setMinHeight(500);
        setMaxWidth(1540);
        setMaxHeight(800);
        getChildren().add(gridPane);
        gridPane.setTranslateY(getMaxHeight() / 3);
        gridPane.setTranslateX(getMaxWidth() / 3);

        getStylesheets().add(Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm());


    }
}