# InvestPro - UML Class Diagrams

**Last Updated**: May 2026  
**Version**: 1.0

---

## 1. Core Application Layer UML

### 1.1 Application Entry Point

```
┌─────────────────────────────────────────────┐
│     InvestProApplication (JavaFX)           │
├─────────────────────────────────────────────┤
│ - primaryStage: Stage                       │
│ - systemCore: SystemCore                    │
│ - tradingWindow: TradingWindow              │
├─────────────────────────────────────────────┤
│ + start(Stage): void                        │
│ + stop(): void                              │
│ + main(args: String[]): void                │
└─────────────────────────────────────────────┘
           │
           │ creates
           ▼
┌─────────────────────────────────────────────┐
│        SystemCore                           │
├─────────────────────────────────────────────┤
│ - exchange: Exchange                        │
│ - smartBot: SmartBot                        │
│ - strategyEngine: StrategyEngine            │
│ - riskMgmt: RiskManagementSystem            │
│ - aiReasoning: AiReasoningService           │
│ - execEngine: ExecutionEngine               │
│ - tradeExecCoord: TradeExecutionCoordinator │
│ - notificationService: NotificationService  │
│ - agentRegistry: AgentRegistry              │
│ - historicalDataRepo: HistoricalDataRepo    │
├─────────────────────────────────────────────┤
│ + startBot(service, pair): void             │
│ + stopBot(): void                           │
│ + setAutoTradingEnabled(enabled): void      │
│ + setAiReasoningEnabled(enabled): void      │
│ + startStreaming(pair, mode): void          │
│ + stopStreaming(): void                     │
│ + getExchange(): Exchange                   │
│ + getSmartBot(): SmartBot                   │
│ + disconnect(): void                        │
└─────────────────────────────────────────────┘
           │
           │ owns & manages
           ▼
┌─────────────────────────────────────────────┐
│        SmartBot                             │
├─────────────────────────────────────────────┤
│ - agentContext: AgentContext                │
│ - agentRuntime: AgentRuntime                │
│ - agentRegistry: AgentRegistry              │
│ - eventBus: AgentEventBus                   │
│ - autoTradingEnabled: boolean               │
│ - aiReasoningEnabled: boolean               │
│ - started: AtomicBoolean                    │
│ - running: AtomicBoolean                    │
├─────────────────────────────────────────────┤
│ + start(exchange, service, pair): void      │
│ + stop(): void                              │
│ + setAutoTradingEnabled(enabled): void      │
│ + setAiReasoningEnabled(enabled): void      │
│ + setSelectedTradePair(pair): void          │
│ + isStarted(): boolean                      │
│ + isRunning(): boolean                      │
│ + publishMarketEvent(...): void             │
│ + publishSignal(...): void                  │
│ + publishExecution(...): void               │
└─────────────────────────────────────────────┘
```

---

## 2. Agent Framework UML

