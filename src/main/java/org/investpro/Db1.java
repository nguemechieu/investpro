package org.investpro;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Properties;

class Db1 implements Db {
    private static final Logger logger = LoggerFactory.getLogger(Db1.class);
     String fullDisplayName;
    Connection conn;

    String dbName;

    public Db1() throws Exception {
        Class.forName("org.sqlite.JDBC");
        try {
            Properties conf = new Properties();
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("InvestPro.properties")) {
                if (inputStream == null) {
                    throw new FileNotFoundException("Property file 'InvestPro.properties' not found in the classpath");
                }
                conf.load(inputStream);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
            this.fullDisplayName = conf.getProperty("fullDisplayName");
            this.dbName = conf.getProperty("dbName");

            // Correct the connection string
            this.conn = DriverManager.getConnection(String.format("jdbc:sqlite:%s", dbName));
            if (conn != null) {
                logger.info("Connected to database: {}", conf.getProperty("dbName"));
                createTables();
            }
        } catch (SQLException e) {
            logger.error("Error connecting to the database", e);
        }
    }

    @Override
    public Connection getConn() {
        return conn;
    }

    @Override
    public void createTables() {
        try {
            String[] tableCreationQueries = {
                    "CREATE TABLE IF NOT EXISTS currencies (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "currency_type VARCHAR(255), " +
                            "full_display_name VARCHAR(255), " +
                            "short_display_name VARCHAR(255), " +
                            "code VARCHAR(255), " +
                            "fractional_digits INTEGER, " +
                            "symbol VARCHAR(255), " +
                            "image VARCHAR(255));",
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "username VARCHAR(255) UNIQUE, " +
                            "password VARCHAR(255), " +
                            "email VARCHAR(255) UNIQUE, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);",
                    "CREATE TABLE IF NOT EXISTS portfolio_items (" +
                            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "user_id INTEGER, " +
                            "currency_id INTEGER, " +
                            "quantity DECIMAL(20,10), " +
                            "cost_basis DECIMAL(20,10), " +
                            "purchase_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (user_id) REFERENCES users(_id), " +
                            "FOREIGN KEY (currency_id) REFERENCES currencies(_id));",
                    "CREATE TABLE IF NOT EXISTS transactions (" +
                            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "user_id INTEGER, " +
                            "currency_id INTEGER, " +
                            "quantity DECIMAL(20,10), " +
                            "transaction_type VARCHAR(255), " +
                            "price DECIMAL(20,10), " +
                            "transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (user_id) REFERENCES users(_id), " +
                            "FOREIGN KEY (currency_id) REFERENCES currencies(_id));",
                    "CREATE TABLE IF NOT EXISTS watchlists (" +
                            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "user_id INTEGER, " +
                            "currency_id INTEGER, " +
                            "FOREIGN KEY (user_id) REFERENCES users(_id), " +
                            "FOREIGN KEY (currency_id) REFERENCES currencies(_id));",
                    "CREATE TABLE IF NOT EXISTS candle_data (" +
                            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "currency_id INTEGER, " +
                            "open DECIMAL(20,10), " +
                            "high DECIMAL(20,10), " +
                            "low DECIMAL(20,10), " +
                            "close DECIMAL(20,10), " +
                            "volume DECIMAL(20,10), " +
                            "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (currency_id) REFERENCES currencies(_id));",
                    "CREATE TABLE IF NOT EXISTS trade_signals (" +
                            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "currency_id INTEGER, " +
                            "signal_type VARCHAR(255), " +
                            "signal_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (currency_id) REFERENCES currencies(_id));",
                    "CREATE TABLE IF NOT EXISTS trade_logs (" +
                            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "currency_id INTEGER, " +
                            "trade_type VARCHAR(255), " +
                            "trade_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "FOREIGN KEY (currency_id) REFERENCES currencies(_id));"
            };

            for (String query : tableCreationQueries) {
                logger.info(query);
                conn.createStatement().executeUpdate(query);
            }

        } catch (SQLException e) {
            logger.error("Error creating the database tables", e);
        }
    }

    @Override
    public void dropTables() {
        try {
            String sql = "DROP TABLE IF EXISTS currencies";
            logger.info(sql);
            this.conn.createStatement().executeUpdate(sql);
        } catch (SQLException e) {
            logger.error("Error dropping the database tables", e);
        }
    }

    @Override
    public void truncateTables() {
        try {
            String sql = "DELETE FROM currencies";
            logger.info(sql);
            this.conn.createStatement().executeUpdate(sql);
        } catch (SQLException e) {
            logger.error("Error truncating the database tables", e);
        }
    }

    @Override
    public void insertData() {
        try {
            String sql = "INSERT INTO currencies (full_display_name, short_display_name, fractional_digits, symbol, image) VALUES (?,?,?,?,?)";
            PreparedStatement stmt = this.conn.prepareStatement(sql);
            stmt.setString(1, "Bitcoin");
            stmt.setString(2, "BTC");
            stmt.setInt(3, 8);
            stmt.setString(4, "$");
            stmt.setString(5, "bitcoin.png");
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error inserting data into the database", e);
        }
    }

    @Override
    public void updateData() {
        
    }

    @Override
    public void deleteData() {

    }

    @Override
    public void createIndexes() {

    }

    @Override
    public void dropIndexes() {

    }

    @Override
    public void truncateIndexes() {

    }

    @Override
    public void createConstraints() {

    }

    @Override
    public void dropConstraints() {

    }

    @Override
    public void close() {

    }

    @Override
    public String getDbName() {
        return "";
    }

    @Override
    public void setDbName(String dbName) {

    }

    @Override
    public String getUserName() {
        return "";
    }

    @Override
    public void setUserName(String userName) {

    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public void setPassword(String password) {

    }

    @Override
    public String getUrl() {
        return "";
    }

    @Override
    public void setUrl(String url) {

    }

    @Override
    public String getDriverClassName() {
        return "";
    }

    @Override
    public String getJdbcUrl() {
        return "";
    }

    @Override
    public String getJdbcUsername() {
        return "";
    }

    @Override
    public String getJdbcPassword() {
        return "";
    }

    @Override
    public String getJdbcDriverClassName() {
        return "";
    }

    @Override
    public int find(String table, String column, String value) {
        return 0;
    }

    @Override
    public void findAll(String table, String column, String value) {

    }

    @Override
    public void update(String table, String column, String value) {

    }

    @Override
    public void insert(String table, String column, String value) {

    }

    @Override
    public void delete(String table, String column, String value) {

    }

    @Override
    public void create(String table, String column, String value) {

    }

    @Override
    public void findById(String table, String column, String value) {

    }

    @Override
    public void save(ArrayList<Currency> currency) throws SQLException {

    }

    // Other methods...

    @Override
    public Currency getCurrency(String code) throws SQLException {
        Currency newCurrency;
        String query = "SELECT * FROM currencies WHERE code = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, code);
            ResultSet check = stmt.executeQuery();

            if (!check.next()) {
                logger.info("Currency not found with code: {}", code);

                // Create a new CryptoCurrency instance
                CryptoCurrency cur = new CryptoCurrency(code, code, code, 0, code, code);

                String insertQuery = "INSERT INTO currencies (currency_type, full_display_name, short_display_name, code, fractional_digits, symbol, image) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                    insertStmt.setString(1, "CRYPTO");
                    insertStmt.setString(2, code);
                    insertStmt.setString(3, code);
                    insertStmt.setString(4, code);
                    insertStmt.setInt(5, 0);
                    insertStmt.setString(6, code);
                    insertStmt.setString(7, code);
                    insertStmt.executeUpdate();
                }

                try {

//                    new CryptoCurrencyDataProvider().registerCurrencies();
                   new FiatCurrencyDataProvider().registerCurrencies();
              } catch (Exception e) {
                    throw new RuntimeException(e);
               }
                return cur;
            } else {
                // Retrieve existing currency data
                String fullDisplayName = check.getString("full_display_name");
                String shortDisplayName = check.getString("short_display_name");
                int fractionalDigits = check.getInt("fractional_digits");
                String symbol = check.getString("symbol");
                String image = check.getString("image");
                String currencyType = check.getString("currency_type");

                logger.info("Currency found with code: {}", code);

                CurrencyType type = CurrencyType.valueOf(currencyType);
                newCurrency = new Currency(type, fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, image) {

                    @Override
                    public int compareTo(java.util.@NotNull Currency o) {
                        return 0;
                    }
                };
            }
        } catch (SQLException e) {
            logger.error("SQL Exception: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return newCurrency;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return null;
    }

    @Override
    public Connection getConnection(String username, String password) {
        return null;
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {

    }

    @Override
    public void setLoginTimeout(int seconds) {

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
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

    @Override
    public ShardingKeyBuilder createShardingKeyBuilder() throws SQLException {
        return Db.super.createShardingKeyBuilder();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    // Remaining methods...

}