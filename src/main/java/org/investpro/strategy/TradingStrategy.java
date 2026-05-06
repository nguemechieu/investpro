package org.investpro.strategy;

import org.investpro.market.AssetClass;
import org.investpro.market.ContractType;
import org.investpro.risk.MarketBehavior;
import org.investpro.timeframe.Timeframe;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

public interface TradingStrategy {

    StrategyMetadata getMetadata();

    @NotNull Side generateSignal(StrategyContext context);

    boolean supportsAssetClass(AssetClass assetClass);

    boolean supportsContractType(ContractType contractType);

    boolean supportsTimeframe(Timeframe timeframe);

    boolean supportsMarketBehavior(MarketBehavior marketBehavior);

    boolean supportsMarketBehavior(@NotNull org.investpro.trading.MarketBehavior marketBehavior);

    int requiredWarmupBars();

    default boolean isEnabled() {
        return true;
    }

    void validateConfiguration();

    String getLastSignalDescription();
}