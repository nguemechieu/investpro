package org.investpro.exchange;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.oanda.Oanda;
import org.investpro.exchange.solona.SolonaNetworkConfig;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalOnlyPaperTradingTest {
    @Test
    void schwabDirectPaperOrdersNeverReachBrokerApi() throws Exception {
        var exchange = new org.investpro.exchange.schwab.Schwab(new ExchangeCredentials("schwab",
                "unused", "unused", null, null, null, "unused", true));
        var field = exchange.getClass().getDeclaredField("apiClient");
        Object api = mock(field.getType());
        field.setAccessible(true);
        field.set(exchange, api);
        TradePair pair = mock(TradePair.class);
        when(pair.toString('/')).thenReturn("AAPL/USD");
        when(pair.getLastPrice()).thenReturn(100.0);
        String id = exchange.createMarketOrder(pair, Side.BUY, 1).join();
        assertTrue(id.startsWith("paper-"));
        assertEquals(9900, exchange.fetchAccount().join().getAvailableBalance());
        String limit = exchange.createLimitOrder(pair, Side.BUY, 1, 90).join();
        assertEquals(1, exchange.fetchAllOpenOrders().join().size());
        exchange.cancelOrder(limit).join();
        assertTrue(exchange.fetchAllOpenOrders().join().isEmpty());
        assertEquals(2, exchange.fetchOrderHistory(pair, null).join().size());
        verifyNoInteractions(api);
    }

    @Test
    void oandaDirectPaperOrdersAccountsHistoryAndCancellationNeverCallBroker() throws Exception {
        Oanda exchange = new Oanda(new ExchangeCredentials("oanda", "unused", "", null, null,
                null, "101-legacy-practice", true));
        HttpClient http = mock(HttpClient.class);
        var field = Oanda.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(exchange, http);
        TradePair pair = new TradePair("EUR", "USD");
        pair.setLast(1.1);

        String market = exchange.createMarketOrder(pair, Side.BUY, 100).join();
        String limit = exchange.createLimitOrder(pair, Side.BUY, 100, 1.0).join();
        assertTrue(market.startsWith("paper-"));
        assertEquals("FILLED", exchange.fetchOrder(market).join().orElseThrow().getStatus());
        assertEquals(2, exchange.fetchOrderHistory(pair, null).join().size());
        assertEquals(1, exchange.fetchAllOpenOrders().join().size());
        exchange.cancelOrder(limit).join();
        assertTrue(exchange.fetchAllOpenOrders().join().isEmpty());
        assertEquals(9890, exchange.fetchAccount().join().getAvailableBalance(), 0.0001);
        assertTrue(exchange.getUserAccountDetails().isPaperTrading());
        // Broker-only portfolio operations must fail instead of mutating a real account.
        assertThrows(CompletionException.class, () -> exchange.closePosition(pair).join());
        verifyNoInteractions(http);
    }

    @Test
    void legacyModeAliasesRemainLocalAndDoNotSelectPracticeEndpoint() {
        Oanda exchange = new Oanda(new ExchangeCredentials("oanda", "", "", null, null,
                null, "", false, Map.of()));
        for (String mode : new String[]{"PAPER", "SANDBOX", "PRACTICE", "TESTNET", "DEMO"}) {
            exchange.setUserSelectedTradingMode(mode);
            assertTrue(exchange.isPaperTrading(), mode);
            assertNotSame(exchange, exchange.orderExecution(), mode);
            assertEquals("https://api-fxtrade.oanda.com", exchange.getCapability().getApiBaseUrl());
        }
    }

    @Test
    void legacySolanaTestNetworkCannotGrantLiveTradingWhenDataMovesToMainnet() {
        var config = new SolonaNetworkConfig(true, "devnet", "https://api.devnet.solona.com",
                "confirmed", true, 30, 3);
        assertTrue(config.isMainnet());
        assertFalse(config.isLiveTradingAllowed());
        assertEquals("https://api.mainnet-beta.solona.com", config.rpcUrl());
    }
}
