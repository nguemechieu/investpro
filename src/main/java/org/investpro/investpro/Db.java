package org.investpro.investpro;

import org.investpro.investpro.model.Candle;
import org.investpro.investpro.model.Currency;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

/**
 * Database access abstraction for InvestPro.
 */
public interface Db extends DataSource {

    // === Schema Control ===
    void createTables();

    void dropTables();

    void truncateTables();

    // === Configuration ===
    String getDbName();

    void setDbName(String dbName);

    String getUserName();

    void setUserName(String userName);

    String getPassword();

    void setPassword(String password);

    String getUrl();

    void setUrl(String url);

    void create(String table, String column, String value);

    String getDriverClassName();

    String getJdbcUrl();

    String getJdbcUsername();

    String getJdbcPassword();

    String getJdbcDriverClassName();

    void close();

    // === Generic Table Operations ===

    /**
     * Finds a single matching row by value and returns the count.
     */
    int find(String table, String column, String value);

    /**
     * Returns all matching rows (as JSON string or another format depending on implementation).
     */
    List<String> findAll(String table, String column, String value);

    void insert(String table, String column, String value);

    void update(String table, String column, String value);

    void delete(String table, String column, String value);

    /**
     * Creates a column entry (or schema entry) dynamically.
     */
    void createColumnIfNotExists(String table, String column, String value);

    /**
     * Retrieves a record by ID value.
     */
    String findById(String table, String column, String value);


    // === Domain-Specific Persistence ===

    void save(Currency currency);

    Currency getCurrency(String code) throws SQLException;

    void save(Candle candle);
}
