package org.investpro.exchange.ibkr;

import java.time.Instant;
import java.util.List;

public record IbkrSessionState(
        IbkrConnectionMode mode,
        String host,
        int port,
        int clientId,
        boolean paper,
        boolean socketConnected,
        boolean apiReady,
        boolean managedAccountsReceived,
        boolean accountSummaryAvailable,
        boolean marketDataPermissionAvailable,
        boolean marketDepthPermissionAvailable,
        boolean tradingEnabled,
        String message,
        Instant connectedAt,
        List<String> managedAccounts) {

    public static IbkrSessionState disconnected(IbkrConnectionProfile profile, String message) {
        IbkrConnectionProfile safe = profile == null ? IbkrConnectionProfile.twsPaper() : profile;
        return new IbkrSessionState(
                safe.mode(),
                safe.host(),
                safe.port(),
                safe.clientId(),
                safe.paper(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                message,
                null,
                List.of());
    }

    public boolean connectionSuccessful() {
        return socketConnected && apiReady && managedAccountsReceived;
    }
}
