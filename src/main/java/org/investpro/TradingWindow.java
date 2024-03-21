package org.investpro;


import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.Objects;

class TradingWindow extends Region {

    private static final Logger logger = LoggerFactory.getLogger(TradingWindow.class);
    Button sellButton = new Button("SELL");
    Button buyButton = new Button("BUY");

    Button loadExchange = new Button("Load Exchange");


    Button connect = new Button("Connexion");
    Exchange exchange = new Coinbase("", "", new TradePair("BTC", "USD"));


    public TradingWindow() throws ParseException, IOException, InterruptedException, SQLException, ClassNotFoundException {
        super();
        setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);

        ChoiceBox<String> exchanges = new ChoiceBox<>();
        TabPane exchangesTab = new TabPane();
        exchangesTab.setSide(Side.TOP);
        exchangesTab.setPrefSize(1420, 450);

        exchangesTab.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        exchanges.getItems().addAll("COINBASE", "BINANCE US", "BINANCE", "OANDA", "BITFINEX", "BITMEX", "BITSTAMP", "BITTREX");


        logger.debug(
                "TradingWindow created"
        );


        ToolBar tradeToolbar = new ToolBar(exchanges);


        Button autoTradeButton = new Button("Auto Trade");
        autoTradeButton.getStyleClass().add("autoTradeButton");
        autoTradeButton.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm());

        tradeToolbar.getStyleClass().add("tradeToolbar");
        tradeToolbar.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm());
        Button cancelALLButton = new Button("Cancel ALL");

        connect.getStyleClass().add("connectButton");
        connect.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm());

        tradeToolbar.getItems().addAll(connect, buyButton, sellButton, cancelALLButton);


        exchangesTab.getStyleClass().add("exchangesTab");

        TabPane bottomTabPane = new TabPane();
        ToolBar bar = new ToolBar();

        bottomTabPane.getStyleClass().add("bottomTabPane");
        bar.getStyleClass().add("bar");

        Node loadChartButton = new Button("Load - Chart");
        loadChartButton.getStyleClass().add("loadChartButton");

        bottomTabPane.setTranslateY(550);
        bottomTabPane.setPrefSize(Double.MAX_VALUE, 230);
        bar.setTranslateY(Double.MAX_VALUE);
        bar.setPrefSize(Double.MAX_VALUE, 20);


        ChoiceBox<String> symbolsChoiceBox = new ChoiceBox<>();


        exchanges.setValue("EXCHANGE");
        exchanges.getSelectionModel().selectNext();


        bottomTabPane = new TabPane();
        bottomTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        bottomTabPane.setSide(Side.BOTTOM);

        bottomTabPane.setPrefSize(1540, 230);
        bottomTabPane.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm());
        Tab tradeTab = new Tab("Trade");
        TreeTableView<Orders> tradeView = new TreeTableView<>();
        tradeView.setEditable(true);//We need to set editable to true so user can close live orders
        tradeView.getStylesheets().add(String.valueOf(Objects.requireNonNull(getClass().getResource("/app.css"))));
        tradeTab.setContent(new VBox(tradeView));
        tradeView.setEditable(true);//We need to set editable to true so user can close live orders
        tradeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TreeTableColumn<Orders, Date> date = new TreeTableColumn<>("Time");
        date.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getDate())
        );
        date.setMinWidth(100);
        date.setMaxWidth(100);
        date.setResizable(true);

        TreeTableColumn<Orders, String> type = new TreeTableColumn<>("Type");
        type.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getType())
        );
        type.setMinWidth(100);
        type.setMaxWidth(100);
        type.setResizable(false);

        TreeTableColumn<Orders, String> symbol = new TreeTableColumn<>("Symbol");
        symbol.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getSymbol())
        );
        symbol.setMinWidth(100);
        symbol.setMaxWidth(100);
        symbol.setResizable(false);

        TreeTableColumn<Orders, Double> quantity = new TreeTableColumn<>("Size");
        quantity.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getQuantity())
        );

        quantity.setMinWidth(100);
        quantity.setMaxWidth(100);
        quantity.setResizable(false);

        TreeTableColumn<Orders, Double> price = new TreeTableColumn<>("Price");
        price.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getPrice())
        );

        price.setMinWidth(100);
        price.setMaxWidth(100);
        price.setResizable(false);

        TreeTableColumn<Orders, Double> commission = new TreeTableColumn<>("Commission");
        commission.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getCommission())
        );

        commission.setMinWidth(100);
        commission.setMaxWidth(100);
        commission.setResizable(false);

        TreeTableColumn<Orders, Double> takeProfit = new TreeTableColumn<>("T/P");
        takeProfit.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getTakeProfit())
        );

        takeProfit.setMinWidth(100);
        takeProfit.setMaxWidth(100);
        takeProfit.setResizable(false);

        TreeTableColumn<Orders, Double> stopLoss = new TreeTableColumn<>("S/L");
        stopLoss.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getStopLoss())
        );

        stopLoss.setMinWidth(100);
        stopLoss.setMaxWidth(100);
        stopLoss.setResizable(false);

        TreeTableColumn<Orders, Double> swap = new TreeTableColumn<>("Swap");
        swap.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getSwap())
        );

        swap.setMinWidth(100);
        swap.setMaxWidth(100);
        swap.setResizable(false);

        TreeTableColumn<Orders, Double> profit = new TreeTableColumn<>("Profit");
        profit.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getProfit())
        );

        profit.setMinWidth(100);
        profit.setMaxWidth(100);
        profit.setResizable(false);

        TreeItem<Orders> root = new TreeItem<>(new Orders(new Date(), "Buy", "BTC", 0.000410, 67000, 1.1, 100, 100, 2, 100));

        root.getChildren().addAll(new TreeItem<>(new Orders(new Date(), "Sell",
                        "BTC", 0.000410, 67000, 1.1, 100, 100, 2, 100)),
                new TreeItem<>(new Orders(new Date(), "Sell",
                        "XLM", 1000, 67000, 1.1, 100, 100, 1, 1)));

        tradeView.setRoot(root);
        tradeView.setShowRoot(true);


        tradeView.getColumns().addAll(date, type, symbol, quantity, price, commission, takeProfit, stopLoss, swap, profit);


        tradeTab.setContent(new VBox(tradeView));
        Tab exposureTab = new Tab("Exposure");
        TreeTableView<Exposure> exposureTreeTableView = new TreeTableView<>();

        exposureTreeTableView.setEditable(false);
        exposureTreeTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        TreeTableColumn<Exposure, String> exposureAsset = new TreeTableColumn<>("Asset");
        exposureAsset.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getAsset())
        );
        exposureAsset.setMinWidth(100);
        exposureAsset.setMaxWidth(100);
        exposureAsset.setResizable(false);

        TreeTableColumn<Exposure, Double> exposureVolume = new TreeTableColumn<>("Volume");
        exposureVolume.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getVolume())
        );
        exposureVolume.setMinWidth(100);
        exposureVolume.setMaxWidth(100);
        exposureVolume.setResizable(false);

        TreeTableColumn<Exposure, Double> exposureRate = new TreeTableColumn<>("Rate");
        exposureRate.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getRate())
        );
        exposureRate.setMinWidth(100);
        exposureRate.setMaxWidth(100);
        exposureRate.setResizable(false);

        TreeTableColumn<Exposure, String> exposureGraph = new TreeTableColumn<>("Graph");
        exposureGraph.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getGraph())
        );
        exposureGraph.setMinWidth(100);
        exposureGraph.setMaxWidth(100);
        exposureGraph.setResizable(false);
        TreeTableColumn<Exposure, String> exposureCurrency = new TreeTableColumn<>("Currency");
        exposureCurrency.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getCurrency())
        );
        exposureCurrency.setMinWidth(100);
        exposureCurrency.setMaxWidth(100);
        exposureCurrency.setResizable(false);
        TreeTableColumn<Exposure, Color> exposureColor = new TreeTableColumn<>("Color");
        exposureColor.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getColor())
        );
        exposureColor.setMinWidth(100);
        exposureColor.setMaxWidth(100);
        exposureColor.setResizable(false);
        exposureTreeTableView.getColumns().addAll(exposureAsset, exposureVolume, exposureRate,
                exposureGraph, exposureCurrency, exposureColor);


        Circle exposureCircle = new Circle(50, Paint.valueOf("blue"));
        exposureCircle.setFill(Paint.valueOf("blue"));
        exposureCircle.setStroke(Paint.valueOf("red"));
        exposureCircle.setCenterY(10);
        exposureCircle.setCenterX(10);

        exposureTab.setContent(new StackPane(new VBox(exposureTreeTableView), exposureCircle));
        Tab accountHistoryTab = new Tab("Account History");


        TreeTableView<Orders> accountHistory = new TreeTableView<>();
        accountHistory.setEditable(false);
        accountHistory.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        TreeTableColumn<Orders, Date> accountHistoryDate = new TreeTableColumn<>("Date");
        accountHistoryDate.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getDate())

        );

        accountHistoryDate.setMinWidth(100);
        accountHistoryDate.setMaxWidth(100);
        accountHistoryDate.setResizable(false);


        TreeTableColumn<Orders, String> accountHistoryType = new TreeTableColumn<>("Type");
        accountHistoryType.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getType())

        );
        accountHistoryType.setMinWidth(100);
        accountHistoryType.setMaxWidth(100);
        accountHistoryType.setResizable(false);

        TreeTableColumn<Orders, String> accountHistorySymbol = new TreeTableColumn<>("Symbol");
        accountHistorySymbol.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getSymbol())

        );
        accountHistorySymbol.setMinWidth(100);
        accountHistorySymbol.setMaxWidth(100);
        accountHistorySymbol.setResizable(false);

        TreeTableColumn<Orders, Double> accountHistoryQuantity = new TreeTableColumn<>("Quantity");
        accountHistoryQuantity.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getQuantity())

        );
        accountHistoryQuantity.setMinWidth(100);
        accountHistoryQuantity.setMaxWidth(100);
        accountHistoryQuantity.setResizable(false);

        TreeTableColumn<Orders, Double> accountHistoryPrice = new TreeTableColumn<>("Price");
        accountHistoryPrice.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getPrice())

        );
        accountHistoryPrice.setMinWidth(100);
        accountHistoryPrice.setMaxWidth(100);
        accountHistoryPrice.setResizable(false);

        TreeTableColumn<Orders, Double> accountHistoryCommission = new TreeTableColumn<>("Commission");
        accountHistoryCommission.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getCommission())

        );
        accountHistoryCommission.setMinWidth(100);
        accountHistoryCommission.setMaxWidth(100);
        accountHistoryCommission.setResizable(false);


        TreeTableColumn<Orders, String> accountHistoryStatus = new TreeTableColumn<>("Status");
        accountHistoryStatus.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getStatus())

        );

        accountHistoryStatus.setMinWidth(100);
        accountHistoryStatus.setMaxWidth(100);
        accountHistoryStatus.setResizable(false);
        TreeTableColumn<Orders, Double> accountHistoryTakeProfit = new TreeTableColumn<>("T/P");
        accountHistoryTakeProfit.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getTakeProfit())

        );
        accountHistoryTakeProfit.setMinWidth(100);
        accountHistoryTakeProfit.setMaxWidth(100);
        accountHistoryTakeProfit.setResizable(false);

        TreeTableColumn<Orders, Double> accountHistoryStopLoss = new TreeTableColumn<>("S/L");

        accountHistoryStopLoss.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getStopLoss())

        );
        accountHistoryStopLoss.setMinWidth(100);
        accountHistoryStopLoss.setMaxWidth(100);
        accountHistoryStopLoss.setResizable(false);

        TreeTableColumn<Orders, Double> accountHistorySwap = new TreeTableColumn<>("Swap");
        accountHistorySwap.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getSwap())

        );
        accountHistorySwap.setMinWidth(100);
        accountHistorySwap.setMaxWidth(100);
        accountHistorySwap.setResizable(false);

        TreeTableColumn<Orders, Double> accountHistoryProfit = new TreeTableColumn<>("Profit");
        accountHistoryProfit.setCellValueFactory(
                c -> new SimpleObjectProperty<>(c.getValue().getValue().getProfit())

        );
        accountHistoryProfit.setMinWidth(100);
        accountHistoryProfit.setMaxWidth(100);
        accountHistoryProfit.setResizable(false);


        TreeItem<Orders> accountHistoryItem = new TreeItem<>();
        accountHistoryItem.getChildren().add(
                new TreeItem<>(new Orders(null, "BUY", "ETHUSD", 100, 100, 100, 100, 100, 100, 100))

        );
        accountHistory.setRoot(accountHistoryItem);
        accountHistory.setShowRoot(false);
        accountHistory.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);


        accountHistory.getColumns().addAll(accountHistoryDate, accountHistoryType, accountHistorySymbol, accountHistoryQuantity,
                accountHistoryPrice, accountHistoryCommission, accountHistoryStatus, accountHistoryTakeProfit, accountHistoryStopLoss, accountHistorySwap, accountHistoryProfit);
        accountHistoryTab.setContent(new VBox(accountHistory));

        Tab newsTab = new Tab("News");
        ListView<News> newsListView = new ListView<>();

        newsListView.setItems(FXCollections.observableArrayList(new NewsDataProvider().getNews()));

        Canvas newsCanvas = new Canvas(200, 150);

        newsCanvas.setMouseTransparent(true);
        newsCanvas.setCache(true);
        newsCanvas.setCacheHint(CacheHint.SPEED);
        newsCanvas.setStyle(
                "-fx-background-color: rgba(125,25,255,0.0); " +
                        "-fx-background-radius: 10; " +
                        "-fx-background-insets: 0; "
        );

        newsCanvas.getGraphicsContext2D().setFill(Color.BLACK);
        newsCanvas.getGraphicsContext2D().fillRect(0, 0, 400, 200);
        newsCanvas.getGraphicsContext2D().setStroke(Color.WHITE);

        newsCanvas.getGraphicsContext2D().fillText(
                "Hello Traders ..! \n Below are upcoming news.",
                10,
                50
        );
        StringBuilder upcomingNews = new StringBuilder();
        try {
            for (News news : newsListView.getItems()) {

                if (news.date.getTime() >= new Date().getTime() - (1000L * 60 * 60 * 24 * 30)) {
                    upcomingNews.append(STR."\n\{news.getTitle()}");

                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }


        newsCanvas.getGraphicsContext2D().fillText(
                upcomingNews.toString(),
                100,
                5
        );


        newsTab.setContent(new VBox(newsListView, new Separator(Orientation.HORIZONTAL), newsCanvas));
        Tab alertTab = new Tab("Alert");
        alertTab.setContent(new VBox());
        Tab mailBoxTab = new Tab("MailBox");
        mailBoxTab.setContent(new VBox(new ListView<>()));
        Tab company = new Tab("Company");
        company.setContent(new VBox());
        Tab market = new Tab("Market");
        WebView marketView = new WebView();
        marketView.getEngine().load("https://www.google.com");

        TextField setext = new TextField();
        setext.setPromptText("Search");
        setext.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F1) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Help");
                alert.setHeaderText(null);
                alert.setContentText("Press F1 to get help");
                alert.showAndWait();
            }
        });
        setext.setOnAction(
                _ -> {
                    if (!setext.getText().isEmpty()) {
                        marketView.getEngine().load(STR."https://www.google.com/search?q=\{setext.getText()}");
                    }

                }
        );
        market.setContent(new VBox(setext, marketView));
        Tab signal = new Tab("Signal");
        signal.setContent(new VBox(
                new ListView<>()
        ));
        Tab article = new Tab("Article");
        article.setContent(new VBox(
                new ListView<>()
        ));
        Tab expert = new Tab("Expert");
        expert.setContent(new VBox(
                new ListView<>()
        ));


        setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setTranslateY(30);
        bottomTabPane.getTabs().addAll(tradeTab, exposureTab, accountHistoryTab,
                newsTab, alertTab, mailBoxTab, company, market, signal, article, expert);
        ProgressBar progressBar = new ProgressBar();
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setProgress(0);
        progressBar.setProgress(10);
        Circle connexionColor = new Circle(10, Paint.valueOf(String.valueOf(Color.rgb(213, 23, 1))));

        if (connect.isArmed()) {
            connexionColor.setFill(Paint.valueOf(String.valueOf(Color.rgb(13, 223, 1))));
        }
        bar = new ToolBar(new Label("For Help, press F1!                                                          "), new Separator(Orientation.VERTICAL), new Label("Default"), new Separator(Orientation.VERTICAL),
                new Separator(Orientation.VERTICAL)
                , new Label("|    |    |    |    |    |"), progressBar, new Separator(Orientation.VERTICAL), new Separator(Orientation.VERTICAL), connexionColor
        );
        bar.setTranslateY(730);
        bar.setPrefSize(1540, 20);
        bottomTabPane.setTranslateY(430);
        bottomTabPane.setPrefSize(1540, 300);

        tradeToolbar.setPrefSize(1540, 15);


        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.setTranslateY(70);
        hBox.getChildren().addAll(exchangesTab);
        tradeToolbar.setBackground(Background.fill(Paint.valueOf(String.valueOf(Color.rgb(13, 123, 134)))));
        getChildren().addAll(tradeToolbar, hBox, bottomTabPane, bar);


