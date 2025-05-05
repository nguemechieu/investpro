package org.investpro.investpro;

import org.investpro.grpc.Predict;
import org.investpro.investpro.ai.InvestProAIPredictor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class TestAIClient {

    public static void main(String[] args) {
        InvestProAIPredictor client = new InvestProAIPredictor("localhost", 50051);

        // Create a sample MarketDataRequest with real values
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

        CompletableFuture<List<Predict.PredictionResponse>> predictionResult = client.streamBatchPredict(featureList);

        try {
            List<Predict.PredictionResponse> results = predictionResult.get();
            if (!results.isEmpty()) {
                Predict.PredictionResponse last = results.getLast();
                System.out.println("✅ Prediction: " + last.getPrediction());
                System.out.println("🎯 Confidence: " + last.getConfidence());
            } else {
                System.out.println("⚠️ No predictions returned.");
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            client.shutdown(); // Clean up gRPC channel
        }
    }
}
