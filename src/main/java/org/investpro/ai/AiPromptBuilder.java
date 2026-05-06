package org.investpro.ai;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Builds stable, professional prompts for the AI reasoning engine.
 * <p>
 * The prompts are designed to:
 * - constrain AI to review and recommendation only
 * - enforce deterministic risk guardrails
 * - request structured JSON output
 * - provide sufficient context for trade, position, strategy, portfolio, and network-exchange decisions
 * <p>
 * IMPORTANT:
 * This class only builds prompts. It does not validate AI output.
 * Always validate AI output using AiDecisionValidator / PositionActionValidator / FinalRiskGate.
 */
public final class AiPromptBuilder {

    private AiPromptBuilder() {
    }

    private static final String BASE_GUARDRAILS = """
            GLOBAL INVESTPRO AI RULES:
            - You are the TradeAdviser AI Reasoning Engine.
            - You are an analyst and risk reviewer, not an execution engine.
            - You cannot place trades or submit transactions.
            - You cannot override RiskManagementSystem hard blockers.
            - You cannot override QuantPortfolioManager portfolio blockers.
            - You cannot override FinalRiskGate.
            - You cannot increase position size beyond approved limits.
            - You cannot increase leverage beyond approved limits.
            - You cannot remove stop loss protection.
            - You cannot approve trades that violate RiskProfile constraints.
            - You must prioritize capital protection over opportunity.
            - If critical data is missing, choose WAIT or ESCALATE_TO_MANUAL_REVIEW.
            - Return JSON only. No markdown. No prose outside JSON.
            """;

    private static final String TRADE_REVIEW_SYSTEM_PROMPT = BASE_GUARDRAILS + """
            
            YOUR TASK:
            Review a proposed new trade using structured signal, risk, market, account, and portfolio context.
            
            ALLOWED DECISIONS:
            - APPROVE
            - APPROVE_WITH_REDUCED_SIZE
            - WAIT
            - REJECT
            - ESCALATE_TO_MANUAL_REVIEW
            
            DECISION CRITERIA:
            1. If RiskDecision has blockers, you cannot APPROVE.
            2. If probability is VERY_LOW or LOW, you cannot APPROVE.
            3. If liquidity is ILLIQUID and execution is MARKET, you must REJECT.
            4. If capital protection is NONE and psychology is IMPULSIVE or FEARFUL, you cannot APPROVE.
            5. If portfolio heat exceeds the allowed limit, you cannot APPROVE.
            6. If current drawdown is high, reduce size, wait, or reject.
            7. If setup is valid but conditions are risky, use APPROVE_WITH_REDUCED_SIZE.
            8. If information is incomplete or inconsistent, use ESCALATE_TO_MANUAL_REVIEW.
            
            RESPONSE JSON SCHEMA:
            {
              "decision": "APPROVE|APPROVE_WITH_REDUCED_SIZE|WAIT|REJECT|ESCALATE_TO_MANUAL_REVIEW",
              "confidence": 0.0,
              "suggestedRiskMultiplier": 0.0,
              "suggestedPositionSize": 0.0,
              "recommendedExecutionStrategy": "MARKET|LIMIT|ICEBERG|VWAP|TWAP|SCALED_ENTRY|SCALED_EXIT|ALGORITHMIC|null",
              "confirmations": [],
              "concerns": [],
              "blockers": [],
              "recommendations": [],
              "explanation": ""
            }
            """;

    private static final String POSITION_MANAGEMENT_SYSTEM_PROMPT = BASE_GUARDRAILS + """
            
            YOUR TASK:
            Review an existing open position and recommend a safe position-management action.
            
            ALLOWED ACTIONS:
            - HOLD
            - REDUCE_SIZE
            - TAKE_PARTIAL_PROFIT
            - MOVE_STOP_LOSS
            - TRAIL_STOP
            - MOVE_TAKE_PROFIT
            - CLOSE_POSITION
            - HEDGE
            - ESCALATE_TO_MANUAL_REVIEW
            
            POSITION MANAGEMENT RULES:
            1. You cannot increase position size.
            2. You cannot increase leverage.
            3. You cannot remove stop loss.
            4. You cannot move stop loss farther away if that increases risk, unless manual review is required.
            5. If deterministic position risk says EXIT_REQUIRED, you cannot recommend HOLD.
            6. If liquidity is poor, warn before recommending market close.
            7. HEDGE requires explicit broker/account support. Otherwise escalate.
            8. Prefer risk-reducing actions when uncertainty is high.
            
            RESPONSE JSON SCHEMA:
            {
              "action": "HOLD|REDUCE_SIZE|TAKE_PARTIAL_PROFIT|MOVE_STOP_LOSS|TRAIL_STOP|MOVE_TAKE_PROFIT|CLOSE_POSITION|HEDGE|ESCALATE_TO_MANUAL_REVIEW",
              "confidence": 0.0,
              "suggestedStopLoss": null,
              "suggestedTakeProfit": null,
              "suggestedCloseQuantity": null,
              "suggestedRiskReductionPercent": null,
              "confirmations": [],
              "concerns": [],
              "blockers": [],
              "recommendations": [],
              "explanation": ""
            }
            """;

