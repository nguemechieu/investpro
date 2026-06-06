package org.investpro.ui.tradingdesk.panels;

import javafx.scene.layout.BorderPane;
import org.investpro.transfer.TransferPanel;
import org.investpro.ui.tradingdesk.TradingDeskContext;
import org.investpro.ui.tradingdesk.TradingDeskState;

import java.util.Objects;
import java.util.function.Consumer;

public final class TransferFundsPanel extends BorderPane {

    private final TradingDeskContext context;
    private final TradingDeskState state;
    private final Consumer<String> journal;

    public TransferFundsPanel(
            TradingDeskContext context,
            TradingDeskState state,
            Consumer<String> journal) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.journal = journal == null ? ignored -> {
        } : journal;

        build();
    }

    private void build() {
        TransferPanel transferPanel = new TransferPanel(
                context.fundingService().connectedFundingExchanges(),
                journal);
        setCenter(transferPanel);
        getStyleClass().addAll("transfer-funds-window", "terminal-window-content");
        state.setStatusMessage("Transfer Funds panel ready.");
    }
}
