package org.investpro;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DisplayExchange extends AnchorPane {

    private static final Logger logger = LoggerFactory.getLogger(DisplayExchange.class);


    final TreeTableView<News> newsTreeTableView = createNewsTreeTableView();


    Exchange exchange;

    // Executor for periodic updates
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private final List<Order> ordersData = new ArrayList<>();
    Label accountDetailsLabel = new Label("Account Details");

    public DisplayExchange(@NotNull Exchange exchange) throws Exception {
        this.exchange = exchange;
        // Create the ComboBox for Trade Pairs
        ComboBox<String> tradePairsCombo = new ComboBox<>();

        ordersData.addAll(exchange.getOrders());


        // Populate trade pairs asynchronously

        List<TradePair> data = exchange.getTradePairs();

        for (TradePair pair : data) {
            logger.info("Trade pair: {}", pair);
            tradePairsCombo.getItems().add(pair.toString('/'));
        }


        tradePairsCombo.setPromptText("Select TradePair");

        // Create toolbar and buttons
        Button autoTradeBtn = new Button("Auto Trade");
        Button addChartBtn = new Button("Add Chart");


        ToolBar tradeToolBar = new ToolBar(tradePairsCombo, addChartBtn, autoTradeBtn);

        // Tab pane for the main sections
        TabPane chartradeTabPane = new TabPane();
        chartradeTabPane.setPrefSize(1540, 780);

        Canvas upcomingNewsBox = new Canvas();
        upcomingNewsBox.setWidth(1540);
        upcomingNewsBox.setHeight(300);
        upcomingNewsBox.getGraphicsContext2D().setFill(Color.web("#212121"));
        upcomingNewsBox.getGraphicsContext2D().fillRect(0, 0, 1540, 300);
        upcomingNewsBox.getGraphicsContext2D().setStroke(Color.WHITE);
        upcomingNewsBox.getGraphicsContext2D().strokeText("Upcoming News " + (((newsTreeTableView.getRoot().getValue().getDate().getTime()) >= new Date().getTime()) ? "N/A" : newsTreeTableView.getRoot().getValue().getTitle()), 20, 20);


        // News section
        Tab newsTab = new Tab("Forex News");

        newsTab.setContent(new VBox(

                upcomingNewsBox, new Separator(Orientation.HORIZONTAL), newsTreeTableView));

        // Other Tabs (Trade, Account, Orders, Positions)
        Tab tradeTab = new Tab("Trading");
        Tab accountTab = createAccountTab();


        Tab coinInfoTab = new CoinInfoTab();
        coinInfoTab.setText("Coin Information");

        Tab orderTab = createOrderTab();


        // Browser Tab
        Tab browser = new Tab("Web Browser");
        browser.setContent(new Browser());
        browser.setClosable(false);


        // Add Chart Button Action
        addChartBtn.setOnAction(_ -> {
            try {
                addChart(tradePairsCombo.getSelectionModel().getSelectedItem(), chartradeTabPane);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Start periodic data updates
        startDataUpdates();

        // Adding all tabs to the main TabPane
        TabPane tradingTabPane = new TabPane(tradeTab, accountTab, orderTab, newsTab, coinInfoTab, browser);
        tradingTabPane.setPrefSize(1540, 780);
        tradingTabPane.setSide(Side.LEFT);
        tradingTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);

        // Set up the trade tab with toolbar and chart tab pane
        tradeToolBar.setPrefSize(1540, 20);
        tradeTab.setContent(new VBox(tradeToolBar, new Separator(Orientation.HORIZONTAL), chartradeTabPane));

        // Add everything to the main container
        getChildren().add(tradingTabPane);

        // Add Chart Button Action
        addChartBtn.setOnAction(_ -> {
            try {
                addChart(tradePairsCombo.getSelectionModel().getSelectedItem(), chartradeTabPane);
            } catch (Exception e) {
                new Messages(Alert.AlertType.ERROR, e.getMessage());
            }
        });

        // Start periodic data updates
        startDataUpdates();
    }

    private @NotNull TreeTableView<News> createNewsTreeTableView() {
        TreeTableView<News> newsTableView = new TreeTableView<>();
        TreeItem<News> root = new TreeItem<>(new News("", "", "", new Date(), "", "")); // Placeholder root

        TreeTableColumn<News, String> dateCol = new TreeTableColumn<>("Date");
        dateCol.setPrefWidth(150);
        dateCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getDate().toString()));

        TreeTableColumn<News, String> titleCol = new TreeTableColumn<>("Title");
        titleCol.setPrefWidth(200);
        titleCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getTitle()));

        TreeTableColumn<News, String> countryCol = new TreeTableColumn<>("Country");
        countryCol.setPrefWidth(150);
        countryCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getCountry()));

        TreeTableColumn<News, String> impactCol = new TreeTableColumn<>("Impact");
        impactCol.setPrefWidth(150);
        impactCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getImpact()));

        newsTableView.getColumns().addAll(dateCol, titleCol, countryCol, impactCol);
        newsTableView.setRoot(root);
        root.setExpanded(true);
        return newsTableView;
    }

    private @NotNull Tab createAccountTab() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
        Tab acc = new Tab("Account ");

        // Add account details here

        acc.setContent(
                new AccountAnchor(exchange)
        );
        return acc;
    }


    private void addChart(String tradePairsCombo, @NotNull TabPane chartradeTabPane) throws Exception {


        Tab chartTab = new Tab(tradePairsCombo);
        TradePair tradePairsCombo0 = new TradePair(tradePairsCombo.split("/")[0], tradePairsCombo.split("/")[1]);

        CandleStickChartDisplay chartDisplay = new CandleStickChartDisplay(exchange, tradePairsCombo0);
        chartDisplay.setPrefSize(1540, 780);
        chartTab.setContent(chartDisplay);
        chartradeTabPane.getTabs().add(chartTab);
        chartradeTabPane.getSelectionModel().select(chartTab);
    }

    private @NotNull Tab createOrderTab() {


        Tab orderTab = new Tab("Orders");
        TreeTableView<Order> orderTableView = new TreeTableView<>();
        TreeItem<Order> root = new TreeItem<>(new Order()); // Placeholder root
        TreeTableColumn<Order, String> typeCol = new TreeTableColumn<>("Type");
        typeCol.setPrefWidth(100);
        typeCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getOrderType().toString()));

        TreeTableColumn<Order, String> sideCol = new TreeTableColumn<>("Side");
        sideCol.setPrefWidth(100);
        sideCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getSide().toString()));

        TreeTableColumn<Order, String> priceCol = new TreeTableColumn<>("Price");
        priceCol.setPrefWidth(100);
        priceCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(String.format("%.2f", param.getValue().getValue().getPrice())));

        TreeTableColumn<Order, String> sizeCol = new TreeTableColumn<>("Size");
        sizeCol.setPrefWidth(100);
        sizeCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(String.format("%.2f", param.getValue().getValue().getSize())));

        TreeTableColumn<Order, String> timeCol = new TreeTableColumn<>("Time");
        timeCol.setPrefWidth(100);
        timeCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(Date.from(Instant.ofEpochMilli(param.getValue().getValue().getTime())).toString()));

        TreeTableColumn<Order, String> isWorkingCol = new TreeTableColumn<>("Working");
        isWorkingCol.setPrefWidth(100);
        isWorkingCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().isWorking() ? "Yes" : "No"));

        TreeTableColumn<Order, String> orderStatusCol = new TreeTableColumn<>("Status");
        orderStatusCol.setPrefWidth(100);
        orderStatusCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getOrderStatus().toString()));
        TreeTableColumn<Order, String> stopLoss = new TreeTableColumn<>("SL");
        stopLoss.setPrefWidth(100);
        stopLoss.setCellValueFactory(param -> new ReadOnlyStringWrapper(String.format("%.2f", param.getValue().getValue().getStopLoss())));
        TreeTableColumn<Order, String> takeProfit = new TreeTableColumn<>("TP");
        takeProfit.setPrefWidth(100);
        takeProfit.setCellValueFactory(param -> new ReadOnlyStringWrapper(String.format("%.2f", param.getValue().getValue().getTakeProfit())));
        orderTableView.getColumns().addAll(typeCol, sideCol, priceCol, stopLoss, takeProfit, sizeCol, timeCol, isWorkingCol, orderStatusCol);


        orderTableView.setRoot(root);
        root.setExpanded(true);
        orderTab.setContent(new VBox(new Label("================== Orders Details ================"), new Separator(Orientation.HORIZONTAL), orderTableView));
        return orderTab;

    }

    private void startDataUpdates() {
        executorService.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            updateOrders();
            updateAccount();

            updateNews();
        }), 0, 10, TimeUnit.SECONDS); // Update every 10 seconds
    }

    private void updateAccount() {
        CompletableFuture.runAsync(() -> {
            try {
                List<Account> account = exchange.getAccounts();
                Platform.runLater(() -> account.stream().peek(c -> accountDetailsLabel.setText(
                        "Account Balance: " + c.balance.getFree() + "\n" +
                                "Free Margin: " + c.getFreeMargin() + "\n" +
                                "Equity: " + c.getEquity() + "\n" +
                                "Available Balance: " + c.getAvailableBalance() + "\n" +
                                "Unrealized PnL: " + c.getUnrealizedProfitLoss() + "\n" +
                                "Margin Level: " + c.getMarginLevel() + "\n" +
                                "Margin Call: " + (c.isMarginCall() ? "Yes" : "No") + "\n" +
                                "Position Margin: " + c.getPosition() + "\n" +
                                "Leverage: " + c.getLeverage() + "\n" +
                                "Position Size: " + c.getPositionSize() + "\n" +
                                "Realised PnL: " + c.getRealizedProfitLoss() + "\n")));
            } catch (Exception e) {
                logger.error(
                        "Error updating account: %s".formatted(e.getMessage()),
                        e
                );
            }
        });
    }



    private void updateOrders() {
        CompletableFuture.runAsync(() -> {
            try {
                List<Order> updatedOrders = exchange.getOrders();
                Platform.runLater(() -> ordersData.addAll(updatedOrders));
            } catch (Exception e) {
                logger.error(
                        "Error updating orders: %s".formatted(e.getMessage()),
                        e
                );
            }
        });
    }



    private void updateNews() {
        CompletableFuture.runAsync(() -> {
            try {
                List<News> updatedNews = new NewsDataProvider().getNewsList();
                Platform.runLater(() -> {
                    newsTreeTableView.getRoot().getChildren().clear();
                    updatedNews.forEach(news -> newsTreeTableView.getRoot().getChildren().add(new TreeItem<>(news)));
                    updateUpcomingNewsBox(updatedNews);
                });
            } catch (Exception e) {

                logger.error(
                        "Error updating news: %s".formatted(e.getMessage()),
                        e
                );
            }
        });
    }

    private void updateUpcomingNewsBox(List<News> newsList) {

        for (News news : newsList) {

            Canvas newsBox = new Canvas();
            if (news.getDate().getTime() >= new Date().getTime()) {
                GraphicsContext graphicsContext2D = newsBox.getGraphicsContext2D();
                graphicsContext2D.setFill(Color.web(news.getCountry().equals("USD") ? "#0076a3" : "#ff9800"));
                graphicsContext2D.fillRect(0, 0, 1500, 300);
                graphicsContext2D.setStroke(Color.BLACK);
                graphicsContext2D.strokeText(news.getDate().toString(), 20, 20);
                graphicsContext2D.setFill(Color.GREEN);
                graphicsContext2D.fillText(news.getTitle(), 20, 50);
                graphicsContext2D.setFill(Color.WHITE);
                graphicsContext2D.fillText(news.getCountry(), 20, 80);
                graphicsContext2D.setFill(Color.WHITE);
                graphicsContext2D.fillText(news.getImpact(), 20, 110);
                newsBox.setLayoutX(1500 - newsBox.getWidth());
                newsBox.setLayoutY(300 - newsBox.getHeight());

            }
        }

    }


}