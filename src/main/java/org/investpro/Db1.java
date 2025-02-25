package org.investpro;

import jakarta.persistence.*;
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

@Getter
@Setter
public class Db1 implements Db {

    private static final Logger logger = LoggerFactory.getLogger(Db1.class);
    private static final String CONFIG_FILE = "src/main/resources/config.properties";

    private static Db1 instance;
    private static final Properties PROPERTIES = new Properties();
    private static EntityManagerFactory entityManagerFactory;

    protected EntityManager entityManager;
    private Connection conn;
    private Class<Object> iface;

    /**
     * **Singleton Constructor for Db1**
     */
    public Db1() {
        loadProperties();
        initializeConnection();
        initializeEntityManager();
    }

    /**
     * **Singleton Accessor**
     */
    public static synchronized Db1 getInstance() {
        if (instance == null) {
            instance = new Db1();
        }
        return instance;
    }

    /**
     * **Get EntityManagerFactory Singleton**
     */
    public static EntityManagerFactory getEntityManagerFactory() {
        if (entityManagerFactory == null) {
            Properties hibernateProps = new Properties();
            hibernateProps.setProperty("jakarta.persistence.jdbc.url",
                    "jdbc:mysql://" + PROPERTIES.getProperty("DB_HOST", "localhost") + ":" +
                            PROPERTIES.getProperty("DB_PORT", "3306") + "/" +
                            PROPERTIES.getProperty("DB_NAME", "InvestPro") +
                            "?useSSL=false&serverTimezone=UTC");

            hibernateProps.setProperty("jakarta.persistence.jdbc.user", PROPERTIES.getProperty("DB_USER", "root"));
            hibernateProps.setProperty("jakarta.persistence.jdbc.password", PROPERTIES.getProperty("DB_PASSWORD", "admin123"));
            hibernateProps.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");

            entityManagerFactory = Persistence.createEntityManagerFactory("users", hibernateProps);
        }
        return entityManagerFactory;
    }

    /**
     * **Loads database properties from config.properties**
     */
    private void loadProperties() {
        try (FileInputStream fileInputStream = new FileInputStream(CONFIG_FILE)) {
            PROPERTIES.load(fileInputStream);
            logger.info("✅ Database properties loaded successfully.");
        } catch (IOException e) {
            logger.error("❌ Failed to load properties: {}", e.getMessage(), e);
        }
    }

    /**
     * **Initialize MySQL Connection**
     */
    private void initializeConnection() {
        try {
            String url = "jdbc:mysql://" + PROPERTIES.getProperty("DB_HOST", "localhost") + ":" +
                    PROPERTIES.getProperty("DB_PORT", "3306") + "/" +
                    PROPERTIES.getProperty("DB_NAME", "InvestPro") + "?useSSL=false";

            this.conn = DriverManager.getConnection(url,
                    PROPERTIES.getProperty("DB_USER", "admin"),
                    PROPERTIES.getProperty("DB_PASSWORD", "admin123"));
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
            this.entityManager = getEntityManagerFactory().createEntityManager();
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
            currency_id VARCHAR(36) PRIMARY KEY,
            code VARCHAR(10) NOT NULL UNIQUE,
            currency_type VARCHAR(20) NOT NULL,
            fractional_digits INT NOT NULL,
            full_display_name VARCHAR(100) NOT NULL,
            image VARCHAR(255),
            short_display_name VARCHAR(50) NOT NULL,
            symbol VARCHAR(10) NOT NULL
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
        String sql = "DROP TABLE IF EXISTS currencies";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            logger.info("�� Table 'currencies' dropped successfully.");
        } catch (SQLException e) {
            logger.error("�� Error dropping table: {}", e.getMessage(), e);
        }

    }

