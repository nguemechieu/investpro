package org.investpro;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import org.investpro.ui.TradingWindow;
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
    public static String CONFIG_FILE;
    public static String CONFIG_FILE2;
    // Capitalized static variables for consistency
    protected static String DB_HOST;
    protected static String DB_NAME;
    protected static String DB_USER;
    protected static String DB_PASSWORD;
    protected static String DB_PORT;
    public static Db1 db1;

    public static void main(String[] args) {
        // Load properties at startup
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        logger.info("InvestPro started at {}", LocalDateTime.now().format(formatter));
        // db1.createTables();
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
            logger.error("⚠ Failed to load properties: {}", e.getMessage(), e);
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
            // Load user-defined width and height from properties
            int width = Integer.parseInt(PROPERTIES.getProperty("window_width", "1530"));
            int height = Integer.parseInt(PROPERTIES.getProperty("window_height", "780"));

            // Assign database properties to static variables
            DB_HOST = PROPERTIES.getProperty("DB_HOST", "localhost");
            DB_NAME = PROPERTIES.getProperty("DB_NAME", "investpro");
            DB_PASSWORD = PROPERTIES.getProperty("DB_PASSWORD", "admin123");
            DB_USER = PROPERTIES.getProperty("DB_USER", "root");
            DB_PORT = PROPERTIES.getProperty("DB_PORT", "3306");

            // Set up the stage with a dynamic title
            primaryStage.setTitle(String.format("%s - © 2020-%s",
                    PROPERTIES.getProperty("app_title", "InvestPro"),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy"))));

            primaryStage.setResizable(true);
            primaryStage.setOnCloseRequest(_ -> Platform.exit());

            // Fullscreen toggle listener
            primaryStage.fullScreenProperty().addListener((_, _, newValue) ->
                    primaryStage.setFullScreen(newValue));


            CONFIG_FILE = "src/main/resources/config.properties";
            CONFIG_FILE2 = "src/main/resources/config2.properties";
            loadProperties();
            db1 = new Db1();
            // Set window icon
            Image icon = new Image(
                    Objects.requireNonNull(InvestPro.class.getResource("/investpro_icon.png")).toExternalForm()
            );
            primaryStage.getIcons().add(icon);
            // Set up the primary scene
            Scene scene = new Scene(new TradingWindow(), width, height);
            scene.getStylesheets().add(Objects.requireNonNull(InvestPro.class.getResource("/css/app.css")).toExternalForm());

            // Set the scene and display the stage
            primaryStage.setScene(scene);


            primaryStage.show();

        } catch (Exception e) {
            logger.error("❌ Application failed to start: {}", e.getMessage(), e);
            new Messages(Alert.AlertType.ERROR, e.getMessage());
        }
    }
}
