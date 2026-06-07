package org.investpro.ai.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.AiCostEstimator;
import org.investpro.config.AppConfig;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.indicators.INDICATORS;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.StrategyParameters;
import org.investpro.strategy.rules.CandlePattern;
import org.investpro.strategy.rules.IndicatorCondition;
import org.investpro.strategy.rules.IndicatorConditionOperator;
import org.investpro.strategy.rules.SignalType;
import org.investpro.strategy.rules.StrategyRuleDefinition;
import org.investpro.strategy.rules.StrategyRuleSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Slf4j
public class SafeAiStrategyGenerator implements AiStrategyGenerator {

    private static final String CFG_AI_ENABLED = "ai.enabled";
    private static final String CFG_MAX_PROMPT_CHARS = "ai.maxPromptChars";
    private static final String CFG_REQUIRE_DISCLAIMER = "ai.requireDisclaimer";

    private final Function<AiStrategyGenerationRequest, String> responseProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    public SafeAiStrategyGenerator() {
        this(null);
    }

    public SafeAiStrategyGenerator(Function<AiStrategyGenerationRequest, String> responseProvider) {
        this.responseProvider = responseProvider;
    }

    @Override
    public AiStrategyGenerationResult generate(AiStrategyGenerationRequest request) {
        BigDecimal estimatedCost = AiCostEstimator.estimateTotalCredits(
                request == null ? null : request.model(),
                request == null ? "" : request.prompt());

        if (!AppConfig.getBoolean(CFG_AI_ENABLED, false)) {
            return AiStrategyGenerationResult.failure(
                    "AI strategy generation is disabled. InvestPro can still generate rule-based strategies.",
                    estimatedCost);
        }
        if (request == null) {
            return AiStrategyGenerationResult.failure("AI strategy request is required.", estimatedCost);
        }
        if (request.model() == null) {
            return AiStrategyGenerationResult.failure("AI model is required.", estimatedCost);
        }
        if (AppConfig.getBoolean(CFG_REQUIRE_DISCLAIMER, true) && !request.disclaimerAccepted()) {
            return AiStrategyGenerationResult.failure("AI trading disclaimer must be accepted before generation.", estimatedCost);
        }
        int maxPromptChars = AppConfig.getInt(CFG_MAX_PROMPT_CHARS, 4000);
        if (request.prompt() != null && request.prompt().length() > maxPromptChars) {
            return AiStrategyGenerationResult.failure("AI prompt is too long. Maximum characters: " + maxPromptChars, estimatedCost);
        }
        if (responseProvider == null) {
            return AiStrategyGenerationResult.failure("AI provider is not configured.", estimatedCost);
        }

        try {
            String rawResponse = Objects.requireNonNullElse(responseProvider.apply(request), "").trim();
            StrategyDefinition definition = parseStrategy(rawResponse);
            List<String> warnings = List.of("AI generated this strategy for review only. Backtest and save it before use.");
            return new AiStrategyGenerationResult(true, definition, rawResponse, warnings, List.of(), estimatedCost, estimatedCost);
        } catch (Exception exception) {
            log.warn("AI strategy generation rejected unsafe response: {}", exception.getMessage());
            return AiStrategyGenerationResult.failure(exception.getMessage(), estimatedCost);
        }
    }

    private StrategyDefinition parseStrategy(String rawResponse) throws Exception {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalArgumentException("AI response was empty.");
        }
        JsonNode root = mapper.readTree(rawResponse);
        String name = text(root, "name", "AI Strategy").trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("AI response must include a strategy name.");
        }

        List<StrategyRuleDefinition> rules = new ArrayList<>();
        JsonNode rulesNode = root.get("rules");
        if (rulesNode == null || !rulesNode.isArray()) {
            throw new IllegalArgumentException("AI response must include a rules array.");
        }
        for (JsonNode ruleNode : rulesNode) {
            StrategyRuleDefinition rule = parseRule(ruleNode);
            if (rule.enabled()) {
                rules.add(rule);
            }
        }
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("AI response did not include any enabled rules.");
        }

        return StrategyDefinition.builder()
                .name(name)
                .baseName(name)
                .parameters(StrategyParameters.builder().build())
                .rules(List.copyOf(rules))
                .build();
    }

    private StrategyRuleDefinition parseRule(JsonNode ruleNode) {
        StrategyRuleSource source = enumValue(StrategyRuleSource.class, text(ruleNode, "source", "INDICATOR"));
        SignalType signalType = enumValue(SignalType.class, text(ruleNode, "signalType", "NEUTRAL"));
        Timeframe timeframe = enumValue(Timeframe.class, text(ruleNode, "timeframe", "H1"));
        INDICATORS indicator = null;
        CandlePattern candlePattern = null;

        if (source == StrategyRuleSource.INDICATOR) {
            indicator = enumValue(INDICATORS.class, text(ruleNode, "indicator", ""));
            if (indicator == INDICATORS.UNKNOWN) {
                throw new IllegalArgumentException("AI response used UNKNOWN indicator.");
            }
        } else if (source == StrategyRuleSource.CANDLE_PATTERN) {
            candlePattern = enumValue(CandlePattern.class, text(ruleNode, "candlePattern", ""));
        }

        return new StrategyRuleDefinition(
                text(ruleNode, "id", null),
                source,
                signalType,
                indicator,
                candlePattern,
                timeframe,
                parseStringMap(ruleNode.get("parameters")),
                parseConditions(ruleNode.get("conditions"), signalType),
                true);
    }

    private Map<String, String> parseStringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            values.put(field.getKey(), field.getValue().isNull() ? "" : field.getValue().asText());
        }
        return values;
    }

    private List<IndicatorCondition> parseConditions(JsonNode node, SignalType signalType) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<IndicatorCondition> conditions = new ArrayList<>();
        for (JsonNode conditionNode : node) {
            conditions.add(new IndicatorCondition(
                    text(conditionNode, "outputKey", "value"),
                    enumValue(IndicatorConditionOperator.class, text(conditionNode, "operator", "GREATER_THAN")),
                    conditionNode.hasNonNull("compareValue") ? conditionNode.get("compareValue").asDouble() : null,
                    text(conditionNode, "compareOutputKey", null),
                    signalType));
        }
        return List.copyOf(conditions);
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return fallback;
        }
        return node.get(field).asText(fallback);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + type.getSimpleName() + " value.");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported " + type.getSimpleName() + ": " + value);
        }
    }
}
