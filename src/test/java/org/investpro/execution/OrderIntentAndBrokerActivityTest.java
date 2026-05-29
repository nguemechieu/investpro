package org.investpro.execution;

import org.investpro.broker.events.BrokerActivityEvent;
import org.investpro.broker.events.BrokerActivityType;
import org.investpro.broker.repository.InMemoryBrokerActivityRepository;
import org.investpro.decision.BotTradeDecision;
import org.investpro.decision.BotTradeDecisionEngine;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderIntentAndBrokerActivityTest {

    @Test
    void orderIntentIsNotTradeAndSubmittedEventsAreNotFills() throws Exception {
        BotTradeDecisionEngine engine = new BotTradeDecisionEngine(null);
        BotTradeDecision decision = engine.evaluateSignal(
                TradePair.fromSymbol("EUR/USD"),
                Side.BUY,
                new Ticker(1.1001, 1.1000, 1.1002, 0.0, System.currentTimeMillis()),
                0.9);

        assertTrue(decision.finalAction().shouldTrade() || decision.finalAction() == BotTradeDecision.FinalAction.SKIP);

        OrderIntent intent = OrderIntent.fromDecision(
                decision,
                BigDecimal.valueOf(1.0),
                BigDecimal.valueOf(1.1002),
                "Pipeline approved order intent");

        assertNotNull(intent.intentId());
        assertNotEquals("", intent.intentId());

        InMemoryBrokerActivityRepository repository = new InMemoryBrokerActivityRepository();
        BrokerActivityEvent submitted = new BrokerActivityEvent(
                UUID.randomUUID().toString(),
                BrokerActivityType.ORDER_SUBMITTED,
                Instant.now(),
                "acct-1",
                intent.tradePair(),
                intent.side(),
                intent.quantity(),
                intent.requestedPrice(),
                "order-1",
                null,
                "Order was submitted");

        repository.append(submitted);
        assertEquals(1, repository.findByType(BrokerActivityType.ORDER_SUBMITTED).size());
        assertEquals(0, repository.findFills().size());

        BrokerActivityEvent filled = new BrokerActivityEvent(
                UUID.randomUUID().toString(),
                BrokerActivityType.ORDER_FILLED,
                Instant.now(),
                "acct-1",
                intent.tradePair(),
                intent.side(),
                intent.quantity(),
                intent.requestedPrice(),
                "order-1",
                "fill-1",
                "Order was filled");

        repository.append(filled);
        assertEquals(2, repository.findAll().size());
        assertEquals(1, repository.findFills().size());
    }
}
