package org.investpro.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Summarizes news events into a directional bias (buy/sell/neutral).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsBias {
    private String direction;
    private double score;
    private double confidence;
    private String reason;
    private String headline = "";
    private int eventCount = 0;
    private int positiveCount = 0;
    private int negativeCount = 0;
    private int neutralCount = 0;
    private double averageImpact = 0.0;
    private String latestTimestamp = "";
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("direction", direction);
        map.put("score", score);
        map.put("confidence", confidence);
        map.put("reason", reason);
        map.put("headline", headline);
        map.put("event_count", eventCount);
        map.put("positive_count", positiveCount);
        map.put("negative_count", negativeCount);
        map.put("neutral_count", neutralCount);
        map.put("average_impact", averageImpact);
        map.put("latest_timestamp", latestTimestamp);
        map.put("metadata", metadata);
        return map;
    }
}
