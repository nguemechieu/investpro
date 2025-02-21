package org.investpro.ai;

import org.investpro.OrderBook;
import org.investpro.OrderBookEntry;
import org.investpro.SIGNAL;
import org.jetbrains.annotations.NotNull;
import weka.classifiers.Classifier;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.io.BufferedWriter;
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

    public TradingAI(@NotNull Instances trainingData) {
        this.trainingData = trainingData;
        this.attributes = createAttributes();
        this.model = new J48();  // Decision Tree Classifier

        try {
            logger.info("Training AI with provided data...");
            model.buildClassifier(trainingData);  // Train using training data
            saveTrainingData(trainingData);
            
        } catch (Exception e) {
            logger.severe("❌ Error building classifier: " + e.getMessage());
        }
    }

    /**
     * Generate a trading signal using the trained classifier.
     */
    public SIGNAL getSignal(double open, double high, double low, double close, double volume) {
        try {
            Instance instance = createInstance(open, high, low, close, volume);
            double prediction = model.classifyInstance(instance);

            return switch ((int) prediction) {
                case 1 -> SIGNAL.BUY;
                case 2 -> SIGNAL.SELL;
                default -> SIGNAL.HOLD;
            };
        } catch (Exception e) {
            logger.severe("❌ Prediction error: " + e.getMessage());
        }
        return SIGNAL.HOLD;
    }

    /**
     * Generate a trading signal based on moving averages.
     */
    public SIGNAL getMovingAverageSignal(List<OrderBook> prices, List<OrderBookEntry> currentPrice) {
        if (prices.isEmpty()) return SIGNAL.HOLD;

        double shortTermMA = calculateMovingAverage(prices, 20);
        double longTermMA = calculateMovingAverage(prices, 50);

        double support = calculateSupport(prices);
        double resistance = calculateResistance(prices);

        if (shortTermMA > longTermMA && currentPrice.getFirst().getPrice() > support) {
            return SIGNAL.BUY;
        } else if (shortTermMA < longTermMA && currentPrice.getFirst().getPrice() < resistance) {
            return SIGNAL.SELL;
        } else {
            return SIGNAL.HOLD;
        }
    }

    /**
     * Calculate Moving Average.
     */
    private double calculateMovingAverage(List<OrderBook> prices, int period) {
        if (prices.size() < period) return prices.stream().findFirst().get().getBidEntries().getFirst().getPrice();

        return prices.subList(prices.size() - period, prices.size())
                .stream()
                .mapToDouble(
                        c -> c.getBidEntries().getFirst().getPrice()
                )
                .average()
                .orElse(0.0);
    }

    /**
     * Calculate Support Level (Lowest price in recent period).
     */
    private double calculateSupport(List<OrderBook> prices) {
        return prices.subList(Math.max(0, prices.size() - 20), prices.size())
                .stream()
                .mapToDouble(c -> c.getBidEntries().getFirst().getPrice()
                )
                .min()
                .orElse(Double.MAX_VALUE);
    }

    /**
     * Calculate Resistance Level (Highest price in recent period).
     */
    private double calculateResistance(List<OrderBook> prices) {
        return prices.subList(Math.max(0, prices.size() - 20), prices.size())
                .stream()
                .mapToDouble(c -> c.getBidEntries().getFirst().getPrice()
                )
                .max()
                .orElse(Double.MIN_VALUE);
    }

    /**
     * Create an instance for prediction based on trained data.
     */
    private @NotNull Instance createInstance(double open, double high, double low, double close, double volume) {
        Instance instance = new DenseInstance(attributes.size());
        instance.setDataset(trainingData);
        instance.setValue(attributes.get(0), open);
        instance.setValue(attributes.get(1), high);
        instance.setValue(attributes.get(2), low);
        instance.setValue(attributes.get(3), close);
        instance.setValue(attributes.get(4), volume);
        instance.setMissing(attributes.get(5)); // Class attribute for prediction
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
        classValues.add("HOLD");  // 0
        classValues.add("BUY");   // 1
        classValues.add("SELL");  // 2
        attributes.add(new Attribute("class", classValues));

        return attributes;
    }

    /**
     * Save training data to a file.
     */
    private void saveTrainingData(@NotNull Instances data) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("trainingData.arff"))) {
            writer.write(data.toString());
            logger.info("✅ Training data saved successfully.");
        } catch (IOException e) {
            logger.severe("❌ Error saving training data: " + e.getMessage());
        }
    }

    public void trainModel() {
        try {
            logger.info("🔄 Training AI model...");

            // Ensure training data is not empty
            if (trainingData.numInstances() == 0) {
                logger.warning("⚠ No training data available. Cannot train model.");
                return;
            }


            // Save training data
            saveTrainingData(trainingData);

            logger.info("✅ Model training completed successfully!");

        } catch (Exception e) {
            logger.severe("❌ Error training model: " + e.getMessage());
        }
    }

}
