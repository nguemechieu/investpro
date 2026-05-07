package org.investpro.repository;

import lombok.extern.slf4j.Slf4j;

import org.investpro.data.Db1;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.util.Properties;

/**
 * Factory for creating repository instances.
 * Manages the singleton Db1 instance and provides methods to create repository
 * implementations.
 */
@Slf4j
public class RepositoryFactory {
    private static final Db1 db1Instance;

    static {
        try {
            db1Instance = initializeDatabase();
        } catch (Exception e) {
            log.error("Failed to initialize database for repositories", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Initialize the database connection from configuration properties.
     * Loads configuration from conf.properties in the classpath.
     *
     * @return initialized Db1 instance
     * @throws ClassNotFoundException if SQLite driver cannot be found
     */
    private static @NotNull Db1 initializeDatabase() throws ClassNotFoundException {
        Properties conf = new Properties();
        try {
            var resourceStream = RepositoryFactory.class.getClassLoader().getResourceAsStream("conf.properties");
            if (resourceStream != null) {
                conf.load(resourceStream);
            } else {
                log.debug("conf.properties not found in classpath, using default database configuration");
            }
        } catch (IOException e) {
            log.warn("Failed to load conf.properties, using default database configuration", e);
        }

        Db1 db1 = new Db1(conf);
        db1.createTables();
        return db1;
    }

    /**
     * Get the singleton Db1 instance.
     *
     * @return the Db1 database instance
     */
    public static Db1 getDatabase() {
        if (db1Instance == null) {
            throw new IllegalStateException("Database has not been initialized");
        }
        return db1Instance;
    }

    /**
     * Create a TradeRepository instance.
     *
     * @return a new TradeRepositoryImpl instance
     */
    public static TradeRepository createTradeRepository() {
        return new TradeRepositoryImpl();
    }

    /**
     * Create an OrderRepository instance.
     *
     * @return a new OrderRepositoryImpl instance
     */
    public static OrderRepository createOrderRepository() {
        return new OrderRepositoryImpl(db1Instance);
    }

    /**
     * Create a CurrencyRepository instance.
     *
     * @return a new CurrencyRepositoryImpl instance
     */
    public static CurrencyRepository createCurrencyRepository() {
        return new CurrencyRepositoryImpl(db1Instance);
    }
}
