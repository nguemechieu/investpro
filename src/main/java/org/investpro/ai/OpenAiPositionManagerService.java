package org.investpro.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * OpenAI-based position manager.
 * Uses GPT to analyze open positions and provide recommendations.
 *
 * Requests structured JSON output.
 * Fails safely to ESCALATE_TO_MANUAL_REVIEW if API is unavailable or response is invalid.
 */
@SuppressWarnings("unused")
public class OpenAiPositionManagerService implements AiPositionManagerService {
    
    private static final Logger logger = Logger.getLogger(OpenAiPositionManagerService.class.getName());
    
    private final String apiKey;
    private final String model;
    private final String apiEndpoint;
    private final HttpClient httpClient;
    
    private long totalProcessingTimeMs = 0;
    private long callCount = 0;
    private boolean lastCallSuccessful = true;
    
    private static final String DEFAULT_MODEL = "gpt-4-turbo";
    private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final int TIMEOUT_SECONDS = 30;
    private static final double TEMPERATURE = 0.3; // Low temperature for consistent, deterministic output
    
    public OpenAiPositionManagerService(String apiKey) {
        this(apiKey, DEFAULT_MODEL, DEFAULT_ENDPOINT);
    }
    
    @SuppressWarnings("unused")
    public OpenAiPositionManagerService(String apiKey, String model, String endpoint) {
        this.apiKey = apiKey;
        this.model = model != null ? model : DEFAULT_MODEL;
        this.apiEndpoint = endpoint != null ? endpoint : DEFAULT_ENDPOINT;
        this.httpClient = HttpClient.newHttpClient();
    }
    
