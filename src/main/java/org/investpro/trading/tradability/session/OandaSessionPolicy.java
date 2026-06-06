package org.investpro.trading.tradability.session;

import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.TradabilityStatus;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;

public final class OandaSessionPolicy implements ProviderSessionPolicy {
    @Override
    public boolean supports(Exchange exchange) {
        String id = exchange == null ? "" : exchange.getExchangeId();
        String name = exchange == null ? "" : exchange.getName();
        return id.toLowerCase(Locale.ROOT).contains("oanda")
                || name.toLowerCase(Locale.ROOT).contains("oanda");
    }

    @Override
    public boolean supports(ProviderSessionContext provider) {
        return provider != null && provider.providerContains("oanda");
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
        if (productStatus != TradabilityStatus.FULLY_TRADABLE) {
            return SessionState.closed(true, "Order blocked because OANDA product status is " + productStatus + ".");
        }

        ZonedDateTime utc = ZonedDateTime.ofInstant(now == null ? Instant.now() : now, ZoneOffset.UTC);
        boolean weekend = utc.getDayOfWeek() == DayOfWeek.SATURDAY
                || (utc.getDayOfWeek() == DayOfWeek.FRIDAY && utc.getHour() >= 22)
                || (utc.getDayOfWeek() == DayOfWeek.SUNDAY && utc.getHour() < 22);
        boolean pairSessionOpen = instrument == null || instrument.getTradingSessionStatus() == null
                || instrument.getTradingSessionStatus().isTradable();

        if (weekend || !pairSessionOpen) {
            return SessionState.closed(true, "Order blocked because exchange session is closed.");
        }
        return new SessionState(true, true, true, false, true, true,
                "OANDA forex session is open and order submission is allowed.");
    }
}
