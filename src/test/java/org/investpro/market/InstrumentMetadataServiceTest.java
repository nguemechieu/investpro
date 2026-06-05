package org.investpro.market;

import org.investpro.enums.AssetClass;
import org.investpro.models.trading.InstrumentMetadata;
import org.investpro.models.trading.TradePair;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentMetadataServiceTest {

    @Test
    void stellarBrokerIsAlwaysClassifiedAsCryptoAsset() throws Exception {
        InstrumentMetadataService service = new InstrumentMetadataService(new InstrumentRegistry());
        TradePair pair = new TradePair("EUR", "USD");

        InstrumentMetadata metadata = service.enrich(pair, "STELLAR NETWORK");

        assertThat(metadata.getAssetClass()).isEqualTo(AssetClass.CRYPTO_ASSET);
    }

    @Test
    void solonaBrokerIsAlwaysClassifiedAsCryptoAsset() throws Exception {
        InstrumentMetadataService service = new InstrumentMetadataService(new InstrumentRegistry());
        TradePair pair = new TradePair("EUR", "USD");

        InstrumentMetadata metadata = service.enrich(pair, "SOLONA NETWORK");

        assertThat(metadata.getAssetClass()).isEqualTo(AssetClass.CRYPTO_ASSET);
    }

    @Test
    void xlmBaseIsClassifiedAsCryptoAsset() throws Exception {
        InstrumentMetadataService service = new InstrumentMetadataService(new InstrumentRegistry());
        TradePair pair = new TradePair("XLM", "USDC");

        InstrumentMetadata metadata = service.enrich(pair, "GENERIC");

        assertThat(metadata.getAssetClass()).isEqualTo(AssetClass.CRYPTO_ASSET);
    }
}
