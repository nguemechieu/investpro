package org.investpro.agent.symbol;

import java.util.List;
import java.util.Optional;

public interface SymbolAgentStateRepository {
    void saveState(SymbolAgentState state);

    Optional<SymbolAgentState> findState(String exchangeId, String symbol);

    List<SymbolAgentState> findAllActiveStates();

    void deleteState(String exchangeId, String symbol);
}
