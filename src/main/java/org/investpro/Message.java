package org.investpro;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class Message extends Stage {
    public Message(@NotNull String title, @NotNull Object message) {
        super();
        setTitle(
                title
        );
        StackPane dialogPane = new StackPane();
        dialogPane.getChildren().add(new VBox(

                new TextArea(message.toString())));

        dialogPane.setMaxWidth(Double.MAX_VALUE);
        dialogPane.setMaxHeight(Double.MAX_VALUE);
        dialogPane.setPrefWidth(500);
        dialogPane.setPrefHeight(400);
        setScene(new Scene(dialogPane));
        setResizable(true);
        setAlwaysOnTop(true);
        show();

    }

    public Message(@NotNull MessageType error, String message) {
        this(error.toString(), message);
    }

    public enum MessageType {
        INFO,
        WARNING,
        ERROR
    }
}
