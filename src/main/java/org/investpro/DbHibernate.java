package org.investpro;

import jakarta.persistence.*;
import javafx.scene.control.Alert;
import org.jetbrains.annotations.NotNull;
import weka.gui.beans.DataSourceListener;
import weka.gui.beans.InstanceListener;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;

import static jakarta.persistence.Persistence.createEntityManagerFactory;
import static org.investpro.Currency.db1;
import static org.investpro.Exchange.logger;

public class DbHibernate implements Db {

    final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;

    public DbHibernate() {
        // Initialize EntityManagerFactory with necessary properties
        entityManagerFactory = createEntityManagerFactory(
                "User",
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
            // dropTables();
            // Create users' table
            entityManager.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT ," +
                            "username VARCHAR(255) NOT NULL UNIQUE," +
                            "password VARCHAR(255) NOT NULL," +
                            "email VARCHAR(255) NOT NULL UNIQUE" +
                            ")"
            ).executeUpdate();

            // Create currency table
            entityManager.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS currencies (" +
                            "currency_id INTEGER PRIMARY KEY AUTOINCREMENT ," +
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
                            "id INTEGER PRIMARY KEY AUTOINCREMENT  NOT NULL," +
                            "user_id INTEGER NOT NULL," +
                            "currency_id INTEGER NOT NULL," +
                            "amount DECIMAL(10, 2) NOT NULL," +
                            "FOREIGN KEY (user_id) REFERENCES users(id)," +
                            "FOREIGN KEY (currency_id) REFERENCES currencies(id)" +
                            ")"
            ).executeUpdate();

            // Create trade table
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
                            "id INTEGER PRIMARY KEY AUTOINCREMENT  NOT NULL," +
                            "user_id INTEGER NOT NULL," +
                            "currency_id INTEGER NOT NULL," +
                            "amount DECIMAL(10, 2) NOT NULL," +
                            "timestamp DATETIME NOT NULL," +
                            "FOREIGN KEY (user_id) REFERENCES users(id)," +
                            "FOREIGN KEY (currency_id) REFERENCES currencies(id)" +
                            ")"
            ).executeUpdate();

            // Create account table
            entityManager.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS accounts (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT  NOT NULL," +
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
                            "id INTEGER PRIMARY KEY AUTOINCREMENT  NOT NULL," +
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

            enableSQLiteForeignKeys(entityManager);
        } catch (Exception e) {
            logger.error("Error during table creation", e);
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
        }

    }

    private void enableSQLiteForeignKeys(@NotNull EntityManager entityManager) {
        entityManager.getTransaction().begin();
        Query query = entityManager.createNativeQuery("PRAGMA foreign_keys = ON;");
        query.executeUpdate();
        entityManager.getTransaction().commit();
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
    public void createTables() {
        // Implement table creation logic (already handled in constructor)
    }

    @Override
    public void truncateTables() {
        // Implement table truncation logic
    }
    @Override
    public void insert(String tableName, String columnName, String value) {
        String sql = String.format("INSERT INTO %s (%s) VALUES (?)", tableName, columnName);  // Correct the query
        EntityTransaction transaction = entityManager.getTransaction();

        try {
            transaction.begin();
            entityManager.createNativeQuery(sql)
                    .setParameter(1, value)  // Use positional parameter for the value
                    .executeUpdate();
            transaction.commit();
        } catch (Exception e) {
            if (transaction.isActive()) {
                transaction.rollback();  // Rollback in case of an error
            }
            throw new RuntimeException("Error inserting data", e);
        }
    }


    @Override
    public void update(String tableName, String columnName, String value) throws SQLException {
        String sql = String.format("UPDATE %s SET %s = ? WHERE " + columnName + " = ?", tableName, columnName);
        entityManager.getTransaction().begin();
        entityManager.createNativeQuery(sql)
                .setParameter(columnName, value)
                .executeUpdate();
        entityManager.getTransaction().commit();
    }


    @Override
    public Connection getConnection(String username, String password) {
        return null;
    }
    @Override
    public Currency getCurrency(String code) {
        // Use native query to get raw result set
        Query query = db1.entityManager.createNativeQuery("SELECT currencyType, fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, image FROM currencies WHERE code = :code");
        query.setParameter("code", code);

        try {
            // Get the result as a single row
            Object[] result = (Object[]) query.getSingleResult();

            // Map the result set to a Currency object
            Currency currency = new Currency();
            currency.setCurrencyType(CurrencyType.valueOf(result[0].toString()));
            currency.setFullDisplayName(result[1].toString());
            currency.setShortDisplayName(result[2].toString());
            currency.setCode(result[3].toString());
            currency.setFractionalDigits(Integer.parseInt(result[4].toString()));
            currency.setSymbol(result[5].toString());
            currency.setImage(result[6].toString());

            return currency;

        } catch (NoResultException e) {
            // Show an error message if the currency is not found

            Currency currency = new Currency();


            currency.setCurrencyType(CurrencyType.CRYPTO);
            currency.setFullDisplayName(code);
            currency.setShortDisplayName(code);
            currency.setCode(code);
            currency.setFractionalDigits(8);
            currency.setSymbol(code);
            currency.setImage(code + ".png");
            ArrayList<Currency> currencyArrayList = new ArrayList<>();
            currencyArrayList.add(currency);

            save(currencyArrayList);
            return currency;

        } catch (Exception e) {
            // Handle other unexpected exceptions
            new Messages(Alert.AlertType.ERROR, "An error occurred while fetching the currency: " + e.getMessage());
            return null;
        }
    }


    @Override
    public void save(@NotNull ArrayList<Currency> currencyList) {
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();

            for (Currency currency : currencyList) {


                // Check if the currency already exists
                Query query = db1.entityManager.createNativeQuery("SELECT * FROM currencies WHERE code = :code");
                query.setParameter("code", currency.getCode());
                if (!query.getResultList().isEmpty()) {
                    logger.error("{} already exists.", currency.getCode());
                    continue; // Skip this currency but continue with others
                }

                // Insert the currency using parameterized queries
                entityManager.createNativeQuery(
                                "INSERT INTO currencies (currency_id,currencyType, fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, image) " +
                                        "VALUES (:currency_id,:currencyType, :fullDisplayName, :shortDisplayName, :code, :fractionalDigits, :symbol, :image)"
                        )
                        .setParameter("currency_id", currency.getCurrencyId())
                        .setParameter("currencyType", currency.getCurrencyType())
                        .setParameter("fullDisplayName", currency.getFullDisplayName())
                        .setParameter("shortDisplayName", currency.getShortDisplayName())
                        .setParameter("code", currency.getCode())
                        .setParameter("fractionalDigits", currency.getFractionalDigits())
                        .setParameter("symbol", currency.getSymbol())
                        .setParameter("image", currency.getImage())
                        .executeUpdate();

                transaction.commit();
                logger.info("Currencies saved successfully");
            }
            // Enable SQLite foreign keys after saving currencies
            enableSQLiteForeignKeys(entityManager);

        } catch (Exception e) {
            logger.error("Error saving currencies", e);
            if (transaction.isActive()) {
                transaction.rollback();
            }
        }
    }


    /**
     */
    @Override
    public Connection getConnection() {
        return null;
    }


    @Override
    public void setLogWriter(PrintWriter out) {
        // Implement log writer setup
        logger.info("Log writer set to {}", out);
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
