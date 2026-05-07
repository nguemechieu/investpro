package org.investpro.ai;

/**
 * Interface for AI reasoning services that review proposed trades.
 * <p>
 * Implementations are expected to:
 * - Review trades using structured request/response objects
 * - Never place trades directly
 * - Fail safely with ESCALATE_TO_MANUAL_REVIEW on errors
 * - Return within reasonable time (<5 seconds preferred)
 * <p>
 * Two implementations provided:
 * - LocalAiReasoningService: Deterministic fallback for offline/test mode
 * - OpenAiReasoningService: Real API integration with gpt-4-turbo
 */
public interface AiReasoningService {
    
    /**
     * Review a proposed trade and return a structured decision.
     *
     * @param request The trade review request with all context
     * @return The AI's review response with decision and reasoning
     */
    AiTradeReviewResponse reviewTrade(AiTradeReviewRequest request);
    
    /**
     * Check if the service is available and can process requests.
     *
     * @return true if service is operational, false if unavailable
     */
    boolean isAvailable();
    
    /**
     * Get the name of this AI service (for logging/auditing).
     *
     * @return Service name (e.g., "OpenAI GPT-4", "Local Fallback")
     */
    String getServiceName();
}
