package org.investpro.exchange.diagnostics;

import org.investpro.exchange.contracts.ExchangeIdentity;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.MarketDepthType;
import org.investpro.exchange.services.ExchangeService;
import org.investpro.utils.MARKET_TYPES;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Integration tests for ExchangeDiagnosticsService.
 *
 * <p>
 * Verifies the service can capture and report exchange health snapshots,
 * and track diagnostic state across time.
 */
@DisplayName("ExchangeDiagnosticsService Integration Tests")
class ExchangeDiagnosticsServiceTest {

        private ExchangeService exchangeService;
        private ExchangeDiagnosticsService diagnosticsService;
        private ExchangeIdentity spyAdapter;

        @BeforeEach
        void setUp() {
                // Use configurable stub to avoid Mockito inline mocking issues
                spyAdapter = new ConfigurableExchangeAdapter("TestExchange");
                exchangeService = new ExchangeService();
                diagnosticsService = new ExchangeDiagnosticsService(exchangeService);
        }

        @Test
        @DisplayName("ExchangeDiagnosticsService captures auth snapshots")
        void testCaptureAuthSnapshot() {
                ConfigurableExchangeAdapter adapter = (ConfigurableExchangeAdapter) spyAdapter;
                adapter.setCapability(ExchangeCapability.builder()
                                .exchangeName("TestExchange")
                                .supportsTopOfBook(true)
                                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                                .build());
                adapter.setAuthResult(AuthCheckResult.builder()
                                .exchangeName("TestExchange")
                                .success(true)
                                .credentialSource("ENV_VAR")
                                .endpointTested("/api/accounts")
                                .httpStatus(200)
                                .message("OK")
                                .checkedAt(Instant.now())
                                .build());

                exchangeService.register("TestExchange", spyAdapter);

                ExchangeDiagnosticSnapshot snapshot = diagnosticsService.runDiagnostics("TestExchange");

                assertThat(snapshot)
                                .isNotNull()
                                .extracting("exchangeName").isEqualTo("TestExchange");
                assertThat(snapshot.isAuthSuccess()).isTrue();
                assertThat(snapshot.getCredentialSource()).isEqualTo("ENV_VAR");
        }

        @Test
        @DisplayName("ExchangeDiagnosticsService caches snapshots")
        void testSnapshotCaching() {
                ConfigurableExchangeAdapter adapter = (ConfigurableExchangeAdapter) spyAdapter;
                adapter.setCapability(ExchangeCapability.builder()
                                .exchangeName("TestExchange")
                                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                                .build());
                adapter.setAuthResult(AuthCheckResult.builder()
                                .exchangeName("TestExchange")
                                .success(true)
                                .httpStatus(200)
                                .build());

                exchangeService.register("TestExchange", spyAdapter);

                // Run diagnostics
                ExchangeDiagnosticSnapshot snapshot1 = diagnosticsService.runDiagnostics("TestExchange");
                assertThat(snapshot1).isNotNull();

                // Get cached snapshot
                Optional<ExchangeDiagnosticSnapshot> cached = diagnosticsService.getSnapshot("TestExchange");
                assertThat(cached).isPresent();
                assertThat(cached.get().getExchangeName()).isEqualTo("TestExchange");
        }

        @Test
        @DisplayName("ExchangeDiagnosticsService tracks credential source")
        void testCredentialSourceTracking() {
                ConfigurableExchangeAdapter adapter = (ConfigurableExchangeAdapter) spyAdapter;
                adapter.setCapability(ExchangeCapability.builder()
                                .exchangeName("TestExchange")
                                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                                .build());
                adapter.setAuthResult(AuthCheckResult.builder()
                                .exchangeName("TestExchange")
                                .success(true)
                                .credentialSource("CONFIG_FILE")
                                .httpStatus(200)
                                .build());

                exchangeService.register("TestExchange", spyAdapter);
                diagnosticsService.runDiagnostics("TestExchange");

                Optional<String> source = diagnosticsService.getCredentialSource("TestExchange");
                assertThat(source)
                                .isPresent()
                                .contains("CONFIG_FILE");
        }

        @Test
        @DisplayName("ExchangeDiagnosticsService tracks HTTP status")
        void testHttpStatusTracking() {
                ConfigurableExchangeAdapter adapter = (ConfigurableExchangeAdapter) spyAdapter;
                adapter.setCapability(ExchangeCapability.builder()
                                .exchangeName("TestExchange")
                                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                                .build());
                adapter.setAuthResult(AuthCheckResult.builder()
                                .exchangeName("TestExchange")
                                .success(true)
                                .httpStatus(200)
                                .build());

                exchangeService.register("TestExchange", spyAdapter);
                diagnosticsService.runDiagnostics("TestExchange");

                int status = diagnosticsService.getLastHttpStatus("TestExchange");
                assertThat(status).isEqualTo(200);
        }

        @Test
        @DisplayName("ExchangeDiagnosticsService aggregates all diagnostics")
        void testAggregateAllDiagnostics() {
                ConfigurableExchangeAdapter spyCoinbase = new ConfigurableExchangeAdapter("Coinbase");
                ConfigurableExchangeAdapter spyOanda = new ConfigurableExchangeAdapter("OANDA");

                spyCoinbase.setCapability(ExchangeCapability.builder()
                                .exchangeName("Coinbase")
                                .marketDepthType(MarketDepthType.FULL_ORDER_BOOK)
                                .build());
                spyOanda.setCapability(ExchangeCapability.builder()
                                .exchangeName("OANDA")
                                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                                .build());
                spyCoinbase.setAuthResult(AuthCheckResult.builder()
                                .exchangeName("Coinbase")
                                .success(true)
                                .httpStatus(200)
                                .build());
                spyOanda.setAuthResult(AuthCheckResult.builder()
                                .exchangeName("OANDA")
                                .success(true)
                                .httpStatus(200)
                                .build());

                exchangeService.register("Coinbase", spyCoinbase);
                exchangeService.register("OANDA", spyOanda);

                Map<String, ExchangeDiagnosticSnapshot> allSnapshots = diagnosticsService.runAllDiagnostics();

                assertThat(allSnapshots)
                                .hasSize(2)
                                .containsKeys("Coinbase", "OANDA");
        }

