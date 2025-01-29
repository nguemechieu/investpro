package org.investpro;

import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DisplayExchange extends AnchorPane {
    private static final Logger logger = LoggerFactory.getLogger(DisplayExchange.class);

    Exchange exchange;
    public DisplayExchange(@NotNull Exchange exchange) throws Exception {
        this.exchange = exchange;
        // Create the ComboBox for Trade Pairs
        ComboBox<String> tradePairsCombo = new ComboBox<>();

        // Populate trade pairs asynchronously

        List<TradePair> data = exchange.getTradePairs();

        for (TradePair pair : data) {
            logger.info("Trade pair: {}", pair);
            tradePairsCombo.getItems().add(pair.toString('/'));
        }


        tradePairsCombo.setPromptText("Select Pair");

        // Create toolbar and buttons
        Button autoTradeBtn = new Button("Auto");
        Button addChartBtn = new Button("Add Chart");


        ToolBar tradeToolBar = new ToolBar(tradePairsCombo, addChartBtn, autoTradeBtn);

        // Tab pane for the main sections
        TabPane chartradeTabPane = new TabPane();
        chartradeTabPane.setPrefSize(1530, 780);
        TreeTableView<News> newsTreeTableView = new TreeTableView<>();

        Canvas upcomingNewsBox = new Canvas();
        upcomingNewsBox.setWidth(540);
        upcomingNewsBox.setHeight(300);
        upcomingNewsBox.getGraphicsContext2D().setFill(Color.web("#212121"));
        upcomingNewsBox.getGraphicsContext2D().fillRect(0, 0, 540, 300);
        upcomingNewsBox.getGraphicsContext2D().setStroke(Color.WHITE);

        newsTreeTableView.setEditable(true);
        newsTreeTableView.setPrefHeight(300);
        newsTreeTableView.setPrefWidth(540);
        newsTreeTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        List<TreeItem<News>> newslist = new ArrayList<>();

        for (News news : new NewsDataProvider().getNewsList()) {
            newslist.add(new TreeItem<>(news));
        }

        upcomingNewsBox.getGraphicsContext2D().strokeText("Upcoming News " + (newslist), 20, 20);
        logger.info("News list size: {}", newslist.size());
        logger.info(
                newslist.toString()
        );

        Tab accountTab = new Tab("Account");
        accountTab.setContent(new VBox(new Label("Account"), new AccountView(exchange)));

        // News section
        Tab newsTab = new Tab("Forex ");
        newsTab.setContent(new VBox(
                upcomingNewsBox, newsTreeTableView));

        // Other Tabs (Trade, Account, Orders, Positions)
        Tab ordersTab = new Tab("Orders");


        ordersTab.setContent(new VBox(
                new Label("Orders"), new OrdersView(exchange)
        ));
        Tab positionTab = new Tab("Position");
        positionTab.setContent(new VBox(new Label("Position"), new PositionView(exchange)));
        Tab tradeTab = new Tab("Trading");


        Tab coinInfoTab = new Tab("Coin info");

        coinInfoTab.setContent(new VBox(
                new Label("Coins"),
                new CoinInfoView(exchange)
        ));

        // Browser Tab
        Tab browser = new Tab("Mini Web");
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

        Tab pendingOrdersTab = new Tab("Pending Orders");
        pendingOrdersTab.setContent(new VBox(
                new Label("Pending Orders"),
                new PendingOrdersView(exchange)
        ));



        // Adding all tabs to the main TabPane
        TabPane tradingTabPane = new TabPane(
                accountTab,
                tradeTab, positionTab, ordersTab, pendingOrdersTab, newsTab, coinInfoTab, browser
        );
        tradingTabPane.setPrefSize(1530, 780);
        tradingTabPane.setSide(Side.TOP);
        tradingTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);

        // Set up the trade tab with toolbar and chart tab pane
        tradeToolBar.setPrefSize(1530, 20);
        tradeTab.setContent(new VBox(tradeToolBar, new Separator(Orientation.HORIZONTAL), chartradeTabPane));



        // Add Chart Button Action
        addChartBtn.setOnAction(_ -> {
            try {
                addChart(tradePairsCombo.getSelectionModel().getSelectedItem(), chartradeTabPane);
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, e.getMessage());
            }
        });

        // Add everything to the main container
        getChildren().add(tradingTabPane);
    }





    private void addChart(String tradePairsCombo, @NotNull TabPane chartradeTabPane) throws Exception {
        Tab chartTab = new Tab(tradePairsCombo);
        TradePair tradePairsCombo0 = new TradePair(tradePairsCombo.split("/")[0], tradePairsCombo.split("/")[1]);
        CandleStickChartDisplay chartDisplay = new CandleStickChartDisplay(exchange, tradePairsCombo0);
        chartDisplay.setPrefSize(1530, 780);
        chartTab.setContent(chartDisplay);
        chartradeTabPane.getTabs().add(chartTab);
        chartradeTabPane.getSelectionModel().select(chartTab);
    }








}