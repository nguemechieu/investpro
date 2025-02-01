package org.investpro;

import org.jetbrains.annotations.NotNull;
import weka.classifiers.Classifier;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class TradingAI {

    private static final Logger logger = Logger.getLogger(TradingAI.class.getName());
    private final Classifier model;
    private final Instances trainingData;
    private final List<Attribute> attributes;

    public TradingAI(Instances trainingData) {
        this.trainingData = trainingData;
        this.attributes = createAttributes();
        this.model = new J48();  // Decision Tree Classifier

        try {
            logger.info("Training data: " + trainingData);

            model.buildClassifier(trainingData); // Train the model

            saveTrainingData(trainingData); // Save training data

        } catch (Exception e) {
            logger.severe("Error building classifier: " + e.getMessage());
        }
    }

    /**
     * Generate a trading signal using the trained classifier.
     */
    public SIGNAL getSignal(double open, double high, double low, double close, double volume) {
        try {
            Instance instance = createInstance(open, high, low, close, volume);
            double prediction = model.classifyInstance(instance);

            if (prediction == 0.0)
                return SIGNAL.HOLD;
            else if (prediction == 1.0)
                return SIGNAL.BUY;
            else if (prediction == 2.0)  // Fix: Weka does not support negative labels (-1.0)
                return SIGNAL.SELL;

        } catch (Exception e) {
            logger.severe("Error creating instance or predicting signal: " + e.getMessage());
        }
        return SIGNAL.HOLD;
    }

    /**
     * Generate a trading signal based on moving averages.
     */
    public SIGNAL getMovingAverageSignal(List<Double> prices, double currentPrice) {
        double shortTermMA = calculateMovingAverage(prices, 20);
        double longTermMA = calculateMovingAverage(prices, 50);

        double support = calculateSupport(prices);
        double resistance = calculateResistance(prices);

        if (shortTermMA > longTermMA && currentPrice > support) {
            return SIGNAL.BUY;
        } else if (shortTermMA < longTermMA && currentPrice < resistance) {
            return SIGNAL.SELL;
        } else {
            return SIGNAL.HOLD;
        }
    }

    /**
     * Calculate Moving Average.
     */
    private double calculateMovingAverage(@NotNull List<Double> prices, int period) {
        if (prices.size() < period) return 0.0;

        double sum = 0.0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum += prices.get(i);
        }
        return sum / period;
    }

    /**
     * Calculate Support Level (Lowest price in recent period).
     */
    private double calculateSupport(@NotNull List<Double> prices) {
        double support = Double.MAX_VALUE;
        int size = prices.size();
        for (int i = Math.max(0, size - 20); i < size; i++) {
            support = Math.min(support, prices.get(i));
        }
        return support;
    }

    /**
     * Calculate Resistance Level (Highest price in recent period).
     */
    private double calculateResistance(@NotNull List<Double> prices) {
        double resistance = Double.MIN_VALUE;
        int size = prices.size();
        for (int i = Math.max(0, size - 20); i < size; i++) {
            resistance = Math.max(resistance, prices.get(i));
        }
        return resistance;
    }

    /**
     * Create an instance for prediction.
     */
    private @NotNull Instance createInstance(double open, double high, double low, double close, double volume) {
        Instance instance = new DenseInstance(attributes.size());
        instance.setDataset(trainingData);
        instance.setValue(attributes.get(0), open);
        instance.setValue(attributes.get(1), high);
        instance.setValue(attributes.get(2), low);
        instance.setValue(attributes.get(3), close);
        instance.setValue(attributes.get(4), volume);
        instance.setMissing(attributes.get(5)); // Ensure class is missing for prediction
        return instance;
    }

    /**
     * Define trading attributes (OHLCV + class labels).
     */
    private @NotNull List<Attribute> createAttributes() {
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("open"));
        attributes.add(new Attribute("high"));
        attributes.add(new Attribute("low"));
        attributes.add(new Attribute("close"));
        attributes.add(new Attribute("volume"));

        // Class Attribute (BUY, SELL, HOLD)
        ArrayList<String> classValues = new ArrayList<>();
        classValues.add("HOLD");  // 0.0
        classValues.add("BUY");   // 1.0
        classValues.add("SELL");  // 2.0
        attributes.add(new Attribute("class", classValues));

        return attributes;
    }

    /**
     * Train and Save Model.
     */
    public void train() {
        try {
            model.buildClassifier(trainingData);
            saveTrainingData(trainingData);
            logger.info("Training completed.");
        } catch (Exception e) {
            logger.severe("Error building classifier: " + e.getMessage());
        }
    }

    /**
     * Save training data to a file.
     */
    private void saveTrainingData(Instances data) {
        try {
            File file = new File("trainingData.pkl");
            if (!file.exists()) file.createNewFile();
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                bw.write(data.toString());
            }
            logger.info("Training data saved.");
        } catch (IOException e) {
            logger.severe("Error saving training data: " + e.getMessage());
        }
    }
}