```
┌──────────────────────────────────────────────────────┐
│        <<interface>> Agent                           │
├──────────────────────────────────────────────────────┤
│ + id(): String                                       │
│ + name(): String                                     │
│ + start(context: AgentContext): void                │
│ + stop(): void                                       │
│ + isRunning(): boolean                               │
│ + onEvent(event: AgentEvent): void                   │
└──────────────────────────────────────────────────────┘
                    ▲
        ┌───────────┼───────────┐
        │           │           │
        │           │           │
        ▼           ▼           ▼
    ┌────────────┐ ┌────────────┐ ┌────────────────────┐
    │ Market     │ │ Signal     │ │ Risk Agent         │
    │ Data Agent │ │ Agent      │ │                    │
    ├────────────┤ ├────────────┤ ├────────────────────┤
    │ ...methods │ │ ...methods │ │ ...methods         │
    └────────────┘ └────────────┘ └────────────────────┘

        ▼           ▼           ▼
    ┌────────────┐ ┌────────────┐ ┌────────────────────┐
    │ Portfolio  │ │ Position   │ │ Execution Agent    │
    │ Agent      │ │ Management │ │                    │
    │            │ │ Agent      │ │                    │
    └────────────┘ └────────────┘ └────────────────────┘

        ▼
    ┌────────────┐
    │ Audit      │
    │ Agent      │
    └────────────┘


┌──────────────────────────────────────────────────────┐
│        AgentRegistry                                 │
├──────────────────────────────────────────────────────┤
│ - agents: Map<String, Agent>                         │
├──────────────────────────────────────────────────────┤
│ + register(agent: Agent): void                       │
│ + getAgent(id: String): Agent                        │
│ + getAllAgents(): Collection<Agent>                  │
│ + startAll(context: AgentContext): void              │
│ + stopAll(): void                                    │
└──────────────────────────────────────────────────────┘


┌──────────────────────────────────────────────────────┐
│        AgentEventBus (Pub/Sub)                        │
├──────────────────────────────────────────────────────┤
│ - subscribers: Map<Class<?>, List<Consumer>>         │
│ - executor: ExecutorService                          │
├──────────────────────────────────────────────────────┤
│ + subscribe(eventType, listener): void               │
│ + publish(event: AgentEvent): void                   │
│ + publishAsync(event: AgentEvent): void              │
│ + unsubscribe(eventType, listener): void             │
│ + clear(): void                                      │
└──────────────────────────────────────────────────────┘


┌──────────────────────────────────────────────────────┐
│        AgentContext (Dependency Container)           │
├──────────────────────────────────────────────────────┤
│ - exchange: Exchange                                 │
│ - strategyEngine: StrategyEngine                     │
│ - riskMgmt: RiskManagementSystem                     │
│ - aiReasoning: AiReasoningService                    │
│ - tradeExecCoord: TradeExecutionCoordinator          │
│ - notificationService: NotificationService           │
│ - account: Account                                   │
│ - eventBus: AgentEventBus                            │
├──────────────────────────────────────────────────────┤
│ + getExchange(): Exchange                            │
│ + getStrategyEngine(): StrategyEngine                │
│ + getRiskManagement(): RiskManagementSystem          │
│ + getAiReasoning(): AiReasoningService               │
│ + ... (getter for each dependency)                   │
└──────────────────────────────────────────────────────┘
```

---

## 3. Execution Pipeline UML

