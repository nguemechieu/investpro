package org.investpro.exchange.coinbase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.models.currency.CurrencyRegistry;
import org.investpro.models.trading.TradePair;
import org.investpro.models.trading.TradeSymbolKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoinbaseProductSymbolParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final CoinbaseProductSymbolParser parser = new CoinbaseProductSymbolParser();

    @Test
    void cdeFutureMapsToNativeTradePairWithoutRegisteringExpiryCurrency() throws Exception {
        CurrencyRegistry registry = CurrencyRegistry.global();
        TradePair pair = parser.parseProduct("SOL-28AUG26-CDE", MAPPER.readTree("""
                {"product_id":"SOL-28AUG26-CDE","product_type":"FUTURE","contract_expiry_type":"EXPIRING"}
                """));

        assertThat(pair.getSymbolKind()).isEqualTo(TradeSymbolKind.DERIVATIVE_CONTRACT);
        assertThat(pair.getNativeSymbol()).isEqualTo("SOL-28AUG26-CDE");
        assertThat(pair.toExchangeSymbol("coinbase")).isEqualTo("SOL-28AUG26-CDE");
        assertThat(pair.toSlashSymbol()).isEqualTo("SOL-28AUG26-CDE");
        assertThat(pair.getUnderlyingCode()).isEqualTo("SOL");
        assertThat(pair.getExpiryCode()).isEqualTo("28AUG26");
        assertThat(registry.findByCode("28AUG26")).isNotPresent();
        assertThat(registry.findByCode("CDE")).isNotPresent();
    }

    @Test
    void perpetualMapsToNativeTradePair() throws Exception {
        TradePair pair = parser.parseProduct("BTC-PERP", MAPPER.readTree("""
                {"product_id":"BTC-PERP","product_type":"FUTURE","contract_expiry_type":"PERPETUAL",
                 "base_currency_id":"BTC","quote_currency_id":"USD"}
                """));

        assertThat(pair.getSymbolKind()).isEqualTo(TradeSymbolKind.PERPETUAL_CONTRACT);
        assertThat(pair.getNativeSymbol()).isEqualTo("BTC-PERP");
        assertThat(pair.toExchangeSymbol("coinbase")).isEqualTo("BTC-PERP");
        assertThat(pair.toSlashSymbol()).isEqualTo("BTC-PERP");
        assertThat(pair.isPerpetual()).isTrue();
    }

    @Test
    void spotMapsToRegularPair() throws Exception {
        TradePair pair = parser.parseProduct("ETH-USD", MAPPER.readTree("""
                {"product_id":"ETH-USD","product_type":"SPOT","base_currency_id":"ETH","quote_currency_id":"USD"}
                """));

        assertThat(pair.getSymbolKind()).isEqualTo(TradeSymbolKind.PAIR);
        assertThat(pair.toSlashSymbol()).isEqualTo("ETH/USD");
        assertThat(pair.toExchangeSymbol("coinbase")).isEqualTo("ETH-USD");
    }
}