    @Override
    public void truncateTables() {
        String sql = "TRUNCATE TABLE currencies";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            logger.info("�� Table 'currencies' truncated successfully.");
        } catch (SQLException e) {
            logger.error("�� Error truncating table: {}", e.getMessage(), e);
        }

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
    @Transactional
    public void save(@NotNull Currency currency) {
        if (currency.code == null || currency.code.equals("XXX")) {
            logger.info("currency code is null or invalid");
            return;
        }

        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            em.setFlushMode(FlushModeType.AUTO);
            tx.begin();

            // Check if the currency exists to prevent duplicate entries
            Currency existingCurrency = em.find(Currency.class, currency.getCode());
            if (existingCurrency != null) {
                logger.info("Currency {} already exists. Updating instead of inserting.", currency.getCode());
                em.merge(currency);
            } else {
                logger.info("Saving new currency {}", currency.getCode());
                em.persist(currency);
            }

            tx.commit();
            logger.info("✅ Currency {} saved successfully.", currency.getCode());

        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();  // Only rollback if the transaction is still active
            }
            logger.error("❌ Transaction failed: {}", e.getMessage(), e);
            throw new RuntimeException("Database transaction failed for currency: " + currency.getCode(), e);
        } finally {
            if (em.isOpen()) {
                em.close();  // Ensure entity manager is closed properly
            }
        }
    }



    public Currency getCurrency(String code) {
        try {
            return entityManager.find(Currency.class, code);
        } catch (Exception e) {
            logger.error("Currency not found: {}", code, e);
            return null;
        }
    }

    @Override
    public void save(CandleData candle) {
        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = em.getTransaction();

        try {
            tx.begin();
            em.setFlushMode(FlushModeType.AUTO);
            em.persist(candle);
            tx.commit();


        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }


    /**
     * **Close Database Connection**
     */
    @Override
    public void close() {
        try {
            if (conn != null) {
                conn.close();
                logger.info("✅ Database connection closed.");
            }
            if (entityManager != null) {
                entityManager.close();
            }
        } catch (SQLException e) {
            logger.error("❌ Error closing database connection: {}", e.getMessage(), e);
        }
    }

    @Override
    public String getDbName() {
        return dbName;
    }
    String dbName;
    @Override
    public void setDbName(String dbName) {
        this.dbName = dbName;


    }

    @Override
    public String getUserName() {
        return username;
    }

    @Override
    public void setUserName(String userName) {
        this.username = userName;

    }
    String password;
    String username;
    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;

    }

    String url;
    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public void setUrl(String url) {
        this.url = url;

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

        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column + " =?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            } else {
                logger.warn("��� No data found in table '{}' for column '{}' with value '{}'.", table, column, value);
                return 0;
            }
        }
        catch (SQLException e) {
            logger.error("�� Error finding data: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public void findAll(String table, String column, String value) {
        String sql = "SELECT * FROM " + table + " WHERE " + column + " =?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                logger.info("Found: {}", rs.getString(column));
            }
        } catch (SQLException e) {
            logger.error("�� Error finding data: {}", e.getMessage(), e);
        }

    }

    @Override
    public void update(String table, String column, String value) {
        String sql = "UPDATE " + table + " SET " + column + " =?" + " WHERE " + column + " =?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            stmt.setString(2, value);
            stmt.executeUpdate();
            logger.info("�� Data updated successfully.");
        } catch (SQLException e) {
            logger.error("�� Error updating data: {}", e.getMessage(), e);
        }

    }

    @Override
    public void insert(String table, String column, String value) {
        String sql = "INSERT INTO " + table + "(" + column + ") VALUES(?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            stmt.executeUpdate();
            logger.info("�� Data inserted successfully.");
        } catch (SQLException e) {
            logger.error("�� Error inserting data: {}", e.getMessage(), e);
        }

    }

    @Override
    public void delete(String table, String column, String value) {
        String sql = "DELETE FROM " + table + " WHERE " + column + " =?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            stmt.executeUpdate();
            logger.info("�� Data deleted successfully.");
        } catch (SQLException e) {
            logger.error("�� Error deleting data: {}", e.getMessage(), e);
        }

    }

    @Override
    public void create(String table, String column, String value) {
        String sql = "INSERT INTO " + table + "(" + column + ") VALUES(?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            stmt.executeUpdate();
            logger.info("�� Data inserted successfully.");
        } catch (SQLException e) {
            logger.error("�� Error inserting data: {}", e.getMessage(), e);
        }


    }

    @Override
    public void findById(String table, String column, String value) {
        String sql = "SELECT * FROM " + table + " WHERE " + column + " =?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                logger.info("Found: {}", rs.getString(column));
            } else {
                logger.warn("��� No data found in table '{}' for column '{}' with value '{}'.", table, column, value);
            }
        } catch (SQLException e) {
            logger.error("�� Error finding data: {}", e.getMessage(), e);
        }


    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(getJdbcUrl());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return DriverManager.getConnection(getJdbcUrl(), username, password);
    }

    @Override
    public PrintWriter getLogWriter() {
        return printWriter;
    }
    PrintWriter printWriter;
    @Override
    public void setLogWriter(PrintWriter out) {
        this.printWriter=out;



    }
    int loginTimeout;
    @Override
    public void setLoginTimeout(int seconds) {
        this.loginTimeout=seconds;


    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public ConnectionBuilder createConnectionBuilder() throws SQLException {
        return Db.super.createConnectionBuilder();
    }

    @Override
    public java.util.logging.Logger getParentLogger() {
        return null;

    }

    @Override
    public ShardingKeyBuilder createShardingKeyBuilder() throws SQLException {
        return Db.super.createShardingKeyBuilder();

    }


    public <T> T unwrap(Class<T> iface) {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }
}
