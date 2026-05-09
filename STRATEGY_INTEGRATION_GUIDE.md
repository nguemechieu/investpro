/**
 * STRATEGY/SIGNAL INTEGRATION GUIDE
 * =================================
 * 
 * This guide shows how to integrate the signal/strategy system into InvestPro's trading loop.
 * 
 * ## Architecture
 * 
 * SignalContext (market data)
 *       ↓
 * StrategyEvaluationService (evaluation)
 *       ↓
 * StrategyEvaluationResult (decision + signals)
 *       ↓
 * StrategyIntegrationBridge (routing)
 *       ↓
 * BehaviourGuardConfig → RiskEngine → ExecutionEngine
 * 
 * 
 * ## Files Created
 * 
 * 1. SignalContextFactory (service/)
 *    - Builds SignalContext from market data, indicators, account
 *    - Detects market conditions (trend, range, volatility)
 * 
 * 2. StrategyEvaluationService (service/)
 *    - Central evaluation engine
 *    - Evaluates selected + all enabled strategies
 *    - Calculates consensus confidence
 *    - Logs supporting/opposing signals
 * 
 * 3. StrategyEvaluationResult (service/)
 *    - DTO for strategy decision
 *    - Contains primary signal, supporting/opposing signals
 *    - Consensus confidence and reasoning
 * 
 * 4. StrategyEvaluationPanel (ui/panels/)
 *    - JavaFX panel showing strategy selector
 *    - Displays latest decision with confidence
 *    - Shows supporting/opposing signals in tables
 *    - Reload button for safe plugin reloading
 * 
 * 5. PluginReloadService (loader/)
 *    - Safe asynchronous plugin reloading
 *    - Stops evaluation, reloads, restarts
 *    - Tracks failed plugins and stats
 * 
 * 6. StrategyIntegrationBridge (core/agents/execution/)
 *    - Routes strategy decisions to execution pipeline
 *    - Handles event-driven evaluation (new candles, tickers)
 *    - Rate-limits evaluations
 *    - Tracks statistics
 * 
 * 7. StrategyIntegrationConfig (config/)
 *    - Central configuration point
 *    - Wires all components together
 *    - Logging and diagnostics
 * 
 * 
 * ## Integration Steps
 * 
 * ### Step 1: Initialize in SystemCore
 * 
 * Add to SystemCore constructor:
 * 
 *   // Initialize strategy integration
 *   StrategyIntegrationConfig strategyConfig = StrategyIntegrationConfig.create(
 *       this.strategyRegistry,
 *       this.riskManagementSystem,
 *       this
 *   );
 *   
 *   this.evaluationService = strategyConfig.getEvaluationService();
 *   this.integrationBridge = strategyConfig.getIntegrationBridge();
 *   this.pluginReloadService = strategyConfig.getPluginReloadService();
 * 
 * 
 * ### Step 2: Add UI Hooks to TradingDesk
 * 
 * Add to TradingDesk field declarations:
 * 
 *   private StrategyEvaluationService evaluationService;
 *   private PluginReloadService pluginReloadService;
 *   private StrategyEvaluationPanel evaluationPanel;
 * 
 * 
 * Add to createMainWorkbench() or similar layout method:
 * 
 *   // Add strategy evaluation panel
 *   evaluationPanel = new StrategyEvaluationPanel(
 *       strategyRegistry,
 *       evaluationService
 *   );
 *   
 *   // Add to tab pane or split pane
 *   terminalTabPane.getTabs().add(
 *       new Tab("Strategy", evaluationPanel)
 *   );
 * 
 * 
 * ### Step 3: Hook Events to Candle Updates
 * 
 * In TradingDesk or a streaming listener:
 * 
 *   // When new candle arrives
 *   integrationBridge.onNewCandle(
 *       pair,
 *       exchange,
 *       timeframe,
 *       candleData,
 *       account
 *   );
 * 
 * 
 * ### Step 4: Add Menu Items
 * 
 * In TradingDesk.createMenuBar():
 * 
 *   Menu strategyMenu = new Menu(t("menu.strategy"));
 *   strategyMenu.getItems().addAll(
 *       menuItem(t("menu.loadStrategies"), null, this::loadStrategies),
 *       menuItem(t("menu.reloadStrategies"), null, this::reloadStrategies),
 *       new SeparatorMenuItem(),
 *       menuItem(t("menu.toggleStrategyEvaluation"), null, this::toggleStrategyEvaluation)
 *   );
 * 
 * 
 * ### Step 5: Add Handler Methods to TradingDesk
 * 
 *   private void loadStrategies() {
 *       evaluationPanel.refreshStrategyList();
 *   }
 * 
 *   private void reloadStrategies() {
 *       pluginReloadService.reloadAsync()
 *           .thenAccept(result -> {
 *               if (result.success) {
 *                   appendAgentActivity("Strategies reloaded: " + result.toString());
 *               } else {
 *                   appendAgentActivity("Strategy reload failed: " + result.errorMessage);
 *               }
 *           });
 *   }
 * 
 *   private void toggleStrategyEvaluation() {
 *       boolean enabled = !evaluationService.isEvaluationEnabled();
 *       evaluationService.setEvaluationEnabled(enabled);
 *       appendAgentActivity("Strategy evaluation " + (enabled ? "enabled" : "disabled"));
 *   }
 * 
 * 
 * ## Event Flow
 * 
 * ### On New Candle (main trading loop trigger):
 * 
 * 1. New candle data arrives from market stream
 * 2. StrategyIntegrationBridge.onNewCandle() called
 * 3. SignalContextFactory builds complete context
 * 4. StrategyEvaluationService.evaluateStrategy() runs
 * 5. StrategyEvaluationPanel UI updates automatically (listener notified)
 * 6. StrategyEvaluationResult routed to execution pipeline:
 *    → BehaviourGuardConfig (behaviour check)
 *    → RiskEngine (risk validation)
 *    → ExecutionEngine (order placement)
 * 7. Statistics updated in StrategyIntegrationBridge
 * 
 * 
 * ### On Plugin Reload:
 * 
 * 1. User clicks "Reload Strategies" button
 * 2. PluginReloadService.reloadAsync() triggered
 * 3. Strategy evaluation paused (setEvaluationEnabled(false))
 * 4. Old strategies cleared from registry
 * 5. JSON definitions + JAR plugins reloaded
 * 6. Strategy registry rebuilt
 * 7. StrategyEvaluationPanel refreshed
 * 8. Evaluation resumed
 * 9. Success/failure logged
 * 
 * 
 * ## Logging
 * 
 * Key logs to monitor:
 * 
 *   INFO: Strategy Decision: BUY BTC/USD (confidence: 87.5%, supporting: 3, opposing: 1)
 *   DEBUG: Supporting signals: [TrendFollower(0.95), MeanReversion(0.82)]
 *   DEBUG: Opposing signals: [RSIOverbought(0.65)]
 *   
 *   INFO: Strategies loaded: 15 total, 12 signals, 2 plugins, 1 failed
 *   INFO: Strategy reload completed successfully
 * 
 * 
 * ## Diagnostics
 * 
 * Access diagnostics via:
 * 
 *   String diag = evaluationService.getDiagnostics();
 *   String stats = integrationBridge.getDiagnostics();
 *   String config = strategyConfig.getDiagnostics();
 * 
 * 
 * ## Performance Considerations
 * 
 * 1. Evaluation Rate Limiting
 *    - Minimum 1 second between evaluations per pair
 *    - Configurable in StrategyIntegrationBridge
 * 
 * 2. Strategy Registry
 *    - Thread-safe (ConcurrentHashMap)
 *    - Lazy loading on first access
 *    - Cached for performance
 * 
 * 3. Listener Pattern
 *    - UI updates only when new decision arrives
 *    - CopyOnWriteArrayList for safe concurrent iteration
 * 
 * 4. Async Reload
 *    - Plugin reload happens on background thread
 *    - CompletableFuture for async handling
 *    - UI remains responsive
 * 
 * 
 * ## Error Handling
 * 
 * All components handle errors gracefully:
 * 
 * - Strategy evaluation failures logged but don't crash system
 * - Invalid strategy selections detected and reported
 * - Plugin reload failures tracked (failedPlugins list)
 * - UI updates wrapped in try-catch with logging
 * - Listener notifications caught and logged
 * 
 * 
 * ## Future Enhancements
 * 
 * 1. Composite strategies (weighted signals from multiple strategies)
 * 2. Real-time confidence scoring based on backtested win rates
 * 3. Machine learning confidence adjustment
 * 4. Trade outcome feedback loop (adjust strategy weights based on results)
 * 5. Multi-timeframe strategy fusion (1m, 5m, 1h consensus)
 * 6. Strategy parameter optimization
 * 7. A/B testing framework for strategy comparison
 */
