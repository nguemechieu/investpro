package org.investpro;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;
import org.jetbrains.annotations.NotNull;
import weka.gui.beans.DataSourceListener;
import weka.gui.beans.InstanceListener;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;

import static jakarta.persistence.Persistence.createEntityManagerFactory;
import static org.investpro.Exchange.logger;

public class DbHibernate implements Db {

    final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;

    public DbHibernate() {
        // Initialize EntityManagerFactory with necessary properties
        entityManagerFactory = createEntityManagerFactory(
                "db",
                new Properties() {{
                    put("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect");
                    put("hibernate.hbm2ddl.auto", "update");
                    put("hibernate.show_sql", "true");
                }}
        );
        entityManager = entityManagerFactory.createEntityManager();




        logger.info("Initializing DbHibernate");

        try {
            entityManager.getTransaction().begin();

            // Create users table
            entityManager.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "username VARCHAR(255) NOT NULL UNIQUE," +
                            "password VARCHAR(255) NOT NULL," +
                            "email VARCHAR(255) NOT NULL UNIQUE" +
                            ")"
            ).executeUpdate();

            // Create currencies table
            entityManager.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS currencies (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "currencyType VARCHAR(255) NOT NULL," +
                            "fullDisplayName VARCHAR(255) NOT NULL," +
                            "shortDisplayName VARCHAR(255)," +
                            "code VARCHAR(5) NOT NULL," +
                            "fractionalDigits INTEGER NOT NULL," +
                            "symbol VARCHAR(10) NOT NULL," +
                            "image VARCHAR(255) NOT NULL" +
                            ")"
            ).executeUpdate();

            // Create portfolio_items table
            entityManager.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS portfolio_items (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "user_id INTEGER NOT NULL," +
                            "currency_id INTEGER NOT NULL," +
                            "amount DECIMAL(10, 2) NOT NULL," +
                            "FOREIGN KEY (user_id) REFERENCES users(id)," +
                            "FOREIGN KEY (currency_id) REFERENCES currencies(id)" +
                            ")"
            ).executeUpdate();

            // Create trades table
            entityManager.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS trades (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "user_id INTEGER NOT NULL," +
                            "currency_id INTEGER NOT NULL," +
                            "price DECIMAL(10, 2) NOT NULL," +
                            "volume DECIMAL(10, 2) NOT NULL," +
                            "timestamp DATETIME NOT NULL," +
                            "FOREIGN KEY (user_id) REFERENCES users(id)," +
                            "FOREIGN KEY (currency_id) REFERENCES currencies(id)" +
                            ")"
            ).executeUpdate();

            // Create portfolio_history table
            entityManager.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS portfolio_history (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "user_id INTEGER NOT NULL," +
                            "currency_id INTEGER NOT NULL," +
                            "amount DECIMAL(10, 2) NOT NULL," +
                            "timestamp DATETIME NOT NULL," +
                            "FOREIGN KEY (user_id) REFERENCES users(id)," +
                            "FOREIGN KEY (currency_id) REFERENCES currencies(id)" +
                            ")"
            ).executeUpdate();

            // Create accounts table
            entityManager.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS accounts (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "user_id INTEGER NOT NULL," +
                            "currency_id INTEGER NOT NULL," +
                            "balance DECIMAL(10, 2) NOT NULL," +
                            "FOREIGN KEY (user_id) REFERENCES users(id)," +
                            "FOREIGN KEY (currency_id) REFERENCES currencies(id)" +
                            ")"
            ).executeUpdate();

            // Create candle_data table
            entityManager.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS candle_data (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "timestamp DATETIME NOT NULL," +
                            "open DECIMAL(10, 2) NOT NULL," +
                            "high DECIMAL(10, 2) NOT NULL," +
                            "low DECIMAL(10, 2) NOT NULL," +
                            "close DECIMAL(10, 2) NOT NULL," +
                            "volume DECIMAL(10, 2) NOT NULL," +
                            "currency_id INTEGER NOT NULL," +
                            "FOREIGN KEY (currency_id) REFERENCES currencies(id)" +
                            ")"
            ).executeUpdate();

            entityManager.getTransaction().commit();
            logger.info("All tables created successfully");

        } catch (Exception e) {
            logger.error("Error during table creation", e);
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
        }
    }

    @Override
    public void dropTables() {
        // Implement drop table logic
        entityManager.getTransaction().begin();
        try {
            entityManager.createNativeQuery("DROP TABLE IF EXISTS portfolio_items").executeUpdate();
            entityManager.createNativeQuery("DROP TABLE IF EXISTS users").executeUpdate();
            entityManager.createNativeQuery("DROP TABLE IF EXISTS currencies").executeUpdate();
            entityManager.createNativeQuery("DROP TABLE IF EXISTS trades").executeUpdate();
            entityManager.createNativeQuery("DROP TABLE IF EXISTS portfolio_history").executeUpdate();
            entityManager.createNativeQuery("DROP TABLE IF EXISTS accounts").executeUpdate();
            entityManager.createNativeQuery("DROP TABLE IF EXISTS candle_data").executeUpdate();
            entityManager.getTransaction().commit();
        } catch (Exception e) {
            logger.error("Error during table drop", e);
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
        }
    }



    @Override
    public void createTables() throws SQLException {
        // Implement table creation logic (already handled in constructor)
    }

    @Override
    public void truncateTables() {
        // Implement table truncation logic
    }

    @Override
    public void insert(String tableName, String columnName, String value) {
        String sql = String.format("INSERT INTO %s (%s) VALUES (?)", tableName, columnName);
        entityManager.getTransaction().begin();
        entityManager.createNativeQuery(sql)
                .setParameter(1, value)
                .executeUpdate();
        entityManager.getTransaction().commit();
    }

    @Override
    public void update(String tableName, String columnName, String value) throws SQLException {
        String sql = String.format("UPDATE %s SET %s = ? WHERE id = ?", tableName, columnName);
        entityManager.getTransaction().begin();
        entityManager.createNativeQuery(sql)
                .setParameter(1, value)
                .executeUpdate();
        entityManager.getTransaction().commit();
    }

    /**
     * @param username
     * @param password
     * @return
     * @throws SQLException
     */
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return null;
    }

    @Override
    public Currency getCurrency(String code) throws Exception {
        String sql = "SELECT * FROM currencies WHERE code = ?1";
        try {
            return (Currency) entityManager.createNativeQuery(sql, Currency.class)
                    .setParameter(1, code)
                    .getSingleResult();
        } catch (NoResultException e) {

            throw new Exception("Currency not found: " + code);
        }
    }

    @Override
    public void save(@NotNull ArrayList<Currency> currencyList) {
        String sql = """
                INSERT INTO currencies 
                (currencyType, fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, image) 
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        entityManager.getTransaction().begin();
        currencyList.forEach(currency -> entityManager.createNativeQuery(sql)
                .setParameter(1, currency.getCurrencyType())
                .setParameter(2, currency.getFullDisplayName())
                .setParameter(3, currency.getShortDisplayName())
                .setParameter(4, currency.getCode())
                .setParameter(5, currency.getFractionalDigits())
                .setParameter(6, currency.getSymbol())
                .setParameter(7, currency.getImage())
                .executeUpdate());
        entityManager.getTransaction().commit();
    }

    @Override
    public int find(String table, String column, String value) {
        String sql = String.format("SELECT id FROM %s WHERE %s = ?", table, column);
        return entityManager.createNativeQuery(sql)
                .setParameter(1, value)
                .executeUpdate();
    }

    /**
     * @return
     * @throws SQLException
     */
    @Override
    public Connection getConnection() throws SQLException {
        return null;
    }


    @Override
    public void setLogWriter(PrintWriter out) {
        // Implement log writer setup
    }

    @Override
    public void close() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    @Override
    public void addDataSourceListener(DataSourceListener dsl) {
        // Add data source listener implementation
    }

    @Override
    public void removeDataSourceListener(DataSourceListener dsl) {
        // Remove data source listener implementation
    }

    @Override
    public void addInstanceListener(InstanceListener dsl) {
        // Add instance listener implementation
    }

    @Override
    public void removeInstanceListener(InstanceListener dsl) {
        // Remove instance listener implementation
    }
}
