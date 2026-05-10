package org.investpro.models;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import lombok.Data;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.Position;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.time.Instant;
import java.util.*;

/**
 * Broker-neutral account snapshot for InvestPro.
 * <p>
 * Designed to work with:
 * - Coinbase / Coinbase Advanced Trade
 * - Binance US
 * - OANDA
 * - future brokers: Alpaca, Schwab, Interactive Brokers, paper trading
 * <p>
 * Important:
 * This class is a data model. It can safely hold normalized account values
 * from different brokers without forcing every exchange to return the same
 * fields.
 */

//
// Instead of TradeAdviser only asking:
//
// “Should I buy or sell?”
//
// It can now ask:
//
// “What kind of market is this?”
// “What kind of trader/user is operating?”
// “What risk profile is allowed?”
// “What execution style fits liquidity?”
// “How much capital can be exposed?”
// “What protection system should be active?”
// “Is this trade worth taking probabilistically?”
@Getter
@Setter
@Data
@Slf4j
public class Account {
    // ---------------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------------
    private String createdBy = "";
    private String username = "";
    private String accountId = "";
    private String account = "";
    private String brokerName = "";
    private String exchangeId = "";
    private String[] source;
    private String destination = "";
    /**
     * Optional exchange reference.
     * Useful in desktop runtime, but avoid serializing it directly.
     */
    private transient Exchange exchange;

    /**
     * Avoid storing raw passwords long-term.
     * Kept only for compatibility with your current constructor.
     */
    private transient String password = "";

    // ---------------------------------------------------------------------
    // User profile fields
    // ---------------------------------------------------------------------

    private String email = "";
    private String firstName = "";
    private String lastName = "";
    private String phone = "";
    private String address = "";
    private String city = "";

    // ---------------------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------------------

    private String telegramToken = "";
    private String emailNotification = "";

    // ---------------------------------------------------------------------
    // Account financial metrics
    // ---------------------------------------------------------------------

    private String baseCurrency = "USD";

    private double totalBalance;
    private double availableBalance;
    private double lockedBalance;

    private double equity;
    private double nav;
    private double marginUsed;
    private double freeMargin;
    private double marginAvailable;
    private double unrealizedPnl;
    private double realizedPnl;

    private double buyingPower;
    private double cash;
    private double portfolioValue;

    private int openPositionCount;
    private int openOrderCount;

    private boolean sandbox;
    private boolean paperTrading;
    private boolean connected;

    private Instant updatedAt;

    /**
     * Balances by currency.
     * <p>
     * Example:
     * balances["BTC"] = 0.05
     * balances["USD"] = 1000.00
     */
    private Map<String, Double> balances = new LinkedHashMap<>();

    /**
     * Available balances by currency.
     */
    private Map<String, Double> availableBalances = new LinkedHashMap<>();

    /**
     * Locked/hold balances by currency.
     */
    private Map<String, Double> lockedBalances = new LinkedHashMap<>();

    /**
     * Extra broker-specific fields.
     * <p>
     * Example:
     * OANDA: marginRate, hedgingEnabled
     * Coinbase: uuid, profileId
     * Binance US: canTrade, canWithdraw
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public Account() {
        this.updatedAt = Instant.now();
    }

    public Account(@NotNull Exchange exchange, String username, String password) {
        this();

        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.username = safe(username);
        this.password = safe(password);
        this.account = this.username;

        hydrateFromExchange(exchange);

        log.info(
                "Account created broker={} username={} accountId={}",
                brokerName,
                this.username,
                accountId);
    }

    /**
     * Preferred constructor when the exchange already fetched account info.
     */
    public Account(@NotNull Exchange exchange, Account source) {
        this();

        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");

        hydrateFromExchange(exchange);

        if (source != null) {
            copyFrom(source);
        }
    }

    @Contract(" -> new")
    public static @NotNull Account empty() {
        return new Account();
    }

    public static @NotNull Account coinbase(String accountId, String baseCurrency) {
        Account account = new Account();
        account.setBrokerName("Coinbase");
        account.setExchangeId("coinbase");
        account.setAccountId(accountId);
        account.setAccount(accountId);
        account.setBaseCurrency(baseCurrency);
        return account;
    }

    public static @NotNull Account oanda(String accountId, String baseCurrency) {
        Account account = new Account();
        account.setBrokerName("OANDA");
        account.setExchangeId("oanda");
        account.setAccountId(accountId);
        account.setAccount(accountId);
        account.setBaseCurrency(baseCurrency);
        return account;
    }

    private void hydrateFromExchange(@NotNull Exchange exchange) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");

