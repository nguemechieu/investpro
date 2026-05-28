package org.investpro.strategy.provider;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.AssetClass;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.spi.PluginRegistry;
import org.investpro.spi.StrategyProvider;
import org.investpro.spi.StrategyProviderContext;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.strategy.StrategyMetadata;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.TradingStrategy;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Lazy strategy discovery registry.
 *
 * <p>Strategies are represented by descriptors and suppliers. Instances are
 * created only when a strategy is assigned, backtested, selected, or opened by
 * the UI.</p>
 */
@Slf4j
@Data
public final class StrategyProviderRegistry {
    private static volatile StrategyProviderRegistry instance;

    private final Map<String, StrategyDescriptor> descriptors = new ConcurrentHashMap<>();
    private final Map<String, Supplier<TradingStrategy>> suppliers = new ConcurrentHashMap<>();
    private final Map<String, TradingStrategy> instanceCache = new ConcurrentHashMap<>();

    public static StrategyProviderRegistry getInstance() {
        StrategyProviderRegistry local = instance;
        if (local == null) {
            synchronized (StrategyProviderRegistry.class) {
                local = instance;
                if (local == null) {
                    local = new StrategyProviderRegistry();
                    local.bootstrapDefaults();
                    instance = local;
                }
            }
        }
        return local;
    }

    public void register(@NotNull StrategyDescriptor descriptor, @NotNull Supplier<TradingStrategy> supplier) {
        if (descriptor.strategyId().isBlank()) {
            throw new IllegalArgumentException("strategyId is required");
        }
        descriptors.put(descriptor.strategyId(), descriptor);
        suppliers.put(descriptor.strategyId(), supplier);
        log.debug("Registered lazy strategy provider: {}", descriptor.strategyId());
    }

    public Optional<TradingStrategy> resolve(String strategyId) {
        if (strategyId == null || strategyId.isBlank()) {
            return Optional.empty();
        }
        Supplier<TradingStrategy> supplier = suppliers.get(strategyId.trim());
        if (supplier == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(instanceCache.computeIfAbsent(strategyId.trim(), ignored -> supplier.get()));
    }

    public List<StrategyDescriptor> descriptors() {
        List<StrategyDescriptor> list = new ArrayList<>(descriptors.values());
        list.sort(Comparator.comparing(StrategyDescriptor::displayName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public List<StrategyDescriptor> filter(AssetClass assetClass, Timeframe timeframe, boolean backtestOnly) {
        List<StrategyDescriptor> result = new ArrayList<>();
        for (StrategyDescriptor descriptor : descriptors.values()) {
            if (!descriptor.supportsAssetClass(assetClass) || !descriptor.supportsTimeframe(timeframe)) {
                continue;
            }
            if (backtestOnly && !descriptor.supportsBacktesting()) {
                continue;
            }
            result.add(descriptor);
        }
        result.sort(Comparator.comparing(StrategyDescriptor::cpuComplexity)
                .thenComparing(StrategyDescriptor::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public Optional<StrategyDescriptor> descriptor(String strategyId) {
        return Optional.ofNullable(descriptors.get(strategyId));
    }

    public int size() {
        return descriptors.size();
    }

    private void bootstrapDefaults() {
        for (String name : StrategyCatalog.availableStrategyNames()) {
            register(StrategyDescriptor.lightweight(name, name), () -> StrategyRegistry.getInstance().getStrategy(name));
        }

        PluginRegistry plugins = PluginRegistry.loadDefault();
        for (StrategyProvider provider : plugins.strategyProviders()) {
            StrategyDescriptor descriptor = new StrategyDescriptor(
                    provider.id(),
                    provider.displayName(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    provider.supportedMarketTypes(),
                    classify(provider.displayName()),
                    StrategyComplexity.MEDIUM,
                    false,
                    false,
                    provider.enabledByDefault(),
                    true,
                    true,
                    Instant.now());
            register(descriptor, () -> provider.create(new StrategyProviderContext(null, plugins, null, Map.of())));
        }
    }

    public static StrategyDescriptor describe(TradingStrategy strategy) {
        if (strategy == null || strategy.getMetadata() == null) {
            return StrategyDescriptor.lightweight("unknown", "Unknown Strategy");
        }
        StrategyMetadata metadata = strategy.getMetadata();
        return new StrategyDescriptor(
                metadata.getStrategyId(),
                metadata.getDisplayName(),
                metadata.getSupportedAssetClasses(),
                metadata.getSupportedTimeframes(),
                Set.of(),
                Set.of(),
                classify(metadata.getDisplayName()),
                classify(metadata.getDisplayName()),
                false,
                false,
                metadata.isEnabled(),
                true,
                true,
                Instant.now());
    }

    private static StrategyComplexity classify(String name) {
        String text = name == null ? "" : name.toLowerCase();
        if (text.contains("ai") || text.contains("optimization") || text.contains("portfolio")) {
            return StrategyComplexity.HEAVY;
        }
        if (text.contains("deep") || text.contains("genetic") || text.contains("monte")) {
            return StrategyComplexity.EXTREME;
        }
        if (text.contains("vwap") || text.contains("macd") || text.contains("ichimoku")) {
            return StrategyComplexity.MEDIUM;
        }
        return StrategyComplexity.LIGHT;
    }
}
