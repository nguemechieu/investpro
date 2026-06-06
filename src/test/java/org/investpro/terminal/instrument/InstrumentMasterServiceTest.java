package org.investpro.terminal.instrument;

import org.investpro.terminal.domain.Asset;
import org.investpro.terminal.domain.AssetClass;
import org.investpro.terminal.domain.Instrument;
import org.investpro.terminal.domain.InstrumentId;
import org.investpro.terminal.domain.TradingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstrumentMasterServiceTest {

    @Test
    void resolvesReversedPairWhenInstrumentIsMarkedReversible() {
        InstrumentMasterService service = new InstrumentMasterService();
        Asset btc = new Asset("BTC", "Bitcoin", AssetClass.CRYPTO, "GISSUERBTC", "example.com");
        Asset usdc = new Asset("USDC", "USD Coin", AssetClass.CRYPTO_STELLAR, "GISSUERUSDC", "circle.com");
        Instrument instrument = new Instrument(
                new InstrumentId("stellar", "BTC/USDC", "BTC/USDC"),
                btc,
                usdc,
                "BTC/USDC",
                AssetClass.CRYPTO_STELLAR,
                "stellar",
                new BigDecimal("0.0000001"),
                new BigDecimal("0.0000001"),
                new BigDecimal("0.0000001"),
                new BigDecimal("0.0000001"),
                new BigDecimal("0.0000001"),
                TradingStatus.ACTIVE,
                false,
                false,
                true,
                true,
                Map.of());

        service.register(instrument);

        PairRelationship relationship = service.resolveEquivalentOrReversed("USDC/BTC").orElseThrow();

        assertTrue(relationship.reversed());
        assertEquals("STELLAR:BTC/USDC", relationship.executionInstrument().key());
        assertEquals("STELLAR:USDC/BTC", relationship.displayInstrument().key());
    }
}
