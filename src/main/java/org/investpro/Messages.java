package org.investpro;

import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;

public class Messages extends Region {

    public Messages(@NotNull Alert.AlertType type, String message) {
        Alert dialogPane = new Alert(type);
        dialogPane.setResizable(true);

        // Create a TextArea for the message content to handle large or wrapped text
        TextArea textArea = new TextArea(message);
        textArea.setWrapText(true);  // Allows the text to wrap within the area
        textArea.setEditable(false); // Prevents editing the message

        // Set the preferred size of the dialog pane to avoid truncating the message
        dialogPane.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        dialogPane.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);
        dialogPane.getDialogPane().setPrefSize(400, 200);  // Customize this size as needed

        dialogPane.getDialogPane().setContent(textArea);  // Set TextArea as the content of the dialog

        // Show the dialog and wait for user interaction
        dialogPane.showAndWait();
    }
}
