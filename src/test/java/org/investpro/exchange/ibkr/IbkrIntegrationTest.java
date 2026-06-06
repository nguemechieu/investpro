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
    void orderSubmissionSupportsMarketAndBracketAndPersists() throws Exception {
        IbkrExchange exchange = paperExchange();
        exchange.connect();

        TradePair pair = new TradePair("EUR", "USD");

        String marketExecutionId = exchange.createMarketOrder(pair, Side.BUY, 10_000).join();
        String bracketOrderId = exchange.createBracketOrder(pair, Side.BUY, 1_000, 1.10, 1.08, 1.12).join();

        assertThat(marketExecutionId).isNotBlank();
        assertThat(bracketOrderId).isNotBlank();
        assertThat(exchange.fetchAllPositions().join()).isNotEmpty();
        assertThat(exchange.fetchAllOpenOrders().join()).hasSizeGreaterThanOrEqualTo(3);

        assertThat(Files.exists(Path.of("data", "ibkr", "orders.json"))).isTrue();
        assertThat(Files.exists(Path.of("data", "ibkr", "executions.json"))).isTrue();
    }

    @Test
    void positionAndAccountSynchronizationWritesSnapshots() throws Exception {
        IbkrExchange exchange = paperExchange();
        exchange.connect();

        TradePair pair = new TradePair("EUR", "USD");
        exchange.createMarketOrder(pair, Side.BUY, 5_000).join();
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
                        "clientId", "42"));

        IbkrExchange exchange = new IbkrExchange(credentials);
        exchange.connect();

        assertThat(exchange.getConnectionManager().getHost()).isEqualTo("192.0.2.10");
        assertThat(exchange.getConnectionManager().getPort()).isEqualTo(7497);
        assertThat(exchange.getConnectionManager().getClientId()).isEqualTo(42);
    }

    @Test
    void paperTradingWorkflowAllowsTradingWithoutLiveLicenseGate() throws Exception {
        IbkrExchange exchange = paperExchange();
        exchange.setLiveTradingLicenseGate(() -> false);
        exchange.setUserSelectedTradingMode("PAPER");
        exchange.connect();

        TradePair pair = new TradePair("EUR", "USD");
        String id = exchange.createLimitOrder(pair, Side.BUY, 1_000, 1.10).join();

        assertThat(id).isNotBlank();
        assertThat(exchange.fetchOpenOrders(pair).join())
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
                true);

        IbkrExchange exchange = new IbkrExchange(credentials);
        exchange.setUserSelectedTradingMode("PAPER");
        return exchange;
    }
}
