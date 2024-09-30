package org.investpro;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;


//This is InvestPro's main class
public class InvestPro extends Application {



    public InvestPro() {
        super();

        
        


        

    }




    @Override
    public void start(@NotNull Stage primaryStage) throws SQLException, ClassNotFoundException, ParseException, IOException, InterruptedException {


        Scene scene = new Scene(new TradingWindow());
        primaryStage.setResizable(true);
        primaryStage.setOnCloseRequest(_ -> Platform.exit());
        primaryStage.setTitle(   "InvestPro                  --------- Copyright 2020-"
                +
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        primaryStage.setScene(scene);
        primaryStage.getIcons().add(
                new Image(
                        Objects.requireNonNull(getClass().getResourceAsStream("/investpro.png"))
                )
        );
        primaryStage.fullScreenProperty().addListener((_, _, newValue) -> primaryStage.setFullScreen(newValue));
        scene.getStylesheets().add(Objects.requireNonNull(InvestPro.class.getResource("/app.css")).toExternalForm());

        primaryStage.show();

    }





}
