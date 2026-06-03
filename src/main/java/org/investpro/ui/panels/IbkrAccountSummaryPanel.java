package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.investpro.exchange.ibkr.IbkrAccountSnapshot;
import org.investpro.exchange.ibkr.IbkrExchange;

public class IbkrAccountSummaryPanel extends VBox {

    private final Label accountLabel = new Label("Account: -");
    private final Label equityLabel = new Label("Equity: -");
    private final Label availableLabel = new Label("Available: -");
    private final Label buyingPowerLabel = new Label("Buying Power: -");

    public IbkrAccountSummaryPanel(IbkrExchange exchange) {
        super(8);
        setPadding(new Insets(14));

        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh(exchange));

        getChildren().addAll(
                new Label("IBKR Account Summary Panel"),
                accountLabel,
                equityLabel,
                availableLabel,
                buyingPowerLabel,
                refresh);

        refresh(exchange);
    }

    private void refresh(IbkrExchange exchange) {
        IbkrAccountSnapshot snapshot = exchange.getAccountService().refreshFromBrokerIfAvailable();
        accountLabel.setText("Account: " + snapshot.accountId() + " (" + (snapshot.paper() ? "PAPER" : "LIVE") + ")");
        equityLabel.setText(String.format("Equity: %.2f", snapshot.equity()));
        availableLabel.setText(String.format("Available: %.2f", snapshot.availableFunds()));
        buyingPowerLabel.setText(String.format("Buying Power: %.2f", snapshot.buyingPower()));
    }
}
