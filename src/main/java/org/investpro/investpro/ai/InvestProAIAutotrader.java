package org.investpro.investpro.ai;

import org.investpro.investpro.chart.CandleStickChart;
import org.investpro.investpro.model.Candle;

import java.util.List;

/**
 * 🚀 InvestProAIAutotrader
 * Automatically sends latest candle data for AI prediction,
 * and executes simulated (or real) trades based on AI output.
 */
public class InvestProAIAutotrader {

    private final CandleStickChart chart;
    private final InvestProAIPredictor predictor;
    private final double confidenceThreshold; // minimum confidence required to act

    public InvestProAIAutotrader(CandleStickChart chart) {
        this.chart = chart;
        this.predictor = new InvestProAIPredictor();
        this.confidenceThreshold = 0.7; // Example: only act if AI is 70% confident
    }

    /**
     * 📈 Should be called every time a new live candle closes.
     * Fetches latest candles, sends features to AI, and decides to trade.
     */
    public void onNewCandle(Candle latestCandle) {
        try {
            List<Candle> candles = chart.getLoadedCandles(); // Full candle history
            if (candles == null || candles.isEmpty()) {
                System.err.println("❗ No candles loaded yet, skipping autotrade.");
                return;
            }

            // 1. Extract features from latest candles
            List<Double> features = InvestProFeatureExtractor.extractFeatures(candles);

            // 2. Send to AI predictor
            InvestProAIPredictor.PredictionResult result = predictor.predict(features);

            // 3. Check AI decision and confidence
            if (result.confidence() >= confidenceThreshold) {
                if ("up".equalsIgnoreCase(result.prediction())) {
                    placeBuyOrder(latestCandle);
                } else if ("down".equalsIgnoreCase(result.prediction())) {
                    placeSellOrder(latestCandle);
                }
            } else {
                System.out.println("ℹ️ Confidence (" + result.confidence() + ") too low. No trade.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❗ Error during AI autotrading: " + e.getMessage());
        }
    }

    private void placeBuyOrder(Candle candle) {
        System.out.println("✅ [AI TRADE] BUY triggered at " + candle.getClose());
        // TODO: Connect this to real exchange or paper trading system
    }

    private void placeSellOrder(Candle candle) {
        System.out.println("✅ [AI TRADE] SELL triggered at " + candle.getClose());
        // TODO: Connect this to real exchange or paper trading system
    }
}
