package org.investpro;

import javax.sql.DataSource;
import java.sql.SQLException;

public interface Db extends DataSource {

    void createTables();

    void dropTables();

    void truncateTables();

    void insertData();

    void updateData();

    void deleteData();

    void createIndexes();

    void dropIndexes();

    void truncateIndexes();

    void createConstraints();

    void dropConstraints();

    void close();

    String getDbName();

    void setDbName(String dbName);

    String getUserName();

    void setUserName(String userName);

    String getPassword();

    void setPassword(String password);

    String getUrl();

    void setUrl(String url);

    String getDriverClassName();

    String getJdbcUrl();

    String getJdbcUsername();

    String getJdbcPassword();

    String getJdbcDriverClassName();

    int find(
            String table,
            String column,
            String value
    );

    void findAll(
            String table,
            String column,
            String value
    );

    void update(
            String table,
            String column,
            String value
    );

    void insert(
            String table,
            String column,
            String value
    );

    void delete(
            String table,
            String column,
            String value
    );

    void create(
            String table,
            String column,
            String value
    );

    void findById(
            String table,
            String column,
            String value
    );


    void save(Currency currency);

    Currency getCurrency(String code) throws SQLException;
}