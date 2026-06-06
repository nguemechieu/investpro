package org.investpro.trading.tradability.session;

public record SessionState(
        boolean marketDataAvailable,
        boolean orderSubmissionOpen,
        boolean cancelAllowed,
        boolean reduceOnly,
        boolean openNewPositionsAllowed,
        boolean requiresActiveSession,
        String reason
) {
    public SessionState {
        reason = reason == null ? "" : reason.trim();
    }

    public static SessionState open24x7(String reason) {
        return new SessionState(true, true, true, false, true, false, reason);
    }

    public static SessionState closed(boolean cancelAllowed, String reason) {
        return new SessionState(false, false, cancelAllowed, false, false, true, reason);
    }
}
