package org.investpro.investpro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InvestProAIPredictor {

    private static final Logger logger = LoggerFactory.getLogger(InvestProAIPredictor.class);
    private static final String API_URL = "http://localhost:8000/predict";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public PredictionResult predict(List<Double> features) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("features", features);

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return objectMapper.readValue(response.body(), PredictionResult.class);

        } catch (IOException | InterruptedException e) {
            logger.error("Prediction error: {}", e.getMessage(), e);
            return new PredictionResult("unknown", 0.0);
        }
    }

    public record PredictionResult(String prediction, double confidence) {
    }
}
