package org.investpro;

import javafx.scene.control.Alert;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Screenshot {

    static void capture(File file1) {//Capturing screen image

        System.out.println("Capturing screenshot...");
        Alert alertAlert = new Alert(Alert.AlertType.INFORMATION);

        alertAlert.setTitle("Screenshot");
        alertAlert.setHeaderText(null);
        alertAlert.setContentText("Capturing Screenshot...");

        alertAlert.showAndWait();
        Alert alert = new Alert(Alert.AlertType.INFORMATION);

        try {
            Thread.sleep(500);

            Robot r = new Robot();

            // Used to get ScreenSize and capture image
            Rectangle capture = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage Image = r.createScreenCapture(capture);
            ImageIO.write(Image, "png", file1);
            System.out.println("Screenshot saved");
            alert.setTitle("Screenshot");
            alert.setContentText("Screenshot saved to directory  " + file1.getAbsolutePath());
            alert.showAndWait();
            //Display Screenshot
        } catch (InterruptedException | IOException | AWTException e) {
            throw new RuntimeException(e);
        }
    }
}