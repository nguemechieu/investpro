package org.investpro;

import javafx.scene.control.Alert;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;

public class Messages extends Region {


    public Messages(@NotNull Alert.AlertType type, String message) {

        Alert
                dialogPane = new Alert(type, message);

        dialogPane.setContentText(message);


        // Load images from resources
        String imagePath = "";
        if (type.equals(Alert.AlertType.ERROR)) {
            imagePath = "/img/error-48243.png";
        } else if (type.equals(Alert.AlertType.INFORMATION)) {
            imagePath = "/img/info.png";
        } else if (type.equals(
                Alert.AlertType.WARNING)) {
            imagePath = "/img/warning.png";
        }

        //  Image image = new Image(Objects.requireNonNull(getClass().getResourceAsStream(imagePath)));

        //dialogPane.getDialogPane().setGraphic(new ImageView(image));

        dialogPane.showAndWait();
    }


}
