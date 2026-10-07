package org.investpro.models.market;

/** Independent browser filters. Null means all values; unknown metadata is never guessed. */
public record MarketSelection(MarketCategory category, ContractType contract, AssetClass asset) {
    public boolean matches(MarketInstrument instrument) {
        return instrument != null && (category == null || category == instrument.marketCategory())
                && (contract == null || contract == instrument.contractType())
                && (asset == null || asset == instrument.assetClass());
    }
}
