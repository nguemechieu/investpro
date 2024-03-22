package org.investpro;


import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.geometry.Side;

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

class TradingWindow extends AnchorPane {

    private static final Logger logger = LoggerFactory.getLogger(TradingWindow.class);
    Button sellButton = new Button("SELL");
    Button buyButton = new Button("BUY");
    Button loadChartButton = new Button("Load - Chart");



    Button connect = new Button("Connexion");
    Exchange exchange;
    public TradingWindow() throws ParseException, IOException, InterruptedException, SQLException, ClassNotFoundException {
        super();


        setPrefSize(1540, 780);
        ChoiceBox<String> exchanges = new ChoiceBox<>();
        exchanges.getItems().addAll("COINBASE", "BINANCE US", "BINANCE", "OANDA", "BITFINEX", "BITMEX", "BITSTAMP", "BITTREX");
        logger.debug(
                "TradingWindow created");


        TabPane chartTabPane = new TabPane();
        chartTabPane.setSide(Side.LEFT);

        TabPane exchangeTabPane = new TabPane();
        exchangeTabPane.setSide(Side.TOP);
        Button addExchangeButton = new Button("Add Exchange");
        Button addChart = new Button("Add Chart");

        ChoiceBox<TradePair> chartSymbols = new ChoiceBox<>();

        exchanges.getSelectionModel().selectFirst();
        switch (exchanges.getSelectionModel().getSelectedItem()) {
            case "COINBASE" -> {
                logger.debug(
                        "Exchange selected"
                );
                exchange = new Coinbase("", "");
                chartSymbols.getItems().addAll(exchange.getTradePairSymbol());

                logger.info(exchange.getTradePairSymbol().toString());
                chartSymbols.getSelectionModel().selectFirst();
            }
            case "BINANCE US" -> {
                logger.debug(
                        "Exchange selected"
                );
                exchange = new BinanceUs("", "");
                chartSymbols.getItems().addAll(exchange.getTradePairSymbol());
                chartSymbols.getSelectionModel().selectFirst();

                logger.info(exchange.getTradePairSymbol().toString());
            }
            case "BINANCE" -> {
                logger.debug(
                        "Exchange selected"
                );
                exchange = new Binance("", "");
                chartSymbols.getItems().addAll(exchange.getTradePairSymbol());

                logger.info(exchange.getTradePairSymbol().toString());
                chartSymbols.getSelectionModel().selectFirst();

            }
            case "OANDA" -> {
                logger.debug(
                        "Exchange selected"
                );
                exchange = new Oanda("", "");
                chartSymbols.getItems().addAll(exchange.getTradePairSymbol());

                logger.info(exchange.getTradePairSymbol().toString());
                chartSymbols.getSelectionModel().selectFirst();

            }
            case "BITFINEX" -> {
                logger.debug(
                        "Exchange selected"
                );
                exchange = new Bitfinex("", "");
                chartSymbols.getItems().addAll(exchange.getTradePairSymbol());

                logger.info(exchange.getTradePairSymbol().toString());
                chartSymbols.getSelectionModel().selectFirst();

            }
            default -> exchanges.setValue("SELECT EXCHANGE");

        }
        chartTabPane.setPrefSize(exchanges.getPrefWidth(), exchanges.getPrefHeight() - 20);
        //chartTabPane.setTranslateY(20);
        addChart.setOnAction(_ -> {
            logger.debug("Symbol selected");
            TradePair symbol = chartSymbols.getSelectionModel().getSelectedItem();
            Tab tab = new Tab();
            tab.setText(symbol.toString('/'));
            tab.setClosable(true);
            tab.setContent(new HBox(new CandleStickChartDisplay(symbol, exchange)));

            chartTabPane.getTabs().add(tab);
            chartTabPane.getSelectionModel().select(chartTabPane.getTabs().size());
        });


        addExchangeButton.setOnAction(_ -> {
            Tab tab = new Tab();
            tab.setContent(new HBox(new ToolBar(chartSymbols, addChart), new Separator(Orientation.HORIZONTAL),
                    chartTabPane
            ));
            tab.setText(exchanges.getSelectionModel().getSelectedItem());
            exchangeTabPane.getTabs().add(tab);

        });


        ToolBar tradeToolbar = new ToolBar(exchanges, new Separator(Orientation.VERTICAL), addExchangeButton, new Separator(Orientation.VERTICAL), addChart);
        tradeToolbar.setTranslateY(20);
        tradeToolbar.setPrefSize(Double.MAX_VALUE, 20);
        Button autoTradeButton = new Button("Auto");
        autoTradeButton.setBackground(Background.fill(Color.RED));
        autoTradeButton.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm());
        tradeToolbar.getStyleClass().add("tradeToolbar");
        tradeToolbar.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm());
        Button cancelALLButton = new Button("Cancel ALL");

        connect.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm());

        tradeToolbar.getItems().addAll(connect, buyButton, sellButton, cancelALLButton);


        TabPane bottomTabPane = new TabPane();
        ToolBar bar = new ToolBar();

        bottomTabPane.getStyleClass().add("bottomTabPane");
        bar.getStyleClass().add("bar");


        loadChartButton.getStyleClass().add("loadChartButton");



        exchanges.setValue("EXCHANGE");


        bottomTabPane = new TabPane();
        bottomTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);


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

        Canvas newsCanvas = new Canvas(1200, 170);


        newsCanvas.getGraphicsContext2D().setStroke(Color.BLACK);
        newsCanvas.getGraphicsContext2D().setFill(Color.WHITE);

        newsCanvas.getGraphicsContext2D().fillText(
                "Hello Traders ..! \n Below are upcoming news.",
                200,
                10
        );
        StringBuilder upcomingNews = new StringBuilder();
        try {
            for (News news : newsListView.getItems()) {

                if (news.date.getTime() >= new Date().getTime() - (1000L * 60 * 60 * 24 * 30)) {
                    upcomingNews.append(news);


                    newsCanvas.getGraphicsContext2D().fillText(
                            STR."News \{upcomingNews.toString()}",
                            10,
                            5
                    );
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }





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


        bottomTabPane.getTabs().addAll(tradeTab, exposureTab, accountHistoryTab,
                newsTab, alertTab, mailBoxTab, company, market, signal, article, expert);
        ProgressBar progressBar = new ProgressBar();
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setProgress(0);


        Label barInfo = new Label();
        Circle connexionColor = new Circle(15, Paint.valueOf(String.valueOf(Color.rgb(250, 23, 1))));
        barInfo.setText("Not Connected ");
        if (exchange.isConnected()) {
            progressBar.setProgress(1);
            connexionColor.setFill(Paint.valueOf(String.valueOf(Color.rgb(50, 233, 1))));
            barInfo.setText("Connected ");
        }


        bar = new ToolBar(new Label("For Help, press F1!                                                          "), new Separator(Orientation.VERTICAL), new Label("Default"), new Separator(Orientation.VERTICAL),
                new Separator(Orientation.VERTICAL)
                , new Label("|    |    |    |    |    |"), progressBar, new Separator(Orientation.VERTICAL), new Separator(Orientation.VERTICAL), barInfo, connexionColor
        );


        bottomTabPane.setPrefSize(1540, 250);
        bottomTabPane.setTranslateY(500);
        bottomTabPane.setSide(Side.TOP);

        exchangeTabPane.setTranslateY(50);
        exchangeTabPane.setPrefSize(1540, (500));
        bar.setTranslateY(730);
        bar.setPrefSize(1540, 30);

        getChildren().addAll(tradeToolbar, exchangeTabPane, bottomTabPane, bar);










    }

}