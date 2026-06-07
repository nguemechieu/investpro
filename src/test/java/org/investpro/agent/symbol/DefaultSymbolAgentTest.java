package org.investpro.agent.symbol;

import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityType;
import org.investpro.data.CandleData;
import org.investpro.news.CryptoNewsItem;
import org.investpro.news.InMemoryNewsRepository;
import org.investpro.news.NewsCategory;
import org.investpro.news.NewsContextService;
import org.investpro.news.NewsImpact;
import org.investpro.news.NewsSourceType;
import org.investpro.news.NewsUrgency;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.utils.Side;
import org.investpro.models.trading.TradePair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSymbolAgentTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void doesNotEvaluateBeforeWarmup() throws Exception {
        RecordingStrategyEvaluator evaluator = new RecordingStrategyEvaluator(holdSignal());
        DefaultSymbolAgent agent = agent(evaluator, new RecordingRiskEvaluator(RiskDecision.approved(BigDecimal.ONE, "ok")),
                new RecordingExecutionEngine(), TradabilityDecision.allowed(), emptyNewsContext(), config(2, true));

        agent.start();
        agent.assignStrategy(strategy());
        agent.onCandle(candle(1));
        agent.evaluateNow("test").get(2, TimeUnit.SECONDS);

        assertEquals(0, evaluator.count.get());
        assertEquals(SymbolAgentStatus.WAITING_FOR_DATA, agent.state().status());
    }

    @Test
    void doesNotTradeWithoutAssignedStrategy() throws Exception {
        RecordingStrategyEvaluator evaluator = new RecordingStrategyEvaluator(buySignal());
        RecordingExecutionEngine execution = new RecordingExecutionEngine();
        DefaultSymbolAgent agent = agent(evaluator, new RecordingRiskEvaluator(RiskDecision.approved(BigDecimal.ONE, "ok")),
                execution, TradabilityDecision.allowed(), emptyNewsContext(), config(2, true));

        agent.start();
        warm(agent);
        agent.evaluateNow("test").get(2, TimeUnit.SECONDS);

        assertEquals(0, evaluator.count.get());
        assertEquals(0, execution.count.get());
        assertEquals(SymbolAgentStatus.STRATEGY_UNASSIGNED, agent.state().status());
    }

    @Test
    void doesNotTradeWhenTradabilityBlocks() throws Exception {
        RecordingStrategyEvaluator evaluator = new RecordingStrategyEvaluator(buySignal());
        RecordingExecutionEngine execution = new RecordingExecutionEngine();
        DefaultSymbolAgent agent = agent(evaluator, new RecordingRiskEvaluator(RiskDecision.approved(BigDecimal.ONE, "ok")),
                execution, TradabilityDecision.notTradable("view only"), emptyNewsContext(), config(2, true));

        agent.start();
        agent.assignStrategy(strategy());
        warm(agent);
        agent.evaluateNow("test").get(2, TimeUnit.SECONDS);

        assertEquals(0, evaluator.count.get());
        assertEquals(0, execution.count.get());
        assertFalse(agent.state().tradable());
        assertEquals(SymbolAgentStatus.WAITING_FOR_TRADABILITY, agent.state().status());
    }

    @Test
    void routesSignalThroughRiskAndBlocksRejectedIntent() throws Exception {
        RecordingStrategyEvaluator evaluator = new RecordingStrategyEvaluator(buySignal());
        RecordingRiskEvaluator risk = new RecordingRiskEvaluator(RiskDecision.rejected("too risky"));
        RecordingExecutionEngine execution = new RecordingExecutionEngine();
        DefaultSymbolAgent agent = agent(evaluator, risk, execution, TradabilityDecision.allowed(),
                emptyNewsContext(), config(2, true));

        agent.start();
        agent.assignStrategy(strategy());
        warm(agent);
        agent.evaluateNow("test").get(2, TimeUnit.SECONDS);

        assertEquals(1, evaluator.count.get());
        assertEquals(1, risk.count.get());
        assertEquals(0, execution.count.get());
        assertEquals(SymbolAgentStatus.RISK_BLOCKED, agent.state().status());
    }

    @Test
    void submitsApprovedIntentToExecutionEngine() throws Exception {
        RecordingRiskEvaluator risk = new RecordingRiskEvaluator(RiskDecision.approved(BigDecimal.ONE, "ok"));
        RecordingExecutionEngine execution = new RecordingExecutionEngine();
        DefaultSymbolAgent agent = agent(new RecordingStrategyEvaluator(buySignal()), risk, execution,
                TradabilityDecision.allowed(), emptyNewsContext(), config(2, true));

        agent.start();
        agent.assignStrategy(strategy());
        warm(agent);
        agent.evaluateNow("test").get(2, TimeUnit.SECONDS);

        assertEquals(1, risk.count.get());
        assertEquals(1, execution.count.get());
        assertEquals(OrderSide.BUY, execution.lastIntent.get().side());
        assertTrue(agent.state().hasPendingOrder());
        assertEquals(SymbolAgentStatus.ACTIVE, agent.state().status());
    }

    @Test
    void brokerActivityUpdatesPendingAndPositionState() throws Exception {
        DefaultSymbolAgent agent = agent(new RecordingStrategyEvaluator(buySignal()),
                new RecordingRiskEvaluator(RiskDecision.approved(BigDecimal.ONE, "ok")), new RecordingExecutionEngine(),
                TradabilityDecision.allowed(), emptyNewsContext(), config(2, true));

        agent.start();
        agent.assignStrategy(strategy());
        warm(agent);
        agent.evaluateNow("test").get(2, TimeUnit.SECONDS);
        assertTrue(agent.state().hasPendingOrder());

        agent.onBrokerActivity(activity(BrokerActivityType.ORDER_FILLED));
        assertFalse(agent.state().hasPendingOrder());
        assertTrue(agent.state().hasOpenPosition());

        agent.onBrokerActivity(activity(BrokerActivityType.POSITION_CLOSED));
        assertFalse(agent.state().hasOpenPosition());
    }

    @Test
    void criticalNegativeNewsBlocksNewEntryBeforeRiskAndExecution() throws Exception {
        RecordingStrategyEvaluator evaluator = new RecordingStrategyEvaluator(buySignal());
        RecordingRiskEvaluator risk = new RecordingRiskEvaluator(RiskDecision.approved(BigDecimal.ONE, "ok"));
        RecordingExecutionEngine execution = new RecordingExecutionEngine();
        DefaultSymbolAgent agent = agent(evaluator, risk, execution, TradabilityDecision.allowed(),
                criticalNewsContext(), config(2, true));

        agent.start();
        agent.assignStrategy(strategy());
        warm(agent);
        agent.evaluateNow("test").get(2, TimeUnit.SECONDS);

        assertEquals(0, evaluator.count.get());
        assertEquals(0, risk.count.get());
        assertEquals(0, execution.count.get());
        assertEquals(SymbolAgentStatus.RISK_BLOCKED, agent.state().status());
    }

    private DefaultSymbolAgent agent(StrategyEvaluator evaluator,
                                     SymbolRiskEvaluator risk,
                                     SymbolExecutionEngine execution,
                                     TradabilityDecision tradabilityDecision,
                                     NewsContextService newsContextService,
                                     SymbolAgentConfig config) throws SQLException, ClassNotFoundException {
        return new DefaultSymbolAgent("Coinbase", pair(), SymbolAgentMode.PAPER, config, executor, evaluator, risk,
                execution, (exchangeId, tradePair, mode) -> tradabilityDecision, newsContextService, null);
    }

    private static SymbolAgentConfig config(int warmupCandles, boolean blockOnNews) {
        return new SymbolAgentConfig(warmupCandles, 20, false, false, false, true, blockOnNews,
                Duration.ZERO, Duration.ofHours(1), Duration.ofMinutes(5), true);
    }

    private static void warm(DefaultSymbolAgent agent) {
        agent.onCandle(candle(1));
        agent.onCandle(candle(2));
    }

    private static CandleData candle(int index) {
        BigDecimal price = BigDecimal.valueOf(100 + index);
        return new CandleData(price.doubleValue(), price.add(BigDecimal.ONE).doubleValue(),
                price.add(BigDecimal.TEN).doubleValue(), price.subtract(BigDecimal.ONE).doubleValue(),
                (int) Instant.now().plusSeconds(index).getEpochSecond(), BigDecimal.TEN.doubleValue());
    }

    private static AgentStrategySignal holdSignal() {
        return new AgentStrategySignal(SignalType.HOLD, 0.1, "hold", Map.of());
    }

    private static AgentStrategySignal buySignal() {
        return new AgentStrategySignal(SignalType.BUY, 0.8, "buy",
                Map.of("quantity", "0.01"));
    }

    private static StrategyDefinition strategy() {
        return StrategyDefinition.builder()
                .name("Test Strategy")
                .baseName("Test")
                .build();
    }

    private static TradePair pair() throws SQLException, ClassNotFoundException {
        return new TradePair("BTC", "USDC");
    }

    private static BrokerActivityEvent activity(BrokerActivityType type) throws SQLException, ClassNotFoundException {
        return BrokerActivityEvent.builder()
                .eventId("test-" + type)
                .exchangeId("Coinbase")
                .activityType(type)
                .tradePair(pair())
                .side(Side.BUY)
                .eventTime(Instant.now())
                .build();
    }

    private static NewsContextService emptyNewsContext() {
        return new NewsContextService(new InMemoryNewsRepository());
    }

    private static NewsContextService criticalNewsContext() {
        InMemoryNewsRepository repository = new InMemoryNewsRepository();
        repository.saveAll(List.of(new CryptoNewsItem("critical-btc", "test", "Test",
                "BTC exchange halted after exploit", "Critical negative BTC news",
                "https://example.com/btc", LocalDateTime.now(), LocalDateTime.now(),
                NewsSourceType.RSS, NewsCategory.SECURITY, NewsImpact.VERY_NEGATIVE,
                NewsUrgency.CRITICAL, Set.of("BTC"), Set.of("BTC"), -0.9, 1.0,
                false, null, Map.of())));
        return new NewsContextService(repository);
    }

    private static final class RecordingStrategyEvaluator implements StrategyEvaluator {
        private final AtomicInteger count = new AtomicInteger();
        private final AgentStrategySignal signal;

        private RecordingStrategyEvaluator(AgentStrategySignal signal) {
            this.signal = signal;
        }

        @Override
        public AgentStrategySignal evaluate(StrategyDefinition strategyDefinition, TradePair pair,
                                            List<CandleData> candles, MarketContext context) {
            count.incrementAndGet();
            return signal;
        }
    }

    private static final class RecordingRiskEvaluator implements SymbolRiskEvaluator {
        private final AtomicInteger count = new AtomicInteger();
        private final RiskDecision decision;

        private RecordingRiskEvaluator(RiskDecision decision) {
            this.decision = decision;
        }

        @Override
        public RiskDecision evaluateTradeIntent(TradeIntent intent, SymbolAgentState state, MarketContext context) {
            count.incrementAndGet();
            return decision;
        }
    }

    private static final class RecordingExecutionEngine implements SymbolExecutionEngine {
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicReference<TradeIntent> lastIntent = new AtomicReference<>();

        @Override
        public void submitTradeIntent(TradeIntent intent, RiskDecision riskDecision) {
            count.incrementAndGet();
            lastIntent.set(intent);
        }
    }
}
