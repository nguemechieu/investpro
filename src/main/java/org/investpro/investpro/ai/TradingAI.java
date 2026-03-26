package org.investpro.investpro.ai;

import org.investpro.investpro.SIGNAL;
import org.investpro.investpro.model.CandleData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class TradingAI {

    private static final Logger logger = LoggerFactory.getLogger(TradingAI.class);
    private final Classifier model;
    private final Instances trainingData;
    private final List<Attribute> attributes;
    double currentPrice;

    public TradingAI(@NotNull Instances trainingData) {
        this.trainingData = trainingData;
        this.attributes = createAttributes();
        this.model = new J48();

        try {
            logger.info("Training AI with provided data...");
            model.buildClassifier(trainingData);
            saveTrainingData(trainingData);
        } catch (Exception e) {
            logger.error("Error building classifier: {}", e.getMessage(), e);
        }
    }

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
            logger.error("Prediction error: {}", e.getMessage(), e);
        }
        return SIGNAL.HOLD;
    }

    public SIGNAL getMovingAverageSignal(List<CandleData> prices, double currentPrice) {
        if (prices.isEmpty()) {
            return SIGNAL.HOLD;
        }

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

    private double calculateMovingAverage(List<CandleData> prices, int period) {
        return prices.subList(Math.max(0, prices.size() - period), prices.size())
                .stream()
                .mapToDouble(CandleData::getClosePrice)
                .average()
                .orElse(0.0);
    }

    private double calculateSupport(@NotNull List<CandleData> prices) {
        return prices.subList(Math.max(0, prices.size() - 20), prices.size())
                .stream()
                .mapToDouble(CandleData::getLowPrice)
                .min()
                .orElse(Double.MAX_VALUE);
    }

    private double calculateResistance(List<CandleData> prices) {
        return prices.subList(Math.max(0, prices.size() - 20), prices.size())
                .stream()
                .mapToDouble(CandleData::getHighPrice)
                .max()
                .orElse(Double.MIN_VALUE);
    }

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

    private @NotNull List<Attribute> createAttributes() {
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("open"));
        attributes.add(new Attribute("high"));
        attributes.add(new Attribute("low"));
        attributes.add(new Attribute("close"));
        attributes.add(new Attribute("volume"));

        ArrayList<String> classValues = new ArrayList<>();
        classValues.add("HOLD");
        classValues.add("BUY");
        classValues.add("SELL");
        attributes.add(new Attribute("class", classValues));

        return attributes;
    }

    private void saveTrainingData(@NotNull Instances data) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("trainingData.arff"))) {
            writer.write(data.toString());
            logger.info("Training data saved successfully.");
        } catch (IOException e) {
            logger.error("Error saving training data: {}", e.getMessage(), e);
        }
    }

    public void trainModel() {
        try {
            logger.info("Training AI model...");

            if (trainingData.numInstances() == 0) {
                logger.warn("No training data available. Cannot train model.");
                return;
            }

            saveTrainingData(trainingData);
            logger.info("Model training completed successfully.");
        } catch (Exception e) {
            logger.error("Error training model: {}", e.getMessage(), e);
        }
    }

    public SIGNAL getSignal(double currentPrice, List<CandleData> prices1, TradeStrategy tradeStrategy) {
        this.currentPrice = currentPrice;
        double open = prices1.getLast().getOpenPrice();
        double high = prices1.stream().mapToDouble(CandleData::getHighPrice).max().orElse(0.0);
        double low = prices1.stream().mapToDouble(CandleData::getLowPrice).min().orElse(0.0);
        double close = prices1.getLast().getClosePrice();
        double volume = prices1.getLast().getVolume();

        return tradeStrategy.getSignal(open, high, low, close, volume, prices1);
    }
}
