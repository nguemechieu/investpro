package org.investpro.news;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RssNewsProviderTest {

    @Test
    void parsesRssItemsSafely() {
        NewsSourceDefinition source = new NewsSourceDefinition(
                "test",
                "Test Feed",
                "https://example.com/rss",
                NewsSourceType.RSS,
                true,
                15,
                Set.of(),
                Map.of());
        String xml = """
                <rss><channel>
                  <item>
                    <title>Bitcoin ETF approved</title>
                    <description><![CDATA[BTC rallies after approval]]></description>
                    <link>https://example.com/story?utm_source=x</link>
                    <pubDate>Sun, 07 Jun 2026 12:00:00 GMT</pubDate>
                  </item>
                </channel></rss>
                """;

        NewsFetchResult result = new RssNewsProvider().parse(source, xml, LocalDateTime.now());

        assertThat(result.errors()).isEmpty();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().title()).isEqualTo("Bitcoin ETF approved");
        assertThat(result.items().getFirst().url()).doesNotContain("utm_source");
    }
}
