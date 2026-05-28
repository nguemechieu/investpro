package org.investpro.monitoring;

import lombok.Builder;
import org.investpro.enums.RiskStatus;
import org.investpro.enums.SystemState;

import java.time.Instant;
import java.util.List;

/**
 * Immutable snapshot of the trading system status at a point in time.
 * Used by the status monitoring panel to display real-time system health.
 */
@Builder
public record TradingSystemStatusSnapshot(
        // System Overview
        SystemState systemState,
        String brokerName,
        String tradingMode,
        boolean autoTradingEnabled,
        boolean killSwitchArmed,
        String activeVenue,
        Instant connectedSince,
        Instant lastHeartbeat,
        long uptimeSeconds,

        // Broker & Market Data Health
        boolean restApiConnected,
        boolean webSocketConnected,
        boolean tickerStreamActive,
        boolean orderBookStreamActive,
        boolean candleStreamActive,
        boolean accountStreamActive,
        long latencyMillis,
        String rateLimitStatus,
        int reconnectCount,
        Instant lastMarketTick,

        // Execution Engine Status
        boolean executionEngineRunning,
        boolean orderSubmissionAllowed,
        int pendingOrders,
        int rejectedOrdersToday,
        String lastOrderId,
        Instant lastFillTime,
        double averageFillLatencyMs,
        double slippageEstimatePips,
        boolean cancelAllSupported,

        // Risk Engine Status
        RiskStatus riskStatus,
        double dailyLoss,
        double maxDailyLoss,
        double maxDrawdown,
        double currentDrawdown,
        double portfolioHeat,
        double marginUsed,
        double freeMargin,
        double maxPositionsAllowed,
        int currentPositionCount,
        String concentrationRisk,
        String correlationRisk,
        String lastRiskDecision,

        // Strategy Engine Status
        int activeStrategies,
        String bestStrategyToday,
        String worstStrategyToday,
        String lastSignal,
        double lastSignalConfidence,
        List<StrategyStatus> strategyStatus,

        // AI / Reasoning Engine Status
        String aiProvider,
        boolean aiEnabled,
        String aiReviewMode,
        String lastAiDecision,
        double confidenceThreshold,
        Instant lastAiReasoningTime,
        String promptVersion,
        boolean learningEngineActive,
        int feedbackSamples,

        // Account & Portfolio Status
        double balance,
        double equity,
        double availableBalance,
        double unrealizedPnl,
        double realizedPnlToday,
        double feesAndCommission,
        double swapOrFundingCost,
        int openPositionCount,
        int openOrderCount,

        // Market Session Status
        String primaryMarketStatus,
        String sessionName,
        long timeToMarketCloseSeconds,
        long timeToMarketOpenSeconds,
        String liquidityCondition,
        boolean rolloverRiskActive,
        boolean newsLockoutActive,

        // Data Quality / Readiness
        int candlesLoaded,
        int minimumCandlesRequired,
        boolean indicatorWarmupComplete,
        int missingCandleGaps,
        boolean backtestReady,
        boolean paperTestReady,
        boolean liveReady,
        Instant lastDataUpdate,

        // Event Bus / Internal System Health
        boolean eventBusRunning,
        int eventQueueSize,
        double eventsPerSecond,
        int droppedEvents,
        int deadLetterQueueSize,
        int activeSubscribers,
        String lastEventType,
        boolean replayAvailable,

        // Alerts and Blockers
        List<SystemAlert> alerts,

        // Overall system health score (0-100)
        double systemHealthScore,
        Instant snapshotTime) {
    /**
     * Inner record for strategy status information.
     */
    public record StrategyStatus(
            String symbol,
            String strategyName,
            String mode,
            String lastSignal,
            double confidence,
            double pnl,
            String status) {
    }

    /**
     * Create a new snapshot builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for fluent snapshot creation.
     */
    public static class Builder {
        // System Overview
        private SystemState systemState = SystemState.STARTING;
        private String brokerName = "";
        private String tradingMode = "Paper";
        private boolean autoTradingEnabled = false;
        private boolean killSwitchArmed = true;
        private String activeVenue = "";
        private Instant connectedSince = Instant.now();
        private Instant lastHeartbeat = Instant.now();
        private long uptimeSeconds = 0;

        // Broker & Market Data Health
        private boolean restApiConnected = false;
        private boolean webSocketConnected = false;
        private boolean tickerStreamActive = false;
        private boolean orderBookStreamActive = false;
        private boolean candleStreamActive = false;
        private boolean accountStreamActive = false;
        private long latencyMillis = 0;
        private String rateLimitStatus = "Normal";
        private int reconnectCount = 0;
        private Instant lastMarketTick = Instant.now();

        // Execution Engine Status
        private boolean executionEngineRunning = false;
        private boolean orderSubmissionAllowed = false;
        private int pendingOrders = 0;
        private int rejectedOrdersToday = 0;
        private String lastOrderId = "";
        private Instant lastFillTime = Instant.now();
        private double averageFillLatencyMs = 0;
        private double slippageEstimatePips = 0;
        private boolean cancelAllSupported = true;

        // Risk Engine Status
        private RiskStatus riskStatus = RiskStatus.PASSING;
        private double dailyLoss = 0;
        private double maxDailyLoss = 100;
        private double maxDrawdown = 0.2;
        private double currentDrawdown = 0;
        private double portfolioHeat = 0;
        private double marginUsed = 0;
        private double freeMargin = 0;
        private double maxPositionsAllowed = 10;
        private int currentPositionCount = 0;
        private String concentrationRisk = "Normal";
        private String correlationRisk = "Normal";
        private String lastRiskDecision = "";

        // Strategy Engine Status
        private int activeStrategies = 0;
        private String bestStrategyToday = "";
        private String worstStrategyToday = "";
        private String lastSignal = "";
        private double lastSignalConfidence = 0;
        private List<StrategyStatus> strategyStatus = List.of();

        // AI / Reasoning Engine Status
        private String aiProvider = "Disabled";
        private boolean aiEnabled = false;
        private String aiReviewMode = "Advisory";
        private String lastAiDecision = "";
        private double confidenceThreshold = 0.6;
        private Instant lastAiReasoningTime = Instant.now();
        private String promptVersion = "v1.0";
        private boolean learningEngineActive = false;
        private int feedbackSamples = 0;

        // Account & Portfolio Status
        private double balance = 0;
        private double equity = 0;
        private double availableBalance = 0;
        private double unrealizedPnl = 0;
        private double realizedPnlToday = 0;
        private double feesAndCommission = 0;
        private double swapOrFundingCost = 0;
        private int openPositionCount = 0;
        private int openOrderCount = 0;

        // Market Session Status
        private String primaryMarketStatus = "Unknown";
        private String sessionName = "";
        private long timeToMarketCloseSeconds = 0;
        private long timeToMarketOpenSeconds = 0;
        private String liquidityCondition = "Normal";
        private boolean rolloverRiskActive = false;
        private boolean newsLockoutActive = false;

        // Data Quality / Readiness
        private int candlesLoaded = 0;
        private int minimumCandlesRequired = 300;
        private boolean indicatorWarmupComplete = false;
        private int missingCandleGaps = 0;
        private boolean backtestReady = false;
        private boolean paperTestReady = false;
        private boolean liveReady = false;
        private Instant lastDataUpdate = Instant.now();

        // Event Bus / Internal System Health
        private boolean eventBusRunning = true;
        private int eventQueueSize = 0;
        private double eventsPerSecond = 0;
        private int droppedEvents = 0;
        private int deadLetterQueueSize = 0;
        private int activeSubscribers = 0;
        private String lastEventType = "";
        private boolean replayAvailable = true;

        // Alerts and Blockers
        private List<SystemAlert> alerts = List.of();

        // Overall system health score (0-100)
        private double systemHealthScore = 0;

        public Builder systemState(SystemState state) {
            this.systemState = state;
            return this;
        }

        public Builder brokerName(String name) {
            this.brokerName = name;
            return this;
        }

        public Builder tradingMode(String mode) {
            this.tradingMode = mode;
            return this;
        }

        public Builder autoTradingEnabled(boolean enabled) {
            this.autoTradingEnabled = enabled;
            return this;
        }

        public Builder killSwitchArmed(boolean armed) {
            this.killSwitchArmed = armed;
            return this;
        }

        public Builder activeVenue(String venue) {
            this.activeVenue = venue;
            return this;
        }

        public Builder connectedSince(Instant time) {
            this.connectedSince = time;
            return this;
        }

        public Builder lastHeartbeat(Instant time) {
            this.lastHeartbeat = time;
            return this;
        }

        public Builder uptimeSeconds(long seconds) {
            this.uptimeSeconds = seconds;
            return this;
        }

        public Builder restApiConnected(boolean connected) {
            this.restApiConnected = connected;
            return this;
        }

        public Builder webSocketConnected(boolean connected) {
            this.webSocketConnected = connected;
            return this;
        }

        public Builder tickerStreamActive(boolean active) {
            this.tickerStreamActive = active;
            return this;
        }

        public Builder orderBookStreamActive(boolean active) {
            this.orderBookStreamActive = active;
            return this;
        }

        public Builder candleStreamActive(boolean active) {
            this.candleStreamActive = active;
            return this;
        }

        public Builder accountStreamActive(boolean active) {
            this.accountStreamActive = active;
            return this;
        }

        public Builder latencyMillis(long latency) {
            this.latencyMillis = latency;
            return this;
        }

        public Builder rateLimitStatus(String status) {
            this.rateLimitStatus = status;
            return this;
        }

        public Builder reconnectCount(int count) {
            this.reconnectCount = count;
            return this;
        }

        public Builder lastMarketTick(Instant time) {
            this.lastMarketTick = time;
            return this;
        }

        public Builder executionEngineRunning(boolean running) {
            this.executionEngineRunning = running;
            return this;
        }

        public Builder orderSubmissionAllowed(boolean allowed) {
            this.orderSubmissionAllowed = allowed;
            return this;
        }

        public Builder pendingOrders(int count) {
            this.pendingOrders = count;
            return this;
        }

        public Builder rejectedOrdersToday(int count) {
            this.rejectedOrdersToday = count;
            return this;
        }

        public Builder lastOrderId(String id) {
            this.lastOrderId = id;
            return this;
        }

        public Builder lastFillTime(Instant time) {
            this.lastFillTime = time;
            return this;
        }

        public Builder averageFillLatencyMs(double latency) {
            this.averageFillLatencyMs = latency;
            return this;
        }

        public Builder slippageEstimatePips(double slippage) {
            this.slippageEstimatePips = slippage;
            return this;
        }

        public Builder cancelAllSupported(boolean supported) {
            this.cancelAllSupported = supported;
            return this;
        }

        public Builder riskStatus(RiskStatus status) {
            this.riskStatus = status;
            return this;
        }

        public Builder dailyLoss(double loss) {
            this.dailyLoss = loss;
            return this;
        }

        public Builder maxDailyLoss(double max) {
            this.maxDailyLoss = max;
            return this;
        }

        public Builder maxDrawdown(double max) {
            this.maxDrawdown = max;
            return this;
        }

        public Builder currentDrawdown(double current) {
            this.currentDrawdown = current;
            return this;
        }

        public Builder portfolioHeat(double heat) {
            this.portfolioHeat = heat;
            return this;
        }

        public Builder marginUsed(double used) {
            this.marginUsed = used;
            return this;
        }

        public Builder freeMargin(double free) {
            this.freeMargin = free;
            return this;
        }

        public Builder maxPositionsAllowed(double max) {
            this.maxPositionsAllowed = max;
            return this;
        }

        public Builder currentPositionCount(int count) {
            this.currentPositionCount = count;
            return this;
        }

        public Builder concentrationRisk(String risk) {
            this.concentrationRisk = risk;
            return this;
        }

        public Builder correlationRisk(String risk) {
            this.correlationRisk = risk;
            return this;
        }

        public Builder lastRiskDecision(String decision) {
            this.lastRiskDecision = decision;
            return this;
        }

        public Builder activeStrategies(int count) {
            this.activeStrategies = count;
            return this;
        }

        public Builder bestStrategyToday(String strategy) {
            this.bestStrategyToday = strategy;
            return this;
        }

        public Builder worstStrategyToday(String strategy) {
            this.worstStrategyToday = strategy;
            return this;
        }

        public Builder lastSignal(String signal) {
            this.lastSignal = signal;
            return this;
        }

        public Builder lastSignalConfidence(double confidence) {
            this.lastSignalConfidence = confidence;
            return this;
        }

        public Builder strategyStatus(List<StrategyStatus> status) {
            this.strategyStatus = status;
            return this;
        }

        public Builder aiProvider(String provider) {
            this.aiProvider = provider;
            return this;
        }

        public Builder aiEnabled(boolean enabled) {
            this.aiEnabled = enabled;
            return this;
        }

        public Builder aiReviewMode(String mode) {
            this.aiReviewMode = mode;
            return this;
        }

        public Builder lastAiDecision(String decision) {
            this.lastAiDecision = decision;
            return this;
        }

        public Builder confidenceThreshold(double threshold) {
            this.confidenceThreshold = threshold;
            return this;
        }

        public Builder lastAiReasoningTime(Instant time) {
            this.lastAiReasoningTime = time;
            return this;
        }

        public Builder promptVersion(String version) {
            this.promptVersion = version;
            return this;
        }

        public Builder learningEngineActive(boolean active) {
            this.learningEngineActive = active;
            return this;
        }

        public Builder feedbackSamples(int count) {
            this.feedbackSamples = count;
            return this;
        }

        public Builder balance(double balance) {
            this.balance = balance;
            return this;
        }

        public Builder equity(double equity) {
            this.equity = equity;
            return this;
        }

        public Builder availableBalance(double available) {
            this.availableBalance = available;
            return this;
        }

        public Builder unrealizedPnl(double pnl) {
            this.unrealizedPnl = pnl;
            return this;
        }

        public Builder realizedPnlToday(double pnl) {
            this.realizedPnlToday = pnl;
            return this;
        }

        public Builder feesAndCommission(double fees) {
            this.feesAndCommission = fees;
            return this;
        }

        public Builder swapOrFundingCost(double cost) {
            this.swapOrFundingCost = cost;
            return this;
        }

        public Builder openPositionCount(int count) {
            this.openPositionCount = count;
            return this;
        }

        public Builder openOrderCount(int count) {
            this.openOrderCount = count;
            return this;
        }

        public Builder primaryMarketStatus(String status) {
            this.primaryMarketStatus = status;
            return this;
        }

        public Builder sessionName(String name) {
            this.sessionName = name;
            return this;
        }

        public Builder timeToMarketCloseSeconds(long seconds) {
            this.timeToMarketCloseSeconds = seconds;
            return this;
        }

        public Builder timeToMarketOpenSeconds(long seconds) {
            this.timeToMarketOpenSeconds = seconds;
            return this;
        }

        public Builder liquidityCondition(String condition) {
            this.liquidityCondition = condition;
            return this;
        }

        public Builder rolloverRiskActive(boolean active) {
            this.rolloverRiskActive = active;
            return this;
        }

        public Builder newsLockoutActive(boolean active) {
            this.newsLockoutActive = active;
            return this;
        }

        public Builder candlesLoaded(int count) {
            this.candlesLoaded = count;
            return this;
        }

        public Builder minimumCandlesRequired(int count) {
            this.minimumCandlesRequired = count;
            return this;
        }

        public Builder indicatorWarmupComplete(boolean complete) {
            this.indicatorWarmupComplete = complete;
            return this;
        }

        public Builder missingCandleGaps(int gaps) {
            this.missingCandleGaps = gaps;
            return this;
        }

        public Builder backtestReady(boolean ready) {
            this.backtestReady = ready;
            return this;
        }

        public Builder paperTestReady(boolean ready) {
            this.paperTestReady = ready;
            return this;
        }

        public Builder liveReady(boolean ready) {
            this.liveReady = ready;
            return this;
        }

        public Builder lastDataUpdate(Instant time) {
            this.lastDataUpdate = time;
            return this;
        }

        public Builder eventBusRunning(boolean running) {
            this.eventBusRunning = running;
            return this;
        }

        public Builder eventQueueSize(int size) {
            this.eventQueueSize = size;
            return this;
        }

        public Builder eventsPerSecond(double eps) {
            this.eventsPerSecond = eps;
            return this;
        }

        public Builder droppedEvents(int count) {
            this.droppedEvents = count;
            return this;
        }

        public Builder deadLetterQueueSize(int size) {
            this.deadLetterQueueSize = size;
            return this;
        }

        public Builder activeSubscribers(int count) {
            this.activeSubscribers = count;
            return this;
        }

        public Builder lastEventType(String type) {
            this.lastEventType = type;
            return this;
        }

        public Builder replayAvailable(boolean available) {
            this.replayAvailable = available;
            return this;
        }

        public Builder alerts(List<SystemAlert> alerts) {
            this.alerts = alerts;
            return this;
        }

        public Builder systemHealthScore(double score) {
            this.systemHealthScore = score;
            return this;
        }

        public TradingSystemStatusSnapshot build() {
            return new TradingSystemStatusSnapshot(
                    systemState, brokerName, tradingMode, autoTradingEnabled, killSwitchArmed, activeVenue,
                    connectedSince, lastHeartbeat, uptimeSeconds,
                    restApiConnected, webSocketConnected, tickerStreamActive, orderBookStreamActive,
                    candleStreamActive, accountStreamActive, latencyMillis, rateLimitStatus, reconnectCount,
                    lastMarketTick,
                    executionEngineRunning, orderSubmissionAllowed, pendingOrders, rejectedOrdersToday, lastOrderId,
                    lastFillTime, averageFillLatencyMs, slippageEstimatePips, cancelAllSupported,
                    riskStatus, dailyLoss, maxDailyLoss, maxDrawdown, currentDrawdown, portfolioHeat, marginUsed,
                    freeMargin, maxPositionsAllowed, currentPositionCount, concentrationRisk, correlationRisk,
                    lastRiskDecision,
                    activeStrategies, bestStrategyToday, worstStrategyToday, lastSignal, lastSignalConfidence,
                    strategyStatus,
                    aiProvider, aiEnabled, aiReviewMode, lastAiDecision, confidenceThreshold, lastAiReasoningTime,
                    promptVersion, learningEngineActive, feedbackSamples,
                    balance, equity, availableBalance, unrealizedPnl, realizedPnlToday, feesAndCommission,
                    swapOrFundingCost,
                    openPositionCount, openOrderCount,
                    primaryMarketStatus, sessionName, timeToMarketCloseSeconds, timeToMarketOpenSeconds,
                    liquidityCondition,
                    rolloverRiskActive, newsLockoutActive,
                    candlesLoaded, minimumCandlesRequired, indicatorWarmupComplete, missingCandleGaps, backtestReady,
                    paperTestReady, liveReady, lastDataUpdate,
                    eventBusRunning, eventQueueSize, eventsPerSecond, droppedEvents, deadLetterQueueSize,
                    activeSubscribers,
                    lastEventType, replayAvailable,
                    alerts, systemHealthScore, Instant.now());
        }
    }
}
