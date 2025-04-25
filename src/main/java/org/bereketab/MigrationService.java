package org.bereketab;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service for managing database migrations, including applying, rolling back, and tracking migrations.
 */
public class MigrationService {
    private static final Logger logger = LoggerFactory.getLogger(MigrationService.class);
    public final HikariDataSource dataSource;
    private String migrationsDir = "migrations";

    public MigrationService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        initMigrationHistoryTable();
    }

    /**
     * Sets the directory where migration SQL files are stored.
     * @param migrationsDir Path to the migrations directory (e.g., "migrations").
     */
    public void setMigrationsDir(String migrationsDir) {
        this.migrationsDir = migrationsDir;
    }

    /**
     * Initializes the `migration_history` table if it doesn't exist, or validates its schema.
     * @throws RuntimeException If the table schema is incompatible.
     */
    private void initMigrationHistoryTable() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS migration_history (
                version VARCHAR(255) PRIMARY KEY,
                file_name VARCHAR(255),
                checksum VARCHAR(255),
                applied_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);

            // Validate schema to ensure required columns exist
            ResultSet rs = conn.getMetaData().getColumns(null, null, "migration_history", null);
            Set<String> requiredColumns = Set.of("version", "file_name", "checksum", "applied_time");
            Set<String> actualColumns = new HashSet<>();
            while (rs.next()) {
                actualColumns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
            if (!actualColumns.containsAll(requiredColumns)) {
                throw new RuntimeException("Existing migration_history table has incompatible schema. Required columns: " + requiredColumns);
            }
            logger.info("Initialized or verified migration_history table");
        } catch (SQLException e) {
            logger.error("Failed to initialize migration_history table", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Returns a sorted list of migration files (excluding rollback scripts).
     * @return List of `.sql` files in the migrations directory, ordered by filename.
     * @throws IOException If the migrations directory cannot be read.
     */
    public List<Path> getMigrationFiles() throws IOException {
        Path dir = Paths.get(migrationsDir);
        return Files.list(dir)
                .filter(path -> path.toString().endsWith(".sql") && !path.toString().contains("_rollback.sql"))
                .sorted()
                .toList();
    }

    /**
     * Checks if a migration (by version) has already been applied.
     * @param conn Active database connection.
     * @param version Migration version (e.g., "V1").
     * @return `true` if the migration is in the history table, `false` otherwise.
     * @throws SQLException If the database query fails.
     */
    public boolean isMigrationApplied(Connection conn, String version) throws SQLException {
        String sql = "SELECT COUNT(*) FROM migration_history WHERE version = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            // Handle case where the history table doesn't exist yet (PostgreSQL-specific error code)
            if (e.getSQLState().equals("42P01")) return false;
            throw e;
        }
    }

    /**
     * Retrieves the checksum of an already-applied migration.
     * @param conn Active database connection.
     * @param version Migration version (e.g., "V1").
     * @return The stored checksum, or `null` if the migration hasn't been applied.
     * @throws SQLException If the database query fails.
     */
    public String getExistingChecksum(Connection conn, String version) throws SQLException {
        String sql = "SELECT checksum FROM migration_history WHERE version = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("checksum");
            return null;
        }
    }

    /**
     * Applies a migration script and records it in the history table.
     * @param conn Active database connection (transaction will be managed here).
     * @param version Migration version (e.g., "V1").
     * @param filename Migration filename (e.g., "V1__create_table.sql").
     * @param sql The SQL script to execute.
     * @throws SQLException If the migration fails or the history update fails.
     */
    public void applyMigration(Connection conn, String version, String filename, String sql) throws SQLException {
        conn.setAutoCommit(false);
        try {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
            // Record the migration in history
            String insertSql = "INSERT INTO migration_history (version, file_name, checksum) VALUES (?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, version);
                insertStmt.setString(2, filename);
                insertStmt.setString(3, calculateChecksum(sql));
                insertStmt.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }

    /**
     * Rolls back a migration by executing its rollback script (if found) and removing its history record.
     * @param conn Active database connection.
     * @param version Migration version (e.g., "V1").
     * @param filename Migration filename (e.g., "V1__create_table.sql").
     * @throws SQLException If the rollback fails.
     * @throws IOException If the rollback script cannot be read.
     */
    public void rollbackMigration(Connection conn, String version, String filename) throws SQLException, IOException {
        conn.setAutoCommit(false);
        try {
            Path rollbackPath = resolveRollbackPath(filename);
            if (rollbackPath != null) {
                executeRollbackScript(conn, rollbackPath, filename);
                deleteMigrationHistory(conn, version);
                conn.commit();
                logger.info("Executed rollback script: {}", filename);
            } else {
                logger.warn("No rollback script found for {}", filename);
                conn.commit(); // Commit even if no rollback script exists (no-op)
            }
        } catch (SQLException | IOException e) {
            conn.rollback();
            throw e;
        }
    }

    /**
     * Resolves the path to a rollback script (checks both `migrationsDir` and `target/test-migrations`).
     * @param filename Base migration filename (e.g., "V1__create_table.sql").
     * @return Path to the rollback script, or `null` if not found.
     */
    private Path resolveRollbackPath(String filename) {
        String rollbackFileName = filename.replace(".sql", "_rollback.sql");
        Path rollbackPath = Paths.get("target/test-migrations", rollbackFileName);
        if (!Files.exists(rollbackPath)) {
            rollbackPath = Paths.get(migrationsDir, rollbackFileName);
        }
        return Files.exists(rollbackPath) ? rollbackPath : null;
    }

    private void executeRollbackScript(Connection conn, Path rollbackPath, String filename) throws IOException, SQLException {
        String rollbackSql = Files.readString(rollbackPath);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(rollbackSql);
        }
    }

    private void deleteMigrationHistory(Connection conn, String version) throws SQLException {
        String deleteSql = "DELETE FROM migration_history WHERE version = ?";
        try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
            deleteStmt.setString(1, version);
            deleteStmt.executeUpdate();
        }
    }

    /**
     * Calculates a SHA-256 checksum for a SQL script (used for detecting changes).
     * @param sql The SQL script to hash.
     * @return Hex-encoded SHA-256 checksum, or "error_checksum" if hashing fails.
     */
    public String calculateChecksum(String sql) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sql.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to calculate checksum", e);
            return "error_checksum";
        }
    }
}