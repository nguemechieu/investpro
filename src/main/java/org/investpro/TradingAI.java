package org.investpro;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;
import weka.classifiers.Classifier;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

public class TradingAI {

    private static final Logger logger = Logger.getLogger(TradingAI.class.getName());

    private final Classifier model;
    private final Instances trainingData;
    private final List<Attribute> attributes;

    public TradingAI(Instances trainingData) {
        this.trainingData = trainingData;
        this.attributes = createAttributes();
        this.model = new J48();

        try {
            this.model.buildClassifier(trainingData);  // Train the model
            File f = new File("trainingData.pkl");
            if (!f.exists()) f.createNewFile();

        } catch (Exception e) {
            logger.severe("Error building classifier: " + e.getMessage());
        }
    }

    // Existing method for getting signal based on candle data
    public SIGNAL getSignal(double open, double high, double low, double close, double volume) {
        try {
            Instance instance = createInstance(open, high, low, close, volume);
            double prediction = model.classifyInstance(instance);

            if (prediction == 0.0)
                return SIGNAL.BUY;
            else if (prediction == 1.0)
                return SIGNAL.SELL;
            else
                return SIGNAL.HOLD;

        } catch (Exception e) {
            logger.severe("Error creating instance or predicting signal: " + e.getMessage());
            return SIGNAL.HOLD;
        }
    }

    // New method for generating a signal based on moving averages and support/resistance
    public SIGNAL getMovingAverageSignal(List<Double> prices, double currentPrice) {
        // Calculate moving averages (e.g., 20-period and 50-period)
        double shortTermMA = calculateMovingAverage(prices, 20);
        double longTermMA = calculateMovingAverage(prices, 50);

        // Calculate support and resistance (recent lows and highs)
        double support = calculateSupport(prices);
        double resistance = calculateResistance(prices);

        // Generate signal based on moving averages
        if (shortTermMA > longTermMA && currentPrice > support) {
            return SIGNAL.BUY;
        } else if (shortTermMA < longTermMA && currentPrice < resistance) {
            return SIGNAL.SELL;
        } else {
            return SIGNAL.HOLD;
        }
    }

    // Helper method to calculate moving average over a specified period
    private double calculateMovingAverage(@NotNull List<Double> prices, int period) {
        if (prices.size() < period) return 0.0;  // Not enough data

        double sum = 0.0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum += prices.get(i);
        }
        return sum / period;
    }

    // Helper method to calculate support level (lowest price in recent period)
    private double calculateSupport(@NotNull List<Double> prices) {
        double support = Double.MAX_VALUE;
        for (int i = prices.size() - 20; i < prices.size(); i++) {
            if (prices.get(i) < support) {
                support = prices.get(i);
            }
        }
        return support;
    }

    // Helper method to calculate resistance level (the highest price in a recent period)
    private double calculateResistance(@NotNull List<Double> prices) {
        double resistance = Double.MIN_VALUE;
        for (int i = prices.size() - 20; i < prices.size(); i++) {
            if (prices.get(i) > resistance) {
                resistance = prices.get(i);
            }
        }
        return resistance;
    }

    // Create an instance based on candle data
    private @NotNull Instance createInstance(double open, double high, double low, double close, double volume) {
        Instance instance = new DenseInstance(attributes.size());
        instance.setDataset(trainingData);
        instance.setValue(attributes.get(0), open);
        instance.setValue(attributes.get(1), high);
        instance.setValue(attributes.get(2), low);
        instance.setValue(attributes.get(3), close);
        instance.setValue(attributes.get(4), volume);
        instance.setMissing(attributes.get(5));
        return instance;
    }

    // Define the candle data attributes (Open, High, Low, Close, Volume) and class labels
    private @NotNull List<Attribute> createAttributes() {
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("open"));
        attributes.add(new Attribute("high"));
        attributes.add(new Attribute("low"));
        attributes.add(new Attribute("close"));
        attributes.add(new Attribute("volume"));

        ArrayList<String> classValues = new ArrayList<>();
        classValues.add("BUY");
        classValues.add("SELL");
        classValues.add("HOLD");
        attributes.add(new Attribute("class", classValues));

        return attributes;
    }


    public void train() {
        try {
            model.buildClassifier(trainingData);  // Train the model
            File f = new File("trainingData.pkl");
            if (!f.exists()) f.createNewFile();
            logger.info("Training completed.");

        } catch (Exception e) {
            logger.severe("Error building classifier: " + e.getMessage());
        }
    }
}