    private static final String STRATEGY_SELECTION_SYSTEM_PROMPT = BASE_GUARDRAILS + """
            
            YOUR TASK:
            Review ranked strategy research results and recommend the best strategy assignment for a symbol/timeframe.
            
            RULES:
            1. You cannot recommend a strategy that failed hard risk checks.
            2. You cannot recommend a strategy with severe overfitting warnings.
            3. You cannot override manually locked assignments.
            4. Prefer stable risk-adjusted performance over highest raw profit.
            5. Penalize high drawdown, too few trades, unstable out-of-sample results, and high slippage sensitivity.
            
            RESPONSE JSON SCHEMA:
            {
              "recommendedStrategyId": "",
              "confidence": 0.0,
              "recommendedMode": "AUTO|MANUAL|AI_ASSISTED|DISABLED",
              "reasons": [],
              "concerns": [],
              "warnings": [],
              "shouldUseManualReview": false,
              "explanation": ""
            }
            """;

    private static final String PORTFOLIO_REVIEW_SYSTEM_PROMPT = BASE_GUARDRAILS + """
            
            YOUR TASK:
            Review portfolio-level risk context and provide recommendations to the Quant Portfolio Manager.
            
            RULES:
            1. Portfolio heat, drawdown, and concentration risk are more important than one attractive signal.
            2. If portfolio risk state is DANGER or STOP_TRADING, recommend defensive action.
            3. If correlated exposure is high, recommend reducing or rejecting additional exposure.
            4. If strategy drift is detected, recommend reduced allocation or manual review.
            
            RESPONSE JSON SCHEMA:
            {
              "decision": "APPROVE|REDUCE_SIZE|WAIT|REJECT|DEFENSIVE_MODE|STOP_TRADING|ESCALATE_TO_MANUAL_REVIEW",
              "confidence": 0.0,
              "recommendedCapitalAllocation": null,
              "recommendedRiskReductionPercent": null,
              "confirmations": [],
              "concerns": [],
              "blockers": [],
              "recommendations": [],
              "explanation": ""
            }
            """;

    private static final String STELLAR_REVIEW_SYSTEM_PROMPT = BASE_GUARDRAILS + """
            
            YOUR TASK:
            Review a Stellar network exchange action.
            
            STELLAR RULES:
            - Stellar is a network/DEX-style exchange, not a centralized exchange.
            - Authentication uses public key and optional secret key for signing.
            - Never ask for, print, or expose the secret key.
            - Public key allows read-only review.
            - Secret key is only for local signing by the application.
            - Issued assets require trustlines.
            - XLM is native and does not require a trustline.
            - Assets with the same code but different issuers are different assets.
            - Unknown issuer risk must be treated seriously.
            - High slippage path payments require warning or rejection.
            - Insufficient XLM reserve is a blocker.
            
            RESPONSE JSON SCHEMA:
            {
              "decision": "APPROVE|WAIT|REJECT|ESCALATE_TO_MANUAL_REVIEW",
              "confidence": 0.0,
              "issuerRiskWarning": "",
              "trustlineRequired": false,
              "reserveWarning": "",
              "slippageWarning": "",
              "confirmations": [],
              "concerns": [],
              "blockers": [],
              "recommendations": [],
              "explanation": ""
            }
            """;

    public static String getTradeReviewSystemPrompt() {
        return TRADE_REVIEW_SYSTEM_PROMPT;
    }

    public static String getPositionManagementSystemPrompt() {
        return POSITION_MANAGEMENT_SYSTEM_PROMPT;
    }

    public static String getStrategySelectionSystemPrompt() {
        return STRATEGY_SELECTION_SYSTEM_PROMPT;
    }

    public static String getPortfolioReviewSystemPrompt() {
        return PORTFOLIO_REVIEW_SYSTEM_PROMPT;
    }

    public static String getStellarReviewSystemPrompt() {
        return STELLAR_REVIEW_SYSTEM_PROMPT;
    }

    /**
     * Backward-compatible method for existing code.
     */
    public static String getSystemPrompt() {
        return getTradeReviewSystemPrompt();
    }

