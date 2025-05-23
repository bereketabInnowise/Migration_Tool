package org.bereketab;

import com.zaxxer.hikari.HikariDataSource;
import org.bereketab.commands.MigrateCommand;
import org.bereketab.commands.RollbackCommand;
import org.bereketab.migrationLibrary.DatabaseConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class MigrationIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("mydb")
            .withUsername("myuser")
            .withPassword("mypassword");

    @BeforeAll
    static void setup() throws IOException {
        // Set up test migration files and datasource
        Path migrationsDir = Path.of("target/test-migrations");
        Files.createDirectories(migrationsDir);
        Files.writeString(migrationsDir.resolve("V1__create_users.sql"),
                "CREATE TABLE users (id SERIAL PRIMARY KEY, name VARCHAR(255));\nINSERT INTO users (name) VALUES ('Test');");
        Files.writeString(migrationsDir.resolve("V1__create_users_rollback.sql"),
                "DROP TABLE users;");

        HikariDataSource testDataSource = new HikariDataSource();
        testDataSource.setJdbcUrl(postgres.getJdbcUrl());
        testDataSource.setUsername(postgres.getUsername());
        testDataSource.setPassword(postgres.getPassword());
        try {
            // Use reflection to inject test datasource into DatabaseConfig
            Field field = DatabaseConfig.class.getDeclaredField("dataSource");
            field.setAccessible(true);
            field.set(null, testDataSource);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set test datasource", e);
        }
    }

    @Test
    void testMigrateAndRollback() throws Exception {
        // Test migration application and rollback
        MigrationService service = new MigrationService(DatabaseConfig.getDataSource()) {
            @Override
            public List<Path> getMigrationFiles() throws IOException {
                return Files.list(Path.of("target/test-migrations"))
                        .filter(path -> path.toString().endsWith(".sql") && !path.toString().contains("_rollback.sql"))
                        .sorted()
                        .toList();
            }
        };

        new MigrateCommand(service).run();
        verifyMigrationApplied(service);
        new RollbackCommand(service).run();
        verifyRollback(service);
    }

    private void verifyMigrationApplied(MigrationService service) throws Exception {
        // Verify migration applied correctly
        try (Connection conn = service.dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
            rs.next();
            assertEquals(1, rs.getInt(1));
            rs = stmt.executeQuery("SELECT COUNT(*) FROM migration_history");
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    private void verifyRollback(MigrationService service) throws Exception {
        // Verify rollback removed migration and table
        try (Connection conn = service.dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM migration_history");
            rs.next();
            assertEquals(0, rs.getInt(1));
            // Use to_regclass to check table existence in Postgres
            ResultSet rsUsers = stmt.executeQuery("SELECT to_regclass('users')");
            rsUsers.next();
            assertTrue(rsUsers.getObject(1) == null);
        }
    }
}