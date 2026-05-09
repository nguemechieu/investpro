package org.investpro.strategy.registry;

import org.investpro.strategy.api.InvestProStrategy;
import org.investpro.strategy.context.StrategyContext;
import org.investpro.strategy.model.StrategyDecision;
import org.investpro.strategy.model.StrategyMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyRegistryTest {
    @Test
    void registersAndFindsStrategy() {
        StrategyRegistry registry = new StrategyRegistry();
        InvestProStrategy strategy = new TestStrategy();

        registry.register(strategy);

        assertThat(registry.getStrategy("test-strategy")).containsSame(strategy);
        assertThat(registry.getAllStrategies()).hasSize(1);
    }

    private static class TestStrategy implements InvestProStrategy {
        @Override
        public StrategyMetadata metadata() {
            return new StrategyMetadata("test-strategy", "Test", "1.0.0", "Test", "", List.of(), List.of(),
                    List.of(), true, true, false);
        }

        @Override
        public void onStart(StrategyContext context) {
        }

        @Override
        public StrategyDecision onTick(StrategyContext context) {
            return StrategyDecision.hold("test");
        }
    }
}
