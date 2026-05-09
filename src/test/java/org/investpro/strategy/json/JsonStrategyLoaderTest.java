package org.investpro.strategy.json;

import org.investpro.strategy.api.CompositeStrategy;
import org.investpro.strategy.api.SignalProvider;
import org.investpro.strategy.registry.SignalRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JsonStrategyLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsSignalDefinition() throws Exception {
        Path json = tempDir.resolve("signal.json");
        Files.writeString(json, """
                {
                  "type": "SIGNAL",
                  "id": "rsi-json",
                  "name": "RSI JSON",
                  "version": "1.0.0",
                  "author": "Test",
                  "weight": 1.0,
                  "requiredIndicators": ["RSI_14"],
                  "supportedTimeframes": ["5m"],
                  "rules": [{"when": "RSI_14 < 30", "action": "BUY", "confidence": 0.7, "reason": "oversold"}]
                }
                """);

        SignalProvider provider = new JsonStrategyLoader(new SignalRegistry()).loadSignal(json);

        assertThat(provider.metadata().id()).isEqualTo("rsi-json");
    }

    @Test
    void loadsCompositeStrategyDefinition() throws Exception {
        SignalRegistry registry = new SignalRegistry();
        registry.register(new JsonRuleSignalProvider(new JsonSignalDefinition("SIGNAL", "rsi-json", "RSI JSON",
                "1.0.0", "Test", "", 1.0, java.util.List.of("RSI_14"), java.util.List.of("5m"),
                java.util.List.of(new JsonSignalRule("RSI_14 < 30", org.investpro.strategy.model.SignalAction.BUY,
                        0.7, "oversold")))));
        Path json = tempDir.resolve("composite.json");
        Files.writeString(json, """
                {
                  "type": "COMPOSITE_STRATEGY",
                  "id": "combo",
                  "name": "Combo",
                  "version": "1.0.0",
                  "timeframes": ["5m"],
                  "fusion": {"method": "WEIGHTED_VOTE", "minimumConfidence": 0.6},
                  "signals": [{"id": "rsi-json", "weight": 1.0, "enabled": true}],
                  "risk": {"riskPercent": 0.5, "stopLossAtrMultiplier": 1.5, "takeProfitAtrMultiplier": 2.0}
                }
                """);

        CompositeStrategy strategy = new JsonStrategyLoader(registry).loadCompositeStrategy(json);

        assertThat(strategy.metadata().id()).isEqualTo("combo");
        assertThat(strategy.signalIds()).containsExactly("rsi-json");
    }
}
