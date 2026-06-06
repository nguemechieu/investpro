package org.investpro.exchange.ibkr;

import java.util.ArrayList;
import java.util.List;

public final class IbkrConnectionDiagnosticsService {

    private final IbkrLocalServiceDetector detector;

    public IbkrConnectionDiagnosticsService(IbkrLocalServiceDetector detector) {
        this.detector = detector == null ? new IbkrLocalServiceDetector() : detector;
    }

    public List<DiagnosticItem> diagnose(IbkrConnectionProfile profile, IbkrSessionState sessionState) {
        IbkrConnectionProfile safeProfile = profile == null ? IbkrConnectionProfile.twsPaper() : profile;
        IbkrSessionState state = sessionState == null
                ? IbkrSessionState.disconnected(safeProfile, "No IBKR session has been started yet.")
                : sessionState;

        List<DiagnosticItem> items = new ArrayList<>();
        boolean reachable = detector.isReachable(safeProfile.host(), safeProfile.port());
        items.add(new DiagnosticItem("TWS/Gateway detected", reachable,
                reachable ? "Local IBKR listener detected." : "TWS or IB Gateway is not running."));
        items.add(new DiagnosticItem("Socket port reachable", reachable,
                reachable ? "Socket port is reachable." : "Socket port is unreachable."));
        items.add(new DiagnosticItem("API enabled", state.socketConnected(),
                state.socketConnected()
                        ? "Socket accepted a connection."
                        : "API socket access may not be enabled in TWS/Gateway."));
        items.add(new DiagnosticItem("API ready / nextValidId received", state.apiReady(),
                state.apiReady()
                        ? "API session is ready."
                        : "Connected to socket, but IBKR API session is not ready yet."));
        items.add(new DiagnosticItem("Managed accounts received", state.managedAccountsReceived(),
                state.managedAccountsReceived()
                        ? "Managed account list is available."
                        : "No managed account received."));
        items.add(new DiagnosticItem("Account summary available", state.accountSummaryAvailable(),
                state.accountSummaryAvailable()
                        ? "Account summary is available."
                        : "Account summary has not been received yet."));
        items.add(new DiagnosticItem("Market data permission status", state.marketDataPermissionAvailable(),
                state.marketDataPermissionAvailable()
                        ? "Top-of-book market data is available."
                        : "Market data permission is missing for this contract."));
        items.add(new DiagnosticItem("Depth/orderbook permission status", state.marketDepthPermissionAvailable(),
                state.marketDepthPermissionAvailable()
                        ? "Level II/orderbook data is available."
                        : "Level II/orderbook data may require an additional IBKR subscription."));
        return List.copyOf(items);
    }

    public record DiagnosticItem(String name, boolean passed, String message) {
    }
}
