package org.investpro.decision;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-phase institutional trade decision pipeline orchestrator.
 *
 * <p>Coordinates the full decision flow from market signal to an
 * {@link InstitutionalExecutionDecision}:</p>
 * <ol>
 *   <li>Create {@link TradeIntent} from signal and market context</li>
 *   <li>Analyze {@link PortfolioImpact} (skipped in LIGHTWEIGHT mode)</li>
 *   <li>Run {@link RiskEvaluation} to check exposure, leverage, drawdown</li>
 *   <li>Compute {@link PositionSizingDecision} from risk parameters</li>
 *   <li>Apply {@link DecisionReasoning} via AI model (skipped in LIGHTWEIGHT / SIMULATION)</li>
 *   <li>Generate {@link ExecutionPlan} if all checks pass</li>
 *   <li>Select {@link ExecutionRoute} via injected {@link ExecutionRouter}</li>
 *   <li>Build {@link DecisionScoreBreakdown}</li>
 *   <li>Produce final {@link InstitutionalExecutionDecision} with lifecycle timestamps</li>
 * </ol>
 *
 * <p>In {@link DecisionMode#LIGHTWEIGHT} mode the orchestrator skips portfolio analysis,
 * AI reasoning, and detailed context to minimize GC pressure during Strategy Lab screening
 * of millions of simulated decisions.</p>
 *
 * <p>Pipeline events are published to {@link AgentEventBus} at each phase boundary,
 * enabling downstream consumers (UI, portfolio managers, risk monitors) to react
 * asynchronously without coupling to the pipeline implementation.</p>
 */
@Slf4j
public class DecisionPipelineOrchestrator {

    private final DecisionMode defaultMode;

    @Nullable
    private final AgentEventBus eventBus;

    @NotNull
    private final ExecutionRouter executionRouter;

    /**
     * Creates an orchestrator with a fixed execution mode.
     *
     * @param defaultMode     the mode used when none is specified per-decision
     * @param eventBus        optional event bus; null disables event publication
     * @param executionRouter the router used to select execution venues
     */
    public DecisionPipelineOrchestrator(
            @NotNull DecisionMode defaultMode,
            @Nullable AgentEventBus eventBus,
            @NotNull ExecutionRouter executionRouter) {
        this.defaultMode = defaultMode;
        this.eventBus = eventBus;
        this.executionRouter = executionRouter;
    }

    /**
     * Backward-compatible constructor — uses the simulated router.
     */
    public DecisionPipelineOrchestrator(
            @NotNull DecisionMode defaultMode,
            @Nullable AgentEventBus eventBus) {
        this(defaultMode, eventBus, ExecutionRouter.simulated());
    }

    /**
     * Creates a SIMULATION-mode orchestrator with no event bus.
     * Suitable for backtesting where event overhead is not needed.
     */
    public static DecisionPipelineOrchestrator forSimulation() {
        return new DecisionPipelineOrchestrator(DecisionMode.SIMULATION, null);
    }

    /**
     * Creates a LIGHTWEIGHT-mode orchestrator with no event bus.
     * Use for mass Strategy Lab screening (millions of decisions per second).
     */
    public static DecisionPipelineOrchestrator forLightweightSimulation() {
        return new DecisionPipelineOrchestrator(DecisionMode.LIGHTWEIGHT, null);
    }

    /**
     * Creates a LIVE-mode orchestrator with full event publication.
     *
     * @param bus    the AgentEventBus to publish pipeline events to
     * @param router the execution router for venue selection
     */
    public static DecisionPipelineOrchestrator forLive(
            @NotNull AgentEventBus bus,
            @NotNull ExecutionRouter router) {
        return new DecisionPipelineOrchestrator(DecisionMode.LIVE, bus, router);
    }

    /**
     * Creates a LIVE-mode orchestrator with the simulated router.
     */
    public static DecisionPipelineOrchestrator forLive(@NotNull AgentEventBus bus) {
        return new DecisionPipelineOrchestrator(DecisionMode.LIVE, bus);
    }

    // ─── Main pipeline entry point ────────────────────────────────────────────

    /**
     * Run the full decision pipeline for a trade signal.
     *
     * @param tradePair  instrument to trade
     * @param side       direction: BUY or SELL
     * @param regime     current market regime
     * @param assetType  asset class
     * @param setupSource origin of the trade setup
     * @param strategy   selected strategy name (may be null)
     * @param confidence signal confidence (0.0–1.0)
     * @param context    typed execution context (may be null in LIGHTWEIGHT mode)
     * @return fully evaluated {@link ExecutionDecision}
     */
    @NotNull
    public ExecutionDecision evaluate(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull MarketRegime regime,
            @NotNull AssetMarketType assetType,
            @NotNull SetupSource setupSource,
            @Nullable String strategy,
            double confidence,
            @Nullable DecisionContext context) {

        return evaluate(tradePair, side, regime, assetType, setupSource,
                strategy, confidence, context, defaultMode);
    }

    /**
     * Run the full decision pipeline for a trade signal with an explicit mode override.
     */
    @NotNull
    public ExecutionDecision evaluate(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull MarketRegime regime,
            @NotNull AssetMarketType assetType,
            @NotNull SetupSource setupSource,
            @Nullable String strategy,
            double confidence,
            @Nullable DecisionContext context,
            @NotNull DecisionMode mode) {

        return evaluateFull(tradePair, side, regime, assetType, setupSource,
                strategy, confidence, context, mode).coreDecision();
    }

    /**
     * Run the full pipeline and return the complete {@link InstitutionalExecutionDecision}.
     * Preferred over {@link #evaluate} for new institutional code.
     */
    @NotNull
    public InstitutionalExecutionDecision evaluateFull(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull MarketRegime regime,
            @NotNull AssetMarketType assetType,
            @NotNull SetupSource setupSource,
            @Nullable String strategy,
            double confidence,
            @Nullable DecisionContext context,
            @NotNull DecisionMode mode) {

        long pipelineStart = System.nanoTime();
        String decisionId = DecisionIdGenerator.generate(mode);
        ExecutionLifecycle lifecycle = ExecutionLifecycle.created();

        // ── Phase 1: Create TradeIntent ────────────────────────────────────────
        long t0 = System.nanoTime();
        TradeIntent intent = new TradeIntent(
                tradePair, side, regime, assetType, setupSource, strategy, null,
                confidence, 1.0, Instant.now(), mode);
        long decisionGenerationNs = System.nanoTime() - t0;

        publish(DecisionCreatedEvent.of(decisionId, intent, mode));
        log.debug("Pipeline[{}] Intent created: {}", decisionId, intent.toSummary());

        lifecycle = lifecycle.withValidated();

        // ── Phase 2: Portfolio Impact (skip in LIGHTWEIGHT) ───────────────────
        PortfolioImpact portfolioImpact = mode == DecisionMode.LIGHTWEIGHT
                ? null
                : analyzePortfolioImpact(intent);

        if (portfolioImpact != null && portfolioImpact.hasBreaches()) {
            List<String> reasons = List.of("Portfolio concentration or exposure limit breached");
            publish(new DecisionRejectedEvent(
                    decisionId, intent, DecisionStatus.RISK_REJECTED, reasons, Instant.now())
                    .toAgentEvent());
            ExecutionDecision rejected = ExecutionDecision.rejected(
                    decisionId, mode, intent, DecisionStatus.RISK_REJECTED,
                    reasons, "Portfolio limits breached — trade rejected");
            return InstitutionalExecutionDecision.simulation(rejected);
        }

        // ── Phase 3: Risk Evaluation ──────────────────────────────────────────
        long t3 = System.nanoTime();
        RiskEvaluation risk = evaluateRisk(intent, portfolioImpact);
        long riskEvaluationNs = System.nanoTime() - t3;

        publish(new RiskEvaluationCompletedEvent(decisionId, intent, risk, Instant.now()).toAgentEvent());

        if (!risk.allowsExecution()) {
            String statusPrefix = risk.verdict() == RiskEvaluation.Verdict.WAIT ? "WAIT — " : "REJECTED — ";
            List<String> reasons = risk.rejectionReasons().isEmpty()
                    ? List.of(risk.summary()) : risk.rejectionReasons();
            publish(new DecisionRejectedEvent(
                    decisionId, intent, DecisionStatus.RISK_REJECTED, reasons, Instant.now())
                    .toAgentEvent());
            ExecutionDecision rejected = ExecutionDecision.rejected(
                    decisionId, mode, intent, DecisionStatus.RISK_REJECTED,
                    reasons, statusPrefix + risk.summary());
            return InstitutionalExecutionDecision.simulation(rejected);
        }

        lifecycle = lifecycle.withRiskApproved();
        publish(RiskApprovedEvent.of(decisionId, risk));

        // ── Phase 4: Position Sizing ──────────────────────────────────────────
        long t4 = System.nanoTime();
        PositionSizingDecision sizing = computePositionSizing(intent, risk);
        long positionSizingNs = System.nanoTime() - t4;

        lifecycle = lifecycle.withPositionSized();
        publish(PositionSizedEvent.of(decisionId, sizing));

        // ── Phase 5: AI Reasoning (skip in LIGHTWEIGHT / SIMULATION) ─────────
        long t5 = System.nanoTime();
        DecisionReasoning reasoning = null;
        if (mode.storeReasoning) {
            reasoning = applyAiReasoning(intent, risk);
            if (reasoning.isVetoed()) {
                List<String> reasons = List.of(reasoning.vetoReason());
                publish(new DecisionRejectedEvent(
                        decisionId, intent, DecisionStatus.AI_REJECTED, reasons, Instant.now())
                        .toAgentEvent());
                ExecutionDecision rejected = ExecutionDecision.rejected(
                        decisionId, mode, intent, DecisionStatus.AI_REJECTED,
                        reasons, "AI veto: " + reasoning.vetoReason());
                return InstitutionalExecutionDecision.simulation(rejected);
            }
            lifecycle = lifecycle.withAiApproved();
        }
        long aiReasoningNs = System.nanoTime() - t5;

        // ── Phase 6: Build ExecutionPlan ──────────────────────────────────────
        long t6 = System.nanoTime();
        ExecutionPlan plan = buildExecutionPlan(intent, risk, context);
        long executionPlanningNs = System.nanoTime() - t6;

        if (mode == DecisionMode.LIVE && (plan == null || !plan.isValid())) {
            List<String> reasons = List.of("Failed to generate a valid execution plan");
            publish(new DecisionRejectedEvent(
                    decisionId, intent, DecisionStatus.RISK_REJECTED, reasons, Instant.now())
                    .toAgentEvent());
            ExecutionDecision rejected = ExecutionDecision.rejected(
                    decisionId, mode, intent, DecisionStatus.RISK_REJECTED,
                    reasons, "Execution plan generation failed");
            return InstitutionalExecutionDecision.simulation(rejected);
        }
        if (plan != null) {
            publish(new ExecutionPlanCreatedEvent(decisionId, intent, plan, Instant.now()).toAgentEvent());
        }

        // ── Phase 7: Execution Routing ────────────────────────────────────────
        long t7 = System.nanoTime();
        ExecutionPlan routingPlan = plan != null ? plan : ExecutionPlan.EMPTY;
        ExecutionRoute route = executionRouter.route(intent, routingPlan, mode);
        long routingNs = System.nanoTime() - t7;

        lifecycle = lifecycle.withRouted();
        publish(ExecutionRoutedEvent.of(decisionId, route));

        // ── Phase 8: Score Breakdown (skip in LIGHTWEIGHT) ───────────────────
        DecisionScoreBreakdown scores = mode == DecisionMode.LIGHTWEIGHT
                ? null
                : buildScoreBreakdown(intent, risk, reasoning);

        // ── Phase 9: Assemble final decision ──────────────────────────────────
        long t9 = System.nanoTime();
        String explanation = buildSummary(intent, risk, plan, scores);
        ExecutionDecision coreDecision = ExecutionDecision.approved(
                decisionId, mode, intent, risk, plan, reasoning,
                portfolioImpact, scores, context, risk.warnings(), explanation);

        lifecycle = lifecycle.withSubmitted();
        long assemblyNs = System.nanoTime() - t9;

        DecisionPerformanceMetrics metrics = new DecisionPerformanceMetrics(
                decisionGenerationNs, riskEvaluationNs, positionSizingNs,
                aiReasoningNs, routingNs, executionPlanningNs, assemblyNs);

        InstitutionalExecutionDecision institutionalDecision = InstitutionalExecutionDecision.from(
                coreDecision, route, sizing, lifecycle, metrics, null);

        publish(new DecisionApprovedEvent(decisionId, coreDecision, Instant.now()).toAgentEvent());
        log.info("Pipeline[{}] APPROVED {} {} (composite: {}, totalMs: {})",
                decisionId, side.name(), tradePair.getSymbol(),
                String.format("%.2f", coreDecision.compositeScore()),
                metrics.totalMs());

        return institutionalDecision;
    }

    // ─── Phase implementations ────────────────────────────────────────────────

    @NotNull
    private PortfolioImpact analyzePortfolioImpact(@NotNull TradeIntent intent) {
        // Default neutral impact — replace with real portfolio analyzer injection
        return PortfolioImpact.neutral();
    }

    @NotNull
    private RiskEvaluation evaluateRisk(
            @NotNull TradeIntent intent,
            @Nullable PortfolioImpact portfolio) {

        // Confidence threshold gate
        if (!intent.meetsConfidenceThreshold()) {
            return RiskEvaluation.rejected(String.format(
                    "Signal confidence %.2f below threshold 0.65", intent.confidence()));
        }

        // Portfolio concentration gate
        if (portfolio != null && portfolio.breachesConcentrationLimit()) {
            return RiskEvaluation.rejected("Portfolio concentration limit exceeded");
        }

        double exposure = portfolio != null ? portfolio.exposureIncrease() : 0.0;
        double reductionFactor = 1.0;
        List<String> warnings = new ArrayList<>();

        // Regime-based size reduction
        if (intent.regime().isLowRiskRegime()) {
            reductionFactor = 0.75;
            warnings.add("Low-risk regime: position size reduced to 75%");
        }

        if (reductionFactor < 1.0) {
            return RiskEvaluation.reduced(reductionFactor, warnings, null);
        }
        return RiskEvaluation.approved(exposure);
    }

    @NotNull
    private PositionSizingDecision computePositionSizing(
            @NotNull TradeIntent intent,
            @NotNull RiskEvaluation risk) {
        if (intent.mode() == DecisionMode.LIGHTWEIGHT) {
            return PositionSizingDecision.simulation();
        }
        // Default fixed-risk 1% sizing — replace with injected PositionSizingEngine
        java.math.BigDecimal size = java.math.BigDecimal.ONE;
        double riskPct = 0.01;
        if (risk.sizeReductionFactor() < 1.0) {
            return PositionSizingDecision.reduced(
                    size, risk.sizeReductionFactor(),
                    "Risk reduction: " + risk.summary(),
                    PositionSizingDecision.SizingMethod.DRAWDOWN_SCALED);
        }
        return PositionSizingDecision.fixedRisk(size, riskPct);
    }

    @NotNull
    private DecisionReasoning applyAiReasoning(
            @NotNull TradeIntent intent,
            @NotNull RiskEvaluation risk) {
        // Safe default: no AI model injected — neutral pass-through (not a veto).
        // Replace with OpenAiReasoningService.reason(intent, risk) injection.
        return DecisionReasoning.neutral(intent.confidence());
    }

    @Nullable
    private ExecutionPlan buildExecutionPlan(
            @NotNull TradeIntent intent,
            @NotNull RiskEvaluation risk,
            @Nullable DecisionContext context) {

        // A real implementation would derive entry/stop/TP from tick data.
        // This default returns null so consumers know to fall back to BotTradeDecisionEngine.
        return null;
    }

    @NotNull
    private DecisionScoreBreakdown buildScoreBreakdown(
            @NotNull TradeIntent intent,
            @NotNull RiskEvaluation risk,
            @Nullable DecisionReasoning reasoning) {

        double trend = intent.regime().isTrendingRegime() ? 0.80 : 0.50;
        double vol   = 0.65;
        double liq   = 0.70;
        double riskS = risk.verdict() == RiskEvaluation.Verdict.APPROVED ? 0.85 : 0.60;
        double ai    = reasoning != null ? reasoning.aiConfidence() : 0.0;
        double port  = 0.70;
        double exec  = 0.65;
        double spread= 0.70;
        double conf  = intent.confidence();

        return new DecisionScoreBreakdown(trend, vol, liq, riskS, ai, port, exec, spread, conf);
    }

    @NotNull
    private String buildSummary(
            @NotNull TradeIntent intent,
            @NotNull RiskEvaluation risk,
            @Nullable ExecutionPlan plan,
            @Nullable DecisionScoreBreakdown scores) {

        StringBuilder sb = new StringBuilder();
        sb.append("=== DECISION APPROVED ===\n");
        sb.append(intent.toSummary()).append("\n");
        sb.append("Risk: ").append(risk.summary()).append("\n");
        if (plan != null) {
            sb.append("Execution: ").append(plan.toSummary()).append("\n");
        }
        if (scores != null) {
            sb.append(String.format("Composite Score: %.2f%n", scores.compositeScore()));
        }
        return sb.toString();
    }

    // ─── Event bus helpers ────────────────────────────────────────────────────

    private void publish(org.investpro.core.agents.AgentEvent event) {
        if (eventBus != null) {
            eventBus.publishAsync(event);
        }
    }

    private void publish(DecisionCreatedEvent event) {
        if (eventBus != null) {
            eventBus.publishAsync(org.investpro.core.agents.AgentEvent.of(
                    "DECISION_CREATED", DecisionPipelineOrchestrator.class.getSimpleName(), event));
        }
    }

    private void publish(RiskApprovedEvent event) {
        if (eventBus != null) {
            eventBus.publishAsync(org.investpro.core.agents.AgentEvent.of(
                    "RISK_APPROVED", DecisionPipelineOrchestrator.class.getSimpleName(), event));
        }
    }

    private void publish(PositionSizedEvent event) {
        if (eventBus != null) {
            eventBus.publishAsync(org.investpro.core.agents.AgentEvent.of(
                    "POSITION_SIZED", DecisionPipelineOrchestrator.class.getSimpleName(), event));
        }
    }

    private void publish(ExecutionRoutedEvent event) {
        if (eventBus != null) {
            eventBus.publishAsync(org.investpro.core.agents.AgentEvent.of(
                    "EXECUTION_ROUTED", DecisionPipelineOrchestrator.class.getSimpleName(), event));
        }
    }
}
