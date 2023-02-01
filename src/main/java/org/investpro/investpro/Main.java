package org.investpro.investpro;

import com.jfoenix.controls.RecursiveTreeItem;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;

import static org.investpro.investpro.OandaClient.getTradeAbleInstruments;


public class Main extends Application {

    private static @NotNull Pane panel() {
        Pane pane = new Pane();

        MenuBar menuBar = new MenuBar();


        Menu[] menu = new Menu[]{
                new Menu("Connection"),
                new Menu("File"),
                new Menu("View"),
                new Menu("Edit"),
                new Menu("Insert"),
                new Menu("Window"),
                new Menu("Charts"),
                new Menu("Settings"),
                new Menu("Languages"),
                new Menu("Browser"),
                new Menu("About")
        };


        menu[0].setVisible(true);

        MenuItem[] menuItem0 = new MenuItem[]{

                new MenuItem("BINANCE US"),
                new MenuItem("COINBASE PRO"),
                new MenuItem("OANDA"),
                new MenuItem("BINANCE COM"),
                new MenuItem("IG "),
                new MenuItem("TD AMERITRADE")
        };
        menu[0].getItems().addAll(menuItem0);


        menu[1].setVisible(true);
        MenuItem[] menuItem1 = new MenuItem[]{

                new MenuItem("open file"),
                new MenuItem("save settings"),
                new MenuItem("save as"),
                new MenuItem("print preview"),
                new MenuItem("Print to PDF")


        };
        menu[1].getItems().addAll(
                menuItem1
        );

        menu[2].setVisible(true);

        menu[3].setVisible(true);
        menu[4].setVisible(true);
        menu[5].setVisible(true);
        menu[6].setVisible(true);
        menu[7].setVisible(true);

        //Languages list settings
        menu[8].setVisible(true);


        MenuItem[] menuLanguages = new MenuItem[]{
                new MenuItem("ENGLISH"),
                new MenuItem("FRENCH"),
                new MenuItem("CHINESE"),
                new MenuItem("JAPANESE"),
                new MenuItem("GERMAN")

        };

        menu[8].getItems().addAll(menuLanguages);


        menu[9].setVisible(true);


        menu[10].setVisible(true);


        menuBar.getMenus().addAll(menu);
        pane.getChildren().addAll(menuBar);


        return pane;
    }

    @Override
    public void start(@NotNull Stage stage) {
        createLogin(stage);

    }

    public static void main(String[] args) {
        launch(Main.class, args);
    }

    static ArrayList<String> symb;
    static Spinner<Double> spinner = new Spinner<>();

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
        accountPerformance.setStyle("-fx-background-color: #32737e;");

        GridPane grid_pane
                = new GridPane();

        grid_pane.setPadding(new Insets(10, 10, 10, 10));
        grid_pane.setVgap(10);
        grid_pane.getChildren().add(new Label("Account Performance"));

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
        gridPane.setStyle("-fx-background-color: #0c4559;");
        gridPane.add(new Label("Total Trades"), 0, 0);
        TextField totalTrades = new TextField("");
        totalTrades.setEditable(false);
        totalTrades.setText(String.valueOf(OandaClient.getOpenTradesList().size()));
        gridPane.add(totalTrades, 0, 1);
        gridPane.add(new Label("Total Orders"), 0, 2);
        TextField totalOrders = new TextField("");
        totalOrders.setEditable(false);
        totalOrders.setText(String.valueOf(OandaClient.getTradesList().size()));
        gridPane.add(totalOrders, 0, 3);


