package org.investpro.ai;//import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static org.investpro.Exchange.logger;

public class InvestProAIPredictorClient {

    private static final String API_URL = "http://localhost:8000/predict";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PredictionResult predict(List<Double> features) {
        try {
            URI url = URI.create(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            Map<String, Object> payload = new HashMap<>();
            payload.put("features", features);

            String jsonPayload = objectMapper.writeValueAsString(payload);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status == 200) {
                try (Scanner scanner = new Scanner(conn.getInputStream())) {
                    String responseBody = scanner.useDelimiter("\\A").next();
                    Map responseMap = objectMapper.readValue(responseBody, Map.class);
                    String prediction = (String) responseMap.get("prediction");
                    double confidence = Double.parseDouble(responseMap.get("confidence").toString());

                    return new PredictionResult(prediction, confidence);
                }
            } else {
                logger.info("Error: HTTP " + status);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new PredictionResult("unknown", 0.0);
    }

    public record PredictionResult(String prediction, double confidence) {
    }
}
