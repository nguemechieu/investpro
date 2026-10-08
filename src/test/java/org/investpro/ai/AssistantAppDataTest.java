package org.investpro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.exchange.Exchange;
import org.investpro.models.Account;
import org.investpro.models.trading.*;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.data.CandleData;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantAppDataTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Test void accountProjectionExcludesCredentialsAndPersonalProfile() throws Exception {
        var exchange = mock(Exchange.class);
        var account = new Account(); account.setPassword("private-password"); account.setEmail("private@example.test");
        account.setBaseCurrency("EUR"); account.setTotalBalance(250); account.setEquity(270);
        when(exchange.tradingAccount()).thenReturn(CompletableFuture.completedFuture(account));
        when(exchange.getResolvedTradingMode()).thenReturn("LIVE");
        var text = AssistantAppData.execute("/data oanda account", Map.of("oanda", exchange));
        var result = JSON.readTree(text);
        assertEquals("EUR", result.path("currency").asText());
        assertEquals(270, result.path("equity").asDouble());
        assertEquals("LIVE", result.path("mode").asText());
        assertFalse(text.contains("private-password")); assertFalse(text.contains("private@example.test"));
        verify(exchange, never()).getCredentials();
    }
    @Test void nativeProductsAndDelayedQuoteMetadataArePreserved() throws Exception {
        var exchange = mock(Exchange.class);
        var quote = new Ticker(0.00123, 0.00122, 0.00124, 100, 123456789L);
        quote.setQuoteType(Ticker.QuoteType.DELAYED);
        when(exchange.fetchTicker(any())).thenReturn(CompletableFuture.completedFuture(quote));
        var text = AssistantAppData.execute("/data coinbase quote BIP-20DEC30-CDE", Map.of("coinbase", exchange));
        var result = JSON.readTree(text);
        assertEquals("BIP-20DEC30-CDE", result.path("symbol").asText());
        assertEquals("DELAYED", result.path("quoteType").asText());
        assertEquals(123456789L, result.path("quoteTimestampMillis").asLong());
        assertEquals(0.00122, result.path("bid").asDouble());
        var pair = org.mockito.ArgumentCaptor.forClass(TradePair.class);
        verify(exchange).fetchTicker(pair.capture());
        assertEquals("BIP-20DEC30-CDE", pair.getValue().getNativeSymbol());
    }
    @Test void paperPositionsStayLocalAndUnknownVenuesDoNotCallAnExchange() throws Exception {
        var exchange = mock(Exchange.class);
        var pair = new TradePair("BTC", "USD");
        when(exchange.isPaperTrading()).thenReturn(true);
        when(exchange.localPaperPositions()).thenReturn(List.of(new Position(pair, org.investpro.utils.Side.BUY, 1, 100)));
        var result = JSON.readTree(AssistantAppData.execute("/data coinbase positions", Map.of("coinbase", exchange)));
        assertEquals(1, result.path("positions").size());
        verify(exchange, never()).fetchAllPositions();
        clearInvocations(exchange);
        assertTrue(AssistantAppData.execute("/data missing account", Map.of("coinbase", exchange)).contains("Unknown"));
        verifyNoInteractions(exchange);
    }
    @Test void candleQueriesAreBoundedAndPreserveOnlyActualNonPlaceholderBars() throws Exception {
        var exchange = mock(Exchange.class); var supplier = mock(CandleDataSupplier.class);
        when(exchange.getCandleDataSupplier(eq(3600), any())).thenReturn(supplier);
        when(supplier.getSupportedGranularities()).thenReturn(Set.of(3600));
        when(supplier.get()).thenReturn(CompletableFuture.completedFuture(List.of(
                new CandleData(2, 3, 4, 1, 7200, 5), new CandleData(1, 2, 3, 0.5, 3600, 5))));
        var result = JSON.readTree(AssistantAppData.execute("/data coinbase candles BTC/USD 3600 1", Map.of("coinbase", exchange)));
        assertEquals(1, result.path("candles").size());
        assertEquals(7200, result.path("candles").get(0).path("openTime").asInt());
        clearInvocations(exchange, supplier);
        assertTrue(AssistantAppData.execute("/data coinbase candles BTC/USD 3600 101", Map.of("coinbase", exchange)).contains("1–100"));
        verify(exchange, never()).getCandleDataSupplier(anyInt(), any());
    }
    @Test void arbitraryAdapterFailureDetailsAreNotDisclosed() throws Exception {
        var exchange = mock(Exchange.class);
        when(exchange.fetchTicker(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("key=private-key")));
        String text = AssistantAppData.execute("/data coinbase quote BTC/USD", Map.of("coinbase", exchange));
        assertFalse(text.contains("private-key"));
        assertFalse(JSON.readTree(text).path("available").asBoolean(true));
    }
}
