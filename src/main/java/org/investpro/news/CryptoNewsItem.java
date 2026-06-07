package org.investpro.news;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

public record CryptoNewsItem(
        String id,
        String sourceId,
        String sourceName,
        String title,
        String summary,
        String url,
        LocalDateTime publishedAt,
        LocalDateTime fetchedAt,
        NewsSourceType sourceType,
        NewsCategory category,
        NewsImpact impact,
        NewsUrgency urgency,
        Set<String> mentionedSymbols,
        Set<String> mentionedAssets,
        double sentimentScore,
        double relevanceScore,
        boolean duplicate,
        String duplicateOfId,
        Map<String, String> metadata) {

    public CryptoNewsItem {
        mentionedSymbols = mentionedSymbols == null ? Set.of() : Set.copyOf(mentionedSymbols);
        mentionedAssets = mentionedAssets == null ? Set.of() : Set.copyOf(mentionedAssets);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        category = category == null ? NewsCategory.UNKNOWN : category;
        impact = impact == null ? NewsImpact.UNKNOWN : impact;
        urgency = urgency == null ? NewsUrgency.LOW : urgency;
    }

    public CryptoNewsItem withAnalysis(
            NewsCategory category,
            NewsImpact impact,
            NewsUrgency urgency,
            Set<String> mentionedSymbols,
            Set<String> mentionedAssets,
            double sentimentScore,
            double relevanceScore) {
        return new CryptoNewsItem(
                id,
                sourceId,
                sourceName,
                title,
                summary,
                url,
                publishedAt,
                fetchedAt,
                sourceType,
                category,
                impact,
                urgency,
                mentionedSymbols,
                mentionedAssets,
                sentimentScore,
                relevanceScore,
                duplicate,
                duplicateOfId,
                metadata);
    }

    public CryptoNewsItem asDuplicateOf(String originalId) {
        return new CryptoNewsItem(
                id,
                sourceId,
                sourceName,
                title,
                summary,
                url,
                publishedAt,
                fetchedAt,
                sourceType,
                category,
                impact,
                urgency,
                mentionedSymbols,
                mentionedAssets,
                sentimentScore,
                relevanceScore,
                true,
                originalId,
                metadata);
    }
}
