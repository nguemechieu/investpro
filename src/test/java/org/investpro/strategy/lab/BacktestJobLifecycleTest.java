package org.investpro.strategy.lab;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BacktestJobLifecycleTest {
    private BacktestJob job(AtomicInteger notifications) {
        return new BacktestJob(StrategyBacktestRequest.builder().symbol("BTC/USD").timeframe(org.investpro.enums.timeframe.Timeframe.values()[0]).strategyName("test").candles(java.util.List.of()).build(), BacktestJobPriority.values()[0],
                _ -> notifications.incrementAndGet());
    }
    @Test void cancelledQueuedJobsCannotBeRestarted() {
        var notifications = new AtomicInteger(); var job = job(notifications); var worker = mock(Thread.class);
        assertTrue(job.cancel()); job.markRunning(worker);
        assertEquals(BacktestJobStatus.CANCELLED, job.getStatus());
        assertTrue(job.getFuture().isCancelled()); assertEquals(1, notifications.get());
        verifyNoInteractions(worker);
    }
    @Test void cancelledRunningJobsCannotBeOverwrittenByLateResultsOrErrors() {
        var notifications = new AtomicInteger(); var job = job(notifications); var worker = mock(Thread.class);
        job.markRunning(worker); assertTrue(job.cancel());
        job.markCompleted(StrategyPerformanceReport.builder().strategyName("test").baseStrategyName("test").symbol("BTC/USD").timeframe(org.investpro.enums.timeframe.Timeframe.values()[0]).build()); job.markFailed(new RuntimeException("late"));
        assertEquals(BacktestJobStatus.CANCELLED, job.getStatus());
        assertTrue(job.getFuture().isCancelled()); assertEquals(1, notifications.get());
        verify(worker).interrupt();
    }
    @Test void completedJobsRemainTerminal() {
        var notifications = new AtomicInteger(); var job = job(notifications); var worker = mock(Thread.class);
        job.markRunning(worker); var report = StrategyPerformanceReport.builder().strategyName("test").baseStrategyName("test").symbol("BTC/USD").timeframe(org.investpro.enums.timeframe.Timeframe.values()[0]).build(); job.markCompleted(report);
        assertFalse(job.cancel()); job.markFailed(new RuntimeException("late"));
        assertSame(report, job.getFuture().join()); assertEquals(BacktestJobStatus.COMPLETED, job.getStatus());
        assertEquals(1, notifications.get()); verify(worker, never()).interrupt();
    }
}
