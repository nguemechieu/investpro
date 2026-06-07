package org.investpro.news;

import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class NewsAggregatorService {

    private final List<NewsSourceDefinition> sources;
    private final Map<NewsSourceType, NewsProvider> providers;
    private final NewsRepository repository;
    private final NewsClassifier classifier;
    private final NewsSymbolTagger symbolTagger;
    private final NewsSentimentService sentimentService;
    private final NewsUrgencyService urgencyService;
    private final NewsDeduplicationService deduplicationService;
    private final NewsAlertService alertService;
    private final ExecutorService executorService;
    private volatile LocalDateTime lastRefreshTime;
    private volatile int lastSuccessfulSources;
    private volatile int lastFailedSources;
    private volatile int lastNewItemCount;

    public NewsAggregatorService() {
        this(
                NewsSourceRegistry.enabledSources(),
                Map.of(
                        NewsSourceType.RSS, new RssNewsProvider(),
                        NewsSourceType.EXCHANGE_ANNOUNCEMENT, new RssNewsProvider(),
                        NewsSourceType.REGULATORY, new RssNewsProvider(),
                        NewsSourceType.PROJECT_BLOG, new RssNewsProvider()),
                new InMemoryNewsRepository(),
                new NewsClassifier(),
                new NewsSymbolTagger(),
                new NewsSentimentService(),
                new NewsUrgencyService(),
                new NewsDeduplicationService(),
                new NewsAlertService());
    }

    public NewsAggregatorService(
            List<NewsSourceDefinition> sources,
            Map<NewsSourceType, NewsProvider> providers,
            NewsRepository repository,
            NewsClassifier classifier,
            NewsSymbolTagger symbolTagger,
            NewsSentimentService sentimentService,
            NewsUrgencyService urgencyService,
            NewsDeduplicationService deduplicationService,
            NewsAlertService alertService) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
        this.providers = providers == null ? Map.of() : Map.copyOf(providers);
        this.repository = repository;
        this.classifier = classifier;
        this.symbolTagger = symbolTagger;
        this.sentimentService = sentimentService;
        this.urgencyService = urgencyService;
        this.deduplicationService = deduplicationService;
        this.alertService = alertService;
        this.executorService = Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable, "crypto-news-aggregator");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<NewsRefreshSummary> refreshAsync() {
        return CompletableFuture.supplyAsync(this::refresh, executorService);
    }

    public NewsRefreshSummary refresh() {
        if (!AppConfig.getBoolean("news.enabled", true)) {
            return new NewsRefreshSummary(LocalDateTime.now(), 0, 0, 0, List.of("News disabled"));
        }
        int maxItems = Math.max(1, AppConfig.getInt("news.maxItemsPerSource", 50));
        List<CryptoNewsItem> fetched = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int ok = 0;
        int failed = 0;
        for (NewsSourceDefinition source : sources) {
            if (source == null || !source.enabled()) {
                continue;
            }
            NewsProvider provider = providers.get(source.type());
            if (provider == null) {
                failed++;
                warnings.add("No provider for " + source.name());
                continue;
            }
            NewsFetchResult result = provider.fetch(source);
            if (!result.errors().isEmpty()) {
                failed++;
                warnings.add(source.name() + ": " + String.join("; ", result.errors()));
                continue;
            }
            ok++;
            fetched.addAll(result.items().stream().limit(maxItems).map(this::analyze).toList());
            warnings.addAll(result.warnings());
        }

        List<CryptoNewsItem> deduplicated = AppConfig.getBoolean("news.deduplicate", true)
                ? deduplicationService.markDuplicates(fetched)
                : fetched;
        repository.saveAll(deduplicated);
        deduplicated.stream()
                .filter(item -> !alertService.matchingRules(item).isEmpty())
                .forEach(item -> log.info("News alert triggered: {} [{} {}]", item.title(), item.urgency(), item.impact()));

        lastRefreshTime = LocalDateTime.now();
        lastSuccessfulSources = ok;
        lastFailedSources = failed;
        lastNewItemCount = deduplicated.size();
        return new NewsRefreshSummary(lastRefreshTime, ok, failed, deduplicated.size(), warnings);
    }

    public CryptoNewsItem analyze(CryptoNewsItem item) {
        NewsCategory category = classifier.classify(item);
        double sentiment = sentimentService.score(item, category);
        NewsImpact impact = sentimentService.impact(sentiment);
        NewsUrgency urgency = urgencyService.urgency(category, impact);
        NewsSymbolTagger.TagResult tags = symbolTagger.tag(item);
        double relevance = Math.min(1.0, 0.25 + tags.symbols().size() * 0.25 + Math.abs(sentiment) * 0.25);
        return item.withAnalysis(category, impact, urgency, tags.symbols(), tags.assets(), sentiment, relevance);
    }

    public List<CryptoNewsItem> latest(int limit) {
        return repository.findLatest(limit);
    }

    public NewsRepository repository() {
        return repository;
    }

    public NewsContextService contextService() {
        return new NewsContextService(repository);
    }

    public NewsRefreshSummary lastSummary() {
        return new NewsRefreshSummary(lastRefreshTime, lastSuccessfulSources, lastFailedSources, lastNewItemCount, List.of());
    }

    public void shutdown() {
        executorService.shutdownNow();
    }

    public List<CryptoNewsItem> latestForSymbol(String symbol, int limit) {
        return repository.findBySymbol(symbol, limit).stream()
                .sorted(Comparator.comparing(CryptoNewsItem::publishedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    public record NewsRefreshSummary(
            LocalDateTime refreshedAt,
            int sourcesOk,
            int sourcesFailed,
            int newItems,
            List<String> warnings) {
    }
}
