package org.investpro;

import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

public class Message extends Stage {
    public Message(@NotNull String title, @NotNull Object message) {
        super();
        setTitle(
                title
        );

        VBox Txt = new VBox(
                new Text(
                        message.toString()
                )
        );

        Txt.setMaxWidth(Double.MAX_VALUE);
        Txt.setMaxHeight(Double.MAX_VALUE);
        Txt.setBackground(
                Background.fill(
                        Paint.valueOf(
                                String.valueOf(Color.GAINSBORO)
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
