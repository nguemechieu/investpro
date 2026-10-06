package org.investpro.exchange.ibkr;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.models.Account;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.ORDER_TYPES;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IbkrIntegrationTest {

    @Test
    void connectionLifecycleWorksForPaperAndReconnect() {
        IbkrExchange exchange = paperExchange();

        exchange.connect();
        assertThat(exchange.isConnected()).isTrue();

        exchange.reconnect();
        assertThat(exchange.isConnected()).isTrue();

        exchange.disconnect();
        assertThat(exchange.isConnected()).isFalse();
    }

    @Test
    void contractMappingSupportsRequestedAssetTypes() throws Exception {
        IbkrContractMapper mapper = new IbkrContractMapper();
        TradePair pair = new TradePair("EUR", "USD");

        assertThat(mapper.toContract(pair, MARKET_TYPES.STOCKS).secType()).isEqualTo("CASH");
        assertThat(mapper.toContract(pair, MARKET_TYPES.FUTURES).secType()).isEqualTo("FUT");
        assertThat(mapper.toContract(pair, ORDER_TYPES.STOP_LIMIT).secType()).isEqualTo("OPT");
    }

    @Test
    void localSimulatorSupportsMarketAndBracketAndPersists() throws Exception {
        IbkrExchange exchange = paperExchange();
        exchange.connect();

        TradePair pair = new TradePair("EUR", "USD");

        String marketExecutionId = exchange.getOrderService().submitMarket(pair, Side.BUY, 10_000);
        String bracketOrderId = exchange.getOrderService().submitBracket(pair, Side.BUY, 1_000, 1.10, 1.08, 1.12);

        assertThat(marketExecutionId).isNotBlank();
        assertThat(bracketOrderId).isNotBlank();
        assertThat(exchange.getPositionService().fetchAll()).isNotEmpty();
        assertThat(exchange.getOrderService().fetchAllOpenOrders()).hasSizeGreaterThanOrEqualTo(3);

        assertThat(Files.exists(Path.of("data", "ibkr", "orders.json"))).isTrue();
        assertThat(Files.exists(Path.of("data", "ibkr", "executions.json"))).isTrue();
    }

    @Test
    void positionAndAccountSynchronizationWritesSnapshots() throws Exception {
        IbkrExchange exchange = paperExchange();
        exchange.connect();

        TradePair pair = new TradePair("EUR", "USD");
        exchange.getOrderService().submitMarket(pair, Side.BUY, 5_000);
        exchange.synchronizePortfolio();

        assertThat(Files.exists(Path.of("data", "ibkr", "positions.json"))).isTrue();
        assertThat(Files.exists(Path.of("data", "ibkr", "account.json"))).isTrue();
    }

    @Test
    void accountSynchronizationReturnsConnectedAccount() {
        IbkrExchange exchange = paperExchange();
        exchange.connect();

        Account account = exchange.fetchAccount().join();

        assertThat(account.isConnected()).isTrue();
        assertThat(account.getExchangeId()).isEqualTo("interactive_brokers");
        assertThat(account.getBalances()).containsKey("USD");
    }

    @Test
    void reconnectBehaviorMaintainsHealthSnapshot() {
        IbkrExchange exchange = paperExchange();
        exchange.connect();
        exchange.reconnect();

        IbkrConnectionManager.ConnectionHealth health = exchange.connectionHealth();

        assertThat(health.connected()).isTrue();
        assertThat(health.reconnectAttempts()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void connectionUsesEndpointParamsFromExchangeCredentials() {
        ExchangeCredentials credentials = new ExchangeCredentials(
                "interactive_brokers",
                "paper-key",
                "paper-secret",
                null,
                null,
                null,
                "DU123456",
                true,
                Map.of(
                        "host", "192.0.2.10",
                        "port", "7497",
                        "clientId", "42", "watchlist", ""));

        IbkrExchange exchange = new IbkrExchange(credentials, new StubIbkrTwsSession());
        exchange.connect();

        assertThat(exchange.getConnectionManager().getHost()).isEqualTo("192.0.2.10");
        assertThat(exchange.getConnectionManager().getPort()).isEqualTo(7497);
        assertThat(exchange.getConnectionManager().getClientId()).isEqualTo(42);
    }

    @Test
    void localSimulatorAllowsTradingWithoutLiveLicenseGate() throws Exception {
        IbkrExchange exchange = paperExchange();
        exchange.setLiveTradingLicenseGate(() -> false);
        exchange.setUserSelectedTradingMode("PAPER");
        exchange.connect();

        TradePair pair = new TradePair("EUR", "USD");
        String id = exchange.getOrderService().submitLimit(pair, Side.BUY, 1_000, 1.10);

        assertThat(id).isNotBlank();
        assertThat(exchange.getOrderService().fetchOpenOrders(pair))
                .extracting(OpenOrder::getOrderId)
                .contains(id);
    }

    private static IbkrExchange paperExchange() {
        ExchangeCredentials credentials = new ExchangeCredentials(
                "interactive_brokers",
                "paper-key",
                "paper-secret",
                null,
                null,
                null,
                "DU123456",
                true, Map.of("watchlist", ""));

        IbkrExchange exchange = new IbkrExchange(credentials, new StubIbkrTwsSession());
        exchange.setUserSelectedTradingMode("PAPER");
        // Seed resolved contract metadata for this offline integration fixture.
        exchange.getContractCache().put(new IbkrResolvedContract(
                12087792L, "EUR", "EUR.USD", "CASH", "USD", "IDEALPRO", "", "", "", "",
                null, "", null, 0.00005, "", "Euro / US Dollar", "Forex", "", "test", null, null, "{}"));
        return exchange;
    }
}