```
┌────────────────────────────────────────────────────────┐
│     BotTradeDecisionEngine                             │
├────────────────────────────────────────────────────────┤
│ - strategyEngine: StrategyEngine                       │
├────────────────────────────────────────────────────────┤
│ + evaluateSignal(pair, side, ticker, strength):       │
│     BotTradeDecision                                   │
│                                                        │
│ # detectAssetMarketType(): AssetMarketType             │
│ # detectMarketRegime(ticker): MarketRegime             │
│ # scoreBestStrategy(): StrategyFitScore                │
│ # estimateTradeCosts(ticker): TradeCostEstimate       │
│ # estimateTradeExpectation(...): TradeExpectation      │
│ # estimateHoldingPeriod(): HoldingPeriodEstimate       │
│ # buildFullAnalysisSummary(): String                   │
│ # createSkipDecision(...): BotTradeDecision            │
└────────────────────────────────────────────────────────┘
           │
           │ returns
           ▼
┌────────────────────────────────────────────────────────┐
│     BotTradeDecision (Record)                          │
├────────────────────────────────────────────────────────┤
│ - tradePair: TradePair                                 │
│ - side: Side                                           │
│ - regime: MarketRegime                                 │
│ - assetType: AssetMarketType                           │
│ - setupSource: SetupSource                             │
│ - strategyName: String                                 │
│ - indicatorSetupType: IndicatorSetupType              │
│ - bestStrategyScore: StrategyFitScore                  │
│ - indicatorSetupScore: IndicatorSetupScore             │
│ - costEstimate: TradeCostEstimate                      │
│ - expectation: TradeExpectation                        │
│ - holdingPeriod: HoldingPeriodEstimate                 │
│ - finalAction: TradeAction (TRADE, SKIP)               │
│ - reasons: List<String>                                │
│ - warnings: List<String>                               │
│ - blockers: List<String>                               │
│ - fullAnalysisSummary: String                          │
│ - decidedAt: Instant                                   │
├────────────────────────────────────────────────────────┤
│ + willTrade(): boolean                                 │
│ + willSkip(): boolean                                  │
│ + hasBlockingIssues(): boolean                         │
│ + getRiskRewardRatio(): double                         │
└────────────────────────────────────────────────────────┘
           │
           │ input to
           ▼
┌────────────────────────────────────────────────────────┐
│     SignalToDecisionFilter                             │
├────────────────────────────────────────────────────────┤
│ - decisionEngine: BotTradeDecisionEngine               │
│ - tradeExecCoord: TradeExecutionCoordinator            │
├────────────────────────────────────────────────────────┤
│ + shouldExecuteSignal(signal, context, ticker):       │
│     boolean                                            │
└────────────────────────────────────────────────────────┘
           │
           │ gates
           ▼
┌────────────────────────────────────────────────────────┐
│     TradeExecutionCoordinator                          │
├────────────────────────────────────────────────────────┤
│ - riskMgmt: RiskManagementSystem                       │
│ - aiReasoning: AiReasoningService                      │
│ - finalRiskGate: FinalRiskGate                         │
│ - execEngine: ExecutionEngine                          │
│ - eventRecorder: SystemEventRecorder                   │
├────────────────────────────────────────────────────────┤
│ + executeSignal(signal, context): TradeExecutionResult │
│                                                        │
│ # evaluateWithRisk(signal, context):                  │
│     RiskDecision                                       │
│ # evaluateWithAi(decision): AiTradeReviewResponse      │
│ # gateDecision(aiReview): OrderApprovalDecision        │
│ # executeIfApproved(approval, order):                 │
│     OrderPlacementResult                               │
└────────────────────────────────────────────────────────┘
           │
           ├──────────────────┬────────────────┐
           ▼                  ▼                ▼
    ┌──────────────┐   ┌─────────────┐  ┌───────────────┐
    │ Risk         │   │ AI          │  │ Final Risk    │
    │ Management   │   │ Reasoning   │  │ Gate          │
    │ System       │   │ Service     │  │               │
    └──────────────┘   └─────────────┘  └───────────────┘
           │                  │                │
           └──────────────────┴────────────────┘
                    │
                    ▼
         ┌────────────────────────┐
         │   ExecutionEngine      │
         │                        │
         │ - orderRepository      │
         │ - tradeRepository      │
         │ - currencyRepository   │
         │ - exchange (delegated) │
         └────────────────────────┘
                    │
                    ▼
         ┌────────────────────────┐
         │ Exchange Adapters      │
         │ (Binance, Coinbase..) │
         └────────────────────────┘
```

---

## 4. Data Models UML

