package org.investpro.news;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NewsSymbolTagger {

    private static final Pattern PAIR_PATTERN = Pattern.compile("\\b([A-Z]{2,10})[/-]?(USD|USDT|USDC|BTC|ETH)\\b");
    private static final Pattern DOLLAR_SYMBOL_PATTERN = Pattern.compile("\\$([A-Z]{2,10})\\b");
    private static final Set<String> COMMON_FALSE_POSITIVES = Set.of(
            "THE", "AND", "FOR", "WITH", "FROM", "THIS", "THAT", "WILL", "JUST", "MORE", "NEWS",
            "SEC", "ETF", "CFTC", "CEO", "USD", "API", "RSS", "AI", "TV", "US");
    private static final Map<String, String> ASSET_NAMES = Map.ofEntries(
            Map.entry("BTC", "Bitcoin"),
            Map.entry("ETH", "Ethereum"),
            Map.entry("SOL", "Solana"),
            Map.entry("XRP", "XRP"),
            Map.entry("XLM", "Stellar"),
            Map.entry("USDC", "USD Coin"),
            Map.entry("USDT", "Tether"),
            Map.entry("DOGE", "Dogecoin"),
            Map.entry("ADA", "Cardano"),
            Map.entry("BNB", "BNB"),
            Map.entry("AVAX", "Avalanche"),
            Map.entry("LINK", "Chainlink"),
            Map.entry("DOT", "Polkadot"),
            Map.entry("LTC", "Litecoin"),
            Map.entry("BCH", "Bitcoin Cash"),
            Map.entry("MATIC", "Polygon"));

    public TagResult tag(CryptoNewsItem item) {
        String text = ((item == null ? "" : item.title()) + " " + (item == null ? "" : item.summary())).toUpperCase(Locale.ROOT);
        Set<String> symbols = new LinkedHashSet<>();
        Set<String> assets = new LinkedHashSet<>();

        Matcher pairMatcher = PAIR_PATTERN.matcher(text);
        while (pairMatcher.find()) {
            addSymbol(pairMatcher.group(1), symbols, assets);
            addSymbol(pairMatcher.group(2), symbols, assets);
        }

        Matcher dollarMatcher = DOLLAR_SYMBOL_PATTERN.matcher(text);
        while (dollarMatcher.find()) {
            addSymbol(dollarMatcher.group(1), symbols, assets);
        }

        for (Map.Entry<String, String> asset : ASSET_NAMES.entrySet()) {
            if (text.matches(".*\\b" + Pattern.quote(asset.getKey()) + "\\b.*")
                    || text.toLowerCase(Locale.ROOT).contains(asset.getValue().toLowerCase(Locale.ROOT))) {
                addSymbol(asset.getKey(), symbols, assets);
            }
        }
        return new TagResult(symbols, assets);
    }

    private void addSymbol(String candidate, Set<String> symbols, Set<String> assets) {
        if (candidate == null) {
            return;
        }
        String symbol = candidate.trim().toUpperCase(Locale.ROOT);
        if (symbol.length() < 2 || COMMON_FALSE_POSITIVES.contains(symbol)) {
            return;
        }
        if (ASSET_NAMES.containsKey(symbol)) {
            symbols.add(symbol);
            assets.add(ASSET_NAMES.get(symbol));
        }
    }

    public record TagResult(Set<String> symbols, Set<String> assets) {
        public TagResult {
            symbols = symbols == null ? Set.of() : Set.copyOf(symbols);
            assets = assets == null ? Set.of() : Set.copyOf(assets);
        }
    }
}
