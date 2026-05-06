package org.investpro.ui;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class CreateAccount extends Stage {
    public CreateAccount() {
        super();
        AnchorPane anc = new AnchorPane();
        anc.getChildren().add(
                new Label("Create an account"));

        Label infoLabel = new Label("Account creation interface\nPlease configure your trading account settings");
        infoLabel.setPrefSize(1000, 700);
        infoLabel.setLayoutX(0);
        infoLabel.setLayoutY(30);
        infoLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #64748b; -fx-padding: 20;");
        anc.getChildren().add(infoLabel);

        Scene scene = new Scene(anc, 500, 500);

        scene.getStylesheets().add("/app.css");
    }
}
