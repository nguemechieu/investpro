package org.investpro.investpro;

import com.jfoenix.controls.RecursiveTreeItem;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.*;

import static java.lang.System.nanoTime;
import static java.lang.System.out;
import static org.investpro.investpro.OandaClient.*;
import static org.investpro.investpro.PLATFORM.COINBASE_PRO;


public class Main extends Application {

    public static final int TRADING_SCREEN_WIDTH = 1530;
    public static final int TRADING_SCREEN_HEIGHT = 780;
    public static final int TRADING_SCREEN_MINIMUM_HEIGHT = 500;
    public static final int TRADING_SCREEN_MINIMUM_WIDTH = 500;
    public static final int TRADING_SCREEN_MAXIMUM_WIDTH = (int) Screen.getPrimary().getBounds().getWidth();
    public static final int TRADING_SCREEN_MAXIMUM_HEIGHT = (int) Screen.getPrimary().getBounds().getHeight();
    static Alert alert;

    private static @NotNull Pane panel() throws URISyntaxException {
        Pane pane = new Pane();

//Create menu
        MenuBar menubar = new MenuBar();
        List<Menu> menuElements =
                Arrays.asList(
                        new Menu("File"),
                        new Menu("View"),
                        new Menu("Tools"),
                        new Menu("Charts"),
                        new Menu("Insert"),
                        new Menu("Window"),
                        new Menu("Settings")
                        , new Menu("Browser"),
                        new Menu("Help"),
                        new Menu("Languages"),
                        new Menu("About")


                );


//Adding languages
        Menu menuLanguage = new Menu("Languages");
        menuLanguage.getItems().addAll(
                Arrays.asList(
                        new MenuItem("English"),
                        new MenuItem("French"),
                        new MenuItem("German"),
                        new MenuItem("Italian"),
                        new MenuItem("Japanese"),
                        new MenuItem("Spanish"),
                        new MenuItem("Portuguese"),
                        new MenuItem("Russian"),
                        new MenuItem("Korean"),
                        new MenuItem("Chinese"),
                        new MenuItem("Vietnamese"),
                        new MenuItem("Arabic"),
                        new MenuItem("Armenian"),
                        new MenuItem("Hindi"),
                        new MenuItem("Thai"),
                        new MenuItem("Turkish")
                )
        );
        menuElements.get(9).getItems().addAll(menuLanguage);

//Adding menu items
        menuElements.get(0).getItems().addAll(
                new MenuItem("Sign in"),
                new MenuItem("Profile"),
                new MenuItem("Open Data Folder"),
                new MenuItem("Save"),
                new MenuItem("Save As"),
                new MenuItem("Print "),
                new MenuItem("Print Preview"));

        //Controlling menu events


        menuElements.get(0).getItems().get(0).setOnAction(e -> {
            Label usernameLabel = new Label("Username ");
            TextField usernameTexfield = new TextField();
            usernameTexfield.setPromptText("Enter username");
            Label passwordLabel = new Label("Password ");
            TextField passwordTexfield = new TextField(" ");
            passwordTexfield.setPromptText("Enter password");
            GridPane gridPane = new GridPane();
            gridPane.setHgap(10);
            gridPane.setVgap(10);
            gridPane.setTranslateX(200);
            gridPane.setTranslateY(50);
            gridPane.setPadding(new Insets(10, 10, 10, 10));
            gridPane.add(usernameLabel, 0, 0);
            gridPane.add(usernameTexfield, 1, 0);
            gridPane.add(passwordLabel, 0, 1);
            gridPane.add(passwordTexfield, 1, 1);
            gridPane.add(new Label("API KEY"), 0, 2);
            TextField apiKeyLabel = new TextField("");
            apiKeyLabel.setPromptText(
                    "Enter API KEY"
            );
            gridPane.add(apiKeyLabel, 1, 2);
            gridPane.add(new Label("API SECRET"), 0, 3);
            TextField apiSecretLabel = new TextField("");
            apiSecretLabel.setPromptText("Enter api secret or leave it empty");
            gridPane.add(apiSecretLabel, 1, 3);
            gridPane.add(new Label("Account ID"), 0, 4);
            TextField accountIdLabel = new TextField("");
            accountIdLabel.setPromptText("Enter account id ");
            gridPane.add(accountIdLabel, 1, 4);
            Button signInButton = new Button("Sign In");
            PLATFORM platform = COINBASE_PRO;
            signInButton.setOnAction(rr -> {

                String username = usernameTexfield.getText();
                String password = passwordTexfield.getText();

                if (username.equals("") || password.equals("")) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Please enter username and password");

                    alert.showAndWait();
                } else {
                    try {
                        String apiKey = apiKeyLabel.getText();
                        String apiSecret = apiSecretLabel.getText();
                        String accountId = accountIdLabel.getText();
                        if (apiKey.equals("") || apiSecret.equals("") || accountId.equals("")) {
                            Alert alert = new Alert(Alert.AlertType.ERROR, "Please enter api key," +
                                    "");
                            alert.showAndWait();
                        } else {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("Connect...");
                            alert.setContentText("Connecting to " + (platform) + " ...");
                            OandaClient.setApi_Key(apiKey);
                            OandaClient.accountID = (accountId);
                            switch (platform) {
                                case BINANCE_US:
                                    alert.showAndWait();

                                case Ally_Invest:
                                    alert.showAndWait();

                                case COINBASE_PRO:
                                    alert.showAndWait();
                                case BINANCE_COM:
                                    alert.showAndWait();
                                default:
                                    alert.setContentText("Unsupported Broker ...");

                            }
                        }


                    } catch (Exception r) {
                        Alert alert = new Alert(Alert.AlertType.ERROR, r.getMessage());
                        alert.showAndWait();

                    }

                }
            });
            HBox hbox = new HBox();
            signInButton.setTranslateX(
                    gridPane.getTranslateX() + gridPane.getWidth() / 2 - signInButton.getWidth() / 2
            );
            signInButton.setTranslateY(
                    400
            );
            hbox.getChildren().addAll(gridPane, signInButton);

            VBox vbox = new VBox();
            vbox.setPadding(new Insets(10, 10, 10, 10));
            vbox.setSpacing(10);
            vbox.getChildren().addAll(hbox);

            Stage s = new Stage();
            ObservableList<PLATFORM> observableValue = FXCollections.observableArrayList();

            observableValue.addAll(PLATFORM.values());
            ComboBox<PLATFORM> comboBox = new ComboBox<>(
                    observableValue

            );

            comboBox.setTranslateX(20);
            comboBox.setTranslateY(50);
            comboBox.setPromptText("Select Exchange");
            comboBox.setOnAction(selectEvent -> {
                platform.setValue(comboBox.getValue().getValue());
            });
            StackPane pane0 = new StackPane(hbox, comboBox);

            Scene scene = new Scene(pane0, 800, 600);
            scene.getStylesheets().add("app.css");

            s.setScene(scene);
            s.setTitle("Exchange -->Login");

            s.show();
        });

