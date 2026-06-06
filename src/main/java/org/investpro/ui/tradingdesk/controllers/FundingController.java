package org.investpro.ui.tradingdesk.controllers;

import javafx.scene.Node;
import org.investpro.ui.tradingdesk.TradingDeskContext;
import org.investpro.ui.tradingdesk.TradingDeskState;
import org.investpro.ui.tradingdesk.panels.DepositsWithdrawalsPanel;
import org.investpro.ui.tradingdesk.panels.TransferFundsPanel;

import java.util.Objects;
import java.util.function.Consumer;

public final class FundingController {

    private final TradingDeskContext context;
    private final TradingDeskState state;
    private final Consumer<String> journal;

    public FundingController(
            TradingDeskContext context,
            TradingDeskState state,
            Consumer<String> journal) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.journal = journal == null ? ignored -> {
        } : journal;
    }

    public Node createDepositsWithdrawalsPanel() {
        return new DepositsWithdrawalsPanel(context, state, journal);
    }

    public Node createTransferFundsPanel() {
        return new TransferFundsPanel(context, state, journal);
    }
}
