package org.investpro.investpro.ai;

import org.investpro.investpro.model.CandleData;

import java.util.List;

import static org.investpro.investpro.Exchange.logger;

public class InvestProAIBacktester {

    private final InvestProAIPredictorClient aiClient = new InvestProAIPredictorClient();

    private int totalTrades = 0;
    private int wins = 0;
    private int losses = 0;
    private double cumulativeProfit = 0.0;

    public void runBacktest(List<CandleData> candles) {
        if (candles.size() < 21) {
            logger.info("Not enough candles to backtest.");
            return;
        }

        for (int i = 20; i < candles.size() - 1; i++) {
            List<Double> features = extractSimpleFeatures(candles.subList(i - 20, i));

            var prediction = aiClient.predict(features);

            CandleData entryCandle = candles.get(i);
            CandleData exitCandle = candles.get(i + 1); // assume we close next candle (for simplicity)

            simulateTrade(prediction, entryCandle, exitCandle);
        }

        printReport();
    }

    private void simulateTrade(InvestProAIPredictorClient.PredictionResult prediction, CandleData entry, CandleData exit) {
        if (prediction.prediction().equalsIgnoreCase("unknown")) return;

        totalTrades++;

        boolean correct = prediction.prediction().equalsIgnoreCase("up") && exit.getClosePrice() > entry.getClosePrice();
        if (prediction.prediction().equalsIgnoreCase("down") && exit.getClosePrice() < entry.getClosePrice()) {
            correct = true;
        }

        if (correct) {
            wins++;
            cumulativeProfit += Math.abs(exit.getClosePrice() - entry.getClosePrice());
        } else {
            losses++;
            cumulativeProfit -= Math.abs(exit.getClosePrice() - entry.getClosePrice());
        }
    }

    private void printReport() {
        System.out.println("\n--- AI Backtest Report ---");
        System.out.println("Total Trades: " + totalTrades);
        System.out.println("Wins: " + wins);
        System.out.println("Losses: " + losses);

        double winRate = (wins * 100.0) / totalTrades;
        logger.info("Win Rate: %.2f%%\n" + winRate);

        logger.info("Cumulative Profit: %.2f\n" + cumulativeProfit);
        logger.info("--------------------------\n");
    }

    private List<Double> extractSimpleFeatures(List<CandleData> candles) {
        return candles.stream()
                .map(CandleData::getClosePrice) // simple features (closes)
                .toList();
    }
}
