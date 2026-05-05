package org.investpro.investpro;

import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.models.CandleData;
import org.investpro.investpro.models.Currency;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

@Getter
@Setter
public class Db1 implements Db {

    private static final Logger logger = LoggerFactory.getLogger(Db1.class);
    private static final Properties PROPERTIES = new Properties();
    private static EntityManagerFactory entityManagerFactory;

    protected EntityManager entityManager;
    private Connection conn;
    private String dbName;
    private String userName;
    private String password;
    private String url;
    private PrintWriter printWriter;
    private int loginTimeout;
    private DatabaseConfiguration databaseConfiguration;

    public Db1() {
        loadProperties();
        DatabaseConfiguration requestedConfiguration = DatabaseConfiguration.fromProperties(PROPERTIES);

        if (!initialize(requestedConfiguration) && requestedConfiguration.isCustom()) {
            logger.warn("Falling back to the local embedded database because the configured custom database is unavailable.");
            initialize(DatabaseConfiguration.local(PROPERTIES));
        }
    }

    private boolean initialize(DatabaseConfiguration configuration) {
        closeOpenResources();
        this.databaseConfiguration = configuration;
        this.dbName = configuration.getDescription();
        this.userName = configuration.getUsername();
        this.password = configuration.getPassword();
        this.url = configuration.getJdbcUrl();

        initializeConnection(configuration);
        return initializeEntityManager(configuration);
    }

    private void loadProperties() {
        PROPERTIES.clear();
        PROPERTIES.putAll(AppFiles.loadProperties(InvestPro.CONFIG_FILE, "config.properties"));
        logger.info("Database properties loaded from {}", InvestPro.CONFIG_FILE);
    }

    private void initializeConnection(DatabaseConfiguration configuration) {
        try {
            loadDriverIfNeeded(configuration);
            this.conn = DriverManager.getConnection(
                    configuration.getJdbcUrl(),
                    configuration.getUsername(),
                    configuration.getPassword()
            );
            logger.info("Connected to {}", configuration.getDescription());
        } catch (Exception e) {
            this.conn = null;
            logger.warn("Could not open JDBC connection for {}", configuration.getDescription(), e);
        }
    }

