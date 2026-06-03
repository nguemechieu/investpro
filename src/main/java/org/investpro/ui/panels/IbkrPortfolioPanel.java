package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.investpro.exchange.ibkr.IbkrExchange;

public class IbkrPortfolioPanel extends VBox {

    private final Label valueLabel = new Label("Portfolio Value: -");
    private final Label positionsLabel = new Label("Positions: -");

    public IbkrPortfolioPanel(IbkrExchange exchange) {
        super(8);
        setPadding(new Insets(14));

        Button refresh = new Button("Refresh");
        Button sync = new Button("Sync");

        refresh.setOnAction(event -> refresh(exchange));
        sync.setOnAction(event -> {
            exchange.synchronizePortfolio();
            refresh(exchange);
        });

        getChildren().addAll(
                new Label("IBKR Portfolio Panel"),
                valueLabel,
                positionsLabel,
                refresh,
                sync);

        refresh(exchange);
    }

    private void refresh(IbkrExchange exchange) {
        valueLabel.setText(String.format("Portfolio Value: %.2f", exchange.getPortfolioService().portfolioValue()));
        positionsLabel.setText("Positions: " + exchange.getPortfolioService().positions().size());
    }
}
