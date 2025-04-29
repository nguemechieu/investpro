package org.investpro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.classic.methods.HttpPost;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.impl.classic.HttpClients;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.investpro.Exchange.logger;

public class InvestProAIPredictor {

    private static final String API_URL = "http://localhost:8000/predict"; // your ML server endpoint
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PredictionResult predict(List<Double> features) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(API_URL);

            Map<String, Object> payload = new HashMap<>();
            payload.put("features", features);

            String json = objectMapper.writeValueAsString(payload);
            post.setEntity(new StringEntity(json));
            post.setHeader("Content-Type", "application/json");

            var response = client.execute(post);
            var responseBody = new String(response.getEntity().getContent().readAllBytes());

            return objectMapper.readValue(responseBody, PredictionResult.class);

        } catch (IOException e) {
            logger.error(e.getMessage());
            return new PredictionResult("unknown", 0.0);
        }
    }

    public record PredictionResult(String prediction, double confidence) {
    }
}
