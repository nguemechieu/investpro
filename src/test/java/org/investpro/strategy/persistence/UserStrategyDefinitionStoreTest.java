package org.investpro.strategy.persistence;

import org.investpro.enums.timeframe.Timeframe;
import org.investpro.indicators.INDICATORS;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.StrategyParameters;
import org.investpro.strategy.rules.SignalType;
import org.investpro.strategy.rules.StrategyRuleDefinition;
import org.investpro.strategy.rules.StrategyRuleSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserStrategyDefinitionStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsStrategyBuilderDefinition() {
        UserStrategyDefinitionStore store = new UserStrategyDefinitionStore(tempDir.resolve("strategies.json"));
        StrategyDefinition definition = StrategyDefinition.builder()
                .name("hope")
                .baseName("hope")
                .parameters(StrategyParameters.builder()
                        .rsiPeriod(9)
                        .emaFast(12)
                        .emaSlow(26)
                        .build())
                .rules(List.of(new StrategyRuleDefinition(
                        StrategyRuleSource.INDICATOR,
                        SignalType.BUY,
                        INDICATORS.MACD_SIGNAL,
                        null,
                        Timeframe.H1,
                        Map.of("fastPeriod", "12", "slowPeriod", "26", "signalPeriod", "9"))))
                .build();

        store.save(definition);

        List<StrategyDefinition> loaded = new UserStrategyDefinitionStore(tempDir.resolve("strategies.json")).loadAll();

        assertThat(loaded).hasSize(1);
        StrategyDefinition restored = loaded.getFirst();
        assertThat(restored.getName()).isEqualTo("hope");
        assertThat(restored.getParameters().getRsiPeriod()).isEqualTo(9);
        assertThat(restored.getRules()).hasSize(1);
        assertThat(restored.getRules().getFirst().indicator()).isEqualTo(INDICATORS.MACD_SIGNAL);
        assertThat(restored.getRules().getFirst().timeframe()).isEqualTo(Timeframe.H1);
        assertThat(restored.getRules().getFirst().parameters()).containsEntry("signalPeriod", "9");
    }

    @Test
    void saveUpsertsByStrategyName() {
        UserStrategyDefinitionStore store = new UserStrategyDefinitionStore(tempDir.resolve("strategies.json"));

        store.save(StrategyDefinition.builder()
                .name("hope")
                .baseName("old")
                .parameters(StrategyParameters.builder().rsiPeriod(7).build())
                .build());
        store.save(StrategyDefinition.builder()
                .name("hope")
                .baseName("new")
                .parameters(StrategyParameters.builder().rsiPeriod(21).build())
                .build());

        List<StrategyDefinition> loaded = store.loadAll();

        assertThat(loaded).hasSize(1);
        assertThat(loaded.getFirst().getBaseName()).isEqualTo("new");
        assertThat(loaded.getFirst().getParameters().getRsiPeriod()).isEqualTo(21);
    }
}
