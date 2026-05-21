package org.investpro.models.currency;

import java.sql.SQLException;
import java.util.List;

/**
 * Provides data for fiat currencies supported by InvestPro.
 *
 * <p>
 * Each currency should have a corresponding image in:
 * {@literal /img/fiat/xxx.png}
 * where {@code xxx} is the ISO-4217 currency code.
 *
 * @author NOEL NGUEMECHIEU
 * @see <a href="https://en.wikipedia.org/wiki/ISO_4217">ISO-4217 Currency Codes</a>
 */
@SuppressWarnings("unused")
public class FiatCurrencyDataProvider extends CurrencyDataProvider {

    public FiatCurrencyDataProvider() {
    }

    @Override
    protected void registerCurrencies() {
        try {
            Currency.registerCurrencies(List.of(
                    new FiatCurrency("Australian dollar", "Australian dollar", "AUD", 2, "A$", "AUD"),
                    new FiatCurrency("Brazilian real", "Real", "BRL", 2, "R$", "BRL"),
                    new FiatCurrency("Canadian dollar", "Canadian dollar", "CAD", 2, "CA$", "CAD"),
                    new FiatCurrency("Swiss franc", "Franc", "CHF", 2, "Fr.", "CHF"),
                    new FiatCurrency("Chinese yuan", "Chinese yuan", "CNY", 2, "¥", "CNY"),
                    new FiatCurrency("Euro", "Euro", "EUR", 2, "€", "EUR"),
                    new FiatCurrency("Pound sterling", "Pound", "GBP", 2, "£", "GBP"),
                    new FiatCurrency("Hong Kong dollar", "Hong Kong dollar", "HKD", 2, "HK$", "HKD"),
                    new FiatCurrency("Indian rupee", "Rupee", "INR", 2, "₹", "INR"),
                    new FiatCurrency("Japanese yen", "Yen", "JPY", 0, "¥", "JPY"),
                    new FiatCurrency("South Korean won", "Won", "KRW", 0, "₩", "KRW"),
                    new FiatCurrency("Kuwaiti dinar", "Dinar", "KWD", 3, "د.ك", "KWD"),
                    new FiatCurrency("Mexican peso", "Mexican peso", "MXN", 2, "MX$", "MXN"),
                    new FiatCurrency("New Zealand dollar", "New Zealand dollar", "NZD", 2, "NZ$", "NZD"),
                    new FiatCurrency("Swedish krona", "Krona", "SEK", 2, "kr", "SEK"),
                    new FiatCurrency("Turkish lira", "Turkish lira", "TRY", 2, "₺", "TRY"),
                    new FiatCurrency("United States dollar", "U.S. dollar", "USD", 2, "$", "USD"),
                    new FiatCurrency("South African rand", "Rand", "ZAR", 2, "R", "ZAR")
            ));
        } catch (SQLException | ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to register fiat currencies", exception);
        }
    }
}
