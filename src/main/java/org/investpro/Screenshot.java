package org.investpro;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.Date;


public class Screenshot extends Application {
    private static Logger logger = LoggerFactory.getLogger(Screenshot.class);
    private final Node node; // The node to capture (e.g., your chart or the root of your scene)

    public Screenshot(Node node) {
        this.node = node;
    }

    public void captureScreen() {
        // Use Platform.runLater to ensure the capture happens on the JavaFX Application Thread
        Platform.runLater(() -> {
            WritableImage image = node.snapshot(null, null);
            File file = new File("../doucuments/screenshot/investpro_screenshot_" + new Date().getTime() + ".png");

            saveToDisk(image, file);


            //Display the saved image in a separate window (if supported)
            ImageView viewer = new ImageView(image);
            Stage stage = new Stage();
            stage.setScene(new Scene(viewer.getParent()));
            stage.show();


        });
    }

    private void saveToDisk(WritableImage image, File file) {
        try {
            RenderedImage renderedImage = SwingFXUtils.fromFXImage(image, null);
            ImageIO.write(renderedImage, "png", file);
            logger.debug(STR."Screenshot saved to \{file.getAbsolutePath()}");
        } catch (IOException e) {
            logger.error("Error saving screenshot", e);
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        logger.debug("Starting Screenshot application");

    }

}