```
┌──────────────────────────────────┐
│   MarketRegime (Enum)            │
├──────────────────────────────────┤
│ STRONG_UPTREND                   │
│ STRONG_DOWNTREND                 │
│ WEAK_UPTREND                      │
│ WEAK_DOWNTREND                    │
│ RANGE_BOUND                       │
│ HIGH_VOLATILITY                   │
│ LOW_VOLATILITY                    │
│ TRANSITIONAL                      │
│ UNKNOWN                           │
├──────────────────────────────────┤
│ + bullish: boolean                │
│ + isLowRiskRegime: boolean        │
└──────────────────────────────────┘


┌──────────────────────────────────────────┐
│   AssetMarketType (Enum)                 │
├──────────────────────────────────────────┤
│ FOREX                                    │
│ CRYPTO_SPOT                              │
│ CRYPTO_DERIVATIVES                       │
│ EQUITIES                                 │
│ EQUITY_DERIVATIVES                       │
│ COMMODITIES                              │
│ FIXED_INCOME                             │
│ UNKNOWN                                  │
├──────────────────────────────────────────┤
│ + is24Hour: boolean                      │
│ + isHighVolatility: boolean              │
│ + supportsLeverage: boolean              │
└──────────────────────────────────────────┘


┌──────────────────────────────────────┐
│   StrategyFitScore (Record)          │
├──────────────────────────────────────┤
│ - regimeFitScore: double [0.0-1.0]   │
│ - assetFitScore: double [0.0-1.0]    │
│ - timeframeFitScore: double [0.0-1.0]│
│ - recentPerformanceScore: double     │
│ - riskCompatibilityScore: double     │
│ - finalFitnessScore: double [0.0-1.0]│
├──────────────────────────────────────┤
│ + isAboveThreshold(threshold): boolean│
│ + getWeakestComponent(): String       │
└──────────────────────────────────────┘


┌──────────────────────────────────────────────┐
│   TradeCostEstimate (Record)                 │
├──────────────────────────────────────────────┤
│ - spread: BigDecimal                         │
│ - commission: BigDecimal (0.2%)              │
│ - slippage: BigDecimal (0.05%)               │
│ - swapCost: BigDecimal                       │
│ - marketImpact: BigDecimal                   │
│ - totalCost: BigDecimal                      │
│ - costBreakdown: String                      │
│ - isCostAcceptable: boolean                  │
│ - warningMessage: String (nullable)          │
├──────────────────────────────────────────────┤
│ + percentageOfProfit(profit): BigDecimal     │
│ + isUnderThreshold(threshold): boolean       │
└──────────────────────────────────────────────┘


┌────────────────────────────────────────┐
│   TradeExpectation (Record)            │
├────────────────────────────────────────┤
│ - expectedGrossProfit: BigDecimal      │
│ - expectedLossIfWrong: BigDecimal      │
│ - expectedNetProfit: BigDecimal        │
│ - expectedValue: BigDecimal            │
│ - winProbability: double [0.0-1.0]    │
│ - riskRewardRatio: double              │
│ - profitBreakdown: String              │
│ - isPositiveExpectancy: boolean        │
│ - isAcceptableRiskReward: boolean      │
├────────────────────────────────────────┤
│ + breakEvenProbability(): double       │
│ + kellyFraction(): double              │
└────────────────────────────────────────┘


┌──────────────────────────────────────┐
│   HoldingPeriodEstimate (Record)      │
├──────────────────────────────────────┤
│ - minimumHoldTime: Duration           │
│ - expectedHoldTime: Duration          │
│ - maximumHoldTime: Duration           │
│ - reason: String                      │
│ - isScalpSetup: boolean               │
├──────────────────────────────────────┤
│ + isWithinBounds(duration): boolean   │
│ + exceedsMaximum(duration): boolean   │
└──────────────────────────────────────┘
```

---

## 5. Risk & AI Layer UML

