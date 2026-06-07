package org.investpro.news;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
public class RssNewsProvider implements NewsProvider {

    private final HttpClient httpClient;
    private final NewsNormalizer normalizer;

    public RssNewsProvider() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), new NewsNormalizer());
    }

    public RssNewsProvider(HttpClient httpClient, NewsNormalizer normalizer) {
        this.httpClient = httpClient;
        this.normalizer = normalizer;
    }

    @Override
    public NewsFetchResult fetch(NewsSourceDefinition source) {
        LocalDateTime fetchedAt = LocalDateTime.now();
        if (source == null || source.url() == null || source.url().isBlank()) {
            return new NewsFetchResult(source, List.of(), List.of(), List.of("News source URL is missing"), fetchedAt);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(source.url()))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "InvestProCryptoNews/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return new NewsFetchResult(source, List.of(), List.of(), List.of("RSS HTTP " + response.statusCode()), fetchedAt);
            }
            return parse(source, response.body(), fetchedAt);
        } catch (Exception exception) {
            log.warn("RSS fetch failed for {}: {}", source.name(), exception.getMessage());
            return new NewsFetchResult(source, List.of(), List.of(), List.of(exception.getMessage()), fetchedAt);
        }
    }

    public NewsFetchResult parse(NewsSourceDefinition source, String xml, LocalDateTime fetchedAt) {
        List<CryptoNewsItem> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml == null ? "" : xml)));
            NodeList rssItems = document.getElementsByTagName("item");
            if (rssItems.getLength() == 0) {
                rssItems = document.getElementsByTagName("entry");
            }
            for (int i = 0; i < rssItems.getLength(); i++) {
                if (rssItems.item(i) instanceof Element element) {
                    String title = firstText(element, "title");
                    String summary = firstNonBlank(firstText(element, "description"), firstText(element, "summary"), firstText(element, "content"));
                    String link = firstNonBlank(firstText(element, "link"), linkHref(element));
                    LocalDateTime publishedAt = parseDate(firstNonBlank(
                            firstText(element, "pubDate"),
                            firstText(element, "published"),
                            firstText(element, "updated"),
                            firstText(element, "dc:date")));
                    if (title == null || title.isBlank()) {
                        continue;
                    }
                    items.add(normalizer.normalizeRawItem(source, title, summary, link, publishedAt));
                }
            }
        } catch (Exception exception) {
            errors.add(exception.getMessage());
        }
        return new NewsFetchResult(source, items, List.of(), errors, fetchedAt);
    }

    private String firstText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0) == null) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }

    private String linkHref(Element parent) {
        NodeList nodes = parent.getElementsByTagName("link");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element link && link.hasAttribute("href")) {
                return link.getAttribute("href");
            }
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.RFC_1123_DATE_TIME,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                DateTimeFormatter.ISO_ZONED_DATE_TIME,
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH));
        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter == DateTimeFormatter.RFC_1123_DATE_TIME) {
                    return ZonedDateTime.parse(value.trim(), formatter).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                }
                return OffsetDateTime.parse(value.trim(), formatter).toLocalDateTime();
            } catch (Exception ignored) {
            }
        }
        return LocalDateTime.now();
    }
}
