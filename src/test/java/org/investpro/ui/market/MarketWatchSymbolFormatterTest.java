package org.investpro.ui.market;

import org.investpro.models.market.AssetClass;
import org.investpro.models.market.ContractExpiryType;
import org.investpro.models.market.ContractType;
import org.investpro.models.market.MarketInstrument;
import org.investpro.models.market.MarketType;
import org.investpro.models.market.TradingEnvironment;
import org.investpro.models.market.UnderlyingType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketWatchSymbolFormatterTest {

    @Test
    void derivativeDisplayUsesNativeProductSymbol() {
        MarketInstrument instrument = instrument(
                "BTC-PERP",
                "BTC Perpetual",
                MarketType.DERIVATIVES,
                ContractType.PERPETUAL);

        assertEquals("BTC-PERP", MarketWatchSymbolFormatter.displaySymbol(instrument, null));
    }

    @Test
    void spotDisplayUsesReadablePairSymbol() {
        MarketInstrument instrument = instrument(
                "BTC-USD",
                "BTC/USD",
                MarketType.SPOT,
                ContractType.CASH);

        assertEquals("BTC/USD", MarketWatchSymbolFormatter.displaySymbol(instrument, null));
    }

    @Test
    void baseAssetHandlesDerivativeProductIds() {
        assertEquals("BTC", MarketWatchSymbolFormatter.baseAsset("BTC-PERP"));
    }

    private MarketInstrument instrument(
            String nativeSymbol,
            String displaySymbol,
            MarketType marketType,
            ContractType contractType) {
        return new MarketInstrument(
                "COINBASE",
                nativeSymbol,
                displaySymbol,
                null,
                AssetClass.CRYPTO,
                marketType,
                contractType,
                "",
                contractType == ContractType.PERPETUAL ? ContractExpiryType.PERPETUAL : ContractExpiryType.UNKNOWN,
                UnderlyingType.SPOT,
                TradingEnvironment.UNKNOWN,
                "",
                "BTC",
                "USD",
                "USD",
                "BTC",
                "",
                "",
                null,
                contractType.isDerivative(),
                false,
                marketType == MarketType.SPOT,
                contractType.isDerivative(),
                null,
                Map.of());
    }
}
