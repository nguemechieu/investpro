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
        // Set up the attributes for the Instances (Open, High, Low, Close, Volume)
        ArrayList<Attribute> attributes = new ArrayList<>();
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

        // Create an empty dataset with these attributes
        Instances trainingData = new Instances("MarketData", attributes, 0);
        trainingData.setClassIndex(Math.max((trainingData.numAttributes() - 1), 0));

        // Add some random data points to the training set
        Random random = new Random();
        for (int i = 0; i < 100; i++) {
            DenseInstance instance = new DenseInstance(attributes.size());
            instance.setValue(attributes.get(0), random.nextDouble() * 100 + 100); // open
            instance.setValue(attributes.get(1), random.nextDouble() * 100 + 150); // high
            instance.setValue(attributes.get(2), random.nextDouble() * 100 + 50);  // low
            instance.setValue(attributes.get(3), random.nextDouble() * 100 + 100); // close
            instance.setValue(attributes.get(4), random.nextDouble() * 1000);      // volume
            instance.setValue(attributes.get(5), random.nextInt(3));               // class: BUY, SELL, HOLD

            trainingData.add(instance);
        }

        // Initialize TradingAI with the training data
        tradingAI = new TradingAI(trainingData);
    }

    @Test
    public void testGetSignal() {
        Random random = new Random();

        // Generate random candle data for the test
        double open = random.nextDouble() * 100 + 100;
        double high = open + random.nextDouble() * 10;
        double low = open - random.nextDouble() * 10;
        double close = random.nextDouble() * (high - low) + low;
        double volume = random.nextDouble() * 1000;

        // Call the getSignal method with the random candle data
        SIGNAL signal = tradingAI.getSignal(open, high, low, close, volume);

        // Ensure that the result is not null and that it's a valid signal
        assertNotNull(signal);
        assertTrue(signal == SIGNAL.BUY || signal == SIGNAL.SELL || signal == SIGNAL.HOLD);

        // Print the result
        logger.info(STR."Candle Data Signal: \{signal}");
    }

    @Test
    public void testGetMovingAverageSignal() throws InterruptedException {
        Random random = new Random();
        List<Double> prices = new ArrayList<>();
        while (true) {
            // Generate random price data for the moving average test
            for (int i = 0; i < 100; i++) {
                prices.add(100.0 + random.nextDouble() * 100.0);  // Random prices between 100 and 200
            }

            double currentPrice = prices.getLast();  // The Current price is the last in the list

            // Call the getMovingAverageSignal method with the random price data
            SIGNAL signal = tradingAI.getMovingAverageSignal(prices, currentPrice);

            // Ensure that the result is not null and that it's a valid signal
            assertNotNull(signal);
            assertTrue(signal == SIGNAL.BUY || signal == SIGNAL.SELL || signal == SIGNAL.HOLD);

            // Print the result
            logger.info(STR."Moving Average Signal: \{signal}");

            Thread.sleep(1000);
        }

    }
}
