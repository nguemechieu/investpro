package org.investpro.ui.panels;

import org.investpro.exchange.ibkr.IbkrExchange;
import org.jspecify.annotations.NonNull;

public class IbkrConnectionPanel extends IbkrSetupWizard {

    public IbkrConnectionPanel(IbkrExchange exchange) {
        this(exchange, null);
    }

    public IbkrConnectionPanel(@NonNull IbkrExchange exchange, Runnable sessionStateChanged) {
        super(exchange.getConnectionService(),
                exchange.getLocalServiceDetector(),
                exchange.getConnectionDiagnosticsService(),
                sessionStateChanged);
    }
}
