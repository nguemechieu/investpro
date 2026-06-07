package org.investpro.agent;

import org.investpro.agent.symbol.InMemorySymbolAgentStateRepository;
import org.investpro.agent.symbol.SymbolAgent;
import org.investpro.agent.symbol.SymbolAgentConfig;
import org.investpro.agent.symbol.SymbolAgentMode;
import org.investpro.agent.symbol.SymbolAgentStatus;
import org.investpro.agent.symbol.SymbolAgentState;
import org.investpro.models.trading.TradePair;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class TradingAgentManagerTest {

    @Test
    void startsStopsAndPersistsSymbolAgentState() throws Exception {
        InMemorySymbolAgentStateRepository repository = new InMemorySymbolAgentStateRepository();
        TradingAgentManager manager = manager(2, repository);

        SymbolAgent agent = manager.startAgent("Coinbase", pair("BTC", "USDC"), SymbolAgentMode.PAPER);

        assertTrue(manager.getAgent("Coinbase", pair("BTC", "USDC")).isPresent());
        assertTrue(repository.findState("Coinbase", pair("BTC", "USDC").toString('/')).isPresent());
        assertNotEquals(SymbolAgentStatus.STOPPED, agent.state().status());

        manager.stopAgent("Coinbase", pair("BTC", "USDC"));

        assertTrue(manager.getAgent("Coinbase", pair("BTC", "USDC")).isEmpty());
        assertTrue(repository.findState("Coinbase", pair("BTC", "USDC").toString('/')).isPresent());
        assertEquals(SymbolAgentStatus.STOPPED,
                repository.findState("Coinbase", pair("BTC", "USDC").toString('/')).map(SymbolAgentState::status).orElseThrow());

        manager.stopAll();
    }

    @Test
    void enforcesMaximumActiveAgents() throws Exception {
        TradingAgentManager manager = manager(1, new InMemorySymbolAgentStateRepository());

        manager.startAgent("Coinbase", pair("BTC", "USDC"), SymbolAgentMode.PAPER);

        assertThrows(IllegalStateException.class,
                () -> manager.startAgent("Coinbase", pair("ETH", "USDC"), SymbolAgentMode.PAPER));

        manager.stopAll();
    }

    private static TradingAgentManager manager(int maxAgents, InMemorySymbolAgentStateRepository repository) {
        return new TradingAgentManager(
                maxAgents,
                Executors.newFixedThreadPool(2),
                Executors.newSingleThreadScheduledExecutor(),
                new MarketDataRouter(),
                repository,
                (strategy, pair, candles, context) -> new org.investpro.agent.symbol.AgentStrategySignal(
                        org.investpro.agent.symbol.SignalType.NEUTRAL, 0.0, "test", java.util.Map.of()),
                (intent, state, context) -> org.investpro.agent.symbol.RiskDecision.rejected("test"),
                (intent, decision) -> { },
                (exchangeId, pair, mode) -> org.investpro.agent.symbol.TradabilityDecision.allowed(),
                null,
                null,
                new SymbolAgentConfig(2, 20, false, false, false, true, true,
                        Duration.ZERO, Duration.ofHours(1), Duration.ofMinutes(5), true)
        );
    }

    private static TradePair pair(String base, String quote) throws SQLException, ClassNotFoundException {
        return new TradePair(base, quote);
    }
}
