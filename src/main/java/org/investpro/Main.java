package org.investpro;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Objects;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static java.lang.System.nanoTime;

public class Main extends Application {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public Main() {
        logger.info("CryptoInvestor started " + new Date());
    }

    public static void main(String[] args) throws Throwable {

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            logger.error("CryptoInvestor " + nanoTime());
            logger.error(e.getMessage());
            e.printStackTrace();
        });
        Preferences preferences = Preferences.userNodeForPackage(
                Main.class
        );
        preferences.put("version", String.valueOf(0.01));
        preferences.put("last_update", String.valueOf(new Date()));
        preferences.put("username", "root");
        preferences.put("password", "root307#");
        try {
         preferences.exportNode(new FileOutputStream(System.getProperty("user.home") + "/.config/cryptoinvestor.xml"));


        } catch (BackingStoreException | IOException e) {
            logger.error(e.getMessage());
            throw new RuntimeException(e);
        }


        preferences.flush();

        launch(args);
        logger.info("Application started");
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
