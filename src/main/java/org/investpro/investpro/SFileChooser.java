/**
 *
 */
package org.investpro.investpro;

import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

import javax.imageio.ImageIO;
import java.util.Arrays;


public class SFileChooser {

    /**
     * The file chooser.
     */
    private final FileChooser fileChooser = new FileChooser();

    /**
     * Instantiates a new s file chooser.
     */
    public SFileChooser() {
        fileChooser.setTitle("Save Image");

        // Extension Filter + Descriptions
        fileChooser.getExtensionFilters().add(new ExtensionFilter("png", ".png"));
        Arrays.stream(ImageIO.getReaderFormatNames()).filter(s -> s.matches("[a-z]*")).forEach(format -> {
            String description;
            switch (format) {
                case "png" ->
                        description = "Better alternative than GIF or JPG for high colour lossless images, supports translucency";
                case "jpg" -> description = "Great for photographic images";
                case "gif" -> description = "Supports animation, and transparent pixels";
                default -> {
                    description = "." + format;
                    // }
                    fileChooser.getExtensionFilters().add(new ExtensionFilter(format, description));
                }
                // System.out.println(format);
            }
        });

    }

    /**
     * Gets the.
     *
     * @return the FileChooser
     */
    public FileChooser get() {
        return fileChooser;
    }
}