        tradeStatistics.getChildren().addAll(gridPane);
        return tradeStatistics;
    }

    private static @NotNull Node getOandaPortFolio() throws OandaException {
        VBox vBox = new VBox();
        vBox.setSpacing(10);
        vBox.setPadding(new Insets(10, 10, 10, 10));
        vBox.setAlignment(Pos.CENTER);
        vBox.setStyle("-fx-background-color: #2de87e;");

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
        grid_pane.add(new Label("Balance"), 0, 1);
        grid_pane.add(new TextField(String.valueOf(root.account.balance)), 1, 1);
        grid_pane.add(new Label("Margin-Available"), 0, 2);
        grid_pane.add(new TextField(String.valueOf(root.account.marginAvailable)), 1, 2);
        grid_pane.add(new Label("Margin-Used"), 0, 3);
        grid_pane.add(new TextField(String.valueOf(root.account.marginUsed)), 1, 3);
        grid_pane.add(new Label("Margin-Rate"), 0, 4);
        grid_pane.add(new TextField(String.valueOf(root.account.marginRate)
        ), 1, 4);
        grid_pane.add(new Label("Hedging "), 0, 5);
        grid_pane.add(new TextField(String.valueOf(root.account.hedgingEnabled)),
                1, 5);
        grid_pane.add(new Label("Commission"), 0, 6);
        grid_pane.add(new TextField(String.valueOf(root.account.commission))
                , 1, 6);
        grid_pane.add(new Label("unrealizedPL"), 0, 7);
        grid_pane.add(new TextField(String.valueOf(root.account.unrealizedPL)),
                1, 7);
        grid_pane.add(new Label("resettablePL"), 0, 8);
        grid_pane.add(new TextField(String.valueOf(root.account.resettablePL)),
                1, 8);
        grid_pane.add(new Label("Margin closeOut %"), 0, 9);
        grid_pane.add(new TextField(String.valueOf(root.account.marginCloseoutPercent)),

                1, 9);
        grid_pane.add(new Label("Open PositionCount"), 0, 10);
        grid_pane.add(new TextField(String.valueOf(root.account.openPositionCount)),
                1, 10);
        grid_pane.add(new Label("PositionCount"), 0, 11);
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
        vbox.getChildren().addAll(new Label("Trade Signals"));
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
        VBox vBox = new VBox();
        vBox.setSpacing(10);
        vBox.setPadding(new Insets(10, 10, 10, 10));
        vBox.setAlignment(Pos.CENTER);
        vBox.setStyle("-fx-background-color: #0c4559;");
        vBox.getChildren().addAll(new Label("Current Orders"));
        vBox.getChildren().addAll(or);
        return vBox;

    }

    private @NotNull TreeTableView<SymbolData> getDepths() {
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


        treeTable.getColumns().addAll(symbolColumn, symbolBid, symbolAsk);
        treeTable.setRoot(root);
        treeTable.setTranslateY(500);
        return treeTable;
    }

    @Contract(pure = true)
    private @NotNull TreeTableView<CandleData> getCandleData() {
        TreeTableView<CandleData> treeTable = new TreeTableView<>();
        TreeTableColumn<CandleData, String> dateColumn = new TreeTableColumn<>("Date");
        TreeTableColumn<CandleData, Double> symbolColum = new TreeTableColumn<>("Symbol");
        TreeTableColumn<CandleData, Double> openColum = new TreeTableColumn<>("Open");
        TreeTableColumn<CandleData, Double> closeColum = new TreeTableColumn<>("Close");
        TreeTableColumn<CandleData, Double> highColum = new TreeTableColumn<>("High");
        TreeTableColumn<CandleData, Double> lowColum = new TreeTableColumn<>("Low");
        TreeTableColumn<CandleData, Long> volColumn = new TreeTableColumn<>("Volume");


        treeTable.getColumns().addAll(dateColumn, symbolColum, openColum, closeColum, highColum, lowColum, volColumn);
        treeTable.setTranslateY(25);

        ObservableList<CandleData> datas = FXCollections.observableArrayList();
        Callback<RecursiveTreeObject<CandleData>, ObservableList<CandleData>> calback
                = RecursiveTreeObject::getChildren;
        RecursiveTreeItem<CandleData> root = new RecursiveTreeItem<>(
                datas, calback
        );

        treeTable.setRoot(root);
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
            createLogin(stage);
        });
        Button lblRegister = new Button("Submit");
        grid.add(lblRegister, 2, 7);
        AnchorPane anchorPane = new AnchorPane(grid);
        Scene scene = new Scene(anchorPane, 1530, 780);
        scene.getStylesheets().add("app.css");
        stage.setScene(scene);
        stage.setTitle("InvestPro -->  Registration");
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

    private void createLogin(@NotNull Stage stage) {
        AnchorPane anchorPane = new AnchorPane();

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10, 10, 10, 10));
        grid.setTranslateX(500);
        grid.setTranslateY(200);

        Label lblUsername = new Label("Username :");
        TextField textField = new TextField("");
        textField.setPromptText("Enter username");
        Label lblPassword = new Label("Password :");
        PasswordField passwordField = new PasswordField();
        grid.add(lblUsername, 0, 0);
        grid.add(textField, 1, 0);
        grid.add(lblPassword, 0, 1);
        grid.add(passwordField, 1, 1);
        Button btnReg = new Button("Register");
        grid.add(btnReg, 0, 7);
        Button btnLgn = new Button("Login");
        grid.add(btnLgn, 2, 7);
        Hyperlink btnForget = new Hyperlink("Forgot Password");
        grid.add(btnForget, 1, 12);
        anchorPane.getChildren().add(grid);
        Scene scene = new Scene(anchorPane, 1530, 780);

        scene.getStylesheets().add("app.css");
        stage.setTitle("InvestPro   " + new Date(System.currentTimeMillis()));
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setIconified(true);
        btnLgn.setOnAction(e -> {
            stage.close();
            try {
                createMainMenu();
            } catch (Exception | OandaException ex) {
                throw new RuntimeException(ex);
            }
        });
        btnForget.setOnAction(this::createForgotPassword);
        btnReg.setOnAction(this::createRegister);
        stage.show();
    }

    private void createForgotPassword(@NotNull ActionEvent e) {
        Node node = (Node) e.getSource();
        Stage stage = (Stage) node.getScene().getWindow();
        stage.close();
        stage.setTitle("Forgot Password " + new Date(System.currentTimeMillis()));
        GridPane grid = new GridPane();
        Label lblEmail = new Label("Email :");
        grid.add(lblEmail, 0, 0);
        TextField txtEmail = new TextField();
        txtEmail.setPromptText("Please enter your email ");
        grid.add(txtEmail, 1, 0);
        Button btnGoback = new Button("GO BACK");
        btnGoback.setOnAction(event -> {
            stage.close();
            createLogin(stage);
        });
        grid.add(btnGoback, 0, 9);
        Button btnSubmit = new Button("SUBMIT");
        btnSubmit.setOnAction(event -> {
            stage.close();
            try {
                createMainMenu();
            } catch (Exception | OandaException ex) {
                throw new RuntimeException(ex);
            }
        });
        grid.add(btnSubmit, 3, 9);
        grid.setTranslateX(499);
        grid.setTranslateY(299);
        AnchorPane anchorPane = new AnchorPane(grid);
        Scene scene = new Scene(anchorPane, 1530, 780);
        stage.setScene(scene);
        scene.getStylesheets().add("app.css");
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
        anchorpane.getChildren().addAll(panel(), getCandleData(), getDepths(), getCandleSticksChart());
        Scene scene = new Scene(anchorpane, 1530, 780);
        scene.getStylesheets().add("/app.css");
        stage.setScene(scene);
        stage.setTitle("InvestPro -->Dashboard   Welcome  " + new Date(System.currentTimeMillis()));
        stage.setResizable(true);
        stage.setIconified(true);
        stage.show();

    }

    TabPane getTabPane() throws Exception, OandaException {


        DraggableTab[] tabs

                = new DraggableTab[]{
                new DraggableTab("Orders Infos"),
                new DraggableTab("Orders History"),
                new DraggableTab("Account Details"),
                new DraggableTab("Account Performance"),
                new DraggableTab("Account Summary"),
                new DraggableTab("Statistics"),
                new DraggableTab("Mail"),
                new DraggableTab("Trade Signals"),
                new DraggableTab("Recommendation"),
                new DraggableTab("Help")};

        tabs[0].setContent(getOandaOrders());

        tabs[1].setContent(getOandaOrdersHistory());
        tabs[2].setContent(getOandaPortFolio());
        tabs[3].setContent(getAccountPerformance());
        tabs[4].setContent(getAccountSummary());
        tabs[5].setContent(getTradeStatistics());
        tabs[6].setContent(getTradeStatistics());
        tabs[7].setContent(getOandaSignals());
        tabs[8].setContent(getOandaRecommendation());
        tabs[9].setContent(getOandaHelp());
        TabPane orderTabPanes = new TabPane();
        orderTabPanes.getTabs().addAll(tabs);


        // timeFrameToolBar.getItems().addAll(timeFrameToolBarButtons)
        // orderTabPanes.setRotateGraphic(true);

        orderTabPanes.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);


        SpinnerValueFactory<Double> spinnerValueFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(0.01,
                60, 0.01);
        spinner.setValueFactory(spinnerValueFactory);
        HBox hbox = new HBox(spinner);

        hbox.setSpacing(10);
        hbox.setAlignment(Pos.CENTER);


        return orderTabPanes;
    }

    private @NotNull VBox getCandleSticksChart() throws Exception, OandaException {


        TabPane candleSticksChartTabPane = new TabPane();
        DraggableTab[] candleStickChartTabs = new DraggableTab[]{
                new DraggableTab("Oanda"),
                new DraggableTab("Binance Us"),
                new DraggableTab("Coinbase Pro"),
                new DraggableTab("IG "),
                new DraggableTab("Binance Com"),
                new DraggableTab("Stock")
        };


        VBox[] vbox = new VBox[]{
                new VBox(),
                new VBox(),
                new VBox(),
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


        ToolBar[] toolbar = new ToolBar[]{

                toolbarBuySell,
                new ToolBar(buttons),
                new ToolBar(buttons), new ToolBar(buttons), new ToolBar(buttons), new ToolBar(buttons)
                , new ToolBar(buttons), new ToolBar(buttons),
                new ToolBar(buttons)

        };

        for (int i = 0; i < candleStickChartTabs.length; i++) {

            vbox[i].setPrefSize(1200, 800);
            toolbar[i].setTranslateY(500);
            vbox[i].getChildren().addAll(toolbar[i]);
            candleStickChartTabs[i].setContent(vbox[i]);
        }

        OandaCandleStick oandaCanleStickChart = new OandaCandleStick();

        vbox[0].getChildren().add(oandaCanleStickChart.start());
        BinanceUsCandleStick binanceUsCandleChart = new BinanceUsCandleStick();
        vbox[1].getChildren().add(binanceUsCandleChart.start());
        CoinbasePro coinbaseCandleChart = new CoinbasePro();
        vbox[2].getChildren().add(coinbaseCandleChart.start());
        BinanceUsCandleStick stockCandleChart = new BinanceUsCandleStick();
        vbox[3].getChildren().add(stockCandleChart.start());
        BinanceUsCandleStick marketCandleChart = new BinanceUsCandleStick();
        vbox[4].getChildren().add(marketCandleChart.start());

        candleSticksChartTabPane.getTabs().addAll(candleStickChartTabs);
        toolbarBuySell.setTranslateY(2);
        toolbarBuySell.setTranslateX(0);
        candleSticksChartTabPane.setPrefSize(1250, 700);
        VBox vBox = new VBox(candleSticksChartTabPane, getTabPane());
        vBox.setTranslateX(200);
        vBox.setTranslateY(20);

        vBox.setPrefSize(1250, 750);
        return vBox;
    }

    TreeTableView<News> getNews() throws IllegalArgumentException {

        TreeTableView<News> treeTableNews = new TreeTableView<>();
        TreeTableColumn<News, String> columnNewsDate = new TreeTableColumn<>("Date");


        Callback<TreeTableColumn.CellDataFeatures<News, String>, ObservableValue<String>> dateCellValueFactory
                = param -> new ReadOnlyStringWrapper(param.getValue().getValue().getDate());
        columnNewsDate.setCellValueFactory(dateCellValueFactory);
        TreeTableColumn<News, String> columnNewsTitle = new TreeTableColumn<>("Title");
        Callback<TreeTableColumn.CellDataFeatures<News, String>, ObservableValue<String>> titleCellValueFactory
                = param -> new ReadOnlyStringWrapper(param.getValue().getValue().getTitle());
        columnNewsDate.setCellValueFactory(titleCellValueFactory);
        TreeTableColumn<News, String> columnNewsCountry = new TreeTableColumn<>("Country");
        Callback<TreeTableColumn.CellDataFeatures<News, String>, ObservableValue<String>> countryCellValueFactory
                = param -> new ReadOnlyStringWrapper(param.getValue().getValue().getCountry());
        columnNewsDate.setCellValueFactory(countryCellValueFactory);
        TreeTableColumn<News, String> columnNewsImpact = new TreeTableColumn<>("Impact");
        Callback<TreeTableColumn.CellDataFeatures<News, String>, ObservableValue<String>> impactCellValueFactory
                = param -> new ReadOnlyStringWrapper(param.getValue().getValue().getImpact());
        columnNewsDate.setCellValueFactory(impactCellValueFactory);
        TreeTableColumn<News, String> columnNewsForecast = new TreeTableColumn<>("Forecast");
        Callback<TreeTableColumn.CellDataFeatures<News, String>, ObservableValue<String>> forecastCellValueFactory
                = param -> new ReadOnlyStringWrapper(param.getValue().getValue().getForecast());
        columnNewsDate.setCellValueFactory(forecastCellValueFactory);
        TreeTableColumn<News, String> columnNewsPrevious = new TreeTableColumn<>("Previous");


        Callback<TreeTableColumn.CellDataFeatures<News, String>, ObservableValue<String>> previousCellValueFactory
                = param -> new ReadOnlyStringWrapper(param.getValue().getValue().getPrevious());
        columnNewsDate.setCellValueFactory(previousCellValueFactory);
        //Loading News from Forex factory url:https://nfs.faireconomy.media/ff_calendar_thisweek.json?version=1bed8a31256f1525dbb0b6daf6898823


        ObservableList<News> data = FXCollections.observableArrayList();

        for (int i = 0; i < NewsManager.getNewsList().size(); i++) {

            data.add(i, NewsManager.getNewsList().get(i));
        }
        TreeItem<News> root = new RecursiveTreeItem<>(data, RecursiveTreeObject::getChildren);


        treeTableNews.getColumns().addAll(columnNewsDate, columnNewsTitle, columnNewsCountry, columnNewsImpact, columnNewsForecast, columnNewsPrevious);


        return treeTableNews;
    }

    private @NotNull VBox getAccountDetails() {
        VBox accountDetails = new VBox();
        accountDetails.setSpacing(10);
        accountDetails.setPadding(new Insets(10, 10, 10, 10));
        accountDetails.setAlignment(Pos.CENTER);
        accountDetails.getChildren().addAll(
                new Label("Account Details"),
                new Label("Name: " + OandaClient.getAccount().getName()),
                new Label("Balance: " + OandaClient.getAccount().getBalance()),
                new Label("Currency: " + OandaClient.getAccount().getCurrency()),
                new Label("Account Type: " + OandaClient.getAccount().getAccountType()),
                new Label("Account Status: " + OandaClient.getAccount().getAccountStatus()),
                new Label("Trading Status: " + OandaClient.getAccount().getTradingStatus()),
                new Label("Trading Mode: " + OandaClient.getAccount().getTradingMode()),
                new Label("Trading Session: " + OandaClient.getAccount().getTradingSession()),
                new Label("Trading Time: " + OandaClient.getAccount().getTradingTime()),
                new Label("Trading Time Zone: " + OandaClient.getAccount().getTradingTimeZone()));


        return accountDetails;
    }

}