package org.investpro.ui;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.investpro.core.SystemCore;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;
import org.investpro.ui.charts.ChartContainer;
import org.investpro.ui.panels.OrderPanel;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UiLoadingResponsivenessTest {
    @Test void strategyLabHealthQueryDoesNotBlockJavaFx() throws Exception {
        new JFXPanel();
        var core = mock(SystemCore.class);
        var exchange = mock(Exchange.class);
        var lab = mock(org.investpro.strategy.lab.StrategyLabService.class);
        var snapshot = mock(org.investpro.strategy.lab.StrategyLabSnapshot.class);
        var ai = mock(org.investpro.ai.local.grpc.LocalAiRuntimeService.class);
        when(core.getExchange()).thenReturn(exchange);
        when(core.getLabService()).thenReturn(lab);
        when(core.getAiReasoningService()).thenReturn(ai);
        when(exchange.getSupportedTimeframes()).thenReturn(List.of(Timeframe.H1));
        when(exchange.getTradePairSymbol()).thenReturn(List.of());
        when(lab.getSnapshot(any(), any())).thenReturn(snapshot);
        when(snapshot.getRankings()).thenReturn(List.of());
        var requested = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(ai.healthStatus()).thenAnswer(_ -> {
            assertFalse(Platform.isFxApplicationThread());
            requested.countDown(); release.await();
            return org.investpro.ai.local.grpc.AiGrpcHealthStatus.unavailable("Test health response");
        });
        try {
            fx(() -> new StrategyLabPanel(core));
            assertTrue(requested.await(3, TimeUnit.SECONDS));
            assertEquals("heartbeat", fx(() -> "heartbeat"));
        } finally { release.countDown(); }
    }

    private static <T> T fx(Callable<T> operation) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> { try { result.complete(operation.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result.get(5, TimeUnit.SECONDS);
    }

    @Test void orderTicketAndUiRemainResponsiveWhileVenueDiscoveryIsBlocked() throws Exception {
        new JFXPanel();
        var exchange = mock(Exchange.class);
        var core = mock(SystemCore.class);
        when(core.getExchange()).thenReturn(exchange);
        var requested = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(exchange.getTradePairSymbol()).thenAnswer(_ -> {
            assertFalse(Platform.isFxApplicationThread());
            requested.countDown(); release.await(); return List.of();
        });
        try {
            OrderPanel panel = fx(() -> new OrderPanel(core));
            assertTrue(requested.await(2, TimeUnit.SECONDS));
            assertTrue(fx(() -> panel.getPlaceOrderButton().isDisabled()));
            assertEquals("heartbeat", fx(() -> "heartbeat"));
        } finally { release.countDown(); }
    }

    @Test void contractResolutionDoesNotFreezeChartsAndClosedChartsIgnoreLateResults() throws Exception {
        new JFXPanel();
        var exchange = mock(Exchange.class);
        when(exchange.getName()).thenReturn("Slow test venue");
        when(exchange.getCapability()).thenReturn(ExchangeCapability.builder().supportsHistoricalCandles(true).build());
        when(exchange.getSupportedTimeframes()).thenReturn(List.of(Timeframe.H1));
        var requested = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        when(exchange.getCandleDataSupplier(anyInt(), any())).thenAnswer(_ -> {
            assertFalse(Platform.isFxApplicationThread());
            requested.countDown();
            try { release.await(); }
            catch (InterruptedException error) { interrupted.countDown(); throw error; }
            return null;
        });
        try {
            ChartContainer container = fx(() -> new ChartContainer(exchange, new TradePair("AAPL", "USD"),
                    false, "", mock(TradingService.class)));
            assertTrue(requested.await(2, TimeUnit.SECONDS));
            assertEquals("heartbeat", fx(() -> "heartbeat"));
            fx(() -> { container.dispose(); return null; });
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            release.countDown();
            assertNull(fx(container::getChart));
        } finally { release.countDown(); }
    }
}
