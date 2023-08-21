package org.investpro;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Objects;

public class Main extends Application {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);



    public static void main(String[] args) throws Throwable {

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            logger.error(e.getMessage());
            e.printStackTrace();
        });


        launch(args);

    }

    @Override
    public void start(@NotNull Stage primaryStage) throws Exception {

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {

            logger.error(e.getMessage());
            e.printStackTrace();
        });


        TradingWindow tradingWindow;
        try {
            tradingWindow = new TradingWindow();

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        primaryStage.setTitle("InvestPro                    " + new Date());
        primaryStage.setScene(new Scene(tradingWindow));
        primaryStage.setResizable(true);
        primaryStage.sizeToScene();
        primaryStage.setIconified(true);
        primaryStage.centerOnScreen();
        primaryStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/Invest.png"))));

        primaryStage.setOnCloseRequest(event -> {
            logger.info("Application closed");
            System.exit(0);
        });
        primaryStage.show();

    }
}
