package org.investpro.news;

import org.investpro.config.AppConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NewsSourceRegistry {

    private NewsSourceRegistry() {
    }

    public static List<NewsSourceDefinition> defaultSources() {
        int interval = AppConfig.getInt("news.refreshIntervalMinutes", 15);
        return List.of(
                rss("coindesk", "CoinDesk", "https://www.coindesk.com/arc/outboundfeeds/rss/"),
                rss("cointelegraph", "Cointelegraph", "https://cointelegraph.com/rss"),
                rss("decrypt", "Decrypt", "https://decrypt.co/feed"),
                rss("the-block", "The Block", "https://www.theblock.co/rss.xml"),
                rss("blockworks", "Blockworks", "https://blockworks.co/feed"),
                rss("cryptoslate", "CryptoSlate", "https://cryptoslate.com/feed/"),
                rss("the-defiant", "The Defiant", "https://thedefiant.io/api/feed"),
                source("coinbase-blog", "Coinbase Blog", "https://www.coinbase.com/blog/rss.xml", NewsSourceType.EXCHANGE_ANNOUNCEMENT, interval),
                source("binance-announcements", "Binance Announcements", "https://www.binance.com/en/support/announcement/rss", NewsSourceType.EXCHANGE_ANNOUNCEMENT, interval),
                source("kraken-blog", "Kraken Blog", "https://blog.kraken.com/feed", NewsSourceType.EXCHANGE_ANNOUNCEMENT, interval),
                source("sec-news", "SEC News", "https://www.sec.gov/news/pressreleases.rss", NewsSourceType.REGULATORY, interval),
                source("cftc-news", "CFTC News", "https://www.cftc.gov/RSS/PressReleases.xml", NewsSourceType.REGULATORY, interval),
                source("federal-reserve", "Federal Reserve News", "https://www.federalreserve.gov/feeds/press_all.xml", NewsSourceType.REGULATORY, interval));
    }

    public static List<NewsSourceDefinition> enabledSources() {
        if (!AppConfig.getBoolean("news.enabled", true)) {
            return List.of();
        }
        return defaultSources().stream().filter(NewsSourceDefinition::enabled).toList();
    }

    private static NewsSourceDefinition rss(String id, String name, String url) {
        return source(id, name, url, NewsSourceType.RSS, AppConfig.getInt("news.refreshIntervalMinutes", 15));
    }

    private static NewsSourceDefinition source(String id, String name, String url, NewsSourceType type, int interval) {
        return new NewsSourceDefinition(id, name, url, type, true, interval, Set.of(NewsCategory.UNKNOWN), Map.of());
    }
}
