package org.investpro;

import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class DisplayExchange extends Region {
    public DisplayExchange(@NotNull Exchange exchange) {

        ComboBox<TradePair> tradePairsCombox = new ComboBox<>();
        tradePairsCombox.setTranslateY(50);
        Button autoTradeBtn = new Button("Auto");
        Button addChartBtn = new Button("Add Chart");
        ToolBar tradeToolBar = new ToolBar(tradePairsCombox, new Separator(Orientation.VERTICAL), autoTradeBtn, new Separator(Orientation.VERTICAL), addChartBtn, new Separator(Orientation.VERTICAL));
        tradeToolBar.setTranslateX(0);
        tradeToolBar.setTranslateY(50);
        ToolBar bottomBar = new ToolBar();
        TabPane tradingTabPane = new TabPane();
        Tab tradeTab = new Tab("Trade");
        Tab accountTab = new Tab("Account");
        Tab positionTab = new Tab("Position");
        Tab orderTab = new Tab("Order");
        Tab marketDataTab = new Tab("Market Data");
        Tab historicalDataTab = new Tab("Historical Data");
        Tab newsTab = new Tab("News");
        Tab candleDataTab = new Tab("Candle Data");
        TabPane chartradeTabPane = new TabPane();
        chartradeTabPane.setSide(Side.BOTTOM);
        chartradeTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        chartradeTabPane.setPrefSize(1540, 780);
        tradeTab.setContent(new VBox(chartradeTabPane));
        accountTab.setContent(new VBox());
        tradingTabPane.setPrefSize(1540, 740);
        tradingTabPane.getTabs().addAll(tradeTab, accountTab, positionTab, orderTab, marketDataTab, historicalDataTab, newsTab, candleDataTab);
        getChildren().add(tradingTabPane);
        getChildren().add(tradeToolBar);

        tradePairsCombox.getItems().addAll(exchange.getTradePairSymbol());
        tradePairsCombox.getSelectionModel().selectedItemProperty().addListener(_ -> {
            bottomBar.setTranslateY(750);
            bottomBar.getItems().addAll(
                    new ToolBar(new Label("Account ID                    Mode :                Status :               |||||")));
        });
        bottomBar.setPrefSize(1540, 20);
        bottomBar.setTranslateY(780);
        getChildren().addAll(bottomBar);
        addChartBtn.setOnAction(_ -> {
            Tab tab0 = new Tab(tradePairsCombox.getSelectionModel().getSelectedItem().toString('-'));
            tab0.setContent(new CandleStickChartDisplay(tradePairsCombox.getSelectionModel().getSelectedItem(), exchange));
            chartradeTabPane.getTabs().add(tab0);
            chartradeTabPane.getSelectionModel().select(tab0);
        });
        getStylesheets().add(Objects.requireNonNull(getClass().getResource("/app.css")).toExternalForm());
    }
}

