package org.investpro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Currency implements Comparable<Currency> {
    protected static String image;
    CurrencyType currencyType;
    String fullDisplayName;
    String shortDisplayName;
    protected String code;
    protected int fractionalDigits;
    protected static String symbol;
    static final Map<SymmetricPair<String, CurrencyType>, Currency> CURRENCIES = new ConcurrentHashMap<>();
    static final CryptoCurrency NULL_CRYPTO_CURRENCY = new NullCryptoCurrency(
            CurrencyType.NULL,
            "XXX",
            "xxx",
            "", 0,
            "",
            "xx"


    );
     static final FiatCurrency NULL_FIAT_CURRENCY = new NullFiatCurrency(

             "",
             "",
             "xxx",
             0,
             "",
             "xx"
     ) {

        @Override
        public int compareTo(java.util.@NotNull Currency o) {
            return 0;
        }
    };
    static Db1 db1;
    static double current_price;
    private static final Logger logger = LoggerFactory.getLogger(Currency.class);
    static double market_cap;
    static int market_cap_rank;
    static double fully_diluted_valuation;


     static double market_cap_change_percentage_24h  ;

     static double market_cap_change_24h  ;
     static String id;
     static String name;
    static double total_volume;
    static double high_24h;
    static double low_24h;
    static double price_change_24h;
    static double price_change_percentage_24h;

    static {
        Properties conf1 = new Properties();
        try {
            conf1.load(
                    Currency.class.getClassLoader().getResourceAsStream("config.properties")
            );
        } catch (IOException e) {
            new Message(
                    Message.MessageType.ERROR,
                    "could not load database parameters\n" + e.getMessage()
            );

        }


        try {
            db1 = new Db1(conf1);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static {


        //Register all currencies
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=false"));

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
//            "id": "bitcoin",
//                    "symbol": "btc",
//                    "name": "Bitcoin",
//                    "image": "https://assets.coingecko.com/coins/images/1/large/bitcoin.png?1547033579",
//                    "current_price": 27246,
//                    "market_cap": 528549112440,
//                    "market_cap_rank": 1,
//                    "fully_diluted_valuation": 572400437376,
//                    "total_volume": 13905042181,
//                    "high_24h": 27271,
//                    "low_24h": 26716,
//                    "price_change_24h": 425.4,
//                    "price_change_percentage_24h": 1.58606,
//                    "market_cap_change_24h": 8537812174,
//                    "market_cap_change_percentage_24h": 1.64185,
//                    "circulating_supply": 19391200,
//                    "total_supply": 21000000,
//                    "max_supply": 21000000,
//                    "ath": 69045,
//                    "ath_change_percentage": -60.53243,
//                    "ath_date": "2021-11-10T14:24:11.849Z",
//                    "atl": 67.81,
//                    "atl_change_percentage": 40086.83652,
//                    "atl_date": "2013-07-06T00:00:00.000Z",
//                    "roi": null,
//                    "last_updated": "2023-06-02T20:02:20.534Z"

            CryptoCurrencyDataProvider cryptoCurrencyDataProvider = null;
            try {
                cryptoCurrencyDataProvider = new CryptoCurrencyDataProvider();
            } catch (SQLException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            cryptoCurrencyDataProvider.registerCurrencies();

            FiatCurrencyDataProvider fiatCurrencyDataProvider = new FiatCurrencyDataProvider();

            fiatCurrencyDataProvider.registerCurrencies();


            if (response.statusCode() == 200) {
                JsonNode rates = new ObjectMapper().readTree(response.body());
                logger.info("found " + rates.size() + " currencies");
                logger.info("response " + response);

                for (JsonNode rate : rates) {


                    id = rate.get("id").asText();
                    symbol = rate.get("symbol").asText();
                    name = rate.get("name").asText();
                    image = rate.get("image").asText();
                    current_price = rate.get("current_price").asDouble();
                    market_cap = rate.get("market_cap").asDouble();
                    market_cap_rank = rate.get("market_cap_rank").asInt();
                    fully_diluted_valuation = rate.get("fully_diluted_valuation").asDouble();
                    total_volume = rate.get("total_volume").asDouble();
                    high_24h = rate.get("high_24h").asDouble();
                    low_24h = rate.get("low_24h").asDouble();
                    price_change_24h = rate.get("price_change_24h").asDouble();
                    price_change_percentage_24h = rate.get("price_change_percentage_24h").asDouble();
                    market_cap_change_24h = rate.get("market_cap_change_24h").asDouble();
                    market_cap_change_percentage_24h = rate.get("market_cap_change_percentage_24h").asDouble();
                    double circulating_supply = rate.get("circulating_supply").asDouble(),
                            total_supply = rate.get("total_supply").asDouble();
                    double max_supply = rate.get("max_supply").asDouble();
                    double ath = rate.get("ath").asDouble();
                    double ath_change_percentage = rate.get("ath_change_percentage").asDouble();
                    String ath_date = rate.get("ath_date").asText();
                    double atl = rate.get("atl").asDouble();
                    double atl_change_percentage = rate.get("atl_change_percentage").asDouble();
                    String atl_date = rate.get("atl_date").asText();
                    String roi = rate.get("roi").asText();
                    String last_updated = rate.get("last_updated").asText();

                    logger.info("found " + id + " " + symbol + " " + name + " " +
                            image + " " + current_price + " " + market_cap + " " + market_cap_rank + " " + fully_diluted_valuation + " " + total_volume + " " + high_24h + " " + low_24h + " " + price_change_24h + " " + price_change_percentage_24h + " " + market_cap_change_24h + " " + market_cap_change_percentage_24h + " " + circulating_supply + " " + total_supply + " " + max_supply + " " + ath + " " + ath_change_percentage + " " + ath_date + " " + atl + " " + atl_change_percentage + " " + atl_date + " " + roi + " " + last_updated);

                    Currency currency = new Currency(
                            CurrencyType.CRYPTO,
                            rate.get("name").asText(),
                            rate.get("id").asText(),
                            rate.get("symbol").asText(),
                            8,
                            rate.get("symbol").asText(),

                            rate.get("image").asText()

                    ) {
                        @Override
                        public int compareTo(java.util.@NotNull Currency o) {
                            return 0;
                        }
                    };

                    db1.save(currency);

                    CURRENCIES.put(SymmetricPair.of(currency.code, currency.currencyType), currency);
                }

                for (java.util.Currency cur : java.util.Currency.getAvailableCurrencies()) {
                    Currency currency = CURRENCIES.get(SymmetricPair.of(cur.getCurrencyCode(), CurrencyType.FIAT));
                    if (currency == null && CURRENCIES.get(SymmetricPair.of(cur.getCurrencyCode(), CurrencyType.CRYPTO)) == null) {
                        currency = new Currency(
                                CurrencyType.FIAT,
                                cur.getDisplayName(),
                                cur.getCurrencyCode(),
                                cur.getSymbol(),
                                (Math.max(cur.getDefaultFractionDigits(), 0)),
                                cur.getSymbol(),

                                ""

                        ) {
                            @Override
                            public int compareTo(java.util.@NotNull Currency o) {
                                return 0;
                            }
                        };
                        CURRENCIES.put(SymmetricPair.of(cur.getCurrencyCode(), CurrencyType.FIAT), currency);

                        logger.info("found " + currency.code + " " + name);

                        db1.save(currency);
                    }
                }


                logger.info("loaded " + CURRENCIES.size() + " currencies");
                logger.info(
                        "currencies: " + CURRENCIES.keySet().stream().map(SymmetricPair::toString)
                );
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }


    }
    /**
     * Private constructor used only for the {@code NULL_CURRENCY}.
     */
    protected Currency() {
        this.currencyType = CurrencyType.NULL;
        this.fullDisplayName = "xxx";
        this.shortDisplayName = "xxx";
        this.code = "***";
        this.fractionalDigits = 0;
        symbol = "xxx";
        image = "xxx";

    }
    /**
     * Protected constructor, called only by CurrencyDataProvider's.
     */
    protected Currency(CurrencyType currencyType, String fullDisplayName, String shortDisplayName, String code,
                       int fractionalDigits, String symbol) {
        Objects.requireNonNull(currencyType, "currencyType must not be null");
        Objects.requireNonNull(fullDisplayName, "fullDisplayName must not be null");
        Objects.requireNonNull(shortDisplayName, "shortDisplayName must not be null");
        Objects.requireNonNull(code, "code must not be null");

        if (fractionalDigits < 0) {
            throw new IllegalArgumentException("fractional digits must be non-negative, was: " + fractionalDigits);
        }
        Objects.requireNonNull(symbol, "symbol must not be null");

        this.currencyType = currencyType;
        this.fullDisplayName = fullDisplayName;
        this.shortDisplayName = shortDisplayName;
        this.code = code;
        this.fractionalDigits = fractionalDigits;
        Currency.symbol = symbol;

    }

    public Currency(CurrencyType currencyType, String fullDisplayName, String shortDisplayName, String code, int fractionalDigits, String symbol, String image) {
        this(currencyType, fullDisplayName, shortDisplayName, code, fractionalDigits, symbol);

        Objects.requireNonNull(currencyType, "currencyType must not be null");
        Objects.requireNonNull(fullDisplayName, "fullDisplayName must not be null");
        Objects.requireNonNull(shortDisplayName, "shortDisplayName must not be null");
        Objects.requireNonNull(code, "code must not be null");

        if (fractionalDigits < 0) {
            throw new IllegalArgumentException("fractional digits must be non-negative, was: " + fractionalDigits);
        }
        Objects.requireNonNull(symbol, "symbol must not be null");
        this.currencyType = currencyType;
        this.fullDisplayName = fullDisplayName;
        this.shortDisplayName = shortDisplayName;
        this.code = code;
        this.fractionalDigits = fractionalDigits;
        Currency.symbol = symbol;
        Currency.image = image;

    }

    public Currency(CurrencyType fiat, String s, String s1, String mxn, int i, String $, Locale es, String s2, int i1) {
        this.currencyType = fiat;
        symbol = s;
        name = s1;
        image = mxn;
        current_price = i;
        market_cap = i1;
        market_cap_rank = 0;
        fully_diluted_valuation = 0;
        total_volume = 0;
        high_24h = 0;
        low_24h = 0;
        price_change_24h = 0;
        price_change_percentage_24h = 0;
        market_cap_change_24h = 0;
        market_cap_change_percentage_24h = 0;

    }

    protected static void registerCurrency(Currency currency) {
        Objects.requireNonNull(currency, "currency must not be null");
        CURRENCIES.put(SymmetricPair.of(currency.code, currency.currencyType), currency);
        logger.info("found " + currency.code);
        db1.save(currency);
    }

    public static Currency of(String code) throws SQLException, ClassNotFoundException {

        Objects.requireNonNull(code, "code must not be null");
        if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.FIAT))
                && CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.CRYPTO))) {
            logger.error("ambiguous currency code: " + code);
            throw new IllegalArgumentException("ambiguous currency code: " + code + " (code" +
                    " is used for multiple currency types); use ofCrypto(...) or ofFiat(...) instead");
        } else {
            if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.CRYPTO))) {
                return CURRENCIES.get(SymmetricPair.of(code, CurrencyType.CRYPTO));
            } else if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.FIAT))) {
                return CURRENCIES.getOrDefault(SymmetricPair.of(code, CurrencyType.FIAT), NULL_CRYPTO_CURRENCY);
            } else {
                logger.error("unknown currency code: " + code);
                logger.error("known codes: " + CURRENCIES.keySet());
                logger.info("Trying to fetch from database");
                //Loading database parameters
                if (
                        CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.CRYPTO))) {
                    return CURRENCIES.get(SymmetricPair.of(code, CurrencyType.CRYPTO));
                } else if (
                        CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.FIAT))
                ) {
                    return CURRENCIES.getOrDefault(SymmetricPair.of(code, CurrencyType.FIAT), NULL_FIAT_CURRENCY);
                } else if (db1.getCurrency(code) != null

                ) {
                    logger.error("could not fetch from database");


                    return db1.getCurrency(code);
                } else {
                    logger.error("could not fetch from database");
                    //Trying registering currency
                    Currency currency = new Currency(
                            CurrencyType.CRYPTO,
                            code,
                            code,
                            code,
                            8,
                            code,

                            ""

                    ) {
                        @Override
                        public int compareTo(java.util.@NotNull Currency o) {
                            return 0;
                        }
                    };
                    //  CURRENCIES.put(SymmetricPair.of(code, CurrencyType.FIAT), currency);
                    CURRENCIES.put(SymmetricPair.of(code, CurrencyType.CRYPTO), currency);
                    db1.save(currency);
                    return currency;


                }


            }
        }

    }

    public static void registerCurrencies(Collection<Currency> currencies) {
        Objects.requireNonNull(currencies, "currencies must not be null");
        currencies.forEach(Currency::registerCurrency);
    }

    /**
     * Get the fiat currency that has a currency code equal to the
     * given {@code}. Using {@literal "¤¤¤"} as the currency code
     * returns {@literal NULL_FIAT_CURRENCY}.
     */
    public static Currency ofFiat(@NotNull String code) {
        if (code.equals("¤¤¤") || code.equals("����") || code.equals("")) {
            return NULL_FIAT_CURRENCY;
        }

        return CURRENCIES.get(SymmetricPair.of(code, CurrencyType.FIAT));
    }


    public static CryptoCurrency ofCrypto(@NotNull String code) {
        if (code.equals("¤¤¤")) {
            return NULL_CRYPTO_CURRENCY;
        }

        CryptoCurrency result = (CryptoCurrency) CURRENCIES.get(SymmetricPair.of(code, CurrencyType.CRYPTO));
        return result == null ? NULL_CRYPTO_CURRENCY : result;
    }

    public static List<FiatCurrency> getFiatCurrencies() {
        return CURRENCIES.values().stream()
                .filter(currency -> currency.getCurrencyType() == CurrencyType.FIAT)
                .map(currency -> (FiatCurrency) currency).toList();
    }

    public static Currency lookupBySymbol(String symbol) {

        return CURRENCIES.values().stream().filter(currency -> currency.getSymbol().equals(symbol))
                .findAny().orElse(NULL_FIAT_CURRENCY);
    }

    public static FiatCurrency lookupFiatByCode(String code) {
        return (FiatCurrency) CURRENCIES.values().stream()
                .filter(currency -> currency.currencyType == CurrencyType.FIAT && currency.code.equals(code))
                .findAny().orElse(NULL_FIAT_CURRENCY);
    }

    public static FiatCurrency lookupLocalFiatCurrency() {
        return (FiatCurrency) CURRENCIES.values().stream()
                .filter(currency -> currency.currencyType == CurrencyType.FIAT)
                .findAny().orElse(NULL_FIAT_CURRENCY);
    }

    public static String valueOf(String currency) {
        return lookupBySymbol(currency).getCode();
    }

    public CurrencyType getCurrencyType() {
        return this.currencyType;
    }

    public String getFullDisplayName() {
        return this.fullDisplayName;
    }

    public String getShortDisplayName() {
        return this.shortDisplayName;
    }

    public String getCode() {
        return this.code;
    }

    public int getFractionalDigits() {
        return this.fractionalDigits;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * The finality of {@code equals(...)} ensures that the equality
     * contract for subclasses must be based on currency type and code alone.
     *
     */
    @Override
    public final boolean equals(Object object) {
        if (object == null) {
            return false;
        }

        if (!(object instanceof Currency other)) {
            return false;
        }

        if (object == this) {
            return true;
        }

        return currencyType == other.currencyType && code.equals(other.code);
    }

    /**
     * The finality of {@code hashCode()} ensures that the equality
     * contract for subclasses must be based on currency
     * type and code alone.
     *
     */
    @Override
    public final int hashCode() {
        return Objects.hash(currencyType, code);
    }

    @Override
    public String toString() {
        if (this == NULL_CRYPTO_CURRENCY) {
            return "the null cryptocurrency";
        } else if (this == NULL_FIAT_CURRENCY) {
            return "the null fiat currency";
        }
        return String.format("%s (%s)", fullDisplayName, code);
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        Currency.image = image;
    }


    public abstract int compareTo(java.util.@NotNull Currency o);

    @Override
    public int compareTo(@NotNull Currency o) {
        return 0;
    }


    private static class NullCryptoCurrency extends CryptoCurrency {
        protected NullCryptoCurrency(CurrencyType currencyType, String fullDisplayName, String shortDisplayName, String code, int fractionalDigits, String symbol, String image) {
            super(fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, image);
        }

        @Override
        public int compareTo(java.util.@NotNull Currency o) {
            return 0;
        }
    }

    private static abstract class NullFiatCurrency extends FiatCurrency {
        protected NullFiatCurrency(String fullDisplayName, String shortDisplayName, String code, int fractionalDigits, String symbol, String image) {
            super(
                    fullDisplayName,
                    shortDisplayName,
                    code,
                    fractionalDigits,
                    symbol, Locale.US, "NO BANK",
                    0,
                    image
            );
        }
    }
}
