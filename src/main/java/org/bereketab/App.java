package org.bereketab; // Groups this class with your project: keeps it in your namespace.

import org.bereketab.commands.MigrateCommand; // Imports the migrate command: handles applying migrations.
import org.bereketab.commands.RollbackCommand; // Imports rollback: lets users undo migrations.
import org.bereketab.commands.StatusCommand; // Imports status: shows what’s applied or pending.
import org.bereketab.commands.ValidateCommand; // Imports validate: checks migration integrity.
import org.bereketab.migrationLibrary.DatabaseConfig; // Brings in DB config: provides the connection pool.
import picocli.CommandLine; // Imports PicoCLI’s core: parses command-line args and runs commands.
import picocli.CommandLine.Command; // Adds @Command annotation: defines this as a CLI app.
import picocli.CommandLine.Option; // Adds @Option: lets you define CLI flags like --migrations-dir.

@Command(name = "migration-tool", mixinStandardHelpOptions = true, version = "1.0",
        description = "A simple database migration tool") // Marks this as the main CLI command: sets name, adds --help/--version, describes purpose.
public class App implements Runnable { // Main app class: implements Runnable to define default behavior (no subcommand).
    @Option(names = {"-m", "--migrations-dir"}, description = "Directory containing migration files", defaultValue = "migrations")
    private String migrationsDir; // Defines a CLI option: lets users specify where migration files are, defaults to "migrations".

    public static void main(String[] args) { // Entry point: runs when you execute `java -jar migration-tool.jar`.
        // Override DB config if all params are provided
        // Comment hints at future flexibility: could override DatabaseConfig here (not implemented yet).

        App app = new App(); // Creates an App instance: holds the parsed --migrations-dir value.
        // Set up MigrationService and subcommands
        MigrationService migrationService = new MigrationService(DatabaseConfig.getDataSource()); // Initializes the migration service: connects it to the DB pool from DatabaseConfig.
        CommandLine cmd = new CommandLine(app) // Sets up PicoCLI: ties it to this App class as the root command.
                .addSubcommand("migrate", new MigrateCommand(migrationService)) // Adds migrate subcommand: links it to the service for execution.
                .addSubcommand("status", new StatusCommand(migrationService)) // Adds status: shows migration state via the service.
                .addSubcommand("rollback", new RollbackCommand(migrationService)) // Adds rollback: undoes migrations using the service.
                .addSubcommand("validate", new ValidateCommand(migrationService)); // Adds validate: checks checksums via the service.
        int exitCode = cmd.execute(args); // Parses args and runs the command: returns 0 (success) or error code.
        if (cmd.getParseResult().subcommand() != null) { // Checks if a subcommand was run: ensures migrationsDir is set only for valid commands.
            migrationService.setMigrationsDir(app.migrationsDir); // Updates MigrationService with user-specified dir: applies to all subcommands.
        }
        System.exit(exitCode); // Exits with the command’s result: standard CLI behavior (0 = OK, non-zero = error).
    }

    @Override
    public void run() { // Defines default behavior: runs if no subcommand is given (e.g., `java -jar migration-tool.jar`).
        System.out.println("Use a subcommand: migrate, status, rollback, validate"); // Guides user: tells them to pick a command.
    }
}