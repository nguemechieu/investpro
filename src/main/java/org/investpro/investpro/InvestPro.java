package org.investpro.investpro;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.dao.UserDao;
import org.investpro.investpro.services.UserService;
import org.investpro.investpro.ui.TradingWindow;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Properties;

import static org.investpro.investpro.Exchange.logger;

@Getter
@Setter
public class InvestPro extends Application {

    protected static final Properties PROPERTIES = new Properties();
    public static Path CONFIG_FILE;
    public static Path CONFIG_FILE2;
    public static Db1 db1;
    protected static String DB_HOST;
    protected static String DB_NAME;
    protected static String DB_USER;
    protected static String DB_PASSWORD;
    protected static String DB_PORT;
    static UserDao dao;
    static UserService userService;
    static int width;
    static int height;

    static {
        CONFIG_FILE = AppFiles.ensureConfigFile("config.properties");
        CONFIG_FILE2 = AppFiles.ensureConfigFile("config2.properties");
        loadProperties();
        configureJavaFxRendering();

        width = Integer.parseInt(PROPERTIES.getProperty("window_width", "1530"));
        height = Integer.parseInt(PROPERTIES.getProperty("window_height", "780"));

        applyConfiguredDatabaseDefaults();
        reinitializeDatabase();
    }

    public static void main(String[] args) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        logger.info("InvestPro started at {}", LocalDateTime.now().format(formatter));
        launch(args);
    }

    private static void loadProperties() {
        PROPERTIES.clear();
        PROPERTIES.putAll(AppFiles.loadProperties(CONFIG_FILE, "config.properties"));
        logger.info("Loaded configuration from {}", CONFIG_FILE);
    }

    private static void configureJavaFxRendering() {
        JavaFxRuntimeBootstrap.configurePrism(PROPERTIES);
    }

    private static void applyConfiguredDatabaseDefaults() {
        DB_HOST = PROPERTIES.getProperty("DB_HOST", "localhost");
        DB_NAME = PROPERTIES.getProperty("DB_NAME", "investpro");
        DB_PASSWORD = PROPERTIES.getProperty("DB_PASSWORD", "");
        DB_USER = PROPERTIES.getProperty("DB_USER", "root");
        DB_PORT = PROPERTIES.getProperty("DB_PORT", "3306");
    }

    public static String getProperty(String key, String defaultValue) {
        return PROPERTIES.getProperty(key, defaultValue);
    }

    public static synchronized boolean reinitializeDatabase() {
        loadProperties();
        applyConfiguredDatabaseDefaults();
        if (db1 != null) {
            db1.close();
        }

        db1 = new Db1();
        if (db1.getEntityManager() != null) {
            dao = new UserDao(db1.getEntityManager());
            userService = new UserService(dao);
            logger.info("Using {}", db1.getDatabaseDescription());
            return true;
        }

        dao = null;
        userService = null;
        logger.warn("Database-backed user features are unavailable because the entity manager did not initialize.");
        return false;
    }

    @Override
    public void start(@NotNull Stage primaryStage) {
        try {
            primaryStage.setTitle(String.format("%s - (c) 2020-%s",
                    PROPERTIES.getProperty("app_title", "InvestPro"),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy"))));

            primaryStage.setResizable(true);
            primaryStage.setOnCloseRequest(event -> Platform.exit());

            primaryStage.fullScreenProperty().addListener((observable, oldValue, newValue) ->
                    primaryStage.setFullScreen(newValue));

            Image icon = new Image(
                    Objects.requireNonNull(InvestPro.class.getResource("/investpro_icon.png")).toExternalForm()
            );
            primaryStage.getIcons().add(icon);

            Scene scene = new Scene(new TradingWindow(), width, height);
            scene.getStylesheets().add(Objects.requireNonNull(
                    InvestPro.class.getResource("/css/app.css")).toExternalForm());

            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (Exception e) {
            logger.error("Application failed to start: {}", e.getMessage(), e);
            new Messages(Alert.AlertType.ERROR, e.getMessage());
        }
    }
}
