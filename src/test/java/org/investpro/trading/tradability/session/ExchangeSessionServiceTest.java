package org.investpro.trading.tradability.session;

import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.TradabilityStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
class ExchangeSessionServiceTest {

    private final ExchangeSessionService service = new ExchangeSessionService();

    @Test
    void coinbaseCryptoFullyTradableConnectedAllowsMarketOrders24x7() throws Exception {
        ProviderSessionContext coinbase = exchange("coinbase", "Coinbase Advanced Trade", true, true);
        SessionState state = service.sessionState(
                coinbase,
                TradePair.of("1INCH", "USD"),
                TradabilityStatus.FULLY_TRADABLE,
                Map.of("product_type", "SPOT"),
                Instant.parse("2026-06-06T12:00:00Z"));

        SymbolTradability tradability = tradability(
                TradePair.of("1INCH", "USD"),
                TradabilityStatus.FULLY_TRADABLE,
                state,
                true,
                true,
                state.reason());

        assertTrue(state.marketDataAvailable());
        assertTrue(state.orderSubmissionOpen());
        assertFalse(state.requiresActiveSession());
        assertTrue(canSubmit(tradability, OpenOrder.OrderType.MARKET));
        assertTrue(tradability.reason().contains("fully tradable"));
        assertFalse(tradability.reason().contains("session is active"));
    }

    @Test
    void coinbaseCryptoDisconnectedBlocksOrderSubmission() throws Exception {
        ProviderSessionContext coinbase = exchange("coinbase", "Coinbase Advanced Trade", false, true);
        SessionState state = service.sessionState(
                coinbase,
                TradePair.of("BTC", "USD"),
                TradabilityStatus.FULLY_TRADABLE,
                Map.of("product_type", "SPOT"),
                Instant.parse("2026-06-06T12:00:00Z"));

        assertFalse(state.orderSubmissionOpen());
        assertTrue(state.reason().contains("disconnected"));
    }

    @Test
    void coinbaseCancelOnlyAllowsCancelButBlocksNewOrders() throws Exception {
        ProviderSessionContext coinbase = exchange("coinbase", "Coinbase Advanced Trade", true, true);
        SessionState state = service.sessionState(
                coinbase,
                TradePair.of("BTC", "USD"),
                TradabilityStatus.CANCEL_ONLY,
                Map.of("cancel_only", true),
                Instant.parse("2026-06-06T12:00:00Z"));

        assertFalse(state.orderSubmissionOpen());
        assertTrue(state.cancelAllowed());
    }

    @Test
    void coinbaseLimitOnlyBlocksRequestedMarketOrder() throws Exception {
        ProviderSessionContext coinbase = exchange("coinbase", "Coinbase Advanced Trade", true, true);
        SessionState state = service.sessionState(
                coinbase,
                TradePair.of("BTC", "USD"),
                TradabilityStatus.LIMIT_ONLY,
                Map.of("limit_only", true),
                Instant.parse("2026-06-06T12:00:00Z"));
        SymbolTradability tradability = tradability(
                TradePair.of("BTC", "USD"),
                TradabilityStatus.LIMIT_ONLY,
                state,
                false,
                true,
                "Coinbase product supports limit-only trading.");

        assertFalse(canSubmit(tradability, OpenOrder.OrderType.MARKET));
        assertTrue(canSubmit(tradability, OpenOrder.OrderType.LIMIT));
    }

    @Test
    void oandaForexWeekendSessionClosedBlocksNewOrders() throws Exception {
        ProviderSessionContext oanda = exchange("oanda", "OANDA", true, true);
        SessionState state = service.sessionState(
                oanda,
                TradePair.of("EUR", "USD"),
                TradabilityStatus.FULLY_TRADABLE,
                Map.of(),
                Instant.parse("2026-06-06T12:00:00Z"));

        assertTrue(state.requiresActiveSession());
        assertFalse(state.orderSubmissionOpen());
        assertTrue(state.reason().contains("session is closed"));
    }

    @Test
    void stellarFullyTradableAllowsOrdersOutsideStockMarketHours() throws Exception {
        ProviderSessionContext stellar = exchange("stellar", "Stellar Network", true, true);
        SessionState state = service.sessionState(
                stellar,
                TradePair.of("BTCLN", "XLM"),
                TradabilityStatus.FULLY_TRADABLE,
                Map.of("stellar.baseTrustlineExists", true, "stellar.tradeable", true),
                Instant.parse("2026-06-06T12:00:00Z"));

        assertFalse(state.requiresActiveSession());
        assertTrue(state.orderSubmissionOpen());
    }

    @Test
    void stockInstrumentOutsideMarketHoursRequiresClosedSession() throws Exception {
        ProviderSessionContext alpaca = exchange("alpaca", "Alpaca", true, true);
        SessionState state = service.sessionState(
                alpaca,
                TradePair.of("AAPL", "USD"),
                TradabilityStatus.FULLY_TRADABLE,
                Map.of(),
                Instant.parse("2026-06-06T12:00:00Z"));

        assertTrue(state.requiresActiveSession());
        assertFalse(state.orderSubmissionOpen());
    }

    private ProviderSessionContext exchange(String id, String name, boolean connected, boolean canSubmitOrders) {
        return new ProviderSessionContext(id, name, connected, canSubmitOrders);
    }

    private SymbolTradability tradability(
            TradePair pair,
            TradabilityStatus status,
            SessionState state,
            boolean marketAllowed,
            boolean limitAllowed,
            String reason
    ) {
        return new SymbolTradability(
                "coinbase",
                pair,
                pair.toString('/'),
                status,
                state.marketDataAvailable(),
                true,
                true,
                true,
                state.orderSubmissionOpen(),
                state.orderSubmissionOpen(),
                state.orderSubmissionOpen(),
                marketAllowed,
                limitAllowed,
                false,
                false,
                false,
                false,
                reason,
                Instant.now(),
                Map.of(
                        "requiresActiveSession", state.requiresActiveSession(),
                        "session.orderSubmissionOpen", state.orderSubmissionOpen()));
    }

    private boolean canSubmit(SymbolTradability status, OpenOrder.OrderType orderType) {
        if (status == null || !status.orderSubmissionAllowed()) {
            return false;
        }
        return switch (orderType) {
            case MARKET -> status.marketOrderAllowed();
            case LIMIT -> status.limitOrderAllowed();
            default -> status.orderSubmissionAllowed();
        };
    }
}
