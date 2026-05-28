package org.investpro.operations;

import org.investpro.execution.lifecycle.ExecutionLifecycleMonitor;
import org.investpro.execution.lifecycle.ExecutionLifecycleState;
import org.investpro.execution.lifecycle.ExecutionLifecycleStatus;
import org.investpro.portfolio.intelligence.PortfolioIntelligenceService;
import org.investpro.strategy.provider.StrategyComplexity;
import org.investpro.telemetry.EventBusMetricsEngine;
import org.investpro.telemetry.StrategyProfiler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantRuntimeFoundationTest {

    @Test
    void eventBusMetricsTracksPublishedEvents() {
        EventBusMetricsEngine metrics = EventBusMetricsEngine.getInstance();
        long before = metrics.snapshot().totalEvents();

        metrics.recordPublished("TEST_EVENT");
        metrics.recordConsumerLatency("TEST_EVENT", 60_000_000L);

        var snapshot = metrics.snapshot();
        assertTrue(snapshot.totalEvents() >= before + 1);
        assertTrue(snapshot.slowConsumerEvents() >= 1);
        assertTrue(snapshot.eventsByType().getOrDefault("TEST_EVENT", 0L) >= 1);
    }

    @Test
    void executionLifecycleKeepsActiveOrdersUntilTerminalState() {
        ExecutionLifecycleMonitor monitor = ExecutionLifecycleMonitor.getInstance();
        String id = "test-" + System.nanoTime();

        monitor.update(new ExecutionLifecycleStatus(
                id,
                "",
                "paper",
                "EUR_USD",
                ExecutionLifecycleState.SUBMITTED,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "Submitted locally",
                0,
                Instant.now(),
                Map.of()));

        assertTrue(monitor.active().stream().anyMatch(status -> id.equals(status.clientOrderId())));

        monitor.transition(id, ExecutionLifecycleState.FILLED, BigDecimal.ONE, BigDecimal.TEN, "Broker fill");

        assertFalse(monitor.active().stream().anyMatch(status -> id.equals(status.clientOrderId())));
    }

    @Test
    void profilerExposesSlowStrategySnapshot() {
        StrategyProfiler profiler = StrategyProfiler.getInstance();
        String id = "profile-test-" + System.nanoTime();

        profiler.record(id, 10, 1_000_000L, StrategyComplexity.LIGHT);

        assertTrue(profiler.topByAverageLatency(20).stream()
                .anyMatch(snapshot -> id.equals(snapshot.strategyId())));
    }

    @Test
    void portfolioIntelligenceReportsConcentration() {
        PortfolioIntelligenceService service = PortfolioIntelligenceService.getInstance();

        var snapshot = service.analyze(List.of(
                new PortfolioIntelligenceService.ExposureRow("BTC-USD", "CRYPTO", new BigDecimal("100"), true, false),
                new PortfolioIntelligenceService.ExposureRow("ETH-USD", "CRYPTO", new BigDecimal("50"), true, false)));

        assertTrue(snapshot.grossExposure().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(snapshot.concentrationScore().compareTo(BigDecimal.ZERO) > 0);
    }
}
