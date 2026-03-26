package org.investpro.investpro;

import org.investpro.grpc.Predict;
import org.investpro.investpro.ai.InvestProAIPredictor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TestAIClient {

    public static void main(String[] args) {
        String host = System.getProperty("investpro.ai.host", "localhost");
        int port = Integer.getInteger("investpro.ai.port", 50051);
        InvestProAIPredictor client = new InvestProAIPredictor(host, port);

        Predict.MarketDataRequest request = Predict.MarketDataRequest.newBuilder()
                .setOpen(29500.0)
                .setClose(29350.0)
                .setHigh(29600.0)
                .setLow(29200.0)
                .setVolume(1200.0)
                .setRsi(55.0)
                .setAtr(150.0)
                .setMacd(0.5)
                .setStoch(55.0)
                .setBbUpper(29450.0)
                .setBbLower(29400.0)
                .build();

        List<Predict.MarketDataRequest> featureList = new ArrayList<>();
        featureList.add(request);

        if (!client.checkHealth()) {
            System.out.printf("AI predictor is unavailable at %s:%d.%n", host, port);
            client.shutdown();
            return;
        }

        CompletableFuture<List<Predict.PredictionResponse>> predictionResult = client.streamBatchPredict(featureList);

        try {
            List<Predict.PredictionResponse> results = predictionResult.get(5, TimeUnit.SECONDS);
            if (!results.isEmpty()) {
                Predict.PredictionResponse last = results.getLast();
                System.out.println("Prediction: " + last.getPrediction());
                System.out.println("Confidence: " + last.getConfidence());
            } else {
                System.out.println("No predictions returned.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        } catch (ExecutionException | TimeoutException e) {
            e.printStackTrace();
        } finally {
            client.shutdown();
        }
    }
}
