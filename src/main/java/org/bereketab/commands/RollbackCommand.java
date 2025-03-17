package org.bereketab.commands; // Groups this with other CLI commands: keeps your project tidy.

import org.bereketab.MigrationService; // Links to the core logic: uses its rollback functionality.
import org.slf4j.Logger; // For logging (covered elsewhere).
import org.slf4j.LoggerFactory; // Creates the logger (covered elsewhere).
import picocli.CommandLine.Command; // Marks this as a CLI subcommand: ties to PicoCLI.
import java.io.IOException; // Handles file errors: e.g., rollback script not found.
import java.sql.Connection; // DB connection: queries history and runs rollback.
import java.sql.ResultSet; // Retrieves query results: finds the last migration.
import java.sql.SQLException; // Catches DB errors: e.g., query or rollback failures.
import java.sql.Statement; // Runs raw SQL: fetches the latest migration.

@Command(name = "rollback", description = "Rollback the last applied migration") // Defines this as the `rollback` subcommand: tells PicoCLI its purpose (e.g., `migration-tool rollback`).
public class RollbackCommand implements Runnable { // Implements Runnable: PicoCLI calls run() when `rollback` is executed.
    private final Logger logger = LoggerFactory.getLogger(RollbackCommand.class); // Logger for this class: logs rollback events (setup covered elsewhere).
    private final MigrationService service; // Holds MigrationService: uses its dataSource and rollback method.

    public RollbackCommand(MigrationService service) { // Constructor: ties this command to the shared migration service.
        this.service = service; // Stores the service: ensures consistent DB access.
    }

    @Override
    public void run() { // Entry point: runs when `rollback` is called, undoes the last migration.
        try (Connection conn = service.dataSource.getConnection()) { // Gets a DB connection: scoped for this operation, auto-closes.
            String sql = "SELECT version, file_name FROM migration_history ORDER BY applied_time DESC LIMIT 1"; // Queries the latest migration: sorts by time, limits to 1 to get the last one.
            String version; // Holds the version: e.g., "V1": passed to rollback.
            String filename; // Holds the filename: e.g., "V1__create_schema.sql": used to find rollback script.
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) { // Executes the query: gets latest migration details, auto-closes resources.
                if (!rs.next()) { // Checks if there’s a result: no rows means no migrations exist.
                    logger.info("No migrations to rollback"); // Logs no-op: tells user there’s nothing to undo.
                    return; // Exits early: nothing to do if history is empty.
                }
                version = rs.getString("version"); // Extracts version: identifies the migration to rollback.
                filename = rs.getString("file_name"); // Extracts filename: needed to locate the rollback script.
            }
            logger.info("Rolling back: {}", filename); // Logs the action: confirms which migration is being undone.
            service.rollbackMigration(conn, version, filename); // Calls the rollback: executes undo script and cleans history (details in MigrationService).
        } catch (SQLException | IOException e) { // Catches errors: e.g., DB query failure (SQLException) or rollback file missing (IOException).
            logger.error("Rollback failed", e); // Logs the failure: helps user debug.
            throw new RuntimeException("Rollback failed", e); // Stops the command: returns error code to CLI.
        }
    }
}