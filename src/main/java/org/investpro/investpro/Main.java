package org.investpro.investpro;

import com.jfoenix.controls.RecursiveTreeItem;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

public class Main extends Application {
    private final String symbol = "BTC";

    private final Currency sym = new Currency(CurrencyType.FIAT, "USD", "USD", "USD", 12, symbol);
    private final String exchangeUrl = "wss://ws-feed.pro.coinbase.com";

    @Override
    public void start(@NotNull Stage stage) {
        createLogin(stage);
    }

    private static @NotNull Pane panel() {
        Pane pane = new Pane();

        MenuBar menuBar = new MenuBar();


        Menu[] menu = new Menu[]{
                new Menu("Connect"),
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
            } catch (URISyntaxException ex) {
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
            } catch (URISyntaxException ex) {
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
            } catch (URISyntaxException ex) {
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

    private void createMainMenu() throws URISyntaxException {

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
        anchorpane.getChildren().addAll(panel(), getCandleData(), getDepths(), grid, getCandleSticksChart());
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

        CandleData candleData = new CandleData(0, 0, 0, 0, 0, 0);

        ObservableList<CandleData> datas = FXCollections.observableArrayList();
        Callback<RecursiveTreeObject<CandleData>, ObservableList<CandleData>> calback
                = RecursiveTreeObject::getChildren;
        RecursiveTreeItem<CandleData> root = new RecursiveTreeItem<>(
                datas, calback
        );

        treeTable.setRoot(root);
        treeTable.setPrefSize(300, 300);
        return treeTable;
    }

    TabPane getTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setVisible(true);
        tabPane.setTabDragPolicy(TabPane.TabDragPolicy.REORDER);
        tabPane.setCache(true);
        DraggableTab[] tabs = new DraggableTab[]{
                new DraggableTab("Account"),
                new DraggableTab("Portfolio"),
                new DraggableTab("Market Analysis"),
                new DraggableTab("Forex News"),
                new DraggableTab("Mail")
                , new DraggableTab("Signals")
                , new DraggableTab("Telegram Bot")
        };
        VBox[] vBox;

        WebView we = new WebView();
        we.getEngine().load("https://www.google.com");

        vBox = new VBox[]{

                new VBox(),
                new VBox(),
                new VBox(),
                new VBox(),
                new VBox(),
                new VBox(we)
        };
        for (int i = 0; i < vBox.length; i++) {


            tabs[i].setContent(vBox[i]);
        }

        tabPane.setPrefSize(1000, 300);

        tabPane.getTabs().addAll(tabs);
        return tabPane;
    }

    private @NotNull VBox getCandleSticksChart() throws URISyntaxException {


        Button[] buttons = new Button[]{
                new Button("MARKET BUY"),
                new Button("MARKET SELL"),
                new Button("BUY LIMIT"),
                new Button("SELL LIMIT"),
                new Button("BUY STOP"),
                new Button("SELL STOP"),
                new Button("CLOSE ALL")

        };


        ToolBar toolbarBuySell = new ToolBar(buttons);


        toolbarBuySell.setTranslateY(2);
        toolbarBuySell.setTranslateX(0);

        TradePair tradePair = new TradePair(Currency.of("BTC"), Currency.of("USD"));

        CandleStickChartExample chart = new CandleStickChartExample();


        VBox vBox = new VBox(CandleStickChartExample.start(), toolbarBuySell, getTabPane());
        vBox.setTranslateX(200);
        vBox.setTranslateY(50);
        vBox.setPrefSize(1000, 750);
        return vBox;
    }

    protected static class ConnectExchange extends ExchangeWebSocketClient {
        protected ConnectExchange(URI clientUri) {
            super(clientUri, new Draft_6455());
        }

        @Override
        public void onMessage(String message) {

        }

        @Override
        public void streamLiveTrades(TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {

        }

        @Override
        public void stopStreamLiveTrades(TradePair tradePair) {

        }

        @Override
        public boolean supportsStreamingTrades(TradePair tradePair) {
            return false;
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {

        }

        @Override
        public void onOpen(ServerHandshake serverHandshake) {

        }

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            return null;
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return null;
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return null;
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return null;
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            return null;
        }

        @Override
        public void request(long n) {

        }

        @Override
        public String getSubprotocol() {
            return null;
        }

        @Override
        public boolean isOutputClosed() {
            return false;
        }

        @Override
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {

        }
    }
}