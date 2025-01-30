package org.investpro;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;


public class PositionView extends AnchorPane {

    public PositionView(@NotNull Exchange exchange) throws IOException, ExecutionException, InterruptedException {
        List<Position> positionList = exchange.getPositions();

        // Separate long and short positions using streams
        List<Position.SubPosition> longPositions = new ArrayList<>();
        List<Position.SubPosition> shortPositions = new ArrayList<>();


        for (Position position : positionList) {

            longPositions.add(position.getLongPosition());
            shortPositions.add(position.getShortPosition());
        }

        // Create ListViews
        ListView<Position.SubPosition> longPositionView = new ListView<>();
        longPositionView.getItems().addAll(longPositions);
        longPositionView.setPrefHeight(500);
        longPositionView.setPrefWidth(700);

        ListView<Position.SubPosition> shortPositionView = new ListView<>();
        shortPositionView.getItems().addAll(shortPositions);
        shortPositionView.setPrefHeight(500);
        shortPositionView.setPrefWidth(700);

        // Statistics and performance
        Label statsLabel = new Label(getStatistics(positionList, longPositions, shortPositions));
        Label pnlLabel = new Label(getPerformanceMetrics(positionList));

        // Layout using VBox & HBox for better structure
        VBox longBox = new VBox(10, new Label("Long Positions:"), longPositionView);
        VBox shortBox = new VBox(10, new Label("Short Positions:"), shortPositionView);
        HBox positionsBox = new HBox(20, longBox, shortBox);
        positionsBox.setPadding(new Insets(10));

        VBox mainLayout = new VBox(15, positionsBox, statsLabel, pnlLabel);
        mainLayout.setPadding(new Insets(10));

        // Anchoring
        AnchorPane.setTopAnchor(mainLayout, 10.0);
        AnchorPane.setLeftAnchor(mainLayout, 10.0);
        AnchorPane.setRightAnchor(mainLayout, 10.0);
        AnchorPane.setBottomAnchor(mainLayout, 10.0);

        getChildren().add(mainLayout);
        setPrefHeight(780);
        setPrefWidth(1500);
    }

    private @NotNull String getStatistics(@NotNull List<Position> allPositions, @NotNull List<Position.SubPosition> longPositions, @NotNull List<Position.SubPosition> shortPositions) {
        int totalPositions = allPositions.size();
        int longCount = longPositions.size();
        int shortCount = shortPositions.size();

        double totalValue = allPositions.stream().mapToDouble(Position::getValue).sum();
        double avgValue = totalPositions > 0 ? totalValue / totalPositions : 0;

        return String.format("Total Positions: %d | Long: %d | Short: %d | Total Value: %.2f | Avg Value: %.2f",
                totalPositions, longCount, shortCount, totalValue, avgValue);
    }

    private @NotNull String getPerformanceMetrics(@NotNull List<Position> positions) {
        double totalPnL = positions.stream().mapToDouble(Position::getProfitOrLoss).sum();
        long profitableTrades = positions.stream().filter(p -> p.getProfitOrLoss() > 0).count();
        long losingTrades = positions.stream().filter(p -> p.getProfitOrLoss() < 0).count();
        double winRate = positions.isEmpty() ? 0 : (double) profitableTrades / positions.size() * 100;

        return String.format("Total PnL: %.2f | Win Rate: %.2f%% | Profitable Trades: %d | Losing Trades: %d",
                totalPnL, winRate, profitableTrades, losingTrades);
    }
}