```
┌──────────────────────────────────────────────────┐
│     RiskManagementSystem                         │
├──────────────────────────────────────────────────┤
│ - account: Account                               │
│ - exchange: Exchange                             │
│ - MAX_POSITION_SIZE: 0.05 (5% of account)       │
│ - MAX_LEVERAGE: 10.0                             │
│ - MAX_CORRELATION: 0.30                          │
├──────────────────────────────────────────────────┤
│ + evaluateTrade(signal, ticker): RiskDecision    │
│                                                  │
│ # checkBalance(): RiskConstraint                 │
│ # checkPositionSize(amount): RiskConstraint      │
│ # checkLeverage(leverage): RiskConstraint        │
│ # checkMargin(position): RiskConstraint          │
│ # checkCorrelation(pair): RiskConstraint         │
│ # checkAuthentication(): RiskConstraint          │
└──────────────────────────────────────────────────┘
           │
           │ returns
           ▼
┌──────────────────────────────────────────────────┐
│     RiskDecision                                 │
├──────────────────────────────────────────────────┤
│ - signal: StrategySignal                         │
│ - approved: boolean                              │
│ - constraints: List<RiskConstraint>              │
│ - estimatedMargin: BigDecimal                    │
│ - estimatedLeverage: double                      │
│ - recommendations: List<String>                  │
├──────────────────────────────────────────────────┤
│ + isApproved(): boolean                          │
│ + hasBlockingConstraints(): boolean              │
│ + getConstraintSummary(): String                 │
└──────────────────────────────────────────────────┘
           │
           │ sent to
           ▼
┌──────────────────────────────────────────────────┐
│   <<interface>> AiReasoningService                │
├──────────────────────────────────────────────────┤
│ + reviewTrade(request: AiTradeReviewRequest):    │
│     AiTradeReviewResponse                        │
│                                                  │
│ + recommendPositionAction(context):              │
│     AiPositionAction                             │
│                                                  │
│ + getProvider(): String                          │
│ + isHealthy(): boolean                           │
└──────────────────────────────────────────────────┘
      │                       │
      │ implemented by        │
      ▼                       ▼
┌─────────────────┐   ┌──────────────────┐
│ OpenAiReasoning │   │ LocalAiReasoning │
│ Service         │   │ Service          │
└─────────────────┘   └──────────────────┘


┌──────────────────────────────────────────────────┐
│     AiTradeReviewRequest (Builder)               │
├──────────────────────────────────────────────────┤
│ - signal: StrategySignal                         │
│ - riskDecision: RiskDecision                     │
│ - currentMarketConditions: String                │
│ - recentPortfolioPerformance: String             │
│ - userPreferences: String                        │
├──────────────────────────────────────────────────┤
│ + build(): AiTradeReviewRequest                  │
│ + addSignal(signal): Builder                     │
│ + addRiskDecision(decision): Builder             │
│ + ... (builder methods)                          │
└──────────────────────────────────────────────────┘
           │
           │ evaluated by
           ▼
┌──────────────────────────────────────────────────┐
│     FinalRiskGate                                │
├──────────────────────────────────────────────────┤
│ - overrideEnabled: boolean                       │
│ - eventRecorder: SystemEventRecorder             │
├──────────────────────────────────────────────────┤
│ + makeDecision(aiReview, riskDecision):          │
│     OrderApprovalDecision                        │
│                                                  │
│ # checkHighRiskScenarios(): boolean              │
│ # checkAiConfidence(response): boolean           │
│ # checkRiskThresholds(decision): boolean         │
│ # applyEmergencyOverride(): boolean              │
└──────────────────────────────────────────────────┘
           │
           │ returns
           ▼
┌──────────────────────────────────────────────────┐
│     OrderApprovalDecision                        │
├──────────────────────────────────────────────────┤
│ - decision: ApprovalStatus                       │
│   (APPROVED, MANUAL_REVIEW, WAIT, REJECTED)      │
│ - reasoning: String                              │
│ - confidence: double [0.0-1.0]                   │
│ - requiresManualReview: boolean                  │
│ - reviewTicketId: String (if manual review)      │
├──────────────────────────────────────────────────┤
│ + isApproved(): boolean                          │
│ + requiresHumanReview(): boolean                 │
│ + canProceedToExecution(): boolean               │
└──────────────────────────────────────────────────┘
```

---

## 6. Exchange Integration UML

