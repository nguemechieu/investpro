package org.investpro.exchange.ibkr;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class IbkrPositionCloseTest {
    private static IbkrResolvedContract contract(long id) {
        return new IbkrResolvedContract(id, "AAPL", "AAPL option", "OPT", "USD", "SMART", "", "AAPL", "100", "20261218", 200.0, "C", null, null, "", "", "", "", "TEST", Instant.now(), Instant.now(), "");
    }
    static class Session extends StubIbkrTwsSession {
        List<Position> held = List.of();
        List<String> submissions = new ArrayList<>();
        @Override public CompletableFuture<List<Position>> positions() { return CompletableFuture.completedFuture(held); }
        @Override public Optional<IbkrResolvedContract> positionContract(String id) { return Optional.of(IbkrPositionCloseTest.contract(Long.parseLong(id))); }
        @Override public CompletableFuture<String> submit(IbkrResolvedContract contract, Side side, double quantity, String type, double price, double stop) {
            submissions.add(contract.conId() + ":" + side + ":" + quantity + ":" + type);
            return CompletableFuture.completedFuture("broker-order-" + contract.conId());
        }
    }
    private IbkrExchange exchange(Session session) {
        var credentials = new ExchangeCredentials("interactive_brokers", null, null, null, null, null, "U123456", false,
                Map.of("host", "127.0.0.1", "port", "4001", "authMode", "gateway", "autoDetect", "false", "watchlist", ""));
        var exchange = new IbkrExchange(credentials, session);
        exchange.setLiveRiskApprovalGate(() -> true); exchange.connect();
        return exchange;
    }
    private Position position(TradePair pair, Side side, double quantity, String id) {
        var position = new Position(pair, side, quantity, 100); position.setPositionId(id); return position;
    }
    @Test void longCloseSellsExactBrokerQuantityWithoutClaimingFill() throws Exception {
        var session = new Session(); var pair = new TradePair("AAPL", "USD");
        var held = position(pair, Side.BUY, 3, "123"); session.held = List.of(held);
        var exchange = exchange(session);
        assertEquals("broker-order-123", exchange.closePosition(new TradePair("AAPL", "USD"), "123").join());
        assertEquals(List.of("123:sell:3.0:MKT"), session.submissions);
        assertTrue(held.isOpen()); exchange.disconnect();
    }
    @Test void partialShortCloseBuysOnlyRequestedQuantity() throws Exception {
        var session = new Session(); var pair = new TradePair("AAPL", "USD");
        session.held = List.of(position(pair, Side.SELL, 5, "123")); var exchange = exchange(session);
        exchange.closePartialPosition(pair, "123", 2).join();
        assertEquals(List.of("123:buy:2.0:MKT"), session.submissions); exchange.disconnect();
    }
    @Test void oversizedAndInvalidClosesNeverSubmit() throws Exception {
        var session = new Session(); var pair = new TradePair("AAPL", "USD");
        session.held = List.of(position(pair, Side.BUY, 2, "123")); var exchange = exchange(session);
        for (double quantity : new double[]{3, Double.NaN, 0}) assertThrows(java.util.concurrent.CompletionException.class,
                () -> exchange.closePartialPosition(pair, "123", quantity).join());
        assertTrue(session.submissions.isEmpty()); exchange.disconnect();
    }
    @Test void differentOptionContractsRequireExactPositionId() throws Exception {
        var session = new Session(); var pair = new TradePair("AAPL", "USD");
        session.held = List.of(position(pair, Side.BUY, 2, "123"), position(pair, Side.SELL, 4, "456"));
        var exchange = exchange(session);
        assertThrows(java.util.concurrent.CompletionException.class, () -> exchange.closePosition(pair).join());
        exchange.closePosition(pair, "456").join();
        assertEquals(List.of("456:buy:4.0:MKT"), session.submissions); exchange.disconnect();
    }
    @Test void riskGateStillAppliesToCloses() throws Exception {
        var session = new Session(); var pair = new TradePair("AAPL", "USD");
        session.held = List.of(position(pair, Side.BUY, 2, "123")); var exchange = exchange(session);
        exchange.setLiveRiskApprovalGate(() -> false);
        assertThrows(java.util.concurrent.CompletionException.class, () -> exchange.closePosition(pair).join());
        assertTrue(session.submissions.isEmpty()); exchange.disconnect();
    }
    @Test void openingOrdersUseBrokerResolvedContractAndNativeOrderTypes() throws Exception {
        var session = new Session();
        var exchange = exchange(session);
        var pair = new TradePair("AAPL", "USD");
        var resolver = org.mockito.Mockito.mock(IbkrContractResolver.class);
        org.mockito.Mockito.when(resolver.requireResolved(pair)).thenReturn(contract(123));
        var field = IbkrExchange.class.getDeclaredField("contractResolver");
        field.setAccessible(true);
        field.set(exchange, resolver);
        assertEquals("broker-order-123", exchange.createMarketOrder(pair, Side.BUY, 1).join());
        assertEquals("broker-order-123", exchange.createLimitOrder(pair, Side.SELL, 2, 5).join());
        assertEquals("broker-order-123", exchange.createStopOrder(pair, Side.SELL, 3, 4).join());
        assertEquals(List.of("123:buy:1.0:MKT", "123:sell:2.0:LMT", "123:sell:3.0:STP"), session.submissions);
        exchange.disconnect();
    }    @Test void adapterSymbolParsingPreservesResolvedContractId() throws Exception {
        var session = new Session(); var exchange = exchange(session);
        var repository = org.mockito.Mockito.mock(IbkrContractRepository.class);
        org.mockito.Mockito.when(repository.findByDisplaySymbol(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        org.mockito.Mockito.when(repository.findByConIdExchange(123, "")).thenReturn(Optional.of(contract(123)));
        var field = IbkrExchange.class.getDeclaredField("contractRepository");
        field.setAccessible(true); field.set(exchange, repository);
        var pair = exchange.parsePair("123");
        assertEquals("123", pair.getNativeSymbol());
        assertEquals("AAPL", pair.getBaseCode());
        assertThrows(IllegalArgumentException.class, () -> exchange.parsePair("456"));
        exchange.disconnect();
    }    @Test void explicitManualOrdersDoNotRequireBotApprovalButKeepLicenseCheck() throws Exception {
        var session = new Session(); var exchange = exchange(session);
        exchange.setAuthenticatedSessionConnected(true);
        exchange.setLiveRiskApprovalGate(() -> false);
        var pair = new TradePair("AAPL", "USD");
        var resolver = org.mockito.Mockito.mock(IbkrContractResolver.class);
        org.mockito.Mockito.when(resolver.requireResolved(pair)).thenReturn(contract(123));
        var field = IbkrExchange.class.getDeclaredField("contractResolver");
        field.setAccessible(true); field.set(exchange, resolver);
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> exchange.createMarketOrder(pair, Side.BUY, 1).join());
        assertEquals("broker-order-123", org.investpro.exchange.ManualOrderSubmission.submit(exchange, false,
                org.investpro.exchange.ManualOrderSubmission.Type.MARKET, pair, Side.BUY, 1, 0, 0, 0, false).join());
        assertEquals("broker-order-123", org.investpro.exchange.ManualOrderSubmission.submit(exchange, false,
                org.investpro.exchange.ManualOrderSubmission.Type.STOP, pair, Side.SELL, 1, 100, 0, 0, false).join());
        exchange.setLiveTradingLicenseGate(() -> false);
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> org.investpro.exchange.ManualOrderSubmission.submit(exchange, false,
                        org.investpro.exchange.ManualOrderSubmission.Type.MARKET, pair, Side.BUY, 1, 0, 0, 0, false).join());
        assertEquals(2, session.submissions.size()); exchange.disconnect();
    }}