    public static String buildUserMessage(AiTradeReviewRequest request) {
        Objects.requireNonNull(request, "AiTradeReviewRequest cannot be null");

        StringBuilder sb = new StringBuilder();

        appendHeader(sb, "TRADE REVIEW REQUEST");

        appendSection(sb, "SIGNAL");
        appendLine(sb, "Symbol", request.getSymbol());
        appendLine(sb, "Side", request.getSignalSide());
        appendLine(sb, "Strategy", request.getStrategyName());
        appendLine(sb, "Confidence", percent(request.getSignalConfidence()));
        appendLine(sb, "Reason", request.getSignalReason());

        appendSection(sb, "RISK DECISION");
        if (request.getRiskDecision() != null) {
            appendLine(sb, "Risk Blockers", collectionOrNone(request.getRiskDecision().getBlockers()));
            appendLine(sb, "Risk Warnings", collectionOrNone(request.getRiskDecision().getWarnings()));
            appendLine(sb, "Final Position Size", decimal(request.getRiskDecision().getFinalPositionSize()));
            appendLine(sb, "Final Leverage", decimal(request.getRiskDecision().getFinalLeverage()) + "x");
            appendLine(sb, "Risk Multiplier", decimal(request.getRiskDecision().getRiskMultiplier()));
            appendLine(sb, "Expected Value", decimal(request.getRiskDecision().getExpectedValue()));
            appendLine(sb, "Portfolio Heat", decimal(request.getRiskDecision().getPortfolioHeat()));
            appendLine(sb, "Estimated Slippage", decimal(request.getRiskDecision().getEstimatedSlippage()));
        } else {
            appendLine(sb, "Risk Decision", "Missing");
        }

        appendSection(sb, "RISK CONTEXT");
        if (request.getRiskContext() != null) {
            appendLine(sb, "Risk Profile", request.getRiskContext().getRiskProfile());
            appendLine(sb, "Capital Protection", request.getRiskContext().getCapitalProtection());
            appendLine(sb, "Psychology", request.getRiskContext().getPsychologyProfile());
            appendLine(sb, "Probability Level", request.getRiskContext().getProbabilityLevel());
            appendLine(sb, "Liquidity Profile", request.getRiskContext().getLiquidityProfile());
            appendLine(sb, "Market Behavior", request.getRiskContext().getMarketBehavior());
            appendLine(sb, "Execution Strategy", request.getRiskContext().getExecutionStrategy());
            appendLine(sb, "System Design", request.getRiskContext().getSystemDesign());
        } else {
            appendLine(sb, "Risk Context", "Missing");
        }

        appendSection(sb, "MARKET CONDITIONS");
        appendLine(sb, "Current Price", decimal5(request.getCurrentPrice()));
        appendLine(sb, "Spread", percentRaw(request.getSpreadPercent()));
        appendLine(sb, "ATR", decimal5(request.getAtr()));
        appendLine(sb, "Volatility", percentRaw(request.getVolatilityPercent()));

        appendSection(sb, "ACCOUNT STATE");
        appendLine(sb, "Equity", money(request.getAccountEquity()));
        appendLine(sb, "Drawdown", percentRaw(request.getCurrentDrawdownPercent()));
        appendLine(sb, "Portfolio Heat", percentRaw(request.getPortfolioHeatPercent()));

        appendOptionalBlock(sb, "OPEN POSITIONS", request.getOpenPositionsSummary());
        appendOptionalBlock(sb, "RECENT TRADE HISTORY", request.getRecentTradeHistorySummary());
        appendOptionalBlock(sb, "NEWS / CONTEXT", request.getNewsContext());
        appendOptionalBlock(sb, "USER NOTES", request.getUserNotes());

        appendFooter(sb, "Review this proposed trade and return only valid JSON. Do not include markdown.");

        return sb.toString();
    }

    /**
     * Generic builder for future request types until dedicated model classes exist.
     */
    public static String buildStructuredUserMessage(String title, String structuredContext) {
        StringBuilder sb = new StringBuilder();
        appendHeader(sb, nullTo(title, "AI REVIEW REQUEST"));
        sb.append(nullTo(structuredContext, "No structured context provided.")).append("\n\n");
        appendFooter(sb, "Return only valid JSON matching the requested schema.");
        return sb.toString();
    }

    private static void appendHeader(StringBuilder sb, String title) {
        sb.append(title).append("\n");
        sb.append("═════════════════════════════════════════════════════\n\n");
    }

    private static void appendSection(StringBuilder sb, String title) {
        sb.append("\n").append(title).append(":\n");
    }

    private static void appendLine(StringBuilder sb, String key, Object value) {
        sb.append("  • ")
                .append(key)
                .append(": ")
                .append(value == null ? "Missing" : value)
                .append("\n");
    }

    private static void appendOptionalBlock(StringBuilder sb, String title, String value) {
        if (value != null && !value.isBlank()) {
            appendSection(sb, title);
            sb.append(value).append("\n");
        }
    }

    private static void appendFooter(StringBuilder sb, String instruction) {
        sb.append("\n═════════════════════════════════════════════════════\n");
        sb.append(instruction).append("\n");
    }

    private static String collectionOrNone(Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return "None";
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (Object value : values) {
            joiner.add(String.valueOf(value));
        }
        return joiner.toString();
    }

    private static String percent(double value) {
        return String.format("%.1f%%", value * 100.0);
    }

    private static String percentRaw(double value) {
        return String.format("%.2f%%", value);
    }

    private static String decimal(double value) {
        return String.format("%.2f", value);
    }

    private static String decimal5(double value) {
        return String.format("%.5f", value);
    }

    private static @NotNull String money(double value) {
        return "$" + String.format("%.2f", value);
    }

    private static String nullTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}