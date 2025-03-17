package org.bereketab; //  Places this in your project’s root package: ties it to your migration tool.

import com.zaxxer.hikari.HikariDataSource; //  Sets up a test DB pool: mimics real app config.
import org.bereketab.commands.MigrateCommand; //  Tests the migrate command: applies migrations.
import org.bereketab.commands.RollbackCommand; //  Tests rollback: undoes migrations.
import org.bereketab.migrationLibrary.DatabaseConfig; //  Links to your DB config: injects test datasource.
import org.junit.jupiter.api.BeforeAll; //  Runs setup once: prepares test environment.
import org.junit.jupiter.api.Test; //  Marks test methods: executes validation logic.
import org.testcontainers.containers.PostgreSQLContainer; //  Spins up a test DB: isolates tests from real DBs.
import org.testcontainers.junit.jupiter.Container; //  Manages the test container: auto-starts/stops Postgres.
import org.testcontainers.junit.jupiter.Testcontainers; //  Enables Testcontainers: handles container lifecycle.
import java.io.IOException; //  Handles file creation errors: e.g., writing test migration files.
import java.lang.reflect.Field; //  Uses reflection: injects test datasource into DatabaseConfig.
import java.nio.file.Files; //  Creates/writes test migration files: sets up test data.
import java.nio.file.Path; //  Represents file paths: points to test migration directory.
import java.sql.Connection; //  Queries the test DB: verifies results.
import java.sql.ResultSet; //  Gets query results: checks table counts and existence.
import java.sql.Statement; //  Runs SQL: executes test queries.
import java.util.List; //  Returns migration files: overridden for test control.
import static org.junit.jupiter.api.Assertions.assertEquals; //  Asserts expected values: validates counts.
import static org.junit.jupiter.api.Assertions.assertTrue; //  Asserts conditions: checks table absence.

@Testcontainers //  Activates Testcontainers: manages the Postgres container for all tests.
public class MigrationIntegrationTest { //  Defines the integration test class: verifies migrate/rollback end-to-end.
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("mydb")
            .withUsername("myuser")
            .withPassword("mypassword"); //  Sets up a Postgres container: isolated test DB with specific credentials.

    @BeforeAll
    static void setup() throws IOException { //  Runs once before tests: prepares test files and DB config.
        Path migrationsDir = Path.of("target/test-migrations"); //  Defines test migration dir: keeps test files separate.
        Files.createDirectories(migrationsDir); //  Creates the dir: ensures it exists for test files.

        Files.writeString(migrationsDir.resolve("V1__create_users.sql"),
                "CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(255));\nINSERT INTO users (name) VALUES ('Test');"); //  Writes a test migration: creates users table with one row.
        Files.writeString(migrationsDir.resolve("V1__create_users_rollback.sql"),
                "DROP TABLE users;"); //  Writes rollback script: undoes the migration for testing rollback.

        HikariDataSource testDataSource = new HikariDataSource(); //  Creates a test pool: connects to the container DB.
        testDataSource.setJdbcUrl(postgres.getJdbcUrl()); //  Points to test DB: uses container’s JDBC URL.
        testDataSource.setUsername(postgres.getUsername()); //  Sets test username: matches container config.
        testDataSource.setPassword(postgres.getPassword()); //  Sets test password: matches container config.
        try {
            Field field = DatabaseConfig.class.getDeclaredField("dataSource"); //  Gets the static dataSource field: needs to override it.
            field.setAccessible(true); //  Bypasses private access: allows reflection to modify it.
            field.set(null, testDataSource); //  Injects test datasource: ensures DatabaseConfig uses the container DB.
        } catch (Exception e) { //  Catches reflection errors: e.g., field not found.
            throw new RuntimeException("Failed to set test datasource", e); //  Stops setup: test can’t run without DB.
        }
    }

    @Test
    void testMigrateAndRollback() throws Exception { //  Tests migrate and rollback: verifies full lifecycle works.
        MigrationService service = new MigrationService(DatabaseConfig.getDataSource()) { //  Creates a test-specific MigrationService: uses injected test DB.
            @Override
            public List<Path> getMigrationFiles() throws IOException { //  Overrides file lookup: points to test migrations dir.
                return Files.list(Path.of("target/test-migrations")) //  Lists files from test dir: controls test scope.
                        .filter(path -> path.toString().endsWith(".sql") && !path.toString().contains("_rollback.sql")) //  Filters for migration files: excludes rollback (matches MigrationService logic).
                        .sorted() //  Sorts files: ensures predictable order (V1 first).
                        .toList(); //  Returns list: feeds test migrations to service.
            }
        };

        // Apply migrations
        new MigrateCommand(service).run(); //  Runs migrate: applies V1__create_users.sql to test DB.
        try (Connection conn = service.dataSource.getConnection(); //  Queries test DB: verifies migration result.
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next(); //  Moves to result: count query returns one row.
            assertEquals(1, rs.getInt(1)); //  Checks users table: ensures 1 row was inserted by V1.
        }
        try (Connection conn = service.dataSource.getConnection(); //  Queries migration_history: verifies tracking.
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM migration_history")) {
            rs.next(); //  Moves to result: count query returns one row.
            assertEquals(1, rs.getInt(1)); //  Confirms 1 migration recorded: V1__ was applied.
        }

        // Rollback V1
        new RollbackCommand(service).run(); //  Runs rollback: undoes V1 using its rollback script.
        try (Connection conn = service.dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM migration_history"); //  Checks history: verifies cleanup.
            rs.next();
            assertEquals(0, rs.getInt(1)); //  Ensures history is empty: V1 was removed.
            ResultSet rsUsers = stmt.executeQuery("SELECT to_regclass('users')"); //  Checks if users table exists: Postgres-specific way to test table presence.
            rsUsers.next();
            assertTrue(rsUsers.getObject(1) == null); //  Confirms table is gone: rollback dropped it (null means non-existent).
        }
    }
}