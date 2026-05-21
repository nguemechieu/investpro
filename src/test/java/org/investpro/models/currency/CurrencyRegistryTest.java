package org.investpro.models.currency;

import org.investpro.models.currency.spi.CurrencyProvider;
import org.investpro.models.trading.TradePair;
import org.investpro.ui.tools.MoneyAxisFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Currency Registry Tests")
class CurrencyRegistryTest {

    @Test
    @DisplayName("loadDefault discovers grouped providers and core assets")
    void loadDefaultDiscoversProvidersAndCoreAssets() {
        CurrencyRegistry registry = CurrencyRegistry.loadDefault();

        assertThat(registry.size()).isGreaterThan(10);
        assertThat(registry.findByCode("USD")).isPresent();
        assertThat(registry.findByCode("EUR")).isPresent();
        assertThat(registry.findByCode("BTC")).isPresent();
        assertThat(registry.findByCode("ETH")).isPresent();
        assertThat(registry.findByCode("XAU")).isPresent();
    }

    @Test
    @DisplayName("findOrUnknown returns UnknownCurrency instead of throwing")
    void unknownCurrencyReturnsUnknownCurrency() {
        CurrencyRegistry registry = CurrencyRegistry.loadDefault();
        Currency unknown = registry.findOrUnknown("NEWCOIN");

        assertThat(unknown).isInstanceOf(UnknownCurrency.class);
        assertThat(unknown.getCode()).isEqualTo("NEWCOIN");
    }

    @Test
    @DisplayName("duplicate currency codes keep first and do not crash")
    void duplicateCurrencyCodesKeepFirst() {
        CurrencyRegistry registry = CurrencyRegistry.loadDefault();

        Currency original = registry.findByCode("USD").orElseThrow();
        registry.registerProvider(new DuplicateUsdProvider());

        Currency stillOriginal = registry.findByCode("USD").orElseThrow();
        assertThat(stillOriginal).isSameAs(original);
    }

    @Test
    @DisplayName("icon path fallback always returns default when missing")
    void iconPathFallbackWorks() {
        CurrencyRegistry registry = CurrencyRegistry.loadDefault();

        String icon = registry.iconPathOrDefault("DOES_NOT_EXIST");
        assertThat(icon).isEqualTo("/icons/currencies/default.svg");
    }

    @Test
    @DisplayName("TradePair parsing handles common delimiters")
    void tradePairParsingHandlesDelimiters() throws Exception {
        TradePair p1 = TradePair.fromSymbol("BTC-USD");
        TradePair p2 = TradePair.fromSymbol("EUR_USD");
        TradePair p3 = TradePair.fromSymbol("EUR/USD");
        TradePair p4 = TradePair.fromSymbol("XAU_USD");

        assertThat(p1.getBaseCode()).isEqualTo("BTC");
        assertThat(p1.getCounterCode()).isEqualTo("USD");
        assertThat(p2.getBaseCode()).isEqualTo("EUR");
        assertThat(p3.getCounterCode()).isEqualTo("USD");
        assertThat(p4.getBaseCode()).isEqualTo("XAU");
    }

    @Test
    @DisplayName("MoneyAxisFormatter formats fiat symbol and crypto code")
    void moneyAxisFormatterFormatting() {
        CurrencyRegistry registry = CurrencyRegistry.loadDefault();

        Currency usd = registry.findOrUnknown("USD");
        Currency btc = registry.findOrUnknown("BTC");

        MoneyAxisFormatter fiatFormatter = new MoneyAxisFormatter(usd);
        MoneyAxisFormatter cryptoFormatter = new MoneyAxisFormatter(btc);

        String usdFormatted = fiatFormatter.toString(1234.56);
        String btcFormatted = cryptoFormatter.toString(0.0025);

        assertThat(usdFormatted).contains("$");
        assertThat(btcFormatted).contains("BTC");
        assertThat(cryptoFormatter.fromString("0.0025 BTC")).isEqualTo(new BigDecimal("0.0025"));
        assertThat(fiatFormatter.fromString("$1,234.56")).isEqualTo(new BigDecimal("1234.56"));
    }

    @Test
    @DisplayName("global registry is memoized")
    void globalRegistryMemoized() {
        CurrencyRegistry first = CurrencyRegistry.global();
        CurrencyRegistry second = CurrencyRegistry.global();

        assertThat(first).isSameAs(second);
    }

    private static final class DuplicateUsdProvider implements CurrencyProvider {

        @Override
        public String providerId() {
            return "DUPLICATE";
        }

        @Override
        public String displayName() {
            return "Duplicate Test Provider";
        }

        @Override
        public Set<String> supportedCurrencyTypes() {
            return Set.of("FIAT");
        }

        @Override
        public Collection<Currency> getCurrencies() {
            return List.of(new SyntheticCurrency(CurrencyType.FIAT, "USD Duplicate", "USD Duplicate", "USD", 2, "$", "USD"));
        }
    }
}