```
┌─────────────────────────────────────────┐
│     <<interface>> Exchange               │
├─────────────────────────────────────────┤
│ + getExchangeName(): String              │
│ + isConnected(): boolean                 │
│ + connect(): void                        │
│ + disconnect(): void                     │
│                                          │
│ + getTicker(pair): Ticker                │
│ + getOrderBook(pair): OrderBook           │
│ + getBalances(): Map<Currency, Balance>  │
│ + getOpenOrders(): List<OpenOrder>       │
│ + getPositions(): List<Position>         │
│                                          │
│ + placeOrder(order): OrderPlacementResult│
│ + cancelOrder(orderId): boolean          │
│ + modifyOrder(orderId, newPrice): bool   │
│                                          │
│ + subscribe(pairs, consumer): void       │
│ + unsubscribe(pairs): void               │
│ + registerTrade(trade): void             │
│ + registerOrder(order): void             │
└─────────────────────────────────────────┘
           │
    ┌──────┴──────┬──────────┬──────────┐
    ▼             ▼          ▼          ▼
┌─────────┐  ┌─────────┐ ┌──────┐  ┌──────┐
│Binance  │  │Coinbase │ │OANDA │  │Alpaca│
│Adapter  │  │Adapter  │ │Adapter│  │Adapt │
└─────────┘  └─────────┘ └──────┘  └──────┘


┌─────────────────────────────────────────────────┐
│     Ticker (Market Data)                        │
├─────────────────────────────────────────────────┤
│ - pair: TradePair                               │
│ - bidPrice: double                              │
│ - askPrice: double                              │
│ - lastPrice: double                             │
│ - volume24h: BigDecimal                         │
│ - high24h: double                               │
│ - low24h: double                                │
│ - priceChangePercent24h: double                 │
│ - timestamp: Instant                            │
├─────────────────────────────────────────────────┤
│ + getBidPrice(): double                         │
│ + getAskPrice(): double                         │
│ + getLastPrice(): double                        │
│ + getMidPrice(): double                         │
│ + getSpread(): double                           │
│ + getSpreadPercentage(): double                 │
└─────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────┐
│     OrderBook (Market Depth)                    │
├─────────────────────────────────────────────────┤
│ - pair: TradePair                               │
│ - bids: List<BookLevel>                         │
│ - asks: List<BookLevel>                         │
│ - timestamp: Instant                            │
│ - sequenceNumber: long                          │
├─────────────────────────────────────────────────┤
│ + getBestBid(): BookLevel                       │
│ + getBestAsk(): BookLevel                       │
│ + getImbalance(): double                        │
│ + getTotalVolume(): BigDecimal                  │
│ + getPriceImpact(quantity): double              │
└─────────────────────────────────────────────────┘


┌──────────────────────────────────────┐
│     Trade (Execution Record)         │
├──────────────────────────────────────┤
│ - tradeId: String                    │
│ - pair: TradePair                    │
│ - side: Side (BUY, SELL)             │
│ - quantity: BigDecimal               │
│ - price: BigDecimal                  │
│ - fee: BigDecimal                    │
│ - feeAsset: Currency                 │
│ - maker: boolean                     │
│ - timestamp: Instant                 │
├──────────────────────────────────────┤
│ + getGrossValue(): BigDecimal        │
│ + getNetValue(): BigDecimal          │
│ + getFeePercentage(): double         │
└──────────────────────────────────────┘


┌──────────────────────────────────────┐
│     OpenOrder (Pending Order)        │
├──────────────────────────────────────┤
│ - orderId: String                    │
│ - pair: TradePair                    │
│ - side: Side                         │
│ - type: OrderType                    │
│ - quantity: BigDecimal               │
│ - price: BigDecimal                  │
│ - filledQuantity: BigDecimal         │
│ - createdTime: Instant               │
│ - updateTime: Instant                │
├──────────────────────────────────────┤
│ + getPercentFilled(): double         │
│ + getRemainingQuantity(): BigDecimal │
│ + isFilled(): boolean                │
└──────────────────────────────────────┘
```

---

## 7. Strategy Engine UML

