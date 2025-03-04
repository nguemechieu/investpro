package org.investpro;

import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;

public class Messages {

    public Messages(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setTitle("InvestPro Notification");
        alert.setHeaderText("InvestPro");
        alert.setContentText(null); // We will use a TextArea for better readability.

        // Creating a TextArea for multi-line messages
        TextArea textArea = new TextArea(message);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefSize(450, 250);

        // Adjusting alert size
        alert.getDialogPane().setContent(textArea);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);

        alert.showAndWait();
    }
}
