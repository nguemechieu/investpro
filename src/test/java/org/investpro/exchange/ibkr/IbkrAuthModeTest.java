package org.investpro.exchange.ibkr;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.models.Account;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IbkrAuthModeTest {

    private static final String AUTH_MODE = "investpro.ibkr.authMode";
    private static final String CLIENT_PORTAL_URL = "investpro.ibkr.clientPortalUrl";
    private static final String TEST_USERNAME = "test-user";
    private static final String TEST_PASSWORD = "test-password";
    private static final String TEST_ACCOUNT_ID = "U25099271";

    @AfterEach
    void clearIbkrRuntimeOverrides() {
        System.clearProperty(AUTH_MODE);
        System.clearProperty(CLIENT_PORTAL_URL);
    }

    @Test
    void gatewayModeDoesNotCallClientPortalEndpoints() throws Exception {
        System.setProperty(AUTH_MODE, "gateway");

        try (MockIbkrGateway gateway = new MockIbkrGateway()) {
            gateway.endpoint("/v1/api/iserver/auth/status", "{\"authenticated\":false}");
            gateway.start();

            System.setProperty(CLIENT_PORTAL_URL, gateway.baseUrl());

            IbkrExchange exchange = liveExchange();
            exchange.connect();

            Account account = exchange.fetchAccount().join();

            assertThat(account).isNotNull();
            assertThat(account.isConnected()).isTrue();
            assertThat(account.getExchangeId()).isEqualTo("interactive_brokers");
            assertThat(account.getAccountId()).isNotBlank();
            assertThat(account.getBalances()).containsKey("USD");
            assertThat(gateway.hitCount("/v1/api/iserver/auth/status")).isZero();
        }
    }

    @Test
    void clientPortalModeAuthenticatesAgainstMockGateway() throws Exception {
        try (MockIbkrGateway gateway = new MockIbkrGateway()) {
            gateway.endpoint("/v1/api/iserver/auth/status",
                    "{\"authenticated\":true,\"connected\":true,\"competing\":false}");
            gateway.endpoint("/v1/api/iserver/auth/ssodh/init", "{\"ok\":true}");
            gateway.endpoint("/v1/api/tickle", "{\"session\":\"alive\"}");
            gateway.endpoint("/v1/api/portfolio/" + TEST_ACCOUNT_ID + "/summary",
                    """
                            {
                              "NetLiquidation": "101000.00",
                              "AvailableFunds": "82000.00",
                              "MaintMarginReq": "10000.00",
                              "BuyingPower": "180000.00",
                              "TotalCashValue": "75000.00"
                            }
                            """);
            gateway.start();

            System.setProperty(AUTH_MODE, "client-portal");
            System.setProperty(CLIENT_PORTAL_URL, gateway.baseUrl());

            IbkrExchange exchange = liveExchange();
            exchange.connect();

            Account account = exchange.fetchAccount().join();
            double usdBalance = exchange.fetchAvailableBalance("USD").join();

            assertThat(account).isNotNull();
            assertThat(account.isConnected()).isTrue();
            assertThat(account.getAccountId()).isEqualTo(TEST_ACCOUNT_ID);
            assertThat(account.getEquity()).isEqualTo(101000.0);
            assertThat(account.getAvailableBalance()).isEqualTo(82000.0);
            assertThat(account.getMarginUsed()).isEqualTo(10000.0);
            assertThat(account.getBuyingPower()).isEqualTo(180000.0);
            assertThat(account.getBalances()).containsEntry("USD", 101000.0);
            assertThat(account.getBrokerName()).isEqualTo("Interactive Brokers");
            assertThat(account.getBaseCurrency()).isEqualTo("USD");
            assertThat(account.getUpdatedAt()).isNotNull();
            assertThat(usdBalance).isEqualTo(101000.0);
        }
    }

    @Test
    void clientPortalAuthenticationFailureBlocksAccountFetch() throws Exception {
        try (MockIbkrGateway gateway = new MockIbkrGateway()) {
            gateway.endpoint("/v1/api/iserver/auth/status",
                    "{\"authenticated\":false,\"connected\":false,\"competing\":false}");
            gateway.start();

            System.setProperty(AUTH_MODE, "client-portal");
            System.setProperty(CLIENT_PORTAL_URL, gateway.baseUrl());

            IbkrExchange exchange = liveExchange();
            exchange.connect();

            assertThatThrownBy(() -> exchange.fetchAccount().join())
                    .hasRootCauseInstanceOf(SecurityException.class)
                    .hasMessageContaining("IBKR access denied");
        }
    }

    @Test
    void missingSummaryFieldsFallBackToSafeDefaults() throws Exception {
        try (MockIbkrGateway gateway = new MockIbkrGateway()) {
            gateway.endpoint("/v1/api/iserver/auth/status",
                    "{\"authenticated\":true,\"connected\":true,\"competing\":false}");
            gateway.endpoint("/v1/api/iserver/auth/ssodh/init", "{\"ok\":true}");
            gateway.endpoint("/v1/api/tickle", "{\"session\":\"alive\"}");
            gateway.endpoint("/v1/api/portfolio/" + TEST_ACCOUNT_ID + "/summary",
                    """
                            {
                              "NetLiquidation": "100000"
                            }
                            """);
            gateway.start();

            System.setProperty(AUTH_MODE, "client-portal");
            System.setProperty(CLIENT_PORTAL_URL, gateway.baseUrl());

            IbkrExchange exchange = liveExchange();
            exchange.connect();

            Account account = exchange.fetchAccount().join();

            assertThat(account.getEquity()).isEqualTo(100000.0);
            assertThat(account.getAvailableBalance()).isZero();
            assertThat(account.getBuyingPower()).isZero();
            assertThat(account.getMarginUsed()).isZero();
        }
    }

    @Test
    void supportsMultiCurrencyAccountSnapshot() throws Exception {
        try (MockIbkrGateway gateway = new MockIbkrGateway()) {
            gateway.endpoint("/v1/api/iserver/auth/status",
                    "{\"authenticated\":true,\"connected\":true,\"competing\":false}");
            gateway.endpoint("/v1/api/iserver/auth/ssodh/init", "{\"ok\":true}");
            gateway.endpoint("/v1/api/tickle", "{\"session\":\"alive\"}");
            gateway.endpoint("/v1/api/portfolio/" + TEST_ACCOUNT_ID + "/summary",
                    """
                            {
                              "NetLiquidation": "13000",
                              "AvailableFunds": "9000",
                              "BuyingPower": "26000",
                              "USD": "10000",
                              "EUR": "2500",
                              "GBP": "500"
                            }
                            """);
            gateway.start();

            System.setProperty(AUTH_MODE, "client-portal");
            System.setProperty(CLIENT_PORTAL_URL, gateway.baseUrl());

            IbkrExchange exchange = liveExchange();
            exchange.connect();

            Account account = exchange.fetchAccount().join();

            assertThat(account.getBalances())
                    .containsEntry("USD", 10000.0)
                    .containsEntry("EUR", 2500.0)
                    .containsEntry("GBP", 500.0);
        }
    }

    @Test
    void supportsConcurrentAccountRequests() throws Exception {
        try (MockIbkrGateway gateway = new MockIbkrGateway()) {
            gateway.endpoint("/v1/api/iserver/auth/status",
                    "{\"authenticated\":true,\"connected\":true,\"competing\":false}");
            gateway.endpoint("/v1/api/iserver/auth/ssodh/init", "{\"ok\":true}");
            gateway.endpoint("/v1/api/tickle", "{\"session\":\"alive\"}");
            gateway.endpoint("/v1/api/portfolio/" + TEST_ACCOUNT_ID + "/summary",
                    """
                            {
                              "NetLiquidation": "101000.00",
                              "AvailableFunds": "82000.00",
                              "MaintMarginReq": "10000.00",
                              "BuyingPower": "180000.00"
                            }
                            """);
            gateway.start();

            System.setProperty(AUTH_MODE, "client-portal");
            System.setProperty(CLIENT_PORTAL_URL, gateway.baseUrl());

            IbkrExchange exchange = liveExchange();
            exchange.connect();

            CompletableFuture<Account> a = exchange.fetchAccount();
            CompletableFuture<Account> b = exchange.fetchAccount();
            CompletableFuture<Account> c = exchange.fetchAccount();
            CompletableFuture.allOf(a, b, c).join();

            assertThat(a.join()).isNotNull();
            assertThat(b.join()).isNotNull();
            assertThat(c.join()).isNotNull();
        }
    }

    private static @NonNull IbkrExchange liveExchange() {
        ExchangeCredentials credentials = new ExchangeCredentials(
                "interactive_brokers",
                TEST_USERNAME,
                TEST_PASSWORD,
                null,
                null,
                null,
                IbkrAuthModeTest.TEST_ACCOUNT_ID,
                false);
        IbkrExchange exchange = new IbkrExchange(credentials);
        exchange.setUserSelectedTradingMode("LIVE");
        return exchange;
    }

    private static HttpHandler jsonHandler(String payload) {
        return exchange -> writeJson(exchange, payload);
    }

    private static void writeJson(HttpExchange exchange, String payload) throws IOException {
        byte[] body = Objects.requireNonNull(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static final class MockIbkrGateway implements AutoCloseable {

        private final HttpServer server;
        private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

        private MockIbkrGateway() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/api";
        }

        void endpoint(String path, String payload) {
            endpoint(path, jsonHandler(payload));
        }

        void endpoint(String path, HttpHandler handler) {
            String normalizedPath = Objects.requireNonNull(path);
            hits.computeIfAbsent(normalizedPath, key -> new AtomicInteger(0));
            server.createContext(normalizedPath, exchange -> {
                hits.get(normalizedPath).incrementAndGet();
                handler.handle(exchange);
            });
        }

        int hitCount(String path) {
            AtomicInteger counter = hits.get(path);
            return counter == null ? 0 : counter.get();
        }

        void start() {
            server.start();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}