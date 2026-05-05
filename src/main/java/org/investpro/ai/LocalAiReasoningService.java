package org.investpro.ai;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic local AI reasoning service.
 * Used when:
 * - OpenAI API key is not configured
 * - Running offline or in test mode
 * - As a fallback when the real API is unavailable
 *
 * Logic is rule-based and deterministic, designed to be conservative and safe.
 * Not a real AI, but produces sound recommendations based on risk factors.
 */
public class LocalAiReasoningService implements AiReasoningService {
    
    private static final String SERVICE_NAME = "Local Fallback";
    
    @Override
    public AiTradeReviewResponse reviewTrade(AiTradeReviewRequest request) {
        if (request == null) {
            return AiTradeReviewResponse.incompleteDataResponse("Request is null");
        }

        try {
            // Validate input
            if (!isRequestComplete(request)) {
                return AiTradeReviewResponse.incompleteDataResponse("Missing required trade context");
            }

            // Evaluate trade using deterministic logic
            return evaluateTradeLogic(request);

        } catch (Exception e) {
            return AiTradeReviewResponse.failedResponse(e.getMessage(), SERVICE_NAME);
        }
    }
    
    @Override
    public boolean isAvailable() {
        return true; // Local fallback is always available
    }
    
    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }
    
    /**
     * Check if request has all required fields.
     */
    private boolean isRequestComplete(AiTradeReviewRequest request) {
        return request.getSymbol() != null && !request.getSymbol().isBlank()
                && request.getSignalSide() != null && !request.getSignalSide().isBlank()
                && request.getStrategyName() != null && !request.getStrategyName().isBlank()
                && request.getRiskContext() != null
                && request.getRiskDecision() != null
                && request.getAccountEquity() > 0;
    }
    
    /**
     * Deterministic trade evaluation logic.
     */
    private AiTradeReviewResponse evaluateTradeLogic(AiTradeReviewRequest request) {
        List<String> confirmations = new ArrayList<>();
        List<String> concerns = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        
        // Check for hard blockers first
        if (request.getRiskDecision().getBlockers() != null && !request.getRiskDecision().getBlockers().isEmpty()) {
            blockers.addAll(request.getRiskDecision().getBlockers());
        }
        
        // If RiskManagementSystem already blocked it, we cannot approve
        if (!blockers.isEmpty()) {
            return AiTradeReviewResponse.builder()
                    .decision(AiDecision.WAIT)
                    .confidence(0.8)
                    .suggestedRiskMultiplier(0.0)
                    .suggestedPositionSize(0.0)
                    .recommendedExecutionStrategy(null)
                    .confirmations(List.of())
                    .concerns(List.of("Risk blockers exist from RiskManagementSystem"))
                    .blockers(blockers)
                    .recommendations(List.of("Address risk blockers before trading"))
                    .explanation("The RiskManagementSystem has identified hard blockers. These must be resolved before trading can proceed.")
                    .modelName(SERVICE_NAME)
                    .createdAt(LocalDateTime.now())
                    .processingTimeMs(System.currentTimeMillis())
                    .hadErrors(false)
                    .build();
        }
        
        // Check signal confidence
        double confidence = calculateConfidence(request);
        if (request.getSignalConfidence() < 0.5) {
            concerns.add("Signal confidence is low (< 50%)");
            recommendations.add("Wait for higher confidence signal");
        } else {
            confirmations.add("Signal confidence is acceptable (%s)".formatted(String.format("%.0f%%", request.getSignalConfidence() * 100)));
        }
        
        // Check account drawdown
        double drawdown = request.getCurrentDrawdownPercent();
        if (drawdown > 20.0) {
            concerns.add("Account drawdown is significant (%s)".formatted(String.format("%.1f%%", drawdown)));
            recommendations.add("Consider reduced position size until drawdown recovers");
        } else if (drawdown > 10.0) {
            concerns.add("Account is in moderate drawdown (%s)".formatted(String.format("%.1f%%", drawdown)));
        } else {
            confirmations.add("Account drawdown is minimal");
        }
        
        // Check portfolio heat
        double portfolioHeat = request.getPortfolioHeatPercent();
        if (portfolioHeat > 20.0) {
            concerns.add("Portfolio heat is elevated (%s)".formatted(String.format("%.1f%%", portfolioHeat)));
            recommendations.add("Consider waiting or reducing position size");
        } else {
            confirmations.add("Portfolio heat is within acceptable range");
        }
        
        // Check volatility
        if (request.getVolatilityPercent() > 30.0) {
            concerns.add("Volatility is elevated (%s)".formatted(String.format("%.1f%%", request.getVolatilityPercent())));
            recommendations.add("Use limit orders instead of market orders");
        } else {
            confirmations.add("Volatility is moderate");
        }
        
        // Check liquidity
        if (request.getRiskContext() != null && request.getRiskContext().getLiquidityProfile() != null) {
            String liquidity = request.getRiskContext().getLiquidityProfile().toString();
            if (liquidity.contains("ILLIQUID") || liquidity.contains("THIN")) {
                concerns.add("Market liquidity is poor");
                recommendations.add("Avoid market orders, use limit orders");
            } else {
                confirmations.add("Market liquidity is acceptable");
            }
        }
        
        // Check psychology profile
        if (request.getRiskContext() != null && request.getRiskContext().getPsychologyProfile() != null) {
            String psychology = request.getRiskContext().getPsychologyProfile().toString();
            if (psychology.contains("IMPULSIVE") || psychology.contains("FEARFUL")) {
                concerns.add("Psychology profile indicates elevated emotional risk");
                recommendations.add("Use stop losses and risk limits strictly");
            }
        }
        
        // Determine decision based on factors
        AiDecision decision = determineDecision(request, blockers, concerns, drawdown, portfolioHeat);
        
        // Build response
        return AiTradeReviewResponse.builder()
                .decision(decision)
                .confidence(confidence)
                .suggestedRiskMultiplier(calculateRiskMultiplier(request, drawdown, portfolioHeat))
                .suggestedPositionSize(calculateSuggestedPositionSize(request, drawdown, portfolioHeat))
                .recommendedExecutionStrategy(recommendExecutionStrategy(request))
                .confirmations(confirmations)
                .concerns(concerns)
                .blockers(blockers)
                .recommendations(recommendations)
                .explanation(buildExplanation(request, decision, concerns))
                .modelName(SERVICE_NAME)
                .createdAt(LocalDateTime.now())
                .processingTimeMs(1L) // Local evaluation is fast
                .hadErrors(false)
                .build();
    }
    
    /**
     * Calculate overall confidence in the decision.
     */
    private double calculateConfidence(AiTradeReviewRequest request) {
        double baseConfidence = 0.7; // Base confidence
        
        // Adjust by signal confidence
        baseConfidence += request.getSignalConfidence() * 0.2;
        
        // Adjust by account state
        if (request.getCurrentDrawdownPercent() < 5.0) {
            baseConfidence += 0.05;
        } else if (request.getCurrentDrawdownPercent() > 15.0) {
            baseConfidence -= 0.1;
        }
        
        return Math.min(1.0, baseConfidence);
    }
    
    /**
     * Determine the AI decision based on risk factors.
     */
    private AiDecision determineDecision(AiTradeReviewRequest request, 
                                        List<String> blockers,
                                        List<String> concerns,
                                        double drawdown,
                                        double portfolioHeat) {
        // If there are hard blockers, cannot approve
        if (!blockers.isEmpty()) {
            return AiDecision.WAIT;
        }
        
        // If too many concerns, wait or reject
        if (concerns.size() >= 4) {
            return AiDecision.WAIT;
        }
        
        // If significant drawdown, reduce size or wait
        if (drawdown > 15.0) {
            return AiDecision.APPROVE_WITH_REDUCED_SIZE;
        }
        
        // If portfolio heat is high, reduce size
        if (portfolioHeat > 15.0) {
            return AiDecision.APPROVE_WITH_REDUCED_SIZE;
        }
        
        // If signal confidence is low, wait
        if (request.getSignalConfidence() < 0.5) {
            return AiDecision.WAIT;
        }
        
        // All checks pass, approve
        return AiDecision.APPROVE;
    }
    
    /**
     * Calculate suggested risk multiplier based on account state.
     */
    private double calculateRiskMultiplier(AiTradeReviewRequest request, double drawdown, double portfolioHeat) {
        double multiplier = 1.0;
        
        // Reduce for drawdown
        if (drawdown > 20.0) {
            multiplier *= 0.5;
        } else if (drawdown > 10.0) {
            multiplier *= 0.75;
        }
        
        // Reduce for high portfolio heat
        if (portfolioHeat > 20.0) {
            multiplier *= 0.6;
        } else if (portfolioHeat > 15.0) {
            multiplier *= 0.8;
        }
        
        // Reduce for high volatility
        if (request.getVolatilityPercent() > 30.0) {
            multiplier *= 0.9;
        }
        
        return Math.min(1.0, multiplier);
    }
    
    /**
     * Calculate suggested position size.
     */
    private double calculateSuggestedPositionSize(AiTradeReviewRequest request, double drawdown, double portfolioHeat) {
        double riskMultiplier = calculateRiskMultiplier(request, drawdown, portfolioHeat);
        double maxPositionSize = request.getRiskDecision().getFinalPositionSize();
        return maxPositionSize * riskMultiplier;
    }
    
    /**
     * Recommend execution strategy based on market conditions.
     */
    private String recommendExecutionStrategy(AiTradeReviewRequest request) {
        if (request.getVolatilityPercent() > 25.0) {
            return "LIMIT_ORDER";
        }
        if (request.getSpreadPercent() > 0.05) {
            return "LIMIT_ORDER";
        }
        if (request.getRiskContext() != null && request.getRiskContext().getLiquidityProfile() != null) {
            String liquidity = request.getRiskContext().getLiquidityProfile().toString();
            if (liquidity.contains("THIN") || liquidity.contains("ILLIQUID")) {
                return "LIMIT_ORDER";
            }
        }
        return "MARKET_ORDER";
    }
    
    /**
     * Build explanation text.
     */
    private String buildExplanation(AiTradeReviewRequest request, AiDecision decision, List<String> concerns) {
        StringBuilder sb = new StringBuilder();
        
        switch (decision) {
            case APPROVE:
                sb.append("Trade setup appears sound. Signal confidence is acceptable, risk metrics are within limits, and market conditions support entry. ");
                sb.append("The risk framework has approved this trade through RiskManagementSystem.");
                break;
            case APPROVE_WITH_REDUCED_SIZE:
                sb.append("Trade setup is valid but current account state warrants reduced position size. ");
                if (request.getCurrentDrawdownPercent() > 10.0) {
                    sb.append("Account is in drawdown; ");
                }
                if (request.getPortfolioHeatPercent() > 15.0) {
                    sb.append("Portfolio heat is elevated; ");
                }
                sb.append("reducing position size mitigates recovery risk.");
                break;
            case WAIT:
                sb.append("Trade is worth considering but market conditions are not optimal. ");
                if (concerns.contains("Signal confidence is low (< 50%)")) {
                    sb.append("Signal confidence is low. ");
                }
                sb.append("Waiting for better market conditions or higher confidence signal is prudent.");
                break;
            case REJECT:
                sb.append("Trade should not be executed at this time. ");
                if (!concerns.isEmpty()) {
                    sb.append("Issues identified: ").append(String.join(", ", concerns)).append(".");
                }
                break;
            case ESCALATE_TO_MANUAL_REVIEW:
                sb.append("Edge case or ambiguous data detected. Manual human review is recommended.");
                break;
        }
        
        return sb.toString();
    }
}
