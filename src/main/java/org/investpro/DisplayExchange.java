package org.investpro;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;


import java.io.IOException;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.ExecutionException;


public class DisplayExchange extends Region {

    public DisplayExchange(@NotNull Exchange exchange) throws IOException, InterruptedException, ParseException, SQLException, ClassNotFoundException, ExecutionException {

        ComboBox<TradePair> tradePairsCombo = new ComboBox<>();
        tradePairsCombo.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(TradePair tradePair, boolean empty) {
                super.updateItem(tradePair, empty);
                if (empty) {
                    setText("Select Trade Pair");
                } else {
                    setText(tradePair.getSymbol());
                }
            }
        });

        tradePairsCombo.getItems().addAll(exchange.getTradePairs());//==null?TradePair.of("ETH", "USD"): (TradePair) exchange.getTradePairs()


        Button autoTradeBtn = new Button("AUTO TRADE");
        Button addChartBtn = new Button("ADD CHART");
        ToolBar tradeToolBar = new ToolBar(tradePairsCombo, addChartBtn, autoTradeBtn);

        Tab tradeTab = new Tab("TRADE");
        Tab accountTab = new Tab("ACCOUNT");
        Tab positionTab = new Tab("POSITIONS");
        Tab orderTab = new Tab("ORDERS");
        Tab marketDataTab = new Tab("MARKET DATA");
        Tab historicalDataTab = new Tab("HISTORICAL DATA");
        Tab newsTab = new Tab("FOREX NEWS");
        TabPane chartradeTabPane = new TabPane();
        chartradeTabPane.setPrefSize(1540, 780);


        // Initialize News TreeTableView
        TreeTableView<News> newsTreeTableView = new TreeTableView<>();

// Populate News TreeTableView
        TreeItem<News> root = new TreeItem<>(); // Placeholder root
        List<News> newsList = new NewsDataProvider().getNews();
        for (News newsItem : newsList) {
            TreeItem<News> item = new TreeItem<>(newsItem);
            root.getChildren().add(item);
        }

        root.setValue(newsList.getFirst());
        root.setExpanded(true);
        newsTreeTableView.setRoot(root);

// Initialize TreeTableView for displaying details with String columns
        TreeTableView<String> newsDetailTreeTableView = new TreeTableView<>();

// Create and configure columns
        TreeTableColumn<String, String> dateCol = new TreeTableColumn<>("Date");
        dateCol.setPrefWidth(150);
        dateCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue()));

        TreeTableColumn<String, String> titleCol = new TreeTableColumn<>("Title");
        titleCol.setPrefWidth(150);
        titleCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue()));

        TreeTableColumn<String, String> impactCol = new TreeTableColumn<>("Impact");
        impactCol.setPrefWidth(150);
        impactCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue()));

        TreeTableColumn<String, String> forecastCol = new TreeTableColumn<>("Forecast");
        forecastCol.setPrefWidth(150);
        forecastCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue()));

        TreeTableColumn<String, String> previousCol = new TreeTableColumn<>("Previous");
        previousCol.setPrefWidth(150);
        previousCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue()));
// Optionally, you can set a root for the second TreeTableView as well
        TreeItem<String> detailRoot = new TreeItem<>("Details");
        detailRoot.setExpanded(true);
        newsDetailTreeTableView.setRoot(detailRoot);
