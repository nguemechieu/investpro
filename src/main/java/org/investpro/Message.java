package org.investpro;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Border;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class Message extends Exception {

    public enum MESSAGE_TYPE {
        ERROR,
        SUCCESS,
        WARNING
    }



    public Message(@NotNull Object p0, String s) {

        AnchorPane pane = new AnchorPane();
        pane.setBorder(Border.stroke(
                Color.PINK
        ));
        pane.setStyle("-fx-background-color: black;");
        DialogPane dialogPane = new DialogPane();
        dialogPane.setContentText(s);
        dialogPane.setHeaderText(p0.toString());
        pane.getChildren().add(dialogPane);
        pane.setPrefWidth(300);
        pane.setPrefHeight(200);
        pane.setMaxWidth(600);
        pane.setMaxHeight(400);
        Scene scene = new Scene(pane);
        Stage s1 = new Stage();
        s1.setScene(scene);
        s1.setResizable(false);
        s1.getIcons().add(new javafx.scene.image.Image(Objects.requireNonNull(Message.class.getResourceAsStream("/invest.png"))));
        s1.show();
    }
}
