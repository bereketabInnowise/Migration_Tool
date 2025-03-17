package org.bereketab.commands; // Groups this with other CLI commands: keeps your project organized.

import org.bereketab.MigrationService; // Links to the migration logic: provides DB access via its dataSource.
import org.slf4j.Logger; // For logging (covered in MigrationService).
import org.slf4j.LoggerFactory; // Creates the logger (covered in MigrationService).
import picocli.CommandLine.Command; // Marks this as a CLI subcommand: ties it to PicoCLI.
import java.sql.Connection; // DB connection: queries migration history.
import java.sql.ResultSet; // Retrieves query results: shows applied migrations.
import java.sql.SQLException; // Handles DB errors: e.g., query failures.
import java.sql.Statement; // Runs raw SQL: fetches history data.

@Command(name = "status", description = "Show applied migrations") // Defines this as the `status` subcommand: tells PicoCLI its purpose (e.g., `migration-tool status`).
public class StatusCommand implements Runnable { // Implements Runnable: PicoCLI calls run() when `status` is executed.
    private final Logger logger = LoggerFactory.getLogger(StatusCommand.class); // Logger for this class: logs status-specific events (setup covered elsewhere).
    private final MigrationService service; // Holds MigrationService: uses its dataSource to access the DB.

    public StatusCommand(MigrationService service) { // Constructor: ties this command to the shared migration service.
        this.service = service; // Stores the service: ensures DB ops go through the same pool.
    }

    @Override
    public void run() { // Entry point for the command: runs when `status` is called, shows applied migrations.
        try (Connection conn = service.dataSource.getConnection()) { // Gets a DB connection: scoped for this query, auto-closes.
            String sql = "SELECT version, file_name, checksum, applied_time FROM migration_history ORDER BY applied_time"; // Queries all migration history: sorts by time to show order of application.
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) { // Executes the query: gets results in a ResultSet, auto-closes resources.
                logger.info("Applied Migrations:"); // Logs a header: signals the start of the status list.
                while (rs.next()) { // Loops through results: each row is an applied migration.
                    logger.info("Version: {}, File: {}, Checksum: {}, Applied: {}",
                            rs.getString("version"), rs.getString("file_name"),
                            rs.getString("checksum"), rs.getTimestamp("applied_time")); // Logs details: shows version, file, checksum, and timestamp for each migration.
                }
            }
        } catch (SQLException e) { // Catches DB errors: e.g., table missing or connection failure.
            logger.error("Status check failed", e); // Logs the failure: helps user debug.
            throw new RuntimeException("Failed to get status", e); // Stops the command: returns error code to CLI.
        }
    }
}