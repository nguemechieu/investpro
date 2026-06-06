package org.investpro.trading.tradability.session;

import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.TradabilityStatus;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

public final class DefaultMarketHoursSessionPolicy implements ProviderSessionPolicy {
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Override
    public boolean supports(Exchange exchange) {
        return true;
    }

    @Override
    public boolean supports(ProviderSessionContext provider) {
        return true;
    }

    @Override
    public SessionState sessionState(
            Exchange exchange,
            TradePair instrument,
            TradabilityStatus productStatus,
            Map<String, Object> productMetadata,
            Instant now
    ) {
        ProviderSessionContext provider = new ProviderSessionContext(
                exchange == null ? "" : exchange.getExchangeId(),
                exchange == null ? "" : exchange.getName(),
                exchange != null && Boolean.TRUE.equals(exchange.isConnected()),
                exchange != null && exchange.canSubmitOrders());
        return sessionState(provider, instrument, productStatus, productMetadata, now);
    }

    @Override
    public SessionState sessionState(
            ProviderSessionContext provider,
            TradePair instrument,
            TradabilityStatus productStatus,
            Map<String, Object> productMetadata,
            Instant now
    ) {
        if (provider == null || !provider.connected()) {
            return SessionState.closed(false, "Order blocked because provider is disconnected.");
        }
        if (!provider.canSubmitOrders()) {
            return SessionState.closed(true, "Order blocked because account lacks live trading permission.");
        }
        if (isCryptoLike(instrument)) {
            return SessionState.open24x7("Crypto product is tradable and order submission is allowed.");
        }
        if (productStatus != TradabilityStatus.FULLY_TRADABLE && productStatus != TradabilityStatus.LIMIT_ONLY) {
            return SessionState.closed(true, "Order blocked because product status is " + productStatus + ".");
        }

        ZonedDateTime ny = ZonedDateTime.ofInstant(now == null ? Instant.now() : now, NEW_YORK);
        boolean weekday = ny.getDayOfWeek() != DayOfWeek.SATURDAY && ny.getDayOfWeek() != DayOfWeek.SUNDAY;
        boolean regularHours = !ny.toLocalTime().isBefore(java.time.LocalTime.of(9, 30))
                && ny.toLocalTime().isBefore(java.time.LocalTime.of(16, 0));
        if (!weekday || !regularHours) {
            return SessionState.closed(true, "Order blocked because exchange session is closed.");
        }
        return new SessionState(true, true, true, false, true, true,
                "Exchange session is open and order submission is allowed.");
    }

    private boolean isCryptoLike(TradePair pair) {
        if (pair == null) {
            return false;
        }
        String base = pair.getBaseCode();
        String quote = pair.getCounterCode();
        return isCryptoCode(base) || isCryptoCode(quote);
    }

    private boolean isCryptoCode(String code) {
        return switch (code == null ? "" : code.toUpperCase(java.util.Locale.ROOT)) {
            case "BTC", "ETH", "SOL", "XLM", "XRP", "USDC", "USDT", "DAI", "LTC", "BCH", "DOGE", "ADA", "AVAX",
                    "1INCH" -> true;
            default -> false;
        };
    }
}
