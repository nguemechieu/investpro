package org.investpro.trading.market;

import org.investpro.models.market.*;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.SymbolTradability;
import java.util.Map;

/** Classifies v20 instruments using the broker's type, never the symbol's shape. */
public final class OandaInstrumentPolicy {
    private OandaInstrumentPolicy() {}

    public static MarketInstrument map(TradePair pair, Map<String, Object> specification, SymbolTradability status) {
        String type = String.valueOf(specification.getOrDefault("type", "UNKNOWN"));
        boolean fx = "CURRENCY".equals(type);
        boolean metal = "METAL".equals(type);
        boolean cfd = "CFD".equals(type);
        boolean known = fx || metal || cfd;
        return new MarketInstrument("OANDA",
                String.valueOf(specification.getOrDefault("name", pair.toString('_'))), pair.toString('/'), pair,
                fx ? AssetClass.FIAT : metal ? AssetClass.METAL : AssetClass.UNKNOWN,
                known ? MarketType.DERIVATIVES : MarketType.UNKNOWN,
                fx ? InstrumentType.FOREX : metal || cfd ? InstrumentType.CFD : InstrumentType.UNKNOWN,
                known ? LeverageMode.MARGIN : LeverageMode.NONE,
                fx || metal ? ContractType.MARGIN : cfd ? ContractType.CFD : ContractType.UNKNOWN,
                type, null, null, TradingEnvironment.LIVE, "OANDA", pair.getBaseCode(), pair.getCounterCode(),
                "", pair.getBaseCode(), "", "", null, known, false, false, known, status, specification);
    }
}
