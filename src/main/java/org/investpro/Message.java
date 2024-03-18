package org.investpro;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

public class Message {

    public enum MESSAGE_TYPE {
        ERROR,
        SUCCESS,
        WARNING
    }

    public static MESSAGE_TYPE MessageType;

    public Message(@NotNull Object p0, String s) {

        DialogPane dialogPane = new DialogPane();
        dialogPane.setContentText(s);
        dialogPane.setContent(
                new HBox(
                        new Text(
                                STR."\{p0.toString()} \{s}"
                        )
                )
        );
        Scene scene = new Scene(dialogPane, 400, 300);
        Stage s1 = new Stage();
        s1.setScene(scene);
        s1.show();
    }
}
