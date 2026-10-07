package org.investpro.strategy.execution;

import org.investpro.exchange.execution.ExecutionMode;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.lifecycle.StrategyLifecycleStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionRouterTest {
    @Test
    void paperAndLiveStrategiesKeepTheSameExchangeDestination() {
        var router = ExecutionRouter.getInstance();
        router.recoverVenue(ExecutionVenue.COINBASE);
        var plan = ExecutionPlan.builder().symbol("BTC/USD").isValid(true).build();
        var paper = router.route(plan, StrategyLifecycleRecord.builder()
                .lifecycleStatus(StrategyLifecycleStatus.PAPER_TRADING).build());
        var live = router.route(plan, StrategyLifecycleRecord.builder()
                .lifecycleStatus(StrategyLifecycleStatus.LIVE_ACTIVE).build());
        assertEquals(ExecutionVenue.COINBASE, paper.getVenue());
        assertEquals(paper.getVenue(), live.getVenue());
        assertEquals(ExecutionMode.LOCAL_PAPER, paper.getExecutionMode());
        assertEquals(ExecutionMode.LIVE, live.getExecutionMode());
    }

    @Test
    void unavailableExchangeDoesNotBecomePaperRouteOrChangeExecutionMode() {
        var router = ExecutionRouter.getInstance();
        try {
            for (int i = 0; i < 5; i++) router.recordVenueError(ExecutionVenue.COINBASE_ADVANCED);
            var routed = router.route(ExecutionPlan.builder().symbol("BTC/USD")
                    .venue(ExecutionVenue.COINBASE).executionMode(ExecutionMode.LIVE).isValid(true).build(), null);
            assertEquals(ExecutionVenue.UNKNOWN, routed.getVenue());
            assertEquals(ExecutionMode.LIVE, routed.getExecutionMode());
            assertFalse(routed.isValid());
        } finally { router.recoverVenue(ExecutionVenue.COINBASE); }
    }

    @Test
    void equityDestinationIsBrokerAndLegacyBrokerAliasBecomesCanonical() {
        var router = ExecutionRouter.getInstance();
        router.recoverVenue(ExecutionVenue.INTERACTIVE_BROKERS);
        router.recoverVenue(ExecutionVenue.OANDA);
        assertEquals(ExecutionVenue.INTERACTIVE_BROKERS,
                router.route(ExecutionPlan.builder().symbol("AAPL").build(), null).getVenue());
        assertEquals(ExecutionVenue.OANDA, router.route(ExecutionPlan.builder().symbol("EUR/USD")
                .venue(ExecutionVenue.OANDA_REST).build(), null).getVenue());
    }
}
