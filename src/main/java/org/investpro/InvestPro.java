package org.investpro;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

//This is InvestPro's main class
public class InvestPro extends Application {

    public InvestPro() {
        super();


    }



    @Override
    public void start(@NotNull Stage primaryStage) throws SQLException, ClassNotFoundException, ParseException, IOException, InterruptedException {

        primaryStage.setResizable(true);
        Scene scene = new Scene(new TradingWindow());
        primaryStage.setOnCloseRequest(_ -> Platform.exit());
        scene.getStylesheets().add(Objects.requireNonNull(InvestPro.class.getResource("/app.css")).toExternalForm());
        primaryStage.setTitle(STR."InvestPro                  --------- Copyright 2020-\{LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}            TradeAdviser.LLC");
        primaryStage.setScene(scene);
        primaryStage.getIcons().add(
                new Image(
                        Objects.requireNonNull(getClass().getResourceAsStream("/investpro.png"))
                )
        );
        primaryStage.fullScreenProperty().addListener((observable, oldValue, newValue) -> primaryStage.setFullScreen(newValue));

        primaryStage.show();

    }




    @Contract(" -> new")
    private @NotNull MenuBar menuBar() {
        Menu fileMenu = new Menu("FILE");
        fileMenu.setMnemonicParsing(false);
        fileMenu.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));


        MenuItem openMenuItem = new MenuItem("OPEN");
        openMenuItem.setMnemonicParsing(false);
        openMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        openMenuItem.setOnAction(_ -> {
            try {
                new OpenFile();
            } catch (SQLException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        MenuItem saveMenuItem = new MenuItem("SAVE");
        MenuItem saveAsMenuItem = new MenuItem("SAVE AS");
        MenuItem openAcccountMenuItem = new MenuItem("OPEN AN ACCOUNT");
        openAcccountMenuItem.setOnAction(_ -> new CreateAccount());

        MenuItem loginMenuItem = new MenuItem("LOG IN TO  TRADE");


        MenuItem exitMenuItem = new MenuItem("EXIT");
        exitMenuItem.setOnAction(_ -> Platform.exit());

        fileMenu.getItems().addAll(
                openMenuItem, saveMenuItem, saveAsMenuItem, openAcccountMenuItem, loginMenuItem, exitMenuItem
        );

        Menu ViewMenu = new Menu("VIEW");
        Menu EditMenu = new Menu("EDIT");
        Menu HelpMenu = new Menu("CHARTS");
        Menu ToolMenu = new Menu("TOOLS");
        Menu windowMenu = new Menu("WINDOW");
        Menu helpMenu = new Menu("HELP");


        return new MenuBar(
                fileMenu,
                ViewMenu,
                EditMenu,
                HelpMenu,
                ToolMenu,
                windowMenu,
                helpMenu
        );
    }


}
