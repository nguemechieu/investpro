package org.investpro.exchange.coinbase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.models.market.AssetClass;
import org.investpro.models.market.ContractExpiryType;
import org.investpro.models.market.ContractType;
import org.investpro.models.market.InstrumentType;
import org.investpro.models.market.LeverageMode;
import org.investpro.models.market.MarketInstrument;
import org.investpro.models.market.MarketType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoinbaseMarketInstrumentMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final CoinbaseMarketInstrumentMapper mapper = new CoinbaseMarketInstrumentMapper();

    @Test void datedGoldFuturePreservesNativeIdAndExpiry() throws Exception {
        var instrument = mapper.map(MAPPER.readTree("""
                {"product_id":"GOL-25NOV26-CDE","product_type":"FUTURE",
                 "future_product_details":{"contract_expiry_type":"EXPIRING","futures_asset_type":"COMMODITY"}}
                """));
        assertEquals(ContractType.FUTURE, instrument.contractType());
        assertEquals(AssetClass.COMMODITY, instrument.assetClass());
        assertEquals("GOL-25NOV26-CDE", instrument.displaySymbol());
        assertEquals("GOL-25NOV26-CDE", instrument.tradePair().toExchangeSymbol("coinbase"));
        assertEquals(java.time.Instant.parse("2026-11-25T00:00:00Z"), instrument.contractExpiry());
        assertEquals("COINBASE_DERIVATIVES", instrument.routingExchange());
    }

    @Test void datedCdePerpetualRetainsExpiryMetadataAndUsVenue() throws Exception {
        var instrument = mapper.map(MAPPER.readTree("""
                {"product_id":"BIP-20DEC30-CDE","product_type":"FUTURE","base_currency_id":"BTC",
                 "future_product_details":{"contract_expiry_type":"EXPIRING","funding_rate":"0",
                 "futures_asset_type":"CRYPTO"}}
                """));
        assertEquals(ContractType.PERPETUAL, instrument.contractType());
        assertEquals(InstrumentType.PERPETUAL, instrument.instrumentType());
        assertEquals(ContractExpiryType.EXPIRING, instrument.contractExpiryType());
        assertEquals("BIP-20DEC30-CDE", instrument.displaySymbol());
        assertEquals("BIP-20DEC30-CDE", instrument.tradePair().toSlashSymbol());
        assertTrue(instrument.tradePair().isPerpetual());
        assertEquals(java.time.Instant.parse("2030-12-20T00:00:00Z"), instrument.contractExpiry());
        assertEquals("COINBASE_DERIVATIVES", instrument.tradePair().getProductVenue());
        assertEquals(org.investpro.models.market.ProductVenue.COINBASE_DERIVATIVES,
                org.investpro.ui.theme.MarketConfiguration.venueFor(instrument));
        assertTrue(new org.investpro.models.market.MarketSelection(null, ContractType.PERPETUAL, AssetClass.CRYPTO).matches(instrument));
        assertFalse(new org.investpro.models.market.MarketSelection(null, ContractType.FUTURE, null).matches(instrument));
    }

    @Test void cdePerpetualRootWorksWithoutMetadataAndFundingSupportsNewRoots() throws Exception {
        var parser = new CoinbaseProductSymbolParser();
        assertTrue(parser.parseProduct("BIP-20DEC30-CDE", null).isPerpetual());
        assertFalse(parser.parseProduct("GOL-25NOV26-CDE", null).isPerpetual());
        var instrument = mapper.map(MAPPER.readTree("""
                {"product_id":"NEW-20DEC30-CDE","product_type":"FUTURE",
                 "future_product_details":{"contract_expiry_type":"EXPIRING","funding_rate":"0"}}
                """));
        assertEquals(ContractType.PERPETUAL, instrument.contractType());
        assertTrue(instrument.tradePair().isPerpetual());
    }

    @Test void nativePerpetualIdAndExplicitVenueArePreserved() throws Exception {
        var instrument = mapper.map(MAPPER.readTree("""
                {"product_id":"BTC-PERP-INTX","product_type":"FUTURE","product_venue":"INTX",
                 "display_name":"Bitcoin Perpetual","base_currency_id":"BTC","quote_currency_id":"USDC",
                 "future_product_details":{"contract_expiry_type":"PERPETUAL"}}
                """));
        assertEquals("BTC-PERP-INTX", instrument.displaySymbol());
        assertEquals("BTC-PERP-INTX", instrument.tradePair().toSlashSymbol());
        assertEquals("INTX", instrument.routingExchange());
        assertEquals("INTX", instrument.tradePair().getProductVenue());
        assertEquals(org.investpro.models.market.MarketCategory.DERIVATIVES, instrument.marketCategory());
        assertTrue(new org.investpro.models.market.MarketSelection(null, ContractType.PERPETUAL, AssetClass.CRYPTO).matches(instrument));
    }

    @Test void futureContractAndUnderlyingAreIndependentAndMetadataOverridesPairFormat() throws Exception {
        var instrument = mapper.map(MAPPER.readTree("""
                {"product_id":"TEST-FUT","product_type":"FUTURE","product_venue":"FCM",
                 "display_name":"Index future","futures_underlying_type":"INDEX",
                 "base_currency_id":"TEST","quote_currency_id":"USD","contract_expiry_type":"EXPIRING"}
                """));
        assertEquals(InstrumentType.FUTURE, instrument.instrumentType());
        assertEquals(AssetClass.INDEX, instrument.assetClass());
        assertEquals("TEST-FUT", instrument.tradePair().toSlashSymbol());
        assertEquals("TEST-FUT", instrument.displaySymbol());
        assertEquals("FCM", instrument.routingExchange());
        var selection = new org.investpro.models.market.MarketSelection(org.investpro.models.market.MarketCategory.DERIVATIVES, ContractType.FUTURE, AssetClass.INDEX);
        assertTrue(selection.matches(instrument));
        assertFalse(new org.investpro.models.market.MarketSelection(org.investpro.models.market.MarketCategory.SPOT, null, null).matches(instrument));
    }

    @Test void expiriesOfSameUnderlyingRemainDistinct() throws Exception {
        var first = mapper.map(MAPPER.readTree("{\"product_id\":\"BTC-26JUN26-CDE\",\"product_type\":\"FUTURE\"}"));
        var second = mapper.map(MAPPER.readTree("{\"product_id\":\"BTC-25SEP26-CDE\",\"product_type\":\"FUTURE\"}"));
        assertNotEquals(first.tradePair().toSlashSymbol(), second.tradePair().toSlashSymbol());
        assertEquals(2, java.util.stream.Stream.of(first.tradePair(), second.tradePair()).distinct().count());
        assertTrue(MarketType.DERIVATIVES.isDerivative());
        assertEquals(org.investpro.models.market.MarketCategory.DERIVATIVES, MarketType.PERPETUAL.category());
    }

    @Test
    void spotProductMapsToSpot() throws Exception {
        MarketInstrument instrument = mapper.map(MAPPER.readTree("""
                {"product_id":"BTC-USD","product_type":"SPOT","base_currency_id":"BTC","quote_currency_id":"USD"}
                """));

        assertEquals(MarketType.SPOT, instrument.marketType());
        assertEquals(InstrumentType.SPOT, instrument.instrumentType());
        assertEquals(LeverageMode.NONE, instrument.leverageMode());
        assertEquals(AssetClass.CRYPTO, instrument.assetClass());
        assertEquals(ContractType.CASH, instrument.contractType());
        assertEquals("", instrument.routingExchange());
        assertNotNull(instrument.tradePair());
        assertEquals("BTC-USD", instrument.nativeSymbol());
    }

    @Test
    void futurePerpetualMapsToPerpetual() throws Exception {
        MarketInstrument instrument = mapper.map(MAPPER.readTree("""
                {"product_id":"BTC-PERP","product_type":"FUTURE","contract_expiry_type":"PERPETUAL",
                 "base_currency_id":"BTC","quote_currency_id":"USD"}
                """));

        assertEquals(MarketType.DERIVATIVES, instrument.marketType());
        assertEquals(InstrumentType.PERPETUAL, instrument.instrumentType());
        assertEquals(LeverageMode.PERPETUAL, instrument.leverageMode());
        assertEquals(AssetClass.CRYPTO, instrument.assetClass());
        assertEquals(ContractType.PERPETUAL, instrument.contractType());
        assertEquals("", instrument.routingExchange());
        assertTrue(instrument.isPerpetual());
        assertNotNull(instrument.tradePair());
        assertEquals("BTC-PERP", instrument.tradePair().getNativeSymbol());
        assertEquals("BTC-PERP", instrument.tradePair().toSlashSymbol());
    }

    @Test
    void usDerivativesVenueMapsToCoinbaseDerivatives() throws Exception {
        MarketInstrument instrument = mapper.map(MAPPER.readTree("""
                {"product_id":"BIT-28JUL23-CDE","product_type":"FUTURE","contract_expiry_type":"EXPIRING",
                 "futures_underlying_type":"INDEX",
                 "future_product_details":{"venue":"US Derivatives","contract_code":"BIT-28JUL23-CDE"}}
                """));

        assertEquals(MarketType.DERIVATIVES, instrument.marketType());
        assertEquals(InstrumentType.FUTURE, instrument.instrumentType());
        assertEquals(LeverageMode.DERIVATIVE_LEVERAGE, instrument.leverageMode());
        assertEquals(AssetClass.INDEX, instrument.assetClass());
        assertEquals(ContractType.FUTURE, instrument.contractType());
        assertEquals("COINBASE_DERIVATIVES", instrument.routingExchange());
        assertTrue(instrument.isDerivative());
        assertNotNull(instrument.tradePair());
        assertEquals("BIT-28JUL23-CDE", instrument.tradePair().getNativeSymbol());
        assertEquals("BIT-28JUL23-CDE", instrument.tradePair().toSlashSymbol());
    }

    @Test
    void cdeContractPreservesNativeSymbolWithoutTradePair() throws Exception {
        MarketInstrument instrument = mapper.map(MAPPER.readTree("""
                {"product_id":"DOG-26JUN26-CDE","product_type":"FUTURE","contract_expiry_type":"EXPIRING",
                 "view_only":true,
                 "future_product_details":{"contract_code":"DOG-26JUN26-CDE","contract_size":"1"}}
                """));

        assertEquals("DOG-26JUN26-CDE", instrument.nativeSymbol());
        assertEquals("DOG", instrument.underlyingAsset());
        assertEquals("DOG", instrument.baseAsset());
        assertEquals("", instrument.quoteAsset());
        assertEquals(MarketType.DERIVATIVES, instrument.marketType());
        assertEquals(InstrumentType.FUTURE, instrument.instrumentType());
        assertEquals(ContractType.FUTURE, instrument.contractType());
        assertEquals(ContractExpiryType.EXPIRING, instrument.contractExpiryType());
        assertEquals("COINBASE_DERIVATIVES", instrument.routingExchange());
        assertNotNull(instrument.contractExpiry());
        assertNotNull(instrument.tradePair());
        assertEquals("DOG-26JUN26-CDE", instrument.tradePair().getNativeSymbol());
        assertEquals("DOG-26JUN26-CDE", instrument.tradePair().toSlashSymbol());
    }

    @Test
    void futureUnderlyingTypesMapToAssetClasses() throws Exception {
        assertEquals(AssetClass.INDEX, mapFuture("INDEX").assetClass());
        assertEquals(AssetClass.EQUITY, mapFuture("EQUITY").assetClass());
        assertEquals(AssetClass.COMMODITY, mapFuture("COMMODITY").assetClass());
        assertEquals(AssetClass.FIAT, mapFuture("FX").assetClass());
        assertEquals(ContractType.FUTURE, mapFuture("INDEX").contractType());
        assertEquals(MarketType.DERIVATIVES, mapFuture("INDEX").marketType());
        assertEquals(InstrumentType.FUTURE, mapFuture("INDEX").instrumentType());
    }

    @Test
    void missingProductTypeMapsToUnknown() throws Exception {
        MarketInstrument instrument = mapper.map(MAPPER.readTree("""
                {"product_id":"MYSTERY"}
                """));

        assertEquals(MarketType.UNKNOWN, instrument.marketType());
    }

    @Test
    void missingBaseQuoteDoesNotCrashAndPreservesRawMetadata() throws Exception {
        MarketInstrument instrument = mapper.map(MAPPER.readTree("""
                {"product_id":"BIT-28JUL23-CDE","product_type":"FUTURE",
                 "future_product_details":{"contract_code":"BIT-28JUL23-CDE","contract_size":"1"}}
                """));

        assertNotNull(instrument.tradePair());
        assertEquals("BIT-28JUL23-CDE", instrument.tradePair().getNativeSymbol());
        assertEquals("BIT-28JUL23-CDE", instrument.nativeSymbol());
        assertTrue(instrument.rawMetadata().containsKey("future_product_details"));
    }

    private MarketInstrument mapFuture(String underlyingType) throws Exception {
        return mapper.map(MAPPER.readTree("""
                {"product_id":"TEST-FUT","product_type":"FUTURE","contract_expiry_type":"EXPIRING",
                 "futures_underlying_type":"%s","base_currency_id":"BTC","quote_currency_id":"USD"}
                """.formatted(underlyingType)));
    }
}
