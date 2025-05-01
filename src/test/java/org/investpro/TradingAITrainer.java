package org.investpro;

import org.investpro.investpro.ai.TradingAI;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.util.ArrayList;

public class TradingAITrainer {
    public static void main(String[] args) {
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("open"));
        attributes.add(new Attribute("high"));
        attributes.add(new Attribute("low"));
        attributes.add(new Attribute("close"));
        attributes.add(new Attribute("volume"));

        // Class Attribute (BUY, SELL, HOLD)
        ArrayList<String> classValues = new ArrayList<>();
        classValues.add("HOLD");
        classValues.add("BUY");
        classValues.add("SELL");
        attributes.add(new Attribute("class", classValues));

        // Create an empty dataset
        Instances trainingData = new Instances("MarketData", attributes, 0);
        trainingData.setClassIndex(attributes.size() - 1);

        // Generate some sample data
        for (int i = 0; i < 100; i++) {
            DenseInstance instance = new DenseInstance(attributes.size());
            instance.setDataset(trainingData);
            instance.setValue(attributes.get(0), Math.random() * 100 + 100); // Open
            instance.setValue(attributes.get(1), Math.random() * 100 + 150); // High
            instance.setValue(attributes.get(2), Math.random() * 100 + 50);  // Low
            instance.setValue(attributes.get(3), Math.random() * 100 + 100); // Close
            instance.setValue(attributes.get(4), Math.random() * 1000);      // Volume
            instance.setValue(attributes.get(5), (int) (Math.random() * 3)); // Class (BUY, SELL, HOLD)

            trainingData.add(instance);
        }
        // Train the AI model
        TradingAI tradingAI = new TradingAI(trainingData);
        tradingAI.trainModel();

    }
}
