package org.investpro.strategy.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.indicators.INDICATORS;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.StrategyMarketCompatibility;
import org.investpro.strategy.StrategyParameters;
import org.investpro.strategy.rules.CandlePattern;
import org.investpro.strategy.rules.IndicatorCondition;
import org.investpro.strategy.rules.IndicatorConditionOperator;
import org.investpro.strategy.rules.SignalType;
import org.investpro.strategy.rules.StrategyRuleDefinition;
import org.investpro.strategy.rules.StrategyRuleSource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public final class UserStrategyDefinitionStore {

    private static final Path DEFAULT_PATH = Path.of("data", "user-strategy-definitions.json");
    private static final UserStrategyDefinitionStore DEFAULT = new UserStrategyDefinitionStore(DEFAULT_PATH);

    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public UserStrategyDefinitionStore(@NotNull Path path) {
        this.path = Objects.requireNonNull(path, "path must not be null");
    }

    public static UserStrategyDefinitionStore getDefault() {
        return DEFAULT;
    }

    public synchronized List<StrategyDefinition> loadAll() {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            List<PersistedStrategyDefinition> persisted = mapper.readValue(
                    path.toFile(),
                    new TypeReference<>() {
                    });
            return persisted.stream()
                    .map(this::toDefinition)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception exception) {
            log.warn("Unable to load user strategy definitions from {}", path, exception);
            return List.of();
        }
    }

    public synchronized void save(@NotNull StrategyDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        if (definition.getName() == null || definition.getName().isBlank()) {
            return;
        }

        Map<String, PersistedStrategyDefinition> definitions = new LinkedHashMap<>();
        for (StrategyDefinition existing : loadAll()) {
            definitions.put(normalize(existing.getName()), fromDefinition(existing));
        }
        definitions.put(normalize(definition.getName()), fromDefinition(definition));
        write(new ArrayList<>(definitions.values()));
    }

    private void write(List<PersistedStrategyDefinition> definitions) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), definitions);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save user strategy definitions to " + path, exception);
        }
    }

    private PersistedStrategyDefinition fromDefinition(StrategyDefinition definition) {
        StrategyParameters parameters = definition.getParameters() == null
                ? StrategyParameters.builder().build()
                : definition.getParameters();
        return new PersistedStrategyDefinition(
                definition.getName(),
                definition.getBaseName(),
                new PersistedStrategyParameters(
                        parameters.getRsiPeriod(),
                        parameters.getEmaFast(),
                        parameters.getEmaSlow(),
                        parameters.getAtrPeriod(),
                        parameters.getBreakoutLookback(),
                        parameters.getOversoldThreshold(),
                        parameters.getOverboughtThreshold(),
                        parameters.getMinConfidence(),
                        parameters.getSignalAmount()),
                definition.getRules() == null
                        ? List.of()
                        : definition.getRules().stream().map(this::fromRule).toList(),
                definition.getMarketCompatibility() == null
                        ? List.of()
                        : definition.getMarketCompatibility().stream().map(Enum::name).toList());
    }

    private StrategyDefinition toDefinition(PersistedStrategyDefinition persisted) {
        if (persisted == null || persisted.name() == null || persisted.name().isBlank()) {
            return null;
        }
        return StrategyDefinition.builder()
                .name(persisted.name())
                .baseName(persisted.baseName() == null || persisted.baseName().isBlank()
                        ? persisted.name()
                        : persisted.baseName())
                .parameters(toParameters(persisted.parameters()))
                .rules(persisted.rules() == null
                        ? List.of()
                        : persisted.rules().stream().map(this::toRule).filter(Objects::nonNull).toList())
                .marketCompatibility(toCompatibility(persisted.marketCompatibility()))
                .build();
    }

    private PersistedStrategyRule fromRule(StrategyRuleDefinition rule) {
        return new PersistedStrategyRule(
                rule.id(),
                enumName(rule.ruleSource()),
                enumName(rule.signalType()),
                enumName(rule.indicator()),
                enumName(rule.candlePattern()),
                enumName(rule.timeframe()),
                rule.parameters() == null ? Map.of() : Map.copyOf(rule.parameters()),
                rule.conditions() == null ? List.of() : rule.conditions().stream().map(this::fromCondition).toList(),
                rule.enabled());
    }

    private StrategyRuleDefinition toRule(PersistedStrategyRule persisted) {
        if (persisted == null) {
            return null;
        }
        return new StrategyRuleDefinition(
                persisted.id(),
                parseEnum(StrategyRuleSource.class, persisted.ruleSource(), StrategyRuleSource.INDICATOR),
                parseEnum(SignalType.class, persisted.signalType(), SignalType.NEUTRAL),
                parseEnum(INDICATORS.class, persisted.indicator(), null),
                parseEnum(CandlePattern.class, persisted.candlePattern(), null),
                parseEnum(Timeframe.class, persisted.timeframe(), null),
                persisted.parameters() == null ? Map.of() : Map.copyOf(persisted.parameters()),
                persisted.conditions() == null
                        ? List.of()
                        : persisted.conditions().stream().map(this::toCondition).filter(Objects::nonNull).toList(),
                persisted.enabled());
    }

    private PersistedIndicatorCondition fromCondition(IndicatorCondition condition) {
        return new PersistedIndicatorCondition(
                condition.outputKey(),
                enumName(condition.operator()),
                condition.compareValue(),
                condition.compareOutputKey(),
                enumName(condition.signalType()));
    }

    private IndicatorCondition toCondition(PersistedIndicatorCondition persisted) {
        if (persisted == null) {
            return null;
        }
        return new IndicatorCondition(
                persisted.outputKey(),
                parseEnum(IndicatorConditionOperator.class, persisted.operator(), null),
                persisted.compareValue(),
                persisted.compareOutputKey(),
                parseEnum(SignalType.class, persisted.signalType(), SignalType.NEUTRAL));
    }

    private StrategyParameters toParameters(PersistedStrategyParameters parameters) {
        if (parameters == null) {
            return StrategyParameters.builder().build();
        }
        return StrategyParameters.builder()
                .rsiPeriod(parameters.rsiPeriod())
                .emaFast(parameters.emaFast())
                .emaSlow(parameters.emaSlow())
                .atrPeriod(parameters.atrPeriod())
                .breakoutLookback(parameters.breakoutLookback())
                .oversoldThreshold(parameters.oversoldThreshold())
                .overboughtThreshold(parameters.overboughtThreshold())
                .minConfidence(parameters.minConfidence())
                .signalAmount(parameters.signalAmount())
                .build();
    }

    private Set<StrategyMarketCompatibility> toCompatibility(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of(StrategyMarketCompatibility.ALL);
        }
        Set<StrategyMarketCompatibility> parsed = values.stream()
                .map(value -> parseEnum(StrategyMarketCompatibility.class, value, null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return parsed.isEmpty() ? Set.of(StrategyMarketCompatibility.ALL) : parsed;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public record PersistedStrategyDefinition(
            String name,
            String baseName,
            PersistedStrategyParameters parameters,
            List<PersistedStrategyRule> rules,
            List<String> marketCompatibility) {
    }

    public record PersistedStrategyParameters(
            int rsiPeriod,
            int emaFast,
            int emaSlow,
            int atrPeriod,
            int breakoutLookback,
            double oversoldThreshold,
            double overboughtThreshold,
            double minConfidence,
            double signalAmount) {
    }

    public record PersistedStrategyRule(
            String id,
            String ruleSource,
            String signalType,
            String indicator,
            String candlePattern,
            String timeframe,
            Map<String, String> parameters,
            List<PersistedIndicatorCondition> conditions,
            boolean enabled) {
    }

    public record PersistedIndicatorCondition(
            String outputKey,
            String operator,
            Double compareValue,
            String compareOutputKey,
            String signalType) {
    }
}
