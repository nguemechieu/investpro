package org.investpro.ai;

/**
 * Interface for AI-based position management recommendations.
 * <p>
 * AI can review open positions and recommend actions (hold, reduce, close, etc.)
 * but CANNOT directly execute orders or bypass risk controls.
 * <p>
 * Implementations must:
 * - Return structured recommendations only
 * - Never send execution orders
 * - Handle errors gracefully (ESCALATE_TO_MANUAL_REVIEW on uncertainty)
 * - Respect the RiskManagementSystem and FinalRiskGate authority
 */
public interface AiPositionManagerService {
    
    /**
     * Review an open position and provide management recommendation.
     *
     * @param request Complete position management context
     * @return AI recommendation with confidence and reasoning
     *
     * Never returns null. Returns failedResponse() if unable to process.
     * Does not modify position state or execute orders.
     */
    AiPositionManagementResponse reviewOpenPosition(AiPositionManagementRequest request);
    
    /**
     * Get the name of this AI service (e.g., "gpt-4-turbo", "local-fallback").
     */
    String getModelName();
    
    /**
     * Check if this service is currently available/operational.
     */
    boolean isAvailable();
    
    /**
     * Get average processing time in milliseconds.
     */
    long getAverageProcessingTimeMs();
}
