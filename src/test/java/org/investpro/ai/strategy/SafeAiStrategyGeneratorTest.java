package org.investpro.ai.strategy;

import org.investpro.ai.AiModelCatalog;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.indicators.INDICATORS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SafeAiStrategyGeneratorTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("ai.enabled");
        System.clearProperty("ai.requireDisclaimer");
    }

    @Test
    void disabledByDefaultRejectsGeneration() {
        AiStrategyGenerationResult result = new SafeAiStrategyGenerator(request -> "{}").generate(request("anything"));

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).contains("AI strategy generation is disabled. InvestPro can still generate rule-based strategies.");
    }

    @Test
    void rejectsUnknownIndicatorFromAiResponse() {
        System.setProperty("ai.enabled", "true");
        AiStrategyGenerationResult result = new SafeAiStrategyGenerator(request -> """
                {"name":"Bad AI","rules":[{"source":"INDICATOR","signalType":"BUY","indicator":"NOT_REAL","timeframe":"H1","parameters":{}}]}
                """).generate(request("bad"));

        assertThat(result.success()).isFalse();
        assertThat(result.errors().getFirst()).contains("Unsupported INDICATORS");
    }

    @Test
    void parsesValidAiStrategyForReviewOnly() {
        System.setProperty("ai.enabled", "true");
        AiStrategyGenerationResult result = new SafeAiStrategyGenerator(request -> """
                {
                  "name":"AI RSI Review",
                  "rules":[
                    {"source":"INDICATOR","signalType":"BUY","indicator":"RSI","timeframe":"H1","parameters":{"period":"14","oversold":"30"}}
                  ]
                }
                """).generate(request("make rsi"));

        assertThat(result.success()).isTrue();
        assertThat(result.strategyDefinition().getRules()).hasSize(1);
        assertThat(result.strategyDefinition().getRules().getFirst().indicator()).isEqualTo(INDICATORS.RSI);
        assertThat(result.strategyDefinition().getRules().getFirst().timeframe()).isEqualTo(Timeframe.H1);
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("review only"));
    }

    private AiStrategyGenerationRequest request(String prompt) {
        return new AiStrategyGenerationRequest(
                AiModelCatalog.find("QWEN3_30B_A3B_FREE").orElseThrow(),
                prompt,
                Optional.empty(),
                Optional.of(Timeframe.H1),
                false,
                true);
    }
}
