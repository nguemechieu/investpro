package org.investpro.ai;

/**
 * Interface for AI-based position management services.
 * Provides recommendations for open positions based on market conditions and
 * risk analysis.
 */
public interface AiPositionManagerService {

    /**
     * Review an open position and provide a management action.
     *
     * @param request The position management request with position details
     * @return The AI's response with recommended actions
     */
    AiPositionManagementResponse reviewOpenPosition(AiPositionManagementRequest request);

    /**
     * Get the model name used by this service.
     *
     * @return The model name
     */
    String getModelName();

    /**
     * Check if this service is available and operational.
     *
     * @return true if the service is available
     */
    boolean isAvailable();

    /**
     * Get the average processing time in milliseconds.
     *
     * @return Average processing time
     */
    long getAverageProcessingTimeMs();
}
