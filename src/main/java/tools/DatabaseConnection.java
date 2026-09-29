package tools;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import config.YamlConfig;
import database.note.NoteRowMapper;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Frz (Big Daddy)
 * @author The Real Spookster - some modifications to this beautiful code
 * @author Ronan - some connection pool to this beautiful code
 */
public class DatabaseConnection {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);
    private static HikariDataSource dataSource;
    private static Jdbi jdbi;

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("Unable to get connection - connection pool is uninitialized");
        }

        return dataSource.getConnection();
    }

    public static Handle getHandle() {
        if (jdbi == null) {
            throw new IllegalStateException("Unable to get handle - connection pool is uninitialized");
        }

        return jdbi.open();
    }

    private static String getDbUrl() {
        return String.format(YamlConfig.config.server.DB_URL_FORMAT, getDbHost(), getDbName());
    }

    private static String getDbHost() {
        String hostOverride = System.getenv("DB_HOST");
        return hostOverride != null ? hostOverride : YamlConfig.config.server.DB_HOST;
    }

    private static String getDbName() {
        String nameOverride = System.getenv("DB_NAME");
        String databaseName = nameOverride != null ? nameOverride : YamlConfig.config.server.DB_NAME;
        if (databaseName == null || databaseName.isBlank()) {
            databaseName = "cosmic"; // Keep compatibility with older config.yaml files.
        }
        if (!databaseName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("DB_NAME must contain only ASCII letters, digits, and underscores");
        }
        return databaseName;
    }

    private static String getDbUser() {
        String userOverride = System.getenv("DB_USER");
        return userOverride != null ? userOverride : YamlConfig.config.server.DB_USER;
    }

    private static String getDbPassword() {
        String passwordOverride = System.getenv("DB_PASS");
        return passwordOverride != null ? passwordOverride : YamlConfig.config.server.DB_PASS;
    }

    private static void ensureDatabaseExists() throws SQLException {
        if (!YamlConfig.config.server.DB_AUTO_CREATE) {
            return;
        }

        String serverUrl = String.format(YamlConfig.config.server.DB_URL_FORMAT, getDbHost(), "");
        String databaseName = getDbName();
        try (Connection connection = DriverManager.getConnection(serverUrl, getDbUser(), getDbPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `" + databaseName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }
    }

    private static HikariConfig getConfig() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(getDbUrl());
        config.setUsername(getDbUser());
        config.setPassword(getDbPassword());

        final int initFailTimeoutSeconds = YamlConfig.config.server.INIT_CONNECTION_POOL_TIMEOUT;
        config.setInitializationFailTimeout(SECONDS.toMillis(initFailTimeoutSeconds));
        config.setConnectionTimeout(SECONDS.toMillis(60)); // Hikari default
        config.setMaximumPoolSize(100); // Hikari default

        config.addDataSourceProperty("cachePrepStmts", true);
        config.addDataSourceProperty("prepStmtCacheSize", 25);
        config.addDataSourceProperty("prepStmtCacheSqlLimit", 2048);

        return config;
    }

    /**
     * Initiate connection to the database
     *
     * @return true if connection to the database initiated successfully, false if not successful
     */
    public static boolean initializeConnectionPool() {
        if (dataSource != null) {
            return true;
        }

        final HikariConfig config = getConfig();
        try {
            ensureDatabaseExists();
        } catch (SQLException e) {
            log.error("Failed to create or access database schema '{}'. Check DB_AUTO_CREATE and database privileges.",
                    getDbName(), e);
            return false;
        }
        log.info("Initializing database connection pool. Connecting to:'{}' with user:'{}'", config.getJdbcUrl(),
                config.getUsername());
        Instant initStart = Instant.now();
        try {
            dataSource = new HikariDataSource(config);
            initializeJdbi(dataSource);
            long initDuration = Duration.between(initStart, Instant.now()).toMillis();
            log.info("Connection pool initialized in {} ms", initDuration);
            return true;
        } catch (Exception e) {
            long timeout = Duration.between(initStart, Instant.now()).getSeconds();
            log.error("Failed to initialize database connection pool. Gave up after {} seconds.", timeout);
        }

        // Timed out - failed to initialize
        return false;
    }

    private static void initializeJdbi(DataSource dataSource) {
        jdbi = Jdbi.create(dataSource)
                .registerRowMapper(new NoteRowMapper());
    }

    public static void closeConnectionPool() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
            jdbi = null;
        }
    }
}
