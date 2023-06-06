package org.investpro;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Screenshot {
    static void capture(@NotNull File file1) {//Capturing screen image
        try {
            Thread.sleep(500);
            Robot r = new Robot();
            // Used to get ScreenSize and capture image

            if (!file1.exists()) {
                file1.createNewFile();
            }


            Rectangle capture = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage Image = r.createScreenCapture(capture);
            ImageIO.write(Image, "png", new File(file1.getAbsolutePath()));
            System.out.println("Screenshot saved");

            new Message(
                    file1.getAbsolutePath(),
                    "Screenshot Saved"
            );
            //Display Screenshot
        } catch (InterruptedException | IOException | AWTException e) {
            throw new RuntimeException(e);
        }
    }
}