package org.investpro.news;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NewsIntelligenceServicesTest {

    @Test
    void normalizesTagsClassifiesScoresAndDeduplicates() {
        CryptoNewsItem item = item("1", "Coinbase lists SOL perpetual futures after ETF approval", "SOL and BTC markets rally");
        NewsClassifier classifier = new NewsClassifier();
        NewsSymbolTagger.TagResult tags = new NewsSymbolTagger().tag(item);
        NewsCategory category = classifier.classify(item);
        double score = new NewsSentimentService().score(item, category);
        NewsImpact impact = new NewsSentimentService().impact(score);
        NewsUrgency urgency = new NewsUrgencyService().urgency(category, impact);

        assertThat(tags.symbols()).contains("SOL", "BTC");
        assertThat(category).isIn(NewsCategory.ETF, NewsCategory.PERPETUALS, NewsCategory.LISTING);
        assertThat(score).isPositive();
        assertThat(impact).isIn(NewsImpact.POSITIVE, NewsImpact.VERY_POSITIVE);
        assertThat(urgency).isIn(NewsUrgency.HIGH, NewsUrgency.MEDIUM);

        List<CryptoNewsItem> deduped = new NewsDeduplicationService().markDuplicates(List.of(item, item("2", item.title(), item.summary())));
        assertThat(deduped.get(1).duplicate()).isTrue();
        assertThat(deduped.get(1).duplicateOfId()).isEqualTo("1");
    }

    @Test
    void alertAndContextDetectCriticalNegativeNews() {
        CryptoNewsItem hacked = item("hack", "BTC exchange hacked in major exploit", "Bitcoin withdrawals suspended")
                .withAnalysis(NewsCategory.HACK, NewsImpact.VERY_NEGATIVE, NewsUrgency.CRITICAL,
                        Set.of("BTC"), Set.of("Bitcoin"), -0.9, 1.0);
        InMemoryNewsRepository repository = new InMemoryNewsRepository();
        repository.saveAll(List.of(hacked));

        assertThat(new NewsAlertService().shouldBlockTrading(hacked)).isTrue();
        NewsContext context = new NewsContextService(repository).getContextForSymbol("BTC", Duration.ofHours(24));
        assertThat(context.criticalNegativeNews()).isTrue();
        assertThat(context.warnings()).isNotEmpty();
    }

    private CryptoNewsItem item(String id, String title, String summary) {
        return new CryptoNewsItem(
                id,
                "test",
                "Test",
                title,
                summary,
                "https://example.com/" + id,
                LocalDateTime.now(),
                LocalDateTime.now(),
                NewsSourceType.RSS,
                NewsCategory.UNKNOWN,
                NewsImpact.UNKNOWN,
                NewsUrgency.LOW,
                Set.of(),
                Set.of(),
                0.0,
                0.0,
                false,
                null,
                Map.of());
    }
}
