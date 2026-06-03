package org.investpro.strategy.user;

import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.api.UserStrategy;
import org.investpro.strategy.impl.UserStrategyAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserStrategySystemAcceptanceTest {

    @TempDir
    Path tempDir;

    @Test
    void emptyStrategiesDirectoryLoadsSafely() {
        UserStrategyLoader loader = new UserStrategyLoader(tempDir);

        int loaded = loader.loadIntoRegistry();

        assertThat(loaded).isEqualTo(0);
        assertThat(loader.getLoadedCount()).isEqualTo(0);
        assertThat(loader.getFailedCount()).isEqualTo(0);
    }

    @Test
    void brokenJarDoesNotCrashLoader() throws Exception {
        Path invalidJar = tempDir.resolve("broken-strategy.jar");
        Files.writeString(invalidJar, "not-a-jar");

        UserStrategyLoader loader = new UserStrategyLoader(tempDir);
        int loaded = loader.loadIntoRegistry();

        assertThat(loaded).isEqualTo(0);
        assertThat(loader.getFailedCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void duplicateUserStrategyIdsAreRejected() {
        String strategyId = "user-dup-" + UUID.randomUUID().toString().substring(0, 8);
        StrategyRegistry registry = StrategyRegistry.getInstance();
        registry.unregister(strategyId);

        UserStrategyAdapter first = new UserStrategyAdapter(new TestUserStrategy(strategyId, "First"));
        UserStrategyAdapter second = new UserStrategyAdapter(new TestUserStrategy(strategyId, "Second"));

        registry.register(strategyId, first);
        registry.register(strategyId, second);

        Optional<?> resolved = registry.findById(strategyId);
        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow()).isSameAs(first);

        registry.unregister(strategyId);
    }

    private record TestUserStrategy(String id, String name) implements UserStrategy {

        @Override
            public StrategySignal generateSignal(StrategyContext context) {
                return StrategySignal.hold("BTC/USD", "1h", id, "test");
            }
        }
}
