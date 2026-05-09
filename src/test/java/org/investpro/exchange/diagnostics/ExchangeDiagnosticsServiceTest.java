package org.investpro.exchange.diagnostics;

import org.investpro.exchange.contracts.ExchangeIdentity;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.MarketDepthType;
import org.investpro.exchange.services.ExchangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
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

    @Mock
    private ExchangeIdentity mockAdapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        exchangeService = new ExchangeService();
        diagnosticsService = new ExchangeDiagnosticsService(exchangeService);
    }

    @Test
    @DisplayName("ExchangeDiagnosticsService captures auth snapshots")
    void testCaptureAuthSnapshot() {
        ExchangeCapability capability = ExchangeCapability.builder()
                .exchangeName("TestExchange")
                .supportsTopOfBook(true)
                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                .build();

        AuthCheckResult authResult = AuthCheckResult.builder()
                .exchangeName("TestExchange")
                .success(true)
                .credentialSource("ENV_VAR")
                .endpointTested("/api/accounts")
                .httpStatus(200)
                .message("OK")
                .checkedAt(Instant.now())
                .build();

        when(mockAdapter.getCapability()).thenReturn(capability);
        when(mockAdapter.checkAuthentication()).thenReturn(authResult);

        exchangeService.register("TestExchange", mockAdapter);

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
        ExchangeCapability capability = ExchangeCapability.builder()
                .exchangeName("TestExchange")
                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                .build();

        AuthCheckResult authResult = AuthCheckResult.builder()
                .exchangeName("TestExchange")
                .success(true)
                .httpStatus(200)
                .build();

        when(mockAdapter.getCapability()).thenReturn(capability);
        when(mockAdapter.checkAuthentication()).thenReturn(authResult);

        exchangeService.register("TestExchange", mockAdapter);

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
        ExchangeCapability capability = ExchangeCapability.builder()
                .exchangeName("TestExchange")
                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                .build();

        AuthCheckResult authResult = AuthCheckResult.builder()
                .exchangeName("TestExchange")
                .success(true)
                .credentialSource("CONFIG_FILE")
                .httpStatus(200)
                .build();

        when(mockAdapter.getCapability()).thenReturn(capability);
        when(mockAdapter.checkAuthentication()).thenReturn(authResult);

        exchangeService.register("TestExchange", mockAdapter);
        diagnosticsService.runDiagnostics("TestExchange");

        Optional<String> source = diagnosticsService.getCredentialSource("TestExchange");
        assertThat(source)
                .isPresent()
                .contains("CONFIG_FILE");
    }

    @Test
    @DisplayName("ExchangeDiagnosticsService tracks HTTP status")
    void testHttpStatusTracking() {
        ExchangeCapability capability = ExchangeCapability.builder()
                .exchangeName("TestExchange")
                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                .build();

        AuthCheckResult authResult = AuthCheckResult.builder()
                .exchangeName("TestExchange")
                .success(true)
                .httpStatus(200)
                .build();

        when(mockAdapter.getCapability()).thenReturn(capability);
        when(mockAdapter.checkAuthentication()).thenReturn(authResult);

        exchangeService.register("TestExchange", mockAdapter);
        diagnosticsService.runDiagnostics("TestExchange");

        int status = diagnosticsService.getLastHttpStatus("TestExchange");
        assertThat(status).isEqualTo(200);
    }

    @Test
    @DisplayName("ExchangeDiagnosticsService aggregates all diagnostics")
    void testAggregateAllDiagnostics() {
        ExchangeCapability coinbase = ExchangeCapability.builder()
                .exchangeName("Coinbase")
                .marketDepthType(MarketDepthType.FULL_ORDER_BOOK)
                .build();

        ExchangeCapability oanda = ExchangeCapability.builder()
                .exchangeName("OANDA")
                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                .build();

        ExchangeIdentity mockCoinbase = mock(ExchangeIdentity.class);
        ExchangeIdentity mockOanda = mock(ExchangeIdentity.class);

        when(mockCoinbase.getCapability()).thenReturn(coinbase);
        when(mockOanda.getCapability()).thenReturn(oanda);
        when(mockCoinbase.checkAuthentication()).thenReturn(
                AuthCheckResult.builder()
                        .exchangeName("Coinbase")
                        .success(true)
                        .httpStatus(200)
                        .build());
        when(mockOanda.checkAuthentication()).thenReturn(
                AuthCheckResult.builder()
                        .exchangeName("OANDA")
                        .success(true)
                        .httpStatus(200)
                        .build());

        exchangeService.register("Coinbase", mockCoinbase);
        exchangeService.register("OANDA", mockOanda);

        Map<String, ExchangeDiagnosticSnapshot> allSnapshots = diagnosticsService.runAllDiagnostics();

        assertThat(allSnapshots)
                .hasSize(2)
                .containsKeys("Coinbase", "OANDA");
    }

    @Test
    @DisplayName("ExchangeDiagnosticsService provides health summaries")
    void testHealthSummary() {
        ExchangeCapability capability = ExchangeCapability.builder()
                .exchangeName("TestExchange")
                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                .build();

        AuthCheckResult authResult = AuthCheckResult.builder()
                .exchangeName("TestExchange")
                .success(true)
                .endpointTested("/api/accounts")
                .httpStatus(200)
                .build();

        when(mockAdapter.getCapability()).thenReturn(capability);
        when(mockAdapter.checkAuthentication()).thenReturn(authResult);

        exchangeService.register("TestExchange", mockAdapter);
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
        ExchangeCapability capability = ExchangeCapability.builder()
                .exchangeName("TestExchange")
                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                .build();

        AuthCheckResult authResult = AuthCheckResult.builder()
                .exchangeName("TestExchange")
                .success(false)
                .credentialIssue(true)
                .endpointTested("/api/accounts")
                .httpStatus(401)
                .message("Invalid API key")
                .build();

        when(mockAdapter.getCapability()).thenReturn(capability);
        when(mockAdapter.checkAuthentication()).thenReturn(authResult);

        exchangeService.register("TestExchange", mockAdapter);
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
        ExchangeCapability capability = ExchangeCapability.builder()
                .exchangeName("TestExchange")
                .supportsSpot(true)
                .supportsForex(false)
                .marketDepthType(MarketDepthType.FULL_ORDER_BOOK)
                .build();

        when(mockAdapter.getCapability()).thenReturn(capability);
        exchangeService.register("TestExchange", mockAdapter);

        ExchangeCapability queried = diagnosticsService.getCapability("TestExchange");
        assertThat(queried)
                .isNotNull()
                .extracting("exchangeName").isEqualTo("TestExchange");
        assertThat(queried.isSupportsSpot()).isTrue();
        assertThat(queried.isSupportsForex()).isFalse();
    }
}
