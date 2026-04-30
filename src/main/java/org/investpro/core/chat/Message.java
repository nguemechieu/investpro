package org.investpro.core.chat;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

public class Message {

    public enum MESSAGE_TYPE {
        ERROR,
        SUCCESS,
        WARNING
    }

    public Message(@NotNull Object p0, String s) {

        DialogPane dialogPane = new DialogPane();
        dialogPane.setContentText(s);

        dialogPane.setHeaderText(p0.toString());
        Scene scene = new Scene(dialogPane);
        Stage s1 = new Stage();
        s1.setScene(scene);
        s1.show();
    }
}
