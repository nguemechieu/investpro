package org.investpro.ai;

import org.jetbrains.annotations.NotNull;

/**
 * Builds system and user prompts for AI position manager.
 * Ensures consistent, safe prompting that respects risk constraints.
 * System prompt emphasizes:
 * - Cannot place orders
 * - Cannot override RiskManagementSystem or FinalRiskGate
 * - Can only recommend actions
 * - Must prioritize capital protection
 * - Must return structured JSON
 */
public class PositionManagementPromptBuilder {
    
    /**
     * Build the system prompt for position manager AI.
     * Defines role, constraints, and response format.
     */
    public static @NotNull String buildSystemPrompt() {
        return """
                You are TradeAdviser AI Position Manager.
                
                YOUR ROLE:
                You monitor open trading positions and provide management recommendations.
                You analyze position health, risk exposure, profit/loss, and market conditions.
                You suggest actions to protect capital and optimize risk-reward.
                
                CRITICAL CONSTRAINTS - YOU MUST OBEY THESE:
                1. You CANNOT place orders or execute trades directly.
                2. You CANNOT override RiskManagementSystem or FinalRiskGate decisions.
                3. You CANNOT increase leverage or position size.
                4. You CANNOT remove stop-loss orders.
                5. You CANNOT ignore hard risk rules (max drawdown, portfolio heat limits).
                6. You CANNOT move stop-loss farther away in ways that increase risk.
                
                DECISION FRAMEWORK:
                Available actions you may recommend:
                - HOLD: Keep position open, conditions are acceptable
                - REDUCE_SIZE: Lower exposure while keeping position alive
                - TAKE_PARTIAL_PROFIT: Close portion of position at favorable price
                - MOVE_STOP_LOSS: Adjust stop to better level (never farther, never closer without reason)
                - TRAIL_STOP: Tighten stop to protect profits as position moves favorably
                - MOVE_TAKE_PROFIT: Adjust profit target based on new analysis
                - CLOSE_POSITION: Close entire position (when risk rules require exit)
                - HEDGE: Open counter-position if broker supports and position is very risky
                - ESCALATE_TO_MANUAL_REVIEW: When uncertain or edge case detected
                
                CAPITAL PROTECTION RULES:
                - Never recommend holding when deterministic risk says EXIT_REQUIRED.
                - Prioritize capital preservation over profit maximization.
                - Reduce size if portfolio heat is high.
                - Close positions if drawdown limits are approached.
                - Recommend trailing stops on profitable positions.
                - Recommend partial profits when thesis weakens.
                
                CONFIDENCE SCORING:
                - 0.9-1.0: Confident recommendation, clear signals, strong thesis
                - 0.7-0.9: Reasonably confident, most signals aligned
                - 0.5-0.7: Moderate confidence, mixed signals
                - 0.3-0.5: Low confidence, prefer manual review
                - <0.3: Escalate to manual review (ESCALATE_TO_MANUAL_REVIEW action)
                
                RESPONSE FORMAT - YOU MUST RETURN VALID JSON:
                {
                    "action": "HOLD" | "REDUCE_SIZE" | "TAKE_PARTIAL_PROFIT" | "MOVE_STOP_LOSS" || 
                              "TRAIL_STOP" | "MOVE_TAKE_PROFIT" | "CLOSE_POSITION" | "HEDGE" | 
                              "ESCALATE_TO_MANUAL_REVIEW",
                    "confidence": 0.0-1.0,
                    "suggestedStopLoss": number or null,
                    "suggestedTakeProfit": number or null,
                    "suggestedCloseQuantity": number or null,
                    "suggestedRiskReductionPercent": number or null,
                    "confirmations": ["reason 1", "reason 2"],
                    "concerns": ["concern 1", "concern 2"],
                    "blockers": ["blocker 1"] or [],
                    "recommendations": ["specific action 1", "specific action 2"],
                    "explanation": "Natural language explanation of recommendation and reasoning"
                }
                
                ANALYSIS PRIORITIES:
                1. Capital preservation (is capital safe?)
                2. Risk management (is risk acceptable?)
                3. Profit protection (can gains be protected?)
                4. Opportunity (can gains be extended?)
                5. Trader psychology (was entry thesis invalidated?)
                """;
    }
    
