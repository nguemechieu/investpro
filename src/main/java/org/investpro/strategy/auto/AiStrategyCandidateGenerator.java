package org.investpro.strategy.auto;

import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.AiModelCatalog;
import org.investpro.ai.strategy.AiStrategyGenerationRequest;
import org.investpro.ai.strategy.AiStrategyGenerationResult;
import org.investpro.ai.strategy.AiStrategyGenerator;
import org.investpro.config.AppConfig;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class AiStrategyCandidateGenerator implements StrategyCandidateGenerator {

    private static final String CFG_ENABLED = "ai.strategyGeneration.enabled";

    private final AiStrategyGenerator generator;

    public AiStrategyCandidateGenerator(AiStrategyGenerator generator) {
        this.generator = generator;
    }

    @Override
    public List<StrategyCandidate> generateCandidates(StrategyGenerationContext context) {
        if (!AppConfig.getBoolean(CFG_ENABLED, false)) {
            log.info("AI strategy candidate generation is disabled");
            return List.of();
        }
        if (generator == null) {
            log.warn("AI strategy candidate generator is not configured");
            return List.of();
        }
        AiStrategyGenerationRequest request = new AiStrategyGenerationRequest(
                AiModelCatalog.defaultModel(),
                context == null ? "" : context.userPrompt(),
                Optional.empty(),
                Optional.ofNullable(context == null ? null : context.timeframe()),
                false,
                true);
        AiStrategyGenerationResult result = generator.generate(request);
        if (!result.success() || result.strategyDefinition() == null) {
            log.warn("AI strategy candidate rejected: {}", result.errors());
            return List.of();
        }
        return List.of(new StrategyCandidate(
                UUID.randomUUID().toString(),
                result.strategyDefinition(),
                StrategyGenerationSource.AI,
                context == null ? "UNKNOWN" : context.symbol(),
                context == null ? MarketRegime.UNKNOWN : context.marketRegime(),
                50.0,
                result.warnings(),
                Instant.now()));
    }
}