//        CryptoCurrencyDataProvider cryptoCurrencyDataProvider = new CryptoCurrencyDataProvider();
//        cryptoCurrencyDataProvider.registerCurrencies();
//        FiatCurrencyDataProvider fiatCurrencyDataProvider = new FiatCurrencyDataProvider();
//        fiatCurrencyDataProvider.registerCurrencies();

        exchanges.setOnAction(_ -> {

            try {

                if (exchanges.getSelectionModel().getSelectedItem().equals("COINBASE")) {
                    logger.debug(
                            "Exchange selected"
                    );
                    try {

                        if (!symbolsChoiceBox.getSelectionModel().getSelectedItem().isEmpty()) {

                            String sy = symbolsChoiceBox.getSelectionModel().getSelectedItem();
                            exchange = new Coinbase("", "", new TradePair(sy.split("/")[0],
                                    sy.split("/")[1]));

                        }

                    } catch (SQLException | ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }

                    logger.debug(
                            "Exchange created"
                    );

                } else if (exchanges.getSelectionModel().getSelectedItem().equals("BINANCE US")) {
                    logger.debug("Exchange selected");
                    exchange = new BinanceUs("", "");


                } else if (exchanges.getSelectionModel().getSelectedItem().equals("BINANCE")) {
                    logger.debug(
                            "Exchange selected"
                    );
                    exchange = new Binance("", "");


                    logger.debug(
                            "Exchange created"
                    );


                } else if (exchanges.getSelectionModel().getSelectedItem().equals("OANDA")) {

                    logger.debug(
                            "Exchange selected"
                    );
                    exchange = new Oanda("", "");

                } else {

                    new Message("EXCHANGE NOT SUPPORTED", "Please select a valid exchange");
                }


            } catch (Exception e) {
                logger.error(e.getMessage());

                new Message("", e.getMessage());
            }

            symbolsChoiceBox.getSelectionModel().selectNext();
        });
        for (TradePair i : exchange.getTradePairSymbol()) {
            symbolsChoiceBox.getItems().addAll(i.toString('-'));
        }
        tradeToolbar.getItems().addAll(symbolsChoiceBox, loadChartButton);

        autoTradeButton.setOnAction(_ -> exchange.autoTrading(true, exchange.getSignal()));
        buyButton.setOnAction(_ -> {
            logger.debug("Buy button pressed");
            double sizes = 12, stoploss = 100000,
                    takeprofit = 100000;
            exchange.buy(exchange.getWebsocketClient().getTradePair(), MARKET_TYPES.LIMIT, sizes, stoploss, takeprofit);
        });

        connect.setOnAction(_ -> exchange.connect());

        sellButton.setOnAction(_ -> {
            logger.debug("Sell button pressed");
            double sizes = 12,
                    stoploss = 100000,
                    takeprofit = 100000;
            exchange.sell(exchange.getWebsocketClient().getTradePair(), MARKET_TYPES.LIMIT, sizes, stoploss, takeprofit);
        });


        cancelALLButton.setOnAction(_ -> {
            logger.debug("Cancel button pressed");
            exchange.cancelALL();
        });


        loadExchange.setOnAction(event -> {

            DraggableTab tab = new DraggableTab(exchanges.getSelectionModel().getSelectedItem());

            try {
                tab.setContent(
                        new TradeView(exchange)
                );
            } catch (SQLException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            exchangesTab.getTabs().add(tab);

        });
        tradeToolbar.getItems().addAll(loadExchange, autoTradeButton);

    }

}