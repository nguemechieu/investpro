package org.investpro;


import java.io.PrintWriter;
import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

public class DataSource implements javax.sql.DataSource {

    static String password = "Bigboss307#";
    static String root = "root";
    static String dbName = "db";
    static String url = "jdbc:mysql://localhost:3306/" + dbName;

    public DataSource() throws SQLException {

    }

    public Connection createConnection() {
        conn = null;
        try {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                System.out.println("Driver loaded!");
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Cannot find the driver in the classpath!" +
                        "You may want to update the dependency name com.mysql.cj.jdbc.Driver", e);
            }

            Properties props = new Properties();
            props.put("user", root);
            props.put("password", password);
            conn = DriverManager.getConnection(url, props);

            if (conn != null) {

                System.out.println("Successfully connected to MySQL database ");
                return conn;
            }

        } catch (SQLException ex) {
            System.out.println("An error occurred while connecting MySQL databse");
            ex.printStackTrace();
        }
        return conn;
    }    Connection conn = createConnection();

    /**
     * Close the database connection.
     */

    public void close() {
        // TODO Auto-generated method stub


    }

    public boolean findOne(String table, String column, Object value) {
        try {
            System.out.println("Looking for " + table + "." + column);
            PreparedStatement ps = conn.prepareStatement("select * from " + table + " where " +
                    column + " =?");
            ps.setString(1, value.toString());
            ResultSet rs = ps.executeQuery();
            return rs.next();


        } catch (Exception e) {
            e.printStackTrace();
            return false;

        }

    }

    public boolean findAll(String table, String column) {
        try {
            System.out.println("Looking for " + table + "." + column);
            PreparedStatement ps = conn.prepareStatement("select * from " + table + " where " +
                    column + " =?");

            ResultSet rs = ps.executeQuery();
            return rs.next();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean insert(String table, String column, Object value) {
        try {
            System.out.println("Inserting into " + table + "." + column);
            PreparedStatement ps = conn.prepareStatement("insert into " + table + " (" + column + ") values (?)");
            ps.setString(1, value.toString());
            ps.executeUpdate();
            return true;


        } catch (Exception e) {
            e.printStackTrace();
            return false;

        }

    }

    public boolean update(String table, String column, Object value) {
        try {

            System.out.println("Updating into " + table + "." + column);
            PreparedStatement ps = conn.prepareStatement("update " + table + " set " + column +
                    " =? where " + column + " =?");

            ps.setString(1, value.toString());
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean delete(String table, String column) {

        try {
            System.out.println("Deleting from " + table + "." + column);
            PreparedStatement ps = conn.prepareStatement("delete from " + table + " where " +
                    column + " =?");
            ps.executeUpdate();
            return true;


        } catch (Exception e) {
            e.printStackTrace();

            return false;
        }
    }

    public boolean create(String username, String password, String email, String firstName, String lastName,
                          String phone, String middleName, String country, String city, String state, String address, String zip) throws SQLException, ClassNotFoundException, InstantiationException, IllegalAccessException {


        Properties props = new Properties();
        props.put("user", username);
        props.put("password", password);
        conn = DriverManager.getConnection(url, props);
        System.out.println("Successfully connected to MySQL database");
        PreparedStatement ps = conn.prepareStatement("select * from Users");
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            System.out.println(rs.getString(1));


            System.out.println("Successfully connected to MySQL database ");
            ps = conn.prepareStatement("insert into Users (username, password, Email, first_name, last_name, Phone, middle_name, Country, City, State, Address, Zip) values (?,?,?,?,?,?,?,?,?,?,?,?)");
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, email);
            ps.setString(4, firstName);
            ps.setString(5, lastName);
            ps.setString(6, phone);
            ps.setString(7, middleName);
            ps.setString(8, country);
            ps.setString(9, city);
            ps.setString(10, state);
            ps.setString(11, address);
            ps.setString(12, zip);
            ps.executeUpdate()
            ;
            System.out.println("Successfully created User " + username);

            return true;
        } else {

            return false;
        }
    }

    public void setUser(String root) {
        DataSource.root = root;
    }

    public void setPassword(String password) {
        DataSource.password = password;
    }

    public void setUrl(String url) {
        DataSource.url = url;
    }

    /**
     * <p>Attempts to establish a connection with the data source that
     * this {@code DataSource} object represents.
     *
     * @return a connection to the data source
     * @throws SQLException        if a database access error occurs
     * @throws SQLTimeoutException when the driver has determined that the
     *                             timeout value specified by the {@code setLoginTimeout} method
     *                             has been exceeded and has at least tried to cancel the
     *                             current database connection attempt
     */
    @Override
    public Connection getConnection() throws SQLException {
        return null;
    }

    /**
     * <p>Attempts to establish a connection with the data source that
     * this {@code DataSource} object represents.
     *
     * @param username the database user on whose behalf the connection is
     *                 being made
     * @param password the user's password
     * @return a connection to the data source
     * @throws SQLException        if a database access error occurs
     * @throws SQLTimeoutException when the driver has determined that the
     *                             timeout value specified by the {@code setLoginTimeout} method
     *                             has been exceeded and has at least tried to cancel the
     *                             current database connection attempt
     * @since 1.4
     */
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return
                null;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.4
     */
    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * @param out
     * @since 1.4
     */
    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {

    }

    /**
     * {@inheritDoc}
     *
     * @since 1.4
     */
    @Override
    public int getLoginTimeout() throws SQLException {
        return 0;
    }

    /**
     * {@inheritDoc}
     *
     * @param seconds
     * @since 1.4
     */
    @Override
    public void setLoginTimeout(int seconds) throws SQLException {


    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

    /**
     * Create a new {@code ConnectionBuilder} instance
     *
     * @return The ConnectionBuilder instance that was created
     * @throws SQLException                    if an error occurs creating the builder
     * @throws SQLFeatureNotSupportedException if the driver does not support sharding
     * @implSpec The default implementation will throw a {@code SQLFeatureNotSupportedException}
     * @see ConnectionBuilder
     * @since 9
     */
    @Override
    public ConnectionBuilder createConnectionBuilder() throws SQLException {
        return javax.sql.DataSource.super.createConnectionBuilder();
    }

    /**
     * Creates a new {@code ShardingKeyBuilder} instance
     *
     * @return The ShardingKeyBuilder instance that was created
     * @throws SQLException                    if an error occurs creating the builder
     * @throws SQLFeatureNotSupportedException if the driver does not support this method
     * @implSpec The default implementation will throw a {@code SQLFeatureNotSupportedException}.
     * @see ShardingKeyBuilder
     * @since 9
     */
    @Override
    public ShardingKeyBuilder createShardingKeyBuilder() throws SQLException {
        return
                null;
    }

    /**
     * Return the parent Logger of all the Loggers used by this data source. This
     * should be the Logger farthest from the root Logger that is
     * still an ancestor of all of the Loggers used by this data source. Configuring
     * this Logger will affect all of the log messages generated by the data source.
     * In the worst case, this may be the root Logger.
     *
     * @return the parent Logger for this data source
     * @throws SQLFeatureNotSupportedException if the data source does not use
     *                                         {@code java.util.logging}
     * @since 1.7
     */

    /**
     * Returns an object that implements the given interface to allow access to
     * non-standard methods, or standard methods not exposed by the proxy.
     * <p>
     * If the receiver implements the interface then the result is the receiver
     * or a proxy for the receiver. If the receiver is a wrapper
     * and the wrapped object implements the interface then the result is the
     * wrapped object or a proxy for the wrapped object. Otherwise return the
     * the result of calling {@code unwrap} recursively on the wrapped object
     * or a proxy for that result. If the receiver is not a
     * wrapper and does not implement the interface, then an {@code SQLException} is thrown.
     *
     * @param iface A Class defining an interface that the result must implement.
     * @return an object that implements the interface. May be a proxy for the actual implementing object.
     * @throws SQLException If no object found that implements the interface
     * @since 1.6
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    /**
     * Returns true if this either implements the interface argument or is directly or indirectly a wrapper
     * for an object that does. Returns false otherwise. If this implements the interface then return true,
     * else if this is a wrapper then return the result of recursively calling {@code isWrapperFor} on the wrapped
     * object. If this does not implement the interface and is not a wrapper, return false.
     * This method should be implemented as a low-cost operation compared to {@code unwrap} so that
     * callers can use this method to avoid expensive {@code unwrap} calls that may fail. If this method
     * returns true then calling {@code unwrap} with the same argument should succeed.
     *
     * @param iface a Class defining an interface.
     * @return true if this implements the interface or directly or indirectly wraps an object that does.
     * @throws SQLException if an error occurs while determining whether this is a wrapper
     *                      for an object with the given interface.
     * @since 1.6
     */
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }


}
