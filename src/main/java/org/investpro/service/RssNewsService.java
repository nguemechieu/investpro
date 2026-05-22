package org.investpro.service;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * RSS News Service for InvestPro.
 *
 * Responsibilities:
 * - Fetch RSS news for symbols/assets
 * - Build symbol-aware search queries (crypto, forex, stocks, futures)
 * - Parse RSS safely
 * - Deduplicate repeated headlines
 * - Score sentiment from title/summary/source
 * - Estimate event impact
 * - Apply recency decay
 * - Summarize news into buy/sell/neutral bias
 * - Support lightweight in-memory caching
 * - Fail-safe: if news fails, return neutral/empty instead of crashing
 */
@Slf4j
public class RssNewsService {

    private static final String DEFAULT_FEED_URL = "https://news.google.com/rss/search?q={query}&hl=en-US&gl=US&ceid=US:en";
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (compatible; InvestProNewsService/1.0; +https://investpro.local)";

    // Top-tier financial RSS feeds for general market news (no API key required)
    private static final List<String> FINANCIAL_RSS_FEEDS = List.of(
            "https://feeds.reuters.com/reuters/businessNews",
            "https://www.cnbc.com/id/100003114/device/rss/rss.html",
            "http://feeds.marketwatch.com/marketwatch/topstories/",
            "https://www.forexlive.com/feed/news",
            "https://www.fxstreet.com/rss/news",
            "https://feeds.bbci.co.uk/news/business/rss.xml",
            "https://news.google.com/rss/search?q=forex+market+trading&hl=en-US&gl=US&ceid=US:en",
            "https://news.google.com/rss/search?q=currency+market+central+bank&hl=en-US&gl=US&ceid=US:en"
    );

    private static final Set<String> POSITIVE_KEYWORDS = Set.of(
            "surge", "surges", "growth", "beat", "beats", "bullish", "approval", "approved",
            "upside", "record", "breakout", "upgrade", "upgraded", "strong", "rally", "rallies",
            "expands", "expansion", "adoption", "profit", "profits", "gain", "gains", "outperform",
            "optimism", "optimistic", "rebound", "recovery", "raises", "raised", "partnership",
            "launch", "launches", "inflows", "accumulation", "demand");

    private static final Set<String> NEGATIVE_KEYWORDS = Set.of(
            "drop", "drops", "fall", "falls", "fell", "lawsuit", "bearish", "downgrade",
            "downgraded", "weak", "weakness", "recession", "selloff", "sell-off", "investigation",
            "risk", "risks", "warning", "loss", "losses", "miss", "misses", "cuts", "cut", "crash",
            "bankruptcy", "default", "fraud", "hack", "hacked", "exploit", "liquidation", "outflows",
            "ban", "banned", "probe", "slump", "plunge", "plunges", "fear", "volatility");

    private static final Set<String> HIGH_IMPACT_KEYWORDS = Set.of(
            "fed", "fomc", "cpi", "inflation", "interest rate", "rate cut", "rate hike", "sec",
            "cftc", "earnings", "etf", "guidance", "payrolls", "nfp", "gdp", "tariff", "tariffs",
            "opec", "war", "sanctions", "recession", "bankruptcy", "default", "federal reserve",
            "treasury", "jobs report", "pce", "unemployment", "oil inventories", "halving", "hack",
            "exploit", "lawsuit", "approval");

    private static final Set<String> NEGATION_TERMS = Set.of(
            "not", "no", "never", "without", "denies", "denied", "rejects", "rejected");

    private static final Map<String, String> SYMBOL_ALIASES = Map.ofEntries(
            // Crypto
            Map.entry("BTC", "Bitcoin"),
            Map.entry("XBT", "Bitcoin"),
            Map.entry("ETH", "Ethereum"),
            Map.entry("SOL", "Solana"),
            Map.entry("XRP", "Ripple XRP"),
            Map.entry("DOGE", "Dogecoin"),
            Map.entry("ADA", "Cardano"),
            Map.entry("BNB", "Binance Coin"),
            Map.entry("AVAX", "Avalanche crypto"),
            Map.entry("DOT", "Polkadot crypto"),
            Map.entry("LINK", "Chainlink crypto"),
            Map.entry("MATIC", "Polygon crypto"),
            Map.entry("LTC", "Litecoin"),
            Map.entry("BCH", "Bitcoin Cash"),
            Map.entry("USDT", "Tether"),
            Map.entry("USDC", "USD Coin"),
            // Forex / macro
            Map.entry("EUR", "Euro"),
            Map.entry("USD", "US Dollar"),
            Map.entry("GBP", "British Pound"),
            Map.entry("JPY", "Japanese Yen"),
            Map.entry("CAD", "Canadian Dollar"),
            Map.entry("AUD", "Australian Dollar"),
            Map.entry("NZD", "New Zealand Dollar"),
            Map.entry("CHF", "Swiss Franc"),
            Map.entry("CNY", "Chinese Yuan"),
            Map.entry("XAU", "Gold"),
            Map.entry("XAG", "Silver"),
            Map.entry("WTI", "Crude Oil"),
            Map.entry("BRENT", "Brent crude"),
            // ETFs / indexes
            Map.entry("SPY", "S&P 500 ETF"),
            Map.entry("QQQ", "Nasdaq 100 ETF"),
            Map.entry("DIA", "Dow Jones ETF"),
            Map.entry("IWM", "Russell 2000 ETF"),
            Map.entry("VIX", "volatility index"),
            // Common stocks
            Map.entry("AAPL", "Apple stock"),
            Map.entry("MSFT", "Microsoft stock"),
            Map.entry("NVDA", "Nvidia stock"),
            Map.entry("TSLA", "Tesla stock"),
            Map.entry("AMZN", "Amazon stock"),
            Map.entry("GOOGL", "Alphabet stock"),
            Map.entry("GOOG", "Alphabet stock"),
            Map.entry("META", "Meta stock"),
            Map.entry("AMD", "AMD stock"),
            Map.entry("COIN", "Coinbase stock"));

    private static final Set<String> STABLE_QUOTES = Set.of("USDT", "USD", "USDC", "BUSD", "DAI", "FDUSD", "TUSD");

    private final HttpClient httpClient;
    private final DocumentBuilder documentBuilder;
    private final boolean enabled;
    private final String feedUrlTemplate;
    private final long requestTimeoutSeconds;
    private final long cacheTtlSeconds;
    private final String userAgent;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(long createdAt, List<Map<String, Object>> events) {
            private CacheEntry(long createdAt, List<Map<String, Object>> events) {
                this.createdAt = createdAt;
                this.events = new ArrayList<>(events);
            }
        }

    public RssNewsService() {
        this(true, DEFAULT_FEED_URL, 15, 300, DEFAULT_USER_AGENT);
    }

    public RssNewsService(boolean enabled, String feedUrlTemplate, long requestTimeoutSeconds,
            long cacheTtlSeconds, String userAgent) {
        this.enabled = enabled;
        this.feedUrlTemplate = feedUrlTemplate != null && !feedUrlTemplate.isBlank() ? feedUrlTemplate
                : DEFAULT_FEED_URL;
        this.requestTimeoutSeconds = Math.max(1, requestTimeoutSeconds);
        this.cacheTtlSeconds = Math.max(0, cacheTtlSeconds);
        this.userAgent = userAgent != null && !userAgent.isBlank() ? userAgent : DEFAULT_USER_AGENT;
        this.httpClient = HttpClient.newHttpClient();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            this.documentBuilder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            log.error("Failed to create DocumentBuilder", e);
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------------
    // Symbol and query handling
    // ------------------------------------------------------------------

    private String cleanSymbolPart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String clean = value.toUpperCase().strip();
        clean = clean.replace(":USD", "").replace(":USDT", "")
                .replace("-PERP", "").replaceAll("[^A-Z0-9.]", "");
        return clean;
    }

    private String[] splitSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return new String[] { "", "" };
        }

