package org.investpro.decision;

import lombok.Builder;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.jetbrains.annotations.Nullable;


import java.util.ArrayList;
import java.util.List;

/**
 * Assembles a fully immutable {@link BotTradeDecision} from an {@link ExecutionDecision}.
 *
 * <p>This assembler bridges the new institutional decision pipeline to the legacy
 * {@link BotTradeDecision} API that is consumed by {@link BotDecisionEngine},
 * {@link SignalToDecisionFilter}, and {@link org.investpro.risk.RiskEngine}.</p>
 *
 * <h3>Design principles:</h3>
 * <ul>
 *   <li>Single-shot construction — all fields set in one pass, no mutable setters</li>
 *   <li>Null-safe — uses null-object constants ({@link ExecutionPlan#EMPTY},
 *       {@link PortfolioImpact#NEUTRAL}, {@link DecisionReasoning#NONE}) instead of raw nulls</li>
 *   <li>Package-private — only classes in {@code org.investpro.decision} can use this</li>
 * </ul>
 *
 * <p>New institutional code should use {@link InstitutionalExecutionDecision} directly
 * and avoid {@link BotTradeDecision} entirely.</p>
 */
@Builder
public final class BotTradeDecisionAssembler {

    static boolean tradeAllowed;

    private BotTradeDecisionAssembler() {}

    /**
     * Assembles a {@link BotTradeDecision} from a pipeline {@link ExecutionDecision}.
     *
     * <p>Maps all fields from the institutional pipeline to the legacy decision format.
     * No mutable setters are used — the package-private constructor on
     * {@link BotTradeDecision} is called exactly once with all fields.</p>
     *
     * @param execDecision  the evaluated decision from the institutional pipeline; must not be null
     * @param costEstimate  optional trade cost estimate (fees, spread); may be null
     * @param expectation   optional trade expectation (expected R, probability); may be null
     * @return a fully populated, immutable {@link BotTradeDecision}
     */
    public static BotTradeDecision assemble(
            ExecutionDecision execDecision,
            @Nullable TradeCostEstimate costEstimate,
            @Nullable TradeExpectation expectation) {

        if (execDecision == null) {
            throw new IllegalArgumentException("execDecision must not be null");
        }

        TradeIntent intent = execDecision.intent();

        // ── Core identity ──────────────────────────────────────────────────────
        String decisionId = execDecision.decisionId();
        DecisionStatus status = execDecision.status();
        DecisionMode mode = execDecision.mode();

        // ── Action mapping ─────────────────────────────────────────────────────
        boolean approved = execDecision.isApproved();
        BotTradeDecision.FinalAction finalAction = approved
                ? BotTradeDecision.FinalAction.TRADE
                : BotTradeDecision.FinalAction.SKIP;
        DecisionAction action = approved ? DecisionAction.TRADE : DecisionAction.SKIP;
        tradeAllowed = approved;

        // ── Confidence ─────────────────────────────────────────────────────────
        double confidence = intent.confidence();

        // ── Explanation and message lists ──────────────────────────────────────
        String explanation = safeString(execDecision.explanation());
        List<String> blockers = safeList(execDecision.blockers());
        List<String> warnings = safeList(execDecision.warnings());
        List<String> reasons  = buildReasons(explanation, intent, execDecision.riskEvaluation());

        // ── Legacy fields from TradeIntent ─────────────────────────────────────
        TradePair tradePair            = intent.tradePair();
        Side side                      = intent.side();
        MarketRegime regime            = intent.regime();
        AssetMarketType assetType      = intent.assetType();
        SetupSource setupSource        = intent.setupSource();
        String selectedStrategyName    = safeString(intent.selectedStrategy());
        String indicatorSetupType      = safeString(intent.indicatorSetup());

        // ── Pipeline component nullability to null-object ──────────────────────
        RiskEvaluation riskEvaluation   = execDecision.riskEvaluation();
        ExecutionPlan executionPlan     = nonNullPlan(execDecision.executionPlan());
        DecisionReasoning reasoning     = nonNullReasoning(execDecision.reasoning(), confidence);
        PortfolioImpact portfolioImpact = nonNullPortfolio(execDecision.portfolioImpact());
        DecisionScoreBreakdown scores   = execDecision.scoreBreakdown();

        // ── Context ────────────────────────────────────────────────────────────
        DecisionContext context = execDecision.context();

        // ── Derived legacy scores ─────────────────────────────────────────────
        StrategyFitScore strategyFitScore    = buildStrategyFitScore(scores, confidence);
        IndicatorSetupScore indicatorScore   = buildIndicatorScore(scores, confidence);

        // ── Summary ───────────────────────────────────────────────────────────
        String fullAnalysisSummary = buildSummary(explanation, intent, approved);

        // ── Package-private constructor — single-shot, no mutable setters ──────
        return new BotTradeDecision(
                decisionId,
                status,
                mode,
                action,
                tradeAllowed,
                confidence,
                explanation,
                null,                   // signalDecision: not available from institutional pipeline
                blockers,
                warnings,
                reasons,
                execDecision.decidedAt(),
                context,
                tradePair,
                side,
                regime,
                assetType,
                setupSource,
                selectedStrategyName,
                indicatorSetupType,
                strategyFitScore,
                indicatorScore,
                costEstimate,
                expectation,
                null,                   // holdingPeriodEstimate: not in pipeline (future)
                finalAction,
                fullAnalysisSummary,
                intent,
                riskEvaluation,
                executionPlan,
                reasoning,
                portfolioImpact,
                scores
        );
    }

