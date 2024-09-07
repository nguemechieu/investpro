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


    Connection conn;
    //= DriverManager.getConnection("jdbc:sqlite:cryptoinvestor");

    private String fullDisplayName;
    private String shortDisplayName;
    private int fractionalDigits;

    private String symbol;
    private String image;
    String dbName;

    public Db1() throws Exception {

        //Class.forName() to load the driver
        Class.forName(
                "org.sqlite.JDBC"
        );


        try {

            Properties conf = new Properties();
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("InvestPro.properties")) {
                if (inputStream == null) {
                    throw new FileNotFoundException("Property file 'InvestPro.properties' not found in the classpath");
                }
                conf.load(inputStream);
            } catch (IOException e) {
                logger.error(e.getMessage());// Handle the exception properly in your application
            }
            this.fullDisplayName = conf.getProperty("fullDisplayName");
            this.dbName = conf.getProperty("dbName");
            this.conn = DriverManager.getConnection(STR."jdbc:sqlite:\{dbName}");
            if (conn != null) {
                logger.info("Connected to database: {}", conf.getProperty("dbName"));
                logger.info("Creating tables...");
                createTables();
                logger.info("Tables created.");

            }

        } catch (SQLException e) {
            logger.error(STR."Error connecting to the database\n\{e.getMessage()}");

        }


    }


    @Override
    public Connection getConn() {
        return conn;
    }

    @Override
    public void createTables() {
        try {
            String sql = "CREATE TABLE IF NOT EXISTS currencies ( " +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "currency_type VARCHAR(255), " +
                    "full_display_name VARCHAR(255), " +
                    "short_display_name VARCHAR(255), " +
                    "code VARCHAR(255) , " +
                    "fractional_digits INTEGER, " +
                    "symbol VARCHAR(255), " +
                    "image VARCHAR(255) " + ");";
            logger.info(sql);

            conn.createStatement().executeUpdate(sql);
            logger.info(sql);

            sql = "CREATE TABLE IF NOT EXISTS users ( " +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username VARCHAR(255) UNIQUE, " +
                    "password VARCHAR(255), " +
                    "email VARCHAR(255) UNIQUE, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP " + ")";
            logger.info(sql);
            conn.createStatement().executeUpdate(sql);


            conn.createStatement().executeUpdate(sql);

            sql = "CREATE TABLE IF NOT EXISTS portfolio_items ( " +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER, " +
                    "currency_id INTEGER, " +
                    "quantity DECIMAL(20,10), " +
                    "cost_basis DECIMAL(20,10), " +
                    "purchase_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(_id), " +
                    "FOREIGN KEY (currency_id) REFERENCES currencies(_id) " + ")";
            logger.info(sql);
            conn.createStatement().executeUpdate(sql);

            sql = "CREATE TABLE IF NOT EXISTS transactions ( " +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER, " +
                    "currency_id INTEGER, " +
                    "quantity DECIMAL(20,10), " +
                    "transaction_type VARCHAR(255), " +
                    "price DECIMAL(20,10), " +
                    "transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (user_id) REFERENCES users(_id), " +
                    "FOREIGN KEY (currency_id) REFERENCES currencies(_id) " + ")";
            logger.info(sql);
            conn.createStatement().executeUpdate(sql);

            sql = "CREATE TABLE IF NOT EXISTS watchlists ( " +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER, " +
                    "currency_id INTEGER, " +
                    "FOREIGN KEY (user_id) REFERENCES users(_id), " +
                    "FOREIGN KEY (currency_id) REFERENCES currencies(_id) " + ")";
            logger.info(sql);
            conn.createStatement().executeUpdate(sql);

            sql = "CREATE TABLE IF NOT EXISTS candle_data ( " +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "currency_id INTEGER, " +
                    "open DECIMAL(20,10), " +
                    "high DECIMAL(20,10), " +
                    "low DECIMAL(20,10), " +
                    "close DECIMAL(20,10), " +
                    "volume DECIMAL(20,10), " +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (currency_id) REFERENCES currencies(_id) " + ")";
            logger.info(sql);
            conn.createStatement().executeUpdate(sql);

            sql = "CREATE TABLE IF NOT EXISTS trade_signals ( " +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "currency_id INTEGER, " +
                    "signal_type VARCHAR(255), " +
                    "signal_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (currency_id) REFERENCES currencies(_id) " + ")";
            logger.info(sql);
            conn.createStatement().executeUpdate(sql);

            sql = "CREATE TABLE IF NOT EXISTS trade_logs ( " +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "currency_id INTEGER, " +
                    "trade_type VARCHAR(255), " +
                    "trade_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (currency_id) REFERENCES currencies(_id) " + ")";
            logger.info(sql);
            conn.createStatement().executeUpdate(sql);






        } catch (SQLException e) {
            logger.error(STR."Error creating the database tables\n\{e.getMessage()}");
        }

    }

    @Override
    public void dropTables() {
        try {
            String sql = "DROP TABLE IF EXISTS currencies";
            logger.info(sql);
            this.conn.createStatement().executeUpdate(sql);
        } catch (SQLException e) {
            logger.error(STR."Error dropping the database tables\n\{e.getMessage()}");
        }

    }

    @Override
    public void truncateTables() {
        try {
            String sql = STR."DELETE FROM currencies";
            logger.info(sql);
            this.conn.createStatement().executeUpdate(sql);
        } catch (SQLException e) {
            logger.error(STR."Error truncating the database tables\n\{e.getMessage()}");
        }

    }

    @Override
    public void insertData() {
        try {
            String sql = "INSERT INTO currencies (full_display_name, short_display_name, fractional_digits, symbol, image) VALUES (?,?,?,?,?)";
            PreparedStatement pstmt = this.conn.prepareStatement(sql);
            pstmt.setString(1, "Bitcoin");
            pstmt.setString(2, "BTC");
            pstmt.setInt(3, 8);
            pstmt.setString(4, "$");
            pstmt.setString(5, "bitcoin.png");
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error(STR."Error inserting data into the database\n\{e.getMessage()}");
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

        return dbName;
    }

    @Override
    public void setDbName(String dbName) {
        this.dbName = dbName;

    }

    @Override
    public String getUserName() {
        return null;
    }

    @Override
    public void setUserName(String userName) {

    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public void setPassword(String password) {

    }

    @Override
    public String getUrl() {
        return null;
    }

    @Override
    public void setUrl(String url) {

    }

    @Override
    public String getDriverClassName() {
        return null;
    }

    @Override
    public String getJdbcUrl() {
        return null;
    }

    @Override
    public String getJdbcUsername() {
        return null;
    }

    @Override
    public String getJdbcPassword() {
        return null;
    }

    @Override
    public String getJdbcDriverClassName() {
        return null;
    }

    @Override
    public int find(String table, String column, String value) {
        int da = 0;
        try {
            da = conn.createStatement().executeUpdate(STR."SELECT * FROM \{table} WHERE \{column} = '\{value}'");
        } catch (SQLException e) {
            logger.error(STR."Error finding the data\n\{e.getMessage()}");

        }

        return da;
    }

    @Override
    public void findAll(String table, String column, String value) {
        try {
            conn.createStatement().executeUpdate(STR."SELECT * FROM \{table} WHERE \{column} = '\{value}'");
        } catch (SQLException e) {
            logger.error(STR."Error finding all the data\n\{e.getMessage()}");
        }

    }

    @Override
    public void update(String table, String column, String value) {
        try {
            conn.createStatement().executeUpdate(STR."UPDATE \{table} SET \{column} = '\{value}'");
        } catch (SQLException e) {
            logger.error(STR."Error updating the database\n\{e.getMessage()}");
        }

    }


    @Override
    public void insert(String table, String column, String value) {
        try {
            conn.createStatement().executeUpdate(STR."INSERT INTO \{table} (\{column}) VALUES ('\{value}')");
        } catch (SQLException e) {

            logger.error(STR."Error inserting the database\n\{e.getMessage()}");
        }

    }

    @Override
    public void delete(String table, String column, String value) {
        try {
            conn.createStatement().executeUpdate(STR."DELETE FROM \{table} WHERE \{column} = '\{value}'");
        } catch (SQLException e) {
            logger.error(STR."Error deleting the database\n\{e.getMessage()}");
        }

    }

    @Override
    public void create(String table, String column, String value) {
        try {
            conn.createStatement().executeUpdate(STR."CREATE TABLE IF NOT EXISTS \{table} (\{column} VARCHAR(255))");
        } catch (SQLException e) {
            logger.error(STR."Error creating the database table\n\{e.getMessage()}");
            LogUtils.logSevere(e.getMessage());
        }

    }

    @Override
    public void findById(String table, String column, String value) {
        try {
            conn.createStatement().executeUpdate(STR."SELECT * FROM \{table} WHERE \{column} = '\{value}'");
            ResultSet rs = conn.createStatement().executeQuery(STR."SELECT * FROM \{table} WHERE \{column} = '\{value}'");
            while (rs.next()) {
                logger.info(rs.getString(column));
            }

        } catch (SQLException e) {
            logger.error(STR."Error finding the database\n\{e.getMessage()}");
            LogUtils.logSevere(e.getMessage());
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
    public void save(@NotNull ArrayList<Currency> currency) throws SQLException {


        try {

            conn.createStatement().executeQuery(STR."SELECT * FROM currencies WHERE code = '\{currency.getFirst().getCode()}' AND currency_type = '\{currency.getFirst().getCurrencyType()}' AND + code = '\{currency.getFirst().getCode()}' ").next();

            logger.info(STR."Currency already exists with code: \{currency.getFirst().getCode()}");


        } catch (SQLException e) {

            createTables();


            conn.prepareStatement(STR."INSERT INTO  currencies (     Currency_type,  code, full_display_name, short_display_name, fractional_digits, symbol, image) VALUES (?,?,?,?,?,?,?)");

                conn.createStatement().executeUpdate(
                        STR."INSERT INTO currencies ( currency_type, code, full_display_name, short_display_name, fractional_digits, symbol, image) VALUES ('\{currency.getFirst().getCurrencyType()}','\{currency.getFirst().getCode()}','\{currency.getFirst().getFullDisplayName()}','\{currency.getFirst().getShortDisplayName()}','\{currency.getFirst().getFractionalDigits()}','\{currency.getFirst().getSymbol()}','\{currency.getFirst().getImage()}')");

            CurrencyType type = currency.getFirst().currencyType;//getCurrencyType();

            Currency.CURRENCIES.put(new SymmetricPair<>(currency.getFirst(), type), currency.getFirst());

                logger.info(
                        STR."New Currency with code: \{currency.getFirst().getCode()}was added  to the  database"
                );



        }
    }


    public Currency getCurrency(String code) throws SQLException {

        Currency newCurrency;

        // Using a prepared statement to prevent SQL injection
        String query = "SELECT * FROM currencies WHERE code = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, code);
            ResultSet check = stmt.executeQuery();

            if (!check.next()) {
                logger.info(String.format("Currency not found with code: %s", code));

                try {
                    // Create a new CryptoCurrency instance if the currency does not exist
                    CryptoCurrency cur = new CryptoCurrency(code, code, code, 0, code, code);

                    // Using a prepared statement for the insert operation
                    String insertQuery = "INSERT INTO currencies (currency_type,  full_display_name, short_display_name,code, fractional_digits, symbol, image) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                        insertStmt.setString(1, "CRYPTO");
                        insertStmt.setString(2, code);
                        insertStmt.setString(3, code); // Assuming full display name is code initially
                        insertStmt.setString(4, code); // Assuming short display name is code initially
                        insertStmt.setInt(5, 0);       // Assuming fractional digits is 0 for now
                        insertStmt.setString(6, code); // Assuming symbol is the code initially
                        insertStmt.setString(7, code); // Assuming image is the code initially
                        insertStmt.executeUpdate();
                    }

                    return cur; // Return the new CryptoCurrency object
                } catch (ClassNotFoundException e) {
                    logger.error("Error creating new Currency instance: {}", e.getMessage());
                    return Currency.NULL_FIAT_CURRENCY;
                }

            } else {
                // Retrieve existing currency data
                String fullDisplayName = check.getString("full_display_name");
                String shortDisplayName = check.getString("short_display_name");
                int fractionalDigits = check.getInt("fractional_digits");
                String symbol = check.getString("symbol");
                String image = check.getString("image");
                String currencyType = check.getString("currency_type");

                logger.info(String.format("Currency found with code: %s", code));
                logger.info(String.format(
                        "Currency with code: %s, full_display_name: %s, short_display_name: %s, fractional_digits: %d, symbol: %s, image: %s, currency_type: %s",
                        code, fullDisplayName, shortDisplayName, fractionalDigits, symbol, image, currencyType));

                CurrencyType type = CurrencyType.valueOf(currencyType);
                logger.info(String.format("Currency Type: %s", type));

                // Create the Currency object
                newCurrency = new Currency(type, fullDisplayName, shortDisplayName,
                        code, fractionalDigits, symbol, image) {
                    @Override
                    public int compareTo(@NotNull Currency o) {
                        return 0;
                    }

                    @Override
                    public int compareTo(java.util.@NotNull Currency o) {
                        return 0;
                    }
                };

                logger.info(String.format("New Currency: %s", newCurrency));
            }
        } catch (SQLException e) {
            logger.error(String.format("SQL Exception: %s", e.getMessage()));
            throw e; // Rethrow the exception for further handling
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return newCurrency;
    }


    public void create(String data) {
        try {
            PreparedStatement co = conn.prepareStatement(data);
            co.execute();

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public ResultSet Select(String s) {
        try {
            ResultSet rs = conn.createStatement().executeQuery(s);
            if (rs.next()) {
                logger.info(rs.getString(1));
                return rs;

            }
        } catch (SQLException e) {
            logger.error(STR."Error finding the database\n\{e.getMessage()}");
        }
        return null;
    }

    public void insert(String s) {
        try {
            PreparedStatement co = conn.prepareStatement(s);
            co.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}