package org.investpro;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.jetbrains.annotations.NotNull;
import weka.gui.beans.DataSourceListener;
import weka.gui.beans.InstanceListener;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;

import static jakarta.persistence.Persistence.createEntityManagerFactory;

public class DbHibernate implements Db {
    final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;
    private Connection conn;


    public DbHibernate() {
        entityManagerFactory = createEntityManagerFactory(
                "default", // persistence unit name
                new Properties() {{
                    put("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect");
                    put("hibernate.hbm2ddl.auto", "update");
                    put("hibernate.show_sql", "true");

                }} // Additional Hibernate configuration properties
        ); // Use Jakarta EntityManagerFactory
        entityManager = entityManagerFactory.createEntityManager();

        entityManager.getTransaction().begin();
        entityManagerFactory.createEntityManager().createNativeQuery(
                "CREATE TABLE IF NOT EXISTS users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "username VARCHAR(255) UNIQUE NOT NULL," +
                        "password VARCHAR(255) NOT NULL)"
        );

//        // Load database properties from a file
//        try {
//
//
//            String dbName = "InvestsPro.db";
//            this.conn = DriverManager.getConnection(String.format("jdbc:sqlite:%s", dbName));
//
//            if (conn != null) {
//                logger.info("Connected to database: {}", dbName);
//            }
//            entityManager.createNativeQuery("PRAGMA foreign_keys = ON ");
//
//        } catch (SQLException e) {
//            logger.error("Error connecting to the database", e);
//        }
    }


    @Override
    public void dropTables() {

    }

    @Override
    public Connection getConn() {
        return null;
    }

    /**
     *
     */
    @Override
    public void createTables() throws SQLException {

    }

    /**
     *
     */
    @Override
    public void truncateTables() {

    }

    @Override
    public void insert(String tableName, String columnName, String value) {
        String sql = "INSERT INTO " + tableName + " (" + columnName + ") VALUES (?)";

        entityManager.createNativeQuery(
                sql
        ).setParameter(1, value).executeUpdate();


    }


    @Override
    public void update(
            String tableName,
            String columnName,
            String value
    ) throws SQLException {

        String sql = "UPDATE %s SET %s =? WHERE id =?".formatted(tableName, columnName);

        entityManager.createNativeQuery(
                sql
        ).executeUpdate();


    }


    @Override
    public Currency getCurrency(String code) {


        Currency currency = entityManager.find(Currency.class, code);

        entityManager.getTransaction().commit();
        entityManager.close();
        return currency;
    }


    @Override
    public void save(@NotNull ArrayList<Currency> currency) {

        String sql =
                "INSERT INTO currencies (full_display_name, short_display_name, fractional_digits, symbol, image) VALUES (?,?,?,?,?)";

        entityManager.createNativeQuery(

                sql
        ).executeUpdate();

    }


    @Override
    public int find(String table, String column, String value) {


        return entityManager.createNativeQuery(
                "SELECT id FROM %s WHERE %s =?".formatted(table, column).toUpperCase()).executeUpdate();
    }


    @Override
    public Connection getConnection() throws SQLException {
        return conn;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return conn;
    }


    @Override
    public void setLogWriter(PrintWriter out) {

    }


    @Override
    public void close() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    /**
     * @param dsl a <code>DataSourceListener</code> value
     */
    @Override
    public void addDataSourceListener(DataSourceListener dsl) {

    }

    /**
     * @param dsl a <code>DataSourceListener</code> value
     */
    @Override
    public void removeDataSourceListener(DataSourceListener dsl) {

    }

    /**
     * @param dsl an <code>InstanceListener</code> value
     */
    @Override
    public void addInstanceListener(InstanceListener dsl) {

    }

    /**
     * @param dsl an <code>InstanceListener</code> value
     */
    @Override
    public void removeInstanceListener(InstanceListener dsl) {

    }

    // Other overridden methods like createTables(), dropTables(), etc. go here...

    // Logger methods (getLogWriter(), setLogWriter(), etc.)
}
