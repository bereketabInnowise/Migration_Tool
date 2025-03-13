
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

public class MigrationService {
    private static final Logger logger = LoggerFactory.getLogger(MigrationService.class);
    public final HikariDataSource dataSource;
    private String migrationsDir = "migrations";

    public MigrationService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        initMigrationHistoryTable();
    }
    public void setMigrationsDir(String migrationsDir){
        this.migrationsDir = migrationsDir;
    }
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

    public List<Path> getMigrationFiles() throws IOException {
        Path dir = Paths.get(migrationsDir);
        return Files.list(dir)
                .filter(path -> path.toString().endsWith(".sql") && !path.toString().contains("_rollback.sql"))
                .sorted()
                .toList();
    }

    public boolean isMigrationApplied(Connection conn, String version) throws SQLException {
        String sql = "SELECT COUNT(*) FROM migration_history WHERE version = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            if (e.getSQLState().equals("42P01")) return false;
            throw e;
        }
    }

    public String getExistingChecksum(Connection conn, String version) throws SQLException {
        String sql = "SELECT checksum FROM migration_history WHERE version = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("checksum");
            return null;
        }
    }

    public void applyMigration(Connection conn, String version, String filename, String sql) throws SQLException {
        conn.setAutoCommit(false);
        try {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
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

    public void rollbackMigration(Connection conn, String version, String filename) throws SQLException, IOException {
        conn.setAutoCommit(false);
        try {

            // Look for a rollback script (e.g., V1__create_schema_rollback.sql)
            String rollbackFileName = filename.replace(".sql", "_rollback.sql");
            Path rollbackPath = Paths.get("target/test-migrations", rollbackFileName);
            if (!Files.exists(rollbackPath)){
                rollbackPath = Paths.get( migrationsDir, rollbackFileName);
            }
            if (Files.exists(rollbackPath)) {
                String rollbackSql = Files.readString(rollbackPath);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(rollbackSql);
                }
                String deleteSql = "DELETE FROM migration_history WHERE version = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setString(1, version);
                    deleteStmt.executeUpdate();
                }
                conn.commit();
                logger.info("Executed rollback script: {}", rollbackFileName);
            } else {
                logger.warn("No rollback script found for {}", filename);
            }
        } catch (SQLException | IOException e) {
            conn.rollback();
            throw e;
        }
    }

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