package org.investpro;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class Login extends Stage {
    public Login() {

        AnchorPane vBox = new AnchorPane();

        vBox.setPrefSize(getWidth() / 3, getHeight() / 3);


        Label loginLabel = new Label("Login :");
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
        Hyperlink forgotPwd = new Hyperlink("Forgot Password?");
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


        gridPane.add(loginButton, 2, 6);
        gridPane.add(cancelButton, 1, 6);

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
            alert.showAndWait();
        } else if (text.equals("<>")) {
            alert.setContentText("LOGIN_FAILED");
            alert.showAndWait();
        } else if (text.isEmpty()) {
            alert.setContentText("ENTER_USERNAME");
            alert.showAndWait();
        } else if (text1.isEmpty()) {
            alert.setContentText("ENTER_PASSWORD");
            alert.showAndWait();
        } else {
            //DISPLAY CANDLESTICK CHARTS
            stage.close();
        }
    }
}
