package org.investpro;

import com.jfoenix.controls.RecursiveTreeItem;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;


public class TradeView extends Region  {
    private static final Logger logger = LoggerFactory.getLogger(TradeView.class);

    public TradeView(@NotNull Exchange exchange, String telegramToken) throws IOException, InterruptedException, ParseException, URISyntaxException, SQLException, ClassNotFoundException {

        super();
        TabPane tradingTabPane = new TabPane();
        tradingTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
        tradingTabPane.setSide(Side.TOP);
        tradingTabPane.setPadding(new Insets(10));

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);

        AnchorPane anchorPane = new AnchorPane();
        logger.debug("Creating TradeView");
        ChoiceBox<String> symbolChoicebox = new ChoiceBox<>();
        try {
            symbolChoicebox.getItems().addAll(exchange.getTradePair());
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        symbolChoicebox.setValue(exchange.getTradePair().get(0));

        double quantity = 1011;
        long orderID = Math.round(Instant.now().getEpochSecond() * 1000000);
        double price = exchange.getLivePrice(new TradePair(
                symbolChoicebox.getValue().split("/")[0],
                symbolChoicebox.getValue().split("/")[1]
        ));

        logger.debug("Price  :  " + price);

        double stopPrice = price - 0.01;
        double takeProfitPrice = price + 0.01;
        Button removeBtn = new Button("Remove");
        removeBtn.setOnAction(event -> tradingTabPane.getTabs().remove(tradingTabPane.getSelectionModel().getSelectedItem()));
        Button AddBtn = new Button("LOAD NEW CHART");
        AddBtn.setOnAction(
                event -> {

                    String sym1 = symbolChoicebox.getValue().split("/")[0];
                    String sym2 =          symbolChoicebox.getValue().split("/")[1];
                    logger.debug(sym1 + sym2);
                    DraggableTab tradeTab2 = new DraggableTab(sym1 + "/" + sym2, "Invest.png");
                    CandleStickChartContainer container2;
                    try {
                        TradePair tradePair3 = new TradePair(sym1, sym2);
                        container2 = new CandleStickChartContainer(exchange, tradePair3, telegramToken, true);
                        tradeTab2.setContent(container2);
                        tradingTabPane.getTabs().add(tradeTab2);
                        tradingTabPane.getSelectionModel().select(tradeTab2);
                    } catch (URISyntaxException | IOException | SQLException | ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });


        Button tradingBtn = new Button("TRADING BUTTONS");
        tradingBtn.setOnAction(
                event -> {
                    GridPane gridPane = new GridPane();
                    gridPane.setHgap(20);
                    gridPane.add(new Label(exchange.getName()), 0, 0
                    );
                    gridPane.setVgap(20);
                    gridPane.setPadding(new Insets(10, 10, 10, 10));
                    Spinner<Double> spinner = new Spinner<>(0.01, 100000, 0);
                    spinner.setValueFactory(
                            new SpinnerValueFactory.DoubleSpinnerValueFactory(0.01, 100000, 0)
                    );
                    spinner.setBorder(Border.stroke(
                            Color.rgb(19, 184, 180, 0.5)
                    ));
                    spinner.setEditable(true);
                    SpinnerValueFactory<Double> sp = new SpinnerValueFactory.DoubleSpinnerValueFactory(0.01, 100000, 0);

                    spinner.setValueFactory(sp);

                    spinner.setPromptText("Enter Lot size");


                    Button btnBuy = new Button("BUY");


                    btnBuy.setOnAction(
                            event1 -> {
                                try {
                                    exchange.createOrder(
                                            new TradePair(
                                                    symbolChoicebox.getValue().split("/")[0],
                                                    symbolChoicebox.getValue().split("/")[1]
                                            ),
                                            Side.BUY,

                                            ENUM_ORDER_TYPE.MARKET, price,

                                            quantity,
                                            new Date(), stopPrice,
                                            takeProfitPrice


                                    );
                                } catch (IOException | InterruptedException | SQLException | ClassNotFoundException e) {
                                    throw new RuntimeException(e);
                                }


                            }
                    );


                    Button btnSell = new Button(" SELL ");
                    btnSell.setOnAction(
                            event1 -> {
                                try {
                                    exchange.createOrder(
                                            new TradePair(
                                                    symbolChoicebox.getValue().split("/")[0],
                                                    symbolChoicebox.getValue().split("/")[1]),
                                            Side.SELL,
                                            ENUM_ORDER_TYPE.MARKET, price,
                                            quantity,
                                            new Date(), stopPrice,
                                            takeProfitPrice);
                                } catch (IOException | InterruptedException | SQLException | ClassNotFoundException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                    );

                    gridPane.setStyle(
                            "-fx-background-color: #000000;" +
                                    "-fx-border-color: #000000;" +
                                    "-fx-border-width: 2;" +
                                    "-fx-padding: 10;" +
                                    "-fx-font-size: 16;" +
                                    "-fx-font-weight: bold;" +
                                    "-fx-text-fill: #ffffff;" +
                                    "-fx-alignment: center;" +
                                    "-fx-background-radius: 10;" +
                                    "-fx-border-radius: 10;" +
                                    "-fx-background-insets: 0;"

                    );

                    Button closeAll = new Button("CLOSE ALL");
                    closeAll.setOnAction(event1 -> {
                        try {
                            exchange.closeAllOrders();
                        } catch (IOException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });


                    Button trailingBuy = new Button("TRAILING BUY");
                    Button trailingSell = new Button("TRAILING SELL");
                    trailingSell.setOnAction(event2 -> {

                        try {
                            exchange.createOrder(
                                    new TradePair(
                                            symbolChoicebox.getValue().split("/")[0],
                                            symbolChoicebox.getValue().split("/")[1]


                                    ),
                                    Side.BUY,
                                    ENUM_ORDER_TYPE.ENTRY, price,
                                    quantity,
                                    new Date(), stopPrice,
                                    takeProfitPrice

                            );
                        } catch (IOException | InterruptedException | SQLException | ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    });


                    trailingBuy.setOnAction(event2 -> {

                        try {
                            exchange.createOrder(
                                    new TradePair(
                                            symbolChoicebox.getValue().split("/")[0],
                                            symbolChoicebox.getValue().split("/")[1]
                                    ),
                                    Side.BUY,

                                    ENUM_ORDER_TYPE.ENTRY, price,

                                    quantity,
                                    new Date(), stopPrice,
                                    takeProfitPrice
                            );
                        } catch (IOException | InterruptedException | SQLException | ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    });


                    Button buyStopBtn = new Button("BUY STOP");
                    Button sellStopBtn = new Button("SELL STOP");
                    Button sellCancelBtn = new Button("SELL CANCEL");
                    Button buyCancelBtn = new Button("BUY CANCEL");
                   // Button cancelAllBtn = new Button("CANCEL ALL");


                    Button buyLimitBtn = new Button("BUY LIMIT");
                    buyLimitBtn.setOnAction(event3 -> {
                        try {
                            exchange.createOrder(
                                    new TradePair(
                                            symbolChoicebox.getValue().split("/")[0],
                                            symbolChoicebox.getValue().split("/")[1]
                                    ),
                                    Side.BUY,

                                    ENUM_ORDER_TYPE.LIMIT, price,

                                    quantity,
                                    new Date(), stopPrice,
                                    takeProfitPrice

                            );
                        } catch (IOException | InterruptedException | SQLException | ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    Button sellLimitBtn = new Button("SELL LIMIT");
                    sellLimitBtn.setOnAction(event3 -> {
                        try {
                            exchange.createOrder(
                                    new TradePair(
                                            symbolChoicebox.getValue().split("/")[0],
                                            symbolChoicebox.getValue().split("/")[1]
                                    ),
                                    Side.SELL,

                                    ENUM_ORDER_TYPE.LIMIT, price,

                                    quantity,
                                    new Date(), stopPrice,
                                    takeProfitPrice

                            );
                        } catch (IOException | InterruptedException | SQLException | ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    });

                    buyStopBtn.setOnAction(
                            event3 -> {
                                try {
                                    exchange.createOrder(
                                            new TradePair(
                                                    symbolChoicebox.getValue().split("/")[0],
                                                    symbolChoicebox.getValue().split("/")[1]),
                                            Side.BUY,

                                            ENUM_ORDER_TYPE.ENTRY, price,

                                            quantity,
                                            new Date(), stopPrice,
                                            takeProfitPrice
                                    );
                                } catch (IOException | InterruptedException | SQLException | ClassNotFoundException e) {
                                    throw new RuntimeException(e);
                                }
                                sellStopBtn.setOnAction(
                                        event4 -> {
                                            try {
                                                exchange.createOrder(
                                                        new TradePair(
                                                                symbolChoicebox.getValue().split("/")[0],
                                                                symbolChoicebox.getValue().split("/")[1]
                                                        ),
                                                        Side.SELL,

                                                        ENUM_ORDER_TYPE.ENTRY, price,

                                                        quantity,
                                                        new Date(), stopPrice,
                                                        takeProfitPrice


                                                );
                                            } catch (IOException | InterruptedException | SQLException |
                                                     ClassNotFoundException e) {
                                                throw new RuntimeException(e);
                                            }
                                        });
                                sellCancelBtn.setOnAction(
                                        event5 -> {
                                            try {
                                                exchange.cancelOrder(orderID);
                                            } catch (IOException | InterruptedException e) {
                                                throw new RuntimeException(e);
                                            }
                                        });
                                buyCancelBtn.setOnAction(
                                        event6 -> {
                                            try {
                                                exchange.cancelOrder(orderID);
                                            } catch (IOException | InterruptedException e) {
                                                throw new RuntimeException(e);
                                            }
                                        });


                            });


                    Stage stage = new Stage();
                    gridPane.add(sellLimitBtn, 0, 3);
                    gridPane.add(buyLimitBtn, 1, 3);

                    gridPane.add(buyStopBtn, 2, 3);
                    gridPane.add(sellStopBtn, 3, 3);

                    gridPane.add(btnBuy, 0, 0);
                    gridPane.add(btnSell, 2, 0);
                    gridPane.add(spinner, 1, 0);

                    gridPane.add(closeAll, 0, 5);
                    gridPane.add(trailingBuy, 0, 2);
                    gridPane.add(trailingSell, 1, 2);
                    stage.setTitle(" InvestPro");
                    stage.setScene(new Scene(gridPane, 600, 320));
                    stage.setAlwaysOnTop(true);
                    stage.show();
                });

        Button orderViewBtn = new Button("Order View");

        orderViewBtn.setOnAction(
                event3 -> {
                    TreeTableView<Trade> orders = new TreeTableView<>();
                    orders.setEditable(true);
                    Scene scene = new Scene(orders, 1300, 300);
                    Stage stage = new Stage();
                    stage.setScene(scene);
                    stage.show();
                });
        Button walletBtn = new Button("-- WALLET  -- ");
        walletBtn.setOnAction(event7 -> new Wallet(exchange));

        symbolChoicebox.setValue(
                "SELECT A SYMBOL"
        );

        Button orderHistoryBtn =
                new Button("-- ORDERS -- ");
        orderHistoryBtn.setOnAction(
                event9 ->
                {
                    try {
                        new OrdersDisplay(exchange);
                    } catch (IOException | InterruptedException | ParseException | URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }
        );


        anchorPane.setPrefSize(1230, 780);
        tabPane.setPrefSize(1530, 630);
        tabPane.setTranslateY(25);
        tabPane.setSide(Side.BOTTOM);
        tabPane.getTabs().add(new Tab("--ORDERS VIEW--"));
        tabPane.getTabs().get(0).setContent(new VBox(

        ));


        Tab tab1 = new Tab(exchange.getName() + " -- WALLET --");
        tab1.setClosable(false);

        tabPane.getTabs().add(tab1);

        Tab tab2 = new Tab("-- Stellar Lumen  Network  (XLM) --");
        tab2.setClosable(false);
        ObservableList<?> obs = FXCollections.observableArrayList();
        tab2.setContent(new VBox(new Separator(Orientation.HORIZONTAL), new VBox(new ListView<>(obs))));
        tabPane.getTabs().add(tab2);

        tabPane.getTabs().get(2).setContent(new Label("-- STELLAR LUMEN 'S --")
        );

        tabPane.getTabs().add(new Tab("-- TRADING VIEW --"));
        tabPane.getSelectionModel().select(3);
        for (int i = 0; i < tabPane.getTabs().size(); i++) {
            tabPane.getTabs().get(i).setContent(tradingTabPane);
        }
        for (int i = 0; i < tradingTabPane.getTabs().size(); i++) {
            if (exchange instanceof Oanda oanda) {

                try {
                    tradingTabPane.getTabs().get(i).setContent(
                            new CandleStickChartContainer(oanda, new TradePair(symbolChoicebox.getValue().split("/")[0],
                                    symbolChoicebox.getValue().split("/")[1]
                            ), telegramToken, true)
                    );
                } catch (URISyntaxException | IOException | SQLException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            } else {
                try {
                    tradingTabPane.getTabs().get(i).setContent(new CandleStickChartContainer(exchange, new TradePair(

                            symbolChoicebox.getValue().split("/")[0],
                            symbolChoicebox.getValue().split("/")[1]
                    ), telegramToken, true));
                } catch (URISyntaxException | IOException | SQLException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }


        Button connexionBtn = new Button("-- CONNECTION --");
        connexionBtn.setOnAction(
                event8 -> new ConnectionScene(exchange));
        Button allOrdersBtn = new Button("-- TOTAL ORDERS --");
        Button sendScreenShotBtn = new Button("-- SEND SCREENSHOT --");
        sendScreenShotBtn.setOnAction(event -> {

            try {
                    TelegramClient telegramClient = new TelegramClient(telegramToken);
                    File fil = File.createTempFile(
                            exchange.getName(), "png"
                    );
                    Screenshot.capture(fil);
                    telegramClient.sendPhoto(fil);

            } catch (IOException | TelegramApiException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        allOrdersBtn.setOnAction(event -> {
            try {
                new OrdersDisplay(exchange);
            } catch (IOException | InterruptedException | ParseException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        });
        Button tradeStrategyBtn = new Button("-- STRATEGY TESTER --");
        tradeStrategyBtn.setOnAction(event -> new StrategyTester(exchange));
        Button navigationBtn = new Button("-- NAVIGATION --");
        navigationBtn.setOnAction(event -> new Navigator(exchange));
        Button pendingOrdersBtn = new Button("-- PENDING ORDERS --");
        pendingOrdersBtn.setOnAction(event -> {
            try {
                new PendingOrders(exchange);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

        });


        Button accountBtn = new Button("-- ACCOUNT --");
        accountBtn.setOnAction(event -> {
            @NotNull List<Account> account;
            try {
                account = exchange.getAccount();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }


            TreeTableView<Account> accounts = new TreeTableView<>();
            TreeTableColumn<Account, String> name = new TreeTableColumn<>("Name");
            TreeTableColumn<Account, String> balance = new TreeTableColumn<>("Balance");
            TreeTableColumn<Account, String> currency = new TreeTableColumn<>("Currency");
            TreeTableColumn<Account, String> type = new TreeTableColumn<>("Type");
            TreeTableColumn<Account, String> status = new TreeTableColumn<>("Status");
            TreeTableColumn<Account, String> created = new TreeTableColumn<>("Created");
            TreeTableColumn<Account, String> updated = new TreeTableColumn<>("Updated");
           TreeTableColumn<Account, String> id = new TreeTableColumn<>("ID");


            accounts.getColumns().addAll(id,name, balance, currency, type, status, created, updated);
            ObservableList<Account> obs0 = FXCollections.observableArrayList();
            obs0.addAll(account);
            RecursiveTreeItem<Account> obs1 = new RecursiveTreeItem<>(obs0, RecursiveTreeObject::getChildren);
            obs1.setValue(account.get(0));
            accounts.setRoot(obs1);
            accounts.setEditable(true);
            Scene scene = new Scene(accounts, 1300, 500);
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.setTitle("-- ACCOUNT --");
            stage.show();
        });


        HBox hBox = new HBox(removeBtn,
                AddBtn,
                tradingBtn,
                new HBox(symbolChoicebox),
                orderHistoryBtn, pendingOrdersBtn,
                connexionBtn,
                allOrdersBtn,
                sendScreenShotBtn,
                tradeStrategyBtn,
                navigationBtn,
                accountBtn

        );
        hBox.setPrefSize(1500, 15);
        anchorPane.getChildren().addAll(hBox, tabPane);
        tabPane.getSelectionModel().select(tabPane.getTabs().size() - 1);
        setPadding(new Insets(10, 10, 10, 10));
        getChildren().add(anchorPane);


    }


}