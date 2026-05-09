package org.investpro.strategy.engine;

import org.investpro.strategy.api.SignalProvider;
import org.investpro.strategy.context.SignalContext;
import org.investpro.strategy.loader.StrategyDirectoryManager;
import org.investpro.strategy.model.SignalMetadata;
import org.investpro.strategy.model.StrategyDecision;
import org.investpro.strategy.model.TradingSignal;
import org.investpro.strategy.registry.SignalRegistry;
import org.investpro.strategy.registry.StrategyRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyRuntimeServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void brokenCustomSignalDoesNotCrashRuntime() {
        SignalRegistry signalRegistry = new SignalRegistry();
        signalRegistry.register(new BrokenSignal());
        StrategyRuntimeService service = new StrategyRuntimeService(signalRegistry, new StrategyRegistry(),
                new SignalFusionEngine(0.6), new StrategyDirectoryManager(tempDir));
        SignalContext context = new SignalContext(null, "test", "SPOT", "5m", null, null, List.of(), null,
                Map.of(), Map.of(), false, Instant.now());

        List<TradingSignal> signals = service.evaluateEnabledSignals(context);
        StrategyDecision decision = service.evaluateSignalsWithFusion(context);

        assertThat(signals).hasSize(1);
        assertThat(signals.getFirst().reason()).contains("Signal failed");
        assertThat(decision.action().name()).isEqualTo("HOLD");
    }

    private static class BrokenSignal implements SignalProvider {
        @Override
        public SignalMetadata metadata() {
            return new SignalMetadata("broken", "Broken", "1.0.0", "Test", "", List.of(), List.of(), List.of(),
                    1.0, true);
        }

        @Override
        public TradingSignal evaluate(SignalContext context) {
            throw new IllegalStateException("boom");
        }
    }
}
