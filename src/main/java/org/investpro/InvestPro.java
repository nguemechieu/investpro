package org.investpro;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Properties;

import static org.investpro.Exchange.logger;

@Getter
@Setter
public class InvestPro extends Application {

    protected static final Properties PROPERTIES = new Properties();
    private static final String CONFIG_FILE = "src/main/resources/config.properties";
    private static final String CONFIG_FILE2 = "src/main/resources/config2.properties";
    // Capitalized static variables for consistency
    protected static String DB_HOST;
    protected static String DB_NAME;
    protected static String DB_USER;
    protected static String DB_PASSWORD;
    protected static String DB_PORT;

    public static void main(String[] args) {
        loadProperties(); // Load properties at startup
        launch(args); // Start JavaFX application
    }

    /**
     * **Loads configuration properties from `config.properties`**
     */
    private static void loadProperties() {
        try (FileInputStream fileInputStream = new FileInputStream(CONFIG_FILE)) {
            PROPERTIES.load(fileInputStream);
            logger.info("✅ Configurations loaded successfully.");
        } catch (IOException e) {
            logger.error("⚠ Failed to load properties: " + e.getMessage(), e);
        }
    }

    /**
     * **Get a property value from the loaded configuration**
     */
    public static String getProperty(String key, String defaultValue) {
        return PROPERTIES.getProperty(key, defaultValue);
    }

    @Override
    public void start(@NotNull Stage primaryStage) {
        try {
            // Load user-defined width & height from properties
            int width = Integer.parseInt(PROPERTIES.getProperty("window_width", "1530"));
            int height = Integer.parseInt(PROPERTIES.getProperty("window_height", "780"));

            // Assign database properties to static variables
            DB_HOST = PROPERTIES.getProperty("DB_HOST", "localhost");
            DB_NAME = PROPERTIES.getProperty("DB_NAME", "default");
            DB_PASSWORD = PROPERTIES.getProperty("DB_PASSWORD", "password");
            DB_USER = PROPERTIES.getProperty("DB_USER", "root");
            DB_PORT = PROPERTIES.getProperty("DB_PORT", "3306");

            // Set window icon
            Image icon = new Image(Objects.requireNonNull(InvestPro.class.getResource("/img/investpro.png")).toExternalForm());
            primaryStage.getIcons().add(icon);

            // Set up the primary scene
            Scene scene = new Scene(new TradingWindow(), width, height);
            scene.getStylesheets().add(Objects.requireNonNull(InvestPro.class.getResource("/app.css")).toExternalForm());

            // Set up the stage with a dynamic title
            primaryStage.setTitle(String.format("%s - © 2020-%s",
                    PROPERTIES.getProperty("app_title", "InvestPro"),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy"))));

            primaryStage.setResizable(true);
            primaryStage.setOnCloseRequest(_ -> Platform.exit());

            // Fullscreen toggle listener
            primaryStage.fullScreenProperty().addListener((_, _, newValue) ->
                    primaryStage.setFullScreen(newValue));

            // Set the scene and display the stage
            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (Exception e) {
            logger.error("❌ Application failed to start: " + e.getMessage(), e);
            Platform.exit();
        }
    }
}
