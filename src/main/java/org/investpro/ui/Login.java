package org.investpro.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class Login extends Stage {
    public Login() {

        AnchorPane vBox = new AnchorPane();

        vBox.setPrefSize(getWidth() / 2, getHeight() / 3);


        Label loginLabel = new Label("Username :");
        TextField loginTextField = new TextField();

        Label passwordLabel = new Label("Password :");
        PasswordField passwordField = new PasswordField();

        GridPane gridPane = new GridPane(

        );

        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.add(loginLabel, 0, 0);
        gridPane.add(loginTextField, 1, 0);
        gridPane.add(passwordLabel, 0, 1);
        gridPane.add(passwordField, 1, 1);


        gridPane.add(new Label("Exchange :"), 0, 2);
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(

                "BINANCE US", "BINANCE", "OANDA", "COINBASE", "BITFINEX", "BITMEX", "BITSTAMP", "BITTREX"
        );
        gridPane.add(comboBox, 1, 2);
        
        Label telegramLabel = new Label("Telegram Token:");
        TextField telegramField = new TextField();
        telegramField.setPromptText("Optional: Bot token for notifications");
        gridPane.add(telegramLabel, 0, 3);
        gridPane.add(telegramField, 1, 3);
        
        Label emailLabel = new Label("Email Notification:");
        TextField emailField = new TextField();
        emailField.setPromptText("Optional: Email for alerts");
        gridPane.add(emailLabel, 0, 4);
        gridPane.add(emailField, 1, 4);
        
        Hyperlink forgotPwd = new Hyperlink("Forgot Password?");
        forgotPwd.setOnAction(_ -> new PasswordReset());
        forgotPwd.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 11px;");
        gridPane.add(
                forgotPwd, 1, 5
        );


        gridPane.setAlignment(Pos.CENTER);
        vBox.getChildren().add(gridPane);

        Button loginButton = new Button("Login");
        loginButton.setOnAction(_ -> {
            if (loginTextField.getText().equals("admin") && passwordField.getText().equals("<PASSWORD>")) {
                close();

            } else if (loginTextField.getText().equals("<USERNAME>") && passwordField.getText().equals("<PASSWORD>")) {
                close();
            } else {


                Connect(this, loginTextField.getText(), passwordField.getText());


            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(_ -> close());


        gridPane.add(loginButton, 2, 7);
        gridPane.add(cancelButton, 1, 7);

        Scene scene = new Scene(vBox);
        scene.getStylesheets().add("/app.css");
        setTitle("Login To Trade");
        setScene(scene);


        show();

    }

    private void Connect(Stage stage, String text, String text1) {
        System.out.println(text);
        System.out.println(text1);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        if (text.equals("admin") && text1.equals("<PASSWORD>")) {
            alert.setContentText("LOGIN_SUCCESS");
            Platform.runLater(alert::showAndWait);
        } else if (text.equals("<>")) {
            alert.setContentText("LOGIN_FAILED");
            Platform.runLater(alert::showAndWait);
        } else if (text.isEmpty()) {
            alert.setContentText("ENTER_USERNAME");
            Platform.runLater(alert::showAndWait);
        } else if (text1.isEmpty()) {
            alert.setContentText("ENTER_PASSWORD");
            Platform.runLater(alert::showAndWait);
        } else {
            //DISPLAY CANDLESTICK CHARTS
            stage.close();
        }
    }
}
