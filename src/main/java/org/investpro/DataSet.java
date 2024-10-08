package org.investpro;

import java.util.HashMap;
import java.util.Map;

import static org.investpro.CandleStickChart.logger;

public class DataSet {
    private final Map<String, Double> data; // To store investment data (e.g., stock symbol -> price)

    public DataSet() {
        data = new HashMap<>();
    }

    // Add data to the set
    public void addData(String key, Double value) {
        data.put(key, value);
    }

    // Get data by key
    public Double getData(String key) {
        return data.get(key);
    }

    // Remove data by key
    public void removeData(String key) {
        data.remove(key);
    }

    // Check if a key exists
    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    // Get the size of the data set
    public int getSize() {
        return data.size();
    }

    // Clear all data
    public void clearData() {
        data.clear();
    }

    // Print the dataset (for debugging purposes)
    public void printDataSet() {
        for (Map.Entry<String, Double> entry : data.entrySet()) {

            logger.info("Key: " + entry.getKey() + ", Value: " + entry.getValue());
        }
    }
}
