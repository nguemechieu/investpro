package org.investpro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-based AI reasoning service.
 * Uses GPT-4 Turbo to review trades with real AI reasoning.
 * <p>
 * Features:
 * - Reads API key from OPENAI_API_KEY environment variable
 * - Fails safely if API key is not configured
 * - Requests structured JSON output for reliable parsing
 * - Returns ESCALATE_TO_MANUAL_REVIEW on errors
 * - Logs all API calls and responses for debugging
 */
@Slf4j
public class OpenAiReasoningService implements AiReasoningService {
    private static final String SERVICE_NAME = "OpenAI GPT-4 Turbo";
    private static final String API_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4-turbo";
    private static final int TIMEOUT_SECONDS = 30;

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LocalAiReasoningService fallback; // Fallback if API fails

    /**
     * Create OpenAI service. Reads API key from OPENAI_API_KEY environment
     * variable.
     * If key is not configured, service will not be available but will not crash.
     */
    @SuppressWarnings("unused")
    public OpenAiReasoningService(String apiKey) {
        this.apiKey = System.getenv("OPENAI_API_KEY");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        this.objectMapper = new ObjectMapper();
        this.fallback = new LocalAiReasoningService();

        if (this.apiKey == null || this.apiKey.isBlank()) {
            log.info(
                    "OpenAI API key not configured. Using local fallback service. Set OPENAI_API_KEY environment variable to enable.");
        } else {
            log.info("OpenAI API service initialized successfully");
        }
    }

    @Override
    public AiTradeReviewResponse reviewTrade(AiTradeReviewRequest request) {
        // If no API key, use fallback
        if (!isAvailable()) {
            log.debug("OpenAI API not configured, using local fallback");
            return fallback.reviewTrade(request);
        }

        long startTime = System.currentTimeMillis();

        try {
            // Build request to OpenAI API
            String systemPrompt = AiPromptBuilder.getSystemPrompt();
            String userMessage = AiPromptBuilder.buildUserMessage(request);

            String jsonBody = buildRequestJson(systemPrompt, userMessage);

            // Call OpenAI API
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(API_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer %s".formatted(apiKey))
                    .timeout(java.time.Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long processingTime = System.currentTimeMillis() - startTime;

            if (response.statusCode() != 200) {
                log.warn("OpenAI API returned status {}: {}", response.statusCode(), response.body());
                return fallback.reviewTrade(request);
            }

            // Parse response
            return parseOpenAiResponse(response.body(), processingTime);

        } catch (Exception e) {
            log.error("Error calling OpenAI API, falling back to local service: {}", e.getMessage());
            return fallback.reviewTrade(request);
        }
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    /**
     * Build JSON request for OpenAI API.
     */
    private String buildRequestJson(String systemPrompt, String userMessage) throws Exception {
        Map<String, Object> request = Map.of(
                "model", MODEL,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", systemPrompt),
                        Map.of(
                                "role", "user",
                                "content", userMessage)),
                "temperature", 0.7,
                "max_tokens", 2000,
                "response_format", Map.of("type", "json_object"));

        return objectMapper.writeValueAsString(request);
    }

    /**
     * Parse OpenAI API response and extract AI decision.
     */

    private AiTradeReviewResponse parseOpenAiResponse(String responseBody, long processingTime) {
        try {
            Map response = objectMapper.readValue(responseBody, Map.class);

            // Extract content from response
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                return AiTradeReviewResponse.failedResponse("Empty response from OpenAI API", SERVICE_NAME);
            }

            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            String content = (String) message.get("content");

            // Parse JSON response from AI
            Map<String, Object> aiDecision = objectMapper.readValue(content, Map.class);

            // Extract fields from AI response
            String decisionStr = (String) aiDecision.get("decision");
            AiDecision decision = AiDecision.valueOf(decisionStr);
            double confidence = ((Number) aiDecision.get("confidence")).doubleValue();
            double suggestedRiskMultiplier = ((Number) aiDecision.get("suggestedRiskMultiplier")).doubleValue();
            double suggestedPositionSize = ((Number) aiDecision.get("suggestedPositionSize")).doubleValue();
            String recommendedStrategy = (String) aiDecision.get("recommendedExecutionStrategy");

            List<String> confirmations = (List<String>) aiDecision.get("confirmations");
            List<String> concerns = (List<String>) aiDecision.get("concerns");
            List<String> blockers = (List<String>) aiDecision.get("blockers");
            List<String> recommendations = (List<String>) aiDecision.get("recommendations");
            String explanation = (String) aiDecision.get("explanation");

            // Validate AI response (ensure it doesn't violate guardrails)
            if (!isValidAiResponse(decision, suggestedRiskMultiplier, suggestedPositionSize)) {
                log.warn("AI response violated guardrails, escalating to manual review");
                return AiTradeReviewResponse.builder()
                        .decision(AiDecision.ESCALATE_TO_MANUAL_REVIEW)
                        .confidence(0.0)
                        .suggestedRiskMultiplier(0.0)
                        .suggestedPositionSize(0.0)
                        .confirmations(List.of())
                        .concerns(List.of("AI response violated risk guardrails"))
                        .blockers(List.of())
                        .recommendations(List.of("Manual review required"))
                        .explanation("AI response was constrained due to guardrail violations")
                        .modelName(SERVICE_NAME)
                        .createdAt(LocalDateTime.now())
                        .processingTimeMs(processingTime)
                        .hadErrors(false)
                        .build();
            }

            return AiTradeReviewResponse.builder()
                    .decision(decision)
                    .confidence(confidence)
                    .suggestedRiskMultiplier(suggestedRiskMultiplier)
                    .suggestedPositionSize(suggestedPositionSize)
                    .recommendedExecutionStrategy(recommendedStrategy)
                    .confirmations(confirmations != null ? confirmations : new ArrayList<>())
                    .concerns(concerns != null ? concerns : new ArrayList<>())
                    .blockers(blockers != null ? blockers : new ArrayList<>())
                    .recommendations(recommendations != null ? recommendations : new ArrayList<>())
                    .explanation(explanation != null ? explanation : "")
                    .modelName(SERVICE_NAME)
                    .createdAt(LocalDateTime.now())
                    .processingTimeMs(processingTime)
                    .hadErrors(false)
                    .build();

        } catch (Exception e) {
            log.error("Error parsing OpenAI response: {}", e.getMessage());
            return AiTradeReviewResponse.failedResponse("Failed to parse AI response: %s".formatted(e.getMessage()),
                    SERVICE_NAME);
        }
    }

    /**
     * Validate that AI response respects guardrails.
     */
    private boolean isValidAiResponse(AiDecision decision, double riskMultiplier, double positionSize) {
        // Risk multiplier must be between 0.0 and 1.0
        if (riskMultiplier < 0.0 || riskMultiplier > 1.0) {
            return false;
        }

        // Position size must be non-negative
        if (positionSize < 0.0) {
            return false;
        }

        // Decision must be one of the valid options
        return decision != null;
    }
}
