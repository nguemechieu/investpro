package org.investpro.strategy;

import org.investpro.enums.AssetClass;
import org.investpro.enums.ContractType;
import org.investpro.enums.MarketBehavior;
import org.investpro.timeframe.Timeframe;

public interface TradingStrategy {

    StrategyMetadata getMetadata();

    StrategySignal generateSignal(StrategyContext context);

    boolean supportsAssetClass(AssetClass assetClass);

    boolean supportsContractType(ContractType contractType);

    boolean supportsTimeframe(Timeframe timeframe);

    boolean supportsMarketBehavior(MarketBehavior marketBehavior);

    int requiredWarmupBars();

    default boolean isEnabled() {
        return true;
    }

    void validateConfiguration();

    String getLastSignalDescription();

    Object getName();

    Object getId();
}