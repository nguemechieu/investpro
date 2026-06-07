package org.investpro.news;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class NewsNormalizer {

    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public String cleanHtml(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String withoutTags = HTML_TAGS.matcher(value).replaceAll(" ");
        return WHITESPACE.matcher(withoutTags
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&nbsp;", " "))
                .replaceAll(" ")
                .trim();
    }

    public String normalizeSourceName(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        return WHITESPACE.matcher(value.trim()).replaceAll(" ");
    }

    public String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            String query = uri.getRawQuery();
            String cleanQuery = "";
            if (query != null && !query.isBlank()) {
                cleanQuery = java.util.Arrays.stream(query.split("&"))
                        .filter(part -> !part.toLowerCase(Locale.ROOT).startsWith("utm_"))
                        .filter(part -> !part.toLowerCase(Locale.ROOT).startsWith("fbclid="))
                        .filter(part -> !part.toLowerCase(Locale.ROOT).startsWith("gclid="))
                        .reduce((left, right) -> left + "&" + right)
                        .orElse("");
            }
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(),
                    cleanQuery.isBlank() ? null : cleanQuery, null).toString();
        } catch (Exception ignored) {
            return url.trim();
        }
    }

    public String stableId(String sourceId, String url, String title) {
        String basis = (sourceId == null ? "" : sourceId)
                + "|"
                + normalizeUrl(url)
                + "|"
                + (title == null ? "" : title.trim().toLowerCase(Locale.ROOT));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(basis.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (Exception exception) {
            return URLEncoder.encode(basis, StandardCharsets.UTF_8);
        }
    }

    public CryptoNewsItem normalizeRawItem(
            NewsSourceDefinition source,
            String title,
            String summary,
            String url,
            LocalDateTime publishedAt) {
        LocalDateTime fetchedAt = LocalDateTime.now();
        String cleanUrl = normalizeUrl(url);
        String cleanTitle = cleanHtml(title);
        String cleanSummary = cleanHtml(summary);
        return new CryptoNewsItem(
                stableId(source.id(), cleanUrl, cleanTitle),
                source.id(),
                normalizeSourceName(source.name()),
                cleanTitle,
                cleanSummary,
                cleanUrl,
                publishedAt == null ? fetchedAt : publishedAt,
                fetchedAt,
                source.type(),
                NewsCategory.UNKNOWN,
                NewsImpact.UNKNOWN,
                NewsUrgency.LOW,
                java.util.Set.of(),
                java.util.Set.of(),
                0.0,
                0.0,
                false,
                null,
                Map.of());
    }
}
