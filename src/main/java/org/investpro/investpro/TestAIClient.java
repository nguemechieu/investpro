package org.investpro.investpro;

import org.investpro.investpro.ai.InvestProAIPredictorClient;

import java.util.Arrays;
import java.util.List;

public class TestAIClient {

    public static void main(String[] args) {
        InvestProAIPredictorClient client = new InvestProAIPredictorClient();

        // Example features (you should send real candle features!)
        List<Double> features = Arrays.asList(29500.0, 29350.0, 29600.0, 29200.0, 1200.0, 150.0, 400.0, 100.0, 29400.0, 29450.0, 55.0, 0.5);

        var predictionResult = client.predict(features);

        System.out.println("Prediction: " + predictionResult.prediction());
        System.out.println("Confidence: " + predictionResult.confidence());
    }
}
