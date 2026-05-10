# InvestPro - Sequence Diagrams & Workflows

**Last Updated**: May 2026  
**Version**: 1.0

---

## 1. Application Startup Sequence

```
User │ App │ SystemCore │ SmartBot │ Agents │ Exchange
  │   │      │            │         │        │
  │─→ │main()│            │         │        │
  │   │      │            │         │        │
  │   ├─new InvestProApplication()  │        │
  │   │      │            │         │        │
  │   │      │─new SystemCore()     │        │
  │   │      │   ├─new Exchange()───┼─────────┐
  │   │      │   ├─new SmartBot() ──┐        │
  │   │      │   │   ├─new AgentContext()    │
  │   │      │   │   ├─new AgentRegistry()   │
  │   │      │   │   ├─new AgentEventBus()   │
  │   │      │   │   └─new AgentRuntime()    │
  │   │      │   │                │           │
  │   │      │   ├─new RiskManagementSystem()│
  │   │      │   ├─new AiReasoningService()  │
  │   │      │   ├─new TradeExecutionCoordinator()
  │   │      │   └─new NotificationService() │
  │   │      │            │         │        │
  │   │      │            │    registerAgents()
  │   │      │            │    │    ├─MarketDataAgent
  │   │      │            │    │    ├─SignalAgent
  │   │      │            │    │    ├─RiskAgent
  │   │      │            │    │    ├─ExecutionAgent
  │   │      │            │    │    └─... (other agents)
  │   │      │            │    │        │
  │   │      │            │    └────────┘
  │   │      │
  │   ├─new TradingWindow()
  │   │      │
  │   │      └─showUI()
  │   │
  │   └─Ready for User Interaction
```

---

## 2. Paper Trade Execution Flow

```
User │ TradingWindow │ SystemCore │ TradeExecCoord │ RiskMgmt │ AIReasoning │ FinalGate │ ExecEngine │ Account
  │   │               │            │                │          │             │           │            │
  │─→ │Click BUY ─────┐            │                │          │             │           │            │
  │   │  BTC/USDT     │            │                │          │             │           │            │
  │   │               ├─Signal ────┐                │          │             │           │            │
  │   │               │            │                │          │             │           │            │
  │   │               │            ├─evaluateTrade()│          │             │           │            │
  │   │               │            │ (StrategySignal)          │             │           │            │
  │   │               │            │                │          │             │           │            │
  │   │               │            │                ├─checkBalance()          │           │            │
  │   │               │            │                │          │             │           │            │
  │   │               │            │                ├─checkPosition()         │           │            │
  │   │               │            │                │          │             │           │            │
  │   │               │            │                ├─checkLeverage()         │           │            │
  │   │               │            │                │          │             │           │            │
  │   │               │            │                └─checkMargin()          │           │            │
  │   │               │            │  ◀─ RiskDecision         │             │           │            │
  │   │               │            │                          │             │           │            │
  │   │               │            ├─reviewTrade(request)─────┐              │           │            │
  │   │               │            │  (includes risk decision)│              │           │            │
  │   │               │            │  (includes market data)  │              │           │            │
  │   │               │            │  (includes AI preferences)              │           │            │
  │   │               │            │                          │              │           │            │
  │   │               │            │                          ├─Call OpenAI API          │           │            │
  │   │               │            │                          │  "Should we trade?"     │           │            │
  │   │               │            │                          │                         │           │            │
  │   │               │            │                          ◀─ Approve/Reject/Review  │           │            │
  │   │               │            │ ◀─ AiTradeReviewResponse│             │           │            │
  │   │               │            │                │          │             │           │            │
  │   │               │            ├─makeDecision(aiReview)──────────────────┐           │            │
  │   │               │            │  (check high-risk rules)                 │           │            │
  │   │               │            │  (check AI confidence)                   │           │            │
  │   │               │            │  (apply final thresholds)                │           │            │
  │   │               │            │                                          │           │            │
  │   │               │            │ ◀─ OrderApprovalDecision (APPROVED)   │           │            │
  │   │               │            │                │          │             │           │            │
  │   │               │            ├──────────────────────────────────────────────────┐  │            │
  │   │               │            │  executeApprovedOrder()   │             │           │            │
  │   │               │            │                │          │             │           │            │
  │   │               │            │                │          │             │           │ ├─Update Balance
  │   │               │            │                │          │             │           │ ├─Create Position
  │   │               │            │                │          │             │           │ └─Record Trade
  │   │               │            │ ◀──────────────────────────────────────────────┘  │            │
  │   │               │            │  TradeExecutionResult (SUCCESS)           │            │
  │   │               │            │                │          │             │           │ ◀─Account Updated
  │   │               │            │                │          │             │           │            │
  │   │               │ ◀──────────┴────────────────┘          │             │           │            │
  │   │               │ Update Charts & Portfolio  │             │           │            │            │
  │   │ ◀─────────────┤ Show Execution Success    │             │           │            │            │
  │   │               │                           │             │           │            │            │
```

