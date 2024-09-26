package org.investpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;


public class Portfolio {
    private static final Logger logger = LoggerFactory.getLogger(Portfolio.class);
    // A map that holds positions (currency and amount)
    private final Map<Currency, BigDecimal> positions;
    // Cash balance in the portfolio
    private BigDecimal cashBalance;

    public Portfolio(BigDecimal initialCash) {
        this.cashBalance = initialCash;
        this.positions = new HashMap<>();
        logger.debug("Portfolio created with initial cash balance: {}", initialCash);
    }

    // Get the current cash balance
    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    // Add cash to the portfolio
    public void increaseCash(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            cashBalance = cashBalance.add(amount);
            logger.debug("Increased cash by: {}. New balance: {}", amount, cashBalance);
        }
    }

    // Deduct cash from the portfolio
    public void decreaseCash(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0 && cashBalance.compareTo(amount) >= 0) {
            cashBalance = cashBalance.subtract(amount);
            logger.debug("Decreased cash by: {}. New balance: {}", amount, cashBalance);
        } else {
            logger.warn("Insufficient cash balance to decrease by: {}", amount);
        }
    }

    // Add to the position of a specific currency
    public void addPosition(Currency currency, BigDecimal amount) {
        positions.put(currency, positions.getOrDefault(currency, BigDecimal.ZERO).add(amount));
        logger.debug("Added position: {} for currency: {}. New position: {}", amount, currency, positions.get(currency));
    }

    // Subtract from the position of a specific currency
    public void subtractPosition(Currency currency, BigDecimal amount) {
        if (positions.containsKey(currency) && positions.get(currency).compareTo(amount) >= 0) {
            positions.put(currency, positions.get(currency).subtract(amount));
            logger.debug("Subtracted position: {} for currency: {}. New position: {}", amount, currency, positions.get(currency));
        } else {
            logger.warn("Insufficient position to subtract: {} for currency: {}", amount, currency);
        }
    }

    // Get the position of a specific currency
    public BigDecimal getPosition(Currency currency) {
        return positions.getOrDefault(currency, BigDecimal.ZERO);
    }

    // Update the portfolio metrics (e.g., total value, profit and loss)
    public void updateMetrics() {
        logger.debug("Updating portfolio metrics...");

        // Example: Calculate the total value of the portfolio in cash
        BigDecimal totalValue = cashBalance;
        for (Map.Entry<Currency, BigDecimal> entry : positions.entrySet()) {
            Currency currency = entry.getKey();
            BigDecimal amount = entry.getValue();
            BigDecimal price = currency.getCurrentPrice(); // Assume you have a method to get the current price

            totalValue = totalValue.add(amount.multiply(price));
        }

        logger.info("Updated total portfolio value: {}", totalValue);
    }

    // Print out portfolio details (for logging or UI display)
    public void printPortfolioDetails() {
        logger.info("Portfolio Cash Balance: {}", cashBalance);
        for (Map.Entry<Currency, BigDecimal> entry : positions.entrySet()) {
            logger.info("Position for {}: {}", entry.getKey().getCode(), entry.getValue());
        }
    }
}
