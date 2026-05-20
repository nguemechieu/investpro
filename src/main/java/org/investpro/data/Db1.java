package org.investpro.data;

import lombok.extern.slf4j.Slf4j;

import org.investpro.models.currency.CryptoCurrency;
import org.investpro.models.currency.Currency;
import org.investpro.models.currency.CurrencyType;
import org.investpro.models.currency.FiatCurrency;

import org.investpro.models.trading.Order;

import org.investpro.utils.SymmetricPair;
import org.jetbrains.annotations.NotNull;
import java.io.PrintWriter;
import java.sql.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j

public class Db1 implements Db {
    private Connection conn;
    private final Map<String, Currency> currencyCache = new ConcurrentHashMap<>();
    private static final int BUSY_TIMEOUT_MS = 10000; // 10 seconds
    private static final String PRAGMA_BUSY_TIMEOUT = "PRAGMA busy_timeout = %d;";
    private static final String PRAGMA_WAL_MODE = "PRAGMA journal_mode = WAL;";

    public Db1(@NotNull Properties conf) throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        initializeConnection(conf);
        createTables();
        preloadCurrencies();
    }

    private void initializeConnection(@NotNull Properties conf) {
        try {
            String databaseFile = conf.getProperty("sqlite_db_file", "investpro_db.sql");
            String connectionUrl = "jdbc:sqlite:%s".formatted(databaseFile);

            // Create connection with journal mode settings
            this.conn = DriverManager.getConnection(connectionUrl);

            if (conn != null) {
                // Enable WAL mode for concurrent access
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(PRAGMA_WAL_MODE);
                    log.info("SQLite WAL mode enabled for better concurrent access");
                }

                // Set busy timeout so SQLite retries instead of failing immediately
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(PRAGMA_BUSY_TIMEOUT.formatted(BUSY_TIMEOUT_MS));
                    log.info("SQLite busy timeout set to {} ms", BUSY_TIMEOUT_MS);
                }

                // Enable auto-commit with proper transaction handling
                conn.setAutoCommit(true);
                log.info("Database connection initialized for {}", databaseFile);
            }
        } catch (SQLException e) {
            log.error("Error connecting to the database: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize database connection", e);
        }
    }

    @Override
    public void createTables() {
        if (conn == null) {
            log.warn("Database connection is null, cannot create tables");
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS currencies ( " +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "currency_type VARCHAR(255), " +
                    "code VARCHAR(255), " +
                    "full_display_name VARCHAR(255), " +
                    "short_display_name VARCHAR(255), " +
                    "fractional_digits INTEGER, " +
                    "symbol VARCHAR(255), " +
                    "image VARCHAR(255) " +
                    ")";
            stmt.executeUpdate(sql);
            ensureCurrencyColumns();
        } catch (SQLException e) {
            log.error("Error creating the database tables: {}", e.getMessage(), e);
        }
    }

    @Override
    public void dropTables() {
        if (conn == null) {
            log.warn("Database connection is null, cannot drop tables");
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS currencies");
            stmt.execute("DROP TABLE IF EXISTS trades");
            stmt.execute("DROP TABLE IF EXISTS orders");
            stmt.execute("DROP TABLE IF EXISTS account_balances");
            log.info("All tables dropped successfully");
        } catch (SQLException e) {
            log.error("Error dropping tables: {}", e.getMessage(), e);
        }
    }

    @Override
    public void truncateTables() {
        if (conn == null) {
            log.warn("Database connection is null, cannot truncate tables");
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM currencies");
            stmt.execute("DELETE FROM trades");
            stmt.execute("DELETE FROM orders");
            stmt.execute("DELETE FROM account_balances");
            log.info("All tables truncated successfully");
        } catch (SQLException e) {
            log.error("Error truncating tables: {}", e.getMessage(), e);
        }
    }

    @Override
    public void insertData() {
        log.debug("insertData() called - use insert(table, column, value) method instead");
    }

    @Override
    public void updateData() {
        log.debug("updateData() called - use update(table, column, value) method instead");
    }

    @Override
    public void deleteData() {
        log.debug("deleteData() called - use truncateTables() to clear all data");
    }

    @Override
    public void createIndexes() {
        if (conn == null) {
            log.warn("Database connection is null, cannot create indexes");
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_currency_code ON currencies(code)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_trades_timestamp ON trades(timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_orders_symbol ON orders(symbol)");
            log.info("Indexes created successfully");
        } catch (SQLException e) {
            log.error("Error creating indexes: {}", e.getMessage(), e);
        }
    }

    @Override
    public void dropIndexes() {
        if (conn == null) {
            log.warn("Database connection is null, cannot drop indexes");
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP INDEX IF EXISTS idx_currency_code");
            stmt.execute("DROP INDEX IF EXISTS idx_trades_timestamp");
            stmt.execute("DROP INDEX IF EXISTS idx_orders_symbol");
            log.info("Indexes dropped successfully");
        } catch (SQLException e) {
            log.error("Error dropping indexes: {}", e.getMessage(), e);
        }
    }

    @Override
    public void truncateIndexes() {
        // SQLite doesn't require explicit truncate for indexes - they're automatically
        // updated
        log.debug("truncateIndexes() - SQLite indexes are automatically managed");
    }

    @Override
    public void createConstraints() {
        // SQLite constraints are created with table definitions
        log.debug("createConstraints() - constraints are defined at table creation");
    }

    @Override
    public void dropConstraints() {
        // SQLite doesn't support dropping individual constraints
        // Would require table recreation
        log.debug("dropConstraints() - cannot drop constraints in SQLite without recreating tables");
    }

    @Override
    public void close() {
        if (conn != null) {
            try {
                conn.close();
                log.info("Database connection closed");
            } catch (SQLException e) {
                log.error("Error closing database connection: {}", e.getMessage(), e);
            }
        }
    }

    private String dbName = "investpro_db.sql";
    private String userName = "";
    private String password = "";
    private String url = "";

    @Override
    public String getDbName() {
        return dbName;
    }

    @Override
    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public int find(String table, String column, String value) {
        int da = 0;
        if (conn == null) {
            log.warn("Database connection is null, cannot find data");
            return da;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?")) {
            stmt.setString(1, value);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    da = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.error("Error finding the data: {}", e.getMessage(), e);
        }
        return da;
    }

    @Override
    public void findAll(String table, String column, String value) {
        if (conn == null) {
            log.warn("Database connection is null, cannot find data");
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM " + table + " WHERE " + column + " = ?")) {
            stmt.setString(1, value);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    log.debug("Found record: {}", rs);
                }
            }
        } catch (SQLException e) {
            log.error("Error finding all the data: {}", e.getMessage(), e);
        }
    }

    @Override
    public void update(String table, String column, String value) {
        if (conn == null) {
            log.warn("Database connection is null, cannot update data");
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE " + table + " SET " + column + " = ?")) {
            stmt.setString(1, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error updating the database: {}", e.getMessage(), e);
        }
    }

    @Override
    public void insert(String table, String column, String value) {
        if (conn == null) {
            log.warn("Database connection is null, cannot insert data");
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + table + " (" + column + ") VALUES (?)")) {
            stmt.setString(1, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error inserting the database: {}", e.getMessage(), e);
        }
    }

    @Override
    public void delete(String table, String column, String value) {
        if (conn == null) {
            log.warn("Database connection is null, cannot delete data");
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM " + table + " WHERE " + column + " = ?")) {
            stmt.setString(1, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Error deleting the database: {}", e.getMessage(), e);
        }
    }

    @Override
    public void create(String table, String column, String value) {
        if (conn == null) {
            log.warn("Database connection is null, cannot create table");
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " (" + column + " VARCHAR(255))");
        } catch (SQLException e) {
            log.error("Error creating the database table: {}", e.getMessage(), e);
        }
    }

    @Override
    public void findById(String table, String column, String value) {
        if (conn == null) {
            log.warn("Database connection is null, cannot find data by id");
            return;
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT * FROM " + table + " WHERE " + column + " = ?")) {
            stmt.setString(1, value);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    log.info("Found: {}", rs.getString(column));
                }
            }
        } catch (SQLException e) {
            log.error("Error finding the database: {}", e.getMessage(), e);
        }
    }

    @Override
    public Connection getConnection() {
        return conn;
    }

    @Override
    public Connection getConnection(String username, String password) {
        return conn;
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {

    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public ConnectionBuilder createConnectionBuilder() throws SQLException {
        return Db.super.createConnectionBuilder();
    }

    @Override
    public void setLoginTimeout(int seconds) {

    }

    @Override
    public java.util.logging.Logger getParentLogger() {
        return null;
    }

    @Override
    public ShardingKeyBuilder createShardingKeyBuilder() throws SQLException {
        return Db.super.createShardingKeyBuilder();
    }

    @Override
    public <T> T unwrap(Class<T> iface) {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }

    @Override
    public void save(@NotNull Currency currency) {
        if (conn == null) {
            log.warn("Database connection is null, cannot save currency");
            return;
        }
        try {
            createTables();
            try (PreparedStatement existing = conn.prepareStatement(
                    "SELECT 1 FROM currencies WHERE code = ? AND currency_type = ?")) {
                existing.setString(1, currency.getCode());
                existing.setString(2, currency.getCurrencyType().name());

                try (ResultSet rs = existing.executeQuery()) {
                    if (rs.next()) {
                        log.info("Currency already exists with code: {}", currency.getCode());
                        return;
                    }
                }
            }

            try (PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO currencies (currency_type, code, full_display_name, short_display_name, fractional_digits, symbol, image) "
                            +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, currency.getCurrencyType().name());
                insert.setString(2, currency.getCode());
                insert.setString(3, currency.getFullDisplayName());
                insert.setString(4, currency.getShortDisplayName());
                insert.setInt(5, currency.getFractionalDigits());
                insert.setString(6, currency.getSymbol());
                insert.setString(7, currency.getImage());
                insert.executeUpdate();

                CurrencyType type = currency.getCurrencyType();
                Currency.CURRENCIES.put(new SymmetricPair<>(currency.getCode(), type), currency);
                currencyCache.put(normalizeCurrencyCode(currency.getCode()), currency);

                log.info("New Currency with code: {} was added to the database", currency.getCode());
            }
        } catch (SQLException e) {
            log.error("Error saving currency: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public Currency getCurrency(String code) throws SQLException {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Currency code must not be blank");
        }

        createTables();
        String normalizedCode = normalizeCurrencyCode(code);

        Currency cached = currencyCache.get(normalizedCode);
        if (cached != null) {
            return cached;
        }

        Currency fromDb = queryCurrencyByCode(normalizedCode);
        if (fromDb != null) {
            currencyCache.put(normalizedCode, fromDb);
            return fromDb;
        }

        try {
            CurrencyType type = determineCurrencyType(normalizedCode);
            int fractionalDigits = determineFractionalDigits(normalizedCode, type);
            Currency created = createCurrencyByType(type, normalizedCode, fractionalDigits);
            save(created);
            currencyCache.put(normalizedCode, created);
            log.info("Created new currency: {} (type: {}, fractional_digits: {})", normalizedCode, type, fractionalDigits);
            return created;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Currency queryCurrencyByCode(String code) throws SQLException {
        String sql = "SELECT * FROM currencies WHERE code = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                String fullDisplayName = resultSet.getString("full_display_name");
                String shortDisplayName = resultSet.getString("short_display_name");
                int fractionalDigits = resultSet.getInt("fractional_digits");
                String symbol = resultSet.getString("symbol");
                String image = resultSet.getString("image");
                String currencyType = resultSet.getString("currency_type");

                log.debug("Currency found with code: {}", code);

                CurrencyType type = CurrencyType.valueOf(currencyType);
                return new Currency(type, fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, image) {
                };
            }
        }
    }

    private String normalizeCurrencyCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public void preloadCurrencies() {
        List<String> common = List.of(
                "USD", "EUR", "GBP", "JPY",
                "BTC", "ETH", "XLM", "USDC", "USDT",
                "SOL", "BNB", "XRP");

        for (String code : common) {
            try {
                getCurrency(code);
            } catch (Exception exception) {
                log.debug("Unable to preload currency {}", code, exception);
            }
        }
    }

    /**
     * Determine currency type based on currency code
     */
    private CurrencyType determineCurrencyType(String code) {
        if (code == null || code.isEmpty()) {
            return CurrencyType.CRYPTO;
        }

        String upperCode = code.toUpperCase();

        // FIAT currencies (forex)
        if (isFiatCurrency(upperCode)) {
            return CurrencyType.FIAT;
        }

        // Default to crypto for other codes (including stocks, since STOCK type doesn't
        // exist)
        return CurrencyType.CRYPTO;
    }

    /**
     * Check if code is a FIAT currency
     */
    private boolean isFiatCurrency(String code) {
        return switch (code) {
            // Major currencies
            case "USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD" -> true;
            // Other major fiat
            case "SGD", "HKD", "CNY", "INR", "BRL", "MXN", "ZAR", "RUB" -> true;
            // Additional fiat
            case "SEK", "NOK", "DKK", "PLN", "CZK", "HUF", "RON", "BGN" -> true;
            // More fiat
            case "TRY", "THB", "MYR", "PHP", "IDR", "KRW" -> true;
            default -> false;
        };
    }

    /**
     * Determine fractional digits based on currency code and type
     */
    private int determineFractionalDigits(String code, CurrencyType type) {
        if (code == null) {
            return 0;
        }

        return switch (type) {
            case FIAT -> {
                // Most fiat currencies have 2 decimal places
                // except JPY (0) and some crypto-fiat pairs
                String upper = code.toUpperCase();
                yield "JPY".equals(upper) ? 0 : 2;
            }
            case CRYPTO -> 8; // Crypto and stocks typically have 8 decimal places
            case NULL -> 0; // Default to 0 for NULL type
        };
    }

    /**
     * Create appropriate currency object based on type
     */
    private Currency createCurrencyByType(CurrencyType type, String code, int fractionalDigits)
            throws ClassNotFoundException, SQLException {
        return switch (type) {
            case FIAT -> new FiatCurrency(code, code, code, fractionalDigits, code, code);
            case CRYPTO -> new CryptoCurrency(code, code, code, fractionalDigits, code, code);
            // Default to CRYPTO for NULL type
            case NULL -> new CryptoCurrency(code, code, code, fractionalDigits, code, code);
        };
    }

    private void ensureCurrencyColumns() throws SQLException {
        addCurrencyColumnIfMissing("currency_type", "VARCHAR(255)");
        addCurrencyColumnIfMissing("code", "VARCHAR(255)");
        addCurrencyColumnIfMissing("full_display_name", "VARCHAR(255)");
        addCurrencyColumnIfMissing("short_display_name", "VARCHAR(255)");
        addCurrencyColumnIfMissing("fractional_digits", "INTEGER");
        addCurrencyColumnIfMissing("symbol", "VARCHAR(255)");
        addCurrencyColumnIfMissing("image", "VARCHAR(255)");
    }

    private void addCurrencyColumnIfMissing(String columnName, String columnType) throws SQLException {
        if (currencyColumnExists(columnName)) {
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE currencies ADD COLUMN " + columnName + " " + columnType);
            log.info("Added missing currencies.{} column", columnName);
        }
    }

    private boolean currencyColumnExists(String columnName) throws SQLException {
        try (Statement stmt = conn.createStatement();
                ResultSet columns = stmt.executeQuery("PRAGMA table_info(currencies)")) {
            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Save an order to the database.
     * Creates the orders table if it doesn't exist.
     * 
     * @param order the order to save
     * @throws SQLException if database operation fails
     */
    public void saveOrder(@NotNull Order order) throws SQLException {
        try {
            // Create table if not exists
            String createTableSql = "CREATE TABLE IF NOT EXISTS orders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "symbol VARCHAR(255) NOT NULL, " +
                    "type VARCHAR(50), " +
                    "quantity REAL NOT NULL, " +
                    "price REAL NOT NULL, " +
                    "commission REAL, " +
                    "take_profit REAL, " +
                    "stop_loss REAL, " +
                    "swap REAL, " +
                    "profit REAL, " +
                    "status VARCHAR(50), " +
                    "created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            conn.createStatement().executeUpdate(createTableSql);

            // Insert order
            String insertSql = "INSERT INTO orders (symbol, type, quantity, price, commission, take_profit, stop_loss, swap, profit, status) "
                    +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(insertSql);
            pstmt.setString(1, order.getSymbol());
            pstmt.setString(2, order.getType());
            pstmt.setDouble(3, order.getQuantity());
            pstmt.setDouble(4, order.getPrice());
            pstmt.setDouble(5, order.getCommission());
            pstmt.setDouble(6, order.getTakeProfit());
            pstmt.setDouble(7, order.getStopLoss());
            pstmt.setDouble(8, order.getSwap());
            pstmt.setDouble(9, order.getProfit());
            pstmt.setString(10, order.getStatus() != null ? order.getStatus() : "PENDING");
            pstmt.executeUpdate();
            log.info("Order saved: symbol=%s, type=%s".formatted(order.getSymbol(), order.getType()));
        } catch (SQLException e) {
            log.error("Error saving order: %s".formatted(e.getMessage()), e);
            throw e;
        }
    }

    /**
     * Retrieve an order by ID.
     * 
     * @param orderId the order ID
     * @return the Order, or null if not found
     * @throws SQLException if database operation fails
     */
    public Order getOrder(String orderId) throws SQLException {
        try {
            String sql = "SELECT * FROM orders WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, orderId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToOrder(rs);
            }
        } catch (SQLException e) {
            log.error("Error retrieving order: %s".formatted(e.getMessage()), e);
            throw e;
        }
        return null;
    }

    /**
     * Get all orders by trading symbol.
     * 
     * @param symbol the trading symbol (e.g., "BTC-USD")
     * @return list of orders for the symbol
     * @throws SQLException if database operation fails
     */
    public java.util.List<Order> getOrdersBySymbol(String symbol) throws SQLException {
        java.util.List<Order> orders = new java.util.ArrayList<>();
        try {
            String sql = "SELECT * FROM orders WHERE symbol = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, symbol);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                orders.add(mapResultSetToOrder(rs));
            }
            log.info("Found %d orders for symbol: %s".formatted(orders.size(), symbol));
        } catch (SQLException e) {
            log.error("Error retrieving orders by symbol: %s".formatted(e.getMessage()), e);
            throw e;
        }
        return orders;
    }

    /**
     * Get all orders with a specific status.
     * 
     * @param status the order status (e.g., "OPEN", "FILLED", "CANCELLED")
     * @return list of orders with the specified status
     * @throws SQLException if database operation fails
     */
    public java.util.List<Order> getOrdersByStatus(String status) throws SQLException {
        java.util.List<Order> orders = new java.util.ArrayList<>();
        try {
            String sql = "SELECT * FROM orders WHERE status = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, status);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                orders.add(mapResultSetToOrder(rs));
            }
            log.info("Found %d orders with status: %s".formatted(orders.size(), status));
        } catch (SQLException e) {
            log.error("Error retrieving orders by status: %s".formatted(e.getMessage()), e);
            throw e;
        }
        return orders;
    }

    /**
     * Get all orders within a time range.
     * 
     * @param startTime the start time
     * @param endTime   the end time
     * @return list of orders within the time range
     * @throws SQLException if database operation fails
     */
    public java.util.List<Order> getOrdersByTimeRange(java.time.Instant startTime, java.time.Instant endTime)
            throws SQLException {
        java.util.List<Order> orders = new java.util.ArrayList<>();
        try {
            String sql = "SELECT * FROM orders WHERE created_date BETWEEN ? AND ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setTimestamp(1, java.sql.Timestamp.from(startTime));
            pstmt.setTimestamp(2, java.sql.Timestamp.from(endTime));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                orders.add(mapResultSetToOrder(rs));
            }
            log.info("Found %d orders in time range".formatted(orders.size()));
        } catch (SQLException e) {
            log.error("Error retrieving orders by time range: %s".formatted(e.getMessage()), e);
            throw e;
        }
        return orders;
    }

    /**
     * Get all open orders (not filled or cancelled).
     * 
     * @return list of open orders
     * @throws SQLException if database operation fails
     */
    public java.util.List<Order> getOpenOrders() throws SQLException {
        java.util.List<Order> orders = new java.util.ArrayList<>();
        try {
            String sql = "SELECT * FROM orders WHERE status != 'FILLED' AND status != 'CANCELLED'";
            ResultSet rs = conn.createStatement().executeQuery(sql);

            while (rs.next()) {
                orders.add(mapResultSetToOrder(rs));
            }
            log.info("Found %d open orders".formatted(orders.size()));
        } catch (SQLException e) {
            log.error("Error retrieving open orders: %s".formatted(e.getMessage()), e);
            throw e;
        }
        return orders;
    }

    /**
     * Get count of all open orders.
     * 
     * @return number of open orders
     * @throws SQLException if database operation fails
     */
    public long getOpenOrdersCount() throws SQLException {
        try {
            String sql = "SELECT COUNT(*) FROM orders WHERE status != 'FILLED' AND status != 'CANCELLED'";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.error("Error counting open orders: " + e.getMessage(), e);
            throw e;
        }
        return 0;
    }

    /**
     * Get count of all orders.
     * 
     * @return number of orders
     * @throws SQLException if database operation fails
     */
    public long getOrdersCount() throws SQLException {
        try {
            String sql = "SELECT COUNT(*) FROM orders";
            ResultSet rs = conn.createStatement().executeQuery(sql);
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.error("Error counting orders: " + e.getMessage(), e);
            throw e;
        }
        return 0;
    }

    /**
     * Delete an order by ID.
     * 
     * @param orderId the order ID
     * @return true if deleted, false otherwise
     * @throws SQLException if database operation fails
     */
    public boolean deleteOrder(String orderId) throws SQLException {
        try {
            String sql = "DELETE FROM orders WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, orderId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            log.error("Error deleting order: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Update order status.
     * 
     * @param orderId   the order ID
     * @param newStatus the new status
     * @throws SQLException if database operation fails
     */
    public void updateOrderStatus(String orderId, String newStatus) throws SQLException {
        try {
            String sql = "UPDATE orders SET status = ? WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, newStatus);
            pstmt.setString(2, orderId);
            pstmt.executeUpdate();
            log.info("Order %s status updated to: %s".formatted(orderId, newStatus));
        } catch (SQLException e) {
            log.error("Error updating order status: %s".formatted(e.getMessage()), e);
            throw e;
        }
    }

    /**
     * Helper method to map a ResultSet row to an Orders object.
     * 
     * @param rs the ResultSet
     * @return the Orders object
     * @throws SQLException if column access fails
     */
    private Order mapResultSetToOrder(ResultSet rs) throws SQLException {
        Order order = new Order(
                rs.getTimestamp("created_date") != null ? new java.util.Date(rs.getTimestamp("created_date").getTime())
                        : new java.util.Date(),
                rs.getString("type"),
                rs.getString("symbol"),
                rs.getDouble("quantity"),
                rs.getDouble("price"),
                rs.getDouble("commission"),
                rs.getDouble("take_profit"),
                rs.getDouble("stop_loss"),
                rs.getDouble("swap"),
                rs.getDouble("profit"));
        order.setStatus(rs.getString("status"));
        return order;
    }

    // ========== Symbol Configuration Methods ==========

    /**
     * Create trading_symbols table for symbol configuration
     */
    public void createSymbolConfigTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS trading_symbols (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "exchange VARCHAR(50) NOT NULL, " +
                "symbol_code VARCHAR(50) NOT NULL, " +
                "enabled INTEGER DEFAULT 1, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(exchange, symbol_code)" +
                ")";
        conn.createStatement().executeUpdate(sql);
        conn.createStatement().executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_symbol_exchange ON trading_symbols(exchange)");
        log.info("Symbol config table created");
    }

    /**
     * Create symbol_selection table for user selections
     */
    public void createSymbolSelectionTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS symbol_selection (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "exchange VARCHAR(50) NOT NULL, " +
                "symbol_code VARCHAR(50) NOT NULL, " +
                "user_selection INTEGER DEFAULT 1, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        conn.createStatement().executeUpdate(sql);
        log.info("Symbol selection table created");
    }

    // COMMENTED OUT - SymbolService class not found in codebase
    // /**
    // * Insert or ignore a symbol configuration
    // */
    // public void insertSymbolConfig(SymbolService.SymbolConfig config) throws
    // SQLException {
    // String sql = "INSERT OR IGNORE INTO trading_symbols (exchange, symbol_code,
    // enabled) VALUES (?, ?, ?)";
    // PreparedStatement pstmt = conn.prepareStatement(sql);
    // pstmt.setString(1, config.getExchange());
    // pstmt.setString(2, config.getSymbolCode());
    // pstmt.setInt(3, config.isEnabled() ? 1 : 0);
    // pstmt.executeUpdate();
    // }
    //
    // /**
    // * Get all symbol configs for an exchange
    // */
    // public java.util.List<SymbolService.SymbolConfig> getSymbolConfigs(String
    // exchange)
    // throws SQLException {
    // java.util.List<SymbolService.SymbolConfig> configs = new
    // java.util.ArrayList<>();
    // String sql = "SELECT * FROM trading_symbols WHERE exchange = ? ORDER BY
    // symbol_code";
    // PreparedStatement pstmt = conn.prepareStatement(sql);
    // pstmt.setString(1, exchange);
    // ResultSet rs = pstmt.executeQuery();
    //
    // while (rs.next()) {
    // SymbolService.SymbolConfig config = new SymbolService.SymbolConfig(
    // rs.getString("symbol_code"),
    // rs.getString("exchange"),
    // rs.getString("symbol_code"),
    // rs.getInt("enabled") == 1,
    // rs.getTimestamp("created_at").getTime());
    // configs.add(config);
    // }
    // return configs;
    // }
    //
    // /**
    // * Get enabled symbol configs for an exchange
    // */
    // public java.util.List<SymbolService.SymbolConfig>
    // getEnabledSymbolConfigs(String exchange)
    // throws SQLException {
    // java.util.List<SymbolService.SymbolConfig> configs = new
    // java.util.ArrayList<>();
    // String sql = "SELECT * FROM trading_symbols WHERE exchange = ? AND enabled =
    // 1 ORDER BY symbol_code";
    // PreparedStatement pstmt = conn.prepareStatement(sql);
    // pstmt.setString(1, exchange);
    // ResultSet rs = pstmt.executeQuery();
    //
    // while (rs.next()) {
    // SymbolService.SymbolConfig config = new SymbolService.SymbolConfig(
    // rs.getString("symbol_code"),
    // rs.getString("exchange"),
    // rs.getString("symbol_code"),
    // true,
    // rs.getTimestamp("created_at").getTime());
    // configs.add(config);
    // }
    // return configs;
    // }
    //
    // /**
    // * Get a specific symbol config
    // */
    // public SymbolService.SymbolConfig getSymbolConfig(String exchange, String
    // symbolCode)
    // throws SQLException {
    // String sql = "SELECT * FROM trading_symbols WHERE exchange = ? AND
    // symbol_code = ?";
    // PreparedStatement pstmt = conn.prepareStatement(sql);
    // pstmt.setString(1, exchange);
    // pstmt.setString(2, symbolCode);
    // ResultSet rs = pstmt.executeQuery();
    //
    // if (rs.next()) {
    // return new SymbolService.SymbolConfig(
    // rs.getString("symbol_code"),
    // rs.getString("exchange"),
    // rs.getString("symbol_code"),
    // rs.getInt("enabled") == 1,
    // rs.getTimestamp("created_at").getTime());
    // }
    // return null;
    // }

    /**
     * Update symbol enabled status
     */
    public void updateSymbolEnabled(String exchange, String symbolCode, boolean enabled) throws SQLException {
        String sql = "UPDATE trading_symbols SET enabled = ? WHERE exchange = ? AND symbol_code = ?";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setInt(1, enabled ? 1 : 0);
        pstmt.setString(2, exchange);
        pstmt.setString(3, symbolCode);
        pstmt.executeUpdate();
    }

    /**
     * Update all symbols enabled status for an exchange
     */
    public void updateAllSymbolsEnabled(String exchange, boolean enabled) throws SQLException {
        String sql = "UPDATE trading_symbols SET enabled = ? WHERE exchange = ?";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setInt(1, enabled ? 1 : 0);
        pstmt.setString(2, exchange);
        pstmt.executeUpdate();
    }

    /**
     * Count enabled symbols for an exchange
     */
    public int countEnabledSymbols(String exchange) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM trading_symbols WHERE exchange = ? AND enabled = 1";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, exchange);
        ResultSet rs = pstmt.executeQuery();
        return rs.next() ? rs.getInt("count") : 0;
    }

    /**
     * Count total symbols for an exchange
     */
    public int countTotalSymbols(String exchange) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM trading_symbols WHERE exchange = ?";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, exchange);
        ResultSet rs = pstmt.executeQuery();
        return rs.next() ? rs.getInt("count") : 0;
    }
}
