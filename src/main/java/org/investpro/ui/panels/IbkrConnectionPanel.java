package org.investpro.ui.panels;

import org.investpro.exchange.ibkr.IbkrExchange;

public class IbkrConnectionPanel extends IbkrSetupWizard {

    public IbkrConnectionPanel(IbkrExchange exchange) {
        this(exchange, null);
    }

    public IbkrConnectionPanel(IbkrExchange exchange, Runnable sessionStateChanged) {
        super(exchange.getConnectionService(),
                exchange.getLocalServiceDetector(),
                exchange.getConnectionDiagnosticsService(),
                sessionStateChanged);
    }
}