    private boolean initializeEntityManager(DatabaseConfiguration configuration) {
        Properties hibernateProps = new Properties();
        hibernateProps.setProperty("jakarta.persistence.jdbc.url", configuration.getJdbcUrl());
        hibernateProps.setProperty("jakarta.persistence.jdbc.user", configuration.getUsername());
        hibernateProps.setProperty("jakarta.persistence.jdbc.password", configuration.getPassword());
        hibernateProps.setProperty("hibernate.hbm2ddl.auto", "update");
        hibernateProps.setProperty("hibernate.show_sql", "false");
        hibernateProps.setProperty("hibernate.format_sql", "false");

        if (!configuration.getDialect().isBlank()) {
            hibernateProps.setProperty("hibernate.dialect", configuration.getDialect());
        }
        if (!configuration.getDriverClass().isBlank()) {
            hibernateProps.setProperty("jakarta.persistence.jdbc.driver", configuration.getDriverClass());
            hibernateProps.setProperty("hibernate.connection.driver_class", configuration.getDriverClass());
        }

        HashMap<Object, Object> mapper = new HashMap<>(hibernateProps);

        try {
            loadDriverIfNeeded(configuration);
            if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
                entityManagerFactory.close();
            }
            entityManagerFactory = Persistence.createEntityManagerFactory("User", mapper);
            entityManager = entityManagerFactory.createEntityManager();
            logger.info("JPA initialized with {}", configuration.getDescription());
            return true;
        } catch (Exception e) {
            logger.error("Failed to initialize JPA entity manager", e);
            entityManager = null;
            return false;
        }
    }

    private static void loadDriverIfNeeded(DatabaseConfiguration configuration) throws ClassNotFoundException {
        if (!configuration.getDriverClass().isBlank()) {
            Class.forName(configuration.getDriverClass());
        }
    }

    private void closeOpenResources() {
        try {
            if (entityManager != null && entityManager.isOpen()) {
                entityManager.close();
            }
        } catch (Exception e) {
            logger.debug("Ignoring entity manager close error", e);
        }

        try {
            if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
                entityManagerFactory.close();
            }
        } catch (Exception e) {
            logger.debug("Ignoring entity manager factory close error", e);
        }

        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            logger.debug("Ignoring JDBC close error", e);
        }
    }

    public String getDatabaseStatusText() {
        if (databaseConfiguration == null) {
            return "Database: unavailable";
        }
        if (databaseConfiguration.isLocal()) {
            return "Database: local embedded";
        }
        return "Database: custom JDBC";
    }

    public String getDatabaseDescription() {
        if (databaseConfiguration == null) {
            return "Database unavailable";
        }
        if (databaseConfiguration.isLocal()) {
            return databaseConfiguration.getDescription();
        }
        return databaseConfiguration.getDescription() + " (" + databaseConfiguration.getJdbcUrl() + ")";
    }

    @Override
    public void createTables() {
        if (conn == null) {
            logger.warn("Skipping table creation because no JDBC connection is available.");
            return;
        }

        String currenciesSql = "CREATE TABLE IF NOT EXISTS currencies (\n" +
                     "    currency_id VARCHAR(36) PRIMARY KEY,\n" +
                     "    code VARCHAR(10) NOT NULL UNIQUE,\n" +
                     "    currency_type VARCHAR(20) NOT NULL,\n" +
                     "    fractional_digits INT NOT NULL,\n" +
                     "    full_display_name VARCHAR(100) NOT NULL,\n" +
                     "    image VARCHAR(255),\n" +
                     "    short_display_name VARCHAR(50) NOT NULL,\n" +
                     "    symbol VARCHAR(10) NOT NULL\n" +
                     ");\n";

        String marketSymbolsSql = "CREATE TABLE IF NOT EXISTS market_symbols (\n" +
                                  "    exchange_name VARCHAR(80) NOT NULL,\n" +
                                  "    symbol VARCHAR(40) NOT NULL,\n" +
                                  "    updated_at BIGINT NOT NULL,\n" +
                                  "    PRIMARY KEY (exchange_name, symbol)\n" +
                                  ");\n";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(currenciesSql);
            stmt.executeUpdate(marketSymbolsSql);
            logger.info("Tables 'currencies' and 'market_symbols' created or already exist.");
        } catch (SQLException e) {
            logger.error("Error creating table: {}", e.getMessage(), e);
        }
    }

    @Override
    public void dropTables() {
        if (conn == null) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS currencies");
            logger.info("Table 'currencies' dropped successfully.");
        } catch (SQLException e) {
            logger.error("Error dropping table: {}", e.getMessage(), e);
        }
    }

    @Override
    public void truncateTables() {
        if (conn == null) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE currencies");
            logger.info("Table 'currencies' truncated successfully.");
        } catch (SQLException e) {
            logger.error("Error truncating table: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void save(@NotNull Currency currency) {
        if (entityManagerFactory == null || !entityManagerFactory.isOpen()) {
            logger.warn("Skipping currency save because the database is unavailable.");
            return;
        }
        if (currency.getCode() == null || currency.getCode().equals("XXX")) {
            logger.info("Currency code is null or invalid");
            return;
        }

        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            em.setFlushMode(FlushModeType.AUTO);
            tx.begin();
            Currency existingCurrency = findCurrencyByCode(em, currency.getCode());
            if (existingCurrency != null) {
                logger.info("Currency {} already exists. Updating instead.", currency.getCode());
                existingCurrency.setCode(currency.getCode());
                existingCurrency.setCurrencyType(currency.getCurrencyType());
                existingCurrency.setFullDisplayName(currency.getFullDisplayName());
                existingCurrency.setShortDisplayName(currency.getShortDisplayName());
                existingCurrency.setSymbol(currency.getSymbol());
                existingCurrency.setImage(currency.getImage());
                existingCurrency.setFractionalDigits(currency.getFractionalDigits());
            } else {
                logger.info("Saving new currency {}", currency.getCode());
                em.persist(currency);
            }
            tx.commit();
            logger.info("Currency {} saved successfully.", currency.getCode());
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            logger.error("Transaction failed: {}", e.getMessage(), e);
            throw new RuntimeException("Database transaction failed for currency: " + currency.getCode(), e);
        } finally {
            if (em.isOpen()) {
                em.close();
            }
        }
    }

    public Currency getCurrency(String code) {
        if (entityManager == null) {
            return null;
        }
        try {
            return findCurrencyByCode(entityManager, code);
        } catch (Exception e) {
            logger.error("Currency not found: {}", code, e);
            return null;
        }
    }

    private Currency findCurrencyByCode(EntityManager em, String code) {
        TypedQuery<Currency> query = em.createQuery(
                "SELECT c FROM Currency c WHERE c.code = :code",
                Currency.class
        );
        query.setParameter("code", code);
        return query.getResultStream().findFirst().orElse(null);
    }

    @Override
    public void save(CandleData candle) {
        if (entityManagerFactory == null || !entityManagerFactory.isOpen()) {
            logger.warn("Skipping candle save because the database is unavailable.");
            return;
        }

        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.setFlushMode(FlushModeType.AUTO);
            em.persist(candle);
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw new RuntimeException(e);
        } finally {
            if (em.isOpen()) {
                em.close();
            }
        }
    }

    @Override
    public List<String> loadMarketSymbols(String exchangeName) {
        if (conn == null || exchangeName == null || exchangeName.isBlank()) {
            return List.of();
        }

        ensureMarketSymbolsTable();
        String sql = "SELECT symbol FROM market_symbols WHERE exchange_name = ? ORDER BY symbol";
        List<String> symbols = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, exchangeName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String symbol = rs.getString("symbol");
                    if (symbol != null && !symbol.isBlank()) {
                        symbols.add(symbol);
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Unable to load cached market symbols for {}", exchangeName, e);
            return List.of();
        }
        return symbols;
    }

    @Override
    public void saveMarketSymbols(String exchangeName, List<String> symbols) {
        if (conn == null || exchangeName == null || exchangeName.isBlank() || symbols == null || symbols.isEmpty()) {
            return;
        }

        ensureMarketSymbolsTable();
        LinkedHashSet<String> uniqueSymbols = new LinkedHashSet<>();
        for (String symbol : symbols) {
            if (symbol != null && !symbol.isBlank()) {
                uniqueSymbols.add(symbol.trim());
            }
        }
        if (uniqueSymbols.isEmpty()) {
            return;
        }

        long updatedAt = System.currentTimeMillis();
        String deleteSql = "DELETE FROM market_symbols WHERE exchange_name = ?";
        String insertSql = "INSERT INTO market_symbols(exchange_name, symbol, updated_at) VALUES(?, ?, ?)";
        try {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setString(1, exchangeName);
                deleteStmt.executeUpdate();
            }
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (String symbol : uniqueSymbols) {
                    insertStmt.setString(1, exchangeName);
                    insertStmt.setString(2, symbol);
                    insertStmt.setLong(3, updatedAt);
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }
            conn.commit();
            conn.setAutoCommit(previousAutoCommit);
            logger.info("Cached {} market symbols for {}", uniqueSymbols.size(), exchangeName);
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackError) {
                logger.debug("Ignoring market symbol cache rollback error", rollbackError);
            }
            logger.warn("Unable to cache market symbols for {}", exchangeName, e);
        }
    }

    private void ensureMarketSymbolsTable() {
        if (conn == null) {
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS market_symbols (\n" +
                     "    exchange_name VARCHAR(80) NOT NULL,\n" +
                     "    symbol VARCHAR(40) NOT NULL,\n" +
                     "    updated_at BIGINT NOT NULL,\n" +
                     "    PRIMARY KEY (exchange_name, symbol)\n" +
                     ");\n";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            logger.warn("Unable to ensure market_symbols cache table exists", e);
        }
    }

    @Override
    public void close() {
        closeOpenResources();
        logger.info("Database resources closed.");
    }

    @Override
    public int find(String table, String column, String value) {
        if (conn == null) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column + " =?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            logger.error("Error finding data: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<String> findAll(String table, String column, String value) {
        if (conn == null) {
            return List.of();
        }
        String sql = "SELECT * FROM " + table + " WHERE " + column + " =?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                logger.info("Found: {}", rs.getString(column));
            }
        } catch (SQLException e) {
            logger.error("Error finding data: {}", e.getMessage(), e);
        }
        return List.of();
    }

    @Override
    public void update(String table, String column, String value) {
        if (conn == null) {
            return;
        }
        String sql = "UPDATE " + table + " SET " + column + " =? WHERE " + column + " =?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            stmt.setString(2, value);
            stmt.executeUpdate();
            logger.info("Data updated successfully.");
        } catch (SQLException e) {
            logger.error("Error updating data: {}", e.getMessage(), e);
        }
    }

    @Override
    public void insert(String table, String column, String value) {
        if (conn == null) {
            return;
        }
        String sql = "INSERT INTO " + table + "(" + column + ") VALUES(?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            stmt.executeUpdate();
            logger.info("Data inserted successfully.");
        } catch (SQLException e) {
            logger.error("Error inserting data: {}", e.getMessage(), e);
        }
    }

    @Override
    public void delete(String table, String column, String value) {
        if (conn == null) {
            return;
        }
        String sql = "DELETE FROM " + table + " WHERE " + column + " =?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            stmt.executeUpdate();
            logger.info("Data deleted successfully.");
        } catch (SQLException e) {
            logger.error("Error deleting data: {}", e.getMessage(), e);
        }
    }

    @Override
    public void createColumnIfNotExists(String table, String column, String value) {
    }

    @Override
    public void create(String table, String column, String value) {
        insert(table, column, value);
    }

    @Override
    public String getDriverClassName() {
        return databaseConfiguration == null ? "" : databaseConfiguration.getDriverClass();
    }

    @Override
    public String getJdbcUrl() {
        return databaseConfiguration == null ? "" : databaseConfiguration.getJdbcUrl();
    }

    @Override
    public String getJdbcUsername() {
        return databaseConfiguration == null ? "" : databaseConfiguration.getUsername();
    }

    @Override
    public String getJdbcPassword() {
        return databaseConfiguration == null ? "" : databaseConfiguration.getPassword();
    }

    @Override
    public String getJdbcDriverClassName() {
        return getDriverClassName();
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (databaseConfiguration == null) {
            throw new SQLException("Database configuration is unavailable.");
        }
        return DriverManager.getConnection(
                databaseConfiguration.getJdbcUrl(),
                databaseConfiguration.getUsername(),
                databaseConfiguration.getPassword()
        );
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        if (databaseConfiguration == null) {
            throw new SQLException("Database configuration is unavailable.");
        }
        return DriverManager.getConnection(databaseConfiguration.getJdbcUrl(), username, password);
    }

    @Override
    public PrintWriter getLogWriter() {
        return printWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.printWriter = out;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        this.loginTimeout = seconds;
    }

    @Override
    public ConnectionBuilder createConnectionBuilder() throws SQLException {
        return Db.super.createConnectionBuilder();
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Db1 does not expose a JUL parent logger.");
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
    public String findById(String table, String column, String value) {
        if (conn == null) {
            return null;
        }
        String sql = "SELECT * FROM " + table + " WHERE " + column + " =?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString(column);
            }
            logger.warn("No data found in table '{}' for column '{}' with value '{}'.", table, column, value);
            return null;
        } catch (SQLException e) {
            logger.error("Error finding data by ID: {}", e.getMessage(), e);
            return null;
        }
    }
}