```
┌──────────────────────────────────────────────┐
│     StrategyEngine                           │
├──────────────────────────────────────────────┤
│ - strategyCatalog: StrategyCatalog           │
│ - account: Account                           │
│ - candleRepository: CandleData               │
├──────────────────────────────────────────────┤
│ + evaluateSignal(pair, ticker): StrategySignal│
│                                              │
│ # runStrategy(strategy, ticker):             │
│     List<StrategySignal>                     │
│ # filterSignals(signals, account): List      │
│ # rankSignalsByStrength(): Ranked List       │
│ # validateSignal(signal): Signal             │
└──────────────────────────────────────────────┘
           │
           │ uses
           ▼
┌──────────────────────────────────────────────┐
│     StrategyCatalog                          │
├──────────────────────────────────────────────┤
│ - strategies: Map<String, Strategy>          │
├──────────────────────────────────────────────┤
│ + getStrategy(name): Strategy                │
│ + getAllStrategies(): Collection<Strategy>   │
│ + registerStrategy(strategy): void           │
│ + isStrategyEnabled(name): boolean           │
└──────────────────────────────────────────────┘
           │
           │ contains
           ▼
┌──────────────────────────────────────────────┐
│     <<abstract>> Strategy                    │
├──────────────────────────────────────────────┤
│ - name: String                               │
│ - description: String                        │
│ - timeframe: Timeframe                       │
│ - enabled: boolean                           │
├──────────────────────────────────────────────┤
│ + evaluate(ticker, candles): StrategySignal │
│ + getName(): String                          │
│ + getTimeframe(): Timeframe                  │
│ + isEnabled(): boolean                       │
│ + setEnabled(enabled): void                  │
└──────────────────────────────────────────────┘
           │
    ┌──────┴──────┬──────────┬─────────────┐
    ▼             ▼          ▼             ▼
┌─────────────┐ ┌────────┐ ┌──────┐  ┌──────────┐
│EMAStrategy  │ │RSIST   │ │MACD  │  │Bollinger │
│Crossover    │ │Strategy│ │Cross │  │Bands     │
└─────────────┘ └────────┘ └──────┘  └──────────┘


┌──────────────────────────────────────┐
│     StrategySignal                   │
├──────────────────────────────────────┤
│ - strategyName: String               │
│ - pair: TradePair                    │
│ - side: Side (BUY, SELL, HOLD)       │
│ - strength: double [0.0-1.0]         │
│ - confidence: double [0.0-1.0]       │
│ - generatedAt: Instant               │
│ - reason: String                     │
│ - technicalFactors: List<String>     │
├──────────────────────────────────────┤
│ + isStrongSignal(): boolean          │
│ + isWeakSignal(): boolean            │
│ + isHoldSignal(): boolean            │
└──────────────────────────────────────┘
```

---

## 8. Complete Class Hierarchy Diagram