        try {
            this.brokerName = safe(exchange.getDisplayName()).isBlank()
                    ? safe(exchange.getName())
                    : safe(exchange.getDisplayName());
        } catch (Exception exception) {
            this.brokerName = safe(exchange.getName());
        }

        try {
            this.exchangeId = safe(exchange.getExchangeId());
        } catch (Exception exception) {
            this.exchangeId = safe(exchange.getName()).toLowerCase().replace(" ", "_");
        }

        try {
            this.telegramToken = safe(exchange.getTelegramToken());
        } catch (Exception exception) {
            this.telegramToken = "";
        }

        try {
            this.emailNotification = safe(exchange.getEmailNotification());
        } catch (Exception exception) {
            this.emailNotification = "";
        }

        try {
            this.sandbox = exchange.isSandbox();
        } catch (Exception exception) {
            this.sandbox = false;
        }

        try {
            this.paperTrading = exchange.isPaperTrading();
        } catch (Exception exception) {
            this.paperTrading = false;
        }

        try {
            this.connected = Boolean.TRUE.equals(exchange.isConnected());
        } catch (Exception exception) {
            this.connected = false;
        }

        this.updatedAt = Instant.now();
    }

    public void copyFrom(Account source) {
        if (source == null) {
            return;
        }

        this.username = safe(source.username);
        this.accountId = safe(source.accountId);
        this.account = safe(source.account);
        this.brokerName = safe(source.brokerName);
        this.exchangeId = safe(source.exchangeId);

        this.email = safe(source.email);
        this.firstName = safe(source.firstName);
        this.lastName = safe(source.lastName);
        this.phone = safe(source.phone);
        this.address = safe(source.address);
        this.city = safe(source.city);

        this.telegramToken = safe(source.telegramToken);
        this.emailNotification = safe(source.emailNotification);

        this.baseCurrency = safe(source.baseCurrency).isBlank() ? "USD" : safe(source.baseCurrency);

        this.totalBalance = sanitize(source.totalBalance);
        this.availableBalance = sanitize(source.availableBalance);
        this.lockedBalance = sanitize(source.lockedBalance);

        this.equity = sanitize(source.equity);
        this.nav = sanitize(source.nav);
        this.marginUsed = sanitize(source.marginUsed);
        this.freeMargin = sanitize(source.freeMargin);
        this.marginAvailable = sanitize(source.marginAvailable);
        this.unrealizedPnl = sanitize(source.unrealizedPnl);
        this.realizedPnl = sanitize(source.realizedPnl);

        this.buyingPower = sanitize(source.buyingPower);
        this.cash = sanitize(source.cash);
        this.portfolioValue = sanitize(source.portfolioValue);

        this.openPositionCount = Math.max(0, source.openPositionCount);
        this.openOrderCount = Math.max(0, source.openOrderCount);

        this.sandbox = source.sandbox;
        this.paperTrading = source.paperTrading;
        this.connected = source.connected;

        setBalances(source.balances);
        setAvailableBalances(source.availableBalances);
        setLockedBalances(source.lockedBalances);
        setMetadata(source.metadata);

        this.updatedAt = source.updatedAt == null ? Instant.now() : source.updatedAt;
    }

    // ---------------------------------------------------------------------
    // Balance helpers
    // ---------------------------------------------------------------------

    public void setBalance(String currencyCode, double amount) {
        String code = normalizeCurrency(currencyCode);

        if (code.isBlank()) {
            return;
        }

        balances.put(code, sanitize(amount));
        recalculateBalanceTotals();
    }

    public Map<String, Double> balancesView() {
        return Collections.unmodifiableMap(balances);
    }

    public void recalculateBalanceTotals() {
        this.totalBalance = balances.values()
                .stream()
                .filter(Double::isFinite)
                .mapToDouble(Double::doubleValue)
                .sum();

        this.availableBalance = availableBalances.values()
                .stream()
                .filter(Double::isFinite)
                .mapToDouble(Double::doubleValue)
                .sum();

        this.lockedBalance = lockedBalances.values()
                .stream()
                .filter(Double::isFinite)
                .mapToDouble(Double::doubleValue)
                .sum();

        if (equity <= 0 && totalBalance > 0) {
            equity = totalBalance + unrealizedPnl;
        }

        if (nav <= 0 && equity > 0) {
            nav = equity;
        }

        if (freeMargin <= 0 && marginAvailable > 0) {
            freeMargin = marginAvailable;
        }

        updatedAt = Instant.now();
    }

    // ---------------------------------------------------------------------
    // Broker-specific normalizers
    // ---------------------------------------------------------------------

    /**
     * Useful for Coinbase/Binance US.
     */

    public Object getMetadata(String key) {
        return metadata.get(safe(key));
    }

    // ---------------------------------------------------------------------
    // Setters with sanitization
    // ---------------------------------------------------------------------

    public void setUsername(String username) {
        this.username = safe(username);
    }

    public void setAccountId(String accountId) {
        this.accountId = safe(accountId);

        if (this.account.isBlank()) {
            this.account = this.accountId;
        }
    }

    public void setAccount(String account) {
        this.account = safe(account);

        if (this.accountId.isBlank()) {
            this.accountId = this.account;
        }
    }

    public void setBrokerName(String brokerName) {
        this.brokerName = safe(brokerName);
    }

    public void setExchangeId(String exchangeId) {
        this.exchangeId = safe(exchangeId);
    }

    public void setPassword(String password) {
        this.password = safe(password);
    }

    public void setEmail(String email) {
        this.email = safe(email);
    }

    public void setAddress(String address) {
        this.address = safe(address);
    }

    public void setTelegramToken(String telegramToken) {
        this.telegramToken = safe(telegramToken);
    }

    public void setBaseCurrency(String baseCurrency) {
        String code = normalizeCurrency(baseCurrency);
        this.baseCurrency = code.isBlank() ? "USD" : code;
    }

    public void setTotalBalance(double totalBalance) {
        this.totalBalance = sanitize(totalBalance);
        this.updatedAt = Instant.now();
    }

    public void setAvailableBalance(double availableBalance) {
        this.availableBalance = sanitize(availableBalance);
        this.updatedAt = Instant.now();
    }

    public void setEquity(double equity) {
        this.equity = sanitize(equity);
        this.updatedAt = Instant.now();
    }

    public void setNav(double nav) {
        this.nav = sanitize(nav);
        this.updatedAt = Instant.now();
    }

    public void setMarginUsed(double marginUsed) {
        this.marginUsed = sanitize(marginUsed);
        this.updatedAt = Instant.now();
    }

    public void setFreeMargin(double freeMargin) {
        this.freeMargin = sanitize(freeMargin);
        this.updatedAt = Instant.now();
    }

    public void setMarginAvailable(double marginAvailable) {
        this.marginAvailable = sanitize(marginAvailable);
        this.updatedAt = Instant.now();
    }

    public void setUnrealizedPnl(double unrealizedPnl) {
        this.unrealizedPnl = sanitizeSigned(unrealizedPnl);
        this.updatedAt = Instant.now();
    }

    public void setRealizedPnl(double realizedPnl) {
        this.realizedPnl = sanitizeSigned(realizedPnl);
        this.updatedAt = Instant.now();
    }

    public void setBuyingPower(double buyingPower) {
        this.buyingPower = sanitize(buyingPower);
        this.updatedAt = Instant.now();
    }

    public void setCash(double cash) {
        this.cash = sanitize(cash);
        this.updatedAt = Instant.now();
    }

    public void setOpenPositionCount(int openPositionCount) {
        this.openPositionCount = Math.max(0, openPositionCount);
        this.updatedAt = Instant.now();
    }

    public void setOpenOrderCount(int openOrderCount) {
        this.openOrderCount = Math.max(0, openOrderCount);
        this.updatedAt = Instant.now();
    }

    public void setBalances(Map<String, Double> balances) {
        this.balances = cleanBalanceMap(balances);
        this.updatedAt = Instant.now();
    }

    public void setAvailableBalances(Map<String, Double> availableBalances) {
        this.availableBalances = cleanBalanceMap(availableBalances);
        this.updatedAt = Instant.now();
    }

    public void setLockedBalances(Map<String, Double> lockedBalances) {
        this.lockedBalances = cleanBalanceMap(lockedBalances);
        this.updatedAt = Instant.now();
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(metadata);

        this.updatedAt = Instant.now();
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    // ---------------------------------------------------------------------
    // Display helpers
    // ---------------------------------------------------------------------

    public String displayName() {
        String fullName = "%s %s".formatted(firstName, lastName).trim();

        if (!fullName.isBlank()) {
            return fullName;
        }

        if (!username.isBlank()) {
            return username;
        }

        if (!email.isBlank()) {
            return email;
        }

        if (!accountId.isBlank()) {
            return accountId;
        }

        return brokerName.isBlank() ? "Account" : "%s Account".formatted(brokerName);
    }

    public String summary() {
        return """
                Account Summary
                ------------------------------
                Broker: %s
                Exchange ID: %s
                Account ID: %s
                User: %s
                Base Currency: %s
                Connected: %s
                Sandbox: %s
                Paper Trading: %s

                Total Balance: %.8f
                Available Balance: %.8f
                Locked Balance: %.8f
                Equity: %.8f
                NAV: %.8f
                Margin Used: %.8f
                Free Margin: %.8f
                Margin Available: %.8f
                Unrealized PnL: %.8f
                Realized PnL: %.8f
                Buying Power: %.8f
                Cash: %.8f
                Portfolio Value: %.8f
                Open Positions: %d
                Open Orders: %d

                Balances: %s
                Available: %s
                Locked: %s

                Updated At: %s
                """.formatted(
                brokerName,
                exchangeId,
                accountId,
                displayName(),
                baseCurrency,
                connected,
                sandbox,
                paperTrading,
                totalBalance,
                availableBalance,
                lockedBalance,
                equity,
                nav,
                marginUsed,
                freeMargin,
                marginAvailable,
                unrealizedPnl,
                realizedPnl,
                buyingPower,
                cash,
                portfolioValue,
                openPositionCount,
                openOrderCount,
                balances,
                availableBalances,
                lockedBalances,
                updatedAt);
    }

    @Override
    public String toString() {
        return summary();
    }

    // ---------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------

    @Contract(pure = true)
    private static @NotNull String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static @NotNull String normalizeCurrency(String value) {
        return safe(value).toUpperCase();
    }

    private static double sanitize(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return 0.0;
        }

        return value;
    }

    private static double sanitizeSigned(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return value;
    }

    private static @NotNull Map<String, Double> cleanBalanceMap(Map<String, Double> source) {
        Map<String, Double> cleaned = new LinkedHashMap<>();

        if (source == null || source.isEmpty()) {
            return cleaned;
        }

        for (Map.Entry<String, Double> entry : source.entrySet()) {
            String code = normalizeCurrency(entry.getKey());

            if (code.isBlank()) {
                continue;
            }

            cleaned.put(code, sanitize(entry.getValue() == null ? 0.0 : entry.getValue()));
        }

        return cleaned;
    }

    private double leverage;

    public Map<String, Double> getBalance() {
        return balances;
    }

    public double getRealizedPnlToday() {
        return realizedPnl;
    }

    public double dailyLoss;

    public double maxDailyLoss;

    /**
     * Open positions in this account.
     * Maps symbol/pair to Position.
     */
    private Map<String, Position> positions = new LinkedHashMap<>();

    // =====================================================================
    // Trading capability methods (for PreTradeValidationEngine)
    // =====================================================================

    /**
     * Check if trading is enabled on this account.
     * Returns true if account is connected and trading is not explicitly disabled.
     * Paper trading and sandbox accounts are considered trading-enabled.
     *
     * @return true if account can submit trades
     */
    public boolean isTradingEnabled() {
        // Account can trade if connected and not explicitly disabled
        // Paper trading and sandbox accounts can trade
        return connected;
    }

    /**
     * Get open positions in this account.
     * Returns an immutable collection of Position objects.
     *
     * @return Collection of open positions, never null (empty if no positions)
     */
    @NotNull
    public Collection<Position> getOpenPositions() {
        return positions == null ? Collections.emptyList() : Collections.unmodifiableCollection(positions.values());
    }

    /**
     * Add or update a position in this account.
     *
     * @param symbol   the symbol/pair
     * @param position the position
     */
    public void setPosition(@NotNull String symbol, @NotNull Position position) {
        Objects.requireNonNull(symbol, "symbol cannot be null");
        Objects.requireNonNull(position, "position cannot be null");

        if (positions == null) {
            positions = new LinkedHashMap<>();
        }

        positions.put(symbol, position);
        this.updatedAt = Instant.now();
    }

    /**
     * Get a specific position by symbol.
     *
     * @param symbol the symbol/pair
     * @return the position or null if not found
     */
    @Nullable
    public Position getPosition(@NotNull String symbol) {
        Objects.requireNonNull(symbol, "symbol cannot be null");
        return positions == null ? null : positions.get(symbol);
    }

    /**
     * Remove a position from this account.
     *
     * @param symbol the symbol/pair
     */
    public void removePosition(@NotNull String symbol) {
        Objects.requireNonNull(symbol, "symbol cannot be null");

        if (positions != null) {
            positions.remove(symbol);
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Clear all positions from this account.
     */
    public void clearPositions() {
        if (positions != null) {
            positions.clear();
            this.updatedAt = Instant.now();
        }
    }
}
