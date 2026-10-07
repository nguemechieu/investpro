package org.investpro.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.exchange.coinbase.CoinbaseMarketInstrumentMapper;
import org.investpro.models.market.MarketCategory;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class AssetCatalogDerivativeTest {
    @Test void cachedFutureWithoutQuoteRestoresNativeIdentityAndContract() throws Exception {
        var mapper = new CoinbaseMarketInstrumentMapper();
        var instrument = mapper.map(new ObjectMapper().readTree("""
                {"product_id":"BTC-25SEP26-CDE","product_type":"FUTURE","display_name":"Bitcoin September",
                 "base_currency_id":"BTC","future_product_details":{"contract_expiry_type":"EXPIRING"}}
                """));
        var entry = AssetCatalogEntry.fromMarketInstrument(ExchangeId.COINBASE, instrument, Instant.now());
        assertEquals("BTC-25SEP26-CDE", entry.symbol());
        var restored = entry.toTradePair();
        assertEquals("BTC-25SEP26-CDE", restored.toSlashSymbol());
        assertTrue(restored.isDerivativeContract());
        assertEquals(MarketCategory.DERIVATIVES, restored.getMarketType().category());
        assertEquals("COINBASE_DERIVATIVES", restored.getProductVenue());
        var second = AssetCatalogEntry.fromTradePair(ExchangeId.COINBASE, restored, Instant.now());
        assertEquals(AssetType.FUTURE, second.assetType());
        assertEquals("BTC-25SEP26-CDE", second.toTradePair().toSlashSymbol());
    }

    @Test void cachedInternationalPerpetualKeepsItsVenue() throws Exception {
        var instrument = new CoinbaseMarketInstrumentMapper().map(new ObjectMapper().readTree("""
                {"product_id":"BTC-PERP-INTX","product_type":"FUTURE","product_venue":"INTX",
                 "base_currency_id":"BTC","quote_currency_id":"USDC",
                 "future_product_details":{"contract_expiry_type":"PERPETUAL"}}
                """));
        var pair = AssetCatalogEntry.fromMarketInstrument(ExchangeId.COINBASE, instrument, Instant.now()).toTradePair();
        assertTrue(pair.isPerpetual());
        assertEquals("BTC-PERP-INTX", pair.toSlashSymbol());
        assertEquals("INTX", pair.getProductVenue());
    }
}
