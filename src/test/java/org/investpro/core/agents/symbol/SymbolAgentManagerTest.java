package org.investpro.core.agents.symbol;

import org.investpro.models.trading.TradePair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolAgentManagerTest {

    @Test
    void tracksSymbolsByStableSymbolKeyWhenQuotesMutate() throws Exception {
        SymbolAgentManager manager = new SymbolAgentManager();
        TradePair symbol = TradePair.of("BTC", "USD");

        SymbolAgentState initialState = manager.ensureSymbol(symbol);
        symbol.updateQuote(6.43683, 6.43815);

        SymbolAgentState currentState = manager.ensureSymbol(symbol);

        assertSame(initialState, currentState);
        assertEquals(1, manager.getTotalSymbolCount());
        assertTrue(manager.getState(symbol).isPresent());
    }
}
