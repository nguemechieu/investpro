package org.investpro.trading.market;

import org.investpro.models.trading.TradePair;
import org.investpro.models.market.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OandaInstrumentPolicyTest {
    @Test
    void instrumentLoadingWaitsForAccountTradability() {
        var oanda = mock(org.investpro.exchange.oanda.Oanda.class);
        var pair = pair();
        var status = mock(org.investpro.trading.tradability.SymbolTradability.class);
        when(status.tradePair()).thenReturn(pair);
        when(oanda.getTradePairSymbol()).thenReturn(java.util.List.of(pair));
        when(oanda.instrumentSpecification(pair)).thenReturn(Map.of("type", "METAL", "name", "XAU_USD"));
        var pending = new java.util.concurrent.CompletableFuture<java.util.List<org.investpro.trading.tradability.SymbolTradability>>();
        when(oanda.fetchTradabilityStatus(java.util.List.of(pair))).thenReturn(pending);
        when(oanda.fetchMarketInstruments()).thenCallRealMethod();
        var result = oanda.fetchMarketInstruments();
        verify(oanda, timeout(2000)).fetchTradabilityStatus(java.util.List.of(pair));
        assertFalse(result.isDone());
        pending.complete(java.util.List.of(status));
        assertSame(status, result.join().getFirst().tradability());
        assertEquals(AssetClass.METAL, result.join().getFirst().assetClass());
    }

    @Test
    void mapsAllBrokerTypesWithoutTreatingThemAsSpot() {
        for (String type : new String[]{"CURRENCY", "METAL", "CFD"}) {
            var specification = Map.<String, Object>of("name", "NATIVE_USD", "type", type,
                    "marginRate", "0.05", "tradeUnitsPrecision", 2);
            var instrument = OandaInstrumentPolicy.map(pair(), specification, null);
            assertEquals(MarketType.DERIVATIVES, instrument.marketType());
            assertEquals(LeverageMode.MARGIN, instrument.leverageMode());
            assertEquals(type.equals("CURRENCY") ? InstrumentType.FOREX : InstrumentType.CFD, instrument.instrumentType());
            assertEquals(type.equals("CURRENCY") ? AssetClass.FIAT : type.equals("METAL") ? AssetClass.METAL : AssetClass.UNKNOWN,
                    instrument.assetClass());
            assertEquals(specification, instrument.rawMetadata());
            assertFalse(instrument.physicallySettled());
            assertEquals("NATIVE_USD", instrument.nativeSymbol());
        }
    }

    @Test
    void unknownTypesDoNotBecomeSpotOrMarginProducts() {
        var instrument = OandaInstrumentPolicy.map(pair(), Map.of(), null);
        assertEquals(InstrumentType.UNKNOWN, instrument.instrumentType());
        assertEquals(ContractType.UNKNOWN, instrument.contractType());
        assertFalse(instrument.leveraged());
    }

    @Test
    void fxOnboardingRetainsMarginClassification() {
        var selection = new org.investpro.ui.theme.MarketConfiguration("", "Margin FX", "OANDA", "oanda",
                "", "", "", "", "", null, null, "PAPER", Map.of("asset_class", "FIAT"));
        assertEquals(InstrumentType.FOREX, selection.normalizedInstrumentType());
        assertEquals(ContractType.MARGIN, selection.normalizedContractType());
        assertEquals(LeverageMode.MARGIN, selection.normalizedLeverageMode());
        assertEquals(ProductVenue.OANDA, selection.normalizedVenue());
    }

    private TradePair pair() {
        TradePair pair = mock(TradePair.class);
        when(pair.getBaseCode()).thenReturn("NATIVE");
        when(pair.getCounterCode()).thenReturn("USD");
        when(pair.toString('/')).thenReturn("NATIVE/USD");
        return pair;
    }
}