        menuElements.get(0).getItems().get(1).setOnAction(e -> {

            MenuItem item = (MenuItem) e.getSource();
            out.println(item.getText());
            Stage s = new Stage();
            VBox vbox = new VBox();

            Parent stackPane = new StackPane(vbox);
            s.setScene(new Scene(stackPane, 800, 600));
            s.setTitle("Profile");
            s.setResizable(true);
            s.show();

        });
        menuElements.get(0).getItems().get(2).setOnAction(e -> {

            FileChoosers fileChoosers = new FileChoosers();
            try {
                fileChoosers.start();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

        });


        //Print preview
        menuElements.get(0).getItems().get(3).setOnAction(e -> {
            MenuItem item = (MenuItem) e.getSource();
            out.println(item.getText());

            Printer.getDefaultPrinter().createPageLayout(
                    Paper.A4,
                    PageOrientation.PORTRAIT,
                    10,
                    10,
                    20,
                    10
            );

            Printer.getDefaultPrinter().getPrinterAttributes().getDefaultPageOrientation();


        });
        menuElements.get(0).getItems().get(4).setOnAction(e -> {
            MenuItem item = (MenuItem) e.getSource();
            out.println(item.getText());

            Printer.getDefaultPrinter().createPageLayout(
                    Paper.A4,
                    PageOrientation.LANDSCAPE,
                    10,
                    10,
                    20,
                    20);
        });


        menuElements.get(1).getItems().addAll(
                new Menu("Settings"),
                new Menu("Navigate"),
                new MenuItem("Infos"),
                new Menu("Telegram "));

        menuElements.get(2).getItems().addAll(
                new Menu("Toolbar"),
                new Menu("Status Bar"),
                new Menu("Chart Bar"),
                new Menu("Symbols"),
                new Menu("Market Watch"),
                new Menu("Navigator"),
                new MenuItem("Terminal"),
                new MenuItem("Strategy Tester"),
                new MenuItem("FullScreen"));
        menuElements.get(2).getItems().get(4).setOnAction(//Display Market Symbols
                e -> {

                    StackPane stackPane = new StackPane();
                    VBox vbox = new VBox();
                    vbox.setPadding(new Insets(10, 10, 10, 10));
                    vbox.setSpacing(10);
                    vbox.setAlignment(Pos.CENTER);

                    ListView<String> symListView = new ListView<>();
                    try {
                        symListView.getItems().addAll(getTradeAbleInstruments());
                    } catch (OandaException ex) {
                        throw new RuntimeException(ex);
                    }
                    vbox.getChildren().add(symListView);
                    stackPane.getChildren().add(vbox);

                    Stage s = new Stage();
                    s.setScene(new Scene(stackPane, 800, 600));
                    s.setTitle("Market Symbol");
                    s.setResizable(true);
                    s.show();
                }
        );
        menuElements.get(2).getItems().get(5).setOnAction(
                e -> {
                    Stage s = new Stage();
                    VBox vBox = new VBox();
                    Parent stackPane = new StackPane(vBox);
                    s.setScene(new Scene(stackPane, 800, 600));
                    s.setTitle("Market Watch");
                    s.setResizable(true);
                    s.show();
                }
        );
        menuElements.get(2).getItems().get(7).setOnAction(
                e -> {
                    Stage s = new Stage();

                    s.setTitle("Terminal");
                    s.setResizable(true);
                    s.show();
                }
        );
        menuElements.get(2).getItems().get(8).setOnAction(event -> {
                    Stage s = new Stage();
                    VBox vBox = new VBox();
                    vBox.setSpacing(10);
                    vBox.setPadding(new Insets(10, 10, 10, 10));
                    vBox.setAlignment(Pos.CENTER);
                    vBox.getChildren().add(new Label("Hello World now you can test your trade strategy!"));
                    Parent stackPane = new StackPane(vBox);
                    s.setScene(new Scene(stackPane, 800, 600));
                    s.setTitle("Strategy Tester");
                    s.setResizable(true);
                    s.show();
                }
        );
        menuElements.get(2).getItems().get(8).setOnAction(e -> {
            //Resizing Main Window
            Stage s = new Stage();
            VBox vBox = new VBox();
            vBox.setSpacing(10);
            vBox.setPadding(new Insets(10, 10, 10, 10));
            vBox.setAlignment(Pos.CENTER);
            vBox.getChildren().add(new Label("Hello World now you can test your trade strategy" + ""));
            Parent stackPane = new StackPane(vBox);
            s.setScene(new Scene(stackPane, 800, 600));
            s.setTitle("Strategy Tester");
            s.setResizable(true);
            s.show();

        });


        menuElements.get(3).getItems().addAll(
                new MenuItem("Indicators"),
                new MenuItem("Lines"),
                new MenuItem("Channels"),
                new MenuItem("Gain"),
                new MenuItem("Fibonacci"),
                new MenuItem("Shape"),
                new MenuItem("Arrows"),
                new MenuItem("Andrew 's Pitchfork "),
                new MenuItem("Circle lines"));
        menuElements.get(3).getItems().get(1).setOnAction(e -> {
            Stage s = new Stage();
            VBox vBox = new VBox();
            vBox.setSpacing(10);
            vBox.setPadding(new Insets(10, 10, 10, 10));
            vBox.setAlignment(Pos.CENTER);
            vBox.getChildren().add(new Label("Hello World now you can test your trade strategy" +
                    ""));
            Parent stackPane = new StackPane(vBox);
            s.setScene(new Scene(stackPane, 800, 600));
            s.setTitle("Indicator");
            s.setResizable(true);
            s.show();

        });
        menuElements.get(3).getItems().get(1).setOnAction(e -> {

        });
        menuElements.get(3).getItems().get(2).setOnAction(e -> {
        });
        menuElements.get(3).getItems().get(3).setOnAction(e -> {
        });
        menuElements.get(3).getItems().get(4).setOnAction(e -> {
        });
        menuElements.get(3).getItems().get(5).setOnAction(e -> {
        });
        menuElements.get(3).getItems().get(6).setOnAction(e -> {
        });
        menuElements.get(3).getItems().get(7).setOnAction(e -> {
        });
        menuElements.get(3).getItems().get(8).setOnAction(e -> {
        });


        menuElements.get(4).getItems().addAll(
                new MenuItem("Objects"),
                new MenuItem("BarChart"),
                new MenuItem("CandleStick"),
                new MenuItem("Lines' Chart"),
                new MenuItem("ForeGround"),
                new MenuItem("Background"),
                new MenuItem("TimesFrame"),
                new MenuItem("Templates"),
                new MenuItem("Refresh"),
                new MenuItem("Grid"),
                new MenuItem("Volume"),
                new MenuItem("Auto Scroll"),
                new MenuItem("Chart Shift"),
                new MenuItem("Zoom In"),
                new MenuItem("Zoom Out"),
                new MenuItem("Properties"));
        menuElements.get(4).getItems().get(0).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(1).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(2).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(3).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(4).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(5).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(6).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(7).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(8).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(9).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(10).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(11).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(12).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(13).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(14).setOnAction(e -> {
        });
        menuElements.get(4).getItems().get(15).setOnAction(e -> {
        });


        menuElements.get(5).getItems().addAll(
                new MenuItem("New Order"),
                new MenuItem("Global variables"),
                new MenuItem("History Center"),
                new MenuItem("Options")
        );


        menuElements.get(5).getItems().get(0).setOnAction(e -> {

            VBox vbox = new VBox();

            Button btnBuy = new Button("Buy Order");

            Button btnSell = new Button("Sell Order");
            Spinner<Double> spinner1 = new Spinner<>();
            spinner1.setPromptText("Lot size: " + 0.01);

            ObservableList<Double> obList = FXCollections.observableArrayList();

            obList.addAll(0.01, 0.02, 0.03, 0.04, 0.05, 0.06);

            SpinnerValueFactory<Double> valueFactory = new SpinnerValueFactory.ListSpinnerValueFactory<>(obList);
            spinner1.setInitialDelay(Duration.ONE);
            spinner1.setValueFactory(valueFactory);
            spinner1.decrement(-1);
            spinner1.increment(1);
            ComboBox<String> combo_box = new ComboBox<>();
            try {
                combo_box.getItems().addAll(OandaClient.getTradeAbleInstruments());
            } catch (OandaException ex) {
                throw new RuntimeException(ex);
            }

            btnSell.setTranslateX(190);
            btnSell.setTranslateY(50);
            btnBuy.setTranslateX(90);
            btnBuy.setTranslateY(50);
            combo_box.setTranslateX(90);
            combo_box.setTranslateY(60);
            spinner1.setTranslateX(200);
            spinner1.setTranslateY(150);

            vbox.getChildren().addAll(
                    btnBuy, btnSell, spinner1, combo_box
            );
            StackPane pane1 = new StackPane();
            pane1.getChildren().addAll(vbox);
            Scene sceneScene = new Scene(pane1, 800, 600);
            Stage stage = new Stage();
            stage.setScene(sceneScene);
            stage.setTitle("New Order");
            stage.show();

        });


        //Global variables

        menuElements.get(5).getItems().get(1).setOnAction(e -> {

            VBox vbox = new VBox();

            ListView<SymbolData> tickListView = new ListView<>();

            vbox.getChildren().addAll(tickListView);
            StackPane pane3 = new StackPane();
            pane3.getChildren().addAll(vbox);
            Scene sceneScene = new Scene(pane3, 800, 600);
            Stage stage = new Stage();
            stage.setScene(sceneScene);
            stage.setTitle("Global Variables");
            stage.show();

        });

        menuElements.get(5).getItems().get(2).setOnAction(e -> {

            VBox vbox = new VBox();
            StackPane pane6 = new StackPane();
            pane6.getChildren().addAll(vbox);
            Scene sceneScene = new Scene(pane6, 800, 600);
            Stage stage = new Stage();
            stage.setScene(sceneScene);
            stage.setTitle("History Center");
            stage.show();

        });


        menuElements.get(5).getItems().get(3).setOnAction(e -> {
            VBox vbox = new VBox();
            StackPane pane7 = new StackPane();
            pane7.getChildren().addAll(vbox);
            Scene sceneScene = new Scene(pane7, 800, 600);
            Stage stage = new Stage();
            stage.setScene(sceneScene);
            stage.setTitle("Options");
            stage.show();

        });


        menuElements.get(6).getItems().addAll(
                new MenuItem("New Window"),
                new MenuItem("Title Window"),
                new MenuItem("Cascade Window"));

        menuElements.get(6).getItems().get(0).setOnAction(e ->
        {
            Scene scene = new Scene(new Group(new StackPane()));

            StackPane stackPane = new StackPane();
            scene.setRoot(stackPane);
            Stage s = new Stage();
            s.setTitle("New Window");
            s.setScene(scene);
            s.show();
        });
        menuElements.get(6).getItems().get(1).setOnAction(e -> {
            StackPane stackPane = new StackPane();
            Scene scene = new Scene(stackPane);
            Stage s = new Stage();
            s.setTitle("Title Window");
            s.setScene(scene);
            s.show();
        });

        menuElements.get(6).getItems().get(2).setOnAction(e -> new CascadeWindow(800, 680));


        menuElements.get(7).getItems().addAll(//Browser
                new MenuItem("Browser"),
                new MenuItem("Online search"),
                new MenuItem("About"));
        menuElements.get(7).getItems().get(0).setOnAction(e -> {
            Browser browser = null;
            try {
                browser = new Browser();
                browser.start();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }


        });
        menuElements.get(7).getItems().get(1).setOnAction(e -> {
            //TODO Online search


        });
        menuElements.get(7).getItems().get(2).setOnAction(e -> {
            //TODO MODIFY TEXT

            Stage stage = new Stage();
            VBox vBox = new VBox();
            GridPane grid_pane = new GridPane();
            grid_pane.setHgap(10);
            grid_pane.setVgap(10);
            grid_pane.setPadding(new Insets(10, 10, 10, 10));
            grid_pane.setAlignment(Pos.CENTER);
            grid_pane.add(new Label("About"), 0, 0);
            vBox.getChildren().add(new Label("InvestPro " + new Date()));

            stage.setScene(new Scene(new Group(new StackPane(vBox))));
            stage.show();
        });


        // instrumentsList.get(i).replace("/", "_");


        pane.setPadding(new Insets(10, 10, 10, 10));

        ToolBar toolBar1 = new ToolBar();
        toolBar1.setTranslateY(430);
        toolBar1.setTranslateX(200);
        toolBar1.setPrefSize(
                1000,
                50
        );
        toolBar1.setOrientation(
                Orientation.HORIZONTAL
        );
        Button[] buttons = new Button[10];
        for (int i = 0; i < 10; i++) {
            buttons[i] = new Button("BUY");
        }
        buttons[0].setText("BUY");
        //buttons[0] action
        buttons[0].setOnAction(
                event -> {//Open Market Buy Orders
                    try {
                        TextField amountField = new TextField("amount");
                        int amount = Integer.parseInt(amountField.getText());
                        if (amount > 0) {


                            // TextField priceField = new TextField("price");
                            String symbol9 = instrumentsList.get(Integer.parseInt(amountField.getText())).replace("/", "_");


                            String side =
                                    "BUY";
                            String type =
                                    "MARKET";
                            double price = 1.45;
                            OandaClient.createOrder(symbol9, amount, type, side);
                        }
                    } catch (Exception e) {
                        out.println(e.getMessage());
                        alert.setTitle("Market Buy Error");
                        alert.setHeaderText("Error");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    } catch (OandaException e) {
                        throw new RuntimeException(e);
                    }
                });


        buttons[1].setText("SELL");
        //buttons[1] action
        buttons[1].setOnAction(
                event -> {//Open Market Sell Orders
                    try {
                        TextField amountField = new TextField("amount");
                        int amount = Integer.parseInt(amountField.getText());

                        if (amount > 0) {
                            out.println(amount);
                            TextField priceField = new TextField("price");
                            String symbo =
                                    instrumentsList.get(Integer.parseInt(amountField.getText())).replace("/", "_");
                            String side =
                                    "SELL";
                            String type =
                                    "MARKET";
                            OandaClient.createMarketOrder(symbo, type, side, amount);
                        }
                    } catch (Exception | OandaException e) {
                        out.println(e.getMessage());
                        alert.setTitle("Market Sell Error");
                        alert.setHeaderText("Error");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    }
                });
        buttons[2].setText("SELL-STOP");
        //buttons[2] action
        buttons[2].setOnAction(
                event -> {//Open Market Sell Stop Orders
                    try {
                        TextField amountField = new TextField("amount");
                        int amount = Integer.parseInt(amountField.getText());
                        if (amount > 0) {
                            out.println(amount);
                        }
                    } catch (Exception e) {
                        out.println(e.getMessage());
                        alert.setTitle("Market Sell Stop Error");
                        alert.setHeaderText("Error");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    }
                });
        buttons[3].setText("BUY-STOP");
        //buttons[3] action
        buttons[3].setOnAction(
                event -> {//Open Market Buy Stop Orders
                    try {
                        TextField amountField = new TextField("amount");
                        int amount = Integer.parseInt(amountField.getText());
                        if (amount > 0) {
                            out.println(amount);


                        }
                    } catch (Exception e) {
                        out.println(e.getMessage());
                        alert.setTitle("Market Buy Stop Error");
                        alert.setHeaderText("Error");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    }
                });

        buttons[4].setText("SELL-LIMIT");
        //buttons[4] action
        buttons[4].setOnAction(
                event -> {//Open Market Sell Limit Orders
                    try {
                        TextField amountField = new TextField("amount");
                        int amount = Integer.parseInt(amountField.getText());
                        if (amount > 0) {
                            out.println(amount);
                            TextField
                                    priceField = new TextField("price");
                            double price = Integer.parseInt(priceField.getText());
                        }

                    } catch (Exception e) {
                        out.println(e.getMessage());
                        alert.setTitle("Market Sell Limit Error");
                        alert.setHeaderText("Error");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    }
                });
        buttons[5].setText("BUY-LIMIT");
        //buttons[5] action
        buttons[5].setOnAction(
                event -> {//Open Market Buy Limit Orders
                    try {
                        TextField amountField = new TextField("amount");
                        int amount = Integer.parseInt(amountField.getText());
                        if (amount > 0) {
                            out.println(amount);
                        }
                    } catch (Exception e) {
                        out.println(e.getMessage());
                        alert.setTitle("Market Buy Limit Error");
                        alert.setHeaderText("Error");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    }
                });

        buttons[6].setText("Screen Shot");
        //buttons[6] action
        buttons[6].setOnAction(
                event -> {//Open ScreenShot Orders
                    try {
                        File file = new File(
                                System.getProperty("user.dir") +
                                        "/resources/images/screenshot" + nanoTime() + ".png"
                        );
                        Screenshot.capture(file);
                    } catch (Exception e) {
                        out.println(e.getMessage());
                        alert.setTitle("ScreenShot Error");
                        alert.setHeaderText("Error");
                        alert.setContentText(e.getMessage());
                        alert.showAndWait();
                    }
                });

        menubar.getMenus().addAll(menuElements);
        pane.getChildren().addAll(menubar);


        return pane;
    }