---

## 3. Automated Signal → Trade Flow

```
Exchange │ MarketDataAgent │ SignalAgent │ BotDecisionEngine │ TradeExecCoord │ Risk │ Exec
   │            │                │            │                 │            │      │
   ├─Ticker Update (WebSocket)    │            │                 │            │      │
   │            │                │            │                 │            │      │
   │            ├─onMarketUpdate()             │                 │            │      │
   │            │                │            │                 │            │      │
   │            ├─normalize data  │            │                 │            │      │
   │            │                │            │                 │            │      │
   │            ├─publishMarketEvent()        │                 │            │      │
   │            │                ├─onMarketEvent()              │            │      │
   │            │                │            │                 │            │      │
   │            │                ├─runStrategy()                │            │      │
   │            │                │  (check EMA, RSI, MACD)      │            │      │
   │            │                │            │                 │            │      │
   │            │                ├─generateSignal()             │            │      │
   │            │                │ (BUY BTC/USDT, strength=0.85)│            │      │
   │            │                │            │                 │            │      │
   │            │                ├─publishStrategySignal()      │            │      │
   │            │                │ ┌──────────────────────────┐  │            │      │
   │            │                │ │ evaluateSignal()         │  │            │      │
   │            │                │ │ 1. Detect market regime  │  │            │      │
   │            │                │ │ 2. Detect asset type     │  │            │      │
   │            │                │ │ 3. Score strategy fit    │  │            │      │
   │            │                │ │ 4. Estimate costs        │  │            │      │
   │            │                │ │ 5. Estimate expectation  │  │            │      │
   │            │                │ │ 6. Estimate hold period  │  │            │      │
   │            │                │ │ 7. TRADE or SKIP?        │  │            │      │
   │            │                │ └──────────────────────────┘  │            │      │
   │            │                │            │                 │            │      │
   │            │                │            ├─BotTradeDecision (TRADE)     │      │
   │            │                │            │                 │            │      │
   │            │                │            ├─Send to TradeExecCoord       │      │
   │            │                │            │                 │            │      │
   │            │                │            │                 ├─RiskCheck()│      │
   │            │                │            │                 ├─AIReview() ├─Call OpenAI
   │            │                │            │                 ├─FinalGate()│      │
   │            │                │            │                 │            │      │
   │            │                │            │                 │            ├─Place Order
   │            │                │            │                 │            │      │
   │            │                │            │                 │            ├─Update Balance
   │            │                │            │                 │            │      │
   │            │ ◀──────────────────────────────────────────────────────────┤      │
   │            │ publishExecutionEvent()     │                 │            │      │
   │            │                │ (Order filled, Position opened)          │      │
   │            │                │            │                 │            │      │
   └─◀──────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Risk Evaluation Sequence

```
Signal │ RiskMgmtSystem │ Account │ Exchange │ Decision
   │    │                │         │          │
   ├───►│evaluateTrade()          │          │
   │    │                │         │          │
   │    ├──────────────────────┐   │          │
   │    │ checkAuthentication()    │          │
   │    ├──────────────────────┘   │          │
   │    │ (verify API credentials) │          │
   │    │                │         │          │
   │    ├──────────────────────────┐          │
   │    │ getBalance()─────────────┼─────────►│
   │    │                │         │          │
   │    │ ◄─────────────────────────────────┤ (USD: 10000)
   │    │                │ ◄──────┤          │
   │    │ ┌─checkBalance()         │          │
   │    │ │ available >= amount?   │          │
   │    │ └─✓ OK                   │          │
   │    │                │         │          │
   │    ├──────────────────────────┐          │
   │    │ checkPositionSize()      │          │
   │    │ ┌─calc trade amount      │          │
   │    │ │ max 5% of account      │          │
   │    │ │ = 500 USD              │          │
   │    │ │ requested = 100 USD    │          │
   │    │ └─✓ OK                   │          │
   │    │                │         │          │
   │    ├──────────────────────────┐          │
   │    │ checkLeverage()          │          │
   │    │ ┌─position leverage?     │          │
   │    │ │ max 10x                │          │
   │    │ │ current = 1.2x         │          │
   │    │ └─✓ OK                   │          │
   │    │                │         │          │
   │    ├──────────────────────────┐          │
   │    │ checkMargin()            │          │
   │    │ ┌─current margin level?  │          │
   │    │ │ must > 5%              │          │
   │    │ │ current = 45%          │          │
   │    │ └─✓ OK                   │          │
   │    │                │         │          │
   │    ├──────────────────────────┐          │
   │    │ checkCorrelation()       │          │
   │    │ ┌─existing positions     │          │
   │    │ │ max 30% correlation    │          │
   │    │ │ current = 15% (good)   │          │
   │    │ └─✓ OK                   │          │
   │    │                │         │          │
   │    │ ◄──────────────────────────────────┤
   │ ◄──┤ RiskDecision(approved=true)        │
   │    │ constraints: []                    │
   │    │ margin: 45%                        │
   │    │ leverage: 1.2x                     │
   │    │                │         │          │
