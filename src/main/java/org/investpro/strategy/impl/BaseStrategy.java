package org.investpro.strategy.impl;

import org.investpro.market.AssetClass;
import org.investpro.market.ContractType;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategyMetadata;
import org.investpro.strategy.TradingStrategy;
import org.investpro.trading.MarketBehavior;
import org.investpro.utils.Side;
import lombok.extern.slf4j.Slf4j;
import org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;

import static org.investpro.utils.Side.HOLD;

/**
 * Base abstract class for trading strategy implementations.
 * Provides common functionality to reduce boilerplate.
 */
@Slf4j
public abstract class BaseStrategy implements TradingStrategy {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BaseStrategy.class);
    protected final StrategyMetadata metadata;
    protected String lastSignalDescription;

    public BaseStrategy(@NotNull StrategyMetadata metadata) {
        this.metadata = metadata;
        this.lastSignalDescription = "Initialized";
    }

    @Override
    @NotNull
    public StrategyMetadata getMetadata() {
        return metadata;
    }

    @Override
    public boolean supportsAssetClass(@NotNull AssetClass assetClass) {
        return metadata.supportsAssetClass(assetClass);
    }

    @Override
    public boolean supportsContractType(@NotNull ContractType contractType) {
        return metadata.supportsContractType(contractType);
    }

    @Override
    public boolean supportsTimeframe(@NotNull Timeframe timeframe) {
        return metadata.supportsTimeframe(timeframe);
    }

    @Override
    public boolean supportsMarketBehavior(@NotNull MarketBehavior marketBehavior) {
        return true; // Can override in subclasses
    }

    @Override
    public int requiredWarmupBars() {
        return metadata.getMinimumBarsRequired();
    }

    @Override
    public void validateConfiguration() throws IllegalStateException {
        if (metadata.getStrategyId() == null) {
            throw new IllegalStateException("Strategy metadata or ID is missing");
        }
        if (metadata.getSupportedTimeframes().isEmpty()) {
            throw new IllegalStateException("Strategy must support at least one timeframe");
        }
    }

    @Override
    public String getLastSignalDescription() {
        return lastSignalDescription;
    }

    protected void updateSignalDescription(String description) {
        this.lastSignalDescription = description;
    }

    protected boolean hasEnoughBars(StrategyContext context) {
        return !context.hasEnoughBars(requiredWarmupBars());
    }

    protected Side noSignal(StrategyContext context, String reason) {
        updateSignalDescription("No signal: " + reason);
        return HOLD;
    }
}