        @Test
        @DisplayName("ExchangeDiagnosticsService provides health summaries")
        void testHealthSummary() {
                ConfigurableExchangeAdapter adapter = (ConfigurableExchangeAdapter) spyAdapter;
                adapter.setCapability(ExchangeCapability.builder()
                                .exchangeName("TestExchange")
                                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                                .build());
                adapter.setAuthResult(AuthCheckResult.builder()
                                .exchangeName("TestExchange")
                                .success(true)
                                .endpointTested("/api/accounts")
                                .httpStatus(200)
                                .build());

                exchangeService.register("TestExchange", spyAdapter);
                diagnosticsService.runDiagnostics("TestExchange");

                String summary = diagnosticsService.getHealthSummary("TestExchange");

                assertThat(summary)
                                .contains("TestExchange")
                                .contains("auth=OK")
                                .contains("httpStatus=200")
                                .contains("/api/accounts");
        }

        @Test
        @DisplayName("ExchangeDiagnosticsService handles auth failures")
        void testAuthFailureDiagnostics() {
                ConfigurableExchangeAdapter adapter = (ConfigurableExchangeAdapter) spyAdapter;
                adapter.setCapability(ExchangeCapability.builder()
                                .exchangeName("TestExchange")
                                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                                .build());
                adapter.setAuthResult(AuthCheckResult.builder()
                                .exchangeName("TestExchange")
                                .success(false)
                                .credentialIssue(true)
                                .endpointTested("/api/accounts")
                                .httpStatus(401)
                                .message("Invalid API key")
                                .build());

                exchangeService.register("TestExchange", spyAdapter);
                ExchangeDiagnosticSnapshot snapshot = diagnosticsService.runDiagnostics("TestExchange");

                assertThat(snapshot.isAuthSuccess()).isFalse();
                assertThat(snapshot.getLastHttpStatus()).isEqualTo(401);
                assertThat(diagnosticsService.isAuthSuccessful("TestExchange")).isFalse();
        }

        @Test
        @DisplayName("ExchangeDiagnosticsService returns empty string for missing error message")
        void testMissingErrorMessage() {
                // No diagnostics run
                String errorMsg = diagnosticsService.getLastErrorMessage("NonExistent");
                assertThat(errorMsg).isEmpty();
        }

        @Test
        @DisplayName("ExchangeDiagnosticsService provides capability queries")
        void testCapabilityQueries() {
                ConfigurableExchangeAdapter adapter = (ConfigurableExchangeAdapter) spyAdapter;
                adapter.setCapability(ExchangeCapability.builder()
                                .exchangeName("TestExchange")
                                .supportsSpot(true)
                                .supportsForex(false)
                                .marketDepthType(MarketDepthType.FULL_ORDER_BOOK)
                                .build());

                exchangeService.register("TestExchange", spyAdapter);

                ExchangeCapability queried = diagnosticsService.getCapability("TestExchange");
                assertThat(queried)
                                .isNotNull()
                                .extracting("exchangeName").isEqualTo("TestExchange");
                assertThat(queried.isSupportsSpot()).isTrue();
                assertThat(queried.isSupportsForex()).isFalse();
        }

        /**
         * Configurable exchange adapter for testing - allows setting behavior without
         * mocking.
         */
        static class ConfigurableExchangeAdapter implements ExchangeIdentity {
                private final String name;
                private ExchangeCapability capability;
                private AuthCheckResult authResult;

                ConfigurableExchangeAdapter(String name) {
                        this.name = name;
                        this.capability = ExchangeCapability.builder()
                                        .exchangeName(name)
                                        .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                                        .build();
                        this.authResult = AuthCheckResult.builder()
                                        .exchangeName(name)
                                        .success(true)
                                        .httpStatus(200)
                                        .build();
                }

                void setCapability(ExchangeCapability capability) {
                        this.capability = capability;
                }

                void setAuthResult(AuthCheckResult authResult) {
                        this.authResult = authResult;
                }

                @Override
                public String getName() {
                        return name;
                }

                @Override
                public String getSignal() {
                        return "";
                }

                @Override
                public String getExchangeId() {
                        return "";
                }

                @Override
                public String getDisplayName() {
                        return name;
                }

                @Override
                public boolean isSandbox() {
                        return false;
                }

                @Override
                public boolean isPaperTrading() {
                        return false;
                }

                @Override
                public String getTimestamp() {
                        return "";
                }

                @Override
                public Instant now() {
                        return Instant.now();
                }

                @Override
                public boolean supportsMarketType(MARKET_TYPES marketType) {
                        return false;
                }

                @Override
                public List<MARKET_TYPES> getSupportedMarketTypes() {
                        return List.of();
                }

                @Override
                public @NotNull ExchangeCapability getCapability() {
                        return capability;
                }

                @Override
                public AuthCheckResult checkAuthentication() {
                        return authResult;
                }
        }
}