```

---

## 5. AI Trade Review Process

```
TradeExecCoord │ AiTradeReviewRequest │ AiReasoningService │ OpenAI API │ Decision
      │            │                  │                  │             │
      ├────────────►│ Builder Pattern   │                  │             │
      │            │ .withSignal()     │                  │             │
      │            │ .withRiskDecision │                  │             │
      │            │ .withMarketData() │                  │             │
      │            │ .withAccount()    │                  │             │
      │            │ .build()          │                  │             │
      │            │                  │                  │             │
      │ ◄──────────┤ AiTradeReviewRequest                 │             │
      │            │                  │                  │             │
      ├─────────────────────────────►│                  │             │
      │            │                  │                  │             │
      │            │                  ├─formatPrompt()   │             │
      │            │                  │                  │             │
      │            │                  │ "Given these signal and account│
      │            │                  │  conditions, should we trade?"│
      │            │                  │  (detailed context)           │
      │            │                  │                  │             │
      │            │                  ├─callOpenAI()────►│             │
      │            │                  │                  │ (gpt-4)     │
      │            │                  │                  │             │
      │            │                  │ ◄────────────────┤ Response    │
      │            │                  │  "APPROVED"      │  (reasoning)│
      │            │                  │  confidence: 0.92│             │
      │            │                  │                  │             │
      │ ◄────────────────────────────┤ AiTradeReviewResponse         │
      │            │                  │ decision: APPROVED             │
      │            │                  │ confidence: 0.92               │
      │            │                  │ reasoning: "Signal is strong, account│
      │            │                  │ has sufficient margin, recent │
      │            │                  │ market conditions support..."  │
      │            │                  │                  │             │
```

---

## 6. Agent Event Publishing & Processing

```
MarketDataAgent │ EventBus │ SignalAgent │ RiskAgent │ PortfolioAgent │ ExecutionAgent
       │          │           │           │            │              │
       ├─publishMarketEvent()  │           │            │              │
       │  (Ticker Update)      │           │            │              │
       │          │            │           │            │              │
       │          ├──distribute to subscribers           │              │
       │          │            │           │            │              │
       │          ├────────────┐ onEvent() │            │              │
       │          │            ├───────────┐            │              │
       │          │            │ (check EMA│            │              │
       │          │            │  generate │            │              │
       │          │            │  signal)  │            │              │
       │          │            │           │            │              │
       │          ├────────────────────────┐ onEvent() │              │
       │          │                        ├──────────┐ (evaluate risk)
       │          │                        │          │               │
       │          ├────────────────────────────────────┐ onEvent()     │
       │          │                                    ├────────────┐  │
       │          │                                    │  (monitor  │  │
       │          │                                    │   positions)  │
       │          │                                    │            │  │
       │          │                                    │            ├──┐ onEvent()
       │          │                                    │            │  │
       │          │                                    │            │  ├─(execute)
       │          │                                    │            │  │
       │ ◄───────────────────────────────────────────────────────────┤
       │          │ publishExecutionEvent()                         │
       │          │ (Order filled, position opened)
       │          │
