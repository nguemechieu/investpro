package org.investpro;

import org.jetbrains.annotations.NotNull;

public class MoneyConverter {
    public MoneyConverter() {
    }

    public double convert(double amount, @NotNull String fromCurrency, String toCurrency) {
        // Implement currency conversion logic here
        if (fromCurrency.equals("USD") && toCurrency.equals("EUR")) {
            return amount * 0.85; // Conversion rate from USD to EUR
        }
        if (fromCurrency.equals("EUR") && toCurrency.equals("USD")) {
            return amount * 1.18; // Conversion rate from EUR to USD
        }
        if (fromCurrency.equals("USD") && toCurrency.equals("GBP")) {
            return amount * 0.75; // Conversion rate from USD to GBP
        }
        if (fromCurrency.equals("GBP") && toCurrency.equals("USD")) {
            return amount * 1.36; // Conversion rate from GBP to USD
        }
        if (fromCurrency.equals("EUR") && toCurrency.equals("GBP")) {
            return amount * 0.90; // Conversion rate from EUR to GBP
        }
        if (fromCurrency.equals("GBP") && toCurrency.equals("EUR")) {
            return amount * 1.10; // Conversion rate from GBP to EUR
        }
        if (fromCurrency.equals("JPY") && toCurrency.equals("USD")) {
            return amount * 0.009; // Conversion rate from JPY to USD
        }
        if (fromCurrency.equals("USD") && toCurrency.equals("JPY")) {
            return amount * 110.5; // Conversion rate from USD to JPY
        }
        if (fromCurrency.equals("EUR") && toCurrency.equals("JPY")) {
            return amount * 111.5; // Conversion rate from EUR to JPY
        }
        if (fromCurrency.equals("JPY") && toCurrency.equals("EUR")) {
            return amount * 0.0089; // Conversion rate from JPY to EUR
        }

        if (fromCurrency.equals("BTC") && toCurrency.equals("USD")) {
            return amount * 40000; // Conversion rate from BTC to USD
            // Note: This is a simplified conversion rate and actual conversion rates can vary
        }
        if (fromCurrency.equals("USD") && toCurrency.equals("BTC")) {
            return amount / 40000; // Conversion rate from USD to BTC
            // Note: This is a simplified conversion rate and actual conversion rates can vary
        }
        if (fromCurrency.equals("BTC") && toCurrency.equals("EUR")) {
            return amount * 40000 * 0.85; // Conversion rate from BTC to EUR
            // Note: This is a simplified conversion rate and actual conversion rates can vary
        }

        if (fromCurrency.equals("EUR") && toCurrency.equals("BTC")) {
            return amount / 40000 / 0.85; // Conversion rate from EUR to BTC
            // Note: This is a simplified conversion rate and actual conversion rates can vary
        }


        // Add more conversion rates as needed (e.g., USD to JPY, EUR to JPY, GBP to JPY)
        return 0; // Placeholder value for demonstration


    }
}
