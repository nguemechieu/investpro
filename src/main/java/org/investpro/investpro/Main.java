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
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.Set;


public class Main extends Application {
    private final String symbol = "BTC";

    private final Currency sym = new Currency(CurrencyType.FIAT, "USD", "USD", "USD", 12, symbol);
    private final String exchangeUrl = "wss://ws-feed.pro.coinbase.com";

    private static final Set<Integer> GRANULARITIES = Set.of(60, 60 * 5, 60 * 15, 60 * 30, 3600, 3600 * 2, 3600 * 3, 3600 * 4, 3600 * 6, 3600 * 24, 3600 * 24 * 7, 3600 * 24 * 7 * 4, 3600 * 24 * 365);

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
            } catch (Exception ex) {
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
            } catch (Exception ex) {
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
            } catch (Exception ex) {
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

    private void createMainMenu() throws Exception {

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

    TabPane getTabPane() throws Exception {
        TabPane tabPane = new TabPane();
        tabPane.setVisible(true);
        tabPane.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
        tabPane.setCache(true);
        DraggableTab[] tabs = new DraggableTab[]{
                new DraggableTab("Orders"),
                new DraggableTab("Portfolio"),
                new DraggableTab("Portfolio  Analysis"),
                new DraggableTab("Market Analysis"),
                new DraggableTab("Browser"),
                new DraggableTab("Statistics ")
                , new DraggableTab("Mails")
                , new DraggableTab("News")
        };
        VBox[] vBox;
        Browser.webWiew.setPrefSize(1530, 780);
        Browser.webWiew.getEngine().load("https://www.google.com");
        ListView<Order> orders = new ListView<>();
        GridPane gridPane = new GridPane();
        gridPane.setPrefSize(1500, 800);
        gridPane.add(new Label("Account"), 0, 0);

        GridPane portFolio = new GridPane();
        portFolio.add(new Label("Profit"), 1, 1);

        vBox = new VBox[]{

                new VBox(orders),
                new VBox(gridPane),
                new VBox(portFolio),
                new VBox(),
                new VBox(),
                new VBox(),
                new VBox(new Browser().start()),
                new VBox(getNews())
        };


        for (int i = 0; i < vBox.length; i++) {

            vBox[i].setPrefSize(1500, 780);
            tabs[i].setContent(vBox[i]);
        }

        tabPane.setPrefSize(1000, 300);

        tabPane.getTabs().addAll(tabs);
        return tabPane;
    }

    private @NotNull VBox getCandleSticksChart() throws Exception {


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
        Color[] xColor = new Color[]{
                Color.GREEN,
                Color.YELLOW, Color.GOLD,
                Color.BEIGE, Color.CORAL
                , Color.WHITE
        };
        int ii;
        ii = ((int) Math.random() * (xColor.length));

        if (ii == xColor.length) {
            ii = 0;
        }

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

            vbox[i].setBackground(Background.fill(xColor[ii]));
            vbox[i].setPrefSize(1530, 600);

            toolbar[i].setTranslateY(500);
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
        candleSticksChartTabPane.setPrefSize(1200, 600);
        VBox vBox = new VBox(candleSticksChartTabPane, getTabPane());
        vBox.setTranslateX(200);
        vBox.setTranslateY(50);
        return vBox;
    }


    TreeTableView<News> getNews() throws IllegalArgumentException {

        Callback<RecursiveTreeObject<News>, ObservableList<News>> callbacks = RecursiveTreeObject::getChildren;
        ObservableList<News> data = FXCollections.observableArrayList();
        data.addAll(NewsManager.getNewsList());
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


        RecursiveTreeItem<News> root = new RecursiveTreeItem<>(data, callbacks);


        treeTableNews.getColumns().addAll(columnNewsDate, columnNewsTitle, columnNewsCountry, columnNewsImpact, columnNewsForecast, columnNewsPrevious);


        return treeTableNews;
    }
}