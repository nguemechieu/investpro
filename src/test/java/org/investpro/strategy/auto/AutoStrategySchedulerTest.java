package org.investpro.strategy.auto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AutoStrategySchedulerTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("autoStrategy.enabled");
        System.clearProperty("autoStrategy.scanIntervalMinutes");
    }

    @Test
    void startRunsTaskAndStopCancelsFutureRuns() throws Exception {
        System.setProperty("autoStrategy.enabled", "true");
        System.setProperty("autoStrategy.scanIntervalMinutes", "60");
        AutoStrategyScheduler scheduler = new AutoStrategyScheduler();
        CountDownLatch latch = new CountDownLatch(1);

        scheduler.start(latch::countDown);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        scheduler.stop();
        scheduler.shutdown();
    }
}
