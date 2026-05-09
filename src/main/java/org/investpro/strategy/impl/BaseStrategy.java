package org.investpro.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.AssetClass;
import org.investpro.enums.ContractType;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategyMetadata;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.TradingStrategy;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.List;

import static org.investpro.utils.Side.HOLD;

/**
 * Base abstract class for trading strategy implementations.
 *
 * Provides common functionality:
 * - metadata access
 * - market support checks
 * - warmup validation
 * - no-signal / HOLD signal creation
 * - common signal description tracking
 */
@Slf4j
public abstract class BaseStrategy implements TradingStrategy {

    protected final StrategyMetadata metadata;
    protected String lastSignalDescription;

    protected BaseStrategy(@NotNull StrategyMetadata metadata) {

        this.metadata = metadata;
        this.lastSignalDescription = "Initialized";
    }

    @Override
    @NotNull
    public StrategyMetadata getMetadata() {
        return metadata;
    }

    @Override
    public String getName() {
        return metadata.getDisplayName();
    }

    @Override
    public boolean isEnabled() {
        return metadata.isEnabled();
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
        return true;
    }

    @Override
    public int requiredWarmupBars() {
        return metadata.getMinimumBarsRequired();
    }

    @Override
    public void validateConfiguration() throws IllegalStateException {
        if (metadata.getStrategyId() == null || metadata.getStrategyId().isBlank()) {
            throw new IllegalStateException("Strategy metadata ID is missing");
        }

        if (metadata.getDisplayName() == null || metadata.getDisplayName().isBlank()) {
            throw new IllegalStateException("Strategy display name is missing");
        }

        if (metadata.getSupportedTimeframes() == null || metadata.getSupportedTimeframes().isEmpty()) {
            throw new IllegalStateException("Strategy must support at least one timeframe");
        }

        if (metadata.getSupportedAssetClasses() == null || metadata.getSupportedAssetClasses().isEmpty()) {
            throw new IllegalStateException("Strategy must support at least one asset class");
        }

        if (metadata.getSupportedContractTypes() == null || metadata.getSupportedContractTypes().isEmpty()) {
            throw new IllegalStateException("Strategy must support at least one contract type");
        }

        if (metadata.getMinimumBarsRequired() <= 0) {
            throw new IllegalStateException("Strategy minimum bars required must be greater than zero");
        }
    }

    @Override
    public String getLastSignalDescription() {
        return lastSignalDescription;
    }

    protected void updateSignalDescription(String description) {
        this.lastSignalDescription = description == null || description.isBlank()
                ? "No description"
                : description;
    }

    /**
     * Returns true when the context contains enough candles for this strategy.
     */
    protected boolean hasEnoughBars(@NotNull StrategyContext context) {
        return !context.hasEnoughBars(requiredWarmupBars());
    }

    /**
     * Builds a normalized HOLD signal.
     *
     * This is used when the strategy has no valid BUY/SELL setup.
     */
    protected StrategySignal noSignal(@NotNull StrategyContext context, @NotNull String reason) {
        String safeReason = reason.isBlank()
                ? "No actionable setup"
                : reason;

        updateSignalDescription("No signal: " + safeReason);

        log.debug(
                "No signal from strategyId={}, symbol={}, timeframe={}, reason={}",
                metadata.getStrategyId(),
                context.getSymbol(),
                context.getTimeframe(),
                safeReason);

        return StrategySignal.builder()
                .strategyId(metadata.getStrategyId())
                .strategyName(metadata.getDisplayName())
                .symbol(context.getSymbol() == null ? "" : context.getSymbol().toString('/'))
                .timeframe(context.getTimeframe() == null ? "" : context.getTimeframe().getCode())
                .side(HOLD)
                .confidence(0.0)
                .entryPrice(context.getCurrentPrice())
                .stopLossPrice(0.0)
                .takeProfitPrice(0.0)
                .riskRewardRatio(0.0)
                .sessionStatus(context.getTradingSessionStatus())
                .sessionNotes(context.getTradingSession() == null ? null : context.getTradingSession().getNotes())
                .reason(safeReason)
                .warnings(List.of())
                .createdAt(LocalDateTime.now())
                .build();
    }
}
