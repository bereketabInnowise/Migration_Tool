package org.bereketab; // Places this class in your project’s namespace:keeps it distinct from other libraries.

import com.zaxxer.hikari.HikariDataSource; // Brings in the connection pool:lets you manage DB connections efficiently.
import org.slf4j.Logger; // Imports SLF4J’s logger interface:used for logging events and errors.
import org.slf4j.LoggerFactory; // Provides the factory to create a logger instance:ties it to this class.
import java.io.IOException; // Handles exceptions when reading migration files from the filesystem.
import java.nio.file.Files; // Allows file operations:reads SQL scripts from the migrations directory.
import java.nio.file.Path; // Represents file paths:used to locate and process migration files.
import java.nio.file.Paths; // Creates Path objects:converts string paths to usable objects.
import java.security.MessageDigest; // Enables SHA-256 hashing:used to calculate migration checksums.
import java.security.NoSuchAlgorithmException; // Handles errors if SHA-256 isn’t available:rare but possible.
import java.sql.Connection; // JDBC’s connection interface:links to the DB for running SQL.
import java.sql.PreparedStatement; // Prepares SQL queries safely:used for inserting/checking migration history.
import java.sql.ResultSet; // Retrieves query results:e.g., checks if a migration was applied.
import java.sql.SQLException; // Manages DB-related errors:critical for robust DB operations.
import java.sql.Statement; // Executes raw SQL:runs migration scripts.
import java.util.HashSet; // Creates a set for column validation:ensures uniqueness and fast lookups.
import java.util.List; // Returns a list of migration files:handles multiple scripts.
import java.util.Set; // Defines a set interface:used for required column names.

public class MigrationService {
    // This class is the core of the migration tool:handles applying, tracking, and rolling back migrations.
    private static final Logger logger = LoggerFactory.getLogger(MigrationService.class); // Static logger:logs events for this class, shared across instances.
    public final HikariDataSource dataSource; // Stores the connection pool:passed in, immutable, used for all DB access.
    private String migrationsDir = "migrations"; // Default directory for migration files:can be overridden, sets where to look for SQL scripts.

    public MigrationService(HikariDataSource dataSource) { // Constructor:requires a pool to connect to the DB, ties this service to a specific DB setup.
        this.dataSource = dataSource; // Assigns the pool:ensures all DB ops use this connection source.
        initMigrationHistoryTable(); // Sets up the history table on creation:ensures the DB is ready before any migrations.
    }

    public void setMigrationsDir(String migrationsDir) { // Allows changing the migration directory:flexible for CLI or app use (e.g., --migrations-dir).
        this.migrationsDir = migrationsDir; // Updates the directory:points to user-specified location.
    }

