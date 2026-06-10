package org.investpro.spi;

import org.investpro.exchange.coinbase.Coinbase;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.contracts.CredentialProvider;
import org.investpro.exchange.factory.ExchangeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginRegistryTest {

    @Test
    void loadDefaultDiscoversBuiltInExchangeProviders() {
        PluginRegistry registry = PluginRegistry.loadDefault();

        assertThat(registry.exchangeProviders())
                .extracting(ExchangeProvider::id)
                .contains(
                        "COINBASE",
                        "OANDA",
                        "BINANCE",
                        "BINANCE_US",
                        "BITFINEX",
                        "ALPACA",
                        "INTERACTIVE_BROKERS",
                        "STELLAR_NETWORK");
    }

    @Test
    void exchangeAliasesResolveToProvider() {
        PluginRegistry registry = PluginRegistry.loadDefault();

        assertThat(registry.findExchangeProvider("Coinbase Advanced Trade"))
                .isPresent()
                .get()
                .extracting(ExchangeProvider::id)
                .isEqualTo("COINBASE");

        assertThat(registry.findExchangeProvider("binance-us"))
                .isPresent()
                .get()
                .extracting(ExchangeProvider::id)
                .isEqualTo("BINANCE_US");

        assertThat(registry.findExchangeProvider("ibkr"))
                .isPresent()
                .get()
                .extracting(ExchangeProvider::id)
                .isEqualTo("INTERACTIVE_BROKERS");

        assertThat(registry.findExchangeProvider("xlm"))
                .isPresent()
                .get()
                .extracting(ExchangeProvider::id)
                .isEqualTo("STELLAR_NETWORK");
    }

    @Test
    void duplicateIdsKeepFirstProvider() {
        ExchangeProvider first = new TestExchangeProvider("DUPLICATE", "First", Set.of("dup"));
        ExchangeProvider second = new TestExchangeProvider("duplicate", "Second", Set.of("duplicate-alias"));

        PluginRegistry registry = PluginRegistry.of(
                List.of(first, second),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertThat(registry.findExchangeProvider("duplicate"))
                .containsSame(first);
        assertThat(registry.findExchangeProvider("duplicate-alias"))
                .isEmpty();
    }

    @Test
    void exchangeFactoryCreatesCoinbaseThroughProvider() {
        ExchangeFactory factory = new ExchangeFactory(emptyCredentialProvider(), PluginRegistry.loadDefault());

        Exchange exchange = factory.create("coinbase_advanced");

        assertThat(exchange).isInstanceOf(Coinbase.class);
    }

    @Test
    void unknownExchangeThrowsClearErrorWhenNoProviderOrLegacyMatch() {
        ExchangeFactory factory = new ExchangeFactory(emptyCredentialProvider(), PluginRegistry.of(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));

        assertThatThrownBy(() -> factory.create("not-real"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown exchange");
    }

    @Test
    void indicatorProvidersAreDiscoverable() {
        PluginRegistry registry = PluginRegistry.loadDefault();

        assertThat(registry.indicatorProviders())
                .extracting(IndicatorProvider::id)
                .contains("SMA", "EMA", "RSI", "MACD", "BOLLINGER_BANDS", "ATR", "VWAP");

        assertThat(registry.findIndicatorProvider("Bollinger Bands")).isPresent();
    }

    @Test
    void strategyProvidersAreDiscoverable() {
        PluginRegistry registry = PluginRegistry.loadDefault();

        assertThat(registry.strategyProviders())
                .extracting(StrategyProvider::id)
                .contains("UNIFIED_STRATEGY", "MOVING_AVERAGE_STRATEGY", "RSI_STRATEGY", "MACD_STRATEGY");
    }

    private static CredentialProvider emptyCredentialProvider() {
        return key -> Optional.empty();
    }

    private record TestExchangeProvider(String id, String displayName, Set<String> aliases) implements ExchangeProvider {
        @Override
        public String version() {
            return "test";
        }

        @Override
        public boolean enabledByDefault() {
            return true;
        }

        @Override
        public boolean supportsPaperTrading() {
            return true;
        }

        @Override
        public boolean supportsLiveTrading() {
            return true;
        }

        @Override
        public Exchange create(ExchangeProviderContext context) {
            return null;
        }
    }
}
