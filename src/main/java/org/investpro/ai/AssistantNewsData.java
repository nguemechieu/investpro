package org.investpro.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.service.RssNewsService;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Bounded news projections shared by the desktop and Telegram assistant. */
final class AssistantNewsData {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RssNewsService service;

    public AssistantNewsData(RssNewsService service) {
        this.service = Objects.requireNonNull(service);
    }

    String execute(String command) {
        var result = JSON.createObjectNode().put("snapshotTime", Instant.now().toString())
                .put("coverage", "RSS search; results may be cached for up to five minutes. Not exhaustive or real-time.");
        try {
            String[] args = command.trim().split("\\s+");
            if (args.length < 2 || args.length > 4 || !args[1].matches("[A-Za-z0-9./:_-]{1,64}"))
                throw new IllegalArgumentException();
            String type = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "stock";
            if (!Set.of("stock", "crypto", "forex").contains(type)) throw new IllegalArgumentException();
            int limit = args.length > 3 ? Math.clamp(Integer.parseInt(args[3]), 1, 20) : 10;
            result.put("symbol", args[1]).put("assetType", type);
            var articles = result.putArray("articles");
            for (var item : service.fetchSymbolNews(args[1], type, limit).stream().limit(limit).toList()) {
                String url = Objects.toString(item.get("url"), "");
                URI uri;
                try { uri = URI.create(url); } catch (IllegalArgumentException error) { continue; }
                if (uri.getHost() == null || !("https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme()))) continue;
                var article = articles.addObject();
                article.put("title", bounded(item.get("title"), 500));
                article.put("summary", bounded(item.get("summary"), 1500));
                article.put("source", bounded(item.get("source"), 200));
                article.put("url", url);
                // Use the feed's actual date text, not the parser's fallback to 'now'.
                article.put("publicationDate", bounded(item.get("publication_date"), 100));
            }
            result.put("available", !articles.isEmpty());
            if (articles.isEmpty()) result.put("message", "No usable articles returned. The feed may be unavailable, disabled, or have no matching news. Do not invent recent events.");
        } catch (IllegalArgumentException error) {
            result.put("available", false).put("message", "Usage: /news SYMBOL [stock|crypto|forex] [LIMIT 1-20]");
        } catch (Exception error) {
            result.put("available", false).put("message", "News retrieval unavailable. Try again later; do not invent recent events.");
        }
        return result.toString();
    }

    private static String bounded(Object value, int limit) {
        String text = Objects.toString(value, "");
        return text.substring(0, Math.min(text.length(), limit));
    }
}
