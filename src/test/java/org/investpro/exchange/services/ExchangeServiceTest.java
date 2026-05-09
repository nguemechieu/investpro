package org.investpro.exchange.services;

import org.investpro.exchange.contracts.ExchangeIdentity;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.MarketDepthType;
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

/**
 * Integration tests for ExchangeService.
 *
 * <p>
 * Verifies the service can coordinate multiple adapters and provide
 * uniform access to capabilities and diagnostics.
 */
@DisplayName("ExchangeService Integration Tests")
class ExchangeServiceTest {

    private ExchangeService service;
    private StubExchangeAdapter coinbaseAdapter;
    private StubExchangeAdapter oandaAdapter;

    @BeforeEach
    void setUp() {
        service = new ExchangeService();
        coinbaseAdapter = new StubExchangeAdapter("Coinbase");
        oandaAdapter = new StubExchangeAdapter("OANDA");
    }

    @Test
    @DisplayName("ExchangeService registers adapters by name")
    void testRegisterAdapter() {
        service.register("Coinbase", coinbaseAdapter);

        assertThat(service.isRegistered("Coinbase")).isTrue();
        assertThat(service.getAvailableExchanges())
                .contains("Coinbase")
                .hasSize(1);
    }

    @Test
    @DisplayName("ExchangeService unregisters adapters")
    void testUnregisterAdapter() {
        service.register("Coinbase", coinbaseAdapter);
        assertThat(service.isRegistered("Coinbase")).isTrue();

        service.unregister("Coinbase");
        assertThat(service.isRegistered("Coinbase")).isFalse();
    }

    @Test
    @DisplayName("ExchangeService retrieves adapter by name")
    void testGetAdapter() {
        service.register("Coinbase", coinbaseAdapter);

        ExchangeIdentity retrieved = service.getAdapter("Coinbase");
        assertThat(retrieved).isSameAs(coinbaseAdapter);
    }

    @Test
    @DisplayName("ExchangeService throws on missing adapter")
    void testGetAdapterThrowsIfNotFound() {
        assertThatThrownBy(() -> service.getAdapter("NonExistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Exchange adapter not found");
    }

    @Test
    @DisplayName("ExchangeService returns Optional for optional adapter access")
    void testGetAdapterOptional() {
        service.register("Coinbase", coinbaseAdapter);

        Optional<ExchangeIdentity> found = service.getAdapterOptional("Coinbase");
        assertThat(found).isPresent().contains(coinbaseAdapter);

        Optional<ExchangeIdentity> notFound = service.getAdapterOptional("NonExistent");
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("ExchangeService aggregates capabilities from all adapters")
    void testGetAllCapabilities() {
        service.register("Coinbase", coinbaseAdapter);
        service.register("OANDA", oandaAdapter);

        Map<String, ExchangeCapability> allCapabilities = service.getAllCapabilities();
        assertThat(allCapabilities)
                .hasSize(2)
                .containsKeys("Coinbase", "OANDA");
    }

    @Test
    @DisplayName("ExchangeService checks authentication for all adapters")
    void testCheckAllAuthentication() {
        service.register("Coinbase", coinbaseAdapter);
        service.register("OANDA", oandaAdapter);

        Map<String, AuthCheckResult> allAuth = service.checkAllAuthentication();
        assertThat(allAuth)
                .hasSize(2)
                .containsKeys("Coinbase", "OANDA");
    }

    @Test
    @DisplayName("ExchangeService tracks adapter count")
    void testAdapterCount() {
        assertThat(service.getAdapterCount()).isZero();

        service.register("Coinbase", coinbaseAdapter);
        assertThat(service.getAdapterCount()).isEqualTo(1);

        service.register("OANDA", oandaAdapter);
        assertThat(service.getAdapterCount()).isEqualTo(2);

        service.unregister("Coinbase");
        assertThat(service.getAdapterCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("ExchangeService handles null parameters gracefully")
    void testNullParameterValidation() {
        assertThatThrownBy(() -> service.register(null, coinbaseAdapter))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.register("Coinbase", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.register("", coinbaseAdapter))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Simple stub adapter implementation for testing.
     */
    private static class StubExchangeAdapter implements ExchangeIdentity {
        private final String name;
        private final ExchangeCapability capability;

        StubExchangeAdapter(String name) {
            this.name = name;
            this.capability = ExchangeCapability.builder()
                    .exchangeName(name)
                    .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                    .build();
        }


        @Override
        public String getName() {
            return getClass().getSimpleName();
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
            return "";
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
            return null;
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
            return AuthCheckResult.builder()
                    .exchangeName(name)
                    .success(true)
                    .httpStatus(200)
                    .build();
        }


    }
}