    @Override
    public void start(@NotNull Stage stage) throws URISyntaxException {
        createLogin(stage);

    }

    public static void main(String[] args) {
        launch(Main.class, args);
    }

    static ArrayList<String> symb;

    static {
        try {
            symb = getTradeAbleInstruments();
        } catch (OandaException e) {
            throw new RuntimeException(e);
        }
    }

    public double amount;

    private static @NotNull Node getAccountSummary() {
        return new ListView<>(FXCollections.observableArrayList(
                "Account" + OandaClient.getAccountID(), "Balance" +
                        OandaClient.getAccount().getBalance(), "Open"));
    }

    private static @NotNull Node getAccountPerformance() {
        VBox accountPerformance
                = new VBox(10);
        accountPerformance.setPadding(new Insets(10, 10, 10, 10));
        accountPerformance.setSpacing(10);
        accountPerformance.setAlignment(Pos.CENTER);

        GridPane grid_pane = new GridPane();

        grid_pane.setPadding(new Insets(10, 10, 10, 10));
        grid_pane.setVgap(10);
        grid_pane.getChildren().add(new Label("Performance"));

        accountPerformance.getChildren().addAll(
                new Label("Account Performance"),
                grid_pane);
        return accountPerformance;
    }

    private static @NotNull VBox getOandaRecommendation() {

        VBox vbox = new VBox();
        vbox.setSpacing(10);
        vbox.setPadding(new Insets(10, 10, 10, 10));
        vbox.setAlignment(Pos.CENTER);

        vbox.getChildren().addAll(new TextArea(
                """
                        This is an example of an OANDA recommendation.
                        You can find more information about the OANDA recommendation here: https://www.oanda.com/docs/recommendations
                        """
        ));
        return vbox;
    }

