package org.investpro.strategy;

import org.investpro.models.market.MarketInstrument;
import org.investpro.models.market.AssetClass;
import org.investpro.models.market.ContractType;
import org.investpro.models.market.InstrumentType;
import org.investpro.models.market.MarketType;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class StrategyInstrumentCompatibilityService {

    public boolean supports(StrategyDefinition strategy, MarketInstrument instrument) {
        if (strategy == null || instrument == null || instrument.marketType() == MarketType.UNKNOWN) {
            return false;
        }

        if (strategy.getMarketCompatibility() != null && !strategy.getMarketCompatibility().isEmpty()) {
            return strategy.supports(instrument);
        }

        return inferSupportFromName(strategy, instrument);
    }

    public List<StrategyDefinition> compatibleStrategies(MarketInstrument instrument) {
        return StrategyCatalog.STRATEGY_DEFINITIONS.values().stream()
                .filter(strategy -> supports(strategy, instrument))
                .toList();
    }

    public List<MarketInstrument> filterInstrumentsForStrategy(
            StrategyDefinition strategy,
            List<MarketInstrument> instruments) {
        if (instruments == null || instruments.isEmpty()) {
            return List.of();
        }
        return instruments.stream()
                .filter(Objects::nonNull)
                .filter(instrument -> supports(strategy, instrument))
                .toList();
    }

    private boolean inferSupportFromName(StrategyDefinition strategy, MarketInstrument instrument) {
        String name = ((strategy.getName() == null ? "" : strategy.getName()) + " "
                + (strategy.getBaseName() == null ? "" : strategy.getBaseName()))
                .toUpperCase(Locale.ROOT);

        if (instrument.instrumentType().isDerivative()
                || instrument.contractType() == ContractType.FUTURE
                || instrument.contractType() == ContractType.PERPETUAL
                || instrument.contractType() == ContractType.OPTION
                || instrument.marketType() == MarketType.DERIVATIVES
                || instrument.marketType() == MarketType.DERIVATIVE
                || instrument.marketType() == MarketType.CFD) {
            return containsAny(name, "TREND", "MOMENTUM", "BREAKOUT", "VOLATILITY", "CARRY", "DERIVATIVE");
        }
        if (instrument.instrumentType() == InstrumentType.FOREX
                || instrument.marketType() == MarketType.FX
                || instrument.marketType() == MarketType.FOREX
                || instrument.assetClass() == AssetClass.FIAT) {
            return containsAny(name, "FX", "FOREX", "CARRY", "TREND", "REVERSION", "BREAKOUT");
        }
        return switch (instrument.assetClass()) {
            case CRYPTO -> true;
            case COMMODITY, METAL -> containsAny(name, "COMMODITY", "METAL", "TREND", "VOLATILITY", "BREAKOUT");
            case INDEX, EQUITY, ETF, FUND -> containsAny(name, "MOMENTUM", "GAP", "ROTATION", "TREND", "INDEX", "EQUITY");
            case BOND -> containsAny(name, "BOND", "CARRY", "TREND");
            case FIAT, SYNTHETIC, UNKNOWN -> instrument.marketType() == MarketType.SPOT
                    || instrument.instrumentType() == InstrumentType.SPOT;
        };
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
