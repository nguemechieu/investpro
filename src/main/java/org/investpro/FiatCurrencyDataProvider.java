package org.investpro;

import java.util.List;

/**
 * Provides data for fiat currencies (actual paper money currencies provided by
 * various state governments) that we support. Each currency has a corresponding
 * image in {@literal /img/fiat/xxx.png} where xxx is the ISO-4217 currency code.
 *
 * @author Michael Ennen
 * @see <a href="http://en.wikipedia.org/wiki/ISO_4217">ISO-4217 Currency Codes</a>
 */
public class FiatCurrencyDataProvider extends CurrencyDataProvider {

    public FiatCurrencyDataProvider() {}

    @Override
    protected void registerCurrencies() {
        Currency.registerCurrencies(List.of(
                new FiatCurrency("Australian dollar", "Australian dollar", "AUD", 2, "AUD$", "AUD$"),
                new FiatCurrency("Brazilian real", "Real", "BRL", 2, "R$", "R$"),
                new FiatCurrency("Canadian dollar", "Canadian dollar", "CAD", 2, "$", "CA$"),
                new FiatCurrency("Swiss franc", "Franc", "CHF", 2, "Fr.",

                        "Fr."),
                new FiatCurrency("Canadian franc", "Franc", "CAD", 2, "CA$.", "CA$"),

                new FiatCurrency("Chinese yuan", "Chinese yuan", "CNY", 2, "¥", "¥"),
                new FiatCurrency("Euro", "Euro", "EUR", 2, "€", ""),
                new FiatCurrency("Pound sterling", "Pound", "GBP", 2, "£", ""),
                new FiatCurrency("Hong Kong dollar", "Hong Kong dollar", "HKD", 2, "$", "HK$"),
                new FiatCurrency("Indian rupee", "Rupee", "INR", 2, "₹", "��"),
                new FiatCurrency("Japanese yen", "Yen", "JPY", 0, "¥", "¥"),
                new FiatCurrency("South Korean won", "Won", "KRW", 0, "₩", ""),
                new FiatCurrency("Kuwaiti dinar", "Dinar", "KWD", 3, "ك.د",
                        "د.ك"),
                new FiatCurrency("Mexican peso", "Mexican peso", "MXN", 2, "$", "MX$"),
                new FiatCurrency("New Zealand dollar", "New Zealand dollar", "NZD", 2, "$"
                        , "NZ$"),
                new FiatCurrency("Swedish krona", "Krona", "SEK", 2, "kr",
                        "kr"),
                new FiatCurrency("Turkish lira", "Turkish lira", "TRY", 2, "₺"
                        , "TRY$"),
                new FiatCurrency("United States dollar", "U.S. dollar", "USD", 2, "$", "$"),
                new FiatCurrency("South African rand", "Rand", "ZAR", 2, "R"
                        , "R")));
    }
}
