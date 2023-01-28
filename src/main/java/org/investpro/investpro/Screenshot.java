package org.investpro.investpro;


import javafx.scene.control.Alert;
import javafx.scene.image.Image;

import javafx.embed.swing.SwingFXUtils;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.IOException;


public class Screenshot {

     static void capture(@NotNull File file1) {//Capturing screen image

            System.out.println("Capturing screenshot...");


            try {
                BufferedImage image = SwingFXUtils.fromFXImage(new Image(
                        new File(file1.getAbsolutePath() + ".png").toURI().toURL().openStream()
                ), null);
                image.flush();
                ImageIO.write(image, "png", new File(file1.getAbsolutePath() + ".png"));
                System.out.println("Screenshot saved to " + file1.getAbsolutePath() + ".png");

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Screenshot saved to " + file1.getAbsolutePath() + ".png");
                alert.setHeaderText("Screenshot saved to " + file1.getAbsolutePath() + ".png");
                alert.setContentText("Screenshot saved to " + file1.getAbsolutePath() + ".png");
                alert.showAndWait();





        }
                catch (IOException e) {
                    e.printStackTrace();
                }


}}
