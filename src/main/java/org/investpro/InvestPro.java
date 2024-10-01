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


    static final DbHibernate db1;

    static {
        db1 = new DbHibernate();
    }


    public InvestPro() throws SQLException {
        super();





    }

    public static void main(String[] args) throws SQLException {
        InvestPro app = new InvestPro();
        app.executeTransaction();
        launch(args);
    }

    public void executeTransaction() throws SQLException {

        // Perform your database operations here.

        db1.createTables();// create all required tab

    }

    @Override
    public void start(@NotNull Stage primaryStage) throws SQLException, ClassNotFoundException, ParseException, IOException, InterruptedException {
        Scene scene = new Scene(new TradingWindow());
        primaryStage.setResizable(true);
        primaryStage.setOnCloseRequest(_ -> Platform.exit());
        primaryStage.setTitle("InvestPro                  --------- Copyright 2020-%s".formatted(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
        primaryStage.setScene(scene);

        primaryStage.fullScreenProperty().addListener((_, _, newValue) -> primaryStage.setFullScreen(newValue));
        scene.getStylesheets().add(Objects.requireNonNull(InvestPro.class.getResource("/app.css")).toExternalForm());
        Image icon = new Image(
                Objects.requireNonNull(InvestPro.class.getResource("/investpro.png")).toExternalForm()
        );
        primaryStage.getIcons().add(icon);
        primaryStage.show();

    }


}
