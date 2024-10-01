package org.investpro;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;

public class DisplayExchange extends Region {

    private static final Logger logger = LoggerFactory.getLogger(DisplayExchange.class);
    private final Exchange exchange;
    private final ObservableList<Order> ordersData;
    private final ObservableList<Position> positionsData;
    private final TreeTableView<News> newsTreeTableView;
    private final Canvas upcomingNewsBox;

    // Executor for periodic updates
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    public DisplayExchange(@NotNull Exchange exchange) throws Exception {
        this.exchange = exchange;

        ordersData = FXCollections.observableArrayList();
        positionsData = FXCollections.observableArrayList();

        // Create the ComboBox for Trade Pairs
        ComboBox<TradePair> tradePairsCombo = new ComboBox<>();
        tradePairsCombo.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(TradePair tradePair, boolean empty) {
                super.updateItem(tradePair, empty);
                setText(empty ? "Select Trade Pair" : tradePair.toString('/'));
            }
        });

        // Populate trade pairs asynchronously
        tradePairsCombo.getItems().addAll(exchange.getTradePairs().get());
        tradePairsCombo.setPromptText("Select TradePair");

        // Create toolbar and buttons
        Button autoTradeBtn = new Button("AUTO TRADE");
        Button addChartBtn = new Button("ADD CHART");
        ToolBar tradeToolBar = new ToolBar(tradePairsCombo, addChartBtn, autoTradeBtn);

        // Tab pane for the main sections
        TabPane chartradeTabPane = new TabPane();
        chartradeTabPane.setPrefSize(1540, 780);

        // News section
        Tab newsTab = new Tab("FOREX NEWS");
        newsTreeTableView = createNewsTreeTableView();
        upcomingNewsBox = createUpcomingNewsBox();
        newsTab.setContent(new VBox(upcomingNewsBox, new Separator(Orientation.HORIZONTAL), newsTreeTableView));

        // Other Tabs (Trade, Account, Orders, Positions)
        Tab tradeTab = new Tab("->LIVE TRADING<-");
        Tab accountTab = createAccountTab();

        Tab positionTab = createPositionTab();
        Tab orderTab = createOrderTab();

        // Adding all tabs to the main TabPane
        TabPane tradingTabPane = new TabPane(tradeTab, accountTab, orderTab, positionTab, newsTab);
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
                addChart(tradePairsCombo, chartradeTabPane);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        // Start periodic data updates
        startDataUpdates();
    }

    private Tab createAccountTab() throws IOException, InterruptedException, ExecutionException, SQLException, ClassNotFoundException, NoSuchAlgorithmException, InvalidKeyException {
        Tab acc = new Tab("Account Details");

        // Add account details here

        CompletableFuture<List<Account>> account_details = exchange.getAccounts();


        acc.setContent(        new VBox(
                new Label("Account ID: %s".formatted(account_details.get().getFirst().getAccountId())),
                new Label("Balance: %s %s".formatted(account_details.get().getFirst().getBalance(), account_details.get().getFirst().getBalance())),
                new Label("Created: %s".formatted(account_details.get().getFirst().getCreated())),
                new Label("Guaranteed Stop Loss:  %s".formatted(account_details.get().getFirst().getGuaranteedStopLoss())),
                new Label("Margin Call: %s".formatted(account_details.get().getFirst().getMarginCall())),



                new Label("Margin Used: %s".formatted(account_details.get().getFirst().getMarginUsed())),

                new Label("Open Orders: %d".formatted(exchange.getOrders().size())),
                new Label("Profit: %s".formatted(account_details.get().getFirst().getProfit()))



        ));





        return acc;
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

    private Canvas createUpcomingNewsBox() {
        Canvas newsBox = new Canvas(1500, 300);
        newsBox.getGraphicsContext2D().setFill(Color.BLACK);
        newsBox.getGraphicsContext2D().fillRect(0, 0, 1500, 300);
        newsBox.getGraphicsContext2D().setStroke(Color.WHITE);
        newsBox.getGraphicsContext2D().strokeText("Upcoming News", 20, 20);
        return newsBox;
    }

    private Tab createOrderTab() {
        Tab orderTab = new Tab("ORDERS");
        ListView<Order> orderView = new ListView<>(ordersData);
        orderTab.setContent(new VBox(new Label("Orders:"), new Separator(Orientation.HORIZONTAL), orderView));
        return orderTab;
    }

    private @NotNull Tab createPositionTab() {
        Tab positionTab = new Tab("POSITIONS");
        ListView<Position> positionView = new ListView<>(positionsData);
        positionTab.setContent(new VBox(new Label("Positions:"), new Separator(Orientation.HORIZONTAL), positionView));
        return positionTab;
    }

    private void addChart(@NotNull ComboBox<TradePair> tradePairsCombo, TabPane chartradeTabPane) throws SQLException {
        if (tradePairsCombo.getSelectionModel().isEmpty()) {
            new Alert(Alert.AlertType.ERROR, "Please select a Trade Pair").showAndWait();
            return;
        }

        TradePair selectedPair = tradePairsCombo.getSelectionModel().getSelectedItem();
        exchange.tradePair = selectedPair;

        Tab chartTab = new Tab(selectedPair.toString('-'));
        chartTab.setGraphic(
                new ImageView(


                        exchange.getChartImage(selectedPair)


                )
        );
        CandleStickChartDisplay chartDisplay = new CandleStickChartDisplay(exchange);
        chartDisplay.setPrefSize(1440, 700);
        chartTab.setContent(chartDisplay);
        chartradeTabPane.getTabs().add(chartTab);
        chartradeTabPane.getSelectionModel().select(chartTab);
    }

    private void startDataUpdates() {
        executorService.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            updateOrders();
            updatePositions();
            updateNews();
        }), 0, 10, TimeUnit.SECONDS); // Update every 10 seconds
    }

    private void updateOrders() {
        CompletableFuture.runAsync(() -> {
            try {
                List<Order> updatedOrders = exchange.getOrders();
                Platform.runLater(() -> ordersData.setAll(updatedOrders));
            } catch (Exception e) {
                logger.error(
                        "Error updating orders: %s".formatted(e.getMessage()),
                        e
                );
            }
        });
    }

    private void updatePositions() {
        CompletableFuture.runAsync(() -> {
            try {
                ArrayList<Position> updatedPositions =new ArrayList<>();
                updatedPositions.add(exchange.getPositions());
                Platform.runLater(() -> positionsData.setAll(updatedPositions));
            } catch (Exception e) {
                logger.error(e.getMessage(), e);

            }
        });
    }

    private void updateNews() {
        CompletableFuture.runAsync(() -> {
            try {
                List<News> updatedNews = new NewsDataProvider().getNews();
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
        upcomingNewsBox.getGraphicsContext2D().clearRect(0, 0, 1500, 300);

    }}