package org.investpro.ai;

/**
 * Builds stable, professional prompts for the AI reasoning engine.
 * Ensures consistent system messages and request formatting.
 *
 * The prompts are designed to:
 * - Constrain AI to trade review (not execution)
 * - Enforce risk guardrails
 * - Request structured JSON output
 * - Provide sufficient context for sound decisions
 */
public class AiPromptBuilder {
    
    private static final String SYSTEM_PROMPT = """
            You are the TradeAdviser AI Reasoning Engine.
            
            YOUR PURPOSE:
            - Review proposed trades based on structured context
            - Assess market conditions, risk metrics, and account state
            - Provide risk-aware recommendations
            - Return structured JSON decisions
            
            YOU CANNOT:
            - Place trades or issue orders directly
            - Override hard risk blockers from RiskManagementSystem
            - Increase position size beyond RiskDecision.finalPositionSize
            - Increase leverage beyond RiskDecision.finalLeverage
            - Approve trades that violate RiskProfile constraints
            
            YOU CAN:
            - APPROVE trades that pass all checks
            - APPROVE_WITH_REDUCED_SIZE if market conditions warrant caution
            - WAIT if market conditions are unfavorable (poor liquidity, high volatility)
            - REJECT trades with fundamental flaws
            - ESCALATE_TO_MANUAL_REVIEW for edge cases or missing data
            
            DECISION CRITERIA:
            1. If RiskDecision has blockers → Cannot APPROVE (must be WAIT, REJECT, or ESCALATE)
            2. If capital protection is NONE and psychology is IMPULSIVE/FEARFUL → Cannot APPROVE (suggest WAIT or REDUCED_SIZE)
            3. If probability is VERY_LOW or LOW → Cannot APPROVE (suggest WAIT or REJECT)
            4. If portfolio heat > max allowed → Cannot APPROVE (suggest REDUCED_SIZE or WAIT)
            5. If liquidity is ILLIQUID and execution is MARKET → Must REJECT
            6. If current drawdown > 20% → Suggest REDUCED_SIZE or WAIT
            7. Otherwise, evaluate market conditions and trade setup merit
            
            RESPONSE FORMAT:
            Always return a JSON object with these fields (exactly):
            {
              "decision": "APPROVE|APPROVE_WITH_REDUCED_SIZE|WAIT|REJECT|ESCALATE_TO_MANUAL_REVIEW",
              "confidence": 0.0-1.0,
              "suggestedRiskMultiplier": 0.0-1.0,
              "suggestedPositionSize": number (must be <= finalPositionSize),
              "recommendedExecutionStrategy": "MARKET_ORDER|LIMIT_ORDER|ICEBERG|VWAP|TWAP|SCALED|ALGORITHMIC|null",
              "confirmations": ["reason1", "reason2"],
              "concerns": ["concern1", "concern2"],
              "blockers": ["blocker1"] (empty if none),
              "recommendations": ["action1", "action2"],
              "explanation": "paragraph explaining the decision"
            }
            """;
    
    /**
     * Get the system prompt for the AI reasoning engine.
     * This prompt is static and enforces guardrails consistently.
     */
    public static String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }
    
    /**
     * Build a user message for a trade review request.
     */
    public static String buildUserMessage(AiTradeReviewRequest request) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("TRADE REVIEW REQUEST\n");
        sb.append("═════════════════════════════════════════════════════\n\n");
        
        // Trade Signal
        sb.append("SIGNAL:\n");
        sb.append("  • Symbol: ").append(request.getSymbol()).append("\n");
        sb.append("  • Side: ").append(request.getSignalSide()).append("\n");
        sb.append("  • Strategy: ").append(request.getStrategyName()).append("\n");
        sb.append("  • Confidence: ").append(String.format("%.1f%%", request.getSignalConfidence() * 100)).append("\n");
        sb.append("  • Reason: ").append(request.getSignalReason()).append("\n\n");
        
        // Risk Metrics
        sb.append("RISK CONTEXT:\n");
        if (request.getRiskDecision() != null) {
            sb.append("  • Risk Blockers: ")
                    .append(request.getRiskDecision().getBlockers().isEmpty() ? "None" : request.getRiskDecision().getBlockers())
                    .append("\n");
            sb.append("  • Final Position Size: ").append(String.format("%.2f", request.getRiskDecision().getFinalPositionSize())).append("\n");
            sb.append("  • Final Leverage: ").append(String.format("%.2fx", request.getRiskDecision().getFinalLeverage())).append("\n");
            sb.append("  • Risk Multiplier: ").append(String.format("%.2f", request.getRiskDecision().getRiskMultiplier())).append("\n");
        }
        if (request.getRiskContext() != null) {
            sb.append("  • Risk Profile: ").append(request.getRiskContext().getRiskProfile()).append("\n");
            sb.append("  • Capital Protection: ").append(request.getRiskContext().getCapitalProtection()).append("\n");
            sb.append("  • Psychology: ").append(request.getRiskContext().getPsychologyProfile()).append("\n");
            sb.append("  • Probability Level: ").append(request.getRiskContext().getProbabilityLevel()).append("\n");
        }
        sb.append("\n");
        
        // Market Conditions
        sb.append("MARKET CONDITIONS:\n");
        sb.append("  • Current Price: ").append(String.format("%.5f", request.getCurrentPrice())).append("\n");
        sb.append("  • Spread: ").append(String.format("%.4f%%", request.getSpreadPercent())).append("\n");
        sb.append("  • ATR: ").append(String.format("%.5f", request.getAtr())).append("\n");
        sb.append("  • Volatility: ").append(String.format("%.2f%%", request.getVolatilityPercent())).append("\n");
        if (request.getRiskContext() != null) {
            sb.append("  • Liquidity Profile: ").append(request.getRiskContext().getLiquidityProfile()).append("\n");
            sb.append("  • Market Behavior: ").append(request.getRiskContext().getMarketBehavior()).append("\n");
        }
        sb.append("\n");
        
        // Account State
        sb.append("ACCOUNT STATE:\n");
        sb.append("  • Equity: $").append(String.format("%.2f", request.getAccountEquity())).append("\n");
        sb.append("  • Drawdown: ").append(String.format("%.2f%%", request.getCurrentDrawdownPercent())).append("\n");
        sb.append("  • Portfolio Heat: ").append(String.format("%.2f%%", request.getPortfolioHeatPercent())).append("\n\n");
        
        // Position Summary
        if (request.getOpenPositionsSummary() != null && !request.getOpenPositionsSummary().isBlank()) {
            sb.append("OPEN POSITIONS:\n").append(request.getOpenPositionsSummary()).append("\n\n");
        }
        
        // Trade History
        if (request.getRecentTradeHistorySummary() != null && !request.getRecentTradeHistorySummary().isBlank()) {
            sb.append("RECENT TRADE HISTORY:\n").append(request.getRecentTradeHistorySummary()).append("\n\n");
        }
        
        // News Context
        if (request.getNewsContext() != null && !request.getNewsContext().isBlank()) {
            sb.append("NEWS/CONTEXT:\n").append(request.getNewsContext()).append("\n\n");
        }
        
        // User Notes
        if (request.getUserNotes() != null && !request.getUserNotes().isBlank()) {
            sb.append("USER NOTES:\n").append(request.getUserNotes()).append("\n\n");
        }
        
        sb.append("═════════════════════════════════════════════════════\n");
        sb.append("Please review this trade request and respond with structured JSON.\n");
        sb.append("Remember: You cannot override hard risk blockers or increase position size beyond limits.\n");
        
        return sb.toString();
    }
}
