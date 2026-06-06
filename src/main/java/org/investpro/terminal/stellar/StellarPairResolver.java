package org.investpro.terminal.stellar;

import org.investpro.terminal.domain.Instrument;
import org.investpro.terminal.domain.InstrumentId;
import org.investpro.terminal.instrument.InstrumentMasterService;
import org.investpro.terminal.instrument.PairRelationship;

import java.util.Optional;

public final class StellarPairResolver {

    private final InstrumentMasterService instrumentMasterService;

    public StellarPairResolver(InstrumentMasterService instrumentMasterService) {
        if (instrumentMasterService == null) {
            throw new IllegalArgumentException("instrumentMasterService is required");
        }
        this.instrumentMasterService = instrumentMasterService;
    }

    public Optional<StellarPairResolution> resolve(String displaySymbol) {
        Optional<PairRelationship> relationship = instrumentMasterService.resolveEquivalentOrReversed(displaySymbol);
        if (relationship.isEmpty()) {
            return Optional.empty();
        }

        PairRelationship pairRelationship = relationship.get();
        Optional<Instrument> executionInstrument = instrumentMasterService.resolve(pairRelationship.executionInstrument());
        if (executionInstrument.isEmpty()) {
            return Optional.empty();
        }

        Instrument instrument = executionInstrument.get();
        StellarAssetMetadata base = metadataFromInstrumentAsset(instrument.baseAsset());
        StellarAssetMetadata quote = metadataFromInstrumentAsset(instrument.quoteAsset());
        StellarLiquidityScore liquidityScore = new StellarLiquidityScore(
                false,
                pairRelationship.reversed(),
                false,
                0.0,
                0.0,
                0.0,
                0,
                "LIQUIDITY_NOT_EVALUATED");
        return Optional.of(new StellarPairResolution(
                pairRelationship.displayInstrument(),
                pairRelationship.executionInstrument(),
                base,
                quote,
                pairRelationship.reversed(),
                liquidityScore,
                pairRelationship.reversed() ? "Pair is reversible; execution direction may differ." : ""));
    }

    private StellarAssetMetadata metadataFromInstrumentAsset(org.investpro.terminal.domain.Asset asset) {
        if (asset == null) {
            return null;
        }
        boolean nativeAsset = "XLM".equalsIgnoreCase(asset.code());
        return new StellarAssetMetadata(
                asset.code(),
                asset.issuer(),
                asset.homeDomain(),
                nativeAsset,
                false,
                !nativeAsset,
                new StellarIssuerProfile(asset.issuer(), asset.homeDomain(), false, false, "NOT_EVALUATED", null));
    }

    public InstrumentId displayInstrumentId(String providerId, String displaySymbol) {
        return new InstrumentId(providerId, displaySymbol, displaySymbol);
    }
}
