package org.investpro;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class ConnectionScene extends Parent {
    public ConnectionScene(@NotNull Exchange exchange) {
        StackPane stackPane = new StackPane();
        GridPane gridPane=new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new javafx.geometry.Insets(10, 10, 10,10));
        gridPane.add(new Label("USER ID :"), 0, 0);
        gridPane.add(new Label(exchange.getName()), 3, 0);
        TextField userId=new TextField();
        userId.setPromptText("Enter your user id");
        gridPane.add(userId,1,0);
        gridPane.add(new Label("API KEY :"),0,1);
        TextField textField = new TextField();
        textField.setPromptText("Enter your API key");

        gridPane.add(textField,1,1);

        gridPane.add(new Label("API SECRET :"),0,2);
        textField=new TextField();
        textField.setPromptText("Enter your api secret");

        gridPane.add(textField,1,2);
        Button btnConnect=new Button("Connect");
gridPane.add(btnConnect,1,3);
        TextField finalTextField = textField;

        btnConnect.setOnAction(event ->

        {if (finalTextField.getText().equals("") || finalTextField.getText() == null) {
            System.out.println("Please enter your user id and api key");
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            //alert.setHeaderText("Please enter your user id and api key");
            alert.setContentText("Please enter your user id and api key");
            alert.showAndWait();

        }

            try {
                exchange.connect(finalTextField.getText(), finalTextField.getText(), userId.getText());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (exchange.isConnected()) {
                btnConnect.setBackground(
                        Background.fill(javafx.scene.paint.Color.GREEN)
                );
            }
            btnConnect.setText(exchange.isConnected() ? "Disconnect" : "Connect");

        });

gridPane.setPadding(new javafx.geometry.Insets(10, 10, 10,10));
gridPane.setHgap(10);
gridPane.setVgap(10);
        gridPane.setAlignment(javafx.geometry.Pos.CENTER);
        gridPane.setPrefSize(600, 300);

        gridPane.setStyle("-fx-background-color: rgb(25,255,25,1);");

        getChildren().add(gridPane);
        Scene scene = new Scene(this);
        Stage stage = new Stage();
        stage.setTitle("Cryptoinvestor");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.centerOnScreen();
        stage.show();

    }
}
