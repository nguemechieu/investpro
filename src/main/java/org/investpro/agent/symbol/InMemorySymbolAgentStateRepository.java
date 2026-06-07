package org.investpro.agent.symbol;

import org.investpro.agent.AgentKey;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemorySymbolAgentStateRepository implements SymbolAgentStateRepository {

    private final ConcurrentMap<AgentKey, SymbolAgentState> states = new ConcurrentHashMap<>();

    @Override
    public void saveState(SymbolAgentState state) {
        if (state == null || state.pair() == null) {
            return;
        }
        states.put(AgentKey.of(state.exchangeId(), state.pair()), state);
    }

    @Override
    public Optional<SymbolAgentState> findState(String exchangeId, String symbol) {
        return Optional.ofNullable(states.get(new AgentKey(exchangeId, symbol)));
    }

    @Override
    public List<SymbolAgentState> findAllActiveStates() {
        return states.values().stream()
                .filter(state -> state.status() != SymbolAgentStatus.STOPPED)
                .toList();
    }

    @Override
    public void deleteState(String exchangeId, String symbol) {
        states.remove(new AgentKey(exchangeId, symbol));
    }
}
