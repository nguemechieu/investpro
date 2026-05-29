package org.investpro.trading.tradability;

import org.investpro.models.trading.TradePair;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record InstrumentTradabilityReport(
        String exchangeId,
        String originalSymbol,
        String normalizedSymbol,
        String displayName,
        InstrumentTradeStatus status,
        boolean tradeable,
        String reason,
        String sourceEndpoint,
        Instant lastCheckedAt,
        Map<String, Object> metadata
) {
    public InstrumentTradabilityReport {
        exchangeId = exchangeId == null ? "" : exchangeId;
        originalSymbol = originalSymbol == null ? "" : originalSymbol;
        normalizedSymbol = normalizedSymbol == null ? "" : normalizedSymbol;
        displayName = displayName == null ? "" : displayName;
        status = status == null ? InstrumentTradeStatus.UNKNOWN : status;
        reason = reason == null ? "" : reason;
        sourceEndpoint = sourceEndpoint == null ? "" : sourceEndpoint;
        lastCheckedAt = lastCheckedAt == null ? Instant.now() : lastCheckedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static InstrumentTradabilityReport from(
            SymbolTradability st,
            String exchangeId,
            String originalSymbol,
            String normalizedSymbol,
            String displayName,
            String sourceEndpoint) {

        InstrumentTradeStatus its = InstrumentTradeStatus.from(st.status());
        Map<String, Object> meta = new HashMap<>(st.rawMetadata());
        return new InstrumentTradabilityReport(
                exchangeId,
                originalSymbol,
                normalizedSymbol,
                displayName,
                its,
                its.isTradeable(),
                st.reason(),
                sourceEndpoint,
                st.checkedAt(),
                meta);
    }

    /** Returns a user-friendly message suitable for UI display. */
    public String uiMessage() {
        return switch (status) {
            case TRADEABLE -> "Ready to trade.";
            case SUPPORTED_BUT_MARKET_CLOSED -> "Market is currently closed for " + displayName + ".";
            case SUPPORTED_BUT_TEMPORARILY_DISABLED ->
                    "Trading temporarily suspended for " + displayName + (reason.isBlank() ? "." : ": " + reason);
            case UNSUPPORTED_BY_ACCOUNT ->
                    "Your account does not have permission to trade " + displayName + ".";
            case UNSUPPORTED_BY_EXCHANGE ->
                    displayName + " is not available on " + exchangeId + ".";
            case INVALID_SYMBOL -> "Symbol '" + originalSymbol + "' is not recognized.";
            case PERMISSION_DENIED -> "Permission denied" + (reason.isBlank() ? "." : ": " + reason);
            case RATE_LIMITED -> "Rate limit reached. Please try again shortly.";
            case API_ERROR -> "Exchange API error" + (reason.isBlank() ? "." : ": " + reason);
            case UNKNOWN -> "Tradability status unknown" + (reason.isBlank() ? "." : ": " + reason);
        };
    }

    /** Converts back to a {@link SymbolTradability} record for interoperability. */
    public SymbolTradability toSymbolTradability() {
        TradabilityStatus ts = status.toTradabilityStatus();
        return new SymbolTradability(
                exchangeId,
                null,
                normalizedSymbol,
                ts,
                tradeable,
                tradeable,
                true,
                true,
                tradeable,
                tradeable,
                tradeable,
                tradeable,
                tradeable,
                tradeable,
                false,
                false,
                false,
                reason,
                lastCheckedAt,
                metadata);
    }
}
