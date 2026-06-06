package org.investpro.terminal.stellar;

import org.investpro.terminal.domain.InstrumentId;

public record StellarPairResolution(
        InstrumentId displayInstrument,
        InstrumentId executionInstrument,
        StellarAssetMetadata base,
        StellarAssetMetadata quote,
        boolean reversed,
        StellarLiquidityScore liquidityScore,
        String warning
) {
    public StellarPairResolution {
        if (displayInstrument == null || executionInstrument == null) {
            throw new IllegalArgumentException("display and execution instruments are required");
        }
        warning = warning == null ? "" : warning.trim();
    }
}
