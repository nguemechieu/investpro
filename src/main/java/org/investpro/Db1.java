package org.investpro;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Properties;

import static org.investpro.InvestPro.*;

@Getter
@Setter
public class Db1 implements Db {

    private static final Logger logger = LoggerFactory.getLogger(Db1.class);
    private static final String CONFIG_FILE = "src/main/resources/config.properties";

    private static Db1 instance;

    protected EntityManager entityManager;
    private Connection conn;

    /**
     * **Singleton Constructor for Db1**
     */
    protected Db1() {

        initializeConnection();
        initializeEntityManager();
    }

    public static EntityManagerFactory getEntityManagerFactory() {
        Properties hibernateProps = new Properties();
        hibernateProps.setProperty("jakarta.persistence.jdbc.url",
                "jdbc:mysql://" + PROPERTIES.getProperty("DB_HOST") + ":" +
                        PROPERTIES.getProperty("DB_PORT") + "/" +
                        PROPERTIES.getProperty("DB_NAME") +
                        "?useSSL=false&serverTimezone=UTC");

        hibernateProps.setProperty("jakarta.persistence.jdbc.user", PROPERTIES.getProperty("DB_USER"));
        hibernateProps.setProperty("jakarta.persistence.jdbc.password", PROPERTIES.getProperty("DB_PASSWORD"));
        hibernateProps.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");

        return Persistence.createEntityManagerFactory("User", hibernateProps);
    }

    /**
     * **Singleton Accessor**
     */
    public synchronized Db1 getInstance() throws SQLException {
        if (instance == unwrap(null)) {
            instance = new Db1();
        }
        return instance;
    }

    /**
     * **Loads database properties from config.properties**
     */
    private Properties loadProperties() {
        Properties props = new Properties();
        try (FileInputStream fileInputStream = new FileInputStream(CONFIG_FILE)) {
            props.load(fileInputStream);
            logger.info("✅ Database properties loaded successfully.");
        } catch (IOException e) {
            logger.error("❌ Failed to load properties: {}", e.getMessage(), e);
        }
        return props;
    }

    /**
     * **Initialize MySQL Connection**
     */
    private void initializeConnection() {
        try {
            String url =

                    "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME + "?useSSL=false";
            this.conn = DriverManager.getConnection(url, DB_USER, DB_PASSWORD);
            logger.info("✅ Connected to MySQL database successfully!");
        } catch (SQLException e) {
            logger.error("❌ Database connection error: {}", e.getMessage(), e);
        }
    }

    /**
     * **Initialize Hibernate Entity Manager**
     */
    private void initializeEntityManager() {
        try {


            setEntityManager(getEntityManagerFactory().createEntityManager());
            logger.info("✅ Hibernate EntityManager initialized successfully.");
        } catch (Exception e) {
            logger.error("❌ Error initializing EntityManager: {}", e.getMessage(), e);
        }
    }

    /**
     * **Create Tables**
     */
    @Override
    public void createTables() {
        String sql = """
                CREATE TABLE IF NOT EXISTS currencies (
                    id VARCHAR(36) PRIMARY KEY,
                    currency_type VARCHAR(255),
                    code VARCHAR(255) UNIQUE,
                    full_display_name VARCHAR(255),
                    short_display_name VARCHAR(255),
                    fractional_digits INTEGER,
                    symbol VARCHAR(255),
                    image VARCHAR(255)
                );
                """;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            logger.info("✅ Table 'currencies' created or already exists.");
        } catch (SQLException e) {
            logger.error("❌ Error creating table: {}", e.getMessage(), e);
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

    /**
     * **Save Currency Data**
     */
    @Override
    @Transactional
    public void save(Currency currency) {
        try {
            entityManager.getTransaction().begin();
            entityManager.merge(currency);
            entityManager.getTransaction().commit();

        } catch (Exception e) {
            logger.error("Transaction failed: ", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * **Retrieve Currency by Code**
     */
    @Override
    public Currency getCurrency(String code) throws SQLException {
        String sql = "SELECT * FROM currencies WHERE code = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new Currency(
                        CurrencyType.valueOf(rs.getString("currency_type")),
                        rs.getString("full_display_name"),
                        rs.getString("short_display_name"),
                        rs.getString("code"),
                        rs.getInt("fractional_digits"),
                        rs.getString("symbol"),
                        rs.getString("image")
                ) {
                    @Override
                    public int compareTo(@NotNull Currency o) {
                        return 0;
                    }

                    @Override
                    public int compareTo(java.util.@NotNull Currency o) {
                        return 0;
                    }
                };
            } else {
                logger.warn("⚠ Currency with code '{}' not found in database.", code);
                return unwrap(null);
            }
        } catch (SQLException e) {
            logger.error("❌ Error retrieving currency '{}': {}", code, e.getMessage(), e);
            return unwrap(null);
        }
    }

    /**
     * **Delete Data from Table**
     */
    @Override
    public void delete(String table, String column, String value) {
        String sql = "DELETE FROM " + table + " WHERE " + column + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("✅ Deleted {} row(s) from table '{}'", rowsAffected, table);
            } else {
                logger.warn("⚠ No rows found for deletion in table '{}'", table);
            }
        } catch (SQLException e) {
            logger.error("❌ Error deleting from table '{}': {}", table, e.getMessage(), e);
        }
    }

    @Override
    public void create(String table, String column, String value) {

    }

    @Override
    public void findById(String table, String column, String value) {

    }

    /**
     * **Close Database Connection**
     */
    @Override
    public void close() {
        try {
            if (conn != unwrap(null)) {
                conn.close();
                logger.info("✅ Database connection closed.");
            }
            if (entityManager != unwrap(null)) {
                entityManager.close();
            }

        } catch (SQLException e) {
            logger.error("❌ Error closing database connection: {}", e.getMessage(), e);
        }
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

    /**
     * **Get Database Connection**
     */
    @Override
    public Connection getConnection() {
        return conn;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return unwrap(null);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return unwrap(null);
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {

    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {

    }

    @Override
    public ConnectionBuilder createConnectionBuilder() throws SQLException {
        return Db.super.createConnectionBuilder();
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        try {
            return unwrap(null);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ShardingKeyBuilder createShardingKeyBuilder() throws SQLException {
        return Db.super.createShardingKeyBuilder();
    }


    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }
}
