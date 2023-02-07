package org.investpro.investpro;

import java.util.Collection;

public class OrderBook {
    private Collection<?> buckets;

    public Collection<?> getBuckets() {
        return buckets;
    }

    public OrderBook setBuckets(Collection<?> jsonArray) {
        this.buckets = jsonArray;
        return this;
    }
}