// Add columns to the TreeTableView
        newsDetailTreeTableView.getColumns().addAll(dateCol, titleCol, impactCol, forecastCol, previousCol);
        Canvas upcoming_new_box = new Canvas(
                1500, 300
        );
        upcoming_new_box.getStyleClass().add("upcoming-news-box_canvas");
        upcoming_new_box.getGraphicsContext2D().setFill(
                Color.BLACK
        );
        upcoming_new_box.getGraphicsContext2D().setStroke(Color.WHITE);
        upcoming_new_box.getGraphicsContext2D().fillRect(
                0, 0,
                1500, 400
        );
        upcoming_new_box.getGraphicsContext2D().strokeRect(
                0, 0,
                1500, 400
        );

        upcoming_new_box.getGraphicsContext2D().strokeLine(
                0, 400,
                1500, 400
        );
        upcoming_new_box.getGraphicsContext2D().strokeText(
                "Upcoming News",
                400,
                10
        );
        upcoming_new_box.getGraphicsContext2D().strokeText(
                "Date",
                10, 50

        );

        upcoming_new_box.getGraphicsContext2D().strokeText(
                newsList.stream().map(news0 -> news0.getDate().toString()).findFirst().orElse(""),
                100, 50

        );
        upcoming_new_box.getGraphicsContext2D().strokeText(
                "Title",
                10, 70

        );
        upcoming_new_box.getGraphicsContext2D().strokeText(
                newsList.getFirst().getTitle(),
                100, 70

        );
        upcoming_new_box.getGraphicsContext2D().strokeText(
                "Impact",
                10, 90

        );
        upcoming_new_box.getGraphicsContext2D().strokeText(
                newsList.getFirst().getImpact(),
                100, 90

        );
        upcoming_new_box.getGraphicsContext2D().strokeText(
                "Forecast",
                10, 110

        );
        upcoming_new_box.getGraphicsContext2D().strokeText(
                newsList.getFirst().getForecast(),
                100, 110

        );

        upcoming_new_box.getGraphicsContext2D().strokeText(
                "Previous",
                10, 130

        );
        upcoming_new_box.getGraphicsContext2D().strokeText(
                newsList.getFirst().getPrevious(),
                100, 130

        );

        newsTab.setContent(new VBox(upcoming_new_box, new Separator(Orientation.HORIZONTAL), newsDetailTreeTableView));
        chartradeTabPane.setTranslateX(0);
        chartradeTabPane.setTranslateY(30);
        Canvas accountCanvas = new Canvas(1540, 710);
        accountCanvas.getGraphicsContext2D().setFill(
                Color.BLACK
        );
        accountCanvas.getGraphicsContext2D().setStroke(Color.GOLD);
        accountCanvas.getStyleClass().add("account-canvas");

        accountCanvas.getGraphicsContext2D().fillRect(
                0, 0,
                1540, 720
        );

        accountCanvas.getGraphicsContext2D().fillText(
                "------------------- Account Information --------------------",
                500,
                20
        );
        accountCanvas.getGraphicsContext2D().strokeText(
                "Account ID",
                10,
                30
        );
        accountCanvas.getGraphicsContext2D().strokeText(
                exchange.getClass().getSimpleName(),
                100,
                30
        );


        Canvas marketDataCanvas = new Canvas(1540, 700);
        marketDataCanvas.getStyleClass().add("market-data-canvas");
        marketDataCanvas.getGraphicsContext2D().setFill(
                Color.BLACK
        );
        marketDataCanvas.getGraphicsContext2D().fillRect(
                0, 0,
                1540, 500
        );

        Canvas historicalDataCanvas = new Canvas(1540, 700);
        historicalDataCanvas.getStyleClass().add("historical-data-canvas");
        historicalDataCanvas.getGraphicsContext2D().setFill(
                Color.BLACK
        );
        historicalDataCanvas.getGraphicsContext2D().fillRect(
                0, 0,
                1540, 500
        );


        accountTab.setContent(accountCanvas);
        ListView<Order> orderListView = new ListView<>(exchange.getOrders());
        orderTab.setContent(new HBox(orderListView, new Separator(Orientation.VERTICAL), historicalDataCanvas));
        marketDataTab.setContent(marketDataCanvas);
        historicalDataTab.setContent(historicalDataCanvas);

        TabPane tradingTabPane = new TabPane();
        tradingTabPane.setTranslateX(20);
        tradingTabPane.setBorder(Border.stroke(Color.ORANGE));
        tradePairsCombo.setPromptText("Select Trade Pair");

        Button buyBtn = new Button("Buy");
        buyBtn.setPrefSize(100, 30);
        buyBtn.setTranslateX(10);
        buyBtn.setTranslateY(40);
        Button sellBtn = new Button("Sell");
        sellBtn.setPrefSize(100, 30);
        sellBtn.setTranslateX(150);
        sellBtn.setTranslateY(40);

        ComboBox<String> market_typeCombox = new ComboBox<>();
        market_typeCombox.setPromptText("Select Market Type");
        market_typeCombox.getItems().addAll(
                "Market",
                "Limit",
                "Stop Limit"
        );

        tradeToolBar.getItems().addAll(market_typeCombox);
        tradingTabPane.setPrefSize(1540, 780);
        getChildren().add(tradingTabPane);
        tradingTabPane.setSide(Side.RIGHT);
        tradingTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);

        tradingTabPane.setSide(Side.LEFT);
        tradeToolBar.setPrefSize(1540, 20);
        tradingTabPane.getStyleClass().add("tool-bar");
        chartradeTabPane.setTranslateX(0);
        chartradeTabPane.setTranslateY(30);
        chartradeTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
        tradeTab.setContent(new VBox(tradeToolBar, new Separator(Orientation.HORIZONTAL), chartradeTabPane));
        tradeTab.getStyleClass().add("trade-tab");
        accountTab.getStyleClass().add("account-tab");
        positionTab.getStyleClass().add("position-tab");
        orderTab.getStyleClass().add("order-tab");
        marketDataTab.getStyleClass().add("market-data-tab");
        historicalDataTab.getStyleClass().add("historical-data-tab");
        newsTab.getStyleClass().add("news-tab");

        tradingTabPane.getTabs().addAll(tradeTab, accountTab, positionTab, orderTab, marketDataTab, historicalDataTab, newsTab);
        tradePairsCombo.setPromptText("Select Trade Pair");
        addChartBtn.setOnAction(_ -> {
            if (tradePairsCombo.getItems() == null) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Trade Pair Selection Error");
                alert.setContentText("Please select a trade pair");
                alert.showAndWait();
                return;
            }

            Tab tab0 = new Tab(tradePairsCombo.getSelectionModel().getSelectedItem().toString('-'));
            tab0.getStyleClass().add("chart-tab");
            CandleStickChartDisplay candlestickChartDisplay = new CandleStickChartDisplay(tradePairsCombo.getSelectionModel().getSelectedItem(), exchange);
            candlestickChartDisplay.setPrefSize(1540, 700);

            tab0.setContent(candlestickChartDisplay);
            candlestickChartDisplay.getStyleClass().add("candlestick-chart");
            chartradeTabPane.getStyleClass().add("tab-pane");
            chartradeTabPane.getTabs().add(tab0);
            chartradeTabPane.getSelectionModel().select(tab0);
        });

        getStyleClass().add("border-pane");

    }
}

