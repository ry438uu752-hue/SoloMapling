package database;

import tools.DatabaseConnection;

/** Runs the Liquibase changelog without starting the game servers. */
public final class DatabaseMigrationRunner {
    private DatabaseMigrationRunner() {
    }

    public static void main(String[] args) {
        if (!DatabaseConnection.initializeConnectionPool()) {
            throw new IllegalStateException("Failed to connect to the configured database");
        }

        try {
            DatabaseMigrations.runDatabaseMigrations();
            System.out.println("Database schema is up to date.");
        } finally {
            DatabaseConnection.closeConnectionPool();
        }
    }
}
