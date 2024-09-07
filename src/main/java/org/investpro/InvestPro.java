package org.investpro;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;


//This is InvestPro's main class
public class InvestPro extends Application {


    static final LogUtils logUtils = new LogUtils();

    public InvestPro() {
        super();


    }




    @Override
    public void start(@NotNull Stage primaryStage) throws SQLException, ClassNotFoundException, ParseException, IOException, InterruptedException {

        // Set up the logger to write to a log file
        LogUtils.setupLogger("InvestPro");
        // Log different levels of messages
        LogUtils.logInfo("This is an info message");
        LogUtils.logWarning("This is a warning message");
        LogUtils.logSevere("This is a severe message");
        Scene scene = new Scene(new TradingWindow());
        primaryStage.setResizable(true);
        primaryStage.setOnCloseRequest(_ -> Platform.exit());
        primaryStage.setTitle(STR."InvestPro                  --------- Copyright 2020-\{LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}            TradeAdviser.LLC");
        primaryStage.setScene(scene);
        primaryStage.getIcons().add(
                new Image(
                        Objects.requireNonNull(getClass().getResourceAsStream("/investpro.png"))
                )
        );
        primaryStage.fullScreenProperty().addListener((observable, oldValue, newValue) -> primaryStage.setFullScreen(newValue));
        scene.getStylesheets().add(Objects.requireNonNull(InvestPro.class.getResource("/app.css")).toExternalForm());

        primaryStage.show();

    }





}
