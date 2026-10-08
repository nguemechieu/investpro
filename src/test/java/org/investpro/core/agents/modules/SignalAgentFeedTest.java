package org.investpro.core.agents.modules;

import org.investpro.core.agents.*;
import org.investpro.data.CandleData;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.*;
import org.investpro.service.StrategyDecisionService;
import org.investpro.strategy.*;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;

class SignalAgentFeedTest {
    @Test void indicativeQuotesCannotProduceExecutableStrategySignals() throws Exception {
        var service = mock(StrategyDecisionService.class);
        var bus = mock(AgentEventBus.class);
        var pair = new TradePair("AAPL", "USD");
        var context = new AgentContext();
        context.setEventBus(bus);
        var agent = new SignalAgent(service);
        agent.start(context);
        Map<String,Object> metadata = Map.of("symbol", "AAPL/USD", "timeframe", "1h", "tradePairObject", pair);
        var candles = new ArrayList<CandleData>();
        for (int i = 0; i < 120; i++) candles.add(new CandleData(200, 200.5, 201, 199, 100000 + i * 3600, 10));
        for (var type : List.of(Ticker.QuoteType.DELAYED, Ticker.QuoteType.FROZEN, Ticker.QuoteType.DELAYED_FROZEN)) {
            var quote = new Ticker(200.5, 200, 201, 10, System.currentTimeMillis());
            quote.setQuoteType(type);
            agent.onEvent(new AgentEvent(AgentEvent.MARKET_TICK, "test", quote, java.time.Instant.now(), metadata));
            agent.onEvent(new AgentEvent(AgentEvent.MARKET_CANDLE, "test", candles, java.time.Instant.now(), metadata));
        }
        verifyNoInteractions(service, bus);
        agent.stop();
    }
    @Test void readyAssignmentCanRetrySameBarButPublishedSignalIsNotRepeated() throws Exception {
        var service = mock(StrategyDecisionService.class);
        var bus = mock(AgentEventBus.class);
        var exchange = mock(Exchange.class);
        var pair = new TradePair("BTC", "USD");
        var context = new AgentContext(); context.setExchange(exchange); context.setEventBus(bus);
        var agent = new SignalAgent(service); agent.start(context);
        var signal = StrategySignal.builder().symbol("BTC/USD").timeframe("1h").strategyId("test")
                .strategyName("Test").side(Side.BUY).confidence(0.8).build();
        when(service.generateDecision(anyString(), anyString(), anyList(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), any(), eq(pair)))
                .thenReturn(StrategyDecisionResult.rejected("Not assigned yet", List.of()),
                        StrategyDecisionResult.success("test", null, signal, List.of()));
        Ticker quote = new Ticker(); quote.setBidPrice(100); quote.setAskPrice(101);
        quote.setTimestamp(System.currentTimeMillis());
        Map<String,Object> metadata = Map.of("symbol", "BTC/USD", "timeframe", "1h", "tradePairObject", pair);
        agent.onEvent(new AgentEvent(AgentEvent.MARKET_TICK, "test", quote, java.time.Instant.now(), metadata));
        var candles = new ArrayList<CandleData>();
        for (int i = 0; i < 120; i++) candles.add(new CandleData(100, 100.5, 101, 99, 100000 + i * 3600, 10));
        var candle = new AgentEvent(AgentEvent.MARKET_CANDLE, "test", candles, java.time.Instant.now(), metadata);
        agent.onEvent(candle); agent.onEvent(candle); agent.onEvent(candle);
        verify(bus, times(1)).publish(argThat(event -> AgentEvent.SIGNAL_CREATED.equals(event.type())
                && event.metadata().get("tradePairObject") == pair));
        candles.add(new CandleData(100, 100.5, 101, 99, 100000 + 120 * 3600, 10));
        agent.onEvent(candle);
        verify(bus, times(2)).publish(any());
        verify(exchange, never()).getTradePairSymbol();
        agent.stop();
    }
}