    @Override
    public AiPositionManagementResponse reviewOpenPosition(@NotNull AiPositionManagementRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            if (!request.isValid()) {
                logger.warning("Invalid position request");
                lastCallSuccessful = false;
                return AiPositionManagementResponse.failedResponse("Invalid request data", getModelName());
            }
            
            // Build prompts
            String systemPrompt = PositionManagementPromptBuilder.buildSystemPrompt();
            String userPrompt = PositionManagementPromptBuilder.buildUserPrompt(request);
            
            // Call OpenAI API
            AiPositionManagementResponse response = callOpenAiApi(systemPrompt, userPrompt);
            
            // Record metrics
            long processingTime = System.currentTimeMillis() - startTime;
            response.setProcessingTimeMs(processingTime);
            totalProcessingTimeMs += processingTime;
            callCount++;
            lastCallSuccessful = !response.isHadErrors();
            
            return response;
            
        } catch (Exception e) {
            logger.severe("OpenAI position manager error: %s".formatted(e.getMessage()));
            lastCallSuccessful = false;
            return AiPositionManagementResponse.failedResponse(e.getMessage(), getModelName());
        }
    }
    
    /**
     * Call OpenAI API with structured JSON request.
     */
    private AiPositionManagementResponse callOpenAiApi(String systemPrompt, String userPrompt) {
        try {
            // Build request JSON
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("temperature", TEMPERATURE);
            requestBody.addProperty("max_tokens", 1000);
            
            // Add messages
            var messagesArray = new com.google.gson.JsonArray();
            
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemPrompt);
            messagesArray.add(systemMsg);
            
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userPrompt);
            messagesArray.add(userMsg);
            
            requestBody.add("messages", messagesArray);
            
            // Request JSON schema for structured output
            JsonObject responseFormat = new JsonObject();
            responseFormat.addProperty("type", "json_object");
            requestBody.add("response_format", responseFormat);
            
            // Build HTTP request
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiEndpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer %s".formatted(apiKey))
                    .timeout(java.time.Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();
            
            // Send request
            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            
            // Parse response
            if (httpResponse.statusCode() != 200) {
                logger.warning("OpenAI API error: %s".formatted(httpResponse.statusCode()));
                return AiPositionManagementResponse.failedResponse(
                        "API returned status %s".formatted(httpResponse.statusCode()), 
                        getModelName());
            }
            
            // Extract JSON from response
            JsonObject responseJson = JsonParser.parseString(httpResponse.body()).getAsJsonObject();
            
            if (!responseJson.has("choices") || responseJson.getAsJsonArray("choices").isEmpty()) {
                logger.warning("No choices in OpenAI response");
                return AiPositionManagementResponse.failedResponse("No AI response generated", getModelName());
            }
            
            String aiResponseText = responseJson.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .get("message").getAsJsonObject()
                    .get("content").getAsString();
            
            // Parse AI recommendation as JSON
            return parseAiResponse(aiResponseText);
            
        } catch (IOException | InterruptedException e) {
            logger.severe("HTTP communication error: %s".formatted(e.getMessage()));
            return AiPositionManagementResponse.failedResponse(e.getMessage(), getModelName());
        }
    }
    
    /**
     * Parse structured JSON response from AI.
     */
    private AiPositionManagementResponse parseAiResponse(String responseText) {
        try {
            // Try to extract JSON from response (in case there's surrounding text)
            String jsonText = extractJsonFromText(responseText);
            JsonObject json = JsonParser.parseString(jsonText).getAsJsonObject();
            
            // Extract fields
            AiPositionAction action = parseAction(json.get("action").getAsString());
            double confidence = json.get("confidence").getAsDouble();
            
            Double suggestedStopLoss = json.has("suggestedStopLoss") && !json.get("suggestedStopLoss").isJsonNull() ?
                    json.get("suggestedStopLoss").getAsDouble() : null;
            Double suggestedTakeProfit = json.has("suggestedTakeProfit") && !json.get("suggestedTakeProfit").isJsonNull() ?
                    json.get("suggestedTakeProfit").getAsDouble() : null;
            Double suggestedCloseQuantity = json.has("suggestedCloseQuantity") && !json.get("suggestedCloseQuantity").isJsonNull() ?
                    json.get("suggestedCloseQuantity").getAsDouble() : null;
            Double suggestedRiskReduction = json.has("suggestedRiskReductionPercent") && !json.get("suggestedRiskReductionPercent").isJsonNull() ?
                    json.get("suggestedRiskReductionPercent").getAsDouble() : null;
            
            List<String> confirmations = parseStringArray(json, "confirmations");
            List<String> concerns = parseStringArray(json, "concerns");
            List<String> blockers = parseStringArray(json, "blockers");
            List<String> recommendations = parseStringArray(json, "recommendations");
            
            String explanation = json.has("explanation") ? json.get("explanation").getAsString() : "";
            
            return AiPositionManagementResponse.builder()
                    .action(action)
                    .confidence(Math.min(1.0, Math.max(0.0, confidence)))
                    .suggestedStopLoss(suggestedStopLoss)
                    .suggestedTakeProfit(suggestedTakeProfit)
                    .suggestedCloseQuantity(suggestedCloseQuantity)
                    .suggestedRiskReductionPercent(suggestedRiskReduction)
                    .confirmations(confirmations)
                    .concerns(concerns)
                    .blockers(blockers)
                    .recommendations(recommendations)
                    .explanation(explanation)
                    .hadErrors(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            
        } catch (Exception e) {
            logger.warning("Failed to parse AI response: %s".formatted(e.getMessage()));
            // Fall back to safe response
            return AiPositionManagementResponse.failedResponse(
                    "Invalid AI response format: %s".formatted(e.getMessage()), 
                    getModelName());
        }
    }
    
    /**
     * Extract JSON from text that may contain surrounding content.
     */
    private String extractJsonFromText(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        
        return text; // Return as-is if no JSON brackets found
    }
    
    /**
     * Parse AiPositionAction from string.
     */
    private AiPositionAction parseAction(String actionStr) {
        try {
            return AiPositionAction.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown action: %s".formatted(actionStr));
            return AiPositionAction.ESCALATE_TO_MANUAL_REVIEW;
        }
    }
    
    /**
     * Parse string array from JSON.
     */
    private List<String> parseStringArray(JsonObject json, String fieldName) {
        List<String> result = new ArrayList<>();
        
        if (json.has(fieldName) && json.get(fieldName).isJsonArray()) {
            var array = json.getAsJsonArray(fieldName);
            for (var element : array) {
                if (element.isJsonPrimitive()) {
                    result.add(element.getAsString());
                }
            }
        }
        
        return result;
    }
    
    @Override
    public String getModelName() {
        return model;
    }
    
    @Override
    public boolean isAvailable() {
        return lastCallSuccessful;
    }
    
    @Override
    public long getAverageProcessingTimeMs() {
        if (callCount == 0) return 0;
        return totalProcessingTimeMs / callCount;
    }
}