    /**
     * Build the user prompt for analyzing a specific position.
     */
    public static @NotNull String buildUserPrompt(@NotNull AiPositionManagementRequest request) {
        if (!request.isValid()) {
            return """
                    POSITION REVIEW REQUEST - INCOMPLETE DATA
                    
                    ERROR: Position management request is incomplete or invalid.
                    Cannot make recommendation without complete position data.
                    
                    Please provide:
                    - Position ID and symbol
                    - Current price and entry price
                    - Position quantity and side
                    - Account equity and portfolio heat
                    - Market behavior and risk profile
                    
                    Recommendation: ESCALATE_TO_MANUAL_REVIEW
                    """;
        }
        
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("OPEN POSITION REVIEW REQUEST\n");
        prompt.append("═════════════════════════════════════════════════════════════\n\n");
        
        // Position Identity
        prompt.append("POSITION IDENTITY:\n");
        prompt.append("  Position ID: ").append(safe(request.getPositionId())).append("\n");
        prompt.append("  Symbol: ").append(request.getSymbol() != null ? request.getSymbol().toString('/') : "N/A").append("\n");
        prompt.append("  Asset Class: ").append(safe(request.getAssetClass())).append("\n");
        prompt.append("  Broker: ").append(safe(request.getBroker())).append("\n");
        prompt.append("  Age: ").append(request.getPositionAgeMinutes()).append(" minutes\n\n");
        
        // Position State
        prompt.append("POSITION STATE:\n");
        prompt.append("  Side: ").append(safe(request.getSide())).append("\n");
        prompt.append("  Quantity: ").append(formatNumber(request.getQuantity())).append("\n");
        prompt.append("  Entry Price: $").append(formatPrice(request.getEntryPrice())).append("\n");
        prompt.append("  Current Price: $").append(formatPrice(request.getCurrentPrice())).append("\n");
        prompt.append("  Leverage: ").append(formatNumber(request.getLeverage())).append("x\n\n");
        
        // P&L
        prompt.append("PROFIT & LOSS:\n");
        prompt.append("  Unrealized P&L: $").append(formatNumber(request.getUnrealizedPnl())).append("\n");
        prompt.append("  Unrealized P&L %: ").append(formatNumber(request.getUnrealizedPnlPercent())).append("%\n");
        prompt.append("  Max Favorable Move: ").append(formatNumber(request.getMaxFavorableExcursion())).append(" bps\n");
        prompt.append("  Max Adverse Move: ").append(formatNumber(request.getMaxAdverseExcursion())).append(" bps\n\n");
        
        // Stop & Take Profit
        prompt.append("RISK MANAGEMENT:\n");
        prompt.append("  Stop Loss: ").append(request.getCurrentStopLoss() != null ?
                "$%s".formatted(formatPrice(request.getCurrentStopLoss())) : "NOT SET").append("\n");
        prompt.append("  Take Profit: ").append(request.getCurrentTakeProfit() != null ?
                "$%s".formatted(formatPrice(request.getCurrentTakeProfit())) : "NOT SET").append("\n");
        if (request.getLiquidationPrice() != null) {
            prompt.append("  Liquidation Price: $").append(formatPrice(request.getLiquidationPrice())).append("\n");
        }
        prompt.append("\n");
        
        // Market Data
        prompt.append("MARKET CONDITIONS:\n");
        prompt.append("  Volatility: ").append(formatNumber(request.getVolatility())).append("%\n");
        prompt.append("  ATR: ").append(formatNumber(request.getAtr())).append("\n");
        prompt.append("  Spread: ").append(formatNumber(request.getSpread())).append(" bps\n");
        if (request.getMarketBehavior() != null) {
            prompt.append("  Market Behavior: ").append(request.getMarketBehavior().name()).append("\n");
        }
        prompt.append("\n");
        
        // Account Data
        prompt.append("ACCOUNT STATUS:\n");
        prompt.append("  Equity: $").append(formatNumber(request.getAccountEquity())).append("\n");
        prompt.append("  Drawdown: ").append(formatNumber(request.getCurrentDrawdown())).append("%\n");
        prompt.append("  Portfolio Heat: ").append(formatNumber(request.getPortfolioHeat())).append("%\n\n");
        
        // Risk Profile
        if (request.getRiskProfile() != null) {
            prompt.append("RISK PROFILE: ").append(request.getRiskProfile().name()).append("\n");
        }
        
        // Entry Thesis
        if (request.getOriginalTradeThesis() != null && !request.getOriginalTradeThesis().isBlank()) {
            prompt.append("ORIGINAL THESIS:\n");
            prompt.append("  ").append(request.getOriginalTradeThesis()).append("\n\n");
        }
        
        // New Warnings
        if (request.getNewWarnings() != null && !request.getNewWarnings().isEmpty()) {
            prompt.append("NEW WARNINGS:\n");
            for (String warning : request.getNewWarnings()) {
                prompt.append("  ⚠ ").append(warning).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("═════════════════════════════════════════════════════════════\n");
        prompt.append("\nAnalyze this position comprehensively.\n");
        prompt.append("Provide structured JSON recommendation (ONLY VALID JSON, no other text).\n");
        
        return prompt.toString();
    }
    
    // =========================================================================
    // Formatting Helpers
    // =========================================================================
    
    private static String safe(String value) {
        return value == null ? "N/A" : value.trim();
    }
    
    private static String formatNumber(double value) {
        if (!Double.isFinite(value)) return "N/A";
        return String.format("%.4f", value);
    }
    
    private static String formatPrice(double value) {
        if (!Double.isFinite(value)) return "N/A";
        return String.format("%.2f", value);
    }
}
