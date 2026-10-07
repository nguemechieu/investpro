package org.investpro.core.agents.execution;

import org.investpro.exchange.Exchange;
import org.investpro.enums.CapitalProtection;
import org.investpro.risk.TradeRiskContext;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProtectedSubmissionTest {
    private TradeRiskContext context() {
        return TradeRiskContext.builder().executionAccountId("account")
                .requestedPositionSize(1).capitalProtection(CapitalProtection.STRICT_STOPS)
                .stopLossPrice(90).takeProfitPrice(120).build();
    }

    @Test void unsupportedProtectionBlocksBeforeAnySubmission() {
        Exchange exchange = mock(Exchange.class);
        assertThrows(UnsupportedOperationException.class,
                () -> ExecutionEngine.validateSubmissionPlan(exchange, Side.BUY, 100, context(), 1, "MARKET"));
        verify(exchange, never()).botOrderExecution();
    }

    @Test void advancedModesDoNotFallBackToMarketOrders() {
        Exchange exchange = mock(Exchange.class);
        for (String strategy : java.util.List.of("VWAP", "TWAP", "ICEBERG", "SCALED_ENTRY", "ALGORITHMIC")) {
            assertThrows(UnsupportedOperationException.class,
                    () -> ExecutionEngine.validateSubmissionPlan(exchange, Side.BUY, 100, context(), 1, strategy));
        }
        verifyNoInteractions(exchange);
    }

    @Test void supportedProtectionRetainsStopsAndQuantity() {
        Exchange exchange = mock(Exchange.class);
        when(exchange.supportsBracketOrders()).thenReturn(true);
        assertDoesNotThrow(() -> ExecutionEngine.validateSubmissionPlan(exchange, Side.BUY, 100, context(), 1, "LIMIT"));
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionEngine.validateSubmissionPlan(exchange, Side.BUY, 100, context(), 2, "LIMIT"));
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionEngine.validateSubmissionPlan(exchange, Side.SELL, 100, context(), 1, "MARKET"));
    }

    @Test void strictProtectionCannotUseMissingStop() {
        Exchange exchange = mock(Exchange.class);
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionEngine.validateSubmissionPlan(exchange, Side.BUY, 100,
                        context().toBuilder().stopLossPrice(0).build(), 1, "MARKET"));
    }
}
