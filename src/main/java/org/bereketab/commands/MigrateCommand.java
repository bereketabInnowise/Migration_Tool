package org.bereketab.commands; // Places this in a subpackage: groups CLI commands together.

import org.bereketab.MigrationService; // Links to the core migration logic: this command uses it to apply migrations.
import org.slf4j.Logger; // For logging: tracks progress and errors (covered in MigrationService).
import org.slf4j.LoggerFactory; // Creates the logger (covered in MigrationService).
import picocli.CommandLine.Command; // Marks this as a CLI subcommand: ties it to PicoCLI.
import java.io.IOException; // Handles file read errors: e.g., missing SQL files.
import java.nio.file.Files; // Reads migration file contents: loads SQL to execute.
import java.nio.file.Path; // Represents migration file paths: passed from getMigrationFiles().
import java.sql.Connection; // DB connection: runs migrations transactionally.
import java.sql.SQLException; // Catches DB errors: e.g., SQL syntax issues.

@Command(name = "migrate", description = "Apply pending migrations") // Defines this as the `migrate` subcommand: tells PicoCLI its purpose for CLI usage (e.g., `migration-tool migrate`).
public class MigrateCommand implements Runnable { // Implements Runnable: PicoCLI calls run() when `migrate` is executed.
    private final Logger logger = LoggerFactory.getLogger(MigrateCommand.class); // Logger for this class: logs migrate-specific events (setup covered in MigrationService).
    private final MigrationService service; // Holds the MigrationService instance: passed in to access DB and migration logic.

    public MigrateCommand(MigrationService service) { // Constructor: requires MigrationService to tie this command to the core logic.
        this.service = service; // Stores the service: ensures this command uses the shared migration functionality.
    }

    @Override
    public void run() { // Entry point for the command: runs when `migrate` is called, applies all pending migrations.
        try {
            logger.info("Starting migration process..."); // Logs the start: lets user know the process kicked off.
            for (Path file : service.getMigrationFiles()) { // Loops through migration files: processes each one in order (sorted by MigrationService).
                String filename = file.getFileName().toString(); // Extracts the filename: e.g., "V1__create_schema.sql" for logging and tracking.
                String version = filename.split("__")[0]; // Parses the version: e.g., "V1": used as the unique key in migration_history.
                try (Connection conn = service.dataSource.getConnection()) { // Gets a DB connection: scoped for this migration, auto-closes via try-with-resources.
                    if (service.isMigrationApplied(conn, version)) { // Checks if this version ran: skips if already applied (idempotency).
                        String existingChecksum = service.getExistingChecksum(conn, version); // Gets the stored checksum: compares to detect changes.
                        String currentChecksum = service.calculateChecksum(Files.readString(file)); // Calculates current file’s checksum: verifies against history.
                        if (!existingChecksum.equals(currentChecksum)) { // Compares checksums: warns if the file changed since last run.
                            logger.warn("Migration {} has changed! Existing: {}, Current: {}", filename, existingChecksum, currentChecksum); // Alerts user: changed migrations could break consistency.
                        }
                        logger.info("Skipping: {}", filename); // Logs skip: confirms this migration is already done.
                        continue; // Moves to next file: nothing to do here.
                    }
                    String sql = Files.readString(file); // Reads the SQL script: loads the commands to execute (e.g., CREATE TABLE).
                    logger.info("Applying: {} (checksum: {})", filename, service.calculateChecksum(sql)); // Logs before applying: shows what’s running and its checksum for transparency.
                    service.applyMigration(conn, version, filename, sql); // Applies the migration: runs SQL and logs it in migration_history (details in MigrationService).
                }
            }
        } catch (IOException | SQLException e) { // Catches errors: e.g., file not found (IOException) or DB failure (SQLException).
            logger.error("Migration failed", e); // Logs the failure: helps user debug what went wrong.
            throw new RuntimeException("Migration failed", e); // Stops the command: returns error code to CLI (non-zero exit).
        }
    }
}