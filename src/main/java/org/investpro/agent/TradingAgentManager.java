package org.investpro.agent;

import lombok.extern.slf4j.Slf4j;
import org.investpro.agent.symbol.DefaultStrategyEvaluator;
import org.investpro.agent.symbol.DefaultSymbolAgent;
import org.investpro.agent.symbol.LoggingExecutionEngine;
import org.investpro.agent.symbol.SafeDefaultRiskEvaluator;
import org.investpro.agent.symbol.StrategyEvaluator;
import org.investpro.agent.symbol.SymbolAgent;
import org.investpro.agent.symbol.SymbolAgentConfig;
import org.investpro.agent.symbol.SymbolAgentMode;
import org.investpro.agent.symbol.SymbolAgentState;
import org.investpro.agent.symbol.SymbolAgentStateRepository;
import org.investpro.agent.symbol.SymbolExecutionEngine;
import org.investpro.agent.symbol.SymbolRiskEvaluator;
import org.investpro.agent.symbol.TradabilityChecker;
import org.investpro.agent.symbol.TradabilityDecision;
import org.investpro.config.AppConfig;
import org.investpro.models.trading.TradePair;
import org.investpro.news.NewsContextService;
import org.investpro.strategy.StrategyDefinition;
import org.investpro.strategy.auto.AutoStrategyLab;
import org.investpro.agent.symbol.InMemorySymbolAgentStateRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TradingAgentManager {

    private final ConcurrentMap<AgentKey, SymbolAgent> agents = new ConcurrentHashMap<>();
    private final int maxActiveAgents;
    private final ExecutorService evaluationExecutor;
    private final ScheduledExecutorService scheduler;
    private final MarketDataRouter marketDataRouter;
    private final SymbolAgentStateRepository stateRepository;
    private final StrategyEvaluator strategyEvaluator;
    private final SymbolRiskEvaluator riskEvaluator;
    private final SymbolExecutionEngine executionEngine;
    private final TradabilityChecker tradabilityChecker;
    private final NewsContextService newsContextService;
    private final AutoStrategyLab autoStrategyLab;
    private final SymbolAgentConfig config;

    public TradingAgentManager() {
        this(
                AppConfig.getInt("agent.maxActiveAgents", 50),
                Executors.newFixedThreadPool(Math.max(1, AppConfig.getInt("agent.maxConcurrentEvaluations", 8)), runnable -> {
                    Thread thread = new Thread(runnable, "symbol-agent-evaluator");
                    thread.setDaemon(true);
                    return thread;
                }),
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "symbol-agent-health");
                    thread.setDaemon(true);
                    return thread;
                }),
                new MarketDataRouter(),
                new InMemorySymbolAgentStateRepository(),
                new DefaultStrategyEvaluator(),
                new SafeDefaultRiskEvaluator(),
                new LoggingExecutionEngine(),
                (exchangeId, pair, mode) -> TradabilityDecision.allowed(),
                null,
                null,
                SymbolAgentConfig.defaults());
    }

    public TradingAgentManager(
            int maxActiveAgents,
            ExecutorService evaluationExecutor,
            ScheduledExecutorService scheduler,
            MarketDataRouter marketDataRouter,
            SymbolAgentStateRepository stateRepository,
            StrategyEvaluator strategyEvaluator,
            SymbolRiskEvaluator riskEvaluator,
            SymbolExecutionEngine executionEngine,
            TradabilityChecker tradabilityChecker,
            NewsContextService newsContextService,
            AutoStrategyLab autoStrategyLab,
            SymbolAgentConfig config) {
        this.maxActiveAgents = Math.max(1, maxActiveAgents);
        this.evaluationExecutor = evaluationExecutor;
        this.scheduler = scheduler;
        this.marketDataRouter = marketDataRouter;
        this.stateRepository = stateRepository;
        this.strategyEvaluator = strategyEvaluator;
        this.riskEvaluator = riskEvaluator;
        this.executionEngine = executionEngine;
        this.tradabilityChecker = tradabilityChecker;
        this.newsContextService = newsContextService;
        this.autoStrategyLab = autoStrategyLab;
        this.config = config == null ? SymbolAgentConfig.defaults() : config;
        startHealthPersistence();
    }

    public SymbolAgent startAgent(String exchangeId, TradePair pair, SymbolAgentMode mode) {
        AgentKey key = AgentKey.of(exchangeId, pair);
        SymbolAgent existing = agents.get(key);
        if (existing != null) {
            existing.resume();
            return existing;
        }
        if (agents.size() >= maxActiveAgents) {
            throw new IllegalStateException("Max active symbol agents reached: " + maxActiveAgents);
        }
        SymbolAgent agent = new DefaultSymbolAgent(exchangeId, pair, mode, config, evaluationExecutor,
                strategyEvaluator, riskEvaluator, executionEngine, tradabilityChecker, newsContextService, autoStrategyLab);
        agents.put(key, agent);
        marketDataRouter.registerAgent(exchangeId, agent);
        agent.start();
        stateRepository.saveState(agent.state());
        log.info("TradingAgentManager started agent. exchange={} symbol={}", exchangeId, key.symbol());
        return agent;
    }

    public void stopAgent(String exchangeId, TradePair pair) {
        AgentKey key = AgentKey.of(exchangeId, pair);
        SymbolAgent agent = agents.remove(key);
        if (agent != null) {
            agent.stop();
            marketDataRouter.unregisterAgent(exchangeId, agent);
            stateRepository.saveState(agent.state());
        }
    }

    public void pauseAgent(String exchangeId, TradePair pair) {
        getAgent(exchangeId, pair).ifPresent(agent -> {
            agent.pause();
            stateRepository.saveState(agent.state());
        });
    }

    public void resumeAgent(String exchangeId, TradePair pair) {
        getAgent(exchangeId, pair).ifPresent(agent -> {
            agent.resume();
            stateRepository.saveState(agent.state());
        });
    }

    public void assignStrategy(String exchangeId, TradePair pair, StrategyDefinition strategy) {
        getAgent(exchangeId, pair).ifPresent(agent -> {
            agent.assignStrategy(strategy);
            stateRepository.saveState(agent.state());
        });
    }

    public Optional<SymbolAgent> getAgent(String exchangeId, TradePair pair) {
        return Optional.ofNullable(agents.get(AgentKey.of(exchangeId, pair)));
    }

    public List<SymbolAgent> listAgents() {
        return new ArrayList<>(agents.values());
    }

    public void stopAll() {
        listAgents().forEach(agent -> stopAgent(agent.state().exchangeId(), agent.pair()));
        evaluationExecutor.shutdownNow();
        scheduler.shutdownNow();
    }

    public MarketDataRouter marketDataRouter() {
        return marketDataRouter;
    }

    public SymbolAgentStateRepository stateRepository() {
        return stateRepository;
    }

    private void startHealthPersistence() {
        scheduler.scheduleWithFixedDelay(() -> {
            for (SymbolAgent agent : agents.values()) {
                try {
                    stateRepository.saveState(agent.state());
                } catch (Exception exception) {
                    log.warn("Unable to persist SymbolAgent state", exception);
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    public List<SymbolAgentState> restorePausedStates() {
        return stateRepository.findAllActiveStates();
    }
}
