package org.investpro.service;

import org.investpro.exchange.Exchange;
import org.investpro.market.MarketStats;
import org.investpro.models.currency.Currency;
import org.investpro.models.market.NewsEvent;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Builds the best available market-information snapshot for a symbol.
 * <p>
 * Provider order:
 * 1. Active exchange ticker, when available.
 * 2. TradePair quote/cache fields.
 * 3. Currency and trading-session metadata.
 * 4. News calendar context.
 */
public class MarketInfoDataProvider {

    private final NewsDataProvider newsDataProvider;

    public MarketInfoDataProvider(NewsDataProvider newsDataProvider) {
        this.newsDataProvider = newsDataProvider;
    }

    public CompletableFuture<MarketStats> getMarketInfo(Exchange exchange, TradePair pair) {
        if (pair == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (exchange == null) {
            return CompletableFuture.completedFuture(buildStats(pair, null, "TradePair cache"));
        }

        CompletableFuture<Ticker> tickerFuture;
        try {
            tickerFuture = exchange.fetchTicker(pair);
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(buildStats(pair, null, "TradePair cache"));
        }

        if (tickerFuture == null) {
            return CompletableFuture.completedFuture(buildStats(pair, null, "TradePair cache"));
        }

        return tickerFuture
                .handle((ticker, error) -> {
                    if (error != null || ticker == null || !ticker.isValid()) {
                        return buildStats(pair, null, "TradePair cache");
                    }
                    return buildStats(pair, ticker, exchange.getName() + " ticker");
                });
    }

    private MarketStats buildStats(TradePair pair, Ticker ticker, String source) {
        double currentPrice = firstPositive(
                ticker == null ? 0.0 : ticker.getMidPrice(),
                pair.getMidPrice(),
                pair.getLastPrice());

        double bid = firstPositive(ticker == null ? 0.0 : ticker.getBidPrice(), pair.getBid());
        double ask = firstPositive(ticker == null ? 0.0 : ticker.getAskPrice(), pair.getAsk());
        double spread = bid > 0.0 && ask > 0.0 ? Math.abs(ask - bid) : pair.getSpread();
        double spreadPercent = currentPrice > 0.0 ? (spread / currentPrice) * 100.0 : 0.0;
        double volume = firstPositive(ticker == null ? 0.0 : ticker.getVolume(), pair.getVolume());
        double changePercent = ticker == null || ticker.getChangePercent() == 0.0
                ? pair.getChangePercent()
                : ticker.getChangePercent();
        double high = firstPositive(ticker == null ? 0.0 : ticker.getHighPrice(), pair.getHigh24h());
        double low = firstPositive(ticker == null ? 0.0 : ticker.getLowPrice(), pair.getLow24h());

        return MarketStats.builder()
                .currentPrice(currentPrice)
                .marketCap(0.0)
                .marketCapRank(0.0)
                .volume24h(volume)
                .volumeChange24h(changePercent)
                .circulating(0.0)
                .circulatingUnit(pair.getBaseCode())
                .fullyDilutedMarketCap(0.0)
                .allTimeHigh(high)
                .allTimeHighDate(high > 0.0 ? "24h high" : "")
                .percentDownFromATH(high > 0.0 && currentPrice > 0.0 ? ((high - currentPrice) / high) * 100.0 : 0.0)
                .performanceOneYear(0.0)
                .performanceOneMonth(0.0)
                .performanceOneWeek(0.0)
                .performanceOneDay(changePercent)
                .vsEthOneYear(0.0)
                .vsMarketOneYear(0.0)
                .vsEthOneMonth(0.0)
                .vsMarketOneMonth(0.0)
                .coinbasePopularityRank(source)
                .symbol(pair.getSymbol())
                .name(pair.getBaseCode() + " / " + pair.getCounterCode())
                .description(buildDescription(pair, source, bid, ask, spread, spreadPercent, high, low))
                .websiteUrl("")
                .whitePaperUrl("")
                .githubUrl("")
                .twitterUrl("")
                .build();
    }

    private String buildDescription(
            TradePair pair,
            String source,
            double bid,
            double ask,
            double spread,
            double spreadPercent,
            double high,
            double low) {
        Currency base = pair.getBaseCurrency();
        Currency counter = pair.getCounterCurrency();
        StringBuilder description = new StringBuilder();

        description.append("Data source: ").append(source).append("\n\n");
        description.append(pair.getSymbol()).append(" quotes ")
                .append(nameOrCode(base)).append(" against ")
                .append(nameOrCode(counter)).append(".\n");
        description.append("Base: ").append(currencyLine(base)).append("\n");
        description.append("Quote: ").append(currencyLine(counter)).append("\n\n");

        if (bid > 0.0 || ask > 0.0) {
            description.append("Live bid/ask: ")
                    .append(formatNumber(bid)).append(" / ")
                    .append(formatNumber(ask)).append("\n");
            description.append("Spread: ").append(formatNumber(spread))
                    .append(" (").append(String.format("%.4f", spreadPercent)).append("%)\n");
        }

        if (high > 0.0 || low > 0.0) {
            description.append("24h range: ")
                    .append(formatNumber(low)).append(" - ")
                    .append(formatNumber(high)).append("\n");
        }

        if (pair.getTradingSession() != null) {
            description.append("\nSession: ").append(pair.getTradingSessionStatus().getDisplayName()).append("\n");
            description.append(pair.getTradingSession().getNotes()).append("\n");
        }

        appendNewsContext(description, pair);
        return description.toString();
    }

    private void appendNewsContext(StringBuilder description, TradePair pair) {
        if (newsDataProvider == null) {
            return;
        }

        try {
            List<NewsEvent> events = newsDataProvider.getUpcomingNewsEvents().stream()
                    .filter(event -> event != null && event.getCurrency() != null)
                    .filter(event -> event.getCurrency().equalsIgnoreCase(pair.getBaseCode())
                            || event.getCurrency().equalsIgnoreCase(pair.getCounterCode()))
                    .limit(3)
                    .toList();

            if (!events.isEmpty()) {
                description.append("\nUpcoming related news:\n");
                for (NewsEvent event : events) {
                    description.append("- ")
                            .append(event.getCurrency()).append(": ")
                            .append(event.getTitle()).append(" (")
                            .append(event.getImportance()).append(")\n");
                }
            }
        } catch (Exception ignored) {
            // Market info should remain available even if news data is unavailable.
        }
    }

    private String currencyLine(Currency currency) {
        if (currency == null) {
            return "Unknown";
        }
        return "%s (%s, %s)".formatted(
                nameOrCode(currency),
                currency.getCode(),
                currency.getCurrencyType());
    }

    private String nameOrCode(Currency currency) {
        if (currency == null) {
            return "Unknown";
        }
        String name = currency.getFullDisplayName();
        return name == null || name.isBlank() ? currency.getCode() : name;
    }

    private double firstPositive(double... values) {
        for (double value : values) {
            if (Double.isFinite(value) && value > 0.0) {
                return value;
            }
        }
        return 0.0;
    }

    private String formatNumber(double value) {
        if (value <= 0.0 || !Double.isFinite(value)) {
            return "N/A";
        }
        return value >= 100.0 ? String.format("%.2f", value) : String.format("%.6f", value);
    }
}
