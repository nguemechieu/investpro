package org.investpro.exchange.coinbase;

import org.investpro.exchange.core.BrokerVenue;
import org.investpro.exchange.core.InstrumentType;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CoinbaseInstrumentClassifierTest {
    private final CoinbaseInstrumentClassifier classifier = new CoinbaseInstrumentClassifier();

    @Test void datedContractIsNotMisclassifiedAsSpotAndBareTickerIsNotGuessedAsFuture() {
        var future = classifier.classify("BTC-25SEP26-CDE", Map.of("futures_underlying_type", "SPOT"));
        assertEquals(InstrumentType.CRYPTO_FUTURE, future.instrumentType());
        assertEquals(BrokerVenue.COINBASE_US_FUTURES, future.venue());
        assertEquals(BrokerVenue.UNKNOWN, classifier.classify("BTC", Map.of()).venue());
    }

    @Test void explicitUnderlyingAndVenueDriveClassification() {
        var index = classifier.classify("INDEX-PERP-INTX", Map.of("product_type", "FUTURE", "contract_expiry_type", "PERPETUAL", "futures_underlying_type", "INDEX", "product_venue", "INTX"));
        assertEquals(InstrumentType.INDEX_PERPETUAL, index.instrumentType());
        assertEquals(BrokerVenue.COINBASE_INTERNATIONAL_PERPETUALS, index.venue());
    }
}