```

---

## 7. Signal Filtering & Decision Making

```
StrategySignal │ SignalToDecision │ BotTradeDecision │ TradeExecCoord │ Result
      │         │    Filter       │    Engine        │                │
      │         │                 │                  │                │
      ├────────►│                 │                  │                │
      │ (BUY    ├─shouldExecute()? │                  │                │
      │  signal)│                 │                  │                │
      │         │ ├─call evaluateSignal()            │                │
      │         │ │                ├─12 questions   │                │
      │         │ │                │ 1. Asset type? │                │
      │         │ │                │ 2. Market regime
      │         │ │                │ 3. Strat fit?
      │         │ │                │ 4. Costs?
      │         │ │                │ 5. Expectation
      │         │ │                │ 6-12. ...      │                │
      │         │ │                │                │                │
      │         │ │ ◄──────────────┤ BotTradeDecision
      │         │ │ decision.willSkip()? 
      │         │ │ decision.hasBlockingIssues()?
      │         │ │                │                │                │
      │ ├───SKIP (if issues found)                 │                │
      │ │       │                 │                │                │
      │ │ ◄─────┤ false           │                │                │
      │ │       │                 │                │                │
      │ └─YES   │                 │                │                │
      │         ├─shouldExecuteSignal() = true      │                │
      │         │                                   │                │
      │         │                                   ├─executeSignal()│
      │         │                                   │                │
      │         │                                   ├─Risk Check    │
      │         │                                   ├─AI Review     │
      │         │                                   ├─Final Gate    │
      │         │                                   │                │
      │         │                                   ├─ APPROVED?    │
      │         │                                   │ ├─YES         │
      │         │                                   │ │  Execute   │
      │         │                                   │ │              │
      │         │                                   │ └─NO          │
      │         │                                   │    Reject     │
      │         │                                   │                │
      │         │                                   ◄─ TradeExecutionResult
      │         │                                   │ (SUCCESS/FAILED/MANUAL_REVIEW)
      │         │                                   │                │
```

---

## 8. Complete Trade Lifecycle

```
┌─────────────────────────────────────────────────────────────────────┐
│                    COMPLETE TRADE LIFECYCLE                         │
└─────────────────────────────────────────────────────────────────────┘

Phase 1: SIGNAL GENERATION
    ├─ MarketDataAgent receives WebSocket ticker
    ├─ SignalAgent runs strategy (EMA, RSI, etc)
    └─ Generates StrategySignal (BUY, strength=0.85)

Phase 2: SIGNAL FILTERING
    ├─ SignalToDecisionFilter intercepts signal
    ├─ BotTradeDecisionEngine evaluates (12 questions)
    └─ Returns BotTradeDecision (TRADE or SKIP)
    
    If SKIP:
    ├─ Log skip reason
    ├─ Send notification (optional)
    └─ Wait for next signal

Phase 3: RISK EVALUATION
    ├─ TradeExecutionCoordinator receives approved decision
    ├─ RiskManagementSystem checks:
    │  ├─ Balance sufficient?
    │  ├─ Position size <= 5% of account?
    │  ├─ Leverage acceptable?
    │  ├─ Margin level healthy?
    │  └─ Correlation limits?
    └─ Returns RiskDecision (APPROVED or BLOCKED)
    
    If BLOCKED:
    ├─ Log constraints
    ├─ Send alert
    └─ Reject trade

Phase 4: AI REVIEW
    ├─ Format AiTradeReviewRequest (signal + risk + market)
    ├─ Call OpenAI API (GPT-4)
    ├─ Get reasoning & decision
    └─ Returns AiTradeReviewResponse (APPROVED, REJECTED, MANUAL, WAIT)
    
    If REJECTED/MANUAL:
    ├─ Log AI reasoning
    ├─ Create manual review ticket
    └─ Reject trade

Phase 5: FINAL RISK GATE
    ├─ Apply final override rules
    ├─ Check emergency conditions
    ├─ Check manual override availability
    └─ Returns OrderApprovalDecision (APPROVED, REJECTED, MANUAL, WAIT)
    
    If REJECTED/MANUAL/WAIT:
    ├─ Log decision
    ├─ Notify user
    └─ Exit pipeline

Phase 6: EXECUTION
    ├─ ExecutionEngine creates Order
    ├─ Apply order type logic (MARKET, LIMIT, etc)
    ├─ Call Exchange.placeOrder()
    └─ Returns OrderPlacementResult
    
    If SUCCESS:
    ├─ Order fills (market order)
    ├─ Exchange publishes Trade event
    ├─ Position created in Account
    └─ Update UI