    private void initMigrationHistoryTable() { // Creates or verifies the migration_history table:tracks which migrations have run.
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS migration_history (
                version VARCHAR(255) PRIMARY KEY,
                file_name VARCHAR(255),
                checksum VARCHAR(255),
                applied_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """; // Defines the table:stores version (unique), filename, checksum (integrity), and timestamp (when applied).
        try (Connection conn = dataSource.getConnection(); // Gets a connection from the pool:scoped to this block for safety.
             Statement stmt = conn.createStatement()) { // Creates a statement:needed to run the CREATE TABLE SQL.
            stmt.execute(createTableSql); // Runs the SQL:creates the table if it doesn’t exist, skips if it does.
            ResultSet rs = conn.getMetaData().getColumns(null, null, "migration_history", null); // Fetches table metadata:checks actual column names.
            Set<String> requiredColumns = Set.of("version", "file_name", "checksum", "applied_time"); // Defines expected columns:ensures schema matches design.
            Set<String> actualColumns = new HashSet<>(); // Collects existing columns:used to compare against required ones.
            while (rs.next()) { // Loops through metadata:builds the set of current column names.
                actualColumns.add(rs.getString("COLUMN_NAME").toLowerCase()); // Adds each column name:lowercase for case-insensitive comparison.
            }
            if (!actualColumns.containsAll(requiredColumns)) { // Validates schema:ensures all needed columns are present.
                throw new RuntimeException("Existing migration_history table has incompatible schema. Required columns: " + requiredColumns); // Stops if schema is wrong:prevents unpredictable behavior.
            }
            logger.info("Initialized or verified migration_history table"); // Logs success:confirms setup worked.
        } catch (SQLException e) { // Catches DB errors:e.g., connection failure or permission issues.
            logger.error("Failed to initialize migration_history table", e); // Logs the error:helps debug what went wrong.
            throw new RuntimeException("Database initialization failed", e); // Halts with a clear message:user needs to fix DB setup.
        }
    }

    public List<Path> getMigrationFiles() throws IOException { // Finds all migration files:returns them for processing (e.g., migrate, status).
        Path dir = Paths.get(migrationsDir); // Converts the directory string to a Path:points to where migrations live.
        return Files.list(dir) // Lists all files in the directory:starts the filtering process.
                .filter(path -> path.toString().endsWith(".sql") && !path.toString().contains("_rollback.sql")) // Filters for .sql files, excludes rollback scripts:focuses on main migrations.
                .sorted() // Sorts files:ensures migrations run in order (e.g., V1 before V2).
                .toList(); // Converts stream to list:returns usable collection of file paths.
    }

    public boolean isMigrationApplied(Connection conn, String version) throws SQLException { // Checks if a migration has run:prevents duplicates (idempotency).
        String sql = "SELECT COUNT(*) FROM migration_history WHERE version = ?"; // Counts rows for this version: 1 means applied, 0 means not.
        try (PreparedStatement stmt = conn.prepareStatement(sql)) { // Prepares the query:safely binds the version parameter.
            stmt.setString(1, version); // Sets the version to check:e.g., "V1".
            ResultSet rs = stmt.executeQuery(); // Runs the query:gets the count result.
            rs.next(); // Moves to first row:count query always returns one row.
            return rs.getInt(1) > 0; // Returns true if count > 0:migration exists in history.
        } catch (SQLException e) { // Handles DB errors:e.g., table missing or connection issues.
            if (e.getSQLState().equals("42P01")) return false; // Special case:if table doesn’t exist (PostgreSQL code 42P01), assume not applied.
            throw e; // Rethrows other errors:caller needs to handle unexpected issues.
        }
    }

    public String getExistingChecksum(Connection conn, String version) throws SQLException { // Fetches a migration’s checksum:used to validate integrity.
        String sql = "SELECT checksum FROM migration_history WHERE version = ?"; // Queries the checksum:checks what was recorded.
        try (PreparedStatement stmt = conn.prepareStatement(sql)) { // Prepares the query:securely binds version.
            stmt.setString(1, version); // Sets the version:e.g., "V1".
            ResultSet rs = stmt.executeQuery(); // Executes the query:gets the checksum result.
            if (rs.next()) return rs.getString("checksum"); // Returns checksum if found:null if not.
            return null; // Indicates migration not applied:no checksum exists.
        }
    }

    public void applyMigration(Connection conn, String version, String filename, String sql) throws SQLException { // Runs a migration:applies SQL and logs it.
        conn.setAutoCommit(false); // Disables auto-commit:starts a transaction for atomicity (all or nothing).
        try {
            try (Statement stmt = conn.createStatement()) { // Creates a statement:runs the migration SQL (e.g., CREATE TABLE).
                stmt.execute(sql); // Executes the script:applies changes to the DB.
            }
            String insertSql = "INSERT INTO migration_history (version, file_name, checksum) VALUES (?, ?, ?)"; // Prepares to log the migration:tracks it in history.
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) { // Prepares the insert:safely binds values.
                insertStmt.setString(1, version); // Logs the version:e.g., "V1".
                insertStmt.setString(2, filename); // Records the file name:e.g., "V1__create_schema.sql".
                insertStmt.setString(3, calculateChecksum(sql)); // Stores the checksum:ensures integrity for validation.
                insertStmt.executeUpdate(); // Inserts the record:marks migration as applied.
            }
            conn.commit(); // Commits the transaction:locks in both SQL changes and history update.
        } catch (SQLException e) { // Catches DB errors:e.g., syntax error in SQL or DB full.
            conn.rollback(); // Undoes changes if anything fails:keeps DB consistent.
            throw e; // Rethrows:caller (e.g., MigrateCommand) handles or logs it.
        }
    }

    public void rollbackMigration(Connection conn, String version, String filename) throws SQLException, IOException { // Undoes a migration:runs rollback script if available.
        conn.setAutoCommit(false); // Starts a transaction:ensures rollback is atomic.
        try {
            String rollbackFileName = filename.replace(".sql", "_rollback.sql"); // Constructs rollback filename:e.g., "V1__create_schema_rollback.sql".
            Path rollbackPath = Paths.get("target/test-migrations", rollbackFileName); // First checks test dir:supports testing scenarios.
            if (!Files.exists(rollbackPath)) { // If not in test dir:looks in main migrations dir.
                rollbackPath = Paths.get(migrationsDir, rollbackFileName); // Sets path to user-specified dir:default location.
            }
            if (Files.exists(rollbackPath)) { // Proceeds only if rollback script exists:optional feature.
                String rollbackSql = Files.readString(rollbackPath); // Reads the rollback script:contains undo SQL (e.g., DROP TABLE).
                try (Statement stmt = conn.createStatement()) { // Creates statement:runs the rollback SQL.
                    stmt.execute(rollbackSql); // Executes the undo:reverts DB changes.
                }
                String deleteSql = "DELETE FROM migration_history WHERE version = ?"; // Prepares to remove history entry:cleans up record.
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) { // Prepares delete:safely binds version.
                    deleteStmt.setString(1, version); // Targets the version:e.g., "V1".
                    deleteStmt.executeUpdate(); // Deletes the record:migration no longer tracked.
                }
                conn.commit(); // Commits the rollback:locks in DB revert and history cleanup.
                logger.info("Executed rollback script: {}", rollbackFileName); // Logs success:confirms rollback worked.
            } else {
                logger.warn("No rollback script found for {}", filename); // Warns if no script:rollback skipped, still commits (no-op).
            }
        } catch (SQLException | IOException e) { // Catches DB or file errors:e.g., bad rollback SQL or missing file.
            conn.rollback(); // Undoes any partial changes:keeps DB safe.
            throw e; // Rethrows:caller handles or logs the failure.
        }
    }

    public String calculateChecksum(String sql) { // Generates a SHA-256 hash:verifies migration content hasn’t changed.
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); // Sets up SHA-256:strong hash algorithm for integrity.
            byte[] hash = digest.digest(sql.getBytes()); // Computes hash:turns SQL string into a fixed-length byte array.
            StringBuilder hexString = new StringBuilder(); // Builds hex string:converts bytes to readable format.
            for (byte b : hash) { // Loops through bytes:each needs to be converted to hex.
                String hex = Integer.toHexString(0xff & b); // Converts byte to hex:masks to 8 bits, ensures 0-255 range.
                if (hex.length() == 1) hexString.append('0'); // Pads with zero:ensures two digits per byte (e.g., "0a" not "a").
                hexString.append(hex); // Adds hex value:builds the full checksum.
            }
            return hexString.toString(); // Returns the checksum:e.g., 64-char hex string for validation.
        } catch (NoSuchAlgorithmException e) { // Catches rare case:SHA-256 not supported (JVM issue).
            logger.error("Failed to calculate checksum", e); // Logs the error:alerts user to unexpected failure.
            return "error_checksum"; // Fallback value:lets migration proceed but flags issue.
        }
    }
}