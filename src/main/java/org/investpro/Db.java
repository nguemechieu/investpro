package org.investpro;


import weka.gui.beans.DataSource;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;

public interface Db extends DataSource {

  void dropTables();



  void createTables() throws SQLException;

  void truncateTables();

  void insert(
          String tableName,
          String columnName,
          String value
  ) throws SQLException;

  void update(
          String tableName,
          String columnName,
          String value
  ) throws SQLException;


  Connection getConnection(String username, String password) throws SQLException;

  void setLogWriter(PrintWriter out) throws SQLException;

  void close();

  // Additional CRUD operations
  Currency getCurrency(String code) throws Exception;

  void save(ArrayList<Currency> currency) throws SQLException;


  Connection getConnection() throws SQLException;
}