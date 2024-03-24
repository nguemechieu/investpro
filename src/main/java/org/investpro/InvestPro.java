package org.investpro;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.AnchorPane;
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


    int i = 0;
    Stage primaryStage = new Stage();

    public InvestPro() {
        super();

    }

    AnchorPane window = new AnchorPane();

    @Override
    public void start(@NotNull Stage primaryStage) throws SQLException, ClassNotFoundException, ParseException, IOException, InterruptedException {


        window.setMaxSize(
                1540, 780
        );


        window.getStylesheets().add(String.valueOf(Objects.requireNonNull(InvestPro.class.getResource("/app.css"))));

        createMultipleWindows(1);


    }


    // Recursive function to create multiple TradingWindow instances
    public void createMultipleWindows(int count) throws SQLException, ParseException, IOException, InterruptedException, ClassNotFoundException {

        window.getChildren().addAll(new TradingWindow());
        primaryStage.setResizable(true);
        Scene scene = new Scene(window);
        primaryStage.setOnCloseRequest(_ -> Platform.exit());
        scene.getStylesheets().add(Objects.requireNonNull(InvestPro.class.getResource("/app.css")).toExternalForm());
        primaryStage.setTitle(STR."InvestPro                  --------- Copyright 2020-\{LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}            TradeAdviser.LLC");
        primaryStage.setScene(scene);
        primaryStage.show();
        // Recursive call to create the next window after 1000 milliseconds (1 second)



    }
// 1000 milliseconds delay




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

        MenuItem loginMenuItem = new MenuItem("LOG IN TO TRADE");
        loginMenuItem.setOnAction(_ ->
                new Login()

        );

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
