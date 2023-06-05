package org.investpro;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.sql.*;
import java.util.Collection;
import java.util.Properties;
import java.util.UUID;


public class Db1 implements Db {
    private static final Logger logger = LoggerFactory.getLogger(Db1.class);


    Connection conn;//= DriverManager.getConnection("jdbc:sqlite:cryptoinvestor");

    private String fullDisplayName;
    private String shortDisplayName;
    private int fractionalDigits;

    private String symbol;
    private String image;


    public Db1(@NotNull Properties conf) throws ClassNotFoundException {


        //Class.forName() to load the driver
        Class.forName(
                "com.mysql.cj.jdbc.Driver"
        );

        try {


            this.conn = DriverManager.getConnection(
                    "jdbc:mysql://" + conf.getProperty("db_host") + ":" + conf.getProperty("db_port") + "/" + conf.getProperty("db_name")
                    , conf.getProperty("db_username"), conf.getProperty("db_password"));
        } catch (SQLException e) {
            e.printStackTrace();
            new Message(
                    Message.MessageType.ERROR,
                    "Error connecting to the database\n" + e.getMessage()
            );
        }
    }


    @Override
    public void createTables() {
        try {
            String sql = "CREATE TABLE IF NOT EXISTS investpro.investpro ( " +
                    "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                    "full_display_name VARCHAR(255), " +
                    "short_display_name VARCHAR(255), " +
                    "fractional_digits INTEGER, " +
                    "symbol VARCHAR(255), " +
                    "image VARCHAR(255) " +
                    ")";
            System.out.println(sql);
            this.conn.createStatement().executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void dropTables() {

    }

    @Override
    public void truncateTables() {

    }

    @Override
    public void insertData() {

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
        return null;
    }

    @Override
    public void setDbName(String dbName) {

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
            da = conn.createStatement().executeUpdate("SELECT * FROM " + table + " WHERE " + column + " = '" + value + "'");
        } catch (SQLException e) {
            e.printStackTrace();

        }

        return da;
    }

    @Override
    public void findAll(String table, String column, String value) {
        try {
            conn.createStatement().executeUpdate("SELECT * FROM " + table + " WHERE " + column + " = '" + value + "'");
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void update(String table, String column, String value) {
        try {
            conn.createStatement().executeUpdate("UPDATE " + table + " SET " + column + " = '" + value + "'");
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


    @Override
    public void insert(String table, String column, String value) {
        try {
            conn.createStatement().executeUpdate("INSERT INTO " + table + " (" + column + ") VALUES ('" + value + "')");
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void delete(String table, String column, String value) {
        try {
            conn.createStatement().executeUpdate("DELETE FROM " + table + " WHERE " + column + " = '" + value + "'");
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void create(String table, String column, String value) {
        try {
            conn.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " (" + column + " VARCHAR(255))");
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void findById(String table, String column, String value) {
        try {
            conn.createStatement().executeUpdate("SELECT * FROM " + table + " WHERE " + column + " = '" + value + "'");
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM " + table + " WHERE " + column + " = '" + value + "'");
            while (rs.next()) {
                System.out.println(rs.getString(column));

            }
        } catch (SQLException e) {
            e.printStackTrace();
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

    public void registerCurrencies(@NotNull Collection<FiatCurrency> currencies) throws SQLException {
        //Create table currencies if not exits
        conn.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS " +
                "currencies (" +
                "id INT PRIMARY KEY ," +
                " currency_type VARCHAR(255)," +
                "code  VARCHAR(255)," +
                "full_display_name VARCHAR(255)," +
                "short_display_name VARCHAR(255)," +
                "fractional_digits INTEGER," +
                "symbol VARCHAR(255)," +
                "image VARCHAR(255)" +
                ");");

        //Insert currencies into database
        for (Currency currency : currencies) {
            try {
                //Check if currency already exists
                int check = conn.createStatement().executeUpdate("SELECT * FROM currencies WHERE code = '" + currency.getCode() + "'AND currency_type = '"
                        + currency.getCurrencyType() + "'AND + code = '" + currency.getCode() + "' ");
                if (check > 0) {
                    conn.prepareStatement("INSERT INTO  currencies (id, currency_type, code, full_display_name, short_display_name, " +
                            "fractional_digits, symbol, image) VALUES (?,?,?,?,?,?,?,?)");
                    conn.createStatement().executeUpdate("INSERT INTO currencies VALUES ('" + UUID.randomUUID().hashCode() + "','" + currency.getCurrencyType()
                            + "','" + currency.getCode()
                            + "','" + currency.getFullDisplayName()
                            + "','" + currency.getShortDisplayName() + "','"
                            + currency.getFractionalDigits() + "','" +
                            currency.getSymbol() + "','" +
                            currency.getImage() + "')");
                    //
                    // conn.createStatement().executeQuery(+"'DELETE FROM table a
//                   WHERE a.ROWID IN
//                   (SELECT ROWID FROM
//                   (SELECT
//                   ROWID,
//                           ROW_NUMBER() OVER
//                           (PARTITION BY unique_columns ORDER BY ROWID) dup
//                   FROM table)
//                   WHERE dup > 1);
                    //    ');");

                    conn.createStatement().executeUpdate("DELETE FROM currencies WHERE code IN (SELECT code FROM (SELECT code, ROW_COUNT()" +
                            " OVER (PARTITION BY full_display_name ORDER BY code) dup FROM currencies) as cd WHERE dup > 1);");
                    logger.info(
                            "New Currency with code: " + currency.getCode() + "was added  to the  database"
                    );

                } else {
                    logger.info("Currency already exists with code: " + currency.getCode());
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void save(@NotNull Currency currency) {

        try {
            conn.createStatement().execute("SELECT * FROM currencies WHERE code = '" + currency.getCode() + "' AND currency_type = '" + currency.getCurrencyType() +
                    "' AND + code = '" + currency.getCode() + "' ");
            if (conn.createStatement().executeQuery("SELECT * FROM currencies WHERE code = '" + currency.getCode() + "' AND currency_type = '" + currency.getCurrencyType() + "' AND + code = '" + currency.getCode() + "' ").next()) {
                logger.info("Currency already exists with code: " + currency.getCode());
            } else {


                conn.prepareStatement("INSERT INTO  currencies (  code, full_display_name, short_display_name, " +
                        "fractional_digits, symbol, image," +
                        "currency_type) VALUES (?,?,?,?,?,?,?)");


                conn.createStatement().executeUpdate(
                        "INSERT INTO currencies VALUES ('"
                                + currency.getCode() + "','" + currency.getFullDisplayName() + "','"
                                + currency.getShortDisplayName() + "','" + currency.getFractionalDigits() + "','" +
                                currency.getSymbol() + "','" + currency.getImage() +
                                "','" + currency.getCurrencyType() + "')");

                logger.info(
                        "New Currency with code: " + currency.getCode() + "was added  to the  database"
                );
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public Currency getCurrency(String code) throws SQLException {
        // Get currency from database
        Currency newCurrency = null;

        conn.createStatement().execute("CREATE TABLE IF NOT EXISTS currencies (code VARCHAR(255), full_display_name VARCHAR(255), short_display_name VARCHAR(255), fractional_digits INTEGER, symbol VARCHAR(255), image VARCHAR(255), currency_type VARCHAR(255));");


        ResultSet check = conn.createStatement().executeQuery("SELECT * FROM currencies WHERE code = '" + code + "'");
        if (!check.next()) {
            logger.info("Currency not found with code: " + code);
            new Message(Message.MessageType.WARNING, "Currency with code: " + code + " not found in database");

            return null;


        } else {
            code = check.getString("code");
            fullDisplayName = check.getString("full_display_name");
            shortDisplayName = check.getString("short_display_name");
            fractionalDigits = check.getInt("fractional_digits");
            symbol = check.getString("symbol");
            image = check.getString("image");
            String currencyType = check.getString("currency_type");
            logger.info("Currency found with code: " + code + " ");
            String format = String.format("Currency with code: %s, full_display_name: %s,  short_display_name: %s, fractional_digits: %s, symbol: %s, image: %s, currency_type: %s",
                    code,
                    fullDisplayName,
                    shortDisplayName,
                    fractionalDigits,
                    symbol,
                    image,
                    currencyType);
            logger.info(format);
            CurrencyType type = CurrencyType.valueOf(currencyType);
            logger.info("Currency Type: " + type);
            newCurrency = new Currency(
                    type,
                    fullDisplayName,
                    shortDisplayName,
                    code,
                    fractionalDigits,
                    symbol,
                    image) {
                @Override
                public int compareTo(@NotNull Currency o) {
                    return 0;
                }

                @Override
                public int compareTo(java.util.@NotNull Currency o) {
                    return 0;
                }
            };
            logger.info("New Currency: " + newCurrency);
        }
        return newCurrency;
    }
}