package org.investpro.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.investpro.Exchange.logger;
import static org.investpro.exchanges.Coinbase.client;

public class InvestProAIPredictor {

    private static final String API_URL = "http://localhost:8000/predict"; // your ML server endpoint
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PredictionResult predict(List<Double> features) throws IOException, InterruptedException {
   try{
        HttpRequest.Builder re=HttpRequest.newBuilder();
        HttpResponse<String> post = client.send(re.uri(URI.create(API_URL)).build(), HttpResponse.BodyHandlers.ofString());


            Map<String, Object> payload = new HashMap<>();
            payload.put("features", features);

            String json = objectMapper.writeValueAsString(payload);

       try {
           return objectMapper.readValue(json, PredictionResult.class);
       } catch (JsonProcessingException e) {
           throw new RuntimeException(e);
       }

   } catch (IOException e) {
            logger.error(e.getMessage());
            return new PredictionResult("unknown", 0.0);

   } }

    public record PredictionResult(String prediction, double confidence) {
    }
}