    // ─── Null-object helpers ──────────────────────────────────────────────────

    private static ExecutionPlan nonNullPlan(@Nullable ExecutionPlan plan) {
        return plan != null ? plan : ExecutionPlan.EMPTY;
    }

    private static DecisionReasoning nonNullReasoning(
            @Nullable DecisionReasoning reasoning, double confidence) {
        return reasoning != null ? reasoning : DecisionReasoning.neutral(confidence);
    }

    private static PortfolioImpact nonNullPortfolio(@Nullable PortfolioImpact impact) {
        return impact != null ? impact : PortfolioImpact.NEUTRAL;
    }

    // ─── Legacy score bridging ─────────────────────────────────────────────────

    private static StrategyFitScore buildStrategyFitScore(
            @Nullable DecisionScoreBreakdown scores, double confidence) {
        if (scores == null) {
            return StrategyFitScore.of(confidence);
        }
        return StrategyFitScore.of(scores.compositeScore());
    }

    private static IndicatorSetupScore buildIndicatorScore(
            @Nullable DecisionScoreBreakdown scores, double confidence) {
        if (scores == null) {
            return IndicatorSetupScore.of(confidence);
        }
        return IndicatorSetupScore.of(scores.confidenceScore());
    }

    // ─── Message list helpers ─────────────────────────────────────────────────

    private static List<String> buildReasons(
            String explanation,
            TradeIntent intent,
            @Nullable RiskEvaluation risk) {
        List<String> reasons = new ArrayList<>();
        if (!explanation.isBlank()) reasons.add(explanation);
        if (intent.selectedStrategy() != null) {
            reasons.add("Strategy: " + intent.selectedStrategy());
        }
        if (risk != null) {
            reasons.add("Risk: " + risk.verdict().name());
        }
        return List.copyOf(reasons);
    }

    private static String buildSummary(String explanation, TradeIntent intent, boolean approved) {
        String side    = intent.side().toString();
        String symbol  = intent.tradePair().toString();
        String regime  = intent.regime().name();
        String conf    = String.format("%.1f%%", intent.confidence() * 100);
        String verdict = approved ? "APPROVED" : "REJECTED";
        return verdict + " | " + side + " " + symbol
                + " | Regime=" + regime
                + " | Confidence=" + conf
                + (explanation.isBlank() ? "" : " | " + explanation);
    }

    // ─── String helpers ───────────────────────────────────────────────────────

    private static String safeString(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    private static List<String> safeList(@Nullable List<String> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