    private static @NotNull VBox getTradeStatistics() throws OandaException {
        VBox tradeStatistics = new VBox();
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(10, 10, 10, 10));
        gridPane.setAlignment(Pos.CENTER);

        gridPane.add(new Label("Total Trades"), 0, 0);
        TextField totalTrades = new TextField("");
        totalTrades.setEditable(false);
        totalTrades.setText(String.valueOf(OandaClient.getOpenTradesList().size()));
        gridPane.add(totalTrades, 1, 0);
        gridPane.add(new Label("Total Orders"), 0, 2);
        TextField totalOrders = new TextField("");
        totalOrders.setEditable(false);
        totalOrders.setText(String.valueOf(OandaClient.getOrdersList().size()));
        Log.info("Order " + OandaClient.getOrdersList());
        gridPane.add(totalOrders, 1, 2);

        tradeStatistics.getChildren().addAll(gridPane);
        return tradeStatistics;
    }

    private static @NotNull Node getOandaPortFolio() throws OandaException {
        VBox vBox = new VBox();
        vBox.setSpacing(10);
        vBox.setPadding(new Insets(10, 10, 10, 10));
        vBox.setAlignment(Pos.CENTER);
        GridPane grid_pane = new GridPane();
        grid_pane.setPadding(new Insets(10, 10, 10, 10));
        grid_pane.setHgap(10);
        grid_pane.setVgap(10);
        grid_pane.setAlignment(Pos.CENTER);
        grid_pane.setStyle("-fx-background-color: #2de87e;");
        Root root = OandaClient.getAccountFullDetails();
        grid_pane.add(new Label("Account Number"), 0, 0);
        TextField accountID = new TextField(OandaClient.getAccountID());
        accountID.setEditable(false);
        grid_pane.add(accountID, 1, 0);
        grid_pane.add(new Label("Balance"), 1, 2);
        grid_pane.add(new TextField(String.valueOf(root.account.balance)), 2, 2);
        grid_pane.add(new Label("Margin-Available"), 3, 2);
        grid_pane.add(new TextField(String.valueOf(root.account.marginAvailable)), 3, 2);
        grid_pane.add(new Label("Commission"), 4, 2);
        grid_pane.add(new TextField(String.valueOf(root.account.commission)), 5, 2);
        grid_pane.add(new Label("Margin-Rate"), 0, 4);
        grid_pane.add(new TextField(String.valueOf(root.account.marginRate)
        ), 1, 4);
        grid_pane.add(new Label("Hedging "), 0, 5);
        grid_pane.add(new TextField(String.valueOf(root.account.hedgingEnabled)),
                1, 5);
        grid_pane.add(new Label("Financing"), 0, 6);
        grid_pane.add(new TextField(String.valueOf(root.account.financing))
                , 1, 6);
        grid_pane.add(new Label("unrealizedPL"), 0, 7);
        grid_pane.add(new TextField(String.valueOf(root.account.unrealizedPL)), 1, 7);
        grid_pane.add(new Label("resettablePL"), 2, 7);
        grid_pane.add(new TextField(String.valueOf(root.account.resettablePL)),
                3, 7);
        grid_pane.add(new Label("Margin closeOut %"), 3, 7);
        grid_pane.add(new TextField(String.valueOf(root.account.marginCloseoutPercent)),

                3, 7);
        grid_pane.add(new Label("Open PositionCount"), 0, 10);
        grid_pane.add(new TextField(String.valueOf(root.account.openPositionCount)),
                1, 10);
        grid_pane.add(new Label("Position - Count"), 0, 11);
        grid_pane.add(new TextField(String.valueOf(root.account.positionCount)), 1, 11);
        vBox.getChildren().addAll(grid_pane);
        return vBox;

    }

    private static @NotNull HBox getOandaHelp() {
        HBox helpBox = new HBox();
        helpBox.setSpacing(10);
        helpBox.setAlignment(Pos.CENTER);
        helpBox.setPadding(new Insets(10, 10, 10, 10));
        helpBox.getChildren().addAll(
                new Label("Help"),
                new Label("OANDA Trading Platform"),
                new Label("Version 1.0.0"),
                new Label("Copyright 2021 OANDA @CryptoInvestor"),
                new Label("All Rights Reserved"),

                new Label(""));
        return helpBox;
    }

    private static @NotNull VBox getOandaSignals() {
        VBox
                vbox = new VBox();
        vbox.setSpacing(10);
        vbox.setPadding(new Insets(10, 10, 10, 10));
        vbox.setAlignment(Pos.CENTER);
        vbox.setStyle("-fx-background-color: #000000;");
        vbox.getChildren().addAll(new Label("Trade Signal"));
        return vbox;

    }

    private static @NotNull ListView<OandaOrder> getOandaOrdersHistory() throws OandaException {
        ListView<OandaOrder> oandaOrdersHistory = new ListView<>();
        oandaOrdersHistory.getItems().addAll(OandaClient.getOrdersHistory());
        return oandaOrdersHistory;
    }

    //Get Oanda forex orders
    private static @NotNull VBox getOandaOrders() throws OandaException {
        ListView<OandaOrder> or = new ListView<>(FXCollections.observableArrayList(OandaClient.getOrdersList()));
        VBox vBox = new VBox(or);
        vBox.setSpacing(10);
        vBox.setPadding(new Insets(10, 10, 10, 10));
        vBox.setAlignment(Pos.CENTER);

        vBox.getChildren().addAll(new Label("Current Orders"));

        return vBox;

    }

    private @NotNull TreeTableView<SymbolData> getDepths() throws OandaException {
        TreeTableView<SymbolData> treeTable = new TreeTableView<>();
        TreeTableColumn<SymbolData, String> symbolColumn = new TreeTableColumn<>();
        symbolColumn.setText("SYMBOL");
        TreeTableColumn<SymbolData, Double> symbolBid = new TreeTableColumn<>();
        symbolBid.setText("BID");
        TreeTableColumn<SymbolData, Double> symbolAsk = new TreeTableColumn<>();
        symbolAsk.setText("ASK");

        ObservableList<SymbolData> datas = FXCollections.observableArrayList();
        Callback<RecursiveTreeObject<SymbolData>, ObservableList<SymbolData>> calback
                = RecursiveTreeObject::getChildren;
        RecursiveTreeItem<SymbolData> root = new RecursiveTreeItem<>(
                datas, calback
        );

        root.setExpanded(true);


        treeTable.getColumns().addAll(symbolColumn, symbolBid, symbolAsk);
        treeTable.setRoot(root);
        treeTable.setTranslateY(500);
        return treeTable;
    }

    @Contract(pure = true)
    private @NotNull TreeTableView<CandleData> getCandleData() throws OandaException, InterruptedException {
        TreeTableView<CandleData> treeTable = new TreeTableView<>();
        TreeTableColumn<CandleData, String> dateColumn = new TreeTableColumn<>("Date");
        Callback<TreeTableColumn.CellDataFeatures<CandleData, String>, ObservableValue<String>> dateColumnValue
                = param -> new ReadOnlyStringWrapper(String.valueOf(new Date(param.getValue().getValue().getOpenTime())));
        dateColumn.setCellValueFactory(dateColumnValue);

        TreeTableColumn<CandleData, String> openColum = new TreeTableColumn<>("Open");
        Callback<TreeTableColumn.CellDataFeatures<CandleData, String>, ObservableValue<String>> openColumnValue
                = param -> new ReadOnlyStringWrapper(String.valueOf(param.getValue().getValue().getOpenPrice()));
        openColum.setCellValueFactory(openColumnValue);


        TreeTableColumn<CandleData, String> closeColum = new TreeTableColumn<>("Close");
        Callback<TreeTableColumn.CellDataFeatures<CandleData, String>, ObservableValue<String>> closeColumnValue
                = param -> new ReadOnlyStringWrapper(String.valueOf(param.getValue().getValue().getClosePrice()));
        closeColum.setCellValueFactory(closeColumnValue);


        TreeTableColumn<CandleData, String> highColum = new TreeTableColumn<>("High");
        Callback<TreeTableColumn.CellDataFeatures<CandleData, String>, ObservableValue<String>> highColumnValue
                = param -> new ReadOnlyStringWrapper(String.valueOf(param.getValue().getValue().getHighPrice()));
        highColum.setCellValueFactory(highColumnValue);


        TreeTableColumn<CandleData, String> lowColum = new TreeTableColumn<>("Low");

        Callback<TreeTableColumn.CellDataFeatures<CandleData, String>, ObservableValue<String>> lowColumnValue
                = param -> new ReadOnlyStringWrapper(String.valueOf(param.getValue().getValue().getLowPrice()));
        lowColum.setCellValueFactory(lowColumnValue);


        TreeTableColumn<CandleData, String> volColumn = new TreeTableColumn<>("Volume");
        Callback<TreeTableColumn.CellDataFeatures<CandleData, String>, ObservableValue<String>> volumeColumnValue
                = param -> new ReadOnlyStringWrapper(String.valueOf(param.getValue().getValue().getOpenPrice()));
        volColumn.setCellValueFactory(volumeColumnValue);
        treeTable.setTranslateY(25);
        ObservableList<CandleData> datas = FXCollections.observableArrayList();
        datas.addAll(OandaClient.getForexCandles("EUR_USD"));
        Callback<RecursiveTreeObject<CandleData>, ObservableList<CandleData>> calback
                = RecursiveTreeObject::getChildren;
        RecursiveTreeItem<CandleData> root = new RecursiveTreeItem<>(datas, calback);

        root.setValue(datas.get(6));
        root.setExpanded(true);
        treeTable.setVisible(true);

        treeTable.setRoot(root);
        treeTable.getColumns().addAll(dateColumn, openColum, closeColum, highColum, lowColum, volColumn);

        treeTable.setTranslateY(500);
        treeTable.setPrefSize(200, 300);
        return treeTable;
    }

    private void createRegister(@NotNull ActionEvent e) {

        Node node = (Node) e.getSource();
        Stage stage = (Stage) node.getScene().getWindow();
        stage.close();
        GridPane grid = new GridPane();


        grid.setTranslateX(500);
        grid.setTranslateY(200);
        Label lblusername = new Label("Username :");
        grid.add(lblusername, 0, 0);
        TextField textUsername = new TextField();
        textUsername.setPromptText("Enter username");
        grid.add(textUsername, 0, 1);

        PasswordField lblPassword = new PasswordField();

        grid.add(lblPassword, 0, 2);
        PasswordField passwordField = new PasswordField();
        grid.add(passwordField, 2, 2);
        Button btnSignIn = new Button("Go Back");
        grid.add(btnSignIn, 0, 7);

        btnSignIn.setOnAction(eb -> {
            stage.close();
            stage.getIcons().add(new Image("logo.png"));
            try {
                createLogin(stage);
            } catch (URISyntaxException ex) {
                throw new RuntimeException(ex);
            }
        });
        Button lblRegister = new Button("Submit");
        grid.add(lblRegister, 2, 7);
        AnchorPane anchorPane = new AnchorPane(grid);

        Scene scene = new Scene(anchorPane, 1530, 780);
        scene.getStylesheets().add("app.css");
        stage.setScene(scene);
        stage.setTitle("InvestPro -->  Registration");
        stage.getIcons().add(new Image("logo.png"));
        stage.setResizable(true);
        stage.setIconified(true);
        stage.show();

        lblRegister.setOnAction(er -> {
            stage.close();
            try {
                createMainMenu();
            } catch (Exception | OandaException ex) {
                throw new RuntimeException(ex);
            }
        });

    }

    private void createLogin(@NotNull Stage stage) throws URISyntaxException {
        AnchorPane anchorPane = new AnchorPane();

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10, 10, 10, 10));
        grid.setTranslateX(500);
        grid.setTranslateY(200);
        grid.setPrefSize(500, 500);
        Label lblUsername = new Label("Username :");
        TextField textField = new TextField("");
        textField.setPromptText("Enter username");
        Label lblPassword = new Label("Password :");
        PasswordField passwordField = new PasswordField();
        grid.add(lblUsername, 0, 0);
        grid.add(textField, 1, 0);
        grid.add(lblPassword, 0, 3);
        grid.add(passwordField, 1, 3);
        grid.setVgap(30);
        Button btnReg = new Button("Register");
        grid.add(btnReg, 0, 5);
        Button btnLgn = new Button("Login");
        grid.add(btnLgn, 2, 5);
        Hyperlink btnForget = new Hyperlink("Forgot Password");
        grid.add(btnForget, 1, 9);
        anchorPane.getChildren().add(grid);
        Scene scene = new Scene(anchorPane, TRADING_SCREEN_WIDTH, TRADING_SCREEN_HEIGHT);

        scene.getStylesheets().add("app.css");
        stage.setTitle("InvestPro    " + new Date(System.currentTimeMillis()));
        stage.getIcons().add(new Image(String.valueOf(new URI("logo.png"))));

        stage.setScene(scene);
        stage.setResizable(true);
        stage.setIconified(true);
        // stage.getIcons().add(new Image("images/Screenshot 2023-02-03 153858.png"));

        btnLgn.setOnAction(e -> {
            stage.close();
            try {
                createMainMenu();
            } catch (Exception | OandaException ex) {
                throw new RuntimeException(ex);
            }
        });
        btnForget.setOnAction(e -> {
            try {
                createForgotPassword(e);
            } catch (URISyntaxException ex) {
                throw new RuntimeException(ex);
            }
        });
        btnReg.setOnAction(this::createRegister);
        stage.show();
    }

    private void createForgotPassword(@NotNull ActionEvent e) throws URISyntaxException {
        Node node = (Node) e.getSource();
        Stage stage = (Stage) node.getScene().getWindow();
        stage.close();
        stage.setTitle("Forgot Password " + new Date(System.currentTimeMillis()));
        GridPane grid = new GridPane();
        Label lblEmail = new Label("Email :");
        grid.setVgap(17);
        grid.add(lblEmail, 5, 0);
        TextField txtEmail = new TextField();
        txtEmail.setPromptText("Please enter your email ");
        grid.add(txtEmail, 5, 0);
        Button btnGoback = new Button("GO BACK");
        btnGoback.setOnAction(event -> {
            stage.close();  //stage.getIcons().add(new Image("images/Screenshot 2023-02-03 153858.png"));
            try {
                stage.getIcons().add(new Image(String.valueOf(new URI("logo.png"))));
            } catch (URISyntaxException ex) {
                throw new RuntimeException(ex);
            }

            try {
                createLogin(stage);
            } catch (URISyntaxException ex) {
                throw new RuntimeException(ex);
            }
        });
        grid.add(btnGoback, 4, 6);
        Button btnSubmit = new Button("Submit");
        btnSubmit.setOnAction(event -> {
            stage.close();
            try {
                createMainMenu();
            } catch (Exception | OandaException ex) {
                throw new RuntimeException(ex);
            }
        });
        grid.add(btnSubmit, 6, 6);
        grid.setTranslateX(400);
        grid.setTranslateY(300);
        AnchorPane anchorPane = new AnchorPane(grid);
        Scene scene = new Scene(anchorPane, 1530, 780);
        stage.setScene(scene);
        stage.getIcons().add(new Image("logo.png"));//new Image(String.valueOf(new URI("images/Screenshot 2023-02-03 153858.png"))));

        scene.getStylesheets().add("/app.css");
        stage.setIconified(true);
        stage.setResizable(true);

        stage.show();
    }

    private void createMainMenu() throws Exception, OandaException {

        GridPane grid = new GridPane();
        Spinner<Double> spinner = new Spinner<>();
        spinner.setPrefSize(50, 50);
        spinner.setEditable(true);
        ObservableList<Double> data = FXCollections.observableArrayList();

        data.addAll((double) (1 / 100), (double) (1 / 200), (double) (1 / 300), (double) (1 / 400), (double) (1 / 500), (double) (1 / 600));

        SpinnerValueFactory<Double> valueFactory = new SpinnerValueFactory.ListSpinnerValueFactory<>(data);
        spinner.setValueFactory(valueFactory);
        grid.add(spinner, 1, 1);
        spinner.decrement(-1 / 100);
        spinner.increment(1 / 100);
        Button btnBuy = new Button("BUY");
        grid.add(btnBuy, 0, 2);
        Button btnSell = new Button("SELL");
        grid.add(btnSell, 1, 2);
        Label bidPrice = new Label("Bid :");
        grid.add(bidPrice, 1, 2);
        Label askPrice = new Label("Ask: ");
        grid.add(askPrice, 3, 2);
        grid.setTranslateX(300);
        grid.setTranslateY(70);
        Stage stage = new Stage();


        AnchorPane anchorpane = new AnchorPane();
        anchorpane.getChildren().addAll(panel(), grid, getCandleSticksChart());
        Scene scene = new Scene(anchorpane, TRADING_SCREEN_WIDTH, TRADING_SCREEN_HEIGHT);
        scene.getStylesheets().add("/app.css");
        stage.setScene(scene);
        stage.setTitle("InvestPro -->Welcome  " + new Date(System.currentTimeMillis()));
        stage.setResizable(true);
        stage.setIconified(true);
        stage.getIcons().add(new Image(String.valueOf(new URI("logo.png"))));

        stage.show();

    }

    TabPane getTabPane() throws OandaException, IOException, ParseException {


        DraggableTab[] tabs

                = new DraggableTab[]{
                new DraggableTab("Live Orders "),
                new DraggableTab("Orders History"),
                new DraggableTab("Account "),
                new DraggableTab("Account Performance"),
                new DraggableTab("Account Details"),
                new DraggableTab("Statistics"),
                new DraggableTab("News Report"),
                new DraggableTab("Trade Signals"),
                new DraggableTab("Recommendation"),
                new DraggableTab("Navigation"),
                new DraggableTab("Oanda Wallet"),
                new DraggableTab("Binance Us Wallet"),
                new DraggableTab("Coinbase Pro Wallet")
                , new DraggableTab("Coinbase Wallet")
        };

        tabs[0].setContent(getOandaOrders());

        ListView<OandaOrder> listOfOrders = new ListView<>();
        listOfOrders.getItems().addAll(getOrdersList());
        tabs[1].setContent(listOfOrders);
        tabs[2].setContent(getOandaPortFolio());
        tabs[3].setContent(getAccountPerformance());
        tabs[4].setContent(getAccountDetails());
        tabs[5].setContent(getTradeStatistics());
        tabs[6].setContent(getNews());
        tabs[7].setContent(getOandaSignals());
        tabs[8].setContent(getOandaRecommendation());
        tabs[9].setContent(getOandaWallet());
        tabs[10].setContent(getCoinbaseProWallet());
        tabs[11].setContent(getBinanceUSProWallet());


        TabPane orderTabPanes = new TabPane();
        orderTabPanes.getTabs().addAll(tabs);


        orderTabPanes.setRotateGraphic(true);
        orderTabPanes.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
        return orderTabPanes;
    }

    @Contract(" -> new")
    private @NotNull Node getCoinbaseProWallet() {
        return new VBox();
    }

    @Contract(" -> new")
    private @NotNull Node getBinanceUsWallet() {

        return new VBox();
    }

    void coinbaseWalletManager(ActionEvent actionEvent) {


        withdrawCoinbase();

        sendMoney();

    }

    private void depositCoinbase() {
    }

    private void withdrawCoinbase() {
    }

    private void sendMoney() {
    }

    @Contract(" -> new")
    private @NotNull Node getBinanceUSProWallet() {//Implement this method to get the coinbase transaction


        Button b = new Button("Deposit");
        Button c = new Button("Withdraw");
        Button d = new Button("SendMoney");

        b.setOnAction(e -> depositCoinbase());
        c.setOnAction(e -> withdrawCoinbase());
        d.setOnAction(e -> sendMoney());

        GridPane grid = new GridPane();

        grid.setPadding(new Insets(10, 10, 10, 10));
        grid.add(new Label("--------Binance Us Wallet ------- "), 0, 0);
        grid.add(new Label(""), 1, 1);
        ListView<Objects> listV = new ListView<>();
        grid.add(listV, 3, 3);
        Node separator = new Separator();
        return new VBox(grid, separator, b, c, d);
    }

    @Contract(" -> new")
    private @NotNull Node getOandaWallet() {
        return new VBox();
    }

    private @NotNull VBox getCandleSticksChart() throws Exception, OandaException {


        TabPane candleSticksChartTabPane = new TabPane();
        DraggableTab[] candleStickChartTabs = new DraggableTab[]{
                new DraggableTab("Oanda Com"),
                new DraggableTab("Binance Us"),
                new DraggableTab("Coinbase Pro"),
                new DraggableTab("Stock"),
                new DraggableTab("Browser"),
                new DraggableTab("Account Info")
        };


        VBox[] vbox = new VBox[]{
                new VBox(),
                new VBox(),
                new VBox(),
                new VBox(),
                new VBox(new Browser().start()),
                new VBox(getTabPane()),
                new VBox(),
                new VBox(),
                new VBox(),
                new VBox(),
                new VBox()
        };


        Button[] buttons = new Button[]{
                new Button("MARKET BUY"),
                new Button("MARKET SELL"),
                new Button("BUY LIMIT"),
                new Button("SELL LIMIT"),
                new Button("SELL STOP"),
                new Button("CLOSE ALL")

        };


        ToolBar toolbarBuySell = new ToolBar(buttons);




        for (int i = 0; i < candleStickChartTabs.length; i++) {

            vbox[i].setPrefSize(1530, 800);
            //toolbar[i].setTranslateY(600);
            //  vbox[i].getChildren().add(toolbar[i]);
            candleStickChartTabs[i].setContent(vbox[i]);
        }

        OandaCandleStick oandaCanleStickChart = new OandaCandleStick();

        vbox[0].getChildren().add(oandaCanleStickChart.start());
        BinanceUS binanceUsCandleChart = new BinanceUS();
        vbox[1].getChildren().add(binanceUsCandleChart.start());
        CoinbasePro coinbaseCandleChart = new CoinbasePro();
        vbox[2].getChildren().add(coinbaseCandleChart.start());
        OandaCandleStick stockCandleChart = new OandaCandleStick();
        vbox[3].getChildren().add(stockCandleChart.start());


        candleSticksChartTabPane.getTabs().addAll(candleStickChartTabs);
        toolbarBuySell.setTranslateY(2);
        toolbarBuySell.setTranslateX(0);


        candleSticksChartTabPane.setPrefSize(1530, 800);
        VBox vBox = new VBox(candleSticksChartTabPane);

        vBox.setTranslateY(22);

        vBox.setPrefSize(1530, 730);
        return vBox;
    }

    TreeTableView<News> getNews() throws IllegalArgumentException, ParseException {

        TreeTableView<News> treeTableNews = new TreeTableView<>();


        TreeTableColumn<News, String> columnNewsDate = new TreeTableColumn<>();
        columnNewsDate.setText("Date");
        TreeTableColumn<News, String> columnNewsTitle = new TreeTableColumn<>();
        columnNewsTitle.setText("Title");
        TreeTableColumn<News, String> columnNewsCountry = new TreeTableColumn<>();
        columnNewsCountry.setText("Country");
        TreeTableColumn<News, String> columnNewsImpact = new TreeTableColumn<>();
        columnNewsImpact.setText("Impact");
        TreeTableColumn<News, String> columnNewsForecast = new TreeTableColumn<>();
        columnNewsForecast.setText("Forecast");
        TreeTableColumn<News, String> columnNewsPrevious = new TreeTableColumn<>();
        columnNewsPrevious.setText("Previous");
        treeTableNews.setBackground(Background.fill(Color.BLACK));

        //Loading News from Forex factory url:https://nfs.faireconomy.media/ff_calendar_thisweek.json?version=1bed8a31256f1525dbb0b6daf6898823
        ObservableList<News> dat = FXCollections.observableArrayList();
        dat.addAll(NewsManager.getNewsList());
        Callback<RecursiveTreeObject<News>, ObservableList<News>> callback
                = RecursiveTreeObject::getChildren;
        RecursiveTreeItem<News> root = new RecursiveTreeItem<>(dat, callback);
        treeTableNews.getColumns().addAll(columnNewsDate, columnNewsTitle, columnNewsCountry, columnNewsImpact, columnNewsForecast, columnNewsPrevious);
        //treeItemDate.getChildren().setAll(root);
        root.setValue(NewsManager.getNewsList().get(6));


        Callback<TreeTableColumn.CellDataFeatures<News, String>, ObservableValue<String>> columnNewsDateValue = param -> new SimpleObjectProperty<>(
                param.getValue().getValue().getDate()).asString();
        columnNewsDate.setCellValueFactory(columnNewsDateValue);


        Callback<TreeTableColumn.CellDataFeatures<News, String>, ObservableValue<String>> columnNewsTitleValue = param -> new ReadOnlyStringWrapper(param.getValue().getValue().getTitle());
        columnNewsTitle.setCellValueFactory(columnNewsTitleValue);


        Callback<TreeTableColumn.CellDataFeatures<News, String>, ObservableValue<String>> columnNewsCountryValue = param -> new ReadOnlyStringWrapper(param.getValue().getValue().getCountry());
        columnNewsCountry.setCellValueFactory(columnNewsCountryValue);

        Callback<TreeTableColumn.CellDataFeatures<News, String>, ObservableValue<String>> columnNewsImpactValue = param -> new ReadOnlyStringWrapper(param.getValue().getValue().getImpact());
        columnNewsImpact.setCellValueFactory(columnNewsImpactValue);

        Callback<TreeTableColumn.CellDataFeatures<News, String>, ObservableValue<String>> columnNewsForecastValue = param -> new ReadOnlyStringWrapper(param.getValue().getValue().getForecast());
        columnNewsForecast.setCellValueFactory(columnNewsForecastValue);

        Callback<TreeTableColumn.CellDataFeatures<News, String>, ObservableValue<String>> columnNewsPreviousValue = param -> new ReadOnlyStringWrapper(param.getValue().getValue().getPrevious());
        columnNewsPrevious.setCellValueFactory(columnNewsPreviousValue);

        root.setExpanded(true);
        treeTableNews.setRoot(root);
        return treeTableNews;
    }

    private @NotNull VBox getAccountDetails() throws IOException {

        VBox accountDetails = new VBox();
        accountDetails.setSpacing(10);
        accountDetails.setPadding(new Insets(10, 10, 10, 10));
        accountDetails.setAlignment(Pos.CENTER);
        accountDetails.getChildren().addAll(
                new Label("___________________ Account Details _____________________"),
                new Label("Date :" + new Date(System.currentTimeMillis())),
                new Label("Name: " + OandaClient.root.account.alias),
                new Label("Balance: " + OandaClient.root.account.balance),
                new Label("Currency: " + OandaClient.root.account.currency),
                new Label("CreateTime: " + OandaClient.root.account.createdTime),
                new Label("Margin Available: " + OandaClient.root.account.marginAvailable),
                new Label("________________________________________"));
        return accountDetails;
    }

}