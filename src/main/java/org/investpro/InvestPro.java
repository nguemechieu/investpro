package org.investpro;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import static org.investpro.Exchange.logger;

public class InvestPro extends Application {

    public static void main(String[] args) {
        launch(args);  // JavaFX will handle the application lifecycle
    }

    @Override
    public void start(@NotNull Stage primaryStage) {
        try {
            // Set window icon
            Image icon = new Image(Objects.requireNonNull(InvestPro.class.getResource("/img/investpro.png")).toExternalForm());
            primaryStage.getIcons().add(icon);

            // Set up the primary scene
            Scene scene = new Scene(new TradingWindow(), 1540, 780);
            scene.getStylesheets().add(Objects.requireNonNull(InvestPro.class.getResource("/app.css")).toExternalForm());

            // Set up the stage
            primaryStage.setTitle(String.format("InvestPro - Copyright 2020-%s",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy"))));
            primaryStage.setResizable(true);
            primaryStage.setOnCloseRequest(_ -> Platform.exit());

            // Optional fullscreen listener
            primaryStage.fullScreenProperty().addListener((_, _, newValue) ->
                    primaryStage.setFullScreen(newValue));

            // Set the scene and display the stage
            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (Exception e) {
            // Handle exception in the JavaFX start process
            logger.error(e.getMessage(), e);
            Platform.exit();  // Exit the application if initialization fails
        }
    }
}
