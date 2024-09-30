package org.investpro;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class Messages extends Region {

    private String type;
    private String message;

    public Messages(@NotNull String type, String message) {
        this.type = type;
        this.message = message;

        Stage primaryStage = new Stage();
        AnchorPane anchorPane = new AnchorPane();
        DialogPane dialogPane = new DialogPane();
        Scene scene = new Scene(anchorPane, 400,400);

        dialogPane.setContentText(message);



        // Load images from resources
        String imagePath = "";
        if (type.equalsIgnoreCase("error")) {
            imagePath = "/img/error-48243.png";
        } else if (type.equalsIgnoreCase("info")) {
            imagePath = "/img/info.png";
        } else if (type.equalsIgnoreCase("warning")) {
            imagePath = "/img/warning.png";
        }

        if (!imagePath.isEmpty()) {
            Image image = new Image(Objects.requireNonNull(getClass().getResourceAsStream(imagePath)));
           primaryStage.getIcons().add(image);

            dialogPane.setExpanded(true);
            primaryStage.setTitle(
                    type.equalsIgnoreCase("error")? "Error" : type.equalsIgnoreCase("info")? "Information" : "Warning"
            );
            dialogPane.setPrefWidth(Region.USE_COMPUTED_SIZE);
            dialogPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
            dialogPane.setMinWidth(Region.USE_PREF_SIZE);
            dialogPane.setMinHeight(Region.USE_PREF_SIZE);
            dialogPane.setPadding(new javafx.geometry.Insets(20));
            primaryStage.setAlwaysOnTop(true);

            Text labelInfo = new Text(message);
            labelInfo.setWrappingWidth(message.length() );

            labelInfo.setStyle(

                    "-fx-background-color: rgba(25,225,225);"+
                            "-fx-font-size: 18px;" +
                            "-fx-text-fill: black;" +
                            "-fx-padding: 10;" +
                            "-fx-border-radius: 10;" +
                            "-fx-border-width: 2;" +
                            "-fx-border-color: black;" +
                            "-fx-background-radius: 10;"
            );

            dialogPane.setContent(
                    new HBox(labelInfo)
            );
        }

        anchorPane.getChildren().add(dialogPane);

        primaryStage.setScene(scene);
        primaryStage.showAndWait();
    }



    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
