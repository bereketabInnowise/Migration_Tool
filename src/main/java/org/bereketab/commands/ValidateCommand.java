package org.bereketab.commands; // Groups this with other CLI commands: organizes your project.

import org.bereketab.MigrationService; // Connects to the migration logic: uses its methods for validation.
import org.slf4j.Logger; // For logging (covered elsewhere).
import org.slf4j.LoggerFactory; // Creates the logger (covered elsewhere).
import picocli.CommandLine.Command; // Marks this as a CLI subcommand: ties to PicoCLI.
import java.io.IOException; // Handles file read errors: e.g., missing migration files.
import java.nio.file.Files; // Reads file contents: loads SQL for checksum calculation.
import java.nio.file.Path; // Represents migration file paths: from getMigrationFiles().
import java.sql.Connection; // DB connection: checks migration history.
import java.sql.SQLException; // Catches DB errors: e.g., connection issues.

@Command(name = "validate", description = "Validate applied migrations against files") // Defines this as the `validate` subcommand: tells PicoCLI its purpose (e.g., `migration-tool validate`).
public class ValidateCommand implements Runnable { // Implements Runnable: PicoCLI calls run() when `validate` is executed.
    private final Logger logger = LoggerFactory.getLogger(ValidateCommand.class); // Logger for this class: logs validation events (setup covered elsewhere).
    private final MigrationService service; // Holds MigrationService: uses its dataSource and methods for DB checks.

    public ValidateCommand(MigrationService service) { // Constructor: links this command to the shared migration service.
        this.service = service; // Stores the service: ensures consistent DB access.
    }

    @Override
    public void run() { // Entry point: runs when `validate` is called, checks migration integrity.
        try {
            for (Path file : service.getMigrationFiles()) { // Loops through migration files: validates each against DB history.
                String filename = file.getFileName().toString(); // Gets the filename: e.g., "V1__create_schema.sql" for logging.
                String version = filename.split("__")[0]; // Extracts version: e.g., "V1": matches against migration_history.
                try (Connection conn = service.dataSource.getConnection()) { // Opens a DB connection: scoped for this check, auto-closes.
                    if (service.isMigrationApplied(conn, version)) { // Checks if migration ran: only validates applied ones.
                        String existingChecksum = service.getExistingChecksum(conn, version); // Fetches stored checksum: what’s in the DB.
                        String currentChecksum = service.calculateChecksum(Files.readString(file)); // Computes current file’s checksum: checks against DB.
                        if (!existingChecksum.equals(currentChecksum)) { // Compares checksums: detects if file changed post-application.
                            logger.error("Validation failed for {}: Checksum mismatch (DB: {}, File: {})",
                                    filename, existingChecksum, currentChecksum); // Logs failure: alerts user to mismatch, shows both values.
                            break; // Stops validation: first failure halts process (could continue if you want all errors).
                        }
                        logger.info("Validated: {}", filename); // Logs success: confirms this migration matches its DB record.
                    } else {
                        logger.warn("Migration {} in files but not applied", filename); // Warns if file exists but isn’t in DB: hints at pending migrations.
                    }
                }
            }
        } catch (IOException | SQLException e) { // Catches errors: e.g., file read failure (IOException) or DB issue (SQLException).
            logger.error("Validation failed", e); // Logs the error: helps debug what broke.
            throw new RuntimeException("Validation failed", e); // Stops the command: returns error code to CLI.
        }
    }
}