package org.investpro.strategy;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.market.AssetClass;
import org.investpro.models.market.ContractType;
import org.investpro.models.market.InstrumentType;
import org.investpro.models.market.MarketInstrument;
import org.investpro.models.market.MarketType;

import java.util.Set;

@Getter
@Builder
@Slf4j
public class StrategyDefinition {

    private final String name;
    private final String baseName;

    @Builder.Default
    private final StrategyParameters parameters = StrategyParameters.builder().build();

    @Builder.Default
    private final Set<StrategyMarketCompatibility> marketCompatibility = Set.of(StrategyMarketCompatibility.ALL);

    public boolean supports(MarketInstrument instrument) {
        if (instrument == null || instrument.marketType() == MarketType.UNKNOWN) {
            return false;
        }
        Set<StrategyMarketCompatibility> compatibility = marketCompatibility == null || marketCompatibility.isEmpty()
                ? Set.of(StrategyMarketCompatibility.ALL)
                : marketCompatibility;
        if (compatibility.contains(StrategyMarketCompatibility.ALL)) {
            return true;
        }
        if (instrument.isDerivative() && compatibility.contains(StrategyMarketCompatibility.DERIVATIVE)) {
            return true;
        }
        if (supportsContract(instrument.contractType(), compatibility)) {
            return true;
        }
        if (supportsInstrument(instrument.instrumentType(), compatibility)) {
            return true;
        }
        if (supportsAsset(instrument.assetClass(), compatibility)) {
            return true;
        }
        return supportsMarket(instrument.marketType(), compatibility);
    }

    private boolean supportsInstrument(
            InstrumentType instrumentType,
            Set<StrategyMarketCompatibility> compatibility) {
        return switch (instrumentType == null ? InstrumentType.UNKNOWN : instrumentType) {
            case FUTURE -> compatibility.contains(StrategyMarketCompatibility.FUTURE);
            case PERPETUAL -> compatibility.contains(StrategyMarketCompatibility.PERPETUAL);
            case OPTION, FORWARD, SWAP, CFD, CRYPTO_SWAP -> compatibility.contains(StrategyMarketCompatibility.DERIVATIVE);
            case FOREX -> compatibility.contains(StrategyMarketCompatibility.FX);
            case STOCK, ETF, FUND, WARRANT -> compatibility.contains(StrategyMarketCompatibility.EQUITY);
            case INDEX -> compatibility.contains(StrategyMarketCompatibility.INDEX);
            case COMMODITY, BOND -> compatibility.contains(StrategyMarketCompatibility.COMMODITY);
            case SPOT, UNKNOWN -> false;
        };
    }

    private boolean supportsContract(
            ContractType contractType,
            Set<StrategyMarketCompatibility> compatibility) {
        return switch (contractType == null ? ContractType.UNKNOWN : contractType) {
            case FUTURE -> compatibility.contains(StrategyMarketCompatibility.FUTURE);
            case PERPETUAL -> compatibility.contains(StrategyMarketCompatibility.PERPETUAL);
            case OPTION, FORWARD, SWAP, CFD -> compatibility.contains(StrategyMarketCompatibility.DERIVATIVE);
            case CASH, MARGIN, NONE, UNKNOWN -> false;
        };
    }

    private boolean supportsAsset(
            AssetClass assetClass,
            Set<StrategyMarketCompatibility> compatibility) {
        return switch (assetClass == null ? AssetClass.UNKNOWN : assetClass) {
            case EQUITY, ETF, FUND -> compatibility.contains(StrategyMarketCompatibility.EQUITY);
            case INDEX -> compatibility.contains(StrategyMarketCompatibility.INDEX);
            case COMMODITY, METAL, BOND -> compatibility.contains(StrategyMarketCompatibility.COMMODITY);
            case FIAT -> compatibility.contains(StrategyMarketCompatibility.FX);
            case CRYPTO, SYNTHETIC, UNKNOWN -> false;
        };
    }

    private boolean supportsMarket(
            MarketType marketType,
            Set<StrategyMarketCompatibility> compatibility) {
        return switch (marketType == null ? MarketType.UNKNOWN : marketType) {
            case SPOT, CRYPTO -> compatibility.contains(StrategyMarketCompatibility.SPOT);
            case DERIVATIVE, DERIVATIVES, FUTURE, PERPETUAL, OPTION, CFD, CRYPTO_SWAP, SYNTHETIC ->
                    compatibility.contains(StrategyMarketCompatibility.DERIVATIVE);
            case MARGIN -> compatibility.contains(StrategyMarketCompatibility.SPOT);
            case FX, FOREX -> compatibility.contains(StrategyMarketCompatibility.FX);
            case SECURITIES, STOCK, ETF, FUND, WARRANT -> compatibility.contains(StrategyMarketCompatibility.EQUITY);
            case INDEX -> compatibility.contains(StrategyMarketCompatibility.INDEX);
            case BOND -> compatibility.contains(StrategyMarketCompatibility.COMMODITY);
            case OTC, UNKNOWN -> false;
        };
    }
}