```
org.investpro
├── app/
│   ├── InvestProApplication
│   └── StartupException
│
├── core/
│   ├── SystemCore ⭐ (Composition Root)
│   ├── SmartBot ⭐ (Bot Runtime)
│   ├── agents/
│   │   ├── Agent (interface)
│   │   ├── AgentRegistry
│   │   ├── AgentEventBus
│   │   ├── AgentContext
│   │   ├── AgentRuntime
│   │   ├── AgentModule
│   │   ├── MarketDataAgent
│   │   ├── SignalAgent
│   │   ├── RiskAgent
│   │   ├── PortfolioAgent
│   │   ├── PositionManagementAgent
│   │   ├── ExecutionAgent
│   │   ├── AuditAgent
│   │   └── modules/
│   │       └── DefaultTradingAgentModule
│   │
│   ├── events/
│   │   ├── AgentEvent (base)
│   │   ├── MarketEvent
│   │   ├── SignalEvent
│   │   ├── TradeEvent
│   │   ├── ExecutionEvent
│   │   └── AuditEvent
│   │
│   ├── execution/
│   │   ├── TradeExecutionCoordinator ⭐
│   │   ├── ExecutionEngine ⭐
│   │   └── SymbolExecutionFilter
│   │
│   └── bot/
│       └── SmartBot ⭐
│
├── decision/ ⭐ (Signal Decision Engine)
│   ├── BotTradeDecisionEngine ⭐
│   ├── SignalToDecisionFilter
│   ├── BotTradeDecision
│   ├── MarketRegime (enum)
│   ├── AssetMarketType (enum)
│   ├── SetupSource (enum)
│   ├── IndicatorSetupType (enum)
│   ├── StrategyFitScore (record)
│   ├── IndicatorSetupScore (record)
│   ├── TradeCostEstimate (record)
│   ├── TradeExpectation (record)
│   └── HoldingPeriodEstimate (record)
│
├── risk/
│   ├── RiskManagementSystem ⭐
│   ├── RiskDecision
│   ├── RiskConstraint
│   ├── FinalRiskGate ⭐
│   └── TradeRiskContext
│
├── ai/
│   ├── AiReasoningService (interface)
│   ├── OpenAiReasoningService
│   ├── LocalAiReasoningService
│   ├── AiTradeReviewRequest
│   ├── AiTradeReviewResponse
│   ├── AiPositionAction
│   └── AiEventRecorder
│
├── strategy/
│   ├── StrategyEngine ⭐
│   ├── StrategyCatalog
│   ├── Strategy (abstract)
│   ├── StrategySignal
│   ├── StrategyAssignment
│   ├── StrategyBootstrapper
│   ├── UserStrategy
│   └── lab/
│       └── StrategyLabService
│
├── exchange/
│   ├── Exchange (interface)
│   ├── ExchangeFactory
│   ├── BinanceExchange
│   ├── CoinbaseExchange
│   ├── OandaExchange
│   ├── AlpacaExchange
│   ├── InteractiveBrokersExchange
│   ├── infrastructure/
│   │   ├── ExchangeStreamConsumer
│   │   ├── ExchangeStreamSubscription
│   │   ├── RateLimiter
│   │   └── QuotaManager
│   └── consumers/
│       └── UiExchangeStreamConsumer
│
├── models/
│   ├── Account
│   ├── AccountSettings
│   ├── trading/
│   │   ├── TradePair
│   │   ├── Ticker ⭐
│   │   ├── OrderBook
│   │   ├── Trade
│   │   ├── OpenOrder
│   │   ├── Position
│   │   ├── OrderType (enum)
│   │   └── Side (enum: BUY, SELL)
│   ├── portfolio/
│   │   ├── Portfolio
│   │   └── AssetAllocation
│   └── market/
│       ├── CandleData
│       ├── Timeframe
│       └── OHLCV
│
├── monitoring/
│   ├── SystemMonitorService
│   ├── SystemEventRecorder
│   ├── SystemHealthSnapshot
│   └── HealthCheck
│
├── repository/
│   ├── RepositoryFactory
│   ├── TradeRepository
│   ├── OrderRepository
│   ├── PositionRepository
│   ├── HistoricalDataRepository
│   └── CurrencyRepository
│
├── service/
│   ├── TradingService
│   ├── NotificationService
│   ├── EmailService
│   └── TelegramBotService
│
├── data/
│   ├── CandleData
│   └── HistoricalDataCache
│
├── indicators/
│   ├── MovingAverage
│   ├── RSI
│   ├── MACD
│   ├── BollingerBands
│   ├── ATR
│   └── (20+ more indicators)
│
└── utils/
    ├── CurrencyConverter
    ├── DateTimeUtils
    ├── CalculationUtils
    └── ValidationUtils
```

---

## 9. Key Design Principles Illustrated

### 9.1 Dependency Injection Pattern
- All major components receive dependencies via constructor
- AgentContext provides centralized dependency container
- Enables easy testing with mocks

### 9.2 Strategy Pattern  
- Strategy (abstract) allows easy addition of new strategies
- StrategyCatalog decouples strategy management from usage
- Enables dynamic strategy enable/disable

### 9.3 Observer Pattern (Pub/Sub)
- AgentEventBus decouples agents from each other
- Agents subscribe to events they care about
- New agents added without changing existing code

### 9.4 Chain of Responsibility
- Signal → BotTradeDecisionEngine → Risk → AI → Gate → Execution
- Each step can reject/modify without affecting others
- Easy to insert additional filters

### 9.5 Repository Pattern
- Data access abstracted behind repository interfaces
- Easy to swap SQLite ↔ PostgreSQL
- Testing doesn't require database

---

**Version**: 1.0  
**Last Updated**: May 2026  
**Document Status**: Complete & Production Ready
