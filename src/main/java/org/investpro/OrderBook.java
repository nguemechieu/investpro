package org.investpro;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderBook {
    private static final Logger logger = LoggerFactory.getLogger(OrderBook.class);
    private Collection<?> buckets;

    public OrderBook(@NotNull JSONArray names) {
        this.buckets =
                names.toList();
        logger.info("OrderBook created");
    }

    public Collection<?> getBuckets() {
        return buckets;
    }

    public OrderBook setBuckets(Collection<?> jsonArray) {
        this.buckets = jsonArray;
        return this;
    }

    public void setPrice(double price) {
        for (Object bucket : buckets) {
            if (bucket instanceof JSONArray) {
                ((JSONArray) bucket).put(price);
            }
        }
    }

    public void setAmount(double amount) {
        for (Object bucket : buckets) {
            if (bucket instanceof JSONArray) {
                ((JSONArray) bucket).put(amount);
            }
        }
    }
}
