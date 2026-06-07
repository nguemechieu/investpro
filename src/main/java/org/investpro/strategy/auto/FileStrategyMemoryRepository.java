package org.investpro.strategy.auto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.indicators.INDICATORS;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.StrategyMarketCompatibility;
import org.investpro.strategy.StrategyParameters;
import org.investpro.strategy.lab.StrategyPerformanceReport;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class FileStrategyMemoryRepository implements StrategyMemoryRepository {

    private static final Path DEFAULT_PATH = Path.of("data", "auto-strategy-memory.json");

    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public FileStrategyMemoryRepository() {
        this(DEFAULT_PATH);
    }

    public FileStrategyMemoryRepository(@NotNull Path path) {
        this.path = Objects.requireNonNull(path, "path must not be null");
    }

    @Override
    public synchronized void saveCandidate(StrategyCandidate candidate) {
        if (candidate == null) {
            return;
        }
        PersistedMemory memory = loadMemory();
        memory.candidates().add(fromCandidate(candidate));
        write(memory);
    }

    @Override
    public synchronized void saveEvaluation(StrategyEvaluationResult result) {
        if (result == null || result.candidate() == null) {
            return;
        }
        PersistedMemory memory = loadMemory();
        memory.evaluations().add(fromEvaluation(result));
        if (result.passed()) {
            memory.winningStrategies().add(fromCandidate(result.candidate()));
        } else {
            memory.rejectedCandidates().add(fromCandidate(result.candidate()));
        }
        write(memory);
    }

    @Override
    public synchronized void saveDecision(StrategyAssignmentDecision decision) {
        if (decision == null) {
            return;
        }
        PersistedMemory memory = loadMemory();
        memory.decisions().add(fromDecision(decision));
        write(memory);
    }

    @Override
    public synchronized List<StrategyCandidate> candidatesFor(String symbol) {
        return loadMemory().candidates().stream()
                .filter(candidate -> sameSymbol(symbol, candidate.symbol()))
                .map(this::toCandidate)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public synchronized List<StrategyEvaluationResult> evaluationsFor(String symbol) {
        return loadMemory().evaluations().stream()
                .filter(evaluation -> sameSymbol(symbol, evaluation.symbol()))
                .map(this::toEvaluation)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public synchronized List<StrategyAssignmentDecision> decisionsFor(String symbol) {
        return loadMemory().decisions().stream()
                .filter(decision -> sameSymbol(symbol, decision.symbol()))
                .map(this::toDecision)
                .toList();
    }

    private PersistedMemory loadMemory() {
        if (!Files.exists(path)) {
            return PersistedMemory.empty();
        }
        try {
            PersistedMemory memory = mapper.readValue(path.toFile(), new TypeReference<>() {
            });
            return memory == null ? PersistedMemory.empty() : memory.normalized();
        } catch (Exception exception) {
            log.warn("Unable to load Auto Strategy Lab memory from {}", path, exception);
            return PersistedMemory.empty();
        }
    }

    private void write(PersistedMemory memory) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), memory.normalized());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save Auto Strategy Lab memory to " + path, exception);
        }
    }

    private PersistedCandidate fromCandidate(StrategyCandidate candidate) {
        return new PersistedCandidate(
                candidate.id(),
                candidate.strategyDefinition() == null ? "" : candidate.strategyDefinition().getName(),
                candidate.symbol(),
                candidate.source() == null ? null : candidate.source().name(),
                candidate.marketRegime() == null ? null : candidate.marketRegime().name(),
                candidate.generationScore(),
                candidate.rationale() == null ? List.of() : List.copyOf(candidate.rationale()),
                candidate.generatedAt(),
                fromDefinition(candidate.strategyDefinition()));
    }

    private StrategyCandidate toCandidate(PersistedCandidate persisted) {
        if (persisted == null) {
            return null;
        }
        return new StrategyCandidate(
                persisted.id(),
                toDefinition(persisted.strategyDefinition()),
                parseEnum(StrategyGenerationSource.class, persisted.source(), StrategyGenerationSource.RULE_BASED),
                persisted.symbol(),
                parseEnum(MarketRegime.class, persisted.marketRegime(), MarketRegime.UNKNOWN),
                persisted.generationScore(),
                persisted.rationale() == null ? List.of() : List.copyOf(persisted.rationale()),
                persisted.generatedAt() == null ? Instant.now() : persisted.generatedAt());
    }

    private PersistedEvaluation fromEvaluation(StrategyEvaluationResult result) {
        return new PersistedEvaluation(
                result.candidate().symbol(),
                fromCandidate(result.candidate()),
                fromReport(result.inSampleReport()),
                fromReport(result.outOfSampleReport()),
                result.score(),
                result.passed(),
                result.warnings() == null ? List.of() : List.copyOf(result.warnings()),
                result.errors() == null ? List.of() : List.copyOf(result.errors()),
                Instant.now());
    }

    private StrategyEvaluationResult toEvaluation(PersistedEvaluation persisted) {
        if (persisted == null) {
            return null;
        }
        return new StrategyEvaluationResult(
                toCandidate(persisted.candidate()),
                toReport(persisted.inSampleReport()),
                toReport(persisted.outOfSampleReport()),
                persisted.score(),
                persisted.passed(),
                persisted.warnings() == null ? List.of() : List.copyOf(persisted.warnings()),
                persisted.errors() == null ? List.of() : List.copyOf(persisted.errors()));
    }

    private PersistedDecision fromDecision(StrategyAssignmentDecision decision) {
        return new PersistedDecision(
                decision.symbol(),
                decision.currentStrategyName(),
                decision.selectedStrategyName(),
                decision.assigned(),
                decision.liveAssignmentAllowed(),
                decision.currentScore(),
                decision.selectedScore(),
                decision.reason(),
                decision.warnings() == null ? List.of() : List.copyOf(decision.warnings()),
                Instant.now());
    }

    private StrategyAssignmentDecision toDecision(PersistedDecision persisted) {
        return new StrategyAssignmentDecision(
                persisted.symbol(),
                persisted.currentStrategyName(),
                persisted.selectedStrategyName(),
                persisted.assigned(),
                persisted.liveAssignmentAllowed(),
                persisted.selectedStrategyName(),
                persisted.currentScore(),
                persisted.selectedScore(),
                persisted.reason(),
                persisted.warnings() == null ? List.of() : List.copyOf(persisted.warnings()),
                null);
    }

    private PersistedReport fromReport(StrategyPerformanceReport report) {
        if (report == null) {
            return null;
        }
        return new PersistedReport(
                report.getStrategyName(),
                report.getBaseStrategyName(),
                report.getSymbol(),
                report.getTimeframe() == null ? null : report.getTimeframe().name(),
                report.getTotalTrades(),
                report.getWinRate(),
                report.getTotalReturn(),
                report.getNetProfit(),
                report.getMaxDrawdown(),
                report.getProfitFactor(),
                report.getSharpeApproximation(),
                report.getScore(),
                report.getWarnings() == null ? List.of() : List.copyOf(report.getWarnings()),
                report.getGeneratedAt());
    }

    private StrategyPerformanceReport toReport(PersistedReport persisted) {
        if (persisted == null) {
            return null;
        }
        return StrategyPerformanceReport.builder()
                .strategyName(blankToDefault(persisted.strategyName(), "unknown"))
                .baseStrategyName(blankToDefault(persisted.baseStrategyName(), "unknown"))
                .symbol(blankToDefault(persisted.symbol(), "UNKNOWN"))
                .timeframe(parseEnum(Timeframe.class, persisted.timeframe(), Timeframe.H1))
                .totalTrades(persisted.totalTrades())
                .winRate(persisted.winRate())
                .totalReturn(persisted.totalReturn())
                .netProfit(persisted.netProfit())
                .maxDrawdown(persisted.maxDrawdown())
                .profitFactor(persisted.profitFactor())
                .sharpeApproximation(persisted.sharpeApproximation())
                .score(persisted.score())
                .warnings(persisted.warnings() == null ? new ArrayList<>() : new ArrayList<>(persisted.warnings()))
                .generatedAt(persisted.generatedAt() == null ? Instant.now() : persisted.generatedAt())
                .build();
    }

    private PersistedStrategyDefinition fromDefinition(StrategyDefinition definition) {
        if (definition == null) {
            return null;
        }
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
                .baseName(blankToDefault(persisted.baseName(), persisted.name()))
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

    private boolean sameSymbol(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equalsIgnoreCase(actual == null ? "" : actual);
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

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record PersistedMemory(
            List<PersistedCandidate> candidates,
            List<PersistedEvaluation> evaluations,
            List<PersistedDecision> decisions,
            List<PersistedCandidate> rejectedCandidates,
            List<PersistedCandidate> winningStrategies) {

        static PersistedMemory empty() {
            return new PersistedMemory(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        PersistedMemory normalized() {
            return new PersistedMemory(
                    candidates == null ? new ArrayList<>() : candidates,
                    evaluations == null ? new ArrayList<>() : evaluations,
                    decisions == null ? new ArrayList<>() : decisions,
                    rejectedCandidates == null ? new ArrayList<>() : rejectedCandidates,
                    winningStrategies == null ? new ArrayList<>() : winningStrategies);
        }
    }

    public record PersistedCandidate(
            String id,
            String name,
            String symbol,
            String source,
            String marketRegime,
            double generationScore,
            List<String> rationale,
            Instant generatedAt,
            PersistedStrategyDefinition strategyDefinition) {
    }

    public record PersistedEvaluation(
            String symbol,
            PersistedCandidate candidate,
            PersistedReport inSampleReport,
            PersistedReport outOfSampleReport,
            double score,
            boolean passed,
            List<String> warnings,
            List<String> errors,
            Instant evaluatedAt) {
    }

    public record PersistedDecision(
            String symbol,
            String currentStrategyName,
            String selectedStrategyName,
            boolean assigned,
            boolean liveAssignmentAllowed,
            double currentScore,
            double selectedScore,
            String reason,
            List<String> warnings,
            Instant decidedAt) {
    }

    public record PersistedReport(
            String strategyName,
            String baseStrategyName,
            String symbol,
            String timeframe,
            int totalTrades,
            double winRate,
            double totalReturn,
            double netProfit,
            double maxDrawdown,
            double profitFactor,
            double sharpeApproximation,
            double score,
            List<String> warnings,
            Instant generatedAt) {
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
