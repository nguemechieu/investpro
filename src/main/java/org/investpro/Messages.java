package org.investpro;

import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

public class Messages extends Region {

    public Messages(@NotNull Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setTitle("Notification");
        alert.setHeaderText(null);  // No header text to keep it clean

        // Create a TextArea to display the message
        TextArea textArea = new TextArea(message);
        textArea.setWrapText(true);  // Wrap long text
        textArea.setEditable(false); // Prevent editing
        textArea.setPrefSize(800, 300);  // Set preferred size

        // Wrap textArea inside VBox for proper layout
        VBox content = new VBox(textArea);
        content.setPrefSize(800, 300);

        // Set dialog size and content
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setMinSize(700, 300);
        alert.getDialogPane().setPrefSize(800, 350);  // Customize this size as needed
        alert.setResizable(true);
        setPrefSize(
                600, 600
        );

        // Show the dialog and wait for user interaction
        alert.show();

    }
}