Phase 7: POSITION MANAGEMENT (Ongoing)
    ├─ PositionManagementAgent monitors position
    ├─ Creates stop-loss order (auto-placed)
    ├─ Creates take-profit order (auto-placed)
    ├─ Monitors P&L in real-time
    └─ Recommends exit if needed

Phase 8: EXIT
    ├─ Stop-loss OR take-profit hit (auto-exit)
    ├─ Manual close by user
    ├─ SignalAgent generates SELL signal
    └─ Repeat from Phase 1 (now SELL side)

Phase 9: SETTLEMENT
    ├─ Fill remaining quantity
    ├─ Calculate P&L
    ├─ Update balance
    ├─ Record completed trade
    ├─ Generate audit log
    └─ Send notification
```

---

## 9. Error Recovery & Fallback Flows

```
Exception │ Handler │ Fallback Strategy │ Result
    │      │         │                  │
    ├─WebSocket disconnects
    │      │         │                  │
    │      ├─Pause stream
    │      │         │                  │
    │      ├─Switch to REST polling ────┐
    │      │         │                  │ (Lower frequency)
    │      │         ├─Poll every 5-10s │
    │      │         │                  │
    │      │         ◄─Resume when connected
    │      │         │                  │
    ├─API Rate limit exceeded
    │      │         │                  │
    │      ├─Log error
    │      │         │                  │
    │      ├─Pause requests for duration
    │      │         │                  │
    │      ├─Retry with exponential backoff
    │      │         │                  │
    │      ├─Queue rejected orders ────►│
    │      │         │                  │ (Retry later)
    │      │         │                  │
    ├─AI service unavailable
    │      │         │                  │
    │      ├─Skip AI review
    │      │         │                  │
    │      ├─Fall back to risk rules only
    │      │         │                  │
    │      ├─Reduce position size ────►│
    │      │         │                  │ (Conservative)
    │      │         │                  │
    ├─Order placement failed
    │      │         │                  │
    │      ├─Log failure
    │      │         │                  │
    │      ├─Retry (up to 3 times) ────┐
    │      │         │                  │ (Immediate retry)
    │      │         │                  │
    │      ├─If still fails: Manual review
    │      │         │                  │
    ├─Position gone negative (stop-loss)
    │      │         │                  │
    │      ├─Market sell immediately ──┐
    │      │         │                  │ (Emergency exit)
    │      │         │                  │
    │      ├─Log loss
    │      │         │                  │
    │      ├─Send alert
    │      │         │                  │
```

---

## 10. Testing Workflows

### 10.1 Unit Test Flow
```
Test │ Component │ Mock Dependencies │ Assertion
 │    │          │                  │
 ├─BotTradeDecision.evaluateSignal()
 │    │          │                  │
 │    ├─Create StrategySignal       │
 │    │          │                  │
 │    ├─Mock Ticker (prices)        │
 │    │          │                  │
 │    ├─Mock StrategyEngine        │
 │    │          │                  │
 │    ├─Call evaluateSignal()      │
 │    │          │                  │
 │    │ ◄────────┤ BotTradeDecision│
 │    │          │                  │
 │    ├─Assert decision is TRADE or SKIP
 │    │          │                  │
 │    ├─Assert all 12 questions answered
 │    │          │                  │
 │    ├─Assert costs < profit
 │    │          │                  │
 │    └─Assert risk/reward >= 1.5
 │              │                  │
```

### 10.2 Integration Test Flow
```
Test │ TradeExecCoord │ RealDeps │ DB │ Result
 │    │               │          │    │
 ├─Full trade pipeline
 │    │               │          │    │
 │    ├─Create signal │          │    │
 │    │               │          │    │
 │    ├─RiskMgmt ────►│ Account  │    │
 │    │               │ checks   │    │
 │    │               │ ◄────────┤    │
 │    │               │          │    │
 │    ├─AiReasoning  │ Mock API │    │
 │    │               │ (return APPROVE)
 │    │               │          │    │
 │    ├─FinalGate ───►│          │    │
 │    │               │          │    │
 │    ├─ExecutionEngine          │    │
 │    │               ├─Create order  │
 │    │               │          │    │
 │    │               ├─Update Balance ├─
 │    │               │          │ ┌──┘
 │    │               │          │ │
 │    │ ◄─────────────┤─Trade recorded in DB
 │    │               │          │    │
 │    └─Assert success & consistency
 │              │          │    │
```

---

**Version**: 1.0  
**Last Updated**: May 2026  
**Document Status**: Complete & Production Ready
