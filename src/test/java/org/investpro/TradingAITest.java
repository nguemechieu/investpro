package org.investpro;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TradingAITest {

    private static final Logger logger = LoggerFactory.getLogger(TradingAITest.class);

    private TradingAI tradingAI;

    @Before
    public void setUp() throws Exception {
        // Set up attributes for the dataset: Open, High, Low, Close, Volume, and Class
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("open"));
        attributes.add(new Attribute("high"));
        attributes.add(new Attribute("low"));
        attributes.add(new Attribute("close"));
        attributes.add(new Attribute("volume"));

        // Define class attribute with categorical values
        ArrayList<String> classValues = new ArrayList<>();
        classValues.add("BUY");
        classValues.add("SELL");
        classValues.add("HOLD");
        attributes.add(new Attribute("class", classValues));

        // Create an empty dataset with these attributes
        Instances trainingData = new Instances("MarketData", attributes, 0);
        trainingData.setClassIndex(attributes.size() - 1); // Ensure class attribute is properly set

        // Generate random training data
        Random random = new Random();
        for (int i = 0; i < 100; i++) {
            DenseInstance instance = new DenseInstance(attributes.size());
            instance.setDataset(trainingData);
            instance.setValue(attributes.get(0), random.nextDouble() * 100 + 100); // open
            instance.setValue(attributes.get(1), random.nextDouble() * 100 + 150); // high
            instance.setValue(attributes.get(2), random.nextDouble() * 100 + 50);  // low
            instance.setValue(attributes.get(3), random.nextDouble() * 100 + 100); // close
            instance.setValue(attributes.get(4), random.nextDouble() * 1000);      // volume

            // Assign a random class label (BUY = 0, SELL = 1, HOLD = 2)
            instance.setValue(attributes.get(5), random.nextInt(3));

            trainingData.add(instance);
        }

        // Initialize TradingAI with the training dataset
        tradingAI = new TradingAI(trainingData);
    }

    @Test
    public void testGetSignal() {
        Random random = new Random();

        // Generate random candle data for testing
        double open = random.nextDouble() * 100 + 100;
        double high = open + random.nextDouble() * 10;
        double low = open - random.nextDouble() * 10;
        double close = random.nextDouble() * (high - low) + low;
        double volume = Math.abs(random.nextDouble() * 1000); // Ensure non-negative volume

        // Call the getSignal method
        SIGNAL signal = tradingAI.getSignal(open, high, low, close, volume);

        // Validate the output
        assertNotNull(signal);
        assertTrue(signal == SIGNAL.BUY || signal == SIGNAL.SELL || signal == SIGNAL.HOLD);

        // Log the result
        logger.info("Candle Data Signal: {}", signal);
    }

    @Test
    public void testGetMovingAverageSignal() {
        Random random = new Random();
        List<Double> prices = new ArrayList<>();

        // Generate random price data for moving average test
        for (int i = 0; i < 100; i++) {
            prices.add(100.0 + random.nextDouble() * 100.0);  // Random prices between 100 and 200
        }


        double currentPrice = prices.getLast();  // Last price is the current price


        // Call the getMovingAverageSignal method
        SIGNAL signalValue = tradingAI.getMovingAverageSignal(prices, currentPrice);

        // Convert to SIGNAL enum
        SIGNAL signal = SIGNAL.values()[signalValue.ordinal()];

        // Validate the output
        assertNotNull(signal);
        assertTrue(signal == SIGNAL.BUY || signal == SIGNAL.SELL || signal == SIGNAL.HOLD);

        // Log the result
        logger.info("Moving Average Signal: {}", signal);

        // Retrain the model after prediction (optional)
        tradingAI.retrainModel();
    }
}
