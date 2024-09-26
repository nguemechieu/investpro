package org.investpro;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
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
                    setText(tradePair.toString('-'));
                }
            }
        });

        tradePairsCombo.getItems().addAll(exchange.getTradePairs().get());

        Button autoTradeBtn = new Button("AUTO TRADE");
        Button addChartBtn = new Button("ADD CHART");
        ToolBar tradeToolBar = new ToolBar(tradePairsCombo, addChartBtn, autoTradeBtn);

        TabPane chartradeTabPane = new TabPane();
        chartradeTabPane.setPrefSize(1540, 780);

        Tab newsTab = new Tab("FOREX NEWS");

        // Initialize News TreeTableView for current news
        TreeTableView<News> newsTreeTableView = new TreeTableView<>();
        TreeItem<News> root = new TreeItem<>(new News("","","",new Date(),"","")); // Placeholder root

        List<News> newsList = new NewsDataProvider().getNews();
        for (News newsItem : newsList) {
            TreeItem<News> item = new TreeItem<>(newsItem);
            root.getChildren().add(item);
        }

        root.setExpanded(true);
        newsTreeTableView.setRoot(root);

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

        TreeTableColumn<News, String> forecastCol = new TreeTableColumn<>("Forecast");
        forecastCol.setPrefWidth(150);
        forecastCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getForecast()));

        TreeTableColumn<News, String> previousCol = new TreeTableColumn<>("Previous");
        previousCol.setPrefWidth(150);
        previousCol.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getPrevious()));

        newsTreeTableView.getColumns().addAll(dateCol, titleCol, countryCol, impactCol, forecastCol, previousCol);

        // Upcoming news displayed on the canvas
        Canvas upcomingNewsBox = new Canvas(1500, 300);
        upcomingNewsBox.getGraphicsContext2D().setFill(Color.BLACK);
        upcomingNewsBox.getGraphicsContext2D().fillRect(0, 0, 1500, 300);
        upcomingNewsBox.getGraphicsContext2D().setStroke(Color.WHITE);
        upcomingNewsBox.getGraphicsContext2D().strokeText("Upcoming News", 20, 20);

        // Populate upcoming news details on the canvas
        News upcomingNews = newsList.stream().filter(
                news -> news.getDate().after(new Date()) // Filter upcoming news
        ).findFirst().orElse(null); // Fetch first item for simplicity
        if (upcomingNews != null) {
            upcomingNewsBox.getGraphicsContext2D().strokeText("Date: %s".formatted(upcomingNews.getDate().toString()), 20, 50);
            upcomingNewsBox.getGraphicsContext2D().strokeText("Title: %s".formatted(upcomingNews.getTitle()), 20, 80);
            upcomingNewsBox.getGraphicsContext2D().strokeText("Country: %s".formatted(upcomingNews.getCountry()), 20, 110);
            upcomingNewsBox.getGraphicsContext2D().strokeText("Impact: %s".formatted(upcomingNews.getImpact()), 20, 140);
            upcomingNewsBox.getGraphicsContext2D().strokeText("Forecast: %s".formatted(upcomingNews.getForecast()), 20, 170);
            upcomingNewsBox.getGraphicsContext2D().strokeText("Previous: %s".formatted(upcomingNews.getPrevious()), 20, 200);

        } else {
            upcomingNewsBox.getGraphicsContext2D().strokeText("No upcoming news available", 20, 50);
        }

        // Add both current news table and upcoming news canvas to the VBox
        newsTab.setContent(new VBox(upcomingNewsBox, new Separator(Orientation.HORIZONTAL), newsTreeTableView));

        // Add tabs for other functionalities like trade, account, market data, etc.
        Tab tradeTab = new Tab("TRADE");
        Tab accountTab = new Tab("ACCOUNT");
        Tab positionTab = new Tab("POSITIONS");
        Tab orderTab = new Tab("ORDERS");
        Tab marketDataTab = new Tab("MARKET DATA");
        Tab historicalDataTab = new Tab("HISTORICAL DATA");
        Tab browserTab = new Tab("Browser");
        Browser browser = new Browser();
        browserTab.setContent(browser);
        // Populate TabPane
        TabPane tradingTabPane = new TabPane(tradeTab, accountTab, positionTab, orderTab, marketDataTab, historicalDataTab, newsTab,browserTab);
        tradingTabPane.setPrefSize(1540, 780);
        tradingTabPane.setSide(Side.LEFT);
        tradingTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);

        // Add trade toolbar
        tradeToolBar.setPrefSize(1540, 20);

        tradeTab.setContent(new VBox(tradeToolBar, new Separator(Orientation.HORIZONTAL), chartradeTabPane));

        // Add everything to the main container
        getChildren().add(tradingTabPane);

        addChartBtn.setOnAction(_ -> {
            if (tradePairsCombo.getItems() == null) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Trade Pair Selection Error");
                alert.setContentText("Please select a trade pair");
                alert.showAndWait();
                return;
            }

            Tab tab = new Tab(tradePairsCombo.getSelectionModel().getSelectedItem().toString('-'));
            tab.getStyleClass().add("chart-tab");

            exchange.tradePair=tradePairsCombo.getSelectionModel().getSelectedItem();
            CandleStickChartDisplay candlestickChartDisplay = new CandleStickChartDisplay( exchange);
            candlestickChartDisplay.setPrefSize(1440, 700);
            tab = new Tab();
            tab.setText(tradePairsCombo.getSelectionModel().getSelectedItem().toString('-'));
            tab.setContent(candlestickChartDisplay);
            candlestickChartDisplay.getStyleClass().add("candlestick-chart");
            chartradeTabPane.getStyleClass().add("tab-pane");
            chartradeTabPane.getTabs().add(tab);
            chartradeTabPane.getSelectionModel().select(tab);
        });

    }
}
