package org.investpro.investpro.ui;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

import org.investpro.investpro.Browser;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.Messages;

import org.investpro.investpro.ui.chart.CandleStickChartContainer;
import org.investpro.investpro.model.TradePair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DisplayExchangeUI extends AnchorPane {
    private static final Logger logger = LoggerFactory.getLogger(DisplayExchangeUI.class);
    private final Exchange exchange;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final TabPane chartradeTabPane = new TabPane();
    private final String tokens;

    public DisplayExchangeUI(@NotNull Exchange exchange, String tokens) {
        this.exchange = exchange;
        this.tokens = tokens;

        initializeUI();
    }

    private void initializeUI() {
        try {
            // Create UI Components
            ComboBox<String> tradePairsCombo = createTradePairsComboBox();
            ToolBar tradeToolBar = createTradeToolBar(tradePairsCombo);
            TabPane tradingTabPane = createTradingTabPane(tradeToolBar);
// Make the TabPane fill the entire DisplayExchangeUI
            AnchorPane.setTopAnchor(tradingTabPane, 0.0);
            AnchorPane.setBottomAnchor(tradingTabPane, 0.0);
            AnchorPane.setLeftAnchor(tradingTabPane, 0.0);
            AnchorPane.setRightAnchor(tradingTabPane, 0.0);

            // Fetch trade pairs asynchronously
            loadTradePairs(tradePairsCombo);

            // Bind tab pane to full size of the AnchorPane
            AnchorPane.setTopAnchor(tradingTabPane, 0.0);
            AnchorPane.setBottomAnchor(tradingTabPane, 0.0);
            AnchorPane.setLeftAnchor(tradingTabPane, 0.0);
            AnchorPane.setRightAnchor(tradingTabPane, 0.0);
            // Add all elements to the main UI
            getChildren().add(tradingTabPane);
        } catch (Exception e) {
            logger.error("Error initializing UI", e);
            throw new RuntimeException("Error initializing UI: " + e.getMessage(), e);
        }
    }

    private @NotNull ComboBox<String> createTradePairsComboBox() {
        ComboBox<String> tradePairsCombo = new ComboBox<>();
        tradePairsCombo.setPromptText("Select Pair");
        return tradePairsCombo;
    }

    @Contract("_ -> new")
    private @NotNull ToolBar createTradeToolBar(ComboBox<String> tradePairsCombo) {
        Button autoTradeBtn = new Button("Auto");
        Button addChartBtn = new Button("Add Chart");

        addChartBtn.setOnAction(_ -> handleAddChart(tradePairsCombo));

        return new ToolBar(tradePairsCombo, addChartBtn, autoTradeBtn);
    }

    private @NotNull TabPane createTradingTabPane(ToolBar tradeToolBar) {
        TabPane tradingTabPane = new TabPane(
                createTab("Account Summary", new AccountSummaryUI(exchange)),
                createTab("Trading", new VBox(tradeToolBar, new Separator(Orientation.HORIZONTAL), chartradeTabPane)),
                createTab("Position", new PositionsUI(exchange)),
                createTab("Orders", new VBox(new Label("Orders..."), new OrdersUI(exchange))),
                createTab("Pending..", new VBox(new Label("Pending..."), new PendingOrdersUI(exchange))),
                createTab("Coin Info", new VBox(new Label("Coins..."), new CoinInfoUI(exchange))),
                createTab("News", new NewsUI(exchange)),
                createTab("Web", new Browser(), false)
        );


        tradingTabPane.setSide(Side.TOP);
        tradingTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);

        return tradingTabPane;
    }

    private @NotNull Tab createTab(String title, Node content) {
        return createTab(title, content, true);
    }

    private @NotNull Tab createTab(String title, Node content, boolean closable) {
        Tab tab = new Tab(title);
        tab.setContent(content);
        tab.setClosable(closable);
        return tab;
    }

    private void loadTradePairs(ComboBox<String> tradePairsCombo) {
        executorService.submit(() -> {
            try {
                List<TradePair> data = exchange.getTradePairs();
                Platform.runLater(() -> tradePairsCombo.getItems().addAll(
                        data.stream().map(pair -> pair.toString('/')).toList()
                ));
            } catch (Exception e) {
                logger.error("Failed to load trade pairs", e);
                showError("Failed to load trade pairs: " + e.getMessage());
            }
        });
    }

    private void handleAddChart(@NotNull ComboBox<String> tradePairsCombo) {
        String selectedPair = tradePairsCombo.getSelectionModel().getSelectedItem();
        if (selectedPair == null || selectedPair.isEmpty()) {
            showError("Please select a trade pair before adding a chart.");
            return;
        }
        addChart(selectedPair);
    }

    private void addChart(String tradePairStr) {
        try {
            String[] pair = tradePairStr.split("/");
            if (pair.length != 2) {
                throw new IllegalArgumentException("Invalid trade pair format: " + tradePairStr);
            }

            TradePair tradePair = new TradePair(pair[0], pair[1]);
            CandleStickChartContainer chartDisplay = new CandleStickChartContainer(exchange, tradePair, tokens);
            chartDisplay.setPrefSize(1530, 800);

            Tab chartTab = new Tab(tradePairStr, chartDisplay);
            chartradeTabPane.getTabs().add(chartTab);
            chartradeTabPane.getSelectionModel().select(chartTab);

        } catch (Exception e) {
            logger.error("Error adding chart", e);
            showError("Error adding chart: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Platform.runLater(() -> new Messages(Alert.AlertType.ERROR, message));
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}