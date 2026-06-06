package org.investpro.ui.market;

import org.investpro.models.market.MarketInstrument;
import org.investpro.models.market.AssetClass;
import org.investpro.models.market.ContractType;
import org.investpro.models.market.InstrumentType;
import org.investpro.models.market.MarketType;
import org.investpro.trading.tradability.TradabilityStatus;

public enum MarketWatchProductFilter {
    ALL,
    SPOT,
    FUTURES,
    PERPETUALS,
    INDICES,
    STOCKS,
    COMMODITIES,
    FX,
    TRADABLE_ONLY,
    RESTRICTED_ONLY;

    public boolean accepts(MarketInstrument instrument) {
        if (this == ALL) {
            return true;
        }
        if (instrument == null) {
            return this != TRADABLE_ONLY && this != RESTRICTED_ONLY;
        }
        return switch (this) {
            case ALL -> true;
            case SPOT -> instrument.isSpot();
            case FUTURES -> instrument.instrumentType() == InstrumentType.FUTURE
                    || instrument.contractType() == ContractType.FUTURE
                    || instrument.isFuture();
            case PERPETUALS -> instrument.instrumentType() == InstrumentType.PERPETUAL
                    || instrument.contractType() == ContractType.PERPETUAL
                    || instrument.isPerpetual();
            case INDICES -> instrument.instrumentType() == InstrumentType.INDEX
                    || instrument.assetClass() == AssetClass.INDEX
                    || instrument.marketType() == MarketType.INDEX;
            case STOCKS -> instrument.instrumentType() == InstrumentType.STOCK
                    || instrument.assetClass() == AssetClass.EQUITY
                    || instrument.marketType() == MarketType.STOCK;
            case COMMODITIES -> instrument.assetClass() == AssetClass.COMMODITY
                    || instrument.assetClass() == AssetClass.METAL
                    || instrument.instrumentType() == InstrumentType.COMMODITY;
            case FX -> instrument.instrumentType() == InstrumentType.FOREX || instrument.marketType().isFx();
            case TRADABLE_ONLY -> instrument.tradability() != null && instrument.tradability().isFullyTradable();
            case RESTRICTED_ONLY -> instrument.tradability() != null
                    && instrument.tradability().status() != TradabilityStatus.FULLY_TRADABLE;
        };
    }
}
