package org.investpro.core.pipeline;

import org.investpro.exchange.Exchange;
import org.investpro.exchange.contracts.OrderExecutionProvider;
import org.investpro.models.Account;
import org.investpro.models.trading.*;
import org.investpro.risk.TradeRiskContext;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotRiskContextServiceTest {
    @Test void strategiesCanDelegateQuantityToRiskUsingActualFunds() {
        Exchange exchange = exchange();
        TradePair pair = mock(TradePair.class);
        when(exchange.getName()).thenReturn("test");
        var signal = org.investpro.strategy.StrategySignal.builder().side(Side.BUY)
                .confidence(.84).entryPrice(100).stopLossPrice(90).takeProfitPrice(120).build();
        var context = BotRiskContextService.fromSignal(exchange, pair, signal);
        assertEquals(250, context.getAccountEquity());
        assertTrue(context.getRequestedPositionSize() > 0);
        assertTrue(context.getRequestedPositionSize() <= 1); // $100 available at $100 per unit.
    }
    private Exchange exchange() {
        Exchange exchange = mock(Exchange.class);
        Account account = new Account();
        account.setAccountId("real-account");
        account.setConnected(true);
        account.setEquity(250);
        account.setAvailableBalance(100);
        when(exchange.fetchAccount()).thenReturn(CompletableFuture.completedFuture(account));
        when(exchange.fetchTicker(any())).thenAnswer(_ -> CompletableFuture.completedFuture(
                new Ticker(100, 99, 101, 0, System.currentTimeMillis())));
        when(exchange.fetchAllPositions()).thenReturn(CompletableFuture.completedFuture(List.of()));
        var provider = mock(OrderExecutionProvider.class);
        when(exchange.botOrderExecution()).thenReturn(provider);
        when(provider.fetchAllOpenOrders()).thenReturn(CompletableFuture.completedFuture(List.of()));
        return exchange;
    }

    @Test void replacesAssumedBalancesWithExecutionAccountAndPortfolioRisk() {
        Exchange exchange = exchange();
        Position position = new Position(mock(TradePair.class), Side.BUY, 2, 100);
        position.setStopLoss(90);
        when(exchange.fetchAllPositions()).thenReturn(CompletableFuture.completedFuture(List.of(position)));
        OpenOrder pending = new OpenOrder();
        pending.setPrice(50);
        pending.setRemainingSize(.1);
        when(exchange.botOrderExecution().fetchAllOpenOrders()).thenReturn(CompletableFuture.completedFuture(List.of(pending)));
        var refreshed = BotRiskContextService.refresh(exchange,
                TradeRiskContext.builder().accountEquity(1000).availableCash(1000).build());
        assertEquals(250, refreshed.getAccountEquity());
        assertEquals(100, refreshed.getAvailableCash());
        assertEquals(25, refreshed.getCurrentOpenRisk());
        assertEquals("real-account", refreshed.getExecutionAccountId());
        assertEquals(99, refreshed.getBidPrice());
    }

    @Test void positionFetchFailureBlocksInsteadOfAssumingNoExposure() {
        Exchange exchange = exchange();
        when(exchange.fetchAllPositions()).thenReturn(CompletableFuture.failedFuture(new RuntimeException("offline")));
        assertThrows(IllegalStateException.class, () -> BotRiskContextService.refresh(exchange, TradeRiskContext.builder().build()));
    }

    @Test void missingOrStaleObservedQuoteBlocksEntry() {
        Exchange exchange = exchange();
        when(exchange.fetchTicker(any())).thenReturn(CompletableFuture.completedFuture(
                new Ticker(100, 99, 101, 0, System.currentTimeMillis() - 60_000)));
        assertThrows(IllegalStateException.class, () -> BotRiskContextService.refresh(exchange, TradeRiskContext.builder().build()));
    }

    @Test void unprotectedPositionConsumesFullExposure() {
        Position position = new Position(mock(TradePair.class), Side.BUY, 2, 100);
        assertEquals(200, BotRiskContextService.openRisk(List.of(position)));
    }
}
