package org.investpro.ui.market;

import org.investpro.models.market.MarketInstrument;
import org.investpro.models.market.AssetClass;
import org.investpro.models.market.ContractType;
import org.investpro.models.market.MarketType;
import org.investpro.models.market.TradingEnvironment;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.TradabilityStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarketWatchProductFilterTest {

    @Test
    void spotFilterReturnsOnlySpot() {
        assertTrue(MarketWatchProductFilter.SPOT.accepts(instrument(MarketType.SPOT, null)));
        assertFalse(MarketWatchProductFilter.SPOT.accepts(
                instrument(AssetClass.CRYPTO, MarketType.DERIVATIVES, ContractType.PERPETUAL, null)));
    }

    @Test
    void perpetualsReturnsOnlyPerpetuals() {
        assertTrue(MarketWatchProductFilter.PERPETUALS.accepts(
                instrument(AssetClass.CRYPTO, MarketType.PERPETUAL, ContractType.PERPETUAL, null)));
        assertFalse(MarketWatchProductFilter.PERPETUALS.accepts(instrument(MarketType.SPOT, null)));
        assertFalse(MarketWatchProductFilter.PERPETUALS.accepts(
                instrument(AssetClass.CRYPTO, MarketType.DERIVATIVES, ContractType.FUTURE, null)));
    }

    @Test
    void futuresReturnsOnlyExpiringFuturesNotPerpetuals() {
        assertTrue(MarketWatchProductFilter.FUTURES.accepts(
                instrument(AssetClass.CRYPTO, MarketType.DERIVATIVES, ContractType.FUTURE, null)));
        assertFalse(MarketWatchProductFilter.FUTURES.accepts(
                instrument(AssetClass.CRYPTO, MarketType.PERPETUAL, ContractType.PERPETUAL, null)));
        assertFalse(MarketWatchProductFilter.FUTURES.accepts(instrument(MarketType.SPOT, null)));
    }

    @Test
    void tradableOnlyUsesSymbolTradability() {
        assertTrue(MarketWatchProductFilter.TRADABLE_ONLY.accepts(
                instrument(MarketType.SPOT, tradability(TradabilityStatus.FULLY_TRADABLE, true))));
        assertFalse(MarketWatchProductFilter.TRADABLE_ONLY.accepts(
                instrument(MarketType.SPOT, tradability(TradabilityStatus.VIEW_ONLY, false))));
    }

    @Test
    void restrictedOnlyShowsViewOnlyOrRestricted() {
        assertTrue(MarketWatchProductFilter.RESTRICTED_ONLY.accepts(
                instrument(MarketType.SPOT, tradability(TradabilityStatus.VIEW_ONLY, false))));
        assertFalse(MarketWatchProductFilter.RESTRICTED_ONLY.accepts(
                instrument(MarketType.SPOT, tradability(TradabilityStatus.FULLY_TRADABLE, true))));
    }

    @Test
    void viewOnlyDerivativeIsVisibleButNotBotEligible() {
        MarketInstrument instrument = instrument(
                AssetClass.CRYPTO,
                MarketType.DERIVATIVES,
                ContractType.FUTURE,
                tradability(TradabilityStatus.VIEW_ONLY, false));

        assertTrue(instrument.canShowInMarketWatch());
        assertFalse(instrument.canBotTrade());
        assertTrue(MarketWatchProductFilter.RESTRICTED_ONLY.accepts(instrument));
    }

    private MarketInstrument instrument(MarketType type, SymbolTradability tradability) {
        return instrument(AssetClass.UNKNOWN, type, type == MarketType.SPOT ? ContractType.CASH : ContractType.UNKNOWN,
                tradability);
    }

    private MarketInstrument instrument(
            AssetClass assetClass,
            MarketType type,
            ContractType contractType,
            SymbolTradability tradability) {
        return new MarketInstrument(
                "TEST",
                type.name(),
                type.name(),
                null,
                assetClass,
                type,
                contractType,
                "",
                null,
                null,
                TradingEnvironment.UNKNOWN,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                null,
                type != MarketType.SPOT,
                false,
                type == MarketType.SPOT,
                type != MarketType.SPOT,
                tradability,
                null);
    }

    private SymbolTradability tradability(TradabilityStatus status, boolean allowed) {
        return new SymbolTradability(
                "TEST",
                null,
                "TEST",
                status,
                true,
                true,
                true,
                true,
                allowed,
                allowed,
                allowed,
                allowed,
                allowed,
                false,
                false,
                false,
                false,
                allowed ? "Tradable" : "Restricted",
                Instant.now(),
                Map.of());
    }
}
