package org.investpro;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Background;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class Message extends Stage {
    public Message(@NotNull String title, @NotNull Object message) {
        super();
        setTitle(
                title
        );

        TextArea Txt = new TextArea(message.toString());
        Txt.setEditable(false);
        Txt.setMaxWidth(Double.MAX_VALUE);
        Txt.setMaxHeight(Double.MAX_VALUE);
        Txt.setBackground(
                Background.fill(
                        Paint.valueOf(
                                String.valueOf(Color.BLACK)
                        )
                )
        );
        StackPane dialogPane = new StackPane();
        dialogPane.getChildren().add(
                Txt

        );

        dialogPane.setMaxWidth(Double.MAX_VALUE);
        dialogPane.setMaxHeight(Double.MAX_VALUE);
        dialogPane.setPrefWidth(400);
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