        String normalized = symbol.toUpperCase().strip().replace("-", "/");
        String settlement = "";

        if (normalized.contains(":")) {
            String[] parts = normalized.split(":", 2);
            normalized = parts[0];
            settlement = cleanSymbolPart(parts[1]);
        }

        String[] result = new String[] { "", "" };
        if (normalized.contains("/")) {
            String[] parts = normalized.split("/", 2);
            result[0] = cleanSymbolPart(parts[0]);
            result[1] = cleanSymbolPart(parts[1]);
        } else {
            result[0] = cleanSymbolPart(normalized);
            result[1] = settlement;
        }

        return result;
    }

    private String alias(String symbolPart) {
        if (symbolPart == null || symbolPart.isBlank()) {
            return "";
        }
        return SYMBOL_ALIASES.getOrDefault(symbolPart.toUpperCase().strip(), symbolPart.toUpperCase().strip());
    }

    private String buildQuery(String symbol, String brokerType) {
        String[] parts = splitSymbol(symbol);
        String base = parts[0];
        String quote = parts[1];

        if (base.isEmpty()) {
            return "";
        }

        String baseAlias = alias(base);
        String quoteAlias = alias(quote);
        String brokerLower = brokerType != null ? brokerType.toLowerCase().strip() : "";

        // Forex pairs
        if ((brokerLower.equals("forex") || brokerLower.equals("fx") || brokerLower.equals("oanda"))
                && !quote.isEmpty()) {
            return String.format("\"%s%s\" OR \"%s %s\" OR \"%s\" OR \"%s\" forex",
                    base, quote, base, quote, baseAlias, quoteAlias);
        }

        // Perpetual/futures-style symbols
        if (symbol.toUpperCase().contains("PERP") || symbol.contains(":")) {
            return String.format("\"%s perpetual\" OR \"%s\" OR \"%s futures\" OR \"%s crypto\"",
                    base, baseAlias, base, base);
        }

        // Crypto pair
        if (quote.isEmpty() || STABLE_QUOTES.contains(quote)
                || brokerLower.matches("(crypto|ccxt|binance|coinbase|kraken)")) {
            return String.format("\"%s\" OR \"%s price\" OR \"%s crypto\"", baseAlias, base, base);
        }

        // Stock-like symbols
        if (brokerLower.matches("(stock|stocks|equity|alpaca)")) {
            return String.format("\"%s\" OR \"%s\" stock earnings", base, baseAlias);
        }

        String result = String.format("\"%s\" OR \"%s\"", base, baseAlias);
        if (!quote.isEmpty() && !STABLE_QUOTES.contains(quote)) {
            result += String.format(" OR \"%s\" OR \"%s\"", quote, quoteAlias);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Text cleanup / parsing
    // ------------------------------------------------------------------

    private String stripHtml(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.replaceAll("&#?[a-zA-Z0-9]+;", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ");
        return text.strip();
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String normalized = stripHtml(title);
        normalized = normalized.replaceAll("\\s+-\\s+[^-]{2,80}$", "").strip();
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized;
    }

    private String dedupeKey(String title, String url) {
        String text = normalizeTitle(title).toLowerCase();
        text = text.replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").strip();

        if (!text.isEmpty()) {
            return text.length() > 180 ? text.substring(0, 180) : text;
        }

        return (url != null ? url : "").toLowerCase().strip();
    }

    private ZonedDateTime parseTimestamp(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ZonedDateTime.now(ZoneId.systemDefault());
        }

        // Try email date format (RFC 2822)
        String[] formats = {
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy HH:mm:ss z",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
        };

        SimpleDateFormat sdf = new SimpleDateFormat();
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        for (String format : formats) {
            try {
                sdf.applyPattern(format);
                return ZonedDateTime.ofInstant(sdf.parse(rawValue).toInstant(), ZoneId.systemDefault());
            } catch (ParseException ignored) {
            }
        }

        // Fallback to current time
        return ZonedDateTime.now(ZoneId.systemDefault());
    }

    // ------------------------------------------------------------------
    // Sentiment scoring
    // ------------------------------------------------------------------

    private boolean containsKeyword(String text, String keyword) {
        text = text.toLowerCase();
        keyword = keyword.toLowerCase().strip();

        if (keyword.contains(" ")) {
            return text.contains(keyword);
        }

        return Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b").matcher(text).find();
    }

    private boolean nearNegation(String text, String keyword) {
        String[] words = text.toLowerCase().split("\\b");
        keyword = keyword.toLowerCase().strip();
        String[] keyParts = keyword.split(" ");
        String first = keyParts.length > 0 ? keyParts[0] : keyword;

        for (int idx = 0; idx < words.length; idx++) {
            if (!words[idx].trim().equals(first)) {
                continue;
            }

            int start = Math.max(0, idx - 3);
            for (int i = start; i < idx; i++) {
                if (NEGATION_TERMS.contains(words[i].trim())) {
                    return true;
                }
            }
        }

        return false;
    }

    private Map<String, Object> scoreHeadline(String title, String summary, String source) {
        String text = String.format("%s %s", title, summary != null ? summary : "").toLowerCase();

        Set<String> positiveHits = new HashSet<>();
        Set<String> negativeHits = new HashSet<>();
        Set<String> highImpactHits = new HashSet<>();

        for (String word : POSITIVE_KEYWORDS) {
            if (containsKeyword(text, word)) {
                if (nearNegation(text, word)) {
                    negativeHits.add("not_" + word);
                } else {
                    positiveHits.add(word);
                }
            }
        }

        for (String word : NEGATIVE_KEYWORDS) {
            if (containsKeyword(text, word)) {
                if (nearNegation(text, word)) {
                    positiveHits.add("not_" + word);
                } else {
                    negativeHits.add(word);
                }
            }
        }

        for (String word : HIGH_IMPACT_KEYWORDS) {
            if (containsKeyword(text, word)) {
                highImpactHits.add(word);
            }
        }

        double rawSentiment = positiveHits.size() - negativeHits.size();
        double sentiment = rawSentiment > 0 ? Math.min(rawSentiment / 4.0, 1.0)
                : rawSentiment < 0 ? Math.max(rawSentiment / 4.0, -1.0) : 0.0;

        double impact = 1.0 + (0.30 * highImpactHits.size());

        String sourceLower = (source != null ? source : "").toLowerCase();
        if (sourceLower.contains("reuters") || sourceLower.contains("bloomberg")
                || sourceLower.contains("cnbc") || sourceLower.contains("wall street journal")
                || sourceLower.contains("financial times")) {
            impact += 0.20;
        }

        impact = Math.min(impact, 3.0);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("positive_hits", new ArrayList<>(positiveHits));
        metadata.put("negative_hits", new ArrayList<>(negativeHits));
        metadata.put("high_impact_hits", new ArrayList<>(highImpactHits));
        metadata.put("raw_sentiment", rawSentiment);

        return Map.of(
                "sentiment", Math.round(sentiment * 10000.0) / 10000.0,
                "impact", Math.round(impact * 100.0) / 100.0,
                "metadata", metadata);
    }

    // ------------------------------------------------------------------
    // Fetch / parse RSS
    // ------------------------------------------------------------------

    public List<Map<String, Object>> fetchSymbolNews(String symbol, String brokerType, int limit) {
        if (!enabled) {
            return new ArrayList<>();
        }

        limit = Math.max(1, Math.min(limit, 50));

        String cacheKey = String.format("%s|%s|%d",
                symbol != null ? symbol.toUpperCase().strip() : "",
                brokerType != null ? brokerType.toLowerCase().strip() : "",
                limit);

        List<Map<String, Object>> cached = getCached(cacheKey);
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        String query = buildQuery(symbol, brokerType);
        if (query.isEmpty()) {
            return new ArrayList<>();
        }

        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = feedUrlTemplate.replace("{query}", encodedQuery);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("User-Agent", userAgent)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.warn("News feed request failed with status {} for symbol {}", response.statusCode(), symbol);
                return new ArrayList<>();
            }

            Document doc = documentBuilder.parse(new org.xml.sax.InputSource(
                    new java.io.StringReader(response.body())));

            List<Map<String, Object>> events = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            NodeList items = doc.getElementsByTagName("item");
            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);

                String rawTitle = getElementText(item, "title");
                String title = normalizeTitle(rawTitle);

                if (title.isEmpty()) {
                    continue;
                }

                String description = stripHtml(getElementText(item, "description"));
                String link = stripHtml(getElementText(item, "link"));
                String source = getSource(item, rawTitle);
                String dedupeKey = dedupeKey(title, link);

                if (seen.contains(dedupeKey)) {
                    continue;
                }
                seen.add(dedupeKey);

                ZonedDateTime timestamp = parseTimestamp(getElementText(item, "pubDate"));
                double ageHours = Math.max(0,
                        Duration.between(timestamp.toInstant(), now.toInstant()).toSeconds() / 3600.0);

                @SuppressWarnings("unchecked")
                Map<String, Object> scoreResult = scoreHeadline(title, description, source);
                double sentimentScore = ((Number) scoreResult.get("sentiment")).doubleValue();
                double impact = ((Number) scoreResult.get("impact")).doubleValue();
                @SuppressWarnings("unchecked")
                Map<String, Object> scoringMetadata = (Map<String, Object>) scoreResult.get("metadata");

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("query", query);
                metadata.put("scoring", scoringMetadata);

                Map<String, Object> eventMap = new LinkedHashMap<>();
                eventMap.put("symbol", symbol != null ? symbol.toUpperCase().strip() : "");
                eventMap.put("title", title);
                eventMap.put("summary", description);
                eventMap.put("url", link);
                eventMap.put("source", !source.isEmpty() ? source : "News Feed");
                eventMap.put("timestamp", timestamp.toInstant().toString());
                eventMap.put("sentiment_score", sentimentScore);
                eventMap.put("impact", impact);
                eventMap.put("age_hours", Math.round(ageHours * 10000.0) / 10000.0);
                eventMap.put("metadata", metadata);

                events.add(eventMap);
            }

            events.sort((e1, e2) -> {
                String ts1 = (String) e1.getOrDefault("timestamp", "");
                String ts2 = (String) e2.getOrDefault("timestamp", "");
                return ts2.compareTo(ts1);
            });

            if (events.size() > limit) {
                events = events.subList(0, limit);
            }

            setCached(cacheKey, events);
            return events;

        } catch (InterruptedException e) {
            log.debug("News fetch interrupted for {}: {}", symbol, e.getMessage());
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        } catch (IOException e) {
            log.debug("News fetch failed for {}: {}", symbol, e.getMessage());
            return new ArrayList<>();
        } catch (SAXException e) {
            log.debug("News XML parse failed for {}: {}", symbol, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ------------------------------------------------------------------
    // News bias summarization
    // ------------------------------------------------------------------

    public NewsBias summarizeNewsBias(List<Map<String, Object>> events, double maxAgeHours,
            double buyThreshold, double sellThreshold) {
        if (events == null || events.isEmpty()) {
            return NewsBias.builder()
                    .direction("neutral")
                    .score(0.0)
                    .confidence(0.0)
                    .reason("No recent news events found.")
                    .build();
        }

        maxAgeHours = Math.max(1.0, maxAgeHours);
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        List<Map<String, Object>> usableEvents = new ArrayList<>();
        double totalScore = 0.0;
        double impactTotal = 0.0;
        int positiveCount = 0;
        int negativeCount = 0;
        int neutralCount = 0;

        for (Map<String, Object> event : events) {
            try {
                String tsStr = (String) event.getOrDefault("timestamp", "");
                ZonedDateTime timestamp = ZonedDateTime.parse(tsStr);
                double ageHours = Math.max(0,
                        Duration.between(timestamp.toInstant(), now.toInstant()).toSeconds() / 3600.0);

                if (ageHours > maxAgeHours) {
                    continue;
                }

                double sentiment = safeDouble(event.get("sentiment_score"), 0.0);
                double impact = Math.max(0.1, safeDouble(event.get("impact"), 1.0));
                double decay = Math.max(0.15, 1.0 - (ageHours / maxAgeHours));
                double eventScore = sentiment * impact * decay;

                if (Math.abs(eventScore) > 0.01) {
                    usableEvents.add(event);
                }

                totalScore += eventScore;
                impactTotal += impact;

                if (sentiment > 0.05) {
                    positiveCount++;
                } else if (sentiment < -0.05) {
                    negativeCount++;
                } else {
                    neutralCount++;
                }

            } catch (Exception e) {
                log.debug("Error processing event: {}", e.getMessage());
            }
        }

        if (usableEvents.isEmpty()) {
            return NewsBias.builder()
                    .direction("neutral")
                    .score(0.0)
                    .confidence(0.0)
                    .reason("News exists, but nothing recent has enough directional impact yet.")
                    .eventCount(0)
                    .positiveCount(positiveCount)
                    .negativeCount(negativeCount)
                    .neutralCount(neutralCount)
                    .build();
        }

        usableEvents.sort((e1, e2) -> {
            String ts1 = (String) e1.getOrDefault("timestamp", "");
            String ts2 = (String) e2.getOrDefault("timestamp", "");
            return ts2.compareTo(ts1);
        });

        Map<String, Object> top = usableEvents.get(0);
        String headline = (String) top.getOrDefault("title", "");
        String latestTimestamp = (String) top.getOrDefault("timestamp", "");

        String direction = "neutral";
        if (totalScore >= buyThreshold) {
            direction = "buy";
        } else if (totalScore <= sellThreshold) {
            direction = "sell";
        }

        int eventCount = usableEvents.size();
        int agreementCount = Math.max(positiveCount, Math.max(negativeCount, neutralCount));
        int totalCount = positiveCount + negativeCount + neutralCount;
        double agreementRatio = totalCount > 0 ? (double) agreementCount / totalCount : 0;
        double scoreStrength = Math.min(Math.abs(totalScore) / Math.max(eventCount, 1), 1.0);
        double confidence = Math.min((0.65 * scoreStrength) + (0.35 * agreementRatio), 1.0);

        double averageImpact = totalCount > 0 ? impactTotal / totalCount : 0;

        String reason;
        if ("neutral".equals(direction)) {
            reason = !headline.isEmpty() ? String.format("Recent headlines are mixed. Latest: %s", headline)
                    : "Recent headlines are mixed.";
        } else if ("buy".equals(direction)) {
            reason = !headline.isEmpty() ? String.format("Recent news bias is supportive. Latest: %s", headline)
                    : "Recent news bias is supportive.";
        } else {
            reason = !headline.isEmpty() ? String.format("Recent news bias is negative. Latest: %s", headline)
                    : "Recent news bias is negative.";
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("max_age_hours", maxAgeHours);
        metadata.put("buy_threshold", buyThreshold);
        metadata.put("sell_threshold", sellThreshold);

        return NewsBias.builder()
                .direction(direction)
                .score(Math.round(totalScore * 10000.0) / 10000.0)
                .confidence(Math.round(confidence * 10000.0) / 10000.0)
                .reason(reason)
                .headline(headline)
                .eventCount(eventCount)
                .positiveCount(positiveCount)
                .negativeCount(negativeCount)
                .neutralCount(neutralCount)
                .averageImpact(Math.round(averageImpact * 10000.0) / 10000.0)
                .latestTimestamp(latestTimestamp)
                .metadata(metadata)
                .build();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String getElementText(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return "";
    }

    private String getSource(Element item, String title) {
        NodeList sourceNodes = item.getElementsByTagName("source");
        if (sourceNodes.getLength() > 0) {
            return stripHtml(sourceNodes.item(0).getTextContent());
        }

        if (title.contains(" - ")) {
            String[] parts = title.split(" - ");
            String possible = parts[parts.length - 1].strip();
            if (possible.length() >= 2 && possible.length() <= 80) {
                return possible;
            }
        }

        return "News Feed";
    }

    private List<Map<String, Object>> getCached(String key) {
        if (cacheTtlSeconds <= 0) {
            return null;
        }

        CacheEntry cached = cache.get(key);
        if (cached == null) {
            return null;
        }

        long ageSeconds = System.currentTimeMillis() - cached.createdAt();
        if (ageSeconds > cacheTtlSeconds * 1000) {
            cache.remove(key);
            return null;
        }

        return new ArrayList<>(cached.events());
    }

    private void setCached(String key, List<Map<String, Object>> events) {
        if (cacheTtlSeconds <= 0) {
            return;
        }

        cache.put(key, new CacheEntry(System.currentTimeMillis(), events));
    }

    private double safeDouble(Object value, double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public void clearCache() {
        cache.clear();
    }

    /**
     * Fetch general market news from multiple top-tier financial RSS feeds.
     * Aggregates, deduplicates, scores sentiment, and returns the top results.
     *
     * @param limit Maximum number of news articles to return (1-100)
     * @return Merged, deduplicated, scored list of news articles
     */
    public List<Map<String, Object>> fetchGeneralMarketNews(int limit) {
        if (!enabled) return new ArrayList<>();
        limit = Math.max(1, Math.min(limit, 100));

        String cacheKey = "general_market_news|" + limit;
        List<Map<String, Object>> cached = getCached(cacheKey);
        if (cached != null) return new ArrayList<>(cached);

        List<Map<String, Object>> aggregated = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        for (String feedUrl : FINANCIAL_RSS_FEEDS) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(feedUrl))
                        .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                        .header("User-Agent", userAgent)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) continue;

                Document doc = documentBuilder.parse(new org.xml.sax.InputSource(
                        new java.io.StringReader(response.body())));

                NodeList items = doc.getElementsByTagName("item");
                for (int i = 0; i < items.getLength(); i++) {
                    Element item = (Element) items.item(i);
                    String rawTitle = getElementText(item, "title");
                    String title = normalizeTitle(rawTitle);
                    if (title.isEmpty()) continue;

                    String link = stripHtml(getElementText(item, "link"));
                    String dedupeKey = dedupeKey(title, link);
                    if (seen.contains(dedupeKey)) continue;
                    seen.add(dedupeKey);

                    String description = stripHtml(getElementText(item, "description"));
                    String source = getSource(item, rawTitle);
                    ZonedDateTime timestamp = parseTimestamp(getElementText(item, "pubDate"));
                    double ageHours = Math.max(0,
                            Duration.between(timestamp.toInstant(), now.toInstant()).toSeconds() / 3600.0);

                    Map<String, Object> scoreResult = scoreHeadline(title, description, source);
                    double sentimentScore = ((Number) scoreResult.get("sentiment")).doubleValue();
                    double impact = ((Number) scoreResult.get("impact")).doubleValue();

                    Map<String, Object> eventMap = new LinkedHashMap<>();
                    eventMap.put("symbol", "MARKET");
                    eventMap.put("title", title);
                    eventMap.put("summary", description);
                    eventMap.put("url", link);
                    eventMap.put("source", !source.isEmpty() ? source : "Financial News");
                    eventMap.put("timestamp", timestamp.toInstant().toString());
                    eventMap.put("sentiment_score", sentimentScore);
                    eventMap.put("impact", impact);
                    eventMap.put("age_hours", Math.round(ageHours * 10000.0) / 10000.0);
                    eventMap.put("feed_url", feedUrl);
                    aggregated.add(eventMap);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("General news fetch interrupted for feed {}", feedUrl);
            } catch (Exception e) {
                log.debug("General news fetch failed for feed {}: {}", feedUrl, e.getMessage());
            }
        }

        // Sort by timestamp (newest first), then score by impact
        aggregated.sort((a, b) -> {
            String ts1 = (String) a.getOrDefault("timestamp", "");
            String ts2 = (String) b.getOrDefault("timestamp", "");
            return ts2.compareTo(ts1);
        });

        List<Map<String, Object>> result = aggregated.size() > limit ? aggregated.subList(0, limit) : aggregated;
        setCached(cacheKey, result);
        log.info("Fetched {} general market news articles from {} feeds", result.size(), FINANCIAL_RSS_FEEDS.size());
        return new ArrayList<>(result);
    }
}
