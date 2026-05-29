package org.investpro.strategy.nocode;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.AssetClass;
import org.investpro.enums.ContractType;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategyMetadata;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.TradingStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * Adapts a {@link CompiledNoCodeStrategy} to the platform's
 * {@link TradingStrategy} interface.
 *
 * <p>This allows no-code strategies built in the {@link StrategyBuilderPanel}
 * to flow through exactly the same pipeline as developer JAR plugins:
 * {@code StrategyRegistry → Backtesting → AI Review → Paper Trading → RiskEngine}.</p>
 *
 * <p>Safety properties (mirroring {@link org.investpro.strategy.impl.UserStrategyAdapter}):
 * <ul>
 *   <li>All exceptions from the runtime are caught; HOLD is returned on failure.</li>
 *   <li>Null signals are replaced with HOLD.</li>
 *   <li>Signals are normalized before being returned.</li>
 *   <li>No order-submission logic exists in this class.</li>
 * </ul>
 * </p>
 *
 * <p>This class is thread-safe: each instance holds immutable compiled state.
 * The {@link NoCodeStrategyRuntime} is stateless for a given evaluation.</p>
 */
@Slf4j
@Getter
@Setter
public class NoCodeStrategyAdapter implements TradingStrategy {

    private final CompiledNoCodeStrategy compiled;
    private final NoCodeStrategyRuntime runtime;
    private final StrategyMetadata metadata;

    private boolean enabled = true;
    private String lastSignalDescription = "No signal generated yet";

    /**
     * Creates an adapter for the given compiled strategy.
     *
     * @param compiled the compiled (and validated) no-code strategy
     */
    public NoCodeStrategyAdapter(@NotNull CompiledNoCodeStrategy compiled) {
        this.compiled = compiled;
        this.runtime = new NoCodeStrategyRuntime(compiled);
        this.metadata = buildMetadata(compiled.getDefinition());
    }

    // =========================================================================
    // TradingStrategy implementation
    // =========================================================================

    @Override
    public StrategyMetadata getMetadata() {
        return metadata;
    }

    @Override
    public @NotNull StrategySignal generateSignal(@NotNull StrategyContext context) {
        try {
            StrategySignal signal = runtime.evaluate(context);
            if (signal == null) {
                log.warn("NoCode strategy '{}' returned null signal; using HOLD", getId());
                return holdSignal(context, "Runtime returned null signal");
            }
            StrategySignal normalized = signal.normalized();
            updateLastDescription(normalized);
            return normalized;
        } catch (Exception e) {
            log.error("Exception in no-code strategy '{}': {}", getId(), e.getMessage(), e);
            lastSignalDescription = "Error: " + e.getClass().getSimpleName();
            return holdSignal(context, "Error in strategy: " + e.getMessage());
        }
    }

    @Override
    public boolean supportsAssetClass(AssetClass assetClass) {
        return true;
    }

    @Override
    public boolean supportsContractType(ContractType contractType) {
        return true;
    }

    @Override
    public boolean supportsTimeframe(Timeframe timeframe) {
        return true;
    }

    @Override
    public boolean supportsMarketBehavior(MarketBehavior marketBehavior) {
        return true;
    }

    @Override
    public int requiredWarmupBars() {
        return compiled.getWarmupBars();
    }

    @Override
    public void validateConfiguration() {
        // Validation performed by NoCodeStrategyValidator during compilation
    }

    @Override
    public @NotNull Object getName() {
        return compiled.getDefinition().getName();
    }

    @Override
    public @NotNull Object getId() {
        return compiled.getDefinition().getStrategyId();
    }

    /** @return true — callers can check strategy type without instanceof. */
    public boolean isNoCodeStrategy() {
        return true;
    }

    @Override
    public String toString() {
        return String.format("NoCodeStrategyAdapter[id=%s, name=%s, warmup=%d, valid=%s]",
                getId(), getName(), compiled.getWarmupBars(), compiled.isValid());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private StrategySignal holdSignal(StrategyContext ctx, String reason) {
        String sym = ctx.getSymbol() != null ? ctx.getSymbol().toString() : "UNKNOWN";
        String tf  = ctx.getTimeframe() != null ? ctx.getTimeframe().toString() : "UNKNOWN";
        return StrategySignal.hold(sym, tf, compiled.getDefinition().getStrategyId(), reason);
    }

    private void updateLastDescription(@NotNull StrategySignal signal) {
        StringBuilder desc = new StringBuilder(signal.getSide().name());
        if (signal.getConfidence() > 0) {
            desc.append(String.format(" (%.0f%%)", signal.getConfidence() * 100));
        }
        if (!signal.getReasons().isEmpty()) {
            desc.append(" - ").append(signal.getReasons().get(0));
        }
        this.lastSignalDescription = desc.toString();
    }

    private static StrategyMetadata buildMetadata(NoCodeStrategyDefinition def) {
        return StrategyMetadata.builder()
                .strategyId(def.getStrategyId())
                .displayName(def.getName())
                .description(def.getDescription() != null ? def.getDescription() : "")
                .version(def.getVersion())
                .author(def.getAuthor())
                .build();
    }
}
