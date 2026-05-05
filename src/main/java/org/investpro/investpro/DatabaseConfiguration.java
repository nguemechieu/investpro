package org.investpro.investpro;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public final class DatabaseConfiguration {
    public static final String MODE_LOCAL = "LOCAL";
    public static final String MODE_CUSTOM = "CUSTOM";

    private final String mode;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String dialect;
    private final String driverClass;
    private final String description;

    private DatabaseConfiguration(String mode, String jdbcUrl, String username, String password,
                                  String dialect, String driverClass, String description) {
        this.mode = mode;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.dialect = dialect;
        this.driverClass = driverClass;
        this.description = description;
    }

    public static DatabaseConfiguration fromProperties(Properties properties) {
        String mode = properties.getProperty("DB_MODE", MODE_LOCAL).trim().toUpperCase(Locale.ROOT);
        if (MODE_CUSTOM.equals(mode)) {
            return custom(properties);
        }
        return local(properties);
    }

    public static DatabaseConfiguration local(Properties properties) {
        Path databasePath = AppFiles.resolveInAppHome("investpro-db");
        String defaultUrl = "jdbc:h2:file:" + normalizePath(databasePath)
                + ";AUTO_SERVER=TRUE;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

        return new DatabaseConfiguration(
                MODE_LOCAL,
                properties.getProperty("DB_LOCAL_URL", defaultUrl).trim(),
                properties.getProperty("DB_LOCAL_USER", "sa").trim(),
                properties.getProperty("DB_LOCAL_PASSWORD", ""),
                "org.hibernate.dialect.H2Dialect",
                "org.h2.Driver",
                "Local embedded database"
        );
    }

    public static DatabaseConfiguration custom(Properties properties) {
        String jdbcUrl = properties.getProperty("DB_URL", "").trim();
        if (jdbcUrl.isEmpty()) {
            String host = properties.getProperty("DB_HOST", "localhost").trim();
            String port = properties.getProperty("DB_PORT", "3306").trim();
            String database = properties.getProperty("DB_NAME", "investpro").trim();
            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        }

        String dialect = properties.getProperty("DB_DIALECT", "").trim();
        if (dialect.isEmpty()) {
            dialect = inferDialect(jdbcUrl);
        }

        String driverClass = properties.getProperty("DB_DRIVER", "").trim();
        if (driverClass.isEmpty()) {
            driverClass = inferDriverClass(jdbcUrl);
        }

        return new DatabaseConfiguration(
                MODE_CUSTOM,
                jdbcUrl,
                properties.getProperty("DB_USER", "root").trim(),
                properties.getProperty("DB_PASSWORD", ""),
                dialect,
                driverClass,
                "Custom database"
        );
    }

    public boolean isCustom() {
        return MODE_CUSTOM.equals(mode);
    }

    public boolean isLocal() {
        return MODE_LOCAL.equals(mode);
    }

    public String getMode() {
        return mode;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDialect() {
        return dialect;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public String getDescription() {
        return description;
    }

    private static String inferDialect(String jdbcUrl) {
        String normalized = jdbcUrl.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("jdbc:mysql:")) {
            return "org.hibernate.dialect.MySQLDialect";
        }
        if (normalized.startsWith("jdbc:h2:")) {
            return "org.hibernate.dialect.H2Dialect";
        }
        if (normalized.startsWith("jdbc:postgresql:")) {
            return "org.hibernate.dialect.PostgreSQLDialect";
        }
        if (normalized.startsWith("jdbc:sqlserver:")) {
            return "org.hibernate.dialect.SQLServerDialect";
        }
        return "";
    }

    private static String inferDriverClass(String jdbcUrl) {
        String normalized = jdbcUrl.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (normalized.startsWith("jdbc:h2:")) {
            return "org.h2.Driver";
        }
        if (normalized.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        if (normalized.startsWith("jdbc:sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        return "";
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
