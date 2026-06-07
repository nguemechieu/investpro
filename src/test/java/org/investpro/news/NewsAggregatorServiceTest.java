package org.investpro.news;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NewsAggregatorServiceTest {

    @Test
    void failedNewsSourceDoesNotCrashAggregator() {
        NewsSourceDefinition source = new NewsSourceDefinition(
                "bad",
                "Bad Feed",
                "https://bad.example",
                NewsSourceType.RSS,
                true,
                15,
                Set.of(),
                Map.of());
        NewsProvider failingProvider = ignored -> new NewsFetchResult(
                source,
                List.of(),
                List.of(),
                List.of("network failed"),
                LocalDateTime.now());
        NewsAggregatorService service = new NewsAggregatorService(
                List.of(source),
                Map.of(NewsSourceType.RSS, failingProvider),
                new InMemoryNewsRepository(),
                new NewsClassifier(),
                new NewsSymbolTagger(),
                new NewsSentimentService(),
                new NewsUrgencyService(),
                new NewsDeduplicationService(),
                new NewsAlertService());

        NewsAggregatorService.NewsRefreshSummary summary = service.refresh();

        assertThat(summary.sourcesFailed()).isEqualTo(1);
        assertThat(summary.newItems()).isZero();
        assertThat(summary.warnings()).isNotEmpty();
        service.shutdown();
    }
}
