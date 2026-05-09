package org.investpro.strategy.registry;

import org.investpro.strategy.api.SignalProvider;
import org.investpro.strategy.context.SignalContext;
import org.investpro.strategy.model.SignalMetadata;
import org.investpro.strategy.model.TradingSignal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignalRegistryTest {
    @Test
    void rejectsDuplicateSignalIdUnlessReplaceEnabled() {
        SignalRegistry registry = new SignalRegistry();
        SignalProvider provider = new TestSignalProvider("duplicate");

        registry.register(provider);

        assertThatThrownBy(() -> registry.register(new TestSignalProvider("duplicate")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate signal id");
    }

    private record TestSignalProvider(String id) implements SignalProvider {
        @Override
        public SignalMetadata metadata() {
            return new SignalMetadata(id, id, "1.0.0", "Test", "", List.of(), List.of(), List.of(), 1.0, true);
        }

        @Override
        public TradingSignal evaluate(SignalContext context) {
            return TradingSignal.hold(id, id, "test");
        }
    }
}
